package com.valledemena.ionosphere.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.gl.GlobeSurfaceView
import com.valledemena.ionosphere.ui.hud.HudEntry
import com.valledemena.ionosphere.ui.hud.HudPanel
import com.valledemena.ionosphere.ui.hud.HudStore
import com.valledemena.ionosphere.ui.hud.StationPopup
import com.valledemena.ionosphere.ui.hud.StationPopupModel
import kotlinx.coroutines.delay

/**
 * Globo a pantalla completa + HUD de produccion (R3.3) + capa de ionosondas
 * y popup (R3.4).
 *
 * El ciclo de vida manda:
 *  - `ON_RESUME` reanuda el contexto GL; `ON_PAUSE` lo suspende
 *    (con `preserveEGLContextOnPause` el contexto sobrevive al background).
 *  - `ON_START` empieza a pedir frames a la rejilla de vsync; `ON_STOP` los
 *    corta.
 * Nada se dibuja con la app en background, y no hay ningún hilo propio: el
 * `Choreographer` del `GlobeSurfaceView` es el único que pide frames.
 */
@Composable
fun GlobeScreen(
    modifier: Modifier = Modifier,
    atmoSteps: Int? = null,
    atmoLightSteps: Int? = null,
    hudStore: HudStore? = null,
    pointSize: Float = 11f,
    onPointSizeChange: (Float) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<GlobeSurfaceView?>(null) }
    var fof2Entry by remember { mutableStateOf<HudEntry<IonosondeData>?>(null) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    val data = payloadOf(fof2Entry)

    DisposableEffect(lifecycleOwner, view) {
        val surfaceView = view
        if (surfaceView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                    Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                    Lifecycle.Event.ON_START -> surfaceView.start()
                    Lifecycle.Event.ON_STOP -> surfaceView.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                surfaceView.onResume()
                surfaceView.start()
            } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                surfaceView.start()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                surfaceView.stop()
                surfaceView.onPause()
            }
        }
    }

    // El render solo consume la capa de datos ya existente (C1): HudStore.fof2().
    LaunchedEffect(hudStore) {
        if (hudStore == null) return@LaunchedEffect
        while (true) {
            fof2Entry = hudStore.fof2()
            delay(1_000L)
        }
    }

    LaunchedEffect(view) {
        view?.onStationPick = { code -> selectedCode = code }
    }
    LaunchedEffect(view, data) {
        view?.setStations(data)
    }
    LaunchedEffect(view, pointSize) {
        view?.setPointSize(pointSize)
    }
    LaunchedEffect(data, selectedCode) {
        if (selectedCode != null && data?.stations?.none { it.code == selectedCode } != false) {
            selectedCode = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GlobeSurfaceView(context, atmoSteps = atmoSteps, atmoLightSteps = atmoLightSteps)
                    .also { view = it }
            },
        )
        if (hudStore != null) {
            HudPanel(
                store = hudStore,
                pointSize = pointSize,
                onPointSizeChange = onPointSizeChange,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }

        val popup = StationPopupModel.contentFor(
            data = data,
            code = selectedCode,
            ageLabel = fof2Entry?.ageLabel ?: "unavailable",
        )
        if (popup != null) {
            StationPopup(
                content = popup,
                onDismiss = { selectedCode = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        // Panel de diagnostico (debug-only; en release dataPanelFactory es null
        // y este call es un no-op - ver DataPanelHost.kt).
        DataPanelHost()
    }
}

private fun payloadOf(e: HudEntry<IonosondeData>?): IonosondeData? = when (val o = e?.outcome) {
    is Outcome.Live -> o.value
    is Outcome.Cached -> o.value
    null, is Outcome.Failed -> null
}
