package io.github.hiramrg.delphis.ui

import io.github.hiramrg.delphis.core.BottomScreen
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
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
        value = act.prefs.autoSaveFor(g.info.gameId) && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.hasAutosave(g.info.gameId) }
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
    // When the D-pad moves up into Continue playing, show the whole card. Android's own
    // "bring the focused button into view" scroll runs right after focus moves and would cancel
    // an earlier scroll, so wait for it to finish, then scroll all the way to the top.
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(heroFocused) {
        if (!heroFocused) return@LaunchedEffect
        delay(80)
        androidx.compose.runtime.snapshotFlow { grid.isScrollInProgress }.first { !it }
        if (grid.firstVisibleItemIndex != 0 || grid.firstVisibleItemScrollOffset != 0) grid.animateScrollToItem(0)
    }
    // Rows above the game list: hero, then (if any) the "Recently played" title and row.
    val gamesHeaderIndex = 1 + if (recent.size > 1) 2 else 0

    LaunchedEffect(act.scrollToGamesRequest) {
        if (act.scrollToGamesRequest > 0) runCatching { grid.animateScrollToItem(gamesHeaderIndex) }
    }

    // "All games" with no search: Netflix-style rows (Recently played, Wii, GameCube, Favorites, All games).
    val rowsMode = act.librarySection == LibSection.ALL && act.libraryQuery.isBlank() && act.games.isNotEmpty()
    val heroAchievements = hero?.let { rememberAchievements(act.prefs, it.key, it.info) }
    var showHeroAchievements by remember { mutableStateOf(false) }
    if (showHeroAchievements && heroAchievements != null) AchievementsDialog(heroAchievements) { showHeroAchievements = false }

    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        val target = act.focusedGame ?: hero
        if (act.searchEditing) return@rememberUpdatedState false
        val onRow = rowsMode && act.homeRow != null && act.focusedGame != null
        when (e.keyCode) {
            // On a row, Ⓐ plays the game under the ring (Ⓧ opens its game page).
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START ->
                if (onRow) { if (e.firstPress()) act.focusedGame?.let { act.quickPlay(it) }; true }
                else if (act.homeFocusedTile || hero == null) false else { if (e.firstPress()) act.quickPlay(hero); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.firstPress()) target?.let { act.openGame(it) }; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) target?.let { act.prefs.toggleFavorite(it.key); act.statsVersion++ }; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (e.firstPress()) act.libraryOpen = !act.libraryOpen; true }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (e.firstPress()) {
                    val row = act.homeRow
                    if (rowsMode && row != null && row.sortable) {
                        val cur = rowSort(act, row)
                        act.prefs.setRowSort(row.name, LibSort.entries[(cur.ordinal + 1) % LibSort.entries.size].name); act.statsVersion++
                    } else act.librarySort = LibSort.entries[(act.librarySort.ordinal + 1) % LibSort.entries.size]
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                if (e.firstPress() && heroAchievements != null && act.homeRow == null) showHeroAchievements = true
                heroAchievements != null && act.homeRow == null
            }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null; act.homeFocusedTile = false; act.homeHint = null; act.searchEditing = false; act.homeRow = null } }

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(act)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LibraryColumn(act, favorites)
            if (rowsMode) HomeRows(act, hero, recent, favorites, lastPlayed, playTime, heroAchievements) { showHeroAchievements = true }
            else LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp), state = grid,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp)
                            .onFocusChanged {
                                heroFocused = it.hasFocus
                                if (it.hasFocus) {
                                    // The hero's own buttons say whether A is theirs (see HeroCard).
                                    if (hero == null) act.homeFocusedTile = false
                                    act.focusedGame = null
                                }
                            },
                    ) { if (hero != null) HeroCard(act, hero, heroAchievements) { showHeroAchievements = true } else WelcomeCard(act) }
                }
                if (recent.size > 1) {
                    item(key = "recent-title", span = { GridItemSpan(maxLineSpan) }) {
                        Text("Recently played", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recent.drop(1).take(12), key = { it.uri.toString() }) { g -> ChannelTile(act, g, 64.dp) }
                        }
                    }
                }
                item(key = "games-header", span = { GridItemSpan(maxLineSpan) }) { GamesHeader(act, shown.size) }
                if (shown.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            HintText(
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
        if (!editing && focused) HintText("Ⓐ type", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
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

// ============================================================================ home rows

/** Home's rows in "All games": Recently played and Favorites mix both platforms; Wii and GameCube have their own. */
enum class HomeRow(val title: String, val sortable: Boolean) {
    RECENT("Recently played", false), WII("Wii", true), GAMECUBE("GameCube", true), FAVORITES("Favorites", true), ALL("All games", true)
}

private fun rowSort(act: MainActivity, row: HomeRow): LibSort =
    act.prefs.rowSort(row.name)?.let { n -> LibSort.entries.firstOrNull { it.name == n } } ?: LibSort.RECENT

/** White for Wii, purple for GameCube: the rim around covers and the row tags. */
val WiiWhite = Color(0xFFF4F7FA)
val CubePurple = Color(0xFF5B4BC4)
fun platformColour(p: Platform) = if (p == Platform.GAMECUBE) CubePurple else WiiWhite

/** A thin rim in the game's platform colour, so mixed rows stay easy to read. */
fun Modifier.platformRim(g: GameEntry, width: androidx.compose.ui.unit.Dp = 2.5.dp): Modifier =
    this.border(width, platformColour(g.info.platform), RoundedCornerShape(10.dp))

/** "Wii" (white) or "▢ GameCube" (purple) tag. */
@Composable
fun PlatformTag(p: Platform, small: Boolean = false) {
    val gc = p == Platform.GAMECUBE
    Row(
        Modifier.height(if (small) 17.dp else 22.dp).clip(RoundedCornerShape(50)).background(if (gc) CubePurple else WiiWhite).padding(horizontal = if (small) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (gc) Box(Modifier.size(if (small) 7.dp else 9.dp).rotate(45f).border(2.dp, Color.White, RoundedCornerShape(2.dp)))
        Text(if (gc) "GameCube" else "Wii", fontSize = if (small) 10.sp else 12.sp, fontWeight = FontWeight.Black, color = if (gc) Color.White else Color(0xFF1A6FB5), maxLines = 1)
    }
}

@Composable
private fun HomeRows(
    act: MainActivity, hero: GameEntry?, recent: List<GameEntry>, favorites: Set<String>,
    lastPlayed: Map<String, Long>, playTime: Map<String, Long>,
    heroAchievements: io.github.hiramrg.delphis.core.RetroAchievements.GameProgress?, onHeroAchievements: () -> Unit,
) {
    val state = rememberLazyListState()
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val rows = buildList {
        recent.drop(1).take(12).takeIf { it.isNotEmpty() }?.let { add(HomeRow.RECENT to it) }
        act.games.filter { it.info.platform == Platform.WII }.takeIf { it.isNotEmpty() }?.let { add(HomeRow.WII to sortGames(it, rowSort(act, HomeRow.WII), lastPlayed, playTime)) }
        act.games.filter { it.info.platform == Platform.GAMECUBE }.takeIf { it.isNotEmpty() }?.let { add(HomeRow.GAMECUBE to sortGames(it, rowSort(act, HomeRow.GAMECUBE), lastPlayed, playTime)) }
        act.games.filter { it.key in favorites }.takeIf { it.isNotEmpty() }?.let { add(HomeRow.FAVORITES to sortGames(it, rowSort(act, HomeRow.FAVORITES), lastPlayed, playTime)) }
        add(HomeRow.ALL to sortGames(act.games, rowSort(act, HomeRow.ALL), lastPlayed, playTime))
    }
    // The top card: back at the top, show all of it.
    LaunchedEffect(act.homeRow) { if (act.homeRow == null && state.firstVisibleItemIndex <= 1) runCatching { state.animateScrollToItem(0) } }
    // Sidebar "All games" jumps to the All games row.
    LaunchedEffect(act.scrollToGamesRequest) { if (act.scrollToGamesRequest > 0) runCatching { state.animateScrollToItem(rows.size) } }
    LazyColumn(
        Modifier.fillMaxHeight().fillMaxWidth(), state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "top") {
            Box(Modifier.fillMaxWidth().height(150.dp).onFocusChanged { if (it.hasFocus) { act.homeRow = null; act.focusedGame = null; if (hero == null) act.homeFocusedTile = false } }) {
                // The continue card stays underneath (so the D-pad can always go back up to its buttons);
                // while the ring is on a row, the preview of that game covers it.
                if (hero != null) HeroCard(act, hero, heroAchievements, onHeroAchievements) else WelcomeCard(act)
                act.focusedGame?.takeIf { act.homeRow != null }?.let { PreviewCard(act, it, playTime[it.key] ?: 0L) }
            }
        }
        rows.forEach { (row, games) -> item(key = "row-" + row.name) { GameRowView(act, row, games, favorites) } }
    }
}

/** The top card while the ring is on a row: the game under the ring, tinted in its platform's colour. */
@Composable
private fun PreviewCard(act: MainActivity, g: GameEntry, playMs: Long) {
    val c = LocalWii.current
    val gc = g.info.platform == Platform.GAMECUBE
    val tint = if (gc) Color(0xFF2A2160) else Color(0xFF1F3F57)
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxSize().clip(shape).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(tint, c.card)))
            .border(2.dp, if (gc) CubePurple else Color(0xFF9FD3F2), shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.72f)) { Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g), titleSize = 14) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val when_ = if (playMs > 60_000) formatPlayTime(playMs) + " played" else "New"
            Text((if (gc) "GAMECUBE" else "WII") + " · " + when_.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                color = if (gc) Color(0xFFC8C0F0) else Color(0xFFBFE0F5), maxLines = 1)
            Text(g.info.title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
            HintText("Ⓐ Play · Ⓧ Game page · Ⓨ " + if (act.prefs.isFavorite(g.key)) "Unfavorite" else "Favorite")
        }
    }
}

/**
 * One Home row. The row the ring is on grows and shows its sort chips (R1 steps through them); the
 * others shrink and fade. Each row remembers its own sort.
 */
@Composable
private fun GameRowView(act: MainActivity, row: HomeRow, games: List<GameEntry>, favorites: Set<String>) {
    val c = LocalWii.current
    val focused = act.homeRow == row
    val quiet = act.homeRow != null && !focused
    val coverW by androidx.compose.animation.core.animateDpAsState(when { focused -> 76.dp; quiet -> 46.dp; else -> 64.dp }, label = "row size")
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (quiet) 0.5f else 1f, label = "row fade")
    Column(Modifier.fillMaxWidth().alpha(alpha)) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (row) {
                HomeRow.WII -> PlatformTag(Platform.WII)
                HomeRow.GAMECUBE -> PlatformTag(Platform.GAMECUBE)
                HomeRow.FAVORITES -> Text("★ Favorites", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                else -> Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Text("${games.size}", fontSize = 12.sp, color = c.textDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (focused && row.sortable) {
                KeyBadge("R1")
                val cur = rowSort(act, row)
                LibSort.entries.forEach { s ->
                    val on = s == cur
                    Text(s.label, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = if (on) Color(0xFF0A2533) else c.text,
                        modifier = Modifier.focusProperties { canFocus = false }.clip(RoundedCornerShape(50)).background(if (on) c.accent else c.card)
                            .border(2.dp, if (on) c.accent else c.cardBorder, RoundedCornerShape(50))
                            .clickable { act.prefs.setRowSort(row.name, s.name); act.statsVersion++ }.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 3.dp)) {
            items(games, key = { row.name + it.uri.toString() }) { g -> RowTile(act, row, g, coverW, g.key in favorites) }
        }
    }
}

@Composable
private fun RowTile(act: MainActivity, row: HomeRow, g: GameEntry, coverW: androidx.compose.ui.unit.Dp, favorite: Boolean) {
    val c = LocalWii.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(
        Modifier.width(coverW + 6.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = g; act.homeRow = row; act.homeHint = "Ⓐ Play · Ⓧ Game page · Ⓨ Favorite · R1 Sort" }
            }
            .clickable { act.focusedGame = g; act.openGame(g) },
    ) {
        Box(Modifier.size(coverW + 6.dp, coverW / 0.72f + 6.dp).border(3.dp, if (focused) c.accent else Color.Transparent, shape).padding(3.dp)) {
            Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g), titleSize = 10)
            if (favorite) Text("★", color = Color(0xFFFFD34D), fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        if (coverW > 50.dp) Text(g.info.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp, top = 1.dp))
    }
}

@Composable
private fun HeroCard(act: MainActivity, g: GameEntry, achievements: io.github.hiramrg.delphis.core.RetroAchievements.GameProgress? = null, onAchievements: () -> Unit = {}) {
    val c = LocalWii.current
    val preset = act.prefs.presetFor(g.key)?.let { act.store.byId(it) }
    val fav = act.prefs.isFavorite(g.key)
    val autosave = rememberHasAutosave(act, g)
    WiiCard(Modifier.fillMaxSize(), radius = 24.dp, padding = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On the Thor's top screen the card is short: one-line title, fewer detail lines.
            val tight = maxHeight < 190.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // No accent frame here: the only blue ring on Home is the one that follows the D-pad focus.
                Box(Modifier.fillMaxHeight().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))) {
                    Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g), titleSize = 14)
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlatformTag(g.info.platform, small = true)
                            Text(playSummary(act, g) + if (fav) "  ★" else "", fontSize = 12.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            achievements?.let { a -> GameChip("🏆 ${a.unlocked}/${a.total}", "R3", height = 24.dp, onClick = onAchievements) }
                        }
                        if (autosave) Text("▶ Continues from your autosave", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.accentText, maxLines = 1)
                        if (!tight || !autosave) Text(withOrangeTags("Controls: ${preset?.name ?: "choose when you play"}", c.orange), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Both buttons neutral, so the blue D-pad ring is the only highlight and is easy to see.
                        val pill = RoundedCornerShape(50)
                        WiiButton("Ⓐ Play", onClick = { act.quickPlay(g) }, Modifier.onFocusChanged { if (it.isFocused) act.homeFocusedTile = false }.focusRing(pill), fontSize = 16.sp)
                        WiiButton(
                            "Ⓧ Controls", onClick = { act.openGame(g) },
                            // While Controls has the focus, A presses it instead of Home's "play the hero" shortcut.
                            Modifier.onFocusChanged { if (it.isFocused) act.homeFocusedTile = true }.focusRing(pill),
                            fontSize = 15.sp,
                        )
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
                .onFocusChanged { if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null; act.homeRow = null } }
                .clickable { act.screen = Screen.Settings },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Settings, "Settings", tint = c.textDim) }
        Text(rememberClock(withDate = true), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        HintText(act.homeHint ?: "Ⓐ Play · Ⓧ Controls · Ⓨ Favorite · L1 Library")
        // The top screen is full screen, so Android's status icons live here instead.
        Box(Modifier.padding(start = 10.dp).width(2.dp).height(26.dp).background(c.barBorder))
        SystemStatusIcons(Modifier.padding(start = 10.dp))
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
enum class LibSort(val label: String) { RECENT("Last played"), AZ("A–Z"), MOST("Most played"), ADDED("Recently added") }

/** Sorts games: last played, A–Z, most played or recently added (newest file first). */
fun sortGames(list: List<GameEntry>, sort: LibSort, lastPlayed: Map<String, Long>, playTime: Map<String, Long>): List<GameEntry> = when (sort) {
    LibSort.RECENT -> list.sortedWith(compareByDescending<GameEntry> { lastPlayed[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
    LibSort.AZ -> list.sortedBy { it.info.title.lowercase() }
    LibSort.MOST -> list.sortedWith(compareByDescending<GameEntry> { playTime[it.key] ?: 0L }.thenBy { it.info.title.lowercase() })
    LibSort.ADDED -> list.sortedWith(compareByDescending<GameEntry> { it.lastModified }.thenBy { it.info.title.lowercase() })
}

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
    return sortGames(list, act.librarySort, lastPlayed, playTime)
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
        if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null; act.homeRow = null; act.homeHint = hint }
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
                if (open) KeyIcon("L1", size = 16.dp)
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
        if (open) HintText("L1 Hide · R1 Sort", Modifier.padding(start = 6.dp), fontSize = 11.sp)
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
            Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g))
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

    val target = p.editTarget
    if (p.editing && target != null) {
        Column(Modifier.fillMaxSize()) {
            Header(game.info.title, onBack = { p.editing = false })
            PresetEditor(
                base = target, customId = act.store.customIdFor(game.key), saveLabel = "Save",
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                controlForKey = remember { thorControlForKey(act.prefs) },
                updateName = if (act.store.isMine(target.id)) null else act.store.root(target).name,
                newName = act.store.newName(game.key, target),
                onReset = if (p.isEdited(target)) ({ p.resetToDefault(target); p.editing = false }) else null,
                onCancel = { p.editing = false },
                // One of My presets is simply updated; a built-in asks: this game's version, or a new preset.
                onSave = { edited, newName ->
                    if (act.store.isMine(target.id)) p.saveEdited(edited.copy(id = target.id, name = target.name), target, null, keepId = true)
                    else p.saveEdited(edited, target, newName)
                },
            )
        }
        return
    }

    // This game's own "Autosave & resume" choice (off by default for games that follow the real clock).
    val gameId = game.info.gameId
    val canAutosave = act.prefs.autoSaveResume && gameId != null
    var gameAutosave by remember(game.key) { mutableStateOf(act.prefs.autoSaveChoiceFor(gameId)) }
    fun toggleGameAutosave() {
        if (!canAutosave) return
        gameAutosave = !gameAutosave
        act.prefs.setAutoSaveFor(gameId!!, gameAutosave)
        act.statsVersion++
    }

    // Earlier autosaves (kept before each autosave overwrites Dolphin's Quick Save slot).
    var backupsVersion by remember { mutableIntStateOf(0) }
    var showBackups by remember { mutableStateOf(false) }
    val backups by produceState(emptyList<io.github.hiramrg.delphis.platform.DolphinBridge.AutosaveBackup>(), game.key, act.statsVersion, backupsVersion) {
        value = if (!act.prefs.autoSaveFor(game.info.gameId)) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.autosaveBackups(game.info.gameId) }
    }
    if (showBackups && backups.isNotEmpty()) {
        EarlierAutosavesDialog(backups, onDismiss = { showBackups = false }) { chosen ->
            showBackups = false
            val id = game.info.gameId ?: return@EarlierAutosavesDialog
            act.lifecycleScope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.restoreAutosave(id, chosen) }
                android.widget.Toast.makeText(
                    act.applicationContext,
                    if (ok) "Restored. Play continues from the save of ${formatSaveTime(chosen.savedAt)}." else "Couldn't restore that autosave.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                backupsVersion++
                act.statsVersion++
            }
        }
    }

    // Multiplayer: who plays this game (only offered when a controller besides the Thor is connected).
    val pads = io.github.hiramrg.delphis.platform.rememberPads(act.prefs)
    var playerSetup by remember(gameId) { mutableStateOf(gameId?.let { act.prefs.playerSetup(it) } ?: io.github.hiramrg.delphis.core.PlayerSetup()) }
    val multiplayer = gameId != null && io.github.hiramrg.delphis.core.Players.hasExternal(pads)
    // Gyro / tilt / trackpad presets only when the Thor plays alone.
    val severalPlayers = multiplayer && io.github.hiramrg.delphis.platform.Pads.multiplayer(io.github.hiramrg.delphis.core.Players.assign(pads, playerSetup))
    LaunchedEffect(severalPlayers) { p.updateMultiplayer(severalPlayers) }
    var showPlayers by remember { mutableStateOf(false) }
    if (showPlayers && multiplayer) {
        PlayersDialog(
            game.info.title, pads, playerSetup, onChange = { playerSetup = it; act.prefs.setPlayerSetup(gameId!!, it) }, onDismiss = { showPlayers = false },
            onChecked = { pad, byLabel -> act.prefs.setPadByLabel(pad.key, byLabel) },
        )
    }

    // RetroAchievements: 🏆 chip under the cover, list in a dialog (tap or R3).
    val achievements = rememberAchievements(act.prefs, game.key, game.info)
    var showAchievements by remember { mutableStateOf(false) }
    if (showAchievements && achievements != null) AchievementsDialog(achievements) { showAchievements = false }

    // GameCube: how the 4:3 picture fills the 16:9 screen (written to Dolphin when the game starts).
    val screenGid = game.info.gameId?.takeIf { game.info.platform == io.github.hiramrg.delphis.core.Platform.GAMECUBE }
    var screen by remember(game.key) { mutableStateOf(screenGid?.let { act.prefs.screenFor(it) }) }
    var showScreen by remember { mutableStateOf(false) }
    val coverColour by produceState(android.graphics.Color.rgb(75, 58, 143), screenGid) {
        screenGid?.let { value = io.github.hiramrg.delphis.platform.SideFill.colourOf(act.covers.load(it)) }
    }
    fun setScreen(c: io.github.hiramrg.delphis.core.GameScreen.Choice) { screen = c; screenGid?.let { act.prefs.setScreenFor(it, c) } }
    // What was set when the picker opened: Ⓑ / Cancel goes back to it.
    var screenBefore by remember { mutableStateOf(screen) }
    fun openScreen() { screenBefore = screen; showScreen = true }
    fun cancelScreen() { screenBefore?.let { setScreen(it) }; showScreen = false }

    // Physical buttons: A = use the preset under the ring, or Play if it's already in use; Start = Play;
    // D-pad = move the ring, X = Edit,
    // L1 = autosave on/off for this game, R1 = remember controls on/off, Y = earlier autosaves. B = Back.
    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        val sc = screen
        if (showScreen && sc != null) {
            // Screen picker open: L1 / R1 = 4:3 / 16:9, ◀ ▶ = card, Ⓐ Done, Ⓑ Cancel; nothing else reaches the page.
            if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> setScreen(sc.copy(shape = io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD))
                KeyEvent.KEYCODE_BUTTON_R1 -> setScreen(sc.copy(shape = io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE))
                KeyEvent.KEYCODE_DPAD_LEFT -> setScreen(screenStep(sc, -1))
                KeyEvent.KEYCODE_DPAD_RIGHT -> setScreen(screenStep(sc, 1))
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> if (e.repeatCount == 0) showScreen = false
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_SELECT -> if (e.repeatCount == 0) cancelScreen()
            }
            true
        } else if (p.editing || e.action != KeyEvent.ACTION_DOWN) false
        else when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (e.repeatCount == 0) toggleGameAutosave(); canAutosave }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (e.repeatCount == 0) p.rememberChoice = !p.rememberChoice; true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { if (e.repeatCount == 0) p.confirm(); true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (e.repeatCount == 0) p.play(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { p.move(1); true }
            KeyEvent.KEYCODE_DPAD_UP -> { p.move(-1); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.repeatCount == 0 && p.editTarget != null) p.editing = true; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.repeatCount == 0 && backups.isNotEmpty()) showBackups = true; true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (e.repeatCount == 0) { if (multiplayer) showPlayers = true else if (screen != null) openScreen() }
                multiplayer || screen != null
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { if (e.repeatCount == 0 && achievements != null) showAchievements = true; achievements != null }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null } }

    var showList by remember { mutableStateOf(!companion) }
    // The bottom screen can come and go: the list sits there when it's up, in the card otherwise.
    LaunchedEffect(companion) { showList = !companion }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf<Preset?>(null) }
    val shown = p.focused ?: p.selected

    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left: the game, then Edit and Play.
        Column(Modifier.fillMaxHeight().width(160.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("◀ Back", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Home }.padding(4.dp))
            // No accent frame: the only blue ring is the one that follows the D-pad focus.
            Box(Modifier.weight(1f, fill = false).aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))) {
                Cover(act, game, act.prefs.showCovers, Modifier.fillMaxSize(), titleSize = 14)
            }
            Text(game.info.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Text(platformLabel(game.info.platform) + " · " + playSummary(act, game), fontSize = 11.sp, color = c.textDim, maxLines = 2)
            if (rememberHasAutosave(act, game)) Text("▶ Resumes from autosave", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, maxLines = 1)
            else if (canAutosave && !gameAutosave && io.github.hiramrg.delphis.core.AutosaveRules.offByDefault(gameId)) Text(
                "🕒 Autosave is off so the game's clock matches the real time. Save in the game.",
                fontSize = 10.5.sp, color = c.textDim, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
            )
            Spacer(Modifier.height(2.dp))
            GameButton("✎", "Edit", "X", primary = false, enabled = p.editTarget != null) { p.editing = true }
            GameButton("▶", if (shown == null) "Choose a style" else "Play", "A", primary = true, enabled = shown != null) { p.play() }
        }
        // Right: one row of chips, then the controls card.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                achievements?.let { a -> GameChip("🏆 ${a.unlocked}/${a.total}", "R3") { showAchievements = true } }
                if (multiplayer) {
                    val newPad = pads.any { !it.builtIn && it.byLabel == null }
                    val n = io.github.hiramrg.delphis.core.Players.assign(pads, playerSetup).size
                    GameChip(if (newPad) "🎮 New controller" else "🎮 $n player" + (if (n == 1) "" else "s"), "⊖", warn = newPad) { showPlayers = true }
                } else screen?.let { sc -> GameChip("▭ " + screenChipLabel(sc, screenGid, act.prefs.gcUseHack), "⊖") { openScreen() } }
                Spacer(Modifier.weight(1f))
                if (canAutosave) GameChip((if (gameAutosave) "✓ " else "") + "Autosave", "L1", on = gameAutosave) { toggleGameAutosave() }
                GameChip((if (p.rememberChoice) "✓ " else "") + "Remember", "R1", on = p.rememberChoice) { p.rememberChoice = !p.rememberChoice }
            }
            WiiCard(Modifier.fillMaxWidth().weight(1f), radius = 20.dp, padding = 12.dp) {
                // Header: what the map shows. "✓ Selected controls" only when it's the preset in use.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(76.dp, 52.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.sidebar else c.bg), contentAlignment = Alignment.Center) {
                        shown?.let { PresetIcon(it, Modifier.size(60.dp, 44.dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        when {
                            shown == null -> Text("CONTROLS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, letterSpacing = 1.sp)
                            p.browsing -> HintText("Ⓐ USE THESE CONTROLS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, letterSpacing = 1.sp)
                            else -> SelectedControlsTitle(10.sp)
                        }
                        Text(
                            if (shown == null) androidx.compose.ui.text.AnnotatedString(p.suggestion.question)
                            else androidx.compose.ui.text.buildAnnotatedString {
                                append(withOrangeTags(shown.name, c.orange))
                                if (p.isEdited(shown)) withStyle(androidx.compose.ui.text.SpanStyle(color = EditedAmber)) { append("  ●") }
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        shown?.let { Text(it.controller.label, fontSize = 11.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    GameChip(if (showList) "Done" else "Change ›", null, on = showList, outline = true) { showList = !showList }
                    val canReset = shown != null && p.isEdited(shown)
                    val canDelete = shown != null && act.store.isMine(shown.id)
                    if (canReset || canDelete || backups.isNotEmpty()) Box {
                        GameChip("⋯", null) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (canReset) DropdownMenuItem(text = { Text("↺ Reset to default", color = Color(0xFFF0866E), fontWeight = FontWeight.Bold) }, onClick = { menu = false; confirmReset = shown })
                            if (canDelete) DropdownMenuItem(text = { Text("🗑 Delete this preset", color = Color(0xFFF0866E), fontWeight = FontWeight.Bold) }, onClick = { menu = false; p.deleteMine(shown!!) })
                            if (backups.isNotEmpty()) DropdownMenuItem(text = { HintText("↺ Earlier autosaves (${backups.size}) · Ⓨ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text) }, onClick = { menu = false; showBackups = true })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (showList) {
                    PresetChooser(p.groups, p.selected, p.focused, game.info.title, { p.isEdited(it) }, Modifier.weight(1f).fillMaxWidth()) { p.choose(it) }
                } else if (shown != null) {
                    ControlsMap(shown, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (companion) "Pick a control style on the bottom screen (or with the D-pad)." else "Press Change › to pick a control style.", color = c.textDim, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    confirmReset?.let { r -> ResetConfirm(r.name, wide = true, onKeep = { confirmReset = null }) { confirmReset = null; p.resetToDefault(r) } }
    val sc = screen
    if (showScreen && sc != null) ScreenPanel(game.info.title, sc, screenGid, act.prefs.gcUseHack, coverColour,
        onPick = { setScreen(it) }, onCancel = { cancelScreen() }, onDone = { showScreen = false })
    }
}

/** The screen chip's text: "16:9", "16:9 · Stretch", "4:3 · Pattern"… */
private fun screenChipLabel(sc: io.github.hiramrg.delphis.core.GameScreen.Choice, gameId: String?, useHack: Boolean): String {
    return when (io.github.hiramrg.delphis.core.GameScreen.method(sc, gameId, useHack)) {
        io.github.hiramrg.delphis.core.GameScreen.Method.STANDARD -> "4:3 · " + sc.sides.label
        io.github.hiramrg.delphis.core.GameScreen.Method.STRETCH -> "16:9 · Stretch"
        else -> "16:9"
    }
}

/** A chip in the game page's top row: text, then the button that toggles it in small grey type ("L1"). */
@Composable
private fun GameChip(text: String, key: String?, on: Boolean = false, warn: Boolean = false, outline: Boolean = false, height: androidx.compose.ui.unit.Dp = 32.dp, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(height).clip(shape)
            .background(if (on) c.accentSoft else c.card)
            .border(2.dp, when { warn -> c.orange; on || outline -> c.accent; else -> c.cardBorder }, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = if (warn) c.orange else c.text, maxLines = 1)
        if (key != null) KeyIcon(key)
    }
}

/**
 * Edit / Play under the cover: both 40 dp tall and as wide as the column; icon, word and the button's
 * ring badge are centred together.
 */
@Composable
private fun GameButton(icon: String, label: String, key: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    val fg = if (primary) Color(0xFF0A2533) else c.text
    Row(
        Modifier.fillMaxWidth().height(40.dp).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .background(if (primary) c.accent else c.card)
            .border(2.dp, if (primary) c.accent else c.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        KeyIcon(key, if (primary) fg.copy(alpha = 0.75f) else c.textDim)
    }
}

/** Gyro presets (marked "(experimental)") are grouped in their own folder in every preset list. */
fun Preset.isGyro(): Boolean =
    mappings.any { it.target is io.github.hiramrg.delphis.core.Target.GyroPointer } || name.contains("(experimental)", ignoreCase = true)

/**
 * How a game's presets are shown: sections that don't fold, in this order: Suggested, My presets,
 * Gyro aiming, Trackpad aiming, Other layouts.
 */
class PresetGroups(choices: List<Preset>, alternatives: List<Preset>, mine: List<Preset> = emptyList()) {
    private fun Preset.kind() = when { isTrackpad -> 2; isGyro() -> 1; else -> 0 }
    private val all = (choices + alternatives).distinctBy { it.id }
    // A game whose suggestions are all gyro/trackpad presets (Red Steel) keeps them under Suggested.
    private val folderChoices = choices.any { it.kind() == 0 }
    val main = if (folderChoices) choices.filter { it.kind() == 0 } else choices
    val mine = mine.filter { m -> main.none { it.id == m.id } }
    val gyro = all.filter { it.kind() == 1 && it !in main }
    val trackpad = all.filter { it.kind() == 2 && it !in main }
    val others = alternatives.filter { it.kind() == 0 && it !in main }
    /** The sections in order: key, title (null = "Suggested for <game>"), presets. */
    val sections get() = listOf(
        Triple("suggested", null, main),
        Triple("mine", "My presets", mine),
        Triple("gyro", "Gyro aiming", gyro),
        Triple("trackpad", "Trackpad aiming", trackpad),
        Triple("others", "Other layouts", others),
    ).filter { it.third.isNotEmpty() }
    /** Top-to-bottom order, used for D-pad navigation. */
    val ordered get() = sections.flatMap { it.third }
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

/** A section heading in a preset list ("MY PRESETS"). */
@Composable
private fun PresetSection(title: String) {
    val c = LocalWii.current
    Text(title.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, letterSpacing = 1.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
}

/**
 * One preset in a list. Two separate marks: the blue ring is where the D-pad is ([ring]); the tint and
 * green "✓ Selected" mark the preset in use ([inUse]). An amber dot means it's this game's edited version.
 */
@Composable
fun PresetRow(preset: Preset, inUse: Boolean, ring: Boolean, edited: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape)
            .background(if (inUse) c.accentSoft else Color.Transparent)
            .then(if (ring) Modifier.border(3.dp, c.accent, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetIcon(preset, Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).background(if (c.dark) c.sidebar else c.bg).padding(2.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(withOrangeTags(preset.name, c.orange))
                if (edited) withStyle(androidx.compose.ui.text.SpanStyle(color = EditedAmber)) { append("  ●") }
            },
            fontSize = 14.sp, fontWeight = if (inUse) FontWeight.ExtraBold else FontWeight.Bold, lineHeight = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (inUse) Text("✓ Selected", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.green, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The amber of the "edited" dot. */
val EditedAmber = Color(0xFFF0B25A)

/**
 * A game's presets in sections that don't fold (see [PresetGroups]); the list scrolls. Used on the
 * game page (single screen), the bottom-screen picker and "Change controls" in game.
 */
@Composable
fun PresetChooser(
    groups: PresetGroups,
    selected: Preset?,
    focused: Preset?,
    gameTitle: String,
    isEdited: (Preset) -> Boolean,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onSelect: (Preset) -> Unit,
) {
    // Keep the ring visible (it moves with the D-pad).
    LaunchedEffect(focused?.id) {
        val id = focused?.id ?: return@LaunchedEffect
        var index = 0
        for ((_, _, list) in groups.sections) {
            val at = list.indexOfFirst { it.id == id }
            if (at >= 0) { runCatching { listState.animateScrollToItem((index + at).coerceAtLeast(0)) }; return@LaunchedEffect }
            index += 1 + list.size
        }
    }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((key, title, list) in groups.sections) {
            item(key = "$key-title") { PresetSection(title ?: "Suggested for $gameTitle") }
            items(list, key = { "$key-" + it.id }) { p ->
                PresetRow(p, inUse = p.id == selected?.id, ring = p.id == focused?.id, edited = isEdited(p)) { onSelect(p) }
            }
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
    DISPLAY("Display"), BOTTOM("Bottom screen"), ACHIEVEMENTS("Achievements"), LIBRARY("Library & folders"), FRONTENDS("Frontends"), UPDATES("Updates"), HELP("Diagnostics"),
}

@Composable
fun SettingsScreen(act: MainActivity) {
    val c = LocalWii.current
    Row(Modifier.fillMaxSize()) {
        Column(
            // Same rounded card as Home's Library column, so the app looks consistent.
            Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp).width(190.dp).fillMaxHeight()
                .clip(RoundedCornerShape(20.dp)).background(c.sidebar).border(2.dp, c.cardBorder, RoundedCornerShape(20.dp))
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
                SettingsSection.DISPLAY -> DisplaySettings(act)
                SettingsSection.BOTTOM -> BottomScreenSettings(act)
                SettingsSection.ACHIEVEMENTS -> AchievementsSettings(act) { t, sub, on, change -> SwitchRow(t, sub, on, change) }
                SettingsSection.LIBRARY -> LibrarySettings(act)
                SettingsSection.FRONTENDS -> FrontendSettings(act)
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
    var backupsVersion by remember { mutableIntStateOf(0) }
    val backupsSize by produceState(0L, backupsVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.backupsSize() }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Earlier autosaves", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Before each autosave, the one before it is kept (up to ${io.github.hiramrg.delphis.core.AutosaveBackups.KEEP} per game), so a broken save never loses much. Restore one with R1 on the game's page." +
                    if (backupsSize > 0) " Using ${formatSize(backupsSize)}." else "",
                fontSize = 12.sp, color = LocalWii.current.textDim,
            )
        }
        if (backupsSize > 0) {
            Spacer(Modifier.width(10.dp))
            var confirm by remember { mutableStateOf(false) }
            LaunchedEffect(confirm) { if (confirm) { delay(4000); confirm = false } }
            WiiButton(if (confirm) "Press again to delete" else "Delete all", primary = confirm, onClick = {
                if (!confirm) { confirm = true; return@WiiButton }
                confirm = false
                act.lifecycleScope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.deleteAllBackups() }
                    backupsVersion++
                    act.statsVersion++
                }
            })
        }
    }
    SwitchRow(
        "Warn before \"Back to main menu\"",
        if (autoSave) "Shows a reminder before the game closes." else "Shows a reminder that unsaved progress will be lost.",
        !skipWarn,
    ) { skipWarn = !it; act.prefs.skipExitWarning = !it }
}

/** Settings → Display: how GameCube games' 4:3 picture fills the 16:9 screen, unless a game picks its own. */
@Composable
private fun DisplaySettings(act: MainActivity) {
    val prefs = act.prefs
    val c = LocalWii.current
    var wide by remember { mutableStateOf(prefs.gcDefaultWide) }
    var fill by remember { mutableStateOf(prefs.gcFill) }
    var sides by remember { mutableStateOf(prefs.gcSides) }
    var hack by remember { mutableStateOf(prefs.gcUseHack) }
    Text("GameCube games", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Text("Defaults for new games; each game can change them under its cover (Screen).", fontSize = 12.sp, color = c.textDim)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Start games in", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        ShapeSwitch(if (wide) io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE else io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD,
            { wide = it == io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE; prefs.gcDefaultWide = wide })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("16:9 fill", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        WiiChip("More of the world", selected = fill == io.github.hiramrg.delphis.core.GameScreen.Fill.WORLD, onClick = { fill = io.github.hiramrg.delphis.core.GameScreen.Fill.WORLD; prefs.gcFill = fill })
        WiiChip("Stretch", selected = fill == io.github.hiramrg.delphis.core.GameScreen.Fill.STRETCH, onClick = { fill = io.github.hiramrg.delphis.core.GameScreen.Fill.STRETCH; prefs.gcFill = fill })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("4:3 sides", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        io.github.hiramrg.delphis.core.GameScreen.Sides.entries.forEach { sd ->
            WiiChip(sd.label, selected = sides == sd, onClick = { sides = sd; prefs.gcSides = sd })
        }
    }
    SwitchRow("Use the widescreen hack when there's no game patch", "More of the world for any 3D game; some menus or edges may glitch. Off = stretch instead.", hack) { hack = it; prefs.gcUseHack = it }
    Text("Delphis turns on Dolphin's own widescreen code when it ships one for the game (cheats on for that game only). Dolphin applies all of this when a game starts.",
        fontSize = 11.sp, color = c.textDim)
}

@Composable
private fun BottomScreenSettings(act: MainActivity) {
    val prefs = act.prefs
    val c = LocalWii.current
    var screenMode by remember { mutableStateOf(prefs.bottomScreenMode) }
    var delaySec by remember { mutableIntStateOf(prefs.dimDelaySeconds) }
    var brightness by remember { mutableFloatStateOf(prefs.brightnessPercent.toFloat()) }
    var swap by remember { mutableStateOf(prefs.swapScreens) }
    val pick: (BottomScreen.Mode) -> Unit = { screenMode = it; prefs.bottomScreenMode = it }
    Text("While you play", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenModeCard("Always on", "Never dims", BottomScreen.Mode.ON, screenMode, pick, Modifier.weight(1f))
        ScreenModeCard("Dim", "Lowers brightness", BottomScreen.Mode.DIM, screenMode, pick, Modifier.weight(1f))
        ScreenModeCard("Screen off", "Goes black", BottomScreen.Mode.OFF, screenMode, pick, Modifier.weight(1f))
    }
    if (screenMode != BottomScreen.Mode.ON) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (screenMode == BottomScreen.Mode.OFF) "Off after" else "Dim after", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
            BottomScreen.DELAYS.forEach { sec ->
                WiiChip(BottomScreen.delayLabel(sec), selected = delaySec == sec, onClick = { delaySec = sec; prefs.dimDelaySeconds = sec })
            }
        }
    }
    if (screenMode == BottomScreen.Mode.DIM) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dimmed level", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(104.dp))
            Slider(
                value = brightness, onValueChange = { brightness = it }, onValueChangeFinished = { prefs.brightnessPercent = brightness.toInt() },
                valueRange = 0f..100f, modifier = Modifier.weight(1f),
            )
            Text("${brightness.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
        }
        Text("Normal brightness is set with the ☀ button on the in-game bottom screen.", fontSize = 11.sp, color = c.textDim)
    }
    Text(
        "Touching the screen lights it up again for the full time; the first touch only wakes it. In game, tap the brightness pill " +
            "to turn Auto-dim off just for that game, or to turn the bottom screen off.",
        fontSize = 12.sp, color = c.textDim,
    )
    SwitchRow("Swap top and bottom screens", "Use if games open on the small screen.", swap) { swap = it; prefs.swapScreens = it }
}

/** One of the three "While you play" choices, with a tiny picture of the in-game screen in that state. */
@Composable
private fun ScreenModeCard(
    title: String, sub: String, value: BottomScreen.Mode, current: BottomScreen.Mode,
    onPick: (BottomScreen.Mode) -> Unit, modifier: Modifier = Modifier,
) {
    val c = LocalWii.current
    val selected = value == current
    WiiCard(modifier, selected = selected, radius = 16.dp, padding = 6.dp, onClick = { onPick(value) }) {
        val bg = if (value == BottomScreen.Mode.OFF) Color.Black else DarkWii.bg
        val a = when (value) { BottomScreen.Mode.ON -> 1f; BottomScreen.Mode.DIM -> 0.35f; BottomScreen.Mode.OFF -> 0f }
        Column(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                .border(2.dp, c.cardBorder, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp).alpha(a),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(3.dp)).background(DarkWii.text))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(DarkWii.card).border(1.dp, DarkWii.cardBorder, RoundedCornerShape(4.dp)))
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE5484D)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            RadioButton(selected = selected, onClick = { onPick(value) })
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, fontSize = 10.sp, color = c.textDim, maxLines = 1)
            }
        }
    }
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

/** The launch command for frontends such as RetroHrai! (see LaunchActivity). */
const val FRONTEND_COMMAND = "-n io.github.hiramrg.delphis/.Play -a android.intent.action.VIEW -d {file.uri}"

@Composable
private fun FrontendSettings(act: MainActivity) {
    val c = LocalWii.current
    var copied by remember { mutableStateOf(false) }
    Text(
        "Start Wii and GameCube games from a frontend that lets you add a custom launch command, like RetroHrai!, and still get " +
            "Delphis' controls and the bottom-screen map. In the frontend, add a custom emulator for Wii and GameCube and paste this launch command:",
        fontSize = 13.sp, color = c.textDim,
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.sidebar)
            .border(2.dp, c.cardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(FRONTEND_COMMAND, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
    WiiButton(if (copied) "Copied ✓" else "Copy command", onClick = {
        val cm = act.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Delphis launch command", FRONTEND_COMMAND))
        copied = true
    })
    Text(
        "If your frontend uses a different word for the game file, swap {file.uri} for it (for example {file.path}). " +
            "The game uses the controls you chose for it in Delphis (or the suggested ones), and Home on the bottom screen takes you back to the frontend.",
        fontSize = 12.sp, color = c.textDim,
    )
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

/** Which Thor control a key press came from (for "press the button" in the editor), using the Controller Check's mapping. */
fun thorControlForKey(prefs: io.github.hiramrg.delphis.platform.Prefs): (KeyEvent) -> io.github.hiramrg.delphis.core.ThorControl? {
    val profile = io.github.hiramrg.delphis.platform.DeviceInfo.controllerProfile(prefs)
    val buttons = io.github.hiramrg.delphis.core.ThorControl.entries.filter { it.kind == io.github.hiramrg.delphis.core.ThorControl.Kind.BUTTON }
    return { e ->
        val name = io.github.hiramrg.delphis.platform.DeviceInfo.dolphinNameForKeycode(e.keyCode)
        when {
            name == null -> null
            name in listOf("Up", "Down", "Left", "Right") -> io.github.hiramrg.delphis.core.ThorControl.DPAD
            else -> buttons.firstOrNull { c -> profile.inputsFor(c).any { it == name || it.endsWith(":$name") } }
        }
    }
}
