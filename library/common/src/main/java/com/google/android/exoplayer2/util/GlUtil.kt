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

import android.content.Contextimport

android.content.pm.PackageManagerimport android.opengl.*import androidx.annotation.*
import com.google.android.exoplayer2.*
import java.nio.ByteBufferimport

java.nio.ByteOrderimport java.nio.FloatBufferimport java.util.*import javax.microedition.khronos.egl.EGL10

/** OpenGL ES utilities.  */
// GLES constants are used safely based on the API version.
object GlUtil {
    /** Number of elements in a 3d homogeneous coordinate vector describing a vertex.  */
    val HOMOGENEOUS_COORDINATE_VECTOR_SIZE: Int = 4

    /** Length of the normalized device coordinate (NDC) space, which spans from -1 to 1.  */
    val LENGTH_NDC: Float = 2f
    val EGL_CONFIG_ATTRIBUTES_RGBA_8888: IntArray = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,  /* redSize= */8,
            EGL14.EGL_GREEN_SIZE,  /* greenSize= */8,
            EGL14.EGL_BLUE_SIZE,  /* blueSize= */8,
            EGL14.EGL_ALPHA_SIZE,  /* alphaSize= */8,
            EGL14.EGL_DEPTH_SIZE,  /* depthSize= */0,
            EGL14.EGL_STENCIL_SIZE,  /* stencilSize= */0,
            EGL14.EGL_NONE
    )
    val EGL_CONFIG_ATTRIBUTES_RGBA_1010102: IntArray = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,  /* redSize= */10,
            EGL14.EGL_GREEN_SIZE,  /* greenSize= */10,
            EGL14.EGL_BLUE_SIZE,  /* blueSize= */10,
            EGL14.EGL_ALPHA_SIZE,  /* alphaSize= */2,
            EGL14.EGL_DEPTH_SIZE,  /* depthSize= */0,
            EGL14.EGL_STENCIL_SIZE,  /* stencilSize= */0,
            EGL14.EGL_NONE
    )

    // https://www.khronos.org/registry/EGL/extensions/EXT/EGL_EXT_protected_content.txt
    private val EXTENSION_PROTECTED_CONTENT: String = "EGL_EXT_protected_content"

    // https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_surfaceless_context.txt
    private val EXTENSION_SURFACELESS_CONTEXT: String = "EGL_KHR_surfaceless_context"

    // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
    private val EXTENSION_YUV_TARGET: String = "GL_EXT_YUV_target"
    private val EGL_WINDOW_SURFACE_ATTRIBUTES_NONE: IntArray = intArrayOf(EGL14.EGL_NONE)

    /** Bounds of normalized device coordinates, commonly used for defining viewport boundaries.  */
    val normalizedCoordinateBounds: FloatArray
        get() {
            return floatArrayOf(
                    -1f, -1f, 0f, 1f,
                    1f, -1f, 0f, 1f,
                    -1f, 1f, 0f, 1f,
                    1f, 1f, 0f, 1f
            )
        }

    /** Typical bounds used for sampling from textures.  */
    val textureCoordinateBounds: FloatArray
        get() {
            return floatArrayOf(
                    0f, 0f, 0f, 1f,
                    1f, 0f, 0f, 1f,
                    0f, 1f, 0f, 1f,
                    1f, 1f, 0f, 1f
            )
        }

    /** Creates a 4x4 identity matrix.  */
    fun create4x4IdentityMatrix(): FloatArray {
        val matrix: FloatArray = FloatArray(16)
        setToIdentity(matrix)
        return matrix
    }

    /** Sets the input `matrix` to an identity matrix.  */
    fun setToIdentity(matrix: FloatArray?) {
        Matrix.setIdentityM(matrix,  /* smOffset= */0)
    }

    /** Flattens the list of 4 element NDC coordinate vectors into a buffer.  */
    fun createVertexBuffer(vertexList: List<FloatArray?>): FloatArray {
        val vertexBuffer: FloatArray = FloatArray(HOMOGENEOUS_COORDINATE_VECTOR_SIZE * vertexList.size)
        for (i in vertexList.indices) {
            System.arraycopy( /* src= */
                    vertexList.get(i),  /* srcPos= */
                    0,  /* dest= */
                    vertexBuffer,  /* destPos= */
                    HOMOGENEOUS_COORDINATE_VECTOR_SIZE * i,  /* length= */
                    HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
        return vertexBuffer
    }

    /**
     * Returns whether creating a GL context with {@value #EXTENSION_PROTECTED_CONTENT} is possible.
     *
     *
     * If `true`, the device supports a protected output path for DRM content when using GL.
     */
    fun isProtectedContentExtensionSupported(context: Context): Boolean {
        if (Util.SDK_INT < 24) {
            return false
        }
        if (Util.SDK_INT < 26 && (("samsung" == Util.MANUFACTURER) || ("XT1650" == Util.MODEL))) {
            // Samsung devices running Nougat are known to be broken. See
            // https://github.com/google/ExoPlayer/issues/3373 and [Internal: b/37197802].
            // Moto Z XT1650 is also affected. See
            // https://github.com/google/ExoPlayer/issues/3215.
            return false
        }
        if ((Util.SDK_INT < 26
                        && !context
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE))) {
            // Pre API level 26 devices were not well tested unless they supported VR mode.
            return false
        }
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val eglExtensions: String? = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS)
        return eglExtensions != null && eglExtensions.contains(EXTENSION_PROTECTED_CONTENT)
    }

    /**
     * Returns whether the {@value #EXTENSION_SURFACELESS_CONTEXT} extension is supported.
     *
     *
     * This extension allows passing [EGL14.EGL_NO_SURFACE] for both the write and read
     * surfaces in a call to [EGL14.eglMakeCurrent].
     */
    val isSurfacelessContextExtensionSupported: Boolean
        get() {
            if (Util.SDK_INT < 17) {
                return false
            }
            val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val eglExtensions: String? = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS)
            return eglExtensions != null && eglExtensions.contains(EXTENSION_SURFACELESS_CONTEXT)
        }// Create a placeholder context and make it current to allow calling GLES20.glGetString().

    /**
     * Returns whether the {@value #EXTENSION_YUV_TARGET} extension is supported.
     *
     *
     * This extension allows sampling raw YUV values from an external texture, which is required
     * for HDR.
     */
    val isYuvTargetExtensionSupported: Boolean
        get() {
            if (Util.SDK_INT < 17) {
                return false
            }
            val glExtensions: String?
            if (Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT)) {
                // Create a placeholder context and make it current to allow calling GLES20.glGetString().
                try {
                    val eglDisplay: EGLDisplay = createEglDisplay()
                    val eglContext: EGLContext = createEglContext(eglDisplay)
                    focusPlaceholderEglSurface(eglContext, eglDisplay)
                    glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                    destroyEglContext(eglDisplay, eglContext)
                } catch (e: GlException) {
                    return false
                }
            } else {
                glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
            }
            return glExtensions != null && glExtensions.contains(EXTENSION_YUV_TARGET)
        }

    /** Returns an initialized default [EGLDisplay].  */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun createEglDisplay(): EGLDisplay {
        return Api17.createEglDisplay()
    }

    /**
     * Creates a new [EGLContext] for the specified [EGLDisplay].
     *
     *
     * Configures the [EGLContext] with [.EGL_CONFIG_ATTRIBUTES_RGBA_8888] and OpenGL
     * ES 2.0.
     *
     * @param eglDisplay The [EGLDisplay] to create an [EGLContext] for.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun createEglContext(eglDisplay: EGLDisplay?): EGLContext {
        return createEglContext(eglDisplay, EGL_CONFIG_ATTRIBUTES_RGBA_8888)
    }

    /**
     * Creates a new [EGLContext] for the specified [EGLDisplay].
     *
     * @param eglDisplay The [EGLDisplay] to create an [EGLContext] for.
     * @param configAttributes The attributes to configure EGL with. Accepts either [     ][.EGL_CONFIG_ATTRIBUTES_RGBA_1010102], which will request OpenGL ES 3.0, or [     ][.EGL_CONFIG_ATTRIBUTES_RGBA_8888], which will request OpenGL ES 2.0.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun createEglContext(eglDisplay: EGLDisplay?, configAttributes: IntArray?): EGLContext {
        Assertions.checkArgument((
                Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_8888)
                        || Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_1010102)))
        return Api17.createEglContext(
                eglDisplay,  /* version= */
                if (Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_1010102)) 3 else 2,
                configAttributes)
    }

    /**
     * Returns a new [EGLSurface] wrapping the specified `surface`.
     *
     *
     * The [EGLSurface] will configure with [.EGL_CONFIG_ATTRIBUTES_RGBA_8888] and
     * OpenGL ES 2.0.
     *
     * @param eglDisplay The [EGLDisplay] to attach the surface to.
     * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun getEglSurface(eglDisplay: EGLDisplay?, surface: Any?): EGLSurface {
        return Api17.getEglSurface(
                eglDisplay, surface, EGL_CONFIG_ATTRIBUTES_RGBA_8888, EGL_WINDOW_SURFACE_ATTRIBUTES_NONE)
    }

    /**
     * Returns a new [EGLSurface] wrapping the specified `surface`.
     *
     * @param eglDisplay The [EGLDisplay] to attach the surface to.
     * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
     * @param configAttributes The attributes to configure EGL with. Accepts [     ][.EGL_CONFIG_ATTRIBUTES_RGBA_1010102] and [.EGL_CONFIG_ATTRIBUTES_RGBA_8888].
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun getEglSurface(
            eglDisplay: EGLDisplay?, surface: Any?, configAttributes: IntArray?): EGLSurface {
        return Api17.getEglSurface(
                eglDisplay, surface, configAttributes, EGL_WINDOW_SURFACE_ATTRIBUTES_NONE)
    }

    /**
     * Creates a new [EGLSurface] wrapping a pixel buffer.
     *
     * @param eglDisplay The [EGLDisplay] to attach the surface to.
     * @param width The width of the pixel buffer.
     * @param height The height of the pixel buffer.
     * @param configAttributes EGL configuration attributes. Valid arguments include [     ][.EGL_CONFIG_ATTRIBUTES_RGBA_8888] and [.EGL_CONFIG_ATTRIBUTES_RGBA_1010102].
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    private fun createPbufferSurface(
            eglDisplay: EGLDisplay?, width: Int, height: Int, configAttributes: IntArray): EGLSurface {
        val pbufferAttributes: IntArray = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        )
        return Api17.createEglPbufferSurface(eglDisplay, configAttributes, pbufferAttributes)
    }

    /**
     * Creates and focuses a placeholder [EGLSurface].
     *
     *
     * This makes a [EGLContext] current when reading and writing to a surface is not
     * required, configured with [.EGL_CONFIG_ATTRIBUTES_RGBA_8888].
     *
     * @param eglContext The [EGLContext] to make current.
     * @param eglDisplay The [EGLDisplay] to attach the surface to.
     * @return [EGL14.EGL_NO_SURFACE] if supported and a 1x1 pixel buffer surface otherwise.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun focusPlaceholderEglSurface(eglContext: EGLContext?, eglDisplay: EGLDisplay?): EGLSurface {
        return createFocusedPlaceholderEglSurface(
                eglContext, eglDisplay, EGL_CONFIG_ATTRIBUTES_RGBA_8888)
    }

    /**
     * Creates and focuses a placeholder [EGLSurface].
     *
     *
     * This makes a [EGLContext] current when reading and writing to a surface is not
     * required.
     *
     * @param eglContext The [EGLContext] to make current.
     * @param eglDisplay The [EGLDisplay] to attach the surface to.
     * @param configAttributes The attributes to configure EGL with. Accepts [     ][.EGL_CONFIG_ATTRIBUTES_RGBA_1010102] and [.EGL_CONFIG_ATTRIBUTES_RGBA_8888].
     * @return A placeholder [EGLSurface] that has been focused to allow rendering to take
     * place, or [EGL14.EGL_NO_SURFACE] if the current context supports rendering without a
     * surface.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun createFocusedPlaceholderEglSurface(
            eglContext: EGLContext?, eglDisplay: EGLDisplay?, configAttributes: IntArray): EGLSurface {
        val eglSurface: EGLSurface = if (isSurfacelessContextExtensionSupported) EGL14.EGL_NO_SURFACE else createPbufferSurface(eglDisplay,  /* width= */1,  /* height= */1, configAttributes)
        focusEglSurface(eglDisplay, eglContext, eglSurface,  /* width= */1,  /* height= */1)
        return eglSurface
    }

    /**
     * Collects all OpenGL errors that occurred since this method was last called and throws a [ ] with the combined error message.
     */
    @Throws(GlException::class)
    fun checkGlError() {
        val errorMessageBuilder: StringBuilder = StringBuilder()
        var foundError: Boolean = false
        var error: Int
        while ((GLES20.glGetError().also({ error = it })) != GLES20.GL_NO_ERROR) {
            if (foundError) {
                errorMessageBuilder.append('\n')
            }
            errorMessageBuilder.append("glError: ").append(GLU.gluErrorString(error))
            foundError = true
        }
        if (foundError) {
            throw GlException(errorMessageBuilder.toString())
        }
    }

    /**
     * Asserts the texture size is valid.
     *
     * @param width The width for a texture.
     * @param height The height for a texture.
     * @throws GlException If the texture width or height is invalid.
     */
    @Throws(GlException::class)
    private fun assertValidTextureSize(width: Int, height: Int) {
        // TODO(b/201293185): Consider handling adjustments for sizes > GL_MAX_TEXTURE_SIZE
        //  (ex. downscaling appropriately) in a texture processor instead of asserting incorrect
        //  values.
        // For valid GL sizes, see:
        // https://www.khronos.org/registry/OpenGL-Refpages/es2.0/xhtml/glTexImage2D.xml
        val maxTextureSizeBuffer: IntArray = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSizeBuffer, 0)
        val maxTextureSize: Int = maxTextureSizeBuffer.get(0)
        Assertions.checkState(
                maxTextureSize > 0,
                "Create a OpenGL context first or run the GL methods on an OpenGL thread.")
        if (width < 0 || height < 0) {
            throw GlException("width or height is less than 0")
        }
        if (width > maxTextureSize || height > maxTextureSize) {
            throw GlException(
                    "width or height is greater than GL_MAX_TEXTURE_SIZE " + maxTextureSize)
        }
    }

    /** Fills the pixels in the current output render target with (r=0, g=0, b=0, a=0).  */
    @Throws(GlException::class)
    fun clearOutputFrame() {
        GLES20.glClearColor( /* red= */0f,  /* green= */0f,  /* blue= */0f,  /* alpha= */0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        checkGlError()
    }

    /**
     * Makes the specified `eglSurface` the render target, using a viewport of `width` by
     * `height` pixels.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun focusEglSurface(
            eglDisplay: EGLDisplay?, eglContext: EGLContext?, eglSurface: EGLSurface?, width: Int, height: Int) {
        Api17.focusRenderTarget(
                eglDisplay, eglContext, eglSurface,  /* framebuffer= */0, width, height)
    }

    /**
     * Makes the specified `framebuffer` the render target, using a viewport of `width` by
     * `height` pixels.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun focusFramebuffer(
            eglDisplay: EGLDisplay?,
            eglContext: EGLContext?,
            eglSurface: EGLSurface?,
            framebuffer: Int,
            width: Int,
            height: Int) {
        Api17.focusRenderTarget(eglDisplay, eglContext, eglSurface, framebuffer, width, height)
    }

    /**
     * Makes the specified `framebuffer` the render target, using a viewport of `width` by
     * `height` pixels.
     *
     *
     * The caller must ensure that there is a current OpenGL context before calling this method.
     *
     * @param framebuffer The identifier of the framebuffer object to bind as the output render
     * target.
     * @param width The viewport width, in pixels.
     * @param height The viewport height, in pixels.
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun focusFramebufferUsingCurrentContext(framebuffer: Int, width: Int, height: Int) {
        Api17.focusFramebufferUsingCurrentContext(framebuffer, width, height)
    }

    /**
     * Deletes a GL texture.
     *
     * @param textureId The ID of the texture to delete.
     */
    @Throws(GlException::class)
    fun deleteTexture(textureId: Int) {
        GLES20.glDeleteTextures( /* n= */1, intArrayOf(textureId),  /* offset= */0)
        checkGlError()
    }

    /**
     * Destroys the [EGLContext] identified by the provided [EGLDisplay] and [ ].
     */
    @RequiresApi(17)
    @Throws(GlException::class)
    fun destroyEglContext(
            eglDisplay: EGLDisplay?, eglContext: EGLContext?) {
        Api17.destroyEglContext(eglDisplay, eglContext)
    }

    /**
     * Allocates a FloatBuffer with the given data.
     *
     * @param data Used to initialize the new buffer.
     */
    fun createBuffer(data: FloatArray): FloatBuffer {
        return createBuffer(data.size).put(data).flip() as FloatBuffer
    }

    /**
     * Allocates a FloatBuffer.
     *
     * @param capacity The new buffer's capacity, in floats.
     */
    private fun createBuffer(capacity: Int): FloatBuffer {
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT)
        return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    /**
     * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
     * GL_CLAMP_TO_EDGE wrapping.
     */
    @Throws(GlException::class)
    fun createExternalTexture(): Int {
        val texId: Int = generateTexture()
        bindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        return texId
    }

    /**
     * Allocates a new RGBA texture with the specified dimensions and color component precision.
     *
     * @param width The width of the new texture in pixels.
     * @param height The height of the new texture in pixels.
     * @param useHighPrecisionColorComponents If `false`, uses 8-bit unsigned bytes. If `true`, use 16-bit (half-precision) floating-point.
     * @throws GlException If the texture allocation fails.
     * @return The texture identifier for the newly-allocated texture.
     */
    @Throws(GlException::class)
    fun createTexture(width: Int, height: Int, useHighPrecisionColorComponents: Boolean): Int {
        // TODO(227624622): Implement a pixel test that confirms 16f has less posterization.
        if (useHighPrecisionColorComponents) {
            Assertions.checkState(Util.SDK_INT >= 18, "GLES30 extensions are not supported below API 18.")
            return createTexture(width, height, GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT)
        }
        return createTexture(width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE)
    }

    /**
     * Allocates a new RGBA texture with the specified dimensions and color component precision.
     *
     * @param width The width of the new texture in pixels.
     * @param height The height of the new texture in pixels.
     * @param internalFormat The number of color components in the texture, as well as their format.
     * @param type The data type of the pixel data.
     * @throws GlException If the texture allocation fails.
     * @return The texture identifier for the newly-allocated texture.
     */
    @Throws(GlException::class)
    private fun createTexture(width: Int, height: Int, internalFormat: Int, type: Int): Int {
        assertValidTextureSize(width, height)
        val texId: Int = generateTexture()
        bindTexture(GLES20.GL_TEXTURE_2D, texId)
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(width * height * 4)
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,  /* level= */
                0,
                internalFormat,
                width,
                height,  /* border= */
                0,
                GLES20.GL_RGBA,
                type,
                byteBuffer)
        checkGlError()
        return texId
    }

    /** Returns a new GL texture identifier.  */
    @Throws(GlException::class)
    private fun generateTexture(): Int {
        checkGlException(
                !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context")
        val texId: IntArray = IntArray(1)
        GLES20.glGenTextures( /* n= */1, texId,  /* offset= */0)
        checkGlError()
        return texId.get(0)
    }

    /**
     * Binds the texture of the given type with default configuration of GL_LINEAR filtering and
     * GL_CLAMP_TO_EDGE wrapping.
     *
     * @param textureTarget The target to which the texture is bound, e.g. [     ][GLES20.GL_TEXTURE_2D] for a two-dimensional texture or [     ][GLES11Ext.GL_TEXTURE_EXTERNAL_OES] for an external texture.
     * @param texId The texture identifier.
     */
    @Throws(GlException::class)
    fun bindTexture(textureTarget: Int, texId: Int) {
        GLES20.glBindTexture(textureTarget, texId)
        checkGlError()
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        checkGlError()
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        checkGlError()
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError()
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError()
    }

    /**
     * Returns a new framebuffer for the texture.
     *
     * @param texId The identifier of the texture to attach to the framebuffer.
     */
    @Throws(GlException::class)
    fun createFboForTexture(texId: Int): Int {
        checkGlException(
                !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context")
        val fboId: IntArray = IntArray(1)
        GLES20.glGenFramebuffers( /* n= */1, fboId,  /* offset= */0)
        checkGlError()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId.get(0))
        checkGlError()
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0)
        checkGlError()
        return fboId.get(0)
    }

    /**
     * Throws a [GlException] with the given message if `expression` evaluates to `false`.
     */
    @Throws(GlException::class)
    fun checkGlException(expression: Boolean, errorMessage: String?) {
        if (!expression) {
            throw GlException(errorMessage)
        }
    }

    @Throws(GlException::class)
    private fun checkEglException(errorMessage: String) {
        val error: Int = EGL14.eglGetError()
        checkGlException(error == EGL14.EGL_SUCCESS, errorMessage + ", error code: " + error)
    }

    /** Thrown when an OpenGL error occurs.  */
    class GlException
    /** Creates an instance with the specified error message.  */
    constructor(message: String?) : Exception(message)

    @RequiresApi(17)
    private object Api17 {
        @DoNotInline
        @Throws(GlException::class)
        fun createEglDisplay(): EGLDisplay {
            val eglDisplay: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            checkGlException(!(eglDisplay == EGL14.EGL_NO_DISPLAY), "No EGL display.")
            checkGlException(
                    EGL14.eglInitialize(
                            eglDisplay, IntArray(1),  /* majorOffset= */
                            0, IntArray(1),  /* minorOffset= */
                            0),
                    "Error in eglInitialize.")
            checkGlError()
            return eglDisplay
        }

        @DoNotInline
        @Throws(GlException::class)
        fun createEglContext(
                eglDisplay: EGLDisplay?, version: Int, configAttributes: IntArray?): EGLContext {
            val contextAttributes: IntArray = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE)
            val eglContext: EGLContext? = EGL14.eglCreateContext(
                    eglDisplay,
                    getEglConfig(eglDisplay, configAttributes),
                    EGL14.EGL_NO_CONTEXT,
                    contextAttributes,  /* offset= */
                    0)
            if (eglContext == null) {
                EGL14.eglTerminate(eglDisplay)
                throw GlException(
                        ("eglCreateContext() failed to create a valid context. The device may not support EGL"
                                + " version "
                                + version))
            }
            checkGlError()
            return eglContext
        }

        @DoNotInline
        @Throws(GlException::class)
        fun getEglSurface(
                eglDisplay: EGLDisplay?,
                surface: Any?,
                configAttributes: IntArray?,
                windowSurfaceAttributes: IntArray?): EGLSurface {
            val eglSurface: EGLSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    getEglConfig(eglDisplay, configAttributes),
                    surface,
                    windowSurfaceAttributes,  /* offset= */
                    0)
            checkEglException("Error creating surface")
            return eglSurface
        }

        @DoNotInline
        @Throws(GlException::class)
        fun createEglPbufferSurface(
                eglDisplay: EGLDisplay?, configAttributes: IntArray?, pbufferAttributes: IntArray?): EGLSurface {
            val eglSurface: EGLSurface = EGL14.eglCreatePbufferSurface(
                    eglDisplay,
                    getEglConfig(eglDisplay, configAttributes),
                    pbufferAttributes,  /* offset= */
                    0)
            checkEglException("Error creating surface")
            return eglSurface
        }

        @DoNotInline
        @Throws(GlException::class)
        fun focusRenderTarget(
                eglDisplay: EGLDisplay?,
                eglContext: EGLContext?,
                eglSurface: EGLSurface?,
                framebuffer: Int,
                width: Int,
                height: Int) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            checkEglException("Error making context current")
            focusFramebufferUsingCurrentContext(framebuffer, width, height)
        }

        @DoNotInline
        @Throws(GlException::class)
        fun focusFramebufferUsingCurrentContext(framebuffer: Int, width: Int, height: Int) {
            checkGlException(
                    !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context")
            val boundFramebuffer: IntArray = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFramebuffer,  /* offset= */0)
            if (boundFramebuffer.get(0) != framebuffer) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            }
            checkGlError()
            GLES20.glViewport( /* x= */0,  /* y= */0, width, height)
            checkGlError()
        }

        @DoNotInline
        @Throws(GlException::class)
        fun destroyEglContext(
                eglDisplay: EGLDisplay?, eglContext: EGLContext?) {
            if (eglDisplay == null) {
                return
            }
            EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            checkEglException("Error releasing context")
            if (eglContext != null) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                checkEglException("Error destroying context")
            }
            EGL14.eglReleaseThread()
            checkEglException("Error releasing thread")
            EGL14.eglTerminate(eglDisplay)
            checkEglException("Error terminating display")
        }

        @DoNotInline
        @Throws(GlException::class)
        private fun getEglConfig(eglDisplay: EGLDisplay?, attributes: IntArray?): EGLConfig? {
            val eglConfigs: Array<EGLConfig?> = arrayOfNulls(1)
            if (!EGL14.eglChooseConfig(
                            eglDisplay,
                            attributes,  /* attrib_listOffset= */
                            0,
                            eglConfigs,  /* configsOffset= */
                            0,  /* config_size= */
                            1, IntArray(1),  /* num_configOffset= */
                            0)) {
                throw GlException("eglChooseConfig failed.")
            }
            return eglConfigs.get(0)
        }
    }
}