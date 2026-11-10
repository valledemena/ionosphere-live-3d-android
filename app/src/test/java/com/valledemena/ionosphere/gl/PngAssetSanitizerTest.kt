package com.valledemena.ionosphere.gl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Slice C / D5: el asset NASA `earth-night.png` no lo decodifica Android.
 *
 * Se comprueba que el filtro conserva intactos los chunks criticos (mismos
 * bytes, mismo CRC), elimina el `zTXt` gigante y deja pasar sin tocar todo lo
 * que no sea un PNG valido.
 */
class PngAssetSanitizerTest {

    private fun chunk(type: String, payload: ByteArray, crc: ByteArray = ByteArray(4)): ByteArray {
        val out = ByteArrayOutputStream()
        val len = payload.size
        out.write(byteArrayOf(
            (len ushr 24).toByte(), (len ushr 16).toByte(),
            (len ushr 8).toByte(), len.toByte(),
        ))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        out.write(crc)
        return out.toByteArray()
    }

    private fun png(vararg chunks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        chunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    @Test
    fun removesTextChunksAndKeepsCriticalOnesByteExact() {
        val ihdr = chunk("IHDR", ByteArray(13))
        val phts = chunk("pHYs", ByteArray(9))
        val ztxt = chunk("zTXt", ByteArray(23_753_168))
        val idat = chunk("IDAT", ByteArray(4) { it.toByte() })
        val iend = chunk("IEND", ByteArray(0))
        val raw = png(ihdr, phts, ztxt, idat, iend)

        val filtered = PngAssetSanitizer.withoutTextChunks(raw)

        val expected = png(ihdr, idat, iend)
        assertArrayEquals(expected, filtered)
        assertEquals(raw.size - phts.size - ztxt.size, filtered.size)
        assertTrue(filtered.size < raw.size)
    }

    @Test
    fun keepsNonPngUntouched() {
        val raw = "no soy un png".toByteArray(Charsets.US_ASCII)
        assertSame(raw, PngAssetSanitizer.withoutTextChunks(raw))
    }

    @Test
    fun keepsTruncatedPngUntouched() {
        val ihdr = chunk("IHDR", ByteArray(13))
        val valid = png(ihdr)
        val truncated = valid.copyOf(valid.size - 1)
        assertContentEquals(truncated, PngAssetSanitizer.withoutTextChunks(truncated))
    }

    @Test
    fun keepsPngWithoutTextChunksByteExact() {
        val ihdr = chunk("IHDR", ByteArray(13))
        val idat = chunk("IDAT", ByteArray(7) { (it * 3).toByte() })
        val iend = chunk("IEND", ByteArray(0))
        val raw = png(ihdr, idat, iend)
        assertArrayEquals(raw, PngAssetSanitizer.withoutTextChunks(raw))
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        assertArrayEquals(expected, actual)
    }
}
