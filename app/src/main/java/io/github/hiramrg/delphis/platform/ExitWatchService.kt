package io.github.hiramrg.delphis.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Closing Delphis by swiping it away in Recent apps doesn't run "Exit Delphis", so Dolphin stayed open.
 * This small service (started while Delphis is open, not stopped with its task) hears that swipe and
 * removes Dolphin's card too — unless a game is running, which must keep going.
 */
class ExitWatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val main = rootIntent?.component?.className?.endsWith(".MainActivity") == true
        if (DolphinExitService.exitRunning) return // Exit Delphis already handled Dolphin
        if (!main || GameWatch.session != null) { CrashLog.event("Delphis swiped away${if (GameWatch.session != null) " during a game: Dolphin kept" else ""}"); return }
        val pkg = DolphinBridge(applicationContext, Prefs(applicationContext)).installedPackage() ?: return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("Dolphin")
        CrashLog.event("Delphis swiped away: closing $label too")
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { DolphinExitService.dismissFromRecents(label, recentsOpen = true) }
            stopSelf()
        }
    }
}
