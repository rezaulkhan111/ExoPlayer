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
package com.google.android.exoplayer2

import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Util.addWithOverflowDefault
import com.google.android.exoplayer2.util.Util.subtractWithOverflowDefault

/**
 * Parameters that apply to seeking.
 *
 *
 * The predefined [.EXACT], [.CLOSEST_SYNC], [.PREVIOUS_SYNC] and [ ][.NEXT_SYNC] parameters are suitable for most use cases. Seeking to sync points is typically
 * faster but less accurate than exact seeking.
 *
 *
 * In the general case, an instance specifies a maximum tolerance before ([ ][.toleranceBeforeUs]) and after ([.toleranceAfterUs]) a requested seek position (`x`).
 * If one or more sync points falls within the window `[x - toleranceBeforeUs, x +
 * toleranceAfterUs]` then the seek will be performed to the sync point within the window that's
 * closest to `x`. If no sync point falls within the window then the seek will be performed to
 * `x - toleranceBeforeUs`. Internally the player may need to seek to an earlier sync point
 * and discard media until this position is reached.
 */
class SeekParameters(toleranceBeforeUs: Long, toleranceAfterUs: Long) {
    /**
     * The maximum time that the actual position seeked to may precede the requested seek position, in
     * microseconds.
     */
    @JvmField
    val toleranceBeforeUs: Long

    /**
     * The maximum time that the actual position seeked to may exceed the requested seek position, in
     * microseconds.
     */
    @JvmField
    val toleranceAfterUs: Long

    /**
     * @param toleranceBeforeUs The maximum time that the actual position seeked to may precede the
     * requested seek position, in microseconds. Must be non-negative.
     * @param toleranceAfterUs The maximum time that the actual position seeked to may exceed the
     * requested seek position, in microseconds. Must be non-negative.
     */
    init {
        checkArgument(toleranceBeforeUs >= 0)
        checkArgument(toleranceAfterUs >= 0)
        this.toleranceBeforeUs = toleranceBeforeUs
        this.toleranceAfterUs = toleranceAfterUs
    }

    /**
     * Resolves a seek based on the parameters, given the requested seek position and two candidate
     * sync points.
     *
     * @param positionUs The requested seek position, in microseocnds.
     * @param firstSyncUs The first candidate seek point, in micrseconds.
     * @param secondSyncUs The second candidate seek point, in microseconds. May equal `firstSyncUs` if there's only one candidate.
     * @return The resolved seek position, in microseconds.
     */
    fun resolveSeekPositionUs(positionUs: Long, firstSyncUs: Long, secondSyncUs: Long): Long {
        if (toleranceBeforeUs == 0L && toleranceAfterUs == 0L) {
            return positionUs
        }
        val minPositionUs = subtractWithOverflowDefault(positionUs, toleranceBeforeUs, Long.MIN_VALUE)
        val maxPositionUs = addWithOverflowDefault(positionUs, toleranceAfterUs, Long.MAX_VALUE)
        val firstSyncPositionValid = minPositionUs <= firstSyncUs && firstSyncUs <= maxPositionUs
        val secondSyncPositionValid = minPositionUs <= secondSyncUs && secondSyncUs <= maxPositionUs
        return if (firstSyncPositionValid && secondSyncPositionValid) {
            if (Math.abs(firstSyncUs - positionUs) <= Math.abs(secondSyncUs - positionUs)) {
                firstSyncUs
            } else {
                secondSyncUs
            }
        } else if (firstSyncPositionValid) {
            firstSyncUs
        } else if (secondSyncPositionValid) {
            secondSyncUs
        } else {
            minPositionUs
        }
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as SeekParameters
        return (toleranceBeforeUs == other.toleranceBeforeUs
                && toleranceAfterUs == other.toleranceAfterUs)
    }

    override fun hashCode(): Int {
        return 31 * toleranceBeforeUs.toInt() + toleranceAfterUs.toInt()
    }

    companion object {
        /** Parameters for exact seeking.  */
        @JvmField
        val EXACT = SeekParameters(0, 0)

        /** Parameters for seeking to the closest sync point.  */
        @JvmField
        val CLOSEST_SYNC = SeekParameters(Long.MAX_VALUE, Long.MAX_VALUE)

        /** Parameters for seeking to the sync point immediately before a requested seek position.  */
        @JvmField
        val PREVIOUS_SYNC = SeekParameters(Long.MAX_VALUE, 0)

        /** Parameters for seeking to the sync point immediately after a requested seek position.  */
        @JvmField
        val NEXT_SYNC = SeekParameters(0, Long.MAX_VALUE)

        /** Default parameters.  */
        @JvmField
        val DEFAULT = EXACT
    }
}