package com.ludobt.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class MqttPacketTest {

    @Test
    fun testVarLengthEncodingAndDecoding() {
        val testValues = listOf(0, 1, 64, 127, 128, 255, 300, 16383, 16384, 65535, 2097151)
        for (v in testValues) {
            val out = ByteArrayOutputStream()
            writeVarLength(out, v)
            val bytes = out.toByteArray()
            val inp = ByteArrayInputStream(bytes)
            val decoded = readVarLength(inp)
            assertEquals("Value $v should match after encode/decode", v, decoded)
        }
    }

    @Test
    fun testConnectPacketFormat() {
        val out = ByteArrayOutputStream()
        val clientId = "test_player_123"
        val clientBytes = clientId.toByteArray(StandardCharsets.UTF_8)
        val varHeader = byteArrayOf(
            0x00, 0x04, 'M'.code.toByte(), 'Q'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(),
            0x04, 0x02, 0x00, 0x3C
        )
        val payload = ByteArrayOutputStream()
        payload.write(clientBytes.size shr 8)
        payload.write(clientBytes.size and 0xFF)
        payload.write(clientBytes)

        val remLen = varHeader.size + payload.size()
        out.write(0x10)
        writeVarLength(out, remLen)
        out.write(varHeader)
        out.write(payload.toByteArray())

        val packet = out.toByteArray()
        assertEquals(0x10.toByte(), packet[0])
        assertTrue(packet.size > 14)
    }

    private fun writeVarLength(out: ByteArrayOutputStream, length: Int) {
        var x = length
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 128
            out.write(encodedByte)
        } while (x > 0)
    }

    private fun readVarLength(input: ByteArrayInputStream): Int {
        var multiplier = 1
        var value = 0
        var digit: Int
        do {
            digit = input.read()
            if (digit == -1) throw java.io.EOFException()
            value += (digit and 127) * multiplier
            multiplier *= 128
        } while ((digit and 128) != 0)
        return value
    }
}
