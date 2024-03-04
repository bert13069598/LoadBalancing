package com.example.demoproject_master;

import java.util.Timer;
import java.util.TimerTask;

// 用于线程间数据传递的类
public class DataHolderDET {
    private static DataHolderDET instance;
    private String bboxdata;
    private Timer timer;

    private DataHolderDET() {
        timer = new Timer(true);
        scheduleBboxdataCleanup();
    }

    public static synchronized DataHolderDET getInstance() {
        if (instance == null) {
            instance = new DataHolderDET();
        }
        return instance;
    }

    public synchronized String getBboxdata() {
        return bboxdata;
    }

    public synchronized void setBboxdata(String bboxdata) {
        this.bboxdata = bboxdata;
        resetBboxdataCleanup();
    }

    // 安排bboxdata的定期清理
    private void scheduleBboxdataCleanup() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 在这里同步以防止同时修改bboxdata
                synchronized (DataHolderDET.this) {
                    // 检查bboxdata是否为空或不再需要，然后清理它
                    if (bboxdata != null && isBboxdataNoLongerNeeded()) {
                        bboxdata = null;
                    }
                }
            }
        }, 60000, 60000); // 每60秒执行一次（可根据需要调整）
    }

    // 重置bboxdata的清理任务（可选，取决于您的需求）
    private void resetBboxdataCleanup() {
        // 取消当前的清理任务（如果有的话）
        timer.cancel(); // 注意：这实际上会取消所有由该定时器调度的任务。在更复杂的情况下，您可能需要更精细的控制。
        // 重新安排清理任务
        scheduleBboxdataCleanup();
    }

    // 确定是否不再需要bboxdata的逻辑（您需要根据您的应用程序来实现这个方法）
    private boolean isBboxdataNoLongerNeeded() {
        return true;
    }
}