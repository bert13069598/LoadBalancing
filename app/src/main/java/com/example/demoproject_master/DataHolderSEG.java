package com.example.demoproject_master;

import android.graphics.Bitmap;

class DataHolderSEG {
    private static final DataHolderSEG instance = new DataHolderSEG();
    private Bitmap segData;

    private DataHolderSEG() {
        // private 생성자로 싱글톤 패턴 유지
    }

    public static DataHolderSEG getInstance() {
        return instance;
    }

    public void setSegdata(Bitmap segData) {
        this.segData = segData;
    }

    public Bitmap getSegdata() {
        return segData;
    }
}