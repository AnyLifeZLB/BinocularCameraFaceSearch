package com.sjl.binocularcamera;

import android.app.Application;

import java.io.File;

public class FaceSearchApplication extends Application {

    //6. 所有的人脸都必须通过SDK 的API 插入到人脸管理目录，而不是File 文件放入到目录就行，SDK API 还会提取人脸特征操作
    public static String CACHE_SEARCH_FACE_DIR;

    @Override
    public void onCreate() {
        super.onCreate();
        //6. 所有的人脸都必须通过SDK 的API 插入到人脸管理目录，而不是File 文件放入到目录就行，SDK API 还会提取人脸特征操作
        CACHE_SEARCH_FACE_DIR = getFilesDir().getPath() + "/faceSearch";

        File file = new File(CACHE_SEARCH_FACE_DIR);
        if (!file.exists()) {
            file.mkdir();
        }

    }


}
