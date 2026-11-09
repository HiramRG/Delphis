package io.github.hiramrg.delphis.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DsuTest {
    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    private fun checkCrc(b: ByteArray) {
        val copy = b.copyOf(); copy[8] = 0; copy[9] = 0; copy[10] = 0; copy[11] = 0
        assertEquals(CRC32().apply { update(copy) }.value.toInt(), le(b).getInt(8))
    }

    private fun clientMessage(type: Int, body: ByteBuffer.() -> Unit, size: Int): ByteArray {
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("DSUC".toByteArray()); b.putShort(1001); b.putShort((size - 16).toShort()); b.putInt(0); b.putInt(1234)
        b.putInt(type); b.body()
        val out = b.array()
        le(out).putInt(8, CRC32().apply { update(out) }.value.toInt())
        return out
    }

    @Test
    fun serverMessagesHaveDolphinsSizesAndValidCrc() {
        val pad = Dsu.padData(7, 1, 1, 10, -20)
        assertEquals(100, pad.size) // sizeof(PadDataResponse)
        assertEquals(84, le(pad).getShort(6).toInt()) // message length = size - header
        assertEquals("DSUS", String(pad, 0, 4))
        assertEquals(Dsu.TYPE_PAD_DATA, le(pad).getInt(16))
        checkCrc(pad)
        // touch 1 at offset 56: active, id, x, y
        assertEquals(1, pad[56].toInt()); assertEquals(1, pad[57].toInt())
        assertEquals(10, le(pad).getShort(58).toInt()); assertEquals(-20, le(pad).getShort(60).toInt())
        val port = Dsu.portInfo(7, 0)
        assertEquals(32, port.size); checkCrc(port)
        assertEquals(2, port[21].toInt()) // connected
        assertEquals(0, Dsu.portInfo(7, 2)[21].toInt())
        assertEquals(24, Dsu.versionResponse(7).size)
    }

    @Test
    fun parsesDolphinsRequests() {
        val ports = clientMessage(Dsu.TYPE_PORTS, { putInt(4); put(byteArrayOf(0, 1, 2, 3)) }, 28)
        assertEquals(Dsu.Request.ListPorts(listOf(0, 1, 2, 3)), Dsu.parse(ports, ports.size))
        val data = clientMessage(Dsu.TYPE_PAD_DATA, { put(1); put(0); put(ByteArray(6)) }, 28)
        assertEquals(Dsu.Request.PadData(0), Dsu.parse(data, data.size))
        val bad = data.copyOf(); bad[25] = 9
        assertEquals(null, Dsu.parse(bad, bad.size))
    }

    /** Dolphin's side: adds up movement between packets with the same id, clamped. */
    private class DolphinTouch {
        var x = 0; var y = 0; var prevId = -1; var prevX = 0; var prevY = 0
        fun feed(p: Triple<Int, Int, Int>) {
            if (p.first == prevId) {
                x = (x + p.second - prevX).coerceIn(-Dsu.TOUCH_X_MAX, Dsu.TOUCH_X_MAX)
                y = (y + p.third - prevY).coerceIn(-Dsu.TOUCH_Y_MAX, Dsu.TOUCH_Y_MAX)
            }
            prevId = p.first; prevX = p.second; prevY = p.third
        }
    }

    @Test
    fun dolphinFollowsThePointerAndSyncFixesAnyDrift() {
        val enc = Dsu.TouchEncoder()
        val d = DolphinTouch()
        enc.move(0f, 0f).forEach(d::feed)
        enc.move(0.5f, -0.4f).forEach(d::feed)
        assertEquals(500, d.x); assertEquals(-200, d.y)
        enc.move(1f, 1f).forEach(d::feed)
        assertEquals(1000, d.x); assertEquals(500, d.y)
        // Dolphin re-adds the device: it forgets everything.
        d.x = 0; d.y = 0; d.prevId = -1
        enc.move(0.2f, 0.2f).forEach(d::feed)
        assertTrue(d.x != 200) // drifted
        enc.sync(0.2f, 0.2f).forEach(d::feed)
        assertEquals(200, d.x); assertEquals(100, d.y)
        enc.sync(0f, 0f).forEach(d::feed)
        assertEquals(0, d.x); assertEquals(0, d.y)
    }
}
