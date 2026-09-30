package com.draftwa.mobile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PhoneNormalizer {
    private static final String DEFAULT_COUNTRY = "228";

    static List<String> candidates(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) return new ArrayList<>();
        String[] parts = raw.split("/");
        for (String part : parts) {
            String digits = part == null ? "" : part.replaceAll("[^0-9]", "");
            if (digits.startsWith("00") && digits.length() > 2) digits = digits.substring(2);
            while (digits.startsWith("0") && digits.length() > 8) digits = digits.substring(1);
            if (digits.length() == 8) digits = DEFAULT_COUNTRY + digits;
            if (digits.length() < 8 || digits.length() > 15) continue;
            if (looksFixedLine(digits)) continue;
            out.add(digits);
        }
        return new ArrayList<>(out);
    }

    static boolean looksFixedLine(String normalized) {
        if (normalized == null) return false;
        String n = normalized.replaceAll("[^0-9]", "");
        String national = n.startsWith(DEFAULT_COUNTRY) && n.length() > DEFAULT_COUNTRY.length()
                ? n.substring(DEFAULT_COUNTRY.length()) : n;
        // Togo fixed lines are generally in the 22 range. "222..." is also
        // explicitly excluded to avoid wasting WhatsApp probes.
        return national.startsWith("22") || n.startsWith("222");
    }

    static String display(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return String.join(" / ", values).toLowerCase(Locale.ROOT);
    }

    private PhoneNormalizer() {}
}
