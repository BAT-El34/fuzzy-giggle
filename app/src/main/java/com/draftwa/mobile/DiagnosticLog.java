package com.draftwa.mobile;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLog {
    private static final String TAG = "DraftWA";
    private static final long MAX_BYTES = 256 * 1024;

    static synchronized void event(Context c, String code, String detail) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + " | " + code + (detail == null || detail.isEmpty() ? "" : " | " + detail) + "\n";
        Log.i(TAG, line.trim());
        try {
            File dir = dir(c);
            File f = new File(dir, "draftwa.log");
            if (f.exists() && f.length() > MAX_BYTES) {
                File old = new File(dir, "draftwa.previous.log");
                if (old.exists()) old.delete();
                f.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to persist diagnostic log", t);
        }
    }

    static synchronized String read(Context c) {
        try {
            File d = dir(c);
            StringBuilder out = new StringBuilder();
            append(out, new File(d, "draftwa.previous.log"));
            append(out, new File(d, "draftwa.log"));
            return out.length() == 0 ? "Aucun diagnostic enregistré." : out.toString();
        } catch (Throwable t) {
            return "Lecture des diagnostics impossible : " + t.getClass().getSimpleName();
        }
    }

    /**
     * User-shareable diagnostic view. Details are intentionally removed because
     * some internal events can contain a conversation fingerprint or UI text.
     */
    static synchronized String safeSummary(Context c) {
        String raw = read(c);
        if (raw.startsWith("Aucun diagnostic") || raw.startsWith("Lecture des diagnostics impossible")) return raw;
        String[] lines = raw.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, lines.length - 120);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.split(" \\| ", 3);
            if (parts.length >= 2) {
                out.append(parts[0]).append(" | ").append(parts[1]).append('\n');
            }
        }
        return out.length() == 0 ? "Aucun diagnostic exploitable." : out.toString();
    }

    static synchronized void clear(Context c) {
        File d = dir(c);
        new File(d, "draftwa.log").delete();
        new File(d, "draftwa.previous.log").delete();
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "diagnostics");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static void append(StringBuilder out, File file) throws Exception {
        if (!file.exists()) return;
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
            out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private DiagnosticLog() {}
}
