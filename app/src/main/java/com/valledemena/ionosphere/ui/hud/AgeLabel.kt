package com.valledemena.ionosphere.ui.hud

/**
 * Formateador puro de edad del dato (deterministico, sin estado, sin Android).
 *
 * Politica de strings = la exacta del DoD R2 (`IMPLEMENTATION_SLICES.md:18`)
 * y del panel (contrato C4-C5):
 *  - "live Ns"        dato recien llegado (la prueba viva marca la senal)
 *  - "live Nm"        live con edad, entre 2 min y su TTL
 *  - "cache +Nm"      Outcome.Cached con cache vigente
 *  - "cache +Nm (stale)" cache vencida (post-TTL)
 *  - "model with age Nh"  fallback de modelo (precedencia live > kc2g >
 *    cache > model, `DATA_CONTRACT.md` s4); los adapters R2 no emiten model
 *    todavia (kc2g reserved): el branch esta listo y testeado.
 *  - "unavailable"    sin dato honesto (nunca vacio disfrazado de bueno).
 */
object AgeLabel {
    const val UNAVAILABLE = "unavailable"

    fun live(nowMs: Long, fetchedAtMs: Long): String {
        val s = ((nowMs - fetchedAtMs) / 1000L).coerceAtLeast(0L)
        return if (s < 120L) "live ${s}s" else "live ${s / 60L}m"
    }

    fun cache(ageMs: Long, stale: Boolean): String {
        val m = (ageMs / 60_000L).coerceAtLeast(0L)
        return "cache +${m}m" + if (stale) " (stale)" else ""
    }

    fun model(ageMs: Long): String {
        val h = (ageMs / 3_600_000L).coerceAtLeast(0L)
        return "model with age ${h}h"
    }
}
