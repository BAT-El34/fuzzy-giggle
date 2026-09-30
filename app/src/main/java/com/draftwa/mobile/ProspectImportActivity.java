package com.draftwa.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import java.util.List;

public class ProspectImportActivity extends Activity {
    private static final int PICK_FILE = 3001;
    private TextView summary;
    private RadioButton draftMode;
    private RadioButton sendMode;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaults(this);
        setContentView(buildUi());
        refreshSummary();
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(245,247,248));
        scroll.addView(root);

        TextView title = text("Base Excel", 27, true);
        title.setTextColor(Color.rgb(17,27,33));
        root.addView(title);

        TextView intro = text(
                "Importe un fichier .xlsx ou .csv. DraftWA traite la base ligne par ligne, teste les numéros séparés par / jusqu’au premier compte WhatsApp valide, puis conserve la progression localement.",
                14, false);
        intro.setTextColor(Color.rgb(84,101,111));
        root.addView(intro, matchTop(8));

        LinearLayout formatHeader = new LinearLayout(this);
        formatHeader.setOrientation(LinearLayout.HORIZONTAL);
        formatHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView formatTitle = text("Format Excel requis", 15, true);
        LinearLayout.LayoutParams formatTitleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        formatHeader.addView(formatTitle, formatTitleLp);

        ImageButton copy = new ImageButton(this);
        copy.setImageResource(R.drawable.ic_copy);
        copy.setContentDescription("Copier les instructions du classeur");
        copy.setBackgroundColor(Color.TRANSPARENT);
        copy.setPadding(dp(10), dp(10), dp(10), dp(10));
        copy.setOnClickListener(v -> copyWorkbookInstructions());
        if (android.os.Build.VERSION.SDK_INT >= 26) copy.setTooltipText("Copier le format Excel");
        formatHeader.addView(copy, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(formatHeader, matchTop(14));

        TextView format = text(
                "prospect_id | nom | telephones | message",
                14, true);
        root.addView(format, matchTop(3));

        TextView detail = text(
                "Touchez l’icône Copier pour récupérer les 4 colonnes obligatoires et leurs règles. Plusieurs numéros sont séparés uniquement par /. Les espaces, +, tirets et parenthèses sont supprimés avant analyse. Les numéros fixes togolais de plage 22 sont ignorés.",
                13, false);
        detail.setTextColor(Color.rgb(84,101,111));
        root.addView(detail, matchTop(5));

        Button pick = primaryButton("Importer un fichier");
        pick.setOnClickListener(v -> pickFile());
        root.addView(pick, matchTop(14));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, matchTop(12));

        summary = text("", 14, false);
        summary.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(summary, matchTop(12));

        TextView actionTitle = text("Action pour chaque contact valide", 15, true);
        root.addView(actionTitle, matchTop(14));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        draftMode = new RadioButton(this);
        draftMode.setText("Laisser le message en brouillon");
        sendMode = new RadioButton(this);
        sendMode.setText("Envoyer le message");
        group.addView(draftMode);
        group.addView(sendMode);
        String action = Prefs.p(this).getString(Prefs.PROSPECT_ACTION, "DRAFT");
        if ("SEND".equals(action)) sendMode.setChecked(true); else draftMode.setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            Prefs.p(this).edit().putString(Prefs.PROSPECT_ACTION, sendMode.isChecked() ? "SEND" : "DRAFT").apply();
        });
        root.addView(group, matchTop(5));

        TextView pacing = text(
                "Le rythme utilise exactement les réglages DraftWA existants : taille de lot, délai entre messages et pause entre lots.",
                13, false);
        pacing.setTextColor(Color.rgb(84,101,111));
        root.addView(pacing, matchTop(8));

        Button start = primaryButton("Démarrer / Reprendre la base");
        start.setOnClickListener(v -> startQueue());
        root.addView(start, matchTop(14));

        Button pause = button("Mettre en pause");
        pause.setOnClickListener(v -> {
            long remaining = Prefs.pauseAutomation(this);
            Prefs.p(this).edit().putString(Prefs.STATUS, "Base Excel en pause").apply();
            Toast.makeText(this, "Pause enregistrée", Toast.LENGTH_SHORT).show();
            refreshSummary();
        });
        root.addView(pause, matchTop(7));

        Button clear = button("Réinitialiser la base importée");
        clear.setOnClickListener(v -> {
            ProspectStore.clear(this);
            Prefs.p(this).edit()
                    .putBoolean(Prefs.PROSPECT_MODE, false)
                    .putString(Prefs.PROSPECT_PHASE, "IDLE")
                    .putString(Prefs.PROSPECT_ACTIVE_PHONE, "")
                    .apply();
            refreshSummary();
        });
        root.addView(clear, matchTop(7));
        return scroll;
    }

    private void copyWorkbookInstructions() {
        String instructions = "| Colonne | En-tête exact | Règle |\n"
                + "| --- | --- | --- |\n"
                + "| A | prospect_id | Identifiant unique et obligatoire |\n"
                + "| B | nom | Nom du prospect, entreprise ou établissement |\n"
                + "| C | telephones | Un ou plusieurs numéros. Plusieurs numéros sont séparés uniquement par / |\n"
                + "| D | message | Message WhatsApp complet à placer en brouillon ou à envoyer |";
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Instructions DraftWA Excel", instructions));
            Toast.makeText(this, "Instructions copiées", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values"
        });
        startActivityForResult(i, PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = displayName(uri);
        try {
            List<ProspectRecord> records = SpreadsheetImporter.importFile(getContentResolver(), uri, name);
            ProspectStore.replace(this, records, name);
            Prefs.p(this).edit()
                    .putBoolean(Prefs.PROSPECT_MODE, true)
                    .putString(Prefs.PROSPECT_PHASE, "IDLE")
                    .putString(Prefs.PROSPECT_ACTIVE_PHONE, "")
                    .apply();
            Toast.makeText(this, records.size() + " prospects importés", Toast.LENGTH_LONG).show();
            refreshSummary();
        } catch (Throwable t) {
            Toast.makeText(this, t.getMessage() == null ? "Import impossible" : t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        String name = "base.xlsx";
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return name == null ? "base.xlsx" : name;
    }

    private void startQueue() {
        if (ProspectStore.total(this) <= 0) {
            Toast.makeText(this, "Importe d’abord une base", Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.p(this).edit()
                .putBoolean(Prefs.PROSPECT_MODE, true)
                .putBoolean(Prefs.RUNNING, true)
                .putBoolean(Prefs.PAUSED, false)
                .putString(Prefs.PROSPECT_ACTION, sendMode.isChecked() ? "SEND" : "DRAFT")
                .putString(Prefs.STATUS, "Base Excel en cours…")
                .apply();
        Intent launch = getPackageManager().getLaunchIntentForPackage(DraftAccessibilityService.WA_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
        finish();
    }

    private void refreshSummary() {
        int total = ProspectStore.total(this);
        int done = ProspectStore.completed(this);
        int sent = ProspectStore.sent(this);
        int drafted = ProspectStore.drafted(this);
        String source = ProspectStore.sourceName(this);
        if (progress != null) progress.setProgress(CampaignStats.percent(this));
        summary.setText(total == 0
                ? "Aucune base importée."
                : "Fichier : " + source + "\nProgression : " + done + " / " + total
                    + "\nEnvoyés : " + sent + " • Brouillons : " + drafted);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(17,27,33));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
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
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,125,90)));
        return b;
    }

    private LinearLayout.LayoutParams matchTop(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
