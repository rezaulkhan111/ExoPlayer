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

import androidx.annotation.IntDef
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.TrackGroup
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A track selection consisting of a static subset of selected tracks belonging to a [ ].
 *
 *
 * Tracks belonging to the subset are exposed in decreasing bandwidth order.
 */
interface TrackSelection {
    /**
     * Represents a type track selection. Either [.TYPE_UNSET] or an app-defined value (see
     * [.TYPE_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [TYPE_UNSET])
    annotation class Type

    companion object {
        /** An unspecified track selection type.  */
        const val TYPE_UNSET = 0

        /** The first value that can be used for application specific track selection types.  */
        const val TYPE_CUSTOM_BASE = 10000
    }

    /**
     * Returns an integer specifying the type of the selection, or [.TYPE_UNSET] if not
     * specified.
     *
     *
     * Track selection types are specific to individual applications, but should be defined
     * starting from [.TYPE_CUSTOM_BASE] to ensure they don't conflict with any types that may
     * be added to the library in the future.
     */
    @Type
    fun getType(): Int

    /** Returns the [TrackGroup] to which the selected tracks belong.  */
    fun getTrackGroup(): TrackGroup?
    // Static subset of selected tracks.
    /** Returns the number of tracks in the selection.  */
    fun length(): Int

    /**
     * Returns the format of the track at a given index in the selection.
     *
     * @param index The index in the selection.
     * @return The format of the selected track.
     */
    fun getFormat(index: Int): Format?

    /**
     * Returns the index in the track group of the track at a given index in the selection.
     *
     * @param index The index in the selection.
     * @return The index of the selected track.
     */
    fun getIndexInTrackGroup(index: Int): Int

    /**
     * Returns the index in the selection of the track with the specified format. The format is
     * located by identity so, for example, `selection.indexOf(selection.getFormat(index)) ==
     * index` even if multiple selected tracks have formats that contain the same values.
     *
     * @param format The format.
     * @return The index in the selection, or [C.INDEX_UNSET] if the track with the specified
     * format is not part of the selection.
     */
    fun indexOf(format: Format?): Int

    /**
     * Returns the index in the selection of the track with the specified index in the track group.
     *
     * @param indexInTrackGroup The index in the track group.
     * @return The index in the selection, or [C.INDEX_UNSET] if the track with the specified
     * index is not part of the selection.
     */
    fun indexOf(indexInTrackGroup: Int): Int
}