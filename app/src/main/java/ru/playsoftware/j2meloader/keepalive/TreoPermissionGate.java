package ru.playsoftware.j2meloader.keepalive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;

import ru.playsoftware.j2meloader.R;

/**
 * Permission flow used by the original NST Treo toggle.
 * Notification permission is requested on Android 13+, while battery
 * optimization exemption remains an explicit user decision in system UI.
 */
public final class TreoPermissionGate {
    public static final int REQUEST_NOTIFICATIONS = 0xA743;

    private TreoPermissionGate() {
    }

    public static boolean needsNotificationPermission(Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    public static void requestNotificationPermission(Activity activity, int requestCode) {
        if (needsNotificationPermission(activity)) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
        }
    }

    public static void runWithBatteryOptimizationPrompt(Activity activity, Runnable continuation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager manager = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
            if (manager != null && !manager.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.treo_battopt_title)
                        .setMessage(R.string.treo_battopt_msg)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            try {
                                Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(request);
                            } catch (Exception ignored) {
                                Intent settings = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                try {
                                    activity.startActivity(settings);
                                } catch (Exception ignoredAgain) {
                                    // The permission remains optional and user-controlled.
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }
        try {
            continuation.run();
        } catch (Exception ignored) {
            // Match the original flow: an optional battery prompt must not block Treo startup.
        }
    }
}
