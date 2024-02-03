package com.example.demoproject;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReceiveDataTask extends ThreadPoolExecutor {

    private static final int PORT_NUMBER = 1300; // 영상 수신을 위한 포트 넘버
    private volatile boolean stopReceiving = false;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private byte[] data = new byte[4096]; // 1024 = 1KB 크기 버퍼
    private byte[] receivedData;
    private BufferedInputStream inFromClient;
    private ByteArrayOutputStream byteArrayOutputStream;
    private int bytesRead;
    private DataReceivedCallback dataReceivedCallback;

    public ReceiveDataTask(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit){
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>());
    }

    public void startReceiving() {
        execute(new ReceiveTask());
    }

    public void stopReceiving() {
        stopReceiving = true;
        shutdown();
    }

    // 데이터를 전달하기 위한 콜백 인터페이스
    public interface DataReceivedCallback {
        void onDataReceived(byte[] data);
    }

    public void setDataReceivedCallback(DataReceivedCallback callback) {
        this.dataReceivedCallback = callback;
    }

    private class ReceiveTask implements Runnable{
        @Override
        public void run(){
            try{
                serverSocket = new ServerSocket(PORT_NUMBER);
                while (!stopReceiving) {
                    clientSocket = serverSocket.accept();
                    inFromClient = new BufferedInputStream(clientSocket.getInputStream());
                    byteArrayOutputStream = new ByteArrayOutputStream();

                    while ((bytesRead = inFromClient.read(data)) != -1) {
                        byteArrayOutputStream.write(data, 0, bytesRead);
                    }
                    receivedData = byteArrayOutputStream.toByteArray();

                    // 데이터를 수신받았을 때
                    if (receivedData != null) {
                        // 스레드 동기화
                        synchronized (dataReceivedCallback) {
                            // 데이터 콜백 호출
                            if (dataReceivedCallback != null) {
                                dataReceivedCallback.onDataReceived(receivedData);
                            }
                        }
                    } else {
                        Log.e("Receive Task", "Received data is null.");
                    }
                    byteArrayOutputStream.close();
                    clientSocket.close();
                }
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}