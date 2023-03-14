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
package com.google.android.exoplayer2

import android.os.Bundle
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselectionimport.TrackSelectionParameters
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.util.Size
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2import.DeviceInfo
import com.google.android.exoplayer2import.MediaMetadata
import com.google.android.exoplayer2import.PlaybackException
import com.google.android.exoplayer2import.PlaybackParameters
import com.google.common.base.Objects
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

/**
 * A media player interface defining traditional high-level functionality, such as the ability to
 * play, pause, seek and query properties of the currently playing media.
 *
 *
 * This interface includes some convenience methods that can be implemented by calling other
 * methods in the interface. [BasePlayer] implements these convenience methods so inheriting
 * [BasePlayer] is recommended when implementing the interface so that only the minimal set of
 * required methods can be implemented.
 *
 *
 * Some important properties of media players that implement this interface are:
 *
 *
 *  * They can provide a [Timeline] representing the structure of the media being played,
 * which can be obtained by calling [.getCurrentTimeline].
 *  * They can provide a [Tracks] defining the currently available tracks and which are
 * selected to be rendered, which can be obtained by calling [.getCurrentTracks].
 *
 */
interface Player {
    /** A set of [events][Event].  */
    class Events {
        private var flags: FlagSet

        /**
         * Creates an instance.
         *
         * @param flags The [FlagSet] containing the [events][Event].
         */

        constructor(flags: FlagSet) {
            this.flags = flags
        }

        operator fun contains(@Event event: Int): Boolean {
            return flags.contains(event)
        }

        /**
         * Returns whether any of the given [events][Event] occurred.
         *
         * @param events The [events][Event].
         * @return Whether any of the [events][Event] occurred.
         */
        fun containsAny(vararg events: Int): Boolean {
            return flags.containsAny(*events)
        }

        /** Returns the number of events in the set.  */
        fun size(): Int {
            return flags.size()
        }

        /**
         * Returns the [Event] at the given index.
         *
         *
         * Although index-based access is possible, it doesn't imply a particular order of these
         * events.
         *
         * @param index The index. Must be between 0 (inclusive) and [.size] (exclusive).
         * @return The [Event] at the given index.
         * @throws IndexOutOfBoundsException If index is outside the allowed range.
         */
        @Event
        operator fun get(index: Int): Int {
            return flags[index]
        }

        override fun hashCode(): Int {
            return flags.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is Events) {
                return false
            }
            return (flags == other.flags)
        }
    }

    /** Position info describing a playback position involved in a discontinuity.  */
    class PositionInfo : Bundleable {

        /**
         * The UID of the window, or `null` if the timeline is [empty][Timeline.isEmpty].
         */
        var windowUid: Any? = null

        @Deprecated("Use {@link #mediaItemIndex} instead.")
        var windowIndex = 0

        /** The media item index.  */
        var mediaItemIndex = 0

        /** The media item, or `null` if the timeline is [empty][Timeline.isEmpty].  */
        var mediaItem: MediaItem? = null

        /**
         * The UID of the period, or `null` if the timeline is [empty][Timeline.isEmpty].
         */
        var periodUid: Any? = null

        /** The period index.  */
        var periodIndex = 0

        /** The playback position, in milliseconds.  */
        var positionMs: Long = 0

        /**
         * The content position, in milliseconds.
         *
         *
         * If [.adGroupIndex] is [C.INDEX_UNSET], this is the same as [ ][.positionMs].
         */
        var contentPositionMs: Long = 0

        /**
         * The ad group index if the playback position is within an ad, [C.INDEX_UNSET] otherwise.
         */
        var adGroupIndex = 0

        /**
         * The index of the ad within the ad group if the playback position is within an ad, [ ][C.INDEX_UNSET] otherwise.
         */
        var adIndexInAdGroup = 0


        @Deprecated("""Use {@link #PositionInfo(Object, int, MediaItem, Object, int, long, long, int,
     *     int)} instead.""")
        constructor(
                windowUid: Any?,
                mediaItemIndex: Int,
                periodUid: Any?,
                periodIndex: Int,
                positionMs: Long,
                contentPositionMs: Long,
                adGroupIndex: Int,
                adIndexInAdGroup: Int) : this(
                windowUid,
                mediaItemIndex,
                MediaItem.EMPTY,
                periodUid,
                periodIndex,
                positionMs,
                contentPositionMs,
                adGroupIndex,
                adIndexInAdGroup)

        /** Creates an instance.  */
        constructor(
                windowUid: Any?,
                mediaItemIndex: Int,
                mediaItem: MediaItem?,
                periodUid: Any?,
                periodIndex: Int,
                positionMs: Long,
                contentPositionMs: Long,
                adGroupIndex: Int,
                adIndexInAdGroup: Int) {

            this.windowUid = windowUid
            windowIndex = mediaItemIndex
            this.mediaItemIndex = mediaItemIndex
            this.mediaItem = mediaItem
            this.periodUid = periodUid
            this.periodIndex = periodIndex
            this.positionMs = positionMs
            this.contentPositionMs = contentPositionMs
            this.adGroupIndex = adGroupIndex
            this.adIndexInAdGroup = adIndexInAdGroup
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as PositionInfo
            return (mediaItemIndex == that.mediaItemIndex && periodIndex == that.periodIndex && positionMs == that.positionMs && contentPositionMs == that.contentPositionMs && adGroupIndex == that.adGroupIndex && adIndexInAdGroup == that.adIndexInAdGroup && Objects.equal(windowUid, that.windowUid)
                    && Objects.equal(periodUid, that.periodUid)
                    && Objects.equal(mediaItem, that.mediaItem))
        }

        override fun hashCode(): Int {
            return Objects.hashCode(
                    windowUid,
                    mediaItemIndex,
                    mediaItem,
                    periodUid,
                    periodIndex,
                    positionMs,
                    contentPositionMs,
                    adGroupIndex,
                    adIndexInAdGroup)
        }

        // Bundleable implementation.
        @Documented
        @Retention(AnnotationRetention.SOURCE)
        @Target(TYPE_USE)
        @IntDef([
            FIELD_MEDIA_ITEM_INDEX,
            FIELD_MEDIA_ITEM,
            FIELD_PERIOD_INDEX,
            FIELD_POSITION_MS,
            FIELD_CONTENT_POSITION_MS,
            FIELD_AD_GROUP_INDEX,
            FIELD_AD_INDEX_IN_AD_GROUP
        ])
        private annotation class FieldNumber

        companion object {
            private const val FIELD_MEDIA_ITEM_INDEX = 0
            private const val FIELD_MEDIA_ITEM = 1
            private const val FIELD_PERIOD_INDEX = 2
            private const val FIELD_POSITION_MS = 3
            private const val FIELD_CONTENT_POSITION_MS = 4
            private const val FIELD_AD_GROUP_INDEX = 5
            private const val FIELD_AD_INDEX_IN_AD_GROUP = 6
        }

        /**
         * {@inheritDoc}
         *
         *
         * It omits the [.windowUid] and [.periodUid] fields. The [.windowUid] and
         * [.periodUid] of an instance restored by [.CREATOR] will always be `null`.
         */
        override fun toBundle(): Bundle {
            val bundle = Bundle()
            bundle.putInt(keyForField(FIELD_MEDIA_ITEM_INDEX), mediaItemIndex)
            if (mediaItem != null) {
                bundle.putBundle(keyForField(FIELD_MEDIA_ITEM), mediaItem.toBundle())
            }
            bundle.putInt(keyForField(FIELD_PERIOD_INDEX), periodIndex)
            bundle.putLong(keyForField(FIELD_POSITION_MS), positionMs)
            bundle.putLong(keyForField(FIELD_CONTENT_POSITION_MS), contentPositionMs)
            bundle.putInt(keyForField(FIELD_AD_GROUP_INDEX), adGroupIndex)
            bundle.putInt(keyForField(FIELD_AD_INDEX_IN_AD_GROUP), adIndexInAdGroup)
            return bundle
        }

        /** Object that can restore [PositionInfo] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<PositionInfo> = Bundleable.Creator<PositionInfo> { obj: PositionInfo, bundle: Bundle -> obj.fromBundle(bundle) }

        private fun fromBundle(bundle: Bundle): PositionInfo {
            val mediaItemIndex = bundle.getInt(keyForField(FIELD_MEDIA_ITEM_INDEX),  /* defaultValue= */C.INDEX_UNSET)
            val mediaItemBundle = bundle.getBundle(keyForField(FIELD_MEDIA_ITEM))
            val mediaItem = if (mediaItemBundle == null) null else MediaItem.CREATOR.fromBundle(mediaItemBundle)
            val periodIndex = bundle.getInt(keyForField(FIELD_PERIOD_INDEX),  /* defaultValue= */C.INDEX_UNSET)
            val positionMs = bundle.getLong(keyForField(FIELD_POSITION_MS),  /* defaultValue= */C.TIME_UNSET)
            val contentPositionMs = bundle.getLong(keyForField(FIELD_CONTENT_POSITION_MS),  /* defaultValue= */C.TIME_UNSET)
            val adGroupIndex = bundle.getInt(keyForField(FIELD_AD_GROUP_INDEX),  /* defaultValue= */C.INDEX_UNSET)
            val adIndexInAdGroup = bundle.getInt(keyForField(FIELD_AD_INDEX_IN_AD_GROUP),  /* defaultValue= */C.INDEX_UNSET)
            return PositionInfo( /* windowUid= */
                    null,
                    mediaItemIndex,
                    mediaItem,  /* periodUid= */
                    null,
                    periodIndex,
                    positionMs,
                    contentPositionMs,
                    adGroupIndex,
                    adIndexInAdGroup)
        }

        private fun keyForField(@FieldNumber field: Int): String? {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }

    /**
     * A set of [commands][Command].
     *
     *
     * Instances are immutable.
     */
    class Commands private constructor(private val flags: FlagSet?) : Bundleable {
        /** A builder for [Commands] instances.  */
        class Builder {
            private val flagsBuilder: FlagSet.Builder

            /** Creates a builder.  */
            constructor() {
                flagsBuilder = FlagSet.Builder()
            }

            constructor(commands: Commands) {
                flagsBuilder = FlagSet.Builder()
                flagsBuilder.addAll(commands.flags)
            }

            /**
             * Adds a [Command].
             *
             * @param command A [Command].
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun add(@Command command: Int): Builder {
                flagsBuilder.add(command)
                return this
            }

            /**
             * Adds a [Command] if the provided condition is true. Does nothing otherwise.
             *
             * @param command A [Command].
             * @param condition A condition.
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun addIf(@Command command: Int, condition: Boolean): Builder {
                flagsBuilder.addIf(command, condition)
                return this
            }

            /**
             * Adds [commands][Command].
             *
             * @param commands The [commands][Command] to add.
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun addAll(vararg commands: Int): Builder {
                flagsBuilder.addAll(*commands)
                return this
            }

            /**
             * Adds [Commands].
             *
             * @param commands The set of [commands][Command] to add.
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun addAll(commands: Commands): Builder {
                flagsBuilder.addAll(commands.flags)
                return this
            }

            /**
             * Adds all existing [commands][Command].
             *
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun addAllCommands(): Builder {
                flagsBuilder.addAll(*SUPPORTED_COMMANDS)
                return this
            }

            /**
             * Removes a [Command].
             *
             * @param command A [Command].
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun remove(@Command command: Int): Builder {
                flagsBuilder.remove(command)
                return this
            }

            /**
             * Removes a [Command] if the provided condition is true. Does nothing otherwise.
             *
             * @param command A [Command].
             * @param condition A condition.
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun removeIf(@Command command: Int, condition: Boolean): Builder {
                flagsBuilder.removeIf(command, condition)
                return this
            }

            /**
             * Removes [commands][Command].
             *
             * @param commands The [commands][Command] to remove.
             * @return This builder.
             * @throws IllegalStateException If [.build] has already been called.
             */
            @CanIgnoreReturnValue
            fun removeAll(vararg commands: Int): Builder {
                flagsBuilder.removeAll(*commands)
                return this
            }

            /**
             * Builds a [Commands] instance.
             *
             * @throws IllegalStateException If this method has already been called.
             */
            fun build(): Commands {
                return Commands(flagsBuilder.build())
            }

            companion object {
                private val SUPPORTED_COMMANDS: IntArray = intArrayOf(
                        COMMAND_PLAY_PAUSE,
                        COMMAND_PREPARE,
                        COMMAND_STOP,
                        COMMAND_SEEK_TO_DEFAULT_POSITION,
                        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        COMMAND_SEEK_TO_PREVIOUS,
                        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        COMMAND_SEEK_TO_NEXT,
                        COMMAND_SEEK_TO_MEDIA_ITEM,
                        COMMAND_SEEK_BACK,
                        COMMAND_SEEK_FORWARD,
                        COMMAND_SET_SPEED_AND_PITCH,
                        COMMAND_SET_SHUFFLE_MODE,
                        COMMAND_SET_REPEAT_MODE,
                        COMMAND_GET_CURRENT_MEDIA_ITEM,
                        COMMAND_GET_TIMELINE,
                        COMMAND_GET_MEDIA_ITEMS_METADATA,
                        COMMAND_SET_MEDIA_ITEMS_METADATA,
                        COMMAND_SET_MEDIA_ITEM,
                        COMMAND_CHANGE_MEDIA_ITEMS,
                        COMMAND_GET_AUDIO_ATTRIBUTES,
                        COMMAND_GET_VOLUME,
                        COMMAND_GET_DEVICE_VOLUME,
                        COMMAND_SET_VOLUME,
                        COMMAND_SET_DEVICE_VOLUME,
                        COMMAND_ADJUST_DEVICE_VOLUME,
                        COMMAND_SET_VIDEO_SURFACE,
                        COMMAND_GET_TEXT,
                        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                        COMMAND_GET_TRACKS)
            }
        }

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        /** Returns whether the set of commands contains the specified [Command].  */
        operator fun contains(@Command command: Int): Boolean {
            return flags!!.contains(command)
        }

        /** Returns whether the set of commands contains at least one of the given `commands`.  */
        fun containsAny(vararg commands: Int): Boolean {
            return flags!!.containsAny(*commands)
        }

        /** Returns the number of commands in this set.  */
        fun size(): Int {
            return flags!!.size()
        }

        /**
         * Returns the [Command] at the given index.
         *
         * @param index The index. Must be between 0 (inclusive) and [.size] (exclusive).
         * @return The [Command] at the given index.
         * @throws IndexOutOfBoundsException If index is outside the allowed range.
         */
        @Command
        operator fun get(index: Int): Int {
            return flags!![index]
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj !is Commands) {
                return false
            }
            return (flags == obj.flags)
        }

        public override fun hashCode(): Int {
            return flags.hashCode()
        }

        // Bundleable implementation.
        @Documented
        @java.lang.annotation.Retention(RetentionPolicy.SOURCE)
        @java.lang.annotation.Target(ElementType.TYPE_USE)
        @IntDef([PositionInfo.FIELD_MEDIA_ITEM_INDEX, PositionInfo.FIELD_MEDIA_ITEM, PositionInfo.FIELD_PERIOD_INDEX, PositionInfo.FIELD_POSITION_MS, PositionInfo.FIELD_CONTENT_POSITION_MS, PositionInfo.FIELD_AD_GROUP_INDEX, PositionInfo.FIELD_AD_INDEX_IN_AD_GROUP])
        private annotation class FieldNumber

        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            val commandsBundle: ArrayList<Int> = ArrayList()
            for (i in 0 until flags!!.size()) {
                commandsBundle.add(flags[i])
            }
            bundle.putIntegerArrayList(keyForField(FIELD_COMMANDS), commandsBundle)
            return bundle
        }

        companion object {
            /** An empty set of commands.  */
            val EMPTY: Commands = Builder().build()
            private const val FIELD_COMMANDS: Int = 0

            /** Object that can restore [Commands] from a [Bundle].  */
            val CREATOR: Bundleable.Creator<Commands> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
            private fun fromBundle(bundle: Bundle): Commands {
                val commands: ArrayList<Int>? = bundle.getIntegerArrayList(keyForField(FIELD_COMMANDS))
                if (commands == null) {
                    return EMPTY
                }
                val builder: Builder = Builder()
                for (i in commands.indices) {
                    builder.add(commands[i])
                }
                return builder.build()
            }

            private fun keyForField(@FieldNumber field: Int): String {
                return field.toString(Character.MAX_RADIX)
            }
        }
    }

    /**
     * Listener of all changes in the Player.
     *
     *
     * All methods have no-op default implementations to allow selective overrides.
     */
    interface Listener {
        /**
         * Called when one or more player states changed.
         *
         *
         * State changes and events that happen within one [Looper] message queue iteration are
         * reported together and only after all individual callbacks were triggered.
         *
         *
         * Only state changes represented by [events][Event] are reported through this method.
         *
         *
         * Listeners should prefer this method over individual callbacks in the following cases:
         *
         *
         *  * They intend to trigger the same logic for multiple events (e.g. when updating a UI for
         * both [.onPlaybackStateChanged] and [.onPlayWhenReadyChanged]).
         *  * They need access to the [Player] object to trigger further events (e.g. to call
         * [Player.seekTo] after a [.onMediaItemTransition]).
         *  * They intend to use multiple state values together or in combination with [Player]
         * getter methods. For example using [.getCurrentMediaItemIndex] with the `timeline` provided in [.onTimelineChanged] is only safe from
         * within this method.
         *  * They are interested in events that logically happened together (e.g [       ][.onPlaybackStateChanged] to [.STATE_BUFFERING] because of [       ][.onMediaItemTransition]).
         *
         *
         * @param player The [Player] whose state changed. Use the getters to obtain the latest
         * states.
         * @param events The [Events] that happened in this iteration, indicating which player
         * states changed.
         */
        fun onEvents(player: Player?, events: Events?) {}

        /**
         * Called when the timeline has been refreshed.
         *
         *
         * Note that the current [MediaItem] or playback position may change as a result of a
         * timeline change. If playback can't continue smoothly because of this timeline change, a
         * separate [.onPositionDiscontinuity] callback will be
         * triggered.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param timeline The latest timeline. Never null, but may be empty.
         * @param reason The [TimelineChangeReason] responsible for this timeline change.
         */
        fun onTimelineChanged(timeline: Timeline?, reason: @TimelineChangeReason Int) {}

        /**
         * Called when playback transitions to a media item or starts repeating a media item according
         * to the current [repeat mode][.getRepeatMode].
         *
         *
         * Note that this callback is also called when the playlist becomes non-empty or empty as a
         * consequence of a playlist change.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param mediaItem The [MediaItem]. May be null if the playlist becomes empty.
         * @param reason The reason for the transition.
         */
        fun onMediaItemTransition(
                mediaItem: MediaItem?, reason: @MediaItemTransitionReason Int) {
        }

        /**
         * Called when the tracks change.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param tracks The available tracks information. Never null, but may be of length zero.
         */
        fun onTracksChanged(tracks: Tracks?) {}

        /**
         * Called when the combined [MediaMetadata] changes.
         *
         *
         * The provided [MediaMetadata] is a combination of the [ MediaItem metadata][MediaItem.mediaMetadata], the static metadata in the media's [Format][Format.metadata], and
         * any timed metadata that has been parsed from the media and output via [ ][Listener.onMetadata]. If a field is populated in the [ ][MediaItem.mediaMetadata], it will be prioritised above the same field coming from static or
         * timed metadata.
         *
         *
         * This method may be called multiple times in quick succession.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param mediaMetadata The combined [MediaMetadata].
         */
        fun onMediaMetadataChanged(mediaMetadata: MediaMetadata?) {}

        /**
         * Called when the playlist [MediaMetadata] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         */
        fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata?) {}

        /**
         * Called when the player starts or stops loading the source.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param isLoading Whether the source is currently being loaded.
         */
        fun onIsLoadingChanged(isLoading: Boolean) {}

        @Deprecated("Use {@link #onIsLoadingChanged(boolean)} instead.")
        fun onLoadingChanged(isLoading: Boolean) {
        }

        /**
         * Called when the value returned from [.isCommandAvailable] changes for at least one
         * [Command].
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param availableCommands The available [Commands].
         */
        fun onAvailableCommandsChanged(availableCommands: Commands?) {}

        /**
         * Called when the value returned from [.getTrackSelectionParameters] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param parameters The new [TrackSelectionParameters].
         */
        fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters?) {}

        @Deprecated("Use {@link #onPlaybackStateChanged(int)} and {@link\n" + "     *     #onPlayWhenReadyChanged(boolean, int)} instead.")
        fun onPlayerStateChanged(playWhenReady: Boolean, @State playbackState: Int) {
        }

        /**
         * Called when the value returned from [.getPlaybackState] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param playbackState The new playback [state][State].
         */
        fun onPlaybackStateChanged(@State playbackState: Int) {}

        /**
         * Called when the value returned from [.getPlayWhenReady] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param playWhenReady Whether playback will proceed when ready.
         * @param reason The [reason][PlayWhenReadyChangeReason] for the change.
         */
        fun onPlayWhenReadyChanged(
                playWhenReady: Boolean, @PlayWhenReadyChangeReason reason: Int) {
        }

        /**
         * Called when the value returned from [.getPlaybackSuppressionReason] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param playbackSuppressionReason The current [PlaybackSuppressionReason].
         */
        fun onPlaybackSuppressionReasonChanged(@PlaybackSuppressionReason
                                               playbackSuppressionReason: Int) {
        }

        /**
         * Called when the value of [.isPlaying] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param isPlaying Whether the player is playing.
         */
        fun onIsPlayingChanged(isPlaying: Boolean) {}

        /**
         * Called when the value of [.getRepeatMode] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param repeatMode The [RepeatMode] used for playback.
         */
        fun onRepeatModeChanged(@RepeatMode repeatMode: Int) {}

        /**
         * Called when the value of [.getShuffleModeEnabled] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param shuffleModeEnabled Whether shuffling of [media items][MediaItem] is enabled.
         */
        fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

        /**
         * Called when an error occurs. The playback state will transition to [.STATE_IDLE]
         * immediately after this method is called. The player instance can still be used, and [ ][.release] must still be called on the player should it no longer be required.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         *
         * Implementations of Player may pass an instance of a subclass of [PlaybackException]
         * to this method in order to include more information about the error.
         *
         * @param error The error.
         */
        fun onPlayerError(error: PlaybackException?) {}

        /**
         * Called when the [PlaybackException] returned by [.getPlayerError] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         *
         * Implementations of Player may pass an instance of a subclass of [PlaybackException]
         * to this method in order to include more information about the error.
         *
         * @param error The new error, or null if the error is being cleared.
         */
        fun onPlayerErrorChanged(error: PlaybackException?) {}

        @Deprecated("Use {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} instead.")
        fun onPositionDiscontinuity(@DiscontinuityReason reason: Int) {
        }

        /**
         * Called when a position discontinuity occurs.
         *
         *
         * A position discontinuity occurs when the playing period changes, the playback position
         * jumps within the period currently being played, or when the playing period has been skipped
         * or removed.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param oldPosition The position before the discontinuity.
         * @param newPosition The position after the discontinuity.
         * @param reason The [DiscontinuityReason] responsible for the discontinuity.
         */
        fun onPositionDiscontinuity(
                oldPosition: PositionInfo?, newPosition: PositionInfo?, @DiscontinuityReason reason: Int) {
        }

        /**
         * Called when the current playback parameters change. The playback parameters may change due to
         * a call to [.setPlaybackParameters], or the player itself may change
         * them (for example, if audio playback switches to passthrough or offload mode, where speed
         * adjustment is no longer possible).
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param playbackParameters The playback parameters.
         */
        fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {}

        /**
         * Called when the value of [.getSeekBackIncrement] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param seekBackIncrementMs The [.seekBack] increment, in milliseconds.
         */
        fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {}

        /**
         * Called when the value of [.getSeekForwardIncrement] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param seekForwardIncrementMs The [.seekForward] increment, in milliseconds.
         */
        fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {}

        /**
         * Called when the value of [.getMaxSeekToPreviousPosition] changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param maxSeekToPreviousPositionMs The maximum position for which [.seekToPrevious]
         * seeks to the previous position, in milliseconds.
         */
        fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {}

        @Deprecated("Seeks are processed without delay. Listen to {@link\n" + "     *     #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} with reason {@link\n" + "     *     #DISCONTINUITY_REASON_SEEK} instead.")
        fun onSeekProcessed() {
        }

        /**
         * Called when the audio session ID changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param audioSessionId The audio session ID.
         */
        fun onAudioSessionIdChanged(audioSessionId: Int) {}

        /**
         * Called when the audio attributes change.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param audioAttributes The audio attributes.
         */
        fun onAudioAttributesChanged(audioAttributes: AudioAttributes?) {}

        /**
         * Called when the volume changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param volume The new volume, with 0 being silence and 1 being unity gain.
         */
        fun onVolumeChanged(volume: Float) {}

        /**
         * Called when skipping silences is enabled or disabled in the audio stream.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
         */
        fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}

        /**
         * Called when the device information changes
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param deviceInfo The new [DeviceInfo].
         */
        fun onDeviceInfoChanged(deviceInfo: DeviceInfo?) {}

        /**
         * Called when the device volume or mute state changes.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param volume The new device volume, with 0 being silence and 1 being unity gain.
         * @param muted Whether the device is muted.
         */
        fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {}

        /**
         * Called each time there's a change in the size of the video being rendered.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param videoSize The new size of the video.
         */
        fun onVideoSizeChanged(videoSize: VideoSize?) {}

        /**
         * Called each time there's a change in the size of the surface onto which the video is being
         * rendered.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param width The surface width in pixels. May be [C.LENGTH_UNSET] if unknown, or 0 if
         * the video is not rendered onto a surface.
         * @param height The surface height in pixels. May be [C.LENGTH_UNSET] if unknown, or 0 if
         * the video is not rendered onto a surface.
         */
        fun onSurfaceSizeChanged(width: Int, height: Int) {}

        /**
         * Called when a frame is rendered for the first time since setting the surface, or since the
         * renderer was reset, or since the stream being rendered was changed.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         */
        fun onRenderedFirstFrame() {}

        /**
         * Called when there is a change in the [Cues][Cue].
         *
         *
         * Both [.onCues] and [.onCues] are called when there is a change
         * in the cues. You should only implement one or the other.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         */
        @Deprecated("Use {@link #onCues(CueGroup)} instead.")
        fun onCues(cues: List<Cue?>?) {
        }

        /**
         * Called when there is a change in the [CueGroup].
         *
         *
         * Both [.onCues] and [.onCues] are called when there is a change
         * in the cues. You should only implement one or the other.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         */
        fun onCues(cueGroup: CueGroup?) {}

        /**
         * Called when there is metadata associated with the current playback time.
         *
         *
         * [.onEvents] will also be called to report this event along with
         * other events that happen in the same [Looper] message queue iteration.
         *
         * @param metadata The metadata.
         */
        fun onMetadata(metadata: Metadata?) {}
    }

    /**
     * Playback state. One of [.STATE_IDLE], [.STATE_BUFFERING], [.STATE_READY] or
     * [.STATE_ENDED].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED])
    annotation class State

    /**
     * Reasons for [playWhenReady][.getPlayWhenReady] changes. One of [ ][.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST], [ ][.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS], [ ][.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY], [ ][.PLAY_WHEN_READY_CHANGE_REASON_REMOTE] or [ ][.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS, PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY, PLAY_WHEN_READY_CHANGE_REASON_REMOTE, PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM])
    annotation class PlayWhenReadyChangeReason

    /**
     * Reason why playback is suppressed even though [.getPlayWhenReady] is `true`. One
     * of [.PLAYBACK_SUPPRESSION_REASON_NONE] or [ ][.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([PLAYBACK_SUPPRESSION_REASON_NONE, PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS])
    annotation class PlaybackSuppressionReason

    /**
     * Repeat modes for playback. One of [.REPEAT_MODE_OFF], [.REPEAT_MODE_ONE] or [ ][.REPEAT_MODE_ALL].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL])
    annotation class RepeatMode

    /**
     * Reasons for position discontinuities. One of [.DISCONTINUITY_REASON_AUTO_TRANSITION],
     * [.DISCONTINUITY_REASON_SEEK], [.DISCONTINUITY_REASON_SEEK_ADJUSTMENT], [ ][.DISCONTINUITY_REASON_SKIP], [.DISCONTINUITY_REASON_REMOVE] or [ ][.DISCONTINUITY_REASON_INTERNAL].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([DISCONTINUITY_REASON_AUTO_TRANSITION, DISCONTINUITY_REASON_SEEK, DISCONTINUITY_REASON_SEEK_ADJUSTMENT, DISCONTINUITY_REASON_SKIP, DISCONTINUITY_REASON_REMOVE, DISCONTINUITY_REASON_INTERNAL])
    annotation class DiscontinuityReason

    /**
     * Reasons for timeline changes. One of [.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED] or [ ][.TIMELINE_CHANGE_REASON_SOURCE_UPDATE].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
    @IntDef([TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, TIMELINE_CHANGE_REASON_SOURCE_UPDATE])
    annotation class TimelineChangeReason

    /**
     * Reasons for media item transitions. One of [.MEDIA_ITEM_TRANSITION_REASON_REPEAT], [ ][.MEDIA_ITEM_TRANSITION_REASON_AUTO], [.MEDIA_ITEM_TRANSITION_REASON_SEEK] or [ ][.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE)
    @IntDef([MEDIA_ITEM_TRANSITION_REASON_REPEAT, MEDIA_ITEM_TRANSITION_REASON_AUTO, MEDIA_ITEM_TRANSITION_REASON_SEEK, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED])
    annotation class MediaItemTransitionReason

    /**
     * Events that can be reported via [Listener.onEvents].
     *
     *
     * One of the [Player]`.EVENT_*` values.
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([EVENT_TIMELINE_CHANGED, EVENT_MEDIA_ITEM_TRANSITION, EVENT_TRACKS_CHANGED, EVENT_IS_LOADING_CHANGED, EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED, EVENT_IS_PLAYING_CHANGED, EVENT_REPEAT_MODE_CHANGED, EVENT_SHUFFLE_MODE_ENABLED_CHANGED, EVENT_PLAYER_ERROR, EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_PARAMETERS_CHANGED, EVENT_AVAILABLE_COMMANDS_CHANGED, EVENT_MEDIA_METADATA_CHANGED, EVENT_PLAYLIST_METADATA_CHANGED, EVENT_SEEK_BACK_INCREMENT_CHANGED, EVENT_SEEK_FORWARD_INCREMENT_CHANGED, EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED, EVENT_TRACK_SELECTION_PARAMETERS_CHANGED, EVENT_AUDIO_ATTRIBUTES_CHANGED, EVENT_AUDIO_SESSION_ID, EVENT_VOLUME_CHANGED, EVENT_SKIP_SILENCE_ENABLED_CHANGED, EVENT_SURFACE_SIZE_CHANGED, EVENT_VIDEO_SIZE_CHANGED, EVENT_RENDERED_FIRST_FRAME, EVENT_CUES, EVENT_METADATA, EVENT_DEVICE_INFO_CHANGED, EVENT_DEVICE_VOLUME_CHANGED])
    annotation class Event

    /**
     * Commands that can be executed on a `Player`. One of [.COMMAND_PLAY_PAUSE], [ ][.COMMAND_PREPARE], [.COMMAND_STOP], [.COMMAND_SEEK_TO_DEFAULT_POSITION], [ ][.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM], [.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM], [ ][.COMMAND_SEEK_TO_PREVIOUS], [.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM], [ ][.COMMAND_SEEK_TO_NEXT], [.COMMAND_SEEK_TO_MEDIA_ITEM], [.COMMAND_SEEK_BACK], [ ][.COMMAND_SEEK_FORWARD], [.COMMAND_SET_SPEED_AND_PITCH], [ ][.COMMAND_SET_SHUFFLE_MODE], [.COMMAND_SET_REPEAT_MODE], [ ][.COMMAND_GET_CURRENT_MEDIA_ITEM], [.COMMAND_GET_TIMELINE], [ ][.COMMAND_GET_MEDIA_ITEMS_METADATA], [.COMMAND_SET_MEDIA_ITEMS_METADATA], [ ][.COMMAND_CHANGE_MEDIA_ITEMS], [.COMMAND_GET_AUDIO_ATTRIBUTES], [ ][.COMMAND_GET_VOLUME], [.COMMAND_GET_DEVICE_VOLUME], [.COMMAND_SET_VOLUME], [ ][.COMMAND_SET_DEVICE_VOLUME], [.COMMAND_ADJUST_DEVICE_VOLUME], [ ][.COMMAND_SET_VIDEO_SURFACE], [.COMMAND_GET_TEXT], [ ][.COMMAND_SET_TRACK_SELECTION_PARAMETERS], [.COMMAND_GET_TRACKS] or [ ][.COMMAND_SET_MEDIA_ITEM].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([COMMAND_INVALID, COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEM, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS, COMMAND_GET_TRACKS])
    annotation class Command

    /**
     * Returns the [Looper] associated with the application thread that's used to access the
     * player and on which player events are received.
     */
    fun getApplicationLooper(): Looper?

    /**
     * Registers a listener to receive all events from the player.
     *
     *
     * The listener's methods will be called on the thread associated with [ ][.getApplicationLooper].
     *
     * @param listener The listener to register.
     */
    fun addListener(listener: Listener?)

    /**
     * Unregister a listener registered through [.addListener]. The listener will no
     * longer receive events.
     *
     * @param listener The listener to unregister.
     */
    fun removeListener(listener: Listener?)

    /**
     * Clears the playlist, adds the specified [MediaItems][MediaItem] and resets the position to
     * the default position.
     *
     * @param mediaItems The new [MediaItems][MediaItem].
     */
    fun setMediaItems(mediaItems: List<MediaItem?>?)

    /**
     * Clears the playlist and adds the specified [MediaItems][MediaItem].
     *
     * @param mediaItems The new [MediaItems][MediaItem].
     * @param resetPosition Whether the playback position should be reset to the default position in
     * the first [Timeline.Window]. If false, playback will start from the position defined
     * by [.getCurrentMediaItemIndex] and [.getCurrentPosition].
     */
    fun setMediaItems(mediaItems: List<MediaItem?>?, resetPosition: Boolean)

    /**
     * Clears the playlist and adds the specified [MediaItems][MediaItem].
     *
     * @param mediaItems The new [MediaItems][MediaItem].
     * @param startIndex The [MediaItem] index to start playback from. If [C.INDEX_UNSET]
     * is passed, the current position is not reset.
     * @param startPositionMs The position in milliseconds to start playback from. If [     ][C.TIME_UNSET] is passed, the default position of the given [MediaItem] is used. In
     * any case, if `startIndex` is set to [C.INDEX_UNSET], this parameter is ignored
     * and the position is not reset at all.
     * @throws IllegalSeekPositionException If the provided `startIndex` is not within the
     * bounds of the list of media items.
     */
    fun setMediaItems(mediaItems: List<MediaItem?>?, startIndex: Int, startPositionMs: Long)

    /**
     * Clears the playlist, adds the specified [MediaItem] and resets the position to the
     * default position.
     *
     * @param mediaItem The new [MediaItem].
     */
    fun setMediaItem(mediaItem: MediaItem?)

    /**
     * Clears the playlist and adds the specified [MediaItem].
     *
     * @param mediaItem The new [MediaItem].
     * @param startPositionMs The position in milliseconds to start playback from.
     */
    fun setMediaItem(mediaItem: MediaItem?, startPositionMs: Long)

    /**
     * Clears the playlist and adds the specified [MediaItem].
     *
     * @param mediaItem The new [MediaItem].
     * @param resetPosition Whether the playback position should be reset to the default position. If
     * false, playback will start from the position defined by [.getCurrentMediaItemIndex]
     * and [.getCurrentPosition].
     */
    fun setMediaItem(mediaItem: MediaItem?, resetPosition: Boolean)

    /**
     * Adds a media item to the end of the playlist.
     *
     * @param mediaItem The [MediaItem] to add.
     */
    fun addMediaItem(mediaItem: MediaItem?)

    /**
     * Adds a media item at the given index of the playlist.
     *
     * @param index The index at which to add the media item. If the index is larger than the size of
     * the playlist, the media item is added to the end of the playlist.
     * @param mediaItem The [MediaItem] to add.
     */
    fun addMediaItem(index: Int, mediaItem: MediaItem?)

    /**
     * Adds a list of media items to the end of the playlist.
     *
     * @param mediaItems The [MediaItems][MediaItem] to add.
     */
    fun addMediaItems(mediaItems: List<MediaItem?>?)

    /**
     * Adds a list of media items at the given index of the playlist.
     *
     * @param index The index at which to add the media items. If the index is larger than the size of
     * the playlist, the media items are added to the end of the playlist.
     * @param mediaItems The [MediaItems][MediaItem] to add.
     */
    fun addMediaItems(index: Int, mediaItems: List<MediaItem?>?)

    /**
     * Moves the media item at the current index to the new index.
     *
     * @param currentIndex The current index of the media item to move.
     * @param newIndex The new index of the media item. If the new index is larger than the size of
     * the playlist the item is moved to the end of the playlist.
     */
    fun moveMediaItem(currentIndex: Int, newIndex: Int)

    /**
     * Moves the media item range to the new index.
     *
     * @param fromIndex The start of the range to move.
     * @param toIndex The first item not to be included in the range (exclusive).
     * @param newIndex The new index of the first media item of the range. If the new index is larger
     * than the size of the remaining playlist after removing the range, the range is moved to the
     * end of the playlist.
     */
    fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int)

    /**
     * Removes the media item at the given index of the playlist.
     *
     * @param index The index at which to remove the media item.
     */
    fun removeMediaItem(index: Int)

    /**
     * Removes a range of media items from the playlist.
     *
     * @param fromIndex The index at which to start removing media items.
     * @param toIndex The index of the first item to be kept (exclusive). If the index is larger than
     * the size of the playlist, media items to the end of the playlist are removed.
     */
    fun removeMediaItems(fromIndex: Int, toIndex: Int)

    /** Clears the playlist.  */
    fun clearMediaItems()

    /**
     * Returns whether the provided [Command] is available.
     *
     *
     * This method does not execute the command.
     *
     *
     * Executing a command that is not available (for example, calling [ ][.seekToNextMediaItem] if [.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM] is unavailable) will
     * neither throw an exception nor generate a [.getPlayerError] player error}.
     *
     *
     * [.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM] and [.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM]
     * are unavailable if there is no such [MediaItem].
     *
     * @param command A [Command].
     * @return Whether the [Command] is available.
     * @see Listener.onAvailableCommandsChanged
     */
    fun isCommandAvailable(@Command command: Int): Boolean

    /** Returns whether the player can be used to advertise a media session.  */
    fun canAdvertiseSession(): Boolean

    /**
     * Returns the player's currently available [Commands].
     *
     *
     * The returned [Commands] are not updated when available commands change. Use [ ][Listener.onAvailableCommandsChanged] to get an update when the available commands
     * change.
     *
     *
     * Executing a command that is not available (for example, calling [ ][.seekToNextMediaItem] if [.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM] is unavailable) will
     * neither throw an exception nor generate a [.getPlayerError] player error}.
     *
     *
     * [.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM] and [.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM]
     * are unavailable if there is no such [MediaItem].
     *
     * @return The currently available [Commands].
     * @see Listener.onAvailableCommandsChanged
     */
    fun getAvailableCommands(): Commands?

    /**
     * Prepares the player.
     *
     *
     * This will move the player out of [idle state][.STATE_IDLE] and the player will start
     * loading media and acquire resources needed for playback.
     */
    fun prepare()

    /**
     * Returns the current [playback state][State] of the player.
     *
     * @return The current [playback state][State].
     * @see Listener.onPlaybackStateChanged
     */
    @State
    fun getPlaybackState(): Int

    /**
     * Returns the reason why playback is suppressed even though [.getPlayWhenReady] is `true`, or [.PLAYBACK_SUPPRESSION_REASON_NONE] if playback is not suppressed.
     *
     * @return The current [playback suppression reason][PlaybackSuppressionReason].
     * @see Listener.onPlaybackSuppressionReasonChanged
     */
    @PlaybackSuppressionReason
    fun getPlaybackSuppressionReason(): Int

    /**
     * Returns whether the player is playing, i.e. [.getCurrentPosition] is advancing.
     *
     *
     * If `false`, then at least one of the following is true:
     *
     *
     *  * The [playback state][.getPlaybackState] is not [ready][.STATE_READY].
     *  * There is no [intention to play][.getPlayWhenReady].
     *  * Playback is [suppressed for other reasons][.getPlaybackSuppressionReason].
     *
     *
     * @return Whether the player is playing.
     * @see Listener.onIsPlayingChanged
     */
    fun isPlaying(): Boolean

    /**
     * Returns the error that caused playback to fail. This is the same error that will have been
     * reported via [Listener.onPlayerError] at the time of failure. It can
     * be queried using this method until the player is re-prepared.
     *
     *
     * Note that this method will always return `null` if [.getPlaybackState] is not
     * [.STATE_IDLE].
     *
     * @return The error, or `null`.
     * @see Listener.onPlayerError
     */
    fun getPlayerError(): PlaybackException?

    /**
     * Resumes playback as soon as [.getPlaybackState] == [.STATE_READY]. Equivalent to
     * `setPlayWhenReady(true)`.
     */
    fun play()

    /** Pauses playback. Equivalent to `setPlayWhenReady(false)`.  */
    fun pause()
    /**
     * Whether playback will proceed when [.getPlaybackState] == [.STATE_READY].
     *
     * @return Whether playback will proceed when ready.
     * @see Listener.onPlayWhenReadyChanged
     */
    /**
     * Sets whether playback should proceed when [.getPlaybackState] == [.STATE_READY].
     *
     *
     * If the player is already in the ready state then this method pauses and resumes playback.
     *
     * @param playWhenReady Whether playback should proceed when ready.
     */
    fun getPlayWhenReady(): Boolean
    /**
     * Returns the current [RepeatMode] used for playback.
     *
     * @return The current repeat mode.
     * @see Listener.onRepeatModeChanged
     */
    /**
     * Sets the [RepeatMode] to be used for playback.
     *
     * @param repeatMode The repeat mode.
     */
    fun setRepeatMode(@RepeatMode repeatMode: Int)
    /**
     * Returns whether shuffling of media items is enabled.
     *
     * @see Listener.onShuffleModeEnabledChanged
     */
    /**
     * Sets whether shuffling of media items is enabled.
     *
     * @param shuffleModeEnabled Whether shuffling is enabled.
     */
    @RepeatMode
    fun getRepeatMode(): Int

    /**
     * Sets whether shuffling of media items is enabled.
     *
     * @param shuffleModeEnabled Whether shuffling is enabled.
     */
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean)

    /**
     * Returns whether shuffling of media items is enabled.
     *
     * @see Listener.onShuffleModeEnabledChanged
     */
    fun getShuffleModeEnabled(): Boolean

    /**
     * Whether the player is currently loading the source.
     *
     * @return Whether the player is currently loading the source.
     * @see Listener#onIsLoadingChanged(boolean)
     */
    fun isLoading(): Boolean

    /**
     * Seeks to the default position associated with the current [MediaItem]. The position can
     * depend on the type of media being played. For live streams it will typically be the live edge.
     * For other streams it will typically be the start.
     */
    fun seekToDefaultPosition()

    /**
     * Seeks to the default position associated with the specified [MediaItem]. The position can
     * depend on the type of media being played. For live streams it will typically be the live edge.
     * For other streams it will typically be the start.
     *
     * @param mediaItemIndex The index of the [MediaItem] whose associated default position
     * should be seeked to.
     * @throws IllegalSeekPositionException If the player has a non-empty timeline and the provided
     * `mediaItemIndex` is not within the bounds of the current timeline.
     */
    fun seekToDefaultPosition(mediaItemIndex: Int)

    /**
     * Seeks to a position specified in milliseconds in the current [MediaItem].
     *
     * @param positionMs The seek position in the current [MediaItem], or [C.TIME_UNSET]
     * to seek to the media item's default position.
     */
    fun seekTo(positionMs: Long)

    /**
     * Seeks to a position specified in milliseconds in the specified [MediaItem].
     *
     * @param mediaItemIndex The index of the [MediaItem].
     * @param positionMs The seek position in the specified [MediaItem], or [C.TIME_UNSET]
     * to seek to the media item's default position.
     * @throws IllegalSeekPositionException If the player has a non-empty timeline and the provided
     * `mediaItemIndex` is not within the bounds of the current timeline.
     */
    fun seekTo(mediaItemIndex: Int, positionMs: Long)

    /**
     * Returns the [.seekBack] increment.
     *
     * @return The seek back increment, in milliseconds.
     * @see Listener.onSeekBackIncrementChanged
     */
    fun getSeekBackIncrement(): Long

    /**
     * Seeks back in the current [MediaItem] by [.getSeekBackIncrement] milliseconds.
     */
    fun seekBack()

    /**
     * Returns the [.seekForward] increment.
     *
     * @return The seek forward increment, in milliseconds.
     * @see Listener.onSeekForwardIncrementChanged
     */
    fun getSeekForwardIncrement(): Long

    /**
     * Seeks forward in the current [MediaItem] by [.getSeekForwardIncrement]
     * milliseconds.
     */
    fun seekForward()

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    fun hasPrevious(): Boolean

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    fun hasPreviousWindow(): Boolean

    /**
     * Returns whether a previous media item exists, which may depend on the current repeat mode and
     * whether shuffle mode is enabled.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun hasPreviousMediaItem(): Boolean

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    fun previous()

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    fun seekToPreviousWindow()

    /**
     * Seeks to the default position of the previous [MediaItem], which may depend on the
     * current repeat mode and whether shuffle mode is enabled. Does nothing if [ ][.hasPreviousMediaItem] is `false`.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun seekToPreviousMediaItem()

    /**
     * Returns the maximum position for which [.seekToPrevious] seeks to the previous [ ], in milliseconds.
     *
     * @return The maximum seek to previous position, in milliseconds.
     * @see Listener.onMaxSeekToPreviousPositionChanged
     */
    fun getMaxSeekToPreviousPosition(): Long

    /**
     * Seeks to an earlier position in the current or previous [MediaItem] (if available). More
     * precisely:
     *
     *
     *  * If the timeline is empty or seeking is not possible, does nothing.
     *  * Otherwise, if the current [MediaItem] is [.isCurrentMediaItemLive] live}
     * and [unseekable][.isCurrentMediaItemSeekable], then:
     *
     *  * If [a previous media item exists][.hasPreviousMediaItem], seeks to the
     * default position of the previous media item.
     *  * Otherwise, does nothing.
     *
     *  * Otherwise, if [a previous media item exists][.hasPreviousMediaItem] and the [       ][.getCurrentPosition] is less than [       ][.getMaxSeekToPreviousPosition], seeks to the default position of the previous [       ].
     *  * Otherwise, seeks to 0 in the current [MediaItem].
     *
     */
    fun seekToPrevious()

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    operator fun hasNext(): Boolean

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    fun hasNextWindow(): Boolean

    /**
     * Returns whether a next [MediaItem] exists, which may depend on the current repeat mode
     * and whether shuffle mode is enabled.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun hasNextMediaItem(): Boolean

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    operator fun next()

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    fun seekToNextWindow()

    /**
     * Seeks to the default position of the next [MediaItem], which may depend on the current
     * repeat mode and whether shuffle mode is enabled. Does nothing if [.hasNextMediaItem] is
     * `false`.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun seekToNextMediaItem()

    /**
     * Seeks to a later position in the current or next [MediaItem] (if available). More
     * precisely:
     *
     *
     *  * If the timeline is empty or seeking is not possible, does nothing.
     *  * Otherwise, if [a next media item exists][.hasNextMediaItem], seeks to the default
     * position of the next [MediaItem].
     *  * Otherwise, if the current [MediaItem] is [live][.isCurrentMediaItemLive] and
     * has not ended, seeks to the live edge of the current [MediaItem].
     *  * Otherwise, does nothing.
     *
     */
    fun seekToNext()

    /**
     * Changes the rate at which playback occurs. The pitch is not changed.
     *
     *
     * This is equivalent to `setPlaybackParameters(getPlaybackParameters().withSpeed(speed))`.
     *
     * @param speed The linear factor by which playback will be sped up. Must be higher than 0. 1 is
     * normal speed, 2 is twice as fast, 0.5 is half normal speed...
     */
    fun setPlaybackSpeed(@FloatRange(from = 0.0, fromInclusive = false) speed: Float)
    /**
     * Returns the currently active playback parameters.
     *
     * @see Listener.onPlaybackParametersChanged
     */
    /**
     * Attempts to set the playback parameters. Passing [PlaybackParameters.DEFAULT] resets the
     * player to the default, which means there is no speed or pitch adjustment.
     *
     *
     * Playback parameters changes may cause the player to buffer. [ ][Listener.onPlaybackParametersChanged] will be called whenever the currently
     * active playback parameters change.
     *
     * @param playbackParameters The playback parameters.
     */
    fun getPlaybackParameters(): PlaybackParameters?

    /**
     * Stops playback without resetting the playlist. Use [.pause] rather than this method if
     * the intention is to pause playback.
     *
     *
     * Calling this method will cause the playback state to transition to [.STATE_IDLE] and
     * the player will release the loaded media and resources required for playback. The player
     * instance can still be used by calling [.prepare] again, and [.release] must
     * still be called on the player if it's no longer required.
     *
     *
     * Calling this method does not clear the playlist, reset the playback position or the playback
     * error.
     */
    fun stop()

    @Deprecated("Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or\n" + "        just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when\n" + "        {@link #prepare() re-preparing} the player.")
    fun stop(reset: Boolean)

    /**
     * Releases the player. This method must be called when the player is no longer required. The
     * player must not be used after calling this method.
     */
    fun release()

    /**
     * Returns the current tracks.
     *
     * @see Listener.onTracksChanged
     */
    fun getCurrentTracks(): Tracks?

    /**
     * Returns the parameters constraining the track selection.
     *
     * @see Listener.onTrackSelectionParametersChanged}
     */
    /**
     * Sets the parameters constraining the track selection.
     *
     *
     * Unsupported parameters will be silently ignored.
     *
     *
     * Use [.getTrackSelectionParameters] to retrieve the current parameters. For example,
     * the following snippet restricts video to SD whilst keep other track selection parameters
     * unchanged:
     *
     * <pre>`player.setTrackSelectionParameters(
     * player.getTrackSelectionParameters()
     * .buildUpon()
     * .setMaxVideoSizeSd()
     * .build())
    `</pre> *
     */
    fun getTrackSelectionParameters(): TrackSelectionParameters?

    /**
     * Returns the current combined [MediaMetadata], or [MediaMetadata.EMPTY] if not
     * supported.
     *
     *
     * This [MediaMetadata] is a combination of the [MediaItem][MediaItem.mediaMetadata], the static metadata in the media's [Format][Format.metadata], and any timed
     * metadata that has been parsed from the media and output via [ ][Listener.onMetadata]. If a field is populated in the [MediaItem.mediaMetadata],
     * it will be prioritised above the same field coming from static or timed metadata.
     */
    fun getMediaMetadata(): MediaMetadata?

    /**
     * Returns the playlist [MediaMetadata], as set by [ ][.setPlaylistMetadata], or [MediaMetadata.EMPTY] if not supported.
     */
    /** Sets the playlist [MediaMetadata].  */
    fun getPlaylistMetadata(): MediaMetadata?

    /** Sets the playlist [MediaMetadata].  */
    fun setPlaylistMetadata(mediaMetadata: MediaMetadata?)

    /**
     * Returns the current manifest. The type depends on the type of media being played. May be null.
     */
    fun getCurrentManifest(): Any?

    /**
     * Returns the current [Timeline]. Never null, but may be empty.
     *
     * @see Listener.onTimelineChanged
     */
    fun getCurrentTimeline(): Timeline?

    /** Returns the index of the period currently being played.  */
    fun getCurrentPeriodIndex(): Int

    @Deprecated("")
    fun getCurrentWindowIndex(): Int

    /**
     * Returns the index of the current [MediaItem] in the [ timeline][.getCurrentTimeline], or the prospective index if the [current timeline][.getCurrentTimeline] is
     * empty.
     */
    fun getCurrentMediaItemIndex(): Int


    @Deprecated("Use {@link #getNextMediaItemIndex()} instead.")
    fun getNextWindowIndex(): Int

    /**
     * Returns the index of the [MediaItem] that will be played if [ ][.seekToNextMediaItem] is called, which may depend on the current repeat mode and whether
     * shuffle mode is enabled. Returns [C.INDEX_UNSET] if [.hasNextMediaItem] is `false`.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun getNextMediaItemIndex(): Int


    @Deprecated("Use {@link #getPreviousMediaItemIndex()} instead.")
    fun getPreviousWindowIndex(): Int

    /**
     * Returns the index of the [MediaItem] that will be played if [ ][.seekToPreviousMediaItem] is called, which may depend on the current repeat mode and whether
     * shuffle mode is enabled. Returns [C.INDEX_UNSET] if [.hasPreviousMediaItem] is
     * `false`.
     *
     *
     * Note: When the repeat mode is [.REPEAT_MODE_ONE], this method behaves the same as when
     * the current repeat mode is [.REPEAT_MODE_OFF]. See [.REPEAT_MODE_ONE] for more
     * details.
     */
    fun getPreviousMediaItemIndex(): Int

    /**
     * Returns the currently playing [MediaItem]. May be null if the timeline is empty.
     *
     * @see Listener.onMediaItemTransition
     */
    fun getCurrentMediaItem(): MediaItem?

    /** Returns the number of [media items][MediaItem] in the playlist.  */
    fun getMediaItemCount(): Int

    /** Returns the [MediaItem] at the given index.  */
    fun getMediaItemAt(index: Int): MediaItem?

    /**
     * Returns the duration of the current content or ad in milliseconds, or [C.TIME_UNSET] if
     * the duration is not known.
     */
    fun getDuration(): Long

    /**
     * Returns the playback position in the current content or ad, in milliseconds, or the prospective
     * position in milliseconds if the [current timeline][.getCurrentTimeline] is empty.
     */
    fun getCurrentPosition(): Long

    /**
     * Returns an estimate of the position in the current content or ad up to which data is buffered,
     * in milliseconds.
     */
    fun getBufferedPosition(): Long

    /**
     * Returns an estimate of the percentage in the current content or ad up to which data is
     * buffered, or 0 if no estimate is available.
     */
    @IntRange(from = 0, to = 100)
    fun getBufferedPercentage(): Int

    /**
     * Returns an estimate of the total buffered duration from the current position, in milliseconds.
     * This includes pre-buffered data for subsequent ads and [media items][MediaItem].
     */
    fun getTotalBufferedDuration(): Long

    @Deprecated("")
    fun isCurrentWindowDynamic(): Boolean

    /**
     * Returns whether the current [MediaItem] is dynamic (may change when the [Timeline]
     * is updated), or `false` if the [Timeline] is empty.
     *
     * @see Timeline.Window.isDynamic
     */
    fun isCurrentMediaItemDynamic(): Boolean


    @Deprecated("Use {@link #isCurrentMediaItemLive()} instead.")
    fun isCurrentWindowLive(): Boolean

    /**
     * Returns whether the current [MediaItem] is live, or `false` if the [Timeline]
     * is empty.
     *
     * @see Timeline.Window.isLive
     */
    fun isCurrentMediaItemLive(): Boolean

    /**
     * Returns the offset of the current playback position from the live edge in milliseconds, or
     * [C.TIME_UNSET] if the current [MediaItem] [.isCurrentMediaItemLive] isn't
     * live} or the offset is unknown.
     *
     *
     * The offset is calculated as `currentTime - playbackPosition`, so should usually be
     * positive.
     *
     *
     * Note that this offset may rely on an accurate local time, so this method may return an
     * incorrect value if the difference between system clock and server clock is unknown.
     */
    fun getCurrentLiveOffset(): Long


    @Deprecated("Use {@link #isCurrentMediaItemSeekable()} instead.")
    fun isCurrentWindowSeekable(): Boolean

    /**
     * Returns whether the current [MediaItem] is seekable, or `false` if the [ ] is empty.
     *
     * @see Timeline.Window.isSeekable
     */
    fun isCurrentMediaItemSeekable(): Boolean

    /** Returns whether the player is currently playing an ad.  */
    fun isPlayingAd(): Boolean

    /**
     * If [.isPlayingAd] returns true, returns the index of the ad group in the period
     * currently being played. Returns [C.INDEX_UNSET] otherwise.
     */
    fun getCurrentAdGroupIndex(): Int

    /**
     * If [.isPlayingAd] returns true, returns the index of the ad in its ad group. Returns
     * [C.INDEX_UNSET] otherwise.
     */
    fun getCurrentAdIndexInAdGroup(): Int

    /**
     * If [.isPlayingAd] returns `true`, returns the duration of the current content in
     * milliseconds, or [C.TIME_UNSET] if the duration is not known. If there is no ad playing,
     * the returned duration is the same as that returned by [.getDuration].
     */
    fun getContentDuration(): Long

    /**
     * If [.isPlayingAd] returns `true`, returns the content position that will be
     * played once all ads in the ad group have finished playing, in milliseconds. If there is no ad
     * playing, the returned position is the same as that returned by [.getCurrentPosition].
     */
    fun getContentPosition(): Long

    /**
     * If [.isPlayingAd] returns `true`, returns an estimate of the content position in
     * the current content up to which data is buffered, in milliseconds. If there is no ad playing,
     * the returned position is the same as that returned by [.getBufferedPosition].
     */
    fun getContentBufferedPosition(): Long

    /** Returns the attributes for audio playback.  */
    fun getAudioAttributes(): AudioAttributes?

    /**
     * Sets the audio volume, valid values are between 0 (silence) and 1 (unity gain, signal
     * unchanged), inclusive.
     *
     * @param volume Linear output gain to apply to all audio channels.
     */
    fun setVolume(@FloatRange(from = 0.0, to = 1.0) volume: Float)

    /**
     * Returns the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
     *
     * @return The linear gain applied to all audio channels.
     */
    @FloatRange(from = 0.0, to = 1.0)
    fun getVolume(): Float

    /**
     * Clears any [Surface], [SurfaceHolder], [SurfaceView] or [TextureView]
     * currently set on the player.
     */
    fun clearVideoSurface()

    /**
     * Clears the [Surface] onto which video is being rendered if it matches the one passed.
     * Else does nothing.
     *
     * @param surface The surface to clear.
     */
    fun clearVideoSurface(surface: Surface?)

    /**
     * Sets the [Surface] onto which video will be rendered. The caller is responsible for
     * tracking the lifecycle of the surface, and must clear the surface by calling `setVideoSurface(null)` if the surface is destroyed.
     *
     *
     * If the surface is held by a [SurfaceView], [TextureView] or [ ] then it's recommended to use [.setVideoSurfaceView], [ ][.setVideoTextureView] or [.setVideoSurfaceHolder] rather than
     * this method, since passing the holder allows the player to track the lifecycle of the surface
     * automatically.
     *
     * @param surface The [Surface].
     */
    fun setVideoSurface(surface: Surface?)

    /**
     * Sets the [SurfaceHolder] that holds the [Surface] onto which video will be
     * rendered. The player will track the lifecycle of the surface automatically.
     *
     *
     * The thread that calls the [SurfaceHolder.Callback] methods must be the thread
     * associated with [.getApplicationLooper].
     *
     * @param surfaceHolder The surface holder.
     */
    fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?)

    /**
     * Clears the [SurfaceHolder] that holds the [Surface] onto which video is being
     * rendered if it matches the one passed. Else does nothing.
     *
     * @param surfaceHolder The surface holder to clear.
     */
    fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?)

    /**
     * Sets the [SurfaceView] onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     *
     * The thread that calls the [SurfaceHolder.Callback] methods must be the thread
     * associated with [.getApplicationLooper].
     *
     * @param surfaceView The surface view.
     */
    fun setVideoSurfaceView(surfaceView: SurfaceView?)

    /**
     * Clears the [SurfaceView] onto which video is being rendered if it matches the one passed.
     * Else does nothing.
     *
     * @param surfaceView The texture view to clear.
     */
    fun clearVideoSurfaceView(surfaceView: SurfaceView?)

    /**
     * Sets the [TextureView] onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     *
     * The thread that calls the [TextureView.SurfaceTextureListener] methods must be the
     * thread associated with [.getApplicationLooper].
     *
     * @param textureView The texture view.
     */
    fun setVideoTextureView(textureView: TextureView?)

    /**
     * Clears the [TextureView] onto which video is being rendered if it matches the one passed.
     * Else does nothing.
     *
     * @param textureView The texture view to clear.
     */
    fun clearVideoTextureView(textureView: TextureView?)

    /**
     * Gets the size of the video.
     *
     *
     * The video's width and height are `0` if there is no video or its size has not been
     * determined yet.
     *
     * @see Listener.onVideoSizeChanged
     */
    fun getVideoSize(): VideoSize?

    /**
     * Gets the size of the surface on which the video is rendered.
     *
     * @see Listener.onSurfaceSizeChanged
     */
    fun getSurfaceSize(): Size?

    /** Returns the current [CueGroup].  */
    fun getCurrentCues(): CueGroup?

    /** Gets the device information.  */
    fun getDeviceInfo(): DeviceInfo?
    /**
     * Gets the current volume of the device.
     *
     *
     * For devices with [local playback][DeviceInfo.PLAYBACK_TYPE_LOCAL], the volume returned
     * by this method varies according to the current [stream type][C.StreamType]. The stream
     * type is determined by [AudioAttributes.usage] which can be converted to stream type with
     * [Util.getStreamTypeForAudioUsage].
     *
     *
     * For devices with [remote playback][DeviceInfo.PLAYBACK_TYPE_REMOTE], the volume of the
     * remote device is returned.
     */
    /**
     * Sets the volume of the device.
     *
     * @param volume The volume to set.
     */
    @IntRange(from = 0)
    open fun getDeviceVolume(): Int

    /** Gets whether the device is muted or not.  */
    open fun isDeviceMuted(): Boolean

    /** Increases the volume of the device.  */
    fun increaseDeviceVolume()

    /** Decreases the volume of the device.  */
    fun decreaseDeviceVolume()

    companion object {
        /**
         * The player is idle, meaning it holds only limited resources. The player must be [ ][.prepare] before it will play the media.
         */
        const val STATE_IDLE: Int = 1

        /**
         * The player is not able to immediately play the media, but is doing work toward being able to do
         * so. This state typically occurs when the player needs to buffer more data before playback can
         * start.
         */
        const val STATE_BUFFERING: Int = 2

        /**
         * The player is able to immediately play from its current position. The player will be playing if
         * [.getPlayWhenReady] is true, and paused otherwise.
         */
        const val STATE_READY: Int = 3

        /** The player has finished playing the media.  */
        const val STATE_ENDED: Int = 4

        /** Playback has been started or paused by a call to [.setPlayWhenReady].  */
        const val PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST: Int = 1

        /** Playback has been paused because of a loss of audio focus.  */
        const val PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS: Int = 2

        /** Playback has been paused to avoid becoming noisy.  */
        const val PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY: Int = 3

        /** Playback has been started or paused because of a remote change.  */
        const val PLAY_WHEN_READY_CHANGE_REASON_REMOTE: Int = 4

        /** Playback has been paused at the end of a media item.  */
        const val PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM: Int = 5

        /** Playback is not suppressed.  */
        const val PLAYBACK_SUPPRESSION_REASON_NONE: Int = 0

        /** Playback is suppressed due to transient audio focus loss.  */
        const val PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS: Int = 1

        /**
         * Normal playback without repetition. "Previous" and "Next" actions move to the previous and next
         * [MediaItem] respectively, and do nothing when there is no previous or next [ ] to move to.
         */
        const val REPEAT_MODE_OFF: Int = 0

        /**
         * Repeats the currently playing [MediaItem] infinitely during ongoing playback. "Previous"
         * and "Next" actions behave as they do in [.REPEAT_MODE_OFF], moving to the previous and
         * next [MediaItem] respectively, and doing nothing when there is no previous or next [ ] to move to.
         */
        const val REPEAT_MODE_ONE: Int = 1

        /**
         * Repeats the entire timeline infinitely. "Previous" and "Next" actions behave as they do in
         * [.REPEAT_MODE_OFF], but with looping at the ends so that "Previous" when playing the
         * first [MediaItem] will move to the last [MediaItem], and "Next" when playing the
         * last [MediaItem] will move to the first [MediaItem].
         */
        const val REPEAT_MODE_ALL: Int = 2

        /**
         * Automatic playback transition from one period in the timeline to the next. The period index may
         * be the same as it was before the discontinuity in case the current period is repeated.
         *
         *
         * This reason also indicates an automatic transition from the content period to an inserted ad
         * period or vice versa. Or a transition caused by another player (e.g. multiple controllers can
         * control the same playback on a remote device).
         */
        const val DISCONTINUITY_REASON_AUTO_TRANSITION: Int = 0

        /** Seek within the current period or to another period.  */
        const val DISCONTINUITY_REASON_SEEK: Int = 1

        /**
         * Seek adjustment due to being unable to seek to the requested position or because the seek was
         * permitted to be inexact.
         */
        const val DISCONTINUITY_REASON_SEEK_ADJUSTMENT: Int = 2

        /** Discontinuity introduced by a skipped period (for instance a skipped ad).  */
        const val DISCONTINUITY_REASON_SKIP: Int = 3

        /** Discontinuity caused by the removal of the current period from the [Timeline].  */
        const val DISCONTINUITY_REASON_REMOVE: Int = 4

        /** Discontinuity introduced internally (e.g. by the source).  */
        const val DISCONTINUITY_REASON_INTERNAL: Int = 5

        /** Timeline changed as a result of a change of the playlist items or the order of the items.  */
        const val TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED: Int = 0

        /**
         * Timeline changed as a result of a source update (e.g. result of a dynamic update by the played
         * media).
         *
         *
         * This reason also indicates a change caused by another player (e.g. multiple controllers can
         * control the same playback on the remote device).
         */
        const val TIMELINE_CHANGE_REASON_SOURCE_UPDATE: Int = 1

        /** The media item has been repeated.  */
        const val MEDIA_ITEM_TRANSITION_REASON_REPEAT: Int = 0

        /**
         * Playback has automatically transitioned to the next media item.
         *
         *
         * This reason also indicates a transition caused by another player (e.g. multiple controllers
         * can control the same playback on a remote device).
         */
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO: Int = 1

        /** A seek to another media item has occurred.  */
        const val MEDIA_ITEM_TRANSITION_REASON_SEEK: Int = 2

        /**
         * The current media item has changed because of a change in the playlist. This can either be if
         * the media item previously being played has been removed, or when the playlist becomes non-empty
         * after being empty.
         */
        const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED: Int = 3

        /** [.getCurrentTimeline] changed.  */
        const val EVENT_TIMELINE_CHANGED: Int = 0

        /** [.getCurrentMediaItem] changed or the player started repeating the current item.  */
        const val EVENT_MEDIA_ITEM_TRANSITION: Int = 1

        /** [.getCurrentTracks] changed.  */
        const val EVENT_TRACKS_CHANGED: Int = 2

        /** [.isLoading] ()} changed.  */
        const val EVENT_IS_LOADING_CHANGED: Int = 3

        /** [.getPlaybackState] changed.  */
        const val EVENT_PLAYBACK_STATE_CHANGED: Int = 4

        /** [.getPlayWhenReady] changed.  */
        const val EVENT_PLAY_WHEN_READY_CHANGED: Int = 5

        /** [.getPlaybackSuppressionReason] changed.  */
        const val EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED: Int = 6

        /** [.isPlaying] changed.  */
        const val EVENT_IS_PLAYING_CHANGED: Int = 7

        /** [.getRepeatMode] changed.  */
        const val EVENT_REPEAT_MODE_CHANGED: Int = 8

        /** [.getShuffleModeEnabled] changed.  */
        const val EVENT_SHUFFLE_MODE_ENABLED_CHANGED: Int = 9

        /** [.getPlayerError] changed.  */
        const val EVENT_PLAYER_ERROR: Int = 10

        /**
         * A position discontinuity occurred. See [Listener.onPositionDiscontinuity].
         */
        const val EVENT_POSITION_DISCONTINUITY: Int = 11

        /** [.getPlaybackParameters] changed.  */
        const val EVENT_PLAYBACK_PARAMETERS_CHANGED: Int = 12

        /** [.isCommandAvailable] changed for at least one [Command].  */
        const val EVENT_AVAILABLE_COMMANDS_CHANGED: Int = 13

        /** [.getMediaMetadata] changed.  */
        const val EVENT_MEDIA_METADATA_CHANGED: Int = 14

        /** [.getPlaylistMetadata] changed.  */
        const val EVENT_PLAYLIST_METADATA_CHANGED: Int = 15

        /** [.getSeekBackIncrement] changed.  */
        const val EVENT_SEEK_BACK_INCREMENT_CHANGED: Int = 16

        /** [.getSeekForwardIncrement] changed.  */
        const val EVENT_SEEK_FORWARD_INCREMENT_CHANGED: Int = 17

        /** [.getMaxSeekToPreviousPosition] changed.  */
        const val EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED: Int = 18

        /** [.getTrackSelectionParameters] changed.  */
        const val EVENT_TRACK_SELECTION_PARAMETERS_CHANGED: Int = 19

        /** [.getAudioAttributes] changed.  */
        const val EVENT_AUDIO_ATTRIBUTES_CHANGED: Int = 20

        /** The audio session id was set.  */
        const val EVENT_AUDIO_SESSION_ID: Int = 21

        /** [.getVolume] changed.  */
        const val EVENT_VOLUME_CHANGED: Int = 22

        /** Skipping silences in the audio stream is enabled or disabled.  */
        const val EVENT_SKIP_SILENCE_ENABLED_CHANGED: Int = 23

        /** The size of the surface onto which the video is being rendered changed.  */
        const val EVENT_SURFACE_SIZE_CHANGED: Int = 24

        /** [.getVideoSize] changed.  */
        const val EVENT_VIDEO_SIZE_CHANGED: Int = 25

        /**
         * A frame is rendered for the first time since setting the surface, or since the renderer was
         * reset, or since the stream being rendered was changed.
         */
        const val EVENT_RENDERED_FIRST_FRAME: Int = 26

        /** [.getCurrentCues] changed.  */
        const val EVENT_CUES: Int = 27

        /** Metadata associated with the current playback time changed.  */
        const val EVENT_METADATA: Int = 28

        /** [.getDeviceInfo] changed.  */
        const val EVENT_DEVICE_INFO_CHANGED: Int = 29

        /** [.getDeviceVolume] changed.  */
        const val EVENT_DEVICE_VOLUME_CHANGED: Int = 30

        /** Command to start, pause or resume playback.  */
        const val COMMAND_PLAY_PAUSE: Int = 1

        /** Command to prepare the player.  */
        const val COMMAND_PREPARE: Int = 2

        /** Command to stop playback or release the player.  */
        const val COMMAND_STOP: Int = 3

        /** Command to seek to the default position of the current [MediaItem].  */
        const val COMMAND_SEEK_TO_DEFAULT_POSITION: Int = 4

        /** Command to seek anywhere into the current [MediaItem].  */
        const val COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM: Int = 5

        @Deprecated("Use {@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM} instead.")
        val COMMAND_SEEK_IN_CURRENT_WINDOW: Int = COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM

        /** Command to seek to the default position of the previous [MediaItem].  */
        const val COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM: Int = 6

        @Deprecated("Use {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM} instead.")
        val COMMAND_SEEK_TO_PREVIOUS_WINDOW: Int = COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM

        /** Command to seek to an earlier position in the current or previous [MediaItem].  */
        const val COMMAND_SEEK_TO_PREVIOUS: Int = 7

        /** Command to seek to the default position of the next [MediaItem].  */
        const val COMMAND_SEEK_TO_NEXT_MEDIA_ITEM: Int = 8

        @Deprecated("Use {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} instead.")
        val COMMAND_SEEK_TO_NEXT_WINDOW: Int = COMMAND_SEEK_TO_NEXT_MEDIA_ITEM

        /** Command to seek to a later position in the current or next [MediaItem].  */
        const val COMMAND_SEEK_TO_NEXT: Int = 9

        /** Command to seek anywhere in any [MediaItem].  */
        const val COMMAND_SEEK_TO_MEDIA_ITEM: Int = 10

        @Deprecated("Use {@link #COMMAND_SEEK_TO_MEDIA_ITEM} instead.")
        val COMMAND_SEEK_TO_WINDOW: Int = COMMAND_SEEK_TO_MEDIA_ITEM

        /** Command to seek back by a fixed increment into the current [MediaItem].  */
        const val COMMAND_SEEK_BACK: Int = 11

        /** Command to seek forward by a fixed increment into the current [MediaItem].  */
        const val COMMAND_SEEK_FORWARD: Int = 12

        /** Command to set the playback speed and pitch.  */
        const val COMMAND_SET_SPEED_AND_PITCH: Int = 13

        /** Command to enable shuffling.  */
        const val COMMAND_SET_SHUFFLE_MODE: Int = 14

        /** Command to set the repeat mode.  */
        const val COMMAND_SET_REPEAT_MODE: Int = 15

        /** Command to get the currently playing [MediaItem].  */
        const val COMMAND_GET_CURRENT_MEDIA_ITEM: Int = 16

        /** Command to get the information about the current timeline.  */
        const val COMMAND_GET_TIMELINE: Int = 17

        /** Command to get the [MediaItems][MediaItem] metadata.  */
        const val COMMAND_GET_MEDIA_ITEMS_METADATA: Int = 18

        /** Command to set the [MediaItems][MediaItem] metadata.  */
        const val COMMAND_SET_MEDIA_ITEMS_METADATA: Int = 19

        /** Command to set a [MediaItem].  */
        const val COMMAND_SET_MEDIA_ITEM: Int = 31

        /** Command to change the [MediaItems][MediaItem] in the playlist.  */
        const val COMMAND_CHANGE_MEDIA_ITEMS: Int = 20

        /** Command to get the player current [AudioAttributes].  */
        const val COMMAND_GET_AUDIO_ATTRIBUTES: Int = 21

        /** Command to get the player volume.  */
        const val COMMAND_GET_VOLUME: Int = 22

        /** Command to get the device volume and whether it is muted.  */
        const val COMMAND_GET_DEVICE_VOLUME: Int = 23

        /** Command to set the player volume.  */
        const val COMMAND_SET_VOLUME: Int = 24

        /** Command to set the device volume and mute it.  */
        const val COMMAND_SET_DEVICE_VOLUME: Int = 25

        /** Command to increase and decrease the device volume and mute it.  */
        const val COMMAND_ADJUST_DEVICE_VOLUME: Int = 26

        /** Command to set and clear the surface on which to render the video.  */
        const val COMMAND_SET_VIDEO_SURFACE: Int = 27

        /** Command to get the text that should currently be displayed by the player.  */
        const val COMMAND_GET_TEXT: Int = 28

        /** Command to set the player's track selection parameters.  */
        const val COMMAND_SET_TRACK_SELECTION_PARAMETERS: Int = 29

        /** Command to get details of the current track selection.  */
        const val COMMAND_GET_TRACKS: Int = 30

        /** Represents an invalid [Command].  */
        const val COMMAND_INVALID: Int = -1
    }
}