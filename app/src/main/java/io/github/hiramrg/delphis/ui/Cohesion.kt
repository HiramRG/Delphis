package io.github.hiramrg.delphis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/*
 * The shared pieces every screen picks from (one look across the app):
 *  - three text sizes: page title 20, row/card title 14-15, body 12 (captions 10.5 caps, never below 10.5)
 *  - ring = where the D-pad is, lit bar = the page you're on, tint + ✓ = turned on / in use
 *  - card 18 radius, pop-up 20 radius with a blue edge, chips 30 dp, buttons 40 dp with the button icon
 */

/** Small caps label above a group ("GAMECUBE SCREEN", "SETTINGS"). */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalWii.current.textDim) {
    Text(text.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = color, modifier = modifier, maxLines = 2)
}

/** "◀ Settings Ⓑ" + caps label + page title + actions on the right. Used by every page below Home. */
@Composable
fun PageHeader(back: String, onBack: () -> Unit, caps: String?, title: String, modifier: Modifier = Modifier, backKey: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalWii.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackLink(back, backKey, onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (caps != null) CapsLabel(caps)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 22.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** "◀ Home Ⓑ" link (Ⓑ does the same). */
@Composable
fun BackLink(label: String, showKey: Boolean = true, onClick: () -> Unit) {
    val c = LocalWii.current
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).focusProperties { canFocus = false }.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("◀ $label", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, maxLines = 1)
        if (showKey) KeyIcon("B", c.textDim)
    }
}

/** A grouped card of settings rows (rows are separated by thin lines). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(horizontal = 8.dp, vertical = 6.dp), content = content)
}

/** Thin divider between rows in a [GroupCard]. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalWii.current.cardBorder.copy(alpha = 0.45f)))
}

/** Title + grey subtitle on the left, a control on the right (toggle, segmented switch, button). */
@Composable
fun SettingRow(title: String, sub: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalWii.current
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.rememberFocus("row-$title").focusRing(RoundedCornerShape(12.dp), fill = true).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

/** The themed on/off toggle (replaces Android's switch). */
@Composable
fun ThemedToggle(on: Boolean, modifier: Modifier = Modifier, onChange: ((Boolean) -> Unit)? = null) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier.size(width = 40.dp, height = 24.dp).clip(shape)
            .background(if (on) c.accent else c.cardBorder.copy(alpha = 0.7f))
            .then(if (onChange != null) Modifier.clickable { onChange(!on) } else Modifier)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(18.dp).clip(shape).background(if (on) c.onAccent else c.textDim)) }
}

/** A settings row whose whole width toggles (Ⓐ on the ring works too). */
@Composable
fun ToggleRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, sub, onClick = { onChange(!on) }) { ThemedToggle(on) }
}

/** Segmented switch ("4:3 | 16:9", "Thor | List", "Slow | Normal | Fast"). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(modifier.height(height).clip(shape).background(if (c.dark) Color(0xFF15181C) else c.bar).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val group = options.joinToString("|") { it.second }
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.fillMaxHeight().rememberFocus("seg-$group-$label").focusRing(shape, width = 2.5.dp).clip(shape).background(if (on) c.accent else Color.Transparent)
                    .clickable { onSelect(v) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = if (on) c.onAccent else c.textDim, maxLines = 1) }
        }
    }
}

/** 40 dp button with its button icon: "Restore" + Ⓐ. [key] null = no icon. */
@Composable
fun PanelBtn(label: String, key: String?, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = ButtonShape
    Row(
        modifier.height(40.dp).rememberFocus("btn-$label").focusRing(shape).channel(shape, primary)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val fg = channelText(primary, enabled)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1, onTextLayout = { TextFit.check(label, it) })
        if (key != null) KeyIcon(key, if (primary) fg.copy(alpha = 0.75f) else c.textDim)
    }
}

/**
 * The one pop-up panel: caps label, title, content, buttons bottom-right (or [corner] top-right).
 * 20 radius, blue edge, soft shadow. Put it inside a Dialog or an overlay Box.
 */
@Composable
fun PopupPanel(
    caps: String?, title: String, modifier: Modifier = Modifier,
    corner: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.shadow(18.dp, shape).clip(shape).background(if (c.dark) Color(0xFF22272D) else c.card)
            .border(2.dp, c.accent, shape).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (caps != null) CapsLabel(caps)
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 21.sp)
            }
            if (corner != null) { Spacer(Modifier.width(10.dp)); corner() }
        }
        content()
        if (buttons != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = buttons)
    }
}

/** The same confirm panel for anything that deletes or resets: Ⓐ Yes / Ⓑ Cancel. Keys are handled by the caller. */
@Composable
fun ConfirmPanel(caps: String?, title: String, sub: String?, confirm: String, modifier: Modifier = Modifier, cancel: String = "Cancel", onCancel: () -> Unit, onConfirm: () -> Unit) {
    PopupPanel(caps, title, modifier.widthIn(max = 440.dp), buttons = {
        PanelBtn(cancel, "B", false, onClick = onCancel)
        PanelBtn(confirm, "A", true, onClick = onConfirm)
    }) { if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.5.sp, color = LocalWii.current.textDim, lineHeight = 16.sp) }
}

/** Dimmed backdrop + a centred pop-up panel, drawn inside the current window (no dialog window). */
@Composable
fun OverlayPanel(onOutside: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onOutside), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(16.dp).clickable(enabled = false) {}, content = content)
    }
}

/** A "Don't ask again" row with the themed toggle. */
@Composable
fun DontAskRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemedToggle(on)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LocalWii.current.text)
    }
}

/** [ConfirmPanel] in its own dialog window, with Ⓐ / Ⓑ wired (for screens without their own key handling). */
@Composable
fun ConfirmDialog(caps: String?, title: String, sub: String?, confirm: String, cancel: String = "Cancel", onCancel: () -> Unit, onConfirm: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        ConfirmPanel(
            caps, title, sub, confirm,
            androidx.compose.ui.Modifier.width(440.dp).then(
                androidx.compose.ui.Modifier.focusRequesterCompat(focus)
            ).onPreviewKeyEventCompat { code ->
                when (code) {
                    android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> { onConfirm(); true }
                    android.view.KeyEvent.KEYCODE_BUTTON_B, android.view.KeyEvent.KEYCODE_BACK -> { onCancel(); true }
                    else -> false
                }
            },
            cancel = cancel, onCancel = onCancel, onConfirm = onConfirm,
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private fun Modifier.focusRequesterCompat(f: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(androidx.compose.ui.Modifier.focusRequester(f)).then(androidx.compose.ui.Modifier.focusable())

private fun Modifier.onPreviewKeyEventCompat(onDown: (Int) -> Boolean): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false else onDown(e.key.nativeKeyCode)
}

/** "Nothing here yet" card: icon in a ring, title, one sentence, one or two buttons. */
@Composable
fun EmptyState(icon: String, title: String, sub: String, modifier: Modifier = Modifier, buttons: @Composable RowScope.() -> Unit = {}) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(50)).background(c.accentSoft).border(2.dp, c.accent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 24.sp, color = c.accentText)
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.text, textAlign = TextAlign.Center)
        Text(sub, fontSize = 12.5.sp, color = c.textDim, textAlign = TextAlign.Center, lineHeight = 16.sp, modifier = Modifier.widthIn(max = 420.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = buttons)
    }
}

/**
 * The bottom bar every screen shares: clock in the middle, what Ⓐ/Ⓑ/Ⓨ do on the right, status icons.
 * [hints] uses the glyph form, e.g. "✚ Steps  Ⓐ Choose  Ⓑ Back".
 */
@Composable
fun AppBottomBar(hints: String?, modifier: Modifier = Modifier, leading: @Composable RowScope.() -> Unit = {}) {
    WiiBar(
        modifier = modifier,
        left = leading,
        right = {
            if (hints != null) HintText(hints, Modifier.weight(1f, fill = false), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
            SystemStatusIcons(Modifier.padding(start = 10.dp))
        },
    )
}

// ============================================================================ notices

/**
 * Delphis notices replace Android's grey toasts: a card drops in at the top for a few seconds.
 * Green = done, blue = info, orange = a problem. An action is offered with a button (Ⓨ presses it).
 * When no Delphis screen is showing (a game is running), it falls back to a normal toast.
 */
object Notices {
    enum class Kind { DONE, INFO, PROBLEM }
    class Notice(val kind: Kind, val title: String, val sub: String?, val action: String?, val onAction: (() -> Unit)?, val id: Long = System.nanoTime())

    var current by mutableStateOf<Notice?>(null)
        private set
    /** How many NoticeHosts are on screen and resumed. */
    var hosts = 0

    fun show(context: android.content.Context, kind: Kind, title: String, sub: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
        val post = Runnable {
            if (hosts > 0) current = Notice(kind, title, sub, action, onAction)
            else android.widget.Toast.makeText(context.applicationContext, listOfNotNull(title, sub).joinToString(". "), android.widget.Toast.LENGTH_LONG).show()
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) post.run()
        else android.os.Handler(android.os.Looper.getMainLooper()).post(post)
    }
    fun done(context: android.content.Context, title: String, sub: String? = null) = show(context, Kind.DONE, title, sub)
    fun info(context: android.content.Context, title: String, sub: String? = null, action: String? = null, onAction: (() -> Unit)? = null) = show(context, Kind.INFO, title, sub, action, onAction)
    fun problem(context: android.content.Context, title: String, sub: String? = null, action: String? = null, onAction: (() -> Unit)? = null) = show(context, Kind.PROBLEM, title, sub, action, onAction)

    fun dismiss() { current = null }
    /** Ⓨ while a notice with an action is showing: runs it. Returns true if handled. */
    fun pressAction(): Boolean {
        val n = current ?: return false
        val a = n.onAction ?: return false
        current = null; a(); return true
    }
}

private val NoticeGreen = Color(0xFF4CD07D)
private val NoticeOrange = Color(0xFFFF8A5A)

/** Shows [Notices.current] at the top of the screen. Put once at the top level of each activity. */
@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    // Counts only while the screen is in front; otherwise notices fall back to a toast.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var counted = false
        fun set(on: Boolean) { if (on != counted) { counted = on; if (on) Notices.hosts++ else Notices.hosts-- } }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) set(true)
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) set(false)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); set(false) }
    }
    val n = Notices.current
    LaunchedEffect(n?.id) {
        if (n != null) { delay(if (n.onAction != null) 7000 else 3500); if (Notices.current?.id == n.id) Notices.dismiss() }
    }
    var shown by remember { mutableStateOf<Notices.Notice?>(null) }
    if (n != null) shown = n
    Box(modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = n != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            shown?.let { NoticeCard(it) }
        }
    }
}

@Composable
private fun NoticeCard(n: Notices.Notice) {
    val c = LocalWii.current
    val col = when (n.kind) { Notices.Kind.DONE -> NoticeGreen; Notices.Kind.INFO -> c.accent; Notices.Kind.PROBLEM -> NoticeOrange }
    val icon = when (n.kind) { Notices.Kind.DONE -> "✓"; Notices.Kind.INFO -> "i"; Notices.Kind.PROBLEM -> "!" }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.widthIn(min = 300.dp, max = 460.dp).shadow(14.dp, shape).clip(shape).background(if (c.dark) Color(0xFF22272D) else c.card)
            .border(2.dp, col, shape).clickable { Notices.dismiss() }.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(col), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A2533))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
            if (!n.sub.isNullOrBlank()) Text(n.sub, fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Clip)
        }
        if (n.action != null && n.onAction != null) {
            val s = ChipShape
            Row(
                Modifier.height(32.dp).channel(s)
                    .clickable { Notices.pressAction() }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(n.action, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.btnText, maxLines = 1, onTextLayout = { TextFit.check(n.action, it) })
                KeyIcon("Y", c.textDim)
            }
        }
    }
}

/** The in-game top row's chip (Home, ▭ 4:3, ☀): same size as the 🏆 chip, lit while its panel is open. */
@Composable
fun TopChip(text: String, on: Boolean = false, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = ChipShape
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        color = if (on) c.accentText else c.text,
        onTextLayout = { TextFit.check(text, it) },
        modifier = Modifier.channel(shape, lit = on)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** "◀ Home Ⓑ" (or "◀ Back Ⓑ") as a pill in the bottom bar (game page). */
@Composable
fun HomeBarPill(label: String = "Home", onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = ChipShape
    Row(
        Modifier.height(34.dp).channel(shape)
            .focusProperties { canFocus = false }.clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("◀ $label", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.btnPrimaryText, maxLines = 1, onTextLayout = { TextFit.check(label, it) })
        KeyIcon("B", c.textDim, 16.dp)
    }
}

/** A title on its own line: still when it fits, otherwise it scrolls slowly inside its margins with faded edges. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScrollingTitle(title: String, fontSize: androidx.compose.ui.unit.TextUnit = 15.5.sp) {
    val c = LocalWii.current
    val style = androidx.compose.ui.text.TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold, color = c.text)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth
        val fits = remember(title, maxW) { measurer.measure(title, style, maxLines = 1).size.width <= maxW }
        if (fits) Text(title, style = style, maxLines = 1, textAlign = TextAlign.Center)
        else Text(
            title, style = style, maxLines = 1, softWrap = false,
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 14.dp.toPx() / size.width
                    drawRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                }
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1500, velocity = 36.dp),
        )
    }
}


/**
 * Fit check: buttons, chips and hints report a label that didn't fit its space (it would be cut off).
 * Diagnostics lists them, so a tight spot shows up on the first test instead of going unnoticed.
 */
object TextFit {
    val misses = androidx.compose.runtime.mutableStateListOf<String>()
    fun check(label: String, layout: androidx.compose.ui.text.TextLayoutResult) {
        if (layout.hasVisualOverflow && label !in misses) {
            misses.add(label)
            android.util.Log.w("DelphisFit", "Label didn't fit: \"$label\" (${layout.size.width}px, needs more)")
        }
    }
}
