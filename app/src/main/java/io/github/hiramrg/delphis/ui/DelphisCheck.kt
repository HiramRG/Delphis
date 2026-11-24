package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import io.github.hiramrg.delphis.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.DolphinExitService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delphis check (Settings → Diagnostics): everything Delphis needs, each with a Fix button when it isn't
 * ready. Read again each time Delphis comes back to the front; while something needs attention Home shows
 * a dot on ⚙ and one notice per opening.
 */
object DelphisCheck {
    /** OK = ready (✓); TIP = optional, offered but not counted (i); FIX = needs attention (!). */
    enum class Level { OK, TIP, FIX }

    class Item(val level: Level, val title: String, val sub: String? = null, val short: String? = null, val fix: String? = null, val onFix: (() -> Unit)? = null)

    /** Reads everything (Dolphin's folder is read through Android's file picker, so call it off the main thread). */
    fun items(act: MainActivity): List<Item> = buildList {
        val prefs = act.prefs
        val one = act.oneScreen
        // Dolphin
        val pkg = act.bridge.installedPackage()
        if (pkg != null) {
            val ver = runCatching { act.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
            add(Item(Level.OK, listOfNotNull("Dolphin", ver, "installed").joinToString(" ")))
        } else add(Item(Level.FIX, "Dolphin isn't installed", "Install the official Dolphin Emulator, then open it once.", "Dolphin isn't installed", "Install") {
            runCatching { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.dolphinemu.dolphinemu"))) }
                .onFailure { Notices.info(act, "Couldn't open the store", "Search for \"Dolphin Emulator\" in your app store.") }
        })
        // Dolphin's user folder (Delphis writes the controls there)
        val folderOk = runCatching { act.bridge.hasConfigAccess() && act.bridge.looksLikeDolphinFolder() }.getOrDefault(false)
        add(
            if (folderOk) Item(Level.OK, "Dolphin's folder connected")
            else Item(Level.FIX, "Dolphin's folder isn't connected", "Delphis writes your controls there. In the picker: ☰ → \"Dolphin\" → \"Use this folder\".", "Dolphin's folder", "Connect") { act.connectDolphinFolder() },
        )
        // Games
        val folders = prefs.gamesFolders.size
        val games = act.games.size
        add(
            when {
                folders == 0 -> Item(Level.FIX, "No games folder", "Choose the folder with your Wii and GameCube games.", "no games folder", "Choose") { act.chooseGamesFolder() }
                games == 0 && !act.scanning -> Item(Level.FIX, "No games found", "Nothing Dolphin can play in ${plural(folders, "folder")}. Add the folder with your games.", "no games found", "Add folder") { act.chooseGamesFolder() }
                else -> Item(Level.OK, "Games · ${plural(folders, "folder")} · ${plural(games, "game")}")
            },
        )
        // "Close games" (the accessibility helper)
        add(
            if (DolphinExitService.isEnabled(act)) Item(Level.OK, "\"Close games\" is on")
            else Item(
                Level.FIX, "\"Close games\" is off",
                if (one) "Needed for the pill, the menu shortcut and quitting games. If Android greys it out: App info → ⋮ → Allow restricted settings first."
                else "Needed to close a game so the next one can start.",
                "\"close games\" is off", "Fix",
            ) {
                // One screen: the pop-up that explains restricted settings (App info / Accessibility buttons).
                if (one) { act.exitHelperForPlay = false; act.exitHelperPrompt = {} } else DolphinExitService.openSettings(act)
            },
        )
        // Installing Delphis' own updates
        add(
            if (runCatching { act.packageManager.canRequestPackageInstalls() }.getOrDefault(true)) Item(Level.OK, "Updates can be installed")
            else Item(Level.FIX, "Updates: \"Install unknown apps\" isn't allowed", "Lets Delphis install its own updates.", "updates not allowed", "Fix") {
                runCatching { act.updater.openInstallPermissionSettings() }
            },
        )
        // Controller
        val pad = if (one) DeviceInfo.gamepads().firstOrNull() else null
        val checked = prefs.calibratedDeviceName
        if (one) add(
            when {
                pad == null && prefs.touchWhenNoController -> Item(Level.OK, "No controller · touch controls in games")
                pad == null -> Item(Level.FIX, "No controller, and touch controls are off", "Connect a controller, or turn touch controls back on.", "no controller", "Touch on") {
                    prefs.touchWhenNoController = true; act.setupVersion++
                }
                pad.name == checked -> Item(Level.OK, "Controller · ${pad.name} (checked)")
                else -> Item(Level.FIX, "Controller · ${pad.name} isn't checked", "Press each button once, so the menus and your games read it right.", "controller not checked", "Check") {
                    act.screen = Screen.ControllerCheck
                }
            },
        ) else add(
            if (checked != null) Item(Level.OK, "Controls checked · $checked")
            else Item(Level.TIP, "Controller check not done", "Recommended: press each button once (about a minute).", null, "Check") { act.screen = Screen.ControllerCheck },
        )
        // Screens
        add(
            Item(
                Level.OK,
                if (one) "One screen" + if (prefs.touchWhenNoController) " · touch controls when no controller" else ""
                else "Two screens · game on top, Delphis below",
            ),
        )
    }

    private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

    fun needsAttention(items: List<Item>) = items.filter { it.level == Level.FIX }

    /** "2 things need attention" / "1 thing needs attention". */
    fun summary(n: Int) = if (n == 1) "1 thing needs attention" else "$n things need attention"
}

/** Settings → Diagnostics: the Delphis check list. */
@Composable
fun DelphisCheckCard(act: MainActivity) {
    val c = LocalWii.current
    val version = act.checkVersion
    val items by produceState<List<DelphisCheck.Item>?>(null, version, act.setupVersion, act.games.size, act.scanning) {
        value = withContext(Dispatchers.IO) { runCatching { DelphisCheck.items(act) }.getOrNull() }
    }
    val list = items ?: return
    val fix = DelphisCheck.needsAttention(list).size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Delphis check", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f))
            Text(
                if (fix == 0) "✓ Everything's ready" else DelphisCheck.summary(fix),
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (fix == 0) c.green else c.orange,
            )
        }
        GroupCard {
            list.forEachIndexed { i, item ->
                if (i > 0) RowDivider()
                CheckRow(item)
            }
        }
    }
}

@Composable
private fun CheckRow(item: DelphisCheck.Item) {
    val c = LocalWii.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (mark, tint) = when (item.level) {
            DelphisCheck.Level.OK -> "✓" to c.green
            DelphisCheck.Level.TIP -> "i" to c.accentText
            DelphisCheck.Level.FIX -> "!" to c.orange
        }
        Box(Modifier.size(20.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = markSp(12f), lineHeight = markSp(12f), fontWeight = FontWeight.Black, color = tint, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 15.sp, maxLines = 2)
            item.sub?.let { Text(it, fontSize = 10.5.sp, color = c.textDim, lineHeight = 13.sp) }
        }
        val action = item.onFix
        if (item.fix != null && action != null) {
            val label = item.fix + " ›"
            Text(
                label, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                color = if (item.level == DelphisCheck.Level.FIX) c.onAccent else c.text,
                onTextLayout = { TextFit.check(label, it) },
                modifier = Modifier.rememberFocus("check-${item.title}").focusRing(ChipShape)
                    .channel(ChipShape, primary = item.level == DelphisCheck.Level.FIX)
                    .clickable(onClick = action).padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

/** The small orange dot on Home's ⚙ while the Delphis check has something to fix. */
@Composable
fun AttentionDot(modifier: Modifier = Modifier) {
    val c = LocalWii.current
    Box(modifier.size(11.dp).clip(CircleShape).background(if (c.dark) Color(0xFF1D2127) else c.card).padding(2.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(c.orange))
    }
}
