package ru.playsoftware.j2meloader.keepalive;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;

/**
 * Quick Settings tile matching the NST Treo tile behavior.
 * An active tile cycles the current Treo mode; an inactive tile opens the app.
 */
public final class TreoTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (TreoController.isRunning()) {
            TreoController.cycleMode(this);
            refreshTile();
            return;
        }

        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) {
            launch = new Intent(this, MainActivity.class);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launch, flags);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(launch);
        }
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = TreoController.isRunning();
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.treo_tile_label));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(running ? modeLabel(TreoController.getCurrentMode()) : null);
        }
        tile.updateTile();
    }

    private String modeLabel(int mode) {
        if (mode == TreoOnlineService.MODE_WIFI) return getString(R.string.treo_mode_wifi);
        if (mode == TreoOnlineService.MODE_STRONG) return getString(R.string.treo_mode_strong);
        return getString(R.string.treo_mode_light);
    }
}
