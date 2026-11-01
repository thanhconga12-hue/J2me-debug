package ru.playsoftware.j2meloader.keepalive;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

/** Original NST OFF / SOFT / STEALTH / NINJA hide strategies. */
public enum HideStrategy {
    OFF,
    SOFT,
    STEALTH,
    NINJA;

    public static HideStrategy from(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
        String value = preferences.getString("pref_hide_strategy", "SOFT");
        try {
            return HideStrategy.valueOf(value);
        } catch (Exception ignored) {
            return SOFT;
        }
    }

    /** Reset launcher visibility and recent-task visibility, matching the original reset path. */
    public static void reset(Context context) {
        setLauncherAliasEnabled(context, false);
        setExcludedFromRecents(context, false);
    }

    public static void setExcludedFromRecents(Context context, boolean excluded) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return;
            List<ActivityManager.AppTask> tasks = manager.getAppTasks();
            if (tasks == null) return;
            for (ActivityManager.AppTask task : tasks) {
                try {
                    task.setExcludeFromRecents(excluded);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void setLauncherAliasEnabled(Context context, boolean enabled) {
        try {
            ComponentName alias = new ComponentName(context.getPackageName(),
                    "ru.playsoftware.j2meloader.LauncherAlias");
            context.getPackageManager().setComponentEnabledSetting(alias,
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
    }

    public void apply(Activity activity, boolean bubbleIsRunning) {
        switch (this) {
            case OFF:
                return;
            case SOFT:
                activity.moveTaskToBack(true);
                return;
            case STEALTH:
                setExcludedFromRecents(activity, true);
                activity.moveTaskToBack(true);
                return;
            case NINJA:
                boolean notificationsEnabled = NotificationManagerCompat
                        .from(activity.getApplicationContext()).areNotificationsEnabled();
                if (!bubbleIsRunning && !notificationsEnabled) {
                    STEALTH.apply(activity, false);
                    return;
                }
                setLauncherAliasEnabled(activity, true);
                setExcludedFromRecents(activity, true);
                activity.moveTaskToBack(true);
        }
    }
}
