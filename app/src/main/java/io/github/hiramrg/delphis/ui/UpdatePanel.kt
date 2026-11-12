package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.ui.MainActivity.UpdateState

/** "Updates" block for Settings: current version, check button, download progress, install. */
@Composable
fun UpdatePanel(act: MainActivity) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(LocalWii.current.card).border(2.dp, LocalWii.current.cardBorder, shape).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, tint = LocalWii.current.accentText)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Delphis ${act.updater.installedVersionName()}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text(statusText(act.updateState), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val s = act.updateState) {
            is UpdateState.Downloading -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(50)), color = LocalWii.current.accent, trackColor = LocalWii.current.cardBorder)
            is UpdateState.Checking, is UpdateState.Installing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            else -> Unit
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            when (val s = act.updateState) {
                is UpdateState.Available -> PanelBtn("Download", "A", true) { act.installUpdate(s.release) }
                is UpdateState.Ready -> PanelBtn("Restart & update", "A", true) { act.restartAndUpdate() }
                is UpdateState.NeedsPermission -> PanelBtn("Allow & continue", "A", true) { act.updater.openInstallPermissionSettings() }
                is UpdateState.Downloading, UpdateState.Checking, UpdateState.Installing -> Unit
                else -> PanelBtn("Check for updates", "A", false) { act.checkForUpdates() }
            }
        }
    }
}

/** Compact banner shown at the top of the game list when a newer version exists. */
@Composable
fun UpdateBanner(act: MainActivity) {
    val s = act.updateState
    if (s !is UpdateState.Available && s !is UpdateState.Downloading && s !is UpdateState.Installing && s !is UpdateState.Ready) return
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(shape).background(LocalWii.current.accentSoft)
            .border(2.dp, LocalWii.current.accent, shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SystemUpdate, null, tint = LocalWii.current.accentText)
        Spacer(Modifier.width(8.dp))
        Text(statusText(s), fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (s is UpdateState.Available) PanelBtn("Download", null, true) { act.installUpdate(s.release) }
        if (s is UpdateState.Ready) PanelBtn("Restart & update", null, true) { act.restartAndUpdate() }
    }
}

private fun statusText(s: UpdateState): String = when (s) {
    UpdateState.Idle -> "Tap \"Check for updates\" to look for a newer version."
    UpdateState.Checking -> "Checking GitHub for a newer version…"
    is UpdateState.UpToDate -> "You have the latest version."
    is UpdateState.Available -> "A new version (build ${s.release.versionCode}) is available."
    is UpdateState.Downloading -> "Downloading update… ${(s.progress * 100).toInt()}% · keep playing, Delphis tells you when it's ready"
    is UpdateState.Ready -> "Build ${s.release.versionCode} is ready to install."
    UpdateState.NeedsPermission -> "Allow Delphis to install apps (one-time), then come back here."
    UpdateState.Installing -> "Updating… Delphis closes and opens again by itself."
    is UpdateState.Failed -> "Couldn't update: ${s.reason}"
}


/** "Build N is ready": Restart & update (Ⓐ) closes Delphis, installs and reopens it; Later (Ⓑ) leaves the banner. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.UpdateReadyCard(build: Int, onLater: () -> Unit, onRestart: () -> Unit) {
    PopupPanel(
        "Update · build $build", "Ready to install",
        Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 16.dp).widthIn(max = 500.dp).fillMaxWidth(0.72f),
        buttons = {
            PanelBtn("Later", "B", false, onClick = onLater)
            PanelBtn("Restart & update", "A", true, onClick = onRestart)
        },
    ) {
        Text("Delphis closes, updates and opens again by itself (about 10 seconds).", fontSize = 12.sp, color = LocalWii.current.textDim)
    }
}

@Composable
private fun UpdateButton(label: String, key: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).clip(shape).background(if (primary) c.accent else c.card).border(2.dp, if (primary) c.accent else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (primary) androidx.compose.ui.graphics.Color(0xFF0A2533) else c.text, maxLines = 1)
        KeyBadge(key, dark = primary)
    }
}
