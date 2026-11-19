package com.valledemena.ionosphere.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valledemena.ionosphere.data.model.DstData
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.SpaceWeatherData
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.render.SliceStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * HUD de produccion R3.3 (PR-B): tres secciones espejo del pin web
 * (Kp / Dst / foF2) con edad y source por seccion (contrato s4). Presenta
 * solamente: los valores salen del pin (HudKp/HudDst/HudFoF2), la edad de
 * la politica sellada (AgeLabel) y el source del payload (dynamico, nunca
 * inventado aqui). El ciclo lo manda el HudStore; esta vista solo lee.
 */
@Composable
fun HudPanel(
    store: HudStore,
    modifier: Modifier = Modifier,
    pointSize: Float = 11f,
    onPointSizeChange: (Float) -> Unit = {},
    epochMs: Long? = null,
    sliceOn: Boolean = false,
    onSliceToggle: (Boolean) -> Unit = {},
    sliceStatus: SliceStatus = SliceStatus(null, null, 0f, false),
    sliceStationCodes: List<String> = emptyList(),
    sliceCodeA: String? = null,
    sliceCodeB: String? = null,
    onSliceCodeA: (String) -> Unit = {},
    onSliceCodeB: (String) -> Unit = {},
) {
    var kpEntry by remember { mutableStateOf(store.kp()) }
    var dstEntry by remember { mutableStateOf(store.dst()) }
    var fof2Entry by remember { mutableStateOf(store.fof2()) }

    // Presentacion cada segundo: el store ya throttlea la red por TTL;
    // aqui solo se lee el estado para recomponer la vista.
    LaunchedEffect(store) {
        while (true) {
            kpEntry = store.kp()
            dstEntry = store.dst()
            fof2Entry = store.fof2()
            delay(1_000L)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        // R4.6.1: con la seccion de cortina el panel ya no cabe en pantalla, y
        // sin scroll la fila del par A-B quedaba inalcanzable (el interruptor
        // solo se alcanzaba cortando la red para que la lista foF2 encogiera).
        // El tope de altura deja libres los 132 dp inferiores de la TimeBar.
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight * 0.84f)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpSection(kpEntry)
            DstSection(dstEntry)
            FoF2Section(fof2Entry, epochMs)
            PointSizeSection(pointSize, onPointSizeChange)
            SliceSection(
                on = sliceOn,
                status = sliceStatus,
                codes = sliceStationCodes,
                codeA = sliceCodeA,
                codeB = sliceCodeB,
                onToggle = onSliceToggle,
                onCodeA = onSliceCodeA,
                onCodeB = onSliceCodeB,
            )
        }
    }
}

// ---- Seccion cortina Ne(h): toggle + readout del oraculo (P4b) ----

/**
 * Toggle de la cortina y su readout. Con la capa encendida y sin volumen en
 * runtime el texto es el prompt del oraculo: la linea `A -> B  pk %.1f` solo
 * aparece cuando la GPU tiene una malla horneada (`App.cpp:5338`) **y** el
 * usuario ha elegido par. Con la capa apagada no se pinta linea
 * (`App.cpp:5336` mete todo el readout en `if (sliceOn)`).
 *
 * R4.6.2: el par que decide la linea es el del usuario (`codeA`/`codeB`), no
 * `status.codeA/B` (par horneado del renderer). Si se usara el horneado, apagar
 * y volver a encender mostraria el `pk` anterior en lugar del prompt.
 */
@Composable
private fun SliceSection(
    on: Boolean,
    status: SliceStatus,
    codes: List<String>,
    codeA: String?,
    codeB: String?,
    onToggle: (Boolean) -> Unit,
    onCodeA: (String) -> Unit,
    onCodeB: (String) -> Unit,
) {
    val state = HudSlice.panelState(
        on = on,
        codeA = codeA,
        codeB = codeB,
        codes = codes,
        peakLogNe = status.peakLogNe,
        hasData = status.hasData,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "cortina",
            color = SLATE,
            fontSize = 11.sp,
            modifier = Modifier.width(52.dp),
        )
        Switch(checked = on, onCheckedChange = onToggle)
    }
    // R4.6.1: en Android no hay hover, asi que el par pin+hover del oraculo se
    // sustituye por dos selectores explicitos. Sin par valido el plan no
    // hornea y el readout se queda en el prompt (`HudSlice.PIN_PROMPT`).
    // R4.6.2: con la capa apagada se deshabilitan, no se ocultan: ocultarlos
    // cambiaria el alto del panel que R4.6.1 estabilizo.
    EndpointPicker("A", codes, codeA, state.pickersEnabled, onCodeA)
    EndpointPicker("B", codes, codeB, state.pickersEnabled, onCodeB)
    // El hueco del readout se mantiene con la capa apagada (texto vacio) para
    // que el alto del panel no salte al conmutar; el oraculo tampoco imprime
    // nada en ese estado.
    Text(
        text = state.line.orEmpty(),
        color = SLATE,
        fontSize = 11.sp,
    )
}

/** Selector compacto de estacion para un extremo de la cortina. */
@Composable
private fun EndpointPicker(
    label: String,
    codes: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Al deshabilitarse (capa apagada) se cierra el desplegable para no dejar
    // un menu huerfano abierto.
    LaunchedEffect(enabled) {
        if (!enabled) open = false
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = SLATE,
            fontSize = 11.sp,
            modifier = Modifier.width(52.dp),
        )
        Box {
            TextButton(
                onClick = { open = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(selected ?: "--", color = Color.White, fontSize = 11.sp)
            }
            DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
                codes.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code, fontSize = 12.sp) },
                        onClick = {
                            open = false
                            onSelect(code)
                        },
                    )
                }
            }
        }
    }
}

// ---- Control del tamano de punto (slider 3..15, capa T2) ----

@Composable
private fun PointSizeSection(pointSize: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "punto " + pointSize.roundToInt(),
            color = SLATE,
            fontSize = 11.sp,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = pointSize.coerceIn(3f, 15f),
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = 3f..15f,
            steps = 11,
            modifier = Modifier.width(140.dp),
        )
    }
}

// ---- Seccion Kp: valor 2 dec + G-label + edad + fuente + barra 9 segmentos ----

@Composable
private fun KpSection(e: HudEntry<SpaceWeatherData>?) {
    SectionHeader("Kp", e)
    val sw = payloadOf(e)
    if (sw == null) {
        Placeholder()
        return
    }
    val kp = sw.kp.now
    Text(
        text = HudKp.format(kp),
        color = zoneColor(HudKp.zone(kp)),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(text = HudKp.gLabel(sw.kp.gScale), color = SLATE, fontSize = 11.sp)
    KpBar(kp)
}

// ---- Seccion Dst: valor nT + banda + min 7d ----

@Composable
private fun DstSection(e: HudEntry<DstData>?) {
    SectionHeader("Dst", e)
    val dd = payloadOf(e)
    if (dd == null) {
        Placeholder()
        return
    }
    Text(
        text = HudDst.format(dd.now),
        color = dstColor(HudDst.band(dd.now)),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = HudDst.bandName(dd.now) + "  -  min 7d " + HudDst.format(dd.min7d),
        color = SLATE,
        fontSize = 11.sp,
    )
}

// ---- Seccion foF2: una fila por estacion core, orden por codigo ----

@Composable
private fun FoF2Section(e: HudEntry<IonosondeData>?, epochMs: Long?) {
    SectionHeader("foF2", e)
    val data = payloadOf(e)
    if (data == null) {
        Placeholder()
        return
    }
    Text(
        text = HudFoF2.rows(data.stations, epochMs).joinToString("\n") { row -> row.code + "  " + row.display },
        color = Color.White,
        fontSize = 11.sp,
    )
}

// ---- piezas comunes ----

@Composable
private fun SectionHeader(title: String, e: HudEntry<*>?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(text = ageOf(e), color = SLATE, fontSize = 11.sp)
        Text(text = "@ " + sourceOf(e?.outcome), color = SLATE_DIM, fontSize = 11.sp)
    }
}

@Composable
private fun Placeholder() {
    Text(text = "...", color = SLATE, fontSize = 14.sp)
}

/** Barra de 9 segmentos: relleno clamp01(kp - i), color por segmento del pin. */
@Composable
private fun KpBar(kp: Double) {
    val fills = HudKp.fills(kp)
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 0 until HudKp.SEGMENT_COUNT) {
            Box(
                Modifier
                    .width(16.dp)
                    .height(7.dp)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fills[i])
                        .fillMaxHeight()
                        .background(zoneColor(HudKp.segmentZone(i + 1))),
                )
            }
        }
    }
}

// ---- lecturas / colores ----

private val SLATE = Color(0xFF94A3B8)
private val SLATE_DIM = Color(0xFF64748B)

/** Payload del outcome sin tocar polimorfismo Honest: null si no hay dato. */
private fun <T> payloadOf(e: HudEntry<T>?): T? = when (val o = e?.outcome) {
    is Outcome.Live -> o.value
    is Outcome.Cached -> o.value
    null, is Outcome.Failed -> null
}

private fun ageOf(e: HudEntry<*>?): String = e?.ageLabel ?: AgeLabel.UNAVAILABLE

private fun sourceOf(o: Outcome<*>?): String = when (o) {
    is Outcome.Live -> o.source
    is Outcome.Cached -> o.source
    else -> "n/d"
}

/** Colores del valor Kp, extraidos del pin (text-*-400/300 de la referencia). */
private fun zoneColor(z: HudKp.Zone): Color = when (z) {
    HudKp.Zone.SEVERE -> Color(0xFFF87171)
    HudKp.Zone.STRONG -> Color(0xFFFB923C)
    HudKp.Zone.MODERATE -> Color(0xFFFDE047)
    HudKp.Zone.MINOR -> Color(0xFF34D399)
}

/** Colores del anillo Dst: exactos por banda (HudDst.color). */
private fun dstColor(b: HudDst.Band): Color = when (b) {
    HudDst.Band.INTENSE -> Color(1.0f, 0.25f, 0.2f)
    HudDst.Band.STORM -> Color(1.0f, 0.55f, 0.15f)
    HudDst.Band.UNSETTLED -> Color(0.9f, 0.75f, 0.3f)
    HudDst.Band.QUIET -> Color(0.35f, 0.55f, 1.0f)
}
