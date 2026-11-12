package io.github.hiramrg.delphis.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
        const val RELEASE_API = "https://api.github.com/repos/HiramRG/Delphis/releases/tags/latest"
        const val RELEASE_PAGE = "https://github.com/HiramRG/Delphis/releases/tag/latest"
        const val APK_NAME = "Delphis.apk"
        const val ACTION_INSTALL_STATUS = "io.github.hiramrg.delphis.INSTALL_STATUS"
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

    suspend fun check(): Check = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Delphis-updater")
            }
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
        file
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
                val hint = if (msg.contains("signature", true) || msg.contains("conflict", true))
                    " — uninstall Delphis once and install the new version manually; updates will work from then on."
                else ""
                Toast.makeText(context, "Update failed: $msg$hint", Toast.LENGTH_LONG).show()
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
