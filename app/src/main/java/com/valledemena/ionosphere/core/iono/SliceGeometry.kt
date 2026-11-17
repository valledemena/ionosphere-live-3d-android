package com.valledemena.ionosphere.core.iono

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometria y valores de la cortina Ne(h) sobre el gran circulo A-B, portados
 * de `src/Ionosphere/SliceLayer.cpp` y de `App::rebuildSlice`
 * (`src/App.cpp:3886-3951`).
 *
 * R4.5: solo la parte pura. La textura RGBA, el quad GL y el dispose son P4.
 *
 * Aritmetica en `Float` con el mismo orden de operaciones que el oraculo.
 *
 * Nota de alcance (dictamen `from-glm/0403`, seccion 5):
 * - `SLICE_LVL = 20` es constante **de la cortina**; no es el `altRes` del
 *   volumen (48 o 60). Las alturas salen de `SLICE_LVL` y los bounds salen del
 *   **volumen**, nunca de los defaults de `SliceLayer.h`.
 * - La convencion de meridianos es `theta = pi - lon`, jamas `radians(lon)` a
 *   secas (historial de meridianos rotados 180 grados).
 */

/** Tramos a lo largo del trayecto (oraculo `SliceLayer::SEG`). */
const val SLICE_SEG = 64

/** Niveles de altura de la cortina (oraculo `SliceLayer::LVL`). */
const val SLICE_LVL = 20

/** Radio terrestre que usa el oraculo para el vertice (`r = 1 + alt/6371`). */
const val SLICE_EARTH_RADIUS_KM = 6371.0f

/** Opacidad por defecto de la capa (oraculo `SliceLayer.h:39`). */
const val SLICE_OPACITY_DEFAULT = 0.9f

/** Techo de opacidad (oraculo `setOpacity` clampa a `[0, 1.5]`). */
const val SLICE_OPACITY_MAX = 1.5f

/** Ganancia de alfa por densidad (oraculo `SliceLayer.cpp:118`). */
const val SLICE_ALPHA_GAIN = 1.2f

private const val PI_F = 3.14159265f

/**
 * Altura del nivel [j] de una cortina de [lvl] niveles:
 * `altMin + (altMax - altMin) * (j / (lvl - 1))^1.5`.
 *
 * `altMinKm`/`altMaxKm` deben venir del volumen activo (`lastVolume`), no de
 * constantes de la capa. Con `lvl <= 1` devuelve `altMinKm`.
 */
fun sliceAltKm(j: Int, altMinKm: Float, altMaxKm: Float, lvl: Int = SLICE_LVL): Float {
    val f = if (lvl <= 1) 0.0f else j.toFloat() / (lvl - 1).toFloat()
    return altMinKm + (altMaxKm - altMinKm) * f.toDouble().pow(1.5).toFloat()
}

/**
 * Direccion unitaria en el marco del motor, en `Float`:
 * `phi = (90 - lat)`, `theta = pi - lon`.
 *
 * Pines: `(0,0) -> (-1,0,0)`, `(0,90E) -> (0,0,1)`, polo -> `(0,1,0)`.
 * Existe aparte de [densityDir] (R4.4, `Double`) para no mezclar precisiones
 * dentro de la cortina; la formula es la misma.
 */
fun densityDirF(latDeg: Float, lonDeg: Float): FloatArray {
    val phi = (90.0f - latDeg) * PI_F / 180.0f
    val theta = PI_F - lonDeg * PI_F / 180.0f
    val sn = sin(phi)
    return floatArrayOf(sn * cos(theta), cos(phi), sn * sin(theta))
}

/** Normaliza un vector de 3 componentes; un vector nulo vuelve nulo. */
private fun normalizeF(v: FloatArray): FloatArray {
    val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    // El oraculo (glm::normalize) dividiria por cero y daria NaN. Aqui se
    // devuelve el vector nulo: unico desvio declarado, evita envenenar la
    // textura con NaN en el caso antipodal exacto (omega = pi).
    if (len == 0.0f) return floatArrayOf(0.0f, 0.0f, 0.0f)
    return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
}

/**
 * Arco A-B de la cortina: extremos normalizados, apertura angular y la
 * decision slerp/nlerp.
 *
 * `useSlerp` es `omega > 1e-4 && omega < pi - 1e-4` (oraculo
 * `SliceLayer.cpp:75` y `App.cpp:3928`): los dos extremos degenerados
 * (puntos coincidentes y antipodales) usan interpolacion lineal normalizada.
 */
data class SliceArc(
    val aUnit: FloatArray,
    val bUnit: FloatArray,
    val omega: Float,
    val useSlerp: Boolean,
)

/** Construye el arco entre dos pares lat/lon en grados. */
fun sliceArc(aLatDeg: Float, aLonDeg: Float, bLatDeg: Float, bLonDeg: Float): SliceArc {
    val na = normalizeF(densityDirF(aLatDeg, aLonDeg))
    val nb = normalizeF(densityDirF(bLatDeg, bLonDeg))
    var dot = na[0] * nb[0] + na[1] * nb[1] + na[2] * nb[2]
    dot = if (dot < -1.0f) -1.0f else if (dot > 1.0f) 1.0f else dot
    val omega = acos(dot)
    return SliceArc(na, nb, omega, omega > 1e-4f && omega < PI_F - 1e-4f)
}

/**
 * Direccion unitaria a la fraccion [s] del arco (`s = 0` en A, `s = 1` en B).
 *
 * Slerp: `(a*sin((1-s)*omega) + b*sin(s*omega)) / sin(omega)`.
 * Si no: `normalize(a*(1-s) + b*s)`.
 *
 * No se renormaliza el resultado del slerp: el oraculo tampoco lo hace y
 * normalizar aqui cambiaria el redondeo.
 */
fun sliceDirectionAt(arc: SliceArc, s: Float): FloatArray {
    if (arc.useSlerp) {
        val so = sin(arc.omega)
        val wa = sin((1.0f - s) * arc.omega)
        val wb = sin(s * arc.omega)
        return floatArrayOf(
            (arc.aUnit[0] * wa + arc.bUnit[0] * wb) / so,
            (arc.aUnit[1] * wa + arc.bUnit[1] * wb) / so,
            (arc.aUnit[2] * wa + arc.bUnit[2] * wb) / so,
        )
    }
    return normalizeF(
        floatArrayOf(
            arc.aUnit[0] * (1.0f - s) + arc.bUnit[0] * s,
            arc.aUnit[1] * (1.0f - s) + arc.bUnit[1] * s,
            arc.aUnit[2] * (1.0f - s) + arc.bUnit[2] * s,
        ),
    )
}

/**
 * Radio del vertice en el marco del motor: `r = 1 + altKm / 6371`.
 * (Oracolo `SliceLayer.cpp:87`; `App.cpp` no lo usa porque solo hornea datos.)
 */
fun sliceVertexRadius(altKm: Float): Float = 1.0f + altKm / SLICE_EARTH_RADIUS_KM

/** Valores horneados de la cortina y su pico. */
data class SliceSamples(
    /** `seg * lvl` valores [0,1], layout `[j * seg + i]` (nivel, tramo). */
    val values: FloatArray,
    /** Maximo de [values]. */
    val peak: Float,
    /** `8 + 4.5 * peak` (oraculo `App.cpp:3950`). */
    val peakLogNe: Float,
)

/**
 * Llena la malla `SEG x LVL` muestreando el volumen a lo largo del arco.
 *
 * Espejo de `App::rebuildSlice` (`App.cpp:3928-3951`):
 * - `f = j / (lvl - 1)` (sin guarda en el oraculo: `LVL` es 20);
 * - `altKm` con bounds **del volumen**;
 * - `s = i / (seg - 1)`;
 * - `lat = asin(clamp(dir.y))`, `lon = pi - atan2(dir.z, dir.x)`, normalizada
 *   a `[-180, 180]`;
 * - valor por [sampleVolumeAt];
 * - `peak` = maximo de los valores.
 *
 * No muta el volumen.
 */
fun buildSliceValues(
    volume: DensityVolume,
    arc: SliceArc,
    seg: Int = SLICE_SEG,
    lvl: Int = SLICE_LVL,
): SliceSamples {
    val altMinKm = volume.altMinKm.toFloat()
    val altMaxKm = volume.altMaxKm.toFloat()
    val values = FloatArray(seg * lvl)
    var peak = 0.0f
    for (j in 0 until lvl) {
        val altKm = sliceAltKm(j, altMinKm, altMaxKm, lvl)
        for (i in 0 until seg) {
            val s = if (seg <= 1) 0.0f else i.toFloat() / (seg - 1).toFloat()
            val dir = sliceDirectionAt(arc, s)
            val lat = asin(if (dir[1] < -1.0f) -1.0f else if (dir[1] > 1.0f) 1.0f else dir[1]) *
                180.0f / PI_F
            var lon = (PI_F - atan2(dir[2], dir[0])) * 180.0f / PI_F
            if (lon > 180.0f) lon -= 360.0f
            if (lon < -180.0f) lon += 360.0f
            val v = sampleVolumeAt(volume, lat, lon, altKm)
            values[j * seg + i] = v
            if (v > peak) peak = v
        }
    }
    return SliceSamples(values, peak, slicePeakLog(peak))
}

/** `8 + 4.5 * peak`: misma normalizacion que el volumen. */
fun slicePeakLog(peak: Float): Float = 8.0f + 4.5f * peak

/**
 * Alfa de la textura para una densidad [d]: `clamp(1.2 * d, 0, 1)`.
 * (Oracolo `SliceLayer.cpp:118`; el rojo/verde/azul salen del colormap.)
 */
fun sliceAlpha(d: Float): Float {
    val a = SLICE_ALPHA_GAIN * d
    return if (a < 0.0f) 0.0f else if (a > 1.0f) 1.0f else a
}

/** Opacidad de la capa clampeada a `[0, 1.5]` (oraculo `setOpacity`). */
fun clampSliceOpacity(opacity: Float): Float =
    if (opacity < 0.0f) 0.0f else if (opacity > SLICE_OPACITY_MAX) SLICE_OPACITY_MAX else opacity

/** Par de extremos por codigo de estacion; sin par valido no se dibuja. */
data class SlicePair(val codeA: String, val codeB: String)

/** Que hacer con la capa en un frame. */
enum class SliceAction {
    /** Sin par valido (o codigos iguales): no se dibuja. */
    Hide,

    /** Se conserva lo que ya habia: mismo par sin `force`, o sin volumen. */
    Keep,

    /** Hay que reconstruir geometria y textura con el par nuevo. */
    Rebuild,
}

/**
 * Decide si tocar la capa, espejo de `App::rebuildSlice`
 * (`App.cpp:3909-3921`):
 * - sin A, sin B o `A == B` por codigo -> [SliceAction.Hide] (D3: sin par
 *   valido no se dibuja);
 * - mismo par y sin `force` -> [SliceAction.Keep] (early-out);
 * - volumen invalido -> [SliceAction.Keep] (el oraculo retorna sin tocar la
 *   textura anterior);
 * - en cualquier otro caso -> [SliceAction.Rebuild].
 *
 * `force` es lo que usa el oraculo al llegar datos nuevos (`App.cpp:2745`).
 */
fun sliceAction(
    codeA: String?,
    codeB: String?,
    volumeValid: Boolean,
    current: SlicePair?,
    force: Boolean,
): SliceAction {
    if (codeA == null || codeB == null || codeA == codeB) return SliceAction.Hide
    if (!force && current != null && current.codeA == codeA && current.codeB == codeB) {
        return SliceAction.Keep
    }
    if (!volumeValid) return SliceAction.Keep
    return SliceAction.Rebuild
}
