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
package com.google.android.exoplayer2.ext.opus

import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DecoderAudioRenderer
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.ext.opus.OpusDecoderException
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.Util

/** Decodes and renders audio using the native Opus decoder.  */
class LibopusAudioRenderer : DecoderAudioRenderer<OpusDecoder?> {
    constructor() : this( /* eventHandler= */null,  /* eventListener= */null) {}

    /**
     * Creates a new instance.
     *
     * @param eventHandler A handler to use when delivering events to `eventListener`. May be
     * null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     * @param audioProcessors Optional [AudioProcessor]s that will process audio before output.
     */
    constructor(
            eventHandler: Handler?,
            eventListener: AudioRendererEventListener?,
            vararg audioProcessors: AudioProcessor?) : super(eventHandler, eventListener, *audioProcessors) {
    }

    /**
     * Creates a new instance.
     *
     * @param eventHandler A handler to use when delivering events to `eventListener`. May be
     * null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     * @param audioSink The sink to which audio will be output.
     */
    constructor(
            eventHandler: Handler?,
            eventListener: AudioRendererEventListener?,
            audioSink: AudioSink?) : super(eventHandler, eventListener, audioSink!!) {
    }

    override fun getName(): String {
        return TAG
    }

    override fun supportsFormatInternal(format: Format): @C.FormatSupport Int {
        val drmIsSupported = OpusLibrary.supportsCryptoType(format.cryptoType)
        return if (!OpusLibrary.isAvailable()
                || !MimeTypes.AUDIO_OPUS.equals(format.sampleMimeType, ignoreCase = true)) {
            C.FORMAT_UNSUPPORTED_TYPE
        } else if (!sinkSupportsFormat(
                        Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate))) {
            C.FORMAT_UNSUPPORTED_SUBTYPE
        } else if (!drmIsSupported) {
            C.FORMAT_UNSUPPORTED_DRM
        } else {
            C.FORMAT_HANDLED
        }
    }

    /** {@inheritDoc}  */
    @Throws(OpusDecoderException::class)
    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): OpusDecoder {
        TraceUtil.beginSection("createOpusDecoder")
        val formatSupport = getSinkFormatSupport(
                Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate))
        val outputFloat = formatSupport == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        val initialInputBufferSize = if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        val decoder = OpusDecoder(
                NUM_BUFFERS,
                NUM_BUFFERS,
                initialInputBufferSize,
                format.initializationData,
                cryptoConfig,
                outputFloat)
        decoder.experimentalSetDiscardPaddingEnabled(experimentalGetDiscardPaddingEnabled())
        TraceUtil.endSection()
        return decoder
    }

    /** {@inheritDoc}  */
    override fun getOutputFormat(decoder: OpusDecoder): Format {
        val pcmEncoding = if (decoder.outputFloat) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT
        return Util.getPcmFormat(pcmEncoding, decoder.channelCount, OpusDecoder.Companion.SAMPLE_RATE)
    }

    /**
     * Returns true if support for padding removal from the end of decoder output buffer should be
     * enabled.
     *
     *
     * This method is experimental, and will be renamed or removed in a future release.
     */
    protected fun experimentalGetDiscardPaddingEnabled(): Boolean {
        return false
    }

    companion object {
        private const val TAG = "LibopusAudioRenderer"

        /** The number of input and output buffers.  */
        private const val NUM_BUFFERS = 16

        /** The default input buffer size.  */
        private const val DEFAULT_INPUT_BUFFER_SIZE = 960 * 6
    }
}