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
import android.os.Handler
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.media2.common.SessionPlayer.PlayerResult
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/** Manages the queue of player actions and handles running them one by one.  */ /* package */
internal class PlayerCommandQueue(// Should be only used on the handler.
        private val player: PlayerWrapper, private val handler: Handler) {
    /** List of session commands whose result would be set after the command is finished.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES, COMMAND_CODE_PLAYER_PLAY, COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA, COMMAND_CODE_PLAYER_SET_REPEAT_MODE, COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE, COMMAND_CODE_PLAYER_SET_MEDIA_ITEM, COMMAND_CODE_PLAYER_SEEK_TO, COMMAND_CODE_PLAYER_PREPARE, COMMAND_CODE_PLAYER_SET_SPEED, COMMAND_CODE_PLAYER_PAUSE, COMMAND_CODE_PLAYER_SET_PLAYLIST, COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM])
    annotation class CommandCode

    /** Command whose result would be set later via listener after the command is finished.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [COMMAND_CODE_PLAYER_PREPARE, COMMAND_CODE_PLAYER_PLAY, COMMAND_CODE_PLAYER_PAUSE])
    annotation class AsyncCommandCode

    private val lock: Any

    @GuardedBy("lock")
    private val pendingPlayerCommandQueue: Deque<PlayerCommand>

    // Should be only used on the handler.
    private var pendingAsyncPlayerCommandResult: AsyncPlayerCommandResult? = null

    init {
        lock = Any()
        pendingPlayerCommandQueue = ArrayDeque()
    }

    @SuppressLint("RestrictedApi")
    fun reset() {
        handler.removeCallbacksAndMessages( /* token= */null)
        var queue: List<PlayerCommand>
        synchronized(lock) {
            queue = ArrayList(pendingPlayerCommandQueue)
            pendingPlayerCommandQueue.clear()
        }
        for (playerCommand in queue) {
            playerCommand.result.set(
                    PlayerResult(PlayerResult.RESULT_INFO_SKIPPED,  /* item= */null))
        }
    }

    fun addCommand(
            commandCode: @CommandCode Int, command: Callable<Boolean>): ListenableFuture<PlayerResult> {
        return addCommand(commandCode, command,  /* tag= */null)
    }

    @SuppressLint("RestrictedApi")
    fun addCommand(
            commandCode: @CommandCode Int, command: Callable<Boolean>, tag: Any?): ListenableFuture<PlayerResult> {
        val result = SettableFuture.create<PlayerResult>()
        synchronized(lock) {
            val playerCommand = PlayerCommand(commandCode, command, result, tag)
            result.addListener(
                    {
                        if (result.isCancelled) {
                            var isCommandPending: Boolean
                            synchronized(lock) { isCommandPending = pendingPlayerCommandQueue.remove(playerCommand) }
                            if (isCommandPending) {
                                result.set(
                                        PlayerResult(
                                                PlayerResult.RESULT_INFO_SKIPPED, player.currentMediaItem))
                                if (DEBUG) {
                                    Log.d(TAG, "canceled $playerCommand")
                                }
                            }
                            if (pendingAsyncPlayerCommandResult != null
                                    && pendingAsyncPlayerCommandResult!!.result == result) {
                                pendingAsyncPlayerCommandResult = null
                            }
                        }
                        processPendingCommandOnHandler()
                    }
            ) { runnable: Runnable? -> Util.postOrRun(handler, runnable!!) }
            if (DEBUG) {
                Log.d(TAG, "adding $playerCommand")
            }
            pendingPlayerCommandQueue.add(playerCommand)
        }
        processPendingCommand()
        return result
    }

    @SuppressLint("RestrictedApi")
    fun notifyCommandError() {
        Util.postOrRun(
                handler
        ) {
            val pendingResult = pendingAsyncPlayerCommandResult
            if (pendingResult == null) {
                if (DEBUG) {
                    Log.d(TAG, "Ignoring notifyCommandError(). No pending async command.")
                }
                return@postOrRun
            }
            pendingResult.result.set(
                    PlayerResult(PlayerResult.RESULT_ERROR_UNKNOWN, player.currentMediaItem))
            pendingAsyncPlayerCommandResult = null
            if (DEBUG) {
                Log.d(TAG, "error on $pendingResult")
            }
            processPendingCommandOnHandler()
        }
    }

    @SuppressLint("RestrictedApi")
    fun notifyCommandCompleted(completedCommandCode: @AsyncCommandCode Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyCommandCompleted, completedCommandCode=$completedCommandCode")
        }
        Util.postOrRun(
                handler
        ) {
            val pendingResult = pendingAsyncPlayerCommandResult
            if (pendingResult == null || pendingResult.commandCode != completedCommandCode) {
                if (DEBUG) {
                    Log.d(
                            TAG,
                            "Unexpected Listener is notified from the Player. Player may be used"
                                    + " directly rather than "
                                    + toLogFriendlyString(completedCommandCode))
                }
                return@postOrRun
            }
            pendingResult.result.set(
                    PlayerResult(PlayerResult.RESULT_SUCCESS, player.currentMediaItem))
            pendingAsyncPlayerCommandResult = null
            if (DEBUG) {
                Log.d(TAG, "completed $pendingResult")
            }
            processPendingCommandOnHandler()
        }
    }

    private fun processPendingCommand() {
        Util.postOrRun(handler) { processPendingCommandOnHandler() }
    }

    @SuppressLint("RestrictedApi")
    private fun processPendingCommandOnHandler() {
        while (pendingAsyncPlayerCommandResult == null) {
            var playerCommand: PlayerCommand?
            synchronized(lock) { playerCommand = pendingPlayerCommandQueue.poll() }
            if (playerCommand == null) {
                return
            }
            val commandCode = playerCommand!!.commandCode
            // Check if it's @AsyncCommandCode
            val asyncCommand = isAsyncCommand(playerCommand!!.commandCode)

            // Continuous COMMAND_CODE_PLAYER_SEEK_TO can be skipped.
            if (commandCode == COMMAND_CODE_PLAYER_SEEK_TO) {
                var skippingCommands: MutableList<PlayerCommand>? = null
                while (true) {
                    synchronized(lock) {
                        val pendingCommand = pendingPlayerCommandQueue.peek()
                        if (pendingCommand == null || pendingCommand.commandCode != commandCode) {
                            break
                        }
                        pendingPlayerCommandQueue.poll()
                        if (skippingCommands == null) {
                            skippingCommands = ArrayList()
                        }
                        skippingCommands!!.add(playerCommand!!)
                        playerCommand = pendingCommand
                    }
                }
                if (skippingCommands != null) {
                    for (skippingCommand in skippingCommands!!) {
                        skippingCommand.result.set(
                                PlayerResult(PlayerResult.RESULT_INFO_SKIPPED, player.currentMediaItem))
                        if (DEBUG) {
                            Log.d(TAG, "skipping pending command, $skippingCommand")
                        }
                    }
                }
            }
            if (asyncCommand) {
                // Result would come later, via #notifyCommandCompleted().
                // Set pending player result first because it may be notified while the command is running.
                pendingAsyncPlayerCommandResult = AsyncPlayerCommandResult(commandCode, playerCommand!!.result)
            }
            if (DEBUG) {
                Log.d(TAG, "start processing command, $playerCommand")
            }
            var resultCode: Int
            resultCode = if (player.hasError()) {
                PlayerResult.RESULT_ERROR_INVALID_STATE
            } else {
                try {
                    val handled = playerCommand!!.command.call()
                    if (handled) PlayerResult.RESULT_SUCCESS else PlayerResult.RESULT_INFO_SKIPPED
                } catch (e: IllegalStateException) {
                    PlayerResult.RESULT_ERROR_INVALID_STATE
                } catch (e: IllegalArgumentException) {
                    PlayerResult.RESULT_ERROR_BAD_VALUE
                } catch (e: IndexOutOfBoundsException) {
                    PlayerResult.RESULT_ERROR_BAD_VALUE
                } catch (e: SecurityException) {
                    PlayerResult.RESULT_ERROR_PERMISSION_DENIED
                } catch (e: Exception) {
                    PlayerResult.RESULT_ERROR_UNKNOWN
                }
            }
            if (DEBUG) {
                Log.d(TAG, "command processed, $playerCommand")
            }
            if (asyncCommand) {
                if (resultCode != PlayerResult.RESULT_SUCCESS && pendingAsyncPlayerCommandResult != null && playerCommand!!.result == pendingAsyncPlayerCommandResult!!.result) {
                    pendingAsyncPlayerCommandResult = null
                    playerCommand!!.result.set(PlayerResult(resultCode, player.currentMediaItem))
                }
            } else {
                playerCommand!!.result.set(PlayerResult(resultCode, player.currentMediaItem))
            }
        }
    }

    private class AsyncPlayerCommandResult(
            val commandCode: @AsyncCommandCode Int, val result: SettableFuture<PlayerResult>) {
        override fun toString(): String {
            val stringBuilder = StringBuilder("AsyncPlayerCommandResult {commandCode=")
                    .append(commandCode)
                    .append(", result=")
                    .append(result.hashCode())
            if (result.isDone) {
                try {
                    val resultCode = result[0, TimeUnit.MILLISECONDS].resultCode
                    stringBuilder.append(", resultCode=").append(resultCode)
                } catch (e: Exception) {
                    // pass-through.
                }
            }
            stringBuilder.append("}")
            return stringBuilder.toString()
        }
    }

    private class PlayerCommand(
            val commandCode: Int,
            val command: Callable<Boolean>,
            // Result shouldn't be set with lock held, because it may trigger listener set by developers.
            val result: SettableFuture<PlayerResult>,
            private val tag: Any?) {
        override fun toString(): String {
            val stringBuilder = StringBuilder("PlayerCommand {commandCode=")
                    .append(commandCode)
                    .append(", result=")
                    .append(result.hashCode())
            if (result.isDone) {
                try {
                    val resultCode = result[0, TimeUnit.MILLISECONDS].resultCode
                    stringBuilder.append(", resultCode=").append(resultCode)
                } catch (e: Exception) {
                    // pass-through.
                }
            }
            if (tag != null) {
                stringBuilder.append(", tag=").append(tag)
            }
            stringBuilder.append("}")
            return stringBuilder.toString()
        }
    }

    companion object {
        private const val TAG = "PlayerCommandQueue"
        private const val DEBUG = false
        // Redefine command codes rather than using constants from SessionCommand here, because command
        // code for setAudioAttribute() is missing in SessionCommand.
        /** Command code for [SessionPlayer.setAudioAttributes].  */
        const val COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES = 0

        /** Command code for [SessionPlayer.play]  */
        const val COMMAND_CODE_PLAYER_PLAY = 1

        /** Command code for [SessionPlayer.replacePlaylistItem]  */
        const val COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM = 2

        /** Command code for [SessionPlayer.skipToPreviousPlaylistItem]  */
        const val COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM = 3

        /** Command code for [SessionPlayer.skipToNextPlaylistItem]  */
        const val COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM = 4

        /** Command code for [SessionPlayer.skipToPlaylistItem]  */
        const val COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM = 5

        /** Command code for [SessionPlayer.updatePlaylistMetadata]  */
        const val COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA = 6

        /** Command code for [SessionPlayer.setRepeatMode]  */
        const val COMMAND_CODE_PLAYER_SET_REPEAT_MODE = 7

        /** Command code for [SessionPlayer.setShuffleMode]  */
        const val COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE = 8

        /** Command code for [SessionPlayer.setMediaItem]  */
        const val COMMAND_CODE_PLAYER_SET_MEDIA_ITEM = 9

        /** Command code for [SessionPlayer.seekTo]  */
        const val COMMAND_CODE_PLAYER_SEEK_TO = 10

        /** Command code for [SessionPlayer.prepare]  */
        const val COMMAND_CODE_PLAYER_PREPARE = 11

        /** Command code for [SessionPlayer.setPlaybackSpeed]  */
        const val COMMAND_CODE_PLAYER_SET_SPEED = 12

        /** Command code for [SessionPlayer.pause]  */
        const val COMMAND_CODE_PLAYER_PAUSE = 13

        /** Command code for [SessionPlayer.setPlaylist]  */
        const val COMMAND_CODE_PLAYER_SET_PLAYLIST = 14

        /** Command code for [SessionPlayer.addPlaylistItem]  */
        const val COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM = 15

        /** Command code for [SessionPlayer.removePlaylistItem]  */
        const val COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM = 16

        /** Command code for [SessionPlayer.movePlaylistItem]  */
        const val COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM = 17
        private fun toLogFriendlyString(commandCode: @AsyncCommandCode Int): String {
            return when (commandCode) {
                COMMAND_CODE_PLAYER_PLAY -> "SessionPlayerConnector#play()"
                COMMAND_CODE_PLAYER_PAUSE -> "SessionPlayerConnector#pause()"
                COMMAND_CODE_PLAYER_PREPARE -> "SessionPlayerConnector#prepare()"
                else -> throw IllegalStateException()
            }
        }

        private fun isAsyncCommand(commandCode: @CommandCode Int): Boolean {
            return when (commandCode) {
                COMMAND_CODE_PLAYER_PLAY, COMMAND_CODE_PLAYER_PAUSE, COMMAND_CODE_PLAYER_PREPARE -> true
                COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SEEK_TO, COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES, COMMAND_CODE_PLAYER_SET_MEDIA_ITEM, COMMAND_CODE_PLAYER_SET_PLAYLIST, COMMAND_CODE_PLAYER_SET_REPEAT_MODE, COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE, COMMAND_CODE_PLAYER_SET_SPEED, COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM, COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA -> false
                else -> false
            }
        }
    }
}