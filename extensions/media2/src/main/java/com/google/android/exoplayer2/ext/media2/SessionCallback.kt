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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.media2.common.*
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.Rating
import androidx.media2.session.MediaSession
import androidx.media2.session.SessionCommand
import androidx.media2.session.SessionCommandGroup
import androidx.media2.session.SessionResult
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.*
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/* package */
internal class SessionCallback(
        sessionPlayerConnector: SessionPlayerConnector,
        fastForwardMs: Int,
        rewindMs: Int,
        seekTimeoutMs: Int,
        allowedCommandProvider: AllowedCommandProvider,
        ratingCallback: SessionCallbackBuilder.RatingCallback?,
        customCommandProvider: CustomCommandProvider?,
        mediaItemProvider: MediaItemProvider?,
        skipCallback: SkipCallback?,
        postConnectCallback: PostConnectCallback?,
        disconnectedCallback: DisconnectedCallback?) : MediaSession.SessionCallback() {
    private val sessionPlayer: SessionPlayer
    private val fastForwardMs: Int
    private val rewindMs: Int
    private val seekTimeoutMs: Int
    private val sessions: MutableSet<MediaSession>
    private val allowedCommandProvider: AllowedCommandProvider
    private val ratingCallback: SessionCallbackBuilder.RatingCallback?
    private val customCommandProvider: CustomCommandProvider?
    private val mediaItemProvider: MediaItemProvider?
    private val skipCallback: SkipCallback?
    private val postConnectCallback: PostConnectCallback?
    private val disconnectedCallback: DisconnectedCallback?
    private var loggedUnexpectedSessionPlayerWarning = false

    init {
        sessionPlayer = sessionPlayerConnector
        this.allowedCommandProvider = allowedCommandProvider
        this.ratingCallback = ratingCallback
        this.customCommandProvider = customCommandProvider
        this.mediaItemProvider = mediaItemProvider
        this.skipCallback = skipCallback
        this.postConnectCallback = postConnectCallback
        this.disconnectedCallback = disconnectedCallback
        this.fastForwardMs = fastForwardMs
        this.rewindMs = rewindMs
        this.seekTimeoutMs = seekTimeoutMs
        sessions = Collections.newSetFromMap(ConcurrentHashMap())

        // Register PlayerCallback and make it to be called before the ListenableFuture set the result.
        // It help the PlayerCallback to update allowed commands before pended Player APIs are executed.
        sessionPlayerConnector.registerPlayerCallback({ obj: Runnable -> obj.run() }, PlayerCallback())
    }

    override fun onConnect(
            session: MediaSession, controllerInfo: MediaSession.ControllerInfo): SessionCommandGroup? {
        sessions.add(session)
        if (!allowedCommandProvider.acceptConnection(session, controllerInfo)) {
            return null
        }
        val baseAllowedCommands = buildAllowedCommands(session, controllerInfo)
        return allowedCommandProvider.getAllowedCommands(session, controllerInfo, baseAllowedCommands)
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        postConnectCallback?.onPostConnect(session, controller)
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (session.connectedControllers.isEmpty()) {
            sessions.remove(session)
        }
        disconnectedCallback?.onDisconnected(session, controller)
    }

    override fun onCommandRequest(
            session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand): Int {
        return allowedCommandProvider.onCommandRequest(session, controller, command)
    }

    override fun onCreateMediaItem(
            session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String): MediaItem? {
        Assertions.checkNotNull(mediaItemProvider)
        return mediaItemProvider!!.onCreateMediaItem(session, controller, mediaId)
    }

    @SuppressLint("RestrictedApi")
    override fun onSetRating(
            session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String, rating: Rating): Int {
        return ratingCallback?.onSetRating(session, controller, mediaId, rating)
                ?: SessionResult.RESULT_ERROR_NOT_SUPPORTED
    }

    @SuppressLint("RestrictedApi")
    override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle?): SessionResult {
        return if (customCommandProvider != null) {
            customCommandProvider.onCustomCommand(session, controller, customCommand, args)!!
        } else SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED, null)
    }

    @SuppressLint("RestrictedApi")
    override fun onFastForward(session: MediaSession, controller: MediaSession.ControllerInfo): Int {
        return if (fastForwardMs > 0) {
            seekToOffset(fastForwardMs.toLong())
        } else SessionResult.RESULT_ERROR_NOT_SUPPORTED
    }

    @SuppressLint("RestrictedApi")
    override fun onRewind(session: MediaSession, controller: MediaSession.ControllerInfo): Int {
        return if (rewindMs > 0) {
            seekToOffset(-rewindMs.toLong())
        } else SessionResult.RESULT_ERROR_NOT_SUPPORTED
    }

    @SuppressLint("RestrictedApi")
    override fun onSkipBackward(session: MediaSession, controller: MediaSession.ControllerInfo): Int {
        return skipCallback?.onSkipBackward(session, controller)
                ?: SessionResult.RESULT_ERROR_NOT_SUPPORTED
    }

    @SuppressLint("RestrictedApi")
    override fun onSkipForward(session: MediaSession, controller: MediaSession.ControllerInfo): Int {
        return skipCallback?.onSkipForward(session, controller)
                ?: SessionResult.RESULT_ERROR_NOT_SUPPORTED
    }

    @SuppressLint("RestrictedApi")
    private fun seekToOffset(offsetMs: Long): Int {
        var positionMs = sessionPlayer.currentPosition + offsetMs
        val durationMs = sessionPlayer.duration
        if (durationMs != C.TIME_UNSET) {
            positionMs = Math.min(positionMs, durationMs)
        }
        positionMs = Math.max(positionMs, 0)
        val result = sessionPlayer.seekTo(positionMs)
        return try {
            if (seekTimeoutMs <= 0) {
                result.get().resultCode
            } else result[seekTimeoutMs.toLong(), TimeUnit.MILLISECONDS].resultCode
        } catch (e: ExecutionException) {
            Log.w(TAG, "Failed to get the seeking result", e)
            SessionResult.RESULT_ERROR_UNKNOWN
        } catch (e: InterruptedException) {
            Log.w(TAG, "Failed to get the seeking result", e)
            SessionResult.RESULT_ERROR_UNKNOWN
        } catch (e: TimeoutException) {
            Log.w(TAG, "Failed to get the seeking result", e)
            SessionResult.RESULT_ERROR_UNKNOWN
        }
    }

    private fun buildAllowedCommands(
            session: MediaSession, controllerInfo: MediaSession.ControllerInfo): SessionCommandGroup {
        val build: SessionCommandGroup.Builder
        val commands = customCommandProvider?.getCustomCommands(session, controllerInfo)
        build = if (commands != null) {
            SessionCommandGroup.Builder(commands)
        } else {
            SessionCommandGroup.Builder()
        }
        build.addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_1)
        build.addCommand(SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM))
        // TODO(internal b/142848015): Use removeCommand(int) when it's added.
        if (mediaItemProvider == null) {
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM))
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SET_PLAYLIST))
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM))
            build.removeCommand(
                    SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM))
        }
        if (ratingCallback == null) {
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
        }
        if (skipCallback == null) {
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SKIP_BACKWARD))
            build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SKIP_FORWARD))
        }

        // Apply player's capability.
        // Check whether the session has unexpectedly changed the player.
        if (session.player is SessionPlayerConnector) {
            val sessionPlayerConnector = session.player as SessionPlayerConnector

            // Check whether skipTo* works.
            if (!sessionPlayerConnector.canSkipToPlaylistItem()) {
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM))
            }
            if (!sessionPlayerConnector.canSkipToPreviousPlaylistItem()) {
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM))
            }
            if (!sessionPlayerConnector.canSkipToNextPlaylistItem()) {
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM))
            }

            // Check whether seekTo/rewind/fastForward works.
            if (!sessionPlayerConnector.isCurrentMediaItemSeekable) {
                build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO))
                build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD))
                build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND))
            } else {
                if (fastForwardMs <= 0) {
                    build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD))
                }
                if (rewindMs <= 0) {
                    build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND))
                }
            }
        } else {
            if (!loggedUnexpectedSessionPlayerWarning) {
                // This can happen if MediaSession#updatePlayer() is called.
                Log.e(TAG, "SessionPlayer isn't a SessionPlayerConnector. Guess the allowed command.")
                loggedUnexpectedSessionPlayerWarning = true
            }
            if (fastForwardMs <= 0) {
                build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD))
            }
            if (rewindMs <= 0) {
                build.removeCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND))
            }
            val playlist = sessionPlayer.playlist
            if (playlist == null) {
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM))
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM))
                build.removeCommand(
                        SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM))
            } else {
                if (playlist.isEmpty()
                        && (sessionPlayer.repeatMode == SessionPlayer.REPEAT_MODE_NONE
                                || sessionPlayer.repeatMode == SessionPlayer.REPEAT_MODE_ONE)) {
                    build.removeCommand(
                            SessionCommand(
                                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM))
                }
                if (playlist.size == sessionPlayer.currentMediaItemIndex + 1
                        && (sessionPlayer.repeatMode == SessionPlayer.REPEAT_MODE_NONE
                                || sessionPlayer.repeatMode == SessionPlayer.REPEAT_MODE_ONE)) {
                    build.removeCommand(
                            SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM))
                }
                if (playlist.size <= 1) {
                    build.removeCommand(
                            SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM))
                }
            }
        }
        return build.build()
    }

    private inner class PlayerCallback : SessionPlayer.PlayerCallback() {
        private var currentMediaItemBuffered = false
        override fun onPlaylistChanged(
                player: SessionPlayer, list: List<MediaItem>?, metadata: MediaMetadata?) {
            updateAllowedCommands()
        }

        override fun onPlayerStateChanged(player: SessionPlayer, playerState: Int) {
            updateAllowedCommands()
        }

        override fun onRepeatModeChanged(player: SessionPlayer, repeatMode: Int) {
            updateAllowedCommands()
        }

        override fun onShuffleModeChanged(player: SessionPlayer, shuffleMode: Int) {
            updateAllowedCommands()
        }

        // TODO(internal b/160846312): Remove warning suppression and mark item @Nullable once we depend
        // on media2 1.2.0.
        override fun onCurrentMediaItemChanged(player: SessionPlayer, item: MediaItem?) {
            currentMediaItemBuffered = isBufferedState(player.bufferingState)
            updateAllowedCommands()
        }

        override fun onBufferingStateChanged(
                player: SessionPlayer, item: MediaItem?, buffState: Int) {
            if (currentMediaItemBuffered || player.currentMediaItem !== item) {
                return
            }
            if (isBufferedState(buffState)) {
                currentMediaItemBuffered = true
                updateAllowedCommands()
            }
        }

        private fun updateAllowedCommands() {
            for (session in sessions) {
                val connectedControllers = session.connectedControllers
                for (controller in connectedControllers) {
                    val baseAllowedCommands = buildAllowedCommands(session, controller)
                    var allowedCommands = allowedCommandProvider.getAllowedCommands(session, controller, baseAllowedCommands)
                    if (allowedCommands == null) {
                        allowedCommands = SessionCommandGroup.Builder().build()
                    }
                    session.setAllowedCommands(controller, allowedCommands)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SessionCallback"
        private fun isBufferedState( /* @SessionPlayer.BuffState */
                buffState: Int): Boolean {
            return (buffState == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE
                    || buffState == SessionPlayer.BUFFERING_STATE_COMPLETE)
        }
    }
}