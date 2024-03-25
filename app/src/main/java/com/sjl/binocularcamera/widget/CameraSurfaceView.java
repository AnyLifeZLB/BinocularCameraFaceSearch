package com.sjl.binocularcamera.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.sjl.binocularcamera.util.CameraHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 仅仅用最原始的方式预览摄像头数据，仅供参考，用户自行管理
 *
 */
public class CameraSurfaceView extends FrameLayout implements SurfaceHolder.Callback,
        Camera.PreviewCallback, Camera.ErrorCallback {
    public static final String TAG = CameraSurfaceView.class.getSimpleName();
    public static final int CAMERA_FACING_BACK = 0;

    public static final int CAMERA_FACING_FRONT = 1;

    public static final int CAMERA_USB = 2;

    // 相机
    public Camera mCamera;
    protected Camera.Parameters mCameraParam;
    protected int mCameraId;

    protected SurfaceHolder mSurfaceHolder;
    protected int mPreviewDegree = -1;
    /**
     * 预览宽
     */
    public int mPreviewWidth;
    /**
     * 预览高
     */
    public int mPreviewHeight;
    protected boolean mIsCompletion = false;
    protected DisplayMetrics dm;
    /**
     * true Ir,falseRgb
     */
    private boolean ir;
    /**
     * 当前相机的ID。
     */
    private int cameraFacing = CAMERA_FACING_FRONT;

    private int screenHeight, screenWidth;
    private Context mContext;
    /**
     * 建议选择640*480， 1280*720
     * int maxWithOrHeight = 1280;16/9
     */
    private int maxWithOrHeight = 640;
    private float rate = 4 / 3f;

    private SurfaceView surfaceView;
    private static Map<Integer, Integer> exist = new ArrayMap<>();





    public interface OnPreviewListener {
        void onPreviewFrame(byte[] data,int degree);
    }




    public CameraSurfaceView(Context context) {
        super(context);
        mContext = context;
    }


    /**
     * 创建预览view
     */
    public void createPreview() {
        surfaceView = new SurfaceView(mContext);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        dm = new DisplayMetrics();
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getMetrics(dm);
            screenHeight = display.getHeight();
            screenWidth = display.getWidth();
        }
        addView(surfaceView);

    }

    @Override
    public void onError(int error, Camera camera) {

    }

    /**
     * 预览数据
     *
     * @param data the contents of the preview frame in the format defined
     *  by {@link android.graphics.ImageFormat}, which can be queried
     *  with {@link android.hardware.Camera.Parameters#getPreviewFormat()}.
     *  If {@link android.hardware.Camera.Parameters#setPreviewFormat(int)}
     *             is never called, the default will be the YCbCr_420_SP
     *             (NV21) format.
     * @param camera the Camera service object.
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mIsCompletion) {
            return;
        }
        if (onPreviewListener != null) {
            //在一个子线程中操作
            onPreviewListener.onPreviewFrame(data,mPreviewDegree);
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mCamera == null) {
            try {
                mCamera = open();
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (mCamera == null) {
            Log.e(TAG, "相机未找到");
            setVisibility(GONE);//不显示，防止界面黑屏
            return;
        }
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "mCameraId:" + mCameraId + ",surfaceDestroyed");
    }


    private synchronized Camera open() {
        Camera camera = null;
        int numCameras = Camera.getNumberOfCameras();
        if (numCameras == 0) {
            return null;
        }
        Log.i(TAG, "cameraFacing:" + cameraFacing);

//       int countFront = 0, countBack = 0;
//        for (int i = 0; i < numCameras; i++) {
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            Camera.getCameraInfo(i, cameraInfo);
//            Log.i(TAG, "摄像头facing:" + cameraInfo.facing);
//            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                countFront++;
//            } else {
//                countBack++;
//            }
//        }

        switch (cameraFacing) {

            case CAMERA_FACING_BACK: {
                camera = initCamera(0);
                break;
            }
            //双目
            case CAMERA_FACING_FRONT: {
                camera = initCamera(0);
                Log.e("TAG", "Front");
                break;
            }
            case CAMERA_USB: {
                camera = initCamera(1);
                break;
            }

            default:
                break;

        }
        return camera;
    }

    private synchronized Camera initCamera(int cameraId) {
        if (exist.get(cameraId) != null) {
            return null;
        }
        Camera camera = Camera.open(cameraId);
        mCameraId = cameraId;
        exist.put(mCameraId, mCameraId);
        return camera;
    }

    /**
     * 开始预览
     */
    public synchronized void startPreview() {
        if (mCamera == null) {
            return;
        }
        mCameraParam = mCamera.getParameters();
        if (mPreviewDegree < 0) {
            mPreviewDegree = getRotateAngle();
        }

        mCamera.setDisplayOrientation(mPreviewDegree);
        mCameraParam.set("rotation", mPreviewDegree);
        mCameraParam.setPictureFormat(ImageFormat.JPEG);


        List<Camera.Size> supportedPictureSizes = mCameraParam.getSupportedPictureSizes();
        Camera.Size pictureSize = CameraHelper.getInstance().getSizeByRate(supportedPictureSizes, rate, maxWithOrHeight);

        mCameraParam.setPictureSize(pictureSize.width, pictureSize.height);

        List<Camera.Size> supportedPreviewSizes = mCameraParam.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getInstance().getSizeByRate(supportedPreviewSizes, rate, maxWithOrHeight);
        mPreviewWidth = previewSize.width;
        mPreviewHeight = previewSize.height;

        mCameraParam.setPreviewSize(mPreviewWidth, mPreviewHeight);
        Log.e(TAG, "pictureSize,width = " + pictureSize.width + ", height = " + pictureSize.height);
        Log.e(TAG, "previewSize,width=" + mPreviewWidth + ", height = " + mPreviewHeight + ",previewDegree=" + mPreviewDegree);


        // 计算缩放比例
        int width;
        int height;
        float sy;
        if (mPreviewWidth > screenWidth) {
            //等比缩放
            sy = (float) screenWidth / mPreviewWidth;
            width = screenWidth;
            height = (int) (mPreviewHeight * sy);
        } else {
            width = mPreviewWidth;
            height = mPreviewHeight;
        }

        LinearLayout.LayoutParams cameraFL = new LinearLayout.LayoutParams(width, height);
        setLayoutParams(cameraFL);

        mCamera.setParameters(mCameraParam);

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.stopPreview();
            mCamera.setErrorCallback(this);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();

            mIsCompletion = false;
        } catch (Exception e) {
            e.printStackTrace();
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }

        }
    }


    /**
     * 停止预览
     */
    public synchronized void stopPreview() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mIsCompletion = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 关闭预览并释放内存
     */
    public synchronized void release() {
        exist.clear();
        if (mCamera != null) {
            try {
                stopPreview();
                mCamera.setErrorCallback(null);
                mCamera.setPreviewCallback(null);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mCamera != null) {
                    mCamera.release();
                    mCamera = null;
                }
            }
        }
        if (mSurfaceHolder != null) {
            mSurfaceHolder.removeCallback(this);
        }

    }

    private int getRotateAngle() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);
        // 获取当前手机的选装角度
        int rotation = ((WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }


    public void print() {
//        setScaleX(-1);
        if (mCameraParam == null) {
            return;
        }
        List<Camera.Size> supportedPreviewSizes = sortSize(mCameraParam.getSupportedPreviewSizes());
        List<Camera.Size> supportedPictureSizes = sortSize(mCameraParam.getSupportedPictureSizes());
        StringBuilder sb = new StringBuilder("\n");
        for (Camera.Size size : supportedPreviewSizes) {
            sb.append(size.width + ":" + size.height + " ");
        }
        Log.i(TAG, "预览分辨率：" + sb.toString());

        StringBuilder sb2 = new StringBuilder("\n");
        for (Camera.Size size : supportedPictureSizes) {
            sb2.append(size.width + ":" + size.height + " ");
        }
        Log.i(TAG, "拍照分辨率：" + sb2.toString());

        Configuration mConfiguration = this.getResources().getConfiguration(); //获取设置的配置信息
        int ori = mConfiguration.orientation; //获取屏幕方向
        if (ori == mConfiguration.ORIENTATION_LANDSCAPE) {
            //横屏
            Log.e(TAG, "横屏");
        } else if (ori == mConfiguration.ORIENTATION_PORTRAIT) {
            //竖屏
            Log.e(TAG, "竖屏");
        }
    }

    private List<Camera.Size> sortSize(List<Camera.Size> sizeList) {
        List<Camera.Size> list = new ArrayList(sizeList);
        Collections.sort(list, new Comparator<Camera.Size>() {
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                } else {
                    return bPixels > aPixels ? 1 : 0;
                }
            }
        });
        return list;
    }

    private OnPreviewListener onPreviewListener;

    public void setOnPreviewListener(OnPreviewListener onPreviewListener) {
        this.onPreviewListener = onPreviewListener;
    }


    public int getPreviewDegree() {
        return mPreviewDegree;
    }


    public CameraSurfaceView setCameraFacing(int cameraFacing) {
        this.cameraFacing = cameraFacing;
        return this;
    }

    public CameraSurfaceView setMaxWithOrHeight(int maxWithOrHeight) {
        this.maxWithOrHeight = maxWithOrHeight;
        return this;
    }


    public CameraSurfaceView setRate(float rate) {
        this.rate = rate;
        return this;
    }


    public CameraSurfaceView setPreviewDegree(int mPreviewDegree) {
        this.mPreviewDegree = mPreviewDegree;
        return this;
    }

    public void setIr(boolean ir) {
        this.ir = ir;
    }



}
