/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.metadata.dvbsi.AppInfoTableDecoder
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder
import com.google.android.exoplayer2.metadata.icy.IcyDecoder
import com.google.android.exoplayer2.metadata.id3.Id3Decoder
import com.google.android.exoplayer2.metadata.scte35.SpliceInfoDecoder
import com.google.android.exoplayer2.util.MimeTypes

/** A factory for [MetadataDecoder] instances.  */
interface MetadataDecoderFactory {
    /**
     * Returns whether the factory is able to instantiate a [MetadataDecoder] for the given
     * [Format].
     *
     * @param format The [Format].
     * @return Whether the factory can instantiate a suitable [MetadataDecoder].
     */
    fun supportsFormat(format: Format): Boolean

    /**
     * Creates a [MetadataDecoder] for the given [Format].
     *
     * @param format The [Format].
     * @return A new [MetadataDecoder].
     * @throws IllegalArgumentException If the [Format] is not supported.
     */
    fun createDecoder(format: Format): MetadataDecoder

    companion object {
        /**
         * Default [MetadataDecoder] implementation.
         *
         *
         * The formats supported by this factory are:
         *
         *
         *  * ID3 ([Id3Decoder])
         *  * EMSG ([EventMessageDecoder])
         *  * SCTE-35 ([SpliceInfoDecoder])
         *  * ICY ([IcyDecoder])
         *
         */
        @JvmField
        val DEFAULT: MetadataDecoderFactory = object : MetadataDecoderFactory {
            override fun supportsFormat(format: Format): Boolean {
                val mimeType = format.sampleMimeType
                return MimeTypes.APPLICATION_ID3 == mimeType || MimeTypes.APPLICATION_EMSG == mimeType || MimeTypes.APPLICATION_SCTE35 == mimeType || MimeTypes.APPLICATION_ICY == mimeType || MimeTypes.APPLICATION_AIT == mimeType
            }

            override fun createDecoder(format: Format): MetadataDecoder {
                val mimeType = format.sampleMimeType
                if (mimeType != null) {
                    when (mimeType) {
                        MimeTypes.APPLICATION_ID3 -> return Id3Decoder()
                        MimeTypes.APPLICATION_EMSG -> return EventMessageDecoder()
                        MimeTypes.APPLICATION_SCTE35 -> return SpliceInfoDecoder()
                        MimeTypes.APPLICATION_ICY -> return IcyDecoder()
                        MimeTypes.APPLICATION_AIT -> return AppInfoTableDecoder()
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