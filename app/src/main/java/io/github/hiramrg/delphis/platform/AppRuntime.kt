package io.github.hiramrg.delphis.platform

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide setup: remembers the last crash (shown in Diagnostics), and frees cached cover
 * images when Android asks for memory back (for example while a game is running).
 */
class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Dolphin asks the trackpad server for its pad every second once it's set up; answer from the start.
        if (runCatching { Prefs(this).trackpadServerSetUp }.getOrDefault(false)) DsuServer.ensureStarted()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) CoverArt.clearMemory()
    }
}

/** Keeps the stack trace of the last crash or failed game launch so it can be copied from Diagnostics. */
object CrashLog {
    private var file: File? = null

    fun install(context: Context) {
        file = File(context.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            record("crash on ${thread.name}", e)
            previous?.uncaughtException(thread, e)
        }
    }

    fun record(what: String, e: Throwable) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file?.writeText("$time  $what (Android ${Build.VERSION.RELEASE})\n${e.stackTraceToString().take(6000)}")
        }
    }

    fun read(): String? = runCatching { file?.takeIf { it.exists() }?.readText() }.getOrNull()

    /** A short timestamped line in the event log (kept to the last 15 lines). */
    fun event(text: String) {
        runCatching {
            val f = File(file?.parentFile ?: return, "events.txt")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "$time  $text"
            f.writeText(lines.takeLast(15).joinToString("\n"))
        }
    }

    fun events(): String? = runCatching {
        File(file?.parentFile ?: return null, "events.txt").takeIf { it.exists() }?.readText()
    }.getOrNull()

    /**
     * Why Android last stopped Delphis' own process (crash, native crash, ANR, low memory...).
     * Android only shares this for our own app, not for Dolphin.
     */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "(needs Android 11+)"
        return runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            am.getHistoricalProcessExitReasons(null, 0, 5).joinToString("\n") { e ->
                val reason = when (e.reason) {
                    android.app.ApplicationExitInfo.REASON_CRASH -> "crash"
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    android.app.ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
                    android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
                    android.app.ApplicationExitInfo.REASON_SIGNALED -> "signaled"
                    android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "user/app update requested"
                    android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "force stopped"
                    android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "exited normally"
                    android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
                    android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
                    android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
                    else -> "other (${e.reason})"
                }
                "${fmt.format(Date(e.timestamp))}  $reason  importance=${e.importance}  ${e.description ?: ""}".trim()
            }.ifEmpty { "(none recorded)" }
        }.getOrDefault("(unavailable)")
    }

    /**
     * For the newest crash or "not responding" of Delphis: the trace Android saved (for an ANR,
     * what the main thread was doing; for a native crash, the tombstone). Trimmed to fit a report.
     */
    fun lastProblemTrace(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val e = am.getHistoricalProcessExitReasons(null, 0, 10).firstOrNull {
                it.reason == android.app.ApplicationExitInfo.REASON_ANR ||
                    it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
            } ?: return null
            val text = e.traceInputStream?.use { it.readBytes().decodeToString() } ?: return "(no trace saved)"
            val lines = text.lines()
            // ANR traces list every thread; the main thread is what matters most.
            val main = lines.indexOfFirst { it.startsWith("\"main\"") }
            val picked = if (main >= 0) lines.drop(main).takeWhile { it.isNotBlank() }.take(60) else lines.take(80)
            val head = lines.take(6).filter { it.isNotBlank() }
            (head + "..." + picked).joinToString("\n").take(6000)
        }.getOrElse { "(couldn't read trace: ${it.message})" }
    }
}

/**
 * The game that's running right now (set by the bottom-screen controls view), so other parts of the
 * app can notice when Dolphin's game screen disappears without Delphis closing it, for example
 * when Dolphin crashes.
 */
object GameWatch {
    @Volatile var session: GameSession? = null
    /** True when the game stopped without Delphis asking; the bottom screen offers a restart. */
    val unexpectedStop = androidx.compose.runtime.mutableStateOf(false)

    /**
     * True while the player has left the game for the home screen or another app (the game is
     * still running in Dolphin, just in the background). The bottom screen hides meanwhile.
     */
    val suspended = androidx.compose.runtime.mutableStateOf(false)
    /** Set by the in-game bottom screen: called with true when the game goes to the background. */
    @Volatile var onSuspended: ((Boolean) -> Unit)? = null

    fun setSuspended(value: Boolean) {
        if (session == null || suspended.value == value) return
        suspended.value = value
        CrashLog.event(if (value) "game moved to the background (home screen or another app)" else "game back on screen")
        onSuspended?.invoke(value)
    }

    fun reportGone(how: String) {
        val s = session ?: return
        if (GameLauncher.busy || unexpectedStop.value) return
        CrashLog.event("Game stopped unexpectedly (${s.info.gameId ?: s.fileName}): $how")
        unexpectedStop.value = true
    }
}

/**
 * Runs the game launch / restart / close sequences.
 *
 * They run in an app-wide scope instead of an Activity's, so a screen closing (or being moved to the
 * other display) halfway through can't cancel a launch after Dolphin was already closed. A lock
 * makes a second request wait for the first instead of interleaving with it, and an error shows a
 * message and is logged instead of crashing the app.
 */
object GameLauncher {
    private val lock = Mutex()
    /** True while a launch/restart/close sequence is running (so its own screen changes aren't "unexpected"). */
    @Volatile var busy = false
        private set
    private lateinit var appContext: Context

    private val errors = CoroutineExceptionHandler { _, e ->
        CrashLog.record("game launch failed", e)
        if (::appContext.isInitialized) {
            io.github.hiramrg.delphis.ui.Notices.problem(appContext, "Something went wrong", e.message ?: e.javaClass.simpleName)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errors)

    fun run(context: Context, block: suspend () -> Unit) {
        appContext = context.applicationContext
        scope.launch {
            lock.withLock {
                busy = true
                try { block() } finally { busy = false }
            }
        }
    }
}
