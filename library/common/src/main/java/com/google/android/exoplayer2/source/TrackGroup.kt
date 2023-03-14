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

import android.os.Bundle
import androidx.annotation.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * An immutable group of tracks available within a media stream. All tracks in a group present the
 * same content, but their formats may differ.
 *
 *
 * As an example of how tracks can be grouped, consider an adaptive playback where a main video
 * feed is provided in five resolutions, and an alternative video feed (e.g., a different camera
 * angle in a sports match) is provided in two resolutions. In this case there will be two video
 * track groups, one corresponding to the main video feed containing five tracks, and a second for
 * the alternative video feed containing two tracks.
 *
 *
 * Note that audio tracks whose languages differ are not grouped, because content in different
 * languages is not considered to be the same. Conversely, audio tracks in the same language that
 * only differ in properties such as bitrate, sampling rate, channel count and so on can be grouped.
 * This also applies to text tracks.
 *
 *
 * Note also that this class only contains information derived from the media itself. Unlike
 * [Tracks.Group], it does not include runtime information such as the extent to which
 * playback of each track is supported by the device, or which tracks are currently selected.
 */
class TrackGroup constructor(id: String, vararg formats: Format) : Bundleable {
    /** The number of tracks in the group.  */
    val length: Int

    /** An identifier for the track group.  */
    val id: String

    /** The type of tracks in the group.  */
    val type: @TrackType Int
    private val formats: Array<Format>

    // Lazily initialized hashcode.
    private var hashCode: Int = 0

    /**
     * Constructs a track group containing the provided `formats`.
     *
     * @param formats The list of [Formats][Format]. Must not be empty.
     */
    constructor(vararg formats: Format?) : this( /* id= */"", *formats) {}

    /**
     * Returns a copy of this track group with the specified `id`.
     *
     * @param id The identifier for the copy of the track group.
     * @return The copied track group.
     */
    @CheckResult
    fun copyWithId(id: String): TrackGroup {
        return TrackGroup(id, *formats)
    }

    /**
     * Returns the format of the track at a given index.
     *
     * @param index The index of the track.
     * @return The track's format.
     */
    fun getFormat(index: Int): Format {
        return formats.get(index)
    }

    /**
     * Returns the index of the track with the given format in the group. The format is located by
     * identity so, for example, `group.indexOf(group.getFormat(index)) == index` even if
     * multiple tracks have formats that contain the same values.
     *
     * @param format The format.
     * @return The index of the track, or [C.INDEX_UNSET] if no such track exists.
     */
    fun indexOf(format: Format): Int {
        for (i in formats.indices) {
            if (format === formats.get(i)) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    public override fun hashCode(): Int {
        if (hashCode == 0) {
            var result: Int = 17
            result = 31 * result + id.hashCode()
            result = 31 * result + Arrays.hashCode(formats)
            hashCode = result
        }
        return hashCode
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: TrackGroup = obj as TrackGroup
        return (id == other.id) && Arrays.equals(formats, other.formats)
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_FORMATS, FIELD_ID])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        val arrayList: ArrayList<Bundle?> = ArrayList(formats.size)
        for (format: Format in formats) {
            arrayList.add(format.toBundle( /* excludeMetadata= */true))
        }
        bundle.putParcelableArrayList(keyForField(FIELD_FORMATS), arrayList)
        bundle.putString(keyForField(FIELD_ID), id)
        return bundle
    }

    /**
     * Constructs a track group with the provided `id` and `formats`.
     *
     * @param id The identifier of the track group. May be an empty string.
     * @param formats The list of [Formats][Format]. Must not be empty.
     */
    init {
        Assertions.checkArgument(formats.size > 0)
        this.id = id
        this.formats = formats
        length = formats.size
        var type: @TrackType Int = MimeTypes.getTrackType(formats.get(0).sampleMimeType)
        if (type == C.TRACK_TYPE_UNKNOWN) {
            type = MimeTypes.getTrackType(formats.get(0).containerMimeType)
        }
        this.type = type
        verifyCorrectness()
    }

    private fun verifyCorrectness() {
        // TrackGroups should only contain tracks with exactly the same content (but in different
        // qualities). We only log an error instead of throwing to not break backwards-compatibility for
        // cases where malformed TrackGroups happen to work by chance (e.g. because adaptive selections
        // are always disabled).
        val language: String = normalizeLanguage(formats.get(0).language)
        val roleFlags: @RoleFlags Int = normalizeRoleFlags(formats.get(0).roleFlags)
        for (i in 1 until formats.size) {
            if (!(language == normalizeLanguage(formats.get(i).language))) {
                logErrorMessage( /* mismatchField= */
                        "languages",  /* valueIndex0= */
                        formats.get(0).language,  /* otherValue=* */
                        formats.get(i).language,  /* otherIndex= */
                        i)
                return
            }
            if (roleFlags != normalizeRoleFlags(formats.get(i).roleFlags)) {
                logErrorMessage( /* mismatchField= */
                        "role flags",  /* valueIndex0= */
                        Integer.toBinaryString(formats.get(0).roleFlags),  /* otherValue=* */
                        Integer.toBinaryString(formats.get(i).roleFlags),  /* otherIndex= */
                        i)
                return
            }
        }
    }

    companion object {
        private val TAG: String = "TrackGroup"
        private val FIELD_FORMATS: Int = 0
        private val FIELD_ID: Int = 1

        /** Object that can restore `TrackGroup` from a [Bundle].  */
        val CREATOR: Bundleable.Creator<TrackGroup> = Bundleable.Creator({ bundle: Bundle ->
            val formatBundles: List<Bundle>? = bundle.getParcelableArrayList(keyForField(FIELD_FORMATS))
            val formats: List<Format> = if (formatBundles == null) ImmutableList.of() else BundleableUtil.fromBundleList(Format.Companion.CREATOR, formatBundles)
            val id: String = bundle.getString(keyForField(FIELD_ID),  /* defaultValue= */"")
            TrackGroup(id, *formats.toTypedArray())
        })

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        private fun normalizeLanguage(language: String?): String {
            // Treat all variants of undetermined or unknown languages as compatible.
            return if (language == null || (language == C.LANGUAGE_UNDETERMINED)) "" else language
        }

        private fun normalizeRoleFlags(roleFlags: @RoleFlags Int): @RoleFlags Int {
            // Treat trick-play and non-trick-play formats as compatible.
            return roleFlags or C.ROLE_FLAG_TRICK_PLAY
        }

        private fun logErrorMessage(
                mismatchField: String,
                valueIndex0: String?,
                otherValue: String?,
                otherIndex: Int) {
            Log.e(
                    TAG,
                    "",
                    IllegalStateException(
                            ("Different "
                                    + mismatchField
                                    + " combined in one TrackGroup: '"
                                    + valueIndex0
                                    + "' (track 0) and '"
                                    + otherValue
                                    + "' (track "
                                    + otherIndex
                                    + ")")))
        }
    }
}