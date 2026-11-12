package io.github.hiramrg.delphis.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.*
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.GameWatch
import java.lang.ref.WeakReference

/**
 * The bottom screen while you're in Delphis' menus (not in a game): quick tiles on Home, the
 * touch preset picker on a game's page, details of the highlighted game in All games.
 *
 * It mirrors MainActivity's state (same process, shared Compose state). Like the in-game screen its
 * window never takes focus, so the controller keeps driving the top screen; a 1x1 focus sink
 * catches keys meant for the bottom display (avoiding "isn't responding") and hands gamepad input
 * back to the top screen.
 */
object MenuCompanion {
    private var activity: WeakReference<MenuCompanionActivity>? = null
    var main: WeakReference<MainActivity>? = null
        private set
    /** True while the bottom-screen menu is up (the top screen can then leave the picker to it). */
    val showing = mutableStateOf(false)

    internal fun attach(a: MenuCompanionActivity?) { activity = a?.let { WeakReference(it) }; showing.value = a != null }

    @Suppress("DEPRECATION")
    fun show(main: MainActivity) {
        this.main = WeakReference(main)
        if (activity?.get() != null || GameWatch.session != null) return
        val roles = DeviceInfo.screenRoles(main, main.prefs.swapScreens)
        val bottom = roles.bottomDisplayId ?: return
        val mainDisplay = runCatching { if (Build.VERSION.SDK_INT >= 30) main.display!!.displayId else main.windowManager.defaultDisplay.displayId }.getOrNull()
        if (mainDisplay == bottom) return // Delphis itself was opened on the bottom screen
        val intent = Intent(main, MenuCompanionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { main.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(bottom).toBundle()) }.isSuccess
        // Starting it moves focus to the bottom display; give it straight back to the top screen.
        if (ok) Handler(Looper.getMainLooper()).postDelayed({ main.bringSelfToFront() }, 350)
    }

    fun close() { activity?.get()?.finish() }
}

class MenuCompanionActivity : ComponentActivity() {
    private var focusSink: View? = null
    private var lastBounce = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        MenuCompanion.attach(this)
        setContent {
            val main = MenuCompanion.main?.get()
            if (main == null) { LaunchedEffect(Unit) { finish() }; return@setContent }
            // A game started: the in-game screen takes over the bottom display.
            if (GameWatch.session != null) { LaunchedEffect(Unit) { finish() }; return@setContent }
            WiiTheme(main.darkTheme) { CompanionContent(main) }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (focusSink != null) return
        val sink = object : View(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = onSinkKey(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = if (isGamepad(event.source)) { bounce(); true } else false
        }.apply { isFocusable = true; isFocusableInTouchMode = true }
        val lp = WindowManager.LayoutParams(
            1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { token = window.decorView.windowToken; gravity = Gravity.TOP or Gravity.START; title = "Delphis menu focus" }
        runCatching { windowManager.addView(sink, lp); focusSink = sink; sink.requestFocus() }
    }

    private fun isGamepad(source: Int) =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

    private fun onSinkKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) MenuCompanion.main?.get()?.onBackPressedDispatcher?.onBackPressed()
            return true
        }
        if (isGamepad(event.source)) { bounce(); return true }
        return false
    }

    /** The controller's input landed here: move focus back to the top screen. */
    private fun bounce() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBounce < 1000) return
        lastBounce = now
        MenuCompanion.main?.get()?.bringSelfToFront()
    }

    override fun onDestroy() {
        focusSink?.let { runCatching { windowManager.removeViewImmediate(it) } }
        focusSink = null
        MenuCompanion.attach(null)
        super.onDestroy()
    }
}

// ---------------------------------------------------------------------------------------- content

@Composable
private fun CompanionContent(act: MainActivity) {
    Box(Modifier.fillMaxSize().padding(14.dp)) {
        when (val s = act.screen) {
            // A game highlighted in Home's lists shows its details; otherwise the quick tiles.
            Screen.Home -> if (act.focusedGame != null) FocusedGamePanel(act) else QuickMenu(act)
            is Screen.Game -> act.picker?.let { PickerPanel(act, it) } ?: IdlePanel()
            else -> IdlePanel()
        }
    }
}

@Composable
private fun QuickMenu(act: MainActivity) {
    val c = LocalWii.current
    val last = act.lastGame()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CapsLabel("Quick menu")
        class Tile(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val action: () -> Unit)
        val tiles = listOf(
            Tile(androidx.compose.material.icons.Icons.Outlined.PlayArrow, if (last != null) "Continue" else "Play", last?.info?.title ?: "Pick a game first") { last?.let { act.quickPlay(it) } ?: act.showAllGames() },
            Tile(androidx.compose.material.icons.Icons.Outlined.GridView, "All games", "${act.games.size} games") { act.showAllGames() },
            Tile(androidx.compose.material.icons.Icons.Outlined.SportsEsports, "Controls", "Change or edit the preset") { last?.let { act.openGame(it) } ?: act.showAllGames() },
            Tile(androidx.compose.material.icons.Icons.Outlined.Settings, "Settings", "Theme, gyro, updates") { act.screen = Screen.Settings },
        )
        for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in 0..1) {
                val i = row * 2 + col
                val t = tiles[i]
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape)
                        .clickable(onClick = t.action).padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        if (i == 0 && last != null) {
                            // The game's cover with its platform rim, and a small play badge.
                            Cover(act, last, act.prefs.showCovers, Modifier.size(width = 40.dp, height = 54.dp).platformRim(last, 2.dp))
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(t.icon, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                            }
                        } else Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) Color(0xFF1D2127) else c.bg), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(t.icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(t.sub, fontSize = 12.sp, color = c.textDim, maxLines = 2, lineHeight = 15.sp)
                }
            }
        }
        HintLine("Tap a tile, or use the D-pad + Ⓐ on the top screen")
    }
}

@Composable
private fun PickerPanel(act: MainActivity, p: GamePicker) {
    val c = LocalWii.current
    if (p.editing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Editing the preset on the top screen…", color = c.textDim, fontSize = 15.sp)
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Controls · " + p.game.info.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            HintText("✚ Move · Ⓐ Choose", fontSize = 11.sp)
        }
        // Tapping a preset makes it the one in use; the ring follows the D-pad on the top screen.
        PresetChooser(p.groups, p.selected, p.focused, p.game.info.title, { p.isEdited(it) }, Modifier.weight(1f).fillMaxWidth()) { p.choose(it) }
    }
}

/** One preset in a list: its illustration, name (with tags) and summary. */
@Composable
fun PickerCard(preset: Preset, selected: Boolean, suggested: Boolean = false, onClick: () -> Unit) {
    val c = LocalWii.current
    WiiCard(Modifier.fillMaxWidth(), selected = selected, radius = 18.dp, padding = 8.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // An illustration of how you'd hold the Wii controller with this preset.
            PresetIcon(preset, Modifier.size(width = 66.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(c.bg).padding(2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(presetTitle(preset.name, suggested), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                Text(preset.summary, fontSize = 11.sp, color = c.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FocusedGamePanel(act: MainActivity) {
    val c = LocalWii.current
    val g = act.focusedGame
    if (g == null) { IdlePanel("Pick a game on the top screen"); return }
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val preset = act.prefs.presetFor(g.key)?.let { act.store.byId(it) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(act, g, act.prefs.showCovers, Modifier.size(width = 70.dp, height = 98.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.info.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(playSummary(act, g), fontSize = 12.sp, color = c.textDim)
            }
        }
        WiiCard(Modifier.fillMaxWidth(), padding = 12.dp) {
            if (preset == null) {
                CapsLabel("Controls")
                Text("Not chosen yet", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                // Same picture as the game page and the preset lists.
                Box(
                    Modifier.size(72.dp, 52.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.sidebar else c.bg),
                    contentAlignment = Alignment.Center,
                ) { PresetIcon(preset, Modifier.size(58.dp, 44.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SelectedControlsTitle()
                    Text(withOrangeTags(preset.name, c.orange), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (preset.controller.label != preset.name)
                        Text(preset.controller.label, fontSize = 11.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelBtn(if (act.prefs.isFavorite(g.key)) "★ Favorite" else "☆ Favorite", null, false, Modifier.weight(1f)) { act.prefs.toggleFavorite(g.key); act.statsVersion++ }
            PanelBtn("Choose controls", "A", true, Modifier.weight(1.6f)) { act.openGame(g) }
        }
    }
}

@Composable
private fun IdlePanel(message: String = "") {
    val c = LocalWii.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Delphis", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        Text(rememberClock(withDate = true), fontSize = 18.sp, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
        if (message.isNotEmpty()) Text(message, fontSize = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 14.dp))
    }
}
