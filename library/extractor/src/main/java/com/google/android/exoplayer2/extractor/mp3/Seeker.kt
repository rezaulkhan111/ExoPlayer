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
package com.google.android.exoplayer2.extractor.mp3

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.extractor.SeekMap
import com.google.android.exoplayer2.extractor.SeekMap.Unseekable

/**
 * [SeekMap] that provides the end position of audio data and also allows mapping from
 * position (byte offset) back to time, which can be used to work out the new sample basis timestamp
 * after seeking and resynchronization.
 */
/* package */
internal interface Seeker : SeekMap {
    /**
     * Maps a position (byte offset) to a corresponding sample timestamp.
     *
     * @param position A seek position (byte offset) relative to the start of the stream.
     * @return The corresponding timestamp of the next sample to be read, in microseconds.
     */
    fun getTimeUs(position: Long): Long

    /**
     * Returns the position (byte offset) in the stream that is immediately after audio data, or
     * [C.POSITION_UNSET] if not known.
     */
    fun getDataEndPosition(): Long

    /** A [Seeker] that does not support seeking through audio data.  */ /* package */
    class UnseekableSeeker : Unseekable, Seeker {

        constructor() : super( /* durationUs= */C.TIME_UNSET) {
        }

        override fun getTimeUs(position: Long): Long {
            return 0
        }

        override fun getDataEndPosition(): Long {
            // Position unset as we do not know the data end position. Note that returning 0 doesn't work.
            return C.POSITION_UNSET.toLong()
        }
    }
}