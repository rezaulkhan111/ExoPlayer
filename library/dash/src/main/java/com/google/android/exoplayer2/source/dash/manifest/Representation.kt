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
package com.google.android.exoplayer2.source.dash.manifest

import android.net.Uri
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.dash.DashSegmentIndex
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.MultiSegmentBase
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase
import com.google.common.collect.ImmutableList
import java.util.Collections

/** A DASH representation.  */
abstract class Representation {

    /** A default value for [.revisionId].  */
    val REVISION_ID_DEFAULT: Long = -1

    /**
     * Identifies the revision of the media contained within the representation. If the media can
     * change over time (e.g. as a result of it being re-encoded), then this identifier can be set to
     * uniquely identify the revision of the media. The timestamp at which the media was encoded is
     * often a suitable.
     */
    val revisionId: Long = 0

    /** The format of the representation.  */
    val format: Format? = null

    /** The base URLs of the representation.  */
    val baseUrls: ImmutableList<BaseUrl>? = null

    /** The offset of the presentation timestamps in the media stream relative to media time.  */
    var presentationTimeOffsetUs: Long = 0

    /** The in-band event streams in the representation. May be empty.  */
    val inbandEventStreams: List<Descriptor>? = null

    /** Essential properties in the representation. May be empty.  */
    val essentialProperties: List<Descriptor>? = null

    /** Supplemental properties in the adaptation set. May be empty.  */
    val supplementalProperties: List<Descriptor>? = null

    private var initializationUri: RangedUri? = null

    /**
     * Constructs a new instance.
     *
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param baseUrls The list of base URLs of the representation.
     * @param segmentBase A segment base element for the representation.
     * @return The constructed instance.
     */
    open fun newInstance(revisionId: Long, format: Format?, baseUrls: List<BaseUrl?>?, segmentBase: SegmentBase?): Representation? {
        return Representation.newInstance(revisionId, format, baseUrls, segmentBase,  /* inbandEventStreams= */
                null,  /* essentialProperties= */
                ImmutableList.of<Descriptor>(),  /* supplementalProperties= */
                ImmutableList.of<Descriptor>(),  /* cacheKey= */
                null)
    }


    /**
     * Constructs a new instance.
     *
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param baseUrls The list of base URLs of the representation.
     * @param segmentBase A segment base element for the representation.
     * @param inbandEventStreams The in-band event streams in the representation. May be null.
     * @param essentialProperties Essential properties in the representation. May be empty.
     * @param supplementalProperties Supplemental properties in the representation. May be empty.
     * @param cacheKey An optional key to be returned from [.getCacheKey], or null. This
     * parameter is ignored if `segmentBase` consists of multiple segments.
     * @return The constructed instance.
     */
    open fun newInstance(revisionId: Long, format: Format?, baseUrls: List<BaseUrl?>?, segmentBase: SegmentBase?, inbandEventStreams: List<Descriptor?>?, essentialProperties: List<Descriptor?>?, supplementalProperties: List<Descriptor?>?, cacheKey: String?): Representation? {
        return if (segmentBase is SingleSegmentBase) {
            Representation.SingleSegmentRepresentation(revisionId, format, baseUrls, segmentBase as SingleSegmentBase?, inbandEventStreams, essentialProperties, supplementalProperties, cacheKey,  /* contentLength= */
                    C.LENGTH_UNSET.toLong())
        } else if (segmentBase is MultiSegmentBase) {
            Representation.MultiSegmentRepresentation(revisionId, format, baseUrls, segmentBase as MultiSegmentBase?, inbandEventStreams, essentialProperties, supplementalProperties)
        } else {
            throw IllegalArgumentException("segmentBase must be of type SingleSegmentBase or " + "MultiSegmentBase")
        }
    }

    private open fun Representation(revisionId: Long, format: Format, baseUrls: List<BaseUrl>, segmentBase: SegmentBase, inbandEventStreams: List<Descriptor>?, essentialProperties: List<Descriptor>, supplementalProperties: List<Descriptor>) {
        checkArgument(!baseUrls.isEmpty())
        this.revisionId = revisionId
        this.format = format
        this.baseUrls = ImmutableList.copyOf(baseUrls)
        this.inbandEventStreams = if (inbandEventStreams == null) emptyList() else Collections.unmodifiableList(inbandEventStreams)
        this.essentialProperties = essentialProperties
        this.supplementalProperties = supplementalProperties
        initializationUri = segmentBase.getInitialization(this)
        presentationTimeOffsetUs = segmentBase.presentationTimeOffsetUs
    }

    /**
     * Returns a [RangedUri] defining the location of the representation's initialization data,
     * or null if no initialization data exists.
     */
    open fun getInitializationUri(): RangedUri? {
        return initializationUri
    }

    /**
     * Returns a [RangedUri] defining the location of the representation's segment index, or
     * null if the representation provides an index directly.
     */
    abstract fun getIndexUri(): RangedUri?

    /** Returns an index if the representation provides one directly, or null otherwise.  */
    abstract fun getIndex(): DashSegmentIndex?

    /** Returns a cache key for the representation if set, or null.  */
    abstract fun getCacheKey(): String?


    /** A DASH representation consisting of a single segment.  */
    class SingleSegmentRepresentation : Representation {

        /** The uri of the single segment.  */
        var uri: Uri? = null

        /** The content length, or [C.LENGTH_UNSET] if unknown.  */
        var contentLength: Long = 0

        private var cacheKey: String? = null
        private var indexUri: RangedUri? = null
        private val segmentIndex: SingleSegmentIndex? = null

        /**
         * @param revisionId Identifies the revision of the content.
         * @param format The format of the representation.
         * @param uri The uri of the media.
         * @param initializationStart The offset of the first byte of initialization data.
         * @param initializationEnd The offset of the last byte of initialization data.
         * @param indexStart The offset of the first byte of index data.
         * @param indexEnd The offset of the last byte of index data.
         * @param inbandEventStreams The in-band event streams in the representation. May be null.
         * @param cacheKey An optional key to be returned from [.getCacheKey], or null.
         * @param contentLength The content length, or [C.LENGTH_UNSET] if unknown.
         */
        fun newInstance(revisionId: Long, format: Format?, uri: String?, initializationStart: Long, initializationEnd: Long, indexStart: Long, indexEnd: Long, inbandEventStreams: List<Descriptor?>?, cacheKey: String?, contentLength: Long): SingleSegmentRepresentation? {
            val rangedUri = RangedUri(null, initializationStart, initializationEnd - initializationStart + 1)
            val segmentBase = SingleSegmentBase(rangedUri, 1, 0, indexStart, indexEnd - indexStart + 1)
            val baseUrls = ImmutableList.of(BaseUrl(uri!!))
            return SingleSegmentRepresentation(revisionId, format, baseUrls, segmentBase, inbandEventStreams,  /* essentialProperties= */
                    ImmutableList.of<Descriptor>(),  /* supplementalProperties= */
                    ImmutableList.of<Descriptor>(), cacheKey, contentLength)
        }

        /**
         * @param revisionId Identifies the revision of the content.
         * @param format The format of the representation.
         * @param baseUrls The base urls of the representation.
         * @param segmentBase The segment base underlying the representation.
         * @param inbandEventStreams The in-band event streams in the representation. May be null.
         * @param essentialProperties Essential properties in the representation. May be empty.
         * @param supplementalProperties Supplemental properties in the representation. May be empty.
         * @param cacheKey An optional key to be returned from {@link #getCacheKey()}, or null.
         * @param contentLength The content length, or {@link C#LENGTH_UNSET} if unknown.
         */
        constructor(revisionId: Long, format: Format, baseUrls: List<BaseUrl>, segmentBase: SingleSegmentBase, @Nullable inbandEventStreams: List<Descriptor>, essentialProperties: List<Descriptor>, supplementalProperties: List<Descriptor>, @Nullable cacheKey: String, contentLength: Long) : super(revisionId, format, baseUrls, segmentBase, inbandEventStreams, essentialProperties, supplementalProperties) {

            this.uri = Uri.parse(baseUrls.get(0).url);
            this.indexUri = segmentBase.getIndex();
            this.cacheKey = cacheKey;
            this.contentLength = contentLength;
            // If we have an index uri then the index is defined externally, and we shouldn't return one
            // directly. If we don't, then we can't do better than an index defining a single segment.
            segmentIndex = indexUri != null ? null : SingleSegmentIndex(new RangedUri(null, 0, contentLength));
        }

        override fun getIndexUri(): RangedUri? {
            return indexUri
        }

        override fun getIndex(): DashSegmentIndex? {
            return segmentIndex
        }

        override fun getCacheKey(): String? {
            return cacheKey
        }
    }

    class MultiSegmentRepresentation : Representation, DashSegmentIndex {

        @VisibleForTesting /* package */
        var segmentBase: MultiSegmentBase? = null

        /**
         * Creates the multi-segment Representation.
         *
         * @param revisionId Identifies the revision of the content.
         * @param format The format of the representation.
         * @param baseUrls The base URLs of the representation.
         * @param segmentBase The segment base underlying the representation.
         * @param inbandEventStreams The in-band event streams in the representation. May be null.
         * @param essentialProperties Essential properties in the representation. May be empty.
         * @param supplementalProperties Supplemental properties in the representation. May be empty.
         */
        constructor(revisionId: Long, format: Format?, baseUrls: List<BaseUrl?>?, segmentBase: MultiSegmentBase?, inbandEventStreams: List<Descriptor?>?, essentialProperties: List<Descriptor?>?, supplementalProperties: List<Descriptor?>?) : super(revisionId, format, baseUrls, segmentBase, inbandEventStreams, essentialProperties, supplementalProperties) {
            this.segmentBase = segmentBase
        }

        override fun getIndexUri(): RangedUri? {
            return null
        }

        override fun getIndex(): DashSegmentIndex? {
            return this
        }

        override fun getCacheKey(): String? {
            return null
        }

        // DashSegmentIndex implementation.

        // DashSegmentIndex implementation.
        override fun getSegmentUrl(segmentNum: Long): RangedUri? {
            return segmentBase!!.getSegmentUrl(this, segmentNum)
        }

        override fun getSegmentNum(timeUs: Long, periodDurationUs: Long): Long {
            return segmentBase!!.getSegmentNum(timeUs, periodDurationUs)
        }

        override fun getTimeUs(segmentNum: Long): Long {
            return segmentBase!!.getSegmentTimeUs(segmentNum)
        }

        override fun getDurationUs(segmentNum: Long, periodDurationUs: Long): Long {
            return segmentBase!!.getSegmentDurationUs(segmentNum, periodDurationUs)
        }

        override fun getFirstSegmentNum(): Long {
            return segmentBase!!.firstSegmentNum
        }

        override fun getFirstAvailableSegmentNum(periodDurationUs: Long, nowUnixTimeUs: Long): Long {
            return segmentBase!!.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs)
        }

        override fun getSegmentCount(periodDurationUs: Long): Long {
            return segmentBase!!.getSegmentCount(periodDurationUs)
        }

        override fun getAvailableSegmentCount(periodDurationUs: Long, nowUnixTimeUs: Long): Long {
            return segmentBase!!.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs)
        }

        override fun getNextSegmentAvailableTimeUs(periodDurationUs: Long, nowUnixTimeUs: Long): Long {
            return segmentBase!!.getNextSegmentAvailableTimeUs(periodDurationUs, nowUnixTimeUs)
        }

        override fun isExplicit(): Boolean {
            return segmentBase!!.isExplicit
        }
    }
}