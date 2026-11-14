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
import com.valledemena.ionosphere.core.PlaceholderGlobeTexture
import com.valledemena.ionosphere.render.AtmoShaderConstants
import com.valledemena.ionosphere.render.AtmoShaderSource
import com.valledemena.ionosphere.render.EarthShaderConstants
import com.valledemena.ionosphere.render.GodRaysPass
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.render.STATION_POINT_REF
import com.valledemena.ionosphere.render.StationScreenPoint
import com.valledemena.ionosphere.render.stationPick
import com.valledemena.ionosphere.render.stationPointSizeClamp
import com.valledemena.ionosphere.render.stationPositions
import com.valledemena.ionosphere.core.OrbitCamera
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
 * R1 dibuja un frame solo cuando hace falta (`RENDERMODE_WHEN_DIRTY`): el dueño
 * del ciclo de vida pide frames a un techo de FPS mientras la escena está viva.
 */
class GlobeRenderer(
    private val context: Context,
    atmoStepsOverride: Int? = null,
    atmoLightStepsOverride: Int? = null,
    measure: MeasureConfig? = null,
) : GLSurfaceView.Renderer {

    private val mesh = SphereMesh.unitSphere()

    // Configuracion de medicion opcional (measure.json externo): fija
    // camara/sol/fov del pin y permite el par ON/OFF. Sin fichero se usan los
    // defaults de produccion.
    private val interactiveCamera = measure == null
    private val camera = OrbitCamera()
    private val cameraEye = FloatArray(3)
    private val sunDir: FloatArray =
        measure?.sunDir ?: floatArrayOf(SUN_DIR_X, SUN_DIR_Y, SUN_DIR_Z)
    private val fieldOfView: Float = measure?.fov ?: 42f
    private val drawAtmosphere: Boolean = measure?.drawAtmosphere ?: true
    private val frozenModel: Boolean = measure?.frozenModel ?: false
    private val bloomEnabled: Boolean = measure?.bloom ?: true
    private val stationsEnabled: Boolean = measure?.stations ?: true
    private val captureName: String? = measure?.captureName
    private var captured = false

    init {
        if (measure != null) {
            cameraEye[0] = measure.cameraEye[0]
            cameraEye[1] = measure.cameraEye[1]
            cameraEye[2] = measure.cameraEye[2]
        } else {
            camera.writeEye(cameraEye)
        }
    }

    private var program = 0
    private var textureId = 0
    private var nightTextureId = 0

    // Slice A (R4 ii): plumbing RT; Slice C: cadena bloom -> ACES -> god-rays.
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var offscreen: OffscreenSurface? = null
    private var postChain: PostProcessChain? = null

    private val sunScreen = FloatArray(2)
    private val sunClip = FloatArray(4)
    private val sunPoint = FloatArray(4)

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
    private var uCameraPos = -1

    // R3.2b-iii: shell atmosferico. Programa y VAO propios (attr locations por programa).
    private var atmoProgram = 0
    private var atmoVao = 0
    private var atmoPositionBuffer = 0
    private var aAtmoPosition = 0
    private var uAtmoMvp = 0
    private var uAtmoModel = 0
    private var uAtmoSunDir = 0
    private var uAtmoCameraPos = 0
    private var uAtmoIntensity = 0
    private var uAtmoSteps = 0
    private var uAtmoLightSteps = 0

    // Rider K3: defaults de pin, override solo si la Activity resolvio un extra en rango.
    private val atmoSteps = atmoStepsOverride ?: AtmoShaderConstants.DEFAULT_STEPS
    private val atmoLightSteps = atmoLightStepsOverride ?: AtmoShaderConstants.DEFAULT_LIGHT_STEPS

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val mvp = FloatArray(16)
    private val viewModel = FloatArray(16)

    // R3.4: capa de ionosondas aislada; el globo no se refactoriza.
    private var stationPoints: StationPointsRenderer? = null
    @Volatile private var stationsData: IonosondeData? = null
    @Volatile private var rawPointSize: Float = STATION_POINT_REF
    @Volatile private var pickRequest: PickRequest? = null
    @Volatile var onStationPick: ((String?) -> Unit)? = null

    private data class PickRequest(val x: Float, val y: Float, val width: Int, val height: Int)

private val clock = RotationClock()

    private var frameCount = 0
    private var lastCameraFrameMs = 0L
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
        uDayTex = GLES20.glGetUniformLocation(program, "uDayTex")
        uNightTex = GLES20.glGetUniformLocation(program, "uNightTex")
        uNight = GLES20.glGetUniformLocation(program, "uNight")
        uSunDir = GLES20.glGetUniformLocation(program, "uSunDir")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uCameraPos = GLES20.glGetUniformLocation(program, "uCameraPos")

        // Programa del shell: fuente sellada en AtmoShaderSource (golden 967f867).
        atmoProgram = ShaderProgram.create(AtmoShaderSource.ATMO_VERT, AtmoShaderSource.ATMO_FRAG)
        aAtmoPosition = GLES20.glGetAttribLocation(atmoProgram, "aPosition")
        uAtmoMvp = GLES20.glGetUniformLocation(atmoProgram, "uMvp")
        uAtmoModel = GLES20.glGetUniformLocation(atmoProgram, "uModel")
        uAtmoSunDir = GLES20.glGetUniformLocation(atmoProgram, "uSunDir")
        uAtmoCameraPos = GLES20.glGetUniformLocation(atmoProgram, "uCameraPos")
        uAtmoIntensity = GLES20.glGetUniformLocation(atmoProgram, "uIntensity")
        uAtmoSteps = GLES20.glGetUniformLocation(atmoProgram, "uSteps")
        uAtmoLightSteps = GLES20.glGetUniformLocation(atmoProgram, "uLightSteps")

        uploadMesh()
        uploadAtmoMesh()
        stationPoints = StationPointsRenderer().also { it.init() }

        // Uniforms estaticos del shell: 12 pasos / 4 pasos de luz, desde pins (web builders.ts L130-131).
        GLES20.glUseProgram(atmoProgram)
        pushSunCamera(uAtmoSunDir, uAtmoCameraPos)
        GLES20.glUniform1f(uAtmoIntensity, 1.0f)
        GLES20.glUniform1f(uAtmoSteps, atmoSteps.toFloat())
        GLES20.glUniform1f(uAtmoLightSteps, atmoLightSteps.toFloat())
        GLES20.glUseProgram(0)
        textureId = uploadTextureFromAsset("earth-day.jpg", srgb = true)
            ?: uploadTexture(PlaceholderGlobeTexture.TIER_MEDIUM_WIDTH, PlaceholderGlobeTexture.TIER_MEDIUM_HEIGHT)
        nightTextureId = uploadTextureFromAsset("earth-night.png", srgb = true)
            ?: uploadTextureFromBitmap(R.drawable.earth_night, srgb = true)

        clock.onSurfaceCreated(SystemClock.uptimeMillis())
        lastCameraFrameMs = 0L

        Log.i(
            TAG,
            "Superficie creada (creación nº ${clock.surfaceCreations}): ${mesh.vertexCount} vértices, ${mesh.triangleCount} triángulos, " +
                "textura ${textureWidth}x${textureHeight}, atmo ${atmoSteps}/${atmoLightSteps}, stations=$stationsEnabled, GL ${GLES20.glGetString(GLES20.GL_VERSION)}",
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceWidth = width; surfaceHeight = height
        // W3: el RT anterior se libera en cada resize (FBO + 2 renderbuffers MSAA).
        offscreen?.release()
        offscreen = null
        postChain?.release()
        postChain = null

        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        val o = OffscreenSurface(width, height)
        o.init(extensions)
        offscreen = o

        val chain = PostProcessChain(
            width,
            height,
            o.supportsHalfFloatExtension(extensions),
        )
        chain.init()
        postChain = if (chain.isReady()) chain else null
        if (postChain == null) chain.release()

        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        if (interactiveCamera) {
            Matrix.perspectiveM(projection, 0, 42f, aspect, 0.1f, 100f)
        } else {
            Matrix.perspectiveM(projection, 0, fieldOfView, aspect, 0.1f, 100f)
        }
        Matrix.setLookAtM(
            view, 0,
            cameraEye[0], cameraEye[1], cameraEye[2],
            0f, 0f, 0f,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val off = offscreen
        if (off?.isReady() == true) off.bindOffscreen()
        // Fix: glClear on MSAA RGBA16F only clears 1 of 4 samples on SwiftShader.
        // Use a fullscreen quad to clear all samples.
        off?.clearColorByQuad()
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

        if (interactiveCamera) {
            val now = SystemClock.uptimeMillis()
            if (lastCameraFrameMs != 0L) {
                camera.update(((now - lastCameraFrameMs) / 1000f).coerceIn(0f, 0.1f))
            }
            lastCameraFrameMs = now
            camera.writeEye(cameraEye)
            Matrix.setLookAtM(
                view, 0,
                cameraEye[0], cameraEye[1], cameraEye[2],
                0f, 0f, 0f,
                0f, 1f, 0f,
            )
            Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        }

        val angle = clock.angleAt(SystemClock.uptimeMillis())

        Matrix.setIdentityM(model, 0)
        if (!frozenModel) {
            Matrix.rotateM(model, 0, -23.4f, 0f, 0f, 1f)   // inclinación axial declarada
            Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        }
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        GLES20.glUniform1i(uDayTex, 0)
        GLES20.glUniform1i(uNightTex, 1)
        GLES20.glUniform1f(uNight, EarthShaderConstants.NIGHT_DEFAULT)
        pushSunCamera(uSunDir, uCameraPos)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nightTextureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES30.glBindVertexArray(vao)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indices.size, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)

        // R3.4: puntos despues del globo (depth test ON, depth write OFF) y
        // antes de la atmosfera, como el material transparente del web.
        if (stationsEnabled) {
            stationPoints?.draw(
                data = stationsData,
                pointSize = stationPointSizeClamp(rawPointSize),
                pixelRatio = pixelRatio(),
                mvp = mvp,
                viewModel = viewModel,
                timeSeconds = SystemClock.uptimeMillis() / 1000f,
            )
            processPickRequest()
        }

        if (drawAtmosphere) {
            // Pase del shell (atmosfera): aditivo, BackSide espejado con cull front,
            // sin escribir z (mirror de web builders.ts L122-138). Restauracion completa
            // de estado: culling vuelve a desactivado (estado global del renderer),
            // depth mask y blend vuelven a sus valores previos al pase.
            // Web NO rota la Tierra (modelMatrix = identity). El ciclo dia/noche viene
            // del uSunDir que cambia con la estacion. Android debe igualar: uAtmoMvp = viewProjection,
            // uAtmoModel = identity.
            GLES20.glUseProgram(atmoProgram)
            GLES20.glUniformMatrix4fv(uAtmoMvp, 1, false, viewProjection, 0)
            GLES20.glUniformMatrix4fv(uAtmoModel, 1, false, IDENTITY_MATRIX, 0)
            pushSunCamera(uAtmoSunDir, uAtmoCameraPos)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
            GLES20.glDepthMask(false)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            // SphereMesh winds its triangles CW when seen from outside, so the
            // far hemisphere is FRONT-facing under the default CCW rule. The
            // web draws the shell with THREE.BackSide (far hemisphere): cull
            // BACK keeps exactly that half. Culling FRONT kept the near half,
            // where t1 - t0 <= 0 and the ray march discarded every fragment.
            GLES20.glCullFace(GLES20.GL_BACK)
            GLES30.glBindVertexArray(atmoVao)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indices.size, GLES20.GL_UNSIGNED_SHORT, 0)
            GLES30.glBindVertexArray(0)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        val chain = postChain
        if (chain != null && !bloomEnabled) chain.bloomStrength = 0f
        if (off?.isReady() == true) {
            off.resolveColor()
            if (chain != null && chain.isReady() && off.resolvedReady()) {
                val gate = updateSunGate()
                chain.render(off.resolvedTexture(), sunScreen[0], sunScreen[1], gate)
                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            } else {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                off.blitToDefault(surfaceWidth, surfaceHeight)
            }
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        frameCount++
        if (captureName != null && !captured && frameCount >= 10) {
            captured = true
            captureFrame(captureName)
        }
    }

    /**
     * Guarda el framebuffer ya presentado a PNG (sin HUD ni barras del
     * sistema, que viven en capas distintas). Solo corre si `measure.json`
     * declara `capture`.
     */
    private fun captureFrame(name: String) {
        val w = surfaceWidth
        val h = surfaceHeight
        if (w <= 0 || h <= 0) return
        val pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        pixels.rewind()
        val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(pixels)
        val flipped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(flipped)
        val flip = android.graphics.Matrix().apply {
            postScale(1f, -1f)
            postTranslate(0f, h.toFloat())
        }
        canvas.drawBitmap(raw, flip, null)
        val dir = context.getExternalFilesDir(null)
        val file = java.io.File(dir, name)
        java.io.FileOutputStream(file).use {
            flipped.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Log.i(TAG, "captura de medicion: ${file.absolutePath} ${w}x${h}")
    }

    /**
     * C7: gate CPU de proyeccion solar. El pase de god-rays solo corre cuando
     * el punto subsolar mira a la camara y cae dentro del encuadre; si no
     * aporta, se hace skip y la imagen post-ACES se presenta directa.
     */
    private fun updateSunGate(): Boolean {
        val len = kotlin.math.sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2])
        sunPoint[0] = sunDir[0] / len
        sunPoint[1] = sunDir[1] / len
        sunPoint[2] = sunDir[2] / len
        sunPoint[3] = 1f
        val facingCamera = sunPoint[2] > 0f
        Matrix.multiplyMV(sunClip, 0, viewProjection, 0, sunPoint, 0)
        val w = sunClip[3]
        if (w == 0f) return false
        val ndcX = sunClip[0] / w
        val ndcY = sunClip[1] / w
        sunScreen[0] = ndcX * 0.5f + 0.5f
        sunScreen[1] = ndcY * 0.5f + 0.5f
        return facingCamera && GodRaysPass.sunContributes(w, ndcX, ndcY)
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

    /** Malla del shell: la misma esfera unitaria, escalada por el pin ATM_R. */
    private fun uploadAtmoMesh() {
        atmoVao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        GLES30.glBindVertexArray(atmoVao)

        val scaled = mesh.positions.map { it * AtmoShaderConstants.ATM_R }.toFloatArray()
        atmoPositionBuffer = uploadFloatAttribute(aAtmoPosition, scaled, 3)

        // Indices compartidos con la malla del planeta (mismo teselado): el ELEMENT
        // buffer queda vinculado dentro de este VAO.
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)

        GLES30.glBindVertexArray(0)
    }

    /** Sol y camara: mismos valores para los dos programas (Earth y shell), una sola fuente. */
    private fun pushSunCamera(uSunDir: Int, uCameraPos: Int) {
        GLES20.glUniform3f(uSunDir, sunDir[0], sunDir[1], sunDir[2])
        GLES20.glUniform3f(uCameraPos, cameraEye[0], cameraEye[1], cameraEye[2])
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

    private fun uploadTextureFromBitmap(resId: Int, srgb: Boolean = false): Int {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bmp = BitmapFactory.decodeResource(context.applicationContext.resources, resId, opts)
        return uploadBitmap(bmp, srgb)
    }

    /** D5: NASA original assets, byte-exact, decoded straight from app assets.
     *
     * Se leen a memoria y se decodifican con `decodeByteArray`: el APK guarda
     * estos assets sin comprimir y `decodeStream` puede devolver `null`
     * ("incomplete input") aunque el fichero este intacto, cayendo entonces al
     * placeholder sin que se note.
     *
     * `earth-night.png` de NASA trae un chunk `zTXt` de 23.7 MB (85% del
     * fichero) que el decodificador PNG de Android rechaza. El asset del APK
     * queda byte-exacto; solo se descartan los chunks auxiliares de texto en
     * memoria antes de decodificar.
     */
    private fun uploadTextureFromAsset(name: String, srgb: Boolean = false): Int? {
        return try {
            val raw = context.assets.open(name).use { it.readBytes() }
            val bytes = PngAssetSanitizer.withoutTextChunks(raw)
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (bmp == null) {
                Log.w(TAG, "asset $name no decodifica (${bytes.size}/${raw.size} B)")
                return null
            }
            Log.i(TAG, "asset $name ${raw.size} B -> ${bmp.width}x${bmp.height}")
            uploadBitmap(bmp, srgb)
        } catch (e: Throwable) {
            Log.w(TAG, "asset $name no disponible: $e")
            null
        }
    }

    private fun uploadBitmap(bmp: Bitmap?, srgb: Boolean = false): Int {
        if (bmp == null) return 0
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
            GLES20.GL_TEXTURE_2D, 0,
            if (srgb) GLES30.GL_SRGB8_ALPHA8 else GLES20.GL_RGBA, bmp.width, bmp.height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        textureWidth = bmp.width
        textureHeight = bmp.height
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

    val cameraInteractive: Boolean get() = interactiveCamera

    /** R3.4: datos de estaciones desde el HudStore (sin fetch propio). */
    fun setStations(data: IonosondeData?) {
        stationsData = data
    }

    /** R3.4: valor crudo del slider; el clamp [2,30] se aplica al dibujar. */
    fun setPointSize(raw: Float) {
        rawPointSize = raw
    }

    /** R3.4: encola un tap; la proyeccion se resuelve en el hilo GL. */
    fun requestPick(x: Float, y: Float, width: Int, height: Int) {
        pickRequest = PickRequest(x, y, width, height)
    }

    private fun pixelRatio(): Float =
        context.resources.displayMetrics.density.coerceIn(1f, 2f)

    private fun processPickRequest() {
        val req = pickRequest ?: return
        pickRequest = null
        val data = stationsData
        if (data == null || data.stations.isEmpty()) {
            onStationPick?.invoke(null)
            return
        }

        val positions = stationPositions(data.stations)
        val points = ArrayList<StationScreenPoint>(data.stations.size)
        val pos = FloatArray(4)
        val world = FloatArray(4)
        val clip = FloatArray(4)
        val eyeLen = kotlin.math.sqrt(
            cameraEye[0] * cameraEye[0] + cameraEye[1] * cameraEye[1] + cameraEye[2] * cameraEye[2],
        ).coerceAtLeast(1e-6f)

        for (i in data.stations.indices) {
            pos[0] = positions[i * 3]
            pos[1] = positions[i * 3 + 1]
            pos[2] = positions[i * 3 + 2]
            pos[3] = 1f
            Matrix.multiplyMV(world, 0, model, 0, pos, 0)
            Matrix.multiplyMV(clip, 0, mvp, 0, pos, 0)
            val w = clip[3]
            if (w <= 0f) continue
            val ndcZ = clip[2] / w
            if (ndcZ > 1f) continue

            val sx = (clip[0] / w + 1f) * 0.5f * req.width
            val sy = (1f - clip[1] / w) * 0.5f * req.height
            val worldLen = kotlin.math.sqrt(
                world[0] * world[0] + world[1] * world[1] + world[2] * world[2],
            ).coerceAtLeast(1e-6f)
            val facing = (
                world[0] * cameraEye[0] + world[1] * cameraEye[1] + world[2] * cameraEye[2]
                ) / (worldLen * eyeLen) >= 0f
            points.add(StationScreenPoint(data.stations[i].code, sx, sy, facing))
        }
        onStationPick?.invoke(stationPick(points, req.x, req.y, 0f))
    }

    fun onCameraDrag(dxPx: Float, dyPx: Float, viewportHeight: Int) {
        if (interactiveCamera) camera.dragFromPixels(dxPx, dyPx, viewportHeight)
    }

    fun onCameraFling(xVelocityPxPerSecond: Float, yVelocityPxPerSecond: Float, viewportHeight: Int) {
        if (interactiveCamera) {
            camera.flingFromPixels(xVelocityPxPerSecond, yVelocityPxPerSecond, viewportHeight)
        }
    }

    fun onCameraScale(scaleFactor: Float) {
        if (interactiveCamera) camera.scaleBy(scaleFactor)
    }

    fun onCameraStopInertia() {
        if (interactiveCamera) camera.stopInertia()
    }

    companion object {
        private const val TAG = "GlobeRenderer"
        private const val SUN_DIR_X = 0.6f
        private const val SUN_DIR_Y = 0.35f
        private const val SUN_DIR_Z = 0.72f
        private val IDENTITY_MATRIX = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

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
    vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
    vFragPos = (uModel * vec4(aPosition, 1.0)).xyz;
    vUv = aUv;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
        """

        private val FRAGMENT_BODY = """
precision mediump float;

in vec3 vNormal;
in vec2 vUv;
in vec3 vFragPos;
uniform sampler2D uDayTex;
uniform sampler2D uNightTex;
uniform vec3 uSunDir;
uniform float uNight;
uniform vec3 uCameraPos;

out vec4 fragColor;

void main() {
    float dayAmount = smoothstep(${EarthShaderConstants.DAY_NIGHT_TERMINATOR_LOW}, ${EarthShaderConstants.DAY_NIGHT_TERMINATOR_HIGH}, dot(normalize(vNormal), normalize(uSunDir)));
    float band = smoothstep(${EarthShaderConstants.NIGHT_BAND_LOW}, ${EarthShaderConstants.NIGHT_BAND_MID}, dot(normalize(vNormal), normalize(uSunDir))) * (1.0 - smoothstep(${EarthShaderConstants.NIGHT_BAND_MID}, ${EarthShaderConstants.NIGHT_BAND_HIGH}, dot(normalize(vNormal), normalize(uSunDir))));

    vec3 dayTex = texture(uDayTex, vUv).rgb;
    vec3 dayCol = dayTex * (0.12 + 1.05 * dayAmount);
    dayCol += vec3(0.38, 0.13, 0.03) * band * (0.35 + 0.65 * dayAmount);

    float ocean = smoothstep(${EarthShaderConstants.OCEAN_THRESHOLD_LOW}, ${EarthShaderConstants.OCEAN_THRESHOLD_HIGH}, dayTex.b - max(dayTex.r, dayTex.g));
    vec3 hv = normalize(normalize(uSunDir) + normalize(uCameraPos - vFragPos));
    float spec = pow(max(dot(normalize(vNormal), hv), 0.0), ${EarthShaderConstants.SPECULAR_POWER}) * ocean * dayAmount;
    dayCol += vec3(1.0, 0.95, 0.85) * (${EarthShaderConstants.GLITTER} * spec);

    vec3 nightTex = texture(uNightTex, vUv).rgb;
    vec3 nightCol = nightTex * (${EarthShaderConstants.NIGHT_MULTIPLIER} * uNight) + vec3(${EarthShaderConstants.AMBIENT_NIGHT_R}, ${EarthShaderConstants.AMBIENT_NIGHT_G}, ${EarthShaderConstants.AMBIENT_NIGHT_B});
    vec3 col = mix(nightCol, dayCol, dayAmount);
    fragColor = vec4(col, 1.0);
}
    """
    }
}
