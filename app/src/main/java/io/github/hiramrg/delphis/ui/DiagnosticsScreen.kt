package io.github.hiramrg.delphis.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.DolphinConfigWriter
import io.github.hiramrg.delphis.core.PresetLibrary
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.InputCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything needed to work out why a preset doesn't respond, in one copyable report. */
@Composable
fun DiagnosticsScreen(act: MainActivity) {
    val live = remember { mutableStateListOf<String>() }
    val capture = remember { InputCapture() }
    var report by remember { mutableStateOf("Collecting…") }

    DisposableEffect(Unit) {
        act.keyListener = { e ->
            capture.onKey(e)?.let { c -> live.add(0, "key   ${c.name}   [${c.deviceName}]"); if (live.size > 8) live.removeAt(live.lastIndex) }
            e.keyCode != android.view.KeyEvent.KEYCODE_BACK
        }
        act.motionListener = { e ->
            capture.onMotion(e)?.let { c ->
                val line = "axis  ${c.name}   [${c.deviceName}]"
                if (live.firstOrNull() != line) { live.add(0, line); if (live.size > 8) live.removeAt(live.lastIndex) }
            }
            true
        }
        onDispose { act.keyListener = null; act.motionListener = null }
    }

    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) { buildReport(act) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header("Diagnostics", onBack = { act.screen = Screen.Settings }) {
            TextButton(onClick = {
                act.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Delphis report", report))
                Toast.makeText(act, "Report copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            TextButton(onClick = {
                act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report)
                }, "Share report"))
            }) { Text("Share") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Press buttons / move sticks to see what Dolphin receives:", fontSize = 13.sp)
            Text(
                if (live.isEmpty()) "(nothing yet)" else live.joinToString("\n"),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalDivider()
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

private fun buildReport(act: MainActivity): String = buildString {
    val prefs = act.prefs
    val bridge = act.bridge
    appendLine("Delphis diagnostics")
    appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
    appendLine("dolphin package: ${bridge.installedPackage()}")
    appendLine("dolphin folder: ${prefs.dolphinTreeUri}")
    appendLine("  write access: ${bridge.hasConfigAccess()}, looks like Dolphin user folder: ${bridge.looksLikeDolphinFolder()}")
    appendLine("  contains: ${bridge.rootListing()}")
    appendLine("  Config/: ${bridge.configListing()}")
    appendLine(bridge.baseConfigStatus())
    val roles = DeviceInfo.screenRoles(act, prefs.swapScreens)
    appendLine("screens: top=${roles.topDisplayId} bottom=${roles.bottomDisplayId}")
    appendLine()
    appendLine("input devices:")
    appendLine(DeviceInfo.describeDevices())
    appendLine()
    val profile = DeviceInfo.controllerProfile(prefs)
    appendLine("bound devices: ${profile.devices}")
    appendLine("calibrated from: ${prefs.calibratedDeviceName ?: "(controller check not done)"}")
    prefs.calibration.forEach { (k, v) -> appendLine("  $k = ${v.joinToString(" | ")}") }
    appendLine()
    appendLine("Delphis process exits (newest first):")
    appendLine(io.github.hiramrg.delphis.platform.CrashLog.exitReasons(act))
    io.github.hiramrg.delphis.platform.CrashLog.lastProblemTrace(act)?.let {
        appendLine("trace of the newest Delphis freeze / native crash:")
        appendLine(it)
    }
    appendLine("gyro aiming: left/right=${prefs.gyroHorizontal.name} speed=${prefs.gyroSensitivity}")
    appendLine("close-games helper: " + (if (io.github.hiramrg.delphis.platform.DolphinExitService.isEnabled(act)) "on" else "off"))
    appendLine("Dolphin's touch settings (Config/Dolphin.ini, read by Dolphin only when it starts):")
    appendLine(runCatching { bridge.dolphinTouchSettings() }.getOrNull() ?: "(couldn't read)")
    appendLine(io.github.hiramrg.delphis.platform.DsuServer.status())
    appendLine("Dolphin's DSU servers (Config/DSUClient.ini):")
    appendLine(runCatching { bridge.dsuClientSettings() }.getOrNull() ?: "(couldn't read)")
    appendLine("events:")
    appendLine(io.github.hiramrg.delphis.platform.CrashLog.events() ?: "(none)")
    appendLine("last crash / failed launch:")
    appendLine(io.github.hiramrg.delphis.platform.CrashLog.read() ?: "(none)")
    appendLine()
    val last = prefs.lastApplied
    if (last != null) {
        val (gameId, presetId) = last.split('|', limit = 2).let { it[0].ifEmpty { null } to it.getOrNull(1) }
        val preset = presetId?.let { act.store.byId(it) }
        appendLine("last applied: game=$gameId preset=$presetId")
        if (preset != null) {
            appendLine(bridge.readBack(gameId, preset))
            appendLine("--- expected profile")
            appendLine(DolphinConfigWriter.renderProfile(preset, profile))
        }
    } else {
        appendLine("last applied: (none yet)")
    }
}
