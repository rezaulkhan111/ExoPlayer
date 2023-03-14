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
import androidx.media.AudioAttributesCompat
import androidx.media2.common.SessionPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes

/** Utility methods for translating between the media2 and ExoPlayer APIs.  */ /* package */
internal object Utils {
    /** Returns ExoPlayer audio attributes for the given audio attributes.  */
    @SuppressLint("WrongConstant") // AudioAttributesCompat.AttributeUsage is equal to C.AudioUsage
    fun getAudioAttributes(audioAttributesCompat: AudioAttributesCompat): AudioAttributes {
        return AudioAttributes.Builder()
                .setContentType(audioAttributesCompat.contentType)
                .setFlags(audioAttributesCompat.flags)
                .setUsage(audioAttributesCompat.usage)
                .build()
    }

    /** Returns audio attributes for the given ExoPlayer audio attributes.  */
    fun getAudioAttributesCompat(audioAttributes: AudioAttributes): AudioAttributesCompat {
        return AudioAttributesCompat.Builder()
                .setContentType(audioAttributes.contentType)
                .setFlags(audioAttributes.flags)
                .setUsage(audioAttributes.usage)
                .build()
    }

    /** Returns the SimpleExoPlayer's shuffle mode for the given shuffle mode.  */
    fun getExoPlayerShuffleMode(shuffleMode: Int): Boolean {
        return when (shuffleMode) {
            SessionPlayer.SHUFFLE_MODE_ALL, SessionPlayer.SHUFFLE_MODE_GROUP -> true
            SessionPlayer.SHUFFLE_MODE_NONE -> false
            else -> throw IllegalArgumentException()
        }
    }

    /** Returns the shuffle mode for the given ExoPlayer's shuffle mode  */
    fun getShuffleMode(exoPlayerShuffleMode: Boolean): Int {
        return if (exoPlayerShuffleMode) SessionPlayer.SHUFFLE_MODE_ALL else SessionPlayer.SHUFFLE_MODE_NONE
    }

    /** Returns the ExoPlayer's repeat mode for the given repeat mode.  */
    fun getExoPlayerRepeatMode(repeatMode: Int): @Player.RepeatMode Int {
        return when (repeatMode) {
            SessionPlayer.REPEAT_MODE_ALL, SessionPlayer.REPEAT_MODE_GROUP -> Player.REPEAT_MODE_ALL
            SessionPlayer.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            SessionPlayer.REPEAT_MODE_NONE -> Player.REPEAT_MODE_OFF
            else -> throw IllegalArgumentException()
        }
    }

    /** Returns the repeat mode for the given SimpleExoPlayer's repeat mode.  */
    fun getRepeatMode(exoPlayerRepeatMode: @Player.RepeatMode Int): Int {
        return when (exoPlayerRepeatMode) {
            Player.REPEAT_MODE_ALL -> SessionPlayer.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ONE -> SessionPlayer.REPEAT_MODE_ONE
            Player.REPEAT_MODE_OFF -> SessionPlayer.REPEAT_MODE_NONE
            else -> throw IllegalArgumentException()
        }
    }
}