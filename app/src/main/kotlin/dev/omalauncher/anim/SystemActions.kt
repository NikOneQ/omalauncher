package dev.omalauncher.anim

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Omalauncher+: restarts from the animations screen, with root: SystemUI (after changing its Vector module), this
 * launcher, and a soft reboot, which restarts Android's zygote and with it every app but not the kernel, so a
 * temporary root stays. Not through ksud, whose soft reboot breaks this phone's temporary root.
 */
object SystemActions {
    private const val TAG = "SystemActions"

    /** SystemUI starts again by itself within a few seconds. */
    suspend fun restartSystemUi(): Boolean = root("pkill -f com.android.systemui || killall com.android.systemui")

    /** Everything above the kernel: like turning the phone off and on, quicker, keeping root. */
    suspend fun softReboot(): Boolean = root("setprop ctl.restart zygote")

    /** This launcher, and its Linux with it; a shell out of its process group opens it again. */
    suspend fun restartLauncher(context: Context): Boolean {
        val pkg = context.packageName
        val script =
            "for g in /sys/fs/cgroup/cgroup.procs /acct/cgroup.procs; do (echo \$\$ > \$g) 2>/dev/null; done; " +
                "sleep 0.4; am force-stop $pkg; sleep 0.4; am start -n $pkg/dev.omalauncher.MainActivity"
        return root("setsid sh -c '$script' >/dev/null 2>&1 < /dev/null &")
    }

    private suspend fun root(command: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0
            }.onFailure { Log.w(TAG, "\"$command\" failed", it) }.getOrDefault(false)
        }
}
