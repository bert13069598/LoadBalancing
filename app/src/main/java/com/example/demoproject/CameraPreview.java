package com.example.demoproject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraPreview extends AppCompatActivity {

    private final String TAG="CameraPreview";
    private ReceiveDataTask receiveDataTaskTask;
    //private SendDataTask sendDataTask = new SendDataTask(CameraPreview.this);
    private ImageView detectView;
    private int port_index;
    private ExecutorService executorService;

    // detection
    //private final NanoDetNcnn nanodetncnn = new NanoDetNcnn();
    private String bboxdata =" ";

    // segmentation
    //private final Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();

    // Both
    private final Ncnn model = new Ncnn();

    private Bitmap maskdata;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;
    int corePoolSize = 2;
    int maximumPoolSize = 4;
    long keepAliveTime = 1;
    private int nThreads;
    private Bitmap receiveBitmap;
    private final Object bitmapLock = new Object();

    // 데이터 송신
    private final String master_IP = "192.168.43.1";

    private final int[] PORT = {13579, 2468}; // 결과값 송신을 위한 포트
    private boolean sendRunning = false;
    private final Handler handler = new Handler();

    private final Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if(port_index == 0){
                        send_connect_det(port_index);
                    }
                    else if(port_index == 1){
                        if(maskdata!=null){
                            send_connect_seg(port_index);
                        }

                    }
                }
            });
            handler.postDelayed(this, 10);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        initsetting();

        startSendingResults();
        ConnectServer();

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        reload();
    }

    private void reload() {

        boolean ret_init_model = model.loadModel(getAssets(),current_model, current_cpugpu);
        if (!ret_init_model)
            Log.e(TAG, "model load failed");
    }

    private void initsetting(){
        //화면 계속 켜진 상태로 유지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        port_index = getIntent().getIntExtra("port_index_key",-1);

        Log.e(TAG, "port_index : "+port_index);

        nThreads = Runtime.getRuntime().availableProcessors();
        executorService = Executors.newFixedThreadPool(nThreads);
        detectView = findViewById(R.id.detectView);
    }

    public void setImageBitmap(Bitmap bitmap) {
        detectView.setImageBitmap(bitmap);
    }

    @Override
    public void onBackPressed() {
        send_disconnect(port_index);
        stopSendingResults();
        receiveDataTaskTask.stopReceiving();

        Intent intent = new Intent(CameraPreview.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        send_disconnect(port_index);
        stopSendingResults();

        executorService.shutdown();
        handler.removeCallbacksAndMessages(null);
    }


    // detection
    private void send_connect_det(int port_index){
        new Thread(new Runnable() {
            @Override
            public void run() {

                String stringValue = bboxdata; // BBOX 데이터 전송

                if (stringValue == null) {
                    stringValue = "";
                }

                try {
                    Socket clientSocket = new Socket(master_IP, 13579);
                    BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream());

                    byte[] byteArray = stringValue.getBytes();

                    outToServer.write(byteArray);
                    outToServer.flush(); // 버퍼 비우기
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // segmentation
    private void send_connect_seg(int port_index){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket clientSocket = new Socket(master_IP, 2468);

                    // seg이미지 전송
                    BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream());
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    maskdata.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                    byte[] byteArray = stream.toByteArray();
                    //maskdata.recycle();

                    outToServer.write(byteArray);
                    outToServer.flush(); // 버퍼 비우기
                    clientSocket.close();

                    //Log.e("Send SEG", "Success!");
                } catch (IOException e) {
                    //Log.e("Send SEG", "SocketThread runs on an error!");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 수신부
    private void DisconnectServer(){
        receiveDataTaskTask.stopReceiving();
    }

    private void ConnectServer(){
        receiveDataTaskTask = new ReceiveDataTask(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS);
        receiveDataTaskTask.setDataReceivedCallback(callback);
        receiveDataTaskTask.startReceiving();
    }

    ReceiveDataTask.DataReceivedCallback callback = new ReceiveDataTask.DataReceivedCallback() {
        @Override
        public void onDataReceived(byte[] data) {
            if (data != null) {
                // 스레드 동기화 -> 이 작업이 끝나기 전까지 데이터 접근x
                synchronized (bitmapLock) {
                    receiveBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //detectView.setImageBitmap(receiveBitmap);
                            // detection
                                //bboxdata = nanodetncnn.predict(detectView, receiveBitmap);
                            // segmentation
                                //maskdata = yolov8ncnn.predict(detectView, receiveBitmap);
                            if(port_index==0){
                                bboxdata = model.predict_det(detectView, receiveBitmap);
                            }
                            else if(port_index==1){
                                maskdata = model.predict_seg(detectView, receiveBitmap);
                            }

                        }
                    });

                }
            } else {
                Log.e(TAG, "Received data is null.");
            }
        }
    };

    // 송신부
    private void startSendingResults() {
        sendRunning = true;
        handler.post(sendRunnable);
    }

    private void stopSendingResults() {
        sendRunning = false;
        handler.removeCallbacks(sendRunnable);
    }

    private void send_disconnect(int port_index){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String stringValue = "off";
                try {
                    Socket clientSocket = new Socket(master_IP, PORT[port_index]);
                    BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream());

                    byte[] byteArray = stringValue.getBytes();

                    outToServer.write(byteArray);
                    outToServer.flush(); // 버퍼 비우기
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

}
