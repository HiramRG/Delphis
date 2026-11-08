package io.github.hiramrg.delphis.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.app.NotificationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.StatusLevels

/**
 * The status icons Android's own bar would show (Do Not Disturb, Bluetooth audio, Wi-Fi, battery),
 * for Home's bottom bar now that the top screen is full screen. Everything is event-driven
 * (broadcasts / callbacks, no polling) and only registered while the bar is on screen.
 */
@Composable
fun SystemStatusIcons(modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val ctx = LocalContext.current
    val battery = rememberBattery(ctx)
    val wifi = rememberWifiBars(ctx)
    val dnd = rememberDnd(ctx)
    val bt = rememberBluetoothAudio(ctx)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (dnd) Icon(Icons.Filled.DoNotDisturbOn, "Do Not Disturb is on", tint = c.text, modifier = Modifier.size(16.dp))
        if (bt) Icon(Icons.Filled.Bluetooth, "Bluetooth audio connected", tint = c.text, modifier = Modifier.size(16.dp))
        WifiIcon(wifi, c.text)
        val (level, charging) = battery
        if (level >= 0) {
            val tint = if (StatusLevels.batteryLow(level, charging)) c.orange else c.text
            BarBatteryIcon(level, charging, tint, c.bar)
            Text("$level%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = tint)
        }
    }
}

/** Wi-Fi fan: faded full fan behind, solid fan up to the signal level; crossed out when [bars] is null. */
@Composable
private fun WifiIcon(bars: Int?, tint: Color) {
    Canvas(Modifier.size(width = 18.dp, height = 15.dp)) {
        val center = Offset(size.width / 2f, size.height * 0.98f)
        val r = size.height * 0.95f
        fun fan(radius: Float, alpha: Float) = drawArc(
            tint.copy(alpha = alpha), startAngle = 225f, sweepAngle = 90f, useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
        )
        fan(r, 0.3f)
        if (bars == null) {
            drawLine(tint, Offset(size.width * 0.12f, size.height * 0.08f), Offset(size.width * 0.88f, size.height * 0.92f), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        } else if (bars > 0) fan(r * (0.35f + 0.65f * bars / 4f), 1f)
    }
}

/** Battery outline filling with the level; green bolt while charging. */
@Composable
private fun BarBatteryIcon(level: Int, charging: Boolean, tint: Color, behind: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val sw = 2.dp.toPx()
        val bodyW = size.width - 4.dp.toPx()
        drawRoundRect(tint, topLeft = Offset(sw / 2, sw / 2), size = Size(bodyW - sw, size.height - sw), cornerRadius = CornerRadius(3.5.dp.toPx()), style = Stroke(sw))
        drawRoundRect(tint, topLeft = Offset(bodyW + 0.5.dp.toPx(), size.height * 0.3f), size = Size(2.5.dp.toPx(), size.height * 0.4f), cornerRadius = CornerRadius(1.dp.toPx()))
        val inset = 3.dp.toPx()
        val fullW = bodyW - 2 * inset
        drawRoundRect(tint, topLeft = Offset(inset, inset), size = Size(fullW * (level.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f), size.height - 2 * inset), cornerRadius = CornerRadius(1.5.dp.toPx()))
        if (charging) {
            val w = bodyW; val h = size.height
            val bolt = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.56f, h * 0.06f); lineTo(w * 0.34f, h * 0.55f); lineTo(w * 0.5f, h * 0.55f)
                lineTo(w * 0.44f, h * 0.94f); lineTo(w * 0.68f, h * 0.45f); lineTo(w * 0.52f, h * 0.45f); close()
            }
            drawPath(bolt, behind, style = Stroke(1.5.dp.toPx()))
            drawPath(bolt, Glow.Green)
        }
    }
}

@Composable
private fun rememberBattery(ctx: Context): Pair<Int, Boolean> {
    var state by remember { mutableStateOf(-1 to false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                if (l >= 0 && scale > 0) state = (l * 100 / scale) to charging
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return state
}

/** 0..4 bars while the default network is Wi-Fi, null when there's no Wi-Fi connection. */
@Composable
private fun rememberWifiBars(ctx: Context): Int? {
    var bars by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        fun read(caps: NetworkCapabilities?): Int? =
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) StatusLevels.wifiBars(caps.signalStrength) else null
        bars = runCatching { read(cm?.getNetworkCapabilities(cm.activeNetwork)) }.getOrNull()
        val main = Handler(Looper.getMainLooper())
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { val b = read(caps); main.post { bars = b } }
            override fun onLost(network: Network) { main.post { bars = null } }
        }
        val ok = runCatching { cm?.registerDefaultNetworkCallback(cb) }.isSuccess
        onDispose { if (ok) runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    return bars
}

@Composable
private fun rememberDnd(ctx: Context): Boolean {
    val nm = remember { ctx.getSystemService(NotificationManager::class.java) }
    var on by remember { mutableStateOf(StatusLevels.dndOn(nm?.currentInterruptionFilter ?: 0)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) { on = StatusLevels.dndOn(nm?.currentInterruptionFilter ?: 0) }
        }
        ctx.registerReceiver(receiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        on = StatusLevels.dndOn(nm?.currentInterruptionFilter ?: 0)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return on
}

/** Bluetooth headphones/speakers connected (no Bluetooth permission needed: read from the audio outputs). */
@Composable
private fun rememberBluetoothAudio(ctx: Context): Boolean {
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    fun check(): Boolean = runCatching {
        am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
    }.getOrDefault(false)
    var on by remember { mutableStateOf(check()) }
    DisposableEffect(Unit) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { on = check() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { on = check() }
        }
        am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(cb) }
    }
    return on
}
