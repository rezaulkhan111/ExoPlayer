/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.vp9

import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.VideoOutputMode
import com.google.android.exoplayer2.decoder.*
import com.google.android.exoplayer2.ext.vp9.VpxDecoderException
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.nio.ByteBuffer

/** Vpx decoder.  */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
class VpxDecoder(
        numInputBuffers: Int,
        numOutputBuffers: Int,
        initialInputBufferSize: Int,
        cryptoConfig: CryptoConfig?,
        threads: Int) : SimpleDecoder<DecoderInputBuffer?, VideoDecoderOutputBuffer?, VpxDecoderException?>(arrayOfNulls(numInputBuffers), arrayOfNulls(numOutputBuffers)) {
    private val cryptoConfig: CryptoConfig?
    private val vpxDecContext: Long
    private var lastSupplementalData: ByteBuffer? = null

    @Volatile
    private var outputMode = 0

    /**
     * Creates a VP9 decoder.
     *
     * @param numInputBuffers The number of input buffers.
     * @param numOutputBuffers The number of output buffers.
     * @param initialInputBufferSize The initial size of each input buffer.
     * @param cryptoConfig The [CryptoConfig] object required for decoding encrypted content.
     * May be null and can be ignored if decoder does not handle encrypted content.
     * @param threads Number of threads libvpx will use to decode.
     * @throws VpxDecoderException Thrown if an exception occurs when initializing the decoder.
     */
    init {
        if (!VpxLibrary.isAvailable()) {
            throw VpxDecoderException("Failed to load decoder native libraries.")
        }
        this.cryptoConfig = cryptoConfig
        if (cryptoConfig != null && !VpxLibrary.vpxIsSecureDecodeSupported()) {
            throw VpxDecoderException("Vpx decoder does not support secure decode.")
        }
        vpxDecContext = vpxInit( /* disableLoopFilter= */false,  /* enableRowMultiThreadMode= */false, threads)
        if (vpxDecContext == 0L) {
            throw VpxDecoderException("Failed to initialize decoder")
        }
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String {
        return "libvpx" + VpxLibrary.getVersion()
    }

    override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    }

    override fun createOutputBuffer(): VideoDecoderOutputBuffer {
        return VideoDecoderOutputBuffer { outputBuffer: VideoDecoderOutputBuffer -> releaseOutputBuffer(outputBuffer) }
    }

    override fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        // Decode only frames do not acquire a reference on the internal decoder buffer and thus do not
        // require a call to vpxReleaseFrame.
        if (outputMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !outputBuffer.isDecodeOnly) {
            vpxReleaseFrame(vpxDecContext, outputBuffer)
        }
        super.releaseOutputBuffer(outputBuffer)
    }

    override fun createUnexpectedDecodeException(error: Throwable): VpxDecoderException {
        return VpxDecoderException("Unexpected decode error", error)
    }

    override fun decode(
            inputBuffer: DecoderInputBuffer, outputBuffer: VideoDecoderOutputBuffer, reset: Boolean): VpxDecoderException? {
        if (reset && lastSupplementalData != null) {
            // Don't propagate supplemental data across calls to flush the decoder.
            lastSupplementalData!!.clear()
        }
        val inputData = Util.castNonNull(inputBuffer.data)
        val inputSize = inputData.limit()
        val cryptoInfo = inputBuffer.cryptoInfo
        val result = if (inputBuffer.isEncrypted) vpxSecureDecode(
                vpxDecContext,
                inputData,
                inputSize,
                cryptoConfig,
                cryptoInfo.mode,
                Assertions.checkNotNull(cryptoInfo.key),
                Assertions.checkNotNull(cryptoInfo.iv),
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData) else vpxDecode(vpxDecContext, inputData, inputSize)
        if (result != NO_ERROR.toLong()) {
            return if (result == DRM_ERROR.toLong()) {
                val message = "Drm error: " + vpxGetErrorMessage(vpxDecContext)
                val cause = CryptoException(vpxGetErrorCode(vpxDecContext), message)
                VpxDecoderException(message, cause)
            } else {
                VpxDecoderException("Decode error: " + vpxGetErrorMessage(vpxDecContext))
            }
        }
        if (inputBuffer.hasSupplementalData()) {
            val supplementalData = Assertions.checkNotNull(inputBuffer.supplementalData)
            val size = supplementalData.remaining()
            if (size > 0) {
                if (lastSupplementalData == null || lastSupplementalData!!.capacity() < size) {
                    lastSupplementalData = ByteBuffer.allocate(size)
                } else {
                    lastSupplementalData!!.clear()
                }
                lastSupplementalData!!.put(supplementalData)
                lastSupplementalData!!.flip()
            }
        }
        if (!inputBuffer.isDecodeOnly) {
            outputBuffer.init(inputBuffer.timeUs, outputMode, lastSupplementalData)
            val getFrameResult = vpxGetFrame(vpxDecContext, outputBuffer)
            if (getFrameResult == 1) {
                outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY)
            } else if (getFrameResult == -1) {
                return VpxDecoderException("Buffer initialization failed.")
            }
            outputBuffer.format = inputBuffer.format
        }
        return null
    }

    override fun release() {
        super.release()
        lastSupplementalData = null
        vpxClose(vpxDecContext)
    }

    /**
     * Sets the output mode for frames rendered by the decoder.
     *
     * @param outputMode The output mode.
     */
    fun setOutputMode(outputMode: @VideoOutputMode Int) {
        this.outputMode = outputMode
    }

    /** Renders the outputBuffer to the surface. Used with OUTPUT_MODE_SURFACE_YUV only.  */
    @Throws(VpxDecoderException::class)
    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        val getFrameResult = vpxRenderFrame(vpxDecContext, surface, outputBuffer)
        if (getFrameResult == -1) {
            throw VpxDecoderException("Buffer render failed.")
        }
    }

    private external fun vpxInit(
            disableLoopFilter: Boolean, enableRowMultiThreadMode: Boolean, threads: Int): Long

    private external fun vpxClose(context: Long): Long
    private external fun vpxDecode(context: Long, encoded: ByteBuffer, length: Int): Long
    private external fun vpxSecureDecode(
            context: Long,
            encoded: ByteBuffer,
            length: Int,
            mediaCrypto: CryptoConfig?,
            inputMode: Int,
            key: ByteArray,
            iv: ByteArray,
            numSubSamples: Int,
            numBytesOfClearData: IntArray?,
            numBytesOfEncryptedData: IntArray?): Long

    private external fun vpxGetFrame(context: Long, outputBuffer: VideoDecoderOutputBuffer): Int

    /**
     * Renders the frame to the surface. Used with OUTPUT_MODE_SURFACE_YUV only. Must only be called
     * if [.vpxInit] was called with `enableBufferManager = true`.
     */
    private external fun vpxRenderFrame(
            context: Long, surface: Surface, outputBuffer: VideoDecoderOutputBuffer): Int

    /**
     * Releases the frame. Used with OUTPUT_MODE_SURFACE_YUV only. Must only be called if [ ][.vpxInit] was called with `enableBufferManager = true`.
     */
    private external fun vpxReleaseFrame(context: Long, outputBuffer: VideoDecoderOutputBuffer): Int
    private external fun vpxGetErrorCode(context: Long): Int
    private external fun vpxGetErrorMessage(context: Long): String

    companion object {
        // These constants should match the codes returned from vpxDecode and vpxSecureDecode functions in
        // https://github.com/google/ExoPlayer/blob/release-v2/extensions/vp9/src/main/jni/vpx_jni.cc.
        private const val NO_ERROR = 0
        private const val DECODE_ERROR = -1
        private const val DRM_ERROR = -2
    }
}