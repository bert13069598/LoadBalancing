package com.example.demoproject_master;

import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Bitmap;

class DataHolderSEG {
    private static final DataHolderSEG instance = new DataHolderSEG();
    private Bitmap segData;
    private Timer timer;

    private DataHolderSEG() {
        timer = new Timer(true);
        scheduleSegdataCleanup();
    }

    public static DataHolderSEG getInstance() {
        return instance;
    }

    public synchronized Bitmap getSegdata() {
        return segData;
    }

    public synchronized void setSegdata(Bitmap segData) {
        this.segData = segData;
        resetSegdataCleanup();
    }

    private void scheduleSegdataCleanup() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 在这里同步以防止同时修改bboxdata
                synchronized (DataHolderSEG.this) {
                    // 检查bboxdata是否为空或不再需要，然后清理它
                    if (segData != null && isSegdataNoLongerNeeded()) {
                        segData = null;
                    }
                }
            }
        }, 60000, 60000); // 每60秒执行一次（可根据需要调整）
    }

    private void resetSegdataCleanup() {
        // 取消当前的清理任务（如果有的话）
        timer.cancel(); // 注意：这实际上会取消所有由该定时器调度的任务。在更复杂的情况下，您可能需要更精细的控制。
        // 重新安排清理任务
        scheduleSegdataCleanup();
    }

    // 确定是否不再需要Segdata的逻辑（您需要根据您的应用程序来实现这个方法）
    private boolean isSegdataNoLongerNeeded() {
        return true;
    }


}