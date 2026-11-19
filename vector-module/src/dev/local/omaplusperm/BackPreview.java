package dev.local.omaplusperm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * In SystemUI: for apps that handle back themselves (BackNavigationInfo TYPE_CALLBACK, where Android plays no animation
 * of its own), the app's window shrinks under the finger during the back gesture, rounded, as it does for apps that
 * hand back to Android. Let go: it draws in a little more at once and Omalauncher+ is told where it will be (its
 * provider's "handoff"), so if the app closes its closing goes on from there into the icon; if the app is still in
 * front a moment later (back stayed inside it), the window springs back. Cancelled, it springs back at once.
 * How it moves comes from Omalauncher+'s animations.back-* settings, read at each gesture's start.
 */
final class BackPreview {
    private static final String TAG = "OmaPlusPreview: ";
    private static final Uri PROVIDER = Uri.parse("content://dev.omalauncher.plus.backtohome");
    private static final int TYPE_CALLBACK = 4;

    /** A way the window moves: how much it shrinks, moves from the edge, and follows the finger across and down. */
    private static final class Look {
        final float shrink, shift, followX, followY, commit;

        Look(float shrink, float shift, float followX, float followY, float commit) {
            this.shrink = shrink;
            this.shift = shift;
            this.followX = followX;
            this.followY = followY;
            this.commit = commit;
        }
    }

    private static Look look(String name) {
        switch (name) {
            case "slide": return new Look(0.08f, 0.10f, 0f, 0.10f, 0.04f);
            case "subtle": return new Look(0.06f, 0.02f, 0f, 0.05f, 0.03f);
            case "follow": return new Look(0.12f, 0f, 0.35f, 0.35f, 0.05f);
            default: return new Look(0.16f, 0.04f, 0f, 0.20f, 0.06f);
        }
    }

    private static volatile Object organizer;
    private static volatile Object displayAreas;
    /** Home's background, small and blurred, from Omalauncher+ (its provider's "backdrop"). */
    private static volatile Bitmap backdropBitmap;
    /** That background behind the shrinking window, instead of the black Android leaves there. */
    private static SurfaceControl backdrop;
    private static Method setRelativeLayer;
    // Omalauncher+'s settings, refreshed in the background at each gesture's start.
    private static volatile boolean enabled = true;
    private static volatile Look style = look("card");
    private static volatile float strength = 1f;
    private static volatile float cornerDp = 28f;

    // Reflection, looked up once.
    private static Field infoField;
    private static Method getType, getActiveTracker, isActive, createProgressEvent, getProgress, getTouchX, getTouchY, getSwipeEdge, setCornerRadius;

    // The gesture under way, on WMShell's thread.
    private static final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
    private static SurfaceControl leash;
    private static Rect bounds;
    private static Point base;
    private static float density = 3f;
    private static float startX = -1, startY = -1;
    private static float scale = 1, dx, dy, corner;
    private static boolean previewing;
    private static ValueAnimator running;

    private BackPreview() {}

    static void install(ClassLoader loader) {
        try {
            Class<?> org = Class.forName("com.android.wm.shell.ShellTaskOrganizer", false, loader);
            for (Constructor<?> k : org.getDeclaredConstructors()) {
                XposedBridge.hookMethod(k, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        organizer = param.thisObject;
                    }
                });
            }
            try {
                Class<?> areas = Class.forName("com.android.wm.shell.RootTaskDisplayAreaOrganizer", false, loader);
                for (Constructor<?> k : areas.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(k, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            displayAreas = param.thisObject;
                        }
                    });
                }
                setRelativeLayer = SurfaceControl.Transaction.class.getMethod("setRelativeLayer", SurfaceControl.class, SurfaceControl.class, int.class);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "no backdrop: " + t);
            }
            Class<?> c = Class.forName("com.android.wm.shell.back.BackAnimationController", false, loader);
            infoField = c.getDeclaredField("mBackNavigationInfo");
            infoField.setAccessible(true);
            getActiveTracker = c.getMethod("getActiveTracker");
            try {
                setCornerRadius = SurfaceControl.Transaction.class.getMethod("setCornerRadius", SurfaceControl.class, float.class);
            } catch (Throwable ignored) {
                // square corners then
            }
            for (Method m : c.getDeclaredMethods()) {
                switch (m.getName()) {
                    case "onMotionEvent":
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    moved(param.thisObject);
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "move: " + t);
                                    previewing = false;
                                }
                            }
                        });
                        break;
                    case "dispatchOnBackInvoked":
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    released();
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "release: " + t);
                                }
                            }
                        });
                        break;
                    case "tryDispatchOnBackCancelled":
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (previewing) springBack();
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "cancel: " + t);
                                }
                            }
                        });
                        break;
                    case "finishBackNavigation":
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                // Ended some other way: never leave the window small.
                                if (previewing) checkLater(200);
                            }
                        });
                        break;
                    default:
                        break;
                }
            }
            XposedBridge.log(TAG + "hooked the back gesture");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "not hooked: " + t);
        }
    }

    private static void moved(Object controller) throws Exception {
        Object info = infoField.get(controller);
        if (info == null) return;
        if (getType == null) getType = info.getClass().getMethod("getType");
        if ((Integer) getType.invoke(info) != TYPE_CALLBACK) return;
        Object tracker = getActiveTracker.invoke(controller);
        if (tracker == null) return;
        if (isActive == null) {
            isActive = tracker.getClass().getMethod("isActive");
            createProgressEvent = tracker.getClass().getMethod("createProgressEvent");
        }
        if (!(Boolean) isActive.invoke(tracker)) return;
        Object e = createProgressEvent.invoke(tracker);
        if (getProgress == null) {
            Class<?> ev = e.getClass();
            getProgress = ev.getMethod("getProgress");
            getTouchX = ev.getMethod("getTouchX");
            getTouchY = ev.getMethod("getTouchY");
            getSwipeEdge = ev.getMethod("getSwipeEdge");
        }
        float p = (Float) getProgress.invoke(e);
        float x = (Float) getTouchX.invoke(e);
        float y = (Float) getTouchY.invoke(e);
        if (!previewing) {
            if (!enabled || !begin()) return;
            Context ctx = SystemUi.context();
            if (ctx != null) {
                density = ctx.getResources().getDisplayMetrics().density;
                refreshConfig(ctx);
            }
            if (running != null) running.cancel();
            startX = x;
            startY = y;
            previewing = true;
            showBackdrop();
        }
        backdropAlpha(Math.min(1f, p * 5));
        int edge = (Integer) getSwipeEdge.invoke(e);
        Look l = style;
        float k = strength;
        scale = 1 - l.shrink * k * p;
        float limitX = bounds.width() * 0.08f, limitY = bounds.height() * 0.06f;
        dx = bounds.width() * l.shift * k * p * (edge == 0 ? 1 : -1) + clamp((x - startX) * l.followX * k, -limitX, limitX);
        dy = clamp((y - startY) * l.followY * k, -limitY, limitY);
        corner = cornerDp * density * Math.min(1f, p * 4);
        apply(scale, dx, dy, corner);
    }

    /** The focused app's task: its surface, bounds and place in its parent. */
    private static boolean begin() throws Exception {
        Object org = organizer;
        if (org == null) return false;
        SparseArray<?> tasks = (SparseArray<?>) field(org, "mTasks");
        for (int i = 0; i < tasks.size(); i++) {
            Object appeared = tasks.valueAt(i);
            Object info = appeared.getClass().getMethod("getTaskInfo").invoke(appeared);
            if (info == null) continue;
            if (!Boolean.TRUE.equals(field(info, "isFocused")) || !Boolean.TRUE.equals(field(info, "isVisible"))) continue;
            if ((Integer) field(info, "topActivityType") != 1) continue;
            if ((Integer) info.getClass().getMethod("getWindowingMode").invoke(info) != 1) continue;
            Object window = field(field(info, "configuration"), "windowConfiguration");
            bounds = new Rect((Rect) window.getClass().getMethod("getBounds").invoke(window));
            Point at = (Point) field(info, "positionInParent");
            base = at != null ? new Point(at) : new Point();
            leash = (SurfaceControl) appeared.getClass().getMethod("getLeash").invoke(appeared);
            Object id = field(info, "displayId");
            displayId = id instanceof Integer ? (Integer) id : 0;
            return leash != null && !bounds.isEmpty();
        }
        return false;
    }

    private static int displayId;

    /**
     * Home's blurred background just under the app's window, filling the screen, drawn once per gesture into a layer of
     * the app's display area; it fades in as the window shrinks. Without Omalauncher+'s picture it stays as Android had it.
     */
    private static void showBackdrop() {
        removeBackdrop();
        Bitmap bmp = backdropBitmap;
        Object areas = displayAreas;
        if (bmp == null || areas == null || setRelativeLayer == null) return;
        try {
            int w = bounds.width(), h = bounds.height();
            SurfaceControl.Builder b = new SurfaceControl.Builder()
                    .setName("OmalauncherPlusBackdrop")
                    .setBufferSize(w, h)
                    .setFormat(PixelFormat.RGBA_8888)
                    .setOpaque(true);
            areas.getClass().getMethod("attachToDisplayArea", int.class, SurfaceControl.Builder.class).invoke(areas, displayId, b);
            SurfaceControl sc = b.build();
            Surface surface = new Surface(sc);
            Canvas c = surface.lockHardwareCanvas();
            // Centre-cropped to the screen, as home draws it, and a little darker, so the window stands out.
            float k = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
            float bw = bmp.getWidth() * k, bh = bmp.getHeight() * k;
            RectF to = new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
            c.drawBitmap(bmp, null, to, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.drawColor(0x40000000);
            surface.unlockCanvasAndPost(c);
            surface.release();
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setPosition(sc, bounds.left, bounds.top);
            t.setAlpha(sc, 0f);
            t.setVisibility(sc, true);
            setRelativeLayer.invoke(t, sc, leash, -1);
            t.apply();
            backdrop = sc;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "backdrop not shown: " + t);
        }
    }

    private static synchronized void backdropAlpha(float a) {
        SurfaceControl sc = backdrop;
        if (sc == null || !sc.isValid()) return;
        tx.setAlpha(sc, a);
        tx.apply();
    }

    private static synchronized void removeBackdrop() {
        SurfaceControl sc = backdrop;
        backdrop = null;
        if (sc == null) return;
        if (sc.isValid()) {
            tx.reparent(sc, null);
            tx.apply();
        }
        sc.release();
    }

    /** The window scaled about its middle, moved, cropped to itself so its corners can round. */
    private static synchronized void apply(float s, float x, float y, float radius) {
        SurfaceControl sc = leash;
        if (sc == null || !sc.isValid()) return;
        tx.setCrop(sc, s < 1f ? new Rect(0, 0, bounds.width(), bounds.height()) : null);
        tx.setScale(sc, s, s);
        tx.setPosition(sc, base.x + bounds.width() * (1 - s) / 2 + x, base.y + bounds.height() * (1 - s) / 2 + y);
        if (setCornerRadius != null) {
            try {
                setCornerRadius.invoke(tx, sc, s > 0 ? radius / s : radius);
            } catch (Throwable ignored) {
                // square corners then
            }
        }
        tx.apply();
    }

    /** Let go: draw in a little more at once, tell Omalauncher+ where the window ends up, then see whether the app closed. */
    private static void released() {
        if (!previewing) return;
        previewing = false;
        final float s0 = scale, s1 = scale - style.commit * strength;
        final float x0 = dx, y0 = dy, r0 = corner;
        Context ctx = SystemUi.context();
        if (ctx != null) {
            float w = bounds.width() * s1;
            float h = bounds.height() * s1;
            float left = bounds.left + bounds.width() * (1 - s1) / 2 + dx;
            float top = bounds.top + bounds.height() * (1 - s1) / 2 + dy;
            Bundle b = new Bundle();
            b.putFloat("left", left);
            b.putFloat("top", top);
            b.putFloat("right", left + w);
            b.putFloat("bottom", top + h);
            b.putFloat("corner", corner);
            try {
                ctx.getContentResolver().call(PROVIDER, "handoff", null, b);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "no handoff: " + t);
            }
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(110);
        a.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        a.addUpdateListener(v -> {
            float f = (Float) v.getAnimatedValue();
            scale = s0 + (s1 - s0) * f;
            apply(scale, x0, y0, r0);
        });
        running = a;
        a.start();
        checkLater(190);
    }

    /** Still in front a moment after letting go: back stayed in the app, so its window comes back. */
    private static void checkLater(long ms) {
        previewing = false;
        Looper looper = Looper.myLooper();
        if (looper == null) looper = Looper.getMainLooper();
        final SurfaceControl was = leash;
        new Handler(looper).postDelayed(() -> {
            try {
                if (was != leash || was == null) return;
                if (stillInFront()) {
                    springBack();
                } else {
                    // The app closed: Omalauncher+ shows home now, under where the backdrop was.
                    removeBackdrop();
                    restoreLater(was);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "check: " + t);
            }
        }, ms);
    }

    private static boolean stillInFront() throws Exception {
        Object org = organizer;
        if (org == null) return true;
        SparseArray<?> tasks = (SparseArray<?>) field(org, "mTasks");
        for (int i = 0; i < tasks.size(); i++) {
            Object appeared = tasks.valueAt(i);
            if (appeared.getClass().getMethod("getLeash").invoke(appeared) != leash) continue;
            Object info = appeared.getClass().getMethod("getTaskInfo").invoke(appeared);
            return Boolean.TRUE.equals(field(info, "isFocused")) && Boolean.TRUE.equals(field(info, "isVisible"));
        }
        return false;
    }

    /** The window back to full size, quickly and softly. */
    private static void springBack() {
        previewing = false;
        if (running != null) running.cancel();
        final float s0 = scale, x0 = dx, y0 = dy, r0 = corner;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(220);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> {
            float f = (Float) v.getAnimatedValue();
            apply(s0 + (1 - s0) * f, x0 * (1 - f), y0 * (1 - f), r0 * (1 - f));
            backdropAlpha(1 - f);
            if (f >= 1f) removeBackdrop();
        });
        running = a;
        a.start();
        scale = 1;
        dx = 0;
        dy = 0;
        corner = 0;
    }

    /** A closed app's window is whole again once its closing is done, for whatever shows it next. */
    private static void restoreLater(SurfaceControl was) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (leash == was) apply(1, 0, 0, 0);
            scale = 1;
            dx = 0;
            dy = 0;
            corner = 0;
        }, 1600);
    }

    static void refreshConfig(Context ctx) {
        new Thread(() -> {
            try {
                Bundle b = ctx.getContentResolver().call(PROVIDER, "config", null, null);
                if (b == null) return;
                enabled = b.getBoolean("preview", true);
                style = look(b.getString("backStyle", "card"));
                strength = b.getFloat("backStrength", 1f);
                cornerDp = b.getFloat("corners", 28f);
                Bundle pic = ctx.getContentResolver().call(PROVIDER, "backdrop", null, null);
                Bitmap bmp = pic != null ? pic.getParcelable("bitmap") : null;
                if (bmp != null) backdropBitmap = bmp;
            } catch (Throwable ignored) {
                // keep the last answer
            }
        }).start();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // up to the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
