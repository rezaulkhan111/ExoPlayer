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
package com.google.android.exoplayer2.ext.cast

import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.cast.CastTimeline.ItemData
import com.google.android.exoplayer2.util.Assertions
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient

/**
 * Creates [CastTimelines][CastTimeline] from cast receiver app status updates.
 *
 *
 * This class keeps track of the duration reported by the current item to fill any missing
 * durations in the media queue items [See internal: b/65152553].
 */
/* package */
internal class CastTimelineTracker(private val mediaItemConverter: MediaItemConverter) {
    private val itemIdToData: SparseArray<ItemData?>

    @JvmField
    @VisibleForTesting /* package */
    val mediaItemsByContentId: HashMap<String?, MediaItem>

    /**
     * Creates an instance.
     *
     * @param mediaItemConverter The converter used to convert from a [MediaQueueItem] to a
     * [MediaItem].
     */
    init {
        itemIdToData = SparseArray()
        mediaItemsByContentId = HashMap()
    }

    /**
     * Called when media items [have been set to the playlist][Player.setMediaItems] and are
     * sent to the cast playback queue. A future queue update of the [RemoteMediaClient] will
     * reflect this addition.
     *
     * @param mediaItems The media items that have been set.
     * @param mediaQueueItems The corresponding media queue items.
     */
    fun onMediaItemsSet(mediaItems: List<MediaItem>, mediaQueueItems: Array<MediaQueueItem?>) {
        mediaItemsByContentId.clear()
        onMediaItemsAdded(mediaItems, mediaQueueItems)
    }

    /**
     * Called when media items [have been added][Player.addMediaItems] and are sent to
     * the cast playback queue. A future queue update of the [RemoteMediaClient] will reflect
     * this addition.
     *
     * @param mediaItems The media items that have been added.
     * @param mediaQueueItems The corresponding media queue items.
     */
    fun onMediaItemsAdded(mediaItems: List<MediaItem>, mediaQueueItems: Array<MediaQueueItem?>) {
        for (i in mediaItems.indices) {
            mediaItemsByContentId[Assertions.checkNotNull(mediaQueueItems[i]!!.media).contentId] = mediaItems[i]
        }
    }

    /**
     * Returns a [CastTimeline] that represents the state of the given `remoteMediaClient`.
     *
     *
     * Returned timelines may contain values obtained from `remoteMediaClient` in previous
     * invocations of this method.
     *
     * @param remoteMediaClient The Cast media client.
     * @return A [CastTimeline] that represents the given `remoteMediaClient` status.
     */
    fun getCastTimeline(remoteMediaClient: RemoteMediaClient?): CastTimeline {
        val itemIds = remoteMediaClient!!.mediaQueue.itemIds
        if (itemIds.size > 0) {
            // Only remove unused items when there is something in the queue to avoid removing all entries
            // if the remote media client clears the queue temporarily. See [Internal ref: b/128825216].
            removeUnusedItemDataEntries(itemIds)
        }

        // TODO: Reset state when the app instance changes [Internal ref: b/129672468].
        val mediaStatus = remoteMediaClient.mediaStatus
                ?: return CastTimeline.Companion.EMPTY_CAST_TIMELINE
        val currentItemId = mediaStatus.currentItemId
        val currentContentId = Assertions.checkStateNotNull(mediaStatus.mediaInfo).contentId
        var mediaItem = mediaItemsByContentId[currentContentId]
        updateItemData(
                currentItemId,
                mediaItem ?: MediaItem.EMPTY,
                mediaStatus.mediaInfo,
                currentContentId,  /* defaultPositionUs= */
                C.TIME_UNSET)
        for (queueItem in mediaStatus.queueItems) {
            val defaultPositionUs = (queueItem.startTime * C.MICROS_PER_SECOND).toLong()
            val mediaInfo = queueItem.media
            val contentId = mediaInfo?.contentId ?: ItemData.Companion.UNKNOWN_CONTENT_ID
            mediaItem = mediaItemsByContentId[contentId]
            updateItemData(
                    queueItem.itemId,
                    mediaItem ?: mediaItemConverter.toMediaItem(queueItem),
                    mediaInfo,
                    contentId,
                    defaultPositionUs)
        }
        return CastTimeline(itemIds, itemIdToData)
    }

    private fun updateItemData(
            itemId: Int,
            mediaItem: MediaItem,
            mediaInfo: MediaInfo?,
            contentId: String,
            defaultPositionUs: Long) {
        var defaultPositionUs = defaultPositionUs
        val previousData = itemIdToData[itemId, ItemData.Companion.EMPTY]
        var durationUs = CastUtils.getStreamDurationUs(mediaInfo)
        if (durationUs == C.TIME_UNSET) {
            durationUs = previousData!!.durationUs
        }
        val isLive = if (mediaInfo == null) previousData!!.isLive else mediaInfo.streamType == MediaInfo.STREAM_TYPE_LIVE
        if (defaultPositionUs == C.TIME_UNSET) {
            defaultPositionUs = previousData!!.defaultPositionUs
        }
        itemIdToData.put(
                itemId,
                previousData!!.copyWithNewValues(
                        durationUs, defaultPositionUs, isLive, mediaItem, contentId))
    }

    private fun removeUnusedItemDataEntries(itemIds: IntArray) {
        val scratchItemIds = HashSet<Int>( /* initialCapacity= */itemIds.size * 2)
        for (id in itemIds) {
            scratchItemIds.add(id)
        }
        var index = 0
        while (index < itemIdToData.size()) {
            if (!scratchItemIds.contains(itemIdToData.keyAt(index))) {
                val itemData = itemIdToData.valueAt(index)
                mediaItemsByContentId.remove(itemData!!.contentId)
                itemIdToData.removeAt(index)
            } else {
                index++
            }
        }
    }
}