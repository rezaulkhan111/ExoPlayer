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

import android.os.Handler
import android.view.Surface
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.VideoOutputMode
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer
import com.google.android.exoplayer2.ext.vp9.VpxDecoderException
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.video.DecoderVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener

/** Decodes and renders video using the native VP9 decoder.  */
class LibvpxVideoRenderer
/**
 * Creates a new instance.
 *
 * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
 * can attempt to seamlessly join an ongoing playback.
 */ @JvmOverloads constructor(
        allowedJoiningTimeMs: Long,
        eventHandler: Handler? = null,
        eventListener: VideoRendererEventListener? = null,
        maxDroppedFramesToNotify: Int = 0,
        private val threads: Int =
                Runtime.getRuntime().availableProcessors(),
        /** The number of input buffers.  */
        private val numInputBuffers: Int =  /* numInputBuffers= */
                4,
        /**
         * The number of output buffers. The renderer may limit the minimum possible value due to
         * requiring multiple output buffers to be dequeued at a time for it to make progress.
         */
        private val numOutputBuffers: Int =  /* numOutputBuffers= */
                4) : DecoderVideoRenderer(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify) {
    private var decoder: VpxDecoder? = null
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
     * @param threads Number of threads libvpx will use to decode.
     * @param numInputBuffers Number of input buffers.
     * @param numOutputBuffers Number of output buffers.
     */
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
     */
    override fun getName(): String {
        return TAG
    }

    override fun supportsFormat(format: Format): @RendererCapabilities.Capabilities Int {
        if (!VpxLibrary.isAvailable() || !MimeTypes.VIDEO_VP9.equals(format.sampleMimeType, ignoreCase = true)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        val drmIsSupported = VpxLibrary.supportsCryptoType(format.cryptoType)
        return if (!drmIsSupported) {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        } else RendererCapabilities.create(
                C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED)
    }

    @Throws(VpxDecoderException::class)
    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): VpxDecoder {
        TraceUtil.beginSection("createVpxDecoder")
        val initialInputBufferSize = if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        val decoder = VpxDecoder(
                numInputBuffers, numOutputBuffers, initialInputBufferSize, cryptoConfig, threads)
        this.decoder = decoder
        TraceUtil.endSection()
        return decoder
    }

    @Throws(VpxDecoderException::class)
    override fun renderOutputBufferToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (decoder == null) {
            throw VpxDecoderException(
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
        private const val TAG = "LibvpxVideoRenderer"

        /**
         * The default input buffer size. The value is based on [SoftVPX.cpp](https://android.googlesource.com/platform/frameworks/av/+/d42b90c5183fbd9d6a28d9baee613fddbf8131d6/media/libstagefright/codecs/on2/dec/SoftVPX.cpp).
         */
        private const val DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024
    }
}