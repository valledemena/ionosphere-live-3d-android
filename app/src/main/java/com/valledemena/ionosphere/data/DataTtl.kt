package com.valledemena.ionosphere.data

/**
 * TTLs extraidos de `DATA_CONTRACT.md` §3 (verificado contra las `route.ts` al portar).
 * Valores en milisegundos. Todo constante publica para tests/contract.
 */
object DataTtl {
    const val SPACE_WEATHER_MS = 90_000L        // 90 s
    const val AURORA_MS = 5 * 60_000L           // 5 min
    const val DST_MS = 15 * 60_000L             // 15 min
    const val IONOSONDES_DATA_MS = 10 * 60_000L // datos 10 min
    const val IONOSONDES_CATALOG_MS = 24 * 60 * 60_000L // catalogo 24 h
    const val IONOSONDES_EXTRAS_MS = 60 * 60_000L      // extras anclas 60 min
    const val IONOSONDES_ANTI_HERD_MS = 5 * 60_000L    // anti-rebanio 5 min
}
