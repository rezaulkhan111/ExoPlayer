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
package com.google.android.exoplayer2.ext.leanback

import android.content.Context
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import androidx.leanback.R
import androidx.leanback.media.PlaybackGlueHost
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.media.SurfaceHolderGlueHost
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize

/** Leanback `PlayerAdapter` implementation for [Player].  */
class LeanbackPlayerAdapter(private val context: Context, private val player: Player, private val updatePeriodMs: Int) : PlayerAdapter(), Runnable {
    private val handler: Handler
    private val playerListener: PlayerListener
    private var errorMessageProvider: ErrorMessageProvider<in PlaybackException>? = null
    private var surfaceHolderGlueHost: SurfaceHolderGlueHost? = null
    private var hasSurface = false
    private var lastNotifiedPreparedState = false

    /**
     * Builds an instance. Note that the `PlayerAdapter` does not manage the lifecycle of the
     * [Player] instance. The caller remains responsible for releasing the player when it's no
     * longer required.
     *
     * @param context The current [Context] (activity).
     * @param player The [Player] being used.
     * @param updatePeriodMs The delay between player control updates, in milliseconds.
     */
    init {
        handler = Util.createHandlerForCurrentOrMainLooper()
        playerListener = PlayerListener()
    }

    /**
     * Sets the optional [ErrorMessageProvider].
     *
     * @param errorMessageProvider The [ErrorMessageProvider].
     */
    fun setErrorMessageProvider(
            errorMessageProvider: ErrorMessageProvider<in PlaybackException>?) {
        this.errorMessageProvider = errorMessageProvider
    }

    // PlayerAdapter implementation.
    override fun onAttachedToHost(host: PlaybackGlueHost) {
        if (host is SurfaceHolderGlueHost) {
            surfaceHolderGlueHost = host
            surfaceHolderGlueHost!!.setSurfaceHolderCallback(playerListener)
        }
        notifyStateChanged()
        player.addListener(playerListener)
    }

    // dereference of possibly-null reference callback
    override fun onDetachedFromHost() {
        player.removeListener(playerListener)
        if (surfaceHolderGlueHost != null) {
            removeSurfaceHolderCallback(surfaceHolderGlueHost!!)
            surfaceHolderGlueHost = null
        }
        hasSurface = false
        val callback = callback
        callback.onBufferingStateChanged(this, false)
        callback.onPlayStateChanged(this)
        maybeNotifyPreparedStateChanged(callback)
    }

    override fun setProgressUpdatingEnabled(enabled: Boolean) {
        handler.removeCallbacks(this)
        if (enabled) {
            handler.post(this)
        }
    }

    override fun isPlaying(): Boolean {
        val playbackState = player.playbackState
        return playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED && player.playWhenReady
    }

    override fun getDuration(): Long {
        val durationMs = player.duration
        return if (durationMs == C.TIME_UNSET) -1 else durationMs
    }

    override fun getCurrentPosition(): Long {
        return if (player.playbackState == Player.STATE_IDLE) -1 else player.currentPosition
    }

    // dereference of possibly-null reference getCallback()
    override fun play() {
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekToDefaultPosition(player.currentMediaItemIndex)
        }
        if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            player.play()
            callback.onPlayStateChanged(this)
        }
    }

    // dereference of possibly-null reference getCallback()
    override fun pause() {
        if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            player.pause()
            callback.onPlayStateChanged(this)
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(player.currentMediaItemIndex, positionMs)
    }

    override fun getBufferedPosition(): Long {
        return player.bufferedPosition
    }

    override fun isPrepared(): Boolean {
        return (player.playbackState != Player.STATE_IDLE
                && (surfaceHolderGlueHost == null || hasSurface))
    }

    // Runnable implementation.
    // dereference of possibly-null reference callback
    override fun run() {
        val callback = callback
        callback.onCurrentPositionChanged(this)
        callback.onBufferedPositionChanged(this)
        handler.postDelayed(this, updatePeriodMs.toLong())
    }

    // Internal methods.
    /* package */ // incompatible argument for parameter callback of maybeNotifyPreparedStateChanged.
    fun setVideoSurface(surface: Surface?) {
        hasSurface = surface != null
        player.setVideoSurface(surface)
        maybeNotifyPreparedStateChanged(callback)
    }

    /* package */ // incompatible argument for parameter callback of maybeNotifyPreparedStateChanged.
    fun notifyStateChanged() {
        val playbackState = player.playbackState
        val callback = callback
        maybeNotifyPreparedStateChanged(callback)
        callback.onPlayStateChanged(this)
        callback.onBufferingStateChanged(this, playbackState == Player.STATE_BUFFERING)
        if (playbackState == Player.STATE_ENDED) {
            callback.onPlayCompleted(this)
        }
    }

    private fun maybeNotifyPreparedStateChanged(callback: Callback) {
        val isPrepared = isPrepared
        if (lastNotifiedPreparedState != isPrepared) {
            lastNotifiedPreparedState = isPrepared
            callback.onPreparedStateChanged(this)
        }
    }

    private inner class PlayerListener : Player.Listener, SurfaceHolder.Callback {
        // SurfaceHolder.Callback implementation.
        override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
            setVideoSurface(surfaceHolder.surface)
        }

        override fun surfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Do nothing.
        }

        override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
            setVideoSurface(null)
        }

        // Player.Listener implementation.
        // dereference of possibly-null reference callback
        override fun onPlayerError(error: PlaybackException) {
            val callback = callback
            if (errorMessageProvider != null) {
                val errorMessage = errorMessageProvider!!.getErrorMessage(error)
                callback.onError(this@LeanbackPlayerAdapter, errorMessage.first, errorMessage.second)
            } else {
                callback.onError(
                        this@LeanbackPlayerAdapter,
                        error.errorCode,  // This string was probably tailored for MediaPlayer, whose error callback takes two
                        // int arguments (int what, int extra). Since PlaybackException defines a single error
                        // code, we pass 0 as the extra.
                        context.getString(
                                R.string.lb_media_player_error,  /* formatArgs...= */error.errorCode, 0))
            }
        }

        // dereference of possibly-null reference callback
        override fun onTimelineChanged(timeline: Timeline, reason: @TimelineChangeReason Int) {
            val callback = callback
            callback.onDurationChanged(this@LeanbackPlayerAdapter)
            callback.onCurrentPositionChanged(this@LeanbackPlayerAdapter)
            callback.onBufferedPositionChanged(this@LeanbackPlayerAdapter)
        }

        // dereference of possibly-null reference callback
        override fun onPositionDiscontinuity(
                oldPosition: PositionInfo,
                newPosition: PositionInfo,
                reason: @DiscontinuityReason Int) {
            val callback = callback
            callback.onCurrentPositionChanged(this@LeanbackPlayerAdapter)
            callback.onBufferedPositionChanged(this@LeanbackPlayerAdapter)
        }

        // dereference of possibly-null reference getCallback()
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // There's no way to pass pixelWidthHeightRatio to leanback, so we scale the width that we
            // pass to take it into account. This is necessary to ensure that leanback uses the correct
            // aspect ratio when playing content with non-square pixels.
            val scaledWidth = Math.round(videoSize.width * videoSize.pixelWidthHeightRatio)
            callback.onVideoSizeChanged(this@LeanbackPlayerAdapter, scaledWidth, videoSize.height)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                notifyStateChanged()
            }
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.leanback")
        }

        private fun removeSurfaceHolderCallback(surfaceHolderGlueHost: SurfaceHolderGlueHost) {
            surfaceHolderGlueHost.setSurfaceHolderCallback(null)
        }
    }
}