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
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(LocalWii.current.card).border(2.dp, LocalWii.current.cardBorder, shape).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, tint = LocalWii.current.accentText)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Delphis ${act.updater.installedVersionName()}", fontWeight = FontWeight.SemiBold)
                Text(statusText(act.updateState), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val s = act.updateState) {
            is UpdateState.Downloading -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            is UpdateState.Checking, is UpdateState.Installing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            else -> Unit
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            when (val s = act.updateState) {
                is UpdateState.Available -> PrimaryGlowButton("Download", Icons.Filled.Download, onClick = { act.installUpdate(s.release) }, fontSize = 14.sp)
                is UpdateState.Ready -> PrimaryGlowButton("Restart & update", Icons.Filled.SystemUpdate, onClick = { act.restartAndUpdate() }, fontSize = 14.sp)
                is UpdateState.NeedsPermission -> SecondaryGlowButton("Allow & continue", Icons.Filled.Download, onClick = { act.updater.openInstallPermissionSettings() }, fontSize = 14.sp)
                is UpdateState.Downloading, UpdateState.Checking, UpdateState.Installing -> Unit
                else -> SecondaryGlowButton("Check for updates", Icons.Filled.SystemUpdate, onClick = { act.checkForUpdates() }, fontSize = 14.sp)
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
        if (s is UpdateState.Available) TextButton(onClick = { act.installUpdate(s.release) }) { Text("Download") }
        if (s is UpdateState.Ready) TextButton(onClick = { act.restartAndUpdate() }) { Text("↻ Restart & update") }
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
    val c = LocalWii.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 16.dp).widthIn(max = 500.dp).fillMaxWidth(0.72f)
            .clip(shape).background(c.card).border(2.dp, c.accent, shape).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Black, color = c.green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Build $build is ready", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Delphis closes, updates and opens again by itself (about 10 seconds).", fontSize = 11.5.sp, color = c.textDim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            UpdateButton("Later", "B", primary = false, onClick = onLater)
            UpdateButton("↻ Restart & update", "A", primary = true, onClick = onRestart)
        }
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
