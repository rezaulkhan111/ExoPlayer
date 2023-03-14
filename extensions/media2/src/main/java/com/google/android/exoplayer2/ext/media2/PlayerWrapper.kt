/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2

import android.os.Handler
import androidx.annotation.IntRange
import androidx.media.AudioAttributesCompat
import androidx.media2.common.*
import androidx.media2.common.MediaMetadata
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import java.io.IOException

/**
 * Wraps an ExoPlayer [Player] instance and provides methods and notifies events like those in
 * the [SessionPlayer] API.
 */
/* package */
internal class PlayerWrapper(private val listener: Listener, private val player: Player, private val mediaItemConverter: MediaItemConverter) {
    /** Listener for player wrapper events.  */
    interface Listener {
        /**
         * Called when the player state is changed.
         *
         *
         * This method will be called at first if multiple events should be notified at once.
         */
        fun onPlayerStateChanged( /* @SessionPlayer.PlayerState */
                playerState: Int)

        /** Called when the player is prepared.  */
        fun onPrepared(media2MediaItem: androidx.media2.common.MediaItem, bufferingPercentage: Int)

        /** Called when a seek request has completed.  */
        fun onSeekCompleted()

        /** Called when the player starts buffering.  */
        fun onBufferingStarted(media2MediaItem: androidx.media2.common.MediaItem)

        /** Called when the player becomes ready again after buffering started.  */
        fun onBufferingEnded(
                media2MediaItem: androidx.media2.common.MediaItem, bufferingPercentage: Int)

        /** Called periodically with the player's buffered position as a percentage.  */
        fun onBufferingUpdate(
                media2MediaItem: androidx.media2.common.MediaItem, bufferingPercentage: Int)

        /** Called when current media item is changed.  */
        fun onCurrentMediaItemChanged(media2MediaItem: androidx.media2.common.MediaItem?)

        /** Called when playback of the item list has ended.  */
        fun onPlaybackEnded()

        /** Called when the player encounters an error.  */
        fun onError(media2MediaItem: androidx.media2.common.MediaItem?)

        /** Called when the playlist is changed.  */
        fun onPlaylistChanged()

        /** Called when the shuffle mode is changed.  */
        fun onShuffleModeChanged(shuffleMode: Int)

        /** Called when the repeat mode is changed.  */
        fun onRepeatModeChanged(repeatMode: Int)

        /** Called when the audio attributes is changed.  */
        fun onAudioAttributesChanged(audioAttributes: AudioAttributesCompat?)

        /** Called when the playback speed is changed.  */
        fun onPlaybackSpeedChanged(playbackSpeed: Float)
    }

    private val handler: Handler
    private val pollBufferRunnable: Runnable
    private val componentListener: ComponentListener
    var playlistMetadata: MediaMetadata? = null
        private set

    // These should be only updated in TimelineChanges.
    private val media2Playlist: MutableList<androidx.media2.common.MediaItem>
    private val exoPlayerPlaylist: MutableList<MediaItem>
    private var sessionPlayerState: Int
    private var prepared: Boolean
    private var bufferingItem: androidx.media2.common.MediaItem? = null
    private var currentWindowIndex: Int
    private var ignoreTimelineUpdates = false

    /**
     * Creates a new ExoPlayer wrapper.
     *
     * @param listener A [Listener].
     * @param player The [Player].
     * @param mediaItemConverter The [MediaItemConverter].
     */
    init {
        componentListener = ComponentListener()
        player.addListener(componentListener)
        handler = Handler(player.applicationLooper)
        pollBufferRunnable = PollBufferRunnable()
        media2Playlist = ArrayList()
        exoPlayerPlaylist = ArrayList()
        currentWindowIndex = C.INDEX_UNSET
        updatePlaylist(player.currentTimeline)
        sessionPlayerState = evaluateSessionPlayerState()
        val playbackState = player.playbackState
        prepared = playbackState != Player.STATE_IDLE
        if (playbackState == Player.STATE_BUFFERING) {
            bufferingItem = currentMediaItem
        }
    }

    fun setMediaItem(media2MediaItem: androidx.media2.common.MediaItem): Boolean {
        return setPlaylist(listOf(media2MediaItem),  /* metadata= */null)
    }

    fun setPlaylist(
            playlist: List<androidx.media2.common.MediaItem>, metadata: MediaMetadata?): Boolean {
        // Check for duplication.
        for (i in playlist.indices) {
            val media2MediaItem = playlist[i]
            Assertions.checkArgument(playlist.indexOf(media2MediaItem) == i)
        }
        playlistMetadata = metadata
        val exoPlayerMediaItems: MutableList<MediaItem> = ArrayList()
        for (i in playlist.indices) {
            val media2MediaItem = playlist[i]
            val exoPlayerMediaItem = Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem))
            exoPlayerMediaItems.add(exoPlayerMediaItem)
        }
        player.setMediaItems(exoPlayerMediaItems,  /* resetPosition= */true)
        currentWindowIndex = currentMediaItemIndex
        return true
    }

    fun addPlaylistItem(index: Int, media2MediaItem: androidx.media2.common.MediaItem): Boolean {
        var index = index
        Assertions.checkArgument(!media2Playlist.contains(media2MediaItem))
        index = Util.constrainValue(index, 0, media2Playlist.size)
        val exoPlayerMediaItem = Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem))
        player.addMediaItem(index, exoPlayerMediaItem)
        return true
    }

    fun removePlaylistItem(@IntRange(from = 0) index: Int): Boolean {
        if (player.mediaItemCount <= index) {
            return false
        }
        player.removeMediaItem(index)
        return true
    }

    fun replacePlaylistItem(index: Int, media2MediaItem: androidx.media2.common.MediaItem): Boolean {
        var index = index
        Assertions.checkArgument(!media2Playlist.contains(media2MediaItem))
        index = Util.constrainValue(index, 0, media2Playlist.size)
        val exoPlayerMediaItemToAdd = Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem))
        ignoreTimelineUpdates = true
        player.removeMediaItem(index)
        ignoreTimelineUpdates = false
        player.addMediaItem(index, exoPlayerMediaItemToAdd)
        return true
    }

    fun movePlaylistItem(
            @IntRange(from = 0) fromIndex: Int, @IntRange(from = 0) toIndex: Int): Boolean {
        val itemCount = player.mediaItemCount
        if (!(fromIndex < itemCount && toIndex < itemCount)) {
            return false
        }
        if (fromIndex == toIndex) {
            return true
        }
        player.moveMediaItem(fromIndex, toIndex)
        return true
    }

    fun skipToPreviousPlaylistItem(): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
            return false
        }
        player.seekToPrevious()
        return true
    }

    fun skipToNextPlaylistItem(): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
            return false
        }
        player.seekToNext()
        return true
    }

    fun skipToPlaylistItem(@IntRange(from = 0) index: Int): Boolean {
        val timeline = player.currentTimeline
        Assertions.checkState(!timeline.isEmpty)
        // Use checkState() instead of checkIndex() for throwing IllegalStateException.
        // checkIndex() throws IndexOutOfBoundsException which maps the RESULT_ERROR_BAD_VALUE
        // but RESULT_ERROR_INVALID_STATE with IllegalStateException is expected here.
        Assertions.checkState(0 <= index && index < timeline.windowCount)
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == index || !player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
            return false
        }
        player.seekToDefaultPosition(index)
        return true
    }

    fun updatePlaylistMetadata(metadata: MediaMetadata?): Boolean {
        playlistMetadata = metadata
        return true
    }

    fun setRepeatMode(repeatMode: Int): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
            return false
        }
        player.repeatMode = Utils.getExoPlayerRepeatMode(repeatMode)
        return true
    }

    fun setShuffleMode(shuffleMode: Int): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
            return false
        }
        player.shuffleModeEnabled = Utils.getExoPlayerShuffleMode(shuffleMode)
        return true
    }

    val playlist: List<androidx.media2.common.MediaItem>?
        get() = ArrayList(media2Playlist)
    val repeatMode: Int
        get() = Utils.getRepeatMode(player.repeatMode)
    val shuffleMode: Int
        get() = Utils.getShuffleMode(player.shuffleModeEnabled)
    val currentMediaItemIndex: Int
        get() = if (media2Playlist.isEmpty()) C.INDEX_UNSET else player.currentMediaItemIndex
    val previousMediaItemIndex: Int
        get() = player.previousWindowIndex
    val nextMediaItemIndex: Int
        get() = player.nextWindowIndex
    val currentMediaItem: androidx.media2.common.MediaItem?
        get() {
            val index = currentMediaItemIndex
            return if (index == C.INDEX_UNSET) null else media2Playlist[index]
        }

    fun prepare(): Boolean {
        if (prepared) {
            return false
        }
        player.prepare()
        return true
    }

    fun play(): Boolean {
        if (player.playbackState == Player.STATE_ENDED) {
            if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                return false
            }
            player.seekTo(player.currentMediaItemIndex,  /* positionMs= */0)
        }
        val playWhenReady = player.playWhenReady
        val suppressReason = player.playbackSuppressionReason
        if (playWhenReady && suppressReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                || !player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            return false
        }
        player.play()
        return true
    }

    fun pause(): Boolean {
        val playWhenReady = player.playWhenReady
        val suppressReason = player.playbackSuppressionReason
        if (!playWhenReady && suppressReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                || !player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            return false
        }
        player.pause()
        return true
    }

    fun seekTo(position: Long): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return false
        }
        player.seekTo(player.currentMediaItemIndex, position)
        return true
    }

    val currentPosition: Long
        get() = player.currentPosition
    val duration: Long
        get() {
            val duration = player.duration
            return if (duration == C.TIME_UNSET) SessionPlayer.UNKNOWN_TIME else duration
        }
    val bufferedPosition: Long
        get() = player.bufferedPosition

    /* @SessionPlayer.PlayerState */
    private fun evaluateSessionPlayerState(): Int {
        if (hasError()) {
            return SessionPlayer.PLAYER_STATE_ERROR
        }
        val state = player.playbackState
        val playWhenReady = player.playWhenReady
        return when (state) {
            Player.STATE_IDLE -> SessionPlayer.PLAYER_STATE_IDLE
            Player.STATE_ENDED -> if (player.currentMediaItem == null) SessionPlayer.PLAYER_STATE_IDLE else SessionPlayer.PLAYER_STATE_PAUSED
            Player.STATE_BUFFERING, Player.STATE_READY -> if (playWhenReady) SessionPlayer.PLAYER_STATE_PLAYING else SessionPlayer.PLAYER_STATE_PAUSED
            else -> throw IllegalStateException()
        }
    }

    private fun updateSessionPlayerState() {
        val newState = evaluateSessionPlayerState()
        if (sessionPlayerState != newState) {
            sessionPlayerState = newState
            listener.onPlayerStateChanged(newState)
            if (newState == SessionPlayer.PLAYER_STATE_ERROR) {
                listener.onError(currentMediaItem)
            }
        }
    }

    private fun updateBufferingState(isBuffering: Boolean) {
        if (isBuffering) {
            val curMediaItem = currentMediaItem
            if (prepared && (bufferingItem == null || bufferingItem != curMediaItem)) {
                bufferingItem = currentMediaItem
                listener.onBufferingStarted(Assertions.checkNotNull(bufferingItem))
            }
        } else if (bufferingItem != null) {
            listener.onBufferingEnded(bufferingItem!!, player.bufferedPercentage)
            bufferingItem = null
        }
    }

    private fun handlePlayerStateChanged() {
        updateSessionPlayerState()
        val playbackState = player.playbackState
        handler.removeCallbacks(pollBufferRunnable)
        when (playbackState) {
            Player.STATE_IDLE -> {
                prepared = false
                updateBufferingState( /* isBuffering= */false)
            }
            Player.STATE_BUFFERING -> {
                updateBufferingState( /* isBuffering= */true)
                Util.postOrRun(handler, pollBufferRunnable)
            }
            Player.STATE_READY -> {
                if (!prepared) {
                    prepared = true
                    handlePositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
                    listener.onPrepared(
                            Assertions.checkNotNull(currentMediaItem), player.bufferedPercentage)
                }
                updateBufferingState( /* isBuffering= */false)
                Util.postOrRun(handler, pollBufferRunnable)
            }
            Player.STATE_ENDED -> {
                if (player.currentMediaItem != null) {
                    listener.onPlaybackEnded()
                }
                player.playWhenReady = false
                updateBufferingState( /* isBuffering= */false)
            }
        }
    }

    // Player interface doesn't support setting audio attributes.
    var audioAttributes: AudioAttributesCompat?
        get() {
            var audioAttributes = AudioAttributes.DEFAULT
            if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_ATTRIBUTES)) {
                audioAttributes = player.audioAttributes
            }
            return Utils.getAudioAttributesCompat(audioAttributes)
        }
        set(audioAttributes) {
            // Player interface doesn't support setting audio attributes.
        }
    var playbackSpeed: Float
        get() = player.playbackParameters.speed
        set(playbackSpeed) {
            player.playbackParameters = PlaybackParameters(playbackSpeed)
        }

    fun reset() {
        player.stop()
        player.clearMediaItems()
        prepared = false
        bufferingItem = null
    }

    fun close() {
        handler.removeCallbacks(pollBufferRunnable)
        player.removeListener(componentListener)
    }

    val isCurrentMediaItemSeekable: Boolean
        get() = (currentMediaItem != null && !player.isPlayingAd
                && player.isCurrentMediaItemSeekable)

    fun canSkipToPlaylistItem(): Boolean {
        val playlist = playlist
        return playlist != null && playlist.size > 1
    }

    fun canSkipToPreviousPlaylistItem(): Boolean {
        return player.hasPreviousMediaItem()
    }

    fun canSkipToNextPlaylistItem(): Boolean {
        return player.hasNextMediaItem()
    }

    fun hasError(): Boolean {
        return player.playerError != null
    }

    private fun handlePositionDiscontinuity(reason: @DiscontinuityReason Int) {
        val currentWindowIndex = currentMediaItemIndex
        if (this.currentWindowIndex != currentWindowIndex) {
            this.currentWindowIndex = currentWindowIndex
            if (currentWindowIndex != C.INDEX_UNSET) {
                val currentMediaItem = Assertions.checkNotNull(currentMediaItem)
                listener.onCurrentMediaItemChanged(currentMediaItem)
            }
        } else {
            listener.onSeekCompleted()
        }
    }

    // Check whether Timeline is changed by media item changes or not
    private fun isExoPlayerMediaItemsChanged(timeline: Timeline): Boolean {
        if (exoPlayerPlaylist.size != timeline.windowCount) {
            return true
        }
        val window = Timeline.Window()
        val windowCount = timeline.windowCount
        for (i in 0 until windowCount) {
            timeline.getWindow(i, window)
            if (!Util.areEqual(exoPlayerPlaylist[i], window.mediaItem)) {
                return true
            }
        }
        return false
    }

    private fun updatePlaylist(timeline: Timeline) {
        val media2MediaItemToBeRemoved: MutableList<androidx.media2.common.MediaItem> = ArrayList(media2Playlist)
        media2Playlist.clear()
        exoPlayerPlaylist.clear()
        val window = Timeline.Window()
        val windowCount = timeline.windowCount
        for (i in 0 until windowCount) {
            timeline.getWindow(i, window)
            val exoPlayerMediaItem = window.mediaItem
            val media2MediaItem = Assertions.checkNotNull(mediaItemConverter.convertToMedia2MediaItem(exoPlayerMediaItem))
            exoPlayerPlaylist.add(exoPlayerMediaItem)
            media2Playlist.add(media2MediaItem)
            media2MediaItemToBeRemoved.remove(media2MediaItem)
        }
        for (item in media2MediaItemToBeRemoved) {
            releaseMediaItem(item)
        }
    }

    private fun updateBufferingAndScheduleNextPollBuffer() {
        val media2MediaItem = Assertions.checkNotNull(currentMediaItem)
        listener.onBufferingUpdate(media2MediaItem, player.bufferedPercentage)
        handler.removeCallbacks(pollBufferRunnable)
        handler.postDelayed(pollBufferRunnable, POLL_BUFFER_INTERVAL_MS.toLong())
    }

    private fun releaseMediaItem(media2MediaItem: androidx.media2.common.MediaItem) {
        try {
            if (media2MediaItem is CallbackMediaItem) {
                media2MediaItem.dataSourceCallback.close()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Error releasing media item $media2MediaItem", e)
        }
    }

    private inner class ComponentListener : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateSessionPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
            handlePlayerStateChanged()
        }

        override fun onPositionDiscontinuity(
                oldPosition: PositionInfo,
                newPosition: PositionInfo,
                reason: @DiscontinuityReason Int) {
            handlePositionDiscontinuity(reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            updateSessionPlayerState()
        }

        override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
            listener.onRepeatModeChanged(Utils.getRepeatMode(repeatMode))
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            listener.onShuffleModeChanged(Utils.getShuffleMode(shuffleModeEnabled))
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            listener.onPlaybackSpeedChanged(playbackParameters.speed)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (ignoreTimelineUpdates) {
                return
            }
            if (!isExoPlayerMediaItemsChanged(timeline)) {
                return
            }
            updatePlaylist(timeline)
            listener.onPlaylistChanged()
        }

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
            listener.onAudioAttributesChanged(Utils.getAudioAttributesCompat(audioAttributes))
        }
    }

    private inner class PollBufferRunnable : Runnable {
        override fun run() {
            updateBufferingAndScheduleNextPollBuffer()
        }
    }

    companion object {
        private const val TAG = "PlayerWrapper"
        private const val POLL_BUFFER_INTERVAL_MS = 1000
    }
}