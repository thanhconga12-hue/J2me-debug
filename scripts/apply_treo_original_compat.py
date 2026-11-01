#!/usr/bin/env python3
"""Port the relevant NST-original Treo service behavior into the FPS rebuild."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_treo_original_compat.py <project-root>")

root = Path(sys.argv[1]).resolve()
manifest = root / "app/src/main/AndroidManifest.xml"
source = root / "app/src/main/java/javax/microedition/shell/TreoOnlineService.java"

# The original gates Wi-Fi locks on an active Wi-Fi network and a selected
# mode. ACCESS_NETWORK_STATE is required for that same network check.
text = manifest.read_text(encoding="utf-8")
if "android.permission.ACCESS_NETWORK_STATE" not in text:
    anchor = '    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />'
    if text.count(anchor) != 1:
        raise SystemExit("Could not locate ACCESS_WIFI_STATE manifest anchor")
    text = text.replace(anchor, anchor + '\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />', 1)
manifest.write_text(text, encoding="utf-8")
ET.parse(manifest)

java = r'''package javax.microedition.shell;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
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
import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.R;

public final class TreoOnlineService extends Service {
    private static final String TAG = "TreoOnlineService";
    private static final String CHANNEL = "nst_treo";
    private static final int NOTIFICATION_ID = 42818;

    private static final String ACTION_START = "javax.microedition.shell.action.TREO_START";
    private static final String ACTION_STOP = "ru.playsoftware.j2meloader.treo.STOP";
    private static final String ACTION_STOP_BY_USER = "ru.playsoftware.j2meloader.treo.STOP_BY_USER";
    private static final String ACTION_CYCLE_MODE = "ru.playsoftware.j2meloader.treo.CYCLE_MODE";

    private static final String PREF_STICKY = "pref_treo_sticky_on";
    private static final String PREF_MODE = "pref_treo_mode";

    // Same three power/network modes as NST original: LIGHT, WIFI, STRONG.
    private static final int MODE_LIGHT = 1;
    private static final int MODE_WIFI = 2;
    private static final int MODE_STRONG = 3;

    private static volatile boolean requested;
    private static volatile boolean active;
    private static volatile String failureReason = "";
    private static volatile int selectedMode = MODE_LIGHT;

    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    private WifiManager.MulticastLock multicast;
    private long startedAt;

    public static boolean isEnabled() { return requested; }
    public static boolean isActive() { return active; }
    public static String getFailureReason() { return failureReason; }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static int readMode(Context context) {
        String value = prefs(context).getString(PREF_MODE, "LIGHT");
        if ("WIFI".equals(value)) return MODE_WIFI;
        if ("STRONG".equals(value)) return MODE_STRONG;
        return MODE_LIGHT;
    }

    private static String modeName(int mode) {
        if (mode == MODE_WIFI) return "WIFI";
        if (mode == MODE_STRONG) return "STRONG";
        return "LIGHT";
    }

    public static boolean enable(Context context) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putBoolean(PREF_STICKY, true).apply();
        requested = true;
        active = false;
        failureReason = "";
        Intent intent = new Intent(app, TreoOnlineService.class).setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
            else app.startService(intent);
            return true;
        } catch (RuntimeException e) {
            requested = false;
            active = false;
            failureReason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            Log.e(TAG, "Unable to start NST-compatible Treo service: " + failureReason, e);
            return false;
        }
    }

    public static void disable(Context context) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putBoolean(PREF_STICKY, false).apply();
        requested = false;
        active = false;
        Intent stop = new Intent(app, TreoOnlineService.class).setAction(ACTION_STOP);
        try {
            app.startService(stop);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to send Treo stop action; falling back to stopService", e);
            try { app.stopService(new Intent(app, TreoOnlineService.class)); }
            catch (RuntimeException ignored) { }
        }
        MidletThread.pauseIfActivityStopped();
    }

    @Override public void onCreate() {
        super.onCreate();
        startedAt = SystemClock.elapsedRealtime();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, getString(R.string.treo_channel_name),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setDescription(getString(R.string.treo_notification_text));
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent serviceAction(String action) {
        Intent intent = new Intent(this, TreoOnlineService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(this, action.hashCode(), intent, flags);
        }
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private Notification makeNotification() {
        long minutes = Math.max(0L, SystemClock.elapsedRealtime() - startedAt) / 60000L;
        String summary = "Chế độ " + modeName(selectedMode) + " • " + minutes + " phút";
        Intent open = new Intent(this, MicroActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPending = PendingIntent.getActivity(this, NOTIFICATION_ID, open, flags);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.treo_notification_title))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        getString(R.string.treo_notification_text) + " • " + summary))
                .setContentIntent(openPending)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Dừng treo", serviceAction(ACTION_STOP_BY_USER))
                .addAction(android.R.drawable.ic_menu_rotate,
                        "Đổi chế độ", serviceAction(ACTION_CYCLE_MODE))
                .build();
    }

    private void enterForeground() {
        Notification notification = makeNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action) || ACTION_STOP_BY_USER.equals(action)) {
            prefs(this).edit().putBoolean(PREF_STICKY, false).apply();
            requested = false;
            active = false;
            releaseLocks();
            stopForeground(true);
            stopSelf(startId);
            MidletThread.pauseIfActivityStopped();
            return START_NOT_STICKY;
        }

        // A null Intent is how Android restarts a START_STICKY service.
        // Honour only a previously user-enabled session; do not invent a new one.
        if (intent == null && !prefs(this).getBoolean(PREF_STICKY, false)) {
            requested = false;
            active = false;
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (ACTION_CYCLE_MODE.equals(action)) {
            selectedMode = (readMode(this) % MODE_STRONG) + 1;
            prefs(this).edit().putString(PREF_MODE, modeName(selectedMode)).apply();
        } else {
            selectedMode = readMode(this);
        }

        requested = true;
        failureReason = "";
        try {
            // Enter the foreground before acquiring locks, matching the original
            // service's order and Android 14+ special-use declaration.
            enterForeground();
            acquireLocksForSelectedMode();
            active = true;
            return START_STICKY;
        } catch (RuntimeException e) {
            active = false;
            requested = false;
            releaseLocks();
            failureReason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            Log.e(TAG, "Treo foreground start failed: " + failureReason, e);
            stopForeground(true);
            stopSelf(startId);
            MidletThread.pauseIfActivityStopped();
            return START_NOT_STICKY;
        }
    }

    private void acquireLocksForSelectedMode() {
        releaseLocks();

        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getPackageName() + ":TreoCPU");
            cpu.setReferenceCounted(false);
            cpu.acquire();
        }

        // The original NST only keeps Wi-Fi awake in WIFI/STRONG modes and only
        // when the active network is actually Wi-Fi. LIGHT mode is CPU-only.
        if (selectedMode == MODE_LIGHT) return;

        ConnectivityManager connectivity = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = connectivity == null ? null : connectivity.getActiveNetworkInfo();
        if (network == null || !network.isConnected()
                || network.getType() != ConnectivityManager.TYPE_WIFI) {
            return;
        }

        WifiManager manager = (WifiManager)
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null) return;

        try {
            int lockMode = selectedMode == MODE_STRONG
                    ? WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    : WifiManager.WIFI_MODE_FULL;
            wifi = manager.createWifiLock(lockMode, getPackageName() + ":TreoWifi");
            wifi.setReferenceCounted(false);
            wifi.acquire();
        } catch (RuntimeException e) {
            Log.w(TAG, "Wi-Fi lock unavailable; CPU keepalive remains active", e);
        }

        try {
            multicast = manager.createMulticastLock(getPackageName() + ":TreoMulticast");
            multicast.setReferenceCounted(false);
            multicast.acquire();
        } catch (RuntimeException e) {
            Log.w(TAG, "Multicast lock unavailable; CPU keepalive remains active", e);
        }
    }

    private void releaseLocks() {
        PowerManager.WakeLock cpuLock = cpu;
        cpu = null;
        if (cpuLock != null) {
            try { if (cpuLock.isHeld()) cpuLock.release(); }
            catch (RuntimeException e) { Log.w(TAG, "CPU lock release failed", e); }
        }

        WifiManager.WifiLock wifiLock = wifi;
        wifi = null;
        if (wifiLock != null) {
            try { if (wifiLock.isHeld()) wifiLock.release(); }
            catch (RuntimeException e) { Log.w(TAG, "Wi-Fi lock release failed", e); }
        }

        WifiManager.MulticastLock multicastLock = multicast;
        multicast = null;
        if (multicastLock != null) {
            try { if (multicastLock.isHeld()) multicastLock.release(); }
            catch (RuntimeException e) { Log.w(TAG, "Multicast lock release failed", e); }
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Foreground service must outlive removal of the UI task.
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        active = false;
        requested = false;
        releaseLocks();
        super.onDestroy();
        MidletThread.pauseIfActivityStopped();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
'''

source.parent.mkdir(parents=True, exist_ok=True)
source.write_text(java, encoding="utf-8")

# Ensure the key NST-original behavior remains present in the generated source.
check = source.read_text(encoding="utf-8")
required = [
    "PARTIAL_WAKE_LOCK", "MODE_LIGHT", "MODE_WIFI", "MODE_STRONG",
    "getActiveNetworkInfo()", "TYPE_WIFI", "WIFI_MODE_FULL_HIGH_PERF",
    "ACTION_CYCLE_MODE", "START_STICKY", "setOngoing(true)",
    "FOREGROUND_SERVICE_TYPE_SPECIAL_USE"
]
missing = [item for item in required if item not in check]
if missing:
    raise SystemExit("Treo compatibility source missing: " + ", ".join(missing))
print("NST-original Treo behavior ported: modes, conditional Wi-Fi locks, foreground notification actions and sticky preference")
