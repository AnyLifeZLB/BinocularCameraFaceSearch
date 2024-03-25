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
    private ImageView iv_rgb, iv_ir,search_result;
    
    private TextView tips, logText;
    private LinearLayout ll_surface_layout;
    private int camera1DataMean, camera2DataMean;
    private volatile boolean rgbOrIrConfirm, camera1IsRgb;
    private Button btn_rgb,btn_ir;

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
        iv_rgb = findViewById(R.id.iv_rgb);
        iv_ir = findViewById(R.id.iv_ir);
        btn_rgb = findViewById(R.id.btn_rgb);
        btn_ir = findViewById(R.id.btn_ir);
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

        mCameraSurfaceView1.setOnPreviewListener(new CameraSurfaceView.OnPreviewListener() {
            @Override
            public void onPreviewFrame(byte[] data,int degree) {
                if (!rgbOrIrConfirm) {
                    rgbOrIr(0,mCameraSurfaceView1.mPreviewWidth, mCameraSurfaceView1.mPreviewHeight, data);
                    if (rgbOrIrConfirm) {
                        //要等两个摄像头都返回一帧数据，rgbOrIrConfirm才会被赋值，此时才能判断到底哪个是RGB摄像头
                        choiceRgbOrIrType(0, data);
                        if (camera1IsRgb) {
                            mCameraSurfaceView1.setIr(false);
                            btn_rgb.setText(R.string.rgb_capture);
                            btn_ir.setText(R.string.ir_capture);
                            Log.i(TAG, "mCameraSurfaceView1 is Rgb.mCameraSurfaceView2 is Ir.");
                        } else {
                            mCameraSurfaceView1.setIr(true);
                            btn_rgb.setText(R.string.ir_capture);
                            btn_ir.setText(R.string.rgb_capture);
                            Log.i(TAG, "mCameraSurfaceView1 is Ir.mCameraSurfaceView2 is Rgb.");
                        }
                    }
                }

            }
        });


        mCameraSurfaceView2.setOnPreviewListener(new CameraSurfaceView.OnPreviewListener() {
            @Override
            public void onPreviewFrame(byte[] data,int degree) {

                if (!rgbOrIrConfirm) {
                    rgbOrIr(1, mCameraSurfaceView2.mPreviewWidth, mCameraSurfaceView2.mPreviewHeight, data);
                    if (rgbOrIrConfirm) {
                        //要等两个摄像头都返回一帧数据，rgbOrIrConfirm才会被赋值，此时才能判断到底哪个是RGB摄像头
                        choiceRgbOrIrType(1, data);
                        if (camera1IsRgb) {
                            mCameraSurfaceView2.setIr(true);
                            btn_rgb.setText(R.string.ir_capture);
                            btn_ir.setText(R.string.rgb_capture);
                            Log.i(TAG, "mCameraSurfaceView1 is Ir.mCameraSurfaceView2 is Rgb.");
                        } else {
                            mCameraSurfaceView2.setIr(false);
                            btn_rgb.setText(R.string.rgb_capture);
                            btn_ir.setText(R.string.ir_capture);
                            Log.i(TAG, "mCameraSurfaceView1 is Rgb.mCameraSurfaceView2 is Ir.");

                        }
                    }
                }


                // 1. 在子线程中执行方法，小心内存泄露
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (isSafe()&& Boolean.FALSE.equals(FaceSearchEngine.Companion.getInstance().isProcessing())) {
                            realTimeFaceBmp = BitmapUtils.convertPreviewFrameToBitmap(data, 640, 480);
                            if(realTimeFaceBmp!=null){
                                //自行保证Bitmap 的方向角度正确无旋转，清晰度，断点调试看看Bitmap
                                FaceSearchEngine.Companion.getInstance().runSearch(adjustPhotoRotation(realTimeFaceBmp,-degree));
                                Log.e(TAG, "getInstance().runSearch ---"+Thread.currentThread().getName()+ "Degree:"+ -degree);
                            }
                        }
                    }
                });

            }
        });



        // 2.各种参数的初始化设置
        SearchProcessBuilder faceProcessBuilder = new SearchProcessBuilder.Builder(getApplication())
                .setLifecycleOwner(this)
                .setThreshold(0.79f)            //阈值设置，范围限 [0.75 , 0.95] 识别可信度，也是识别灵敏度
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


    Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {
        if(orientationDegree==0){
            return bm;
        }

        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        try {
            Bitmap bm1 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            return bm1;
        } catch (OutOfMemoryError ex) {
        }
        return null;
    }


    Bitmap adjustPhotoRotation2(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        float targetX, targetY;
        if (orientationDegree == 90) {
            targetX = bm.getHeight();
            targetY = 0;
        } else {
            targetX = bm.getHeight();
            targetY = bm.getWidth();
        }

        final float[] values = new float[9];
        m.getValues(values);

        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];

        m.postTranslate(targetX - x1, targetY - y1);
        Bitmap bm1 = Bitmap.createBitmap(bm.getHeight(), bm.getWidth(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        Canvas canvas = new Canvas(bm1);
        canvas.drawBitmap(bm, m, paint);

        return bm1;
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

    public void btnRgbTakePhoto(View view) {
        if (mCameraSurfaceView1 == null ||  mCameraSurfaceView1.mCamera == null) {
            return;
        }
        mCameraSurfaceView1.mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                bitmap = convertBmp(bitmap);
                Matrix matrix = new Matrix();
                matrix.setRotate(mCameraSurfaceView1.getPreviewDegree());
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                bitmap = BitmapUtils.scaleBitmap(bitmap, 0.5f);

                iv_rgb.setImageBitmap(bitmap);

                mCameraSurfaceView1.startPreview();

                saveImg(bitmap, "rgb.jpg");
            }
        });
    }


    /**
     * 红外
     *
     * @param view
     */
    public void btnIrTakePhoto(View view) {
        if (mCameraSurfaceView2 == null ||  mCameraSurfaceView2.mCamera == null) {
            Toast.makeText(this,"未找到摄像头",Toast.LENGTH_LONG).show();
            return;
        }
        mCameraSurfaceView2.mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap bitmap = convertBmp(bm);
                Matrix matrix = new Matrix();
                matrix.setRotate(mCameraSurfaceView2.getPreviewDegree());
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                bitmap = BitmapUtils.scaleBitmap(bitmap, 0.5f);
                iv_ir.setImageBitmap(bitmap);
                mCameraSurfaceView2.startPreview();
                saveImg(bm, "ir.jpg");

            }
        });
    }

    public Bitmap convertBmp(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1); // 镜像水平翻转
        Bitmap convertBmp = Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);

        return convertBmp;
    }

    private void saveImg(Bitmap bm, String fileName) {
        File dir = new File(Environment.getExternalStorageDirectory(), "temp");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        BufferedOutputStream bos;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 把预览帧转为图片并保存
     *
     * @param data
     * @param width
     * @param height
     * @param fileName
     */
    public void savePreviewFrameToBitmap(byte[] data, int width, int height, String fileName) {
        Bitmap bitmap = BitmapUtils.convertPreviewFrameToBitmap(data, width, height);
        saveImg(bitmap, fileName);
    }

    public void btnFaceDetect(View view) {
        if (mCameraSurfaceView1 != null) {
            mCameraSurfaceView1.startPreview();
        }

        if (mCameraSurfaceView2 != null) {
            mCameraSurfaceView2.startPreview();
        }
    }

    public void btnSetting(View view) {
        startActivity(new Intent(this,CameraSetting.class));
    }
}
