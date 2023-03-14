/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import com.google.android.exoplayer2.upstream.Allocator

/** Controls buffering of media.  */
interface LoadControl {
    /** Called by the player when prepared with a new source.  */
    fun onPrepared()

    /**
     * Called by the player when a track selection occurs.
     *
     * @param renderers The renderers.
     * @param trackGroups The [TrackGroup]s from which the selection was made.
     * @param trackSelections The track selections that were made.
     */
    fun onTracksSelected(
            renderers: Array<Renderer?>?, trackGroups: TrackGroupArray?, trackSelections: Array<ExoTrackSelection?>?)

    /** Called by the player when stopped.  */
    fun onStopped()

    /** Called by the player when released.  */
    fun onReleased()

    /** Returns the [Allocator] that should be used to obtain media buffer allocations.  */
    open fun getAllocator(): Allocator?

    /**
     * Returns the duration of media to retain in the buffer prior to the current playback position,
     * for fast backward seeking.
     *
     *
     * Note: If [.retainBackBufferFromKeyframe] is false then seeking in the back-buffer
     * will only be fast if the back-buffer contains a keyframe prior to the seek position.
     *
     *
     * Note: Implementations should return a single value. Dynamic changes to the back-buffer are
     * not currently supported.
     *
     * @return The duration of media to retain in the buffer prior to the current playback position,
     * in microseconds.
     */
    open fun getBackBufferDurationUs(): Long

    /**
     * Returns whether media should be retained from the keyframe before the current playback position
     * minus [.getBackBufferDurationUs], rather than any sample before or at that position.
     *
     *
     * Warning: Returning true will cause the back-buffer size to depend on the spacing of
     * keyframes in the media being played. Returning true is not recommended unless you control the
     * media and are comfortable with the back-buffer size exceeding [ ][.getBackBufferDurationUs] by as much as the maximum duration between adjacent keyframes in
     * the media.
     *
     *
     * Note: Implementations should return a single value. Dynamic changes to the back-buffer are
     * not currently supported.
     *
     * @return Whether media should be retained from the keyframe before the current playback position
     * minus [.getBackBufferDurationUs], rather than any sample before or at that
     * position.
     */
    fun retainBackBufferFromKeyframe(): Boolean

    /**
     * Called by the player to determine whether it should continue to load the source.
     *
     * @param playbackPositionUs The current playback position in microseconds, relative to the start
     * of the [period][Timeline.Period] that will continue to be loaded if this method
     * returns `true`. If playback of this period has not yet started, the value will be
     * negative and equal in magnitude to the duration of any media in previous periods still to
     * be played.
     * @param bufferedDurationUs The duration of media that's currently buffered.
     * @param playbackSpeed The current factor by which playback is sped up.
     * @return Whether the loading should continue.
     */
    fun shouldContinueLoading(
            playbackPositionUs: Long, bufferedDurationUs: Long, playbackSpeed: Float): Boolean

    /**
     * Called repeatedly by the player when it's loading the source, has yet to start playback, and
     * has the minimum amount of data necessary for playback to be started. The value returned
     * determines whether playback is actually started. The load control may opt to return `false` until some condition has been met (e.g. a certain amount of media is buffered).
     *
     * @param bufferedDurationUs The duration of media that's currently buffered.
     * @param playbackSpeed The current factor by which playback is sped up.
     * @param rebuffering Whether the player is rebuffering. A rebuffer is defined to be caused by
     * buffer depletion rather than a user action. Hence this parameter is false during initial
     * buffering and when buffering as a result of a seek operation.
     * @param targetLiveOffsetUs The desired playback position offset to the live edge in
     * microseconds, or [C.TIME_UNSET] if the media is not a live stream or no offset is
     * configured.
     * @return Whether playback should be allowed to start or resume.
     */
    fun shouldStartPlayback(
            bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean, targetLiveOffsetUs: Long): Boolean
}