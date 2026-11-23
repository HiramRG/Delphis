package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.platform.Screenshots
import kotlinx.coroutines.launch

/** The camera button beside the one-screen menu's tabs: as tall as them, icon over "Photo", so it reads as a button. */
@Composable
fun CameraChip(onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(14.dp)
    androidx.compose.foundation.layout.Column(
        Modifier.width(56.dp).heightIn(min = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480) 32.dp else 40.dp).focusRing(shape).channel(shape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PhotoCamera, null, Modifier.size(16.dp), tint = c.text)
        if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480) Text("Photo", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, maxLines = 1, lineHeight = 12.sp)
    }
}

/** The game's screenshots, newest first (re-read when [version] changes). */
@Composable
fun rememberScreenshots(title: String?, version: Int): List<Screenshots.Shot> {
    val ctx = LocalContext.current
    val shots by produceState(io.github.hiramrg.delphis.platform.TestHooks.screenshots, title, version) {
        if (io.github.hiramrg.delphis.platform.TestHooks.screenshots.isEmpty())
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Screenshots.list(ctx, title) }
    }
    return shots
}

/**
 * Game page: the game's screenshots. D-pad (or a tap) picks one; A views it in Photos, Y shares it, X deletes it
 * (asks once), B closes.
 */
@Composable
fun ScreenshotsDialog(title: String?, shots: List<Screenshots.Shot>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val c = LocalWii.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keys = !PadGlyphs.touch
    var index by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val current = shots.getOrNull(index.coerceIn(0, (shots.size - 1).coerceAtLeast(0)))
    val cols = 4
    val grid = rememberLazyGridState()
    LaunchedEffect(index) { if (shots.isNotEmpty()) runCatching { grid.animateScrollToItem(index) } }

    fun view() = current?.let { s ->
        runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(s.uri, "image/png").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { status = "No app here can open pictures." }
    }
    fun share() = current?.let { s ->
        runCatching {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png").putExtra(android.content.Intent.EXTRA_STREAM, s.uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(android.content.Intent.createChooser(send, "Share screenshot"))
        }.onFailure { status = "Couldn't open sharing." }
    }
    fun delete() {
        val s = current ?: return
        if (!askDelete) { askDelete = true; return }
        askDelete = false
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Screenshots.delete(ctx, s.uri) }
            status = if (ok) "Deleted." else "Couldn't delete it."
            index = (index - 1).coerceAtLeast(0)
            onChanged()
        }
    }

    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_RIGHT -> { index = (index + 1).coerceAtMost(shots.lastIndex); askDelete = false; true }
                    AndroidKey.KEYCODE_DPAD_LEFT -> { index = (index - 1).coerceAtLeast(0); askDelete = false; true }
                    AndroidKey.KEYCODE_DPAD_DOWN -> { index = (index + cols).coerceAtMost(shots.lastIndex); askDelete = false; true }
                    AndroidKey.KEYCODE_DPAD_UP -> { index = (index - cols).coerceAtLeast(0); askDelete = false; true }
                    AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> { view(); true }
                    AndroidKey.KEYCODE_BUTTON_Y -> { share(); true }
                    AndroidKey.KEYCODE_BUTTON_X -> { delete(); true }
                    AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { if (askDelete) askDelete = false else onDismiss(); true }
                    else -> true
                }
            },
        ) {
            PopupPanel(
                "Screenshots · " + (title ?: "Game"), "${shots.size} screenshot${if (shots.size == 1) "" else "s"}", Modifier.width(520.dp),
                buttons = {
                    PanelBtn("Close", if (keys) "B" else null, false, onClick = onDismiss)
                    PanelBtn(if (askDelete) "Press again to delete" else "Delete", if (keys) "X" else null, false, warn = true, enabled = current != null) { delete() }
                    PanelBtn("Share", if (keys) "Y" else null, false, enabled = current != null) { share() }
                    PanelBtn("View", if (keys) "A" else null, true, enabled = current != null) { view() }
                },
            ) {
                Text(status ?: "In Photos, under Pictures/Delphis/${io.github.hiramrg.delphis.core.ScreenshotNames.folder(title)}.", fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, maxLines = 2)
                LazyVerticalGrid(
                    GridCells.Fixed(cols), Modifier.fillMaxWidth().heightIn(max = 190.dp), state = grid,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shots.size) { i ->
                        val s = shots[i]
                        val img by produceState<ImageBitmap?>(null, s.uri) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Screenshots.thumbnail(ctx, s.uri)?.asImageBitmap() }
                        }
                        val shape = RoundedCornerShape(8.dp)
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).litRing(i == index, shape).clip(shape).background(c.bg).clickable { index = i; askDelete = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                ?: Text("📷", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textDim)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
