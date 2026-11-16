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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.valledemena.ionosphere.core.ReplaySettings
import com.valledemena.ionosphere.core.replayEpochMs
import com.valledemena.ionosphere.core.solarEpochForScene
import com.valledemena.ionosphere.core.sampledAt
import com.valledemena.ionosphere.core.tick
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.gl.GlobeSurfaceView
import com.valledemena.ionosphere.ui.hud.HudEntry
import com.valledemena.ionosphere.ui.hud.HudPanel
import com.valledemena.ionosphere.ui.hud.HudStore
import com.valledemena.ionosphere.ui.hud.StationPopup
import com.valledemena.ionosphere.ui.hud.StationPopupModel
import com.valledemena.ionosphere.ui.hud.TimeBar
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
    replaySettings: ReplaySettings = ReplaySettings(),
    onReplayChange: (ReplaySettings) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<GlobeSurfaceView?>(null) }
    var fof2Entry by remember { mutableStateOf<HudEntry<IonosondeData>?>(null) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val data = payloadOf(fof2Entry)
    val sceneEpochMs = replayEpochMs(nowMs, replaySettings)
    val solarEpochMs = solarEpochForScene(nowMs, replaySettings)
    val displayData = data?.sampledAt(sceneEpochMs)

    DisposableEffect(lifecycleOwner, view) {
        val surfaceView = view
        if (surfaceView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isResumed = true
                        surfaceView.onResume()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        isResumed = false
                        surfaceView.onPause()
                    }
                    Lifecycle.Event.ON_START -> surfaceView.start()
                    Lifecycle.Event.ON_STOP -> surfaceView.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                isResumed = true
                surfaceView.onResume()
                surfaceView.start()
            } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                isResumed = false
                surfaceView.start()
            } else {
                isResumed = false
            }
            onDispose {
                isResumed = false
                lifecycleOwner.lifecycle.removeObserver(observer)
                surfaceView.stop()
                surfaceView.onPause()
            }
        }
    }

    // Un solo reloj para TimeBar, HUD y estaciones. Se congela en background.
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        nowMs = System.currentTimeMillis()
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
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
    LaunchedEffect(view, displayData) {
        view?.setStations(displayData)
    }
    LaunchedEffect(view, solarEpochMs) {
        view?.setSolarEpoch(solarEpochMs)
    }
    LaunchedEffect(view, pointSize) {
        view?.setPointSize(pointSize)
    }

    // R4.2: mismo intervalo que el oráculo (500 ms, dt = 0.5). El efecto no se
    // reinicia al cambiar de minuto: lee el estado más reciente en cada tick.
    val latestReplay by rememberUpdatedState(replaySettings)
    val latestOnReplayChange by rememberUpdatedState(onReplayChange)
    LaunchedEffect(
        isResumed,
        replaySettings.timeMode,
        replaySettings.replayPlaying,
        replaySettings.replaySpeed,
        replaySettings.replayLoop,
    ) {
        if (!isResumed) return@LaunchedEffect
        var lastTickNanos = System.nanoTime()
        while (true) {
            delay(500L)
            val nowNanos = System.nanoTime()
            val dtSec = (nowNanos - lastTickNanos) / 1_000_000_000.0
            lastTickNanos = nowNanos
            val current = latestReplay
            val next = current.tick(dtSec)
            if (next != current) latestOnReplayChange(next)
        }
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
                epochMs = sceneEpochMs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }

        TimeBar(
            settings = replaySettings,
            onChange = onReplayChange,
            nowMs = nowMs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
        )

        val popup = StationPopupModel.contentFor(
            data = displayData,
            code = selectedCode,
            ageLabel = fof2Entry?.ageLabel ?: "unavailable",
        )
        if (popup != null) {
            StationPopup(
                content = popup,
                onDismiss = { selectedCode = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 132.dp),
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
