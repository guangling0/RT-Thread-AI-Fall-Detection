package com.example.jiankong;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class client extends AppCompatActivity {

    private String product_ID;
    private String device_name;

    private String version = "2022-05-01";
    private String userid = "userid/451213";
    private String expirationTime = "1908018128";
    private String signatureMethod = token.SignatureMethod.SHA1.name().toLowerCase();
    private String accessKey = "yA8lGFR9PxmfBY0VVNkTTD4ArKC1Jm6qKgKRz/MgWviKDhKlCEuW0xBO3LBuJDZ/";
    private String token_use;

    private Button button;
    private EditText editText1, editText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        Toolbar mToolbarTb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbarTb);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // 初始化token
        try {
            token_use = token.assembleToken(version, userid, expirationTime, signatureMethod, accessKey);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            Toast.makeText(this, "Token生成失败", Toast.LENGTH_SHORT).show();
        }

        // 初始化UI组件
        editText1 = findViewById(R.id.edit1);
        editText2 = findViewById(R.id.edit2);
        button = findViewById(R.id.button);

        // 获取保存的偏好设置
        SharedPreferences sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        String savedProductId = sharedPreferences.getString("product_ID", "");
        String savedDeviceName = sharedPreferences.getString("device_name", "");

        // 设置输入框提示
        editText1.setHint(savedProductId);
        editText2.setHint(savedDeviceName);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 获取用户输入
                product_ID = editText1.getText().toString().trim();
                device_name = editText2.getText().toString().trim();

                // 检查输入是否为空
                if (product_ID.isEmpty() || device_name.isEmpty()) {
                    Toast.makeText(client.this, "请输入产品ID和设备名称", Toast.LENGTH_SHORT).show();
                }

                // 保存用户输入
                boolean shouldSave = false;
                if (!savedProductId.equals(product_ID)) {
                    shouldSave = true;
                }
                if (!savedDeviceName.equals(device_name)) {
                    shouldSave = true;
                }

                if (shouldSave) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("product_ID", product_ID);
                    editor.putString("device_name", device_name);
                    editor.putString("token", token_use);
                    editor.apply();
                    Toast.makeText(client.this, "保存成功", Toast.LENGTH_SHORT).show();
                }

                http_get.get_http_new(client.this, new http_get.HttpCallback() {
                    @Override
                    public void onSuccess(String jsonData) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONObject jsonObject = new JSONObject(jsonData);
                                    int code = jsonObject.getInt("code");
                                    if (code == 0) {
                                        Toast.makeText(client.this, "连接成功", Toast.LENGTH_SHORT).show();
                                    } else {
                                        String errorMsg = jsonObject.optString("error", "未知错误");
                                        Toast.makeText(client.this, "连接失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    Toast.makeText(client.this, "解析响应失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(client.this, "请求失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}