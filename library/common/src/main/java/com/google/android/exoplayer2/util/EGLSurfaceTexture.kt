/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.*
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.checkGlError
import com.google.android.exoplayer2.util.GlUtil.checkGlException
import com.google.android.exoplayer2.util.Util.formatInvariant
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Generates a [SurfaceTexture] using EGL/GLES functions.  */
@RequiresApi(17)
class EGLSurfaceTexture : SurfaceTexture.OnFrameAvailableListener, Runnable {

    /** Listener to be called when the texture image on [SurfaceTexture] has been updated.  */
    interface TextureImageListener {
        /** Called when the [SurfaceTexture] receives a new frame from its image producer.  */
        fun onFrameAvailable()
    }

    /**
     * Secure mode to be used by the EGL surface and context. One of [.SECURE_MODE_NONE], [ ][.SECURE_MODE_SURFACELESS_CONTEXT] or [.SECURE_MODE_PROTECTED_PBUFFER].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [SECURE_MODE_NONE, SECURE_MODE_SURFACELESS_CONTEXT, SECURE_MODE_PROTECTED_PBUFFER])
    annotation class SecureMode

    private var handler: Handler? = null
    private var textureIdHolder: IntArray? = null
    private var callback: TextureImageListener? = null

    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var surface: EGLSurface? = null
    private var texture: SurfaceTexture? = null

    /**
     * @param handler The [Handler] that will be used to call [     ][SurfaceTexture.updateTexImage] to update images on the [SurfaceTexture]. Note that
     * [.init] has to be called on the same looper thread as the [Handler]'s
     * looper.
     */
    constructor(handler: Handler?) {
        EGLSurfaceTexture(handler,  /* callback= */null)
    }

    /**
     * @param handler The [Handler] that will be used to call [     ][SurfaceTexture.updateTexImage] to update images on the [SurfaceTexture]. Note that
     * [.init] has to be called on the same looper thread as the looper of the [     ].
     * @param callback The [TextureImageListener] to be called when the texture image on [     ] has been updated. This callback will be called on the same handler thread
     * as the `handler`.
     */
    constructor(handler: Handler?, callback: TextureImageListener?) {
        this.handler = handler
        this.callback = callback
        textureIdHolder = IntArray(1)
    }

    /**
     * Initializes required EGL parameters and creates the [SurfaceTexture].
     *
     * @param secureMode The [SecureMode] to be used for EGL surface.
     */
    @Throws(GlException::class)
    fun init(@SecureMode secureMode: Int) {
        display = getDefaultDisplay()
        val config = chooseEGLConfig(display)
        context = createEGLContext(display, config, secureMode)
        surface = createEGLSurface(display, config, context, secureMode)
        generateTextureIds(textureIdHolder)
        texture = SurfaceTexture(textureIdHolder!![0])
        texture!!.setOnFrameAvailableListener(this)
    }

    /** Releases all allocated resources.  */
    fun release() {
        handler!!.removeCallbacks(this)
        try {
            if (texture != null) {
                texture!!.release()
                GLES20.glDeleteTextures(1, textureIdHolder, 0)
            }
        } finally {
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface)
            }
            if (context != null) {
                EGL14.eglDestroyContext(display, context)
            }
            // EGL14.eglReleaseThread could crash before Android K (see [internal: b/11327779]).
            if (Util.SDK_INT >= 19) {
                EGL14.eglReleaseThread()
            }
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
                // every eglInitialize() we need an eglTerminate().
                EGL14.eglTerminate(display)
            }
            display = null
            context = null
            surface = null
            texture = null
        }
    }

    /**
     * Returns the wrapped [SurfaceTexture]. This can only be called after [.init].
     */
    fun getSurfaceTexture(): SurfaceTexture? {
        return checkNotNull(texture)
    }

    // SurfaceTexture.OnFrameAvailableListener

    // SurfaceTexture.OnFrameAvailableListener
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        handler!!.post(this)
    }

    // Runnable

    // Runnable
    override fun run() {
        // Run on the provided handler thread when a new image frame is available.
        dispatchOnFrameAvailable()
        if (texture != null) {
            try {
                texture!!.updateTexImage()
            } catch (e: RuntimeException) {
                // Ignore
            }
        }
    }

    private fun dispatchOnFrameAvailable() {
        callback?.onFrameAvailable()
    }

    @Throws(GlException::class)
    private fun getDefaultDisplay(): EGLDisplay? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        checkGlException(display != null, "eglGetDisplay failed")
        val version = IntArray(2)
        val eglInitialized = EGL14.eglInitialize(display, version,  /* majorOffset= */0, version,  /* minorOffset= */1)
        checkGlException(eglInitialized, "eglInitialize failed")
        return display
    }

    @Throws(GlException::class)
    private fun chooseEGLConfig(display: EGLDisplay?): EGLConfig? {
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val success = EGL14.eglChooseConfig(display, EGL_CONFIG_ATTRIBUTES,  /* attrib_listOffset= */
                0, configs,  /* configsOffset= */
                0,  /* config_size= */
                1, numConfigs,  /* num_configOffset= */
                0)
        checkGlException(success && numConfigs[0] > 0 && configs[0] != null, formatInvariant( /* format= */
                "eglChooseConfig failed: success=%b, numConfigs[0]=%d, configs[0]=%s", success, numConfigs[0], configs[0]))
        return configs[0]
    }

    @Throws(GlException::class)
    private fun createEGLContext(display: EGLDisplay?, config: EGLConfig?, @SecureMode secureMode: Int): EGLContext? {
        val glAttributes: IntArray = if (secureMode == SECURE_MODE_NONE) {
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        } else {
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE, EGL14.EGL_NONE)
        }
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, glAttributes, 0)
        checkGlException(context != null, "eglCreateContext failed")
        return context
    }

    @Throws(GlException::class)
    private fun createEGLSurface(display: EGLDisplay?, config: EGLConfig?, context: EGLContext?, secureMode: @SecureMode Int): EGLSurface? {
        val surface: EGLSurface?
        if (secureMode == SECURE_MODE_SURFACELESS_CONTEXT) {
            surface = EGL14.EGL_NO_SURFACE
        } else {
            val pbufferAttributes: IntArray = if (secureMode == SECURE_MODE_PROTECTED_PBUFFER) {
                intArrayOf(EGL14.EGL_WIDTH, EGL_SURFACE_WIDTH, EGL14.EGL_HEIGHT, EGL_SURFACE_HEIGHT, EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE, EGL14.EGL_NONE)
            } else {
                intArrayOf(EGL14.EGL_WIDTH, EGL_SURFACE_WIDTH, EGL14.EGL_HEIGHT, EGL_SURFACE_HEIGHT, EGL14.EGL_NONE)
            }
            surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttributes,  /* offset= */0)
            checkGlException(surface != null, "eglCreatePbufferSurface failed")
        }
        val eglMadeCurrent = EGL14.eglMakeCurrent(display,  /* draw= */surface,  /* read= */surface, context)
        checkGlException(eglMadeCurrent, "eglMakeCurrent failed")
        return surface
    }

    @Throws(GlException::class)
    private fun generateTextureIds(textureIdHolder: IntArray?) {
        GLES20.glGenTextures( /* n= */1, textureIdHolder,  /* offset= */0)
        checkGlError()
    }

    companion object {
        /** No secure EGL surface and context required.  */
        const val SECURE_MODE_NONE = 0

        /** Creating a surfaceless, secured EGL context.  */
        const val SECURE_MODE_SURFACELESS_CONTEXT = 1

        /** Creating a secure surface backed by a pixel buffer.  */
        const val SECURE_MODE_PROTECTED_PBUFFER = 2

        private const val EGL_SURFACE_WIDTH = 1
        private const val EGL_SURFACE_HEIGHT = 1

        private val EGL_CONFIG_ATTRIBUTES = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE)

        private const val EGL_PROTECTED_CONTENT_EXT = 0x32C0
    }
}