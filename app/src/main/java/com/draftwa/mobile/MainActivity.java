package com.draftwa.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText condition, remove, timezone;
    private EditText yMin, yMax, zMin, zMax, xMin, xMax, dayHour, eveningHour;
    private CheckBox greeting, diagnostic, autoUpdate, autoDownload, destroyRejected;
    private TextView status, readiness, updateStatus;
    private LinearLayout advancedContainer;
    private Button advancedToggle;
    private UpdateManager.UpdateInfo pendingUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaults(this);
        DiagnosticLog.event(this, "APP_OPEN", "v" + BuildConfig.VERSION_NAME);
        setContentView(buildUi());
        loadSettings();
        refreshStatus();
        if (Prefs.p(this).getBoolean(Prefs.AUTO_UPDATE, true)) {
            checkUpdates(Prefs.p(this).getBoolean(Prefs.AUTO_DOWNLOAD, true));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        SharedPreferences sp = Prefs.p(this);

        if (sp.getBoolean(Prefs.PENDING_INSTALL_PERMISSION, false)) {
            boolean allowed = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
            if (allowed) {
                sp.edit().putBoolean(Prefs.PENDING_INSTALL_PERMISSION, false).apply();
                if (pendingUpdate != null) UpdateManager.downloadAndInstall(this, pendingUpdate, this::onUpdateStatus);
                else checkUpdates(true);
            }
        }

        if (sp.getBoolean(Prefs.PENDING_DIAGNOSTIC_START, false)) {
            sp.edit().putBoolean(Prefs.PENDING_DIAGNOSTIC_START, false).apply();
            if (isAccessibilityEnabled()) {
                diagnostic.setChecked(true);
                DiagnosticLog.event(this, "GUIDED_DIAGNOSTIC_RESUME", "accessibility-ready");
                startAutomation();
            } else {
                DiagnosticLog.event(this, "GUIDED_DIAGNOSTIC_CANCELLED", "accessibility-disabled");
                Toast.makeText(this, "Accessibilité non activée. Le test n’a pas démarré.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(32));
        root.setBackgroundColor(Color.rgb(245, 247, 248));
        scroll.addView(root);

        TextView title = text("DraftWA Mobile", 29, true);
        title.setTextColor(Color.rgb(17, 27, 33));
        root.addView(title);
        TextView sub = text("v" + BuildConfig.VERSION_NAME + " • WhatsApp Business + Prospection serveur • hors ligne pour le moteur de brouillons", 14, false);
        sub.setTextColor(Color.rgb(84, 101, 111));
        root.addView(sub);

        status = text("", 15, true);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams statusLp = lpMatch();
        statusLp.setMargins(0, dp(16), 0, dp(8));
        root.addView(status, statusLp);

        readiness = text("", 13, false);
        readiness.setTextColor(Color.rgb(84, 101, 111));
        readiness.setPadding(dp(4), dp(4), dp(4), dp(8));
        root.addView(readiness, lpMatch());

        TextView quickHelp = text("Premier test recommandé : DraftWA transforme puis restaure le brouillon sans appuyer sur Envoyer.", 13, false);
        quickHelp.setTextColor(Color.rgb(84, 101, 111));
        root.addView(quickHelp, lpMatch());

        Button quickDiagnostic = primaryButton("Tester en diagnostic");
        quickDiagnostic.setOnClickListener(v -> startGuidedDiagnostic());
        root.addView(quickDiagnostic, lpMatchWithTop(10));

        Button quickAccessibility = button("Ouvrir les réglages d’accessibilité");
        quickAccessibility.setOnClickListener(v -> openAccessibilitySettings(false));
        root.addView(quickAccessibility, lpMatchWithTop(6));

        LinearLayout runActions = horizontal(root);
        Button start = button("Démarrer");
        start.setOnClickListener(v -> startAutomation());
        runActions.addView(start, weighted());
        Button stop = button("Pause");
        stop.setOnClickListener(v -> stopAutomation());
        runActions.addView(stop, weighted());

        section(root, "Prospection cartographique");
        TextView opportunityHelp = text("Nouveau : recherche d’entreprises et d’opportunités avec rayon, filtres, scoring et exports. Les calculs lourds s’exécutent sur le backend ; cette section nécessite Internet.", 13, false);
        opportunityHelp.setTextColor(Color.rgb(84, 101, 111));
        root.addView(opportunityHelp, lpMatch());
        Button opportunity = primaryButton("Ouvrir Carte & Prospection");
        opportunity.setOnClickListener(v -> openOpportunityModule());
        root.addView(opportunity, lpMatchWithTop(8));

        section(root, "Réglages essentiels");
        condition = field(root, "Texte de confirmation", "Ex. Soko ou Soko ; Allo.", false);
        remove = field(root, "Retraitement / suppression", "Ex. ,Enregistré,", false);

        advancedToggle = button("Afficher les réglages avancés");
        advancedToggle.setOnClickListener(v -> toggleAdvanced());
        root.addView(advancedToggle, lpMatchWithTop(12));

        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        root.addView(advancedContainer, lpMatch());

        section(advancedContainer, "Règles complémentaires");
        destroyRejected = check(advancedContainer, "Détruire les brouillons non conformes (sinon Conserver)");

        section(advancedContainer, "Salutation");
        greeting = check(advancedContainer, "Adapter automatiquement Bonjour / Bonsoir");
        timezone = field(advancedContainer, "Fuseau horaire", "Africa/Abidjan", false);
        LinearLayout hours = horizontal(advancedContainer);
        dayHour = compactField(hours, "Jour", "5");
        eveningHour = compactField(hours, "Soir", "18");

        section(advancedContainer, "Rythme");
        LinearLayout batch = horizontal(advancedContainer);
        yMin = compactField(batch, "Lot min", "3");
        yMax = compactField(batch, "Lot max", "5");
        LinearLayout msgWait = horizontal(advancedContainer);
        zMin = compactField(msgWait, "Msg min (s)", "25");
        zMax = compactField(msgWait, "Msg max (s)", "60");
        LinearLayout batchWait = horizontal(advancedContainer);
        xMin = compactField(batchWait, "Pause min (min)", "5");
        xMax = compactField(batchWait, "Pause max (min)", "12");

        section(advancedContainer, "Test et sécurité");
        diagnostic = check(advancedContainer, "Mode diagnostic : ne pas appuyer sur Envoyer");
        TextView diagHelp = text("Désactive ce mode uniquement après validation sur ton téléphone.", 13, false);
        diagHelp.setTextColor(Color.rgb(84, 101, 111));
        advancedContainer.addView(diagHelp);

        section(advancedContainer, "Mises à jour");
        autoUpdate = check(advancedContainer, "Vérifier automatiquement au démarrage");
        autoDownload = check(advancedContainer, "Télécharger automatiquement si une version est disponible");
        updateStatus = text("", 14, false);
        updateStatus.setTextColor(Color.rgb(84, 101, 111));
        advancedContainer.addView(updateStatus);

        LinearLayout updates = horizontal(advancedContainer);
        Button check = button("Rechercher");
        check.setOnClickListener(v -> { if (saveSettings()) checkUpdates(false); });
        updates.addView(check, weighted());
        Button install = button("Télécharger / Mettre à jour");
        install.setOnClickListener(v -> {
            if (!saveSettings()) return;
            if (pendingUpdate == null) checkUpdates(true);
            else UpdateManager.downloadAndInstall(this, pendingUpdate, this::onUpdateStatus);
        });
        updates.addView(install, weighted());

        Button save = button("Enregistrer la configuration");
        save.setOnClickListener(v -> {
            if (saveSettings()) Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
        });
        advancedContainer.addView(save, lpMatchWithTop(12));

        section(root, "Diagnostic technique");
        TextView privacy = text("Le partage ci-dessous retire volontairement les détails des événements afin de ne pas exposer le texte des brouillons.", 12, false);
        privacy.setTextColor(Color.rgb(84, 101, 111));
        root.addView(privacy);
        LinearLayout diagActions = horizontal(root);
        Button share = button("Partager le diagnostic");
        share.setOnClickListener(v -> shareSafeDiagnostics());
        diagActions.addView(share, weighted());
        Button clear = button("Effacer le journal");
        clear.setOnClickListener(v -> {
            DiagnosticLog.clear(this);
            DiagnosticLog.event(this, "DIAGNOSTICS_CLEARED", "user");
            Toast.makeText(this, "Journal diagnostic effacé", Toast.LENGTH_SHORT).show();
        });
        diagActions.addView(clear, weighted());

        return scroll;
    }

    private void openOpportunityModule() {
        try {
            startActivity(new Intent(this, OpportunityActivity.class));
            DiagnosticLog.event(this, "OPPORTUNITY_ACTIVITY_LAUNCH", "ok");
        } catch (Throwable t) {
            DiagnosticLog.event(this, "OPPORTUNITY_ACTIVITY_FAILED", t.getClass().getSimpleName());
            Toast.makeText(this, "Impossible d’ouvrir la prospection", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleAdvanced() {
        boolean show = advancedContainer.getVisibility() != View.VISIBLE;
        advancedContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        advancedToggle.setText(show ? "Masquer les réglages avancés" : "Afficher les réglages avancés");
    }

    private void startGuidedDiagnostic() {
        diagnostic.setChecked(true);
        if (!saveSettings()) return;
        if (!isWhatsAppBusinessInstalled()) {
            refuseMissingWhatsApp();
            return;
        }
        if (!isAccessibilityEnabled()) {
            Prefs.p(this).edit().putBoolean(Prefs.PENDING_DIAGNOSTIC_START, true).apply();
            DiagnosticLog.event(this, "GUIDED_DIAGNOSTIC_ACCESSIBILITY_REQUEST", "");
            Toast.makeText(this, "Active DraftWA dans Accessibilité puis reviens : le test démarrera automatiquement.", Toast.LENGTH_LONG).show();
            openAccessibilitySettings(true);
            return;
        }
        startAutomation();
    }

    private void openAccessibilitySettings(boolean guided) {
        if (!guided) Prefs.p(this).edit().putBoolean(Prefs.PENDING_DIAGNOSTIC_START, false).apply();
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            DiagnosticLog.event(this, "ACCESSIBILITY_SETTINGS_FAILED", t.getClass().getSimpleName());
            Toast.makeText(this, "Impossible d’ouvrir les réglages d’accessibilité", Toast.LENGTH_LONG).show();
        }
    }

    private void startAutomation() {
        if (!saveSettings()) return;
        if (!isWhatsAppBusinessInstalled()) {
            refuseMissingWhatsApp();
            return;
        }
        if (!isAccessibilityEnabled()) {
            DiagnosticLog.event(this, "ACCESSIBILITY_NOT_ENABLED_UI", "");
            Toast.makeText(this, "Active d’abord DraftWA dans Accessibilité", Toast.LENGTH_LONG).show();
            openAccessibilitySettings(false);
            return;
        }

        Prefs.resetRunState(this);
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, true)
                .putBoolean(Prefs.PAUSED, false)
                .putString(Prefs.STATUS, diagnostic.isChecked() ? "Diagnostic en cours…" : "Démarrage…")
                .apply();
        DiagnosticLog.event(this, "RUN_START", "diagnostic=" + diagnostic.isChecked());
        if (!launchWhatsAppBusiness()) {
            Prefs.p(this).edit()
                    .putBoolean(Prefs.RUNNING, false)
                    .putString(Prefs.STATUS, "WhatsApp Business n’a pas pu être ouvert")
                    .apply();
        }
        refreshStatus();
    }

    private void stopAutomation() {
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putBoolean(Prefs.PAUSED, true)
                .putString(Prefs.STATUS, "En pause")
                .apply();
        DiagnosticLog.event(this, "RUN_PAUSE", "user");
        refreshStatus();
    }

    private void refuseMissingWhatsApp() {
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putString(Prefs.STATUS, "WhatsApp Business introuvable")
                .apply();
        DiagnosticLog.event(this, "WA_BUSINESS_MISSING", DraftAccessibilityService.WA_PACKAGE);
        Toast.makeText(this, "WhatsApp Business doit être installé avant de démarrer DraftWA.", Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    private boolean launchWhatsAppBusiness() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(DraftAccessibilityService.WA_PACKAGE);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            DiagnosticLog.event(this, "WA_BUSINESS_LAUNCH", "ok");
            return true;
        } catch (Throwable t) {
            DiagnosticLog.event(this, "WA_BUSINESS_LAUNCH_FAILED", t.getClass().getSimpleName());
            Toast.makeText(this, "Impossible d’ouvrir WhatsApp Business", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean isWhatsAppBusinessInstalled() {
        try {
            return getPackageManager().getLaunchIntentForPackage(DraftAccessibilityService.WA_PACKAGE) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private void shareSafeDiagnostics() {
        String summary = "DraftWA Mobile v" + BuildConfig.VERSION_NAME
                + "\nAndroid API " + Build.VERSION.SDK_INT
                + "\nWhatsApp Business : " + (isWhatsAppBusinessInstalled() ? "détecté" : "non détecté")
                + "\nAccessibilité : " + (isAccessibilityEnabled() ? "activée" : "désactivée")
                + "\nÉtat : " + Prefs.p(this).getString(Prefs.STATUS, "Prêt")
                + "\n\nÉvénements (détails masqués) :\n"
                + DiagnosticLog.safeSummary(this);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Diagnostic DraftWA " + BuildConfig.VERSION_NAME);
        send.putExtra(Intent.EXTRA_TEXT, summary);
        try {
            startActivity(Intent.createChooser(send, "Partager le diagnostic DraftWA"));
            DiagnosticLog.event(this, "SAFE_DIAGNOSTIC_SHARED", "chooser-opened");
        } catch (Throwable t) {
            DiagnosticLog.event(this, "SAFE_DIAGNOSTIC_SHARE_FAILED", t.getClass().getSimpleName());
            Toast.makeText(this, "Aucune application disponible pour partager le diagnostic", Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveSettings() {
        try {
            int ymin = num(yMin, 1), ymax = num(yMax, 1);
            int zmin = num(zMin, 0), zmax = num(zMax, 0);
            int xmin = num(xMin, 0), xmax = num(xMax, 0);
            int dh = num(dayHour, 0), eh = num(eveningHour, 0);
            if (ymin > ymax || zmin > zmax || xmin > xmax) throw new IllegalArgumentException("Les valeurs min doivent être ≤ aux valeurs max");
            if (dh > 23 || eh > 23) throw new IllegalArgumentException("Les heures doivent être entre 0 et 23");
            SharedPreferences.Editor e = Prefs.p(this).edit();
            e.putString(Prefs.CONDITION, condition.getText().toString().trim());
            e.putString(Prefs.REMOVE, remove.getText().toString());
            e.putString(Prefs.REJECT_MODE, destroyRejected.isChecked() ? "Détruire" : "Conserver");
            e.putBoolean(Prefs.GREETING_ENABLED, greeting.isChecked());
            e.putString(Prefs.TIMEZONE, timezone.getText().toString().trim());
            e.putInt(Prefs.DAY_HOUR, dh).putInt(Prefs.EVENING_HOUR, eh);
            e.putInt(Prefs.Y_MIN, ymin).putInt(Prefs.Y_MAX, ymax);
            e.putInt(Prefs.Z_MIN, zmin).putInt(Prefs.Z_MAX, zmax);
            e.putInt(Prefs.X_MIN, xmin).putInt(Prefs.X_MAX, xmax);
            e.putBoolean(Prefs.DIAGNOSTIC, diagnostic.isChecked());
            e.putBoolean(Prefs.AUTO_UPDATE, autoUpdate.isChecked());
            e.putBoolean(Prefs.AUTO_DOWNLOAD, autoDownload.isChecked());
            e.apply();
            refreshStatus();
            return true;
        } catch (Throwable t) {
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void loadSettings() {
        SharedPreferences sp = Prefs.p(this);
        condition.setText(sp.getString(Prefs.CONDITION, "Soko"));
        remove.setText(sp.getString(Prefs.REMOVE, ",Enregistré,"));
        destroyRejected.setChecked("Détruire".equals(sp.getString(Prefs.REJECT_MODE, "Conserver")));
        greeting.setChecked(sp.getBoolean(Prefs.GREETING_ENABLED, true));
        timezone.setText(sp.getString(Prefs.TIMEZONE, "Africa/Abidjan"));
        dayHour.setText(String.valueOf(sp.getInt(Prefs.DAY_HOUR, 5)));
        eveningHour.setText(String.valueOf(sp.getInt(Prefs.EVENING_HOUR, 18)));
        yMin.setText(String.valueOf(sp.getInt(Prefs.Y_MIN, 3)));
        yMax.setText(String.valueOf(sp.getInt(Prefs.Y_MAX, 5)));
        zMin.setText(String.valueOf(sp.getInt(Prefs.Z_MIN, 25)));
        zMax.setText(String.valueOf(sp.getInt(Prefs.Z_MAX, 60)));
        xMin.setText(String.valueOf(sp.getInt(Prefs.X_MIN, 5)));
        xMax.setText(String.valueOf(sp.getInt(Prefs.X_MAX, 12)));
        diagnostic.setChecked(sp.getBoolean(Prefs.DIAGNOSTIC, true));
        autoUpdate.setChecked(sp.getBoolean(Prefs.AUTO_UPDATE, true));
        autoDownload.setChecked(sp.getBoolean(Prefs.AUTO_DOWNLOAD, true));
    }

    private void checkUpdates(boolean installAfterCheck) {
        if (updateStatus != null) updateStatus.setText("Vérification…");
        UpdateManager.checkAsync(this, (s, info) -> {
            onUpdateStatus(s, info);
            if (installAfterCheck && info != null) UpdateManager.downloadAndInstall(this, info, this::onUpdateStatus);
        }, false);
    }

    private void onUpdateStatus(String text, UpdateManager.UpdateInfo info) {
        if (updateStatus != null) {
            updateStatus.setText(text + (info != null && !info.releaseNotes.isEmpty() ? "\n" + info.releaseNotes : ""));
        }
        if (info != null) pendingUpdate = info;
    }

    private void refreshStatus() {
        SharedPreferences sp = Prefs.p(this);
        boolean running = sp.getBoolean(Prefs.RUNNING, false);
        String s = sp.getString(Prefs.STATUS, running ? "En cours" : "Prêt");
        boolean accessibilityEnabled = isAccessibilityEnabled();
        boolean waInstalled = isWhatsAppBusinessInstalled();
        boolean diagnosticEnabled = diagnostic != null
                ? diagnostic.isChecked()
                : sp.getBoolean(Prefs.DIAGNOSTIC, true);

        if (status != null) {
            status.setText((running ? "● EN COURS" : "● ARRÊTÉ") + "  •  " + s);
            int bg = !waInstalled
                    ? Color.rgb(255, 231, 231)
                    : accessibilityEnabled ? Color.rgb(226, 244, 239) : Color.rgb(255, 247, 214);
            status.setBackgroundColor(bg);
        }
        if (readiness != null) {
            readiness.setText((waInstalled ? "✓" : "!") + " WhatsApp Business : " + (waInstalled ? "détecté" : "à installer")
                    + "\n" + (accessibilityEnabled ? "✓" : "!") + " Accessibilité DraftWA : " + (accessibilityEnabled ? "activée" : "à activer")
                    + "\n" + (diagnosticEnabled ? "✓ Mode diagnostic sécurisé" : "⚠ Mode réel : envoi autorisé")
                    + "\n☁ Prospection : backend séparé, Internet uniquement pour cette section");
        }
    }

    private boolean isAccessibilityEnabled() {
        String packageName = getPackageName();
        String serviceClass = DraftAccessibilityService.class.getName();
        try {
            AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager != null && manager.isEnabled()) {
                List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                if (services != null) {
                    for (AccessibilityServiceInfo info : services) {
                        if (info == null || info.getId() == null) continue;
                        String id = info.getId().toLowerCase(Locale.ROOT);
                        if (id.equals((packageName + "/" + serviceClass).toLowerCase(Locale.ROOT))
                                || id.startsWith(packageName.toLowerCase(Locale.ROOT) + "/")) return true;
                    }
                }
            }
        } catch (Throwable t) {
            DiagnosticLog.event(this, "ACCESSIBILITY_MANAGER_CHECK_FAILED", t.getClass().getSimpleName());
        }

        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String lower = enabled.toLowerCase(Locale.ROOT);
        String needle = (packageName + "/" + serviceClass).toLowerCase(Locale.ROOT);
        return lower.contains(needle) || lower.contains(packageName.toLowerCase(Locale.ROOT));
    }

    private int num(EditText e, int min) {
        int n = Integer.parseInt(e.getText().toString().trim());
        if (n < min) throw new IllegalArgumentException("Valeur minimale : " + min);
        return n;
    }

    private void section(LinearLayout root, String name) {
        TextView t = text(name, 18, true);
        t.setTextColor(Color.rgb(17, 27, 33));
        LinearLayout.LayoutParams lp = lpMatch();
        lp.setMargins(0, dp(20), 0, dp(8));
        root.addView(t, lp);
    }

    private EditText field(LinearLayout root, String label, String hint, boolean numeric) {
        TextView l = text(label, 13, true);
        l.setTextColor(Color.rgb(84, 101, 111));
        root.addView(l);
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(e, lpMatch());
        return e;
    }

    private EditText compactField(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setGravity(Gravity.CENTER);
        parent.addView(e, weighted());
        return e;
    }

    private CheckBox check(LinearLayout root, String label) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextSize(15);
        c.setTextColor(Color.rgb(17, 27, 33));
        root.addView(c, lpMatch());
        return c;
    }

    private LinearLayout horizontal(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, lpMatch());
        return row;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private Button primaryButton(String label) {
        Button b = button(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(11, 107, 87)));
        return b;
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams lpMatch() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpMatchWithTop(int topDp) {
        LinearLayout.LayoutParams lp = lpMatch();
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
