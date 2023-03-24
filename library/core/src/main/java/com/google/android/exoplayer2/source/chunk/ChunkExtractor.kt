/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.chunk

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.extractor.ChunkIndex
import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.extractor.TrackOutput
import com.google.android.exoplayer2.source.chunk.ChunkExtractor.TrackOutputProvider
import java.io.IOException

/**
 * Extracts samples and track [Formats][Format] from chunks.
 *
 *
 * The [TrackOutputProvider] passed to [.init] provides the [ TrackOutputs][TrackOutput] that receive the extracted data.
 */
interface ChunkExtractor {
    /** Creates [ChunkExtractor] instances.  */
    interface Factory {
        /**
         * Returns a new [ChunkExtractor] instance.
         *
         * @param primaryTrackType The [type][C.TrackType] of the primary track.
         * @param representationFormat The format of the representation to extract from.
         * @param enableEventMessageTrack Whether to enable the event message track.
         * @param closedCaptionFormats The [Formats][Format] of the Closed-Caption tracks.
         * @param playerEmsgTrackOutput The [TrackOutput] for extracted EMSG messages, or null.
         * @param playerId The [PlayerId] of the player using this chunk extractor.
         * @return A new [ChunkExtractor] instance, or null if not applicable.
         */
        fun createProgressiveMediaExtractor(
            @TrackType primaryTrackType: Int,
            representationFormat: Format?,
            enableEventMessageTrack: Boolean,
            closedCaptionFormats: List<Format?>?,
            playerEmsgTrackOutput: TrackOutput?,
            playerId: PlayerId?
        ): ChunkExtractor?
    }

    /** Provides [TrackOutput] instances to be written to during extraction.  */
    interface TrackOutputProvider {
        /**
         * Called to get the [TrackOutput] for a specific track.
         *
         *
         * The same [TrackOutput] is returned if multiple calls are made with the same `id`.
         *
         * @param id A track identifier.
         * @param type The [type][C.TrackType] of the track.
         * @return The [TrackOutput] for the given track identifier.
         */
        fun track(id: Int, @TrackType type: Int): TrackOutput?
    }

    /**
     * Returns the [ChunkIndex] most recently obtained from the chunks, or null if a [ ] has not been obtained.
     */
    fun getChunkIndex(): ChunkIndex?

    /**
     * Returns the sample [Format]s for the tracks identified by the extractor, or null if the
     * extractor has not finished identifying tracks.
     */
    fun getSampleFormats(): Array<Format?>?

    /**
     * Initializes the wrapper to output to [TrackOutput]s provided by the specified [ ], and configures the extractor to receive data from a new chunk.
     *
     * @param trackOutputProvider The provider of [TrackOutput]s that will receive sample data.
     * @param startTimeUs The start position in the new chunk, or [C.TIME_UNSET] to output
     * samples from the start of the chunk.
     * @param endTimeUs The end position in the new chunk, or [C.TIME_UNSET] to output samples
     * to the end of the chunk.
     */
    fun init(trackOutputProvider: TrackOutputProvider?, startTimeUs: Long, endTimeUs: Long)

    /** Releases any held resources.  */
    fun release()

    /**
     * Reads from the given [ExtractorInput].
     *
     * @param input The input to read from.
     * @return Whether there is any data left to extract. Returns false if the end of input has been
     * reached.
     * @throws IOException If an error occurred reading from or parsing the input.
     */
    @Throws(IOException::class)
    fun read(input: ExtractorInput?): Boolean
}