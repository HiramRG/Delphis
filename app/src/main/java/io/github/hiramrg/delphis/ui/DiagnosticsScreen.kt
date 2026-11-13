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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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

    val copyReport = {
        act.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Delphis report", report))
        Notices.done(act, "Report copied")
    }
    val shareReport = {
        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report)
        }, "Share report"))
    }
    DisposableEffect(Unit) {
        act.keyListener = { e ->
            capture.onKey(e)?.let { c -> live.add(0, "key   ${c.name}   [${c.deviceName}]"); if (live.size > 8) live.removeAt(live.lastIndex) }
            if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) when (e.keyCode) {
                android.view.KeyEvent.KEYCODE_BUTTON_B -> act.screen = Screen.Settings
                android.view.KeyEvent.KEYCODE_BUTTON_X -> copyReport()
                android.view.KeyEvent.KEYCODE_BUTTON_Y -> shareReport()
            }
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

    val c = LocalWii.current
    val dolphin = remember { act.bridge.installedPackage() }
    val folderOk = remember { act.bridge.hasConfigAccess() && act.bridge.looksLikeDolphinFolder() }
    val helperOn = remember { io.github.hiramrg.delphis.platform.DolphinExitService.isEnabled(act) }
    val lastCrash = remember { io.github.hiramrg.delphis.platform.CrashLog.read()?.lineSequence()?.firstOrNull { it.isNotBlank() } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", { act.screen = Screen.Settings }, "Settings · Diagnostics", "Diagnostics", Modifier.padding(start = 10.dp, end = 16.dp, top = 10.dp)) {
            PanelBtn("Copy report", "X", false, onClick = copyReport)
            PanelBtn("Share", "Y", true, onClick = shareReport)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GroupCard(Modifier.weight(1f)) {
                    StatusRow("Dolphin", listOfNotNull(dolphin ?: "Not installed", if (folderOk) "folder connected" else "folder not connected").joinToString(" · "), dolphin != null && folderOk)
                    RowDivider()
                    StatusRow("Close-games helper", if (helperOn) "On" else "Off", helperOn)
                    RowDivider()
                    StatusRow("Trackpad server", io.github.hiramrg.delphis.platform.DsuServer.status().lineSequence().firstOrNull() ?: "", null)
                    RowDivider()
                    StatusRow("Last crash", lastCrash ?: "None", if (lastCrash == null) true else null)
                }
                val shape = RoundedCornerShape(18.dp)
                Column(Modifier.weight(1f).clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapsLabel("Live input")
                    Text("Press buttons or move sticks to see what Dolphin receives.", fontSize = 12.sp, color = c.textDim)
                    Text(if (live.isEmpty()) "(nothing yet)" else live.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.accentText)
                }
            }
            val shape = RoundedCornerShape(18.dp)
            // Fit check: any button, chip or hint whose words didn't fit (would be cut off) since Delphis started.
            Column(Modifier.fillMaxWidth().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CapsLabel("Labels that didn't fit")
                val misses = TextFit.misses
                if (misses.isEmpty()) Text("✓ None so far. Every button, chip and hint shown since Delphis started fit its space.", fontSize = 12.sp, color = c.green)
                else misses.forEach { Text("• $it", fontSize = 12.sp, color = c.orange) }
            }
            Column(Modifier.fillMaxWidth().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CapsLabel("Full report")
                Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = c.text)
            }
        }
        AppBottomBar("Ⓧ Copy  Ⓨ Share  Ⓑ Back")
    }
}

/** A status line with a green ✓ when it's fine ([ok] null = no mark). */
@Composable
private fun StatusRow(title: String, sub: String, ok: Boolean?) {
    val c = LocalWii.current
    SettingRow(title, sub) {
        if (ok == true) Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Black, color = c.green)
        if (ok == false) Text("!", fontSize = 15.sp, fontWeight = FontWeight.Black, color = c.orange)
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
    // RetroAchievements: Dolphin earns them (its own settings), Delphis only reads the account.
    appendLine("retroachievements (Dolphin): ")
    appendLine(bridge.achievementSettings())
    val ra = io.github.hiramrg.delphis.platform.Achievements.account(prefs)
    appendLine("retroachievements (Delphis): account=${ra?.user ?: "(not signed in)"}, last error=${io.github.hiramrg.delphis.platform.Achievements.lastError ?: "none"}")
    act.lastGame()?.let { g -> appendLine("  last game: ${g.info.title} → RA game id ${prefs.raGameId(g.key) ?: "(not looked up yet)"}") }
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
