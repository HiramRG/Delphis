package io.github.hiramrg.delphis.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * The DSU ("cemuhook") protocol, server side, as Dolphin's DualShockUDPClient speaks it
 * (InputCommon/ControllerInterface/DualShockUDPClient/DualShockUDPProto.h, protocol 1001).
 * Little-endian packed structs: a 16-byte header ("DSUS", version, length, CRC32, server id), the
 * message type, then the body. The CRC32 covers the whole message with the CRC field zeroed.
 *
 * Delphis serves one pad (id 0, named after the server entry "Delphis") whose only moving part is
 * touch 1: Dolphin adds up how far it moved between packets (clamped to ±[TOUCH_X_MAX] / ±[TOUCH_Y_MAX])
 * and offers it as the inputs "Touch X-/X+/Y-/Y+", which trackpad profiles put on the Wii pointer.
 */
object Dsu {
    const val PORT = 26761 // not the usual 26760, so it can't clash with another DSU app on the device
    const val SERVER_NAME = "Delphis"
    /** Dolphin's qualifier for Delphis' pad: source / id / server description. */
    const val DEVICE = "DSUClient/0/$SERVER_NAME"
    const val TOUCH_X_MAX = 1000 // Dolphin's TOUCH_X_AXIS_MAX
    const val TOUCH_Y_MAX = 500 // Dolphin's TOUCH_Y_AXIS_MAX

    const val VERSION = 1001
    const val TYPE_VERSION = 0x100000
    const val TYPE_PORTS = 0x100001
    const val TYPE_PAD_DATA = 0x100002

    private val SERVER = byteArrayOf('D'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 'S'.code.toByte())
    private val CLIENT = byteArrayOf('D'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 'C'.code.toByte())

    /** A client message Delphis answers. */
    sealed class Request {
        object Version : Request()
        data class ListPorts(val padIds: List<Int>) : Request()
        data class PadData(val padId: Int?) : Request() // null = all pads
    }

    fun parse(bytes: ByteArray, length: Int): Request? {
        if (length < 20) return null
        val b = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..3) if (bytes[i] != CLIENT[i]) return null
        b.position(4)
        b.short // version
        val msgLen = b.short.toInt() and 0xFFFF
        if (msgLen + 16 > length) return null
        val crc = b.int
        // CRC check: same bytes with the CRC field zeroed.
        val copy = bytes.copyOf(16 + msgLen)
        copy[8] = 0; copy[9] = 0; copy[10] = 0; copy[11] = 0
        if (crc32(copy) != crc) return null
        b.position(16)
        return when (b.int) {
            TYPE_VERSION -> Request.Version
            TYPE_PORTS -> {
                val count = b.int.coerceIn(0, 4)
                Request.ListPorts((0 until count).map { bytes[24 + it].toInt() and 0xFF })
            }
            TYPE_PAD_DATA -> {
                val flags = bytes[20].toInt() and 0xFF
                Request.PadData(if (flags and 0x01 != 0) bytes[21].toInt() and 0xFF else null)
            }
            else -> null
        }
    }

    private fun crc32(bytes: ByteArray): Int = CRC32().apply { update(bytes) }.value.toInt()

    private fun message(size: Int, type: Int, serverId: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.put(SERVER); b.putShort(VERSION.toShort()); b.putShort((size - 16).toShort()); b.putInt(0); b.putInt(serverId)
        b.putInt(type)
        b.body()
        val out = b.array()
        val crc = crc32(out)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putInt(8, crc)
        return out
    }

    fun versionResponse(serverId: Int): ByteArray = message(24, TYPE_VERSION, serverId) { putShort(VERSION.toShort()); putShort(0) }

    /** Pad 0 is connected, the others aren't. */
    fun portInfo(serverId: Int, padId: Int): ByteArray = message(32, TYPE_PORTS, serverId) {
        put(padId.toByte())
        put((if (padId == 0) 2 else 0).toByte()) // state: connected / disconnected
        put((if (padId == 0) 3 else 0).toByte()) // model: generic
        put((if (padId == 0) 1 else 0).toByte()) // connection: USB
        put(ByteArray(6) { if (padId == 0 && it == 5) 1 else 0 }) // MAC
        put((if (padId == 0) 5 else 0).toByte()) // battery: full
        put(0)
    }

    /** Pad 0's state: sticks centred, nothing pressed, touch 1 down at ([touchX], [touchY]) with id [touchId]. */
    fun padData(serverId: Int, counter: Int, touchId: Int, touchX: Int, touchY: Int): ByteArray = message(100, TYPE_PAD_DATA, serverId) {
        put(0); put(2); put(3); put(1) // pad 0, connected, generic, USB
        put(ByteArray(6) { if (it == 5) 1 else 0 })
        put(5) // battery full
        put(1) // active
        putInt(counter)
        put(0); put(0); put(0); put(0) // buttons, PS, touch button
        put(128.toByte()); put(128.toByte()); put(128.toByte()); put(128.toByte()) // sticks centred
        repeat(12) { put(0) } // analog d-pad, face buttons, R1 L1 R2 L2
        put(1); put(touchId.toByte()); putShort(touchX.toShort()); putShort(touchY.toShort()) // touch 1
        put(0); put(0); putShort(0); putShort(0) // touch 2
        putLong(0L) // motion timestamp
        putFloat(0f); putFloat(0f); putFloat(0f) // accelerometer (unused)
        putFloat(0f); putFloat(0f); putFloat(0f) // gyro
    }

    /**
     * Turns pointer positions ([-1, 1]) into touch-1 packets. Dolphin only adds up movement between
     * packets with the same touch id, so its position can drift from Delphis' (e.g. after it re-adds the
     * device). [sync] fixes that exactly: a new touch id (no movement counted), a jump far past the clamp
     * (Dolphin sits at its maximum), then a step back to the wanted position.
     */
    class TouchEncoder {
        private var id = 1
        private var wireX = 0
        private var wireY = 0
        private var lastX = 0
        private var lastY = 0

        /** Touch values (id, x, y) to send so Dolphin ends up at ([x], [y]) in [-1, 1]. */
        fun move(x: Float, y: Float): List<Triple<Int, Int, Int>> {
            val tx = (x.coerceIn(-1f, 1f) * TOUCH_X_MAX).toInt()
            val ty = (y.coerceIn(-1f, 1f) * TOUCH_Y_MAX).toInt()
            if (wireX + (tx - lastX) !in -30000..30000 || wireY + (ty - lastY) !in -30000..30000) return sync(x, y)
            wireX += tx - lastX; wireY += ty - lastY
            lastX = tx; lastY = ty
            return listOf(Triple(id, wireX, wireY))
        }

        fun sync(x: Float, y: Float): List<Triple<Int, Int, Int>> {
            val tx = (x.coerceIn(-1f, 1f) * TOUCH_X_MAX).toInt()
            val ty = (y.coerceIn(-1f, 1f) * TOUCH_Y_MAX).toInt()
            id = if (id >= 250) 1 else id + 1
            val far = 4 * TOUCH_X_MAX
            val out = listOf(
                Triple(id, 0, 0), // new id: Dolphin starts counting again
                Triple(id, far, far), // way past the clamp: Dolphin is now at (TOUCH_X_MAX, TOUCH_Y_MAX)
                Triple(id, far - (TOUCH_X_MAX - tx), far - (TOUCH_Y_MAX - ty)),
            )
            wireX = out.last().second; wireY = out.last().third
            lastX = tx; lastY = ty
            return out
        }

        /** The last touch values, to repeat as a keep-alive (no movement). */
        fun current() = Triple(id, wireX, wireY)
    }
}
