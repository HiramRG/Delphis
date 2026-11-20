package io.github.hiramrg.delphis.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from the project's GitHub release ("latest" tag). The release notes carry
 * `versionCode: N` (written by the CI workflow), which is compared with the installed version.
 * The APK is downloaded and handed to Android's PackageInstaller, which asks the user to confirm.
 */
class Updater(private val context: Context) {
    companion object {
        const val APK_NAME = "Delphis.apk"
        const val ACTION_INSTALL_STATUS = "io.github.hiramrg.delphis.INSTALL_STATUS"

        /** The release tag of a channel: "latest" for Stable, "beta" for Beta (a GitHub pre-release). */
        fun tag(channel: String) = if (channel == "beta") "beta" else "latest"
        fun releaseApi(channel: String) = "https://api.github.com/repos/HiramRG/Delphis/releases/tags/${tag(channel)}"
        fun releasePage(channel: String) = "https://github.com/HiramRG/Delphis/releases/tag/${tag(channel)}"
        /** The channel's newest APK, for a download in the browser. */
        fun apkUrl(channel: String) = "https://github.com/HiramRG/Delphis/releases/download/${tag(channel)}/$APK_NAME"
        val RELEASE_PAGE get() = releasePage("stable")
        val APK_URL get() = apkUrl("stable")

        /** "stable" or "beta": the channel this APK was built for (CI's -Pchannel). */
        fun builtChannel(context: Context): String =
            runCatching { context.getString(io.github.hiramrg.delphis.R.string.build_channel) }.getOrDefault("stable")

        /**
         * Some Android versions (e.g. Android 17 on a Galaxy Tab) refuse to let an app installed from a downloaded
         * file update itself ("Self update is blocked by unknown source package"). Then the downloaded APK goes to
         * Android's own installer (the same screen as opening the file), and a notice offers the browser download.
         */
        fun installWithSystem(context: Context) {
            val apk = File(File(context.cacheDir, "updates"), APK_NAME)
            val opened = apk.exists() && runCatching {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { CrashLog.event("system installer didn't open: ${it.message}") }.isSuccess
            io.github.hiramrg.delphis.ui.Notices.info(
                context, if (opened) "Finish the update in Android's installer" else "Android blocked the update",
                if (opened) "Android won't let Delphis update itself here, so its own installer took over: tap Update. If it doesn't work, download it instead."
                else "Android won't let Delphis update itself here. Download the new version and open it to install.",
                "Download",
            ) { openDownload(context) }
        }

        fun openDownload(context: Context) {
            val url = apkUrl(Prefs(context).updateChannel)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    data class Release(val versionCode: Int, val apkUrl: String, val sizeBytes: Long, val publishedAt: String)

    sealed interface Check {
        data class UpdateAvailable(val release: Release) : Check
        data class UpToDate(val installed: Int) : Check
        data class Failed(val reason: String) : Check
    }

    fun installedVersionCode(): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

    fun installedVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** The newest build on [channel] (null when it can't be read, e.g. offline or no beta yet). */
    suspend fun peek(channel: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            TestHooks.net()
            val conn = (URL(releaseApi(channel)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Delphis-updater")
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            Regex("""versionCode:\s*(\d+)""").find(json.optString("body"))?.groupValues?.get(1)?.toInt()
        }.getOrNull()
    }

    /** Checks the chosen update channel (Settings → Updates). */
    suspend fun check(channel: String = Prefs(context).updateChannel): Check = withContext(Dispatchers.IO) {
        try {
            TestHooks.net()
            val conn = (URL(releaseApi(channel)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Delphis-updater")
            }
            if (conn.responseCode == 404 && channel == "beta") return@withContext Check.Failed("There's no beta build right now")
            if (conn.responseCode != 200) return@withContext Check.Failed("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = Regex("""versionCode:\s*(\d+)""").find(json.optString("body"))?.groupValues?.get(1)?.toInt()
                ?: return@withContext Check.Failed("The release doesn't say which version it is")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name") == APK_NAME }
                ?: return@withContext Check.Failed("No $APK_NAME in the release")
            val release = Release(code, apk.getString("browser_download_url"), apk.optLong("size"), json.optString("published_at"))
            val installed = installedVersionCode()
            if (code > installed) Check.UpdateAvailable(release) else Check.UpToDate(installed)
        } catch (e: Exception) {
            Check.Failed(if (e is java.net.UnknownHostException) "No internet connection" else (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Downloads the APK, reporting progress 0..1. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, APK_NAME)
        var url = URL(release.apkUrl)
        var conn: HttpURLConnection
        // GitHub redirects release downloads to its CDN; follow a few hops manually (https -> https).
        var hops = 0
        while (true) {
            TestHooks.net()
            // Only GitHub over https, for the first address and every redirect.
            if (!io.github.hiramrg.delphis.core.UpdateSource.allowed(url)) {
                CrashLog.event("update refused: download moved to ${url.host}")
                error("The download didn't come from GitHub, so it wasn't used. Nothing was installed.")
            }
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10000; readTimeout = 20000
                setRequestProperty("User-Agent", "Delphis-updater")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code != 200) error("Download failed ($code)")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        verify(file)?.let { why ->
            file.delete()
            CrashLog.event("update refused: $why")
            error("The download didn't pass Delphis' check ($why). Nothing was installed.")
        }
        file
    }

    /**
     * Checks a downloaded APK before it goes to Android: it must be Delphis, signed with Delphis' own key, and not
     * older than this build. Returns why it failed, or null when it's fine. (Android refuses a different key anyway;
     * this stops a bad file early, with a clear message, on every install path.)
     */
    @Suppress("DEPRECATION")
    fun verify(apk: File): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return "not an Android app"
        if (archive.packageName != context.packageName) return "it's a different app"
        val mine = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull() ?: return null
        if (archive.longVersionCode < mine.longVersionCode) return "it's an older build"
        fun certs(info: android.content.pm.PackageInfo): Set<String> {
            val si = info.signingInfo ?: return emptySet()
            val all = (si.apkContentsSigners.orEmpty() + if (si.hasMultipleSigners()) emptyArray() else si.signingCertificateHistory.orEmpty())
            return all.map { it.toCharsString() }.toSet()
        }
        val theirs = certs(archive)
        // Some older Androids can't read an unopened APK's signer: leave that check to Android's installer.
        if (theirs.isEmpty()) { CrashLog.event("update check: couldn't read the download's signature; Android will check it"); return null }
        if (theirs.intersect(certs(mine)).isEmpty()) return "it isn't signed with Delphis' key"
        return null
    }

    /** Android requires the user to allow "Install unknown apps" for Delphis once. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to PackageInstaller; Android then shows its own "Update this app?" prompt. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives PackageInstaller results; shows Android's confirmation screen when it asks for one. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // UpdatedReceiver opens Delphis again
            else -> {
                Prefs(context).relaunchAfterUpdate = false
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                CrashLog.event("update failed: $msg")
                // Android blocks self-updates of apps installed from a downloaded file: use its own installer instead.
                if (msg.contains("self update", true) || msg.contains("unknown source", true)) {
                    Updater.installWithSystem(context)
                    return
                }
                val hint = if (msg.contains("signature", true) || msg.contains("conflict", true))
                    " — uninstall Delphis once and install the new version manually; updates will work from then on."
                else ""
                io.github.hiramrg.delphis.ui.Notices.problem(context, "Update failed", "$msg$hint")
            }
        }
    }
}


/**
 * Android kills Delphis while it updates itself. Once the new version is in place (MY_PACKAGE_REPLACED),
 * this opens it again if the update came from "Restart & update". If Android won't let a receiver start
 * an activity, the exit helper (an accessibility service, which Android allows to) does it when it
 * reconnects; see [relaunchIfUpdated].
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) relaunchIfUpdated(context)
    }
}

/** Opens Delphis on the new version after "Restart & update" (once). */
fun relaunchIfUpdated(context: Context) {
    val prefs = Prefs(context)
    if (!prefs.relaunchAfterUpdate) return
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
    if (runCatching { context.startActivity(open) }.isSuccess) prefs.relaunchAfterUpdate = false
}
