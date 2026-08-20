package com.draftwa.mobile;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageRules {
    static final class Pair {
        final String left;
        final String right;
        Pair(String left, String right) { this.left = left; this.right = right; }
    }

    static Pair splitRule(String raw) {
        String s = raw == null ? "" : raw.trim();
        int i = s.indexOf(';');
        if (i < 0) return new Pair(s, "");
        return new Pair(s.substring(0, i).trim(), s.substring(i + 1).trim());
    }

    static boolean matches(String message, String condition) {
        String msg = message == null ? "" : message;
        Pair p = splitRule(condition);
        boolean containsOk = p.left.isEmpty() || containsIgnoreCase(msg, p.left);
        boolean endsOk = p.right.isEmpty() || endsWithIgnoreCase(msg.trim(), p.right);
        return containsOk && endsOk;
    }

    static String transform(String message, String removalRule, boolean greetingEnabled,
                            String timezone, int dayHour, int eveningHour) {
        String out = message == null ? "" : message;
        Pair p = splitRule(removalRule);
        if (!p.left.isEmpty()) out = replaceAllIgnoreCase(out, p.left, "");
        if (!p.right.isEmpty()) out = removeSuffixIgnoreCase(out, p.right);
        if (greetingEnabled) out = adaptGreeting(out, timezone, dayHour, eveningHour);
        return normalizeSpacing(out);
    }

    private static String adaptGreeting(String s, String timezone, int dayHour, int eveningHour) {
        try {
            ZoneId zone = ZoneId.of(timezone == null || timezone.isEmpty() ? "Africa/Abidjan" : timezone);
            int hour = ZonedDateTime.now(zone).getHour();
            boolean day;
            if (dayHour <= eveningHour) day = hour >= dayHour && hour < eveningHour;
            else day = hour >= dayHour || hour < eveningHour;
            String wanted = day ? "Bonjour" : "Bonsoir";
            Pattern p = Pattern.compile("^(bonjour|bonsoir)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(s);
            if (m.find()) return wanted + s.substring(m.end());
            return s;
        } catch (Throwable ignored) {
            return s;
        }
    }

    private static String normalizeSpacing(String s) {
        return s.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("[ \t]*\\n[ \t]*", "\n")
                .trim();
    }

    private static boolean containsIgnoreCase(String a, String b) {
        return a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
    }

    private static boolean endsWithIgnoreCase(String a, String b) {
        return a.toLowerCase(Locale.ROOT).endsWith(b.toLowerCase(Locale.ROOT));
    }

    private static String replaceAllIgnoreCase(String source, String target, String replacement) {
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String removeSuffixIgnoreCase(String source, String suffix) {
        String trimmed = source.trim();
        if (!endsWithIgnoreCase(trimmed, suffix)) return source;
        return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
    }

    private MessageRules() {}
}
