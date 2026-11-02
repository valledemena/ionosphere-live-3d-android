package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.valledemena.ionosphere.core.PlaceholderGlobeTexture
import com.valledemena.ionosphere.core.Rotation
import com.valledemena.ionosphere.core.SphereMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer propio sobre GLES 3.x: un globo texturizado, sin motor de terceros.
 *
 * R1 dibuja un frame solo cuando hace falta (`RENDERMODE_WHEN_DIRTY`): el dueño
 * del ciclo de vida pide frames a un techo de FPS mientras la escena está viva.
 */
class GlobeRenderer : GLSurfaceView.Renderer {

    private val mesh = SphereMesh.unitSphere()

    private var program = 0
    private var textureId = 0
    private var vao = 0
    private var positionBuffer = 0
    private var normalBuffer = 0
    private var uvBuffer = 0
    private var indexBuffer = 0

    private var aPosition = -1
    private var aNormal = -1
    private var aUv = -1
    private var uMvp = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val mvp = FloatArray(16)

    private var startedAtMs = 0L
    private var frameCount = 0
    private var textureWidth = 0
    private var textureHeight = 0

    /** Nº de frames dibujados desde que se creó la superficie. Solo diagnóstico. */
    val framesDrawn: Int get() = frameCount

    /** Tamaño de la textura cargada. */
    val loadedTextureSize: IntArray get() = intArrayOf(textureWidth, textureHeight)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)   // la malla es cerrada: sin culling no hay caras invisibles por winding
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        program = ShaderProgram.create(VERTEX_BODY, FRAGMENT_BODY)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")

        uploadMesh()
        textureId = uploadTexture(PlaceholderGlobeTexture.TIER_MEDIUM_WIDTH, PlaceholderGlobeTexture.TIER_MEDIUM_HEIGHT)

        startedAtMs = SystemClock.uptimeMillis()
        frameCount = 0

        Log.i(
            TAG,
            "Superficie creada: ${mesh.vertexCount} vértices, ${mesh.triangleCount} triángulos, " +
                "textura ${textureWidth}x${textureHeight}, GL ${GLES20.glGetString(GLES20.GL_VERSION)}",
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val elapsed = SystemClock.uptimeMillis() - startedAtMs
        val angle = Rotation.angleDegrees(elapsed)

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -23.4f, 0f, 0f, 1f)   // inclinación axial declarada
        Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES30.glBindVertexArray(vao)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indices.size, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)

        frameCount++
    }

    private fun uploadMesh() {
        vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        GLES30.glBindVertexArray(vao)

        positionBuffer = uploadFloatAttribute(aPosition, mesh.positions, 3)
        normalBuffer = uploadFloatAttribute(aNormal, mesh.normals, 3)
        uvBuffer = uploadFloatAttribute(aUv, mesh.uvs, 2)

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        indexBuffer = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            mesh.indices.size * 2,
            mesh.indices.toShortBuffer(),
            GLES20.GL_STATIC_DRAW,
        )

        GLES30.glBindVertexArray(0)
    }

    private fun uploadFloatAttribute(location: Int, data: FloatArray, components: Int): Int {
        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        val buffer = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            data.size * 4,
            data.toFloatBuffer(),
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glVertexAttribPointer(location, components, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return buffer
    }

    private fun uploadTexture(width: Int, height: Int): Int {
        val pixels = PlaceholderGlobeTexture.generate(width, height)
        val bytes = PlaceholderGlobeTexture.toRgbaBytes(pixels)
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
        buffer.position(0)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        textureWidth = width
        textureHeight = height
        return id
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(this)
            .also { it.position(0) }

    private fun ShortArray.toShortBuffer(): ShortBuffer =
        ByteBuffer.allocateDirect(size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(this)
            .also { it.position(0) }

    companion object {
        private const val TAG = "GlobeRenderer"

        private const val VERTEX_BODY = """
uniform mat4 uMvp;
in vec3 aPosition;
in vec3 aNormal;
in vec2 aUv;
out vec3 vNormal;
out vec2 vUv;

void main() {
    vNormal = aNormal;
    vUv = aUv;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""

        private const val FRAGMENT_BODY = """
precision mediump float;

in vec3 vNormal;
in vec2 vUv;
uniform sampler2D uTexture;
out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 lightDir = normalize(vec3(0.6, 0.35, 0.72));
    float diffuse = clamp(dot(n, lightDir), 0.0, 1.0);
    vec3 albedo = texture(uTexture, vUv).rgb;
    fragColor = vec4(albedo * (0.18 + 0.95 * diffuse), 1.0);
}
"""
    }
}
