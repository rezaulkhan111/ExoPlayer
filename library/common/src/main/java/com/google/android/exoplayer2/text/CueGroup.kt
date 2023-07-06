/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.text

import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.IntDef
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.util.BundleableUtil.fromBundleList
import com.google.android.exoplayer2.util.BundleableUtil.toBundleArrayList
import com.google.common.collect.ImmutableList
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Class to represent the state of active [Cues][Cue] at a particular time.  */
class CueGroup : Bundleable {

    /**
     * The cues in this group.
     *
     *
     * This list is in ascending order of priority. If any of the cue boxes overlap when displayed,
     * the [Cue] nearer the end of the list should be shown on top.
     *
     *
     * This list may be empty if the group represents a state with no cues.
     */
    var cues: ImmutableList<Cue>? = null

    /**
     * The presentation time of the [.cues], in microseconds.
     *
     *
     * This time is an offset from the start of the current [Timeline.Period].
     */
    var presentationTimeUs: Long = 0

    /** Creates a CueGroup.  */
    constructor(cues: List<Cue>?, presentationTimeUs: Long) {
        this.cues = ImmutableList.copyOf(cues)
        this.presentationTimeUs = presentationTimeUs
    }

    // Bundleable implementation.

    companion object {
        /** An empty group with no [Cues][Cue] and presentation time of zero.  */
        val EMPTY_TIME_ZERO: CueGroup = CueGroup(ImmutableList.of(),  /* presentationTimeUs= */0)

        private const val FIELD_CUES = 0
        private const val FIELD_PRESENTATION_TIME_US = 1

        val CREATOR: Bundleable.Creator<CueGroup> = Bundleable.Creator<CueGroup> { obj: CueGroup, bundle: Bundle -> obj.fromBundle(bundle) }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FIELD_CUES, FIELD_PRESENTATION_TIME_US])
    private annotation class FieldNumber {}

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putParcelableArrayList(keyForField(FIELD_CUES), toBundleArrayList(filterOutBitmapCues(cues)))
        bundle.putLong(keyForField(FIELD_PRESENTATION_TIME_US), presentationTimeUs)
        return bundle
    }

    private fun fromBundle(bundle: Bundle): CueGroup {
        val cueBundles = bundle.getParcelableArrayList<Bundle?>(keyForField(FIELD_CUES))
        val cues: List<Cue> = if (cueBundles == null) ImmutableList.of() else fromBundleList(Cue.CREATOR, cueBundles)!!
        val presentationTimeUs = bundle.getLong(keyForField(FIELD_PRESENTATION_TIME_US))
        return CueGroup(cues, presentationTimeUs)
    }

    private fun keyForField(@FieldNumber field: Int): String? {
        return field.toString(Character.MAX_RADIX)
    }

    /**
     * Filters out [Cue] objects containing [Bitmap]. It is used when transferring cues
     * between processes to prevent transferring too much data.
     */
    private fun filterOutBitmapCues(cues: List<Cue>?): ImmutableList<Cue>? {
        val builder = ImmutableList.builder<Cue>()
        for (i in cues!!.indices) {
            if (cues[i].bitmap != null) {
                continue
            }
            builder.add(cues[i])
        }
        return builder.build()
    }
}