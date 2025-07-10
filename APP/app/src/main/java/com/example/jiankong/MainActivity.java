package com.example.jiankong;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button btn1,btn2;
    private String image;
    private TextView textView;
    private Handler mainHandler;
    private PeriodicFetcher periodicFetcher;
    private ExecutorService ioExecutor;
    private boolean isDestroyed = false;
    private static final long FETCH_INTERVAL = 500;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ioExecutor = Executors.newSingleThreadExecutor();
        btn1 = findViewById(R.id.button1);
        btn2 = findViewById(R.id.button2);
        textView = findViewById(R.id.text);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, history_list_view.class);
                startActivity(intent);
            }
        });
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, client.class);
                startActivity(intent);
            }
        });
        Intent serviceIntent = new Intent(this, MybackboardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        initDataDisplay();
        mainHandler = new Handler(Looper.getMainLooper());
        periodicFetcher = new PeriodicFetcher(this);


    }
    private void initDataDisplay() {
        // 添加空指针检查
        if (ioExecutor != null) {
            ioExecutor.execute(() -> {
                try {
                    SharedPreferences sharedPreferences = getSharedPreferences("json_data", Context.MODE_PRIVATE);
                    String json = sharedPreferences.getString("json", "");
                    if (!json.isEmpty()) {
                        int fallValue = http_get.json_get(json, "fall").getInt("value");
                        if(fallValue ==0){
                            updateTextViewOnMainThread("未检测到摔倒情况");
                        }
                        else{
                            Date date = new Date();
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String current_time = formatter.format(date);
                            SharedPreferences sharedPreferences1 = getSharedPreferences("fall_history",Context.MODE_PRIVATE);
                            int total = sharedPreferences1.getInt("total",0);
                            String last_time = sharedPreferences1.getString("time_fall"+total,"");
                            String time_sustain = http_get.getFormattedTimeDifference(last_time,current_time);
                            updateTextViewOnMainThread("已检测到摔倒情况："+ time_sustain);
                        }
                    }
                } catch (JSONException e) {

                    updateTextViewOnMainThread("数据加载失败");
                }
            });
        } else {

            updateTextViewOnMainThread("数据加载组件未初始化");
        }
    }

    private void updateTextViewOnMainThread(final String text) {
        mainHandler.post(() -> {
            if (!isDestroyed) {
                textView.setText(text);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isDestroyed = false;
        startPeriodicFetch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isDestroyed = true;
        stopPeriodicFetch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        stopPeriodicFetch();
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            ioExecutor = null;
        }
    }

    private void startPeriodicFetch() {
        mainHandler.postDelayed(periodicFetcher, FETCH_INTERVAL);
    }

    private void stopPeriodicFetch() {
        mainHandler.removeCallbacks(periodicFetcher);
    }


    private static class PeriodicFetcher implements Runnable {
        private final WeakReference<MainActivity> activityRef;

        public PeriodicFetcher(MainActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            MainActivity activity = activityRef.get();
            if (activity == null || activity.isDestroyed) {
                return;
            }

            // 在IO线程中执行网络请求前检查线程池状态
            if (activity.ioExecutor != null && !activity.ioExecutor.isShutdown()) {
                activity.ioExecutor.execute(() -> {
                    http_get.get_http_new(activity, new http_get.HttpCallback() {
                        @Override
                        public void onSuccess(String jsonData) {
                            activity.handleSuccessOnMainThread(jsonData);
                            activity.scheduleNextFetch();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            activity.handleFailureOnMainThread(errorMessage);
                            activity.scheduleNextFetch();
                        }
                    });
                });
            }
        }
    }

    private void handleSuccessOnMainThread(final String jsonData) {
        mainHandler.post(() -> {
            if (!isDestroyed) {
                try {
                    int fallValue = http_get.json_get(jsonData, "fall").getInt("value");
                    if(fallValue == 0){
                        updateTextViewOnMainThread("未检测到摔倒情况");
                    }else{
                        Date date = new Date();
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String current_time = formatter.format(date);
                        SharedPreferences sharedPreferences1 = getSharedPreferences("fall_history",Context.MODE_PRIVATE);
                        int total = sharedPreferences1.getInt("total",0);
                        String last_time = sharedPreferences1.getString("time_fall"+total,"");
                        String time_sustain = http_get.getFormattedTimeDifference(last_time,current_time);
                        updateTextViewOnMainThread("已检测到摔倒情况："+ time_sustain);
                    }
                } catch (JSONException e) {
                    textView.setText("数据解析失败");
                }
            }
        });
    }

    private void handleFailureOnMainThread(final String errorMessage) {
        mainHandler.post(() -> {
            if (!isDestroyed) {
                textView.setText("请求失败");
                Toast.makeText(this, "请求失败: " + errorMessage, Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void scheduleNextFetch() {
        if (!isDestroyed) {
            mainHandler.postDelayed(periodicFetcher, FETCH_INTERVAL);
        }
    }


}