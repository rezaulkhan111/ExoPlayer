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
package com.google.android.exoplayer2.util

import androidx.annotation.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Assertions.checkState

/**
 * Adjusts and offsets sample timestamps. MPEG-2 TS timestamps scaling and adjustment is supported,
 * taking into account timestamp rollover.
 */
class TimestampAdjuster {
    @GuardedBy("this")
    private var firstSampleTimestampUs: Long = 0

    @GuardedBy("this")
    private var timestampOffsetUs: Long = 0

    @GuardedBy("this")
    private var lastUnadjustedTimestampUs: Long = 0

    /**
     * Next sample timestamps for calling threads in shared mode when [.timestampOffsetUs] has
     * not yet been set.
     */
    // incompatible type argument for type parameter T of ThreadLocal.
    private var nextSampleTimestampUs: ThreadLocal<Long>? = null

    /**
     * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp in
     * microseconds, or [.MODE_NO_OFFSET] if timestamps should not be offset, or [     ][.MODE_SHARED] if the adjuster will be used in shared mode.
     */
    // incompatible types in assignment.
    constructor(firstSampleTimestampUs: Long) {
        nextSampleTimestampUs = ThreadLocal()
        reset(firstSampleTimestampUs)
    }

    /**
     * For shared timestamp adjusters, performs necessary initialization actions for a caller.
     *
     *
     *  * If the adjuster has already established a [timestamp offset][.getTimestampOffsetUs]
     * then this method is a no-op.
     *  * If `canInitialize` is `true` and the adjuster has not yet established a
     * timestamp offset, then the adjuster records the desired first sample timestamp for the
     * calling thread and returns to allow the caller to proceed. If the timestamp offset has
     * still not been established when the caller attempts to adjust its first timestamp, then
     * the recorded timestamp is used to set it.
     *  * If `canInitialize` is `false` and the adjuster has not yet established a
     * timestamp offset, then the call blocks until the timestamp offset is set.
     *
     *
     * @param canInitialize Whether the caller is able to initialize the adjuster, if needed.
     * @param nextSampleTimestampUs The desired timestamp for the next sample loaded by the calling
     * thread, in microseconds. Only used if `canInitialize` is `true`.
     * @throws InterruptedException If the thread is interrupted whilst blocked waiting for
     * initialization to complete.
     */
    @Synchronized
    @Throws(InterruptedException::class)
    fun sharedInitializeOrWait(canInitialize: Boolean, nextSampleTimestampUs: Long) {
        checkState(firstSampleTimestampUs == MODE_SHARED)
        if (timestampOffsetUs != C.TIME_UNSET) {
            // Already initialized.
            return
        } else if (canInitialize) {
            this.nextSampleTimestampUs!!.set(nextSampleTimestampUs)
        } else {
            // Wait for another calling thread to complete initialization.
            while (timestampOffsetUs == C.TIME_UNSET) {
                wait()
            }
        }
    }

    /**
     * Returns the value of the first adjusted sample timestamp in microseconds, or [ ][C.TIME_UNSET] if timestamps will not be offset or if the adjuster is in shared mode.
     */
    @Synchronized
    fun getFirstSampleTimestampUs(): Long {
        return if (firstSampleTimestampUs == MODE_NO_OFFSET || firstSampleTimestampUs == MODE_SHARED) C.TIME_UNSET else firstSampleTimestampUs
    }

    /**
     * Returns the last adjusted timestamp, in microseconds. If no timestamps have been adjusted yet
     * then the result of [.getFirstSampleTimestampUs] is returned.
     */
    @Synchronized
    fun getLastAdjustedTimestampUs(): Long {
        return if (lastUnadjustedTimestampUs != C.TIME_UNSET) lastUnadjustedTimestampUs + timestampOffsetUs else getFirstSampleTimestampUs()
    }

    /**
     * Returns the offset between the input of [.adjustSampleTimestamp] and its output, or
     * [C.TIME_UNSET] if the offset has not yet been determined.
     */
    @Synchronized
    fun getTimestampOffsetUs(): Long {
        return timestampOffsetUs
    }

    /**
     * Resets the instance.
     *
     * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp after
     * this reset in microseconds, or [.MODE_NO_OFFSET] if timestamps should not be offset,
     * or [.MODE_SHARED] if the adjuster will be used in shared mode.
     */
    @Synchronized
    fun reset(firstSampleTimestampUs: Long) {
        this.firstSampleTimestampUs = firstSampleTimestampUs
        timestampOffsetUs = if (firstSampleTimestampUs == MODE_NO_OFFSET) 0 else C.TIME_UNSET
        lastUnadjustedTimestampUs = C.TIME_UNSET
    }

    /**
     * Scales and offsets an MPEG-2 TS presentation timestamp considering wraparound.
     *
     * @param pts90Khz A 90 kHz clock MPEG-2 TS presentation timestamp.
     * @return The adjusted timestamp in microseconds.
     */
    @Synchronized
    fun adjustTsTimestamp(pts90Khz: Long): Long {
        var pts90Khz = pts90Khz
        if (pts90Khz == C.TIME_UNSET) {
            return C.TIME_UNSET
        }
        if (lastUnadjustedTimestampUs != C.TIME_UNSET) {
            // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
            // and we need to snap to the one closest to lastSampleTimestampUs.
            val lastPts = usToNonWrappedPts(lastUnadjustedTimestampUs)
            val closestWrapCount = lastPts + MAX_PTS_PLUS_ONE / 2 / MAX_PTS_PLUS_ONE
            val ptsWrapBelow = pts90Khz + MAX_PTS_PLUS_ONE * (closestWrapCount - 1)
            val ptsWrapAbove = pts90Khz + MAX_PTS_PLUS_ONE * closestWrapCount
            pts90Khz = if (Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)) ptsWrapBelow else ptsWrapAbove
        }
        return adjustSampleTimestamp(ptsToUs(pts90Khz))
    }

    /**
     * Offsets a timestamp in microseconds.
     *
     * @param timeUs The timestamp to adjust in microseconds.
     * @return The adjusted timestamp in microseconds.
     */
    @Synchronized
    fun adjustSampleTimestamp(timeUs: Long): Long {
        if (timeUs == C.TIME_UNSET) {
            return C.TIME_UNSET
        }
        if (timestampOffsetUs == C.TIME_UNSET) {
            val desiredSampleTimestampUs = if (firstSampleTimestampUs == MODE_SHARED) checkNotNull(nextSampleTimestampUs!!.get())!! else firstSampleTimestampUs
            timestampOffsetUs = desiredSampleTimestampUs - timeUs
            // Notify threads waiting for the timestamp offset to be determined.
            notifyAll()
        }
        lastUnadjustedTimestampUs = timeUs
        return timeUs + timestampOffsetUs
    }

    companion object {
        /**
         * A special `firstSampleTimestampUs` value indicating that presentation timestamps should
         * not be offset. In this mode:
         *
         *
         *  * [.getFirstSampleTimestampUs] will always return [C.TIME_UNSET].
         *  * The only timestamp adjustment performed is to account for MPEG-2 TS timestamp rollover.
         *
         */
        const val MODE_NO_OFFSET: Long = Long.MAX_VALUE

        /**
         * A special `firstSampleTimestampUs` value indicating that the adjuster will be shared by
         * multiple threads. In this mode:
         *
         *
         *  * [.getFirstSampleTimestampUs] will always return [C.TIME_UNSET].
         *  * Calling threads must call [.sharedInitializeOrWait] prior to adjusting timestamps.
         *
         */
        val MODE_SHARED: Long = Long.MAX_VALUE - 1

        /**
         * The value one greater than the largest representable (33 bit) MPEG-2 TS 90 kHz clock
         * presentation timestamp.
         */
        private val MAX_PTS_PLUS_ONE: Long = 0x200000000L

        /**
         * Converts a 90 kHz clock timestamp to a timestamp in microseconds.
         *
         * @param pts A 90 kHz clock timestamp.
         * @return The corresponding value in microseconds.
         */
        fun ptsToUs(pts: Long): Long {
            return (pts * C.MICROS_PER_SECOND) / 90000
        }

        /**
         * Converts a timestamp in microseconds to a 90 kHz clock timestamp, performing wraparound to keep
         * the result within 33-bits.
         *
         * @param us A value in microseconds.
         * @return The corresponding value as a 90 kHz clock timestamp, wrapped to 33 bits.
         */
        fun usToWrappedPts(us: Long): Long {
            return usToNonWrappedPts(us) % MAX_PTS_PLUS_ONE
        }

        /**
         * Converts a timestamp in microseconds to a 90 kHz clock timestamp.
         *
         *
         * Does not perform any wraparound. To get a 90 kHz timestamp suitable for use with MPEG-TS,
         * use [.usToWrappedPts].
         *
         * @param us A value in microseconds.
         * @return The corresponding value as a 90 kHz clock timestamp.
         */
        fun usToNonWrappedPts(us: Long): Long {
            return (us * 90000) / C.MICROS_PER_SECOND
        }
    }
}