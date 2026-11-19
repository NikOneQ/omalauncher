package dev.local.omaplusperm;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * In system_server (Vector scope "System Framework"), answers "granted" when Android asks whether Omalauncher+
 * (dev.omalauncher.plus) holds CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS, the one permission its own launch animations
 * need. Android gives it only to system apps; this grants it without mounting anything into /system. Only that one
 * permission and that one app are touched; every other check returns what Android said. Disabled, or with Vector not
 * running, the launcher falls back to Android's simple scale-up.
 */
public class Module implements IXposedHookLoadPackage {
    private static final String TAG = "OmaPlusPerm: ";
    private static final String PKG = "dev.omalauncher.plus";
    private static final String PERMISSION = "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";
    private static final int FIRST_APP_UID = 10000;
    private static final int PER_USER_RANGE = 100000;

    private static final String[] CLASSES = {
        "com.android.server.pm.permission.PermissionManagerService",
        "com.android.server.pm.permission.PermissionManagerServiceImpl",
        "com.android.server.am.ActivityManagerService",
    };

    private static volatile int appId = -1;
    private static final ThreadLocal<Boolean> busy = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if ("com.android.systemui".equals(lpp.packageName)) {
            SystemUi.install(lpp.classLoader);
            return;
        }
        if (!"android".equals(lpp.packageName)) return;
        int hooked = 0;
        for (String name : CLASSES) {
            Class<?> c;
            try {
                c = Class.forName(name, false, lpp.classLoader);
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (!(n.equals("checkUidPermission") || n.equals("checkPermission")) || m.getReturnType() != int.class) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                grant(param);
                            } catch (Throwable t) {
                                // never break a permission check
                            }
                        }
                    });
                    hooked++;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "not hooked: " + m + ": " + t);
                }
            }
        }
        XposedBridge.log(TAG + "hooked " + hooked + " permission checks");
    }

    private static void grant(XC_MethodHook.MethodHookParam param) {
        Object result = param.getResult();
        if (!(result instanceof Integer) || (Integer) result == 0) return;
        boolean permission = false;
        boolean ours = false;
        int uid = -1;
        for (Object a : param.args) {
            if (PERMISSION.equals(a)) permission = true;
            else if (PKG.equals(a)) ours = true;
            // The uid is the last app uid among the numbers: checkPermission(permission, pid, uid), checkUidPermission(uid, …).
            else if (a instanceof Integer && (Integer) a >= FIRST_APP_UID) uid = (Integer) a;
        }
        if (!permission) return;
        if (!ours && uid >= FIRST_APP_UID) {
            int id = appId();
            ours = id > 0 && uid % PER_USER_RANGE == id;
        }
        if (ours) param.setResult(0);
    }

    /** Omalauncher+'s app id, looked up once it's installed. */
    private static int appId() {
        if (appId > 0 || Boolean.TRUE.equals(busy.get())) return appId;
        busy.set(true);
        try {
            Object pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
            int uid = (Integer) pm.getClass().getMethod("getPackageUid", String.class, long.class, int.class).invoke(pm, PKG, 0L, 0);
            if (uid >= FIRST_APP_UID) appId = uid % PER_USER_RANGE;
        } catch (Throwable t) {
            // not installed yet, or asked too early in boot
        } finally {
            busy.set(false);
        }
        return appId;
    }
}
