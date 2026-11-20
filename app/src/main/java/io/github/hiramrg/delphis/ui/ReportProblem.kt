package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import io.github.hiramrg.delphis.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.platform.DeviceInfo

/**
 * "Report a problem": the player picks an area and writes a line; Delphis opens a GitHub issue (the bug form in
 * .github/ISSUE_TEMPLATE/bug.yml) with the device, versions and controller filled in, and copies the Diagnostics
 * report without anything personal so it can be pasted in the last box.
 */
object ProblemReport {
    val AREAS = listOf("Controller", "In a game", "Setup", "Crashed", "Something else")
    private val AREA_LABELS = listOf("🎮 Controller", "🕹 In a game", "⚙ Setup", "💥 Crashed", "… Something else")
    fun areaLabel(i: Int) = AREA_LABELS[i]
    private const val NEW_ISSUE = "https://github.com/HiramRG/Delphis/issues/new"

    fun device(act: MainActivity): String =
        "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · " +
            if (act.oneScreen) "one screen" else "two screens"

    fun versions(act: MainActivity): String {
        val dolphin = act.bridge.installedPackage()?.let { p ->
            runCatching { act.packageManager.getPackageInfo(p, 0).let { "${it.versionName} (${it.longVersionCode})" } }.getOrNull()
        } ?: "not found"
        val beta = if (io.github.hiramrg.delphis.platform.Updater.builtChannel(act) == "beta") " beta" else ""
        return "Delphis ${runCatching { act.updater.installedVersionCode() }.getOrDefault(0)}$beta · Dolphin $dolphin"
    }

    fun controller(act: MainActivity): String {
        val pads = DeviceInfo.gamepads().map { it.name }.distinct()
        if (pads.isEmpty()) return "None (touch)"
        return pads.joinToString(", ") { if (it == act.prefs.calibratedDeviceName) "$it (checked)" else it }
    }

    /** The Diagnostics report without usernames, keys, folder and file paths. */
    fun sanitized(act: MainActivity): String = shareableReport(act, buildReport(act))

    fun send(act: MainActivity, area: Int, text: String) {
        val what = text.trim().ifEmpty { "(no description)" }
        val tag = if (act.oneScreen) "[One screen] " else ""
        val url = android.net.Uri.parse(NEW_ISSUE).buildUpon()
            .appendQueryParameter("template", "bug.yml")
            .appendQueryParameter("title", (tag + what.lineSequence().first()).take(110))
            .appendQueryParameter("what", what.take(1500))
            .appendQueryParameter("area", AREAS[area])
            .appendQueryParameter("device", device(act))
            .appendQueryParameter("versions", versions(act))
            .appendQueryParameter("controller", io.github.hiramrg.delphis.core.ReportPrivacy.sanitize(controller(act)))
            .build()
        runCatching {
            val clip = act.getSystemService(android.content.ClipboardManager::class.java)
            clip.setPrimaryClip(android.content.ClipData.newPlainText("Delphis diagnostics", sanitized(act)))
        }
        val opened = runCatching { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (opened) Notices.done(act, "Report copied", "Paste it in the last box on GitHub (long-press → Paste), then Submit.")
        else Notices.problem(act, "No browser found", "The report is copied. Open github.com/HiramRG/Delphis/issues to send it.")
    }
}

@Composable
fun ReportProblemPanel(act: MainActivity) {
    val c = LocalWii.current
    val game = act.lastGame()?.info?.title
    val included = remember {
        listOf(ProblemReport.device(act), ProblemReport.versions(act), ProblemReport.controller(act), "Controls, settings and recent events")
    }
    OverlayPanel(onOutside = { act.reportOpen = false }) {
        PopupPanel("Report a problem", "What went wrong?", Modifier.widthIn(max = 760.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProblemReport.AREAS.indices.forEach { i -> WiiChip(ProblemReport.areaLabel(i), act.reportArea == i, { act.reportArea = i }) }
                    }
                    val shape = RoundedCornerShape(12.dp)
                    Box(Modifier.fillMaxWidth().height(68.dp).clip(shape).background(Color(0xFF15181C)).border(2.dp, c.accent, shape).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (act.reportText.isEmpty()) Text("Describe it in a line or two (tap to type)", fontSize = 12.5.sp, color = c.textDim)
                        BasicTextField(
                            act.reportText, { act.reportText = it.take(1500) }, Modifier.fillMaxSize(),
                            textStyle = TextStyle(fontSize = 12.5.sp, color = c.text, lineHeight = 17.sp), cursorBrush = SolidColor(c.accent),
                        )
                    }
                    if (game != null) Text("Last game: $game", fontSize = 11.5.sp, color = c.textDim, maxLines = 2)
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelBtn("Cancel", "B", false, Modifier.weight(1f)) { act.reportOpen = false }
                        PanelBtn("Open on GitHub", "A", true, Modifier.weight(1.8f)) { act.sendReport() }
                    }
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1F24)).border(1.5.dp, c.cardBorder, RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CapsLabel("Included automatically")
                    included.forEach { Text("✓ $it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text, lineHeight = 15.sp) }
                    CapsLabel("Left out", Modifier.padding(top = 4.dp))
                    listOf("RetroAchievements username and key", "Your folder names and file paths").forEach { Text("✕ $it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textDim, lineHeight = 15.sp) }
                    Text(
                        "GitHub opens with this filled in; the full report is copied, so paste it in the last box. You'll need a free GitHub account.",
                        fontSize = 11.sp, color = c.textDim, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
