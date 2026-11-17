package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import com.valledemena.ionosphere.render.SliceDisposeGuard
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Capa GL de la cortina Ne(h) (R4.5, P4b).
 *
 * Programa, VAO, VBO, EBO y textura propios: no se refactoriza `GlobeRenderer`.
 * Espejo de `SliceLayer` (`src/Ionosphere/SliceLayer.cpp`):
 *
 * - Vertice `(aPos vec3, aTexCoord vec2)` interleaved, stride `5 * 4`.
 * - `u_mvp` sustituye a `u_model`/`u_view`/`u_projection` del oraculo, que
 *   multiplica la identidad: el resultado es el mismo producto.
 * - Fragmento identico: `fragColor = vec4(c.rgb, c.a * u_opacity)`.
 * - Textura `SEG x LVL` RGBA, `GL_LINEAR` + `GL_CLAMP_TO_EDGE`.
 * - Indices `GL_UNSIGNED_INT` (ES 3 admite el tipo sin extension).
 *
 * Estado de blending: el oraculo dibuja la cortina dentro del bloque de capas
 * de datos, que fija `glDepthMask(GL_FALSE)`, `glEnable(GL_BLEND)` y
 * `glBlendFunc(GL_SRC_ALPHA, GL_ONE)` (`App.cpp:4505-4509`) y no lo cambia
 * antes de `sliceLayer.render` (`App.cpp:4539`). Aqui se fija y se restaura
 * explicitamente el mismo estado, con la restauracion que ya usa la capa de
 * estaciones (`depth mask` ON, blend OFF).
 *
 * Sin volumen en runtime no hay nada que subir: [com.valledemena.ionosphere.render.planSliceFrame]
 * devuelve `Keep`/`Hide` y [draw] no dibuja porque [hasData] sigue en `false`.
 */
internal class SliceLayerRenderer {

    private val guard = SliceDisposeGuard()

    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var ebo = 0
    private var texture = 0
    private var indexCount = 0
    private var hasData = false

    private var uMvp = -1
    private var uTex = -1
    private var uOpacity = -1
    private var aPos = -1
    private var aTexCoord = -1

    /** `true` cuando hay malla y textura horneadas (`SliceLayer::hasData_`). */
    fun hasData(): Boolean = hasData

    /** `true` si el dispose ya se ejecuto. */
    fun isDisposed(): Boolean = guard.disposed

    fun init() {
        program = ShaderProgram.create(SLICE_VERT, SLICE_FRAG)
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uOpacity = GLES20.glGetUniformLocation(program, "uOpacity")
        // Las locations se consultan al programa; el oraculo las fija con
        // `layout(location=0/1)` y aqui no se asume ninguna numeracion.
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")

        vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        val buffers = IntArray(2).also { GLES20.glGenBuffers(2, it, 0) }
        vbo = buffers[0]
        ebo = buffers[1]
        texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]

        // Atributos fijos del layout (oraculo `SliceLayer.cpp:50-55`): se ligan
        // una sola vez con el VBO/EBO del VAO; `upload` solo reescribe datos.
        GLES30.glBindVertexArray(vao)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(
            aTexCoord,
            2,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            POSITION_BYTES,
        )
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        Log.i(TAG, "cortina GL lista (SEG x LVL RGBA, indices unsigned int)")
    }

    /**
     * Sube malla y textura horneadas (`SliceLayer::updateSlice`,
     * `SliceLayer.cpp:121-129`). Un tamano incoherente no toca la GPU: el
     * oraculo retorna sin reescribir la textura anterior.
     */
    fun upload(vertices: FloatArray, indices: IntArray, texels: ByteArray, seg: Int, lvl: Int) {
        if (vao == 0 || vbo == 0 || ebo == 0 || texture == 0) return
        if (seg <= 0 || lvl <= 0) return
        if (vertices.size != seg * lvl * 5) return
        if (texels.size != seg * lvl * 4) return
        if (indices.isEmpty()) return

        GLES30.glBindVertexArray(vao)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertices.size * 4,
            vertices.toFloatBuffer(),
            GLES20.GL_DYNAMIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * 4,
            indices.toIntBuffer(),
            GLES20.GL_DYNAMIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        indexCount = indices.size
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            seg,
            lvl,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            texels.toDirectBuffer(),
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        hasData = true
    }

    /**
     * Dibuja la cortina (`SliceLayer::render`, `SliceLayer.cpp:132-146`).
     *
     * @param mvp `viewProjection * model` del frame, la misma matriz que usa la
     *   capa de estaciones: el motor Android aplica la inclinacion axial
     *   declarada en `model`, asi que la cortina debe compartir ese marco con
     *   el volumen y las estaciones (el oraculo tiene `model` identidad en
     *   `App.cpp:4372` y pasa `view`/`proj` sueltas).
     */
    fun draw(mvp: FloatArray, opacity: Float) {
        if (program == 0 || vao == 0 || texture == 0 || !hasData || indexCount <= 0) return

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform1f(uOpacity, opacity)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)

        GLES30.glBindVertexArray(vao)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    /**
     * Libera los handles de la capa. Idempotente: la segunda llamada y las
     * siguientes no tocan GL (`SliceLayer::destroy`, `SliceLayer.cpp:148-157`).
     */
    fun dispose() {
        if (!guard.markDisposed()) return
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (ebo != 0) GLES20.glDeleteBuffers(1, intArrayOf(ebo), 0)
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        vao = 0
        vbo = 0
        ebo = 0
        texture = 0
        program = 0
        indexCount = 0
        hasData = false
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(this)
            .also { it.position(0) }

    private fun IntArray.toIntBuffer(): IntBuffer =
        ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(this)
            .also { it.position(0) }

    private fun ByteArray.toDirectBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(size)
            .order(ByteOrder.nativeOrder())
            .put(this)
            .also { it.position(0) }

    private companion object {
        private const val TAG = "SliceLayerRenderer"

        private const val POSITION_BYTES = 3 * 4
        private const val STRIDE_BYTES = 5 * 4

        /** Cuerpo del vertex shader; la cabecera `#version` la pone [ShaderProgram]. */
        private const val SLICE_VERT = """
uniform mat4 uMvp;
in vec3 aPos;
in vec2 aTexCoord;
out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

        /** Cuerpo del fragment shader: identico al oraculo salvo ES 3. */
        private const val SLICE_FRAG = """
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uTex;
uniform float uOpacity;
out vec4 fragColor;

void main() {
    vec4 c = texture(uTex, vTexCoord);
    fragColor = vec4(c.rgb, c.a * uOpacity);
}
"""
    }
}
