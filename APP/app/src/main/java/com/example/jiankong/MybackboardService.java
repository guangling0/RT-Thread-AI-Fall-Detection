package com.example.jiankong;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MybackboardService extends Service {
    private static final String TAG = "MybackboardService";
    private static final String CHANNEL_ID = "data_channel";
    private static final long FETCH_INTERVAL = 1000;
    private static final int NOTIFICATION_ID = 1;
    private ScheduledExecutorService scheduler;
    private ServiceTask serviceTask;
    private boolean isRunning = false;
    private NotificationManager notificationManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        scheduler = Executors.newSingleThreadScheduledExecutor();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动命令");

        if (checkForegroundServicePermission()) {
            Notification notification = createForegroundNotification();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            startPeriodicFetch();
        } else {
            Log.e(TAG, "缺少前台服务权限，无法启动");
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务销毁");
        stopPeriodicFetch();
        shutdownScheduler();
        stopForeground(true);
    }

    // 正确检查前台服务权限
    private boolean checkForegroundServicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ContextCompat.checkSelfPermission(
                    this, Manifest.permission.FOREGROUND_SERVICE) ==
                    PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void startPeriodicFetch() {
        if (!isRunning && scheduler != null) {
            serviceTask = new ServiceTask(this);
            scheduler.scheduleAtFixedRate(serviceTask, 0, FETCH_INTERVAL, TimeUnit.MILLISECONDS);
            isRunning = true;
        }
    }

    private void stopPeriodicFetch() {
        if (serviceTask != null) {
            serviceTask.cancel();
            serviceTask = null;
        }
        isRunning = false;
    }

    private void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    // 改进通知渠道创建，添加描述和其他属性
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "摔倒检测",
                    NotificationManager.IMPORTANCE_HIGH // 改为 HIGH 以确保锁屏显示
            );
            channel.setDescription("摔倒检测服务的通知");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 改进前台服务通知创建
    private Notification createForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, pendingIntentFlags
        );

        // 创建通知构建器时指定小图标资源
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 使用专门的通知图标
                .setContentTitle("监控服务运行中")
                .setContentText("正在实时监测")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 使用默认优先级
                .setContentIntent(pendingIntent)
                .setOngoing(true); // 设置为正在进行的通知，不容易被用户清除

        // 确保在Android 8.0及以上版本设置渠道ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    // 改进普通通知创建
    private void showNotification(String title, String content) {
        // 1. 先检查通知权限（适用于Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "通知权限未授予，无法显示通知");
                return;
            }
        }

        // 2. 检查通知渠道是否存在（适用于Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                Log.e(TAG, "通知渠道不存在，重新创建");
                createNotificationChannel(); // 重新创建渠道
            }
        }

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "com.example.app:wake_lock_bright" // 使用应用包名作为前缀
            );
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 1, intent, pendingIntentFlags
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);

            // 3. 确保设置渠道ID（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID);
            }

            Notification notification = builder.build();
            // 4. 使用不同的通知ID（避免冲突）
            notificationManager.notify(3, notification); // 将ID从2改为3
            wl.acquire(10 * 1000); // 点亮屏幕10秒
            wl.release(); // 释放WakeLock

            Log.d(TAG, "通知已发送，标题：" + title + "，内容：" + content);
        } catch (Exception e) {
            Log.e(TAG, "创建通知时发生异常", e);
        }
    }

    private static class ServiceTask implements Runnable {
        private final WeakReference<MybackboardService> serviceRef;
        private volatile boolean isCancelled = false;

        public ServiceTask(MybackboardService service) {
            this.serviceRef = new WeakReference<>(service);
        }

        @Override
        public void run() {
            if (isCancelled) {
                return;
            }

            MybackboardService service = serviceRef.get();
            if (service == null) {
                cancel();
                return;
            }

            http_get.get_http_new(service, new http_get.HttpCallback() {
                @Override
                public void onSuccess(String jsonData) {

                    SharedPreferences sharedPreferences = service.getSharedPreferences("last", Context.MODE_PRIVATE);
                    int lastFallValue = sharedPreferences.getInt("last",0);
                    int currentFallValue ;
                    try {
                        currentFallValue = http_get.json_get(jsonData,"fall").getInt("value");
                    } catch (JSONException e) {
                        Log.e(TAG, "解析JSON数据失败", e);
                        return;
                    }
                    if (lastFallValue == 0 && currentFallValue == 1) {
                        service.showNotification("检测到有人摔倒", "请确认情况");
                    }
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt("last", currentFallValue);
                    editor.apply();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "HTTP请求失败: " + errorMessage);
                }
            });
        }

        public void cancel() {
            isCancelled = true;
        }
    }
}