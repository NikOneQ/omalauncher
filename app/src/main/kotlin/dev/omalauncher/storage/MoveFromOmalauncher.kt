package dev.omalauncher.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Omalauncher+ installs next to Omalauncher (dev.omalauncher) as an app of its own. This copies everything of
 * Omalauncher's into it: widgets, home, style, themes and the whole Linux. Android gives no app another's data, so it
 * takes root: a detached root shell stops both launchers, copies the data folders into place beside this app's
 * (swapped in only once all copied, so a failed copy leaves both as they were), points proot's hard-link symlinks at
 * the new place (they hold absolute host paths; see Proot's linkStore), gives the files this app's owner and SELinux
 * label, and opens this launcher again. Omalauncher keeps its own copy.
 */
object MoveFromOmalauncher {
    private const val TAG = "MoveFromOmalauncher"
    const val SOURCE = "dev.omalauncher"

    /** The app-data folders moved; cache and code_cache are left to be made again. */
    private val FOLDERS = listOf("files", "shared_prefs", "databases", "no_backup")

    /** Whether Omalauncher is installed and this isn't it. */
    fun available(context: Context): Boolean =
        context.packageName != SOURCE && runCatching { context.packageManager.getPackageInfo(SOURCE, 0) }.isSuccess

    /**
     * Starts the move in a root shell that outlives this app, which it stops at once; false when su didn't answer as
     * root (no root, or the grant refused or not answered within a minute). The texts are the notification's.
     */
    suspend fun start(
        context: Context,
        moving: String,
        done: String,
        failed: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isRoot()) return@runCatching false
                val dir = context.cacheDir.apply { mkdirs() }
                val script = File(dir, "move-from-omalauncher.sh")
                script.writeText(script(context.packageName))
                val log = File(dir, "move-from-omalauncher.log")
                val args = listOf(moving, done, failed).joinToString(" ") { quote(it) }
                val p =
                    ProcessBuilder("su", "-c", "setsid sh ${quote(script.path)} $args > ${quote(log.path)} 2>&1 < /dev/null &")
                        .redirectErrorStream(true)
                        .start()
                p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0
            }.onFailure { Log.e(TAG, "couldn't start the move", it) }.getOrDefault(false)
        }

    private fun isRoot(): Boolean {
        val p = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroy()
            return false
        }
        return p.inputStream.bufferedReader().readText().trim() == "0"
    }

    /** A single-quoted shell word. */
    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** The root shell's script; $1, $2 and $3 are the notification's texts for moving, done and failed. */
    internal fun script(target: String): String =
        """
        |#!/system/bin/sh
        |SRC_PKG=$SOURCE
        |DST_PKG=$target
        |SRC=/data/data/${'$'}SRC_PKG
        |DST=/data/data/${'$'}DST_PKG
        |MOVING=${'$'}1 DONE=${'$'}2 FAILED=${'$'}3
        |# An app's root shell sees only that app's data (app data isolation): run in init's mount namespace instead.
        |if [ -z "${'$'}MOVE_GLOBAL_NS" ]; then export MOVE_GLOBAL_NS=1; exec nsenter -t 1 -m -- sh "${'$'}0" "${'$'}@"; fi
        |# Out of this app's process group first: force-stopping it kills everything in that group, root or not.
        |for g in /sys/fs/cgroup/cgroup.procs /acct/cgroup.procs /dev/cg2_bpf/cgroup.procs; do (echo ${'$'}${'$'} > "${'$'}g") 2>/dev/null; done
        |note() { cmd notification post -S bigtext -t 'Omalauncher+' omalauncher-move "${'$'}1" >/dev/null 2>&1; echo "${'$'}1"; }
        |reopen() { am start -n "${'$'}DST_PKG/dev.omalauncher.MainActivity" >/dev/null 2>&1; }
        |fail() { echo "failed: ${'$'}1"; for d in ${FOLDERS.joinToString(" ")}; do rm -rf "${'$'}DST/${'$'}d.moving"; done; note "${'$'}FAILED"; reopen; exit 1; }
        |[ -d "${'$'}SRC/files" ] || fail "no ${'$'}SRC/files"
        |[ -d "${'$'}DST" ] || fail "no ${'$'}DST"
        |OWNER=${'$'}(stat -c %u:%g "${'$'}DST") || fail "stat"
        |LABEL=${'$'}(ls -Zd "${'$'}DST" | awk '{print ${'$'}1}')
        |note "${'$'}MOVING"
        |am force-stop "${'$'}SRC_PKG"
        |am force-stop "${'$'}DST_PKG"
        |sleep 1
        |for d in ${FOLDERS.joinToString(" ")}; do
        |  [ -d "${'$'}SRC/${'$'}d" ] || continue
        |  rm -rf "${'$'}DST/${'$'}d.moving"
        |  cp -a "${'$'}SRC/${'$'}d" "${'$'}DST/${'$'}d.moving" || fail "copying ${'$'}d"
        |done
        |for d in ${FOLDERS.joinToString(" ")}; do
        |  [ -d "${'$'}DST/${'$'}d.moving" ] || continue
        |  rm -rf "${'$'}DST/${'$'}d"
        |  mv "${'$'}DST/${'$'}d.moving" "${'$'}DST/${'$'}d" || fail "placing ${'$'}d"
        |done
        |# proot's state for running guests, and what was in their /tmp.
        |rm -f "${'$'}DST/files/arch.fastpath"
        |rm -rf "${'$'}DST/files/tmp" && mkdir -p "${'$'}DST/files/tmp"
        |# Hard links in the guest are symlinks to absolute host paths (proot's --link2symlink).
        |for old in "${'$'}SRC" "/data/user/0/${'$'}SRC_PKG"; do
        |  case "${'$'}old" in /data/user/0/*) new="/data/user/0/${'$'}DST_PKG" ;; *) new="${'$'}DST" ;; esac
        |  find "${'$'}DST/files" -xdev -type l -lname "${'$'}old/*" 2>/dev/null | while IFS= read -r l; do
        |    t=${'$'}(readlink "${'$'}l")
        |    ln -sfn "${'$'}new${'$'}{t#${'$'}old}" "${'$'}l"
        |  done
        |done
        |for d in ${FOLDERS.joinToString(" ")}; do
        |  [ -e "${'$'}DST/${'$'}d" ] || continue
        |  chown -R -h "${'$'}OWNER" "${'$'}DST/${'$'}d"
        |  if [ -n "${'$'}LABEL" ]; then chcon -R -h "${'$'}LABEL" "${'$'}DST/${'$'}d" 2>/dev/null || restorecon -R "${'$'}DST/${'$'}d"; else restorecon -R "${'$'}DST/${'$'}d"; fi
        |done
        |echo moved
        |note "${'$'}DONE"
        |reopen
        |""".trimMargin()
}
