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
import androidx.annotation.FloatRange
import androidx.annotation.GuardedBy
import androidx.annotation.IntRange
import androidx.media.AudioAttributesCompat
import androidx.media2.common.*
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * An implementation of [SessionPlayer] that wraps a given ExoPlayer [Player] instance.
 *
 *
 * Internally this implementation posts operations to and receives callbacks on the thread
 * associated with [Player.getApplicationLooper], so it is important not to block this
 * thread. In particular, when awaiting the result of an asynchronous session player operation, apps
 * should generally use [ListenableFuture.addListener] to be notified of
 * completion, rather than calling the blocking [ListenableFuture.get] method.
 */
class SessionPlayerConnector @JvmOverloads constructor(player: Player, mediaItemConverter: MediaItemConverter = DefaultMediaItemConverter()) : SessionPlayer() {
    private val stateLock = Any()
    private val taskHandler: Handler
    private val taskHandlerExecutor: Executor
    private val player: PlayerWrapper
    private val playerCommandQueue: PlayerCommandQueue

    @GuardedBy("stateLock")
    private val mediaItemToBuffState: MutableMap<MediaItem, Int> = HashMap()

    @GuardedBy("stateLock")
    /* @PlayerState */
    private var state: Int

    @GuardedBy("stateLock")
    private var closed = false

    // Should be only accessed on the executor, which is currently single-threaded.
    private var currentMediaItem: MediaItem? = null
    /**
     * Creates an instance.
     *
     * @param player The player to wrap.
     * @param mediaItemConverter The [MediaItemConverter].
     */
    /**
     * Creates an instance using [DefaultMediaItemConverter] to convert between ExoPlayer and
     * media2 MediaItems.
     *
     * @param player The player to wrap.
     */
    init {
        Assertions.checkNotNull(player)
        Assertions.checkNotNull(mediaItemConverter)
        state = PLAYER_STATE_IDLE
        taskHandler = Handler(player.applicationLooper)
        taskHandlerExecutor = Executor { runnable: Runnable? -> Util.postOrRun(taskHandler, runnable!!) }
        this.player = PlayerWrapper(ExoPlayerWrapperListener(), player, mediaItemConverter)
        playerCommandQueue = PlayerCommandQueue(this.player, taskHandler)
    }

    override fun play(): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PLAY) { player.play() }
    }

    override fun pause(): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PAUSE) { player.pause() }
    }

    override fun prepare(): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PREPARE) { player.prepare() }
    }

    override fun seekTo(position: Long): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SEEK_TO,  /* command= */
                { player.seekTo(position) },  /* tag= */
                position)
    }

    override fun setPlaybackSpeed(
            @FloatRange(from = 0.0f, to = Float.MAX_VALUE.toDouble(), fromInclusive = false) playbackSpeed: Float): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(playbackSpeed > 0f)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_SPEED
        )  /* command= */
        {
            player.playbackSpeed = playbackSpeed
            true
        }
    }

    override fun setAudioAttributes(attr: AudioAttributesCompat): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES
        )  /* command= */
        {
            player.audioAttributes = Assertions.checkNotNull(attr)
            true
        }
    }

    /* @PlayerState */  override fun getPlayerState(): Int {
        synchronized(stateLock) { return state }
    }

    override fun getCurrentPosition(): Long {
        val position = runPlayerCallableBlocking({ player.currentPosition },  /* defaultValueWhenException= */
                UNKNOWN_TIME)
        return if (position >= 0) position else UNKNOWN_TIME
    }

    override fun getDuration(): Long {
        val position = runPlayerCallableBlocking({ player.duration },  /* defaultValueWhenException= */UNKNOWN_TIME)
        return if (position >= 0) position else UNKNOWN_TIME
    }

    override fun getBufferedPosition(): Long {
        val position = runPlayerCallableBlocking({ player.bufferedPosition },  /* defaultValueWhenException= */
                UNKNOWN_TIME)
        return if (position >= 0) position else UNKNOWN_TIME
    }

    /* @BuffState */  override fun getBufferingState(): Int {
        val mediaItem = this.runPlayerCallableBlocking({ player.currentMediaItem },  /* defaultValueWhenException= */null)
                ?: return BUFFERING_STATE_UNKNOWN
        var buffState: Int?
        synchronized(stateLock) { buffState = mediaItemToBuffState[mediaItem] }
        return if (buffState == null) BUFFERING_STATE_UNKNOWN else buffState!!
    }

    @FloatRange(from = 0.0f, to = Float.MAX_VALUE.toDouble(), fromInclusive = false)
    override fun getPlaybackSpeed(): Float {
        return runPlayerCallableBlocking({ player.playbackSpeed },  /* defaultValueWhenException= */1.0f)
    }

    override fun getAudioAttributes(): AudioAttributesCompat? {
        return runPlayerCallableBlockingWithNullOnException { player.audioAttributes }
    }

    /**
     * {@inheritDoc}
     *
     *
     * [FileMediaItem] and [CallbackMediaItem] are not supported.
     */
    override fun setMediaItem(item: MediaItem): ListenableFuture<PlayerResult> {
        Assertions.checkNotNull(item)
        Assertions.checkArgument(item !is FileMediaItem)
        Assertions.checkArgument(item !is CallbackMediaItem)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM) { player.setMediaItem(item) }
    }

    /**
     * {@inheritDoc}
     *
     *
     * [FileMediaItem] and [CallbackMediaItem] are not supported.
     */
    override fun setPlaylist(
            playlist: List<MediaItem>, metadata: MediaMetadata?): ListenableFuture<PlayerResult> {
        Assertions.checkNotNull(playlist)
        Assertions.checkArgument(!playlist.isEmpty())
        for (i in playlist.indices) {
            val item = playlist[i]
            Assertions.checkNotNull(item)
            Assertions.checkArgument(item !is FileMediaItem)
            Assertions.checkArgument(item !is CallbackMediaItem)
            for (j in 0 until i) {
                Assertions.checkArgument(
                        item !== playlist[j],
                        "playlist shouldn't contain duplicated item, index=$i vs index=$j")
            }
        }
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_PLAYLIST
        )  /* command= */
        { player.setPlaylist(playlist, metadata) }
    }

    /**
     * {@inheritDoc}
     *
     *
     * [FileMediaItem] and [CallbackMediaItem] are not supported.
     */
    override fun addPlaylistItem(index: Int, item: MediaItem): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(index >= 0)
        Assertions.checkNotNull(item)
        Assertions.checkArgument(item !is FileMediaItem)
        Assertions.checkArgument(item !is CallbackMediaItem)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM
        )  /* command= */
        { player.addPlaylistItem(index, item) }
    }

    override fun removePlaylistItem(@IntRange(from = 0) index: Int): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(index >= 0)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM
        )  /* command= */
        { player.removePlaylistItem(index) }
    }

    /**
     * {@inheritDoc}
     *
     *
     * [FileMediaItem] and [CallbackMediaItem] are not supported.
     */
    override fun replacePlaylistItem(index: Int, item: MediaItem): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(index >= 0)
        Assertions.checkNotNull(item)
        Assertions.checkArgument(item !is FileMediaItem)
        Assertions.checkArgument(item !is CallbackMediaItem)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM
        )  /* command= */
        { player.replacePlaylistItem(index, item) }
    }

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(fromIndex >= 0)
        Assertions.checkArgument(toIndex >= 0)
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM
        )  /* command= */
        { player.movePlaylistItem(fromIndex, toIndex) }
    }

    override fun skipToPreviousPlaylistItem(): ListenableFuture<PlayerResult> {
        val result: ListenableFuture<PlayerResult?> = playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM) { player.skipToPreviousPlaylistItem() }
        result.addListener({ notifySkipToCompletedOnHandler() }, taskHandlerExecutor)
        return result
    }

    override fun skipToNextPlaylistItem(): ListenableFuture<PlayerResult> {
        val result: ListenableFuture<PlayerResult?> = playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM) { player.skipToNextPlaylistItem() }
        result.addListener({ notifySkipToCompletedOnHandler() }, taskHandlerExecutor)
        return result
    }

    override fun skipToPlaylistItem(@IntRange(from = 0) index: Int): ListenableFuture<PlayerResult> {
        Assertions.checkArgument(index >= 0)
        val result: ListenableFuture<PlayerResult?> = playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM
        )  /* command= */
        { player.skipToPlaylistItem(index) }
        result.addListener({ notifySkipToCompletedOnHandler() }, taskHandlerExecutor)
        return result
    }

    override fun updatePlaylistMetadata(metadata: MediaMetadata?): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA
        )  /* command= */
        {
            val handled = player.updatePlaylistMetadata(metadata)
            if (handled) {
                notifySessionPlayerCallback(
                        SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onPlaylistMetadataChanged(this@SessionPlayerConnector, metadata) })
            }
            handled
        }
    }

    override fun setRepeatMode(repeatMode: Int): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_REPEAT_MODE
        )  /* command= */
        { player.setRepeatMode(repeatMode) }
    }

    override fun setShuffleMode(shuffleMode: Int): ListenableFuture<PlayerResult> {
        return playerCommandQueue.addCommand(
                PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE
        )  /* command= */
        { player.setShuffleMode(shuffleMode) }
    }

    override fun getPlaylist(): List<MediaItem>? {
        return runPlayerCallableBlockingWithNullOnException<List<MediaItem>?>(Callable<List<MediaItem?>?> { player.playlist })
    }

    override fun getPlaylistMetadata(): MediaMetadata? {
        return runPlayerCallableBlockingWithNullOnException { player.playlistMetadata }
    }

    override fun getRepeatMode(): Int {
        return runPlayerCallableBlocking({ player.repeatMode },  /* defaultValueWhenException= */REPEAT_MODE_NONE)
    }

    override fun getShuffleMode(): Int {
        return runPlayerCallableBlocking({ player.shuffleMode },  /* defaultValueWhenException= */SHUFFLE_MODE_NONE)
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return runPlayerCallableBlockingWithNullOnException { player.currentMediaItem }
    }

    override fun getCurrentMediaItemIndex(): Int {
        return runPlayerCallableBlocking({ player.currentMediaItemIndex },  /* defaultValueWhenException= */
                END_OF_PLAYLIST)
    }

    override fun getPreviousMediaItemIndex(): Int {
        return runPlayerCallableBlocking({ player.previousMediaItemIndex },  /* defaultValueWhenException= */
                END_OF_PLAYLIST)
    }

    override fun getNextMediaItemIndex(): Int {
        return runPlayerCallableBlocking({ player.nextMediaItemIndex },  /* defaultValueWhenException= */
                END_OF_PLAYLIST)
    }

    override fun close() {
        synchronized(stateLock) {
            if (closed) {
                return
            }
            closed = true
        }
        reset()
        this.runPlayerCallableBlocking<Void?> /* callable= */
        {
            player.close()
            null
        }
        super.close()
    }
    // SessionPlayerConnector-specific functions./* defaultValueWhenException= */
    /**
     * Returns whether the current media item is seekable.
     *
     * @return `true` if supported. `false` otherwise.
     */
    /* package */
    val isCurrentMediaItemSeekable: Boolean
        get() = runPlayerCallableBlocking({ player.isCurrentMediaItemSeekable },  /* defaultValueWhenException= */false)

    /**
     * Returns whether [.skipToPlaylistItem] is supported.
     *
     * @return `true` if supported. `false` otherwise.
     */
    /* package */
    fun canSkipToPlaylistItem(): Boolean {
        return runPlayerCallableBlocking({ player.canSkipToPlaylistItem() },  /* defaultValueWhenException= */false)
    }

    /**
     * Returns whether [.skipToPreviousPlaylistItem] is supported.
     *
     * @return `true` if supported. `false` otherwise.
     */
    /* package */
    fun canSkipToPreviousPlaylistItem(): Boolean {
        return runPlayerCallableBlocking({ player.canSkipToPreviousPlaylistItem() },  /* defaultValueWhenException= */
                false)
    }

    /**
     * Returns whether [.skipToNextPlaylistItem] is supported.
     *
     * @return `true` if supported. `false` otherwise.
     */
    /* package */
    fun canSkipToNextPlaylistItem(): Boolean {
        return runPlayerCallableBlocking({ player.canSkipToNextPlaylistItem() },  /* defaultValueWhenException= */false)
    }

    /**
     * Resets [SessionPlayerConnector] to its uninitialized state if not closed. After calling
     * this method, you will have to initialize it again by setting the media item and calling [ ][.prepare].
     *
     *
     * Note that if the player is closed, there is no way to reuse the instance.
     */
    private fun reset() {
        // Cancel the pending commands.
        playerCommandQueue.reset()
        synchronized(stateLock) {
            state = PLAYER_STATE_IDLE
            mediaItemToBuffState.clear()
        }
        this.runPlayerCallableBlocking<Void?> /* callable= */
        {
            player.reset()
            null
        }
    }

    private fun setState( /* @PlayerState */
            state: Int) {
        var needToNotify = false
        synchronized(stateLock) {
            if (this.state != state) {
                this.state = state
                needToNotify = true
            }
        }
        if (needToNotify) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onPlayerStateChanged(this@SessionPlayerConnector, state) })
        }
    }

    private fun setBufferingState(item: MediaItem,  /* @BuffState */
                                  state: Int) {
        var previousState: Int?
        synchronized(stateLock) { previousState = mediaItemToBuffState.put(item, state) }
        if (previousState == null || previousState != state) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onBufferingStateChanged(this@SessionPlayerConnector, item, state) })
        }
    }

    private fun notifySessionPlayerCallback(notifier: SessionPlayerCallbackNotifier) {
        synchronized(stateLock) {
            if (closed) {
                return
            }
        }
        val callbacks = callbacks
        for (pair in callbacks) {
            val callback = Assertions.checkNotNull(pair.first)
            val executor = Assertions.checkNotNull(pair.second)
            executor.execute { notifier.callCallback(callback) }
        }
    }

    // TODO(internal b/160846312): Remove this suppress warnings and call onCurrentMediaItemChanged
    // with a null item once we depend on media2 1.2.0.
    private fun handlePlaylistChangedOnHandler() {
        val currentPlaylist: List<MediaItem?>? = player.playlist
        val playlistMetadata = player.playlistMetadata
        val currentMediaItem = player.currentMediaItem
        val notifyCurrentMediaItem = !Util.areEqual(this.currentMediaItem, currentMediaItem) && currentMediaItem != null
        this.currentMediaItem = currentMediaItem
        val currentPosition = currentPosition
        notifySessionPlayerCallback(
                SessionPlayerCallbackNotifier { callback: PlayerCallback ->
                    callback.onPlaylistChanged(
                            this@SessionPlayerConnector, currentPlaylist, playlistMetadata)
                    if (notifyCurrentMediaItem) {
                        callback.onCurrentMediaItemChanged(this@SessionPlayerConnector, currentMediaItem)
                    }
                })
    }

    private fun notifySkipToCompletedOnHandler() {
        val currentMediaItem = Assertions.checkNotNull(player.currentMediaItem)
        if (Util.areEqual(this.currentMediaItem, currentMediaItem)) {
            return
        }
        this.currentMediaItem = currentMediaItem
        val currentPosition = currentPosition
        notifySessionPlayerCallback(
                SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onCurrentMediaItemChanged(this@SessionPlayerConnector, currentMediaItem) })
    }

    private fun <T> runPlayerCallableBlocking(callable: Callable<T>): T {
        val future = SettableFuture.create<T>()
        val success = Util.postOrRun(
                taskHandler
        ) {
            try {
                future.set(callable.call())
            } catch (e: Throwable) {
                future.setException(e)
            }
        }
        Assertions.checkState(success)
        var wasInterrupted = false
        try {
            while (true) {
                wasInterrupted = try {
                    return future.get()
                } catch (e: InterruptedException) {
                    // We always wait for player calls to return.
                    true
                } catch (e: ExecutionException) {
                    if (DEBUG) {
                        Log.d(TAG, "Internal player error", e)
                    }
                    throw IllegalStateException(e.cause)
                }
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun <T> runPlayerCallableBlockingWithNullOnException(callable: Callable<T>): T? {
        return try {
            runPlayerCallableBlocking(callable)
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> runPlayerCallableBlocking(callable: Callable<T>, defaultValueWhenException: T): T {
        return try {
            runPlayerCallableBlocking(callable)
        } catch (e: Exception) {
            defaultValueWhenException
        }
    }

    private interface SessionPlayerCallbackNotifier {
        fun callCallback(callback: PlayerCallback?)
    }

    private inner class ExoPlayerWrapperListener : PlayerWrapper.Listener {
        override fun onPlayerStateChanged(playerState: Int) {
            setState(playerState)
            if (playerState == PLAYER_STATE_PLAYING) {
                playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PLAY)
            } else if (playerState == PLAYER_STATE_PAUSED) {
                playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PAUSE)
            }
        }

        override fun onPrepared(mediaItem: MediaItem, bufferingPercentage: Int) {
            Assertions.checkNotNull(mediaItem)
            if (bufferingPercentage >= 100) {
                setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE)
            } else {
                setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_PLAYABLE)
            }
            playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.Companion.COMMAND_CODE_PLAYER_PREPARE)
        }

        override fun onSeekCompleted() {
            val currentPosition = currentPosition
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onSeekCompleted(this@SessionPlayerConnector, currentPosition) })
        }

        override fun onBufferingStarted(mediaItem: MediaItem) {
            setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_STARVED)
        }

        override fun onBufferingUpdate(mediaItem: MediaItem, bufferingPercentage: Int) {
            if (bufferingPercentage >= 100) {
                setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE)
            }
        }

        override fun onBufferingEnded(mediaItem: MediaItem, bufferingPercentage: Int) {
            if (bufferingPercentage >= 100) {
                setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE)
            } else {
                setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_PLAYABLE)
            }
        }

        override fun onCurrentMediaItemChanged(mediaItem: MediaItem?) {
            if (Util.areEqual(currentMediaItem, mediaItem)) {
                return
            }
            currentMediaItem = mediaItem
            val currentPosition = currentPosition
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onCurrentMediaItemChanged(this@SessionPlayerConnector, mediaItem) })
        }

        override fun onPlaybackEnded() {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onPlaybackCompleted(this@SessionPlayerConnector) })
        }

        override fun onError(mediaItem: MediaItem?) {
            playerCommandQueue.notifyCommandError()
            if (mediaItem != null) {
                setBufferingState(mediaItem, BUFFERING_STATE_UNKNOWN)
            }
        }

        override fun onPlaylistChanged() {
            handlePlaylistChangedOnHandler()
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onShuffleModeChanged(this@SessionPlayerConnector, shuffleMode) })
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onRepeatModeChanged(this@SessionPlayerConnector, repeatMode) })
        }

        override fun onPlaybackSpeedChanged(playbackSpeed: Float) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onPlaybackSpeedChanged(this@SessionPlayerConnector, playbackSpeed) })
        }

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributesCompat?) {
            notifySessionPlayerCallback(
                    SessionPlayerCallbackNotifier { callback: PlayerCallback -> callback.onAudioAttributesChanged(this@SessionPlayerConnector, audioAttributes) })
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.media2")
        }

        private const val TAG = "SessionPlayerConnector"
        private const val DEBUG = false
        private const val END_OF_PLAYLIST = -1
    }
}