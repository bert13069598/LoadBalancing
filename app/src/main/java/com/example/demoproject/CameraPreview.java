package com.example.demoproject;

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

import com.example.demoproject.Seg.ImageData;
import com.google.protobuf.ByteString;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraPreview extends AppCompatActivity {

    private final String TAG = "CameraPreview";
    private Receiver receiveDataTaskTask;
    //private SendDataTask sendDataTask = new SendDataTask(CameraPreview.this);
    private ImageView detectView;
    private int port_index;
    private ExecutorService executorService;
    private String bboxdata = " ";
    private Bitmap maskdata;
    private final Ncnn model = new Ncnn();

    private int current_cpugpu = 0;

    // 데이터 송신
    private final String master_IP = "192.168.43.1";
    private final int[] PORT = {3001, 3002}; // 결과값 송신을 위한 포트
    private final Handler handler = new Handler();

    private final Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            executorService.submit(() -> {
                try (Socket clientSocket = new Socket(master_IP, PORT[port_index]);
                     BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream())) {

                    if (port_index == 0) {
                        byte[] byteArray = bboxdata.getBytes();
                        outToServer.write(byteArray);
                        outToServer.flush();
                    } else if (port_index == 1 && maskdata != null) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        maskdata.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                        ImageData.Builder imageDataBuilder = ImageData.newBuilder();
                        imageDataBuilder.setImageData(ByteString.copyFrom(stream.toByteArray()));
                        ImageData imageDataProto = imageDataBuilder.build();

                        byte[] protobufBytes = imageDataProto.toByteArray();
                        outToServer.write(protobufBytes);
                        outToServer.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            handler.postDelayed(this, 10);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        port_index = getIntent().getIntExtra("port_index_key", -1);

        int nThreads = Runtime.getRuntime().availableProcessors();
        executorService = Executors.newFixedThreadPool(nThreads);
        detectView = findViewById(R.id.detectView);

        handler.post(sendRunnable);

        // Receiver
        int corePoolSize = 2;
        int maximumPoolSize = 4;
        long keepAliveTime = 1;
        receiveDataTaskTask = new Receiver(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, callback);

        Spinner spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_cpugpu) {
                    current_cpugpu = position;
                    reload();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
        reload();
    }

    private void reload() {
        int current_model = 0;
        if (!model.loadModel(getAssets(), current_model, current_cpugpu))
            Log.e(TAG, "model load failed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        send_disconnect(port_index);

        handler.removeCallbacks(sendRunnable);

        receiveDataTaskTask.stopReceiving();
        executorService.shutdown();
        handler.removeCallbacksAndMessages(null);
    }

    Receiver.DataReceivedCallback callback = data -> {
        if (data != null) {
            // 스레드 동기화 -> 이 작업이 끝나기 전까지 데이터 접근x
            synchronized (new Object()) {
                Bitmap receiveBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                runOnUiThread(() -> {
                    if (port_index == 0) {
                        bboxdata = model.predict_det(detectView, receiveBitmap);
                    } else if (port_index == 1) {
                        maskdata = model.predict_seg(detectView, receiveBitmap);
                    }
                });
            }
        }
    };

    private void send_disconnect(int port_index) {
        new Thread(() -> {
            // detection
            if (port_index == 0) {
                String stringValue = "off";
                try (Socket clientSocket = new Socket(master_IP, PORT[port_index]);) {

                    BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream());

                    byte[] byteArray = stringValue.getBytes();

                    outToServer.write(byteArray);
                    outToServer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // segmentation
            } else if (port_index == 1) {
                Bitmap bitmap = Bitmap.createBitmap(902, 270, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0x00000000);

                try (Socket clientSocket = new Socket(master_IP, PORT[port_index]);) {
                    BufferedOutputStream outToServer = new BufferedOutputStream(clientSocket.getOutputStream());
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                    byte[] byteArray = stream.toByteArray();

                    outToServer.write(byteArray);
                    outToServer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
