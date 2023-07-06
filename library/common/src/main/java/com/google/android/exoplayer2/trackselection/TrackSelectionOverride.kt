/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle
import androidx.annotation.IntDef
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.source.TrackGroup
import com.google.common.collect.ImmutableList
import com.google.common.primitives.Ints
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * A track selection override, consisting of a [TrackGroup] and the indices of the tracks
 * within the group that should be selected.
 *
 *
 * A track selection override is applied during playback if the media being played contains a
 * [TrackGroup] equal to the one in the override. If a [TrackSelectionParameters]
 * contains only one override of a given track type that applies to the media, this override will be
 * used to control the track selection for that type. If multiple overrides of a given track type
 * apply then the player will apply only one of them.
 *
 *
 * If [.trackIndices] is empty then the override specifies that no tracks should be
 * selected. Adding an empty override to a [TrackSelectionParameters] is similar to [ ][TrackSelectionParameters.Builder.setTrackTypeDisabled], except that an
 * empty override will only be applied if the media being played contains a [TrackGroup] equal
 * to the one in the override. Conversely, disabling a track type will prevent selection of tracks
 * of that type for all media.
 */
class TrackSelectionOverride : Bundleable {
    /** The media [TrackGroup] whose [.trackIndices] are forced to be selected.  */
    var mediaTrackGroup: TrackGroup? = null

    /** The indices of tracks in a [TrackGroup] to be selected.  */
    var trackIndices: ImmutableList<Int>? = null

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = [FIELD_TRACK_GROUP, FIELD_TRACKS])
    private annotation class FieldNumber {}

    /**
     * Constructs an instance to force `trackIndex` in `trackGroup` to be selected.
     *
     * @param mediaTrackGroup The media [TrackGroup] for which to override the track selection.
     * @param trackIndex The index of the track in the [TrackGroup] to select.
     */
    constructor(mediaTrackGroup: TrackGroup?, trackIndex: Int) {
        TrackSelectionOverride(mediaTrackGroup, ImmutableList.of<Int>(trackIndex))
    }

    /**
     * Constructs an instance to force `trackIndices` in `trackGroup` to be selected.
     *
     * @param mediaTrackGroup The media [TrackGroup] for which to override the track selection.
     * @param trackIndices The indices of the tracks in the [TrackGroup] to select.
     */
    constructor(mediaTrackGroup: TrackGroup?, trackIndices: List<Int>) {
        if (!trackIndices.isEmpty()) {
            if (Collections.min(trackIndices) < 0 || Collections.max(trackIndices) >= mediaTrackGroup!!.length) {
                throw IndexOutOfBoundsException()
            }
        }
        this.mediaTrackGroup = mediaTrackGroup
        this.trackIndices = ImmutableList.copyOf(trackIndices)
    }

    /** Returns the [C.TrackType] of the overridden track group.  */
    fun getType(): @TrackType Int {
        return mediaTrackGroup!!.type
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val that = obj as TrackSelectionOverride
        return mediaTrackGroup!! == that.mediaTrackGroup && trackIndices == that.trackIndices
    }

    override fun hashCode(): Int {
        return mediaTrackGroup.hashCode() + 31 * trackIndices.hashCode()
    }

    // Bundleable implementation

    // Bundleable implementation
    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putBundle(keyForField(FIELD_TRACK_GROUP), mediaTrackGroup!!.toBundle())
        bundle.putIntArray(keyForField(FIELD_TRACKS), Ints.toArray(trackIndices))
        return bundle
    }

    companion object {
        /** Object that can restore `TrackSelectionOverride` from a [Bundle].  */
        val CREATOR: Bundleable.Creator<TrackSelectionOverride> = Bundleable.Creator<TrackSelectionOverride> { bundle: Bundle ->
            val trackGroupBundle = checkNotNull(bundle.getBundle(keyForField(FIELD_TRACK_GROUP)))
            val mediaTrackGroup = TrackGroup.CREATOR.fromBundle(trackGroupBundle)
            val tracks = checkNotNull(bundle.getIntArray(keyForField(FIELD_TRACKS)))
            TrackSelectionOverride(mediaTrackGroup, Ints.asList(*tracks))
        }

        private fun keyForField(@FieldNumber field: Int): String? {
            return field.toString(Character.MAX_RADIX)
        }

        private const val FIELD_TRACK_GROUP = 0
        private const val FIELD_TRACKS = 1
    }
}