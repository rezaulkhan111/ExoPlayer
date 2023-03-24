/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source

import android.net.Uri
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.extractor.ExtractorOutput
import com.google.android.exoplayer2.extractor.PositionHolder
import com.google.android.exoplayer2.upstream.DataReader
import java.io.IOException

/** Extracts the contents of a container file from a progressive media stream.  */
interface ProgressiveMediaExtractor {
    /** Creates [ProgressiveMediaExtractor] instances.  */
    interface Factory {
        /**
         * Returns a new [ProgressiveMediaExtractor] instance.
         *
         * @param playerId The [PlayerId] of the player this extractor is used for.
         */
        fun createProgressiveMediaExtractor(playerId: PlayerId?): ProgressiveMediaExtractor?
    }

    /**
     * Initializes the underlying infrastructure for reading from the input.
     *
     * @param dataReader The [DataReader] from which data should be read.
     * @param uri The [Uri] from which the media is obtained.
     * @param responseHeaders The response headers of the media, or an empty map if there are none.
     * @param position The initial position of the `dataReader` in the stream.
     * @param length The length of the stream, or [C.LENGTH_UNSET] if length is unknown.
     * @param output The [ExtractorOutput] that will be used to initialize the selected
     * extractor.
     * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
     * @throws IOException Thrown if the input could not be read.
     */
    @Throws(IOException::class)
    fun init(
        dataReader: DataReader?,
        uri: Uri?,
        responseHeaders: Map<String?, List<String?>?>?,
        position: Long,
        length: Long,
        output: ExtractorOutput?
    )

    /** Releases any held resources.  */
    fun release()

    /**
     * Disables seeking in MP3 streams.
     *
     *
     * MP3 live streams commonly have seekable metadata, despite being unseekable.
     */
    fun disableSeekingOnMp3Streams()

    /**
     * Returns the current read position in the input stream, or [C.POSITION_UNSET] if no input
     * is available.
     */
    fun getCurrentInputPosition(): Long

    /**
     * Notifies the extracting infrastructure that a seek has occurred.
     *
     * @param position The byte offset in the stream from which data will be provided.
     * @param seekTimeUs The seek time in microseconds.
     */
    fun seek(position: Long, seekTimeUs: Long)

    /**
     * Extracts data starting at the current input stream position.
     *
     * @param positionHolder If [Extractor.RESULT_SEEK] is returned, this holder is updated to
     * hold the position of the required data.
     * @return One of the [Extractor]`.RESULT_*` values.
     * @throws IOException If an error occurred reading from the input.
     */
    @Throws(IOException::class)
    fun read(positionHolder: PositionHolder?): Int
}