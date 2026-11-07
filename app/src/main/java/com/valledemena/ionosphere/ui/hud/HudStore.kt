package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.DstData
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.SpaceWeatherData
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.data.source.DstAdapter
import com.valledemena.ionosphere.data.source.SpaceWeatherAdapter
import com.valledemena.ionosphere.data.source.ionosondes.IonosondeAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Seccion del HUD: cada una con su adapter, su TTL y su ciclo propio. */
enum class HudSection { KP, DST, FOF2 }

/**
 * Valor presentable: outcome crudo del adapter + etiqueta de edad con la
 * politica de edad sellada (C4-C5). El consumidor (panel de release en PR-B)
 * lee `outcome` para el valor/fuente y `ageLabel` para la edad.
 */
data class HudEntry<out T>(val outcome: Outcome<T>, val ageLabel: String)

/**
 * Fuente colgable por seccion. Seam para tests: fakes via lambda sin tocar
 * la capa de datos (`data/` es intocable en la fila).
 */
fun interface HudSource<T> {
    suspend fun fetch(): Outcome<T>
}

/**
 * Ciclo del HUD: un tick (por defecto 1 s) intenta refresco por seccion,
 * throttled por su TTL del pin (`DataTtl`, single-source). Solo foreground:
 * `start()`/`stop()` explicitos por el contrato de datos (s5). Por diseno no
 * conoce ni inventa ninguna fuente: el `source` vive en el payload.
 *
 * Los adapters R2 ya traen cache: el Outcome.Cached del adapter alimenta el
 * modo offline con edad honesta; aqui solo se presentan.
 */
class HudStore internal constructor(
    swSource: HudSource<SpaceWeatherData>,
    dstSource: HudSource<DstData>,
    fof2Source: HudSource<IonosondeData>,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val tickMs: Long = 1_000L,
) {

    private class Slot<T>(val ttlMs: Long, val source: HudSource<T>) {
        @Volatile var lastAttemptAt: Long = Long.MIN_VALUE
        @Volatile var entry: HudEntry<T>? = null

        /** Un intento throttled: true si hubo red en este tick. */
        suspend fun attempt(nowMs: Long): Boolean {
            val goodAt = when (val o = entry?.outcome) {
                is Outcome.Live -> o.fetchedAtMs
                is Outcome.Cached -> if (o.stale) null else o.fetchedAtMs
                null, is Outcome.Failed -> null
            }
            // valor bueno no vencido: sin red (throttle por TTL)
            if (goodAt != null && nowMs - goodAt < ttlMs) return false
            // sin valor bueno (o vencido): maximo un intento por TTL
            if (goodAt == null && lastAttemptAt != Long.MIN_VALUE && nowMs - lastAttemptAt < ttlMs) {
                return false
            }
            lastAttemptAt = nowMs
            entry = present(safeFetch(), nowMs)
            return true
        }

        private suspend fun safeFetch(): Outcome<T> =
            try {
                source.fetch()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Outcome.Failed(t)
            }

        private fun present(o: Outcome<T>, nowMs: Long): HudEntry<T> = HudEntry(
            outcome = o,
            ageLabel = when (o) {
                is Outcome.Live -> AgeLabel.live(nowMs, o.fetchedAtMs)
                is Outcome.Cached -> AgeLabel.cache(nowMs - o.fetchedAtMs, o.stale)
                is Outcome.Failed -> AgeLabel.UNAVAILABLE
            },
        )
    }

    private val kpSlot = Slot(DataTtl.SPACE_WEATHER_MS, swSource)
    private val dstSlot = Slot(DataTtl.DST_MS, dstSource)
    private val fof2Slot = Slot(DataTtl.IONOSONDES_DATA_MS, fof2Source)

    /** Un tick del ciclo: intento throttled por seccion. */
    suspend fun refresh(): Map<HudSection, Boolean> {
        val nowMs = clock()
        return mapOf(
            HudSection.KP to kpSlot.attempt(nowMs),
            HudSection.DST to dstSlot.attempt(nowMs),
            HudSection.FOF2 to fof2Slot.attempt(nowMs),
        )
    }

    // ---- lecturas para el consumidor (null = sin intento todavia) ----

    fun kp(): HudEntry<SpaceWeatherData>? = kpSlot.entry
    fun dst(): HudEntry<DstData>? = dstSlot.entry
    fun fof2(): HudEntry<IonosondeData>? = fof2Slot.entry

    // ---- ciclo foreground-only (DATA_CONTRACT s5) ----

    private var job: Job? = null

    /** Arranca el ciclo; idempotente (sin doble job). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (coroutineContext.isActive) {
                try {
                    refresh()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // sin propagar: la seccion reintenta por TTL en el proximo tick
                }
                delay(tickMs)
            }
        }
    }

    /** Detiene el ciclo; idempotente. */
    fun stop() {
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = job?.isActive == true

    companion object {
        /**
         * Grafo real: adapters R2 como fuentes, clock inyectable, scope del
         * llamador. El TTL de cada seccion sale de `DataTtl` (single-source).
         */
        fun withAdapters(
            spaceWeather: SpaceWeatherAdapter,
            dst: DstAdapter,
            ionosondes: IonosondeAdapter,
            clock: () -> Long = System::currentTimeMillis,
            scope: CoroutineScope,
            tickMs: Long = 1_000L,
        ): HudStore = HudStore(
            swSource = HudSource<SpaceWeatherData> { spaceWeather.fetch() },
            dstSource = HudSource<DstData> { dst.fetch() },
            fof2Source = HudSource<IonosondeData> { ionosondes.fetch() },
            clock = clock,
            scope = scope,
            tickMs = tickMs,
        )
    }
}
