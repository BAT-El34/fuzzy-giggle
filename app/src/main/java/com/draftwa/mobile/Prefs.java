package com.draftwa.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class Prefs {
    static final String NAME = "draftwa_mobile_v070";
    private static final String LEGACY_NAME = "draftwa_mobile";

    static final String CONDITION = "condition";
    static final String REMOVE = "remove";
    static final String REJECT_MODE = "reject_mode";
    static final String GREETING_ENABLED = "greeting_enabled";
    static final String TIMEZONE = "timezone";
    static final String DAY_HOUR = "day_hour";
    static final String EVENING_HOUR = "evening_hour";
    static final String Y_MIN = "y_min";
    static final String Y_MAX = "y_max";
    static final String Z_MIN = "z_min";
    static final String Z_MAX = "z_max";
    static final String X_MIN = "x_min";
    static final String X_MAX = "x_max";
    static final String RUNNING = "running";
    static final String PAUSED = "paused";
    static final String DIAGNOSTIC = "diagnostic";
    static final String AUTO_UPDATE = "auto_update";
    static final String AUTO_DOWNLOAD = "auto_download";
    static final String PENDING_INSTALL_PERMISSION = "pending_install_permission";
    static final String PENDING_DIAGNOSTIC_START = "pending_diagnostic_start";
    static final String STATUS = "status";
    static final String BATCH_TARGET = "batch_target";
    static final String SENT_IN_BATCH = "sent_in_batch";
    static final String NEXT_ACTION_AT = "next_action_at";
    static final String CHATS_TAB_PROBED = "chats_tab_probed";
    static final String FILTER_PROBED = "filter_probed";
    static final String FILTER_MENU_PROBED = "filter_menu_probed";
    static final String ALL_FILTER_PROBED = "all_filter_probed";
    static final String SCROLL_COUNT = "scroll_count";
    static final String SCAN_PAGE = "scan_page";
    static final String LAST_VIEWPORT = "last_viewport";
    static final String SAME_VIEWPORT_COUNT = "same_viewport_count";
    static final String SCROLL_STALL_COUNT = "scroll_stall_count";
    static final String LIST_MISSING_COUNT = "list_missing_count";
    static final String SKIPPED = "skipped";
    static final String LEGACY_MIGRATED = "legacy_migrated";

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static void ensureDefaults(Context c) {
        SharedPreferences sp = p(c);
        migrateLegacy(c, sp);
        SharedPreferences.Editor e = sp.edit();
        if (!sp.contains(CONDITION)) e.putString(CONDITION, "Soko");
        if (!sp.contains(REMOVE)) e.putString(REMOVE, ",Enregistré,");
        if (!sp.contains(REJECT_MODE)) e.putString(REJECT_MODE, "Conserver");
        if (!sp.contains(GREETING_ENABLED)) e.putBoolean(GREETING_ENABLED, true);
        if (!sp.contains(TIMEZONE)) e.putString(TIMEZONE, "Africa/Abidjan");
        if (!sp.contains(DAY_HOUR)) e.putInt(DAY_HOUR, 5);
        if (!sp.contains(EVENING_HOUR)) e.putInt(EVENING_HOUR, 18);
        if (!sp.contains(Y_MIN)) e.putInt(Y_MIN, 3);
        if (!sp.contains(Y_MAX)) e.putInt(Y_MAX, 5);
        if (!sp.contains(Z_MIN)) e.putInt(Z_MIN, 25);
        if (!sp.contains(Z_MAX)) e.putInt(Z_MAX, 60);
        if (!sp.contains(X_MIN)) e.putInt(X_MIN, 5);
        if (!sp.contains(X_MAX)) e.putInt(X_MAX, 12);
        if (!sp.contains(DIAGNOSTIC)) e.putBoolean(DIAGNOSTIC, true);
        if (!sp.contains(AUTO_UPDATE)) e.putBoolean(AUTO_UPDATE, true);
        if (!sp.contains(AUTO_DOWNLOAD)) e.putBoolean(AUTO_DOWNLOAD, true);
        if (!sp.contains(RUNNING)) e.putBoolean(RUNNING, false);
        if (!sp.contains(PAUSED)) e.putBoolean(PAUSED, false);
        if (!sp.contains(PENDING_DIAGNOSTIC_START)) e.putBoolean(PENDING_DIAGNOSTIC_START, false);
        if (!sp.contains(STATUS)) e.putString(STATUS, "Prêt");
        e.apply();
    }

    private static void migrateLegacy(Context c, SharedPreferences current) {
        if (current.getBoolean(LEGACY_MIGRATED, false)) return;
        SharedPreferences legacy = c.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = legacy.getAll();
        SharedPreferences.Editor e = current.edit();
        if (!all.isEmpty()) {
            putStringIfPresent(e, current, CONDITION, all, "draft_condition_raw");
            putStringIfPresent(e, current, REMOVE, all, "draft_removal_raw");
            putStringIfPresent(e, current, REJECT_MODE, all, "draft_nonconform_action");
            putStringIfPresent(e, current, TIMEZONE, all, "draft_timezone");
            putBooleanIfPresent(e, current, GREETING_ENABLED, all, "draft_greeting");
            putIntIfPresent(e, current, DAY_HOUR, all, "draft_day_raw", "draft_day");
            putIntIfPresent(e, current, EVENING_HOUR, all, "draft_eve_raw", "draft_eve");
            putIntIfPresent(e, current, Y_MIN, all, "draft_ymin_raw", "draft_ymin");
            putIntIfPresent(e, current, Y_MAX, all, "draft_ymax_raw", "draft_ymax");
            putIntIfPresent(e, current, Z_MIN, all, "draft_zmin_raw", "draft_zmin");
            putIntIfPresent(e, current, Z_MAX, all, "draft_zmax_raw", "draft_zmax");
            putIntIfPresent(e, current, X_MIN, all, "draft_xmin_raw", "draft_xmin");
            putIntIfPresent(e, current, X_MAX, all, "draft_xmax_raw", "draft_xmax");
            e.putString(STATUS, "Configuration 0.6.x migrée • diagnostic activé");
            e.putBoolean(RUNNING, false);
            e.putBoolean(PAUSED, false);
            e.putBoolean(DIAGNOSTIC, true);
            DiagnosticLog.event(c, "LEGACY_PREFS_MIGRATED", "draftwa_mobile");
        }
        e.putBoolean(LEGACY_MIGRATED, true).apply();
    }

    private static void putStringIfPresent(SharedPreferences.Editor e, SharedPreferences current,
                                           String target, Map<String, ?> all, String source) {
        if (current.contains(target) || !all.containsKey(source)) return;
        Object value = all.get(source);
        if (value != null) e.putString(target, String.valueOf(value));
    }

    private static void putBooleanIfPresent(SharedPreferences.Editor e, SharedPreferences current,
                                            String target, Map<String, ?> all, String source) {
        if (current.contains(target) || !all.containsKey(source)) return;
        Object value = all.get(source);
        if (value instanceof Boolean) e.putBoolean(target, (Boolean) value);
        else if (value != null) e.putBoolean(target, Boolean.parseBoolean(String.valueOf(value)));
    }

    private static void putIntIfPresent(SharedPreferences.Editor e, SharedPreferences current,
                                        String target, Map<String, ?> all, String... sources) {
        if (current.contains(target)) return;
        for (String source : sources) {
            Object value = all.get(source);
            if (value == null) continue;
            try {
                int parsed = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value).trim());
                e.putInt(target, parsed);
                return;
            } catch (Throwable ignored) {}
        }
    }

    static void resetRunState(Context c) {
        SharedPreferences sp = p(c);
        sp.edit()
                .putBoolean(CHATS_TAB_PROBED, false)
                .putBoolean(FILTER_PROBED, false)
                .putBoolean(FILTER_MENU_PROBED, false)
                .putBoolean(ALL_FILTER_PROBED, false)
                .putInt(SCROLL_COUNT, 0)
                .putInt(SCAN_PAGE, 1)
                .putString(LAST_VIEWPORT, "")
                .putInt(SAME_VIEWPORT_COUNT, 0)
                .putInt(SCROLL_STALL_COUNT, 0)
                .putInt(LIST_MISSING_COUNT, 0)
                .putInt(SENT_IN_BATCH, 0)
                .putInt(BATCH_TARGET, 0)
                .putLong(NEXT_ACTION_AT, 0L)
                .putStringSet(SKIPPED, new HashSet<>())
                .apply();
    }

    static Set<String> skipped(Context c) {
        Set<String> s = p(c).getStringSet(SKIPPED, Collections.emptySet());
        return new HashSet<>(s == null ? Collections.emptySet() : s);
    }

    static void addSkipped(Context c, String value) {
        if (value == null || value.isEmpty()) return;
        Set<String> s = skipped(c);
        s.add(value);
        p(c).edit().putStringSet(SKIPPED, s).apply();
    }

    private Prefs() {}
}