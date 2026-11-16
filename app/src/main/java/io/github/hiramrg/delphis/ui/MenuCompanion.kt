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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import io.github.hiramrg.delphis.platform.GameEntry
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
    val ach = rememberAchievements(act.prefs, g.key, g.info)
    val progressView = act.bottomView == "progress"
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        GameBanner(act, g, ach, progressView) { act.bottomView = if (progressView) "controls" else "progress"; act.prefs.bottomView = act.bottomView }
        if (progressView) {
            if (ach != null) ProgressCard(ach, Modifier.weight(1f).fillMaxWidth())
            else WiiCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                Text("🏆 No achievements for this game", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
            }
            StatCards(act, g)
            if (ach == null) ControlsSummary(act, g, preset, Modifier.weight(1f).fillMaxWidth())
        } else {
            WiiCard(Modifier.weight(1f).fillMaxWidth(), padding = 8.dp) {
                if (preset == null) {
                    CapsLabel("Controls")
                    Text("Not chosen yet · press Ⓧ for the game page", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectedControlsTitle()
                        Text(" " + preset.name.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.green, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    ControlsSwitchable(preset, act.prefs, g.key, Modifier.fillMaxSize().padding(top = 4.dp), toggle = act.homeControlsToggle, toggleKey = "L2")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelBtn(if (act.prefs.isFavorite(g.key)) "★ Favorite" else "☆ Favorite", "Y", false, Modifier.weight(1f)) { act.prefs.toggleFavorite(g.key); act.statsVersion++ }
            PanelBtn("Play", "A", true, Modifier.weight(1.6f)) { act.quickPlay(g) }
        }
    }
}

/**
 * The game's banner: its title-screen picture from RetroAchievements (or the cover, softly blurred), the
 * cover, name and year · genre · developer, plus the Controls | Progress switch (L3) in the corner.
 */
@Composable
private fun GameBanner(act: MainActivity, g: GameEntry, ach: io.github.hiramrg.delphis.core.RetroAchievements.GameProgress?, progressView: Boolean, onSwitch: () -> Unit) {
    val c = LocalWii.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val picture by produceState<android.graphics.Bitmap?>(null, ach?.titleImage) {
        value = ach?.titleImage?.let { io.github.hiramrg.delphis.platform.Achievements.badge(context, it) }
    }
    Box(Modifier.fillMaxWidth().height(118.dp).clip(shape).background(Color(0xFF15181C))) {
        if (picture != null) androidx.compose.foundation.Image(picture!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().blur(18.dp).alpha(0.75f), titleSize = 10)
        // Darker at the bottom so the name always reads.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x33000000), 1f to Color(0xEE14171B))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            Cover(act, g, act.prefs.showCovers, Modifier.size(width = 68.dp, height = 96.dp).platformRim(g, 2.dp), titleSize = 9)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(g.info.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                val time = act.prefs.playTime()[g.key] ?: 0L
                val meta = listOfNotNull(
                    ach?.year, ach?.genre,
                    if (ach?.year == null && ach?.genre == null) (if (g.info.platform == io.github.hiramrg.delphis.core.Platform.WII) "Wii" else "GameCube") else null,
                    if (time > 60_000) formatPlayTime(time) + " played" else null,
                ).joinToString(" · ")
                Text(meta, fontSize = 11.sp, color = Color(0xFFC9D0D8), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val seg = RoundedCornerShape(50)
            Row(Modifier.height(26.dp).clip(seg).background(Color(0xC014171B)).border(1.5.dp, Color(0xFF4A535E), seg).padding(2.dp)) {
                listOf(false to "🎮 Controls", true to "🏆 Progress").forEach { (p, label) ->
                    val on = p == progressView
                    Box(
                        Modifier.fillMaxHeight().clip(seg).background(if (on) c.accent else Color.Transparent).clickable { if (!on) onSwitch() }.padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color(0xFF0A2533) else Color(0xFFC9D0D8), maxLines = 1) }
                }
            }
            KeyIcon("L3", Color(0xFFE8ECF0))
        }
    }
}

/** Achievements bar (R3 opens the list) and the next two to go for. */
@Composable
private fun ProgressCard(p: io.github.hiramrg.delphis.core.RetroAchievements.GameProgress, modifier: Modifier) {
    val c = LocalWii.current
    WiiCard(modifier, padding = 10.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏆 ${p.unlocked} / ${p.total}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(if (c.dark) Color(0xFF15181C) else c.cardBorder)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((p.percent / 100f).coerceIn(0.02f, 1f)).background(Color(0xFFF5C542)))
                }
                if (p.savedAt != null) OfflineTag() else Text("${p.percent}%", fontSize = 11.5.sp, color = c.textDim, maxLines = 1)
                KeyIcon("R3", size = 16.dp)
            }
            val next = p.achievements.filter { !it.unlocked }.sortedWith(compareBy({ it.displayOrder }, { it.id })).take(2)
            if (next.isEmpty()) Text("✓ Every achievement unlocked!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.green)
            else {
                Text("NEXT UP", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.textDim)
                next.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BadgeImage(a.badgeUrl(), 28.dp, 6.dp)
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.description, fontSize = 10.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${a.points} pts", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE0A82E))
                    }
                }
            }
        }
    }
}

/** Time played · sessions · when the autosave is from ("today" or the day and date). */
@Composable
private fun StatCards(act: MainActivity, g: GameEntry) {
    val c = LocalWii.current
    val time = act.prefs.playTime()[g.key] ?: 0L
    val sessions = act.prefs.sessions()[g.key] ?: 0L
    val autosave by produceState<Long?>(null, g.key, act.statsVersion) {
        value = g.info.gameId?.let { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.quickStateTime(id) } }
    }
    val fmtTime = remember { java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(act)) "HH:mm" else "h:mm a", java.util.Locale.getDefault()) }
    val save = autosave
    val saveDay = save?.let {
        val now = java.util.Calendar.getInstance(); val then = java.util.Calendar.getInstance().apply { timeInMillis = it }
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) "today"
        else java.text.SimpleDateFormat("EEE M/d", java.util.Locale.getDefault()).format(java.util.Date(it))
    }
    val cards = listOf(
        "🕒 " + (if (time > 60_000) formatPlayTime(time) else "—") to "played",
        "▶ " + (if (sessions > 0) "$sessions" else "—") to "sessions",
        "💾 " + (save?.let { fmtTime.format(java.util.Date(it)) } ?: "None") to (if (saveDay != null) "autosave · $saveDay" else "autosave"),
    )
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { (big, small) ->
            val shape = RoundedCornerShape(13.dp)
            Column(Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(horizontal = 9.dp), verticalArrangement = Arrangement.Center) {
                Text(big, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                Text(small, fontSize = 10.5.sp, color = c.textDim, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** For games without achievements: the selected controls, where the game starts and its picture. */
@Composable
private fun ControlsSummary(act: MainActivity, g: GameEntry, preset: Preset?, modifier: Modifier) {
    val c = LocalWii.current
    WiiCard(modifier, padding = 10.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            SelectedControlsTitle()
            Text(preset?.name ?: "Not chosen yet", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (act.prefs.autoSaveFor(g.info.gameId)) "Starts from your autosave" else "Starts from the beginning", fontSize = 11.sp, color = c.textDim, maxLines = 1)
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

