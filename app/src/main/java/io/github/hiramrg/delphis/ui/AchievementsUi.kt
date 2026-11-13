package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.core.GameInfo
import io.github.hiramrg.delphis.core.RetroAchievements.Achievement
import io.github.hiramrg.delphis.core.RetroAchievements.GameProgress
import io.github.hiramrg.delphis.platform.Achievements
import io.github.hiramrg.delphis.platform.Prefs
import kotlinx.coroutines.launch

val AchGold = Color(0xFFE8B64A)
private val AchGoldSoft = Color(0xFF2A2618)
private val AchGoldBorder = Color(0xFF6B5A2E)

/** This game's achievements and progress (null when not signed in, the list is off, or there's no set). Refreshes after unlocks. */
@Composable
fun rememberAchievements(prefs: Prefs, key: String, info: GameInfo): GameProgress? {
    val context = LocalContext.current
    val version by Achievements.version.collectAsState()
    val p by produceState<GameProgress?>(null, key, version) {
        value = if (prefs.raList && Achievements.account(prefs) != null) Achievements.progress(context, prefs, key, info) else null
    }
    return p
}

@Composable
fun BadgeImage(url: String, size: Dp, radius: Dp = 7.dp) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, url) { value = Achievements.badge(context, url) }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color(0xFF2A2F36)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("🏆", fontSize = (size.value * 0.45f).sp)
    }
}

/** "🏆 13 / 40 · 32%" (or compact "🏆 13/40" for the in-game top bar). */
@Composable
fun AchievementsChip(p: GameProgress, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, active: Boolean = false) {
    val shape = ChipShape
    Text(
        if (compact) "🏆 ${p.unlocked}/${p.total}" else "🏆 ${p.unlocked} / ${p.total} · ${p.percent}%",
        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (active) Color(0xFF2A2010) else Color(0xFFF5D27A), maxLines = 1,
        modifier = modifier.clip(shape).background(if (active) AchGold else AchGoldSoft).border(2.dp, if (active) AchGold else AchGoldBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

private enum class AchFilter(val label: String) { ALL("All"), UNLOCKED("Unlocked"), LOCKED("Locked") }

/** The game's list: header with progress, a filter, then every achievement (unlocked first). */
@Composable
fun AchievementList(
    p: GameProgress, modifier: Modifier, onClose: (() -> Unit)? = null, closeLabel: String = "✕",
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    var filter by remember { mutableStateOf(AchFilter.ALL) }
    val items = remember(p, filter) {
        p.sorted.filter { when (filter) { AchFilter.ALL -> true; AchFilter.UNLOCKED -> it.unlocked; AchFilter.LOCKED -> !it.unlocked } }
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 Achievements", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("${p.unlocked} of ${p.total} · ${p.pointsEarned} / ${p.pointsTotal} pts", fontSize = 11.sp, color = Color(0xFFA3ADB8), modifier = Modifier.weight(1f), maxLines = 1)
            if (onClose != null) Row(
                Modifier.clip(RoundedCornerShape(50)).border(2.dp, Color(0xFF4A535E), RoundedCornerShape(50)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val split = splitButtonLabel(closeLabel)
                Text(split?.first ?: closeLabel, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC9D0D8))
                split?.let { KeyIcon(it.second, Color(0xFFA3ADB8), 16.dp) }
            }
        }
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF15181C))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.percent / 100f).background(Brush.horizontalGradient(listOf(AchGold, Color(0xFFF5D27A)))))
        }
        SegmentedPill(AchFilter.entries.map { it to it.label }, filter, { filter = it }, Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(items, key = { it.id }) { AchievementRow(it) }
            if (items.isEmpty()) item {
                Text(if (filter == AchFilter.UNLOCKED) "Nothing unlocked yet." else "All unlocked!", fontSize = 12.sp, color = Color(0xFF8E9AA6), modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun AchievementRow(a: Achievement) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp).alpha(if (a.unlocked) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically) {
        BadgeImage(a.badgeUrl(), 34.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.description, fontSize = 10.5.sp, color = Color(0xFFA3ADB8), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text((if (a.unlocked) "✓ " else "") + "${a.points} pts", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = AchGold, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The list as a dialog (game page). D-pad scrolls, Ⓑ closes. */
@Composable
fun AchievementsDialog(p: GameProgress, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        AchievementList(
            p,
            Modifier.width(440.dp).heightIn(max = 340.dp).shadow(18.dp, shape).clip(shape).background(Color(0xFF22272D)).border(2.dp, Color(0xFF34BEED), shape)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_DOWN -> { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex + 1).coerceAtMost(p.total)) }; true }
                        AndroidKey.KEYCODE_DPAD_UP -> { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 1).coerceAtLeast(0)) }; true }
                        AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_BUTTON_THUMBR -> { onDismiss(); true }
                        else -> true
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            onClose = onDismiss, closeLabel = "Ⓑ Close", listState = state,
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The unlock badge on the bottom screen (slides down from the top for 5 s). */
@Composable
fun UnlockToast(popup: Achievements.Popup?, modifier: Modifier) {
    AnimatedVisibility(popup != null, modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        var last by remember { mutableStateOf(popup) }
        if (popup != null) last = popup
        val p = last ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Color(0xF014181D)).border(2.dp, AchGold, shape).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgeImage(p.unlock.badgeUrl(), 46.dp, 9.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("🏆 ACHIEVEMENT UNLOCKED", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = AchGold, letterSpacing = 0.8.sp)
                Text(p.unlock.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.unlock.description, fontSize = 11.sp, color = Color(0xFFA3ADB8), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text("${p.unlock.points} pts", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = AchGold)
                if (p.unlocked != null && p.total != null) Text("${p.unlocked} / ${p.total}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E9AA6))
            }
        }
    }
}

// ------------------------------------------------------------------ Settings → Achievements

@Composable
fun AchievementsSettings(act: MainActivity, switchRow: @Composable (String, String, Boolean, (Boolean) -> Unit) -> Unit) {
    val prefs = act.prefs
    val c = LocalWii.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(Achievements.account(prefs)) }
    var profile by remember { mutableStateOf<io.github.hiramrg.delphis.core.RetroAchievements.Profile?>(null) }
    var user by remember { mutableStateOf(prefs.raUser.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var popups by remember { mutableStateOf(prefs.raPopups) }
    var where by remember { mutableStateOf(prefs.raWhere) }
    var list by remember { mutableStateOf(prefs.raList) }
    LaunchedEffect(account) { account?.let { profile = Achievements.verify(it.user, it.key) } }

    Text(
        "Achievements are unlocked by Dolphin: sign in to RetroAchievements in Dolphin too (Dolphin → Settings → Achievements). " +
            "Delphis reads your account to show a badge when you unlock one and each game's list.",
        fontSize = 12.sp, color = c.textDim,
    )
    if (account == null) {
        Text("RetroAchievements account", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(user, { user = it.trim(); error = null }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            key, { key = it.trim(); error = null }, label = { Text("Web API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Text("Find your web API key on retroachievements.org → Settings → Keys.", fontSize = 11.sp, color = c.textDim)
        error?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.orange) }
        WiiButton(if (checking) "Checking…" else "Connect", primary = true, enabled = !checking && user.isNotBlank() && key.isNotBlank(), onClick = {
            checking = true
            scope.launch {
                val p = Achievements.verify(user, key)
                checking = false
                if (p == null) error = "Couldn't sign in. Check the username and web API key" + (Achievements.lastError?.let { " ($it)" } ?: "") + "."
                else { prefs.raUser = p.user; prefs.raKey = key; profile = p; account = Achievements.account(prefs); key = "" }
            }
        })
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RetroAchievements account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Connected as ${account!!.user}" + (profile?.let { " · ${"%,d".format(it.points)} pts" } ?: ""), fontSize = 12.sp, color = c.textDim)
            }
            WiiButton("Sign out", onClick = { prefs.raKey = null; account = null; profile = null })
        }
    }
    switchRow("Show unlock pop-ups", "A badge appears when you unlock an achievement.", popups) { popups = it; prefs.raPopups = it }
    if (popups) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Where", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
        listOf("top" to "Top screen", "bottom" to "Bottom screen", "both" to "Both").forEach { (id, label) ->
            WiiChip(label, where == id, { where = id; prefs.raWhere = id })
        }
    }
    switchRow("Achievement list", "🏆 button on the game page and in game, with your progress.", list) { list = it; prefs.raList = it }
    Text(
        "Badges appear a few seconds after Dolphin unlocks the achievement. Top-screen badges need \"close games\" (Delphis' accessibility helper) turned on.",
        fontSize = 11.sp, color = c.textDim,
    )
}
