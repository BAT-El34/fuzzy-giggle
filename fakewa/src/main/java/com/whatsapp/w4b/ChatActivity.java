package com.whatsapp.w4b;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ChatActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("Conversation test");
        title.setTextSize(20);
        root.addView(title);

        EditText entry = new EditText(this);
        entry.setId(R.id.entry);
        entry.setText("Bonjour Soko, votre demande ,Enregistré, est prête.");
        entry.setTextSize(16);
        root.addView(entry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button send = new Button(this);
        send.setId(R.id.send);
        send.setText("Envoyer");
        root.addView(send);

        entry.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                String value = s == null ? "" : s.toString();
                boolean transformedNow = value.contains("Soko") && !value.contains(",Enregistré,");
                boolean transformedSeen = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("transformed_seen", false);
                android.content.SharedPreferences.Editor e = getSharedPreferences("ci", MODE_PRIVATE).edit();
                if (transformedNow) {
                    e.putBoolean("transformed_seen", true);
                } else if (transformedSeen && value.contains("Soko") && value.contains(",Enregistré,")) {
                    e.putBoolean("restored", true);
                }
                e.apply();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        send.setOnClickListener(v -> getSharedPreferences("ci", MODE_PRIVATE).edit().putBoolean("sent", true).apply());
        setContentView(root);
    }
}
