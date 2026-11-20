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
import com.valledemena.ionosphere.core.SolarPosition
import com.valledemena.ionosphere.core.iono.F107_DEFAULT
import com.valledemena.ionosphere.core.iono.buildVolumeFromStations
import com.valledemena.ionosphere.core.replayEpochMs
import com.valledemena.ionosphere.core.solarEpochForScene
import com.valledemena.ionosphere.core.sampledAt
import com.valledemena.ionosphere.core.tick
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.gl.GlobeSurfaceView
import com.valledemena.ionosphere.render.SLICE_VISIBLE_DEFAULT
import com.valledemena.ionosphere.render.SliceEndpoints
import com.valledemena.ionosphere.render.SliceStatus
import com.valledemena.ionosphere.ui.hud.HudEntry
import com.valledemena.ionosphere.ui.hud.HudPanel
import com.valledemena.ionosphere.ui.hud.HudStore
import com.valledemena.ionosphere.ui.hud.StationPopup
import com.valledemena.ionosphere.ui.hud.StationPopupModel
import com.valledemena.ionosphere.ui.hud.TimeBar
import com.valledemena.ionosphere.render.VolumeMode
import com.valledemena.ionosphere.render.VolumeQuality
import com.valledemena.ionosphere.render.VolumeStatus
import com.valledemena.ionosphere.render.VOLUME_VISIBLE_DEFAULT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    // R4.5 P4b: la cortina arranca apagada (`SliceLayer.h:41`); el estado del
    // readout sale del renderer, no se inventa aqui.
    var sliceOn by remember { mutableStateOf(SLICE_VISIBLE_DEFAULT) }
    var sliceStatus by remember { mutableStateOf(SliceStatus(null, null, 0f, false)) }
    // R4.6.1: par A-B de la cortina elegido en el HUD y catalogo de estaciones
    // del payload vivo. Sin par valido el plan no hornea: el readout sigue en
    // el prompt del oraculo.
    var sliceCodeA by remember { mutableStateOf<String?>(null) }
    var sliceCodeB by remember { mutableStateOf<String?>(null) }
    // R4.6.3 (density + chapman): pase volumetrico, apagado por defecto.
    var volumeOn by remember { mutableStateOf(VOLUME_VISIBLE_DEFAULT) }
    var volumeMode by remember { mutableStateOf(VolumeMode.DENSITY) }
    var volumeStatus by remember { mutableStateOf(VolumeStatus(VOLUME_VISIBLE_DEFAULT, false)) }
    val data = payloadOf(fof2Entry)
    val sceneEpochMs = replayEpochMs(nowMs, replaySettings)
    val solarEpochMs = solarEpochForScene(nowMs, replaySettings)
    val displayData = data?.sampledAt(sceneEpochMs)
    val stationsByCode = remember(data) {
        data?.stations?.associateBy { it.code } ?: emptyMap()
    }
    val stationCodes = remember(data) { data?.stations?.map { it.code }?.sorted() ?: emptyList() }

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

    // R4.5 P4b: toggle de la cortina + refresco del readout al mismo ritmo que
    // el resto del HUD. Sin volumen inyectado, `hasData` es false y el panel
    // muestra el prompt del oraculo.
    LaunchedEffect(view, sliceOn) {
        val surfaceView = view ?: return@LaunchedEffect
        surfaceView.setShowSlice(sliceOn)
        while (true) {
            sliceStatus = surfaceView.sliceStatus()
            delay(1_000L)
        }
    }

    // R4.6.2: al apagar la cortina el oraculo vacia el par del usuario
    // (`App.cpp:5321-5322`) y, como todo el readout vive dentro de
    // `if (sliceOn)` (`App.cpp:5336`), con la capa apagada no hay linea. Se
    // limpian solo los codigos del usuario, no el par horneado ni el volumen
    // del renderer: el oraculo tampoco invalida la malla al desmarcar.
    LaunchedEffect(sliceOn) {
        if (!sliceOn) {
            sliceCodeA = null
            sliceCodeB = null
        }
    }

    // R4.6.1: defaults del par A-B cuando llega catalogo. Se mantiene la
    // eleccion del usuario mientras sus codigos sigan existiendo.
    LaunchedEffect(stationCodes) {
        if (stationCodes.isEmpty()) {
            sliceCodeA = null
            sliceCodeB = null
        } else {
            if (sliceCodeA !in stationCodes) sliceCodeA = stationCodes.first()
            val b = sliceCodeB
            if (b == null || b !in stationCodes || b == sliceCodeA) {
                sliceCodeB = stationCodes.firstOrNull { it != sliceCodeA }
            }
        }
    }

    LaunchedEffect(view, sliceCodeA, sliceCodeB, stationsByCode) {
        val surfaceView = view ?: return@LaunchedEffect
        val a = sliceCodeA?.let { stationsByCode[it] }
        val b = sliceCodeB?.let { stationsByCode[it] }
        val endpoints = if (a != null && b != null && a.code != b.code) {
            SliceEndpoints(
                codeA = a.code,
                latA = a.lat.toFloat(),
                lonA = a.lon.toFloat(),
                codeB = b.code,
                latB = b.lat.toFloat(),
                lonB = b.lon.toFloat(),
            )
        } else {
            null
        }
        surfaceView.setSliceEndpoints(endpoints)
    }

    // R4.6.3: interruptor + conmutador de modos del volumen; conmutar sin
    // textura residente es sin efecto, asi que la conmutacion queda deshabilitada
    // desde el HUD (D-R4.6.3: "conmutacion en runtime sin recompilar").
    LaunchedEffect(view, volumeOn) {
        val surfaceView = view ?: return@LaunchedEffect
        surfaceView.setShowVolume(volumeOn)
        while (true) {
            volumeStatus = surfaceView.volumeStatus()
            delay(1_000L)
        }
    }

    LaunchedEffect(view, volumeMode, volumeOn) {
        val surfaceView = view ?: return@LaunchedEffect
        surfaceView.setVolumeMode(volumeMode)
    }

    // R4.6.1: volumen Ne(h) vivo desde las ionosondas. Se rehornea solo al
    // cambiar el payload o al cruzar un escalon solar de 10 min: 72x72x48
    // celdas no caben en el hilo de UI, y el Sol se mueve despacio. Apagada la
    // capa no se construye nada (delta ON/OFF limpio para el protocolo R4.6.5).
    val solarVolumeStepMs = sliceVolumeSolarStepMs(nowMs, solarEpochMs)
    LaunchedEffect(view, sliceOn, volumeOn, data, solarVolumeStepMs) {
        val surfaceView = view ?: return@LaunchedEffect
        if ((!sliceOn && !volumeOn) || data == null) {
            surfaceView.setSliceVolume(null)
            surfaceView.setVolume(null)
            return@LaunchedEffect
        }
        val sun = SolarPosition.directionAt(solarVolumeStepMs).map { it.toDouble() }.toDoubleArray()
        val f107 = f107Of(hudStore)
        val volume = withContext(Dispatchers.Default) {
            buildVolumeFromStations(data.stations, sun, solarVolumeStepMs / 1000L, f107)
        }
        surfaceView.setSliceVolume(volume)
        // R4.6.3: el pase raymarche por el mismo volumen; apagado, lo vacia.
        surfaceView.setVolume(if (volumeOn) volume else null)
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
                sliceOn = sliceOn,
                onSliceToggle = { sliceOn = it },
                sliceStatus = sliceStatus,
                sliceStationCodes = stationCodes,
                sliceCodeA = sliceCodeA,
                sliceCodeB = sliceCodeB,
                onSliceCodeA = { sliceCodeA = it },
                onSliceCodeB = { sliceCodeB = it },
                volumeOn = volumeOn,
                volumeMode = volumeMode,
                volumeStatus = volumeStatus,
                onVolumeToggle = { volumeOn = it },
                onVolumeMode = { volumeMode = it },
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

/** Escalon solar del volumen: 10 min, muy por debajo del movimiento real. */
private const val SOLAR_VOLUME_STEP_MS = 600_000L

/**
 * Epoca con la que se hornea el volumen Ne(h), redondeada al escalon solar.
 *
 * En LIVE [solarEpochForScene] devuelve `null` a proposito: el renderer lee el
 * reloj en cada frame para mover el terminador. El volumen no puede hacer eso
 * (se hornea en [Dispatchers.Default] y se sube como textura), asi que su
 * epoca de LIVE es `nowMs`; en REPLAY manda la epoca de escena. Redondear hacia
 * abajo deja el efecto estable dentro del escalon y lo rehornea al cruzarlo.
 */
internal fun sliceVolumeSolarStepMs(nowMs: Long, solarEpochMs: Long?): Long =
    ((solarEpochMs ?: nowMs) / SOLAR_VOLUME_STEP_MS) * SOLAR_VOLUME_STEP_MS

/**
 * F10.7 del payload de clima espacial. Solo alimenta la mezcla climatologica
 * lejos de las estaciones; si no hay valor usable se declara [F107_DEFAULT]
 * en vez de inventar uno por celda.
 */
private fun f107Of(store: HudStore?): Double {
    val sw = when (val o = store?.kp()?.outcome) {
        is Outcome.Live -> o.value
        is Outcome.Cached -> o.value
        else -> null
    } ?: return F107_DEFAULT
    val value = sw.f107.now
    return if (value.isFinite() && value > 0.0) value else F107_DEFAULT
}

private fun payloadOf(e: HudEntry<IonosondeData>?): IonosondeData? = when (val o = e?.outcome) {
    is Outcome.Live -> o.value
    is Outcome.Cached -> o.value
    null, is Outcome.Failed -> null
}
