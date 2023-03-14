/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2

import com.google.android.exoplayer2.MediaItem.LiveConfiguration

/**
 * Controls the playback speed while playing live content in order to maintain a steady target live
 * offset.
 */
interface LivePlaybackSpeedControl {
    /**
     * Sets the live configuration defined by the media.
     *
     * @param liveConfiguration The [LiveConfiguration] as defined by the media.
     */
    fun setLiveConfiguration(liveConfiguration: LiveConfiguration?)

    /**
     * Sets the target live offset in microseconds that overrides the live offset [ ][.setLiveConfiguration] by the media. Passing `C.TIME_UNSET` deletes a previous
     * override.
     *
     *
     * If no target live offset is configured by [.setLiveConfiguration], this override has
     * no effect.
     */
    fun setTargetLiveOffsetOverrideUs(liveOffsetUs: Long)

    /**
     * Notifies the live playback speed control that a rebuffer occurred.
     *
     *
     * A rebuffer is defined to be caused by buffer depletion rather than a user action. Hence this
     * method is not called during initial buffering or when buffering as a result of a seek
     * operation.
     */
    fun notifyRebuffer()

    /**
     * Returns the adjusted playback speed in order get closer towards the [ ][.getTargetLiveOffsetUs].
     *
     * @param liveOffsetUs The current live offset, in microseconds.
     * @param bufferedDurationUs The duration of media that's currently buffered, in microseconds.
     * @return The adjusted factor by which playback should be sped up.
     */
    fun getAdjustedPlaybackSpeed(liveOffsetUs: Long, bufferedDurationUs: Long): Float

    /**
     * Returns the current target live offset, in microseconds, or [C.TIME_UNSET] if no target
     * live offset is defined for the current media.
     */
    open fun getTargetLiveOffsetUs(): Long
}