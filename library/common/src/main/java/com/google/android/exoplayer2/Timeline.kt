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

import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Pair
import androidx.annotation.IntDef
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
import com.google.android.exoplayer2.Timeline.Period
import com.google.android.exoplayer2.Timeline.Window
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.InlineMe
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A flexible representation of the structure of media. A timeline is able to represent the
 * structure of a wide variety of media, from simple cases like a single media file through to
 * complex compositions of media such as playlists and streams with inserted ads. Instances are
 * immutable. For cases where media is changing dynamically (e.g. live streams), a timeline provides
 * a snapshot of the current state.
 *
 *
 * A timeline consists of [Windows][Window] and [Periods][Period].
 *
 *
 *  * A [Window] usually corresponds to one playlist item. It may span one or more periods
 * and it defines the region within those periods that's currently available for playback. The
 * window also provides additional information such as whether seeking is supported within the
 * window and the default position, which is the position from which playback will start when
 * the player starts playing the window.
 *  * A [Period] defines a single logical piece of media, for example a media file. It may
 * also define groups of ads inserted into the media, along with information about whether
 * those ads have been loaded and played.
 *
 *
 *
 * The following examples illustrate timelines for various use cases.
 *
 * <h2 id="single-file">Single media file or on-demand stream</h2>
 *
 *
 * <img src="doc-files/timeline-single-file.svg" alt="Example timeline for a
single file"></img>
 *
 *
 * A timeline for a single media file or on-demand stream consists of a single period and window.
 * The window spans the whole period, indicating that all parts of the media are available for
 * playback. The window's default position is typically at the start of the period (indicated by the
 * black dot in the figure above).
 *
 * <h2>Playlist of media files or on-demand streams</h2>
 *
 *
 * <img src="doc-files/timeline-playlist.svg" alt="Example timeline for a
playlist of files"></img>
 *
 *
 * A timeline for a playlist of media files or on-demand streams consists of multiple periods,
 * each with its own window. Each window spans the whole of the corresponding period, and typically
 * has a default position at the start of the period. The properties of the periods and windows
 * (e.g. their durations and whether the window is seekable) will often only become known when the
 * player starts buffering the corresponding file or stream.
 *
 * <h2 id="live-limited">Live stream with limited availability</h2>
 *
 *
 * <img src="doc-files/timeline-live-limited.svg" alt="Example timeline for
a live stream with limited availability"></img>
 *
 *
 * A timeline for a live stream consists of a period whose duration is unknown, since it's
 * continually extending as more content is broadcast. If content only remains available for a
 * limited period of time then the window may start at a non-zero position, defining the region of
 * content that can still be played. The window will return true from [Window.isLive] to
 * indicate it's a live stream and [Window.isDynamic] will be set to true as long as we expect
 * changes to the live window. Its default position is typically near to the live edge (indicated by
 * the black dot in the figure above).
 *
 * <h2>Live stream with indefinite availability</h2>
 *
 *
 * <img src="doc-files/timeline-live-indefinite.svg" alt="Example timeline
for a live stream with indefinite availability"></img>
 *
 *
 * A timeline for a live stream with indefinite availability is similar to the [Live stream with limited availability](#live-limited) case, except that the window
 * starts at the beginning of the period to indicate that all of the previously broadcast content
 * can still be played.
 *
 * <h2 id="live-multi-period">Live stream with multiple periods</h2>
 *
 *
 * <img src="doc-files/timeline-live-multi-period.svg" alt="Example timeline
for a live stream with multiple periods"></img>
 *
 *
 * This case arises when a live stream is explicitly divided into separate periods, for example
 * at content boundaries. This case is similar to the [Live stream with
 * limited availability](#live-limited) case, except that the window may span more than one period. Multiple
 * periods are also possible in the indefinite availability case.
 *
 * <h2>On-demand stream followed by live stream</h2>
 *
 *
 * <img src="doc-files/timeline-advanced.svg" alt="Example timeline for an
on-demand stream followed by a live stream"></img>
 *
 *
 * This case is the concatenation of the [Single media file or on-demand
 * stream](#single-file) and [Live stream with multiple periods](#multi-period) cases. When playback
 * of the on-demand stream ends, playback of the live stream will start from its default position
 * near the live edge.
 *
 * <h2 id="single-file-midrolls">On-demand stream with mid-roll ads</h2>
 *
 *
 * <img src="doc-files/timeline-single-file-midrolls.svg" alt="Example
timeline for an on-demand stream with mid-roll ad groups"></img>
 *
 *
 * This case includes mid-roll ad groups, which are defined as part of the timeline's single
 * period. The period can be queried for information about the ad groups and the ads they contain.
 */
abstract class Timeline protected constructor() : Bundleable {
    /**
     * Holds information about a window in a [Timeline]. A window usually corresponds to one
     * playlist item and defines a region of media currently available for playback along with
     * additional information such as whether seeking is supported within the window. The figure below
     * shows some of the information defined by a window, as well as how this information relates to
     * corresponding [Periods][Period] in the timeline.
     *
     *
     * <img src="doc-files/timeline-window.svg" alt="Information defined by a
    timeline window"></img>
     */
    class Window constructor() : Bundleable {
        /**
         * A unique identifier for the window. Single-window [Timelines][Timeline] must use [ ][.SINGLE_WINDOW_UID].
         */
        var uid: Any

        @Deprecated("Use {@link #mediaItem} instead.")
        var tag: Any? = null

        /** The [MediaItem] associated to the window. Not necessarily unique.  */
        var mediaItem: MediaItem?

        /** The manifest of the window. May be `null`.  */
        var manifest: Any? = null

        /**
         * The start time of the presentation to which this window belongs in milliseconds since the
         * Unix epoch, or [C.TIME_UNSET] if unknown or not applicable. For informational purposes
         * only.
         */
        var presentationStartTimeMs: Long = 0

        /**
         * The window's start time in milliseconds since the Unix epoch, or [C.TIME_UNSET] if
         * unknown or not applicable.
         */
        var windowStartTimeMs: Long = 0

        /**
         * The offset between [SystemClock.elapsedRealtime] and the time since the Unix epoch
         * according to the clock of the media origin server, or [C.TIME_UNSET] if unknown or not
         * applicable.
         *
         *
         * Note that the current Unix time can be retrieved using [.getCurrentUnixTimeMs] and
         * is calculated as `SystemClock.elapsedRealtime() + elapsedRealtimeEpochOffsetMs`.
         */
        var elapsedRealtimeEpochOffsetMs: Long = 0

        /** Whether it's possible to seek within this window.  */
        var isSeekable: Boolean = false
        // TODO: Split this to better describe which parts of the window might change. For example it
        // should be possible to individually determine whether the start and end positions of the
        // window may change relative to the underlying periods. For an example of where it's useful to
        // know that the end position is fixed whilst the start position may still change, see:
        // https://github.com/google/ExoPlayer/issues/4780.
        /** Whether this window may change when the timeline is updated.  */
        var isDynamic: Boolean = false

        @Deprecated("Use {@link #isLive()} instead.")
        var isLive: Boolean = false

        /**
         * The [MediaItem.LiveConfiguration] that is used or null if [.isLive] returns
         * false.
         */
        var liveConfiguration: LiveConfiguration? = null

        /**
         * Whether this window contains placeholder information because the real information has yet to
         * be loaded.
         */
        var isPlaceholder: Boolean = false
        /**
         * Returns the default position relative to the start of the window at which to begin playback,
         * in microseconds. May be [C.TIME_UNSET] if and only if the window was populated with a
         * non-zero default position projection, and if the specified projection cannot be performed
         * whilst remaining within the bounds of the window.
         */
        /**
         * The default position relative to the start of the window at which to begin playback, in
         * microseconds. May be [C.TIME_UNSET] if and only if the window was populated with a
         * non-zero default position projection, and if the specified projection cannot be performed
         * whilst remaining within the bounds of the window.
         */
        var defaultPositionUs: Long = 0
        /** Returns the duration of this window in microseconds, or [C.TIME_UNSET] if unknown.  */
        /** The duration of this window in microseconds, or [C.TIME_UNSET] if unknown.  */
        var durationUs: Long = 0

        /** The index of the first period that belongs to this window.  */
        var firstPeriodIndex: Int = 0

        /** The index of the last period that belongs to this window.  */
        var lastPeriodIndex: Int = 0
        /**
         * Returns the position of the start of this window relative to the start of the first period
         * belonging to it, in microseconds.
         */
        /**
         * The position of the start of this window relative to the start of the first period belonging
         * to it, in microseconds.
         */
        var positionInFirstPeriodUs: Long = 0

        /** Sets the data held by this window.  */
        @CanIgnoreReturnValue
        operator fun set(
                uid: Any,
                mediaItem: MediaItem?,
                manifest: Any?,
                presentationStartTimeMs: Long,
                windowStartTimeMs: Long,
                elapsedRealtimeEpochOffsetMs: Long,
                isSeekable: Boolean,
                isDynamic: Boolean,
                liveConfiguration: LiveConfiguration?,
                defaultPositionUs: Long,
                durationUs: Long,
                firstPeriodIndex: Int,
                lastPeriodIndex: Int,
                positionInFirstPeriodUs: Long): Window {
            this.uid = uid
            this.mediaItem = if (mediaItem != null) mediaItem else EMPTY_MEDIA_ITEM
            tag = if (mediaItem != null && mediaItem.localConfiguration != null) mediaItem.localConfiguration.tag else null
            this.manifest = manifest
            this.presentationStartTimeMs = presentationStartTimeMs
            this.windowStartTimeMs = windowStartTimeMs
            this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs
            this.isSeekable = isSeekable
            this.isDynamic = isDynamic
            isLive = liveConfiguration != null
            this.liveConfiguration = liveConfiguration
            this.defaultPositionUs = defaultPositionUs
            this.durationUs = durationUs
            this.firstPeriodIndex = firstPeriodIndex
            this.lastPeriodIndex = lastPeriodIndex
            this.positionInFirstPeriodUs = positionInFirstPeriodUs
            isPlaceholder = false
            return this
        }

        /**
         * Returns the default position relative to the start of the window at which to begin playback,
         * in milliseconds. May be [C.TIME_UNSET] if and only if the window was populated with a
         * non-zero default position projection, and if the specified projection cannot be performed
         * whilst remaining within the bounds of the window.
         */
        val defaultPositionMs: Long
            get() {
                return Util.usToMs(defaultPositionUs)
            }

        /** Returns the duration of the window in milliseconds, or [C.TIME_UNSET] if unknown.  */
        val durationMs: Long
            get() {
                return Util.usToMs(durationUs)
            }

        /**
         * Returns the position of the start of this window relative to the start of the first period
         * belonging to it, in milliseconds.
         */
        val positionInFirstPeriodMs: Long
            get() {
                return Util.usToMs(positionInFirstPeriodUs)
            }

        /**
         * Returns the current time in milliseconds since the Unix epoch.
         *
         *
         * This method applies [known corrections][.elapsedRealtimeEpochOffsetMs] made available
         * by the media such that this time corresponds to the clock of the media origin server.
         */
        val currentUnixTimeMs: Long
            get() {
                return Util.getNowUnixTimeMs(elapsedRealtimeEpochOffsetMs)
            }

        /** Returns whether this is a live stream.  */ // Verifies whether the deprecated isLive member field is in a correct state.
        fun isLive(): Boolean {
            Assertions.checkState(isLive == (liveConfiguration != null))
            return liveConfiguration != null
        }

        // Provide backward compatibility for tag.
        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null || !(javaClass == obj.javaClass)) {
                return false
            }
            val that: Window = obj as Window
            return (Util.areEqual(uid, that.uid)
                    && Util.areEqual(mediaItem, that.mediaItem)
                    && Util.areEqual(manifest, that.manifest)
                    && Util.areEqual(liveConfiguration, that.liveConfiguration)
                    && (presentationStartTimeMs == that.presentationStartTimeMs
                    ) && (windowStartTimeMs == that.windowStartTimeMs
                    ) && (elapsedRealtimeEpochOffsetMs == that.elapsedRealtimeEpochOffsetMs
                    ) && (isSeekable == that.isSeekable
                    ) && (isDynamic == that.isDynamic
                    ) && (isPlaceholder == that.isPlaceholder
                    ) && (defaultPositionUs == that.defaultPositionUs
                    ) && (durationUs == that.durationUs
                    ) && (firstPeriodIndex == that.firstPeriodIndex
                    ) && (lastPeriodIndex == that.lastPeriodIndex
                    ) && (positionInFirstPeriodUs == that.positionInFirstPeriodUs))
        }

        // Provide backward compatibility for tag.
        public override fun hashCode(): Int {
            var result: Int = 7
            result = 31 * result + uid.hashCode()
            result = 31 * result + mediaItem.hashCode()
            result = 31 * result + (if (manifest == null) 0 else manifest.hashCode())
            result = 31 * result + (if (liveConfiguration == null) 0 else liveConfiguration.hashCode())
            result = 31 * result + (presentationStartTimeMs xor (presentationStartTimeMs ushr 32)).toInt()
            result = 31 * result + (windowStartTimeMs xor (windowStartTimeMs ushr 32)).toInt()
            result = (31 * result
                    + (elapsedRealtimeEpochOffsetMs xor (elapsedRealtimeEpochOffsetMs ushr 32)).toInt())
            result = 31 * result + (if (isSeekable) 1 else 0)
            result = 31 * result + (if (isDynamic) 1 else 0)
            result = 31 * result + (if (isPlaceholder) 1 else 0)
            result = 31 * result + (defaultPositionUs xor (defaultPositionUs ushr 32)).toInt()
            result = 31 * result + (durationUs xor (durationUs ushr 32)).toInt()
            result = 31 * result + firstPeriodIndex
            result = 31 * result + lastPeriodIndex
            result = 31 * result + (positionInFirstPeriodUs xor (positionInFirstPeriodUs ushr 32)).toInt()
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_MEDIA_ITEM, FIELD_PRESENTATION_START_TIME_MS, FIELD_WINDOW_START_TIME_MS, FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS, FIELD_IS_SEEKABLE, FIELD_IS_DYNAMIC, FIELD_LIVE_CONFIGURATION, FIELD_IS_PLACEHOLDER, FIELD_DEFAULT_POSITION_US, FIELD_DURATION_US, FIELD_FIRST_PERIOD_INDEX, FIELD_LAST_PERIOD_INDEX, FIELD_POSITION_IN_FIRST_PERIOD_US])
        private annotation class FieldNumber constructor()

        fun toBundle(excludeMediaItem: Boolean): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putBundle(
                    keyForField(FIELD_MEDIA_ITEM),
                    if (excludeMediaItem) MediaItem.Companion.EMPTY.toBundle() else mediaItem!!.toBundle())
            bundle.putLong(keyForField(FIELD_PRESENTATION_START_TIME_MS), presentationStartTimeMs)
            bundle.putLong(keyForField(FIELD_WINDOW_START_TIME_MS), windowStartTimeMs)
            bundle.putLong(
                    keyForField(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS), elapsedRealtimeEpochOffsetMs)
            bundle.putBoolean(keyForField(FIELD_IS_SEEKABLE), isSeekable)
            bundle.putBoolean(keyForField(FIELD_IS_DYNAMIC), isDynamic)
            val liveConfiguration: LiveConfiguration? = liveConfiguration
            if (liveConfiguration != null) {
                bundle.putBundle(keyForField(FIELD_LIVE_CONFIGURATION), liveConfiguration.toBundle())
            }
            bundle.putBoolean(keyForField(FIELD_IS_PLACEHOLDER), isPlaceholder)
            bundle.putLong(keyForField(FIELD_DEFAULT_POSITION_US), defaultPositionUs)
            bundle.putLong(keyForField(FIELD_DURATION_US), durationUs)
            bundle.putInt(keyForField(FIELD_FIRST_PERIOD_INDEX), firstPeriodIndex)
            bundle.putInt(keyForField(FIELD_LAST_PERIOD_INDEX), lastPeriodIndex)
            bundle.putLong(keyForField(FIELD_POSITION_IN_FIRST_PERIOD_US), positionInFirstPeriodUs)
            return bundle
        }

        /**
         * {@inheritDoc}
         *
         *
         * It omits the [.uid] and [.manifest] fields. The [.uid] of an instance
         * restored by [.CREATOR] will be a fake [Object] and the [.manifest] of the
         * instance will be `null`.
         */
        // TODO(b/166765820): See if missing fields would be okay and add them to the Bundle otherwise.
        public override fun toBundle(): Bundle {
            return toBundle( /* excludeMediaItem= */false)
        }

        /** Creates window.  */
        init {
            uid = SINGLE_WINDOW_UID
            mediaItem = EMPTY_MEDIA_ITEM
        }

        companion object {
            /**
             * A [.uid] for a window that must be used for single-window [Timelines][Timeline].
             */
            val SINGLE_WINDOW_UID: Any = Any()
            private val FAKE_WINDOW_UID: Any = Any()
            private val EMPTY_MEDIA_ITEM: MediaItem? = MediaItem.Builder()
                    .setMediaId("com.google.android.exoplayer2.Timeline")
                    .setUri(Uri.EMPTY)
                    .build()
            private val FIELD_MEDIA_ITEM: Int = 1
            private val FIELD_PRESENTATION_START_TIME_MS: Int = 2
            private val FIELD_WINDOW_START_TIME_MS: Int = 3
            private val FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS: Int = 4
            private val FIELD_IS_SEEKABLE: Int = 5
            private val FIELD_IS_DYNAMIC: Int = 6
            private val FIELD_LIVE_CONFIGURATION: Int = 7
            private val FIELD_IS_PLACEHOLDER: Int = 8
            private val FIELD_DEFAULT_POSITION_US: Int = 9
            private val FIELD_DURATION_US: Int = 10
            private val FIELD_FIRST_PERIOD_INDEX: Int = 11
            private val FIELD_LAST_PERIOD_INDEX: Int = 12
            private val FIELD_POSITION_IN_FIRST_PERIOD_US: Int = 13

            /**
             * Object that can restore [Period] from a [Bundle].
             *
             *
             * The [.uid] of a restored instance will be a fake [Object] and the [ ][.manifest] of the instance will be `null`.
             */
            val CREATOR: Bundleable.Creator<Window> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
            private fun fromBundle(bundle: Bundle): Window {
                val mediaItemBundle: Bundle? = bundle.getBundle(keyForField(FIELD_MEDIA_ITEM))
                val mediaItem: MediaItem? = if (mediaItemBundle != null) MediaItem.Companion.CREATOR.fromBundle(mediaItemBundle) else null
                val presentationStartTimeMs: Long = bundle.getLong(
                        keyForField(FIELD_PRESENTATION_START_TIME_MS),  /* defaultValue= */C.TIME_UNSET)
                val windowStartTimeMs: Long = bundle.getLong(keyForField(FIELD_WINDOW_START_TIME_MS),  /* defaultValue= */C.TIME_UNSET)
                val elapsedRealtimeEpochOffsetMs: Long = bundle.getLong(
                        keyForField(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS),  /* defaultValue= */
                        C.TIME_UNSET)
                val isSeekable: Boolean = bundle.getBoolean(keyForField(FIELD_IS_SEEKABLE),  /* defaultValue= */false)
                val isDynamic: Boolean = bundle.getBoolean(keyForField(FIELD_IS_DYNAMIC),  /* defaultValue= */false)
                val liveConfigurationBundle: Bundle? = bundle.getBundle(keyForField(FIELD_LIVE_CONFIGURATION))
                val liveConfiguration: LiveConfiguration? = if (liveConfigurationBundle != null) LiveConfiguration.Companion.CREATOR.fromBundle(liveConfigurationBundle) else null
                val isPlaceHolder: Boolean = bundle.getBoolean(keyForField(FIELD_IS_PLACEHOLDER),  /* defaultValue= */false)
                val defaultPositionUs: Long = bundle.getLong(keyForField(FIELD_DEFAULT_POSITION_US),  /* defaultValue= */0)
                val durationUs: Long = bundle.getLong(keyForField(FIELD_DURATION_US),  /* defaultValue= */C.TIME_UNSET)
                val firstPeriodIndex: Int = bundle.getInt(keyForField(FIELD_FIRST_PERIOD_INDEX),  /* defaultValue= */0)
                val lastPeriodIndex: Int = bundle.getInt(keyForField(FIELD_LAST_PERIOD_INDEX),  /* defaultValue= */0)
                val positionInFirstPeriodUs: Long = bundle.getLong(keyForField(FIELD_POSITION_IN_FIRST_PERIOD_US),  /* defaultValue= */0)
                val window: Window = Window()
                window.set(
                        FAKE_WINDOW_UID,
                        mediaItem,  /* manifest= */
                        null,
                        presentationStartTimeMs,
                        windowStartTimeMs,
                        elapsedRealtimeEpochOffsetMs,
                        isSeekable,
                        isDynamic,
                        liveConfiguration,
                        defaultPositionUs,
                        durationUs,
                        firstPeriodIndex,
                        lastPeriodIndex,
                        positionInFirstPeriodUs)
                window.isPlaceholder = isPlaceHolder
                return window
            }

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /**
     * Holds information about a period in a [Timeline]. A period defines a single logical piece
     * of media, for example a media file. It may also define groups of ads inserted into the media,
     * along with information about whether those ads have been loaded and played.
     *
     *
     * The figure below shows some of the information defined by a period, as well as how this
     * information relates to a corresponding [Window] in the timeline.
     *
     *
     * <img src="doc-files/timeline-period.svg" alt="Information defined by a
    period"></img>
     */
    class Period constructor() : Bundleable {
        /**
         * An identifier for the period. Not necessarily unique. May be null if the ids of the period
         * are not required.
         */
        var id: Any? = null

        /**
         * A unique identifier for the period. May be null if the ids of the period are not required.
         */
        var uid: Any? = null

        /** The index of the window to which this period belongs.  */
        var windowIndex: Int = 0
        /** Returns the duration of this period in microseconds, or [C.TIME_UNSET] if unknown.  */
        /** The duration of this period in microseconds, or [C.TIME_UNSET] if unknown.  */
        var durationUs: Long = 0
        /**
         * Returns the position of the start of this period relative to the start of the window to which
         * it belongs, in microseconds. May be negative if the start of the period is not within the
         * window.
         */
        /**
         * The position of the start of this period relative to the start of the window to which it
         * belongs, in microseconds. May be negative if the start of the period is not within the
         * window.
         */
        var positionInWindowUs: Long = 0

        /**
         * Whether this period contains placeholder information because the real information has yet to
         * be loaded.
         */
        var isPlaceholder: Boolean = false
        var adPlaybackState: AdPlaybackState

        /**
         * Sets the data held by this period.
         *
         * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
         * period are not required.
         * @param uid A unique identifier for the period. May be null if the ids of the period are not
         * required.
         * @param windowIndex The index of the window to which this period belongs.
         * @param durationUs The duration of this period in microseconds, or [C.TIME_UNSET] if
         * unknown.
         * @param positionInWindowUs The position of the start of this period relative to the start of
         * the window to which it belongs, in milliseconds. May be negative if the start of the
         * period is not within the window.
         * @return This period, for convenience.
         */
        @CanIgnoreReturnValue
        operator fun set(
                id: Any?,
                uid: Any?,
                windowIndex: Int,
                durationUs: Long,
                positionInWindowUs: Long): Period {
            return set(
                    id,
                    uid,
                    windowIndex,
                    durationUs,
                    positionInWindowUs,
                    AdPlaybackState.Companion.NONE,  /* isPlaceholder= */
                    false)
        }

        /**
         * Sets the data held by this period.
         *
         * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
         * period are not required.
         * @param uid A unique identifier for the period. May be null if the ids of the period are not
         * required.
         * @param windowIndex The index of the window to which this period belongs.
         * @param durationUs The duration of this period in microseconds, or [C.TIME_UNSET] if
         * unknown.
         * @param positionInWindowUs The position of the start of this period relative to the start of
         * the window to which it belongs, in milliseconds. May be negative if the start of the
         * period is not within the window.
         * @param adPlaybackState The state of the period's ads, or [AdPlaybackState.NONE] if
         * there are no ads.
         * @param isPlaceholder Whether this period contains placeholder information because the real
         * information has yet to be loaded.
         * @return This period, for convenience.
         */
        @CanIgnoreReturnValue
        operator fun set(
                id: Any?,
                uid: Any?,
                windowIndex: Int,
                durationUs: Long,
                positionInWindowUs: Long,
                adPlaybackState: AdPlaybackState,
                isPlaceholder: Boolean): Period {
            this.id = id
            this.uid = uid
            this.windowIndex = windowIndex
            this.durationUs = durationUs
            this.positionInWindowUs = positionInWindowUs
            this.adPlaybackState = adPlaybackState
            this.isPlaceholder = isPlaceholder
            return this
        }

        /** Returns the duration of the period in milliseconds, or [C.TIME_UNSET] if unknown.  */
        val durationMs: Long
            get() {
                return Util.usToMs(durationUs)
            }

        /**
         * Returns the position of the start of this period relative to the start of the window to which
         * it belongs, in milliseconds. May be negative if the start of the period is not within the
         * window.
         */
        val positionInWindowMs: Long
            get() {
                return Util.usToMs(positionInWindowUs)
            }

        /** Returns the opaque identifier for ads played with this period, or `null` if unset.  */
        val adsId: Any?
            get() {
                return adPlaybackState.adsId
            }

        /** Returns the number of ad groups in the period.  */
        val adGroupCount: Int
            get() {
                return adPlaybackState.adGroupCount
            }

        /**
         * Returns the number of removed ad groups in the period. Ad groups with indices between `0` (inclusive) and `removedAdGroupCount` (exclusive) will be empty.
         */
        val removedAdGroupCount: Int
            get() {
                return adPlaybackState.removedAdGroupCount
            }

        /**
         * Returns the time of the ad group at index `adGroupIndex` in the period, in
         * microseconds.
         *
         * @param adGroupIndex The ad group index.
         * @return The time of the ad group at the index relative to the start of the enclosing [     ], in microseconds, or [C.TIME_END_OF_SOURCE] for a post-roll ad group.
         */
        fun getAdGroupTimeUs(adGroupIndex: Int): Long {
            return adPlaybackState.getAdGroup(adGroupIndex).timeUs
        }

        /**
         * Returns the index of the first ad in the specified ad group that should be played, or the
         * number of ads in the ad group if no ads should be played.
         *
         * @param adGroupIndex The ad group index.
         * @return The index of the first ad that should be played, or the number of ads in the ad group
         * if no ads should be played.
         */
        fun getFirstAdIndexToPlay(adGroupIndex: Int): Int {
            return adPlaybackState.getAdGroup(adGroupIndex).getFirstAdIndexToPlay()
        }

        /**
         * Returns the index of the next ad in the specified ad group that should be played after
         * playing `adIndexInAdGroup`, or the number of ads in the ad group if no later ads should
         * be played.
         *
         * @param adGroupIndex The ad group index.
         * @param lastPlayedAdIndex The last played ad index in the ad group.
         * @return The index of the next ad that should be played, or the number of ads in the ad group
         * if the ad group does not have any ads remaining to play.
         */
        fun getNextAdIndexToPlay(adGroupIndex: Int, lastPlayedAdIndex: Int): Int {
            return adPlaybackState.getAdGroup(adGroupIndex).getNextAdIndexToPlay(lastPlayedAdIndex)
        }

        /**
         * Returns whether all ads in the ad group at index `adGroupIndex` have been played,
         * skipped or failed.
         *
         * @param adGroupIndex The ad group index.
         * @return Whether all ads in the ad group at index `adGroupIndex` have been played,
         * skipped or failed.
         */
        fun hasPlayedAdGroup(adGroupIndex: Int): Boolean {
            return !adPlaybackState.getAdGroup(adGroupIndex).hasUnplayedAds()
        }

        /**
         * Returns the index of the ad group at or before `positionUs` in the period that should
         * be played before the content at `positionUs`. Returns [C.INDEX_UNSET] if the ad
         * group at or before `positionUs` has no ads remaining to be played, or if there is no
         * such ad group.
         *
         * @param positionUs The period position at or before which to find an ad group, in
         * microseconds.
         * @return The index of the ad group, or [C.INDEX_UNSET].
         */
        fun getAdGroupIndexForPositionUs(positionUs: Long): Int {
            return adPlaybackState.getAdGroupIndexForPositionUs(positionUs, durationUs)
        }

        /**
         * Returns the index of the next ad group after `positionUs` in the period that has ads
         * that should be played. Returns [C.INDEX_UNSET] if there is no such ad group.
         *
         * @param positionUs The period position after which to find an ad group, in microseconds.
         * @return The index of the ad group, or [C.INDEX_UNSET].
         */
        fun getAdGroupIndexAfterPositionUs(positionUs: Long): Int {
            return adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, durationUs)
        }

        /**
         * Returns the number of ads in the ad group at index `adGroupIndex`, or [ ][C.LENGTH_UNSET] if not yet known.
         *
         * @param adGroupIndex The ad group index.
         * @return The number of ads in the ad group, or [C.LENGTH_UNSET] if not yet known.
         */
        fun getAdCountInAdGroup(adGroupIndex: Int): Int {
            return adPlaybackState.getAdGroup(adGroupIndex).count
        }

        /**
         * Returns the duration of the ad at index `adIndexInAdGroup` in the ad group at `adGroupIndex`, in microseconds, or [C.TIME_UNSET] if not yet known.
         *
         * @param adGroupIndex The ad group index.
         * @param adIndexInAdGroup The ad index in the ad group.
         * @return The duration of the ad, or [C.TIME_UNSET] if not yet known.
         */
        fun getAdDurationUs(adGroupIndex: Int, adIndexInAdGroup: Int): Long {
            val adGroup: AdGroup? = adPlaybackState.getAdGroup(adGroupIndex)
            return if (adGroup!!.count != C.LENGTH_UNSET) adGroup.durationsUs.get(adIndexInAdGroup) else C.TIME_UNSET
        }

        /**
         * Returns the state of the ad at index `adIndexInAdGroup` in the ad group at `adGroupIndex`, or [AdPlaybackState.AD_STATE_UNAVAILABLE] if not yet known.
         *
         * @param adGroupIndex The ad group index.
         * @return The state of the ad, or [AdPlaybackState.AD_STATE_UNAVAILABLE] if not yet
         * known.
         */
        fun getAdState(adGroupIndex: Int, adIndexInAdGroup: Int): Int {
            val adGroup: AdGroup? = adPlaybackState.getAdGroup(adGroupIndex)
            return if (adGroup!!.count != C.LENGTH_UNSET) adGroup.states.get(adIndexInAdGroup) else AdPlaybackState.Companion.AD_STATE_UNAVAILABLE
        }

        /**
         * Returns the position offset in the first unplayed ad at which to begin playback, in
         * microseconds.
         */
        val adResumePositionUs: Long
            get() {
                return adPlaybackState.adResumePositionUs
            }

        /**
         * Returns whether the ad group at index `adGroupIndex` is server-side inserted and part
         * of the content stream.
         *
         * @param adGroupIndex The ad group index.
         * @return Whether this ad group is server-side inserted and part of the content stream.
         */
        fun isServerSideInsertedAdGroup(adGroupIndex: Int): Boolean {
            return adPlaybackState.getAdGroup(adGroupIndex).isServerSideInserted
        }

        /**
         * Returns the offset in microseconds which should be added to the content stream when resuming
         * playback after the specified ad group.
         *
         * @param adGroupIndex The ad group index.
         * @return The offset that should be added to the content stream, in microseconds.
         */
        fun getContentResumeOffsetUs(adGroupIndex: Int): Long {
            return adPlaybackState.getAdGroup(adGroupIndex).contentResumeOffsetUs
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null || !(javaClass == obj.javaClass)) {
                return false
            }
            val that: Period = obj as Period
            return (Util.areEqual(id, that.id)
                    && Util.areEqual(uid, that.uid)
                    && (windowIndex == that.windowIndex
                    ) && (durationUs == that.durationUs
                    ) && (positionInWindowUs == that.positionInWindowUs
                    ) && (isPlaceholder == that.isPlaceholder
                    ) && Util.areEqual(adPlaybackState, that.adPlaybackState))
        }

        public override fun hashCode(): Int {
            var result: Int = 7
            result = 31 * result + (if (id == null) 0 else id.hashCode())
            result = 31 * result + (if (uid == null) 0 else uid.hashCode())
            result = 31 * result + windowIndex
            result = 31 * result + (durationUs xor (durationUs ushr 32)).toInt()
            result = 31 * result + (positionInWindowUs xor (positionInWindowUs ushr 32)).toInt()
            result = 31 * result + (if (isPlaceholder) 1 else 0)
            result = 31 * result + adPlaybackState.hashCode()
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_WINDOW_INDEX, FIELD_DURATION_US, FIELD_POSITION_IN_WINDOW_US, FIELD_PLACEHOLDER, FIELD_AD_PLAYBACK_STATE])
        private annotation class FieldNumber constructor()

        /**
         * {@inheritDoc}
         *
         *
         * It omits the [.id] and [.uid] fields so these fields of an instance restored
         * by [.CREATOR] will always be `null`.
         */
        // TODO(b/166765820): See if missing fields would be okay and add them to the Bundle otherwise.
        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putInt(keyForField(FIELD_WINDOW_INDEX), windowIndex)
            bundle.putLong(keyForField(FIELD_DURATION_US), durationUs)
            bundle.putLong(keyForField(FIELD_POSITION_IN_WINDOW_US), positionInWindowUs)
            bundle.putBoolean(keyForField(FIELD_PLACEHOLDER), isPlaceholder)
            bundle.putBundle(keyForField(FIELD_AD_PLAYBACK_STATE), adPlaybackState.toBundle())
            return bundle
        }

        /** Creates a new instance with no ad playback state.  */
        init {
            adPlaybackState = AdPlaybackState.Companion.NONE
        }

        companion object {
            private val FIELD_WINDOW_INDEX: Int = 0
            private val FIELD_DURATION_US: Int = 1
            private val FIELD_POSITION_IN_WINDOW_US: Int = 2
            private val FIELD_PLACEHOLDER: Int = 3
            private val FIELD_AD_PLAYBACK_STATE: Int = 4

            /**
             * Object that can restore [Period] from a [Bundle].
             *
             *
             * The [.id] and [.uid] of restored instances will always be `null`.
             */
            val CREATOR: Bundleable.Creator<Period> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
            private fun fromBundle(bundle: Bundle): Period {
                val windowIndex: Int = bundle.getInt(keyForField(FIELD_WINDOW_INDEX),  /* defaultValue= */0)
                val durationUs: Long = bundle.getLong(keyForField(FIELD_DURATION_US),  /* defaultValue= */C.TIME_UNSET)
                val positionInWindowUs: Long = bundle.getLong(keyForField(FIELD_POSITION_IN_WINDOW_US),  /* defaultValue= */0)
                val isPlaceholder: Boolean = bundle.getBoolean(keyForField(FIELD_PLACEHOLDER))
                val adPlaybackStateBundle: Bundle? = bundle.getBundle(keyForField(FIELD_AD_PLAYBACK_STATE))
                val adPlaybackState: AdPlaybackState = if (adPlaybackStateBundle != null) AdPlaybackState.Companion.CREATOR.fromBundle(adPlaybackStateBundle) else AdPlaybackState.Companion.NONE
                val period: Period = Period()
                period.set( /* id= */
                        null,  /* uid= */
                        null,
                        windowIndex,
                        durationUs,
                        positionInWindowUs,
                        adPlaybackState,
                        isPlaceholder)
                return period
            }

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /** Returns whether the timeline is empty.  */
    val isEmpty: Boolean
        get() {
            return windowCount == 0
        }

    /** Returns the number of windows in the timeline.  */
    abstract val windowCount: Int

    /**
     * Returns the index of the window after the window at index `windowIndex` depending on the
     * `repeatMode` and whether shuffling is enabled.
     *
     * @param windowIndex Index of a window in the timeline.
     * @param repeatMode A repeat mode.
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return The index of the next window, or [C.INDEX_UNSET] if this is the last window.
     */
    open fun getNextWindowIndex(
            windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
        when (repeatMode) {
            Player.Companion.REPEAT_MODE_OFF -> return if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) C.INDEX_UNSET else windowIndex + 1
            Player.Companion.REPEAT_MODE_ONE -> return windowIndex
            Player.Companion.REPEAT_MODE_ALL -> return if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) getFirstWindowIndex(shuffleModeEnabled) else windowIndex + 1
            else -> throw IllegalStateException()
        }
    }

    /**
     * Returns the index of the window before the window at index `windowIndex` depending on the
     * `repeatMode` and whether shuffling is enabled.
     *
     * @param windowIndex Index of a window in the timeline.
     * @param repeatMode A repeat mode.
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return The index of the previous window, or [C.INDEX_UNSET] if this is the first window.
     */
    open fun getPreviousWindowIndex(
            windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
        when (repeatMode) {
            Player.Companion.REPEAT_MODE_OFF -> return if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) C.INDEX_UNSET else windowIndex - 1
            Player.Companion.REPEAT_MODE_ONE -> return windowIndex
            Player.Companion.REPEAT_MODE_ALL -> return if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) getLastWindowIndex(shuffleModeEnabled) else windowIndex - 1
            else -> throw IllegalStateException()
        }
    }

    /**
     * Returns the index of the last window in the playback order depending on whether shuffling is
     * enabled.
     *
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return The index of the last window in the playback order, or [C.INDEX_UNSET] if the
     * timeline is empty.
     */
    open fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int {
        return if (isEmpty) C.INDEX_UNSET else windowCount - 1
    }

    /**
     * Returns the index of the first window in the playback order depending on whether shuffling is
     * enabled.
     *
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return The index of the first window in the playback order, or [C.INDEX_UNSET] if the
     * timeline is empty.
     */
    open fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int {
        return if (isEmpty) C.INDEX_UNSET else 0
    }

    /**
     * Populates a [Window] with data for the window at the specified index.
     *
     * @param windowIndex The index of the window.
     * @param window The [Window] to populate. Must not be null.
     * @return The populated [Window], for convenience.
     */
    fun getWindow(windowIndex: Int, window: Window): Window {
        return getWindow(windowIndex, window,  /* defaultPositionProjectionUs= */0)
    }

    /**
     * Populates a [Window] with data for the window at the specified index.
     *
     * @param windowIndex The index of the window.
     * @param window The [Window] to populate. Must not be null.
     * @param defaultPositionProjectionUs A duration into the future that the populated window's
     * default start position should be projected.
     * @return The populated [Window], for convenience.
     */
    abstract fun getWindow(
            windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window

    /** Returns the number of periods in the timeline.  */
    abstract val periodCount: Int

    /**
     * Returns the index of the period after the period at index `periodIndex` depending on the
     * `repeatMode` and whether shuffling is enabled.
     *
     * @param periodIndex Index of a period in the timeline.
     * @param period A [Period] to be used internally. Must not be null.
     * @param window A [Window] to be used internally. Must not be null.
     * @param repeatMode A repeat mode.
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return The index of the next period, or [C.INDEX_UNSET] if this is the last period.
     */
    fun getNextPeriodIndex(
            periodIndex: Int,
            period: Period,
            window: Window,
            repeatMode: @Player.RepeatMode Int,
            shuffleModeEnabled: Boolean): Int {
        val windowIndex: Int = getPeriod(periodIndex, period).windowIndex
        if (getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
            val nextWindowIndex: Int = getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
            if (nextWindowIndex == C.INDEX_UNSET) {
                return C.INDEX_UNSET
            }
            return getWindow(nextWindowIndex, window).firstPeriodIndex
        }
        return periodIndex + 1
    }

    /**
     * Returns whether the given period is the last period of the timeline depending on the `repeatMode` and whether shuffling is enabled.
     *
     * @param periodIndex A period index.
     * @param period A [Period] to be used internally. Must not be null.
     * @param window A [Window] to be used internally. Must not be null.
     * @param repeatMode A repeat mode.
     * @param shuffleModeEnabled Whether shuffling is enabled.
     * @return Whether the period of the given index is the last period of the timeline.
     */
    fun isLastPeriod(
            periodIndex: Int,
            period: Period,
            window: Window,
            repeatMode: @Player.RepeatMode Int,
            shuffleModeEnabled: Boolean): Boolean {
        return (getNextPeriodIndex(periodIndex, period, window, repeatMode, shuffleModeEnabled)
                == C.INDEX_UNSET)
    }

    @InlineMe(replacement = "this.getPeriodPositionUs(window, period, windowIndex, windowPositionUs)")
    @Deprecated("Use {@link #getPeriodPositionUs(Window, Period, int, long)} instead.")
    fun getPeriodPosition(
            window: Window, period: Period, windowIndex: Int, windowPositionUs: Long): Pair<Any, Long>? {
        return getPeriodPositionUs(window, period, windowIndex, windowPositionUs)
    }

    @InlineMe(replacement = ("this.getPeriodPositionUs("
            + "window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs)"))
    @Deprecated("Use {@link #getPeriodPositionUs(Window, Period, int, long, long)} instead.")
    fun getPeriodPosition(
            window: Window,
            period: Period,
            windowIndex: Int,
            windowPositionUs: Long,
            defaultPositionProjectionUs: Long): Pair<Any?, Long>? {
        return getPeriodPositionUs(
                window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs)
    }

    /**
     * Calls [.getPeriodPositionUs] with a zero default position
     * projection.
     */
    fun getPeriodPositionUs(
            window: Window, period: Period, windowIndex: Int, windowPositionUs: Long): Pair<Any, Long>? {
        return Assertions.checkNotNull<Pair<Any, Long>?>(
                getPeriodPositionUs(
                        window, period, windowIndex, windowPositionUs,  /* defaultPositionProjectionUs= */0))
    }

    /**
     * Converts `(windowIndex, windowPositionUs)` to the corresponding `(periodUid,
     * periodPositionUs)`. The returned `periodPositionUs` is constrained to be non-negative,
     * and to be less than the containing period's duration if it is known.
     *
     * @param window A [Window] that may be overwritten.
     * @param period A [Period] that may be overwritten.
     * @param windowIndex The window index.
     * @param windowPositionUs The window time, or [C.TIME_UNSET] to use the window's default
     * start position.
     * @param defaultPositionProjectionUs If `windowPositionUs` is [C.TIME_UNSET], the
     * duration into the future by which the window's position should be projected.
     * @return The corresponding (periodUid, periodPositionUs), or null if `#windowPositionUs`
     * is [C.TIME_UNSET], `defaultPositionProjectionUs` is non-zero, and the window's
     * position could not be projected by `defaultPositionProjectionUs`.
     */
    fun getPeriodPositionUs(
            window: Window,
            period: Period,
            windowIndex: Int,
            windowPositionUs: Long,
            defaultPositionProjectionUs: Long): Pair<Any?, Long>? {
        var windowPositionUs: Long = windowPositionUs
        Assertions.checkIndex(windowIndex, 0, windowCount)
        getWindow(windowIndex, window, defaultPositionProjectionUs)
        if (windowPositionUs == C.TIME_UNSET) {
            windowPositionUs = window.defaultPositionUs
            if (windowPositionUs == C.TIME_UNSET) {
                return null
            }
        }
        var periodIndex: Int = window.firstPeriodIndex
        getPeriod(periodIndex, period)
        while ((periodIndex < window.lastPeriodIndex
                        ) && (period.positionInWindowUs != windowPositionUs
                        ) && (getPeriod(periodIndex + 1, period).positionInWindowUs <= windowPositionUs)) {
            periodIndex++
        }
        getPeriod(periodIndex, period,  /* setIds= */true)
        var periodPositionUs: Long = windowPositionUs - period.positionInWindowUs
        // The period positions must be less than the period duration, if it is known.
        if (period.durationUs != C.TIME_UNSET) {
            periodPositionUs = Math.min(periodPositionUs, period.durationUs - 1)
        }
        // Period positions cannot be negative.
        periodPositionUs = Math.max(0, periodPositionUs)
        return Pair.create(Assertions.checkNotNull(period.uid), periodPositionUs)
    }

    /**
     * Populates a [Period] with data for the period with the specified unique identifier.
     *
     * @param periodUid The unique identifier of the period.
     * @param period The [Period] to populate. Must not be null.
     * @return The populated [Period], for convenience.
     */
    open fun getPeriodByUid(periodUid: Any?, period: Period): Period? {
        return getPeriod(getIndexOfPeriod(periodUid), period,  /* setIds= */true)
    }

    /**
     * Populates a [Period] with data for the period at the specified index. [Period.id]
     * and [Period.uid] will be set to null.
     *
     * @param periodIndex The index of the period.
     * @param period The [Period] to populate. Must not be null.
     * @return The populated [Period], for convenience.
     */
    fun getPeriod(periodIndex: Int, period: Period): Period {
        return getPeriod(periodIndex, period, false)
    }

    /**
     * Populates a [Period] with data for the period at the specified index.
     *
     * @param periodIndex The index of the period.
     * @param period The [Period] to populate. Must not be null.
     * @param setIds Whether [Period.id] and [Period.uid] should be populated. If false,
     * the fields will be set to null. The caller should pass false for efficiency reasons unless
     * the fields are required.
     * @return The populated [Period], for convenience.
     */
    abstract fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period

    /**
     * Returns the index of the period identified by its unique [Period.uid], or [ ][C.INDEX_UNSET] if the period is not in the timeline.
     *
     * @param uid A unique identifier for a period.
     * @return The index of the period, or [C.INDEX_UNSET] if the period was not found.
     */
    abstract fun getIndexOfPeriod(uid: Any?): Int

    /**
     * Returns the unique id of the period identified by its index in the timeline.
     *
     * @param periodIndex The index of the period.
     * @return The unique id of the period.
     */
    abstract fun getUidOfPeriod(periodIndex: Int): Any?
    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (!(obj is Timeline)) {
            return false
        }
        val other: Timeline = obj
        if (other.windowCount != windowCount || other.periodCount != periodCount) {
            return false
        }
        val window: Window = Window()
        val period: Period = Period()
        val otherWindow: Window = Window()
        val otherPeriod: Period = Period()
        for (i in 0 until windowCount) {
            if (!(getWindow(i, window) == other.getWindow(i, otherWindow))) {
                return false
            }
        }
        for (i in 0 until periodCount) {
            if (!(getPeriod(i, period,  /* setIds= */true)
                            == other.getPeriod(i, otherPeriod,  /* setIds= */true))) {
                return false
            }
        }

        // Check shuffled order
        var windowIndex: Int = getFirstWindowIndex( /* shuffleModeEnabled= */true)
        if (windowIndex != other.getFirstWindowIndex( /* shuffleModeEnabled= */true)) {
            return false
        }
        val lastWindowIndex: Int = getLastWindowIndex( /* shuffleModeEnabled= */true)
        if (lastWindowIndex != other.getLastWindowIndex( /* shuffleModeEnabled= */true)) {
            return false
        }
        while (windowIndex != lastWindowIndex) {
            val nextWindowIndex: Int = getNextWindowIndex(windowIndex, Player.Companion.REPEAT_MODE_OFF,  /* shuffleModeEnabled= */true)
            if ((nextWindowIndex
                            != other.getNextWindowIndex(
                            windowIndex, Player.Companion.REPEAT_MODE_OFF,  /* shuffleModeEnabled= */true))) {
                return false
            }
            windowIndex = nextWindowIndex
        }
        return true
    }

    public override fun hashCode(): Int {
        val window: Window = Window()
        val period: Period = Period()
        var result: Int = 7
        result = 31 * result + windowCount
        for (i in 0 until windowCount) {
            result = 31 * result + getWindow(i, window).hashCode()
        }
        result = 31 * result + periodCount
        for (i in 0 until periodCount) {
            result = 31 * result + getPeriod(i, period,  /* setIds= */true).hashCode()
        }
        var windowIndex: Int = getFirstWindowIndex(true)
        while (windowIndex != C.INDEX_UNSET) {
            result = 31 * result + windowIndex
            windowIndex = getNextWindowIndex(windowIndex, Player.Companion.REPEAT_MODE_OFF, true)
        }
        return result
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_WINDOWS, FIELD_PERIODS, FIELD_SHUFFLED_WINDOW_INDICES])
    private annotation class FieldNumber constructor()

    /**
     * {@inheritDoc}
     *
     *
     * The [.getWindow] windows} and [periods][.getPeriod] of
     * an instance restored by [.CREATOR] may have missing fields as described in [ ][Window.toBundle] and [Period.toBundle].
     *
     * @param excludeMediaItems Whether to exclude all [media items][Window.mediaItem] of windows
     * in the timeline.
     */
    fun toBundle(excludeMediaItems: Boolean): Bundle {
        val windowBundles: MutableList<Bundle> = ArrayList()
        val windowCount: Int = windowCount
        val window: Window = Window()
        for (i in 0 until windowCount) {
            windowBundles.add(
                    getWindow(i, window,  /* defaultPositionProjectionUs= */0).toBundle(excludeMediaItems))
        }
        val periodBundles: MutableList<Bundle> = ArrayList()
        val periodCount: Int = periodCount
        val period: Period = Period()
        for (i in 0 until periodCount) {
            periodBundles.add(getPeriod(i, period,  /* setIds= */false).toBundle())
        }
        val shuffledWindowIndices: IntArray = IntArray(windowCount)
        if (windowCount > 0) {
            shuffledWindowIndices.get(0) = getFirstWindowIndex( /* shuffleModeEnabled= */true)
        }
        for (i in 1 until windowCount) {
            shuffledWindowIndices.get(i) = getNextWindowIndex(
                    shuffledWindowIndices.get(i - 1), Player.Companion.REPEAT_MODE_OFF,  /* shuffleModeEnabled= */true)
        }
        val bundle: Bundle = Bundle()
        BundleUtil.putBinder(
                bundle, keyForField(FIELD_WINDOWS), BundleListRetriever(windowBundles))
        BundleUtil.putBinder(
                bundle, keyForField(FIELD_PERIODS), BundleListRetriever(periodBundles))
        bundle.putIntArray(keyForField(FIELD_SHUFFLED_WINDOW_INDICES), shuffledWindowIndices)
        return bundle
    }

    /**
     * {@inheritDoc}
     *
     *
     * The [.getWindow] windows} and [periods][.getPeriod] of
     * an instance restored by [.CREATOR] may have missing fields as described in [ ][Window.toBundle] and [Period.toBundle].
     */
    public override fun toBundle(): Bundle {
        return toBundle( /* excludeMediaItems= */false)
    }

    /**
     * A concrete class of [Timeline] to restore a [Timeline] instance from a [ ] sent by another process via [IBinder].
     */
    class RemotableTimeline constructor(
            windows: ImmutableList<Window>, periods: ImmutableList<Period>, shuffledWindowIndices: IntArray) : Timeline() {
        private val windows: ImmutableList<Window>
        private val periods: ImmutableList<Period>
        private val shuffledWindowIndices: IntArray
        private val windowIndicesInShuffled: IntArray

        init {
            Assertions.checkArgument(windows.size == shuffledWindowIndices.size)
            this.windows = windows
            this.periods = periods
            this.shuffledWindowIndices = shuffledWindowIndices
            windowIndicesInShuffled = IntArray(shuffledWindowIndices.size)
            for (i in shuffledWindowIndices.indices) {
                windowIndicesInShuffled.get(shuffledWindowIndices.get(i)) = i
            }
        }

        public override fun getWindowCount(): Int {
            return windows.size
        }

        public override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            val w: Window = windows.get(windowIndex)
            window.set(
                    w.uid,
                    w.mediaItem,
                    w.manifest,
                    w.presentationStartTimeMs,
                    w.windowStartTimeMs,
                    w.elapsedRealtimeEpochOffsetMs,
                    w.isSeekable,
                    w.isDynamic,
                    w.liveConfiguration,
                    w.defaultPositionUs,
                    w.durationUs,
                    w.firstPeriodIndex,
                    w.lastPeriodIndex,
                    w.positionInFirstPeriodUs)
            window.isPlaceholder = w.isPlaceholder
            return window
        }

        public override fun getNextWindowIndex(
                windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
            if (repeatMode == Player.Companion.REPEAT_MODE_ONE) {
                return windowIndex
            }
            if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
                return if (repeatMode == Player.Companion.REPEAT_MODE_ALL) getFirstWindowIndex(shuffleModeEnabled) else C.INDEX_UNSET
            }
            return if (shuffleModeEnabled) shuffledWindowIndices.get(windowIndicesInShuffled.get(windowIndex) + 1) else windowIndex + 1
        }

        public override fun getPreviousWindowIndex(
                windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
            if (repeatMode == Player.Companion.REPEAT_MODE_ONE) {
                return windowIndex
            }
            if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
                return if (repeatMode == Player.Companion.REPEAT_MODE_ALL) getLastWindowIndex(shuffleModeEnabled) else C.INDEX_UNSET
            }
            return if (shuffleModeEnabled) shuffledWindowIndices.get(windowIndicesInShuffled.get(windowIndex) - 1) else windowIndex - 1
        }

        public override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int {
            if (isEmpty) {
                return C.INDEX_UNSET
            }
            return if (shuffleModeEnabled) shuffledWindowIndices.get(getWindowCount() - 1) else getWindowCount() - 1
        }

        public override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int {
            if (isEmpty) {
                return C.INDEX_UNSET
            }
            return if (shuffleModeEnabled) shuffledWindowIndices.get(0) else 0
        }

        public override fun getPeriodCount(): Int {
            return periods.size
        }

        public override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            val p: Period = periods.get(periodIndex)
            period.set(
                    p.id,
                    p.uid,
                    p.windowIndex,
                    p.durationUs,
                    p.positionInWindowUs,
                    p.adPlaybackState,
                    p.isPlaceholder)
            return period
        }

        public override fun getIndexOfPeriod(uid: Any?): Int {
            throw UnsupportedOperationException()
        }

        public override fun getUidOfPeriod(periodIndex: Int): Any? {
            throw UnsupportedOperationException()
        }
    }

    companion object {
        /** An empty timeline.  */
        val EMPTY: Timeline = object : Timeline() {
            override val windowCount: Int
                get() {
                    return 0
                }

            public override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                throw IndexOutOfBoundsException()
            }

            override val periodCount: Int
                get() {
                    return 0
                }

            public override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                throw IndexOutOfBoundsException()
            }

            public override fun getIndexOfPeriod(uid: Any?): Int {
                return C.INDEX_UNSET
            }

            public override fun getUidOfPeriod(periodIndex: Int): Any? {
                throw IndexOutOfBoundsException()
            }
        }
        private val FIELD_WINDOWS: Int = 0
        private val FIELD_PERIODS: Int = 1
        private val FIELD_SHUFFLED_WINDOW_INDICES: Int = 2

        /**
         * Object that can restore a [Timeline] from a [Bundle].
         *
         *
         * The [.getWindow] windows} and [periods][.getPeriod] of
         * a restored instance may have missing fields as described in [Window.CREATOR] and [ ][Period.CREATOR].
         */
        val CREATOR: Bundleable.Creator<Timeline> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): Timeline {
            val windows: ImmutableList<Window> = fromBundleListRetriever(
                    Window.CREATOR, BundleUtil.getBinder(bundle, keyForField(FIELD_WINDOWS)))
            val periods: ImmutableList<Period> = fromBundleListRetriever(
                    Period.CREATOR, BundleUtil.getBinder(bundle, keyForField(FIELD_PERIODS)))
            val shuffledWindowIndices: IntArray? = bundle.getIntArray(keyForField(FIELD_SHUFFLED_WINDOW_INDICES))
            return RemotableTimeline(
                    windows,
                    periods,
                    if (shuffledWindowIndices == null) generateUnshuffledIndices(windows.size) else shuffledWindowIndices)
        }

        private fun <T : Bundleable?> fromBundleListRetriever(
                creator: Bundleable.Creator<T>, binder: IBinder?): ImmutableList<T> {
            if (binder == null) {
                return ImmutableList.of()
            }
            val builder: ImmutableList.Builder<T> = ImmutableList.Builder()
            val bundleList: List<Bundle> = BundleListRetriever.Companion.getList(binder)
            for (i in bundleList.indices) {
                builder.add(creator.fromBundle(bundleList.get(i)))
            }
            return builder.build()
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        private fun generateUnshuffledIndices(n: Int): IntArray {
            val indices: IntArray = IntArray(n)
            for (i in 0 until n) {
                indices.get(i) = i
            }
            return indices
        }
    }
}