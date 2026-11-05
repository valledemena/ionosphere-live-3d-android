package com.valledemena.ionosphere.debug.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Una fila = un adapter: titulo, TTL de refresco (de `DataTtl`) y fetch. */
class RowSpec(val title: String, val ttlMs: Long, val fetch: suspend () -> Outcome<*>)

private val INK = Color(0xFF74FFB8)
private val FADE = Color(0xB3121212)

/**
 * Panel de diagnostico (debug-only): invoca a los adapters como lector puro,
 * muestra por fuente el `Outcome` tipado + edad + el `source` embebido.
 *
 * El panel maneja su propio ciclo de refresco: cada fila se refresca cuando
 * su TTL de `DataTtl` vence (1 s de grano), con las instancias de adapter
 * vivas (el N2 in-memory de SpaceWeather sobrevive entre ciclos). Cache
 * in-memory compartida: en modo avion la fila pasa a `cache +Nm` u
 * `unavailable` honesto, sin relleno en ningun texto.
 */
@Composable
fun DebugDataPanel(specs: List<RowSpec>, clock: () -> Long = System::currentTimeMillis) {
    val states = remember(specs) {
        List(specs.size) { mutableStateOf<Outcome<*>?>(null) }
    }

    LaunchedEffect(specs) {
        val lastMs = LongArray(specs.size) { -1L * 60_000L }
        while (true) {
            val now = clock()
            specs.forEachIndexed { i, spec ->
                // -60_000 inicial: primera lectura inmediata al abrir.
                if (now - lastMs[i] >= spec.ttlMs) {
                    lastMs[i] = now
                    launch {
                        states[i].value = runCatching { spec.fetch() }
                            .getOrElse { Outcome.Failed(it) }
                    }
                }
            }
            delay(1_000L)
        }
    }

    // Re-render 1 vez/s para que la edad respire (etiqueta deriva de now).
    var tick by remember(specs) { mutableStateOf(0L) }
    LaunchedEffect(specs) {
        while (true) {
            delay(1_000L)
            tick = clock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(FADE)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text("DATA DIAGNOSTICS (debug)", color = INK, fontSize = 10.sp)
        specs.forEachIndexed { i, spec ->
            val o = states[i].value
            val now = tick
            Text(
                "${spec.title}: ${labelOf(o, now)} | ${sourceOf(o)}",
                color = INK,
                fontSize = 10.sp,
            )
        }
    }
}

private fun labelOf(o: Outcome<*>?, now: Long): String = when (o) {
    null -> "..."
    is Outcome.Live -> AgeLabel.live(now, o.fetchedAtMs)
    is Outcome.Cached -> AgeLabel.cache(now - o.fetchedAtMs, o.stale)
    else -> AgeLabel.UNAVAILABLE
}

private fun sourceOf(o: Outcome<*>?): String = when (o) {
    is Outcome.Live -> o.source
    is Outcome.Cached -> o.source
    else -> ">"
}
