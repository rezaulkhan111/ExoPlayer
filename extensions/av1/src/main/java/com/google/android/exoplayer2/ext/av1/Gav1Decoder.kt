/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.av1

import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.VideoOutputMode
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.SimpleDecoder
import com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer
import com.google.android.exoplayer2.ext.av1.Gav1DecoderException
import com.google.android.exoplayer2.ext.av1.Libgav1VideoRenderer
import com.google.android.exoplayer2.util.Util
import java.nio.ByteBuffer

/**
 * Gav1 decoder.
 */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
class Gav1Decoder(
        numInputBuffers: Int, numOutputBuffers: Int, initialInputBufferSize: Int, threads: Int) :
        SimpleDecoder<DecoderInputBuffer?, VideoDecoderOutputBuffer?, Gav1DecoderException?>(arrayOfNulls(numInputBuffers), arrayOfNulls(numOutputBuffers)) {
    private val gav1DecoderContext: Long

    @Volatile
    private var outputMode = 0

    /**
     * Creates a Gav1Decoder.
     *
     * @param numInputBuffers        Number of input buffers.
     * @param numOutputBuffers       Number of output buffers.
     * @param initialInputBufferSize The initial size of each input buffer, in bytes.
     * @param threads                Number of threads libgav1 will use to decode. If [                               ][Libgav1VideoRenderer.THREAD_COUNT_AUTODETECT] is passed, then this class will auto detect
     * the number of threads to be used.
     * @throws Gav1DecoderException Thrown if an exception occurs when initializing the decoder.
     */
    init {
        var threads = threads
        if (!Gav1Library.isAvailable()) {
            throw Gav1DecoderException("Failed to load decoder native library.")
        }
        if (threads == Libgav1VideoRenderer.Companion.THREAD_COUNT_AUTODETECT) {
            // Try to get the optimal number of threads from the AV1 heuristic.
            threads = gav1GetThreads()
            if (threads <= 0) {
                // If that is not available, default to the number of available processors.
                threads = Runtime.getRuntime().availableProcessors()
            }
        }
        gav1DecoderContext = gav1Init(threads)
        if (gav1DecoderContext == GAV1_ERROR.toLong() || gav1CheckError(gav1DecoderContext) == GAV1_ERROR) {
            throw Gav1DecoderException(
                    "Failed to initialize decoder. Error: " + gav1GetErrorMessage(gav1DecoderContext))
        }
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String {
        return "libgav1"
    }

    override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    }

    override fun createOutputBuffer(): VideoDecoderOutputBuffer {
        return VideoDecoderOutputBuffer { outputBuffer: VideoDecoderOutputBuffer -> releaseOutputBuffer(outputBuffer) }
    }

    override fun decode(
            inputBuffer: DecoderInputBuffer, outputBuffer: VideoDecoderOutputBuffer, reset: Boolean): Gav1DecoderException? {
        val inputData = Util.castNonNull(inputBuffer.data)
        val inputSize = inputData.limit()
        if (gav1Decode(gav1DecoderContext, inputData, inputSize) == GAV1_ERROR) {
            return Gav1DecoderException(
                    "gav1Decode error: " + gav1GetErrorMessage(gav1DecoderContext))
        }
        val decodeOnly = inputBuffer.isDecodeOnly
        if (!decodeOnly) {
            outputBuffer.init(inputBuffer.timeUs, outputMode,  /* supplementalData= */null)
        }
        // We need to dequeue the decoded frame from the decoder even when the input data is
        // decode-only.
        val getFrameResult = gav1GetFrame(gav1DecoderContext, outputBuffer, decodeOnly)
        if (getFrameResult == GAV1_ERROR) {
            return Gav1DecoderException(
                    "gav1GetFrame error: " + gav1GetErrorMessage(gav1DecoderContext))
        }
        if (getFrameResult == GAV1_DECODE_ONLY) {
            outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY)
        }
        if (!decodeOnly) {
            outputBuffer.format = inputBuffer.format
        }
        return null
    }

    override fun createUnexpectedDecodeException(error: Throwable): Gav1DecoderException {
        return Gav1DecoderException("Unexpected decode error", error)
    }

    override fun release() {
        super.release()
        gav1Close(gav1DecoderContext)
    }

    override fun releaseOutputBuffer(outputBuffer: VideoDecoderOutputBuffer) {
        // Decode only frames do not acquire a reference on the internal decoder buffer and thus do not
        // require a call to gav1ReleaseFrame.
        if (outputBuffer.mode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !outputBuffer.isDecodeOnly) {
            gav1ReleaseFrame(gav1DecoderContext, outputBuffer)
        }
        super.releaseOutputBuffer(outputBuffer)
    }

    /**
     * Sets the output mode for frames rendered by the decoder.
     *
     * @param outputMode The output mode.
     */
    fun setOutputMode(outputMode: @VideoOutputMode Int) {
        this.outputMode = outputMode
    }

    /**
     * Renders output buffer to the given surface. Must only be called when in [ ][C.VIDEO_OUTPUT_MODE_SURFACE_YUV] mode.
     *
     * @param outputBuffer Output buffer.
     * @param surface      Output surface.
     * @throws Gav1DecoderException Thrown if called with invalid output mode or frame rendering
     * fails.
     */
    @Throws(Gav1DecoderException::class)
    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
            throw Gav1DecoderException("Invalid output mode.")
        }
        if (gav1RenderFrame(gav1DecoderContext, surface, outputBuffer) == GAV1_ERROR) {
            throw Gav1DecoderException(
                    "Buffer render error: " + gav1GetErrorMessage(gav1DecoderContext))
        }
    }

    /**
     * Initializes a libgav1 decoder.
     *
     * @param threads Number of threads to be used by a libgav1 decoder.
     * @return The address of the decoder context or [.GAV1_ERROR] if there was an error.
     */
    private external fun gav1Init(threads: Int): Long

    /**
     * Deallocates the decoder context.
     *
     * @param context Decoder context.
     */
    private external fun gav1Close(context: Long)

    /**
     * Decodes the encoded data passed.
     *
     * @param context     Decoder context.
     * @param encodedData Encoded data.
     * @param length      Length of the data buffer.
     * @return [.GAV1_OK] if successful, [.GAV1_ERROR] if an error occurred.
     */
    private external fun gav1Decode(context: Long, encodedData: ByteBuffer, length: Int): Int

    /**
     * Gets the decoded frame.
     *
     * @param context      Decoder context.
     * @param outputBuffer Output buffer for the decoded frame.
     * @return [.GAV1_OK] if successful, [.GAV1_DECODE_ONLY] if successful but the frame
     * is decode-only, [.GAV1_ERROR] if an error occurred.
     */
    private external fun gav1GetFrame(
            context: Long, outputBuffer: VideoDecoderOutputBuffer, decodeOnly: Boolean): Int

    /**
     * Renders the frame to the surface. Used with [C.VIDEO_OUTPUT_MODE_SURFACE_YUV] only.
     *
     * @param context      Decoder context.
     * @param surface      Output surface.
     * @param outputBuffer Output buffer with the decoded frame.
     * @return [.GAV1_OK] if successful, [.GAV1_ERROR] if an error occurred.
     */
    private external fun gav1RenderFrame(
            context: Long, surface: Surface, outputBuffer: VideoDecoderOutputBuffer): Int

    /**
     * Releases the frame. Used with [C.VIDEO_OUTPUT_MODE_SURFACE_YUV] only.
     *
     * @param context      Decoder context.
     * @param outputBuffer Output buffer.
     */
    private external fun gav1ReleaseFrame(context: Long, outputBuffer: VideoDecoderOutputBuffer)

    /**
     * Returns a human-readable string describing the last error encountered in the given context.
     *
     * @param context Decoder context.
     * @return A string describing the last encountered error.
     */
    private external fun gav1GetErrorMessage(context: Long): String

    /**
     * Returns whether an error occurred.
     *
     * @param context Decoder context.
     * @return [.GAV1_OK] if there was no error, [.GAV1_ERROR] if an error occurred.
     */
    private external fun gav1CheckError(context: Long): Int

    /**
     * Returns the optimal number of threads to be used for AV1 decoding.
     *
     * @return Optimal number of threads if there was no error, 0 if an error occurred.
     */
    private external fun gav1GetThreads(): Int

    companion object {
        private const val GAV1_ERROR = 0
        private const val GAV1_OK = 1
        private const val GAV1_DECODE_ONLY = 2
    }
}