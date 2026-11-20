package io.github.hiramrg.delphis.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Closing Delphis by swiping it away in Recent apps doesn't run "Exit Delphis". This small service (started while
 * Delphis is open, not stopped with its task) hears that swipe and logs it; on older Androids (the Thor's 13) it also
 * ends Dolphin's background process. Dolphin's card is left in Recent apps, like after Exit.
 */
class ExitWatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val main = rootIntent?.component?.className?.endsWith(".MainActivity") == true
        if (DolphinExitService.exitRunning) return // Exit Delphis already handled Dolphin
        if (!main || GameWatch.session != null) { CrashLog.event("Delphis swiped away${if (GameWatch.session != null) " during a game: Dolphin kept" else ""}"); return }
        val pkg = DolphinBridge(applicationContext, Prefs(applicationContext)).installedPackage() ?: return
        CrashLog.event("Delphis swiped away")
        runCatching { getSystemService(android.app.ActivityManager::class.java).killBackgroundProcesses(pkg) }
        stopSelf()
    }
}
