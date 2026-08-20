package com.draftwa.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private EditText condition, remove, timezone;
    private EditText yMin, yMax, zMin, zMax, xMin, xMax, dayHour, eveningHour;
    private CheckBox greeting, diagnostic, autoUpdate, autoDownload, destroyRejected;
    private TextView status, updateStatus;
    private UpdateManager.UpdateInfo pendingUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaults(this);
        DiagnosticLog.event(this, "APP_OPEN", "v" + BuildConfig.VERSION_NAME);
        setContentView(buildUi());
        loadSettings();
        refreshStatus();
        if (Prefs.p(this).getBoolean(Prefs.AUTO_UPDATE, true)) checkUpdates(Prefs.p(this).getBoolean(Prefs.AUTO_DOWNLOAD, true));
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
                if (pendingUpdate != null) {
                    UpdateManager.downloadAndInstall(this, pendingUpdate, this::onUpdateStatus);
                } else {
                    checkUpdates(true);
                }
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

        TextView title = text("DraftWA Mobile", 28, true);
        title.setTextColor(Color.rgb(17, 27, 33));
        root.addView(title);
        TextView sub = text("v0.7.0 • moteur Brouillons + mise à jour sécurisée", 14, false);
        sub.setTextColor(Color.rgb(84, 101, 111));
        root.addView(sub);

        status = text("", 15, true);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackgroundColor(Color.rgb(226, 244, 239));
        LinearLayout.LayoutParams statusLp = lpMatch();
        statusLp.setMargins(0, dp(16), 0, dp(12));
        root.addView(status, statusLp);

        section(root, "1. Règles des brouillons");
        condition = field(root, "Texte de confirmation", "Ex. Soko ou Soko ; Allo.", false);
        remove = field(root, "Retraitement / suppression", "Ex. ,Enregistré,", false);
        destroyRejected = check(root, "Détruire les brouillons non conformes (sinon Conserver)");

        section(root, "2. Salutation");
        greeting = check(root, "Adapter automatiquement Bonjour / Bonsoir");
        timezone = field(root, "Fuseau horaire", "Africa/Abidjan", false);
        LinearLayout hours = horizontal(root);
        dayHour = compactField(hours, "Jour", "5");
        eveningHour = compactField(hours, "Soir", "18");

        section(root, "3. Rythme");
        LinearLayout batch = horizontal(root);
        yMin = compactField(batch, "Lot min", "3");
        yMax = compactField(batch, "Lot max", "5");
        LinearLayout msgWait = horizontal(root);
        zMin = compactField(msgWait, "Msg min (s)", "25");
        zMax = compactField(msgWait, "Msg max (s)", "60");
        LinearLayout batchWait = horizontal(root);
        xMin = compactField(batchWait, "Pause min (min)", "5");
        xMax = compactField(batchWait, "Pause max (min)", "12");

        section(root, "4. Test et sécurité");
        diagnostic = check(root, "Mode diagnostic : ne pas appuyer sur Envoyer");
        TextView diagHelp = text("À garder activé jusqu'à validation sur ton téléphone. DraftWA ouvre, lit, filtre et transforme, mais n'envoie rien.", 13, false);
        diagHelp.setTextColor(Color.rgb(84, 101, 111));
        root.addView(diagHelp);

        LinearLayout actions = horizontal(root);
        Button start = button("Démarrer");
        start.setOnClickListener(v -> startAutomation());
        actions.addView(start, weighted());
        Button stop = button("Pause");
        stop.setOnClickListener(v -> stopAutomation());
        actions.addView(stop, weighted());

        Button accessibility = button("Ouvrir les réglages d’accessibilité");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, lpMatchWithTop(10));

        section(root, "5. Mises à jour");
        autoUpdate = check(root, "Vérifier automatiquement au démarrage");
        autoDownload = check(root, "Télécharger automatiquement si une version est disponible");
        updateStatus = text("", 14, false);
        updateStatus.setTextColor(Color.rgb(84, 101, 111));
        root.addView(updateStatus);

        LinearLayout updates = horizontal(root);
        Button check = button("Rechercher");
        check.setOnClickListener(v -> { saveSettings(); checkUpdates(false); });
        updates.addView(check, weighted());
        Button install = button("Télécharger / Mettre à jour");
        install.setOnClickListener(v -> {
            saveSettings();
            if (pendingUpdate == null) checkUpdates(true);
            else UpdateManager.downloadAndInstall(this, pendingUpdate, this::onUpdateStatus);
        });
        updates.addView(install, weighted());

        Button save = button("Enregistrer la configuration");
        save.setOnClickListener(v -> {
            if (saveSettings()) Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, lpMatchWithTop(18));

        return scroll;
    }

    private void startAutomation() {
        if (!saveSettings()) return;
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Active d’abord DraftWA dans Accessibilité", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        Prefs.resetRunState(this);
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, true)
                .putString(Prefs.STATUS, "Démarrage…")
                .apply();
        DiagnosticLog.event(this, "RUN_START", "diagnostic=" + diagnostic.isChecked());
        launchWhatsAppBusiness();
        refreshStatus();
    }

    private void stopAutomation() {
        Prefs.p(this).edit().putBoolean(Prefs.RUNNING, false).putString(Prefs.STATUS, "En pause").apply();
        DiagnosticLog.event(this, "RUN_PAUSE", "user");
        refreshStatus();
    }

    private void launchWhatsAppBusiness() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(DraftAccessibilityService.WA_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "WhatsApp Business (com.whatsapp.w4b) introuvable", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
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
        updateStatus.setText("Vérification…");
        UpdateManager.checkAsync(this, (s, info) -> {
            onUpdateStatus(s, info);
            if (installAfterCheck && info != null) UpdateManager.downloadAndInstall(this, info, this::onUpdateStatus);
        }, false);
    }

    private void onUpdateStatus(String text, UpdateManager.UpdateInfo info) {
        updateStatus.setText(text + (info != null && !info.releaseNotes.isEmpty() ? "\n" + info.releaseNotes : ""));
        if (info != null) pendingUpdate = info;
    }

    private void refreshStatus() {
        boolean running = Prefs.p(this).getBoolean(Prefs.RUNNING, false);
        String s = Prefs.p(this).getString(Prefs.STATUS, running ? "En cours" : "Prêt");
        status.setText((running ? "● EN COURS" : "● ARRÊTÉ") + "  •  " + s
                + "\nAccessibilité : " + (isAccessibilityEnabled() ? "activée" : "à activer"));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String needle = getPackageName().toLowerCase(Locale.ROOT) + "/" + DraftAccessibilityService.class.getName().toLowerCase(Locale.ROOT);
        return enabled.toLowerCase(Locale.ROOT).contains(needle) || enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
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
