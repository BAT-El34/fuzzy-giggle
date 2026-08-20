package com.draftwa.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final int BG = Color.rgb(246, 248, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(23, 34, 40);
    private static final int MUTED = Color.rgb(91, 107, 116);
    private static final int GREEN = Color.rgb(20, 125, 90);
    private static final int GREEN_SOFT = Color.rgb(231, 247, 240);
    private static final int AMBER = Color.rgb(151, 101, 0);
    private static final int AMBER_SOFT = Color.rgb(255, 246, 221);
    private static final int RED = Color.rgb(177, 42, 42);
    private static final int RED_SOFT = Color.rgb(255, 235, 235);
    private static final int BORDER = Color.rgb(222, 228, 232);

    private EditText condition, remove, timezone;
    private EditText yMin, yMax, zMin, zMax, xMin, xMax, dayHour, eveningHour;
    private CheckBox greeting, diagnostic, autoUpdate, autoDownload, destroyRejected;
    private TextView status, accessStatus, whatsappStatus, networkStatus, updateStatus, safetyBanner, logsView;
    private Button startButton, pauseButton;
    private UpdateManager.UpdateInfo pendingUpdate;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean uiTickerActive;
    private final Runnable uiTicker = new Runnable() {
        @Override public void run() {
            if (!uiTickerActive) return;
            refreshStatus();
            refreshRuntimeIndicators();
            refreshDiagnostics();
            uiHandler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaults(this);
        DiagnosticLog.event(this, "APP_START", "v" + BuildConfig.VERSION_NAME);
        setContentView(buildUi());
        loadSettings();
        refreshStatus();
        refreshRuntimeIndicators();
        refreshDiagnostics();
        updateSafetyAppearance();
        if (Prefs.p(this).getBoolean(Prefs.AUTO_UPDATE, true)) {
            checkUpdates(Prefs.p(this).getBoolean(Prefs.AUTO_DOWNLOAD, true));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiTickerActive = true;
        uiHandler.removeCallbacks(uiTicker);
        uiHandler.post(uiTicker);

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

    @Override
    protected void onPause() {
        super.onPause();
        uiTickerActive = false;
        uiHandler.removeCallbacks(uiTicker);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(36));
        scroll.addView(root);

        LinearLayout header = card();
        TextView eyebrow = text("DRAFTWA • PRÉPRODUCTION", 12, true);
        eyebrow.setTextColor(GREEN);
        header.addView(eyebrow);
        TextView title = text("DraftWA Mobile", 29, true);
        title.setTextColor(TEXT);
        header.addView(title, lpMatchWithTop(2));
        TextView sub = text("v" + BuildConfig.VERSION_NAME + "  •  brouillons WhatsApp Business", 14, false);
        sub.setTextColor(MUTED);
        header.addView(sub, lpMatchWithTop(2));
        TextView offline = text("Le moteur reste utilisable sans Internet. Internet sert uniquement aux mises à jour.", 13, false);
        offline.setTextColor(MUTED);
        header.addView(offline, lpMatchWithTop(10));
        root.addView(header, lpCard());

        LinearLayout runtime = card();
        addCardTitle(runtime, "État du test", "Vue instantanée avant de lancer le moteur");
        status = pill(runtime, "Chargement…", GREEN_SOFT, GREEN);
        accessStatus = pill(runtime, "Accessibilité : vérification…", AMBER_SOFT, AMBER);
        whatsappStatus = pill(runtime, "WhatsApp Business : vérification…", AMBER_SOFT, AMBER);
        networkStatus = pill(runtime, "Internet : vérification…", AMBER_SOFT, AMBER);

        LinearLayout accessActions = horizontal(runtime);
        Button accessibility = button("Activer l’accessibilité", false);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        accessActions.addView(accessibility, weighted());
        Button openWa = button("Ouvrir WhatsApp", false);
        openWa.setOnClickListener(v -> launchWhatsAppBusiness());
        accessActions.addView(openWa, weighted());
        root.addView(runtime, lpCard());

        LinearLayout safety = card();
        addCardTitle(safety, "Mode de test", "Garde-fou principal de la préproduction");
        diagnostic = check(safety, "Diagnostic sans modification ni envoi");
        diagnostic.setOnCheckedChangeListener((buttonView, isChecked) -> updateSafetyAppearance());
        safetyBanner = text("", 14, true);
        safetyBanner.setPadding(dp(12), dp(11), dp(12), dp(11));
        safety.addView(safetyBanner, lpMatchWithTop(6));

        LinearLayout runActions = horizontal(safety);
        startButton = button("Tester en diagnostic", true);
        startButton.setOnClickListener(v -> startAutomation());
        runActions.addView(startButton, weighted());
        pauseButton = button("Pause", false);
        pauseButton.setOnClickListener(v -> pauseAutomation());
        runActions.addView(pauseButton, weighted());

        Button fresh = button("Nouveau test / réinitialiser le parcours", false);
        fresh.setOnClickListener(v -> resetTestSession());
        safety.addView(fresh, lpMatchWithTop(8));
        root.addView(safety, lpCard());

        LinearLayout rules = card();
        addCardTitle(rules, "Règles des brouillons", "Ce qui doit être reconnu et retiré");
        condition = field(rules, "Condition de confirmation", "Soko  ou  Soko ; Allo.", false);
        remove = field(rules, "Retraitement / suppression", ",Enregistré,", false);
        destroyRejected = check(rules, "Détruire les brouillons non conformes");
        TextView rejectHelp = text("Désactivé = Conserver. En diagnostic,