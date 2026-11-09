package io.github.hiramrg.delphis.platform

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.hiramrg.delphis.core.Dsu
import io.github.hiramrg.delphis.core.Trackpad
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * The bottom-screen trackpad as a controller Dolphin reads directly: a DSU server on 127.0.0.1
 * ([Dsu.PORT]) serving one pad whose touchpad is the trackpad (see [Dsu]). Dolphin's DSU client
 * (Config/DSUClient.ini, set up by DolphinBridge for trackpad presets) asks for the pad list and pad
 * data every second; trackpad profiles put "Touch X/Y" on the Wii pointer.
 *
 * No touches are relayed to Dolphin's screen, so nothing can be cancelled by Android or set off its
 * gesture navigation. Only reachable from the device itself; nothing is sent until Dolphin asks.
 */
object DsuServer {
    private val serverId = (System.nanoTime() and 0x7fffffff).toInt()
    @Volatile private var socket: DatagramSocket? = null
    private val sender: Handler by lazy { HandlerThread("dsu-send").apply { start() }.let { Handler(it.looper) } }

    // Only touched on the sender thread.
    private var client: SocketAddress? = null
    private var lastRequestAt = 0L
    private var counter = 0
    private val encoder = Dsu.TouchEncoder()
    private var x = 0f
    private var y = 0f
    private var faceButtons = 0

    @Volatile var requests = 0; private set
    @Volatile var packetsSent = 0; private set
    @Volatile var lastError: String? = null; private set

    /** Starts listening (once per process). Safe to call often. */
    @Synchronized
    fun ensureStarted() {
        if (socket != null) return
        val s = runCatching {
            DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), Dsu.PORT)) }
        }.getOrElse { lastError = "couldn't open port ${Dsu.PORT}: ${it.message}"; CrashLog.event("trackpad: $lastError"); return }
        socket = s
        Thread({ receiveLoop(s) }, "dsu-receive").apply { isDaemon = true; start() }
    }

    val running: Boolean get() = socket != null
    val lastSeenMsAgo: Long get() = if (lastRequestAt == 0L) -1 else SystemClock.uptimeMillis() - lastRequestAt

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(128)
        while (true) {
            val p = DatagramPacket(buf, buf.size)
            try { s.receive(p) } catch (e: Exception) { lastError = e.message; break }
            val req = Dsu.parse(buf, p.length) ?: continue
            val from = p.socketAddress
            requests++
            when (req) {
                is Dsu.Request.Version -> send(s, from, Dsu.versionResponse(serverId))
                is Dsu.Request.ListPorts -> req.padIds.forEach { send(s, from, Dsu.portInfo(serverId, it)) }
                is Dsu.Request.PadData -> if (req.padId == null || req.padId == 0) sender.post { onRegister(from) }
            }
        }
        synchronized(this) { socket = null }
    }

    /** Dolphin asked for pad data (it does every second while it reads controllers). */
    private fun onRegister(from: SocketAddress) {
        val now = SystemClock.uptimeMillis()
        val fresh = client != from || now - lastRequestAt > 1500
        client = from; lastRequestAt = now
        // After a pause (a new game, Dolphin restarted…) Dolphin's touch total may be anything: set it exactly.
        sendTouches(if (fresh) encoder.sync(x, y) else listOf(encoder.current()))
    }

    private fun sendTouches(list: List<Triple<Int, Int, Int>>) {
        val s = socket ?: return
        val to = client ?: return
        if (SystemClock.uptimeMillis() - lastRequestAt > 5000) return // Dolphin isn't listening
        for ((id, tx, ty) in list) send(s, to, Dsu.padData(serverId, ++counter, id, tx, ty, faceButtons))
    }

    private fun send(s: DatagramSocket, to: SocketAddress, bytes: ByteArray) {
        runCatching { s.send(DatagramPacket(bytes, bytes.size, to)); packetsSent++ }.onFailure { lastError = it.message }
    }

    // ---- the trackpad (called from the bottom screen)

    /** Finger moved [dxDp]/[dyDp] dp: the pointer moves like a laptop trackpad's (same speed across and down the picture). */
    fun move(dxDp: Float, dyDp: Float, speed: Trackpad.Speed) {
        ensureStarted()
        sender.post {
            val (gx, gy) = Trackpad.normalizedGain(speed)
            x = (x + dxDp * gx).coerceIn(-1f, 1f)
            y = (y + dyDp * gy).coerceIn(-1f, 1f)
            sendTouches(encoder.move(x, y))
        }
    }

    /** Pointer back to the middle of the picture (exactly, whatever Dolphin had). */
    fun recenter() {
        ensureStarted()
        sender.post { x = 0f; y = 0f; CrashLog.event("trackpad: recenter"); sendTouches(encoder.sync(x, y)) }
    }

    /** New game: start in the middle, nothing held. */
    fun reset() {
        ensureStarted()
        sender.post { x = 0f; y = 0f; faceButtons = 0; sendTouches(encoder.sync(x, y)) }
    }

    /** A touch button beside the trackpad went down / up: it holds the pad's face button [index]. */
    fun setTouchButton(index: Int, pressed: Boolean) {
        ensureStarted()
        sender.post {
            val bit = 1 shl index
            faceButtons = if (pressed) faceButtons or bit else faceButtons and bit.inv()
            sendTouches(listOf(encoder.current()))
        }
    }

    /** Lets go of every touch button (leaving the trackpad). */
    fun releaseTouchButtons() {
        sender.post { if (faceButtons != 0) { faceButtons = 0; sendTouches(listOf(encoder.current())) } }
    }

    fun status(): String =
        "trackpad server: " + (if (running) "listening on 127.0.0.1:${Dsu.PORT}" else "off") +
            ", Dolphin last asked " + (lastSeenMsAgo.let { if (it < 0) "never" else "${it / 1000} s ago" }) +
            ", requests $requests, packets sent $packetsSent" + (lastError?.let { ", last error: $it" } ?: "")
}
