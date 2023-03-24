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
package com.google.android.exoplayer2.text

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.text.cea.Cea608Decoder
import com.google.android.exoplayer2.text.cea.Cea708Decoder
import com.google.android.exoplayer2.text.dvb.DvbDecoder
import com.google.android.exoplayer2.text.pgs.PgsDecoder
import com.google.android.exoplayer2.text.ssa.SsaDecoder
import com.google.android.exoplayer2.text.subrip.SubripDecoder
import com.google.android.exoplayer2.text.ttml.TtmlDecoder
import com.google.android.exoplayer2.text.tx3g.Tx3gDecoder
import com.google.android.exoplayer2.text.webvtt.Mp4WebvttDecoder
import com.google.android.exoplayer2.text.webvtt.WebvttDecoder
import com.google.android.exoplayer2.util.MimeTypes

/** A factory for [SubtitleDecoder] instances.  */
interface SubtitleDecoderFactory {
    /**
     * Returns whether the factory is able to instantiate a [SubtitleDecoder] for the given
     * [Format].
     *
     * @param format The [Format].
     * @return Whether the factory can instantiate a suitable [SubtitleDecoder].
     */
    fun supportsFormat(format: Format): Boolean

    /**
     * Creates a [SubtitleDecoder] for the given [Format].
     *
     * @param format The [Format].
     * @return A new [SubtitleDecoder].
     * @throws IllegalArgumentException If the [Format] is not supported.
     */
    fun createDecoder(format: Format): SubtitleDecoder

    companion object {
        /**
         * Default [SubtitleDecoderFactory] implementation.
         *
         *
         * The formats supported by this factory are:
         *
         *
         *  * WebVTT ([WebvttDecoder])
         *  * WebVTT (MP4) ([Mp4WebvttDecoder])
         *  * TTML ([TtmlDecoder])
         *  * SubRip ([SubripDecoder])
         *  * SSA/ASS ([SsaDecoder])
         *  * TX3G ([Tx3gDecoder])
         *  * Cea608 ([Cea608Decoder])
         *  * Cea708 ([Cea708Decoder])
         *  * DVB ([DvbDecoder])
         *  * PGS ([PgsDecoder])
         *  * Exoplayer Cues ([ExoplayerCuesDecoder])
         *
         */
        @JvmField
        val DEFAULT: SubtitleDecoderFactory = object : SubtitleDecoderFactory {
            override fun supportsFormat(format: Format): Boolean {
                val mimeType = format.sampleMimeType
                return MimeTypes.TEXT_VTT == mimeType || MimeTypes.TEXT_SSA == mimeType || MimeTypes.APPLICATION_TTML == mimeType || MimeTypes.APPLICATION_MP4VTT == mimeType || MimeTypes.APPLICATION_SUBRIP == mimeType || MimeTypes.APPLICATION_TX3G == mimeType || MimeTypes.APPLICATION_CEA608 == mimeType || MimeTypes.APPLICATION_MP4CEA608 == mimeType || MimeTypes.APPLICATION_CEA708 == mimeType || MimeTypes.APPLICATION_DVBSUBS == mimeType || MimeTypes.APPLICATION_PGS == mimeType || MimeTypes.TEXT_EXOPLAYER_CUES == mimeType
            }

            override fun createDecoder(format: Format): SubtitleDecoder {
                val mimeType = format.sampleMimeType
                if (mimeType != null) {
                    when (mimeType) {
                        MimeTypes.TEXT_VTT -> return WebvttDecoder()
                        MimeTypes.TEXT_SSA -> return SsaDecoder(format.initializationData)
                        MimeTypes.APPLICATION_MP4VTT -> return Mp4WebvttDecoder()
                        MimeTypes.APPLICATION_TTML -> return TtmlDecoder()
                        MimeTypes.APPLICATION_SUBRIP -> return SubripDecoder()
                        MimeTypes.APPLICATION_TX3G -> return Tx3gDecoder(format.initializationData)
                        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> return Cea608Decoder(
                            mimeType,
                            format.accessibilityChannel,
                            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS
                        )
                        MimeTypes.APPLICATION_CEA708 -> return Cea708Decoder(
                            format.accessibilityChannel, format.initializationData
                        )
                        MimeTypes.APPLICATION_DVBSUBS -> return DvbDecoder(format.initializationData)
                        MimeTypes.APPLICATION_PGS -> return PgsDecoder()
                        MimeTypes.TEXT_EXOPLAYER_CUES -> return ExoplayerCuesDecoder()
                        else -> {}
                    }
                }
                throw IllegalArgumentException(
                    "Attempted to create decoder for unsupported MIME type: $mimeType"
                )
            }
        }
    }
}