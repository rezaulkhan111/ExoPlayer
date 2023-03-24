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
package com.google.android.exoplayer2.trackselection

import com.google.android.exoplayer2.C.SelectionReason
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.chunk.Chunk
import com.google.android.exoplayer2.source.chunk.MediaChunk
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.util.Log.e

/**
 * A [TrackSelection] that can change the individually selected track as a result of calling
 * [.updateSelectedTrack] or [ ][.evaluateQueueSize]. This only happens between calls to [.enable] and [ ][.disable].
 */
interface ExoTrackSelection : TrackSelection {
    /** Contains of a subset of selected tracks belonging to a [TrackGroup].  */
    class Definition {
        /** The [TrackGroup] which tracks belong to.  */
        @JvmField
        val group: TrackGroup?

        /** The indices of the selected tracks in [.group].  */
        @JvmField
        val tracks: IntArray

        /** The type that will be returned from [TrackSelection.getType].  */
        @JvmField
        val type: @TrackSelection.Type Int

        /**
         * @param group The [TrackGroup]. Must not be null.
         * @param tracks The indices of the selected tracks within the [TrackGroup]. Must not be
         * null or empty. May be in any order.
         */
        constructor(group: TrackGroup?, vararg tracks: Int) : this(group, tracks, TrackSelection.TYPE_UNSET) {}

        /**
         * @param group The [TrackGroup]. Must not be null.
         * @param tracks The indices of the selected tracks within the [TrackGroup]. Must not be
         * null or empty. May be in any order.
         * @param type The type that will be returned from [TrackSelection.getType].
         */
        constructor(group: TrackGroup?, tracks: IntArray, type: @TrackSelection.Type Int) {
            if (tracks.size == 0) {
                // TODO: Turn this into an assertion.
                e(TAG, "Empty tracks are not allowed", IllegalArgumentException())
            }
            this.group = group
            this.tracks = tracks
            this.type = type
        }

        companion object {
            private const val TAG = "ETSDefinition"
        }
    }

    /** Factory for [ExoTrackSelection] instances.  */
    interface Factory {
        /**
         * Creates track selections for the provided [Definitions][Definition].
         *
         *
         * Implementations that create at most one adaptive track selection may use [ ][TrackSelectionUtil.createTrackSelectionsForDefinitions].
         *
         * @param definitions A [Definition] array. May include null values.
         * @param bandwidthMeter A [BandwidthMeter] which can be used to select tracks.
         * @param mediaPeriodId The [MediaPeriodId] of the period for which tracks are to be
         * selected.
         * @param timeline The [Timeline] holding the period for which tracks are to be selected.
         * @return The created selections. Must have the same length as `definitions` and may
         * include null values.
         */
        fun createTrackSelections(
                definitions: Array<Definition?>?,
                bandwidthMeter: BandwidthMeter?,
                mediaPeriodId: MediaSource.MediaPeriodId?,
                timeline: Timeline?): Array<ExoTrackSelection?>?
    }

    /**
     * Enables the track selection. Dynamic changes via [.updateSelectedTrack], [.evaluateQueueSize] or [ ][.shouldCancelChunkLoad] will only happen after this call.
     *
     *
     * This method may not be called when the track selection is already enabled.
     */
    fun enable()

    /**
     * Disables this track selection. No further dynamic changes via [.updateSelectedTrack], [.evaluateQueueSize] or [ ][.shouldCancelChunkLoad] will happen after this call.
     *
     *
     * This method may only be called when the track selection is already enabled.
     */
    fun disable()

    // Individual selected track.

    // Individual selected track.
    /** Returns the [Format] of the individual selected track.  */
    fun getSelectedFormat(): Format?

    /** Returns the index in the track group of the individual selected track.  */
    fun getSelectedIndexInTrackGroup(): Int

    /** Returns the index of the selected track.  */
    fun getSelectedIndex(): Int

    /** Returns the reason for the current track selection.  */
    @SelectionReason
    fun getSelectionReason(): Int

    /** Returns optional data associated with the current track selection.  */
    fun getSelectionData(): Any?
    // Adaptation.
    /**
     * Called to notify the selection of the current playback speed. The playback speed may affect
     * adaptive track selection.
     *
     * @param playbackSpeed The factor by which playback is sped up.
     */
    fun onPlaybackSpeed(playbackSpeed: Float)

    /**
     * Called to notify the selection of a position discontinuity.
     *
     *
     * This happens when the playback position jumps, e.g., as a result of a seek being performed.
     */
    fun onDiscontinuity() {}

    /**
     * Called to notify when a rebuffer occurred.
     *
     *
     * A rebuffer is defined to be caused by buffer depletion rather than a user action. Hence this
     * method is not called during initial buffering or when buffering as a result of a seek
     * operation.
     */
    fun onRebuffer() {}

    /**
     * Called to notify when the playback is paused or resumed.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {}

    /**
     * Updates the selected track for sources that load media in discrete [MediaChunk]s.
     *
     *
     * This method will only be called when the selection is enabled.
     *
     * @param playbackPositionUs The current playback position in microseconds. If playback of the
     * period to which this track selection belongs has not yet started, the value will be the
     * starting position in the period minus the duration of any media in previous periods still
     * to be played.
     * @param bufferedDurationUs The duration of media currently buffered from the current playback
     * position, in microseconds. Note that the next load position can be calculated as `(playbackPositionUs + bufferedDurationUs)`.
     * @param availableDurationUs The duration of media available for buffering from the current
     * playback position, in microseconds, or [C.TIME_UNSET] if media can be buffered to the
     * end of the current period. Note that if not set to [C.TIME_UNSET], the position up to
     * which media is available for buffering can be calculated as `(playbackPositionUs +
     * availableDurationUs)`.
     * @param queue The queue of already buffered [MediaChunk]s. Must not be modified.
     * @param mediaChunkIterators An array of [MediaChunkIterator]s providing information about
     * the sequence of upcoming media chunks for each track in the selection. All iterators start
     * from the media chunk which will be loaded next if the respective track is selected. Note
     * that this information may not be available for all tracks, and so some iterators may be
     * empty.
     */
    fun updateSelectedTrack(
            playbackPositionUs: Long,
            bufferedDurationUs: Long,
            availableDurationUs: Long,
            queue: List<MediaChunk?>?,
            mediaChunkIterators: Array<MediaChunkIterator?>?)

    /**
     * Returns the number of chunks that should be retained in the queue.
     *
     *
     * May be called by sources that load media in discrete [MediaChunks][MediaChunk] and
     * support discarding of buffered chunks.
     *
     *
     * To avoid excessive re-buffering, implementations should normally return the size of the
     * queue. An example of a case where a smaller value may be returned is if network conditions have
     * improved dramatically, allowing chunks to be discarded and re-buffered in a track of
     * significantly higher quality. Discarding chunks may allow faster switching to a higher quality
     * track in this case.
     *
     *
     * Note that even if the source supports discarding of buffered chunks, the actual number of
     * discarded chunks is not guaranteed. The source will call [.updateSelectedTrack] with the updated queue of chunks before loading a new
     * chunk to allow switching to another quality.
     *
     *
     * This method will only be called when the selection is enabled and none of the [ ] in the queue are currently loading.
     *
     * @param playbackPositionUs The current playback position in microseconds. If playback of the
     * period to which this track selection belongs has not yet started, the value will be the
     * starting position in the period minus the duration of any media in previous periods still
     * to be played.
     * @param queue The queue of buffered [MediaChunks][MediaChunk]. Must not be modified.
     * @return The number of chunks to retain in the queue.
     */
    fun evaluateQueueSize(playbackPositionUs: Long, queue: List<MediaChunk?>?): Int

    /**
     * Returns whether an ongoing load of a chunk should be canceled.
     *
     *
     * May be called by sources that load media in discrete [MediaChunks][MediaChunk] and
     * support canceling the ongoing chunk load. The ongoing chunk load is either the last [ ] in the queue or another type of [Chunk], for example, if the source loads
     * initialization or encryption data.
     *
     *
     * To avoid excessive re-buffering, implementations should normally return `false`. An
     * example where `true` might be returned is if a load of a high quality chunk gets stuck
     * and canceling this load in favor of a lower quality alternative may avoid a rebuffer.
     *
     *
     * The source will call [.evaluateQueueSize] after the cancelation finishes
     * to allow discarding of chunks, and [.updateSelectedTrack] before loading a new chunk to allow switching to another quality.
     *
     *
     * This method will only be called when the selection is enabled.
     *
     * @param playbackPositionUs The current playback position in microseconds. If playback of the
     * period to which this track selection belongs has not yet started, the value will be the
     * starting position in the period minus the duration of any media in previous periods still
     * to be played.
     * @param loadingChunk The currently loading [Chunk] that will be canceled if this method
     * returns `true`.
     * @param queue The queue of buffered [MediaChunks][MediaChunk], including the `loadingChunk` if it's a [MediaChunk]. Must not be modified.
     * @return Whether the ongoing load of `loadingChunk` should be canceled.
     */
    fun shouldCancelChunkLoad(playbackPositionUs: Long, loadingChunk: Chunk?, queue: List<MediaChunk?>?): Boolean {
        return false
    }

    /**
     * Attempts to exclude the track at the specified index in the selection, making it ineligible for
     * selection by calls to [.updateSelectedTrack] for the specified period of time.
     *
     *
     * Exclusion will fail if all other tracks are currently excluded. If excluding the currently
     * selected track, note that it will remain selected until the next call to [ ][.updateSelectedTrack].
     *
     *
     * This method will only be called when the selection is enabled.
     *
     * @param index The index of the track in the selection.
     * @param exclusionDurationMs The duration of time for which the track should be excluded, in
     * milliseconds.
     * @return Whether exclusion was successful.
     */
    fun blacklist(index: Int, exclusionDurationMs: Long): Boolean

    /**
     * Returns whether the track at the specified index in the selection is excluded.
     *
     * @param index The index of the track in the selection.
     * @param nowMs The current time in the timebase of [     ][android.os.SystemClock.elapsedRealtime].
     */
    fun isBlacklisted(index: Int, nowMs: Long): Boolean
}