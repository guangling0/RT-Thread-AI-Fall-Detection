package com.example.jiankong;

import static android.content.Context.MODE_PRIVATE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class http_get {
    private static final String TAG = "http_get";
    private static final String CHANNEL_ID = "fall_detection_channel";
    private static final OkHttpClient client_use = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper()); // 主线程Handler

    public static void get_http_new(Context context, HttpCallback callback) {
        if (context == null) {
            postToMainThread(callback, false, "Context不能为空");
            return;
        }

        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("user", MODE_PRIVATE);
            String product_ID = sharedPreferences.getString("product_ID", "");
            String device_name = sharedPreferences.getString("device_name", "");
            String token = sharedPreferences.getString("token", "");


            if (product_ID.isEmpty() || device_name.isEmpty() ) {
                postToMainThread(callback, false, "认证信息不完整"+product_ID+device_name+token);
                return;
            }

            String url = "https://iot-api.heclouds.com/datapoint/current-datapoints?product_id=" +
                    product_ID + "&device_name=" + device_name;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("authorization", token)
                    .build();

            client_use.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "HTTP请求失败: " + e.getMessage());
                    postToMainThread(callback, false, "网络错误: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (response.isSuccessful()) {
                            String res = response.body().string();
                            if (res != null) {
                                processResponse(context, res, callback);
                            } else {
                                postToMainThread(callback, false, "响应数据为空");
                            }
                        } else {
                            postToMainThread(callback, false, "HTTP错误: " + response.code());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理响应时出错: " + e.getMessage());
                        postToMainThread(callback, false, "处理响应时出错: " + e.getMessage());
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "构建请求时出错: " + e.getMessage());
            postToMainThread(callback, false, "构建请求时出错: " + e.getMessage());
        }
    }

    private static void processResponse(Context context, String res, HttpCallback callback) {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("json_data", Context.MODE_PRIVATE);
            String lastJson = sharedPreferences.getString("json", "");

            int lastFallValue = getFallValue(lastJson);
            int currentFallValue = getFallValue(res);

            if (lastFallValue == 0 && currentFallValue == 1) {
                saveFallHistory(context, res,true);
            }
            if (lastFallValue == 1 && currentFallValue == 0) {
                saveFallHistory(context,res,false);
            }

            saveJsonData(context, res);
            postToMainThread(callback, true, res);
        } catch (Exception e) {
            Log.e(TAG, "处理响应数据时出错: " + e.getMessage());
            postToMainThread(callback, false, "处理数据时出错: " + e.getMessage());
        }
    }

    private static int getFallValue(String jsonData) {
        try {
            JSONObject dataStream = json_get(jsonData, "fall");
            if (dataStream != null) {
                return dataStream.getInt("value");
            }
        } catch (JSONException e) {
            Log.e(TAG, "获取摔倒值时出错: " + e.getMessage());
        }
        return 0;
    }


    private static void saveFallHistory(Context context, String jsonData,boolean i) {
        try {
            JSONObject dataStream = json_get(jsonData, "fall");
            if (dataStream != null) {
                String time = dataStream.getString("at");
                SharedPreferences sharedPreferences = context.getSharedPreferences("fall_history", MODE_PRIVATE);
                int total = sharedPreferences.getInt("total", 0);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                if(i){
                    editor.putString("time_fall" + (total + 1), time);
                    editor.putInt("total", total + 1);
                    editor.apply();
                }else{
                    String last_time = sharedPreferences.getString("time_fall"+total, "");
                    String sustained_time = getFormattedTimeDifference(last_time,time);
                    editor.putString("time_sustained" + total , sustained_time);
                    editor.apply();
                }

            }
        } catch (JSONException e) {
            Log.e(TAG, "保存摔倒历史时出错: " + e.getMessage());
        }
    }

    private static void saveJsonData(Context context, String jsonData) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("json_data", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("json", jsonData);
        editor.apply();
    }

    private static void postToMainThread(final HttpCallback callback, final boolean success, final String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> {
            if (success) {
                callback.onSuccess(message);
            } else {
                callback.onFailure(message);
            }
        });
    }

    public interface HttpCallback {
        void onSuccess(String jsonData);
        void onFailure(String errorMessage);
    }

    public static JSONObject json_get(String json_data, String key) {
        if (json_data == null || json_data.isEmpty()) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(json_data);
            JSONObject data = jsonObject.getJSONObject("data");
            JSONArray devices = data.getJSONArray("devices");
            JSONObject device = devices.getJSONObject(0);
            JSONArray dataStream = device.getJSONArray("datastreams");

            for (int i = 0; i < dataStream.length(); i++) {
                JSONObject data1 = dataStream.getJSONObject(i);
                if (data1.getString("id").equals(key)) {
                    return data1;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析JSON时出错: " + e.getMessage());
        }
        return null;
    }
    public static String getFormattedTimeDifference(String timeStr1, String timeStr2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date date1 = sdf.parse(timeStr1);
            Date date2 = sdf.parse(timeStr2);
            long diffMillis = date2.getTime() - date1.getTime();

            // 转换为时分秒
            long seconds = diffMillis / 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            seconds = seconds % 60;

            return String.format("%d小时%d分钟%d秒", hours, minutes, seconds);
        } catch (ParseException e) {
            e.printStackTrace();
            return "时间格式错误，请使用yyyy-MM-dd HH:mm:ss格式";
        }
    }
}