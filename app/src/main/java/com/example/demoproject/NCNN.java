package com.example.demoproject;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.widget.ImageView;

public class NCNN {
    public native boolean loadModel(AssetManager mgr,
                                    int cpugpu);

    public native String predict_det(ImageView imageView, Bitmap bitmap);

    public native Bitmap predict_seg(ImageView imageView, Bitmap bitmap);

    static {
        System.loadLibrary("ncnntotal");
    }
}
