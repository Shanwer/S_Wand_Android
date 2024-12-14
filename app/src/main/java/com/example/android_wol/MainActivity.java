package com.example.android_wol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private EditText ipEditText, macEditText, portEditText;//ip，mac，port
    private TextView statusIndicator;//状态
    private ProgressBar progressBar;
    private Button wakeUpButton;//唤醒按钮，查看状态
    private Handler handler;
    private CheckBox checkBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipEditText = findViewById(R.id.remote_device_ip);
        macEditText = findViewById(R.id.remote_device_mac);
        portEditText = findViewById(R.id.remote_device_port);
        statusIndicator = findViewById(R.id.status_indicator);
        wakeUpButton = findViewById(R.id.open_pc_button);
        checkBox = findViewById(R.id.connectivity_checkbox);
        progressBar = findViewById(R.id.status_circle);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        handler = new Handler(Looper.getMainLooper());

        wakeUpButton.setOnClickListener(v -> sendMagicPacket());
        loadSettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.popup_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置要保存的地址和端口");

        final EditText inputIp = new EditText(this);
        inputIp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        inputIp.setHint("IP或域名");
        inputIp.setText(ipEditText.getText());

        final EditText inputPort = new EditText(this);
        inputPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputPort.setHint("测试端口号");

        final EditText inputWakePort = new EditText(this);
        inputWakePort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputWakePort.setHint("唤醒端口号");

        final EditText inputMac = new EditText(this);
        inputMac.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        inputMac.setHint("MAC地址");

        SharedPreferences sharedPreferences = getSharedPreferences("WOL_SETTINGS", MODE_PRIVATE);
        inputPort.setText(sharedPreferences.getString("PORT_NUMBER", ""));
        inputWakePort.setText(sharedPreferences.getString("WAKE_PORT", ""));
        inputMac.setText(sharedPreferences.getString("MAC_ADDRESS", ""));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(inputIp);
        layout.addView(inputPort);
        layout.addView(inputWakePort);
        layout.addView(inputMac);
        builder.setView(layout);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String ip = inputIp.getText().toString();
            String port = inputPort.getText().toString();
            String wakePort = inputWakePort.getText().toString();
            String mac = inputMac.getText().toString();
            saveSettings(ip, port, wakePort, mac);
            ipEditText.setText(ip);
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void saveSettings(String ip, String port, String wakePort, String mac) {
        SharedPreferences sharedPreferences = getSharedPreferences("WOL_SETTINGS", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("IP_ADDRESS", ip);
        editor.putString("PORT_NUMBER", port);
        editor.putString("WAKE_PORT", wakePort);
        editor.putString("MAC_ADDRESS", mac);
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences("WOL_SETTINGS", MODE_PRIVATE);
        String ip = sharedPreferences.getString("IP_ADDRESS", "");
        String wakePort = sharedPreferences.getString("WAKE_PORT", "");
        String mac = sharedPreferences.getString("MAC_ADDRESS", "");
        ipEditText.setText(ip);
        portEditText.setText(wakePort);
        macEditText.setText(mac);
    }


    private void sendMagicPacket() {
            String address = ipEditText.getText().toString();
            String mac = macEditText.getText().toString();
            String port = portEditText.getText().toString();

            new Thread(() -> {
                try {
                    InetAddress ip = InetAddress.getByName(address);
                    byte[] MACTo2Bits = getMacBytes(mac);
                    byte[] magic = new byte[102];
                    for (int i = 0; i < 6; i++)
                        magic[i] = (byte) 0xFF;
                    //从第7个位置开始把MAC地址放入16次
                    for (int i = 0; i < 16; i++) {
                        for (int j = 0; j < MACTo2Bits.length; j++) {
                            magic[6 + MACTo2Bits.length * i + j] = MACTo2Bits[j];
                        }
                    }
                    DatagramPacket packet = new DatagramPacket(magic, magic.length, ip, Integer.parseInt(port));
                    //创建套接字
                    DatagramSocket socket = new DatagramSocket();
                    //发送数据
                    socket.send(packet);
                    socket.close();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Shanwer的魔法棒发光了", Toast.LENGTH_SHORT)
                                .show();
                        if(checkBox.isChecked()){
                            getDisplayStatus();
                            statusIndicator.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Shanwer的魔法棒失效了", Toast.LENGTH_SHORT)
                            .show());
                    e.printStackTrace();
                }
            }).start();
    }

    private byte[] getMacBytes(String mac) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = mac.split("[-:]");
        if (hex.length != 6) {
            throw new IllegalArgumentException("无效的MAC地址");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的MAC地址");
        }
        return bytes;
    }

    private void getDisplayStatus() {
        //等待30秒
        new Thread(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (TCPTestConnection()) {
                    handler.post(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setVisibility(View.VISIBLE);
                        statusIndicator.setText("设备已开机");
                    });
                    return; // Exit the loop and thread
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            handler.post(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(0);
                progressBar.setVisibility(View.VISIBLE);
                statusIndicator.setText("设备未开机");
            });
        }).start();
    }

    private boolean TCPTestConnection() {
        String address = ipEditText.getText().toString();
        SharedPreferences sharedPreferences = getSharedPreferences("WOL_SETTINGS", MODE_PRIVATE);
        int port = Integer.parseInt(sharedPreferences.getString("PORT_NUMBER", ""));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 5000); // 5000 毫秒超时时间
            return true;
        } catch (IOException e) {
            Log.e("WOL", "TCP connection failed to " + address + ":" + port, e);
            return false;
        }
    }
}


