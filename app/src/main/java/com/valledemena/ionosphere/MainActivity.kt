package com.valledemena.ionosphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.OkHttpHttpClient
import com.valledemena.ionosphere.data.source.DstAdapter
import com.valledemena.ionosphere.data.source.SpaceWeatherAdapter
import com.valledemena.ionosphere.data.source.ionosondes.IonosondeAdapter
import com.valledemena.ionosphere.render.AtmoShaderConstants
import com.valledemena.ionosphere.ui.GlobeScreen
import com.valledemena.ionosphere.ui.hud.HudStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Una sola actividad, sin navegación y sin pantallas inventadas: R1 es esqueleto.
 * El HUD llega en R3.3 (`IMPLEMENTATION_SLICES.md`): el grafo de datos vive aqui
 * (release lleva el camino de red por primera vez, contrato C3).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // R3.2b-iii rider (K1/K2): overrides de pasos por extras de lanzamiento,
        // resueltos solo aqui y solo si caen en el rango de los pins; fuera de
        // rango o ausentes el renderer queda en los defaults (12/4). Sin UI,
        // sin persistencia, sin clamp.
        val atmoSteps = intent.getIntExtra("atmoSteps", -1)
            .takeIf { it in AtmoShaderConstants.STEPS_MIN..AtmoShaderConstants.STEPS_MAX }
        val atmoLightSteps = intent.getIntExtra("atmoLightSteps", -1)
            .takeIf { it in AtmoShaderConstants.LIGHT_STEPS_MIN..AtmoShaderConstants.LIGHT_STEPS_MAX }

        // Grafo HUD R3.3: http + cache + 3 adapters + store. Solo foreground:
        // start en ON_START, stop en ON_STOP (contrato de datos s5).
        val hudStore = buildHudStore()
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> hudStore.start()
                    Lifecycle.Event.ON_STOP -> hudStore.stop()
                    else -> Unit
                }
            },
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    GlobeScreen(
                        atmoSteps = atmoSteps,
                        atmoLightSteps = atmoLightSteps,
                        hudStore = hudStore,
                    )
                }
            }
        }
    }

    /** Grafo del HUD: http + cache + 3 adapters R2 + store TTL (single-source). */
    private fun buildHudStore(): HudStore {
        val http = OkHttpHttpClient()
        val cache = InMemoryCache()
        return HudStore.withAdapters(
            spaceWeather = SpaceWeatherAdapter(http, cache),
            dst = DstAdapter(http, cache),
            ionosondes = IonosondeAdapter(http, cache),
            scope = CoroutineScope(Dispatchers.IO),
        )
    }
}
