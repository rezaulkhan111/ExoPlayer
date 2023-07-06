/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline

import android.os.Parcel
import android.os.Parcelable

/**
 * A key for a subset of media that can be separately loaded (a "stream").
 *
 *
 * The stream key consists of a period index, a group index within the period and a stream index
 * within the group. The interpretation of these indices depends on the type of media for which the
 * stream key is used. Note that they are *not* the same as track group and track indices,
 * because multiple tracks can be multiplexed into a single stream.
 *
 *
 * Application code should not generally attempt to build StreamKey instances directly. Instead,
 * `DownloadHelper.getDownloadRequest` can be used to generate download requests with the
 * correct StreamKeys for the track selections that have been configured on the helper. `MediaPeriod.getStreamKeys` provides a lower level way of generating StreamKeys corresponding to a
 * particular track selection.
 */
class StreamKey : Comparable<StreamKey>, Parcelable {
    /** The period index.  */
    val periodIndex: Int

    /** The group index.  */
    val groupIndex: Int

    /** The stream index.  */
    val streamIndex: Int

    @Deprecated("Use {@link #streamIndex}.")
    val trackIndex: Int

    /**
     * Creates an instance with [.periodIndex] set to 0.
     *
     * @param groupIndex The group index.
     * @param streamIndex The stream index.
     */
    constructor(groupIndex: Int, streamIndex: Int) : this(0, groupIndex, streamIndex) {}

    /**
     * Creates an instance.
     *
     * @param periodIndex The period index.
     * @param groupIndex The group index.
     * @param streamIndex The stream index.
     */
    constructor(periodIndex: Int, groupIndex: Int, streamIndex: Int) {
        this.periodIndex = periodIndex
        this.groupIndex = groupIndex
        this.streamIndex = streamIndex
        trackIndex = streamIndex
    }

    internal constructor(parcel: Parcel) {
        periodIndex = parcel.readInt()
        groupIndex = parcel.readInt()
        streamIndex = parcel.readInt()
        trackIndex = streamIndex
    }

    override fun toString(): String {
        return "$periodIndex.$groupIndex.$streamIndex"
    }

    override fun equals(anyObj: Any?): Boolean {
        if (this === anyObj) {
            return true
        }
        if (anyObj == null || javaClass != anyObj.javaClass) {
            return false
        }
        val that: StreamKey = anyObj as StreamKey
        return (periodIndex == that.periodIndex) && (groupIndex == that.groupIndex) && (streamIndex == that.streamIndex)
    }

    override fun hashCode(): Int {
        var result: Int = periodIndex
        result = 31 * result + groupIndex
        result = 31 * result + streamIndex
        return result
    }

    // Comparable implementation.
    override fun compareTo(o: StreamKey): Int {
        var result: Int = periodIndex - o.periodIndex
        if (result == 0) {
            result = groupIndex - o.groupIndex
            if (result == 0) {
                result = streamIndex - o.streamIndex
            }
        }
        return result
    }

    // Parcelable implementation.
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(periodIndex)
        dest.writeInt(groupIndex)
        dest.writeInt(streamIndex)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<StreamKey> = object : Parcelable.Creator<StreamKey> {
            public override fun createFromParcel(`in`: Parcel): StreamKey {
                return StreamKey(`in`)
            }

            public override fun newArray(size: Int): Array<StreamKey?> {
                return arrayOfNulls(size)
            }
        }
    }
}