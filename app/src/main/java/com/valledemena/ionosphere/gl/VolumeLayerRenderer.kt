package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import com.valledemena.ionosphere.core.SphereMesh
import com.valledemena.ionosphere.core.iono.VOL_ALT_MAX_KM
import com.valledemena.ionosphere.core.iono.VOL_ALT_MAX_KM
import com.valledemena.ionosphere.core.iono.VOL_ALT_MIN_KM
import com.valledemena.ionosphere.render.AtmoShaderConstants
import com.valledemena.ionosphere.render.Colormaps
import com.valledemena.ionosphere.render.VolumeShaderSource
import com.valledemena.ionosphere.render.VolumeUpload
import com.valledemena.ionosphere.render.VolumeDraw
import com.valledemena.ionosphere.render.VOL_STEPS_MAX
import com.valledemena.ionosphere.render.volumeInnerRadius
import com.valledemena.ionosphere.render.volumeOuterRadius
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Capa GL del pase volumetrico Ne(h) (R4.6.3 density + chapman).
 *
 * Espejo de `VolumeRenderer` (`src/Ionosphere/VolumeRenderer.cpp`): esfera
 * proxy con culling frontal (se entra por la cara interior), texturas 3D R8 de
 * densidad y capa, colormap viridis y acumulacion front-to-back.
 *
 * Reglas de coste (DoD de R4.6.3):
 *
 * - El volumen apagado no toca GL: [upload] y [draw] no se llaman (el plan ya
 *   devuelve `null`), de modo que no hay ni subida ni draw.
 * - La textura se sube una sola vez por volumen; [VolumeUpload.fingerprint] es
 *   la clave que decide si hay que repetir.
 * - Los 64 pasos de referencia viven en el shader; `uSteps` es el unico
 *   parametro de coste y no toca la atmosfera.
 *
 * El color de salida esta premultiplicado por alpha (`accumColor`), de modo que
 * el blending es `GL_ONE, GL_ONE_MINUS_SRC_ALPHA`.
 */
internal class VolumeLayerRenderer {

    private val guard = com.valledemena.ionosphere.render.SliceDisposeGuard()

    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var ebo = 0
    private var densityTex = 0
    private var layerTex = 0
    private var colormapTex = 0
    private var indexCount = 0
    private var hasData = false

    private var uMvp = -1
    private var uCamPos = -1
    private var uSunDir = -1
    private var uInnerR = -1
    private var uOuterR = -1
    private var uOpacity = -1
    private var uSteps = -1
    private var uMode = -1
    private var uVolRes = -1
    private var uDensity = -1
    private var uLayer = -1
    private var uColormap = -1
    private var aPosition = -1

    /** `true` cuando la textura 3D esta subida (`VolumeRenderer::hasData`). */
    fun hasData(): Boolean = hasData

    /** `true` si el dispose ya se ejecuto. */
    fun isDisposed(): Boolean = guard.disposed

    /**
     * Radio exterior de la esfera proxy: 500 km sobre el radio del planeta
     * (espejo C++ `VolumeRenderer.cpp:256`).
     */
    val outerRadius: Float = volumeOuterRadius(VOL_ALT_MAX_KM)

    /**
     * Radio interior: superficie del planeta + `altMinKm` (60 km), espejo
     * C++ `VolumeRenderer.cpp:257` (`innerR = 1 + (altMinKm_/6371)*explode_`,
     * `explode_ = 1.0`). El shader mapea `altNorm = (r - uInnerR)/(uOuterR -
     * uInnerR)`, asi el volumen cubre alt 60..500 km sin desplazamiento
     * vertical (G7c_10).
     */
    val innerRadius: Float = volumeInnerRadius(VOL_ALT_MIN_KM)

    fun init() {
        program = ShaderProgram.create(VolumeShaderSource.VOLUME_VERT, VolumeShaderSource.VOLUME_FRAG)
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uCamPos = GLES20.glGetUniformLocation(program, "uCamPos")
        uSunDir = GLES20.glGetUniformLocation(program, "uSunDir")
        uInnerR = GLES20.glGetUniformLocation(program, "uInnerR")
        uOuterR = GLES20.glGetUniformLocation(program, "uOuterR")
        uOpacity = GLES20.glGetUniformLocation(program, "uOpacity")
        uSteps = GLES20.glGetUniformLocation(program, "uSteps")
        uMode = GLES20.glGetUniformLocation(program, "uMode")
        uVolRes = GLES20.glGetUniformLocation(program, "uVolRes")
        uDensity = GLES20.glGetUniformLocation(program, "uDensity")
        uLayer = GLES20.glGetUniformLocation(program, "uLayer")
        uColormap = GLES20.glGetUniformLocation(program, "uColormap")
        // FIX r4.6.5: el vertex shader declara `in vec3 aPosition` (VolumeShaderSource.VOLUME_VERT);
        // pedir "aPos" daba -1 y dejaba el atributo a (0,0,0): triangulos degenerados, 0 fragmentos.
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")

        // Esfera proxy a radio exterior: el vertice ya vive en coordenadas mundo.
        val mesh = SphereMesh.unitSphere(rings = 48, segments = 96)
        val positions = FloatArray(mesh.positions.size)
        for (i in mesh.positions.indices) positions[i] = mesh.positions[i] * outerRadius
        val indices = mesh.indices

        vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        vbo = IntArray(1).also { GLES20.glGenBuffers(1, it, 0) }[0]
        ebo = IntArray(1).also { GLES20.glGenBuffers(1, it, 0) }[0]
        GLES30.glBindVertexArray(vao)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            positions.size * 4,
            positions.toFloatBuffer(),
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * 2,
            indices.toShortBuffer(),
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
        indexCount = indices.size

        densityTex = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        layerTex = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        colormapTex = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colormapTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        val palette = Colormaps.buildPalette("viridis", COLORMAP_SIZE)
        val rgba = ByteArray(COLORMAP_SIZE * 4)
        for (i in 0 until COLORMAP_SIZE) {
            rgba[i * 4 + 0] = (palette[i * 3 + 0] * 255.0).toInt().toByte()
            rgba[i * 4 + 1] = (palette[i * 3 + 1] * 255.0).toInt().toByte()
            rgba[i * 4 + 2] = (palette[i * 3 + 2] * 255.0).toInt().toByte()
            rgba[i * 4 + 3] = 255.toByte()
        }
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, COLORMAP_SIZE, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba.toDirectBuffer(),
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        Log.i(TAG, "volumen GL listo (3D R8 density+layer, colormap viridis ${COLORMAP_SIZE}, ${VOL_STEPS_MAX} pasos)")
    }

    /**
     * Sube densidad y capa (`updateVolumeTextures`). Un tamano incoherente no
     * toca la GPU: no hay reescritura parcial.
     */
    fun upload(upload: VolumeUpload) {
        if (densityTex == 0 || layerTex == 0) return
        if (upload.density.size != upload.lonRes * upload.latRes * upload.altRes) return
        if (upload.layer.size != upload.density.size) return

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, densityTex)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8,
            upload.lonRes, upload.latRes, upload.altRes, 0,
            GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, upload.density.toDirectBuffer(),
        )
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, 0)

        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, layerTex)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8,
            upload.lonRes, upload.latRes, upload.altRes, 0,
            GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, upload.layer.toDirectBuffer(),
        )
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, 0)

        hasData = true
    }

    /**
     * Dibuja el volumen (`VolumeRenderer::render`).
     *
     * @param mvp `viewProjection` del frame (la esfera ya esta en mundo).
     * @param camPos ojo en el marco del motor/mundo.
     * @param sunDir sol unitario en el mismo marco.
     */
    fun draw(
        draw: VolumeDraw,
        mvp: FloatArray,
        camPos: FloatArray,
        sunDir: FloatArray,
        innerR: Float = innerRadius,
    ) {
        if (program == 0 || vao == 0 || densityTex == 0 || !hasData) return

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform3f(uCamPos, camPos[0], camPos[1], camPos[2])
        GLES20.glUniform3f(uSunDir, sunDir[0], sunDir[1], sunDir[2])
        GLES20.glUniform1f(uInnerR, innerR)
        GLES20.glUniform1f(uOuterR, outerRadius)
        GLES20.glUniform1f(uOpacity, draw.opacity)
        GLES20.glUniform1i(uSteps, draw.steps)
        GLES20.glUniform1i(uMode, draw.mode.shaderIndex)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, densityTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, layerTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colormapTex)
        GLES20.glUniform1i(uDensity, 0)
        GLES20.glUniform1i(uLayer, 1)
        GLES20.glUniform1i(uColormap, 2)
        GLES20.glUniform3i(
            uVolRes,
            VOL_LON_RES, VOL_LAT_RES, VOL_ALT_RES,
        )

        // Premultiplicado: accumColor ya lleva el alpha.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_FRONT)
        GLES30.glBindVertexArray(vao)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    /** Libera handles; idempotente. */
    fun dispose() {
        if (!guard.markDisposed()) return
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (ebo != 0) GLES20.glDeleteBuffers(1, intArrayOf(ebo), 0)
        if (densityTex != 0) GLES20.glDeleteTextures(1, intArrayOf(densityTex), 0)
        if (layerTex != 0) GLES20.glDeleteTextures(1, intArrayOf(layerTex), 0)
        if (colormapTex != 0) GLES20.glDeleteTextures(1, intArrayOf(colormapTex), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        vao = 0
        vbo = 0
        ebo = 0
        densityTex = 0
        layerTex = 0
        colormapTex = 0
        program = 0
        hasData = false
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(this)
            .also { it.position(0) }

    private fun ByteArray.toDirectBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(size)
            .order(ByteOrder.nativeOrder())
            .put(this)
            .also { it.position(0) }

    private fun ShortArray.toShortBuffer(): java.nio.ShortBuffer =
        ByteBuffer.allocateDirect(size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(this)
            .also { it.position(0) }

    private companion object {
        private const val TAG = "VolumeLayerRenderer"
        private const val COLORMAP_SIZE = 256

        private const val VOL_LON_RES = 72
        private const val VOL_LAT_RES = 72
        private const val VOL_ALT_RES = 48
    }
}
