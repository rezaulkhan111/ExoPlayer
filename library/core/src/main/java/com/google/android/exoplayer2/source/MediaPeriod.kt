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
package com.google.android.exoplayer2.source

import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import java.io.IOException

/**
 * Loads media corresponding to a [Timeline.Period], and allows that media to be read. All
 * methods are called on the player's internal playback thread, as described in the [ ] Javadoc.
 *
 *
 * A [MediaPeriod] may only able to provide one [SampleStream] corresponding to a
 * group at any given time, however this [SampleStream] may adapt between multiple tracks
 * within the group.
 */
interface MediaPeriod : SequenceableLoader {
    /** A callback to be notified of [MediaPeriod] events.  */
    interface Callback : SequenceableLoader.Callback<MediaPeriod?> {
        /**
         * Called when preparation completes.
         *
         *
         * Called on the playback thread. After invoking this method, the [MediaPeriod] can
         * expect for [.selectTracks] to be called with the initial track selection.
         *
         * @param mediaPeriod The prepared [MediaPeriod].
         */
        fun onPrepared(mediaPeriod: MediaPeriod?)
    }

    /**
     * Prepares this media period asynchronously.
     *
     *
     * `callback.onPrepared` is called when preparation completes. If preparation fails,
     * [.maybeThrowPrepareError] will throw an [IOException].
     *
     *
     * If preparation succeeds and results in a source timeline change (e.g. the period duration
     * becoming known), [MediaSourceCaller.onSourceInfoRefreshed] will be
     * called before `callback.onPrepared`.
     *
     * @param callback Callback to receive updates from this period, including being notified when
     * preparation completes.
     * @param positionUs The expected starting position, in microseconds.
     */
    fun prepare(callback: Callback?, positionUs: Long)

    /**
     * Throws an error that's preventing the period from becoming prepared. Does nothing if no such
     * error exists.
     *
     *
     * This method is only called before the period has completed preparation.
     *
     * @throws IOException The underlying error.
     */
    @Throws(IOException::class)
    fun maybeThrowPrepareError()

    /**
     * Returns the [TrackGroup]s exposed by the period.
     *
     *
     * This method is only called after the period has been prepared.
     *
     * @return The [TrackGroup]s.
     */
    fun getTrackGroups(): TrackGroupArray?

    /**
     * Returns a list of [StreamKeys][StreamKey] which allow to filter the media in this period
     * to load only the parts needed to play the provided [TrackSelections][ExoTrackSelection].
     *
     *
     * This method is only called after the period has been prepared.
     *
     * @param trackSelections The [TrackSelections][ExoTrackSelection] describing the tracks for
     * which stream keys are requested.
     * @return The corresponding [StreamKeys][StreamKey] for the selected tracks, or an empty
     * list if filtering is not possible and the entire media needs to be loaded to play the
     * selected tracks.
     */
    fun getStreamKeys(trackSelections: List<ExoTrackSelection?>?): List<StreamKey?>? {
        return emptyList<StreamKey>()
    }

    /**
     * Performs a track selection.
     *
     *
     * The call receives track `selections` for each renderer, `mayRetainStreamFlags`
     * indicating whether the existing [SampleStream] can be retained for each selection, and
     * the existing `stream`s themselves. The call will update `streams` to reflect the
     * provided selections, clearing, setting and replacing entries as required. If an existing sample
     * stream is retained but with the requirement that the consuming renderer be reset, then the
     * corresponding flag in `streamResetFlags` will be set to true. This flag will also be set
     * if a new sample stream is created.
     *
     *
     * Note that previously passed [TrackSelections][ExoTrackSelection] are no longer valid,
     * and any references to them must be updated to point to the new selections.
     *
     *
     * This method is only called after the period has been prepared.
     *
     * @param selections The renderer track selections.
     * @param mayRetainStreamFlags Flags indicating whether the existing sample stream can be retained
     * for each track selection. A `true` value indicates that the selection is equivalent
     * to the one that was previously passed, and that the caller does not require that the sample
     * stream be recreated. If a retained sample stream holds any references to the track
     * selection then they must be updated to point to the new selection.
     * @param streams The existing sample streams, which will be updated to reflect the provided
     * selections.
     * @param streamResetFlags Will be updated to indicate new sample streams, and sample streams that
     * have been retained but with the requirement that the consuming renderer be reset.
     * @param positionUs The current playback position in microseconds. If playback of this period has
     * not yet started, the value will be the starting position.
     * @return The actual position at which the tracks were enabled, in microseconds.
     */
    fun selectTracks(
        selections: Array<ExoTrackSelection?>?,
        mayRetainStreamFlags: BooleanArray?,
        streams: Array<SampleStream?>?,
        streamResetFlags: BooleanArray?,
        positionUs: Long
    ): Long

    /**
     * Discards buffered media up to the specified position.
     *
     *
     * This method is only called after the period has been prepared.
     *
     * @param positionUs The position in microseconds.
     * @param toKeyframe If true then for each track discards samples up to the keyframe before or at
     * the specified position, rather than any sample before or at that position.
     */
    fun discardBuffer(positionUs: Long, toKeyframe: Boolean)

    /**
     * Attempts to read a discontinuity.
     *
     *
     * After this method has returned a value other than [C.TIME_UNSET], all [ ]s provided by the period are guaranteed to start from a key frame.
     *
     *
     * This method is only called after the period has been prepared and before reading from any
     * [SampleStream]s provided by the period.
     *
     * @return If a discontinuity was read then the playback position in microseconds after the
     * discontinuity. Else [C.TIME_UNSET].
     */
    fun readDiscontinuity(): Long

    /**
     * Attempts to seek to the specified position in microseconds.
     *
     *
     * After this method has been called, all [SampleStream]s provided by the period are
     * guaranteed to start from a key frame.
     *
     *
     * This method is only called when at least one track is selected.
     *
     * @param positionUs The seek position in microseconds.
     * @return The actual position to which the period was seeked, in microseconds.
     */
    fun seekToUs(positionUs: Long): Long

    /**
     * Returns the position to which a seek will be performed, given the specified seek position and
     * [SeekParameters].
     *
     *
     * This method is only called after the period has been prepared.
     *
     * @param positionUs The seek position in microseconds.
     * @param seekParameters Parameters that control how the seek is performed. Implementations may
     * apply seek parameters on a best effort basis.
     * @return The actual position to which a seek will be performed, in microseconds.
     */
    fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters?): Long
    // SequenceableLoader interface. Overridden to provide more specific documentation.
    /**
     * Returns an estimate of the position up to which data is buffered for the enabled tracks.
     *
     *
     * This method is only called when at least one track is selected.
     *
     * @return An estimate of the absolute position in microseconds up to which data is buffered, or
     * [C.TIME_END_OF_SOURCE] if the track is fully buffered.
     */
    override fun getBufferedPositionUs(): Long

    /**
     * Returns the next load time, or [C.TIME_END_OF_SOURCE] if loading has finished.
     *
     *
     * This method is only called after the period has been prepared. It may be called when no
     * tracks are selected.
     */
    override fun getNextLoadPositionUs(): Long

    /**
     * Attempts to continue loading.
     *
     *
     * This method may be called both during and after the period has been prepared.
     *
     *
     * A period may call [Callback.onContinueLoadingRequested] on the
     * [Callback] passed to [.prepare] to request that this method be
     * called when the period is permitted to continue loading data. A period may do this both during
     * and after preparation.
     *
     * @param positionUs The current playback position in microseconds. If playback of this period has
     * not yet started, the value will be the starting position in this period minus the duration
     * of any media in previous periods still to be played.
     * @return True if progress was made, meaning that [.getNextLoadPositionUs] will return a
     * different value than prior to the call. False otherwise.
     */
    override fun continueLoading(positionUs: Long): Boolean

    /** Returns whether the media period is currently loading.  */
    override fun isLoading(): Boolean

    /**
     * Re-evaluates the buffer given the playback position.
     *
     *
     * This method is only called after the period has been prepared.
     *
     *
     * A period may choose to discard buffered media or cancel ongoing loads so that media can be
     * re-buffered in a different quality.
     *
     * @param positionUs The current playback position in microseconds. If playback of this period has
     * not yet started, the value will be the starting position in this period minus the duration
     * of any media in previous periods still to be played.
     */
    override fun reevaluateBuffer(positionUs: Long)
}