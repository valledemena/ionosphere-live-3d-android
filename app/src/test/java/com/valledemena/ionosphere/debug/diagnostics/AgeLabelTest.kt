package com.valledemena.ionosphere.debug.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contrato C5 del panel: el formateador de edad es una funcion pura con las
 * fronteras cubiertas (live->cache, stale post-TTL, model age, unavailable).
 */
class AgeLabelTest {

    // ---- live: segundos < 2 min, minutos a partir de ahi ----

    @Test
    fun live_alIstante() {
        assertEquals("live 0s", AgeLabel.live(1_000, 1_000))
    }

    @Test
    fun live_segundos() {
        assertEquals("live 45s", AgeLabel.live(1_000, 1_000 - 45_000))
    }

    @Test
    fun live_frontera_dos_minutos() {
        // 119 s = live 119s; 120 s = live 2m (frontera exacta).
        assertEquals("live 119s", AgeLabel.live(120_000, 1_000))
        assertEquals("live 2m", AgeLabel.live(121_000, 1_000))
    }

    @Test
    fun live_con_edad_en_minutos() {
        // 291 s = 4.85 min -> piso 4m; 300 s exactos -> 5m (frontera).
        assertEquals("live 4m", AgeLabel.live(301_000, 10_000))
        assertEquals("live 5m", AgeLabel.live(310_000, 10_000))
    }

    @Test
    fun live_reloj_retrocedido_no_negativo() {
        // fetchedAt "despues" de now: 0s honesto, nunca "live -5s".
        assertEquals("live 0s", AgeLabel.live(1_000, 61_000))
    }

    // ---- cache: minutos siempre, (+stale) cuando el outcome ya sobrepaso TTL ----

    @Test
    fun cache_fresca_minutos() {
        assertEquals("cache +3m", AgeLabel.cache(3 * 60_000L, false))
    }

    @Test
    fun cache_sub_minuto_redondea_a_cero() {
        assertEquals("cache +0m", AgeLabel.cache(59_999, false))
    }

    @Test
    fun cache_stale_marca_la_condicion_del_DoD() {
        assertEquals("cache +12m (stale)", AgeLabel.cache(12 * 60_000L, true))
    }

    @Test
    fun cache_edad_negativa_no_negativa() {
        assertEquals("cache +0m", AgeLabel.cache(-5_000, false))
    }

    // ---- model: edad en horas (precedencia live > kc2g > cache > model) ----

    @Test
    fun model_edad_en_horas_exactas() {
        assertEquals("model with age 3h", AgeLabel.model(3 * 3_600_000L))
    }

    @Test
    fun model_fraccion_redondea_abajo() {
        // 90 min = 1 h 30 m -> 1h (piso, el DoD marca en horas).
        assertEquals("model with age 1h", AgeLabel.model(90 * 60_000L))
    }

    @Test
    fun model_sin_edad() {
        assertEquals("model with age 0h", AgeLabel.model(0))
    }

    // ---- unavailable: sin dato honesto (nunca vacio disfrazado) ----

    @Test
    fun unavailable_constante_estable() {
        assertEquals("unavailable", AgeLabel.UNAVAILABLE)
    }
}
