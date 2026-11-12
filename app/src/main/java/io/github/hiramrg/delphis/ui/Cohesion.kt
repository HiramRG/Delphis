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
        BackLink(back, onBack, backKey)
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
fun BackLink(label: String, onClick: () -> Unit, showKey: Boolean = true) {
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
    Column(modifier.fillMaxWidth().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(horizontal = 14.dp, vertical = 4.dp), content = content)
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
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.focusRing(RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp, horizontal = 2.dp),
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
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.fillMaxHeight().clip(shape).background(if (on) c.accent else Color.Transparent)
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
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(40.dp).clip(shape).background(if (primary) c.accent else c.card)
            .border(2.dp, if (primary) c.accent else c.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val fg = (if (primary) c.onAccent else c.text).copy(alpha = if (enabled) 1f else 0.4f)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
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
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth().height(48.dp)
            .clip(shape).background(c.bar).border(2.dp, c.barBorder, shape).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(rememberClock(withDate = true), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (hints != null) HintText(hints, fontWeight = FontWeight.ExtraBold)
        Box(Modifier.padding(start = 12.dp).width(2.dp).height(26.dp).background(c.barBorder))
        SystemStatusIcons(Modifier.padding(start = 10.dp))
    }
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
    DisposableEffect(Unit) { Notices.hosts++; onDispose { Notices.hosts-- } }
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
            val s = RoundedCornerShape(50)
            Row(
                Modifier.height(32.dp).clip(s).background(c.card).border(2.dp, c.cardBorder, s)
                    .clickable { Notices.pressAction() }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(n.action, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
                KeyIcon("Y", c.textDim)
            }
        }
    }
}
