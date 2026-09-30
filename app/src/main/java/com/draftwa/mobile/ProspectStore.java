package com.draftwa.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ProspectStore {
    private static final String DB_NAME = "draftwa_prospects.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "prospects";

    // Legacy 0.9.0-dev SharedPreferences keys. Kept only for one-time migration.
    private static final String KEY_DATA = "prospect_queue_json";
    private static final String KEY_INDEX = "prospect_queue_index";
    private static final String KEY_IMPORTED_NAME = "prospect_imported_name";

    private static final String KEY_IMPORT_SOURCE_ROWS = "prospect_import_source_rows";
    private static final String KEY_IMPORT_SKIPPED_EMPTY = "prospect_import_skipped_empty";
    private static final String KEY_IMPORT_SKIPPED_NO_PHONE = "prospect_import_skipped_no_phone";
    private static final String KEY_IMPORT_SKIPPED_INVALID_PHONE = "prospect_import_skipped_invalid_phone";
    private static final String KEY_IMPORT_SKIPPED_MALFORMED = "prospect_import_skipped_malformed";

    private static volatile Helper helper;

    private static Helper helper(Context c) {
        if (helper == null) {
            synchronized (ProspectStore.class) {
                if (helper == null) helper = new Helper(c.getApplicationContext());
            }
        }
        migrateLegacyIfNeeded(c);
        return helper;
    }

    static synchronized void replace(Context c, SpreadsheetImportResult result, String sourceName) {
        replaceInternal(c, result.records, sourceName);
        Prefs.p(c).edit()
                .putInt(KEY_IMPORT_SOURCE_ROWS, result.sourceRows)
                .putInt(KEY_IMPORT_SKIPPED_EMPTY, result.skippedEmpty)
                .putInt(KEY_IMPORT_SKIPPED_NO_PHONE, result.skippedNoPhone)
                .putInt(KEY_IMPORT_SKIPPED_INVALID_PHONE, result.skippedInvalidPhone)
                .putInt(KEY_IMPORT_SKIPPED_MALFORMED, result.skippedMalformed)
                .apply();
    }

    static synchronized void replace(Context c, List<ProspectRecord> records, String sourceName) {
        replaceInternal(c, records, sourceName);
    }

    private static void replaceInternal(Context c, List<ProspectRecord> records, String sourceName) {
        SQLiteDatabase db = helper(c).getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, null, null);
            for (ProspectRecord r : records) insert(db, r);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Prefs.p(c).edit()
                .putInt(KEY_INDEX, 0)
                .putString(KEY_IMPORTED_NAME, sourceName == null ? "" : sourceName)
                .remove(KEY_DATA)
                .apply();
    }

    static synchronized List<ProspectRecord> load(Context c) {
        List<ProspectRecord> out = new ArrayList<>();
        try (Cursor cur = helper(c).getReadableDatabase().query(
                TABLE, columns(), null, null, null, null, "seq ASC")) {
            while (cur.moveToNext()) out.add(fromCursor(cur));
        }
        return out;
    }

    static synchronized ProspectRecord current(Context c) {
        String where = "status=?";
        String[] args = new String[]{ProspectRecord.PENDING};
        try (Cursor cur = helper(c).getReadableDatabase().query(
                TABLE, columns(), where, args, null, null, "seq ASC", "1")) {
            return cur.moveToFirst() ? fromCursor(cur) : null;
        }
    }

    static synchronized int currentIndex(Context c) {
        return completed(c);
    }

    static synchronized void saveCurrent(Context c, ProspectRecord updated) {
        update(c, updated);
    }

    static synchronized void completeCurrent(Context c, ProspectRecord updated) {
        update(c, updated);
    }

    private static void update(Context c, ProspectRecord r) {
        ContentValues v = values(r);
        helper(c).getWritableDatabase().update(TABLE, v, "prospect_id=?", new String[]{r.id});
    }

    static synchronized void clear(Context c) {
        helper(c).getWritableDatabase().delete(TABLE, null, null);
        Prefs.p(c).edit()
                .remove(KEY_DATA)
                .remove(KEY_INDEX)
                .remove(KEY_IMPORTED_NAME)
                .remove(KEY_IMPORT_SOURCE_ROWS)
                .remove(KEY_IMPORT_SKIPPED_EMPTY)
                .remove(KEY_IMPORT_SKIPPED_NO_PHONE)
                .remove(KEY_IMPORT_SKIPPED_INVALID_PHONE)
                .remove(KEY_IMPORT_SKIPPED_MALFORMED)
                .apply();
    }

    static String sourceName(Context c) {
        return Prefs.p(c).getString(KEY_IMPORTED_NAME, "");
    }

    static int total(Context c) {
        return count(c, null, null);
    }

    static int completed(Context c) {
        return count(c, "status<>?", new String[]{ProspectRecord.PENDING});
    }

    static int sent(Context c) {
        return count(c, "status=?", new String[]{ProspectRecord.SENT});
    }

    static int drafted(Context c) {
        return count(c, "status=?", new String[]{ProspectRecord.DRAFTED});
    }

    static int importSourceRows(Context c) {
        return Prefs.p(c).getInt(KEY_IMPORT_SOURCE_ROWS, 0);
    }

    static int importSkipped(Context c) {
        return Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_EMPTY, 0)
                + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_NO_PHONE, 0)
                + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_INVALID_PHONE, 0)
                + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_MALFORMED, 0);
    }

    static String importSkipDetails(Context c) {
        return "Sans contact : " + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_NO_PHONE, 0)
                + " • Téléphone invalide/fixe : " + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_INVALID_PHONE, 0)
                + " • Ligne incomplète/doublon : " + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_MALFORMED, 0)
                + " • Ligne vide : " + Prefs.p(c).getInt(KEY_IMPORT_SKIPPED_EMPTY, 0);
    }

    private static int count(Context c, String where, String[] args) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + (where == null ? "" : " WHERE " + where);
        try (Cursor cur = helper(c).getReadableDatabase().rawQuery(sql, args)) {
            return cur.moveToFirst() ? cur.getInt(0) : 0;
        }
    }

    private static String[] columns() {
        return new String[]{"prospect_id","name","raw_phones","message","status","candidate_index","selected_phone","last_error"};
    }

    private static ProspectRecord fromCursor(Cursor c) {
        ProspectRecord r = new ProspectRecord(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3));
        r.status = c.getString(4);
        r.candidateIndex = Math.max(0, c.getInt(5));
        r.selectedPhone = c.isNull(6) ? "" : c.getString(6);
        r.lastError = c.isNull(7) ? "" : c.getString(7);
        return r;
    }

    private static ContentValues values(ProspectRecord r) {
        ContentValues v = new ContentValues();
        v.put("prospect_id", r.id);
        v.put("name", r.name);
        v.put("raw_phones", r.rawPhones);
        v.put("message", r.message);
        v.put("status", r.status);
        v.put("candidate_index", r.candidateIndex);
        v.put("selected_phone", r.selectedPhone);
        v.put("last_error", r.lastError);
        return v;
    }

    private static void insert(SQLiteDatabase db, ProspectRecord r) {
        db.insertOrThrow(TABLE, null, values(r));
    }

    private static void migrateLegacyIfNeeded(Context c) {
        Helper h = helper;
        if (h == null) return;
        SQLiteDatabase db = h.getWritableDatabase();
        try (Cursor cur = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            if (cur.moveToFirst() && cur.getInt(0) > 0) return;
        }

        String raw = Prefs.p(c).getString(KEY_DATA, "");
        if (raw == null || raw.trim().isEmpty() || "[]".equals(raw.trim())) return;

        try {
            JSONArray a = new JSONArray(raw);
            db.beginTransaction();
            try {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    insert(db, ProspectRecord.fromJson(o));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            Prefs.p(c).edit().remove(KEY_DATA).apply();
        } catch (Throwable ignored) {
            // Leave the legacy payload untouched if migration cannot be completed.
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context c) {
            super(c, DB_NAME, null, DB_VERSION);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "seq INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "prospect_id TEXT NOT NULL UNIQUE,"
                    + "name TEXT,"
                    + "raw_phones TEXT NOT NULL,"
                    + "message TEXT NOT NULL,"
                    + "status TEXT NOT NULL,"
                    + "candidate_index INTEGER NOT NULL DEFAULT 0,"
                    + "selected_phone TEXT,"
                    + "last_error TEXT"
                    + ")");
            db.execSQL("CREATE INDEX idx_prospects_status_seq ON " + TABLE + "(status, seq)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // First SQLite-backed queue version. Future migrations must preserve campaign progress.
        }
    }

    private ProspectStore() {}
}
