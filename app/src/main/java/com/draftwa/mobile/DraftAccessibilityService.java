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
    private String originalText = null;
    private PendingAction pendingAction = PendingAction.NONE;
    private int setTextRetries = 0;
    private boolean ticking = false;

    private enum PendingAction { NONE, SEND, DELETE, DIAG_TRANSFORM, DIAG_RESTORE }

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
            originalText = null;
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
        if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "view-id");
        if (list == null) {
            list = findBestScrollable(root);
            if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "scrollable-fallback");
        }

        if (firstById(root, "conversation_list") == null && !sp.getBoolean(Prefs.CHATS_TAB_PROBED, false)) {
            AccessibilityNodeInfo chats = findExactTextClickableOutside(root, list, "Chats", "Discussions");
            if (chats != null) DiagnosticLog.event(this, "CHATS_TAB_FOUND", "");
            Prefs.p(this).edit().putBoolean(Prefs.CHATS_TAB_PROBED, true).putInt(Prefs.SCROLL_COUNT, 0).apply();
            if (chats != null && chats.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                status("Ouverture de l’onglet Chats", "CHATS_TAB_CLICK");
                schedule(700);
                return;
            }
        }

        if (!sp.getBoolean(Prefs.FILTER_PROBED, false)) {
            AccessibilityNodeInfo filter = findExactTextClickableOutside(root, list, "Brouillons", "Drafts");
            if (filter != null) DiagnosticLog.event(this, "DRAFT_FILTER_FOUND", "");
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
                DiagnosticLog.event(this, "DRAFT_FILTER_NOT_FOUND", "fallback scan");
                DiagnosticLog.event(this, "DRAFT_FILTER_UNAVAILABLE", "fallback scan");
            }
        }

        sp = Prefs.p(this);
        if (sp.getBoolean(Prefs.FILTER_PROBED, false) && !sp.getBoolean(Prefs.ALL_FILTER_PROBED, false)) {
            AccessibilityNodeInfo all = findExactTextClickableOutside(root, list, "Toutes", "All");
            if (all != null) DiagnosticLog.event(this, "ALL_FILTER_FOUND", "");
            Prefs.p(this).edit().putBoolean(Prefs.ALL_FILTER_PROBED, true).apply();
            if (all != null && all.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, 0).apply();
                status("Vue Toutes active", "ALL_FILTER_CLICK");
                schedule(650);
                return;
            }
        }

        DiagnosticLog.event(this, "FALLBACK_SCAN_STARTED", "");
        List<AccessibilityNodeInfo> indicators = byId(root, "draft_indicator");
        if (!indicators.isEmpty()) {
            DiagnosticLog.event(this, "DRAFT_INDICATORS", String.valueOf(indicators.size()));
            if (openFirstUnskipped(indicators)) return;
        }

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
        int scrolls = sp.getInt(Prefs.SCROLL_COUNT, 0);
        DiagnosticLog.event(this, "DRAFT_SCAN_PAGE", String.valueOf(scrolls + 1));
        if (list != null && scrolls < MAX_SCROLLS) {
            boolean moved = list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            if (moved) {
                Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, scrolls + 1).apply();
                status("Défilement • page " + (scrolls + 2), "LIST_SCROLL");
                schedule(650);
                return;
            }
        }

        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putString(Prefs.STATUS, "Aucun autre brouillon détecté")
                .apply();
        DiagnosticLog.event(this, "NO_MORE_DRAFTS", "scrolls=" + scrolls);
    }

    private boolean openFirstUnskipped(List<AccessibilityNodeInfo> candidates) {
        Set<String> skipped = Prefs.skipped(this);
        for (AccessibilityNodeInfo n : candidates) {
            if (n == null) continue;
            AccessibilityNodeInfo row = clickableAncestor(n, 10);
            if (row == null) continue;
            String fp = fingerprint(row);
            if (skipped.contains(fp)) continue;
            currentFingerprint = fp;
            if (row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, 0).apply();
                DiagnosticLog.event(this, "DRAFT_OPENED", fp);
                status("Brouillon ouvert", "DRAFT_OPENED");
                schedule(700);
                return true;
            }
        }
        return false;
    }

    private void processChat(AccessibilityNodeInfo root, AccessibilityNodeInfo editor) {
        DiagnosticLog.event(this, "EDITOR_FOUND", "entry");
        String current = nodeText(editor);
        if (pendingAction != PendingAction.NONE && expectedText != null) {
            verifyPendingText(root, editor, current);
            return;
        }

        SharedPreferences sp = Prefs.p(this);
        boolean diagnostic = sp.getBoolean(Prefs.DIAGNOSTIC, true);
        String condition = sp.getString(Prefs.CONDITION, "Soko");
        boolean conform = MessageRules.matches(current, condition);
        DiagnosticLog.event(this, conform ? "CONFIRMATION_PASS" : "CONFIRMATION_FAIL", "");
        DiagnosticLog.event(this, "DRAFT_RULE", "match=" + conform + " length=" + current.length());

        if (!conform) {
            String reject = sp.getString(Prefs.REJECT_MODE, "Conserver");
            if (diagnostic) {
                String code = "Détruire".equalsIgnoreCase(reject) ? "DIAGNOSTIC_DELETE_SKIPPED" : "DIAGNOSTIC_KEEP";
                finishDiagnostic("Test OK • brouillon non conforme • " + reject + " simulé", code);
                return;
            }
            if ("Détruire".equalsIgnoreCase(reject)) {
                if (!setEditorText(editor, "")) {
                    failSafe("Impossible d’effacer le brouillon", "DELETE_SET_TEXT_FAILED");
                    return;
                }
                expectedText = "";
                originalText = current;
                pendingAction = PendingAction.DELETE;
                setTextRetries = 0;
                status("Vérification de l’effacement…", "DELETE_VERIFY");
                schedule(400);
            } else {
                Prefs.addSkipped(this, currentFingerprint);
                DiagnosticLog.event(this, "DRAFT_REJECTED_KEPT", currentFingerprint);
                goBackToList(700);
            }
            return;
        }

        String transformed = MessageRules.transform(
                current,
                sp.getString(Prefs.REMOVE, ",Enregistré,"),
                sp.getBoolean(Prefs.GREETING_ENABLED, true),
                sp.getString(Prefs.TIMEZONE, "Africa/Abidjan"),
                sp.getInt(Prefs.DAY_HOUR, 5),
                sp.getInt(Prefs.EVENING_HOUR, 18));

        if (!transformed.equals(current)) {
            if (!setEditorText(editor, transformed)) {
                failSafe("Retraitement refusé par WhatsApp", "TRANSFORM_SET_TEXT_FAILED");
                return;
            }
            originalText = current;
            expectedText = transformed;
            pendingAction = diagnostic ? PendingAction.DIAG_TRANSFORM : PendingAction.SEND;
            setTextRetries = 0;
            status(diagnostic ? "Test du retraitement…" : "Vérification du retraitement…",
                    diagnostic ? "DIAGNOSTIC_TRANSFORM_VERIFY" : "TRANSFORM_VERIFY");
            schedule(400);
            return;
        }

        if (diagnostic) {
            finishDiagnostic("Test OK • brouillon conforme • aucun retraitement nécessaire", "DIAGNOSTIC_PASS");
        } else {
            sendOrSimulate(root);
        }
    }

    private void verifyPendingText(AccessibilityNodeInfo root, AccessibilityNodeInfo editor, String current) {
        if (current.equals(expectedText)) {
            PendingAction action = pendingAction;
            setTextRetries = 0;

            if (action == PendingAction.DIAG_TRANSFORM) {
                DiagnosticLog.event(this, "TRANSFORM_PASS", "verified");
                String restore = originalText == null ? "" : originalText;
                if (!setEditorText(editor, restore)) {
                    failSafe("Le test a transformé le brouillon mais n’a pas pu restaurer le texte original", "DIAGNOSTIC_RESTORE_FAILED");
                    return;
                }
                expectedText = restore;
                pendingAction = PendingAction.DIAG_RESTORE;
                status("Restauration du brouillon original…", "DIAGNOSTIC_RESTORE_VERIFY");
                schedule(400);
                return;
            }

            expectedText = null;
            pendingAction = PendingAction.NONE;
            if (action == PendingAction.DIAG_RESTORE) {
                DiagnosticLog.event(this, "RESTORED_OK", "verified");
                DiagnosticLog.event(this, "SEND_SKIPPED_DIAGNOSTIC", "restored-before-exit");
                originalText = null;
                finishDiagnostic("Test OK • brouillon détecté et retraitement validé • texte original restauré", "DIAGNOSTIC_PASS_RESTORED");
            } else if (action == PendingAction.DELETE) {
                originalText = null;
                Prefs.addSkipped(this, currentFingerprint);
                DiagnosticLog.event(this, "DRAFT_REJECTED_DELETED", currentFingerprint);
                goBackToList(700);
            } else {
                originalText = null;
                sendOrSimulate(root);
            }
            return;
        }

        if (setTextRetries < 1) {
            setTextRetries++;
            if (setEditorText(editor, expectedText)) {
                DiagnosticLog.event(this, "SET_TEXT_RETRY", pendingAction.name());
                schedule(450);
                return;
            }
        }
        failSafe("WhatsApp n’a pas confirmé la modification du texte", "SET_TEXT_VERIFY_FAILED");
    }

    private void sendOrSimulate(AccessibilityNodeInfo root) {
        SharedPreferences sp = Prefs.p(this);
        if (sp.getBoolean(Prefs.DIAGNOSTIC, true)) {
            finishDiagnostic("Test OK • envoi volontairement ignoré", "SEND_SKIPPED_DIAGNOSTIC");
            return;
        }

        AccessibilityNodeInfo send = firstById(root, "send");
        if (send == null) send = firstById(root, "send_message");
        if (send == null) send = findTextClickable(root, "Envoyer", "Send");
        send = clickableAncestor(send, 5);
        if (send == null || !send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            failSafe("Bouton Envoyer introuvable", "SEND_BUTTON_NOT_FOUND");
            return;
        }

        DiagnosticLog.event(this, "MESSAGE_SENT", currentFingerprint);
        updateBatchAndDelay();
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            currentFingerprint = "";
        }, 650);
    }

    private void updateBatchAndDelay() {
        SharedPreferences sp = Prefs.p(this);
        int target = sp.getInt(Prefs.BATCH_TARGET, 0);
        if (target <= 0) target = rand(sp.getInt(Prefs.Y_MIN, 3), sp.getInt(Prefs.Y_MAX, 5));
        int sent = sp.getInt(Prefs.SENT_IN_BATCH, 0) + 1;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = sp.edit();
        if (sent >= target) {
            int minutes = rand(sp.getInt(Prefs.X_MIN, 5), sp.getInt(Prefs.X_MAX, 12));
            e.putInt(Prefs.SENT_IN_BATCH, 0)
                    .putInt(Prefs.BATCH_TARGET, rand(sp.getInt(Prefs.Y_MIN, 3), sp.getInt(Prefs.Y_MAX, 5)))
                    .putLong(Prefs.NEXT_ACTION_AT, now + minutes * 60_000L)
                    .putString(Prefs.STATUS, "Pause entre lots • " + minutes + " min");
            DiagnosticLog.event(this, "BATCH_PAUSE", minutes + " min");
        } else {
            int seconds = rand(sp.getInt(Prefs.Z_MIN, 25), sp.getInt(Prefs.Z_MAX, 60));
            e.putInt(Prefs.SENT_IN_BATCH, sent)
                    .putInt(Prefs.BATCH_TARGET, target)
                    .putLong(Prefs.NEXT_ACTION_AT, now + seconds * 1000L)
                    .putString(Prefs.STATUS, "Pause entre messages • " + seconds + " s");
            DiagnosticLog.event(this, "MESSAGE_PAUSE", seconds + " s");
        }
        e.apply();
    }

    private void goBackToList(long delayAfterBack) {
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            currentFingerprint = "";
            Prefs.p(this).edit().putLong(Prefs.NEXT_ACTION_AT, System.currentTimeMillis() + delayAfterBack).apply();
        }, 250);
    }

    private void finishDiagnostic(String message, String code) {
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putString(Prefs.STATUS, message)
                .apply();
        DiagnosticLog.event(this, code, currentFingerprint);
        lastStatus = message;
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            currentFingerprint = "";
            expectedText = null;
            originalText = null;
            pendingAction = PendingAction.NONE;
        }, 350);
    }

    private void failSafe(String message, String code) {
        Prefs.p(this).edit().putBoolean(Prefs.RUNNING, false).putString(Prefs.STATUS, message).apply();
        DiagnosticLog.event(this, code, "PAUSED");
        lastStatus = message;
    }

    private void launchWhatsApp() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunchAt < 4000) return;
        lastLaunchAt = now;
        Intent i = getPackageManager().getLaunchIntentForPackage(WA_PACKAGE);
        if (i == null) {
            failSafe("WhatsApp Business introuvable", "WA_NOT_INSTALLED");
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        status("Ouverture de WhatsApp Business", "WA_LAUNCH");
    }

    private void status(String message, String code) {
        if (!message.equals(lastStatus)) {
            lastStatus = message;
            Prefs.p(this).edit().putString(Prefs.STATUS, message).apply();
            DiagnosticLog.event(this, code, message);
        }
    }

    private List<AccessibilityNodeInfo> byId(AccessibilityNodeInfo root, String id) {
        if (root == null) return new ArrayList<>();
        try {
            List<AccessibilityNodeInfo> r = root.findAccessibilityNodeInfosByViewId(ID_PREFIX + id);
            return r == null ? new ArrayList<>() : r;
        } catch (Throwable t) {
            DiagnosticLog.event(this, "VIEW_ID_SEARCH_FAILED", id + ": " + t.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private AccessibilityNodeInfo firstById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> r = byId(root, id);
        return r.isEmpty() ? null : r.get(0);
    }

    private List<AccessibilityNodeInfo> byText(AccessibilityNodeInfo root, String text) {
        try {
            List<AccessibilityNodeInfo> r = root.findAccessibilityNodeInfosByText(text);
            return r == null ? new ArrayList<>() : r;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private AccessibilityNodeInfo findTextClickable(AccessibilityNodeInfo root, String... tokens) {
        for (String token : tokens) {
            for (AccessibilityNodeInfo n : byText(root, token)) {
                String text = nodeText(n).trim();
                CharSequence desc = n.getContentDescription();
                String all = (text + " " + (desc == null ? "" : desc)).toLowerCase(Locale.ROOT);
                if (!all.contains(token.toLowerCase(Locale.ROOT))) continue;
                AccessibilityNodeInfo c = clickableAncestor(n, 8);
                if (c != null) return c;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo n, int max) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; cur != null && i <= max; i++) {
            if (cur.isClickable() && cur.isEnabled()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findExactTextClickableOutside(AccessibilityNodeInfo root, AccessibilityNodeInfo excludedSubtree, String... tokens) {
        for (String token : tokens) {
            for (AccessibilityNodeInfo n : byText(root, token)) {
                if (n == null) continue;
                String text = nodeText(n).trim();
                CharSequence desc = n.getContentDescription();
                String d = desc == null ? "" : desc.toString().trim();
                if (!text.equalsIgnoreCase(token) && !d.equalsIgnoreCase(token)) continue;
                if (excludedSubtree != null && isDescendantOf(n, excludedSubtree)) continue;
                AccessibilityNodeInfo c = clickableAncestor(n, 8);
                if (c != null) return c;
            }
        }
        return null;
    }

    private boolean isDraftTextMarker(AccessibilityNodeInfo n) {
        if (n == null) return false;
        String text = nodeText(n).trim().toLowerCase(Locale.ROOT);
        CharSequence desc = n.getContentDescription();
        String all = (text + " " + (desc == null ? "" : desc.toString())).trim().toLowerCase(Locale.ROOT);
        if (all.equals("drafts") || all.equals("brouillons")) return false;
        return all.contains("brouillon") || all.startsWith("draft");
    }

    private boolean isDescendantOf(AccessibilityNodeInfo node, AccessibilityNodeInfo ancestor) {
        if (node == null || ancestor == null) return false;
        AccessibilityNodeInfo cur = node;
        for (int i = 0; cur != null && i < 16; i++) {
            if (cur.equals(ancestor)) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private AccessibilityNodeInfo findBestScrollable(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo[] best = new AccessibilityNodeInfo[1];
        int[] bestScore = new int[] { Integer.MIN_VALUE };
        findBestScrollable(root, 0, best, bestScore);
        return best[0];
    }

    private void findBestScrollable(AccessibilityNodeInfo n, int depth, AccessibilityNodeInfo[] best, int[] bestScore) {
        if (n == null || depth > 14) return;
        try {
            if (n.isScrollable()) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                int height = Math.max(0, r.bottom - r.top);
                int width = Math.max(0, r.right - r.left);
                int score = n.getChildCount() * 10000 + height * 4 - Math.max(0, width - height);
                if (score > bestScore[0]) {
                    bestScore[0] = score;
                    best[0] = n;
                }
            }
        } catch (Throwable ignored) {}
        int count = Math.min(n.getChildCount(), 30);
        for (int i = 0; i < count; i++) findBestScrollable(n.getChild(i), depth + 1, best, bestScore);
    }

    private boolean setEditorText(AccessibilityNodeInfo editor, String value) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private String nodeText(AccessibilityNodeInfo n) {
        if (n == null) return "";
        CharSequence t = n.getText();
        return t == null ? "" : t.toString();
    }

    private String fingerprint(AccessibilityNodeInfo row) {
        StringBuilder b = new StringBuilder();
        collectText(row, b, 0, 5);
        Rect r = new Rect();
        row.getBoundsInScreen(r);
        String raw = b.toString().trim();
        if (raw.isEmpty()) raw = row.getViewIdResourceName() + "@" + r.left + "," + r.top + "," + r.right + "," + r.bottom;
        return Integer.toHexString(raw.toLowerCase(Locale.ROOT).hashCode());
    }

    private void collectText(AccessibilityNodeInfo n, StringBuilder b, int depth, int maxDepth) {
        if (n == null || depth > maxDepth) return;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if (t != null && t.length() > 0) b.append(t).append('|');
        if (d != null && d.length() > 0) b.append(d).append('|');
        int count = Math.min(n.getChildCount(), 12);
        for (int i = 0; i < count; i++) collectText(n.getChild(i), b, depth + 1, maxDepth);
    }

    private int rand(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return lo == hi ? lo : lo + random.nextInt(hi - lo + 1);
    }
}
