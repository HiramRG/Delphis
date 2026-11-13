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
import kotlinx.coroutines.delay
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

    // One card for both: Dolphin earns achievements (its own sign-in), Delphis reads your progress (web API key).
    var dolphin by remember { mutableStateOf<io.github.hiramrg.delphis.platform.DolphinBridge.RaStatus?>(null) }
    var statusVersion by remember { mutableStateOf(0) }
    LaunchedEffect(statusVersion) { dolphin = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.raStatus() } }
    var showSignIn by remember { mutableStateOf(false) }
    LaunchedEffect(act.raSignInPrompt) { if (act.raSignInPrompt) { act.raSignInPrompt = false; showSignIn = true } }
    val dolOk = dolphin?.signedIn == true
    val name = account?.user ?: dolphin?.user ?: prefs.raUser

    CapsLabel("RetroAchievements account" + (name?.let { " · $it" } ?: ""))
    GroupCard {
        RaStatusRow(
            dolOk, "Dolphin", "earns them while you play",
            when { dolphin == null -> "Checking…"; dolOk -> "Signed in · ready to earn achievements"; else -> "Signed out · games won't earn achievements" },
            warn = dolphin != null && !dolOk,
        ) { if (dolphin != null && !dolOk) PanelBtn("Sign in", "A", true) { showSignIn = true } }
        RowDivider()
        RaStatusRow(
            account != null, "Delphis", "shows your progress",
            if (account != null) "Connected" + (profile?.let { " · ${"%,d".format(it.points)} pts" } ?: "") else "Not connected · no badges or progress",
            warn = account == null,
        ) {
            when {
                account == null && dolOk -> PanelBtn("Connect", "A", true) { showSignIn = true }
                account != null && dolOk -> PanelBtn("Sign out", null, false) {
                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.stopIdleDolphin(); delay(300); act.bridge.setDolphinRa(null, null) }
                        prefs.raKey = null; account = null; profile = null; statusVersion++
                        Notices.done(act, "Signed out", "Dolphin and Delphis are both signed out of RetroAchievements.")
                    }
                }
            }
        }
    }

    if (showSignIn) RaSignInPanel(
        act, startUser = name.orEmpty(), needToken = !dolOk, needKey = account == null,
        onDone = { p -> if (p != null) { profile = p; account = Achievements.account(prefs) }; statusVersion++; showSignIn = false },
        onCancel = { showSignIn = false },
    )
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


@Composable
private fun RaStatusRow(ok: Boolean, who: String, role: String, line: String, warn: Boolean, trailing: @Composable RowScope.() -> Unit) {
    val c = LocalWii.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(if (ok) c.green.copy(alpha = 0.18f) else c.orange.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (ok) "✓" else "!", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (ok) c.green else c.orange) }
        Column(Modifier.weight(1f)) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(who)
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.textDim)); append(" · $role"); pop()
                },
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(line, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (warn) c.orange else c.textDim)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/**
 * Signs in both at once: the password gets Dolphin its sign-in (saved where Dolphin keeps it), the web API
 * key (only asked if Delphis doesn't have it) lets Delphis read progress. Ⓐ signs in, Ⓑ cancels.
 */
@Composable
private fun RaSignInPanel(act: MainActivity, startUser: String, needToken: Boolean, needKey: Boolean, onDone: (io.github.hiramrg.delphis.core.RetroAchievements.Profile?) -> Unit, onCancel: () -> Unit) {
    val c = LocalWii.current
    val prefs = act.prefs
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(startUser) }
    var password by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = !busy && user.isNotBlank() && (!needToken || password.isNotEmpty()) && (!needKey || key.isNotBlank())
    val submit: () -> Unit = submit@{
        if (!ready) return@submit
        if (needToken && io.github.hiramrg.delphis.platform.GameWatch.session != null) { error = "Close your game first: Dolphin has to restart to pick up the sign-in."; return@submit }
        busy = true; error = null
        scope.launch {
            var profile: io.github.hiramrg.delphis.core.RetroAchievements.Profile? = null
            if (needKey) {
                profile = Achievements.verify(user, key)
                if (profile == null) { busy = false; error = "Delphis couldn't connect: check the username and web API key" + (Achievements.lastError?.let { " ($it)" } ?: "") + "."; return@launch }
                prefs.raUser = profile.user; prefs.raKey = key
            }
            if (needToken) {
                val r = Achievements.dolphinToken(user, password)
                val (u, token) = r.getOrElse { busy = false; error = "Dolphin couldn't sign in: ${it.message}."; return@launch }
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { act.bridge.stopIdleDolphin(); delay(300); act.bridge.setDolphinRa(u, token) }
                if (!ok) { busy = false; error = "Couldn't save Dolphin's sign-in. Check Delphis still has access to Dolphin's folder (Settings → Library)."; return@launch }
                if (prefs.raUser.isNullOrBlank()) prefs.raUser = u
            }
            password = ""
            busy = false
            Notices.done(act, "Signed in to RetroAchievements", if (needToken) "Dolphin will earn achievements from your next game." else "Delphis shows your progress again.")
            onDone(profile)
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        PopupPanel(
            "RetroAchievements",
            when { needToken && needKey -> "Sign in Dolphin and Delphis"; needToken -> "Sign in Dolphin"; else -> "Connect Delphis" },
            Modifier.widthIn(max = 480.dp).onPreviewKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_BUTTON_A) { submit(); true } else false
            },
            buttons = {
                PanelBtn("Cancel", "B", false, onClick = onCancel)
                PanelBtn(if (busy) "Signing in…" else "Sign in", "A", true, enabled = ready, onClick = submit)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it.trim(); error = null }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (needToken) OutlinedTextField(
                    password, { password = it; error = null }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                if (needKey) {
                    OutlinedTextField(
                        key, { key = it.trim(); error = null }, label = { Text("Web API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Find it on retroachievements.org → Settings → Keys.", fontSize = 11.5.sp, color = c.textDim)
                } else Text("✓ Web API key already saved · Delphis uses it to show your progress", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textDim)
                if (needToken) Text(
                    "Your password goes straight to RetroAchievements and is never saved. Dolphin gets its own sign-in, the same as signing in inside Dolphin. If Dolphin is open in the background, it's closed first.",
                    fontSize = 11.5.sp, color = c.textDim, lineHeight = 15.sp,
                )
                error?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.orange) }
            }
        }
    }
}
