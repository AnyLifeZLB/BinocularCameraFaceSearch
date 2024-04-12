package com.sjl.binocularcamera;

import static com.ai.face.faceSearch.search.SearchProcessTipsCode.EMGINE_INITING;
import static com.ai.face.faceSearch.search.SearchProcessTipsCode.FACE_DIR_EMPTY;
import static com.ai.face.faceSearch.search.SearchProcessTipsCode.MASK_DETECTION;
import static com.ai.face.faceSearch.search.SearchProcessTipsCode.NO_LIVE_FACE;
import static com.ai.face.faceSearch.search.SearchProcessTipsCode.NO_MATCHED;
import static com.ai.face.faceSearch.search.SearchProcessTipsCode.THRESHOLD_ERROR;
import static com.sjl.binocularcamera.FaceSearchApplication.CACHE_SEARCH_FACE_DIR;
import com.bumptech.glide.Glide;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.ai.face.faceSearch.search.FaceSearchEngine;
import com.ai.face.faceSearch.search.SearchProcessBuilder;
import com.ai.face.faceSearch.search.SearchProcessCallBack;
import com.ai.face.faceSearch.utils.RectLabel;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.sjl.binocularcamera.util.BitmapUtils;
import com.sjl.binocularcamera.util.CameraHelper;
import com.sjl.binocularcamera.widget.CameraSurfaceView;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 双目摄像头取原始NV21 数据，调用SDK  Run Search
 *
 *
 */
public class CameraActivity extends BaseActivity {
    public static final String TAG = CameraActivity.class.getSimpleName();

    private CameraSurfaceView mCameraSurfaceView1, mCameraSurfaceView2;
    private ImageView search_result;
    private TextView tips, logText;
    private LinearLayout ll_surface_layout;
    private int camera1DataMean, camera2DataMean;
    private volatile boolean rgbOrIrConfirm, camera1IsRgb;

    private Bitmap realTimeFaceBmp;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_camera;
    }




    /**
     * 线程池
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool();



    @Override
    protected void initView() {
        search_result=findViewById(R.id.image_result);
        tips=findViewById(R.id.searchTips);
        logText =findViewById(R.id.log);

        ll_surface_layout = findViewById(R.id.ll_surface_layout);
        ll_surface_layout.removeAllViews();
    }


    @Override
    protected void initData() {
        int numberOfCameras = CameraHelper.getNumberOfCameras();
        if (numberOfCameras < 2) {
            Toast.makeText(this, "未检测到2个摄像头,请检查设备是否正常", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mCameraSurfaceView1 = new CameraSurfaceView(this);
        ll_surface_layout.addView(mCameraSurfaceView1);
        mCameraSurfaceView1.setPreviewDegree(Constant.previewDegree)
                .setCameraFacing(CameraSurfaceView.CAMERA_FACING_FRONT)
                .createPreview();

        mCameraSurfaceView2 = new CameraSurfaceView(this);
        ll_surface_layout.addView(mCameraSurfaceView2);


        mCameraSurfaceView2.setPreviewDegree(Constant.previewDegree)
                .setCameraFacing(CameraSurfaceView.CAMERA_USB)
              .createPreview();

        mCameraSurfaceView1.setOnPreviewListener((data, degree) -> {
            if (!rgbOrIrConfirm) {
                rgbOrIr(0,mCameraSurfaceView1.mPreviewWidth, mCameraSurfaceView1.mPreviewHeight, data);
                if (rgbOrIrConfirm) {
                    //要等两个摄像头都返回一帧数据，rgbOrIrConfirm才会被赋值，此时才能判断到底哪个是RGB摄像头
                    choiceRgbOrIrType(0, data);
                    if (camera1IsRgb) {
                        mCameraSurfaceView1.setIr(false);
                    } else {
                        mCameraSurfaceView1.setIr(true);
                    }
                }
            }
        });


        mCameraSurfaceView2.setOnPreviewListener((data, degree) -> {

            if (!rgbOrIrConfirm) {
                rgbOrIr(1, mCameraSurfaceView2.mPreviewWidth, mCameraSurfaceView2.mPreviewHeight, data);
                if (rgbOrIrConfirm) {
                    //要等两个摄像头都返回一帧数据，rgbOrIrConfirm才会被赋值，此时才能判断到底哪个是RGB摄像头
                    choiceRgbOrIrType(1, data);
                    if (camera1IsRgb) {
                        mCameraSurfaceView2.setIr(true);
                    } else {
                        mCameraSurfaceView2.setIr(false);
                    }
                }
            }


            // 1. 在子线程中执行方法，小心内存泄露
            executorService.execute(() -> {
                if (isSafe()&& Boolean.FALSE.equals(FaceSearchEngine.Companion.getInstance().isProcessing())) {
                    realTimeFaceBmp = BitmapUtils.convertPreviewFrameToBitmap(data, 640, 480);
                    if(realTimeFaceBmp!=null){

                        realTimeFaceBmp=BitmapUtils.adjustPhotoRotation(realTimeFaceBmp,-degree);
                        //自行保证Bitmap 的方向角度正确无旋转，清晰度，断点调试看看Bitmap
                        FaceSearchEngine.Companion.getInstance().runSearch(realTimeFaceBmp);
                        Log.e(TAG, "getInstance().runSearch ---"+Thread.currentThread().getName()+ "Degree:"+ -degree);
                    }
                }
            });

        });



        // 2.各种参数的初始化设置
        SearchProcessBuilder faceProcessBuilder = new SearchProcessBuilder.Builder(getApplication())
                .setLifecycleOwner(this)
                .setNeedMultiValidate(false)          //是否需要筛选结果防止误识别，需要硬件CPU配置高，可以先尝试看看
                .setThreshold(0.79f)            //阈值设置，范围限 [0.75 , 0.95] 识别可信度，也是识别灵敏度
                .setNeedNirLiveness(false)           //是否需要红外活体能力，只有1:N 有
                .setNeedRGBLiveness(false)            //是否需要普通RGB活体检测能力，只有1:N 有

                //6. 所有的人脸都必须通过SDK 的API 插入到人脸管理目录，而不是File 文件放入到目录就行，SDK API 还会提取人脸特征操作
                .setFaceLibFolder(CACHE_SEARCH_FACE_DIR)  //内部存储目录中保存N 个人脸图片库的目录
                .setProcessCallBack(new SearchProcessCallBack() {
                    //人脸识别检索回调
                    @Override
                    public void onMostSimilar(String similar, Bitmap realTimeImg) {
                        if(isSafe()){
                            //根据你的业务逻辑，各种提示 & 触发成功后面的操作
                            tips.setText(similar);
                            Glide.with(CameraActivity.this)
                                    .load(CACHE_SEARCH_FACE_DIR + File.separatorChar + similar)
                                    .skipMemoryCache(false)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .transform(new RoundedCorners(11))
                                    .into(search_result);
                        }
                    }

                    @Override
                    public void onProcessTips(int i) {
                        showPrecessTips(i);
                    }
                    //坐标框和对应的 搜索匹配到的图片标签
                    //人脸检测成功后画白框，此时还没有标签字段Label 字段为空
                    //人脸搜索匹配成功后白框变绿框，并标记出对应的Label
                    //部分设备会有左右图像翻转问题
                    @Override
                    public void onFaceMatched(List<RectLabel> rectLabels) {
//                        binding.graphicOverlay.drawRect(rectLabels, cameraXFragment);
//                        if(!rectLabels.isEmpty()) {
//                            tips.setText("");
//                        }
                    }

                    @Override
                    public void onLog(String log) {
                        logText.setText(log);
                    }

                }).create();


        //3.初始化引擎
        FaceSearchEngine.Companion.getInstance().initSearchParams(faceProcessBuilder);
    }

    private boolean isSafe(){
        return (!isDestroyed() && !isFinishing());
    }




    /**
     * 显示提示
     *
     * @param code
     */
    private void showPrecessTips(int code) {
        search_result.setImageResource(R.mipmap.ic_launcher);
        switch (code) {
            default:
                tips.setText("提示码："+code);
                break;

            case THRESHOLD_ERROR :
                tips.setText("识别阈值Threshold范围为0.75-0.95");
                break;

            case MASK_DETECTION:
                tips.setText("请摘下口罩");
                break;

            case NO_LIVE_FACE:
                tips.setText("未检测到人脸");
                logText.setText("");
                break;

            case EMGINE_INITING:
                tips.setText("初始化中");
                break;

            case FACE_DIR_EMPTY:
                tips.setText("人脸库为空");
                break;

            case NO_MATCHED:
                //本次摄像头预览帧无匹配而已，会快速取下一帧进行分析检索
                tips.setText("无匹配");
                logText.setText("");

                break;
        }
    }



    /**
     * 判断是RGB 摄像头还是双目摄像头
     *
     * 参考：https://blog.csdn.net/yzzzza/article/details/107670521
     *
     * @param index
     * @param data
     */
    private synchronized void rgbOrIr(int index, int PREFER_WIDTH, int PREFER_HEIGHT, byte[] data) {
        byte[] tmp = new byte[PREFER_WIDTH * PREFER_HEIGHT];
        try {
            System.arraycopy(data, 0, tmp, 0, PREFER_WIDTH * PREFER_HEIGHT);
        } catch (NullPointerException e) {
            Log.e(TAG, String.valueOf(e.getStackTrace()));
        }
        int count = 0;
        int total = 0;
        for (int i = 0; i < PREFER_WIDTH * PREFER_HEIGHT; i = i + 10) {
            total += tmp[i];
            count++;
        }
        if (index == 0) {
            camera1DataMean = total / count;
        } else {
            camera2DataMean = total / count;
        }
        if (camera1DataMean != 0 && camera2DataMean != 0) {
            if (camera1DataMean > camera2DataMean) {
                camera1IsRgb = true; //把两个摄像头一帧数据的所有byte值加起来比大小
            } else {
                camera1IsRgb = false;
            }
            rgbOrIrConfirm = true;
        }
    }

    private void choiceRgbOrIrType(int index, byte[] data) {
        // camera1如果为rgb数据，调用dealRgb，否则为Ir数据，调用Ir
        if (index == 0) {
            if (camera1IsRgb) {
                dealRgb(data);
            } else {
                dealIr(data);
            }
        } else {
            if (camera1IsRgb) {
                dealIr(data);
            } else {
                dealRgb(data);
            }
        }
    }

    private void dealIr(byte[] data) {
    }

    private void dealRgb(byte[] data) {
    }


    @Override
    public void onResume() {
        super.onResume();
        mCameraSurfaceView1
                .setPreviewDegree(Constant.previewDegree)
                .startPreview();
        mCameraSurfaceView2
                .setPreviewDegree(Constant.previewDegree)
                .startPreview();
    }


    @Override
    public void onPause() {
        super.onPause();
        mCameraSurfaceView1.stopPreview();
        mCameraSurfaceView2.stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ll_surface_layout != null) {
            ll_surface_layout.removeAllViews();
        }
        mCameraSurfaceView1.release();
        mCameraSurfaceView2.release();
    }



//    public void btnFaceDetect(View view) {
//        if (mCameraSurfaceView1 != null) {
//            mCameraSurfaceView1.startPreview();
//        }
//
//        if (mCameraSurfaceView2 != null) {
//            mCameraSurfaceView2.startPreview();
//        }
//    }

    public void btnSetting(View view) {
        startActivity(new Intent(this,CameraSetting.class));
    }

}
