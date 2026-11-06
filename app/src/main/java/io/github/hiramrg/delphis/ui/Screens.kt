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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.text.withStyle
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
    val favorites = act.prefs.favorites()
    val playTime = act.prefs.playTime()
    val recent = act.games.filter { it.key in lastPlayed }.sortedByDescending { lastPlayed[it.key] }
    val hero = recent.firstOrNull()
    val shown = libraryList(act, favorites, lastPlayed, playTime)
    val grid = act.homeGrid
    // Rows above the game list: hero, then (if any) the "Recently played" title and row.
    val gamesHeaderIndex = 1 + if (recent.size > 1) 2 else 0

    LaunchedEffect(act.scrollToGamesRequest) {
        if (act.scrollToGamesRequest > 0) runCatching { grid.animateScrollToItem(gamesHeaderIndex) }
    }

    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        val target = act.focusedGame ?: hero
        if (act.searchEditing) return@rememberUpdatedState false
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START ->
                if (act.homeFocusedTile || hero == null) false else { if (e.firstPress()) act.quickPlay(hero); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.firstPress()) target?.let { act.openGame(it) }; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) target?.let { act.prefs.toggleFavorite(it.key); act.statsVersion++ }; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (e.firstPress()) act.libraryOpen = !act.libraryOpen; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (e.firstPress()) act.librarySort = LibSort.entries[(act.librarySort.ordinal + 1) % LibSort.entries.size]; true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null; act.homeFocusedTile = false; act.homeHint = null; act.searchEditing = false } }

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(act)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LibraryColumn(act, favorites)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp), state = grid,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp)
                            .onFocusChanged { if (it.hasFocus) { act.homeFocusedTile = false; act.focusedGame = null } },
                    ) { if (hero != null) HeroCard(act, hero) else WelcomeCard(act) }
                }
                if (recent.size > 1) {
                    item(key = "recent-title", span = { GridItemSpan(maxLineSpan) }) {
                        Text("Recently played", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recent.drop(1).take(12), key = { it.uri.toString() }) { g -> ChannelTile(act, g, 64.dp) }
                            item { AllGamesTile(act, 64.dp) }
                        }
                    }
                }
                item(key = "games-header", span = { GridItemSpan(maxLineSpan) }) { GamesHeader(act, shown.size) }
                if (shown.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when {
                                    act.scanning -> "Looking for GameCube and Wii games…"
                                    act.games.isEmpty() -> "No GameCube or Wii games found."
                                    act.librarySection == LibSection.FAVORITES -> "Press Ⓨ on a game to add it to your favorites."
                                    else -> "No games match."
                                },
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = c.textDim,
                            )
                            if (act.games.isEmpty() && !act.scanning) WiiButton("Choose games folder", onClick = act::chooseGamesFolder, primary = true, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                } else {
                    items(shown, key = { it.uri.toString() }) { g -> GameCard(act, g, g.key in favorites, playTime[g.key] ?: 0L) }
                }
            }
        }
        HomeBar(act)
    }
}

/** The game list part of Home: section title, search and sort. */
@Composable
private fun GamesHeader(act: MainActivity, count: Int) {
    val c = LocalWii.current
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp).onFocusChanged { if (it.hasFocus) { act.homeFocusedTile = true; act.focusedGame = null } },
    ) {
        Text(act.librarySection.label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text("$count", fontSize = 12.sp, color = c.textDim, modifier = Modifier.weight(1f))
        SearchBox(act)
        LibSort.entries.forEach { s -> WiiChip(s.label, act.librarySort == s, { act.librarySort = s }, Modifier.focusRing(RoundedCornerShape(50))) }
    }
}

/**
 * The search box never opens the keyboard by itself: moving onto it with the D-pad only highlights
 * it, A starts typing, and B (or the keyboard's Done) closes the keyboard and returns to the box.
 */
@Composable
private fun SearchBox(act: MainActivity) {
    val c = LocalWii.current
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val pillFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var returnFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    fun stop() {
        keyboard?.hide()
        editing = false
        act.searchEditing = false
        returnFocus = true
    }
    Row(
        Modifier.width(150.dp).height(34.dp).clip(shape).background(c.card)
            .border(if (focused || editing) 3.dp else 2.dp, if (focused || editing) c.accent else c.cardBorder, shape)
            .then(
                if (editing) Modifier
                else Modifier.focusRequester(pillFocus)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) act.homeHint = "Ⓐ Type to search" else if (act.homeHint == "Ⓐ Type to search") act.homeHint = null
                    }
                    .clickable { editing = true; act.searchEditing = true },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.textDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (editing) {
                BasicTextField(
                    act.libraryQuery, { act.libraryQuery = it }, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 13.sp), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { stop() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
                        .onPreviewKeyEvent { e ->
                            val code = e.nativeKeyEvent.keyCode
                            if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                                if (e.nativeKeyEvent.action == KeyEvent.ACTION_UP) stop()
                                true
                            } else false
                        }
                        .onFocusChanged { if (!it.isFocused && editing) { editing = false; act.searchEditing = false } },
                )
            } else {
                Text(act.libraryQuery.ifEmpty { "Search" }, color = if (act.libraryQuery.isEmpty()) c.textDim else c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!editing && focused) Text("Ⓐ type", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
    }
    LaunchedEffect(editing) {
        if (editing) {
            kotlinx.coroutines.delay(50)
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            kotlinx.coroutines.delay(50)
            runCatching { pillFocus.requestFocus() }
            returnFocus = false
        }
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
            else WiiButton("All games ▸", onClick = { act.showAllGames() }, primary = true)
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
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = g } }
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
            .clickable { act.showAllGames() },
        contentAlignment = Alignment.Center,
    ) { Text("All games\n▸", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, textAlign = TextAlign.Center) }
}

/** Wii Menu-style bottom bar: settings button, big date/time, controller hints. */
@Composable
private fun HomeBar(act: MainActivity) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth().height(48.dp)
            .clip(shape).background(c.bar).border(2.dp, c.barBorder, shape).padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).focusRing(RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(c.card).border(2.dp, c.cardBorder, RoundedCornerShape(50))
                .onFocusChanged { if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null } }
                .clickable { act.screen = Screen.Settings },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Settings, "Settings", tint = c.textDim) }
        Text(rememberClock(withDate = true), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(act.homeHint ?: "Ⓐ Play · Ⓧ Controls · Ⓨ Favorite · L1 Library", fontSize = 12.sp, color = c.textDim)
    }
}

/** Blue ring around whatever the D-pad is on, so it's always clear where you are. */
fun Modifier.focusRing(shape: androidx.compose.ui.graphics.Shape, width: androidx.compose.ui.unit.Dp = 3.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val c = LocalWii.current
    this.then(if (focused) Modifier.border(width, c.accent, shape) else Modifier).onFocusChanged { focused = it.hasFocus }
}

// ============================================================================ library (column + list on Home)

enum class LibSection(val label: String) { ALL("All games"), FAVORITES("Favorites"), WII("Wii"), GAMECUBE("GameCube") }
enum class LibSort(val label: String) { RECENT("Recent"), AZ("A–Z"), MOST("Most played") }

private fun inSection(g: GameEntry, s: LibSection, favorites: Set<String>) = when (s) {
    LibSection.ALL -> true
    LibSection.FAVORITES -> g.key in favorites
    LibSection.WII -> g.info.platform == Platform.WII
    LibSection.GAMECUBE -> g.info.platform == Platform.GAMECUBE
}

/** The games in the selected section, filtered by the search and sorted. */
private fun libraryList(act: MainActivity, favorites: Set<String>, lastPlayed: Map<String, Long>, playTime: Map<String, Long>): List<GameEntry> {
    val q = act.libraryQuery.trim().lowercase()
    val list = act.games.filter { inSection(it, act.librarySection, favorites) && (q.isEmpty() || it.info.title.lowercase().contains(q)) }
    return when (act.librarySort) {
        LibSort.RECENT -> list.sortedWith(compareByDescending<GameEntry> { lastPlayed[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
        LibSort.AZ -> list.sortedBy { it.info.title.lowercase() }
        LibSort.MOST -> list.sortedWith(compareByDescending<GameEntry> { playTime[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
    }
}

/** Retractable column on the left of Home: All games, Favorites, Wii, GameCube. L1 or the arrow opens/closes it. */
@Composable
private fun LibraryColumn(act: MainActivity, favorites: Set<String>) {
    val c = LocalWii.current
    val open = act.libraryOpen
    val width by androidx.compose.animation.core.animateDpAsState(if (open) 164.dp else 58.dp, label = "library column")
    val shape = RoundedCornerShape(20.dp)
    // Rows tell Home that A belongs to them (not "play the hero") while they have the focus.
    fun Modifier.claimsA(hint: String? = null) = onFocusChanged {
        if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null; act.homeHint = hint }
        else if (act.homeHint == hint) act.homeHint = null
    }
    Column(
        Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp).width(width).fillMaxHeight()
            .clip(shape).background(c.sidebar).border(2.dp, c.cardBorder, shape).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (open) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (open) Text("Library", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1)
            else Spacer(Modifier.weight(1f))
            // The open/close button: reachable with the D-pad (A toggles) and labelled with its L1 shortcut.
            val pill = RoundedCornerShape(50)
            Row(
                Modifier.height(30.dp).focusRing(pill).clip(pill).border(2.dp, c.cardBorder, pill)
                    .claimsA(if (open) "Ⓐ Hide column · L1 Library" else "Ⓐ Show column · L1 Library")
                    .clickable { act.libraryOpen = !open }
                    .padding(horizontal = if (open) 8.dp else 0.dp).then(if (open) Modifier else Modifier.width(30.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                Text(if (open) "◀" else "▶", fontSize = 11.sp, color = c.textDim)
                if (open) Text(
                    "L1", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = c.card,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.text).padding(horizontal = 4.dp),
                )
            }
            if (!open) Spacer(Modifier.weight(1f))
        }
        LibSection.entries.forEach { s ->
            val selected = act.librarySection == s
            val rowShape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().focusRing(rowShape).clip(rowShape).background(if (selected) c.accentSoft else Color.Transparent)
                    .border(2.dp, if (selected) c.accent else Color.Transparent, rowShape)
                    .claimsA()
                    .clickable { act.librarySection = s; act.showAllGames() }
                    .padding(horizontal = if (open) 10.dp else 0.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (open) Arrangement.Start else Arrangement.Center,
            ) {
                LibraryIcon(s, if (selected) c.accentText else c.text, Modifier.size(22.dp))
                if (open) {
                    Spacer(Modifier.width(10.dp))
                    Text(s.label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) c.accentText else c.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${act.games.count { inSection(it, s, favorites) }}", fontSize = 12.sp, color = c.textDim)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (open) Text("L1 hide · R1 sort", fontSize = 11.sp, color = c.textDim, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Simple, rounded icons for the library column (drawn, so they follow the theme colours). */
@Composable
fun LibraryIcon(section: LibSection, tint: Color, modifier: Modifier) {
    val bg = LocalWii.current.sidebar
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        val sw = 2.2f * u
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * u, y * u)
        fun sz(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w * u, h * u)
        fun r(v: Float) = androidx.compose.ui.geometry.CornerRadius(v * u, v * u)
        when (section) {
            LibSection.ALL -> { // two stacked game cases
                drawRoundRect(tint, o(3.5f, 3f), sz(12.5f, 15f), r(4f), style = stroke)
                drawRoundRect(bg, o(8f, 6.5f), sz(12.5f, 15f), r(4f))
                drawRoundRect(tint, o(8f, 6.5f), sz(12.5f, 15f), r(4f), style = stroke)
                drawCircle(tint, 2.1f * u, o(14.25f, 14f))
            }
            LibSection.FAVORITES -> { // soft star
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0 until 10) {
                    val a = Math.toRadians((-90 + i * 36).toDouble())
                    val rad = if (i % 2 == 0) 9.2f else 4.4f
                    val x = 12f + rad * Math.cos(a).toFloat(); val y = 12.8f + rad * Math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u)
                }
                path.close()
                drawPath(path, tint, alpha = 0.25f)
                drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            LibSection.WII -> { // a remote
                drawRoundRect(tint, o(8f, 2f), sz(8f, 20f), r(4f), style = stroke)
                drawCircle(tint, 2f * u, o(12f, 8.5f))
                drawCircle(tint, 1f * u, o(12f, 14.5f))
                drawCircle(tint, 1f * u, o(12f, 17.5f))
            }
            LibSection.GAMECUBE -> { // a soft cube, top face tinted like the star
                fun poly(vararg pts: Float) = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0] * u, pts[1] * u)
                    for (k in 2 until pts.size step 2) lineTo(pts[k] * u, pts[k + 1] * u)
                    close()
                }
                val top = poly(12f, 2.9f, 20f, 7.4f, 12f, 11.9f, 4f, 7.4f)
                drawPath(top, tint, alpha = 0.25f)
                drawPath(poly(12f, 2.9f, 20f, 7.4f, 20f, 16.6f, 12f, 21.1f, 4f, 16.6f, 4f, 7.4f), tint, style = stroke)
                drawPath(top, tint, style = stroke)
                drawLine(tint, o(12f, 11.9f), o(12f, 21.1f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
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
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) { act.focusedGame = g; act.homeFocusedTile = true } }
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
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Home }.padding(4.dp))
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
                Text(if (sel != null) presetTitle(sel.name, sel.id == p.suggestedId) else androidx.compose.ui.text.AnnotatedString(p.suggestion.question), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                if (companion) {
                    if (sel != null) ControlsOverview(sel, Modifier.weight(1f).fillMaxWidth())
                    else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Pick a control style on the bottom screen (or with the D-pad).", color = c.textDim, textAlign = TextAlign.Center)
                    }
                } else {
                    PresetChooser(p.suggestion.question, p.choices, p.alternatives, sel, p.suggestedId, Modifier.weight(1f)) { p.selected = it }
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

/** Gyro presets (marked "(experimental)") are grouped in their own folder in every preset list. */
fun Preset.isGyro(): Boolean =
    mappings.any { it.target is io.github.hiramrg.delphis.core.Target.GyroPointer } || name.contains("(experimental)", ignoreCase = true)

/** How a game's presets are shown: the main list, the gyro folder, and "Other layouts". */
class PresetGroups(choices: List<Preset>, alternatives: List<Preset>) {
    val main = choices.filterNot { it.isGyro() }
    val gyro = (choices + alternatives).filter { it.isGyro() }.distinctBy { it.id }
    val others = alternatives.filterNot { it.isGyro() }
    /** Top-to-bottom order, used for D-pad navigation. */
    val ordered get() = main + gyro + others
}

/** The preset's name with its "(experimental)" tag in orange and, if suggested, "(suggested)" in green. */
@Composable
fun presetTitle(name: String, suggested: Boolean): androidx.compose.ui.text.AnnotatedString {
    val c = LocalWii.current
    return androidx.compose.ui.text.buildAnnotatedString {
        append(withOrangeTags(name, c.orange))
        if (suggested) {
            append(" ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = c.green, fontWeight = FontWeight.ExtraBold)) { append("(Suggested)") }
        }
    }
}

/** A folder row in a preset list (tap to open or close it). */
@Composable
private fun PresetFolder(title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.accentSoft).border(2.dp, c.accent.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        Spacer(Modifier.width(8.dp))
        Text(withOrangeTags(title, c.orange), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.card).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/**
 * List of a game's presets: the suggested one first (tagged "(suggested)"), then the gyro presets in a
 * "Gyro aiming (experimental)" folder and the rest in an "Other layouts" folder. Used on the game page,
 * the bottom-screen picker and "Change controls" in game.
 */
@Composable
fun PresetChooser(
    question: String,
    choices: List<Preset>,
    alternatives: List<Preset>,
    selected: Preset?,
    suggestedId: String?,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onSelect: (Preset) -> Unit,
) {
    val groups = remember(choices, alternatives) { PresetGroups(choices, alternatives) }
    var showGyro by remember { mutableStateOf(groups.gyro.any { it.id == selected?.id }) }
    var showOthers by remember { mutableStateOf(groups.others.any { it.id == selected?.id }) }
    // Keep the selected preset visible (it can change from the controller), opening its folder if needed.
    LaunchedEffect(selected?.id) {
        val id = selected?.id ?: return@LaunchedEffect
        val m = groups.main.size
        val g = groups.gyro.indexOfFirst { it.id == id }
        val o = groups.others.indexOfFirst { it.id == id }
        if (g >= 0) showGyro = true
        if (o >= 0) showOthers = true
        val gyroRows = if (groups.gyro.isEmpty()) 0 else 1 + if (showGyro) groups.gyro.size else 0
        val index = when {
            groups.main.any { it.id == id } -> 1 + groups.main.indexOfFirst { it.id == id }
            g >= 0 -> 1 + m + 1 + g
            o >= 0 -> 1 + m + gyroRows + 1 + o
            else -> return@LaunchedEffect
        }
        runCatching { listState.animateScrollToItem(index) }
    }
    @Composable fun card(p: Preset) = PickerCard(p, selected?.id == p.id, suggested = p.id == suggestedId) { onSelect(p) }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(question, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
        items(groups.main, key = { it.id }) { p -> card(p) }
        if (groups.gyro.isNotEmpty()) {
            item(key = "gyro-folder") { PresetFolder("Gyro aiming (Experimental)", groups.gyro.size, showGyro) { showGyro = !showGyro } }
            if (showGyro) items(groups.gyro, key = { "gyro-" + it.id }) { p -> Box(Modifier.padding(start = 14.dp)) { card(p) } }
        }
        if (groups.others.isNotEmpty()) {
            item(key = "others-folder") { PresetFolder("Other layouts", groups.others.size, showOthers) { showOthers = !showOthers } }
            if (showOthers) items(groups.others, key = { "alt-" + it.id }) { p -> Box(Modifier.padding(start = 14.dp)) { card(p) } }
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
        ThemeCard("Light", "Classic Wii look", listOf(LightWii), act.themeMode == "light") { act.setTheme("light") }
        ThemeCard("Dark", "Easier on battery", listOf(DarkWii), act.themeMode == "dark") { act.setTheme("dark") }
        ThemeCard("Auto", "Follows Android", listOf(LightWii, DarkWii), act.themeMode == "auto") { act.setTheme("auto") }
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(c.accentSoft).border(2.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("ℹ  The in-game bottom screen always uses the dark style, so it saves battery while you play.", fontSize = 13.sp) }
    SwitchRow("Show cover art", "HD box art from GameTDB (needs internet once per game).", covers) { covers = it; act.prefs.showCovers = it }
}

@Composable
private fun ThemeCard(title: String, sub: String, previews: List<WiiColors>, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    WiiCard(Modifier.width(150.dp), selected = selected, radius = 20.dp, padding = 8.dp, onClick = onClick) {
        // One mini screen per palette; Auto shows light and dark side by side.
        Row(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, c.cardBorder, RoundedCornerShape(12.dp))) {
            previews.forEach { preview ->
                Row(
                    Modifier.weight(1f).fillMaxHeight().wiiStripes(preview).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.width(if (previews.size > 1) 18.dp else 36.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(0xFF2B3F9E)).border(2.dp, preview.accent, RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(preview.text))
                        Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(preview.textDim))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(preview.accent))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, fontSize = 11.sp, color = c.textDim, maxLines = 1)
            }
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
    var invertX by remember { mutableStateOf(prefs.gyroInvertX) }
    var invertY by remember { mutableStateOf(prefs.gyroInvertY) }
    Text("Left/right follows: ${prefs.gyroHorizontal.label}. Speed: ${gyroSpeed.toInt()} (5 ≈ a real Wii Remote). Changes apply the next time a game starts.", fontSize = 13.sp)
    Slider(value = gyroSpeed, onValueChange = { gyroSpeed = it }, onValueChangeFinished = { prefs.gyroSensitivity = gyroSpeed.toInt() }, valueRange = 1f..10f, steps = 8)
    SwitchRow("Invert up/down", "Tilting the Thor up moves the pointer down.", invertY) { invertY = it; prefs.gyroInvertY = it }
    SwitchRow("Invert left/right", "Turning the Thor left moves the pointer right.", invertX) { invertX = it; prefs.gyroInvertX = it }
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
