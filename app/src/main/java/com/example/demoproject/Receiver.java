package com.example.demoproject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Receiver extends ThreadPoolExecutor {

    private static final int PORT_NUMBER = 1300; // 영상 수신을 위한 포트 넘버
    private volatile boolean stopReceiving = false;
    private final byte[] data = new byte[1]; // 1024 = 1KB 크기 버퍼
    private final DataReceivedCallback dataReceivedCallback;

    public Receiver(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            DataReceivedCallback callback) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>());
        this.dataReceivedCallback = callback;
        execute(new ReceiveTask());
    }

    public void stopReceiving() {
        stopReceiving = true;
        shutdown();
    }

    public interface DataReceivedCallback {
        void onDataReceived(byte[] data);
    }

    private class ReceiveTask implements Runnable {
        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)) {
                while (!stopReceiving) {
                    Socket clientSocket = serverSocket.accept();

                    BufferedInputStream inFromClient = new BufferedInputStream(clientSocket.getInputStream());
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                    int bytesRead;
                    while ((bytesRead = inFromClient.read(data)) != -1) {
                        byteArrayOutputStream.write(data, 0, bytesRead);
                    }
                    byte[] receivedData = byteArrayOutputStream.toByteArray();

                    synchronized (dataReceivedCallback) {
                        if (dataReceivedCallback != null) {
                            dataReceivedCallback.onDataReceived(receivedData);
                        }
                    }
                    byteArrayOutputStream.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}