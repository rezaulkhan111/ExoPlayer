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

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.checkGlError
import com.google.android.exoplayer2.util.GlUtil.checkGlException
import com.google.android.exoplayer2.util.GlUtil.createBuffer
import com.google.android.exoplayer2.util.Util.closeQuietly
import com.google.android.exoplayer2.util.Util.fromUtf8Bytes
import com.google.android.exoplayer2.util.Util.toByteArray
import java.io.IOException
import java.io.InputStream
import java.nio.Buffer

/**
 * Represents a GLSL shader program.
 *
 *
 * After constructing a program, keep a reference for its lifetime and call [.delete] (or
 * release the current GL context) when it's no longer needed.
 */
class GlProgram {
    // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt

    /** The identifier of a compiled and linked GLSL shader program.  */
    private var programId = 0

    private var attributes: Array<Attribute?>? = null
    private var uniforms: Array<Uniform?>? = null
    private var attributeByName: HashMap<String, Attribute>? = null
    private var uniformByName: HashMap<String, Uniform>? = null

    /**
     * Compiles a GL shader program from vertex and fragment shader GLSL GLES20 code.
     *
     * @param context The [Context].
     * @param vertexShaderFilePath The path to a vertex shader program.
     * @param fragmentShaderFilePath The path to a fragment shader program.
     * @throws IOException When failing to read shader files.
     */
    @Throws(IOException::class, GlException::class)
    constructor(context: Context, vertexShaderFilePath: String?, fragmentShaderFilePath: String?) {
        GlProgram(loadAsset(context, vertexShaderFilePath), loadAsset(context, fragmentShaderFilePath))
    }

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
        return try {
            inputStream = context.assets.open(assetPath!!)
            fromUtf8Bytes(toByteArray(inputStream))
        } finally {
            closeQuietly(inputStream)
        }
    }

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
    @Throws(GlException::class)
    constructor(vertexShaderGlsl: String?, fragmentShaderGlsl: String?) {
        programId = GLES20.glCreateProgram()
        checkGlError()

        // Add the vertex and fragment shaders.
        addShader(programId, GLES20.GL_VERTEX_SHADER, vertexShaderGlsl)
        addShader(programId, GLES20.GL_FRAGMENT_SHADER, fragmentShaderGlsl)

        // Link and use the program, and enumerate attributes/uniforms.
        GLES20.glLinkProgram(programId)
        val linkStatus = intArrayOf(GLES20.GL_FALSE)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus,  /* offset= */0)
        checkGlException(linkStatus[0] == GLES20.GL_TRUE, """
                Unable to link shader program: 
                ${GLES20.glGetProgramInfoLog(programId)}
                """.trimIndent())
        GLES20.glUseProgram(programId)
        attributeByName = HashMap()
        val attributeCount = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_ATTRIBUTES, attributeCount,  /* offset= */0)
        attributes = arrayOfNulls(attributeCount[0])
        for (i in 0 until attributeCount[0]) {
            val attribute = Attribute.create(programId, i)
            attributes!![i] = attribute
            attributeByName!![attribute.name] = attribute
        }
        uniformByName = HashMap()
        val uniformCount = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORMS, uniformCount,  /* offset= */0)
        uniforms = arrayOfNulls(uniformCount[0])
        for (i in 0 until uniformCount[0]) {
            val uniform = Uniform.create(programId, i)
            uniforms!![i] = uniform
            uniformByName!![uniform.name!!] = uniform
        }
        checkGlError()
    }

    @Throws(GlException::class)
    private fun addShader(programId: Int, type: Int, glsl: String) {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, glsl)
        GLES20.glCompileShader(shader)
        val result = intArrayOf(GLES20.GL_FALSE)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result,  /* offset= */0)
        checkGlException(result[0] == GLES20.GL_TRUE, GLES20.glGetShaderInfoLog(shader) + ", source: " + glsl)
        GLES20.glAttachShader(programId, shader)
        GLES20.glDeleteShader(shader)
        checkGlError()
    }

    private fun getAttributeLocation(programId: Int, attributeName: String): Int {
        return GLES20.glGetAttribLocation(programId, attributeName)
    }

    /** Returns the location of an [Attribute].  */
    private fun getAttributeLocation(attributeName: String): Int {
        return getAttributeLocation(programId, attributeName)
    }

    private fun getUniformLocation(programId: Int, uniformName: String): Int {
        return GLES20.glGetUniformLocation(programId, uniformName)
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
        checkGlError()
    }

    /** Deletes the program. Deleted programs cannot be used again.  */
    @Throws(GlException::class)
    fun delete() {
        GLES20.glDeleteProgram(programId)
        checkGlError()
    }

    /**
     * Returns the location of an [Attribute], which has been enabled as a vertex attribute
     * array.
     */
    @Throws(GlException::class)
    fun getAttributeArrayLocationAndEnable(attributeName: String): Int {
        val location = getAttributeLocation(attributeName)
        GLES20.glEnableVertexAttribArray(location)
        checkGlError()
        return location
    }

    /** Sets a float buffer type attribute.  */
    fun setBufferAttribute(name: String?, values: FloatArray?, size: Int) {
        checkNotNull(attributeByName!![name!!]).setBuffer(values!!, size)
    }

    /**
     * Sets a texture sampler type uniform.
     *
     * @param name The uniform's name.
     * @param texId The texture identifier.
     * @param texUnitIndex The texture unit index. Use a different index (0, 1, 2, ...) for each
     * texture sampler in the program.
     */
    fun setSamplerTexIdUniform(name: String?, texId: Int, texUnitIndex: Int) {
        checkNotNull(uniformByName!![name!!]).setSamplerTexId(texId, texUnitIndex)
    }

    /** Sets an `int` type uniform.  */
    fun setIntUniform(name: String?, value: Int) {
        checkNotNull(uniformByName!![name!!]).setInt(value)
    }

    /** Sets a `float` type uniform.  */
    fun setFloatUniform(name: String?, value: Float) {
        checkNotNull(uniformByName!![name!!]).setFloat(value)
    }

    /** Sets a `float[]` type uniform.  */
    fun setFloatsUniform(name: String?, value: FloatArray?) {
        checkNotNull(uniformByName!![name!!]).setFloats(value!!)
    }

    /** Binds all attributes and uniforms in the program.  */
    @Throws(GlException::class)
    fun bindAttributesAndUniforms() {
        for (attribute in attributes!!) {
            attribute?.bind()
        }
        for (uniform in uniforms!!) {
            uniform?.bind()
        }
    }

    /** Returns the length of the null-terminated C string in `cString`.  */
    private fun getCStringLength(cString: ByteArray): Int {
        for (i in cString.indices) {
            if (cString[i] == '\u0000'.code.toByte()) {
                return i
            }
        }
        return cString.size
    }

    /**
     * GL attribute, which can be attached to a buffer with [Attribute.setBuffer].
     */
    private class Attribute {
        /* Returns the attribute at the given index in the program. */
        companion object {
            fun create(programId: Int, index: Int): Attribute {
                val length = IntArray(1)
                GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, length,  /* offset= */0)
                val nameBytes = ByteArray(length[0])
                GLES20.glGetActiveAttrib(programId, index, length[0], IntArray(1),  /* lengthOffset= */
                        0, IntArray(1),  /* sizeOffset= */
                        0, IntArray(1),  /* typeOffset= */
                        0, nameBytes,  /* nameOffset= */
                        0)
                val name = String(nameBytes,  /* offset= */0, getCStringLength(nameBytes))
                val location = getAttributeLocation(programId, name)
                return Attribute(name, index, location)
            }
        }

        /** The name of the attribute in the GLSL sources.  */
        var name: String? = null

        private var index = 0
        private var location = 0

        private var buffer: Buffer? = null
        private var size = 0

        private constructor(name: String, index: Int, location: Int) {
            this.name = name
            this.index = index
            this.location = location
        }

        /**
         * Configures [.bind] to attach vertices in `buffer` (each of size `size`
         * elements) to this [Attribute].
         *
         * @param buffer Buffer to bind to this attribute.
         * @param size Number of elements per vertex.
         */
        fun setBuffer(buffer: FloatArray?, size: Int) {
            this.buffer = createBuffer(buffer!!)
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
            val buffer = checkNotNull(buffer) { "call setBuffer before bind" }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,  /* buffer= */0)
            GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT,  /* normalized= */false,  /* stride= */0, buffer)
            GLES20.glEnableVertexAttribArray(index)
            checkGlError()
        }
    }

    /**
     * GL uniform, which can be attached to a sampler using [Uniform.setSamplerTexId].
     */
    private class Uniform {

        /** The name of the uniform in the GLSL sources.  */
        var name: String? = null

        private var location = 0
        private var type = 0
        private var floatValue: FloatArray

        private var intValue = 0
        private var texIdValue = 0
        private var texUnitIndex = 0

        private constructor(name: String, location: Int, type: Int) {
            this.name = name
            this.location = location
            this.type = type
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
            floatValue[0] = value
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
        @Throws(GlUtil.GlException::class)
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
                    GLES20.glUniformMatrix3fv(location,  /* count= */1,  /* transpose= */false, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }

                GLES20.GL_FLOAT_MAT4 -> {
                    GLES20.glUniformMatrix4fv(location,  /* count= */1,  /* transpose= */false, floatValue,  /* offset= */0)
                    GlUtil.checkGlError()
                }

                GLES20.GL_SAMPLER_2D, GLES11Ext.GL_SAMPLER_EXTERNAL_OES, GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT -> {
                    if (texIdValue == 0) {
                        throw IllegalStateException("No call to setSamplerTexId() before bind.")
                    }
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texUnitIndex)
                    GlUtil.checkGlError()
                    GlUtil.bindTexture(if (type == GLES20.GL_SAMPLER_2D) GLES20.GL_TEXTURE_2D else GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIdValue)
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
                GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORM_MAX_LENGTH, length,  /* offset= */0)
                val type: IntArray = IntArray(1)
                val nameBytes: ByteArray = ByteArray(length[0])
                GLES20.glGetActiveUniform(programId, index, length[0], IntArray(1),  /* lengthOffset= */
                        0, IntArray(1),  /*sizeOffset= */
                        0, type,  /* typeOffset= */
                        0, nameBytes,  /* nameOffset= */
                        0)
                val name: String = String(nameBytes,  /* offset= */0, getCStringLength(nameBytes))
                val location: Int = getUniformLocation(programId, name)
                return Uniform(name, location, type[0])
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

        @Throws(GlUtil.GlException::class)
        private fun addShader(programId: Int, type: Int, glsl: String?) {
            val shader: Int = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, glsl)
            GLES20.glCompileShader(shader)
            val result: IntArray = intArrayOf(GLES20.GL_FALSE)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result,  /* offset= */0)
            GlUtil.checkGlException(result.get(0) == GLES20.GL_TRUE, GLES20.glGetShaderInfoLog(shader) + ", source: " + glsl)
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