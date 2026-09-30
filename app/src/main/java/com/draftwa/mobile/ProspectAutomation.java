package com.draftwa.mobile;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class ProspectAutomation {
    private static final String WA_PACKAGE = DraftAccessibilityService.WA_PACKAGE;
    private static final String ID_PREFIX = WA_PACKAGE + ":id/";
    private final AccessibilityService service;
    private final Random random = new Random();
    private long lastOpenAt;

    ProspectAutomation(AccessibilityService service) {
        this.service = service;
    }

    boolean tick(AccessibilityNodeInfo root) {
        SharedPreferences sp = Prefs.p(service);
        if (!sp.getBoolean(Prefs.PROSPECT_MODE, false)) return false;

        long now = System.currentTimeMillis();
        long next = sp.getLong(Prefs.NEXT_ACTION_AT, 0L);
        if (next > now) return true;

        ProspectRecord prospect = ProspectStore.current(service);
        if (prospect == null) {
            sp.edit()
                    .putBoolean(Prefs.RUNNING, false)
                    .putBoolean(Prefs.PROSPECT_MODE, false)
                    .putString(Prefs.PROSPECT_PHASE, "IDLE")
                    .putString(Prefs.STATUS, "Base Excel terminée")
                    .apply();
            DiagnosticLog.event(service, "PROSPECT_QUEUE_DONE", "");
            RemoteNotificationManager.send(
                    service,
                    "CAMPAIGN_COMPLETE",
                    "DraftWA - campagne terminée",
                    "La campagne est terminée. Progression : " + ProspectStore.completed(service) + "/" + ProspectStore.total(service)
                            + ", envoyés : " + ProspectStore.sent(service)
                            + ", brouillons : " + ProspectStore.drafted(service) + ".");
            return true;
        }

        if (prospect.phones.isEmpty()) {
            prospect.status = ProspectRecord.NO_WHATSAPP;
            prospect.lastError = "Aucun numéro mobile admissible";
            ProspectStore.completeCurrent(service, prospect);
            return true;
        }

        String phase = sp.getString(Prefs.PROSPECT_PHASE, "IDLE");
        if ("IDLE".equals(phase)) {
            String phone = prospect.currentPhone();
            if (phone.isEmpty()) {
                prospect.status = ProspectRecord.NO_WHATSAPP;
                prospect.lastError = "Tous les numéros ont été testés";
                ProspectStore.completeCurrent(service, prospect);
                return true;
            }
            openCandidate(prospect, phone);
            return true;
        }

        AccessibilityNodeInfo editor = firstById(root, "entry");
        if (editor != null) {
            String active = sp.getString(Prefs.PROSPECT_ACTIVE_PHONE, prospect.currentPhone());
            prospect.selectedPhone = active;
            if (!setText(editor, prospect.message)) {
                prospect.status = ProspectRecord.FAILED;
                prospect.lastError = "Impossible d’insérer le message";
                ProspectStore.completeCurrent(service, prospect);
                resetPhase();
                return true;
            }

            String action = sp.getString(Prefs.PROSPECT_ACTION, "DRAFT");
            if ("SEND".equals(action)) {
                AccessibilityNodeInfo send = firstById(root, "send");
                if (send == null) send = firstById(root, "send_message");
                if (send == null) send = findTextClickable(root, "Envoyer", "Send");
                send = clickableAncestor(send, 5);
                if (send == null || !send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    prospect.status = ProspectRecord.FAILED;
                    prospect.lastError = "Bouton Envoyer introuvable";
                    ProspectStore.completeCurrent(service, prospect);
                    resetPhase();
                    return true;
                }
                prospect.status = ProspectRecord.SENT;
                DiagnosticLog.event(service, "PROSPECT_SENT", prospect.id);
            } else {
                prospect.status = ProspectRecord.DRAFTED;
                DiagnosticLog.event(service, "PROSPECT_DRAFTED", prospect.id);
            }

            ProspectStore.completeCurrent(service, prospect);
            applyPacingDelay();
            resetPhase();
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return true;
        }

        long openedAt = sp.getLong(Prefs.PROSPECT_OPENED_AT, 0L);
        boolean invalid = containsAny(root,
                "n'est pas sur whatsapp", "n’est pas sur whatsapp",
                "isn't on whatsapp", "is not on whatsapp",
                "numéro de téléphone partagé via une url n'est pas valide",
                "phone number shared via url is invalid",
                "ce numéro n'est pas valide", "invalid phone number");

        if (invalid || (openedAt > 0L && now - openedAt > 9000L)) {
            boolean hasNext = prospect.advancePhone();
            if (hasNext) {
                ProspectStore.saveCurrent(service, prospect);
                resetPhase();
                DiagnosticLog.event(service, "PROSPECT_PHONE_REJECTED", prospect.id);
            } else {
                prospect.status = ProspectRecord.NO_WHATSAPP;
                prospect.lastError = "Aucun numéro WhatsApp valide";
                ProspectStore.completeCurrent(service, prospect);
                resetPhase();
                DiagnosticLog.event(service, "PROSPECT_NO_WHATSAPP", prospect.id);
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return true;
        }

        return true;
    }

    private void openCandidate(ProspectRecord prospect, String phone) {
        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastOpenAt < 1000L) return;
        lastOpenAt = nowElapsed;
        try {
            Uri uri = Uri.parse("https://wa.me/" + phone);
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage(WA_PACKAGE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(i);
            Prefs.p(service).edit()
                    .putString(Prefs.PROSPECT_PHASE, "OPENING")
                    .putString(Prefs.PROSPECT_ACTIVE_PHONE, phone)
                    .putLong(Prefs.PROSPECT_OPENED_AT, System.currentTimeMillis())
                    .putString(Prefs.STATUS, "Test WhatsApp • " + prospect.id)
                    .apply();
            DiagnosticLog.event(service, "PROSPECT_PHONE_OPEN", prospect.id);
        } catch (Throwable t) {
            prospect.lastError = "Ouverture WhatsApp impossible";
            if (!prospect.advancePhone()) {
                prospect.status = ProspectRecord.FAILED;
                ProspectStore.completeCurrent(service, prospect);
            } else {
                ProspectStore.saveCurrent(service, prospect);
            }
            resetPhase();
        }
    }

    private void resetPhase() {
        Prefs.p(service).edit()
                .putString(Prefs.PROSPECT_PHASE, "IDLE")
                .putString(Prefs.PROSPECT_ACTIVE_PHONE, "")
                .putLong(Prefs.PROSPECT_OPENED_AT, 0L)
                .apply();
    }

    private void applyPacingDelay() {
        SharedPreferences sp = Prefs.p(service);
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
                    .putString(Prefs.STATUS, "Base Excel • pause " + minutes + " min");
        } else {
            int seconds = rand(sp.getInt(Prefs.Z_MIN, 25), sp.getInt(Prefs.Z_MAX, 60));
            e.putInt(Prefs.SENT_IN_BATCH, sent)
                    .putInt(Prefs.BATCH_TARGET, target)
                    .putLong(Prefs.NEXT_ACTION_AT, now + seconds * 1000L)
                    .putString(Prefs.STATUS, "Base Excel • prochain dans " + seconds + " s");
        }
        e.apply();
    }

    private int rand(int a, int b) {
        int lo = Math.min(a,b), hi = Math.max(a,b);
        return lo == hi ? lo : lo + random.nextInt(hi - lo + 1);
    }

    private AccessibilityNodeInfo firstById(AccessibilityNodeInfo root, String id) {
        if (root == null) return null;
        try {
            List<AccessibilityNodeInfo> r = root.findAccessibilityNodeInfosByViewId(ID_PREFIX + id);
            return r == null || r.isEmpty() ? null : r.get(0);
        } catch (Throwable ignored) { return null; }
    }

    private boolean setText(AccessibilityNodeInfo editor, String value) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private List<AccessibilityNodeInfo> byText(AccessibilityNodeInfo root, String text) {
        try {
            List<AccessibilityNodeInfo> r = root.findAccessibilityNodeInfosByText(text);
            return r == null ? new ArrayList<>() : r;
        } catch (Throwable ignored) { return new ArrayList<>(); }
    }

    private AccessibilityNodeInfo findTextClickable(AccessibilityNodeInfo root, String... tokens) {
        for (String token : tokens) {
            for (AccessibilityNodeInfo n : byText(root, token)) {
                AccessibilityNodeInfo c = clickableAncestor(n, 8);
                if (c != null) return c;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo n, int max) {
        AccessibilityNodeInfo cur = n;
        for (int i=0; cur != null && i<=max; i++) {
            if (cur.isClickable() && cur.isEnabled()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    private boolean containsAny(AccessibilityNodeInfo root, String... tokens) {
        StringBuilder b = new StringBuilder();
        collect(root, b, 0);
        String all = b.toString().toLowerCase(Locale.ROOT);
        for (String t : tokens) if (all.contains(t.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder b, int depth) {
        if (n == null || depth > 8) return;
        CharSequence t=n.getText(), d=n.getContentDescription();
        if (t != null) b.append(t).append(' ');
        if (d != null) b.append(d).append(' ');
        int count = Math.min(n.getChildCount(), 24);
        for (int i=0;i<count;i++) collect(n.getChild(i), b, depth+1);
    }
}
