package com.example.camera.engine

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.camera.model.DualVideoLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance hardware OpenGL ES 2.0 Compositor for Dual Camera simultaneous recording.
 *
 * Receives live frames from both primary and secondary camera hardware streams via
 * OES external SurfaceTextures, continuously composes both feeds in real time according to
 * the selected layout (Side-by-Side, Top-Bottom, or PiP), and renders directly into the
 * MediaRecorder input Surface.
 */
class DualCameraCompositor(
    private val outputSurface: Surface,
    val width: Int = 1920,
    val height: Int = 1080,
    private val layoutProvider: () -> DualVideoLayout
) {
    companion object {
        private const val TAG = "DualCameraCompositor"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private val FULL_QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        private val TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var glProgram = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexMatrixLoc = 0
    private var sTextureLoc = 0

    private var primaryTexId = 0
    private var secondaryTexId = 0

    var primarySurfaceTexture: SurfaceTexture? = null
        private set
    var secondarySurfaceTexture: SurfaceTexture? = null
        private set

    var primarySurface: Surface? = null
        private set
    var secondarySurface: Surface? = null
        private set

    private val primaryMtx = FloatArray(16)
    private val secondaryMtx = FloatArray(16)

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val isRunning = AtomicBoolean(false)
    private val isReady = AtomicBoolean(false)

    init {
        val bb = ByteBuffer.allocateDirect(FULL_QUAD_COORDS.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer().apply {
            put(FULL_QUAD_COORDS)
            position(0)
        }

        val tb = ByteBuffer.allocateDirect(TEX_COORDS.size * 4)
        tb.order(ByteOrder.nativeOrder())
        texCoordBuffer = tb.asFloatBuffer().apply {
            put(TEX_COORDS)
            position(0)
        }

        Matrix.setIdentityM(primaryMtx, 0)
        Matrix.setIdentityM(secondaryMtx, 0)

        initGlContext()
    }

    private fun initGlContext() {
        val initLatch = CountDownLatch(1)
        val thread = HandlerThread("DualCameraCompositorThread").apply { start() }
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler

        handler.post {
            try {
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                    throw RuntimeException("eglGetDisplay failed")
                }

                val version = IntArray(2)
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    throw RuntimeException("eglInitialize failed")
                }

                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
                )

                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                val config = configs[0] ?: throw RuntimeException("No suitable EGLConfig")

                val contextAttribs = intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                )
                eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                if (eglContext == EGL14.EGL_NO_CONTEXT) {
                    throw RuntimeException("eglCreateContext failed")
                }

                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, outputSurface, surfaceAttribs, 0)
                if (eglSurface == EGL14.EGL_NO_SURFACE) {
                    throw RuntimeException("eglCreateWindowSurface failed")
                }

                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    throw RuntimeException("eglMakeCurrent failed")
                }

                // Compile Shaders
                glProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
                aPositionLoc = GLES20.glGetAttribLocation(glProgram, "aPosition")
                aTexCoordLoc = GLES20.glGetAttribLocation(glProgram, "aTexCoord")
                uTexMatrixLoc = GLES20.glGetUniformLocation(glProgram, "uTexMatrix")
                sTextureLoc = GLES20.glGetUniformLocation(glProgram, "sTexture")

                // Generate OES Textures
                val textures = IntArray(2)
                GLES20.glGenTextures(2, textures, 0)
                primaryTexId = textures[0]
                secondaryTexId = textures[1]

                for (id in textures) {
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                }

                val pTex = SurfaceTexture(primaryTexId).apply {
                    setDefaultBufferSize(width, height)
                    setOnFrameAvailableListener({ requestRender() }, handler)
                }
                primarySurfaceTexture = pTex
                primarySurface = Surface(pTex)

                val sTex = SurfaceTexture(secondaryTexId).apply {
                    setDefaultBufferSize(width, height)
                    setOnFrameAvailableListener({ requestRender() }, handler)
                }
                secondarySurfaceTexture = sTex
                secondarySurface = Surface(sTex)

                isReady.set(true)
                Log.d(TAG, "DualCameraCompositor GL initialized successfully ($width x $height)")
            } catch (e: Exception) {
                Log.e(TAG, "DualCameraCompositor initialization error", e)
            } finally {
                initLatch.countDown()
            }
        }

        initLatch.await(2, TimeUnit.SECONDS)
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            renderHandler?.post(renderLoopRunnable)
            Log.d(TAG, "Compositor recording render loop started")
        }
    }

    private val renderLoopRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return
            drawFrame()
            // 30 FPS pacing (~33ms)
            renderHandler?.postDelayed(this, 33)
        }
    }

    private fun requestRender() {
        if (!isRunning.get()) return
        // Frames also trigger immediate draw if available
        renderHandler?.post {
            if (isRunning.get()) {
                drawFrame()
            }
        }
    }

    private fun drawFrame() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return

        try {
            // Update camera texture images
            primarySurfaceTexture?.let {
                try {
                    it.updateTexImage()
                    it.getTransformMatrix(primaryMtx)
                } catch (ignored: Throwable) {}
            }
            secondarySurfaceTexture?.let {
                try {
                    it.updateTexImage()
                    it.getTransformMatrix(secondaryMtx)
                } catch (ignored: Throwable) {}
            }

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            GLES20.glUseProgram(glProgram)
            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)

            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

            val layout = layoutProvider()
            when (layout) {
                DualVideoLayout.SIDE_BY_SIDE -> {
                    // Left half: Primary camera
                    GLES20.glViewport(0, 0, width / 2, height)
                    drawTexture(primaryTexId, primaryMtx)

                    // Right half: Secondary camera
                    GLES20.glViewport(width / 2, 0, width / 2, height)
                    drawTexture(secondaryTexId, secondaryMtx)
                }
                DualVideoLayout.TOP_BOTTOM -> {
                    // Top half: Primary camera
                    GLES20.glViewport(0, height / 2, width, height / 2)
                    drawTexture(primaryTexId, primaryMtx)

                    // Bottom half: Secondary camera
                    GLES20.glViewport(0, 0, width, height / 2)
                    drawTexture(secondaryTexId, secondaryMtx)
                }
                DualVideoLayout.PIP -> {
                    // Full screen: Primary camera
                    GLES20.glViewport(0, 0, width, height)
                    drawTexture(primaryTexId, primaryMtx)

                    // Inset Picture-in-Picture window: Secondary camera
                    val pipW = (width * 0.36f).toInt()
                    val pipH = (height * 0.36f).toInt()
                    val marginX = (width * 0.04f).toInt()
                    val marginY = (height * 0.04f).toInt()
                    val pipX = width - pipW - marginX
                    val pipY = height - pipH - marginY

                    GLES20.glViewport(pipX, pipY, pipW, pipH)
                    drawTexture(secondaryTexId, secondaryMtx)
                }
            }

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTexCoordLoc)

            // Presentation timestamp
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        } catch (e: Exception) {
            Log.w(TAG, "Error rendering composite frame", e)
        }
    }

    private fun drawTexture(textureId: Int, matrix: FloatArray) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sTextureLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun stop() {
        isRunning.set(false)
        renderHandler?.removeCallbacks(renderLoopRunnable)
    }

    fun release() {
        stop()
        val latch = CountDownLatch(1)
        renderHandler?.post {
            try {
                if (glProgram != 0) {
                    GLES20.glDeleteProgram(glProgram)
                    glProgram = 0
                }
                val textures = intArrayOf(primaryTexId, secondaryTexId)
                GLES20.glDeleteTextures(2, textures, 0)

                primarySurface?.release()
                primarySurface = null
                primarySurfaceTexture?.release()
                primarySurfaceTexture = null

                secondarySurface?.release()
                secondarySurface = null
                secondarySurfaceTexture?.release()
                secondarySurfaceTexture = null

                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                        eglSurface = EGL14.EGL_NO_SURFACE
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                        eglContext = EGL14.EGL_NO_CONTEXT
                    }
                    EGL14.eglTerminate(eglDisplay)
                    eglDisplay = EGL14.EGL_NO_DISPLAY
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing compositor resources", e)
            } finally {
                latch.countDown()
            }
        }
        latch.await(1, TimeUnit.SECONDS)
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $error")
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $error")
        }
        return shader
    }
}
