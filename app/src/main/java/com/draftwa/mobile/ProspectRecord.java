package com.draftwa.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ProspectRecord {
    static final String PENDING = "PENDING";
    static final String DRAFTED = "DRAFTED";
    static final String SENT = "SENT";
    static final String NO_WHATSAPP = "NO_WHATSAPP";
    static final String FAILED = "FAILED";

    final String id;
    final String name;
    final String rawPhones;
    final String message;
    final List<String> phones;
    String status;
    int candidateIndex;
    String selectedPhone;
    String lastError;

    ProspectRecord(String id, String name, String rawPhones, String message) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "" : name.trim();
        this.rawPhones = rawPhones == null ? "" : rawPhones.trim();
        this.message = message == null ? "" : message;
        this.phones = PhoneNormalizer.candidates(this.rawPhones);
        this.status = PENDING;
        this.candidateIndex = 0;
        this.selectedPhone = "";
        this.lastError = "";
    }

    boolean isComplete() {
        return SENT.equals(status) || DRAFTED.equals(status) || NO_WHATSAPP.equals(status) || FAILED.equals(status);
    }

    String currentPhone() {
        return candidateIndex >= 0 && candidateIndex < phones.size() ? phones.get(candidateIndex) : "";
    }

    boolean advancePhone() {
        candidateIndex++;
        return candidateIndex < phones.size();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("rawPhones", rawPhones);
        o.put("message", message);
        o.put("status", status);
        o.put("candidateIndex", candidateIndex);
        o.put("selectedPhone", selectedPhone);
        o.put("lastError", lastError);
        JSONArray p = new JSONArray();
        for (String v : phones) p.put(v);
        o.put("phones", p);
        return o;
    }

    static ProspectRecord fromJson(JSONObject o) throws JSONException {
        ProspectRecord r = new ProspectRecord(
                o.optString("id", ""),
                o.optString("name", ""),
                o.optString("rawPhones", ""),
                o.optString("message", ""));
        r.phones.clear();
        JSONArray a = o.optJSONArray("phones");
        if (a != null) for (int i = 0; i < a.length(); i++) r.phones.add(a.optString(i, ""));
        r.status = o.optString("status", PENDING);
        r.candidateIndex = Math.max(0, o.optInt("candidateIndex", 0));
        r.selectedPhone = o.optString("selectedPhone", "");
        r.lastError = o.optString("lastError", "");
        return r;
    }
}
