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
package com.google.android.exoplayer2.ext.mediasession

import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CommandReceiver
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueEditor
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.MediaDescriptionConverter
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.QueueDataAdapter
import com.google.android.exoplayer2.util.Util

/**
 * A [MediaSessionConnector.QueueEditor] implementation.
 *
 *
 * This class implements the [MediaSessionConnector.CommandReceiver] interface and handles
 * the [.COMMAND_MOVE_QUEUE_ITEM] to move a queue item instead of removing and inserting it.
 * This allows to move the currently playing window without interrupting playback.
 */
class TimelineQueueEditor
/**
 * Creates a new [TimelineQueueEditor] with a given mediaSourceFactory.
 *
 * @param mediaController A [MediaControllerCompat] to read the current queue.
 * @param queueDataAdapter A [QueueDataAdapter] to change the backing data.
 * @param mediaDescriptionConverter The [MediaDescriptionConverter] for converting media
 * descriptions to [MediaItems][MediaItem].
 */ @JvmOverloads constructor(
        private val mediaController: MediaControllerCompat,
        private val queueDataAdapter: QueueDataAdapter,
        private val mediaDescriptionConverter: MediaDescriptionConverter,
        private val equalityChecker: MediaDescriptionEqualityChecker = MediaIdEqualityChecker()) : QueueEditor, CommandReceiver {
    /** Converts a [MediaDescriptionCompat] to a [MediaItem].  */
    interface MediaDescriptionConverter {
        /**
         * Returns a [MediaItem] for the given [MediaDescriptionCompat] or null if the
         * description can't be converted.
         *
         *
         * If not null, the media item that is returned will be used to call [ ][Player.addMediaItem].
         */
        fun convert(description: MediaDescriptionCompat?): MediaItem?
    }

    /**
     * Adapter to get [MediaDescriptionCompat] of items in the queue and to notify the
     * application about changes in the queue to sync the data structure backing the [ ].
     */
    interface QueueDataAdapter {
        /**
         * Adds a [MediaDescriptionCompat] at the given `position`.
         *
         * @param position The position at which to add.
         * @param description The [MediaDescriptionCompat] to be added.
         */
        fun add(position: Int, description: MediaDescriptionCompat?)

        /**
         * Removes the item at the given `position`.
         *
         * @param position The position at which to remove the item.
         */
        fun remove(position: Int)

        /**
         * Moves a queue item from position `from` to position `to`.
         *
         * @param from The position from which to remove the item.
         * @param to The target position to which to move the item.
         */
        fun move(from: Int, to: Int)
    }

    /** Used to evaluate whether two [MediaDescriptionCompat] are considered equal.  */
    interface MediaDescriptionEqualityChecker {
        /**
         * Returns `true` whether the descriptions are considered equal.
         *
         * @param d1 The first [MediaDescriptionCompat].
         * @param d2 The second [MediaDescriptionCompat].
         * @return `true` if considered equal.
         */
        fun equals(d1: MediaDescriptionCompat, d2: MediaDescriptionCompat): Boolean
    }

    /**
     * Media description comparator comparing the media IDs. Media IDs are considered equals if both
     * are `null`.
     */
    class MediaIdEqualityChecker : MediaDescriptionEqualityChecker {
        override fun equals(d1: MediaDescriptionCompat, d2: MediaDescriptionCompat): Boolean {
            return Util.areEqual(d1.mediaId, d2.mediaId)
        }
    }

    /**
     * Creates a new [TimelineQueueEditor] with a given mediaSourceFactory.
     *
     * @param mediaController A [MediaControllerCompat] to read the current queue.
     * @param queueDataAdapter A [QueueDataAdapter] to change the backing data.
     * @param mediaDescriptionConverter The [MediaDescriptionConverter] for converting media
     * descriptions to [MediaItems][MediaItem].
     * @param equalityChecker The [MediaDescriptionEqualityChecker] to match queue items.
     */
    override fun onAddQueueItem(player: Player?, description: MediaDescriptionCompat?) {
        onAddQueueItem(player, description, player!!.currentTimeline.windowCount)
    }

    override fun onAddQueueItem(player: Player?, description: MediaDescriptionCompat?, index: Int) {
        val mediaItem = mediaDescriptionConverter.convert(description)
        if (mediaItem != null) {
            queueDataAdapter.add(index, description)
            player!!.addMediaItem(index, mediaItem)
        }
    }

    override fun onRemoveQueueItem(player: Player?, description: MediaDescriptionCompat) {
        val queue = mediaController.queue
        for (i in queue.indices) {
            if (equalityChecker.equals(queue[i].description, description)) {
                queueDataAdapter.remove(i)
                player!!.removeMediaItem(i)
                return
            }
        }
    }

    // CommandReceiver implementation.
    override fun onCommand(
            player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
        if (COMMAND_MOVE_QUEUE_ITEM != command || extras == null) {
            return false
        }
        val from = extras.getInt(EXTRA_FROM_INDEX, C.INDEX_UNSET)
        val to = extras.getInt(EXTRA_TO_INDEX, C.INDEX_UNSET)
        if (from != C.INDEX_UNSET && to != C.INDEX_UNSET) {
            queueDataAdapter.move(from, to)
            player.moveMediaItem(from, to)
        }
        return true
    }

    companion object {
        const val COMMAND_MOVE_QUEUE_ITEM = "exo_move_window"
        const val EXTRA_FROM_INDEX = "from_index"
        const val EXTRA_TO_INDEX = "to_index"
    }
}