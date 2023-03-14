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
package com.google.android.exoplayer2.drm

import android.os.Parcelimport

android.os.Parcelableimport android.text.TextUtilsimport androidx.annotation .CheckResultimport com.google.android.exoplayer2.Cimport com.google.android.exoplayer2.drm.DrmInitData.SchemeDataimport com.google.android.exoplayer2.util.Assertionsimport com.google.android.exoplayer2.util.Utilimport java.util.*
/** Initialization data for one or more DRM schemes.  */
class DrmInitData : Comparator<SchemeData>, Parcelable {
    private val schemeDatas: Array<SchemeData>?

    // Lazily initialized hashcode.
    private var hashCode = 0

    /** The protection scheme type, or null if not applicable or unknown.  */
    val schemeType: String?

    /** Number of [SchemeData]s.  */
    val schemeDataCount: Int

    /**
     * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
     */
    constructor(schemeDatas: List<SchemeData>) : this(null, false, *schemeDatas.toTypedArray<SchemeData>()) {}

    /**
     * @param schemeType See [.schemeType].
     * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
     */
    constructor(schemeType: String?, schemeDatas: List<SchemeData>) : this(schemeType, false, *schemeDatas.toTypedArray<SchemeData>()) {}

    /**
     * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
     */
    constructor(vararg schemeDatas: SchemeData?) : this(null, *schemeDatas) {}

    /**
     * @param schemeType See [.schemeType].
     * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
     */
    constructor(schemeType: String?, vararg schemeDatas: SchemeData?) : this(schemeType, true, *schemeDatas) {}
    private constructor(
            schemeType: String?, cloneSchemeDatas: Boolean, vararg schemeDatas: SchemeData) {
        var schemeDatas = schemeDatas
        this.schemeType = schemeType
        if (cloneSchemeDatas) {
            schemeDatas = schemeDatas.clone()
        }
        this.schemeDatas = schemeDatas
        schemeDataCount = schemeDatas.size
        // Sorting ensures that universal scheme data (i.e. data that applies to all schemes) is matched
        // last. It's also required by the equals and hashcode implementations.
        Arrays.sort(this.schemeDatas, this)
    }

    /* package */
    internal constructor(`in`: Parcel) {
        schemeType = `in`.readString()
        schemeDatas = Util.castNonNull(`in`.createTypedArray(SchemeData.CREATOR))
        schemeDataCount = schemeDatas!!.size
    }

    /**
     * Retrieves the [SchemeData] at a given index.
     *
     * @param index The index of the scheme to return. Must not exceed [.schemeDataCount].
     * @return The [SchemeData] at the specified index.
     */
    operator fun get(index: Int): SchemeData {
        return schemeDatas!![index]
    }

    /**
     * Returns a copy with the specified protection scheme type.
     *
     * @param schemeType A protection scheme type. May be null.
     * @return A copy with the specified protection scheme type.
     */
    @CheckResult
    fun copyWithSchemeType(schemeType: String?): DrmInitData {
        return if (Util.areEqual(this.schemeType, schemeType)) {
            this
        } else DrmInitData(schemeType, false, *schemeDatas!!)
    }

    /**
     * Returns an instance containing the [.schemeDatas] from both this and `other`. The
     * [.schemeType] of the instances being merged must either match, or at least one scheme
     * type must be `null`.
     *
     * @param drmInitData The instance to merge.
     * @return The merged result.
     */
    fun merge(drmInitData: DrmInitData): DrmInitData {
        Assertions.checkState(
                schemeType == null || drmInitData.schemeType == null || TextUtils.equals(schemeType, drmInitData.schemeType))
        val mergedSchemeType = if (schemeType != null) schemeType else drmInitData.schemeType
        val mergedSchemeDatas: Array<SchemeData?> = Util.nullSafeArrayConcatenation<SchemeData>(schemeDatas, drmInitData.schemeDatas)
        return DrmInitData(mergedSchemeType, *mergedSchemeDatas)
    }

    override fun hashCode(): Int {
        if (hashCode == 0) {
            var result = schemeType?.hashCode() ?: 0
            result = 31 * result + Arrays.hashCode(schemeDatas)
            hashCode = result
        }
        return hashCode
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as DrmInitData
        return (Util.areEqual(schemeType, other.schemeType)
                && Arrays.equals(schemeDatas, other.schemeDatas))
    }

    override fun compare(first: SchemeData, second: SchemeData): Int {
        return if (C.UUID_NIL == first.uuid) (if (C.UUID_NIL == second.uuid) 0 else 1) else first.uuid!!.compareTo(second.uuid)
    }

    // Parcelable implementation.
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(schemeType)
        dest.writeTypedArray(schemeDatas, 0)
    }

    /** Scheme initialization data.  */
    class SchemeData : Parcelable {
        // Lazily initialized hashcode.
        private var hashCode = 0

        /**
         * The [UUID] of the DRM scheme, or [C.UUID_NIL] if the data is universal (i.e.
         * applies to all schemes).
         */
        val uuid: UUID?

        /** The URL of the server to which license requests should be made. May be null if unknown.  */
        val licenseServerUrl: String?

        /** The mimeType of [.data].  */
        val mimeType: String?

        /** The initialization data. May be null for scheme support checks only.  */
        val data: ByteArray?

        /**
         * @param uuid The [UUID] of the DRM scheme, or [C.UUID_NIL] if the data is
         * universal (i.e. applies to all schemes).
         * @param mimeType See [.mimeType].
         * @param data See [.data].
         */
        constructor(uuid: UUID?, mimeType: String?, data: ByteArray?) : this(uuid,  /* licenseServerUrl= */null, mimeType, data) {}

        /**
         * @param uuid The [UUID] of the DRM scheme, or [C.UUID_NIL] if the data is
         * universal (i.e. applies to all schemes).
         * @param licenseServerUrl See [.licenseServerUrl].
         * @param mimeType See [.mimeType].
         * @param data See [.data].
         */
        constructor(
                uuid: UUID?, licenseServerUrl: String?, mimeType: String?, data: ByteArray?) {
            this.uuid = Assertions.checkNotNull(uuid)
            this.licenseServerUrl = licenseServerUrl
            this.mimeType = Assertions.checkNotNull(mimeType)
            this.data = data
        }

        /* package */
        internal constructor(`in`: Parcel) {
            uuid = UUID(`in`.readLong(), `in`.readLong())
            licenseServerUrl = `in`.readString()
            mimeType = Util.castNonNull(`in`.readString())
            data = `in`.createByteArray()
        }

        /**
         * Returns whether this initialization data applies to the specified scheme.
         *
         * @param schemeUuid The scheme [UUID].
         * @return Whether this initialization data applies to the specified scheme.
         */
        fun matches(schemeUuid: UUID?): Boolean {
            return C.UUID_NIL == uuid || schemeUuid == uuid
        }

        /**
         * Returns whether this [SchemeData] can be used to replace `other`.
         *
         * @param other A [SchemeData].
         * @return Whether this [SchemeData] can be used to replace `other`.
         */
        fun canReplace(other: SchemeData): Boolean {
            return hasData() && !other.hasData() && matches(other.uuid)
        }

        /** Returns whether [.data] is non-null.  */
        fun hasData(): Boolean {
            return data != null
        }

        /**
         * Returns a copy of this instance with the specified data.
         *
         * @param data The data to include in the copy.
         * @return The new instance.
         */
        @CheckResult
        fun copyWithData(data: ByteArray?): SchemeData {
            return SchemeData(uuid, licenseServerUrl, mimeType, data)
        }

        override fun equals(obj: Any?): Boolean {
            if (obj !is SchemeData) {
                return false
            }
            if (obj === this) {
                return true
            }
            val other = obj
            return (Util.areEqual(licenseServerUrl, other.licenseServerUrl)
                    && Util.areEqual(mimeType, other.mimeType)
                    && Util.areEqual(uuid, other.uuid)
                    && Arrays.equals(data, other.data))
        }

        override fun hashCode(): Int {
            if (hashCode == 0) {
                var result = uuid.hashCode()
                result = 31 * result + (licenseServerUrl?.hashCode() ?: 0)
                result = 31 * result + mimeType.hashCode()
                result = 31 * result + Arrays.hashCode(data)
                hashCode = result
            }
            return hashCode
        }

        // Parcelable implementation.
        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(uuid!!.mostSignificantBits)
            dest.writeLong(uuid.leastSignificantBits)
            dest.writeString(licenseServerUrl)
            dest.writeString(mimeType)
            dest.writeByteArray(data)
        }

        companion object {
            val CREATOR: Parcelable.Creator<SchemeData> = object : Parcelable.Creator<SchemeData?> {
                override fun createFromParcel(`in`: Parcel): SchemeData? {
                    return SchemeData(`in`)
                }

                override fun newArray(size: Int): Array<SchemeData?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        /**
         * Merges [DrmInitData] obtained from a media manifest and a media stream.
         *
         *
         * The result is generated as follows.
         *
         *
         *  1. Include all [SchemeData]s from `manifestData` where [       ][SchemeData.hasData] is true.
         *  1. Include all [SchemeData]s in `mediaData` where [SchemeData.hasData]
         * is true and for which we did not include an entry from the manifest targeting the same
         * UUID.
         *  1. If available, the scheme type from the manifest is used. If not, the scheme type from the
         * media is used.
         *
         *
         * @param manifestData DRM session acquisition data obtained from the manifest.
         * @param mediaData DRM session acquisition data obtained from the media.
         * @return A [DrmInitData] obtained from merging a media manifest and a media stream.
         */
        fun createSessionCreationData(
                manifestData: DrmInitData?, mediaData: DrmInitData?): DrmInitData? {
            val result = ArrayList<SchemeData>()
            var schemeType: String? = null
            if (manifestData != null) {
                schemeType = manifestData.schemeType
                for (data in manifestData.schemeDatas!!) {
                    if (data.hasData()) {
                        result.add(data)
                    }
                }
            }
            if (mediaData != null) {
                if (schemeType == null) {
                    schemeType = mediaData.schemeType
                }
                val manifestDatasCount = result.size
                for (data in mediaData.schemeDatas!!) {
                    if (data.hasData() && !containsSchemeDataWithUuid(result, manifestDatasCount, data.uuid)) {
                        result.add(data)
                    }
                }
            }
            return if (result.isEmpty()) null else DrmInitData(schemeType, result)
        }

        val CREATOR: Parcelable.Creator<DrmInitData> = object : Parcelable.Creator<DrmInitData?> {
            override fun createFromParcel(`in`: Parcel): DrmInitData? {
                return DrmInitData(`in`)
            }

            override fun newArray(size: Int): Array<DrmInitData?> {
                return arrayOfNulls(size)
            }
        }

        // Internal methods.
        private fun containsSchemeDataWithUuid(
                datas: ArrayList<SchemeData>, limit: Int, uuid: UUID?): Boolean {
            for (i in 0 until limit) {
                if (datas[i].uuid == uuid) {
                    return true
                }
            }
            return false
        }
    }
}