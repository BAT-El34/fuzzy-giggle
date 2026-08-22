package com.whatsapp.w4b;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private LinearLayout root;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) updateState();
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 18, 16, 18);

        TextView title = new TextView(this);
        title.setText("WhatsApp Business");
        title.setTextSize(24);
        root.addView(title);

        TextView chats = new TextView(this);
        chats.setText("Chats");
        chats.setTextSize(18);
        chats.setClickable(true);
        chats.setPadding(8, 12, 8, 12);
        root.addView(chats);

        updateState();

        ListView list = new FlakyListView();
        list.setId(R.id.conversation_list);
        list.setAdapter(new ChatAdapter());
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void updateState() {
        View old = root.findViewById(R.id.test_state);
        if (old != null) root.removeView(old);
        TextView state = new TextView(this);
        state.setId(R.id.test_state);
        boolean transformed = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("transformed_seen", false);
        boolean restored = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("restored", false);
        boolean sent = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("sent", false);
        String label = sent ? "SENT_BAD" : (restored ? "RESTORED_OK" : (transformed ? "TRANSFORMED_SEEN" : "WAITING_FOR_DRAFTWA"));
        state.setText(label);
        state.setTextColor(sent ? Color.RED : ((restored || transformed) ? Color.rgb(0, 120, 80) : Color.GRAY));
        root.addView(state, Math.min(2, root.getChildCount()));
    }

    // Reproduces the real-device condition reported on Android API 31: the
    // WhatsApp conversation list exists, but its first accessibility scroll
    // commands can be refused while the list/tree is refreshing. DraftWA must
    // recover via its gesture fallback rather than declaring NO_MORE_DRAFTS.
    private final class FlakyListView extends ListView {
        private int accessibilityScrollRefusals = 2;

        FlakyListView() {
            super(MainActivity.this);
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            boolean isScroll = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
            if (isScroll && accessibilityScrollRefusals > 0) {
                accessibilityScrollRefusals--;
                return false;
            }
            return super.performAccessibilityAction(action, arguments);
        }
    }

    private final class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return 90; }
        @Override public Object getItem(int p) { return p; }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(24, 24, 24, 24);
            row.setClickable(true);
            row.setFocusable(true);

            TextView name = new TextView(MainActivity.this);
            name.setText("Conversation " + p);
            name.setTextSize(17);
            row.addView(name);

            if (p == 72) {
                TextView draft = new TextView(MainActivity.this);
                draft.setId(R.id.draft_indicator);
                draft.setText("Brouillon");
                draft.setTextSize(14);
                row.addView(draft);
                row.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ChatActivity.class)));
            } else {
                TextView preview = new TextView(MainActivity.this);
                preview.setText("Message précédent " + p);
                row.addView(preview);
            }
            return row;
        }
    }
}
