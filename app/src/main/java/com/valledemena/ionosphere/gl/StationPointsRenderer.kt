package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.render.Colormaps
import com.valledemena.ionosphere.render.stationColorRange
import com.valledemena.ionosphere.render.stationFoValues
import com.valledemena.ionosphere.render.stationPositions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Capa GL de puntos de ionosondas (R3.4, pieza B).
 *
 * Programa, VAO y VBO propios: no se refactoriza `GlobeRenderer`. El dibujo
 * va despues del globo (depth test ON, depth write OFF) y antes de la
 * atmosfera. `gl_PointSize` se escribe siempre en el vertex shader; en GLES no
 * existe `GL_PROGRAM_POINT_SIZE`.
 */
internal class StationPointsRenderer {

    private var program = 0
    private var vao = 0
    private var positionBuffer = 0
    private var foBuffer = 0
    private var cmapTexture = 0
    private var count = 0
    private var uploaded: List<com.valledemena.ionosphere.data.model.IonosondeStation>? = null
    private var maxPointSize = 70f

    private var aPosition = -1
    private var aFo = -1
    private var uMvp = -1
    private var uViewModel = -1
    private var uPointSize = -1
    private var uPixelRatio = -1
    private var uMaxPointSize = -1
    private var uCmap = -1
    private var uTime = -1
    private var uHighlight = -1
    private var uHasData = -1

    fun init() {
        program = ShaderProgram.create(STATION_VERT, STATION_FRAG)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aFo = GLES20.glGetAttribLocation(program, "aFo")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uViewModel = GLES20.glGetUniformLocation(program, "uViewModel")
        uPointSize = GLES20.glGetUniformLocation(program, "uPointSize")
        uPixelRatio = GLES20.glGetUniformLocation(program, "uPixelRatio")
        uMaxPointSize = GLES20.glGetUniformLocation(program, "uMaxPointSize")
        uCmap = GLES20.glGetUniformLocation(program, "uCmap")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uHighlight = GLES20.glGetUniformLocation(program, "uHighlight")
        uHasData = GLES20.glGetUniformLocation(program, "uHasData")

        vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        val buffers = IntArray(2).also { GLES20.glGenBuffers(2, it, 0) }
        positionBuffer = buffers[0]
        foBuffer = buffers[1]
        cmapTexture = buildColormapTexture()

        val range = IntArray(2)
        GLES20.glGetIntegerv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0)
        val driverMax = range[1].toFloat().takeIf { it.isFinite() && it > 0f } ?: 1f
        maxPointSize = minOf(driverMax, 70f).coerceAtLeast(1f)
        Log.i(
            TAG,
            "puntos GL listos: GL_ALIASED_POINT_SIZE_RANGE=${range[0]}..${range[1]}, techo=${maxPointSize}px",
        )
    }

    fun draw(
        data: IonosondeData?,
        pointSize: Float,
        pixelRatio: Float,
        mvp: FloatArray,
        viewModel: FloatArray,
        timeSeconds: Float,
    ) {
        val stations = data?.stations ?: return
        if (stations.isEmpty()) return
        if (uploaded !== stations) upload(stations)
        if (count <= 0) return

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uViewModel, 1, false, viewModel, 0)
        GLES20.glUniform1f(uPointSize, pointSize)
        GLES20.glUniform1f(uPixelRatio, pixelRatio)
        GLES20.glUniform1f(uMaxPointSize, maxPointSize)
        GLES20.glUniform1f(uTime, timeSeconds)
        GLES20.glUniform1f(uHighlight, 0f)
        GLES20.glUniform1f(uHasData, 1f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cmapTexture)
        GLES20.glUniform1i(uCmap, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES30.glBindVertexArray(0)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun upload(stations: List<com.valledemena.ionosphere.data.model.IonosondeStation>) {
        val positions = stationPositions(stations)
        val (min, max) = stationColorRange(stations.mapNotNull { it.foF2 })
        val fos = stationFoValues(stations, min, max)
        count = stations.size

        GLES30.glBindVertexArray(vao)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBuffer)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            positions.size * 4,
            positions.toFloatBuffer(),
            GLES20.GL_DYNAMIC_DRAW,
        )
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, foBuffer)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            fos.size * 4,
            fos.toFloatBuffer(),
            GLES20.GL_DYNAMIC_DRAW,
        )
        GLES20.glEnableVertexAttribArray(aFo)
        GLES20.glVertexAttribPointer(aFo, 1, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
        uploaded = stations
    }

    private fun buildColormapTexture(): Int {
        val n = 256
        val buffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until n) {
            val rgb = Colormaps.sample("viridis", i.toDouble() / (n - 1).toDouble())
            buffer.put((rgb[0] * 255.0).toInt().coerceIn(0, 255).toByte())
            buffer.put((rgb[1] * 255.0).toInt().coerceIn(0, 255).toByte())
            buffer.put((rgb[2] * 255.0).toInt().coerceIn(0, 255).toByte())
            buffer.put(255.toByte())
        }
        buffer.position(0)
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            n,
            1,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texture
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(this)
            .also { it.position(0) }

    private companion object {
        private const val TAG = "StationPointsRenderer"

        private const val STATION_VERT = """
uniform mat4 uMvp;
uniform mat4 uViewModel;
uniform float uPointSize;
uniform float uPixelRatio;
uniform float uMaxPointSize;
uniform float uTime;
uniform float uHighlight;
in vec3 aPosition;
in float aFo;
out float vF;
out float vFade;

void main() {
    vec4 mv = uViewModel * vec4(aPosition, 1.0);
    gl_Position = uMvp * vec4(aPosition, 1.0);
    float dist = -mv.z;
    float sizePx = clamp(140.0 * uPointSize / 11.0 / dist, 3.0, min(26.0 * uPointSize / 11.0, 35.0)) * uPixelRatio;
    gl_PointSize = min(sizePx, uMaxPointSize);
    vF = aFo;
    vFade = 0.75 + 0.25 * sin(uTime * 2.2 + aPosition.x * 40.0 + aPosition.y * 33.0);
    if (uHighlight > 0.5) vFade = 0.6 + 0.4 * sin(uTime * 6.0);
}
"""

        private const val STATION_FRAG = """
precision mediump float;

uniform sampler2D uCmap;
uniform float uHasData;
in float vF;
in float vFade;
out vec4 fragColor;

void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float d = dot(c, c);
    if (d > 1.0) discard;
    float a = smoothstep(1.0, 0.25, d) * vFade;
    vec3 col = uHasData > 0.5 ? texture(uCmap, vec2(vF, 0.5)).rgb : vec3(0.55);
    float halo = smoothstep(1.0, 0.55, d) - smoothstep(0.55, 0.0, d);
    fragColor = vec4(col + vec3(0.9) * halo * 0.35, a * (uHasData > 0.5 ? 0.95 : 0.45));
}
"""
    }
}
