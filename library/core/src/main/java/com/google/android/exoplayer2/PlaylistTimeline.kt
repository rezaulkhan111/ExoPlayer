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
package com.google.android.exoplayer2

import com.google.android.exoplayer2.source.ShuffleOrder
import com.google.android.exoplayer2.util.Util.binarySearchFloor
import java.util.*

/** Timeline exposing concatenated timelines of playlist media sources.  */ /* package */
internal class PlaylistTimeline(
        mediaSourceInfoHolders: Collection<MediaSourceInfoHolder>,
        shuffleOrder: ShuffleOrder?) : AbstractConcatenatedTimeline( /* isAtomic= */false, shuffleOrder) {
    override val windowCount: Int
    override val periodCount: Int
    private val firstPeriodInChildIndices: IntArray
    private val firstWindowInChildIndices: IntArray
    private val timelines: Array<Timeline>
    private val uids: Array<Any>
    private val childIndexByUid: HashMap<Any, Int>

    /** Creates an instance.  */
    init {
        val childCount = mediaSourceInfoHolders.size
        firstPeriodInChildIndices = IntArray(childCount)
        firstWindowInChildIndices = IntArray(childCount)
        timelines = arrayOfNulls(childCount)
        uids = arrayOfNulls(childCount)
        childIndexByUid = HashMap()
        var index = 0
        var windowCount = 0
        var periodCount = 0
        for (mediaSourceInfoHolder in mediaSourceInfoHolders) {
            timelines[index] = mediaSourceInfoHolder.timeline
            firstWindowInChildIndices[index] = windowCount
            firstPeriodInChildIndices[index] = periodCount
            windowCount += timelines[index].windowCount
            periodCount += timelines[index].periodCount
            uids[index] = mediaSourceInfoHolder.uid
            childIndexByUid[uids[index]] = index++
        }
        this.windowCount = windowCount
        this.periodCount = periodCount
    }

    /** Returns the child timelines.  */ /* package */
    val childTimelines: List<Timeline>
        get() = Arrays.asList(*timelines)

    override fun getChildIndexByPeriodIndex(periodIndex: Int): Int {
        return binarySearchFloor(firstPeriodInChildIndices, periodIndex + 1, false, false)
    }

    override fun getChildIndexByWindowIndex(windowIndex: Int): Int {
        return binarySearchFloor(firstWindowInChildIndices, windowIndex + 1, false, false)
    }

    override fun getChildIndexByChildUid(childUid: Any): Int {
        val index = childIndexByUid[childUid]
        return index ?: C.INDEX_UNSET
    }

    override fun getTimelineByChildIndex(childIndex: Int): Timeline {
        return timelines[childIndex]
    }

    override fun getFirstPeriodIndexByChildIndex(childIndex: Int): Int {
        return firstPeriodInChildIndices[childIndex]
    }

    override fun getFirstWindowIndexByChildIndex(childIndex: Int): Int {
        return firstWindowInChildIndices[childIndex]
    }

    override fun getChildUidByChildIndex(childIndex: Int): Any {
        return uids[childIndex]
    }
}