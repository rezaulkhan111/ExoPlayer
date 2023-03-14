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
package com.google.android.exoplayer2.ext.flac

import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DecoderAudioRenderer
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.ext.flac.FlacDecoderException
import com.google.android.exoplayer2.extractor.FlacStreamMetadata
import com.google.android.exoplayer2.util.*

/** Decodes and renders audio using the native Flac decoder.  */
class LibflacAudioRenderer : DecoderAudioRenderer<FlacDecoder?> {
    constructor() : this( /* eventHandler= */null,  /* eventListener= */null) {}

    /**
     * Creates an instance.
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
     * Creates an instance.
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
        if (!FlacLibrary.isAvailable()
                || !MimeTypes.AUDIO_FLAC.equals(format.sampleMimeType, ignoreCase = true)) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        // Compute the format that the FLAC decoder will output.
        val outputFormat: Format
        outputFormat = if (format.initializationData.isEmpty()) {
            // The initialization data might not be set if the format was obtained from a manifest (e.g.
            // for DASH playbacks) rather than directly from the media. In this case we assume
            // ENCODING_PCM_16BIT. If the actual encoding is different then playback will still succeed as
            // long as the AudioSink supports it, which will always be true when using DefaultAudioSink.
            Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate)
        } else {
            val streamMetadataOffset = STREAM_MARKER_SIZE + METADATA_BLOCK_HEADER_SIZE
            val streamMetadata = FlacStreamMetadata(format.initializationData[0], streamMetadataOffset)
            getOutputFormat(streamMetadata)
        }
        return if (!sinkSupportsFormat(outputFormat)) {
            C.FORMAT_UNSUPPORTED_SUBTYPE
        } else if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            C.FORMAT_UNSUPPORTED_DRM
        } else {
            C.FORMAT_HANDLED
        }
    }

    /** {@inheritDoc}  */
    @Throws(FlacDecoderException::class)
    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FlacDecoder {
        TraceUtil.beginSection("createFlacDecoder")
        val decoder = FlacDecoder(NUM_BUFFERS, NUM_BUFFERS, format.maxInputSize, format.initializationData)
        TraceUtil.endSection()
        return decoder
    }

    /** {@inheritDoc}  */
    override fun getOutputFormat(decoder: FlacDecoder): Format {
        return getOutputFormat(decoder.streamMetadata)
    }

    companion object {
        private const val TAG = "LibflacAudioRenderer"
        private const val NUM_BUFFERS = 16
        private const val STREAM_MARKER_SIZE = 4
        private const val METADATA_BLOCK_HEADER_SIZE = 4
        private fun getOutputFormat(streamMetadata: FlacStreamMetadata?): Format {
            return Util.getPcmFormat(
                    Util.getPcmEncoding(streamMetadata!!.bitsPerSample),
                    streamMetadata.channels,
                    streamMetadata.sampleRate)
        }
    }
}