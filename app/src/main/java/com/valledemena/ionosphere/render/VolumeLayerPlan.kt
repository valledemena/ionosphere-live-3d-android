package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.DensityVolume
import com.valledemena.ionosphere.core.iono.VOL_ALT_MAX_KM
import com.valledemena.ionosphere.core.iono.VOL_ALT_MIN_KM
import com.valledemena.ionosphere.render.AtmoShaderConstants

/**
 * R4.6.3 (density + chapman): plan puro del pase volumetrico Ne(h).
 *
 * Espejo de la parte de render del oraculo (`src/Ionosphere/VolumeRenderer.h`,
 * `VolumeRenderer.cpp` y `src/lib/iono/densityVolume.ts` seccion W-4 F2 del pin
 * web `8cdc334`): la rejilla, el layout, el clamp de opacidad, los colores
 * ganadores Chapman y la eleccion de modo via `volumeModeIndex`.
 *
 * Decisiones pre-registradas (seccion 4.5 del plan R4, `docs/r4/PLAN-R4-TIEMPO-PROFUNDIDAD.md`):
 *
 * - `showVolume=false` por defecto, como el oraculo.
 * - **64 pasos de referencia**; el unico mando de coste es el volumen. Android
 *   no tiene `atmoQuality`, asi que `VolumeQuality` es su propio parametro y
 *   **nunca** alimenta los pasos de la atmosfera (12/4 de R3.4, intactos).
 * - Con el volumen apagado el coste es cero: ni subida de textura ni draw
 *   ([VolumeFramePlan.upload] y [VolumeFramePlan.draw] son `null`).
 * - `density` y `chapman` son modos del mismo volumen: no cambian la rejilla ni
 *   el layout, se conmutan en runtime sin recompilar y comparten los mismos
 *   pasos y la misma tabla.
 *
 * Puro: sin GL, sin UI, sin red.
 */

/** Estado inicial del volumen: apagado (`VolumeRenderer.h`, `visible=false`). */
const val VOLUME_VISIBLE_DEFAULT = false

/** Opacidad por defecto y rango (`VolumeRenderer.h:37`, `volumeOpacityClamp`). */
const val VOLUME_OPACITY_DEFAULT = 1.0f
const val VOLUME_OPACITY_MIN = 0.0f
const val VOLUME_OPACITY_MAX = 3.0f

/** Pasos de referencia del raymarch (plan R4 seccion 4.5). */
const val VOL_STEPS_REF = 64
const val VOL_STEPS_MIN = 16
const val VOL_STEPS_MAX = 64

/**
 * Modo de pintado del volumen.
 *
 * `DENSITY` colorea por el campo muestreado (colormap viridis); `CHAPMAN`
 * colorea por la capa ganadora D/E/F1/F2 (la rama `u_colorMode==1` de `VOL_FRAG`
 * del oraculo, byte-igual a `CHAPMAN_WINNER_COLORS`).
 */
enum class VolumeMode(val shaderIndex: Int) {
    DENSITY(0),
    CHAPMAN(1),
}

/**
 * Coste del volumen. `steps` es el unico parametro de coste y va de 16 a 64,
 * con 64 como referencia; no toca la atmosfera.
 */
enum class VolumeQuality(val steps: Int) {
    LOW(VOL_STEPS_MIN),
    MEDIUM(32),
    HIGH(VOL_STEPS_REF),
}

/** Clamp [0,3] de la opacidad (`volumeOpacityClamp`, `densityVolume.ts`). */
fun volumeOpacityClamp(v: Float): Float {
    if (!(v > VOLUME_OPACITY_MIN)) return VOLUME_OPACITY_MIN
    if (v > VOLUME_OPACITY_MAX) return VOLUME_OPACITY_MAX
    return v
}

/** Indice de modo que entiende el shader (`volumeModeIndex`, `densityVolume.ts`). */
fun volumeModeIndex(mode: VolumeMode): Int = mode.shaderIndex

/** Colores ganadores D/E/F1/F2, byte-iguales a la rama Chapman de `VOL_FRAG`. */
val CHAPMAN_WINNER_COLORS: List<Triple<Float, Float, Float>> = listOf(
    Triple(1.00f, 0.55f, 0.10f),
    Triple(0.20f, 0.85f, 0.30f),
    Triple(1.00f, 0.90f, 0.20f),
    Triple(0.20f, 0.80f, 1.00f),
)

/** Estado del pase volumetrico para el HUD (R4.6.3). */
data class VolumeStatus(
    val showVolume: Boolean,
    val hasData: Boolean,
)

/** Radio del planeta en km (para los radios del proxy del volumen). */
const val PLANET_R_KM = 6371.0

/** Radio exterior de la esfera proxy: 500 km sobre el radio del planeta. */
fun volumeOuterRadius(altMaxKm: Double): Float =
    (AtmoShaderConstants.PLANET_R + (altMaxKm / PLANET_R_KM)).toFloat()

/**
 * Radio interior de la esfera proxy: planeta + `altMinKm` (60 km), espejo
 * C++ `VolumeRenderer.cpp:257` (`1 + altMinKm/6371`, `explode_ = 1.0`). El
 * shader mapea `altNorm = (r - uInnerR)/(uOuterR - uInnerR)` en [0,1] = alt
 * 60..500 km.
 */
fun volumeInnerRadius(altMinKm: Double): Float =
    (AtmoShaderConstants.PLANET_R + (altMinKm / PLANET_R_KM)).toFloat()

/**
 * Huella del volumen para decidir si la textura 3D debe subirse de nuevo.
 *
 * El oraculo reconstruye el volumen cada ~5 s con `timestamp=0`, asi que la
 * clave no puede ser el timestamp: es un FNV-1a de las celdas muestreadas (un
 * paso de 8) mas resolucion y rango. **No** es criptografica: solo detecta
 * "volumen nuevo", que es lo que decide la subida.
 */
fun volumeFingerprint(volume: DensityVolume): String {
    var hash = 0x811c9dc5L
    val step = 8
    var i = 0
    while (i < volume.data.size) {
        val bits = volume.data[i].toRawBits()
        hash = hash xor (bits and 0xffffffffL)
        hash = hash * 0x01000193L and 0xffffffffL
        hash = hash xor ((bits ushr 32) and 0xffffffffL)
        hash = hash * 0x01000193L and 0xffffffffL
        i += step
    }
    return "${volume.lonRes}x${volume.latRes}x${volume.altRes}" +
        "|${volume.altMinKm}|${volume.altMaxKm}|${volume.valid}|" +
        java.lang.Long.toHexString(hash)
}

/**
 * Texels R8 de densidad: `trunc(clamp(data,0,1) * 255)`.
 *
 * Truncado, no redondeo: es el `(unsigned char)` del oraculo
 * (`builders.ts:563`, pin P3-184).
 */
fun packDensityTexels(volume: DensityVolume): ByteArray {
    val out = ByteArray(volume.data.size)
    for (i in volume.data.indices) {
        val v = volume.data[i]
        val c = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
        out[i] = (c * 255.0).toInt().toByte()
    }
    return out
}

/** Texels R8 de capa ganadora (0=D, 1=E, 2=F1, 3=F2); el shader multiplica por 255. */
fun packLayerTexels(volume: DensityVolume): ByteArray {
    val out = ByteArray(volume.layer.size)
    for (i in volume.layer.indices) {
        val w = volume.layer[i]
        out[i] = (if (w < 0) 0 else if (w > 3) 3 else w).toByte()
    }
    return out
}

/** Texturas nuevas que hay que subir a la GPU (`updateVolumeTextures`). */
data class VolumeUpload(
    val density: ByteArray,
    val layer: ByteArray,
    val lonRes: Int,
    val latRes: Int,
    val altRes: Int,
    val fingerprint: String,
)

/** Parametros del draw del frame. */
data class VolumeDraw(
    val steps: Int,
    val mode: VolumeMode,
    val opacity: Float,
)

/**
 * Plan de un frame del volumen.
 *
 * - `upload == null` y `draw == null` con el volumen apagado: **cero coste**
 *   (ni `glTexImage3D` ni draw).
 * - `upload != null` solo cuando llega un volumen con huella nueva.
 * - `draw != null` solo con el volumen encendido, volumen valido y textura
 *   disponible (recien subida o ya residente).
 */
data class VolumeFramePlan(
    val upload: VolumeUpload?,
    val draw: VolumeDraw?,
) {
    val hasUpload: Boolean get() = upload != null
    val hasDraw: Boolean get() = draw != null
}

/**
 * Decide subida y draw del frame.
 *
 * @param showVolume interruptor del volumen (`false` = cero coste).
 * @param volume volumen vivo; `null` o invalido = nada que dibujar.
 * @param uploadedKey huella actualmente residente en la GPU; `null` = sin textura.
 * @param hasData si la capa GL ya tiene textura subida (aunque sea de otro volumen).
 */
fun planVolumeFrame(
    showVolume: Boolean,
    volume: DensityVolume?,
    uploadedKey: String?,
    hasData: Boolean,
    mode: VolumeMode,
    quality: VolumeQuality,
    opacity: Float = VOLUME_OPACITY_DEFAULT,
): VolumeFramePlan {
    if (!showVolume) return VolumeFramePlan(upload = null, draw = null)
    val v = volume ?: return VolumeFramePlan(upload = null, draw = null)
    if (!v.valid || v.data.isEmpty() || v.layer.size != v.data.size) {
        return VolumeFramePlan(upload = null, draw = null)
    }
    // La esfera proxy y el mapeo de altitud del shader quedan fijos en la
    // rejilla del contrato (60..500 km): una rejilla distinta no se dibuja
    // antes que rehacer la geometria, y la degrada a "sin volumen" declarado.
    if (v.altMinKm != VOL_ALT_MIN_KM || v.altMaxKm != VOL_ALT_MAX_KM) {
        return VolumeFramePlan(upload = null, draw = null)
    }
    val key = volumeFingerprint(v)
    val upload = if (key != uploadedKey) {
        VolumeUpload(
            density = packDensityTexels(v),
            layer = packLayerTexels(v),
            lonRes = v.lonRes,
            latRes = v.latRes,
            altRes = v.altRes,
            fingerprint = key,
        )
    } else {
        null
    }
    val canDraw = hasData || upload != null
    val draw = if (canDraw) {
        VolumeDraw(
            steps = quality.steps,
            mode = mode,
            opacity = volumeOpacityClamp(opacity),
        )
    } else {
        null
    }
    return VolumeFramePlan(upload = upload, draw = draw)
}
