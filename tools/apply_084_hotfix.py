from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f'Expected block not found in {path}: {old[:120]!r}')
    path.write_text(text.replace(old, new, 1))


service = ROOT / 'app/src/main/java/com/draftwa/mobile/DraftAccessibilityService.java'
replace_once(
    service,
    '    private long lastLaunchAt = 0L;\n    private String lastStatus = "";\n',
    '    private long lastLaunchAt = 0L;\n    private long lastChatsRecoveryAt = 0L;\n    private String lastStatus = "";\n'
)

replace_once(
    service,
    '''        AccessibilityNodeInfo list = firstById(root, "conversation_list");
        if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "view-id");
        if (list == null) {
            list = findBestScrollable(root);
            if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "scrollable-fallback");
        }
''',
    '''        AccessibilityNodeInfo exactList = firstById(root, "conversation_list");
        AccessibilityNodeInfo list = exactList;
        if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "view-id");
        if (list == null) {
            list = findBestConversationScrollable(root);
            if (list != null) DiagnosticLog.event(this, "CONVERSATION_LIST_FOUND", "safe-scrollable-fallback");
        }

        // WhatsApp Business exposes tab containers as scrollable accessibility
        // nodes on some builds. A generic ACTION_SCROLL_FORWARD on that pager can
        // switch from Chats/Discussions to Calls/Appels. Recover the Chats tab
        // before any list action and never trust a navigation pager as the list.
        if (recoverChatsTabIfNeeded(root, list, exactList != null)) return;
'''
)

replace_once(
    service,
    '''        list = firstById(root, "conversation_list");
        if (list == null) list = findBestScrollable(root);
''',
    '''        list = firstById(root, "conversation_list");
        if (list == null) list = findBestConversationScrollable(root);
'''
)

replace_once(
    service,
    '        int scrollResult = performRobustScroll(list);\n',
    '        int scrollResult = performRobustScroll(root, list);\n'
)

replace_once(
    service,
    '''    private int performRobustScroll(AccessibilityNodeInfo list) {
        if (list == null) return 0;
''',
    '''    private int performRobustScroll(AccessibilityNodeInfo root, AccessibilityNodeInfo list) {
        if (list == null) return 0;
'''
)

replace_once(
    service,
    '''        if (dispatchListSwipe(list)) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE", "fallback-after-node-refusal");
            return 1;
        }
''',
    '''        if (dispatchListSwipe(root, list)) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE", "safe-zone-fallback-after-node-refusal");
            return 1;
        }
'''
)

replace_once(
    service,
    '''    private boolean dispatchListSwipe(AccessibilityNodeInfo list) {
        try {
            Rect r = new Rect();
            list.getBoundsInScreen(r);
            int width = Math.max(0, r.right - r.left);
            int height = Math.max(0, r.bottom - r.top);
            if (width < 80 || height < 240) return false;
            float x = r.left + width * 0.5f;
            float fromY = r.top + height * 0.78f;
            float toY = r.top + height * 0.24f;
            if (fromY <= toY + 80f) return false;
            Path path = new Path();
            path.moveTo(x, fromY);
            path.lineTo(x, toY);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 360))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE_FAILED", t.getClass().getSimpleName());
            return false;
        }
    }
''',
    '''    private boolean dispatchListSwipe(AccessibilityNodeInfo root, AccessibilityNodeInfo list) {
        try {
            Rect r = new Rect();
            list.getBoundsInScreen(r);
            int width = Math.max(0, r.right - r.left);
            int height = Math.max(0, r.bottom - r.top);
            if (width < 80 || height < 240) return false;

            // Never start a synthetic swipe near WhatsApp's bottom navigation.
            // Some accessibility trees report a list/pager whose bounds include
            // the tab bar; reserving the bottom area prevents a gesture from
            // touching Calls/Appels even when those bounds are temporarily broad.
            int safeTop = r.top + Math.max(24, height / 12);
            int safeBottom = r.bottom - Math.max(36, height / 8);
            int navTop = navigationBarTop(root, list, r);
            if (navTop > safeTop) {
                safeBottom = Math.min(safeBottom, navTop - Math.max(18, height / 50));
            }
            int safeHeight = safeBottom - safeTop;
            if (safeHeight < 180) {
                DiagnosticLog.event(this, "LIST_SCROLL_SAFE_ZONE_REJECTED",
                        "bounds=" + r.flattenToString() + " navTop=" + navTop);
                return false;
            }

            float x = r.left + width * 0.5f;
            float fromY = safeTop + safeHeight * 0.82f;
            float toY = safeTop + safeHeight * 0.24f;
            if (fromY <= toY + 80f) return false;
            DiagnosticLog.event(this, "LIST_SCROLL_SAFE_ZONE",
                    "x=" + (int) x + " from=" + (int) fromY + " to=" + (int) toY
                            + " navTop=" + navTop);
            Path path = new Path();
            path.moveTo(x, fromY);
            path.lineTo(x, toY);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 360))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE_FAILED", t.getClass().getSimpleName());
            return false;
        }
    }

    private int navigationBarTop(AccessibilityNodeInfo root, AccessibilityNodeInfo list, Rect listBounds) {
        int best = Integer.MAX_VALUE;
        String[] labels = new String[] {
                "Chats", "Discussions", "Calls", "Appels", "Updates", "Actus",
                "Mises à jour", "Communities", "Communautés"
        };
        for (String label : labels) {
            for (AccessibilityNodeInfo n : byText(root, label)) {
                if (n == null || (list != null && isDescendantOf(n, list))) continue;
                String text = nodeText(n).trim();
                CharSequence desc = n.getContentDescription();
                String d = desc == null ? "" : desc.toString().trim();
                if (!text.equalsIgnoreCase(label) && !d.equalsIgnoreCase(label)) continue;
                AccessibilityNodeInfo c = clickableAncestor(n, 8);
                if (c == null) c = n;
                Rect b = new Rect();
                c.getBoundsInScreen(b);
                if (b.top > listBounds.top + 40 && b.top < listBounds.bottom) {
                    best = Math.min(best, b.top);
                }
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }
'''
)

replace_once(
    service,
    '''    private AccessibilityNodeInfo findBestScrollable(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo[] best = new AccessibilityNodeInfo[1];
        int[] bestScore = new int[] { Integer.MIN_VALUE };
        findBestScrollable(root, 0, best, bestScore);
        return best[0];
    }

    private void findBestScrollable(AccessibilityNodeInfo n, int depth, AccessibilityNodeInfo[] best, int[] bestScore) {
        if (n == null || depth > 14) return;
        try {
            if (n.isScrollable()) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                int height = Math.max(0, r.bottom - r.top);
                int width = Math.max(0, r.right - r.left);
                int score = n.getChildCount() * 10000 + height * 4 - Math.max(0, width - height);
                if (score > bestScore[0]) {
                    bestScore[0] = score;
                    best[0] = n;
                }
            }
        } catch (Throwable ignored) {}
        int count = Math.min(n.getChildCount(), 30);
        for (int i = 0; i < count; i++) findBestScrollable(n.getChild(i), depth + 1, best, bestScore);
    }
''',
    '''    private AccessibilityNodeInfo findBestConversationScrollable(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo[] best = new AccessibilityNodeInfo[1];
        int[] bestScore = new int[] { Integer.MIN_VALUE };
        findBestConversationScrollable(root, 0, best, bestScore);
        return best[0];
    }

    private void findBestConversationScrollable(AccessibilityNodeInfo n, int depth,
            AccessibilityNodeInfo[] best, int[] bestScore) {
        if (n == null || depth > 14) return;
        try {
            if (n.isScrollable() && !looksLikeNavigationScroller(n)) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                int height = Math.max(0, r.bottom - r.top);
                int width = Math.max(0, r.right - r.left);
                int score = n.getChildCount() * 10000 + height * 4 - Math.max(0, width - height);
                if (score > bestScore[0]) {
                    bestScore[0] = score;
                    best[0] = n;
                }
            }
        } catch (Throwable ignored) {}
        int count = Math.min(n.getChildCount(), 30);
        for (int i = 0; i < count; i++) {
            findBestConversationScrollable(n.getChild(i), depth + 1, best, bestScore);
        }
    }

    private boolean looksLikeNavigationScroller(AccessibilityNodeInfo n) {
        if (n == null) return true;
        try {
            CharSequence cls = n.getClassName();
            String c = cls == null ? "" : cls.toString().toLowerCase(Locale.ROOT);
            if (c.contains("viewpager") || c.contains("pager") || c.contains("tablayout")) return true;
            for (AccessibilityNodeInfo.AccessibilityAction a : n.getActionList()) {
                int id = a.getId();
                if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()
                        || id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId()) {
                    return true;
                }
            }
            boolean chats = containsExactLabel(n, 0, "Chats", "Discussions");
            boolean otherTab = containsExactLabel(n, 0, "Calls", "Appels", "Updates", "Actus",
                    "Mises à jour", "Communities", "Communautés");
            return chats && otherTab;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean containsExactLabel(AccessibilityNodeInfo n, int depth, String... labels) {
        if (n == null || depth > 8) return false;
        String text = nodeText(n).trim();
        CharSequence desc = n.getContentDescription();
        String d = desc == null ? "" : desc.toString().trim();
        for (String label : labels) {
            if (text.equalsIgnoreCase(label) || d.equalsIgnoreCase(label)) return true;
        }
        int count = Math.min(n.getChildCount(), 24);
        for (int i = 0; i < count; i++) {
            if (containsExactLabel(n.getChild(i), depth + 1, labels)) return true;
        }
        return false;
    }

    private boolean recoverChatsTabIfNeeded(AccessibilityNodeInfo root, AccessibilityNodeInfo list,
            boolean exactListPresent) {
        AccessibilityNodeInfo chats = findExactTextClickableOutside(root, list, "Chats", "Discussions");
        if (chats == null) return false;

        boolean chatsSelected = nodeOrAncestorSelected(chats, 8);
        boolean otherSelected = false;
        String[] otherLabels = new String[] {
                "Calls", "Appels", "Updates", "Actus", "Mises à jour", "Communities", "Communautés"
        };
        for (String label : otherLabels) {
            AccessibilityNodeInfo other = findExactTextClickableOutside(root, list, label);
            if (other != null && nodeOrAncestorSelected(other, 8)) {
                otherSelected = true;
                break;
            }
        }

        long now = SystemClock.elapsedRealtime();
        boolean uncertainMissingList = !exactListPresent && !chatsSelected && !otherSelected
                && now - lastChatsRecoveryAt > 2500L;
        if (!otherSelected && !uncertainMissingList) return false;

        boolean clicked = false;
        try {
            clicked = chats.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Throwable ignored) {}
        if (!clicked) {
            DiagnosticLog.event(this, "CHATS_TAB_RECOVERY_FAILED",
                    "otherSelected=" + otherSelected + " exactList=" + exactListPresent);
            return false;
        }

        lastChatsRecoveryAt = now;
        resetListScanGuards(true);
        DiagnosticLog.event(this, "CHATS_TAB_RECOVERY",
                "otherSelected=" + otherSelected + " exactList=" + exactListPresent);
        status("Retour à l’onglet Discussions avant défilement", "CHATS_TAB_RECOVERY");
        schedule(700);
        return true;
    }

    private boolean nodeOrAncestorSelected(AccessibilityNodeInfo n, int max) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; cur != null && i <= max; i++) {
            try {
                if (cur.isSelected() || cur.isChecked()) return true;
            } catch (Throwable ignored) {}
            cur = cur.getParent();
        }
        return false;
    }
'''
)

# Bump release identity.
build = ROOT / 'app/build.gradle'
replace_once(build, '// DraftWA 0.8.3 scroll-reliability release marker\n',
             '// DraftWA 0.8.4 safe-tab-scroll release marker\n')
replace_once(build, "orElse('803')", "orElse('804')")
replace_once(build, "orElse('0.8.3')", "orElse('0.8.4')")

# Harden the FakeWA regression: expose a scrollable tab pager that wins the old
# generic scoring and marks a failure if DraftWA scrolls it instead of the chat list.
fake = ROOT / 'fakewa/src/main/java/com/whatsapp/w4b/MainActivity.java'
replace_once(
    fake,
    '        root = new LinearLayout(this);\n',
    '        root = new DangerousPager();\n'
)
replace_once(
    fake,
    '''        chats.setClickable(true);
        chats.setPadding(8, 12, 8, 12);
        root.addView(chats);

        updateState();

        ListView list = new FlakyListView();
        list.setId(R.id.conversation_list);
''',
    '''        chats.setClickable(true);
        chats.setPadding(8, 12, 8, 12);
        chats.setSelected(true);
        chats.setOnClickListener(v -> {
            getSharedPreferences("ci", MODE_PRIVATE).edit().putBoolean("calls_selected", false).apply();
            chats.setSelected(true);
        });
        root.addView(chats);

        updateState();

        // Inflate the outer scrollable pager score so 0.8.3's generic fallback
        // chooses it and accidentally changes tabs. 0.8.4 must reject it.
        for (int i = 0; i < 18; i++) {
            TextView spacer = new TextView(this);
            spacer.setText("Header " + i);
            spacer.setVisibility(View.GONE);
            root.addView(spacer);
        }

        ListView list = new FlakyListView();
'''
)
replace_once(
    fake,
    '''        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
''',
    '''        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView calls = new TextView(this);
        calls.setText("Calls");
        calls.setTextSize(18);
        calls.setClickable(true);
        calls.setPadding(8, 12, 8, 12);
        calls.setOnClickListener(v -> markCallsSelected());
        root.addView(calls);
        setContentView(root);
'''
)
replace_once(
    fake,
    '''        boolean sent = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("sent", false);
        String label = sent ? "SENT_BAD" : (restored ? "RESTORED_OK" : (transformed ? "TRANSFORMED_SEEN" : "WAITING_FOR_DRAFTWA"));
''',
    '''        boolean sent = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("sent", false);
        boolean callsSelected = getSharedPreferences("ci", MODE_PRIVATE).getBoolean("calls_selected", false);
        String label = callsSelected ? "CALLS_SELECTED_BAD" : (sent ? "SENT_BAD" : (restored ? "RESTORED_OK" : (transformed ? "TRANSFORMED_SEEN" : "WAITING_FOR_DRAFTWA")));
'''
)
replace_once(
    fake,
    '''        state.setTextColor(sent ? Color.RED : ((restored || transformed) ? Color.rgb(0, 120, 80) : Color.GRAY));
''',
    '''        state.setTextColor((sent || callsSelected) ? Color.RED : ((restored || transformed) ? Color.rgb(0, 120, 80) : Color.GRAY));
'''
)
insert_before = '''    // Reproduces the real-device condition reported on Android API 31: the
'''
addition = '''    private void markCallsSelected() {
        getSharedPreferences("ci", MODE_PRIVATE).edit().putBoolean("calls_selected", true).apply();
        updateState();
    }

    private final class DangerousPager extends LinearLayout {
        DangerousPager() {
            super(MainActivity.this);
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("androidx.viewpager.widget.ViewPager");
            info.setScrollable(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT);
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            boolean navigationScroll = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()
                    || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()
                    || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
            if (navigationScroll) {
                markCallsSelected();
                return true;
            }
            return super.performAccessibilityAction(action, arguments);
        }
    }

'''
replace_once(fake, insert_before, addition + insert_before)

# Strengthen CI assertion: changing to Calls is a release-blocking regression.
journey = ROOT / 'tools/ci_android_journey.sh'
replace_once(
    journey,
    '''! grep -q 'name="sent" value="true"' artifacts/fakewa-ci.xml

grep -q 'DRAFT_SCAN_PAGE' artifacts/logcat.txt
''',
    '''! grep -q 'name="sent" value="true"' artifacts/fakewa-ci.xml
! grep -q 'name="calls_selected" value="true"' artifacts/fakewa-ci.xml

grep -q 'DRAFT_SCAN_PAGE' artifacts/logcat.txt
'''
)
replace_once(
    journey,
    '''grep -q 'DRAFT_SCAN_PAGE' artifacts/logcat.txt
grep -q 'DRAFT_INDICATORS' artifacts/logcat.txt
''',
    '''grep -q 'DRAFT_SCAN_PAGE' artifacts/logcat.txt
grep -q 'CHATS_TAB_RECOVERY' artifacts/logcat.txt
grep -q 'LIST_SCROLL_SAFE_ZONE' artifacts/logcat.txt
grep -q 'DRAFT_INDICATORS' artifacts/logcat.txt
'''
)
replace_once(
    journey,
    "printf 'API=%s\\nDIAGNOSTIC_FLOW=PASS\\nACCESSIBILITY=PASS\\nNO_SEND=PASS\\nRESTORE=PASS\\nNO_FATAL=PASS\\nNO_DRAFTWA_ANR=PASS\\n' \"$API_LEVEL\" > artifacts/result.txt\n",
    "printf 'API=%s\\nDIAGNOSTIC_FLOW=PASS\\nACCESSIBILITY=PASS\\nNO_SEND=PASS\\nNO_CALLS_TAB_SWITCH=PASS\\nSAFE_SCROLL_ZONE=PASS\\nRESTORE=PASS\\nNO_FATAL=PASS\\nNO_DRAFTWA_ANR=PASS\\n' \"$API_LEVEL\" > artifacts/result.txt\n"
)

review = ROOT / 'tools/static_review.py'
replace_once(
    review,
    "check('Generic scrollable list fallback', 'findBestScrollable' in service)\n",
    "check('Safe conversation scrollable fallback', 'findBestConversationScrollable' in service and 'looksLikeNavigationScroller' in service)\n"
)
replace_once(
    review,
    "check('Gesture scroll fallback exists', 'GestureDescription' in service and 'dispatchListSwipe' in service and 'LIST_SCROLL_GESTURE' in service)\n",
    "check('Gesture scroll fallback exists', 'GestureDescription' in service and 'dispatchListSwipe' in service and 'LIST_SCROLL_GESTURE' in service)\ncheck('Gesture avoids bottom navigation', 'LIST_SCROLL_SAFE_ZONE' in service and 'navigationBarTop' in service)\ncheck('Chats tab recovery guard exists', 'CHATS_TAB_RECOVERY' in service and 'recoverChatsTabIfNeeded' in service)\ncheck('Navigation pager is rejected', 'ACTION_SCROLL_LEFT' in service and 'ACTION_SCROLL_RIGHT' in service and 'looksLikeNavigationScroller' in service)\n"
)
replace_once(
    review,
    "scroll_block = service[service.find('private int performRobustScroll'):service.find('private boolean dispatchListSwipe')]\n",
    "scroll_block = service[service.find('private int performRobustScroll'):service.find('private boolean dispatchListSwipe')]\n"
)
replace_once(
    review,
    "check('Release version is 0.8.3', \"orElse('803')\" in build and \"orElse('0.8.3')\" in build)\n",
    "check('Release version is 0.8.4', \"orElse('804')\" in build and \"orElse('0.8.4')\" in build)\n"
)

print('DraftWA 0.8.4 hotfix applied successfully')
