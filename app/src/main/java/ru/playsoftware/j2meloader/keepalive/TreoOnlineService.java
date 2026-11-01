package ru.playsoftware.j2meloader.keepalive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.preference.PreferenceManager;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

public final class TreoOnlineService extends Service {
    public static final String ACTION_STOP = "ru.playsoftware.j2meloader.treo.STOP";
    public static final String ACTION_STOP_BY_USER = "ru.playsoftware.j2meloader.treo.STOP_BY_USER";
    public static final String ACTION_CYCLE_MODE = "ru.playsoftware.j2meloader.treo.CYCLE_MODE";
    public static final String ACTION_OPEN_GAME = "ru.playsoftware.j2meloader.treo.OPEN_GAME";

    public static final int MODE_LIGHT = 1;
    public static final int MODE_WIFI = 2;
    public static final int MODE_STRONG = 3;

    private static final String TAG = "NSTTREO";
    private static final int NOTIFICATION_ID = 0xA742;
    private static final String CHANNEL_NORMAL = "nst_treo";
    private static final String CHANNEL_MIN = "nst_treo_min";

    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    private int mode = MODE_LIGHT;
    private long startedAtElapsed;
    private boolean foregroundStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        if (startedAtElapsed == 0L) startedAtElapsed = SystemClock.elapsedRealtime();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        ensureForegroundStarted();
        if (!foregroundStarted) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopTreo(false);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_BY_USER.equals(action)) {
            TreoController.setStickyEnabled(this, false);
            stopTreo(true);
            return START_NOT_STICKY;
        }

        if (ACTION_CYCLE_MODE.equals(action)) {
            mode = mode % MODE_STRONG + 1;
            saveMode(mode);
        } else {
            mode = readMode();
        }

        releaseLocks();
        acquireLocks();
        TreoController.onServiceStarted(mode);
        ru.playsoftware.j2meloader.bubble.BubbleController.updateMode(this, getModeLabel());
        updateForegroundNotification();
        return START_STICKY;
    }

    private int readMode() {
        String stored = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("pref_treo_mode", "LIGHT");
        if ("WIFI".equals(stored)) return MODE_WIFI;
        if ("STRONG".equals(stored)) return MODE_STRONG;
        return MODE_LIGHT;
    }

    private void saveMode(int value) {
        String stored = value == MODE_WIFI ? "WIFI" : value == MODE_STRONG ? "STRONG" : "LIGHT";
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putString("pref_treo_mode", stored).apply();
    }

    private void acquireLocks() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                cpuLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NST:TreoCPU");
                cpuLock.setReferenceCounted(false);
                cpuLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to acquire CPU wake lock", e);
        }

        if (mode == MODE_LIGHT) return;

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        ConnectivityManager connectivity =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (wifi == null || connectivity == null) return;

        try {
            NetworkInfo active = connectivity.getActiveNetworkInfo();
            if (active == null || !active.isConnected()
                    || active.getType() != ConnectivityManager.TYPE_WIFI) {
                return;
            }

            int lockMode = mode == MODE_STRONG
                    ? WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    : WifiManager.WIFI_MODE_FULL;
            try {
                wifiLock = wifi.createWifiLock(lockMode, "NST:TreoWifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            } catch (Exception e) {
                Log.w(TAG, "Unable to acquire Wi-Fi lock", e);
            }

            try {
                multicastLock = wifi.createMulticastLock("NST:TreoMulticast");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            } catch (Exception e) {
                Log.w(TAG, "Unable to acquire multicast lock", e);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to inspect active Wi-Fi network", e);
        }
    }

    private void releaseLocks() {
        try {
            if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        } catch (Exception e) {
            Log.w(TAG, "Unable to release CPU wake lock", e);
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception e) {
            Log.w(TAG, "Unable to release Wi-Fi lock", e);
        }
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception e) {
            Log.w(TAG, "Unable to release multicast lock", e);
        }
        cpuLock = null;
        wifiLock = null;
        multicastLock = null;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        if (manager.getNotificationChannel(CHANNEL_NORMAL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_NORMAL,
                    getString(R.string.treo_channel_name), NotificationManager.IMPORTANCE_LOW);
            configureChannel(channel);
            manager.createNotificationChannel(channel);
        }
        if (manager.getNotificationChannel(CHANNEL_MIN) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_MIN,
                    getString(R.string.treo_channel_name), NotificationManager.IMPORTANCE_MIN);
            configureChannel(channel);
            manager.createNotificationChannel(channel);
        }
    }

    private void configureChannel(NotificationChannel channel) {
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setDescription(getString(R.string.treo_channel_desc));
    }

    private String getModeLabel() {
        if (mode == MODE_WIFI) return getString(R.string.treo_mode_wifi);
        if (mode == MODE_STRONG) return getString(R.string.treo_mode_strong);
        return getString(R.string.treo_mode_light);
    }

    private String getNotificationChannelId() {
        String hideStrategy = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("pref_hide_strategy", "SOFT");
        return ("STEALTH".equals(hideStrategy) || "NINJA".equals(hideStrategy))
                ? CHANNEL_MIN : CHANNEL_NORMAL;
    }

    private PendingIntent servicePendingIntent(String action) {
        Intent intent = new Intent(this, TreoOnlineService.class).setAction(action);
        int requestCode = action.hashCode();
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, requestCode, intent, pendingFlags);
    }

    private Notification buildNotification() {
        long minutes = Math.max(0L, (SystemClock.elapsedRealtime() - startedAtElapsed) / 60_000L);
        Intent openIntent = new Intent(this, MicroActivity.class)
                .setAction(ACTION_OPEN_GAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int activityFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, activityFlags);

        return new NotificationCompat.Builder(this, getNotificationChannelId())
                .setSmallIcon(R.drawable.ic_treo_dot)
                .setContentTitle(getString(R.string.treo_running))
                .setContentText(getString(R.string.treo_noti_content_fmt, getModeLabel(), minutes))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.treo_action_stop),
                        servicePendingIntent(ACTION_STOP_BY_USER))
                .addAction(android.R.drawable.ic_menu_rotate,
                        getString(R.string.treo_action_mode),
                        servicePendingIntent(ACTION_CYCLE_MODE))
                .build();
    }

    private void ensureForegroundStarted() {
        if (foregroundStarted) return;
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable firstFailure) {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Throwable secondFailure) {
                Log.e(TAG, "Unable to enter foreground", secondFailure);
                TreoController.onServiceFailed();
                stopSelf();
                return;
            }
        }
        foregroundStarted = true;
    }

    private void updateForegroundNotification() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
        } catch (Throwable e) {
            Log.w(TAG, "Unable to refresh foreground notification", e);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void stopTreo(boolean userRequested) {
        ru.playsoftware.j2meloader.bubble.BubbleController.hide(this);
        if (userRequested) HideStrategy.reset(this);
        releaseLocks();
        TreoController.onServiceStopped();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        foregroundStarted = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        TreoController.onServiceStopped();
        super.onDestroy();
    }
}
