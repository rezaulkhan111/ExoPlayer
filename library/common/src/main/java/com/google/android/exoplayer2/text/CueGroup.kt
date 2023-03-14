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

import android.graphics.Bitmapimport

android.os.Bundleimport androidx.annotation .IntDefimport com.google.android.exoplayer2.Bundleableimport com.google.android.exoplayer2.Timelineimport com.google.android.exoplayer2.util.BundleableUtilimport com.google.common.collect.ImmutableList java.lang.annotation .Documentedimport java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicy
/** Class to represent the state of active [Cues][Cue] at a particular time.  */
class CueGroup(cues: List<Cue>?, presentationTimeUs: Long) : Bundleable {
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
    val cues: ImmutableList<Cue>

    /**
     * The presentation time of the [.cues], in microseconds.
     *
     *
     * This time is an offset from the start of the current [Timeline.Period].
     */
    val presentationTimeUs: Long

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_CUES, FIELD_PRESENTATION_TIME_US])
    private annotation class FieldNumber

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putParcelableArrayList(
                keyForField(FIELD_CUES), BundleableUtil.toBundleArrayList(filterOutBitmapCues(cues)))
        bundle.putLong(keyForField(FIELD_PRESENTATION_TIME_US), presentationTimeUs)
        return bundle
    }

    /** Creates a CueGroup.  */
    init {
        this.cues = ImmutableList.copyOf(cues)
        this.presentationTimeUs = presentationTimeUs
    }

    companion object {
        /** An empty group with no [Cues][Cue] and presentation time of zero.  */
        val EMPTY_TIME_ZERO = CueGroup(ImmutableList.of(),  /* presentationTimeUs= */0)
        private const val FIELD_CUES = 0
        private const val FIELD_PRESENTATION_TIME_US = 1
        val CREATOR = Bundleable.Creator { bundle: Bundle -> fromBundle(bundle) }
        private fun fromBundle(bundle: Bundle): CueGroup {
            val cueBundles = bundle.getParcelableArrayList<Bundle>(keyForField(FIELD_CUES))
            val cues: List<Cue> = if (cueBundles == null) ImmutableList.of() else BundleableUtil.fromBundleList(Cue.Companion.CREATOR, cueBundles)
            val presentationTimeUs = bundle.getLong(keyForField(FIELD_PRESENTATION_TIME_US))
            return CueGroup(cues, presentationTimeUs)
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        /**
         * Filters out [Cue] objects containing [Bitmap]. It is used when transferring cues
         * between processes to prevent transferring too much data.
         */
        private fun filterOutBitmapCues(cues: List<Cue>): ImmutableList<Cue> {
            val builder = ImmutableList.builder<Cue>()
            for (i in cues.indices) {
                if (cues[i].bitmap != null) {
                    continue
                }
                builder.add(cues[i])
            }
            return builder.build()
        }
    }
}