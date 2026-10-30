package dev.kartikmodi.cricfyremote;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * CRICFy TV's "Multiple Links Available" dialog cannot be used with a remote: its full-screen backdrop
 * is clickable, so on Android 8+ it takes focus, OK lands on it and closes the dialog, and the rows
 * never show a highlight. The app itself is left untouched. Instead, while that dialog is open, focus
 * goes to the list (whose own D-pad handling then
 * works: Up/Down move, OK plays) and an outline is drawn over the selected row or Cancel.
 *
 * Its player has the same problem with the full-screen button, which the remote cannot reach: when a
 * stream starts, the button is pressed once on the person's behalf.
 */
final class CricfyRemote {

    static final String PKG = "app.cricfy.tv";
    private static final String LIST = PKG + ":id/link_listView";
    private static final String CANCEL = PKG + ":id/link_cancel";
    private static final String ROOT = PKG + ":id/dialog_link_root";
    private static final String CARD = PKG + ":id/dialog_link_card";
    /** While the dialog is open: how often to check it is still there and the outline is in place. */
    private static final long POLL_MS = 400;
    private static final String PLAYER_LAYOUT = PKG + ":id/playerLayout";
    private static final String PLAYER_VIEW = PKG + ":id/player_view";
    private static final String FULLSCREEN = PKG + ":id/exo_fullscreen_btn";
    /** Tries, half a second apart, while the player starts and its controls appear. */
    private static final int FULLSCREEN_TRIES = 10;
    private final Runnable fullscreen = this::fullscreen;
    private int fullscreenTries;

    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private Outline outline;

    CricfyRemote(AccessibilityService service) {
        this.service = service;
    }

    /** A window of the app changed: the dialog may have opened, or a stream started. */
    void onWindowChanged(CharSequence cls) {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 150);
        if (cls.toString().endsWith(".PlayerActivity")) {
            fullscreenTries = FULLSCREEN_TRIES;
            handler.removeCallbacks(fullscreen);
            handler.postDelayed(fullscreen, 800);
        }
    }

    /** Press the player's full-screen button once, showing its controls first when they are hidden. */
    private void fullscreen() {
        if (fullscreenTries-- <= 0) return;
        AccessibilityNodeInfo layout = find(PLAYER_LAYOUT);
        if (layout != null) {
            Rect r = new Rect();
            layout.getBoundsInScreen(r);
            if (r.height() >= service.getResources().getDisplayMetrics().heightPixels * 0.95f) return;   // already full
            AccessibilityNodeInfo button = find(FULLSCREEN);
            if (button != null && button.isVisibleToUser()) {
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                fullscreenTries = Math.min(fullscreenTries, 2);   // check once more that it took
            } else {
                AccessibilityNodeInfo player = find(PLAYER_VIEW);
                if (player != null) player.performAction(AccessibilityNodeInfo.ACTION_CLICK);   // shows the controls
            }
        }
        handler.postDelayed(fullscreen, 500);
    }

    /** Another app is in front: nothing to do, and no outline left behind. */
    void stop() {
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(fullscreen);
        hideOutline();
    }

    /**
     * Called for D-pad keys while the app is in front. Returns true to swallow the key: only when the
     * dialog is open and focus sits on its backdrop, where the key would close it.
     */
    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN
                && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
        AccessibilityNodeInfo list = find(LIST);
        if (list == null) return false;
        if (onBackdrop()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) list.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            handler.removeCallbacks(refresh);
            handler.postDelayed(refresh, 60);
            return true;
        }
        // Let the list move its selection, then follow it with the outline.
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 60);
        return false;
    }

    private void refresh() {
        AccessibilityNodeInfo list = find(LIST);
        if (list == null) {
            hideOutline();
            return;
        }
        if (onBackdrop()) list.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Rect r = selectedBounds(list);
        if (r != null) showOutline(r); else hideOutline();
        handler.postDelayed(refresh, POLL_MS);
    }

    /** The selected row while the list has focus, or Cancel when it has. */
    private Rect selectedBounds(AccessibilityNodeInfo list) {
        Rect r = new Rect();
        AccessibilityNodeInfo cancel = find(CANCEL);
        if (cancel != null && cancel.isFocused()) {
            cancel.getBoundsInScreen(r);
            return r;
        }
        if (!list.isFocused()) return null;
        for (int i = 0; i < list.getChildCount(); i++) {
            AccessibilityNodeInfo row = list.getChild(i);
            if (row != null && row.isSelected()) {
                row.getBoundsInScreen(r);
                Rect clip = new Rect();
                list.getBoundsInScreen(clip);
                return r.intersect(clip) ? r : null;
            }
        }
        return null;
    }

    private boolean onBackdrop() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo focused = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) return true;
        String id = focused.getViewIdResourceName();
        return ROOT.equals(id) || CARD.equals(id);
    }

    private AccessibilityNodeInfo find(String id) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !PKG.contentEquals(root.getPackageName())) return null;
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(id);
        return found == null || found.isEmpty() ? null : found.get(0);
    }

    private void showOutline(Rect r) {
        WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        float density = service.getResources().getDisplayMetrics().density;
        int pad = Math.round(3 * density);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                r.width() + 2 * pad, r.height() + 2 * pad,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = r.left - pad;
        lp.y = r.top - pad;
        try {
            if (outline == null) {
                outline = new Outline(service, density);
                wm.addView(outline, lp);
            } else {
                wm.updateViewLayout(outline, lp);
            }
        } catch (RuntimeException e) {
            outline = null;   // the overlay is a nicety; never take the service down over it
        }
    }

    private void hideOutline() {
        if (outline == null) return;
        try {
            ((WindowManager) service.getSystemService(Context.WINDOW_SERVICE)).removeView(outline);
        } catch (RuntimeException ignored) {
        }
        outline = null;
    }

    /** The app's own orange, as a rounded stroke with a faint fill. */
    private static final class Outline extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private final RectF box = new RectF();

        Outline(Context context, float density) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3 * density);
            stroke.setColor(0xFFFF6A2B);
            fill.setColor(0x33FF6A2B);
            radius = 10 * density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float half = stroke.getStrokeWidth() / 2;
            box.set(half, half, getWidth() - half, getHeight() - half);
            canvas.drawRoundRect(box, radius, radius, fill);
            canvas.drawRoundRect(box, radius, radius, stroke);
        }
    }
}
