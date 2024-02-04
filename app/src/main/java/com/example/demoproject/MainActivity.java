package com.example.demoproject;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

//import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    TextView textView; // 현재 상태 알 수 있는 텍스트 뷰
    // Device 선택 스위치
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switch1;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switch2;

    private int port_index = 0; // 1 = Device1, 2 = Device2, 0 = 선택x
    private CameraPreview cameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraPreview = new CameraPreview();

        // 검색, 전송 버튼 설정
        Button scanButton = findViewById(R.id.scan_button);
        Button serverButton = findViewById(R.id.server_button);

        textView = findViewById(R.id.state_textview);
        switch1 = findViewById(R.id.device_switch1);
        switch2 = findViewById(R.id.device_switch2);

        view_connect_device();

        // 검색 버튼 눌렀을 때
        scanButton.setOnClickListener(view -> view_connect_device());

        // 수신 버튼 눌렀을 때
        serverButton.setOnClickListener(view -> {
            switch (port_index) {
                case 0:
                    Toast.makeText(MainActivity.this, "Choose at least 1 option.", Toast.LENGTH_SHORT).show();
                    break;

                // Device1
                case 1:
                    startCameraPreview(0);
                    break;

                // Device2
                case 2:
                    startCameraPreview(1);
                    break;
            }
        });

        // Devie switch1 눌렀을 때
        switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                port_index = 1;
            } else {
                port_index = 0;
            }
        });

        // Devie switch2 눌렀을 때
        switch2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                port_index = 2;
            } else {
                port_index = 0;
            }
        });

    }

    @SuppressLint("SetTextI18n")
    private void view_connect_device() {
        String ipList = getConnectedDevices();
        if (ipList.isEmpty()) {
            textView.setText("No devices connected.");
        } else {
            String master_IP = "192.168.43.1";
            if (ipList.equals(master_IP)) {
                textView.setText(ipList); // master의 ip
            } else {
                textView.setText("You should connect Master Device.");
            }

        }
    }


    private String getConnectedDevices() {
        return "192.168.43.1";
    }

//    private InetAddress intToInetAddress(int hostAddress) {
//        byte[] addressBytes = {(byte) (0xff & hostAddress), (byte) (0xff & (hostAddress >> 8)), (byte) (0xff & (hostAddress >> 16)), (byte) (0xff & (hostAddress >> 24))};
//
//        try {
//            return InetAddress.getByAddress(addressBytes);
//        } catch (Exception e) {
//            throw new AssertionError();
//        }
//    }

    // CameraPreview 시작
    private void startCameraPreview(int port_index) {

        if (cameraPreview != null) {
            cameraPreview.finish();  // Finish the current instance
            cameraPreview = null;    // Set the reference to null
        }

        Intent intent = new Intent(MainActivity.this, CameraPreview.class);
        intent.putExtra("port_index_key", port_index);
        startActivity(intent);
    }

}