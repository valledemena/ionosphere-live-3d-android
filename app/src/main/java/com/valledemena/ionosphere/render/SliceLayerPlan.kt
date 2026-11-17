package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.DensityVolume
import com.valledemena.ionosphere.core.iono.SLICE_LVL
import com.valledemena.ionosphere.core.iono.SLICE_SEG
import com.valledemena.ionosphere.core.iono.SliceAction
import com.valledemena.ionosphere.core.iono.SlicePair
import com.valledemena.ionosphere.core.iono.buildSliceValues
import com.valledemena.ionosphere.core.iono.sliceAction
import com.valledemena.ionosphere.core.iono.sliceArc

/**
 * Estado de la capa de cortina (R4.5, P4b): extremos, plan de frame y guarda
 * de dispose. Todo puro y testeable en JVM; el VAO/VBO/textura viven en
 * `gl/SliceLayerRenderer`.
 *
 * Espejo de `App::rebuildSlice` (`src/App.cpp:3886-3951`) y de
 * `SliceLayer::render` (`SliceLayer.cpp:132-146`), incluido el reparto de
 * responsabilidades del oraculo:
 *
 * - `rebuildSlice` decide **si** hornear (par valido, volumen valido, par
 *   distinto o `force`); si no toca nada, deja la GPU como estaba.
 * - `SliceLayer::render` dibuja si `visible && hasData`, **sin** borrar la
 *   malla anterior cuando el par deja de ser valido.
 *
 * P4b no inventa el volumen: el `DensityVolume?` se inyecta y hoy es `null`
 * (R4.6 cablea `profileGrid` + `buildDensityVolume`). Por eso el plan, con
 * volumen nulo, siempre devuelve [SliceAction.Keep] o [SliceAction.Hide] y
 * **nunca** sube GPU: la capa no dibuja nada y el gate visual de R4.5 queda
 * NO PROBADO.
 */

/** Estado inicial de la capa: apagada (oraculo `SliceLayer.h:41`, `visible=false`). */
const val SLICE_VISIBLE_DEFAULT = false

/**
 * Extremos de la cortina tal como los ve la capa: codigos de estacion (para el
 * readout) y coordenadas en grados.
 *
 * En R4.5 el unico origen previsto es el par pin+hover (`sliceSrc = 0` del
 * oraculo); `Manual A/B` y `Fixed point` estan diferidos.
 */
data class SliceEndpoints(
    val codeA: String,
    val latA: Float,
    val lonA: Float,
    val codeB: String,
    val latB: Float,
    val lonB: Float,
) {
    val pair: SlicePair get() = SlicePair(codeA, codeB)
}

/**
 * Plan de un frame de la cortina.
 *
 * `vertices`/`indices`/`texels` solo llegan llenos con [SliceAction.Rebuild];
 * con [SliceAction.Keep] o [SliceAction.Hide] son `null` y la GPU conserva lo
 * que hubiera (el oraculo retorna sin tocar la textura anterior).
 */
data class SliceFramePlan(
    val action: SliceAction,
    val vertices: FloatArray? = null,
    val indices: IntArray? = null,
    val texels: ByteArray? = null,
    val peakLogNe: Float = 0.0f,
    val pair: SlicePair? = null,
    val seg: Int = SLICE_SEG,
    val lvl: Int = SLICE_LVL,
) {
    /** `true` solo cuando hay malla y textura nuevas que subir a la GPU. */
    val hasUpload: Boolean
        get() = action == SliceAction.Rebuild && vertices != null && indices != null && texels != null
}

/**
 * Decide y prepara la cortina para un frame.
 *
 * Orden del oraculo (`App.cpp:3909-3930`): sin par valido no se dibuja; mismo
 * par sin `force` no se rehornea; sin volumen valido no se rehornea (se
 * conserva lo anterior); en cualquier otro caso se rehornea.
 *
 * La capa apagada gana sobre todo lo demas: [SliceAction.Hide] sin tocar datos.
 */
fun planSliceFrame(
    showSlice: Boolean,
    volume: DensityVolume?,
    endpoints: SliceEndpoints?,
    current: SlicePair?,
    force: Boolean = false,
    seg: Int = SLICE_SEG,
    lvl: Int = SLICE_LVL,
): SliceFramePlan {
    if (!showSlice) return SliceFramePlan(SliceAction.Hide, pair = current, seg = seg, lvl = lvl)
    val e = endpoints ?: return SliceFramePlan(SliceAction.Hide, pair = current, seg = seg, lvl = lvl)
    val action = sliceAction(e.codeA, e.codeB, volume?.valid == true, current, force)
    if (action != SliceAction.Rebuild) {
        return SliceFramePlan(action, pair = current ?: e.pair, seg = seg, lvl = lvl)
    }
    val v = volume ?: return SliceFramePlan(SliceAction.Keep, pair = current, seg = seg, lvl = lvl)
    val arc = sliceArc(e.latA, e.lonA, e.latB, e.lonB)
    val samples = buildSliceValues(v, arc, seg, lvl)
    return SliceFramePlan(
        action = SliceAction.Rebuild,
        vertices = sliceVertices(arc, v.altMinKm.toFloat(), v.altMaxKm.toFloat(), seg, lvl),
        indices = sliceIndices(seg, lvl),
        texels = sliceTextureRgba(samples.values, seg, lvl, SLICE_COLORMAP),
        peakLogNe = samples.peakLogNe,
        pair = e.pair,
        seg = seg,
        lvl = lvl,
    )
}

/**
 * Estado que el HUD necesita para el readout de la cortina.
 *
 * `hasData` es "la GPU tiene una malla horneada", no "hay un par elegido": el
 * oraculo solo imprime `A -> B  pk ...` cuando `sliceLayer.hasData()`
 * (`App.cpp:5338`).
 */
data class SliceStatus(
    val codeA: String?,
    val codeB: String?,
    val peakLogNe: Float,
    val hasData: Boolean,
)

/**
 * Guarda de `dispose` idempotente.
 *
 * El oraculo libera handles sin repetir la llamada (`SliceLayer::destroy`,
 * `SliceLayer.cpp:148-157`); aqui la idempotencia es explicita y testeable en
 * JVM: la primera llamada devuelve `true` y las siguientes `false`, de modo
 * que el renderer no vuelve a pedir `glDelete*` con nombres ya liberados.
 */
internal class SliceDisposeGuard {

    private var done = false

    /** `true` si el dispose ya se ejecuto. */
    val disposed: Boolean get() = done

    /**
     * Marca el dispose como ejecutado.
     *
     * @return `true` la primera vez (hay que liberar) y `false` en las
     *   siguientes (no hay nada que liberar).
     */
    fun markDisposed(): Boolean {
        if (done) return false
        done = true
        return true
    }
}
