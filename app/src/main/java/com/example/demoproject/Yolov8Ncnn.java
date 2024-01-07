package com.example.demoproject;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.widget.ImageView;

public class Yolov8Ncnn {
    public native boolean loadModel(AssetManager mgr, int cpugpu);
    public native Bitmap predict(ImageView imageView, Bitmap bitmap);
    static {
        System.loadLibrary("ncnnyolov8");
    }
}



