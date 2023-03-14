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

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import com.google.android.exoplayer2.util.RepeatModeUtil
import com.google.android.exoplayer2.util.RepeatModeUtil.RepeatToggleModes

/** Provides a custom action for toggling repeat modes.  */
class RepeatModeActionProvider @JvmOverloads constructor(
        context: Context, private val repeatToggleModes: @RepeatToggleModes Int = DEFAULT_REPEAT_TOGGLE_MODES) : CustomActionProvider {
    private val repeatAllDescription: CharSequence
    private val repeatOneDescription: CharSequence
    private val repeatOffDescription: CharSequence
    /**
     * Creates a new instance enabling the given repeat toggle modes.
     *
     * @param context The context.
     * @param repeatToggleModes The toggle modes to enable.
     */
    /**
     * Creates a new instance.
     *
     *
     * Equivalent to `RepeatModeActionProvider(context, DEFAULT_REPEAT_TOGGLE_MODES)`.
     *
     * @param context The context.
     */
    init {
        repeatAllDescription = context.getString(R.string.exo_media_action_repeat_all_description)
        repeatOneDescription = context.getString(R.string.exo_media_action_repeat_one_description)
        repeatOffDescription = context.getString(R.string.exo_media_action_repeat_off_description)
    }

    override fun onCustomAction(player: Player, action: String?, extras: Bundle?) {
        val mode = player.repeatMode
        val proposedMode = RepeatModeUtil.getNextRepeatMode(mode, repeatToggleModes)
        if (mode != proposedMode) {
            player.repeatMode = proposedMode
        }
    }

    override fun getCustomAction(player: Player): PlaybackStateCompat.CustomAction? {
        val actionLabel: CharSequence
        val iconResourceId: Int
        when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                actionLabel = repeatOneDescription
                iconResourceId = R.drawable.exo_media_action_repeat_one
            }
            Player.REPEAT_MODE_ALL -> {
                actionLabel = repeatAllDescription
                iconResourceId = R.drawable.exo_media_action_repeat_all
            }
            Player.REPEAT_MODE_OFF -> {
                actionLabel = repeatOffDescription
                iconResourceId = R.drawable.exo_media_action_repeat_off
            }
            else -> {
                actionLabel = repeatOffDescription
                iconResourceId = R.drawable.exo_media_action_repeat_off
            }
        }
        val repeatBuilder = PlaybackStateCompat.CustomAction.Builder(
                ACTION_REPEAT_MODE, actionLabel, iconResourceId)
        return repeatBuilder.build()
    }

    companion object {
        /** The default repeat toggle modes.  */
        const val DEFAULT_REPEAT_TOGGLE_MODES = RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
        private const val ACTION_REPEAT_MODE = "ACTION_EXO_REPEAT_MODE"
    }
}