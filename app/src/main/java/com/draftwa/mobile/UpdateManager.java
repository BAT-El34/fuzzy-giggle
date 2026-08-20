package com.draftwa.mobile;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateManager {
    interface Callback {
        void onStatus(String status, UpdateInfo info);
    }

    static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;
        final boolean mandatory;
        final String releaseNotes;

        UpdateInfo(long versionCode, String versionName, String apkUrl, String sha256,
                   boolean mandatory, String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.mandatory = mandatory;
            this.releaseNotes = releaseNotes;
        }
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    static void checkAsync(Activity activity, Callback callback, boolean autoDownload) {
        EXEC.execute(() -> {
            try {
                UpdateInfo info = fetchManifest();
                long current = currentVersionCode(activity);
                if (info.versionCode <= current) {
                    post(activity, callback, "À jour • v" + BuildConfig.VERSION_NAME, null);
                    return;
                }
                if (info.sha256 == null || !info.sha256.matches("(?i)[0-9a-f]{64}")) {
                    DiagnosticLog.event(activity, "UPDATE_MANIFEST_REJECTED", "SHA-256 absent/invalide");
                    post(activity, callback, "Mise à jour ignorée • manifeste incomplet", null);
                    return;
                }
                String msg = "Mise à jour disponible • v" + info.versionName;
                post(activity, callback, msg, info);
                DiagnosticLog.event(activity, "UPDATE_AVAILABLE", "v" + info.versionName);
                if (autoDownload) downloadAndInstall(activity, info, callback);
            } catch (Throwable t) {
                DiagnosticLog.event(activity, "UPDATE_CHECK_FAILED", t.getClass().getSimpleName() + ": " + t.getMessage());
                post(activity, callback, "Vérification impossible (hors ligne ?) ", null);
            }
        });
    }

    static void downloadAndInstall(Activity activity, UpdateInfo info, Callback callback) {
        EXEC.execute(() -> {
            try {
                if (info == null) throw new IllegalArgumentException("Aucune mise à jour sélectionnée");
                post(activity, callback, "Téléchargement v" + info.versionName + "…", info);
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "DraftWA-latest.apk");
                download(info.apkUrl, apk);

                String actualSha = sha256(apk);
                if (info.sha256 == null || info.sha256.length() < 32 || !actualSha.equalsIgnoreCase(info.sha256)) {
                    throw new SecurityException("SHA-256 invalide");
                }
                verifyArchiveIdentity(activity, apk);
                DiagnosticLog.event(activity, "UPDATE_VERIFIED", actualSha);

                if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                    Prefs.p(activity).edit().putBoolean(Prefs.PENDING_INSTALL_PERMISSION, true).apply();
                    post(activity, callback, "Autorise DraftWA à installer les mises à jour", info);
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(settings);
                    return;
                }

                Prefs.p(activity).edit().putBoolean(Prefs.PENDING_INSTALL_PERMISSION, false).apply();
                installWithPackageInstaller(activity, apk);
                post(activity, callback, "Android va proposer « Mettre à jour »", info);
            } catch (Throwable t) {
                DiagnosticLog.event(activity, "UPDATE_INSTALL_FAILED", t.getClass().getSimpleName() + ": " + t.getMessage());
                post(activity, callback, "Échec mise à jour : " + safe(t.getMessage()), info);
            }
        });
    }

    private static UpdateInfo fetchManifest() throws Exception {
        URL manifestUrl = new URL(BuildConfig.UPDATE_MANIFEST_URL);
        if (!"https".equalsIgnoreCase(manifestUrl.getProtocol())) throw new SecurityException("Manifeste non HTTPS");
        HttpURLConnection c = (HttpURLConnection) manifestUrl.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        c.setUseCaches(false);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        String json;
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            json = new String(readAll(in), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
        JSONObject o = new JSONObject(json);
        String rawApkUrl = o.getString("apkUrl");
        String resolvedApkUrl = new URL(manifestUrl, rawApkUrl).toString();
        return new UpdateInfo(
                o.getLong("versionCode"),
                o.getString("versionName"),
                resolvedApkUrl,
                o.optString("sha256", ""),
                o.optBoolean("mandatory", false),
                o.optString("releaseNotes", "")
        );
    }

    private static void download(String url, File target) throws Exception {
        URL parsed = new URL(url);
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) throw new SecurityException("URL APK non HTTPS");
        HttpURLConnection c = (HttpURLConnection) parsed.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Téléchargement HTTP " + code);
        if (!"https".equalsIgnoreCase(c.getURL().getProtocol())) throw new SecurityException("Redirection APK non HTTPS");
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buf = new byte[32 * 1024];
            int n;
            long total = 0;
            final long maxBytes = 25L * 1024L * 1024L;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) throw new SecurityException("APK de mise à jour trop volumineuse");
                out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }

    private static void verifyArchiveIdentity(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new SecurityException("APK illisible");
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException("Package inattendu : " + archive.packageName);
        }
        PackageInfo current = pm.getPackageInfo(context.getPackageName(), flags);
        String a = signerSha(current);
        String b = signerSha(archive);
        if (a == null || b == null || !a.equalsIgnoreCase(b)) {
            throw new SecurityException("Signature release différente");
        }
        if (packageVersionCode(archive) <= packageVersionCode(current)) {
            throw new SecurityException("Version non supérieure");
        }
    }

    private static String signerSha(PackageInfo p) throws Exception {
        Signature sig = null;
        if (Build.VERSION.SDK_INT >= 28 && p.signingInfo != null) {
            Signature[] s = p.signingInfo.hasMultipleSigners()
                    ? p.signingInfo.getApkContentsSigners()
                    : p.signingInfo.getSigningCertificateHistory();
            if (s != null && s.length > 0) sig = s[0];
        } else if (p.signatures != null && p.signatures.length > 0) {
            sig = p.signatures[0];
        }
        if (sig == null) return null;
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        byte[] h = d.digest(sig.toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte x : h) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private static void installWithPackageInstaller(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try (InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("DraftWA.apk", 0, apk.length())) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            session.fsync(out);
        }
        Intent result = new Intent(context, UpdateInstallReceiver.class)
                .setAction("com.draftwa.mobile.UPDATE_INSTALL_RESULT");
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) piFlags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, id, result, piFlags);
        session.commit(pending.getIntentSender());
        session.close();
    }

    private static long currentVersionCode(Context c) throws Exception {
        return packageVersionCode(c.getPackageManager().getPackageInfo(c.getPackageName(), 0));
    }

    private static long packageVersionCode(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) d.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : d.digest()) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private static void post(Activity a, Callback cb, String status, UpdateInfo info) {
        a.runOnUiThread(() -> cb.onStatus(status, info));
    }

    private static String safe(String s) {
        return s == null || s.trim().isEmpty() ? "erreur inconnue" : s;
    }

    private UpdateManager() {}
}
