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

import com.google.android.exoplayer2.util.Assertions.checkNotNull

/**
 * Maps seek positions (in microseconds) to corresponding positions (byte offsets) in the stream.
 */
interface SeekMap {
    /** A [SeekMap] that does not support seeking.  */
    open class Unseekable : SeekMap {

        private var durationUs: Long = 0
        private var startSeekPoints: SeekPoints? = null

        /**
         * @param durationUs The duration of the stream in microseconds, or [C.TIME_UNSET] if the
         * duration is unknown.
         */
        constructor(durationUs: Long) : this(durationUs, 0) {

        }

        /**
         * @param durationUs The duration of the stream in microseconds, or [C.TIME_UNSET] if the
         * duration is unknown.
         * @param startPosition The position (byte offset) of the start of the media.
         */
        constructor(durationUs: Long, startPosition: Long) {
            this.durationUs = durationUs
            startSeekPoints = SeekPoints(
                if (startPosition == 0L) SeekPoint.START else SeekPoint(
                    0, startPosition
                )
            )
        }

        override fun isSeekable(): Boolean {
            return false
        }

        override fun getDurationUs(): Long {
            return durationUs
        }

        override fun getSeekPoints(timeUs: Long): SeekPoints? {
            return startSeekPoints
        }
    }

    /** Contains one or two [SeekPoint]s.  */
    class SeekPoints {
        /** The first seek point.  */
        var first: SeekPoint? = null

        /** The second seek point, or [.first] if there's only one seek point.  */
        var second: SeekPoint? = null

        /**
         * @param point The single seek point.
         */
        constructor(point: SeekPoint?) : this(point, point) {

        }

        /**
         * @param first The first seek point.
         * @param second The second seek point.
         */
        constructor(first: SeekPoint?, second: SeekPoint?) {
            this.first = checkNotNull(first)
            this.second = checkNotNull(second)
        }

        override fun toString(): String {
            return "[" + first + (if (first == second) "" else ", $second") + "]"
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null || javaClass != obj.javaClass) {
                return false
            }
            val other = obj as SeekPoints
            return first == other.first && second == other.second
        }

        override fun hashCode(): Int {
            return 31 * first.hashCode() + second.hashCode()
        }
    }

    /**
     * Returns whether seeking is supported.
     *
     * @return Whether seeking is supported.
     */
    fun isSeekable(): Boolean

    /**
     * Returns the duration of the stream in microseconds.
     *
     * @return The duration of the stream in microseconds, or [C.TIME_UNSET] if the duration is
     * unknown.
     */
    fun getDurationUs(): Long

    /**
     * Obtains seek points for the specified seek time in microseconds. The returned [ ] will contain one or two distinct seek points.
     *
     *
     * Two seek points [A, B] are returned in the case that seeking can only be performed to
     * discrete points in time, there does not exist a seek point at exactly the requested time, and
     * there exist seek points on both sides of it. In this case A and B are the closest seek points
     * before and after the requested time. A single seek point is returned in all other cases.
     *
     * @param timeUs A seek time in microseconds.
     * @return The corresponding seek points.
     */
    fun getSeekPoints(timeUs: Long): SeekPoints?
}