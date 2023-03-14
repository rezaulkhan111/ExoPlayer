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
package com.google.android.exoplayer2.ext.cast

import android.util.SparseArray
import android.util.SparseIntArray
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.cast.CastTimeline.ItemData
import java.util.*

/** A [Timeline] for Cast media queues.  */ /* package */
internal class CastTimeline(itemIds: IntArray, itemIdToData: SparseArray<ItemData?>) : Timeline() {
    /** Holds [Timeline] related data for a Cast media item.  */
    class ItemData
    /**
     * Creates an instance.
     *
     * @param durationUs See [.durationsUs].
     * @param defaultPositionUs See [.defaultPositionUs].
     * @param isLive See [.isLive].
     * @param mediaItem See [.mediaItem].
     * @param contentId See [.contentId].
     */(
            /** The duration of the item in microseconds, or [C.TIME_UNSET] if unknown.  */
            val durationUs: Long,
            /**
             * The default start position of the item in microseconds, or [C.TIME_UNSET] if unknown.
             */
            val defaultPositionUs: Long,
            /** Whether the item is live content, or `false` if unknown.  */
            val isLive: Boolean,
            /** The original media item that has been set or added to the playlist.  */
            val mediaItem: MediaItem,
            /** The [content ID][MediaInfo.getContentId] of the cast media queue item.  */
            val contentId: String) {
        /**
         * Returns a copy of this instance with the given values.
         *
         * @param durationUs The duration in microseconds, or [C.TIME_UNSET] if unknown.
         * @param defaultPositionUs The default start position in microseconds, or [C.TIME_UNSET]
         * if unknown.
         * @param isLive Whether the item is live, or `false` if unknown.
         * @param mediaItem The media item.
         * @param contentId The content ID.
         */
        fun copyWithNewValues(
                durationUs: Long,
                defaultPositionUs: Long,
                isLive: Boolean,
                mediaItem: MediaItem,
                contentId: String): ItemData {
            return if (durationUs == this.durationUs && defaultPositionUs == this.defaultPositionUs && isLive == this.isLive && contentId == this.contentId && mediaItem == this.mediaItem) {
                this
            } else ItemData(durationUs, defaultPositionUs, isLive, mediaItem, contentId)
        }

        companion object {
            /* package */
            const val UNKNOWN_CONTENT_ID = "UNKNOWN_CONTENT_ID"

            /** Holds no media information.  */
            val EMPTY = ItemData( /* durationUs= */
                    C.TIME_UNSET,  /* defaultPositionUs= */
                    C.TIME_UNSET,  /* isLive= */
                    false,
                    MediaItem.EMPTY,
                    UNKNOWN_CONTENT_ID)
        }
    }

    private val idsToIndex: SparseIntArray
    private val mediaItems: Array<MediaItem?>
    private val ids: IntArray
    private val durationsUs: LongArray
    private val defaultPositionsUs: LongArray
    private val isLive: BooleanArray

    /**
     * Creates a Cast timeline from the given data.
     *
     * @param itemIds The ids of the items in the timeline.
     * @param itemIdToData Maps item ids to [ItemData].
     */
    init {
        val itemCount = itemIds.size
        idsToIndex = SparseIntArray(itemCount)
        ids = Arrays.copyOf(itemIds, itemCount)
        durationsUs = LongArray(itemCount)
        defaultPositionsUs = LongArray(itemCount)
        isLive = BooleanArray(itemCount)
        mediaItems = arrayOfNulls(itemCount)
        for (i in ids.indices) {
            val id = ids[i]
            idsToIndex.put(id, i)
            val data = itemIdToData[id, ItemData.EMPTY]
            mediaItems[i] = data!!.mediaItem
            durationsUs[i] = data.durationUs
            defaultPositionsUs[i] = if (data.defaultPositionUs == C.TIME_UNSET) 0 else data.defaultPositionUs
            isLive[i] = data.isLive
        }
    }

    // Timeline implementation.
    override fun getWindowCount(): Int {
        return ids.size
    }

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        val durationUs = durationsUs[windowIndex]
        val isDynamic = durationUs == C.TIME_UNSET
        return window.set( /* uid= */
                ids[windowIndex],  /* mediaItem= */
                mediaItems[windowIndex],  /* manifest= */
                null,  /* presentationStartTimeMs= */
                C.TIME_UNSET,  /* windowStartTimeMs= */
                C.TIME_UNSET,  /* elapsedRealtimeEpochOffsetMs= */
                C.TIME_UNSET,  /* isSeekable= */
                !isDynamic,
                isDynamic,
                if (isLive[windowIndex]) mediaItems[windowIndex]!!.liveConfiguration else null,
                defaultPositionsUs[windowIndex],
                durationUs,  /* firstPeriodIndex= */
                windowIndex,  /* lastPeriodIndex= */
                windowIndex,  /* positionInFirstPeriodUs= */
                0)
    }

    override fun getPeriodCount(): Int {
        return ids.size
    }

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        val id = ids[periodIndex]
        return period.set(id, id, periodIndex, durationsUs[periodIndex], 0)
    }

    override fun getIndexOfPeriod(uid: Any): Int {
        return if (uid is Int) idsToIndex[uid, C.INDEX_UNSET] else C.INDEX_UNSET
    }

    override fun getUidOfPeriod(periodIndex: Int): Int {
        return ids[periodIndex]
    }

    // equals and hashCode implementations.
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is CastTimeline) {
            return false
        }
        val that = other
        return (Arrays.equals(ids, that.ids)
                && Arrays.equals(durationsUs, that.durationsUs)
                && Arrays.equals(defaultPositionsUs, that.defaultPositionsUs)
                && Arrays.equals(isLive, that.isLive))
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(ids)
        result = 31 * result + Arrays.hashCode(durationsUs)
        result = 31 * result + Arrays.hashCode(defaultPositionsUs)
        result = 31 * result + Arrays.hashCode(isLive)
        return result
    }

    companion object {
        /** [Timeline] for a cast queue that has no items.  */
        val EMPTY_CAST_TIMELINE = CastTimeline(IntArray(0), SparseArray())
    }
}