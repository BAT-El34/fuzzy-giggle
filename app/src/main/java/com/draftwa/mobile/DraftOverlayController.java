package com.draftwa.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/**
 * Compatibility overlay used only before SYSTEM_ALERT_WINDOW is granted.
 * As soon as the real application overlay permission exists, ownership is
 * transferred to DraftOverlayService and this accessibility overlay disappears.
 */
final class DraftOverlayController {
    private final Context context;
    private final Runnable toggleAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private WindowManager windowManager;
    private TextView bubble;
    private TextView timer;
    private WindowManager.LayoutParams bubbleParams;
    private boolean shown;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
        if (Prefs.RUNNING.equals(key) || Prefs.PAUSED.equals(key) || Prefs.NEXT_ACTION_AT.equals(key)) refreshNow();
    };

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (hasSystemOverlayPermission()) {
                destroy();
                DraftOverlayService.start(context);
                DiagnosticLog.event(context, "ACCESSIBILITY_OVERLAY_HANDOFF", "system-overlay");
                return;
            }
            refreshNow();
            if (shown) handler.postDelayed(this, 500L);
        }
    };

    DraftOverlayController(Context context, Runnable toggleAction) {
        this.context = context;
        this.toggleAction = toggleAction;
        this.prefs = Prefs.p(context);
    }

    void show() {
        if (shown) return;
        if (hasSystemOverlayPermission()) {
            DraftOverlayService.start(context);
            DiagnosticLog.event(context, "ACCESSIBILITY_OVERLAY_DEFERRED", "system-overlay");
            return;
        }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) throw new IllegalStateException("WindowManager unavailable");

            bubble = new TextView(context);
            bubble.setGravity(Gravity.CENTER);
            bubble.setTextSize(20f);
            bubble.setTextColor(Color.rgb(38, 50, 56));
            bubble.setBackground(roundRect(Color.WHITE, 24, Color.rgb(226, 232, 235), 1));
            bubble.setElevation(dp(5));
            bubble.setAlpha(0.94f);
            bubble.setOnTouchListener(new BubbleTouch());

            int defaultX = Math.max(dp(12), context.getResources().getDisplayMetrics().widthPixels - dp(62));
            int defaultY = dp(180);
            bubbleParams = new WindowManager.LayoutParams(
                    dp(46), dp(46),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            bubbleParams.gravity = Gravity.TOP | Gravity.START;
            bubbleParams.x = clampX(prefs.getInt(Prefs.OVERLAY_X, defaultX));
            bubbleParams.y = clampY(prefs.getInt(Prefs.OVERLAY_Y, defaultY));

            timer = new TextView(context);
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
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
            DiagnosticLog.event(context, "OVERLAY_SHOWN", "accessibility-fallback-overlay");
        } catch (Throwable t) {
            DiagnosticLog.event(context, "OVERLAY_SHOW_FAILED", t.getClass().getSimpleName());
            destroy();
        }
    }

    void refreshNow() {
        if (!shown || bubble == null || timer == null) return;
        boolean running = prefs.getBoolean(Prefs.RUNNING, false);
        boolean paused = prefs.getBoolean(Prefs.PAUSED, false);
        bubble.setText(running ? "Ⅱ" : "▶");
        bubble.setContentDescription(running
                ? "Contrôle flottant DraftWA — pause"
                : paused ? "Contrôle flottant DraftWA — reprendre" : "Contrôle flottant DraftWA — démarrer");

        String label;
        if (paused) label = "En pause";
        else if (running) {
            long next = prefs.getLong(Prefs.NEXT_ACTION_AT, 0L);
            long remaining = next - System.currentTimeMillis();
            label = next > 0L && remaining > 0L ? "Prochain en " + formatDuration(remaining) : "Traitement…";
        } else label = "DraftWA prêt";
        timer.setText(label);
        timer.setContentDescription("État DraftWA : " + label);
    }

    void destroy() {
        shown = false;
        handler.removeCallbacksAndMessages(null);
        try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        if (windowManager != null) {
            try { if (bubble != null) windowManager.removeView(bubble); } catch (Throwable ignored) {}
            try { if (timer != null) windowManager.removeView(timer); } catch (Throwable ignored) {}
        }
        bubble = null;
        timer = null;
        bubbleParams = null;
    }

    private boolean hasSystemOverlayPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context);
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
        int max = Math.max(0, context.getResources().getDisplayMetrics().widthPixels - dp(50));
        return Math.max(0, Math.min(max, x));
    }

    private int clampY(int y) {
        int max = Math.max(dp(48), context.getResources().getDisplayMetrics().heightPixels - dp(96));
        return Math.max(dp(24), Math.min(max, y));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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
                        prefs.edit().putInt(Prefs.OVERLAY_X, bubbleParams.x).putInt(Prefs.OVERLAY_Y, bubbleParams.y).apply();
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP && toggleAction != null) {
                        toggleAction.run();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
