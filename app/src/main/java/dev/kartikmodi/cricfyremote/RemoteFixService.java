package dev.kartikmodi.cricfyremote;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The accessibility service that hosts {@link CricfyRemote}. It only looks at CRICFy TV's windows,
 * reads nothing else, sends nothing anywhere, and lets every key outside that app pass through.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services dev.kartikmodi.cricfyremote/.RemoteFixService
 *   settings put secure accessibility_enabled 1
 */
public class RemoteFixService extends AccessibilityService {

    private CricfyRemote cricfy;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        cricfy = new CricfyRemote(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // onKey checks the window in front itself: after an install no window event has arrived yet.
        return cricfy != null && cricfy.onKey(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (cricfy == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        if (CricfyRemote.PKG.contentEquals(pkg)) cricfy.onWindowChanged(cls);
        // The outline itself is a window of this package; only a real screen of another app ends it.
        else if (!"android.widget.FrameLayout".contentEquals(cls) && !getPackageName().contentEquals(pkg)) cricfy.stop();
    }

    @Override
    public void onInterrupt() {
    }
}
