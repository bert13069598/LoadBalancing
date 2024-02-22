package com.example.demoproject_master;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import com.example.demoproject.Det.Bbox;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// DET 소켓 통신
// Device1 socket (DET)
public class ReceiveDET implements Runnable {

    private final Handler uiHandler;
    private final TextView device1_state;
    private final int serverPort;
    public static final int MESSAGE_DET_DATA = 1;

    ExecutorService executorService = Executors.newFixedThreadPool(3);
    WeakReference<ExecutorService> executorServiceRef = new WeakReference<>(executorService);

    public ReceiveDET(int serverPort, Handler uiHandler, TextView device1_state) {
        this.serverPort = serverPort;
        this.uiHandler = uiHandler;
        this.device1_state = device1_state;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void run() {
        WeakReference<Handler> uiHandlerRef = new WeakReference<>(uiHandler);
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while (!Thread.interrupted()) {
                Socket clientSocket = serverSocket.accept();

                executorServiceRef.get().submit(() -> {
                    try (BufferedInputStream inFromClient = new BufferedInputStream(clientSocket.getInputStream());
                         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                        byte[] data = new byte[1];
                        int bytesRead;

                        while ((bytesRead = inFromClient.read(data)) != -1) {
                            byteArrayOutputStream.write(data, 0, bytesRead);
                        }
                        byte[] receivedData = byteArrayOutputStream.toByteArray();

                        // Parse the protobuf message
                        Bbox bbox = Bbox.parseFrom(receivedData);

                        // Get the image data from the protobuf message
                        String receivedText = bbox.getBbox();
                        boolean run = bbox.getRun();

                        uiHandlerRef.get().post(() -> {
                            if (run)
                                device1_state.setText("on");
                            else
                                device1_state.setText("off");
                        });

                        Message message = uiHandler.obtainMessage(MESSAGE_DET_DATA, receivedText);
                        uiHandlerRef.get().sendMessage(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
        }
    }
}