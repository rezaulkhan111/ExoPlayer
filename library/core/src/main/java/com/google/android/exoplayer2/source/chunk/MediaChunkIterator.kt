/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.android.exoplayer2.upstream.DataSpec
import java.util.NoSuchElementException

/**
 * Iterator for media chunk sequences.
 *
 *
 * The iterator initially points in front of the first available element. The first call to
 * [.next] moves the iterator to the first element. Check the return value of [ ][.next] or [.isEnded] to determine whether the iterator reached the end of the available
 * data.
 */
interface MediaChunkIterator {
    companion object {
        /** An empty media chunk iterator without available data.  */
        @JvmField
        val EMPTY: MediaChunkIterator = object : MediaChunkIterator {
            override fun isEnded(): Boolean {
                return true
            }

            override fun next(): Boolean {
                return false
            }

            override fun getDataSpec(): DataSpec? {
                throw NoSuchElementException()
            }

            override fun getChunkStartTimeUs(): Long {
                throw NoSuchElementException()
            }

            override fun getChunkEndTimeUs(): Long {
                throw NoSuchElementException()
            }

            override fun reset() {
                // Do nothing.
            }
        }
    }

    /** Returns whether the iteration has reached the end of the available data.  */
    fun isEnded(): Boolean

    /**
     * Moves the iterator to the next media chunk.
     *
     *
     * Check the return value or [.isEnded] to determine whether the iterator reached the
     * end of the available data.
     *
     * @return Whether the iterator points to a media chunk with available data.
     */
    operator fun next(): Boolean

    /**
     * Returns the [DataSpec] used to load the media chunk.
     *
     * @throws java.util.NoSuchElementException If the method is called before the first call to
     * [.next] or when [.isEnded] is true.
     */
    fun getDataSpec(): DataSpec?

    /**
     * Returns the media start time of the chunk, in microseconds.
     *
     * @throws java.util.NoSuchElementException If the method is called before the first call to
     * [.next] or when [.isEnded] is true.
     */
    fun getChunkStartTimeUs(): Long

    /**
     * Returns the media end time of the chunk, in microseconds.
     *
     * @throws java.util.NoSuchElementException If the method is called before the first call to
     * [.next] or when [.isEnded] is true.
     */
    fun getChunkEndTimeUs(): Long

    /** Resets the iterator to the initial position.  */
    fun reset()
}