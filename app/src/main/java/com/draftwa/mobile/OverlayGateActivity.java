package com.draftwa.mobile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Launcher wrapper that keeps the existing MainActivity UI untouched while
 * handling the one Android permission required by the persistent system overlay.
 */
public class OverlayGateActivity extends MainActivity {
    private boolean overlaySettingsOpened;
    private boolean denialNotified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureOverlayPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DraftOverlayService.canDraw(this)) {
            DraftOverlayService.start(this);
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_PERMISSION_OK", "");
            return;
        }

        if (overlaySettingsOpened && !denialNotified) {
            denialNotified = true;
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_PERMISSION_DENIED", "");
            Toast.makeText(
                    this,
                    "Autorise « Afficher par-dessus les autres applications » pour garder le bouton pause/reprise hors de DraftWA.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void ensureOverlayPermission() {
        if (DraftOverlayService.canDraw(this)) {
            DraftOverlayService.start(this);
            return;
        }
        try {
            overlaySettingsOpened = true;
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_PERMISSION_REQUEST", "");
            startActivity(intent);
        } catch (Throwable t) {
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_PERMISSION_REQUEST_FAILED", t.getClass().getSimpleName());
            Toast.makeText(
                    this,
                    "Active manuellement « Afficher par-dessus les autres applications » pour DraftWA.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
