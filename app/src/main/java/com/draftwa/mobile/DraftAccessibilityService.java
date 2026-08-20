package com.draftwa.mobile;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class DraftAccessibilityService extends AccessibilityService {
    static final String WA_PACKAGE = "com.whatsapp.w4b";
    private static final String ID_PREFIX = WA_PACKAGE + ":id/";
    private static final int MAX_SCROLLS = 18;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private long lastLaunchAt = 0L;
    private String lastStatus = "";
    private String currentFingerprint = "";
    private String expectedText = null;
    private PendingAction pendingAction = PendingAction.NONE;
    private int setTextRetries = 0;
    private boolean ticking = false;

    private enum PendingAction { NONE, SEND, DELETE }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            ticking = false;
            tick();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Prefs.ensureDefaults(this);
        DiagnosticLog.event(this, "ACCESSIBILITY_CONNECTED", "");
        schedule(300);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!Prefs.p(this).getBoolean(Prefs.RUNNING, false)) return;
        if (event != null && event.getPackageName() != null) {
            DiagnosticLog.event(this, "UI_EVENT", event.getEventType() + " @ " + event.getPackageName());
        }
        schedule(250);
    }

    @Override
    public void onInterrupt() {
        status("Service d’accessibilité interrompu", "ACCESSIBILITY_INTERRUPTED");
    }

    private void schedule(long delayMs) {
        if (ticking) return;
        ticking = true;
        handler.postDelayed(ticker, Math.max(80, delayMs));
    }

    private void tick() {
        SharedPreferences sp = Prefs.p(this);
        if (!sp.getBoolean(Prefs.RUNNING, false)) return;

        long now = System.currentTimeMillis();
        long next = sp.getLong(Prefs.NEXT_ACTION_AT, 0L);
        if (next > now) {
            schedule(Math.min(2000, next - now));
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            status("Fenêtre Android indisponible", "ROOT_NULL");
            schedule(800);
            return;
        }

        CharSequence pkg = root.getPackageName();
        if (pkg == null || !WA_PACKAGE.contentEquals(pkg)) {
            launchWhatsApp();
            schedule(900);
            return;
        }
        DiagnosticLog.event(this, "WA_PACKAGE_OK", WA_PACKAGE);

        AccessibilityNodeInfo editor = firstById(root, "entry");
        if (editor != null) {
            processChat(root, editor);
        } else {
            expectedText = null;
            pendingAction = PendingAction.NONE;
            setTextRetries = 0;
            processConversationList(root);
        }

        schedule(700);
    }

    private void processConversationList(AccessibilityNodeInfo root) {
        SharedPreferences sp = Prefs.p(this);
        status("Recherche des brouillons…", "LIST_SCAN");

        AccessibilityNodeInfo list = firstById(root, "conversation_list");
        if (list == null) list = findBestScrollable(root);

        // If WhatsApp opened on another tab, probe Chats/Discussions only once per run.
        // This avoids repeatedly clicking an already-selected Chats tab on builds that hide conversation_list IDs.
        if (firstById(root, "conversation_list") == null && !sp.getBoolean(Prefs.CHATS_TAB_PROBED, false)) {
            AccessibilityNodeInfo chats = findExactTextClickableOutside(root, list, "Chats", "Discussions");
            Prefs.p(this).edit().putBoolean(Prefs.CHATS_TAB_PROBED, true).putInt(Prefs.SCROLL_COUNT, 0).apply();
            if (chats != null && chats.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                status("Ouverture de l’onglet Chats", "CHATS_TAB_CLICK");
                schedule(700);
                return;
            }
        }

        if (!sp.getBoolean(Prefs.FILTER_PROBED, false)) {
            AccessibilityNodeInfo filter = findExactTextClickableOutside(root, list, "Brouillons", "Drafts");
            if (filter != null && filter.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Prefs.p(this).edit()
                        .putBoolean(Prefs.FILTER_PROBED, true)
                        .putBoolean(Prefs.ALL_FILTER_PROBED, true)
                        .putInt(Prefs.SCROLL_COUNT, 0)
                        .apply();
                status("Filtre Brouillons actif", "DRAFT_FILTER_CLICK");
                schedule(700);
                return;
            }

            if (!sp.getBoolean(Prefs.FILTER_MENU_PROBED, false)) {
                AccessibilityNodeInfo menu = findExactTextClickableOutside(root, list, "Filtres", "Filters");
                Prefs.p(this).edit().putBoolean(Prefs.FILTER_MENU_PROBED, true).apply();
                if (menu != null && menu.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    status("Recherche du filtre Brouillons…", "FILTER_MENU_CLICK");
                    schedule(650);
                    return;
                }
            } else {
                Prefs.p(this).edit().putBoolean(Prefs.FILTER_PROBED, true).apply();
                DiagnosticLog.event(this, "DRAFT_FILTER_UNAVAILABLE", "fallback scan");
            }
        }

        // When no dedicated Drafts filter exists, force the normal All/Toutes list once.
        sp = Prefs.p(this);
        if (sp.getBoolean(Prefs.FILTER_PROBED, false) && !sp.getBoolean(Prefs.ALL_FILTER_PROBED, false)) {
            AccessibilityNodeInfo all = findExactTextClickableOutside(root, list, "Toutes", "All");
            Prefs.p(this).edit().putBoolean(Prefs.ALL_FILTER_PROBED, true).apply();
            if (all != null && all.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, 0).apply();
                status("Vue Toutes active", "ALL_FILTER_CLICK");
                schedule(650);
                return;
            }
        }

        List<AccessibilityNodeInfo> indicators = byId(root, "draft_indicator");
        if (!indicators.isEmpty()) {
            DiagnosticLog.event(this, "DRAFT_INDICATORS", String.valueOf(indicators.size()));
            if (openFirstUnskipped(indicators)) return;
        }

        // Fallback for builds/locales that do not expose view IDs through Accessibility.
        List<AccessibilityNodeInfo> fallback = new ArrayList<>();
        for (AccessibilityNodeInfo n : byText(root, "Brouillon")) {
            if (isDraftTextMarker(n) && (list == null || isDescendantOf(n, list))) fallback.add(n);
        }
        for (AccessibilityNodeInfo n : byText(root, "Draft")) {
            if (isDraftTextMarker(n) && (list == null || isDescendantOf(n, list))) fallback.add(n);
        }
        if (openFirstUnskipped(fallback)) return;

        list = firstById(root, "conversation_list");
        if (list == null) list = findBestScrollable(root);
        sp = Prefs.p(this);
        int scrolls = sp.getInt(Prefs.SCROLL_COUNT, 0);
        int page = Math.max(1, sp.getInt(Prefs.SCAN_PAGE, 1));
        DiagnosticLog.event(this, "DRAFT_SCAN_PAGE", String.valueOf(page));

        if (list != null) {
            String viewport = viewportSignature(list);
            String previous = sp.getString(Prefs.LAST_VIEWPORT, "");
            int same = viewport.equals(previous) && !viewport.isEmpty()
                    ? sp.getInt(Prefs.SAME_VIEWPORT_COUNT, 0) + 1
                    : 0;
            Prefs.p(this).edit()
                    .putString(Prefs.LAST_VIEWPORT, viewport)
                    .putInt(Prefs.SAME_VIEWPORT_COUNT, same)
                    .apply();
            if (same >= 2) {
                stopScan("Fin de liste détectée • vie