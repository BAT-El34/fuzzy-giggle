package com.draftwa.mobile;

import android.content.Context;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteNotificationManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    static void send(Context c, String type, String title, String message) {
        if (c == null) return;
        if (!Prefs.p(c).getBoolean(Prefs.REMOTE_NOTIFICATIONS, false)) return;
        String email = Prefs.p(c).getString(Prefs.NOTIFICATION_EMAIL, "").trim();
        if (email.isEmpty()) return;
        String key = type + "-" + System.currentTimeMillis() / 60000L;
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BuildConfig.BACKEND_BASE_URL + "/api/notifications/send");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject();
                body.put("to", email);
                body.put("type", type);
                body.put("title", title);
                body.put("message", message);
                body.put("idempotencyKey", "draftwa-" + key);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
                int code = conn.getResponseCode();
                DiagnosticLog.event(c, code >= 200 && code < 300 ? "REMOTE_NOTIFICATION_SENT" : "REMOTE_NOTIFICATION_FAILED", type + " http=" + code);
            } catch (Throwable t) {
                DiagnosticLog.event(c, "REMOTE_NOTIFICATION_FAILED", type + " " + t.getClass().getSimpleName());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private RemoteNotificationManager() {}
}
