package io.github.hiramrg.delphis.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Platform
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.DolphinExitService
import io.github.hiramrg.delphis.platform.GameEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================ shared bits

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = LocalWii.current.accentText) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun Chip(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun platformLabel(p: Platform) = when (p) { Platform.WII -> "Wii"; Platform.GAMECUBE -> "GameCube"; Platform.UNKNOWN -> "?" }

/** Current time (and date), refreshed every minute; follows the system 12/24 h setting. */
@Composable
fun rememberClock(withDate: Boolean = false): String {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            now = System.currentTimeMillis()
        }
    }
    val time = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    return SimpleDateFormat(if (withDate) "EEE M/d · $time" else time, Locale.getDefault()).format(Date(now))
}

private fun formatPlayTime(ms: Long): String {
    val min = ms / 60_000
    return when {
        min <= 0 -> "New"
        min < 60 -> "$min min"
        else -> "${min / 60} h"
    }
}

private fun formatAgo(t: Long?): String {
    if (t == null) return "Never played"
    val days = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    return when (days) { 0 -> "Played today"; 1 -> "Played yesterday"; else -> "Played $days days ago" }
}

/** Whether the game has a Quick Save state to resume from (checked off the main thread). */
@Composable
fun rememberHasAutosave(act: MainActivity, g: GameEntry): Boolean {
    val v = act.statsVersion
    val has by produceState(false, g.key, v) {
        value = act.prefs.autoSaveResume && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.hasAutosave(g.info.gameId) }
    }
    return has
}

/** "Played yesterday · 14 h played" */
fun playSummary(act: MainActivity, g: GameEntry): String {
    val time = act.prefs.playTime()[g.key] ?: 0L
    return formatAgo(act.prefs.lastPlayed()[g.key]) + if (time > 60_000) " · ${formatPlayTime(time)} played" else ""
}

/** HD box art if enabled and available, otherwise a tidy title card. */
@Composable
fun Cover(act: MainActivity, game: GameEntry, show: Boolean, modifier: Modifier, titleSize: Int = 13) {
    val id = game.info.gameId
    val bitmap by produceState<android.graphics.Bitmap?>(null, id, show) {
        value = if (show && id != null) act.covers.load(id) else null
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(if (game.info.platform == Platform.GAMECUBE) Color(0xFF3B2F66) else Color(0xFF2B3F9E)), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), game.info.title, contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High, modifier = Modifier.fillMaxSize())
        else Text(game.info.title, fontSize = titleSize.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
    }
}

/** A sidebar entry (Library / Settings). */
@Composable
private fun NavItem(label: String, selected: Boolean, trailing: String = "", onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
            .border(2.dp, if (selected) c.accent else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) c.accentText else c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 13.sp, color = c.textDim)
    }
}

/** Handles a key only on its first press (no auto-repeat). */
private fun KeyEvent.firstPress() = action == KeyEvent.ACTION_DOWN && repeatCount == 0

// ============================================================================ home

@Composable
fun HomeScreen(act: MainActivity) {
    val c = LocalWii.current
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val lastPlayed = act.prefs.lastPlayed()
    val recent = act.games.filter { it.key in lastPlayed }.sortedByDescending { lastPlayed[it.key] }
    val hero = recent.firstOrNull()

    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START ->
                if (act.homeFocusedTile || hero == null) false else { if (e.firstPress()) act.quickPlay(hero); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.firstPress()) hero?.let { act.openGame(it) }; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) hero?.let { act.prefs.toggleFavorite(it.key); act.statsVersion++ }; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (e.firstPress()) act.screen = Screen.Library; true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null; act.homeFocusedTile = false } }

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(act)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            // The tiles get a fixed share; the hero card takes whatever is left, so nothing is cut off.
            val tileH = (maxHeight * 0.24f).coerceIn(64.dp, 104.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (hero != null) HeroCard(act, hero) else WelcomeCard(act)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently played", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("All games (${act.games.size}) ▸", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Library }.padding(4.dp))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent.drop(1).take(12), key = { it.uri.toString() }) { g -> ChannelTile(act, g, tileH) }
                    item { AllGamesTile(act, tileH) }
                }
            }
        }
        HomeBar(act)
    }
}

@Composable
private fun HeroCard(act: MainActivity, g: GameEntry) {
    val c = LocalWii.current
    val preset = act.prefs.presetFor(g.key)?.let { act.store.byId(it) }
    val fav = act.prefs.isFavorite(g.key)
    val autosave = rememberHasAutosave(act, g)
    WiiCard(Modifier.fillMaxSize(), radius = 24.dp, padding = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On the Thor's top screen the card is short: one-line title, fewer detail lines.
            val tight = maxHeight < 190.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp)).border(3.dp, c.accent, RoundedCornerShape(14.dp)).padding(3.dp)) {
                    Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize(), titleSize = 14)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Details take the space that's left and are clipped if needed; the buttons always fit.
                    Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                        Text("CONTINUE PLAYING", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, letterSpacing = 1.sp)
                        Text(
                            g.info.title, fontSize = if (tight) 21.sp else 26.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = if (tight) 23.sp else 28.sp, maxLines = if (tight) 1 else 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(playSummary(act, g) + if (fav) "  ★" else "", fontSize = 12.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (autosave) Text("▶ Continues from your autosave", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.accentText, maxLines = 1)
                        if (!tight || !autosave) Text(withOrangeTags("Controls: ${preset?.name ?: "choose when you play"}", c.orange), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WiiButton("Ⓐ Play", onClick = { act.quickPlay(g) }, primary = true, fontSize = 16.sp)
                        WiiButton("Ⓧ Controls", onClick = { act.openGame(g) }, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(act: MainActivity) {
    val c = LocalWii.current
    WiiCard(Modifier.fillMaxSize(), radius = 24.dp, padding = 16.dp) {
        Text("WELCOME", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, letterSpacing = 1.sp)
        Text(if (act.games.isEmpty()) "No games found yet" else "Pick a game to start", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (act.games.isEmpty()) "Choose the folder with your GameCube and Wii games." else "Your last game will show up here, ready to continue.",
            fontSize = 14.sp, color = c.textDim,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (act.games.isEmpty()) WiiButton("Choose games folder", onClick = act::chooseGamesFolder, primary = true)
            else WiiButton("All games ▸", onClick = { act.screen = Screen.Library }, primary = true)
        }
    }
}

@Composable
private fun ChannelTile(act: MainActivity, g: GameEntry, height: androidx.compose.ui.unit.Dp) {
    val c = LocalWii.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.height(height).aspectRatio(1.33f).clip(shape).background(c.card)
            .border(if (focused) 3.dp else 2.dp, if (focused) c.accent else c.cardBorder, shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) act.homeFocusedTile = true else if (focused) act.homeFocusedTile = false }
            .clickable { act.openGame(g) }.padding(5.dp),
    ) {
        Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize(), titleSize = 11)
    }
}

@Composable
private fun AllGamesTile(act: MainActivity, height: androidx.compose.ui.unit.Dp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.height(height).aspectRatio(1.33f).clip(shape).background(c.accentSoft).border(2.dp, c.accent, shape)
            .clickable { act.screen = Screen.Library },
        contentAlignment = Alignment.Center,
    ) { Text("All games\n▸", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, textAlign = TextAlign.Center) }
}

/** Wii Menu-style bottom bar: settings button, big date/time, controller hints. */
@Composable
private fun HomeBar(act: MainActivity) {
    val c = LocalWii.current
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(c.bar).border(BorderStroke(2.dp, c.barBorder)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(c.card).border(2.dp, c.cardBorder, RoundedCornerShape(50))
                .clickable { act.screen = Screen.Settings },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Settings, "Settings", tint = c.textDim) }
        Text(rememberClock(withDate = true), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("Ⓐ Play · Ⓧ Controls · Ⓨ Favorite · R1 All games", fontSize = 12.sp, color = c.textDim)
    }
}

// ============================================================================ all games

enum class LibSection(val label: String) { ALL("All games"), FAVORITES("Favorites"), RECENT("Recently played"), WII("Wii"), GAMECUBE("GameCube") }
enum class LibSort(val label: String) { RECENT("Recent"), AZ("A–Z"), MOST("Most played") }

@Composable
fun LibraryScreen(act: MainActivity) {
    val c = LocalWii.current
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val favorites = act.prefs.favorites()
    val lastPlayed = act.prefs.lastPlayed()
    val playTime = act.prefs.playTime()

    fun inSection(g: GameEntry, s: LibSection) = when (s) {
        LibSection.ALL -> true
        LibSection.FAVORITES -> g.key in favorites
        LibSection.RECENT -> g.key in lastPlayed
        LibSection.WII -> g.info.platform == Platform.WII
        LibSection.GAMECUBE -> g.info.platform == Platform.GAMECUBE
    }
    val q = act.libraryQuery.trim().lowercase()
    val shown = act.games.filter { inSection(it, act.librarySection) && (q.isEmpty() || it.info.title.lowercase().contains(q)) }.let { list ->
        when (act.librarySort) {
            LibSort.RECENT -> list.sortedWith(compareByDescending<GameEntry> { lastPlayed[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
            LibSort.AZ -> list.sortedBy { it.info.title.lowercase() }
            LibSort.MOST -> list.sortedWith(compareByDescending<GameEntry> { playTime[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
        }
    }

    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) act.focusedGame?.let { act.prefs.toggleFavorite(it.key); act.statsVersion++ }; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (e.firstPress()) act.librarySort = LibSort.entries[(act.librarySort.ordinal + 1) % LibSort.entries.size]; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (e.firstPress()) act.librarySection = LibSection.entries[(act.librarySection.ordinal + 1) % LibSection.entries.size]; true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null } }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(176.dp).fillMaxHeight().background(c.sidebar).border(BorderStroke(2.dp, c.cardBorder)).padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("◀ Home", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Home }.padding(horizontal = 8.dp, vertical = 6.dp))
            LibSection.entries.forEach { s ->
                NavItem(s.label, act.librarySection == s, act.games.count { inSection(it, s) }.toString()) { act.librarySection = s }
            }
            Spacer(Modifier.weight(1f))
            NavItem(if (act.scanning) "Scanning… ${act.scanCount}" else "Rescan folder", false) { act.rescan() }
            NavItem("Settings", false) { act.screen = Screen.Settings }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UpdateBanner(act)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(c.card).border(2.dp, c.cardBorder, RoundedCornerShape(50)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = c.textDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (act.libraryQuery.isEmpty()) Text("Search ${act.games.size} games", color = c.textDim, fontSize = 14.sp)
                        BasicTextField(
                            act.libraryQuery, { act.libraryQuery = it }, singleLine = true,
                            textStyle = TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                LibSort.entries.forEach { s -> WiiChip(s.label, act.librarySort == s, { act.librarySort = s }) }
            }
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                act.scanning -> "Looking for GameCube and Wii games…"
                                act.games.isEmpty() -> "No GameCube or Wii games found."
                                else -> "Nothing here yet."
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        )
                        if (act.games.isEmpty() && !act.scanning) WiiButton("Choose games folder", onClick = act::chooseGamesFolder, primary = true, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.uri.toString() }) { g -> GameCard(act, g, g.key in favorites, playTime[g.key] ?: 0L) }
                }
            }
            HintLine("Ⓐ Choose · Ⓨ Favorite · R1 Sort · L1 Section · Ⓑ Home")
        }
    }
}

@Composable
private fun GameCard(act: MainActivity, g: GameEntry, favorite: Boolean, playMs: Long) {
    val c = LocalWii.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.clip(shape).background(c.card).border(if (focused) 3.dp else 2.dp, if (focused) c.accent else c.cardBorder, shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) act.focusedGame = g }
            .clickable { act.focusedGame = g; act.openGame(g) }.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize())
            if (favorite) Text("★", color = Color(0xFFFFD34D), fontSize = 16.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            Text(
                if (g.info.platform == Platform.GAMECUBE) "GC" else platformLabel(g.info.platform), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3A3F45),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Text(g.info.title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp))
        Text(formatPlayTime(playMs), fontSize = 10.sp, color = c.textDim, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
    }
}

// ============================================================================ game page / preset choice

@Composable
fun GameScreen(act: MainActivity, game: GameEntry) {
    val c = LocalWii.current
    val p = act.picker?.takeIf { it.game.uri == game.uri } ?: return
    val companion = MenuCompanion.showing.value

    if (p.editing && p.selected != null) {
        Column(Modifier.fillMaxSize()) {
            Header(game.info.title, onBack = { p.editing = false })
            PresetEditor(
                base = p.selected!!, customId = act.store.customIdFor(game.key), saveLabel = "Save",
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                onCancel = { p.editing = false },
                onSave = { p.saveEdited(it) },
            )
        }
        return
    }

    // Physical buttons: A / Start = Play, D-pad or left stick = pick a preset, X = Edit, Y = Remember. B = Back.
    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        if (p.editing || e.action != KeyEvent.ACTION_DOWN) false
        else when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { if (e.repeatCount == 0) p.play(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { p.move(1); true }
            KeyEvent.KEYCODE_DPAD_UP -> { p.move(-1); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.repeatCount == 0 && p.selected != null) p.editing = true; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.repeatCount == 0) p.rememberChoice = !p.rememberChoice; true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null } }

    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left: the game.
        Column(Modifier.fillMaxHeight().widthIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("◀ Back", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Library }.padding(4.dp))
            Box(Modifier.weight(1f, fill = false).aspectRatio(0.72f).clip(RoundedCornerShape(14.dp)).border(3.dp, c.accent, RoundedCornerShape(14.dp)).padding(3.dp)) {
                Cover(act, game, act.prefs.showCovers, Modifier.fillMaxSize(), titleSize = 14)
            }
            Text(game.info.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(platformLabel(game.info.platform) + " · " + playSummary(act, game), fontSize = 11.sp, color = c.textDim, maxLines = 2)
            if (rememberHasAutosave(act, game)) Text("▶ Continues from your autosave", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accentText)
        }
        // Right: controls preview (picker on the bottom screen) or the picker itself (single screen).
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sel = p.selected
            WiiCard(Modifier.fillMaxWidth().weight(1f), radius = 20.dp, padding = 12.dp) {
                Text("CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, letterSpacing = 1.sp)
                Text(withOrangeTags(sel?.name ?: p.suggestion.question, c.orange), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                if (companion) {
                    if (sel != null) ControlsOverview(sel, Modifier.weight(1f).fillMaxWidth())
                    else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Pick a control style on the bottom screen (or with the D-pad).", color = c.textDim, textAlign = TextAlign.Center)
                    }
                } else {
                    PresetChooser(p.suggestion.question, p.choices, p.alternatives, sel, p.recommendFirst, Modifier.weight(1f)) { p.selected = it }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WiiChip(if (p.rememberChoice) "✓ Remember" else "Remember", p.rememberChoice, { p.rememberChoice = !p.rememberChoice })
                Spacer(Modifier.weight(1f))
                WiiButton("Ⓧ Edit", onClick = { p.editing = true }, enabled = sel != null)
                WiiButton(if (sel == null) "Choose a style" else "Ⓐ Play", onClick = { p.play() }, primary = true, enabled = sel != null, fontSize = 17.sp)
            }
            HintLine("Ⓐ Play · ✚ choose · Ⓧ Edit · Ⓨ Remember · Ⓑ Back" + if (companion) " · or tap a style on the bottom screen" else "")
        }
    }
}

/** List of presets: the suggested ones first, the rest under "Other layouts". Reused on the bottom screen in game. */
@Composable
fun PresetChooser(
    question: String,
    choices: List<Preset>,
    alternatives: List<Preset>,
    selected: Preset?,
    recommendFirst: Boolean,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onSelect: (Preset) -> Unit,
) {
    var showOthers by remember { mutableStateOf(selected != null && selected in alternatives) }
    // Keep the selected preset visible (it can change from the controller), opening "Other layouts" if needed.
    LaunchedEffect(selected?.id) {
        val sel = selected ?: return@LaunchedEffect
        val inChoices = choices.indexOfFirst { it.id == sel.id }
        val inOthers = alternatives.indexOfFirst { it.id == sel.id }
        val index = when {
            inChoices >= 0 -> 1 + inChoices
            inOthers >= 0 -> { showOthers = true; 1 + choices.size + 1 + inOthers }
            else -> return@LaunchedEffect
        }
        runCatching { listState.animateScrollToItem(index) }
    }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(question, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
        items(choices, key = { it.id }) { p -> PickerCard(p, selected?.id == p.id) { onSelect(p) } }
        if (alternatives.isNotEmpty()) {
            item {
                TextButton(onClick = { showOthers = !showOthers }) { Text(if (showOthers) "Hide other layouts" else "Other layouts (${alternatives.size})") }
            }
            if (showOthers) items(alternatives, key = { "alt-" + it.id }) { p -> PickerCard(p, selected?.id == p.id) { onSelect(p) } }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ============================================================================ first-run walkthrough

@Composable
fun SetupScreen(act: MainActivity) {
    val c = LocalWii.current
    @Suppress("UNUSED_VARIABLE") val version = act.setupVersion
    val dolphinPkg = act.bridge.installedPackage()
    val folderOk = act.bridge.hasConfigAccess() && act.bridge.looksLikeDolphinFolder()
    val hasGames = act.prefs.gamesTreeUri != null
    val helperOn = DolphinExitService.isEnabled(act)
    val calibrated = act.prefs.calibratedDeviceName != null

    class Step(val title: String, val sub: String, val done: Boolean, val button: String?, val optional: Boolean = false, val action: () -> Unit = {})
    val steps = listOf(
        Step("Install Dolphin", if (dolphinPkg != null) "Found $dolphinPkg" else "Install the official Dolphin Emulator, then open it once.", dolphinPkg != null, null),
        Step("Connect Dolphin's folder", "In the picker: side menu (☰) → \"Dolphin\" → \"Use this folder\".", folderOk, if (folderOk) "Change" else "Connect", action = act::connectDolphinFolder),
        Step("Choose your games folder", if (hasGames) "${act.games.size} games found" else "The folder with your GameCube and Wii games.", hasGames, if (hasGames) "Change" else "Choose", action = act::chooseGamesFolder),
        Step("Turn on \"close games\"", "Lets Delphis exit a game cleanly (no input delay).", helperOn, if (helperOn) "Settings" else "Turn on", action = { DolphinExitService.openSettings(act) }),
        Step("Controller check", "Recommended · press each button once (about a minute).", calibrated, if (calibrated) "Redo" else "Start", optional = true, action = { act.screen = Screen.ControllerCheck }),
        Step("Try gyro aiming", "Optional · find the speed you like.", false, "Try", optional = true, action = { act.screen = Screen.GyroTest }),
    )
    val current = steps.indexOfFirst { !it.done }.let { if (it < 0) steps.lastIndex else it }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Welcome! Let's set up Delphis", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("Step ${current + 1} of ${steps.size}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, s ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (s.done || i <= current) c.accent else c.cardBorder))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, s ->
                WiiCard(Modifier.fillMaxWidth(), selected = i == current, radius = 16.dp, padding = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(
                                when { s.done -> Glow.Green; i == current -> c.accent; else -> c.cardBorder.copy(alpha = 0.5f) },
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (s.done) "✓" else "${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (s.done || i == current) Color.White else c.textDim) }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text(s.sub, fontSize = 11.sp, color = c.textDim, maxLines = 2)
                        }
                        if (s.button != null) WiiButton(s.button, onClick = s.action, primary = i == current && !s.done, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (DeviceInfo.screenRoles(act, act.prefs.swapScreens).isDualScreen) "✓ Two screens detected" else "Only one screen detected",
                fontSize = 12.sp, color = c.textDim, modifier = Modifier.weight(1f),
            )
            WiiButton("Go to my games ▸", onClick = { act.screen = Screen.Home; if (act.games.isEmpty()) act.rescan() }, primary = true, enabled = act.isSetUp())
        }
    }
}

// ============================================================================ settings

enum class SettingsSection(val label: String) {
    APPEARANCE("Appearance"), SOUND("Sound"), CONTROLS("Controls"), GYRO("Gyro aiming"), CLOSING("Closing games"),
    BOTTOM("Bottom screen"), LIBRARY("Library & folders"), UPDATES("Updates"), HELP("Diagnostics"),
}

@Composable
fun SettingsScreen(act: MainActivity) {
    val c = LocalWii.current
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(190.dp).fillMaxHeight().background(c.sidebar).border(BorderStroke(2.dp, c.cardBorder))
                .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("◀ Settings", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Home }.padding(horizontal = 8.dp, vertical = 6.dp))
            SettingsSection.entries.forEach { s -> NavItem(s.label, act.settingsSection == s) { act.settingsSection = s } }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(act.settingsSection.label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            when (act.settingsSection) {
                SettingsSection.APPEARANCE -> AppearanceSettings(act)
                SettingsSection.SOUND -> SoundSettings(act)
                SettingsSection.CONTROLS -> ControlsSettings(act)
                SettingsSection.GYRO -> GyroSettings(act)
                SettingsSection.CLOSING -> ClosingSettings(act)
                SettingsSection.BOTTOM -> BottomScreenSettings(act)
                SettingsSection.LIBRARY -> LibrarySettings(act)
                SettingsSection.UPDATES -> UpdatePanel(act)
                SettingsSection.HELP -> {
                    Text("If presets don't respond or something crashes, open Diagnostics and copy the report.", fontSize = 13.sp, color = c.textDim)
                    WiiButton("Open diagnostics", onClick = { act.screen = Screen.Diagnostics }, primary = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            HintLine("✚ choose · Ⓐ select · Ⓑ back")
        }
    }
}

@Composable
private fun AppearanceSettings(act: MainActivity) {
    val c = LocalWii.current
    var covers by remember { mutableStateOf(act.prefs.showCovers) }
    Text("Theme", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemeCard("Light", "Classic Wii look", LightWii, !act.darkTheme) { act.setTheme(false) }
        ThemeCard("Dark", "Easier on battery", DarkWii, act.darkTheme) { act.setTheme(true) }
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(c.accentSoft).border(2.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("ℹ  The in-game bottom screen always uses the dark style, so it saves battery while you play.", fontSize = 13.sp) }
    SwitchRow("Show cover art", "HD box art from GameTDB (needs internet once per game).", covers) { covers = it; act.prefs.showCovers = it }
}

@Composable
private fun ThemeCard(title: String, sub: String, preview: WiiColors, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    WiiCard(Modifier.width(220.dp), selected = selected, radius = 20.dp, padding = 10.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).wiiStripes(preview)
                .border(2.dp, preview.cardBorder, RoundedCornerShape(12.dp)).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B3F9E)).border(2.dp, preview.accent, RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(preview.text))
                Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(preview.textDim))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(56.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(preview.accent))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text(sub, fontSize = 12.sp, color = c.textDim)
        }
    }
}

@Composable
private fun SoundSettings(act: MainActivity) {
    val c = LocalWii.current
    @Suppress("UNUSED_VARIABLE") val v = act.setupVersion // re-read after picking a file
    var on by remember { mutableStateOf(act.prefs.menuMusicOn) }
    var volume by remember { mutableFloatStateOf(act.prefs.menuMusicVolume.toFloat()) }
    val custom = act.prefs.menuMusicUri
    val music = io.github.hiramrg.delphis.platform.MenuMusic
    SwitchRow("Menu music", "Plays in the Delphis menus; stops when a game starts.", on) {
        on = it; act.prefs.menuMusicOn = it; music.refresh(act, act.prefs)
    }
    Text("Volume: ${volume.toInt()}%  (relative to your device's media volume)", fontSize = 14.sp, color = if (on) c.text else c.textDim)
    Slider(
        value = volume, enabled = on, valueRange = 0f..100f,
        onValueChange = { volume = it; act.prefs.menuMusicVolume = it.toInt(); music.refresh(act, act.prefs) },
    )
    Text("Music", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WiiChip("Built-in tune", custom == null, { act.prefs.menuMusicUri = null; music.refresh(act, act.prefs); act.setupVersion++ })
        WiiChip(if (custom != null) "✓ Your file" else "Your own file…", custom != null, { act.chooseMenuMusic() })
        if (custom != null) WiiButton("Change file", onClick = { act.chooseMenuMusic() }, fontSize = 13.sp)
    }
    Text(
        "The built-in tune is an original, Wii-style menu loop made for Delphis. Nintendo's Wii Menu music can't be included, but you can pick your own copy (or any song) as an audio file.",
        fontSize = 12.sp, color = c.textDim,
    )
}

@Composable
private fun ControlsSettings(act: MainActivity) {
    val prefs = act.prefs
    var deviceName by remember { mutableStateOf(prefs.deviceName ?: "") }
    var invert by remember { mutableStateOf(prefs.invertMotion) }
    val detected = remember { prefs.calibratedDeviceName ?: DeviceInfo.builtInGamepadName() }
    val pads = remember { DeviceInfo.allGamepadNames() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (prefs.calibratedDeviceName == null) "Controller check not done yet" else "Controller checked: ${prefs.calibratedDeviceName}", fontSize = 14.sp, modifier = Modifier.weight(1f))
        WiiButton(if (prefs.calibratedDeviceName == null) "Start" else "Redo", onClick = { act.screen = Screen.ControllerCheck })
    }
    SwitchRow("Invert motion steering", "For the Mario Kart \"tilt the Thor\" layout, if turning left steers right.", invert) { invert = it; prefs.invertMotion = it }
    Text("Gamepad name override (leave empty to use the checked/detected one)", fontSize = 13.sp)
    OutlinedTextField(
        value = deviceName, onValueChange = { deviceName = it; prefs.deviceName = it.ifBlank { null } },
        placeholder = { Text(detected ?: "Thor Controller") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Text("Connected gamepads: ${pads.joinToString().ifEmpty { "none" }}", fontSize = 12.sp, color = LocalWii.current.textDim)
}

@Composable
private fun GyroSettings(act: MainActivity) {
    val prefs = act.prefs
    var gyroSpeed by remember { mutableFloatStateOf(prefs.gyroSensitivity.toFloat()) }
    Text("Left/right follows: ${prefs.gyroHorizontal.label}. Speed: ${gyroSpeed.toInt()} (5 ≈ a real Wii Remote). Applies the next time a game starts. To invert up/down or left/right, pick one of the \"inverted\" gyro presets.", fontSize = 13.sp)
    Slider(value = gyroSpeed, onValueChange = { gyroSpeed = it }, onValueChangeFinished = { prefs.gyroSensitivity = gyroSpeed.toInt() }, valueRange = 1f..10f, steps = 8)
    WiiButton("Test gyro aiming", onClick = { act.screen = Screen.GyroTest }, primary = true)
}

@Composable
private fun ClosingSettings(act: MainActivity) {
    val helperOn = remember(act.setupVersion) { DolphinExitService.isEnabled(act) }
    var skipWarn by remember { mutableStateOf(act.prefs.skipExitWarning) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (helperOn) "Close-games helper is on" else "Close-games helper is off", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Presses Dolphin's own \"Exit Emulation\" when you leave or restart a game. Needed on the Thor, where Android won't let Delphis force-close Dolphin. Doesn't touch controller input.",
                fontSize = 12.sp, color = LocalWii.current.textDim,
            )
        }
        Spacer(Modifier.width(10.dp))
        WiiButton(if (helperOn) "Settings" else "Turn on", onClick = { DolphinExitService.openSettings(act) }, primary = !helperOn)
    }
    var autoSave by remember { mutableStateOf(act.prefs.autoSaveResume) }
    SwitchRow(
        "Autosave & resume",
        "When Delphis closes a game it saves a save state (Dolphin's Quick Save slot); next time you start that game from Delphis it continues from there. Needs the close-games helper.",
        autoSave,
    ) { autoSave = it; act.prefs.autoSaveResume = it }
    if (autoSave && act.prefs.saveStatesOff) {
        Text(
            "⚠ Dolphin's save states are turned off, so nothing could be saved. Delphis has switched them on in Dolphin's settings file; if it still doesn't work, open Dolphin → Settings → Config → General → Enable Savestates.",
            fontSize = 12.sp, color = LocalWii.current.orange,
        )
    }
    SwitchRow(
        "Warn before \"Back to main menu\"",
        if (autoSave) "Shows a reminder before the game closes." else "Shows a reminder that unsaved progress will be lost.",
        !skipWarn,
    ) { skipWarn = !it; act.prefs.skipExitWarning = !it }
}

@Composable
private fun BottomScreenSettings(act: MainActivity) {
    val prefs = act.prefs
    var brightness by remember { mutableFloatStateOf(prefs.brightnessPercent.toFloat()) }
    var delaySec by remember { mutableFloatStateOf(prefs.dimDelaySeconds.toFloat()) }
    var swap by remember { mutableStateOf(prefs.swapScreens) }
    Text("Brightness while playing: ${brightness.toInt()} (also changeable in game)", fontSize = 14.sp)
    Slider(value = brightness, onValueChange = { brightness = it }, onValueChangeFinished = { prefs.brightnessPercent = brightness.toInt() }, valueRange = 0f..100f)
    Text("Dim after: ${delaySec.toInt()} s", fontSize = 14.sp)
    Slider(value = delaySec, onValueChange = { delaySec = it; prefs.dimDelaySeconds = it.toInt() }, valueRange = 0f..15f, steps = 14)
    SwitchRow("Swap top and bottom screens", "Use if games open on the small screen.", swap) { swap = it; prefs.swapScreens = it }
}

@Composable
private fun LibrarySettings(act: MainActivity) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WiiButton("Dolphin folder", onClick = act::connectDolphinFolder)
        WiiButton("Games folder", onClick = act::chooseGamesFolder)
        WiiButton("Rescan", onClick = act::rescan)
    }
    Text("Dolphin: ${act.bridge.installedPackage() ?: "not installed"} · ${act.games.size} games", fontSize = 12.sp, color = LocalWii.current.textDim)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = LocalWii.current.textDim)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
