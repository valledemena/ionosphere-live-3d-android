package com.valledemena.ionosphere.gl

/**
 * D5: filtro de chunks PNG antes de decodificar.
 *
 * `earth-night.png` de NASA (asset byte-exacto del APK) trae un chunk `zTXt`
 * de 23.7 MB (el 85% del fichero) que el decoder de Android rechaza: la
 * textura caia al placeholder sin avisar. El asset del APK no se toca; solo se
 * descartan en memoria los chunks auxiliares de texto/metadatos y se conservan
 * los criticos (IHDR, PLTE, tRNS, IDAT, IEND) con sus CRC intactos.
 */
internal object PngAssetSanitizer {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private val KEPT = setOf("IHDR", "PLTE", "tRNS", "IDAT", "IEND")

    /** Devuelve los bytes originales si no es un PNG o si el recorrido falla. */
    fun withoutTextChunks(raw: ByteArray): ByteArray {
        if (raw.size < 8) return raw
        for (i in SIGNATURE.indices) if (raw[i] != SIGNATURE[i]) return raw
        val kept = java.io.ByteArrayOutputStream(raw.size)
        kept.write(raw, 0, 8)
        var off = 8
        while (off + 12 <= raw.size) {
            val len = ((raw[off].toInt() and 0xFF) shl 24) or
                ((raw[off + 1].toInt() and 0xFF) shl 16) or
                ((raw[off + 2].toInt() and 0xFF) shl 8) or
                (raw[off + 3].toInt() and 0xFF)
            if (len < 0 || off + 12 + len > raw.size) return raw
            val type = String(raw, off + 4, 4, Charsets.US_ASCII)
            if (type in KEPT) kept.write(raw, off, 12 + len)
            off += 12 + len
            if (type == "IEND") break
        }
        val filtered = kept.toByteArray()
        return if (filtered.size > 8) filtered else raw
    }
}
