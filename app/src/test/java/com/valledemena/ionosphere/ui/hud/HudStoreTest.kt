package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.data.model.DstData
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.SpaceWeatherData
import com.valledemena.ionosphere.data.model.SpaceWeatherForecast
import com.valledemena.ionosphere.data.model.SpaceWeatherF107
import com.valledemena.ionosphere.data.model.SpaceWeatherKp
import com.valledemena.ionosphere.data.model.SpaceWeatherMag
import com.valledemena.ionosphere.data.model.SpaceWeatherProtons
import com.valledemena.ionosphere.data.model.SpaceWeatherSunspots
import com.valledemena.ionosphere.data.model.SpaceWeatherWind
import com.valledemena.ionosphere.data.model.SpaceWeatherXray
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ciclo del store: intento por seccion throttled por TTL (single-source),
 * etiquetas de edad con la politica sellada (live/cache/stale/unavailable)
 * y start/stop idempotente (foreground-only). Fuentes falsas via lambda
 * (seam del store), estilo de la casa JUnit4 con runBlocking.
 */
class HudStoreTest {

    // ---- fixtures minimos: el store solo transporta el payload, no lo lee ----

    private fun swData() = SpaceWeatherData(
        updated = 0L,
        utc = "",
        kp = SpaceWeatherKp(2.1, 0, emptyList(), "fake"),
        f107 = SpaceWeatherF107(0.0, emptyList(), "fake"),
        wind = SpaceWeatherWind(0.0, 0.0, 0.0, 0.0, 0.0, null, "fake"),
        mag = SpaceWeatherMag(emptyList(), "fake"),
        xray = SpaceWeatherXray(0.0, "", emptyList(), emptyList(), "fake"),
        protons = SpaceWeatherProtons(0.0, emptyList(), 0, "fake"),
        sunspots = SpaceWeatherSunspots(emptyList(), "fake"),
        forecast = SpaceWeatherForecast(null, "", "fake"),
    )

    private fun dstData() = DstData(
        now = -12.0,
        min7d = -50.0,
        series = emptyList(),
        count = 0,
        gst = emptyList(),
        checkedAt = 0L,
        source = "fake",
    )

    private fun ionoData() = IonosondeData(
        updated = 0L,
        stations = emptyList(),
        total = 0,
        catalogSize = 0,
        source = "fake",
    )

    private fun store(
        swSource: HudSource<SpaceWeatherData> = HudSource {
            Outcome.Failed(RuntimeException("sin sw"))
        },
        dstSource: HudSource<DstData> = HudSource {
            Outcome.Failed(RuntimeException("sin dst"))
        },
        ionoSource: HudSource<IonosondeData> = HudSource {
            Outcome.Failed(RuntimeException("sin iono"))
        },
        clock: () -> Long = { 0L },
    ): HudStore = HudStore(swSource, dstSource, ionoSource, clock, CoroutineScope(Dispatchers.Default))

    // ---- live: entradas con edad de la politica C4-C5 ----

    @Test
    fun live_actualiza_y_etiqueta_edad() = runBlocking {
        var now = 10_000L
        val s = store(
            swSource = HudSource { Outcome.Live(swData(), "fake", now) },
            clock = { now },
        )
        val res = s.refresh()
        assertTrue(res.getValue(HudSection.KP))
        val e = s.kp()!!
        assertTrue(e.outcome is Outcome.Live<*>)
        assertEquals("live 0s", e.ageLabel)
    }

    @Test
    fun live_con_edad_usa_fetched_at_del_outcome() = runBlocking {
        var now = 10_000L
        val s = store(
            swSource = HudSource { Outcome.Live(swData(), "fake", now - 45_000L) },
            clock = { now },
        )
        s.refresh()
        assertEquals("live 45s", s.kp()!!.ageLabel)
    }

    // ---- cached: cache vigente y vencida (stale) ----

    @Test
    fun cached_con_edad_vigente_y_vencida() = runBlocking {
        var now = 600_000L
        val vigente = store(
            swSource = HudSource { Outcome.Cached(swData(), "fake", now - 300_000L, stale = false) },
            clock = { now },
        )
        vigente.refresh()
        assertEquals("cache +5m", vigente.kp()!!.ageLabel)
        val vencida = store(
            swSource = HudSource { Outcome.Cached(swData(), "fake", now - 300_000L, stale = true) },
            clock = { now },
        )
        vencida.refresh()
        assertEquals("cache +5m (stale)", vencida.kp()!!.ageLabel)
    }

    // ---- failed: honesto y con reintento throttled por TTL ----

    @Test
    fun failed_honesto_unavailable_y_reintento_post_ttl() = runBlocking {
        var now = 0L
        var calls = 0
        val s = store(
            swSource = HudSource { calls++; Outcome.Failed(RuntimeException("sin red")) },
            clock = { now },
        )
        s.refresh()
        assertEquals("unavailable", s.kp()!!.ageLabel)
        assertEquals(1, calls)
        // throttle: el fallo no reintenta en el mismo TTL
        s.refresh()
        assertEquals(1, calls)
        now += 90_000L
        s.refresh()
        assertEquals(2, calls)
    }

    // ---- throttle por TTL del valor bueno ----

    @Test
    fun throttle_refetch_solo_post_ttl() = runBlocking {
        var now = 10_000L
        var calls = 0
        val s = store(
            swSource = HudSource { calls++; Outcome.Live(swData(), "fake", now) },
            clock = { now },
        )
        assertTrue(s.refresh().getValue(HudSection.KP))
        assertEquals(1, calls)
        // mismo instante: sin red
        assertFalse(s.refresh().getValue(HudSection.KP))
        assertEquals(1, calls)
        // TTL vencido: reintenta
        now += 90_000L
        assertTrue(s.refresh().getValue(HudSection.KP))
        assertEquals(2, calls)
    }

    // ---- start/stop idempotente (foreground-only) ----

    @Test
    fun start_stop_idempotente_e_isRunning() {
        val s = store()
        assertFalse(s.isRunning())
        s.start()
        s.start()
        assertTrue(s.isRunning())
        s.stop()
        s.stop()
        assertFalse(s.isRunning())
    }
}
