package dev.local.omaplusperm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.SparseArray;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * In SystemUI (Vector scope "System UI"): gives the back gesture to home to Omalauncher+ as SystemUI gives it to the
 * phone's own launcher. When Omalauncher+ starts it broadcasts READY; this asks its provider for its back callback and
 * animation runner and puts them in WMShell's back animation registry for returning home, which is what
 * setBackToLauncherCallback does for the phone's launcher once its binder caller's permission is checked. While
 * Omalauncher+ is the home app and registered, One UI Home's own registration is skipped so it doesn't take the gesture
 * back. Every failure leaves SystemUI as it was.
 */
final class SystemUi {
    private static final String TAG = "OmaPlusBack: ";
    private static final String PKG = "dev.omalauncher.plus";
    private static final Uri PROVIDER = Uri.parse("content://dev.omalauncher.plus.backtohome");
    private static final String READY = "dev.omalauncher.plus.BACK_TO_HOME_READY";
    private static final String IMPL = "com.android.wm.shell.back.BackAnimationController$IBackAnimationImpl";
    /** BackNavigationInfo.TYPE_RETURN_TO_HOME. */
    private static final int TYPE_RETURN_TO_HOME = 1;

    private static volatile Object controller;
    private static volatile Object remoteHandler;
    private static volatile Object closeFilter;
    private static volatile Context context;
    private static volatile boolean registered;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private SystemUi() {}

    /** SystemUI's context, once WMShell's back controller is made. */
    static Context context() {
        return context;
    }

    static void install(ClassLoader loader) {
        Class<?> c;
        try {
            c = Class.forName(IMPL, false, loader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "no " + IMPL + ": " + t);
            return;
        }
        BackPreview.install(loader);
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            XposedBridge.hookMethod(k, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        captured(param.args.length > 0 ? param.args[0] : null);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "not captured: " + t);
                    }
                }
            });
        }
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals("setBackToLauncherCallback")) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // Another launcher (One UI Home) registering: kept out while Omalauncher+ is home and has its own in.
                        if (registered && omalauncherIsHome()) {
                            XposedBridge.log(TAG + "kept " + PKG + "'s back animation over another launcher's");
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        // leave the call as it was
                    }
                }
            });
        }
        // Where launchers' transition filters live (what IShellTransitions.registerRemote fills for the phone's launcher).
        try {
            Class<?> handlerClass = Class.forName("com.android.wm.shell.transition.RemoteTransitionHandler", false, loader);
            for (Constructor<?> k : handlerClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(k, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        remoteHandler = param.thisObject;
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "no RemoteTransitionHandler: " + t);
        }
        // The controller itself is made as SystemUI starts; the binder interface only once a launcher connects.
        try {
            Class<?> controllerClass = Class.forName("com.android.wm.shell.back.BackAnimationController", false, loader);
            for (Constructor<?> k : controllerClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(k, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            captured(param.thisObject);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "controller not captured: " + t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "no BackAnimationController: " + t);
        }
        XposedBridge.log(TAG + "hooked " + IMPL);
    }

    private static void captured(Object owner) throws Exception {
        if (owner == null) return;
        controller = owner;
        if (context != null) return;
        Context ctx = (Context) owner.getClass().getMethod("getContext").invoke(owner);
        if (ctx == null) return;
        context = ctx;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                main.postDelayed(SystemUi::fetch, 500);
            }
        };
        ctx.registerReceiver(receiver, new IntentFilter(READY), Context.RECEIVER_EXPORTED);
        // Omalauncher+ may be up already (SystemUI restarted under it).
        main.postDelayed(SystemUi::fetch, 3000);
        XposedBridge.log(TAG + "listening for " + READY);
    }

    private static boolean omalauncherIsHome() {
        Context ctx = context;
        if (ctx == null) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = ctx.getPackageManager().resolveActivity(home, 0);
        return r != null && r.activityInfo != null && PKG.equals(r.activityInfo.packageName);
    }

    /** Asks Omalauncher+ for its back animation and registers it. */
    private static void fetch() {
        Context ctx = context;
        if (ctx == null || controller == null) return;
        try {
            if (!omalauncherIsHome()) return;
            Bundle b = ctx.getContentResolver().call(PROVIDER, "backToHome", null, null);
            if (b == null) {
                XposedBridge.log(TAG + PKG + " gave no back animation");
                return;
            }
            IBinder callback = b.getBinder("callback");
            IBinder runner = b.getBinder("runner");
            if (callback == null || runner == null) return;
            Object cb = asInterface("android.window.IOnBackInvokedCallback$Stub", callback);
            Object rn = asInterface("android.view.IRemoteAnimationRunner$Stub", runner);
            register(cb, rn);
            registered = true;
            // The back preview's settings and backdrop, ready before the first gesture.
            BackPreview.refreshConfig(ctx);
            callback.linkToDeath(() -> registered = false, 0);
            IBinder close = b.getBinder("close");
            if (close != null) registerClose(close);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            XposedBridge.log(TAG + "not registered: " + c);
            XposedBridge.log(c);
        }
    }

    /**
     * What setBackToLauncherCallback does once its caller's permission is checked (a check that only passes for a binder
     * call, so it fails from here): a runner in the back animation registry for returning home, on WMShell's thread.
     */
    @SuppressWarnings("unchecked")
    private static void register(Object callback, Object runner) throws Exception {
        Object owner = controller;
        Context ctx = context;
        if (owner == null || ctx == null) throw new IllegalStateException("no BackAnimationController yet");
        ClassLoader loader = owner.getClass().getClassLoader();
        Class<?> runnerClass = Class.forName("com.android.wm.shell.back.BackAnimationRunner", false, loader);
        Constructor<?> make = runnerClass.getDeclaredConstructor(
                Class.forName("android.window.IOnBackInvokedCallback"), Class.forName("android.view.IRemoteAnimationRunner"), Context.class);
        make.setAccessible(true);
        Object backRunner = make.newInstance(callback, runner, ctx);
        Object registry = field(owner, "mShellBackAnimationRegistry");
        Object executor = field(owner, "mShellExecutor");
        Runnable put = () -> {
            try {
                ((SparseArray<Object>) field(registry, "mAnimationDefinition")).put(TYPE_RETURN_TO_HOME, backRunner);
                Field changed = registry.getClass().getDeclaredField("mSupportedAnimatorsChanged");
                changed.setAccessible(true);
                changed.setBoolean(registry, true);
                Method update = registry.getClass().getDeclaredMethod("updateSupportedAnimators");
                update.setAccessible(true);
                update.invoke(registry);
                XposedBridge.log(TAG + "back to home now plays in " + PKG);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "registry not updated: " + t);
            }
        };
        Method execute = null;
        for (Class<?> k = executor.getClass(); k != null && execute == null; k = k.getSuperclass()) {
            for (Method m : k.getMethods()) if (m.getName().equals("execute") && m.getParameterCount() == 1) execute = m;
        }
        if (execute == null) throw new IllegalStateException("no ShellExecutor.execute");
        execute.setAccessible(true);
        execute.invoke(executor, put);
    }

    /**
     * Omalauncher+'s transition for an app closing as home comes forward, first among the shell's filters, as the
     * phone's launcher registers its own: home (activity type HOME) opening or to front, an app (STANDARD) closing or
     * to back.
     */
    @SuppressWarnings("unchecked")
    private static void registerClose(IBinder close) throws Exception {
        Object handler = remoteHandler;
        if (handler == null) {
            XposedBridge.log(TAG + "no RemoteTransitionHandler yet; closing stays Android's");
            return;
        }
        Class<?> iRemote = Class.forName("android.window.IRemoteTransition");
        Object remote = asInterface("android.window.IRemoteTransition$Stub", close);
        Object transition = Class.forName("android.window.RemoteTransition").getConstructor(iRemote, String.class).newInstance(remote, "OmalauncherPlusClose");
        Class<?> filterClass = Class.forName("android.window.TransitionFilter");
        Class<?> reqClass = Class.forName("android.window.TransitionFilter$Requirement");
        Object filter = filterClass.getConstructor().newInstance();
        Object home = reqClass.getConstructor().newInstance();
        Object app = reqClass.getConstructor().newInstance();
        reqClass.getField("mActivityType").setInt(home, 2);
        reqClass.getField("mModes").set(home, new int[] {1, 3});
        reqClass.getField("mActivityType").setInt(app, 1);
        reqClass.getField("mModes").set(app, new int[] {2, 4});
        Object reqs = java.lang.reflect.Array.newInstance(reqClass, 2);
        java.lang.reflect.Array.set(reqs, 0, home);
        java.lang.reflect.Array.set(reqs, 1, app);
        filterClass.getField("mRequirements").set(filter, reqs);
        filterClass.getField("mTypeSet").set(filter, new int[] {2, 4});
        Object pair = new android.util.Pair<>(filter, transition);
        Object executor = field(handler, "mMainExecutor");
        Runnable put = () -> {
            try {
                java.util.ArrayList<Object> filters = (java.util.ArrayList<Object>) field(handler, "mFilters");
                if (closeFilter != null) filters.remove(closeFilter);
                filters.add(0, pair);
                closeFilter = pair;
                XposedBridge.log(TAG + "apps closing to home now play in " + PKG + " (" + filters.size() + " filters)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "close filter not added: " + t);
            }
        };
        Runnable drop = () -> {
            try {
                ((java.util.ArrayList<Object>) field(handler, "mFilters")).remove(pair);
                if (closeFilter == pair) closeFilter = null;
            } catch (Throwable ignored) {
                // gone with SystemUI
            }
        };
        close.linkToDeath(() -> run(executor, drop), 0);
        run(executor, put);
    }

    private static void run(Object executor, Runnable r) {
        try {
            Method execute = null;
            for (Class<?> k = executor.getClass(); k != null && execute == null; k = k.getSuperclass()) {
                for (Method m : k.getMethods()) if (m.getName().equals("execute") && m.getParameterCount() == 1) execute = m;
            }
            if (execute == null) throw new IllegalStateException("no execute");
            execute.setAccessible(true);
            execute.invoke(executor, r);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "not run on the shell: " + t);
        }
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

    private static Object asInterface(String stub, IBinder binder) throws Exception {
        return Class.forName(stub).getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
}
