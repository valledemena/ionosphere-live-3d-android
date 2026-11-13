package com.valledemena.ionosphere.gl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Configuracion opcional de medicion (W11), leida de
 * `getExternalFilesDir(null)/measure.json`.
 *
 * El fichero viaja por adb y **no** forma parte del APK: el arbol medido es el
 * mismo que el commiteado. Si falta o no es valido, [from] devuelve null y el
 * renderer usa exactamente los defaults de produccion (fail-secure).
 */
data class MeasureConfig(
    val cameraEye: FloatArray,
    val sunDir: FloatArray,
    val fov: Float,
    val drawAtmosphere: Boolean,
    val captureName: String?,
    val frozenModel: Boolean,
    val bloom: Boolean,
    /** Gate T4: permite medir la misma corrida con la capa de estaciones OFF. */
    val stations: Boolean = true,
) {
    companion object {
        private const val FILE_NAME = "measure.json"
        private const val CAPTURE_PATTERN = "^[A-Za-z0-9._-]+\\.png$"

        fun from(context: Context): MeasureConfig? {
            val dir = context.getExternalFilesDir(null) ?: return null
            val file = File(dir, FILE_NAME)
            if (!file.isFile) return null
            return try {
                val json = JSONObject(file.readText())
                val camera = json.getJSONArray("cam").toVec3() ?: return null
                val sun = json.getJSONArray("sun").toVec3() ?: return null

                val fovRaw = json.opt("fov")
                if (fovRaw !is Number) return null
                val fov = fovRaw.toDouble()
                if (!fov.isFinite() || fov <= 0.0 || fov >= 180.0) return null

                val rawCapture = json.opt("capture")
                if (rawCapture !is String) return null
                val capture = when {
                    rawCapture.isEmpty() -> null
                    isSafeCapture(rawCapture) -> rawCapture
                    else -> return null
                }

                val model = json.opt("model")
                if (model !is String) return null
                if (model != "product" && model != "identity") return null

                val atmoRaw = json.opt("atmo")
                if (atmoRaw !is Boolean) return null
                val bloomRaw = json.opt("bloom")
                if (bloomRaw !is Boolean) return null
                val stationsRaw = json.opt("stations")
                if (stationsRaw != null && stationsRaw !is Boolean) return null
                val stations = stationsRaw as? Boolean ?: true

                MeasureConfig(
                    cameraEye = camera,
                    sunDir = sun,
                    fov = fov.toFloat(),
                    drawAtmosphere = atmoRaw,
                    captureName = capture,
                    frozenModel = model == "identity",
                    bloom = bloomRaw,
                    stations = stations,
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun isSafeCapture(name: String): Boolean =
            name.length <= 128 && name.matches(Regex(CAPTURE_PATTERN))

        private fun JSONArray.toVec3(): FloatArray? {
            if (length() != 3) return null
            val out = FloatArray(3)
            for (i in 0..2) {
                val v = opt(i)
                if (v !is Number) return null
                val d = v.toDouble()
                if (!d.isFinite() || kotlin.math.abs(d) > 1_000_000.0) return null
                out[i] = d.toFloat()
                if (!out[i].isFinite()) return null
            }
            return out
        }
    }
}
