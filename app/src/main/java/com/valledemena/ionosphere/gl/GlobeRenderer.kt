package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.valledemena.ionosphere.R
import com.valledemena.ionosphere.render.EarthShaderConstants
import com.valledemena.ionosphere.core.PlaceholderGlobeTexture
import com.valledemena.ionosphere.core.Rotation
import com.valledemena.ionosphere.core.RotationClock
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
 * R1 dibuja un frame solo cuando hace falta (`RENDERMODE_WHEN_DIRTY`): el due    o
 * del ciclo de vida pide frames a un techo de FPS mientras la escena est     viva.
 */
class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val mesh = SphereMesh.unitSphere()

    private var program = 0
    private var textureId = 0
    private var nightTextureId = 0
    private val earthShaderConstantGuard = EarthShaderConstants.DAY_NIGHT_TERMINATOR_LOW // keeps pins and shader aligned
    private var vao = 0
    private var positionBuffer = 0
    private var normalBuffer = 0
    private var uvBuffer = 0
    private var indexBuffer = 0

    private var aPosition = -1
    private var aNormal = -1
    private var aUv = -1
    private var uMvp = -1
    private var uDayTex = -1
    private var uNightTex = -1
    private var uNight = -1
    private var uSunDir = -1
    private var uModel = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val mvp = FloatArray(16)

private val clock = RotationClock()

    private var frameCount = 0
    private var textureWidth = 0
    private var textureHeight = 0

    /** N     de frames dibujados desde que se cre     la superficie. Solo diagn    stico. */
    val framesDrawn: Int get() = frameCount

    /** Tama    o de la textura cargada. */
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
        uDayTex = GLES20.glGetUniformLocation(program, "uDayTex")
        uNightTex = GLES20.glGetUniformLocation(program, "uNightTex")
        uNight = GLES20.glGetUniformLocation(program, "uNight")
        uSunDir = GLES20.glGetUniformLocation(program, "uSunDir")
        uModel = GLES20.glGetUniformLocation(program, "uModel")

        uploadMesh()
        textureId = uploadTexture(PlaceholderGlobeTexture.TIER_MEDIUM_WIDTH, PlaceholderGlobeTexture.TIER_MEDIUM_HEIGHT)
        nightTextureId = uploadTextureFromBitmap(R.drawable.earth_night)

        clock.onSurfaceCreated(SystemClock.uptimeMillis())

        Log.i(
            TAG,
            "Superficie creada (creaci    n n     ${clock.surfaceCreations}): ${mesh.vertexCount} v    rtices, ${mesh.triangleCount} tri    ngulos, " +
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

        val angle = clock.angleAt(SystemClock.uptimeMillis())

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -23.4f, 0f, 0f, 1f)   // inclinaci    n axial declarada
        Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        GLES20.glUniform1i(uDayTex, 0)
        GLES20.glUniform1i(uNightTex, 1)
        GLES20.glUniform1f(uNight, 0.45f)
        GLES20.glUniform3f(uSunDir, 0.6f, 0.35f, 0.72f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nightTextureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

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
    private fun uploadTextureFromBitmap(resId: Int): Int {
        val bmp = BitmapFactory.decodeResource(context.resources, resId)
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val buffer = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
        for (px in pixels) {
            buffer.put(((px shr 16) and 0xFF).toByte())
            buffer.put(((px shr 8) and 0xFF).toByte())
            buffer.put((px and 0xFF).toByte())
            buffer.put(((px ushr 24) and 0xFF).toByte())
        }
        buffer.position(0)
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val tid = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tid)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bmp.width, bmp.height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bmp.recycle()
        return tid
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
    private val earthShaderConstantsGuard = EarthShaderConstants.DAY_NIGHT_TERMINATOR_LOW

        private const val VERTEX_BODY = """
uniform mat4 uMvp;
uniform mat4 uModel;
in vec3 aPosition;
in vec3 aNormal;
in vec2 aUv;
out vec3 vNormal;
out vec2 vUv;
out vec3 vFragPos;

void main() {
    vFragPos = (uModel * vec4(aPosition, 1.0)).xyz;
    vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
    vUv = aUv;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
        """
        private const val FRAGMENT_BODY = """
precision mediump float;

in vec3 vNormal;
in vec2 vUv;
in vec3 vFragPos;
uniform sampler2D uDayTex;
uniform sampler2D uNightTex;
uniform vec3 uSunDir;
uniform float uNight;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 sun = normalize(uSunDir);
    float ndl = dot(n, sun);
    float dayAmount = smoothstep(-0.08, 0.25, ndl);
    float band = smoothstep(-0.08, 0.02, ndl) * (1.0 - smoothstep(0.02, 0.35, ndl));

    vec3 dayTex = texture(uDayTex, vUv).rgb;
    vec3 dayCol = dayTex * (0.12 + 1.05 * dayAmount);
    dayCol += vec3(0.38, 0.13, 0.03) * band * (0.35 + 0.65 * dayAmount);

    float ocean = smoothstep(0.008, 0.07, dayTex.b - max(dayTex.r, dayTex.g));
    vec3 hv = normalize(sun + vec3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, hv), 0.0), 36.0) * ocean * dayAmount;
    dayCol += vec3(1.0, 0.95, 0.85) * (0.7 * spec);

    vec3 nightTex = texture(uNightTex, vUv).rgb;
    vec3 nightCol = nightTex * (uNight * 2.5) + vec3(0.004, 0.007, 0.016);
    vec3 col = mix(nightCol, dayCol, dayAmount);
    fragColor = vec4(col, 1.0);
}
        """
    }
}
