package com.draftwa.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/**
 * Persistent white/minimal system overlay for DraftWA.
 *
 * This service is deliberately independent from DraftAccessibilityService.
 * Accessibility controls WhatsApp; this foreground service owns the pause/resume
 * bubble and the real countdown while the user is in WhatsApp, another app, or
 * on the launcher.
 */
public class DraftOverlayService extends Service {
    private static final String CHANNEL_ID = "draftwa_overlay";
    private static final int NOTIFICATION_ID = 806;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WindowManager windowManager;
    private TextView bubble;
    private TextView timer;
    private WindowManager.LayoutParams bubbleParams;
    private boolean shown;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
        if (Prefs.RUNNING.equals(key) || Prefs.PAUSED.equals(key) || Prefs.NEXT_ACTION_AT.equals(key)) {
            refreshNow();
        }
    };

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            refreshNow();
            if (shown) handler.postDelayed(this, 500L);
        }
    };

    public static boolean canDraw(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    public static void start(Context context) {
        if (context == null || !canDraw(context)) return;
        Intent intent = new Intent(context, DraftOverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable t) {
            DiagnosticLog.event(context, "SYSTEM_OVERLAY_SERVICE_START_FAILED", t.getClass().getSimpleName());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.ensureDefaults(this);
        prefs = Prefs.p(this);
        startAsForeground();
        showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!canDraw(this)) {
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_PERMISSION_MISSING", "service-stop");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!shown) showOverlay();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        destroyOverlay();
        super.onDestroy();
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Contrôle DraftWA",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Maintient le contrôle flottant pause/reprise de DraftWA disponible hors de l’application.");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, OverlayGateActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_draftwa)
                .setContentTitle("DraftWA")
                .setContentText("Contrôle flottant actif")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void showOverlay() {
        if (shown || !canDraw(this)) return;
        try {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) throw new IllegalStateException("WindowManager unavailable");

            bubble = new TextView(this);
            bubble.setGravity(Gravity.CENTER);
            bubble.setTextSize(20f);
            bubble.setTextColor(Color.rgb(38, 50, 56));
            bubble.setBackground(roundRect(Color.WHITE, 24, Color.rgb(226, 232, 235), 1));
            bubble.setElevation(dp(5));
            bubble.setAlpha(0.94f);
            bubble.setOnTouchListener(new BubbleTouch());

            int defaultX = Math.max(dp(12), getResources().getDisplayMetrics().widthPixels - dp(62));
            int defaultY = dp(180);
            int x = prefs.getInt(Prefs.OVERLAY_X, defaultX);
            int y = prefs.getInt(Prefs.OVERLAY_Y, defaultY);
            bubbleParams = new WindowManager.LayoutParams(
                    dp(46), dp(46),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            bubbleParams.gravity = Gravity.TOP | Gravity.START;
            bubbleParams.x = clampX(x);
            bubbleParams.y = clampY(y);

            timer = new TextView(this);
            timer.setGravity(Gravity.CENTER);
            timer.setTextSize(12f);
            timer.setTextColor(Color.rgb(55, 65, 71));
            timer.setPadding(dp(13), dp(7), dp(13), dp(7));
            timer.setBackground(roundRect(Color.WHITE, 18, Color.rgb(231, 235, 237), 1));
            timer.setElevation(dp(4));
            timer.setAlpha(0.96f);
            WindowManager.LayoutParams timerParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            timerParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            timerParams.y = dp(20);

            windowManager.addView(bubble, bubbleParams);
            windowManager.addView(timer, timerParams);
            shown = true;
            prefs.registerOnSharedPreferenceChangeListener(listener);
            refreshNow();
            handler.post(clock);
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_SHOWN", "application-overlay");
            DiagnosticLog.event(this, "OVERLAY_SHOWN", "system-application-overlay");
        } catch (Throwable t) {
            DiagnosticLog.event(this, "SYSTEM_OVERLAY_SHOW_FAILED", t.getClass().getSimpleName());
            destroyOverlay();
        }
    }

    private void toggleEngine() {
        SharedPreferences sp = Prefs.p(this);
        if (sp.getBoolean(Prefs.RUNNING, false)) {
            long remaining = Prefs.pauseAutomation(this);
            Prefs.p(this).edit().putString(Prefs.STATUS, "En pause").apply();
            DiagnosticLog.event(this, "OVERLAY_PAUSE", "remainingMs=" + remaining);
        } else {
            boolean paused = sp.getBoolean(Prefs.PAUSED, false);
            if (paused) {
                long remaining = Prefs.resumeAutomation(this);
                Prefs.p(this).edit().putString(Prefs.STATUS, "Reprise…").apply();
                DiagnosticLog.event(this, "OVERLAY_RESUME", "remainingMs=" + remaining);
            } else {
                Prefs.resetRunState(this);
                Prefs.p(this).edit()
                        .putBoolean(Prefs.RUNNING, true)
                        .putBoolean(Prefs.PAUSED, false)
                        .putString(Prefs.STATUS, "Démarrage…")
                        .apply();
                DiagnosticLog.event(this, "OVERLAY_START", "");
            }
            launchWhatsAppBusiness();
        }
        refreshNow();
    }

    private void launchWhatsAppBusiness() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(DraftAccessibilityService.WA_PACKAGE);
            if (launch == null) {
                DiagnosticLog.event(this, "OVERLAY_WA_LAUNCH_FAILED", "missing-package");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            DiagnosticLog.event(this, "OVERLAY_WA_LAUNCH", "ok");
        } catch (Throwable t) {
            DiagnosticLog.event(this, "OVERLAY_WA_LAUNCH_FAILED", t.getClass().getSimpleName());
        }
    }

    private void refreshNow() {
        if (!shown || bubble == null || timer == null) return;
        boolean running = prefs.getBoolean(Prefs.RUNNING, false);
        boolean paused = prefs.getBoolean(Prefs.PAUSED, false);

        if (running) {
            bubble.setText("Ⅱ");
            bubble.setContentDescription("Contrôle flottant DraftWA — pause");
        } else {
            bubble.setText("▶");
            bubble.setContentDescription(paused
                    ? "Contrôle flottant DraftWA — reprendre"
                    : "Contrôle flottant DraftWA — démarrer");
        }

        String label;
        if (paused) {
            label = "En pause";
        } else if (running) {
            long next = prefs.getLong(Prefs.NEXT_ACTION_AT, 0L);
            long remaining = next - System.currentTimeMillis();
            label = next > 0L && remaining > 0L ? "Prochain en " + formatDuration(remaining) : "Traitement…";
        } else {
            label = "DraftWA prêt";
        }
        timer.setText(label);
        timer.setContentDescription("État DraftWA : " + label);
    }

    private void destroyOverlay() {
        shown = false;
        handler.removeCallbacksAndMessages(null);
        if (prefs != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        if (windowManager != null) {
            try { if (bubble != null) windowManager.removeView(bubble); } catch (Throwable ignored) {}
            try { if (timer != null) windowManager.removeView(timer); } catch (Throwable ignored) {}
        }
        bubble = null;
        timer = null;
        bubbleParams = null;
    }

    private String formatDuration(long millis) {
        long total = Math.max(0L, (millis + 999L) / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private int clampX(int x) {
        int max = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(50));
        return Math.max(0, Math.min(max, x));
    }

    private int clampY(int y) {
        int max = Math.max(dp(48), getResources().getDisplayMetrics().heightPixels - dp(96));
        return Math.max(dp(24), Math.min(max, y));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class BubbleTouch implements View.OnTouchListener {
        private float downRawX, downRawY;
        private int downX, downY;
        private boolean moved;

        @Override public boolean onTouch(View v, MotionEvent event) {
            if (bubbleParams == null || windowManager == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = bubbleParams.x;
                    downY = bubbleParams.y;
                    moved = false;
                    bubble.setAlpha(1f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!moved && Math.hypot(dx, dy) > dp(6)) moved = true;
                    if (moved) {
                        bubbleParams.x = clampX(downX + Math.round(dx));
                        bubbleParams.y = clampY(downY + Math.round(dy));
                        try { windowManager.updateViewLayout(bubble, bubbleParams); } catch (Throwable ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    bubble.setAlpha(0.94f);
                    if (moved) {
                        prefs.edit()
                                .putInt(Prefs.OVERLAY_X, bubbleParams.x)
                                .putInt(Prefs.OVERLAY_Y, bubbleParams.y)
                                .apply();
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        toggleEngine();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
