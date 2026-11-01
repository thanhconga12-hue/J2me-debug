package ru.playsoftware.j2meloader.keepalive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores launcher and recent-task visibility after a reboot or package replacement. */
public final class TreoBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        try {
            HideStrategy.reset(context.getApplicationContext());
        } catch (Exception ignored) {
            // The original receiver deliberately treats this cleanup as best effort.
        }
    }
}
