package com.draftwa.mobile;

import android.content.Context;

import org.json.JSONObject;

final class CampaignStats {
    static JSONObject json(Context c) {
        JSONObject o = new JSONObject();
        try {
            int total = ProspectStore.total(c);
            int completed = ProspectStore.completed(c);
            int sent = ProspectStore.sent(c);
            int drafted = ProspectStore.drafted(c);
            o.put("total", total);
            o.put("completed", completed);
            o.put("pending", Math.max(0, total - completed));
            o.put("sent", sent);
            o.put("drafted", drafted);
            o.put("source", ProspectStore.sourceName(c));
            o.put("running", Prefs.p(c).getBoolean(Prefs.RUNNING, false));
            o.put("paused", Prefs.p(c).getBoolean(Prefs.PAUSED, false));
            o.put("status", Prefs.p(c).getString(Prefs.STATUS, "Prêt"));
        } catch (Throwable ignored) {}
        return o;
    }

    static int percent(Context c) {
        int total = ProspectStore.total(c);
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100, Math.round(ProspectStore.completed(c) * 100f / total)));
    }

    private CampaignStats() {}
}
