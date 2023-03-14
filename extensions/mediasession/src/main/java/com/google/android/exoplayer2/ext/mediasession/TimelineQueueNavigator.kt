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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator
import com.google.android.exoplayer2.util.Assertions
import java.util.*

/**
 * An abstract implementation of the [MediaSessionConnector.QueueNavigator] that maps the
 * windows of a [Player]'s [Timeline] to the media session queue.
 */
abstract class TimelineQueueNavigator @JvmOverloads constructor(mediaSession: MediaSessionCompat, maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE) : QueueNavigator {
    private val mediaSession: MediaSessionCompat
    private val window: Timeline.Window
    private val maxQueueSize: Int
    private var activeQueueItemId: Long
    /**
     * Creates an instance for a given [MediaSessionCompat] and maximum queue size.
     *
     *
     * If the number of windows in the [Player]'s [Timeline] exceeds `maxQueueSize`, the media session queue will correspond to `maxQueueSize` windows centered
     * on the one currently being played.
     *
     * @param mediaSession The [MediaSessionCompat].
     * @param maxQueueSize The maximum queue size.
     */
    /**
     * Creates an instance for a given [MediaSessionCompat].
     *
     *
     * Equivalent to `TimelineQueueNavigator(mediaSession, DEFAULT_MAX_QUEUE_SIZE)`.
     *
     * @param mediaSession The [MediaSessionCompat].
     */
    init {
        Assertions.checkState(maxQueueSize > 0)
        this.mediaSession = mediaSession
        this.maxQueueSize = maxQueueSize
        activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        window = Timeline.Window()
    }

    /**
     * Gets the [MediaDescriptionCompat] for a given timeline window index.
     *
     *
     * Often artworks and icons need to be loaded asynchronously. In such a case, return a [ ] without the images, load your images asynchronously off the main thread
     * and then call [MediaSessionConnector.invalidateMediaSessionQueue] to make the connector
     * update the queue by calling this method again.
     *
     * @param player The current player.
     * @param windowIndex The timeline window index for which to provide a description.
     * @return A [MediaDescriptionCompat].
     */
    abstract fun getMediaDescription(player: Player?, windowIndex: Int): MediaDescriptionCompat?
    override fun getSupportedQueueNavigatorActions(player: Player): Long {
        var enableSkipTo = false
        var enablePrevious = false
        var enableNext = false
        val timeline = player.currentTimeline
        if (!timeline.isEmpty && !player.isPlayingAd) {
            timeline.getWindow(player.currentMediaItemIndex, window)
            enableSkipTo = timeline.windowCount > 1
            enablePrevious = (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    || !window.isLive()
                    || player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            enableNext = (window.isLive() && window.isDynamic
                    || player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        }
        var actions: Long = 0
        if (enableSkipTo) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        }
        if (enablePrevious) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }
        if (enableNext) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        return actions
    }

    override fun onTimelineChanged(player: Player) {
        publishFloatingQueueWindow(player)
    }

    override fun onCurrentMediaItemIndexChanged(player: Player) {
        if (activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                || player.currentTimeline.windowCount > maxQueueSize) {
            publishFloatingQueueWindow(player)
        } else if (!player.currentTimeline.isEmpty) {
            activeQueueItemId = player.currentMediaItemIndex.toLong()
        }
    }

    override fun getActiveQueueItemId(player: Player?): Long {
        return activeQueueItemId
    }

    override fun onSkipToPrevious(player: Player?) {
        player!!.seekToPrevious()
    }

    override fun onSkipToQueueItem(player: Player?, id: Long) {
        val timeline = player!!.currentTimeline
        if (timeline.isEmpty || player.isPlayingAd) {
            return
        }
        val windowIndex = id.toInt()
        if (0 <= windowIndex && windowIndex < timeline.windowCount) {
            player.seekToDefaultPosition(windowIndex)
        }
    }

    override fun onSkipToNext(player: Player?) {
        player!!.seekToNext()
    }

    // CommandReceiver implementation.
    override fun onCommand(
            player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
        return false
    }

    // Helper methods.
    private fun publishFloatingQueueWindow(player: Player) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            mediaSession.setQueue(emptyList())
            activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            return
        }
        val queue = ArrayDeque<MediaSessionCompat.QueueItem>()
        val queueSize = Math.min(maxQueueSize, timeline.windowCount)

        // Add the active queue item.
        val currentMediaItemIndex = player.currentMediaItemIndex
        queue.add(
                MediaSessionCompat.QueueItem(
                        getMediaDescription(player, currentMediaItemIndex), currentMediaItemIndex.toLong()))

        // Fill queue alternating with next and/or previous queue items.
        var firstMediaItemIndex = currentMediaItemIndex
        var lastMediaItemIndex = currentMediaItemIndex
        val shuffleModeEnabled = player.shuffleModeEnabled
        while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET)
                && queue.size < queueSize) {
            // Begin with next to have a longer tail than head if an even sized queue needs to be trimmed.
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                lastMediaItemIndex = timeline.getNextWindowIndex(
                        lastMediaItemIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
                if (lastMediaItemIndex != C.INDEX_UNSET) {
                    queue.add(
                            MediaSessionCompat.QueueItem(
                                    getMediaDescription(player, lastMediaItemIndex), lastMediaItemIndex.toLong()))
                }
            }
            if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
                firstMediaItemIndex = timeline.getPreviousWindowIndex(
                        firstMediaItemIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
                if (firstMediaItemIndex != C.INDEX_UNSET) {
                    queue.addFirst(
                            MediaSessionCompat.QueueItem(
                                    getMediaDescription(player, firstMediaItemIndex), firstMediaItemIndex.toLong()))
                }
            }
        }
        mediaSession.setQueue(ArrayList(queue))
        activeQueueItemId = currentMediaItemIndex.toLong()
    }

    companion object {
        const val DEFAULT_MAX_QUEUE_SIZE = 10
    }
}