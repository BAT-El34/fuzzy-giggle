package com.draftwa.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ProspectStore {
    private static final String KEY_DATA = "prospect_queue_json";
    private static final String KEY_INDEX = "prospect_queue_index";
    private static final String KEY_IMPORTED_NAME = "prospect_imported_name";

    static synchronized void replace(Context c, List<ProspectRecord> records, String sourceName) {
        JSONArray a = new JSONArray();
        try {
            for (ProspectRecord r : records) a.put(r.toJson());
        } catch (Throwable t) {
            throw new IllegalStateException("Impossible d'enregistrer la base importée", t);
        }
        Prefs.p(c).edit()
                .putString(KEY_DATA, a.toString())
                .putInt(KEY_INDEX, 0)
                .putString(KEY_IMPORTED_NAME, sourceName == null ? "" : sourceName)
                .apply();
    }

    static synchronized List<ProspectRecord> load(Context c) {
        List<ProspectRecord> out = new ArrayList<>();
        String raw = Prefs.p(c).getString(KEY_DATA, "[]");
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) out.add(ProspectRecord.fromJson(a.getJSONObject(i)));
        } catch (Throwable ignored) {}
        return out;
    }

    static synchronized ProspectRecord current(Context c) {
        List<ProspectRecord> all = load(c);
        int idx = Math.max(0, Prefs.p(c).getInt(KEY_INDEX, 0));
        while (idx < all.size() && all.get(idx).isComplete()) idx++;
        if (idx != Prefs.p(c).getInt(KEY_INDEX, 0)) Prefs.p(c).edit().putInt(KEY_INDEX, idx).apply();
        return idx < all.size() ? all.get(idx) : null;
    }

    static synchronized int currentIndex(Context c) {
        return Math.max(0, Prefs.p(c).getInt(KEY_INDEX, 0));
    }

    static synchronized void saveCurrent(Context c, ProspectRecord updated) {
        List<ProspectRecord> all = load(c);
        int idx = Math.max(0, Prefs.p(c).getInt(KEY_INDEX, 0));
        if (idx >= all.size()) return;
        all.set(idx, updated);
        saveAll(c, all);
    }

    static synchronized void completeCurrent(Context c, ProspectRecord updated) {
        List<ProspectRecord> all = load(c);
        int idx = Math.max(0, Prefs.p(c).getInt(KEY_INDEX, 0));
        if (idx < all.size()) all.set(idx, updated);
        int next = idx + 1;
        while (next < all.size() && all.get(next).isComplete()) next++;
        saveAll(c, all);
        Prefs.p(c).edit().putInt(KEY_INDEX, next).apply();
    }

    private static void saveAll(Context c, List<ProspectRecord> all) {
        JSONArray a = new JSONArray();
        try {
            for (ProspectRecord r : all) a.put(r.toJson());
            Prefs.p(c).edit().putString(KEY_DATA, a.toString()).apply();
        } catch (Throwable t) {
            throw new IllegalStateException("Impossible de sauvegarder la progression", t);
        }
    }

    static synchronized void clear(Context c) {
        Prefs.p(c).edit().remove(KEY_DATA).remove(KEY_INDEX).remove(KEY_IMPORTED_NAME).apply();
    }

    static String sourceName(Context c) {
        return Prefs.p(c).getString(KEY_IMPORTED_NAME, "");
    }

    static int total(Context c) { return load(c).size(); }

    static int completed(Context c) {
        int n = 0;
        for (ProspectRecord r : load(c)) if (r.isComplete()) n++;
        return n;
    }

    static int sent(Context c) {
        int n = 0;
        for (ProspectRecord r : load(c)) if (ProspectRecord.SENT.equals(r.status)) n++;
        return n;
    }

    static int drafted(Context c) {
        int n = 0;
        for (ProspectRecord r : load(c)) if (ProspectRecord.DRAFTED.equals(r.status)) n++;
        return n;
    }

    private ProspectStore() {}
}
