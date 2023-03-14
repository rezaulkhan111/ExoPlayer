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

import android.os.Bundle
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.BundleableUtil
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.primitives.Booleans

java.lang.annotation .Documentedimport java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicyimport java.util.*
/** Information about groups of tracks.  */
class Tracks constructor(groups: List<Group>?) : Bundleable {
    /**
     * Information about a single group of tracks, including the underlying [TrackGroup], the
     * level to which each track is supported by the player, and whether any of the tracks are
     * selected.
     */
    class Group constructor(
            mediaTrackGroup: TrackGroup,
            adaptiveSupported: Boolean,
            trackSupport: IntArray,
            trackSelected: BooleanArray) : Bundleable {
        /** The number of tracks in the group.  */
        val length: Int

        /**
         * Returns the underlying [TrackGroup] defined by the media.
         *
         *
         * Unlike this class, [TrackGroup] only contains information defined by the media
         * itself, and does not contain runtime information such as which tracks are supported and
         * currently selected. This makes it suitable for use as a `key` in certain `(key,
         * value)` data structures.
         */
        val mediaTrackGroup: TrackGroup
        /** Returns whether adaptive selections containing more than one track are supported.  */
        val isAdaptiveSupported: Boolean
        private val trackSupport: IntArray
        private val trackSelected: BooleanArray

        /**
         * Returns the [Format] for a specified track.
         *
         * @param trackIndex The index of the track in the group.
         * @return The [Format] of the track.
         */
        fun getTrackFormat(trackIndex: Int): Format? {
            return mediaTrackGroup.getFormat(trackIndex)
        }

        /**
         * Returns the level of support for a specified track.
         *
         * @param trackIndex The index of the track in the group.
         * @return The [C.FormatSupport] of the track.
         */
        fun getTrackSupport(trackIndex: Int): @C.FormatSupport Int {
            return trackSupport.get(trackIndex)
        }

        /**
         * Returns whether a specified track is supported for playback, without exceeding the advertised
         * capabilities of the device. Equivalent to `isTrackSupported(trackIndex, false)`.
         *
         * @param trackIndex The index of the track in the group.
         * @return True if the track's format can be played, false otherwise.
         */
        fun isTrackSupported(trackIndex: Int): Boolean {
            return isTrackSupported(trackIndex,  /* allowExceedsCapabilities= */false)
        }

        /**
         * Returns whether a specified track is supported for playback.
         *
         * @param trackIndex The index of the track in the group.
         * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
         * supported [MIME type][Format.sampleMimeType], but otherwise exceeds the advertised
         * capabilities of the device. For example, a video track for which there's a corresponding
         * decoder whose maximum advertised resolution is exceeded by the resolution of the track.
         * Such tracks may be playable in some cases.
         * @return True if the track's format can be played, false otherwise.
         */
        fun isTrackSupported(trackIndex: Int, allowExceedsCapabilities: Boolean): Boolean {
            return (trackSupport.get(trackIndex) == C.FORMAT_HANDLED
                    || (allowExceedsCapabilities
                    && trackSupport.get(trackIndex) == C.FORMAT_EXCEEDS_CAPABILITIES))
        }

        /** Returns whether at least one track in the group is selected for playback.  */
        val isSelected: Boolean
            get() {
                return Booleans.contains(trackSelected, true)
            }/* allowExceedsCapabilities= */

        /**
         * Returns whether at least one track in the group is supported for playback, without exceeding
         * the advertised capabilities of the device. Equivalent to `isSupported(false)`.
         */
        val isSupported: Boolean
            get() {
                return isSupported( /* allowExceedsCapabilities= */false)
            }

        /**
         * Returns whether at least one track in the group is supported for playback.
         *
         * @param allowExceedsCapabilities Whether to consider a track as supported if it has a
         * supported [MIME type][Format.sampleMimeType], but otherwise exceeds the advertised
         * capabilities of the device. For example, a video track for which there's a corresponding
         * decoder whose maximum advertised resolution is exceeded by the resolution of the track.
         * Such tracks may be playable in some cases.
         */
        fun isSupported(allowExceedsCapabilities: Boolean): Boolean {
            for (i in trackSupport.indices) {
                if (isTrackSupported(i, allowExceedsCapabilities)) {
                    return true
                }
            }
            return false
        }

        /**
         * Returns whether a specified track is selected for playback.
         *
         *
         * Note that multiple tracks in the group may be selected. This is common in adaptive
         * streaming, where tracks of different qualities are selected and the player switches between
         * them during playback (e.g., based on the available network bandwidth).
         *
         *
         * This class doesn't provide a way to determine which of the selected tracks is currently
         * playing, however some player implementations have ways of getting such information. For
         * example, ExoPlayer provides this information via `ExoTrackSelection.getSelectedFormat`.
         *
         * @param trackIndex The index of the track in the group.
         * @return True if the track is selected, false otherwise.
         */
        fun isTrackSelected(trackIndex: Int): Boolean {
            return trackSelected.get(trackIndex)
        }

        /** Returns the [C.TrackType] of the group.  */
        val type: @TrackType Int
            get() {
                return mediaTrackGroup.type
            }

        public override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }
            val that: Group = other as Group
            return ((isAdaptiveSupported == that.isAdaptiveSupported
                    ) && (mediaTrackGroup == that.mediaTrackGroup) && Arrays.equals(trackSupport, that.trackSupport)
                    && Arrays.equals(trackSelected, that.trackSelected))
        }

        public override fun hashCode(): Int {
            var result: Int = mediaTrackGroup.hashCode()
            result = 31 * result + (if (isAdaptiveSupported) 1 else 0)
            result = 31 * result + Arrays.hashCode(trackSupport)
            result = 31 * result + Arrays.hashCode(trackSelected)
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_TRACK_GROUP, FIELD_TRACK_SUPPORT, FIELD_TRACK_SELECTED, FIELD_ADAPTIVE_SUPPORTED])
        private annotation class FieldNumber constructor()

        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putBundle(keyForField(FIELD_TRACK_GROUP), mediaTrackGroup.toBundle())
            bundle.putIntArray(keyForField(FIELD_TRACK_SUPPORT), trackSupport)
            bundle.putBooleanArray(keyForField(FIELD_TRACK_SELECTED), trackSelected)
            bundle.putBoolean(keyForField(FIELD_ADAPTIVE_SUPPORTED), isAdaptiveSupported)
            return bundle
        }

        /**
         * Constructs an instance.
         *
         * @param mediaTrackGroup The underlying [TrackGroup] defined by the media.
         * @param adaptiveSupported Whether the player supports adaptive selections containing more than
         * one track in the group.
         * @param trackSupport The [C.FormatSupport] of each track in the group.
         * @param trackSelected Whether each track in the `trackGroup` is selected.
         */
        init {
            length = mediaTrackGroup.length
            Assertions.checkArgument(length == trackSupport.size && length == trackSelected.size)
            this.mediaTrackGroup = mediaTrackGroup
            isAdaptiveSupported = adaptiveSupported && length > 1
            this.trackSupport = trackSupport.clone()
            this.trackSelected = trackSelected.clone()
        }

        companion object {
            private val FIELD_TRACK_GROUP: Int = 0
            private val FIELD_TRACK_SUPPORT: Int = 1
            private val FIELD_TRACK_SELECTED: Int = 3
            private val FIELD_ADAPTIVE_SUPPORTED: Int = 4

            /** Object that can restore a group of tracks from a [Bundle].  */
            val CREATOR: Bundleable.Creator<Group> = Bundleable.Creator({ bundle: Bundle ->
                // Can't create a Tracks.Group without a TrackGroup
                val trackGroup: TrackGroup = TrackGroup.Companion.CREATOR.fromBundle(
                        (Assertions.checkNotNull(bundle.getBundle(keyForField(FIELD_TRACK_GROUP))))!!)
                val trackSupport: IntArray = MoreObjects.firstNonNull(
                        bundle.getIntArray(keyForField(FIELD_TRACK_SUPPORT)), IntArray(trackGroup.length))
                val selected: BooleanArray = MoreObjects.firstNonNull(
                        bundle.getBooleanArray(keyForField(FIELD_TRACK_SELECTED)), BooleanArray(trackGroup.length))
                val adaptiveSupported: Boolean = bundle.getBoolean(keyForField(FIELD_ADAPTIVE_SUPPORTED), false)
                Group(trackGroup, adaptiveSupported, trackSupport, selected)
            })

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /** Returns the [groups][Group] of tracks.  */
    val groups: ImmutableList<Group>

    /** Returns `true` if there are no tracks, and `false` otherwise.  */
    val isEmpty: Boolean
        get() {
            return groups.isEmpty()
        }

    /** Returns true if there are tracks of type `trackType`, and false otherwise.  */
    fun containsType(trackType: @TrackType Int): Boolean {
        for (i in groups.indices) {
            if (groups.get(i).type == trackType) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if at least one track of type `trackType` is [ ][Group.isTrackSupported].
     */
    fun isTypeSupported(trackType: @TrackType Int): Boolean {
        return isTypeSupported(trackType,  /* allowExceedsCapabilities= */false)
    }

    /**
     * Returns true if at least one track of type `trackType` is [ ][Group.isTrackSupported].
     *
     * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
     * supported [MIME type][Format.sampleMimeType], but otherwise exceeds the advertised
     * capabilities of the device. For example, a video track for which there's a corresponding
     * decoder whose maximum advertised resolution is exceeded by the resolution of the track.
     * Such tracks may be playable in some cases.
     */
    fun isTypeSupported(trackType: @TrackType Int, allowExceedsCapabilities: Boolean): Boolean {
        for (i in groups.indices) {
            if (groups.get(i).type == trackType) {
                if (groups.get(i).isSupported(allowExceedsCapabilities)) {
                    return true
                }
            }
        }
        return false
    }

    @Deprecated("Use {@link #containsType(int)} and {@link #isTypeSupported(int)}.")
    fun isTypeSupportedOrEmpty(trackType: @TrackType Int): Boolean {
        return isTypeSupportedOrEmpty(trackType,  /* allowExceedsCapabilities= */false)
    }

    @Deprecated("Use {@link #containsType(int)} and {@link #isTypeSupported(int, boolean)}.")
    fun isTypeSupportedOrEmpty(
            trackType: @TrackType Int, allowExceedsCapabilities: Boolean): Boolean {
        return !containsType(trackType) || isTypeSupported(trackType, allowExceedsCapabilities)
    }

    /** Returns true if at least one track of the type `trackType` is selected for playback.  */
    fun isTypeSelected(trackType: @TrackType Int): Boolean {
        for (i in groups.indices) {
            val group: Group = groups.get(i)
            if (group.isSelected && group.type == trackType) {
                return true
            }
        }
        return false
    }

    public override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that: Tracks = other as Tracks
        return (groups == that.groups)
    }

    public override fun hashCode(): Int {
        return groups.hashCode()
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_TRACK_GROUPS])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putParcelableArrayList(keyForField(FIELD_TRACK_GROUPS), BundleableUtil.toBundleArrayList(groups))
        return bundle
    }

    /**
     * Constructs an instance.
     *
     * @param groups The [groups][Group] of tracks.
     */
    init {
        this.groups = ImmutableList.copyOf(groups)
    }

    companion object {
        /** Empty tracks.  */
        val EMPTY: Tracks = Tracks(ImmutableList.of())
        private val FIELD_TRACK_GROUPS: Int = 0

        /** Object that can restore tracks from a [Bundle].  */
        val CREATOR: Bundleable.Creator<Tracks> = Bundleable.Creator({ bundle: Bundle ->
            val groupBundles: List<Bundle>? = bundle.getParcelableArrayList(keyForField(FIELD_TRACK_GROUPS))
            val groups: List<Group> = if (groupBundles == null) ImmutableList.of() else BundleableUtil.fromBundleList(Group.CREATOR, groupBundles)
            Tracks(groups)
        })

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}