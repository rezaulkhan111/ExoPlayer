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

import android.os.Handler
import android.view.Surface
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.VideoOutputMode
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer
import com.google.android.exoplayer2.ext.av1.Gav1DecoderException
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.DecoderVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener

/** Decodes and renders video using libgav1 decoder.  */
class Libgav1VideoRenderer
/**
 * Creates a new instance.
 *
 * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
 * can attempt to seamlessly join an ongoing playback.
 * @param eventHandler A handler to use when delivering events to `eventListener`. May be
 * null if delivery of events is not required.
 * @param eventListener A listener of events. May be null if delivery of events is not required.
 * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
 * invocations of [VideoRendererEventListener.onDroppedFrames].
 */ @JvmOverloads constructor(
        allowedJoiningTimeMs: Long,
        eventHandler: Handler?,
        eventListener: VideoRendererEventListener?,
        maxDroppedFramesToNotify: Int,
        private val threads: Int =
                THREAD_COUNT_AUTODETECT,
        /** The number of input buffers.  */
        private val numInputBuffers: Int =
                DEFAULT_NUM_OF_INPUT_BUFFERS,
        /**
         * The number of output buffers. The renderer may limit the minimum possible value due to
         * requiring multiple output buffers to be dequeued at a time for it to make progress.
         */
        private val numOutputBuffers: Int =
                DEFAULT_NUM_OF_OUTPUT_BUFFERS) : DecoderVideoRenderer(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify) {
    private var decoder: Gav1Decoder? = null

    /**
     * Creates a new instance.
     *
     * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
     * can attempt to seamlessly join an ongoing playback.
     * @param eventHandler A handler to use when delivering events to `eventListener`. May be
     * null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
     * invocations of [VideoRendererEventListener.onDroppedFrames].
     * @param threads Number of threads libgav1 will use to decode. If [     ][.THREAD_COUNT_AUTODETECT] is passed, then the number of threads to use is autodetected
     * based on CPU capabilities.
     * @param numInputBuffers Number of input buffers.
     * @param numOutputBuffers Number of output buffers.
     */
    override fun getName(): String {
        return TAG
    }

    override fun supportsFormat(format: Format): @RendererCapabilities.Capabilities Int {
        if (!MimeTypes.VIDEO_AV1.equals(format.sampleMimeType, ignoreCase = true)
                || !Gav1Library.isAvailable()) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        return if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        } else RendererCapabilities.create(
                C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED)
    }

    /** {@inheritDoc}  */
    @Throws(Gav1DecoderException::class)
    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): Gav1Decoder {
        TraceUtil.beginSection("createGav1Decoder")
        val initialInputBufferSize = if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        val decoder = Gav1Decoder(numInputBuffers, numOutputBuffers, initialInputBufferSize, threads)
        this.decoder = decoder
        TraceUtil.endSection()
        return decoder
    }

    @Throws(Gav1DecoderException::class)
    override fun renderOutputBufferToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (decoder == null) {
            throw Gav1DecoderException(
                    "Failed to render output buffer to surface: decoder is not initialized.")
        }
        decoder!!.renderToSurface(outputBuffer, surface)
        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: @VideoOutputMode Int) {
        if (decoder != null) {
            decoder!!.setOutputMode(outputMode)
        }
    }

    override fun canReuseDecoder(
            decoderName: String, oldFormat: Format, newFormat: Format): DecoderReuseEvaluation {
        return DecoderReuseEvaluation(
                decoderName,
                oldFormat,
                newFormat,
                DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,  /* discardReasons= */
                0)
    }

    companion object {
        /**
         * Attempts to use as many threads as performance processors available on the device. If the
         * number of performance processors cannot be detected, the number of available processors is
         * used.
         */
        const val THREAD_COUNT_AUTODETECT = 0
        private const val TAG = "Libgav1VideoRenderer"
        private const val DEFAULT_NUM_OF_INPUT_BUFFERS = 4
        private const val DEFAULT_NUM_OF_OUTPUT_BUFFERS = 4

        /**
         * Default input buffer size in bytes, based on 720p resolution video compressed by a factor of
         * two.
         */
        private val DEFAULT_INPUT_BUFFER_SIZE = Util.ceilDivide(1280, 64) * Util.ceilDivide(720, 64) * (64 * 64 * 3 / 2) / 2
    }
}