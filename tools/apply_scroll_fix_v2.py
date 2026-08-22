from pathlib import Path

p = Path('app/src/main/java/com/draftwa/mobile/DraftAccessibilityService.java')
s = p.read_text(encoding='utf-8')
old = '''        Prefs.p(this).edit().putInt(Prefs.LIST_MISSING_COUNT, 0).apply();
        String viewport = viewportSignature(list);
        String previous = sp.getString(Prefs.LAST_VIEWPORT, "");
        boolean sameViewport = viewport.equals(previous) && !viewport.isEmpty();
        int same = sameViewport ? sp.getInt(Prefs.SAME_VIEWPORT_COUNT, 0) + 1 : 0;
        int stalled = sameViewport ? sp.getInt(Prefs.SCROLL_STALL_COUNT, 0) + 1 : 0;
        Prefs.p(this).edit()
                .putString(Prefs.LAST_VIEWPORT, viewport)
                .putInt(Prefs.SAME_VIEWPORT_COUNT, same)
                .putInt(Prefs.SCROLL_STALL_COUNT, stalled)
                .apply();

        // Only claim end-of-list after several independently observed unchanged
        // viewports. Between observations we actively attempt both Accessibility
        // scrolling and, when WhatsApp refuses/no-ops it, a real swipe gesture.
        if (same >= END_STABLE_CONFIRMATIONS && stalled >= END_STABLE_CONFIRMATIONS) {
            stopNoMoreDrafts(scrolls, "stable-viewport-confirmed");
            return;
        }

        if (scrolls >= MAX_SCROLLS) {
            failSafe("Limite de sécurité de défilement atteinte avant confirmation de fin",
                    "DRAFT_SCAN_SCROLL_LIMIT");
            return;
        }

        boolean moved = performRobustScroll(list, sameViewport);
        if (moved) {
            Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, scrolls + 1).apply();
            DiagnosticLog.event(this, "LIST_SCROLL_ACCEPTED",
                    "page=" + (scrolls + 2) + " stable=" + same + " fallback=" + sameViewport);
            status("Défilement • page " + (scrolls + 2), "LIST_SCROLL");
            schedule(sameViewport ? 900 : 750);
            return;
        }

        DiagnosticLog.event(this, "LIST_SCROLL_RETRY",
                "stable=" + same + " stalled=" + stalled + " scrolls=" + scrolls);
        status("Défilement non confirmé • nouvelle tentative", "LIST_SCROLL_RETRY");
        schedule(650);
    }

    private void stopNoMoreDrafts(int scrolls, String reason) {
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putString(Prefs.STATUS, "Aucun autre brouillon détecté • fin confirmée")
                .apply();
        DiagnosticLog.event(this, "DRAFT_SCAN_END_CONFIRMED",
                "scrolls=" + scrolls + " reason=" + reason);
        DiagnosticLog.event(this, "NO_MORE_DRAFTS",
                "scrolls=" + scrolls + " reason=" + reason + " confirmed=true");
    }

    private boolean performRobustScroll(AccessibilityNodeInfo list, boolean preferGesture) {
        if (list == null) return false;

        if (preferGesture && dispatchListSwipe(list)) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE", "preferred-after-stall");
            return true;
        }

        try {
            if (list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                DiagnosticLog.event(this, "LIST_SCROLL_NODE", "ACTION_SCROLL_FORWARD");
                return true;
            }
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_NODE_FAILED", t.getClass().getSimpleName());
        }

        try {
            if (list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId())) {
                DiagnosticLog.event(this, "LIST_SCROLL_NODE", "ACTION_SCROLL_DOWN");
                return true;
            }
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_DOWN_FAILED", t.getClass().getSimpleName());
        }

        if (!preferGesture && dispatchListSwipe(list)) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE", "fallback-after-node-refusal");
            return true;
        }
        return false;
    }
'''
new = '''        Prefs.p(this).edit().putInt(Prefs.LIST_MISSING_COUNT, 0).apply();
        String viewport = viewportSignature(list);
        String previous = sp.getString(Prefs.LAST_VIEWPORT, "");
        boolean sameViewport = viewport.equals(previous) && !viewport.isEmpty();
        int same = sameViewport ? sp.getInt(Prefs.SAME_VIEWPORT_COUNT, 0) + 1 : 0;
        int stalled = sameViewport ? sp.getInt(Prefs.SCROLL_STALL_COUNT, 0) : 0;
        Prefs.p(this).edit()
                .putString(Prefs.LAST_VIEWPORT, viewport)
                .putInt(Prefs.SAME_VIEWPORT_COUNT, same)
                .apply();

        if (scrolls >= MAX_SCROLLS) {
            failSafe("Limite de sécurité de défilement atteinte avant confirmation de fin",
                    "DRAFT_SCAN_SCROLL_LIMIT");
            return;
        }

        // 2 = native accessibility scroll accepted, 1 = gesture dispatched,
        // 0 = every method refused. Native actions are always retried first on a
        // freshly acquired node. A dispatched gesture is only an attempt, not
        // proof that the viewport actually moved.
        int scrollResult = performRobustScroll(list);
        if (scrollResult == 2) {
            Prefs.p(this).edit()
                    .putInt(Prefs.SCROLL_COUNT, scrolls + 1)
                    .putInt(Prefs.SCROLL_STALL_COUNT, 0)
                    .apply();
            DiagnosticLog.event(this, "LIST_SCROLL_ACCEPTED",
                    "mode=node page=" + (scrolls + 2) + " stable=" + same);
            status("Défilement • page " + (scrolls + 2), "LIST_SCROLL");
            schedule(750);
            return;
        }

        if (sameViewport) stalled++;
        else stalled = 0;
        Prefs.p(this).edit().putInt(Prefs.SCROLL_STALL_COUNT, stalled).apply();

        if (scrollResult == 1) {
            Prefs.p(this).edit().putInt(Prefs.SCROLL_COUNT, scrolls + 1).apply();
            DiagnosticLog.event(this, "LIST_SCROLL_ACCEPTED",
                    "mode=gesture-unverified page=" + (scrolls + 2)
                            + " stable=" + same + " stalled=" + stalled);
            if (same >= END_STABLE_CONFIRMATIONS && stalled >= END_STABLE_CONFIRMATIONS) {
                stopNoMoreDrafts(scrolls + 1, "gesture-no-movement-confirmed");
                return;
            }
            status("Défilement gestuel • vérification du déplacement", "LIST_SCROLL");
            schedule(900);
            return;
        }

        if (same >= END_STABLE_CONFIRMATIONS && stalled >= END_STABLE_CONFIRMATIONS) {
            stopNoMoreDrafts(scrolls, "scroll-refused-and-viewport-stable");
            return;
        }

        DiagnosticLog.event(this, "LIST_SCROLL_RETRY",
                "stable=" + same + " stalled=" + stalled + " scrolls=" + scrolls);
        status("Défilement non confirmé • nouvelle tentative", "LIST_SCROLL_RETRY");
        schedule(650);
    }

    private void stopNoMoreDrafts(int scrolls, String reason) {
        Prefs.p(this).edit()
                .putBoolean(Prefs.RUNNING, false)
                .putString(Prefs.STATUS, "Aucun autre brouillon détecté • fin confirmée")
                .apply();
        DiagnosticLog.event(this, "DRAFT_SCAN_END_CONFIRMED",
                "scrolls=" + scrolls + " reason=" + reason);
        DiagnosticLog.event(this, "NO_MORE_DRAFTS",
                "scrolls=" + scrolls + " reason=" + reason + " confirmed=true");
    }

    private int performRobustScroll(AccessibilityNodeInfo list) {
        if (list == null) return 0;

        try {
            if (list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                DiagnosticLog.event(this, "LIST_SCROLL_NODE", "ACTION_SCROLL_FORWARD");
                return 2;
            }
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_NODE_FAILED", t.getClass().getSimpleName());
        }

        try {
            if (list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId())) {
                DiagnosticLog.event(this, "LIST_SCROLL_NODE", "ACTION_SCROLL_DOWN");
                return 2;
            }
        } catch (Throwable t) {
            DiagnosticLog.event(this, "LIST_SCROLL_DOWN_FAILED", t.getClass().getSimpleName());
        }

        if (dispatchListSwipe(list)) {
            DiagnosticLog.event(this, "LIST_SCROLL_GESTURE", "fallback-after-node-refusal");
            return 1;
        }
        return 0;
    }
'''
if old not in s:
    raise SystemExit('expected old scroll block not found; refusing to patch')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('scroll fix v2 applied')
