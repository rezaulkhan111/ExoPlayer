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
package com.google.android.exoplayer2.metadata

import android.os.Parcel
import android.os.Parcelable
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.util.Util
import com.google.common.primitives.Longs
import java.util.*

/** A collection of metadata entries.  */
class Metadata : Parcelable {
    /** A metadata entry.  */
    open interface Entry : Parcelable {
        /**
         * Returns the [Format] that can be used to decode the wrapped metadata in [ ][.getWrappedMetadataBytes], or null if this Entry doesn't contain wrapped metadata.
         */
        val wrappedMetadataFormat: Format?
            get() {
                return null
            }

        /**
         * Returns the bytes of the wrapped metadata in this Entry, or null if it doesn't contain
         * wrapped metadata.
         */
        val wrappedMetadataBytes: ByteArray?
            get() {
                return null
            }

        /**
         * Updates the [MediaMetadata.Builder] with the type specific values stored in this Entry.
         *
         *
         * The order of the [Entry] objects in the [Metadata] matters. If two [ ] entries attempt to populate the same [MediaMetadata] field, then the last one in
         * the list is used.
         *
         * @param builder The builder to be updated.
         */
        fun populateMediaMetadata(builder: MediaMetadata.Builder?) {}
    }

    private val entries: Array<Entry?>

    /**
     * The presentation time of the metadata, in microseconds.
     *
     *
     * This time is an offset from the start of the current [Timeline.Period].
     *
     *
     * This time is [C.TIME_UNSET] when not known or undefined.
     */
    val presentationTimeUs: Long

    /**
     * @param entries The metadata entries.
     */
    constructor(vararg entries: Entry?) : this( /* presentationTimeUs= */C.TIME_UNSET, *entries) {}

    /**
     * @param presentationTimeUs The presentation time for the metadata entries.
     * @param entries The metadata entries.
     */
    constructor(presentationTimeUs: Long, vararg entries: Entry?) {
        this.presentationTimeUs = presentationTimeUs
        this.entries = entries
    }

    /**
     * @param entries The metadata entries.
     */
    constructor(entries: List<Entry>) : this(*entries.toTypedArray<Entry>()) {}

    /**
     * @param presentationTimeUs The presentation time for the metadata entries.
     * @param entries The metadata entries.
     */
    constructor(presentationTimeUs: Long, entries: List<Entry>) : this(presentationTimeUs, *entries.toTypedArray<Entry>()) {}

    /* package */
    internal constructor(`in`: Parcel) {
        entries = arrayOfNulls(`in`.readInt())
        for (i in entries.indices) {
            entries.get(i) = `in`.readParcelable(Entry::class.java.getClassLoader())
        }
        presentationTimeUs = `in`.readLong()
    }

    /** Returns the number of metadata entries.  */
    fun length(): Int {
        return entries.size
    }

    /**
     * Returns the entry at the specified index.
     *
     * @param index The index of the entry.
     * @return The entry at the specified index.
     */
    operator fun get(index: Int): Entry? {
        return entries.get(index)
    }

    /**
     * Returns a copy of this metadata with the entries of the specified metadata appended. Returns
     * this instance if `other` is null.
     *
     * @param other The metadata that holds the entries to append. If null, this methods returns this
     * instance.
     * @return The metadata instance with the appended entries.
     */
    fun copyWithAppendedEntriesFrom(other: Metadata?): Metadata {
        if (other == null) {
            return this
        }
        return copyWithAppendedEntries(*other.entries)
    }

    /**
     * Returns a copy of this metadata with the specified entries appended.
     *
     * @param entriesToAppend The entries to append.
     * @return The metadata instance with the appended entries.
     */
    fun copyWithAppendedEntries(vararg entriesToAppend: Entry?): Metadata {
        if (entriesToAppend.size == 0) {
            return this
        }
        return Metadata(
                presentationTimeUs, *Util.nullSafeArrayConcatenation(entries, entriesToAppend))
    }

    /**
     * Returns a copy of this metadata with the specified presentation time.
     *
     * @param presentationTimeUs The new presentation time, in microseconds.
     * @return The metadata instance with the new presentation time.
     */
    fun copyWithPresentationTimeUs(presentationTimeUs: Long): Metadata {
        if (this.presentationTimeUs == presentationTimeUs) {
            return this
        }
        return Metadata(presentationTimeUs, *entries)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: Metadata = obj as Metadata
        return Arrays.equals(entries, other.entries) && presentationTimeUs == other.presentationTimeUs
    }

    public override fun hashCode(): Int {
        var result: Int = Arrays.hashCode(entries)
        result = 31 * result + Longs.hashCode(presentationTimeUs)
        return result
    }

    public override fun toString(): String {
        return ("entries="
                + Arrays.toString(entries)
                + (if (presentationTimeUs == C.TIME_UNSET) "" else ", presentationTimeUs=" + presentationTimeUs))
    }

    // Parcelable implementation.
    public override fun describeContents(): Int {
        return 0
    }

    public override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(entries.size)
        for (entry: Entry? in entries) {
            dest.writeParcelable(entry, 0)
        }
        dest.writeLong(presentationTimeUs)
    }

    companion object {
        val CREATOR: Parcelable.Creator<Metadata> = object : Parcelable.Creator<Metadata?> {
            public override fun createFromParcel(`in`: Parcel): Metadata? {
                return Metadata(`in`)
            }

            public override fun newArray(size: Int): Array<Metadata?> {
                return arrayOfNulls(size)
            }
        }
    }
}