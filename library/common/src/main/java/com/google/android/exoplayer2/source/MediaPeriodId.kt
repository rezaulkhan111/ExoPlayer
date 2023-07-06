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
package com.google.android.exoplayer2.source

import com.google.android.exoplayer2.*

/**
 * Identifies a specific playback of a [Timeline.Period].
 *
 *
 * A [Timeline.Period] can be played multiple times, for example if it is repeated. Each
 * instances of this class identifies a specific playback of a [Timeline.Period].
 *
 *
 * In ExoPlayer's implementation, [MediaPeriodId] identifies a `MediaPeriod`.
 */
// TODO(b/172315872) Should be final, but subclassed in MediaSource for backward-compatibility.
open class MediaPeriodId {
    /** The unique id of the timeline period.  */
    val periodUid: Any

    /**
     * If the media period is in an ad group, the index of the ad group in the period. [ ][C.INDEX_UNSET] otherwise.
     */
    val adGroupIndex: Int

    /**
     * If the media period is in an ad group, the index of the ad in its ad group in the period.
     * [C.INDEX_UNSET] otherwise.
     */
    val adIndexInAdGroup: Int

    /**
     * The sequence number of the window in the buffered sequence of windows this media period is part
     * of. [C.INDEX_UNSET] if the media period id is not part of a buffered sequence of windows.
     */
    val windowSequenceNumber: Long

    /**
     * The index of the next ad group to which the media period's content is clipped, or [ ][C.INDEX_UNSET] if there is no following ad group or if this media period is an ad.
     */
    val nextAdGroupIndex: Int
    /**
     * Creates a media period identifier for the specified period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     * windows this media period is part of.
     */
    /**
     * Creates a media period identifier for a period which is not part of a buffered sequence of
     * windows.
     *
     * @param periodUid The unique id of the timeline period.
     */
    @JvmOverloads
    constructor(periodUid: Any, windowSequenceNumber: Long =  /* windowSequenceNumber= */C.INDEX_UNSET.toLong()) : this(
            periodUid,  /* adGroupIndex= */
            C.INDEX_UNSET,  /* adIndexInAdGroup= */
            C.INDEX_UNSET,
            windowSequenceNumber,  /* nextAdGroupIndex= */
            C.INDEX_UNSET) {
    }

    /**
     * Creates a media period identifier for the specified clipped period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     * windows this media period is part of.
     * @param nextAdGroupIndex The index of the next ad group to which the media period's content is
     * clipped.
     */
    constructor(periodUid: Any, windowSequenceNumber: Long, nextAdGroupIndex: Int) : this(
            periodUid,  /* adGroupIndex= */
            C.INDEX_UNSET,  /* adIndexInAdGroup= */
            C.INDEX_UNSET,
            windowSequenceNumber,
            nextAdGroupIndex) {
    }

    /**
     * Creates a media period identifier that identifies an ad within an ad group at the specified
     * timeline period.
     *
     * @param periodUid The unique id of the timeline period that contains the ad group.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     * windows this media period is part of.
     */
    constructor(
            periodUid: Any, adGroupIndex: Int, adIndexInAdGroup: Int, windowSequenceNumber: Long) : this(
            periodUid,
            adGroupIndex,
            adIndexInAdGroup,
            windowSequenceNumber,  /* nextAdGroupIndex= */
            C.INDEX_UNSET) {
    }

    /** Copy constructor for inheritance.  */ // TODO(b/172315872) Delete when client have migrated from MediaSource.MediaPeriodId
    protected constructor(mediaPeriodId: MediaPeriodId) {
        periodUid = mediaPeriodId.periodUid
        adGroupIndex = mediaPeriodId.adGroupIndex
        adIndexInAdGroup = mediaPeriodId.adIndexInAdGroup
        windowSequenceNumber = mediaPeriodId.windowSequenceNumber
        nextAdGroupIndex = mediaPeriodId.nextAdGroupIndex
    }

    private constructor(
            periodUid: Any,
            adGroupIndex: Int,
            adIndexInAdGroup: Int,
            windowSequenceNumber: Long,
            nextAdGroupIndex: Int) {
        this.periodUid = periodUid
        this.adGroupIndex = adGroupIndex
        this.adIndexInAdGroup = adIndexInAdGroup
        this.windowSequenceNumber = windowSequenceNumber
        this.nextAdGroupIndex = nextAdGroupIndex
    }

    /** Returns a copy of this period identifier but with `newPeriodUid` as its period uid.  */
    open fun copyWithPeriodUid(newPeriodUid: Any): MediaPeriodId? {
        return if ((periodUid == newPeriodUid)) this else MediaPeriodId(
                newPeriodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, nextAdGroupIndex)
    }

    /** Returns a copy of this period identifier with a new `windowSequenceNumber`.  */
    open fun copyWithWindowSequenceNumber(windowSequenceNumber: Long): MediaPeriodId? {
        return if (this.windowSequenceNumber == windowSequenceNumber) this else MediaPeriodId(
                periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, nextAdGroupIndex)
    }

    /** Returns whether this period identifier identifies an ad in an ad group in a period.  */
    val isAd: Boolean
        get() {
            return adGroupIndex != C.INDEX_UNSET
        }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (!(obj is MediaPeriodId)) {
            return false
        }
        val periodId: MediaPeriodId = obj
        return ((periodUid == periodId.periodUid) && (adGroupIndex == periodId.adGroupIndex
                ) && (adIndexInAdGroup == periodId.adIndexInAdGroup
                ) && (windowSequenceNumber == periodId.windowSequenceNumber
                ) && (nextAdGroupIndex == periodId.nextAdGroupIndex))
    }

    override fun hashCode(): Int {
        var result: Int = 17
        result = 31 * result + periodUid.hashCode()
        result = 31 * result + adGroupIndex
        result = 31 * result + adIndexInAdGroup
        result = 31 * result + windowSequenceNumber.toInt()
        result = 31 * result + nextAdGroupIndex
        return result
    }
}