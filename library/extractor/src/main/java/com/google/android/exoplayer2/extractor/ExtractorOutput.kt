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
package com.google.android.exoplayer2.extractor

import com.google.android.exoplayer2.C.TrackType

/** Receives stream level data extracted by an [Extractor].  */
interface ExtractorOutput {
    /**
     * Called by the [Extractor] to get the [TrackOutput] for a specific track.
     *
     *
     * The same [TrackOutput] is returned if multiple calls are made with the same `id`.
     *
     * @param id A track identifier.
     * @param type The [track type][C.TrackType].
     * @return The [TrackOutput] for the given track identifier.
     */
    fun track(id: Int, type: @TrackType Int): TrackOutput?

    /**
     * Called when all tracks have been identified, meaning no new `trackId` values will be
     * passed to [.track].
     */
    fun endTracks()

    /**
     * Called when a [SeekMap] has been extracted from the stream.
     *
     * @param seekMap The extracted [SeekMap].
     */
    fun seekMap(seekMap: SeekMap?)

    companion object {
        /**
         * Placeholder [ExtractorOutput] implementation throwing an [ ] in each method.
         */
        @JvmField
        val PLACEHOLDER: ExtractorOutput = object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput? {
                throw UnsupportedOperationException()
            }

            override fun endTracks() {
                throw UnsupportedOperationException()
            }

            override fun seekMap(seekMap: SeekMap?) {
                throw UnsupportedOperationException()
            }
        }
    }
}