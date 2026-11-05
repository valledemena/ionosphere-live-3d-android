package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.BxSeriesPoint
import com.valledemena.ionosphere.data.model.SpaceWeatherData
import com.valledemena.ionosphere.data.model.SpaceWeatherForecast
import com.valledemena.ionosphere.data.model.SpaceWeatherF107
import com.valledemena.ionosphere.data.model.SpaceWeatherKp
import com.valledemena.ionosphere.data.model.SpaceWeatherMag
import com.valledemena.ionosphere.data.model.SpaceWeatherParser
import com.valledemena.ionosphere.data.model.SpaceWeatherProtons
import com.valledemena.ionosphere.data.model.SpaceWeatherSunspots
import com.valledemena.ionosphere.data.model.SpaceWeatherWind
import com.valledemena.ionosphere.data.model.SpaceWeatherXray
import com.valledemena.ionosphere.data.model.SunspotRegion
import com.valledemena.ionosphere.data.model.TimeSeriesNumber
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * SpaceWeatherAdapter (H5 decision C - PR #11, pin `de56407f`).
 *
 * Nivel 1 - ciclo 90 s (TTL `DataTtl.SPACE_WEATHER_MS`): kp 1-min, F10.7,
 *   2 resumenes de viento, X-ray 6 h, forecast texto. ~25 KB wire/ciclo.
 * Nivel 2 - TTL propio de 60 min (60 min = ningun valor de estos bloques
 *   alimenta al globo por segundo): rtsw wind (densidad), rtsw mag (Bx),
 *   X-ray 1 dia, protones integrales, manchas. ~380 KB wire / ~5 MB parseo.
 *   Vive en memoria del adapter; mientras no madure, sus bloques quedan
 *   `source: "unavailable"` (contrato web, cero dato inventado) - el mismo
 *   patron de degradacion de IonosondeAdapter con las extras anclas.
 *
 * Semantica de Outcome y cache: espejo de DstAdapter. Cache fresca (<90 s)
 * devuelve Cached(stale=false) sin ninguna red; fallos de transporte con
 * cache previa degradan a Cached(stale) y sin cache a Failed. Un fallo HTTP
 * nunca crashea.
 *
 * Divergencias documentadas de la casa (regla DstParser B-3):
 *  - Nivel 2 corre en el MISMO coroutineScope que Nivel 1 (H5 decision C:
 *    "detras, sin bloquear" cumple como max(N1,N2) en cold start, no como
 *    suma - F1 de la revision hunk-a-hunk del PR #12).
 *  - `mag.series` NO porta el downsampling doble `i % 5 === 0` del web (que
 *    deja ~1 de cada 25 muestras, ~7-8 puntos por ventana: casi seguro bug
 *    del web). El adapter entrega la serie densa (1/1) con `takeLast(216)`.
 *    20x mas denso que el espejo: divergencia declarada aqui, no un typo
 *    del puerto.
 *  - Centinela de clase X-ray vacia `--` (ASCII) en vez del em-dash del web.
 *  - Checks `isNotEmpty()` en vez de truthy de JS: con 0 filas el pin decia
 *    "live"; aqui es "unavailable" honesto.
 *  - Sort defensivo por `t` en todas las series antes de saturar/tail;
 *    `sunspots.regions` se ordena por `region` (el pin no ordena: aqui el
 *    orden es determinista para la UI).
 *  - Si TODOS los endpoints del Nivel 2 fallan (todos los bodies null),
 *    el Nivel 2 NO se adopta y su timestamp NO se descuenta: el proximo
 *    ciclo reintenta. Si algunos llegan, se adopta con su `fetchedAtMs`.
 */
class SpaceWeatherAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var nivel2: Nivel2? = null

    /** TTL del Nivel 2: 60 min (verificado en casa contra el uso real de esos bloques). */
    val nivel2TtlMs: Long = 60L * 60_000L

    private data class Nivel2(
        val bx: Double?,
        val density: Double?,
        val magSeries: List<BxSeriesPoint>,
        val xrayDay: List<TimeSeriesNumber>,
        val protons: SpaceWeatherProtons,
        val sunspots: SpaceWeatherSunspots,
        val fetchedAtMs: Long,
    )

    suspend fun fetch(): Outcome<SpaceWeatherData> {
        val now = clock()

        cache?.load(KEY)?.let { hit ->
            if (now - hit.fetchedAtMs < DataTtl.SPACE_WEATHER_MS) {
                valueOf(hit)?.let { v -> return Outcome.Cached(v, hit.source, hit.fetchedAtMs, false) }
            }
        }

        return try {
            var transportError: Throwable? = null
            suspend fun getBody(path: String): String? = try {
                val r = http.get("$BASE$path")
                if (r.ok) r.body else null
            } catch (e: Throwable) {
                if (transportError == null) transportError = e
                null
            }

            // ---- Nivel 1 + (si toca) Nivel 2: un mismo coroutineScope, todo
            // async lanzado antes de esperar (cold = max(N1,N2), no suma) ----
            val n2Due = nivel2 == null || now - nivel2!!.fetchedAtMs >= nivel2TtlMs
            val pair = coroutineScope {
                val n1Async = listOf(
                    async { getBody("/json/planetary_k_index_1m.json") },
                    async { getBody("/json/f107_cm_flux.json") },
                    async { getBody("/products/summary/solar-wind-speed.json") },
                    async { getBody("/products/summary/solar-wind-mag-field.json") },
                    async { getBody("/json/goes/primary/xrays-6-hour.json") },
                    async { getBody("/text/3-day-forecast.txt") },
                )
                val n2Async = if (n2Due) listOf(
                    async { getBody("/json/rtsw/rtsw_wind_1m.json") },
                    async { getBody("/json/rtsw/rtsw_mag_1m.json") },
                    async { getBody("/json/goes/primary/xrays-1-day.json") },
                    async { getBody("/json/goes/primary/integral-protons-1-day.json") },
                    async { getBody("/json/sunspot_report.json") },
                ) else null
                val n1 = n1Async.awaitAll().map { it ?: "" }
                val n2 = n2Async?.awaitAll()
                n1 to n2
            }
            val n1 = pair.first
            val n2Bodies = pair.second

            // Nivel 2: si TODOS sus bodies fallan (null), no se adopta ni se
            // descuenta su TTL: el proximo ciclo reintenta (F-N2).
            var n2: Nivel2 = nivel2 ?: emptyNivel2(now)
            if (n2Bodies != null && n2Bodies.any { !it.isNullOrEmpty() }) {
                n2 = buildNivel2(n2Bodies[0], n2Bodies[1], n2Bodies[2], n2Bodies[3], n2Bodies[4], now)
                    .also { nivel2 = it }
            }

            // ---- Parseo de Nivel 1 ----
            val kpRows = n1[0].toArr.map {
                SpaceWeatherParser.KpRow(
                    t = it.iso("time_tag") ?: 0L,
                    estimatedKp = it.dbl("estimated_kp"),
                    kpIndex = it.dbl("kp_index"),
                )
            }
            // El feed es ascendente (la mas vieja primero): kpNow = extremo nuevo (max t),
            // fiel al `kpArr[kpArr.length-1]` del pin.
            val kpNow = kpRows.maxByOrNull { it.t }?.let { r -> r.estimatedKp ?: r.kpIndex ?: 0.0 } ?: 0.0

            val f107Pts = f107Series(n1[1])
            val f107Now = f107Pts.lastOrNull()?.f ?: 0.0

            val speedRow = n1[2].toArr.lastOrNull()  // web: windSpeed[windSpeed.length-1]
            val magRow = n1[3].toArr.lastOrNull()    // web: windMag[windMag.length-1]
            val wind = SpaceWeatherWind(
                speed = speedRow?.dbl("proton_speed") ?: 0.0,
                bt = magRow?.dbl("bt") ?: 0.0,
                bz = magRow?.dbl("bz_gsm") ?: 0.0,
                bx = n2.bx ?: 0.0,
                density = n2.density ?: 0.0,
                t = magRow?.str("time_tag"),
                source = if (speedRow != null && magRow != null) "NOAA DSCOVR (live)" else "unavailable",
            )

            val xrSeries = xraySeries6h(n1[4])
            // Web retiene el ultimo flujo positivo (xrNow = flux>0 de la serie).
            val xrNow = xrSeries.lastOrNull { it.f > 0.0 }?.f ?: 0.0

            val forecast = if (n1[5].isNotEmpty()) SpaceWeatherParser.parseForecast(n1[5]) else null

            val payload = SpaceWeatherData(
                updated = now,
                utc = nowIsoUtc(now),
                kp = SpaceWeatherKp(
                    now = kpNow,
                    gScale = SpaceWeatherParser.gScale(kpNow),
                    series = SpaceWeatherParser.kpSeries(kpRows, now).takeLast(96),
                    source = if (kpRows.isNotEmpty()) "NOAA SWPC (live)" else "unavailable",
                ),
                f107 = SpaceWeatherF107(
                    now = f107Now,
                    series = f107Pts.takeLast(72),
                    source = if (f107Pts.isNotEmpty()) "NOAA (live)" else "unavailable",
                ),
                wind = wind,
                mag = SpaceWeatherMag(
                    series = n2.magSeries.takeLast(216),
                    source = if (n2.magSeries.isNotEmpty()) "DSCOVR mag 1-min (live)" else "unavailable",
                ),
                xray = SpaceWeatherXray(
                    now = xrNow,
                    xrayClass = if (xrNow > 0.0) SpaceWeatherParser.xrayClass(xrNow) else "--",
                    series = xrSeries.takeLast(360),
                    seriesDay = n2.xrayDay.takeLast(1440),
                    source = if (xrSeries.isNotEmpty()) "GOES primario (live)" else "unavailable",
                ),
                protons = if (n2.protons.series.isNotEmpty()) n2.protons else
                    SpaceWeatherProtons(now = 0.0, series = emptyList(), satellite = 18, source = "unavailable"),
                sunspots = if (n2.sunspots.regions.isNotEmpty()) n2.sunspots else
                    SpaceWeatherSunspots(regions = emptyList(), source = "unavailable"),
                forecast = SpaceWeatherForecast(
                    maxKp = forecast?.maxKp,
                    note = forecast?.note ?: "",
                    source = if (forecast != null) "NOAA 3-day (live)" else "unavailable",
                ),
            )

            // ---- Degradacion: mismo patron de DstAdapter ----
            if (transportError != null && kpRows.isEmpty() && f107Pts.isEmpty() && xrSeries.isEmpty() && forecast == null) {
                cache?.load(KEY)?.let { hit ->
                    valueOf(hit)?.let { v ->
                        return Outcome.Cached(v, hit.source, hit.fetchedAtMs, now - hit.fetchedAtMs >= DataTtl.SPACE_WEATHER_MS, transportError)
                    }
                }
                return Outcome.Failed(transportError)
            }

            cache?.store(KEY, payload.kp.source, Json.encodeToString(SpaceWeatherData.serializer(), payload), now)
            Outcome.Live(payload, payload.kp.source, now)
        } catch (e: Throwable) {
            cache?.load(KEY)?.let { hit ->
                valueOf(hit)?.let { v -> return Outcome.Cached(v, hit.source, hit.fetchedAtMs, now - hit.fetchedAtMs >= DataTtl.SPACE_WEATHER_MS, e) }
            }
            Outcome.Failed(e)
        }
    }

    private fun valueOf(hit: com.valledemena.ionosphere.data.net.CacheHit): SpaceWeatherData? =
        runCatching { Json.decodeFromString<SpaceWeatherData>(hit.rawJson) }.getOrNull()

    private val json = Json { ignoreUnknownKeys = true }

    private val String.toArr: List<JsonElement>
        get() = runCatching { json.parseToJsonElement(this).let { if (it is JsonArray) it.toList() else emptyList() } }.getOrElse { emptyList() }

    private fun JsonElement.dbl(key: String): Double? = obj()?.get(key)?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
    private fun JsonElement.str(key: String): String? = obj()?.get(key)?.let { (it as? JsonPrimitive)?.content }
    private fun JsonElement.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement.iso(key: String): Long? = str(key)?.let(::parseIso)

    /** kp/f107/rtsw llegan sin Z (UTC); GOES llega con Z. */
    private fun parseIso(tag: String): Long = runCatching {
        val t = if (tag.endsWith("Z")) tag else "$tag+00:00"
        OffsetDateTime.parse(t).toInstant().toEpochMilli()
    }.getOrElse { 0L }

    private fun nowIsoUtc(now: Long): String = Instant.ofEpochMilli(now).toString()

    private fun f107Series(txt: String): List<TimeSeriesNumber> =
        txt.toArr.asSequence()
            .mapNotNull { e -> e.dbl("flux")?.takeIf { it > 0.0 }?.let { TimeSeriesNumber(e.iso("time_tag") ?: 0L, it) } }
            .sortedBy { it.t }
            .toList()

    private fun xraySeries6h(txt: String): List<TimeSeriesNumber> =
        txt.toArr.asSequence()
            .filter { e -> e.str("energy") == "0.1-0.8nm" && e.dbl("flux") != null }
            .mapNotNull { e -> e.dbl("flux")?.let { TimeSeriesNumber(e.iso("time_tag") ?: 0L, it) } }
            .sortedBy { it.t }
            .toList()

    private fun xraySeriesDay(txt: String): List<TimeSeriesNumber> =
        txt.toArr.asSequence()
            .filter { e -> e.str("energy") == "0.1-0.8nm" }
            .mapNotNull { e -> e.dbl("flux")?.takeIf { it > 0.0 }?.let { TimeSeriesNumber(e.iso("time_tag") ?: 0L, it) } }
            .sortedBy { it.t }
            .toList()

    private fun buildProtons(txt: String): SpaceWeatherProtons {
        val series = txt.toArr.asSequence()
            .filter { e -> e.str("energy") == ">=10 MeV" && e.dbl("flux") != null }
            .mapNotNull { e -> e.dbl("flux")?.takeIf { it >= 0.0 }?.let { TimeSeriesNumber(e.iso("time_tag") ?: 0L, it) } }
            .sortedBy { it.t }
            .toList()
        return SpaceWeatherProtons(
            now = series.lastOrNull()?.f ?: 0.0,
            series = series.takeLast(1440),
            satellite = 18,
            source = if (series.isNotEmpty()) "GOES-18 protones (live)" else "unavailable",
        )
    }

    private fun buildSunspots(txt: String): SpaceWeatherSunspots {
        val latest = mutableMapOf<Int, Pair<Long, SunspotRegion>>()
        txt.toArr.forEach { e ->
            val region = e.dbl("Region")?.toInt() ?: return@forEach
            val t = e.iso("time_tag") ?: 0L
            val cand = Pair(
                t,
                SunspotRegion(
                    region = region,
                    lat = e.dbl("Latitude") ?: 0.0,
                    lon = e.dbl("Longitude") ?: 0.0,
                    loc = e.str("Location") ?: e.str("Report_Location") ?: "",
                ),
            )
            val prev = latest[region]
            if (prev == null || cand.first > prev.first) latest[region] = cand
        }
        return SpaceWeatherSunspots(
            regions = latest.values.sortedBy { it.second.region }.map { it.second },
            source = if (latest.isNotEmpty()) "NOAA SWPC (live)" else "unavailable",
        )
    }

    private fun buildNivel2(windTxt: String?, magTxt: String?, xrayDayTxt: String?, protonsTxt: String?, sunspotsTxt: String?, now: Long): Nivel2 {
        val magRows = (magTxt ?: "").toArr.map { e ->
            SpaceWeatherParser.MagRow(
                t = e.iso("time_tag") ?: 0L,
                bx = e.dbl("bx_gse"),
                active = e.str("active") == "true",
            )
        }
        val magSeries = SpaceWeatherParser.selectMagSeries(magRows, hoursBack = 3, nowMs = now)
        return Nivel2(
            bx = magSeries.lastOrNull()?.bx,
            density = (windTxt ?: "").toArr.lastOrNull()?.dbl("proton_density"),
            magSeries = magSeries,
            xrayDay = xraySeriesDay(xrayDayTxt ?: ""),
            protons = if (protonsTxt != null) buildProtons(protonsTxt) else
                SpaceWeatherProtons(0.0, emptyList(), 18, "unavailable"),
            sunspots = if (sunspotsTxt != null) buildSunspots(sunspotsTxt) else
                SpaceWeatherSunspots(emptyList(), "unavailable"),
            fetchedAtMs = now,
        )
    }

    private fun emptyNivel2(now: Long) = Nivel2(
        bx = null, density = null, magSeries = emptyList(), xrayDay = emptyList(),
        protons = SpaceWeatherProtons(0.0, emptyList(), 18, "unavailable"),
        sunspots = SpaceWeatherSunspots(emptyList(), "unavailable"),
        fetchedAtMs = now - 60L * 60_000L,
    )

    companion object {
        const val BASE = "https://services.swpc.noaa.gov"
        private const val KEY = "spaceweather"
    }
}
