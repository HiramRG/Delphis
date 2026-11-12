package io.github.hiramrg.delphis.ui

import io.github.hiramrg.delphis.core.BottomScreen
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.draw.drawBehind
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
    val rowsMode = act.librarySection == LibSection.ALL && act.libraryQuery.isBlank() && !act.searchOpen && act.games.isNotEmpty()
    var showFavPicker by remember { mutableStateOf(false) }
    val heroAchievements = hero?.let { rememberAchievements(act.prefs, it.key, it.info) }
    // R3 opens the achievements of the game under the ring (or the continue game).
    var achGame by remember { mutableStateOf<GameEntry?>(null) }
    achGame?.let { g ->
        val prog = rememberAchievements(act.prefs, g.key, g.info)
        if (prog != null) AchievementsDialog(prog) { achGame = null }
    }

    var holdingB by remember { mutableStateOf(false) }
    LaunchedEffect(holdingB) { if (holdingB) { delay(1000); if (holdingB) { holdingB = false; act.exitAll() } } }
    if (act.exitPrompt) ConfirmDialog(
        "Delphis", "Exit Delphis?", "Closes Delphis on both screens and closes Dolphin too, so nothing keeps running in the background.",
        confirm = "⏻ Exit", onCancel = { act.exitPrompt = false },
    ) { act.exitAll() }
    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        val target = act.focusedGame ?: hero
        if (act.searchEditing) return@rememberUpdatedState false
        // "Add favorites" picker: Ⓐ stars/unstars the game under the ring (its click), Ⓨ / Ⓑ finish.
        if (showFavPicker) return@rememberUpdatedState when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { if (e.firstPress()) showFavPicker = false; true }
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_THUMBR -> true
            else -> false
        }
        val onRow = rowsMode && act.homeRow != null && act.focusedGame != null
        // No games yet: Ⓐ chooses the games folder, Ⓨ scans again.
        if (act.games.isEmpty() && !act.scanning) when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> { if (e.firstPress()) act.chooseGamesFolder(); return@rememberUpdatedState true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) act.rescan(); return@rememberUpdatedState true }
        }
        // Search with no matches: Ⓑ clears it.
        if (e.keyCode == KeyEvent.KEYCODE_BUTTON_B && act.searchOpen && act.libraryQuery.isNotBlank() && shown.isEmpty()) {
            if (e.firstPress()) act.libraryQuery = ""; return@rememberUpdatedState true
        }
        when (e.keyCode) {
            // On a row, Ⓐ plays the game under the ring (Ⓧ opens its game page).
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START ->
                if (onRow) {
                    if (e.firstPress()) act.focusedGame?.let { g ->
                        // In the empty Favorites row the games are suggestions: Ⓐ adds one.
                        if (act.homeRow == HomeRow.FAVORITES && g.key !in favorites) { act.prefs.toggleFavorite(g.key); act.statsVersion++ } else act.quickPlay(g)
                    }
                    true
                }
                else if (act.homeFocusedTile || hero == null) false else { if (e.firstPress()) act.quickPlay(hero); true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (e.firstPress()) target?.let { act.openGame(it) }; true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.firstPress()) target?.let { act.prefs.toggleFavorite(it.key); act.statsVersion++ }; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (e.firstPress()) act.libraryOpen = !act.libraryOpen; true }
            // Ⓑ in search goes back to the rows; elsewhere, holding Ⓑ for a second exits Delphis.
            KeyEvent.KEYCODE_BUTTON_B -> when {
                act.searchOpen && act.libraryQuery.isBlank() -> { if (e.firstPress()) act.searchOpen = false; true }
                act.searchOpen -> false
                else -> {
                    if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) holdingB = true
                    else if (e.action == KeyEvent.ACTION_UP) holdingB = false
                    true
                }
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (e.firstPress()) {
                    val row = act.homeRow
                    if (rowsMode && row != null && row.sortable) {
                        val cur = rowSort(act, row)
                        act.prefs.setRowSort(row.name, LibSort.entries[(cur.ordinal + 1) % LibSort.entries.size].name); act.statsVersion++
                        act.sortPopupUntil = System.currentTimeMillis() + 2000
                    } else act.librarySort = LibSort.entries[(act.librarySort.ordinal + 1) % LibSort.entries.size]
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                val g = act.focusedGame ?: hero
                if (e.firstPress() && g != null) achGame = g
                g != null
            }
            // ⊖ opens Settings from anywhere on Home.
            KeyEvent.KEYCODE_BUTTON_SELECT -> { if (e.firstPress()) act.screen = Screen.Settings; true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null; act.homeFocusedTile = false; act.homeHint = null; act.searchEditing = false; act.homeRow = null } }

    // R1 on a row: the four sorts pop up above the bar's sort pill for a moment.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(act.sortPopupUntil) { now = System.currentTimeMillis(); if (act.sortPopupUntil > now) { delay(act.sortPopupUntil - now); now = System.currentTimeMillis() } }
    val sortPopup = act.sortPopupUntil > now
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        UpdateBanner(act)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            HomeRail(act, favorites, rowsMode, hero != null)
            if (rowsMode) HomeRows(act, hero, recent, favorites, lastPlayed, playTime, { showFavPicker = true })
            else LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp), state = grid,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    var ring by remember { mutableStateOf(false) }
                    Box(
                        Modifier.fillMaxWidth().height(HERO_H)
                            .onFocusChanged {
                                heroFocused = it.hasFocus
                                ring = it.isFocused
                                if (it.hasFocus) { act.homeFocusedTile = false; act.focusedGame = null }
                            }
                            .then(if (hero != null) Modifier.focusable().clickable { act.quickPlay(hero) } else Modifier),
                    ) { if (hero != null) GameInfoCard(act, hero, focused = ring, continueMode = true) else WelcomeCard(act) }
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
                        // Says why there's nothing to show, with a way forward.
                        val folder = act.prefs.gamesTreeUri?.lastPathSegment?.substringAfterLast(':')?.ifBlank { null }
                        when {
                            act.scanning -> EmptyState("⌕", "Looking for games…", "Delphis is scanning your games folder. ${act.scanCount} found so far.", Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            act.games.isEmpty() -> EmptyState(
                                "▤", "No games found yet",
                                (if (folder != null) "Delphis looked in \u201C$folder\u201D but found nothing it can play." else "Choose the folder with your GameCube and Wii games.") + " Choose another folder or scan again.",
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                PanelBtn("Choose folder", "A", true, onClick = act::chooseGamesFolder)
                                PanelBtn("Scan again", "Y", false, onClick = act::rescan)
                            }
                            act.librarySection == LibSection.FAVORITES && act.libraryQuery.isBlank() -> EmptyState(
                                "★", "No favorites yet", "Press Ⓨ on any game to add it here.", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                            act.libraryQuery.isNotBlank() -> EmptyState(
                                "⌕", "No games match \u201C${act.libraryQuery}\u201D", "Check the spelling, or search by part of the name.", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) { PanelBtn("Clear", "B", false) { act.libraryQuery = "" } }
                            else -> EmptyState("▤", "Nothing here", "There are no games in this list yet.", Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                } else {
                    items(shown, key = { it.uri.toString() }) { g -> GameCard(act, g, g.key in favorites, playTime[g.key] ?: 0L) }
                }
            }
        }
        HomeBar(act, rowsMode, sortPopup)
    }
    val row = act.homeRow
    if (rowsMode && row != null && row.sortable && sortPopup) SortPopup(rowSort(act, row), Modifier.align(Alignment.BottomEnd).padding(end = 150.dp, bottom = 62.dp))
    if (showFavPicker) FavoritesPicker(act, favorites) { showFavPicker = false }
    if (holdingB) HoldToExit(Modifier.align(Alignment.Center))
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
    CONTINUE("Continue playing", false), RECENT("Recently played", false), WII("Wii", true), GAMECUBE("GameCube", true), FAVORITES("Favorites", true), ALL("All games", true)
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
    val fg = if (gc) Color.White else Color(0xFF1A6FB5)
    Row(
        Modifier.height(if (small) 19.dp else 22.dp).clip(RoundedCornerShape(50)).background(if (gc) CubePurple else WiiWhite).padding(horizontal = if (small) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Same line icons as the rail: the remote for Wii, the cube for GameCube.
        RailIcon(if (gc) "gc" else "wii", fg, Modifier.size(if (small) 12.dp else 14.dp))
        Text(if (gc) "GameCube" else "Wii", fontSize = if (small) 10.5.sp else 12.sp, fontWeight = FontWeight.Black, color = fg, maxLines = 1)
    }
}

@Composable
private fun HomeRows(
    act: MainActivity, hero: GameEntry?, recent: List<GameEntry>, favorites: Set<String>,
    lastPlayed: Map<String, Long>, playTime: Map<String, Long>,
    onAddFavorites: () -> Unit,
) {
    val state = rememberLazyListState()
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val rows = homeRows(act, recent, favorites, lastPlayed, playTime)
    // The row the ring is on sits right under the pinned card, fully visible; the row above only
    // peeks out (the list's top padding) and the next one is cut off by the bottom of the screen.
    LaunchedEffect(act.homeRow, rows.size) {
        val idx = rows.indexOfFirst { it.first == act.homeRow }
        delay(60)
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.first { !it }
        runCatching { if (idx < 0) state.animateScrollToItem(0) else state.animateScrollToItem(idx) }
    }
    // The rail asks to jump to a row: scroll there, then put the ring on its first game.
    LaunchedEffect(act.railJump) {
        val target = act.railTarget ?: return@LaunchedEffect
        if (target == HomeRow.CONTINUE) { runCatching { state.animateScrollToItem(0) }; delay(30); runCatching { act.rowFocus[target]?.requestFocus() }; return@LaunchedEffect }
        val idx = rows.indexOfFirst { it.first == target }
        if (idx < 0) return@LaunchedEffect
        runCatching { state.animateScrollToItem(idx) }
        delay(30)
        runCatching { act.rowFocus[target]?.requestFocus() }
    }
    Column(Modifier.fillMaxHeight().fillMaxWidth()) {
        // Pinned: it never scrolls away. It's the Continue row (the ring goes on it like on any game),
        // or a preview of the game under the ring on the rows below.
        val heroFocus = act.rowFocus.getOrPut(HomeRow.CONTINUE) { androidx.compose.ui.focus.FocusRequester() }
        // Home opens with the ring on Continue.
        LaunchedEffect(hero?.key) { delay(80); if (hero != null && act.homeRow == null) runCatching { heroFocus.requestFocus() } }
        Box(Modifier.padding(start = 12.dp, end = 14.dp, top = 10.dp).fillMaxWidth().height(HERO_H)) {
            if (hero != null) Box(
                Modifier.fillMaxSize().focusRequester(heroFocus)
                    .onFocusChanged { if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = hero; act.homeRow = HomeRow.CONTINUE } }
                    .focusable().clickable { act.quickPlay(hero) },
            ) { GameInfoCard(act, hero, focused = act.homeRow == HomeRow.CONTINUE, continueMode = true) }
            else Box(Modifier.onFocusChanged { if (it.hasFocus) { act.homeRow = null; act.focusedGame = null; act.homeFocusedTile = false } }) { WelcomeCard(act) }
            val g = act.focusedGame?.takeIf { act.homeRow != null && act.homeRow != HomeRow.CONTINUE }
            when {
                g != null -> GameInfoCard(act, g, focused = false, continueMode = false)
                act.homeRow == HomeRow.FAVORITES && favorites.isEmpty() -> FavoritesEmptyCard()
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = state,
            contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { (row, games) ->
                item(key = "row-" + row.name) {
                    if (row == HomeRow.FAVORITES && games.isEmpty()) FavoritesEmptyRow(act, suggestions(act, favorites, playTime), onAddFavorites)
                    else GameRowView(act, row, games, favorites, onAddFavorites)
                }
            }
        }
    }
}

/** Home's rows, top to bottom (the rail lists them in the same order). */
private fun homeRows(act: MainActivity, recent: List<GameEntry>, favorites: Set<String>, lastPlayed: Map<String, Long>, playTime: Map<String, Long>) = buildList {
    recent.drop(1).take(12).takeIf { it.isNotEmpty() }?.let { add(HomeRow.RECENT to it) }
    act.games.filter { it.info.platform == Platform.WII }.takeIf { it.isNotEmpty() }?.let { add(HomeRow.WII to sortGames(it, rowSort(act, HomeRow.WII), lastPlayed, playTime)) }
    act.games.filter { it.info.platform == Platform.GAMECUBE }.takeIf { it.isNotEmpty() }?.let { add(HomeRow.GAMECUBE to sortGames(it, rowSort(act, HomeRow.GAMECUBE), lastPlayed, playTime)) }
    add(HomeRow.ALL to sortGames(act.games, rowSort(act, HomeRow.ALL), lastPlayed, playTime))
    add(HomeRow.FAVORITES to sortGames(act.games.filter { it.key in favorites }, rowSort(act, HomeRow.FAVORITES), lastPlayed, playTime))
}

/** Up to four of the most played games that aren't favorites yet. */
private fun suggestions(act: MainActivity, favorites: Set<String>, playTime: Map<String, Long>) =
    act.games.filter { it.key !in favorites && (playTime[it.key] ?: 0L) > 60_000 }.sortedByDescending { playTime[it.key] ?: 0L }.take(4)

/** A small chip for the pinned card ("12 min played", "🏆 3/60"). */
@Composable
private fun InfoChip(t: androidx.compose.ui.text.AnnotatedString) {
    val c = LocalWii.current
    Box(Modifier.height(22.dp).clip(RoundedCornerShape(50)).background(c.card).border(1.5.dp, c.cardBorder, RoundedCornerShape(50)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
private fun chipText(s: String) = androidx.compose.ui.text.AnnotatedString(s)

/** Height of Home's top card (Continue playing / preview of the game under the ring). */
val HERO_H = 186.dp

/**
 * Home's top card: box art · about the game · "Ready to play". One grid: 12 dp padding, 14 dp between the
 * three blocks, all sharing the same top and bottom edge. The title gets two lines; a name too long for two
 * lines shows on one line that scrolls, so nothing else in the card ever moves.
 */
@Composable
private fun GameInfoCard(act: MainActivity, g: GameEntry, focused: Boolean, continueMode: Boolean) {
    val c = LocalWii.current
    @Suppress("UNUSED_VARIABLE") val v = act.statsVersion
    val shape = RoundedCornerShape(18.dp)
    val ach = rememberAchievements(act.prefs, g.key, g.info)
    val autosave = rememberHasAutosave(act, g)
    val gc = g.info.platform == Platform.GAMECUBE
    val preset = act.prefs.presetFor(g.key)?.let { act.store.forGame(g.key, it) }
        ?: act.store.withEdits(g.key, io.github.hiramrg.delphis.core.GameDatabase.suggest(g.info).choices).firstOrNull()
    val screenGid = g.info.gameId?.takeIf { gc }
    val favorites = act.prefs.favorites()
    val last = act.prefs.lastPlayed()[g.key]
    val playMs = act.prefs.playTime()[g.key] ?: 0L
    val days = last?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
    val whenText = when (days) { null -> null; 0 -> "today"; 1 -> "yesterday"; else -> "$days days ago" }
    val label = when {
        continueMode -> "Continue playing" + (whenText?.let { " · $it" } ?: "")
        act.homeRow == HomeRow.FAVORITES && g.key !in favorites -> "Suggestion · add to favorites"
        whenText != null -> "Last played $whenText"
        System.currentTimeMillis() - g.lastModified < 86_400_000L -> "New · added today"
        else -> "Not played yet"
    }
    Row(
        Modifier.fillMaxSize()
            .then(if (focused) Modifier.shadow(14.dp, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape).background(c.card).border(if (focused) 3.dp else 2.dp, if (focused) c.accent else c.cardBorder, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.72f)) {
            Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g, 3.dp), titleSize = 12)
            if (g.key in favorites) Text("★", color = Color(0xFFFFD34D), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        // About the game.
        Column(Modifier.weight(1.25f).fillMaxHeight()) {
            Text(label.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            CardTitle(g.info.title)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformTag(g.info.platform, small = true)
                Text("🕒 " + if (playMs > 60_000) formatPlayTime(playMs) + " played" else "Not played yet", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
            }
            Spacer(Modifier.height(7.dp))
            if (ach != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("🏆 ${ach.unlocked} / ${ach.total}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(c.cardBorder)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((ach.percent / 100f).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(50)).background(Color(0xFFF5C542)))
                }
                Text("${ach.percent}%", fontSize = 11.sp, color = c.textDim, maxLines = 1)
                KeyIcon("R3", size = 16.dp)
            } else Text("🏆 No achievements for this game", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(c.cardBorder.copy(alpha = 0.6f)))
            val suggestion = act.homeRow == HomeRow.FAVORITES && g.key !in favorites
            HintText(if (suggestion) "Ⓐ Add to favorites  Ⓧ Game page" else "Ⓐ Play  Ⓧ Game page", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (focused) c.text else c.textDim)
        }
        // Ready to play: what loads when you press Play.
        val pshape = RoundedCornerShape(14.dp)
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(pshape).background(if (c.dark) Color(0xFF1D2127) else c.bg).border(1.5.dp, c.cardBorder.copy(alpha = 0.7f), pshape).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("✓ READY TO PLAY", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.green, maxLines = 1)
            ReadyRow(Icons.Outlined.SportsEsports, "Controls", preset?.name ?: "Choose on the game page", edited = preset != null && act.store.isEdited(preset.id))
            ReadyRow(Icons.Outlined.Restore, "Starts from", if (autosave) "Your autosave" else "The start")
            ReadyRow(Icons.Outlined.Crop169, "Screen", screenGid?.let { screenChipLabel(act.prefs.screenFor(it), it, act.prefs.gcUseHack) } ?: "16:9")
        }
    }
}

/** One line of "Ready to play": icon tile, caps label, value (wraps to two lines, never cut). */
@Composable
private fun ReadyRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, edited: Boolean = false) {
    val c = LocalWii.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(c.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accentText, modifier = Modifier.size(17.dp))
        }
        Column {
            Text(label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.textDim, maxLines = 1)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(withOrangeTags(value, c.orange))
                    if (edited) withStyle(androidx.compose.ui.text.SpanStyle(color = EditedAmber)) { append(" ●") }
                },
                fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 15.sp,
            )
        }
    }
}

/** The card's title: two lines when it fits, otherwise one line that scrolls (same height either way). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CardTitle(title: String) {
    val style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp, color = LocalWii.current.text)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().height(46.dp)) {
        val maxW = constraints.maxWidth
        val fits = remember(title, maxW) {
            !measurer.measure(title, style, maxLines = 2, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxW)).hasVisualOverflow
        }
        if (fits) Text(title, style = style, maxLines = 2)
        else Text(
            title, style = style, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1500, velocity = 40.dp),
        )
    }
}

/** The pinned card on the "+" of an empty Favorites row. */
@Composable
private fun FavoritesEmptyCard() {
    val c = LocalWii.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxSize().clip(shape).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF3A3014), c.card))).border(2.dp, Color(0xFFFFD34D), shape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(50)).background(c.card), contentAlignment = Alignment.Center) { Text("☆", fontSize = 30.sp, color = Color(0xFFFFD34D)) }
        Column(Modifier.weight(1f)) {
            Text("FAVORITES", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color(0xFFE8D38A))
            Text("No favorites yet", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            HintText("Ⓐ on “+” to pick some, or Ⓐ on a suggestion to add it. Ⓨ on any game adds it too.", fontSize = 11.5.sp)
        }
    }
}

/** The empty Favorites row: a "+" tile, then suggestions from the most played games. */
@Composable
private fun FavoritesEmptyRow(act: MainActivity, suggestions: List<GameEntry>, onAdd: () -> Unit) {
    val c = LocalWii.current
    val focused = act.homeRow == HomeRow.FAVORITES
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (act.homeRow != null && !focused) 0.45f else 1f, label = "fav fade")
    Column(Modifier.fillMaxWidth().alpha(alpha)) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("★ Favorites", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("0", fontSize = 12.sp, color = c.textDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (suggestions.isNotEmpty()) Text("Suggested · your most played", fontSize = 11.sp, color = c.textDim, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 3.dp)) {
            item(key = "add") { AddTile(act, HomeRow.FAVORITES, onAdd, act.rowFocus.getOrPut(HomeRow.FAVORITES) { androidx.compose.ui.focus.FocusRequester() }) }
            items(suggestions, key = { "sug" + it.uri.toString() }) { g -> RowTile(act, HomeRow.FAVORITES, g, 76.dp, favorite = false, suggestion = true) }
        }
    }
}

/** The dashed "+ Add favorites" tile. */
@Composable
private fun AddTile(act: MainActivity, row: HomeRow, onAdd: () -> Unit, focus: androidx.compose.ui.focus.FocusRequester? = null) {
    val c = LocalWii.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(
        Modifier.width(82.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) { act.homeRow = row; act.focusedGame = null; act.homeFocusedTile = true }
            }
            .clickable(onClick = onAdd),
    ) {
        Box(Modifier.size(82.dp, 76.dp / 0.72f + 6.dp).border(3.dp, if (focused) c.accent else Color.Transparent, shape).padding(3.dp)) {
            Column(
                Modifier.fillMaxSize().drawBehind {
                    drawRoundRect(c.accentText, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("+", fontSize = 32.sp, color = c.accentText, fontWeight = FontWeight.Light)
                Text("Add", fontSize = 11.sp, color = c.accentText, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("Add favorites", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 3.dp, top = 1.dp))
    }
}

/**
 * One Home row. The row the ring is on is full size; the others shrink and fade. Its sort shows in
 * the bottom bar (R1 changes it); each row remembers its own.
 */
@Composable
private fun GameRowView(act: MainActivity, row: HomeRow, games: List<GameEntry>, favorites: Set<String>, onAddFavorites: () -> Unit) {
    val c = LocalWii.current
    val focused = act.homeRow == row
    val quiet = act.homeRow != null && !focused
    val coverW by androidx.compose.animation.core.animateDpAsState(when { focused -> 58.dp; quiet -> 40.dp; else -> 50.dp }, label = "row size")
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (quiet) 0.45f else 1f, label = "row fade")
    val first = act.rowFocus.getOrPut(row) { androidx.compose.ui.focus.FocusRequester() }
    Column(Modifier.fillMaxWidth().alpha(alpha)) {
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (row) {
                HomeRow.WII -> PlatformTag(Platform.WII)
                HomeRow.GAMECUBE -> PlatformTag(Platform.GAMECUBE)
                HomeRow.FAVORITES -> Text("★ Favorites", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                else -> Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Text("${games.size}", fontSize = 12.sp, color = c.textDim, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 3.dp)) {
            itemsIndexed(games, key = { _, g -> row.name + g.uri.toString() }) { i, g -> RowTile(act, row, g, coverW, g.key in favorites, focus = if (i == 0) first else null) }
            // Favorites keeps its "+" at the end.
            if (row == HomeRow.FAVORITES) item(key = "add") { AddTile(act, row, onAddFavorites) }
        }
    }
}

@Composable
private fun RowTile(act: MainActivity, row: HomeRow, g: GameEntry, coverW: androidx.compose.ui.unit.Dp, favorite: Boolean, suggestion: Boolean = false, focus: androidx.compose.ui.focus.FocusRequester? = null) {
    val c = LocalWii.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(
        Modifier.width(coverW + 6.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = g; act.homeRow = row }
            }
            .clickable { act.focusedGame = g; act.openGame(g) },
    ) {
        Box(Modifier.size(coverW + 6.dp, coverW / 0.72f + 6.dp).border(3.dp, if (focused) c.accent else Color.Transparent, shape).padding(3.dp)) {
            Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g).alpha(if (suggestion) 0.85f else 1f), titleSize = 10)
            if (favorite) Text("★", color = Color(0xFFFFD34D), fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            if (suggestion) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(RoundedCornerShape(50)).background(Color(0xCC0E1013)), contentAlignment = Alignment.Center) {
                Text("☆", color = Color(0xFFFFD34D), fontSize = 13.sp)
            }
        }
        if (coverW > 52.dp) Text(g.info.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp, top = 1.dp))
    }
}

/** "Add favorites": every game; Ⓐ (or a tap) stars or unstars, Ⓨ / Ⓑ finish. */
@Composable
private fun FavoritesPicker(act: MainActivity, favorites: Set<String>, onDone: () -> Unit) {
    val c = LocalWii.current
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDone)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.88f).fillMaxHeight(0.86f).clip(shape).background(c.sidebar).border(2.dp, c.accent, shape)
                .clickable(enabled = false) {}.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ADD FAVORITES", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.textDim)
                    Text("Pick the games you come back to", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("${favorites.size} picked", fontSize = 12.sp, color = c.textDim, fontWeight = FontWeight.Bold)
            }
            LazyVerticalGrid(GridCells.Adaptive(70.dp), Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(3.dp)) {
                gridItemsIndexed(act.games.sortedBy { it.info.title.lowercase() }, key = { _, g -> "pick" + g.uri.toString() }) { i, g ->
                    var focused by remember { mutableStateOf(false) }
                    val fav = g.key in favorites
                    Box(
                        Modifier.aspectRatio(0.72f).then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .border(3.dp, if (focused) c.accent else Color.Transparent, RoundedCornerShape(13.dp)).padding(3.dp)
                            .clickable { act.prefs.toggleFavorite(g.key); act.statsVersion++ },
                    ) {
                        Cover(act, g, act.prefs.showCovers, Modifier.fillMaxSize().platformRim(g), titleSize = 9)
                        if (fav) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFFD34D)), contentAlignment = Alignment.Center) {
                            Text("★", color = Color(0xFF3A2A00), fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                HintText("Ⓐ Add / remove", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                WiiButton("Ⓨ Done", onClick = onDone, primary = true, fontSize = 14.sp)
            }
        }
    }
}

/** The four sorts, above the bar's pill for a moment after R1. */
@Composable
private fun SortPopup(current: LibSort, modifier: Modifier) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.clip(shape).background(c.sidebar).border(2.dp, c.accent, shape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        LibSort.entries.forEach { s ->
            val on = s == current
            Text(s.label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = if (on) Color(0xFF0A2533) else c.textDim,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else Color.Transparent).padding(horizontal = 11.dp, vertical = 6.dp))
        }
    }
}

/** Play / Controls on the continue card: 32 dp, chip style, button icon after the word. */
@Composable
private fun CardButton(label: String, key: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(32.dp).focusRing(shape).clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        KeyIcon(key, size = 16.dp)
    }
}

@Composable
private fun WelcomeCard(act: MainActivity) {
    val c = LocalWii.current
    WiiCard(Modifier.fillMaxSize(), radius = 24.dp, padding = 16.dp) {
        CapsLabel("Welcome", color = c.accentText)
        Text(if (act.games.isEmpty()) "No games found yet" else "Pick a game to start", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (act.games.isEmpty()) "Choose the folder with your GameCube and Wii games." else "Your last game will show up here, ready to continue.",
            fontSize = 14.sp, color = c.textDim,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (act.games.isEmpty()) PanelBtn("Choose folder", "A", true, onClick = act::chooseGamesFolder)
            else PanelBtn("All games", null, true) { act.showAllGames() }
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
private fun HomeBar(act: MainActivity, rowsMode: Boolean, lit: Boolean) {
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
        // ⊖ opens Settings too.
        Box(Modifier.padding(start = 8.dp)) { KeyIcon("⊖") }
        // ⏻ exits Delphis (asks first). Holding Ⓑ on Home does the same.
        Box(
            Modifier.padding(start = 10.dp).size(36.dp).focusRing(RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(c.card).border(2.dp, c.cardBorder, RoundedCornerShape(50))
                .onFocusChanged { if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null; act.homeRow = null } }
                .clickable { act.exitPrompt = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PowerSettingsNew, "Exit Delphis", tint = c.textDim, modifier = Modifier.size(20.dp)) }
        Text(rememberClock(withDate = true), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        // No button hints here: the pinned card and the rail show them. On a row, its sort (R1 changes it);
        // a hint only when something needs one (the search box).
        val row = act.homeRow
        val hint = act.homeHint
        when {
            hint != null -> HintText(hint)
            rowsMode && row != null && row.sortable -> {
                val sort = rowSort(act, row)
                Row(
                    Modifier.height(32.dp).clip(shape).background(if (lit) c.accentSoft else c.card).border(2.dp, if (lit) c.accent else c.cardBorder, shape)
                        .focusProperties { canFocus = false }
                        .clickable { act.prefs.setRowSort(row.name, LibSort.entries[(sort.ordinal + 1) % LibSort.entries.size].name); act.statsVersion++; act.sortPopupUntil = System.currentTimeMillis() + 2000 }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SortIcon(c.text)
                    Text(sort.label, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    KeyIcon("R1")
                }
            }
        }
        // The top screen is full screen, so Android's status icons live here instead.
        Box(Modifier.padding(start = 10.dp).width(2.dp).height(26.dp).background(c.barBorder))
        SystemStatusIcons(Modifier.padding(start = 10.dp))
    }
}

/** ⇅ */
@Composable
private fun SortIcon(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
        val u = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(2.4f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(7 * u, 4 * u); lineTo(7 * u, 20 * u); moveTo(4 * u, 17 * u); lineTo(7 * u, 20 * u); lineTo(10 * u, 17 * u)
            moveTo(17 * u, 20 * u); lineTo(17 * u, 4 * u); moveTo(14 * u, 7 * u); lineTo(17 * u, 4 * u); lineTo(20 * u, 7 * u)
        }
        drawPath(p, tint, style = st)
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

/**
 * The rail on the left of Home: Recently played, Wii, GameCube, All games, Favorites (the rows'
 * order), then Search. The lit icon and its coloured bar follow the row you're on; picking one jumps
 * to its row, picking it again opens that section's full grid. L1 shows names and counts.
 */
@Composable
private fun HomeRail(act: MainActivity, favorites: Set<String>, rowsMode: Boolean, hasContinue: Boolean) {
    val c = LocalWii.current
    val open = act.libraryOpen
    val width by androidx.compose.animation.core.animateDpAsState(if (open) 170.dp else 54.dp, label = "rail")
    val active: String = when {
        act.searchOpen || act.libraryQuery.isNotBlank() -> "search"
        !rowsMode -> when (act.librarySection) { LibSection.WII -> "wii"; LibSection.GAMECUBE -> "gc"; LibSection.FAVORITES -> "fav"; LibSection.ALL -> "all" }
        else -> when (act.homeRow) { HomeRow.CONTINUE -> "cont"; HomeRow.WII -> "wii"; HomeRow.GAMECUBE -> "gc"; HomeRow.ALL -> "all"; HomeRow.FAVORITES -> "fav"; HomeRow.RECENT -> "recent"; null -> if (hasContinue) "cont" else "recent" }
    }
    val items = listOfNotNull(
        Triple("cont", "Continue playing", null as Int?).takeIf { hasContinue },
        Triple("recent", "Recently played", null as Int?),
        Triple("wii", "Wii", act.games.count { it.info.platform == Platform.WII }),
        Triple("gc", "GameCube", act.games.count { it.info.platform == Platform.GAMECUBE }),
        Triple("all", "All games", act.games.size),
        Triple("fav", "Favorites", favorites.size),
    )
    fun pick(key: String) {
        // Continue: the ring goes on the top card.
        if (key == "cont") {
            act.searchOpen = false; act.libraryQuery = ""; act.librarySection = LibSection.ALL
            act.railTarget = HomeRow.CONTINUE; act.railJump++
            return
        }
        val row = when (key) { "wii" -> HomeRow.WII; "gc" -> HomeRow.GAMECUBE; "all" -> HomeRow.ALL; "fav" -> HomeRow.FAVORITES; else -> HomeRow.RECENT }
        val section = when (key) { "wii" -> LibSection.WII; "gc" -> LibSection.GAMECUBE; "fav" -> LibSection.FAVORITES; else -> LibSection.ALL }
        if (rowsMode && active == key && key != "recent") { act.librarySection = section; act.homeRow = null }
        else {
            act.searchOpen = false; act.libraryQuery = ""; act.librarySection = LibSection.ALL
            act.railTarget = row
            act.railJump++
        }
    }
    Column(
        Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp).width(width).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (key, label, count) ->
            RailItem(act, key, label, count?.toString(), key == active, open, badge = key == "fav" && favorites.isEmpty()) { pick(key) }
        }
        Spacer(Modifier.weight(1f))
        RailItem(act, "search", "Search", null, active == "search", open) { act.searchOpen = true; act.librarySection = LibSection.ALL; act.homeRow = null }
        Row(Modifier.padding(start = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyIcon("L1", size = 16.dp)
            if (open) Text("Hide names", fontSize = 11.sp, color = c.textDim, maxLines = 1)
        }
    }
}

/** The colour of a rail entry when lit: white Wii, purple GameCube, yellow Favorites, blue for the rest. */
private fun railColour(key: String, c: WiiColors) = when (key) { "wii" -> WiiWhite; "gc" -> Color(0xFF8F80FF); "fav" -> Color(0xFFFFD34D); else -> c.accentText }
private fun railBar(key: String, c: WiiColors) = when (key) { "wii" -> WiiWhite; "gc" -> CubePurple; "fav" -> Color(0xFFFFD34D); else -> c.accent }

@Composable
private fun RailItem(act: MainActivity, key: String, label: String, count: String?, lit: Boolean, open: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    val tint = if (lit) railColour(key, c) else c.textDim
    Box {
        if (lit) Box(Modifier.align(Alignment.CenterStart).offset(x = (-6).dp).size(4.dp, 20.dp).clip(RoundedCornerShape(2.dp)).background(railBar(key, c)))
        Row(
            Modifier.height(38.dp).then(if (open) Modifier.fillMaxWidth() else Modifier.width(40.dp)).focusRing(shape).clip(shape)
                .background(if (lit) c.card else Color.Transparent)
                .onFocusChanged { if (it.isFocused) { act.homeFocusedTile = true; act.focusedGame = null } }
                .clickable(onClick = onClick).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                RailIcon(key, tint, Modifier.size(22.dp))
                if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-4).dp).size(13.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A2533), style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
                }
            }
            if (open) {
                Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (lit) c.text else c.textDim, maxLines = 1, modifier = Modifier.weight(1f))
                count?.let { Text(it, fontSize = 11.sp, color = c.textDim) }
            }
        }
    }
}

/** Line icons for the rail (drawn, so they follow the theme). */
@Composable
private fun RailIcon(key: String, tint: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * u, y * u)
        fun sz(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w * u, h * u)
        fun cr(v: Float) = androidx.compose.ui.geometry.CornerRadius(v * u, v * u)
        when (key) {
            "cont" -> {
                drawRoundRect(tint, o(3f, 4f), sz(18f, 16f), cr(4f), style = st)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(10 * u, 8.5f * u); lineTo(15.5f * u, 12 * u); lineTo(10 * u, 15.5f * u); close() }, tint)
            }
            "recent" -> { drawCircle(tint, 8.5f * u, o(12f, 12f), style = st); drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(12 * u, 7 * u); lineTo(12 * u, 12 * u); lineTo(15.5f * u, 14 * u) }, tint, style = st) }
            "wii" -> { drawRoundRect(tint, o(8f, 2f), sz(8f, 20f), cr(4f), style = st); drawCircle(tint, 2f * u, o(12f, 8.5f)) }
            "gc" -> drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(12 * u, 2.9f * u); lineTo(20 * u, 7.4f * u); lineTo(20 * u, 16.6f * u); lineTo(12 * u, 21.1f * u); lineTo(4 * u, 16.6f * u); lineTo(4 * u, 7.4f * u); close()
                moveTo(4 * u, 7.4f * u); lineTo(12 * u, 11.9f * u); lineTo(20 * u, 7.4f * u); moveTo(12 * u, 11.9f * u); lineTo(12 * u, 21.1f * u)
            }, tint, style = st)
            "all" -> listOf(3.5f to 3.5f, 13.5f to 3.5f, 3.5f to 13.5f, 13.5f to 13.5f).forEach { (x, y) -> drawRoundRect(tint, o(x, y), sz(7f, 7f), cr(2f), style = st) }
            "fav" -> {
                val p = androidx.compose.ui.graphics.Path()
                for (i in 0 until 10) {
                    val a = Math.toRadians((-90 + i * 36).toDouble()); val r = if (i % 2 == 0) 9.2f else 4.4f
                    val x = 12f + r * Math.cos(a).toFloat(); val y = 12.8f + r * Math.sin(a).toFloat()
                    if (i == 0) p.moveTo(x * u, y * u) else p.lineTo(x * u, y * u)
                }
                p.close(); drawPath(p, tint, style = st)
            }
            "search" -> { drawCircle(tint, 6.5f * u, o(10.5f, 10.5f), style = st); drawLine(tint, o(15.5f, 15.5f), o(20.5f, 20.5f), 2.2f * u, androidx.compose.ui.graphics.StrokeCap.Round) }
        }
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
                if (ok) Notices.done(act, "Autosave restored", "Play continues from the save of ${formatSaveTime(chosen.savedAt)}.")
                else Notices.problem(act, "Couldn't restore that autosave")
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
    // Looks (post-processing shader), per game: shares the Picture panel with the screen.
    val lookGid = game.info.gameId?.takeIf { act.prefs.looksEnabled }
    var look by remember(game.key) { mutableStateOf(lookGid?.let { act.prefs.lookFor(it) }) }
    var lookBefore by remember { mutableStateOf(look) }
    var pictureTab by remember { mutableIntStateOf(0) }
    fun setLook(l: io.github.hiramrg.delphis.core.Looks.Look) { look = l; lookGid?.let { act.prefs.setLookFor(it, l) } }
    fun openScreen() { screenBefore = screen; lookBefore = look; pictureTab = if (screen == null) 1 else 0; showScreen = true }
    fun cancelScreen() { screenBefore?.let { setScreen(it) }; lookBefore?.let { setLook(it) }; showScreen = false }
    val hasPicture = screen != null || look != null

    // Physical buttons: A = use the preset under the ring, or Play if it's already in use; Start = Play;
    // D-pad = move the ring, X = Edit,
    // L1 = autosave on/off for this game, R1 = remember controls on/off, Y = earlier autosaves. B = Back.
    var showList by remember { mutableStateOf(!companion) }
    // The bottom screen can come and go: the list sits there when it's up, in the card otherwise.
    LaunchedEffect(companion) { showList = !companion }
    // L2 flips the controls card between the Thor diagram and the list.
    var viewToggle by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf<Preset?>(null) }
    var confirmDelete by remember { mutableStateOf<Preset?>(null) }
    val onKey by rememberUpdatedState<(KeyEvent) -> Boolean> { e ->
        val sc = screen
        // The shared confirm panel: Ⓐ yes, Ⓑ cancel; nothing else reaches the page.
        val pending = confirmReset ?: confirmDelete
        if (pending != null) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (confirmReset != null) p.resetToDefault(pending) else p.deleteMine(pending)
                    confirmReset = null; confirmDelete = null
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { confirmReset = null; confirmDelete = null }
            }
            return@rememberUpdatedState true
        }
        if (showScreen && hasPicture) {
            // Picture panel open: L1 / R1 = Screen / Look tab; Screen: Ⓨ = 4:3 / 16:9, ◀ ▶ = card;
            // Look: the D-pad moves on the grid. Ⓐ Done, Ⓑ Cancel; nothing else reaches the page.
            val onLook = look != null && (pictureTab == 1 || sc == null)
            val lk = look
            if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> if (sc != null) pictureTab = 0
                KeyEvent.KEYCODE_BUTTON_R1 -> if (look != null) pictureTab = 1
                KeyEvent.KEYCODE_BUTTON_Y -> if (!onLook && sc != null && e.repeatCount == 0) setScreen(sc.copy(shape = if (sc.shape == io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE) io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD else io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE))
                KeyEvent.KEYCODE_DPAD_LEFT -> if (onLook && lk != null) setLook(lookStep(lk, -1)) else if (sc != null) setScreen(screenStep(sc, -1))
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (onLook && lk != null) setLook(lookStep(lk, 1)) else if (sc != null) setScreen(screenStep(sc, 1))
                KeyEvent.KEYCODE_DPAD_UP -> if (onLook && lk != null) setLook(lookStep(lk, -3))
                KeyEvent.KEYCODE_DPAD_DOWN -> if (onLook && lk != null) setLook(lookStep(lk, 3))
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
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.repeatCount == 0) showList = !showList; true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { if (e.repeatCount == 0 && backups.isNotEmpty()) showBackups = true; true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { if (e.repeatCount == 0 && !showList) viewToggle++; true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (e.repeatCount == 0) { if (multiplayer) showPlayers = true else if (hasPicture) openScreen() }
                multiplayer || hasPicture
            }
            // Multiplayer: ⊖ is Players, so the Picture panel opens with L3.
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                if (e.repeatCount == 0 && multiplayer && hasPicture) openScreen()
                multiplayer && hasPicture
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { if (e.repeatCount == 0 && achievements != null) showAchievements = true; achievements != null }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = { onKey(it) }; onDispose { act.keyListener = null } }

    var menu by remember { mutableStateOf(false) }
    val shown = p.focused ?: p.selected

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left: the game, then Edit and Play.
        Column(Modifier.fillMaxHeight().width(160.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                }
                // One Picture chip for the screen (GameCube) and the look: "▭ 16:9 · ✦ CRT".
                if (hasPicture) {
                    val parts = listOfNotNull(screen?.let { if (look != null) "▭ " + (if (it.shape == io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE) "16:9" else "4:3") else "▭ " + screenChipLabel(it, screenGid, act.prefs.gcUseHack) }, look?.let { "✦ " + it.label })
                    GameChip(parts.joinToString(" · "), if (multiplayer) "L3" else "⊖") { openScreen() }
                }
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
                    PanelBtn(if (showList) "Done" else "Change", "Y", false, Modifier.then(if (showList) Modifier.border(2.dp, c.accent, RoundedCornerShape(50)) else Modifier)) { showList = !showList }
                    PanelBtn("✎ Edit", "X", false, enabled = p.editTarget != null) { p.editing = true }
                    val canReset = shown != null && p.isEdited(shown)
                    val canDelete = shown != null && act.store.isMine(shown.id)
                    if (canReset || canDelete || backups.isNotEmpty()) Box {
                        GameChip("⋯", null) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (canReset) DropdownMenuItem(text = { Text("↺ Reset to default", color = Color(0xFFF0866E), fontWeight = FontWeight.Bold) }, onClick = { menu = false; confirmReset = shown })
                            if (canDelete) DropdownMenuItem(text = { Text("🗑 Delete this preset", color = Color(0xFFF0866E), fontWeight = FontWeight.Bold) }, onClick = { menu = false; confirmDelete = shown })
                            if (backups.isNotEmpty()) DropdownMenuItem(text = { HintText("↺ Earlier autosaves (${backups.size}) · R2", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text) }, onClick = { menu = false; showBackups = true })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (showList) {
                    PresetChooser(p.groups, p.selected, p.focused, game.info.title, { p.isEdited(it) }, Modifier.weight(1f).fillMaxWidth()) { p.choose(it) }
                } else if (shown != null) {
                    ControlsSwitchable(shown, act.prefs, game.key, Modifier.weight(1f).fillMaxWidth(), toggle = viewToggle, toggleKey = "L2")
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        HintText(if (companion) "Pick a control style on the bottom screen (or with the D-pad)." else "Press Change Ⓨ to pick a control style.", color = c.textDim, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    // The same bottom bar as Home; "◀ Home Ⓑ" lives in it.
    AppBottomBar(null, leading = { HomeBarPill { act.screen = Screen.Home }; Spacer(Modifier.width(14.dp)) })
    }
    confirmReset?.let { r -> ResetConfirm(r.name, wide = true, onKeep = { confirmReset = null }) { confirmReset = null; p.resetToDefault(r) } }
    confirmDelete?.let { d ->
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)).clickable { confirmDelete = null })
        ConfirmPanel(
            "My presets · ${game.info.title}", "Delete this preset?", "\u201C${d.name}\u201D is removed from My presets. Built-in presets aren't affected.",
            confirm = "Delete", modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.56f).clickable(enabled = false) {},
            onCancel = { confirmDelete = null }, onConfirm = { confirmDelete = null; p.deleteMine(d) },
        )
    }
    val sc = screen
    if (showScreen && hasPicture) PicturePanel(
        game.info.title, pictureTab, { pictureTab = it },
        sc, screenGid, act.prefs.gcUseHack, coverColour, { setScreen(it) },
        look, { setLook(it) },
        onCancel = { cancelScreen() }, onDone = { showScreen = false },
    )
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
        Step("Install Dolphin", if (dolphinPkg != null) "Found $dolphinPkg" else "Install the official Dolphin Emulator, then open it once.", dolphinPkg != null, if (dolphinPkg != null) null else "Open store", action = {
            runCatching { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.dolphinemu.dolphinemu"))) }
                .onFailure { Notices.info(act, "Couldn't open the store", "Search for \"Dolphin Emulator\" in your app store.") }
        }),
        Step("Connect Dolphin's folder", "In the picker: side menu (☰) → \"Dolphin\" → \"Use this folder\".", folderOk, if (folderOk) "Change" else "Connect", action = act::connectDolphinFolder),
        Step("Choose your games folder", if (hasGames) "${act.games.size} games found" else "The folder with your GameCube and Wii games.", hasGames, if (hasGames) "Change" else "Choose", action = act::chooseGamesFolder),
        Step("Turn on \"close games\"", "Lets Delphis exit a game cleanly (no input delay).", helperOn, if (helperOn) "Settings" else "Turn on", action = { DolphinExitService.openSettings(act) }),
        Step("Controller check", "Recommended · press each button once (about a minute).", calibrated, if (calibrated) "Redo" else "Start", optional = true, action = { act.screen = Screen.ControllerCheck }),
        Step("Try gyro aiming", "Optional · find the speed you like.", false, "Try", optional = true, action = { act.screen = Screen.GyroTest }),
    )
    val current = steps.indexOfFirst { !it.done }.let { if (it < 0) steps.lastIndex else it }
    // The step on the ring: follows the first unfinished step until the D-pad moves it.
    var sel by remember { mutableIntStateOf(current) }
    var moved by remember { mutableStateOf(false) }
    LaunchedEffect(current) { if (!moved) sel = current }
    val step = steps[sel.coerceIn(0, steps.lastIndex)]
    val goHome = { act.screen = Screen.Home; if (act.games.isEmpty()) act.rescan() }
    fun next() { moved = true; sel = (sel + 1).coerceAtMost(steps.lastIndex) }
    val onKey: (KeyEvent) -> Boolean = { e ->
        if (e.action != KeyEvent.ACTION_DOWN) false else when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { moved = true; sel = (sel - 1).coerceAtLeast(0); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { next(); true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (e.repeatCount == 0) { if (step.button != null) step.action() else if (act.isSetUp()) goHome() }; true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.repeatCount == 0) next(); true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (e.repeatCount == 0 && act.isSetUp()) goHome(); true }
            else -> false
        }
    }
    DisposableEffect(Unit) { act.keyListener = onKey; onDispose { act.keyListener = null } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Welcome! Let's set up Delphis", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            CapsLabel("Step ${sel + 1} of ${steps.size}", color = c.accentText)
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The steps as a rail, like Settings.
            Column(Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                steps.forEachIndexed { i, s ->
                    val on = i == sel
                    val shape = RoundedCornerShape(12.dp)
                    Box(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        if (on) Box(Modifier.align(Alignment.CenterStart).offset(x = (-8).dp).width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(if (on) c.card else Color.Transparent)
                                .border(if (on) 3.dp else 0.dp, if (on) c.accent else Color.Transparent, shape)
                                .clickable { moved = true; sel = i }.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(
                                    when { s.done -> Color(0xFF2E7D4F); on -> c.accent; else -> c.cardBorder.copy(alpha = 0.6f) },
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (s.done) "✓" else "${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (on && !s.done) c.onAccent else Color.White) }
                            Text(s.title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (on || s.done) c.text else c.textDim, maxLines = 2, lineHeight = 16.sp)
                        }
                    }
                }
            }
            // The step on the ring, explained in plain words.
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapsLabel(if (step.done) "Step ${sel + 1} · done" else if (step.optional) "Step ${sel + 1} · optional" else "Step ${sel + 1}", color = if (step.done) c.green else c.accentText)
                Text(step.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp)
                Text(step.sub, fontSize = 13.sp, color = c.textDim, lineHeight = 18.sp)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step.button != null) PanelBtn(step.button, "A", !step.done, Modifier.weight(1f), onClick = step.action)
                    if (sel < steps.lastIndex) PanelBtn(if (step.done) "Next step" else "Skip for now", "Y", false) { next() }
                    else PanelBtn("Go to my games", "Start", act.isSetUp(), enabled = act.isSetUp(), onClick = goHome)
                }
            }
        }
        AppBottomBar(if (act.isSetUp()) "✚ Steps  Ⓐ Do it  Start My games" else "✚ Steps  Ⓐ Do it  Ⓨ Skip")
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Same nav as Home's rail: line icons, a lit bar for the page you're on, the ring only where the D-pad is.
            Column(
                Modifier.width(206.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BackLink("Home") { act.screen = Screen.Home }
                Spacer(Modifier.height(6.dp))
                SettingsSection.entries.forEach { s -> SettingsNavItem(s, act.settingsSection == s) { act.settingsSection = s } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    CapsLabel("Settings")
                    Text(act.settingsSection.label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
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
                    SettingsSection.HELP -> GroupCard {
                        SettingRow("Diagnostics", "If presets don't respond or something crashes, open Diagnostics and copy the report.", onClick = { act.screen = Screen.Diagnostics }) {
                            Text("Open ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
                        }
                    }
                }
            }
        }
        AppBottomBar("✚ Choose  Ⓐ Select  Ⓑ Back")
    }
}

/** Line icon for each Settings page. */
private fun settingsIcon(s: SettingsSection): androidx.compose.ui.graphics.vector.ImageVector = when (s) {
    SettingsSection.APPEARANCE -> Icons.Outlined.Palette
    SettingsSection.SOUND -> Icons.Outlined.VolumeUp
    SettingsSection.CONTROLS -> Icons.Outlined.SportsEsports
    SettingsSection.GYRO -> Icons.Outlined.ScreenRotation
    SettingsSection.CLOSING -> Icons.Outlined.ExitToApp
    SettingsSection.DISPLAY -> Icons.Outlined.Tv
    SettingsSection.BOTTOM -> Icons.Outlined.StayCurrentLandscape
    SettingsSection.ACHIEVEMENTS -> Icons.Outlined.EmojiEvents
    SettingsSection.LIBRARY -> Icons.Outlined.Folder
    SettingsSection.FRONTENDS -> Icons.Outlined.Launch
    SettingsSection.UPDATES -> Icons.Outlined.Download
    SettingsSection.HELP -> Icons.Outlined.HelpOutline
}

@Composable
private fun SettingsNavItem(s: SettingsSection, lit: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        if (lit) Box(Modifier.align(Alignment.CenterStart).offset(x = (-8).dp).width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
        Row(
            Modifier.fillMaxWidth().focusRing(shape).clip(shape).background(if (lit) c.card else Color.Transparent)
                .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(settingsIcon(s), null, tint = if (lit) c.accentText else c.textDim, modifier = Modifier.size(18.dp))
            Text(s.label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (lit) c.text else c.textDim, maxLines = 2, lineHeight = 16.sp)
        }
    }
}

/** Themed slider (accent thumb and track). */
@Composable
private fun WiiSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = LocalWii.current
    Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.cardBorder, activeTickColor = c.onAccent, inactiveTickColor = c.textDim),
    )
}

/** Filled dot with ✓ for the picked card (replaces Android's radio button). */
@Composable
private fun SelectDot(selected: Boolean) {
    val c = LocalWii.current
    Box(
        Modifier.padding(end = 8.dp).size(20.dp).clip(RoundedCornerShape(50)).background(if (selected) c.accent else Color.Transparent)
            .border(2.dp, if (selected) c.accent else c.cardBorder, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) { if (selected) Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Black, color = c.onAccent) }
}

@Composable
private fun AppearanceSettings(act: MainActivity) {
    val c = LocalWii.current
    var covers by remember { mutableStateOf(act.prefs.showCovers) }
    CapsLabel("Theme")
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
            SelectDot(selected)
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
    WiiSlider(
        value = volume, enabled = on, valueRange = 0f..100f,
        onValueChange = { volume = it; act.prefs.menuMusicVolume = it.toInt(); music.refresh(act, act.prefs) },
    )
    CapsLabel("Music")
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
    GroupCard {
        SettingRow(
            "Controller check", if (prefs.calibratedDeviceName == null) "Not done yet · press each button once (about a minute)." else "Checked: ${prefs.calibratedDeviceName}",
        ) { PanelBtn(if (prefs.calibratedDeviceName == null) "Start" else "Redo", null, prefs.calibratedDeviceName == null) { act.screen = Screen.ControllerCheck } }
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
    WiiSlider(value = gyroSpeed, onValueChange = { gyroSpeed = it }, onValueChangeFinished = { prefs.gyroSensitivity = gyroSpeed.toInt() }, valueRange = 1f..10f, steps = 8)
    SwitchRow("Invert up/down", "Tilting the Thor up moves the pointer down.", invertY) { invertY = it; prefs.gyroInvertY = it }
    SwitchRow("Invert left/right", "Turning the Thor left moves the pointer right.", invertX) { invertX = it; prefs.gyroInvertX = it }
    PanelBtn("Test gyro aiming", null, true) { act.screen = Screen.GyroTest }
}

@Composable
private fun ClosingSettings(act: MainActivity) {
    val helperOn = remember(act.setupVersion) { DolphinExitService.isEnabled(act) }
    var skipWarn by remember { mutableStateOf(act.prefs.skipExitWarning) }
    GroupCard {
        SettingRow(
            if (helperOn) "Close-games helper is on" else "Close-games helper is off",
            "Presses Dolphin's own \"Exit Emulation\" when you leave or restart a game. Needed on the Thor, where Android won't let Delphis force-close Dolphin. Doesn't touch controller input.",
        ) { PanelBtn(if (helperOn) "Settings" else "Turn on", null, !helperOn) { DolphinExitService.openSettings(act) } }
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
            PanelBtn("Delete all", null, false) { confirm = true }
            if (confirm) ConfirmDialog(
                "Closing games", "Delete all earlier autosaves?", "Your current autosaves stay; only the older copies kept as backups are deleted (${formatSize(backupsSize)}).",
                confirm = "Delete", onCancel = { confirm = false },
            ) {
                confirm = false
                act.lifecycleScope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.deleteAllBackups() }
                    backupsVersion++
                    act.statsVersion++
                    Notices.done(act, "Earlier autosaves deleted")
                }
            }
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
    CapsLabel("GameCube games")
    GroupCard {
        SettingRow("New games start in", "Each game can change it on its page (▭).") {
            Segmented(listOf(io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD to "4:3", io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE to "16:9"), if (wide) io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE else io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD,
                { wide = it == io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE; prefs.gcDefaultWide = wide })
        }
        RowDivider()
        SettingRow("16:9 fill", "How a 4:3 game fills the wide screen.") {
            Segmented(listOf(io.github.hiramrg.delphis.core.GameScreen.Fill.WORLD to "More world", io.github.hiramrg.delphis.core.GameScreen.Fill.STRETCH to "Stretch"), fill, { fill = it; prefs.gcFill = it })
        }
        RowDivider()
        SettingRow("4:3 sides", "What fills the space next to a 4:3 picture.") {
            Segmented(io.github.hiramrg.delphis.core.GameScreen.Sides.entries.map { it to it.label }, sides, { sides = it; prefs.gcSides = it })
        }
        RowDivider()
        ToggleRow("Use the widescreen hack", "When a game has no widescreen patch. More of the world for any 3D game; some menus or edges may glitch. Off = stretch instead.", hack) { hack = it; prefs.gcUseHack = it }
    }
    Text("Delphis turns on Dolphin's own widescreen code when it ships one for the game (cheats on for that game only). Dolphin applies all of this when a game starts.",
        fontSize = 12.sp, color = c.textDim)
    // Looks (post-processing shaders), every game.
    var looksOn by remember { mutableStateOf(prefs.looksEnabled) }
    var defLook by remember { mutableStateOf(prefs.defaultLook) }
    CapsLabel("Looks")
    GroupCard {
        ToggleRow("Use Delphis' looks", "Scanlines, CRT, Vivid… per game, from the ✦ chip on the game page. Off: Delphis leaves Dolphin's own Post-Processing setting alone.", looksOn) { looksOn = it; prefs.looksEnabled = it }
        if (looksOn) {
            RowDivider()
            Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("New games look", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("Each game can change it on its page.", fontSize = 12.sp, color = c.textDim)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    io.github.hiramrg.delphis.core.Looks.Look.entries.forEach { l -> WiiChip(l.label, defLook == l, { defLook = l; prefs.defaultLook = l }) }
                }
            }
        }
    }
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
    CapsLabel("While you play")
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
            WiiSlider(
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
            SelectDot(selected)
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, fontSize = 10.sp, color = c.textDim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LibrarySettings(act: MainActivity) {
    val c = LocalWii.current
    GroupCard {
        SettingRow("Dolphin folder", if (act.bridge.hasConfigAccess()) "Connected · ${act.bridge.installedPackage() ?: "Dolphin not installed"}" else "Not connected", onClick = act::connectDolphinFolder) {
            Text("Change ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        }
        RowDivider()
        SettingRow("Games folder", "${act.games.size} games", onClick = act::chooseGamesFolder) {
            Text("Change ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        }
        RowDivider()
        SettingRow("Scan again", "Looks for games you added or removed.", onClick = act::rescan) {
            Text(if (act.scanning) "Scanning…" else "Scan ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        }
    }
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
    GroupCard { ToggleRow(title, subtitle, checked, onChange) }
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


/** "Keep holding Ⓑ to exit": a ring fills over a second; letting go cancels. */
@Composable
private fun HoldToExit(modifier: Modifier) {
    val c = LocalWii.current
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.shadow(14.dp, shape).clip(shape).background(if (c.dark) Color(0xFF22272D) else c.card).border(2.dp, c.accent, shape).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
            val st = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(c.cardBorder, 0f, 360f, false, style = st)
            drawArc(c.accent, -90f, 360f * progress.value, false, style = st)
        }
        Column {
            HintText("Keep holding Ⓑ to exit", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
            Text("Closes Delphis and Dolphin", fontSize = 11.5.sp, color = c.textDim)
        }
    }
}
