/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.opengl.GLES11Extimport

android.opengl.GLES20import com.google.android.exoplayer2.util.GlUtil.GlException android.content.Context
import com.google.android.exoplayer2.util.NotificationUtil
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import com.google.android.exoplayer2.util.ParsableBitArray
import com.google.android.exoplayer2.util.TimestampAdjuster
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import com.google.android.exoplayer2.util.XmlPullParserUtil
import com.google.android.exoplayer2.util.UnknownNull
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
import com.google.android.exoplayer2.util.CodecSpecificDataUtil
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Api32
import com.google.android.exoplayer2.audio.AudioAttributes.AudioAttributesV21
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import androidx.annotation.FloatRange
import android.media.MediaCodec
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdState
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.C.StereoMode
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.DeviceInfo
import android.view.SurfaceHolder
import android.view.TextureView
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
import com.google.android.exoplayer2.BundleListRetriever
import com.google.android.exoplayer2.Timeline.RemotableTimeline
import com.google.android.exoplayer2.MediaItem.ClippingProperties
import com.google.android.exoplayer2.MediaItem.PlaybackProperties
import com.google.android.exoplayer2.MediaItem.RequestMetadata
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.common.primitives.Ints
import android.view.accessibility.CaptioningManager
import com.google.android.exoplayer2.DeviceInfo.PlaybackType
import com.google.android.exoplayer2.MediaMetadata.PictureType
import com.google.android.exoplayer2.MediaMetadata.FolderType
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.BasePlayer
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import com.google.android.exoplayer2.SimpleBasePlayer
import androidx.annotation.CallSuper
import android.media.MediaPlayerimport

java.io.IOExceptionimport java.io.InputStreamimport java.lang.IllegalStateExceptionimport java.nio.Bufferimport java.util.HashMap
/**
 * Represents a GLSL shader program.
 *
 *
 * After constructing a program, keep a reference for its lifetime and call [.delete] (or
 * release the current GL context) when it's no longer needed.
 */
class GlProgram constructor(vertexShaderGlsl: String?, fragmentShaderGlsl: String?) {
    /** The identifier of a compiled and linked GLSL shader program.  */
    private val programId: Int
    private val attributes: Array<Attribute?>
    private val uniforms: Array<Uniform?>
    private val attributeByName: MutableMap<String, Attribute>
    private val uniformByName: MutableMap<String, Uniform>

    /**
     * Compiles a GL shader program from vertex and fragment shader GLSL GLES20 code.
     *
     * @param context The [Context].
     * @param vertexShaderFilePath The path to a vertex shader program.
     * @param fragmentShaderFilePath The path to a fragment shader program.
     * @throws IOException When failing to read shader files.
     */
    constructor(context: Context, vertexShaderFilePath: String?, fragmentShaderFilePath: String?) : this(loadAsset(context, vertexShaderFilePath), loadAsset(context, fragmentShaderFilePath)) {}

    /**
     * Creates a GL shader program from vertex and fragment shader GLSL GLES20 code.
     *
     *
     * This involves slow steps, like compiling, linking, and switching the GL program, so do not
     * call this in fast rendering loops.
     *
     * @param vertexShaderGlsl The vertex shader program.
     * @param fragmentShaderGlsl The fragment shader program.
     */
    init {
        programId = GLES20.glCreateProgram()
        GlUtil.checkGlError()

        // Add the vertex and fragment shaders.
        addShader(programId, GLES20.GL_VERTEX_SHADER, vertexShaderGlsl)
        addShader(programId, GLES20.GL_FRAGMENT_SHADER, fragmentShaderGlsl)

        // Link and use the program, and enumerate attributes/uniforms.
        GLES20.glLinkProgram(programId)
        val linkStatus: IntArray = intArrayOf(GLES20.GL_FALSE)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus,  /* offset= */0)
        GlUtil.checkGlException(
                linkStatus.get(0) == GLES20.GL_TRUE,
                "Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(programId))
        GLES20.glUseProgram(programId)
        attributeByName = HashMap()
        val attributeCount: IntArray = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_ATTRIBUTES, attributeCount,  /* offset= */0)
        attributes = arrayOfNulls(attributeCount.get(0))
        for (i in 0 until attributeCount.get(0)) {
            val attribute: Attribute = Attribute.create(programId, i)
            attributes.get(i) = attribute
            attributeByName.put(attribute.name, attribute)
        }
        uniformByName = HashMap()
        val uniformCount: IntArray = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORMS, uniformCount,  /* offset= */0)
        uniforms = arrayOfNulls(uniformCount.get(0))
        for (i in 0 until uniformCount.get(0)) {
            val uniform: Uniform = Uniform.create(programId, i)
            uniforms.get(i) = uniform
            uniformByName.put(uniform.name, uniform)
        }
        GlUtil.checkGlError()
    }

    /** Returns the location of an [Attribute].  */
    private fun getAttributeLocation(attributeName: String): Int {
        return getAttributeLocation(programId, attributeName)
    }

    /** Returns the location of a [Uniform].  */
    fun getUniformLocation(uniformName: String): Int {
        return getUniformLocation(programId, uniformName)
    }

    /**
     * Uses the program.
     *
     *
     * Call this in the rendering loop to switch between different programs.
     */
    @Throws(GlException::class)
    fun use() {
        GLES20.glUseProgram(programId)
        GlUtil.checkGlError()
    }

    /** Deletes the program. Deleted programs cannot be used again.  */
    @Throws(GlException::class)
    fun delete() {
        GLES20.glDeleteProgram(programId)
        GlUtil.checkGlError()
    }

    /**
     * Returns the location of an [Attribute], which has been enabled as a vertex attribute
     * array.
     */
    @Throws(GlException::class)
    fun getAttributeArrayLocationAndEnable(attributeName: String): Int {
        val location: Int = getAttributeLocation(attributeName)
        GLES20.glEnableVertexAttribArray(location)
        GlUtil.checkGlError()
        return location
    }

    /** Sets a float buffer type attribute.  */
    fun setBufferAttribute(name: String, values: FloatArray, size: Int) {
        Assertions.checkNotNull(attributeByName.get(name))!!.setBuffer(values, size)
    }

    /**
     * Sets a texture sampler type uniform.
     *
     * @param name The uniform's name.
     * @param texId The texture identifier.
     * @param texUnitIndex The texture unit index. Use a different index (0, 1, 2, ...) for each
     * texture sampler in the program.
     */
    fun setSamplerTexIdUniform(name: String, texId: Int, texUnitIndex: Int) {
        Assertions.checkNotNull(uniformByName.get(name))!!.setSamplerTexId(texId, texUnitIndex)
    }

    /** Sets an `int` type uniform.  */
    fun setIntUniform(name: String, value: Int) {
        Assertions.checkNotNull(uniformByName.get(name))!!.setInt(value)
    }

    /** Sets a `float` type uniform.  */
    fun setFloatUniform(name: String, value: Float) {
        Assertions.checkNotNull(uniformByName.get(name))!!.setFloat(value)
    }

    /** Sets a `float[]` type uniform.  */
    fun setFloatsUniform(name: String, value: FloatArray) {
        Assertions.checkNotNull(uniformByName.get(name))!!.setFloats(value)
    }

    /** Binds all attributes and uniforms in the program.  */
    @Throws(GlException::class)
    fun bindAttributesAndUniforms() {
        for (attribute: Attribute? in attributes) {
            attribute!!.bind()
        }
        for (uniform: Uniform? in uniforms) {
            uniform!!.bind()
        }
    }

    /**
     * GL attribute, which can be attached to a buffer with [Attribute.setBuffer].
     */
    private class Attribute private constructor(
            /** The name of the attribute in the GLSL sources.  */
            val name: String, private val index: Int, private val location: Int) {
        private var buffer: Buffer? = null
        private var size: Int = 0

        /**
         * Configures [.bind] to attach vertices in `buffer` (each of size `size`
         * elements) to this [Attribute].
         *
         * @param buffer Buffer to bind to this attribute.
         * @param size Number of elements per vertex.
         */
        fun setBuffer(buffer: FloatArray, size: Int) {
            this.buffer = GlUtil.createBuffer(buffer)
            this.size = size
        }

        /**
         * Sets the vertex attribute to whatever was attached via [.setBuffer].
         *
         *
         * Should be called before each drawing call.
         */
        @Throws(GlException::class)
        fun bind() {
            val buffer: Buffer? = Assertions.checkNotNull(buffer, "call setBuffer before bind")
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,  /* buffer= */0)
            GLES20.glVertexAttribPointer(
                    location, size, GLES20.GL_FLOAT,  /* normalized= */false,  /* stride= */0, buffer)
            GLES20.glEnableVertexAttribArray(index)
            GlUtil.checkGlError()
        }

        companion object {
            /* Returns the attribute at the given index in the program. */
            fun create(programId: Int, index: Int): Attribute {
                val length: IntArray = IntArray(1)
                GLES20.glGetProgramiv(
                        programId, GLES20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, length,  /* offset= */0)
                val nameBytes: ByteArray = ByteArray(length.get(0))
                GLES20.glGetActiveAttrib(
                        programId,
                        index,
                        length.get(0), IntArray(1),  /* lengthOffset= */
                        0, IntArray(1),  /* sizeOffset= */
                        0, IntArray(1),  /* typeOffset= */
                        0,
                        nameBytes,  /* nameOffset= */
                        0)
                val name: String = String(nameBytes,  /* offset= */0, getCStringLength(nameBytes))
                val location: Int = getAttributeLocation(programId, name)
                return Attribute(name, index, location)
            }
        }
    }

    /**
     * GL uniform, which can be attached to a sampler using [Uniform.setSamplerTexId].
     */
    private class Uniform private constructor(
            /** The name of the uniform in the GLSL sources.  */
            val name: String, private val location: Int, private val type: Int) {
        private val floatValue: FloatArray
        private var intValue: Int = 0
        private var texIdValue: Int = 0
        private var texUnitIndex: Int = 0

        init {
            floatValue = FloatArray(16)
        }

        /**
         * Configures [.bind] to use the specified `texId` for this sampler uniform.
         *
         * @param texId The GL texture identifier from which to sample.
         * @param texUnitIndex The GL texture unit index.
         */
        fun setSamplerTexId(texId: Int, texUnitIndex: Int) {
            texIdValue = texId
            this.texUnitIndex = texUnitIndex
        }

        /** Configures [.bind] to use the specified `int` `value`.  */
        fun setInt(value: Int) {
            intValue = value
        }

        /** Configures [.bind] to use the specified `float` `value`.  */
        fun setFloat(value: Float) {
            floatValue.get(0) = value
        }

        /** Configures [.bind] to use the specified `float[]` `value`.  */
        fun setFloats(value: FloatArray) {
            System.arraycopy(value,  /* srcPos= */0, floatValue,  /* destPos= */0, value.size)
        }

        /**
         * Sets the uniform to whatever value was passed via [.setSamplerTexId], [ ][.setFloat] or [.setFloats].
         *
         *
         * Should be called before each drawing call.
         */
        @Throws(GlException::class)
        fun bind() {
            when (type) {
                GLES20.GL_INT -> GLES20.glUniform1i(location, intValue)
                GLES20.GL_FLOAT -> {
                    GLES20.glUniform1fv(location,  /* count= */1, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }
                GLES20.GL_FLOAT_VEC2 -> {
                    GLES20.glUniform2fv(location,  /* count= */1, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }
                GLES20.GL_FLOAT_VEC3 -> {
                    GLES20.glUniform3fv(location,  /* count= */1, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }
                GLES20.GL_FLOAT_MAT3 -> {
                    GLES20.glUniformMatrix3fv(
                            location,  /* count= */1,  /* transpose= */false, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }
                GLES20.GL_FLOAT_MAT4 -> {
                    GLES20.glUniformMatrix4fv(
                            location,  /* count= */1,  /* transpose= */false, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }
                GLES20.GL_SAMPLER_2D, GLES11Ext.GL_SAMPLER_EXTERNAL_OES, GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT -> {
                    if (texIdValue == 0) {
                        throw IllegalStateException("No call to setSamplerTexId() before bind.")
                    }
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texUnitIndex)
                    GlUtil.checkGlError()
                    GlUtil.bindTexture(
                            if (type == GLES20.GL_SAMPLER_2D) GLES20.GL_TEXTURE_2D else GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                            texIdValue)
                    GLES20.glUniform1i(location, texUnitIndex)
                    GlUtil.checkGlError()
                }
                else -> throw IllegalStateException("Unexpected uniform type: " + type)
            }
        }

        companion object {
            /** Returns the uniform at the given index in the program.  */
            fun create(programId: Int, index: Int): Uniform {
                val length: IntArray = IntArray(1)
                GLES20.glGetProgramiv(
                        programId, GLES20.GL_ACTIVE_UNIFORM_MAX_LENGTH, length,  /* offset= */0)
                val type: IntArray = IntArray(1)
                val nameBytes: ByteArray = ByteArray(length.get(0))
                GLES20.glGetActiveUniform(
                        programId,
                        index,
                        length.get(0), IntArray(1),  /* lengthOffset= */
                        0, IntArray(1),  /*sizeOffset= */
                        0,
                        type,  /* typeOffset= */
                        0,
                        nameBytes,  /* nameOffset= */
                        0)
                val name: String = String(nameBytes,  /* offset= */0, getCStringLength(nameBytes))
                val location: Int = getUniformLocation(programId, name)
                return Uniform(name, location, type.get(0))
            }
        }
    }

    companion object {
        // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
        private val GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT: Int = 0x8BE7

        /**
         * Loads a file from the assets folder.
         *
         * @param context The [Context].
         * @param assetPath The path to the file to load, from the assets folder.
         * @return The content of the file to load.
         * @throws IOException If the file couldn't be read.
         */
        @Throws(IOException::class)
        fun loadAsset(context: Context, assetPath: String?): String? {
            var inputStream: InputStream? = null
            try {
                inputStream = context.getAssets().open((assetPath)!!)
                return Util.fromUtf8Bytes(Util.toByteArray(inputStream))
            } finally {
                Util.closeQuietly(inputStream)
            }
        }

        @Throws(GlException::class)
        private fun addShader(programId: Int, type: Int, glsl: String?) {
            val shader: Int = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, glsl)
            GLES20.glCompileShader(shader)
            val result: IntArray = intArrayOf(GLES20.GL_FALSE)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result,  /* offset= */0)
            GlUtil.checkGlException(
                    result.get(0) == GLES20.GL_TRUE, GLES20.glGetShaderInfoLog(shader) + ", source: " + glsl)
            GLES20.glAttachShader(programId, shader)
            GLES20.glDeleteShader(shader)
            GlUtil.checkGlError()
        }

        private fun getAttributeLocation(programId: Int, attributeName: String): Int {
            return GLES20.glGetAttribLocation(programId, attributeName)
        }

        private fun getUniformLocation(programId: Int, uniformName: String): Int {
            return GLES20.glGetUniformLocation(programId, uniformName)
        }

        /** Returns the length of the null-terminated C string in `cString`.  */
        private fun getCStringLength(cString: ByteArray): Int {
            for (i in cString.indices) {
                if (cString.get(i) == '\u0000'.code.toByte()) {
                    return i
                }
            }
            return cString.size
        }
    }
}