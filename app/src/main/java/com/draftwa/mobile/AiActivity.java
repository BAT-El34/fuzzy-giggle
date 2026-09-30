package com.draftwa.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONArray messages = new JSONArray();
    private TextView report;
    private TextView transcript;
    private EditText input;
    private ProgressBar busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(245,247,248));
        scroll.addView(root);

        root.addView(text("Rapport IA", 27, true));
        TextView privacy = text("Le rapport transmet uniquement des statistiques opérationnelles de la campagne, pas le contenu des messages.", 13, false);
        privacy.setTextColor(Color.rgb(84,101,111));
        root.addView(privacy, top(6));

        busy = new ProgressBar(this);
        busy.setIndeterminate(true);
        busy.setVisibility(ProgressBar.GONE);
        root.addView(busy, top(8));

        Button generate = primary("Générer le rapport IA");
        generate.setOnClickListener(v -> requestReport());
        root.addView(generate, top(10));

        report = text("Aucun rapport généré.", 14, false);
        report.setTextIsSelectable(true);
        report.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(report, top(8));

        root.addView(text("Chat interactif", 20, true), top(18));
        transcript = text("Pose une question sur la progression ou les résultats de la campagne.", 14, false);
        transcript.setTextIsSelectable(true);
        root.addView(transcript, top(8));

        input = new EditText(this);
        input.setHint("Ex. Quels prospects restent à traiter ?");
        input.setMinLines(2);
        input.setMaxLines(5);
        root.addView(input, top(8));

        Button send = primary("Envoyer au chat IA");
        send.setOnClickListener(v -> sendChat());
        root.addView(send, top(8));
        return scroll;
    }

    private void requestReport() {
        setBusy(true);
        JSONObject body = new JSONObject();
        try { body.put("stats", CampaignStats.json(this)); } catch (Throwable ignored) {}
        post("/api/ai/report", body, "report");
    }

    private void sendChat() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        try {
            JSONObject m = new JSONObject();
            m.put("role","user"); m.put("content",q);
            messages.put(m);
            JSONObject body = new JSONObject();
            body.put("stats", CampaignStats.json(this));
            body.put("messages", messages);
            appendTranscript("Vous", q);
            input.setText("");
            setBusy(true);
            post("/api/ai/chat", body, "chat");
        } catch (Throwable t) {
            Toast.makeText(this,"Impossible de préparer la requête",Toast.LENGTH_LONG).show();
        }
    }

    private void post(String path, JSONObject payload, String kind) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(BuildConfig.BACKEND_BASE_URL + path).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
                int code = conn.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String raw = read(stream);
                JSONObject result = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
                if (code < 200 || code >= 300 || !result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("error","Service indisponible"));
                }
                String value = "report".equals(kind) ? result.optString("report","") : result.optString("answer","");
                main.post(() -> {
                    setBusy(false);
                    if ("report".equals(kind)) report.setText(value);
                    else {
                        try {
                            JSONObject m = new JSONObject();
                            m.put("role","assistant"); m.put("content",value);
                            messages.put(m);
                        } catch (Throwable ignored) {}
                        appendTranscript("DraftWA IA", value);
                    }
                });
            } catch (Throwable t) {
                main.post(() -> {
                    setBusy(false);
                    Toast.makeText(this,t.getMessage()==null?"Service IA indisponible":t.getMessage(),Toast.LENGTH_LONG).show();
                });
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void appendTranscript(String who, String value) {
        String current = transcript.getText().toString();
        if (current.startsWith("Pose une question")) current = "";
        transcript.setText((current.isEmpty() ? "" : current + "\n\n") + who + " :\n" + value);
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String line;
        while((line=br.readLine())!=null)b.append(line);
        return b.toString();
    }

    private void setBusy(boolean value){ busy.setVisibility(value ? ProgressBar.VISIBLE : ProgressBar.GONE); }
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(17,27,33));if(bold)t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);return t;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,125,90)));return b;}
    private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,dp(v),0,0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
