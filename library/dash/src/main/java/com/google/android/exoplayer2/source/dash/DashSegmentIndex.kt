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
package com.google.android.exoplayer2.source.dash

import com.google.android.exoplayer2.source.dash.manifest.RangedUri

/** Indexes the segments within a media stream.  */
interface DashSegmentIndex {

    companion object {
        const val INDEX_UNBOUNDED = -1
    }

    /**
     * Returns `getFirstSegmentNum()` if the index has no segments or if the given media time is
     * earlier than the start of the first segment. Returns `getFirstSegmentNum() +
     * getSegmentCount() - 1` if the given media time is later than the end of the last segment.
     * Otherwise, returns the segment number of the segment containing the given media time.
     *
     * @param timeUs The time in microseconds.
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @return The segment number of the corresponding segment.
     */
    fun getSegmentNum(timeUs: Long, periodDurationUs: Long): Long

    /**
     * Returns the start time of a segment.
     *
     * @param segmentNum The segment number.
     * @return The corresponding start time in microseconds.
     */
    fun getTimeUs(segmentNum: Long): Long

    /**
     * Returns the duration of a segment.
     *
     * @param segmentNum The segment number.
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @return The duration of the segment, in microseconds.
     */
    fun getDurationUs(segmentNum: Long, periodDurationUs: Long): Long

    /**
     * Returns a [RangedUri] defining the location of a segment.
     *
     * @param segmentNum The segment number.
     * @return The [RangedUri] defining the location of the data.
     */
    fun getSegmentUrl(segmentNum: Long): RangedUri?

    /** Returns the segment number of the first defined segment in the index.  */
    fun getFirstSegmentNum(): Long

    /**
     * Returns the segment number of the first available segment in the index.
     *
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @param nowUnixTimeUs The current time in milliseconds since the Unix epoch.
     * @return The number of the first available segment.
     */
    fun getFirstAvailableSegmentNum(periodDurationUs: Long, nowUnixTimeUs: Long): Long

    /**
     * Returns the number of segments defined in the index, or [.INDEX_UNBOUNDED].
     *
     *
     * An unbounded index occurs if a dynamic manifest uses SegmentTemplate elements without a
     * SegmentTimeline element, and if the period duration is not yet known. In this case the caller
     * can query the available segment using [.getFirstAvailableSegmentNum] and
     * [.getAvailableSegmentCount].
     *
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @return The number of segments in the index, or [.INDEX_UNBOUNDED].
     */
    fun getSegmentCount(periodDurationUs: Long): Long

    /**
     * Returns the number of available segments in the index.
     *
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @param nowUnixTimeUs The current time in milliseconds since the Unix epoch.
     * @return The number of available segments in the index.
     */
    fun getAvailableSegmentCount(periodDurationUs: Long, nowUnixTimeUs: Long): Long

    /**
     * Returns the time, in microseconds, at which a new segment becomes available, or [ ][C.TIME_UNSET] if not applicable.
     *
     * @param periodDurationUs The duration of the enclosing period in microseconds, or [     ][C.TIME_UNSET] if the period's duration is not yet known.
     * @param nowUnixTimeUs The current time in milliseconds since the Unix epoch.
     * @return The time, in microseconds, at which a new segment becomes available, or [     ][C.TIME_UNSET] if not applicable.
     */
    fun getNextSegmentAvailableTimeUs(periodDurationUs: Long, nowUnixTimeUs: Long): Long

    /**
     * Returns true if segments are defined explicitly by the index.
     *
     *
     * If true is returned, each segment is defined explicitly by the index data, and all of the
     * listed segments are guaranteed to be available at the time when the index was obtained.
     *
     *
     * If false is returned then segment information was derived from properties such as a fixed
     * segment duration. If the presentation is dynamic, it's possible that only a subset of the
     * segments are available.
     *
     * @return Whether segments are defined explicitly by the index.
     */
    fun isExplicit(): Boolean
}