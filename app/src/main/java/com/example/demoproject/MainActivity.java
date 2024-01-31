package com.example.demoproject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.net.InetAddress;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView textView; // 현재 상태 알 수 있는 텍스트 뷰
    // Device 선택 스위치
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switch1;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switch2;

    private final String master_IP = "192.168.43.1";
    private int port_index = 0; // 1 = Device1, 2 = Device2, 0 = 선택x
    private CameraPreview cameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraPreview = new CameraPreview();
        setPermission();

        // 검색, 전송 버튼 설정
        Button scanButton = findViewById(R.id.scan_button);
        Button serverButton = findViewById(R.id.server_button);

        textView = findViewById(R.id.state_textview);
        switch1 = findViewById(R.id.device_switch1);
        switch2 = findViewById(R.id.device_switch2);

        view_connect_device();

        // 검색 버튼 눌렀을 때
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view_connect_device();
            }
        });

        // 수신 버튼 눌렀을 때
        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (port_index){
                    case 0:
                        System.out.println("Devide를 선택해 주세요");
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
            }
        });

        // Devie switch1 눌렀을 때
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 스위치가 켜진 경우
                    port_index = 1;
                } else {
                    // 스위치가 꺼진 경우
                    port_index = 0;
                }
            }
        });

        // Devie switch2 눌렀을 때
        switch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 스위치가 켜진 경우
                    port_index = 2;
                } else {
                    // 스위치가 꺼진 경우
                    port_index = 0;
                }
            }
        });

    }

    @SuppressLint("SetTextI18n")
    private void view_connect_device() {
        String ipList = getConnectedDevices();
        if (ipList.isEmpty()) {
            textView.setText("No devices connected.");
        } else {
            if (ipList.equals(master_IP))
            {
                textView.setText(ipList); // master의 ip
            } else {
                textView.setText("You should connect Master Device.");
            }

        }
    }

    // TedPermission 권한 체크
    private void setPermission() {
        PermissionListener permission = new PermissionListener() {
            @Override
            public void onPermissionGranted() { // 설정해 놓은 위험권한들이 허용 되었을 경우
                Toast.makeText(MainActivity.this, "권한이 허용 되었습니다.",Toast.LENGTH_SHORT).show();
                Log.e("권한", "권한 허가 상태");
            }
            @Override
            public void onPermissionDenied(List<String> deniedPermissions) { // 설정해 놓은 위험권한들이 허용되지 않았을 경우우
                Toast.makeText(MainActivity.this, "권한이 거부 되었습니다.",Toast.LENGTH_SHORT).show();
                Log.e("권한", "권한 거부 상태");
            }
        };
        TedPermission.with(MainActivity.this)
                .setPermissionListener(permission)
                .setRationaleMessage("위치정보 권한이 필요합니다.")
                .setDeniedMessage("권한을 거부하시면 해당 기능을 사용할 수 없습니다. 설정에서 권한을 허용해주세요.")
                // 어느 권한을 허용할 것인지 설정
                .setPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .check();
    }

    private String getConnectedDevices() {
        return "192.168.43.1";
    }

    private InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = {(byte) (0xff & hostAddress), (byte) (0xff & (hostAddress >> 8)), (byte) (0xff & (hostAddress >> 16)), (byte) (0xff & (hostAddress >> 24))};

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

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