/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics

import android.util.SparseArray
import androidx.annotation.IntDef
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventFlags
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2import.DeviceInfo
import com.google.common.base.Objects
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A listener for analytics events.
 *
 *
 * All events are recorded with an [EventTime] specifying the elapsed real time and media
 * time at the time of the event.
 *
 *
 * All methods have no-op default implementations to allow selective overrides.
 *
 *
 * Listeners can choose to implement individual events (e.g. [ ][.onIsPlayingChanged]) or [.onEvents], which is called
 * after one or more events occurred together.
 */
interface AnalyticsListener {
    /** A set of [EventFlags].  */
    class Events {
        private val flags: FlagSet
        private val eventTimes: SparseArray<EventTime>

        /**
         * Creates an instance.
         *
         * @param flags The [FlagSet] containing the [EventFlags] in the set.
         * @param eventTimes A map from [EventFlags] to [EventTime]. Must at least contain
         * all the events recorded in `flags`. Events that are not recorded in `flags`
         * are ignored.
         */
        constructor(flags: FlagSet, eventTimes: SparseArray<EventTime?>) {
            this.flags = flags
            val flagsToTimes = SparseArray<EventTime>( /* initialCapacity= */flags.size())
            for (i in 0 until flags.size()) {
                @EventFlags
                val eventFlag = flags[i]
                flagsToTimes.append(eventFlag, checkNotNull(eventTimes[eventFlag]))
            }
            this.eventTimes = flagsToTimes
        }

        /**
         * Returns the [EventTime] for the specified event.
         *
         * @param event The [event][EventFlags].
         * @return The [EventTime] of this event.
         */
        fun getEventTime(@EventFlags event: Int): EventTime {
            return checkNotNull(eventTimes[event])
        }

        /**
         * Returns whether the given event occurred.
         *
         * @param event The [event][EventFlags].
         * @return Whether the event occurred.
         */
        operator fun contains(@EventFlags event: Int): Boolean {
            return flags.contains(event)
        }

        /**
         * Returns whether any of the given events occurred.
         *
         * @param events The [events][EventFlags].
         * @return Whether any of the events occurred.
         */
        fun containsAny(vararg events: Int): Boolean {
            return flags.containsAny(*events)
        }

        /** Returns the number of events in the set.  */
        fun size(): Int {
            return flags.size()
        }

        /**
         * Returns the [event][EventFlags] at the given index.
         *
         *
         * Although index-based access is possible, it doesn't imply a particular order of these
         * events.
         *
         * @param index The index. Must be between 0 (inclusive) and [.size] (exclusive).
         * @return The [event][EventFlags] at the given index.
         */
        @EventFlags
        operator fun get(index: Int): Int {
            return flags[index]
        }
    }

    /**
     * Events that can be reported via [.onEvents].
     *
     *
     * One of the [AnalyticsListener]`.EVENT_*` flags.
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.LOCAL_VARIABLE,
        TYPE_USE
    )
    @IntDef(
        value = [EVENT_TIMELINE_CHANGED, EVENT_MEDIA_ITEM_TRANSITION, EVENT_TRACKS_CHANGED, EVENT_IS_LOADING_CHANGED, EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED, EVENT_IS_PLAYING_CHANGED, EVENT_REPEAT_MODE_CHANGED, EVENT_SHUFFLE_MODE_ENABLED_CHANGED, EVENT_PLAYER_ERROR, EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_PARAMETERS_CHANGED, EVENT_AVAILABLE_COMMANDS_CHANGED, EVENT_MEDIA_METADATA_CHANGED, EVENT_PLAYLIST_METADATA_CHANGED, EVENT_SEEK_BACK_INCREMENT_CHANGED, EVENT_SEEK_FORWARD_INCREMENT_CHANGED, EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED, EVENT_TRACK_SELECTION_PARAMETERS_CHANGED, EVENT_DEVICE_INFO_CHANGED, EVENT_DEVICE_VOLUME_CHANGED, EVENT_LOAD_STARTED, EVENT_LOAD_COMPLETED, EVENT_LOAD_CANCELED, EVENT_LOAD_ERROR, EVENT_DOWNSTREAM_FORMAT_CHANGED, EVENT_UPSTREAM_DISCARDED, EVENT_BANDWIDTH_ESTIMATE, EVENT_METADATA, EVENT_CUES, EVENT_AUDIO_ENABLED, EVENT_AUDIO_DECODER_INITIALIZED, EVENT_AUDIO_INPUT_FORMAT_CHANGED, EVENT_AUDIO_POSITION_ADVANCING, EVENT_AUDIO_UNDERRUN, EVENT_AUDIO_DECODER_RELEASED, EVENT_AUDIO_DISABLED, EVENT_AUDIO_SESSION_ID, EVENT_AUDIO_ATTRIBUTES_CHANGED, EVENT_SKIP_SILENCE_ENABLED_CHANGED, EVENT_AUDIO_SINK_ERROR, EVENT_VOLUME_CHANGED, EVENT_VIDEO_ENABLED, EVENT_VIDEO_DECODER_INITIALIZED, EVENT_VIDEO_INPUT_FORMAT_CHANGED, EVENT_DROPPED_VIDEO_FRAMES, EVENT_VIDEO_DECODER_RELEASED, EVENT_VIDEO_DISABLED, EVENT_VIDEO_FRAME_PROCESSING_OFFSET, EVENT_RENDERED_FIRST_FRAME, EVENT_VIDEO_SIZE_CHANGED, EVENT_SURFACE_SIZE_CHANGED, EVENT_DRM_SESSION_ACQUIRED, EVENT_DRM_KEYS_LOADED, EVENT_DRM_SESSION_MANAGER_ERROR, EVENT_DRM_KEYS_RESTORED, EVENT_DRM_KEYS_REMOVED, EVENT_DRM_SESSION_RELEASED, EVENT_PLAYER_RELEASED, EVENT_AUDIO_CODEC_ERROR, EVENT_VIDEO_CODEC_ERROR]
    )
    annotation class EventFlags

    companion object {
        /** [Player.getCurrentTimeline] changed.  */
        const val EVENT_TIMELINE_CHANGED: Int = Player.EVENT_TIMELINE_CHANGED

        /**
         * [Player.getCurrentMediaItem] changed or the player started repeating the current item.
         */
        const val EVENT_MEDIA_ITEM_TRANSITION: Int = Player.EVENT_MEDIA_ITEM_TRANSITION

        /** [Player.getCurrentTracks] changed.  */
        const val EVENT_TRACKS_CHANGED: Int = Player.EVENT_TRACKS_CHANGED

        /** [Player.isLoading] ()} changed.  */
        const val EVENT_IS_LOADING_CHANGED: Int = Player.EVENT_IS_LOADING_CHANGED

        /** [Player.getPlaybackState] changed.  */
        const val EVENT_PLAYBACK_STATE_CHANGED: Int = Player.EVENT_PLAYBACK_STATE_CHANGED

        /** [Player.getPlayWhenReady] changed.  */
        const val EVENT_PLAY_WHEN_READY_CHANGED: Int = Player.EVENT_PLAY_WHEN_READY_CHANGED

        /** [Player.getPlaybackSuppressionReason] changed.  */
        const val EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED: Int =
            Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED

        /** [Player.isPlaying] changed.  */
        const val EVENT_IS_PLAYING_CHANGED: Int = Player.EVENT_IS_PLAYING_CHANGED

        /** [Player.getRepeatMode] changed.  */
        const val EVENT_REPEAT_MODE_CHANGED: Int = Player.EVENT_REPEAT_MODE_CHANGED

        /** [Player.getShuffleModeEnabled] changed.  */
        const val EVENT_SHUFFLE_MODE_ENABLED_CHANGED: Int =
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED

        /** [Player.getPlayerError] changed.  */
        const val EVENT_PLAYER_ERROR: Int = Player.EVENT_PLAYER_ERROR

        /**
         * A position discontinuity occurred. See [ ][Player.Listener.onPositionDiscontinuity].
         */
        const val EVENT_POSITION_DISCONTINUITY: Int = Player.EVENT_POSITION_DISCONTINUITY

        /** [Player.getPlaybackParameters] changed.  */
        const val EVENT_PLAYBACK_PARAMETERS_CHANGED: Int = Player.EVENT_PLAYBACK_PARAMETERS_CHANGED

        /** [Player.getAvailableCommands] changed.  */
        const val EVENT_AVAILABLE_COMMANDS_CHANGED: Int = Player.EVENT_AVAILABLE_COMMANDS_CHANGED

        /** [Player.getMediaMetadata] changed.  */
        const val EVENT_MEDIA_METADATA_CHANGED: Int = Player.EVENT_MEDIA_METADATA_CHANGED

        /** [Player.getPlaylistMetadata] changed.  */
        const val EVENT_PLAYLIST_METADATA_CHANGED: Int = Player.EVENT_PLAYLIST_METADATA_CHANGED

        /** [Player.getSeekBackIncrement] changed.  */
        const val EVENT_SEEK_BACK_INCREMENT_CHANGED: Int = Player.EVENT_SEEK_BACK_INCREMENT_CHANGED

        /** [Player.getSeekForwardIncrement] changed.  */
        const val EVENT_SEEK_FORWARD_INCREMENT_CHANGED: Int =
            Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED

        /** [Player.getMaxSeekToPreviousPosition] changed.  */
        const val EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED: Int =
            Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED

        /** [Player.getTrackSelectionParameters] changed.  */
        const val EVENT_TRACK_SELECTION_PARAMETERS_CHANGED: Int =
            Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED

        /** Audio attributes changed.  */
        const val EVENT_AUDIO_ATTRIBUTES_CHANGED: Int = Player.EVENT_AUDIO_ATTRIBUTES_CHANGED

        /** An audio session id was set.  */
        const val EVENT_AUDIO_SESSION_ID: Int = Player.EVENT_AUDIO_SESSION_ID

        /** The volume changed.  */
        const val EVENT_VOLUME_CHANGED: Int = Player.EVENT_VOLUME_CHANGED

        /** Skipping silences was enabled or disabled in the audio stream.  */
        const val EVENT_SKIP_SILENCE_ENABLED_CHANGED: Int =
            Player.EVENT_SKIP_SILENCE_ENABLED_CHANGED

        /** The surface size changed.  */
        const val EVENT_SURFACE_SIZE_CHANGED: Int = Player.EVENT_SURFACE_SIZE_CHANGED

        /** The video size changed.  */
        const val EVENT_VIDEO_SIZE_CHANGED: Int = Player.EVENT_VIDEO_SIZE_CHANGED

        /**
         * The first frame has been rendered since setting the surface, since the renderer was reset or
         * since the stream changed.
         */
        const val EVENT_RENDERED_FIRST_FRAME: Int = Player.EVENT_RENDERED_FIRST_FRAME

        /** Metadata associated with the current playback time was reported.  */
        const val EVENT_METADATA: Int = Player.EVENT_METADATA

        /** [Player.getCurrentCues] changed.  */
        const val EVENT_CUES: Int = Player.EVENT_CUES

        /** [Player.getDeviceInfo] changed.  */
        const val EVENT_DEVICE_INFO_CHANGED: Int = Player.EVENT_DEVICE_INFO_CHANGED

        /** [Player.getDeviceVolume] changed.  */
        const val EVENT_DEVICE_VOLUME_CHANGED: Int = Player.EVENT_DEVICE_VOLUME_CHANGED

        /** A source started loading data.  */
        const val EVENT_LOAD_STARTED = 1000 // Intentional gap to leave space for new Player events

        /** A source started completed loading data.  */
        const val EVENT_LOAD_COMPLETED = 1001

        /** A source canceled loading data.  */
        const val EVENT_LOAD_CANCELED = 1002

        /** A source had a non-fatal error loading data.  */
        const val EVENT_LOAD_ERROR = 1003

        /** The downstream format sent to renderers changed.  */
        const val EVENT_DOWNSTREAM_FORMAT_CHANGED = 1004

        /** Data was removed from the end of the media buffer.  */
        const val EVENT_UPSTREAM_DISCARDED = 1005

        /** The bandwidth estimate has been updated.  */
        const val EVENT_BANDWIDTH_ESTIMATE = 1006

        /** An audio renderer was enabled.  */
        const val EVENT_AUDIO_ENABLED = 1007

        /** An audio renderer created a decoder.  */
        const val EVENT_AUDIO_DECODER_INITIALIZED = 1008

        /** The format consumed by an audio renderer changed.  */
        const val EVENT_AUDIO_INPUT_FORMAT_CHANGED = 1009

        /** The audio position has increased for the first time since the last pause or position reset.  */
        const val EVENT_AUDIO_POSITION_ADVANCING = 1010

        /** An audio underrun occurred.  */
        const val EVENT_AUDIO_UNDERRUN = 1011

        /** An audio renderer released a decoder.  */
        const val EVENT_AUDIO_DECODER_RELEASED = 1012

        /** An audio renderer was disabled.  */
        const val EVENT_AUDIO_DISABLED = 1013

        /** The audio sink encountered a non-fatal error.  */
        const val EVENT_AUDIO_SINK_ERROR = 1014

        /** A video renderer was enabled.  */
        const val EVENT_VIDEO_ENABLED = 1015

        /** A video renderer created a decoder.  */
        const val EVENT_VIDEO_DECODER_INITIALIZED = 1016

        /** The format consumed by a video renderer changed.  */
        const val EVENT_VIDEO_INPUT_FORMAT_CHANGED = 1017

        /** Video frames have been dropped.  */
        const val EVENT_DROPPED_VIDEO_FRAMES = 1018

        /** A video renderer released a decoder.  */
        const val EVENT_VIDEO_DECODER_RELEASED = 1019

        /** A video renderer was disabled.  */
        const val EVENT_VIDEO_DISABLED = 1020

        /** Video frame processing offset data has been reported.  */
        const val EVENT_VIDEO_FRAME_PROCESSING_OFFSET = 1021

        /** A DRM session has been acquired.  */
        const val EVENT_DRM_SESSION_ACQUIRED = 1022

        /** DRM keys were loaded.  */
        const val EVENT_DRM_KEYS_LOADED = 1023

        /** A non-fatal DRM session manager error occurred.  */
        const val EVENT_DRM_SESSION_MANAGER_ERROR = 1024

        /** DRM keys were restored.  */
        const val EVENT_DRM_KEYS_RESTORED = 1025

        /** DRM keys were removed.  */
        const val EVENT_DRM_KEYS_REMOVED = 1026

        /** A DRM session has been released.  */
        const val EVENT_DRM_SESSION_RELEASED = 1027

        /** The player was released.  */
        const val EVENT_PLAYER_RELEASED = 1028

        /** The audio codec encountered an error.  */
        const val EVENT_AUDIO_CODEC_ERROR = 1029

        /** The video codec encountered an error.  */
        const val EVENT_VIDEO_CODEC_ERROR = 1030
    }

    /** Time information of an event.  */
    class EventTime {

        /**
         * Elapsed real-time as returned by `SystemClock.elapsedRealtime()` at the time of the
         * event, in milliseconds.
         */
        var realtimeMs: Long = 0

        /** Most recent [Timeline] that contains the event position.  */
        var timeline: Timeline? = null

        /**
         * Window index in the [.timeline] this event belongs to, or the prospective window index
         * if the timeline is not yet known and empty.
         */
        var windowIndex = 0

        /**
         * [Media period identifier][MediaPeriodId] for the media period this event belongs to, or
         * `null` if the event is not associated with a specific media period.
         */
        var mediaPeriodId: MediaSource.MediaPeriodId? = null

        /**
         * Position in the window or ad this event belongs to at the time of the event, in milliseconds.
         */
        var eventPlaybackPositionMs: Long = 0

        /**
         * The current [Timeline] at the time of the event (equivalent to [ ][Player.getCurrentTimeline]).
         */
        var currentTimeline: Timeline? = null

        /**
         * The current window index in [.currentTimeline] at the time of the event, or the
         * prospective window index if the timeline is not yet known and empty (equivalent to [ ][Player.getCurrentMediaItemIndex]).
         */
        var currentWindowIndex = 0

        /**
         * [Media period identifier][MediaPeriodId] for the currently playing media period at the
         * time of the event, or `null` if no current media period identifier is available.
         */
        var currentMediaPeriodId: MediaSource.MediaPeriodId? = null

        /**
         * Position in the [current timeline window][.currentWindowIndex] or the currently playing
         * ad at the time of the event, in milliseconds.
         */
        var currentPlaybackPositionMs: Long = 0

        /**
         * Total buffered duration from [.currentPlaybackPositionMs] at the time of the event, in
         * milliseconds. This includes pre-buffered data for subsequent ads and windows.
         */
        var totalBufferedDurationMs: Long = 0

        /**
         * @param realtimeMs Elapsed real-time as returned by {@code SystemClock.elapsedRealtime()} at
         *     the time of the event, in milliseconds.
         * @param timeline Most recent {@link Timeline} that contains the event position.
         * @param windowIndex Window index in the {@code timeline} this event belongs to, or the
         *     prospective window index if the timeline is not yet known and empty.
         * @param mediaPeriodId {@link MediaPeriodId Media period identifier} for the media period this
         *     event belongs to, or {@code null} if the event is not associated with a specific media
         *     period.
         * @param eventPlaybackPositionMs Position in the window or ad this event belongs to at the time
         *     of the event, in milliseconds.
         * @param currentTimeline The current {@link Timeline} at the time of the event (equivalent to
         *     {@link Player#getCurrentTimeline()}).
         * @param currentWindowIndex The current window index in {@code currentTimeline} at the time of
         *     the event, or the prospective window index if the timeline is not yet known and empty
         *     (equivalent to {@link Player#getCurrentMediaItemIndex()}).
         * @param currentMediaPeriodId {@link MediaPeriodId Media period identifier} for the currently
         *     playing media period at the time of the event, or {@code null} if no current media period
         *     identifier is available.
         * @param currentPlaybackPositionMs Position in the current timeline window or the currently
         *     playing ad at the time of the event, in milliseconds.
         * @param totalBufferedDurationMs Total buffered duration from {@code currentPlaybackPositionMs}
         *     at the time of the event, in milliseconds. This includes pre-buffered data for subsequent
         *     ads and windows.
         */
        constructor(
            realtimeMs: Long,
            timeline: Timeline?,
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            eventPlaybackPositionMs: Long,
            currentTimeline: Timeline?,
            currentWindowIndex: Int,
            currentMediaPeriodId: MediaSource.MediaPeriodId?,
            currentPlaybackPositionMs: Long,
            totalBufferedDurationMs: Long
        ) {
            this.realtimeMs = realtimeMs
            this.timeline = timeline
            this.windowIndex = windowIndex
            this.mediaPeriodId = mediaPeriodId
            this.eventPlaybackPositionMs = eventPlaybackPositionMs
            this.currentTimeline = currentTimeline
            this.currentWindowIndex = currentWindowIndex
            this.currentMediaPeriodId = currentMediaPeriodId
            this.currentPlaybackPositionMs = currentPlaybackPositionMs
            this.totalBufferedDurationMs = totalBufferedDurationMs
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val eventTime = o as EventTime
            return (realtimeMs == eventTime.realtimeMs && windowIndex == eventTime.windowIndex && eventPlaybackPositionMs == eventTime.eventPlaybackPositionMs && currentWindowIndex == eventTime.currentWindowIndex && currentPlaybackPositionMs == eventTime.currentPlaybackPositionMs && totalBufferedDurationMs == eventTime.totalBufferedDurationMs && Objects.equal(
                timeline, eventTime.timeline
            ) && Objects.equal(mediaPeriodId, eventTime.mediaPeriodId) && Objects.equal(
                currentTimeline, eventTime.currentTimeline
            ) && Objects.equal(currentMediaPeriodId, eventTime.currentMediaPeriodId))
        }

        override fun hashCode(): Int {
            return Objects.hashCode(
                realtimeMs,
                timeline,
                windowIndex,
                mediaPeriodId,
                eventPlaybackPositionMs,
                currentTimeline,
                currentWindowIndex,
                currentMediaPeriodId,
                currentPlaybackPositionMs,
                totalBufferedDurationMs
            )
        }
    }


    @Deprecated(
        """Use {@link #onPlaybackStateChanged(EventTime, int)} and {@link
   *     #onPlayWhenReadyChanged(EventTime, boolean, int)} instead."""
    )
    fun onPlayerStateChanged(
        eventTime: EventTime?, playWhenReady: Boolean, @State playbackState: Int
    ) {
    }

    /**
     * Called when the playback state changed.
     *
     * @param eventTime The event time.
     * @param state The new [playback state][Player.State].
     */
    fun onPlaybackStateChanged(eventTime: EventTime?, @State state: Int) {}

    /**
     * Called when the value changed that indicates whether playback will proceed when ready.
     *
     * @param eventTime The event time.
     * @param playWhenReady Whether playback will proceed when ready.
     * @param reason The [reason][Player.PlayWhenReadyChangeReason] of the change.
     */
    fun onPlayWhenReadyChanged(
        eventTime: EventTime?, playWhenReady: Boolean, @PlayWhenReadyChangeReason reason: Int
    ) {
    }

    /**
     * Called when playback suppression reason changed.
     *
     * @param eventTime The event time.
     * @param playbackSuppressionReason The new [PlaybackSuppressionReason].
     */
    fun onPlaybackSuppressionReasonChanged(
        eventTime: EventTime?, @PlaybackSuppressionReason playbackSuppressionReason: Int
    ) {
    }

    /**
     * Called when the player starts or stops playing.
     *
     * @param eventTime The event time.
     * @param isPlaying Whether the player is playing.
     */
    fun onIsPlayingChanged(eventTime: EventTime?, isPlaying: Boolean) {}

    /**
     * Called when the timeline changed.
     *
     * @param eventTime The event time.
     * @param reason The reason for the timeline change.
     */
    fun onTimelineChanged(eventTime: EventTime?, reason: @TimelineChangeReason Int) {}

    /**
     * Called when playback transitions to a different media item.
     *
     * @param eventTime The event time.
     * @param mediaItem The media item.
     * @param reason The reason for the media item transition.
     */
    fun onMediaItemTransition(
        eventTime: EventTime?,
        mediaItem: MediaItem?,
        @Player.MediaItemTransitionReason reason: Int
    ) {
    }


    @Deprecated(
        """Use {@link #onPositionDiscontinuity(EventTime, Player.PositionInfo,
   *     Player.PositionInfo, int)} instead."""
    )
    fun onPositionDiscontinuity(eventTime: EventTime?, @DiscontinuityReason reason: Int) {
    }

    /**
     * Called when a position discontinuity occurred.
     *
     * @param eventTime The event time.
     * @param oldPosition The position before the discontinuity.
     * @param newPosition The position after the discontinuity.
     * @param reason The reason for the position discontinuity.
     */
    fun onPositionDiscontinuity(
        eventTime: EventTime?,
        oldPosition: PositionInfo?,
        newPosition: PositionInfo?,
        @DiscontinuityReason reason: Int
    ) {
    }


    @Deprecated(
        """Use {@link #onPositionDiscontinuity(EventTime, Player.PositionInfo,
   *     Player.PositionInfo, int)} instead, listening to changes with {@link
   *     Player#DISCONTINUITY_REASON_SEEK}."""
    )
    fun onSeekStarted(eventTime: EventTime?) {
    }


    @Deprecated(
        """Seeks are processed without delay. Use {@link #onPositionDiscontinuity(EventTime,
   *     int)} with reason {@link Player#DISCONTINUITY_REASON_SEEK} instead."""
    )
    fun onSeekProcessed(eventTime: EventTime?) {
    }

    /**
     * Called when the playback parameters changed.
     *
     * @param eventTime The event time.
     * @param playbackParameters The new playback parameters.
     */
    fun onPlaybackParametersChanged(
        eventTime: EventTime?, playbackParameters: PlaybackParameters?
    ) {
    }

    /**
     * Called when the seek back increment changed.
     *
     * @param eventTime The event time.
     * @param seekBackIncrementMs The seek back increment, in milliseconds.
     */
    fun onSeekBackIncrementChanged(eventTime: EventTime?, seekBackIncrementMs: Long) {}

    /**
     * Called when the seek forward increment changed.
     *
     * @param eventTime The event time.
     * @param seekForwardIncrementMs The seek forward increment, in milliseconds.
     */
    fun onSeekForwardIncrementChanged(eventTime: EventTime?, seekForwardIncrementMs: Long) {}

    /**
     * Called when the maximum position for which [Player.seekToPrevious] seeks to the
     * previous window changes.
     *
     * @param eventTime The event time.
     * @param maxSeekToPreviousPositionMs The maximum seek to previous position, in milliseconds.
     */
    fun onMaxSeekToPreviousPositionChanged(
        eventTime: EventTime?, maxSeekToPreviousPositionMs: Long
    ) {
    }

    /**
     * Called when the repeat mode changed.
     *
     * @param eventTime The event time.
     * @param repeatMode The new repeat mode.
     */
    fun onRepeatModeChanged(eventTime: EventTime?, @RepeatMode repeatMode: Int) {}

    /**
     * Called when the shuffle mode changed.
     *
     * @param eventTime The event time.
     * @param shuffleModeEnabled Whether the shuffle mode is enabled.
     */
    fun onShuffleModeChanged(eventTime: EventTime?, shuffleModeEnabled: Boolean) {}

    /**
     * Called when the player starts or stops loading data from a source.
     *
     * @param eventTime The event time.
     * @param isLoading Whether the player is loading.
     */
    fun onIsLoadingChanged(eventTime: EventTime?, isLoading: Boolean) {}


    @Deprecated("Use {@link #onIsLoadingChanged(EventTime, boolean)} instead.")
    fun onLoadingChanged(eventTime: EventTime?, isLoading: Boolean) {
    }

    /**
     * Called when the player's available commands changed.
     *
     * @param eventTime The event time.
     * @param availableCommands The available commands.
     */
    fun onAvailableCommandsChanged(eventTime: EventTime?, availableCommands: Commands?) {}

    /**
     * Called when a fatal player error occurred.
     *
     *
     * Implementations of [Player] may pass an instance of a subclass of [ ] to this method in order to include more information about the error.
     *
     * @param eventTime The event time.
     * @param error The error.
     */
    fun onPlayerError(eventTime: EventTime?, error: PlaybackException?) {}

    /**
     * Called when the [PlaybackException] returned by [Player.getPlayerError] changes.
     *
     *
     * Implementations of Player may pass an instance of a subclass of [PlaybackException] to
     * this method in order to include more information about the error.
     *
     * @param eventTime The event time.
     * @param error The new error, or null if the error is being cleared.
     */
    fun onPlayerErrorChanged(eventTime: EventTime?, error: PlaybackException?) {}

    /**
     * Called when the tracks change.
     *
     * @param eventTime The event time.
     * @param tracks The tracks. Never null, but may be of length zero.
     */
    fun onTracksChanged(eventTime: EventTime?, tracks: Tracks?) {}

    /**
     * Called when track selection parameters change.
     *
     * @param eventTime The event time.
     * @param trackSelectionParameters The new [TrackSelectionParameters].
     */
    fun onTrackSelectionParametersChanged(
        eventTime: EventTime?, trackSelectionParameters: TrackSelectionParameters?
    ) {
    }

    /**
     * Called when the combined [MediaMetadata] changes.
     *
     *
     * The provided [MediaMetadata] is a combination of the [MediaItem.mediaMetadata]
     * and the static and dynamic metadata from the [track][TrackSelection.getFormat] and [MetadataOutput.onMetadata].
     *
     * @param eventTime The event time.
     * @param mediaMetadata The combined [MediaMetadata].
     */
    fun onMediaMetadataChanged(eventTime: EventTime?, mediaMetadata: MediaMetadata?) {}

    /**
     * Called when the playlist [MediaMetadata] changes.
     *
     * @param eventTime The event time.
     * @param playlistMetadata The playlist [MediaMetadata].
     */
    fun onPlaylistMetadataChanged(eventTime: EventTime?, playlistMetadata: MediaMetadata?) {}

    /**
     * Called when a media source started loading data.
     *
     * @param eventTime The event time.
     * @param loadEventInfo The [LoadEventInfo] defining the load event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadStarted(
        eventTime: EventTime?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a media source completed loading data.
     *
     * @param eventTime The event time.
     * @param loadEventInfo The [LoadEventInfo] defining the load event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadCompleted(
        eventTime: EventTime?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a media source canceled loading data.
     *
     * @param eventTime The event time.
     * @param loadEventInfo The [LoadEventInfo] defining the load event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadCanceled(
        eventTime: EventTime?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a media source loading error occurred.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param eventTime The event time.
     * @param loadEventInfo The [LoadEventInfo] defining the load event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     * @param error The load error.
     * @param wasCanceled Whether the load was canceled as a result of the error.
     */
    fun onLoadError(
        eventTime: EventTime?,
        loadEventInfo: LoadEventInfo?,
        mediaLoadData: MediaLoadData?,
        error: IOException?,
        wasCanceled: Boolean
    ) {
    }

    /**
     * Called when the downstream format sent to the renderers changed.
     *
     * @param eventTime The event time.
     * @param mediaLoadData The [MediaLoadData] defining the newly selected media data.
     */
    fun onDownstreamFormatChanged(eventTime: EventTime?, mediaLoadData: MediaLoadData?) {}

    /**
     * Called when data is removed from the back of a media buffer, typically so that it can be
     * re-buffered in a different format.
     *
     * @param eventTime The event time.
     * @param mediaLoadData The [MediaLoadData] defining the media being discarded.
     */
    fun onUpstreamDiscarded(eventTime: EventTime?, mediaLoadData: MediaLoadData?) {}

    /**
     * Called when the bandwidth estimate for the current data source has been updated.
     *
     * @param eventTime The event time.
     * @param totalLoadTimeMs The total time spend loading this update is based on, in milliseconds.
     * @param totalBytesLoaded The total bytes loaded this update is based on.
     * @param bitrateEstimate The bandwidth estimate, in bits per second.
     */
    fun onBandwidthEstimate(
        eventTime: EventTime?, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long
    ) {
    }

    /**
     * Called when there is [Metadata] associated with the current playback time.
     *
     * @param eventTime The event time.
     * @param metadata The metadata.
     */
    fun onMetadata(eventTime: EventTime?, metadata: Metadata?) {}

    /**
     * Called when there is a change in the [Cues][Cue].
     *
     *
     * Both [.onCues] and [.onCues] are called
     * when there is a change in the cues. You should only implement one or the other.
     *
     * @param eventTime The event time.
     * @param cues The [Cues][Cue].
     */
    @Deprecated("Use {@link #onCues(EventTime, CueGroup)} instead.")
    fun onCues(eventTime: EventTime?, cues: List<Cue?>?) {
    }

    /**
     * Called when there is a change in the [CueGroup].
     *
     *
     * Both [.onCues] and [.onCues] are called
     * when there is a change in the cues. You should only implement one or the other.
     *
     * @param eventTime The event time.
     * @param cueGroup The [CueGroup].
     */
    fun onCues(eventTime: EventTime?, cueGroup: CueGroup?) {}


    @Deprecated("Use {@link #onAudioEnabled} and {@link #onVideoEnabled} instead.")
    fun onDecoderEnabled(
        eventTime: EventTime?, trackType: Int, decoderCounters: DecoderCounters?
    ) {
    }


    @Deprecated(
        """Use {@link #onAudioDecoderInitialized} and {@link #onVideoDecoderInitialized}
        instead."""
    )
    fun onDecoderInitialized(
        eventTime: EventTime?, trackType: Int, decoderName: String?, initializationDurationMs: Long
    ) {
    }


    @Deprecated(
        """Use {@link #onAudioInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}
        and {@link #onVideoInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}. instead."""
    )
    fun onDecoderInputFormatChanged(eventTime: EventTime?, trackType: Int, format: Format?) {
    }


    @Deprecated("Use {@link #onAudioDisabled} and {@link #onVideoDisabled} instead.")
    fun onDecoderDisabled(
        eventTime: EventTime?, trackType: Int, decoderCounters: DecoderCounters?
    ) {
    }

    /**
     * Called when an audio renderer is enabled.
     *
     * @param eventTime The event time.
     * @param decoderCounters [DecoderCounters] that will be updated by the renderer for as long
     * as it remains enabled.
     */
    fun onAudioEnabled(eventTime: EventTime?, decoderCounters: DecoderCounters?) {}

    /**
     * Called when an audio renderer creates a decoder.
     *
     * @param eventTime The event time.
     * @param decoderName The decoder that was created.
     * @param initializedTimestampMs [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onAudioDecoderInitialized(
        eventTime: EventTime?,
        decoderName: String?,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
    }


    @Deprecated("Use {@link #onAudioDecoderInitialized(EventTime, String, long, long)}.")
    fun onAudioDecoderInitialized(
        eventTime: EventTime?, decoderName: String?, initializationDurationMs: Long
    ) {
    }


    @Deprecated("Use {@link #onAudioInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}.")
    fun onAudioInputFormatChanged(eventTime: EventTime?, format: Format?) {
    }

    /**
     * Called when the format of the media being consumed by an audio renderer changes.
     *
     * @param eventTime The event time.
     * @param format The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onAudioInputFormatChanged(
        eventTime: EventTime?,
        format: Format?,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
    }

    /**
     * Called when the audio position has increased for the first time since the last pause or
     * position reset.
     *
     * @param eventTime The event time.
     * @param playoutStartSystemTimeMs The approximate derived [System.currentTimeMillis] at
     * which playout started.
     */
    fun onAudioPositionAdvancing(eventTime: EventTime?, playoutStartSystemTimeMs: Long) {}

    /**
     * Called when an audio underrun occurs.
     *
     * @param eventTime The event time.
     * @param bufferSize The size of the audio output buffer, in bytes.
     * @param bufferSizeMs The size of the audio output buffer, in milliseconds, if it contains PCM
     * encoded audio. [C.TIME_UNSET] if the output buffer contains non-PCM encoded audio.
     * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
     */
    fun onAudioUnderrun(
        eventTime: EventTime?, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long
    ) {
    }

    /**
     * Called when an audio renderer releases a decoder.
     *
     * @param eventTime The event time.
     * @param decoderName The decoder that was released.
     */
    fun onAudioDecoderReleased(eventTime: EventTime?, decoderName: String?) {}

    /**
     * Called when an audio renderer is disabled.
     *
     * @param eventTime The event time.
     * @param decoderCounters [DecoderCounters] that were updated by the renderer.
     */
    fun onAudioDisabled(eventTime: EventTime?, decoderCounters: DecoderCounters?) {}

    /**
     * Called when the audio session ID changes.
     *
     * @param eventTime The event time.
     * @param audioSessionId The audio session ID.
     */
    fun onAudioSessionIdChanged(eventTime: EventTime?, audioSessionId: Int) {}

    /**
     * Called when the audio attributes change.
     *
     * @param eventTime The event time.
     * @param audioAttributes The audio attributes.
     */
    fun onAudioAttributesChanged(eventTime: EventTime?, audioAttributes: AudioAttributes?) {}

    /**
     * Called when skipping silences is enabled or disabled in the audio stream.
     *
     * @param eventTime The event time.
     * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
     */
    fun onSkipSilenceEnabledChanged(eventTime: EventTime?, skipSilenceEnabled: Boolean) {}

    /**
     * Called when [AudioSink] has encountered an error.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param eventTime The event time.
     * @param audioSinkError The error that occurred. Typically an [     ], a [AudioSink.WriteException], or an [     ].
     */
    fun onAudioSinkError(eventTime: EventTime?, audioSinkError: Exception?) {}

    /**
     * Called when an audio decoder encounters an error.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param eventTime The event time.
     * @param audioCodecError The error. Typically a [CodecException] if the renderer uses
     * [MediaCodec], or a [DecoderException] if the renderer uses a software decoder.
     */
    fun onAudioCodecError(eventTime: EventTime?, audioCodecError: Exception?) {}

    /**
     * Called when the volume changes.
     *
     * @param eventTime The event time.
     * @param volume The new volume, with 0 being silence and 1 being unity gain.
     */
    fun onVolumeChanged(eventTime: EventTime?, volume: Float) {}

    /**
     * Called when the device information changes
     *
     * @param eventTime The event time.
     * @param deviceInfo The new [DeviceInfo].
     */
    fun onDeviceInfoChanged(eventTime: EventTime?, deviceInfo: DeviceInfo?) {}

    /**
     * Called when the device volume or mute state changes.
     *
     * @param eventTime The event time.
     * @param volume The new device volume, with 0 being silence and 1 being unity gain.
     * @param muted Whether the device is muted.
     */
    fun onDeviceVolumeChanged(eventTime: EventTime?, volume: Int, muted: Boolean) {}

    /**
     * Called when a video renderer is enabled.
     *
     * @param eventTime The event time.
     * @param decoderCounters [DecoderCounters] that will be updated by the renderer for as long
     * as it remains enabled.
     */
    fun onVideoEnabled(eventTime: EventTime?, decoderCounters: DecoderCounters?) {}

    /**
     * Called when a video renderer creates a decoder.
     *
     * @param eventTime The event time.
     * @param decoderName The decoder that was created.
     * @param initializedTimestampMs [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onVideoDecoderInitialized(
        eventTime: EventTime?,
        decoderName: String?,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
    }


    @Deprecated("Use {@link #onVideoDecoderInitialized(EventTime, String, long, long)}.")
    fun onVideoDecoderInitialized(
        eventTime: EventTime?, decoderName: String?, initializationDurationMs: Long
    ) {
    }


    @Deprecated("Use {@link #onVideoInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}.")
    fun onVideoInputFormatChanged(eventTime: EventTime?, format: Format?) {
    }

    /**
     * Called when the format of the media being consumed by a video renderer changes.
     *
     * @param eventTime The event time.
     * @param format The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onVideoInputFormatChanged(
        eventTime: EventTime?,
        format: Format?,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
    }

    /**
     * Called after video frames have been dropped.
     *
     * @param eventTime The event time.
     * @param droppedFrames The number of dropped frames since the last call to this method.
     * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
     * is timed from when the renderer was started or from when dropped frames were last reported
     * (whichever was more recent), and not from when the first of the reported drops occurred.
     */
    fun onDroppedVideoFrames(eventTime: EventTime?, droppedFrames: Int, elapsedMs: Long) {}

    /**
     * Called when a video renderer releases a decoder.
     *
     * @param eventTime The event time.
     * @param decoderName The decoder that was released.
     */
    fun onVideoDecoderReleased(eventTime: EventTime?, decoderName: String?) {}

    /**
     * Called when a video renderer is disabled.
     *
     * @param eventTime The event time.
     * @param decoderCounters [DecoderCounters] that were updated by the renderer.
     */
    fun onVideoDisabled(eventTime: EventTime?, decoderCounters: DecoderCounters?) {}

    /**
     * Called when there is an update to the video frame processing offset reported by a video
     * renderer.
     *
     *
     * The processing offset for a video frame is the difference between the time at which the
     * frame became available to render, and the time at which it was scheduled to be rendered. A
     * positive value indicates the frame became available early enough, whereas a negative value
     * indicates that the frame wasn't available until after the time at which it should have been
     * rendered.
     *
     * @param eventTime The event time.
     * @param totalProcessingOffsetUs The sum of the video frame processing offsets for frames
     * rendered since the last call to this method.
     * @param frameCount The number to samples included in `totalProcessingOffsetUs`.
     */
    fun onVideoFrameProcessingOffset(
        eventTime: EventTime?, totalProcessingOffsetUs: Long, frameCount: Int
    ) {
    }

    /**
     * Called when a video decoder encounters an error.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param eventTime The event time.
     * @param videoCodecError The error. Typically a [CodecException] if the renderer uses
     * [MediaCodec], or a [DecoderException] if the renderer uses a software decoder.
     */
    fun onVideoCodecError(eventTime: EventTime?, videoCodecError: Exception?) {}

    /**
     * Called when a frame is rendered for the first time since setting the surface, or since the
     * renderer was reset, or since the stream being rendered was changed.
     *
     * @param eventTime The event time.
     * @param output The output to which a frame has been rendered. Normally a [Surface],
     * however may also be other output types (e.g., a [VideoDecoderOutputBufferRenderer]).
     * @param renderTimeMs [SystemClock.elapsedRealtime] when the first frame was rendered.
     */
    fun onRenderedFirstFrame(eventTime: EventTime?, output: Any?, renderTimeMs: Long) {}

    /**
     * Called before a frame is rendered for the first time since setting the surface, and each time
     * there's a change in the size or pixel aspect ratio of the video being rendered.
     *
     * @param eventTime The event time.
     * @param videoSize The new size of the video.
     */
    fun onVideoSizeChanged(eventTime: EventTime?, videoSize: VideoSize?) {}


    @Deprecated("Implement {@link #onVideoSizeChanged(EventTime eventTime, VideoSize)} instead.")
    fun onVideoSizeChanged(
        eventTime: EventTime?,
        width: Int,
        height: Int,
        unappliedRotationDegrees: Int,
        pixelWidthHeightRatio: Float
    ) {
    }

    /**
     * Called when the output surface size changed.
     *
     * @param eventTime The event time.
     * @param width The surface width in pixels. May be [C.LENGTH_UNSET] if unknown, or 0 if the
     * video is not rendered onto a surface.
     * @param height The surface height in pixels. May be [C.LENGTH_UNSET] if unknown, or 0 if
     * the video is not rendered onto a surface.
     */
    fun onSurfaceSizeChanged(eventTime: EventTime?, width: Int, height: Int) {}


    @Deprecated("Implement {@link #onDrmSessionAcquired(EventTime, int)} instead.")
    fun onDrmSessionAcquired(eventTime: EventTime?) {
    }

    /**
     * Called each time a drm session is acquired.
     *
     * @param eventTime The event time.
     * @param state The [DrmSession.State] of the session when the acquisition completed.
     */
    fun onDrmSessionAcquired(eventTime: EventTime?, @DrmSession.State state: Int) {}

    /**
     * Called each time drm keys are loaded.
     *
     * @param eventTime The event time.
     */
    fun onDrmKeysLoaded(eventTime: EventTime?) {}

    /**
     * Called when a drm error occurs.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param eventTime The event time.
     * @param error The error.
     */
    fun onDrmSessionManagerError(eventTime: EventTime?, error: Exception?) {}

    /**
     * Called each time offline drm keys are restored.
     *
     * @param eventTime The event time.
     */
    fun onDrmKeysRestored(eventTime: EventTime?) {}

    /**
     * Called each time offline drm keys are removed.
     *
     * @param eventTime The event time.
     */
    fun onDrmKeysRemoved(eventTime: EventTime?) {}

    /**
     * Called each time a drm session is released.
     *
     * @param eventTime The event time.
     */
    fun onDrmSessionReleased(eventTime: EventTime?) {}

    /**
     * Called when the [Player] is released.
     *
     * @param eventTime The event time.
     */
    fun onPlayerReleased(eventTime: EventTime?) {}

    /**
     * Called after one or more events occurred.
     *
     *
     * State changes and events that happen within one [Looper] message queue iteration are
     * reported together and only after all individual callbacks were triggered.
     *
     *
     * Listeners should prefer this method over individual callbacks in the following cases:
     *
     *
     *  * They intend to trigger the same logic for multiple events (e.g. when updating a UI for
     * both [.onPlaybackStateChanged] and [       ][.onPlayWhenReadyChanged]).
     *  * They need access to the [Player] object to trigger further events (e.g. to call
     * [Player.seekTo] after a [       ][AnalyticsListener.onMediaItemTransition]).
     *  * They intend to use multiple state values together or in combination with [Player]
     * getter methods. For example using [Player.getCurrentMediaItemIndex] with the
     * `timeline` provided in [.onTimelineChanged] is only safe from
     * within this method.
     *  * They are interested in events that logically happened together (e.g [       ][.onPlaybackStateChanged] to [Player.STATE_BUFFERING] because of
     * [.onMediaItemTransition]).
     *
     *
     * @param player The [Player].
     * @param events The [Events] that occurred in this iteration.
     */
    fun onEvents(player: Player?, events: Events?) {}
}