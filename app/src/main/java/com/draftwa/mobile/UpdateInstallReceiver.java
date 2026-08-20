package com.draftwa.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
                DiagnosticLog.event(context, "UPDATE_CONFIRMATION_OPENED", "");
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            DiagnosticLog.event(context, "UPDATE_SUCCESS", "");
            Toast.makeText(context, "DraftWA mis à jour", Toast.LENGTH_LONG).show();
        } else {
            DiagnosticLog.event(context, "UPDATE_RESULT_FAILED", String.valueOf(message));
            Toast.makeText(context, "Mise à jour non installée : " + message, Toast.LENGTH_LONG).show();
        }
    }
}
