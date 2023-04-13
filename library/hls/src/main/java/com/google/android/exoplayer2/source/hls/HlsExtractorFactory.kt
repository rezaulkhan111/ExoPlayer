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
package com.google.android.exoplayer2.source.hls

import android.net.Uri
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.util.TimestampAdjuster
import java.io.IOException

/** Factory for HLS media chunk extractors.  */
interface HlsExtractorFactory {
    /**
     * Creates an [Extractor] for extracting HLS media chunks.
     *
     * @param uri The URI of the media chunk.
     * @param format A [Format] associated with the chunk to extract.
     * @param muxedCaptionFormats List of muxed caption [Format]s. Null if no closed caption
     * information is available in the multivariant playlist.
     * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
     * @param responseHeaders The HTTP response headers associated with the media segment or
     * initialization section to extract.
     * @param sniffingExtractorInput The first extractor input that will be passed to the returned
     * extractor's [Extractor.read]. Must only be used to
     * call [Extractor.sniff].
     * @param playerId The [PlayerId] of the player using this extractors factory.
     * @return An [HlsMediaChunkExtractor].
     * @throws IOException If an I/O error is encountered while sniffing.
     */
    @Throws(IOException::class)
    fun createExtractor(
        uri: Uri?,
        format: Format?,
        muxedCaptionFormats: List<Format?>?,
        timestampAdjuster: TimestampAdjuster?,
        responseHeaders: Map<String?, List<String?>?>?,
        sniffingExtractorInput: ExtractorInput?,
        playerId: PlayerId?
    ): HlsMediaChunkExtractor?

    companion object {
        @JvmField
        val DEFAULT: HlsExtractorFactory = DefaultHlsExtractorFactory()
    }
}