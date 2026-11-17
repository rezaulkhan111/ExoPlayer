/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.offline

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.io.IOException
import java.util.Arrays
import java.util.Collections

/** Defines content to be downloaded. */
class DownloadRequest private constructor(
    /** The unique content id. */
    @JvmField val id: String,
    /** The uri being downloaded. */
    @JvmField val uri: Uri,
    /**
     * The MIME type of this content. Used as a hint to infer the content's type (DASH, HLS,
     * SmoothStreaming). If null, a `DownloadService` will infer the content type from the
     * [uri].
     */
    @JvmField val mimeType: String?,
    streamKeys: List<StreamKey>,
    keySetId: ByteArray?,
    /**
     * Custom key for cache indexing, or null. Must be null for DASH, HLS and SmoothStreaming
     * downloads.
     */
    @JvmField val customCacheKey: String?,
    data: ByteArray?
) : Parcelable {

  /** Stream keys to be downloaded. If empty, all streams will be downloaded. */
  @JvmField val streamKeys: List<StreamKey>

  /** The key set id of the offline licence if the content is protected with DRM. */
  @JvmField val keySetId: ByteArray?

  /** Application defined data associated with the download. May be empty. */
  @JvmField val data: ByteArray

  /** Thrown when the encoded request data belongs to an unsupported request type. */
  class UnsupportedRequestException : IOException()

  /** A builder for download requests. */
  class Builder(
      private val id: String,
      private val uri: Uri
  ) {
    private var mimeType: String? = null
    private var streamKeys: List<StreamKey>? = null
    private var keySetId: ByteArray? = null
    private var customCacheKey: String? = null
    private var data: ByteArray? = null

    /** Sets the [DownloadRequest.mimeType]. */
    @CanIgnoreReturnValue
    fun setMimeType(mimeType: String?): Builder {
      this.mimeType = mimeType
      return this
    }

    /** Sets the [DownloadRequest.streamKeys]. */
    @CanIgnoreReturnValue
    fun setStreamKeys(streamKeys: List<StreamKey>?): Builder {
      this.streamKeys = streamKeys
      return this
    }

    /** Sets the [DownloadRequest.keySetId]. */
    @CanIgnoreReturnValue
    fun setKeySetId(keySetId: ByteArray?): Builder {
      this.keySetId = keySetId
      return this
    }

    /** Sets the [DownloadRequest.customCacheKey]. */
    @CanIgnoreReturnValue
    fun setCustomCacheKey(customCacheKey: String?): Builder {
      this.customCacheKey = customCacheKey
      return this
    }

    /** Sets the [DownloadRequest.data]. */
    @CanIgnoreReturnValue
    fun setData(data: ByteArray?): Builder {
      this.data = data
      return this
    }

    fun build(): DownloadRequest {
      return DownloadRequest(
          id,
          uri,
          mimeType,
          streamKeys ?: emptyList(),
          keySetId,
          customCacheKey,
          data
      )
    }
  }

  init {
    val contentType = Util.inferContentTypeForUriAndMimeType(uri, mimeType)
    if ((contentType == C.CONTENT_TYPE_DASH) || (contentType == C.CONTENT_TYPE_HLS) || (contentType == C.CONTENT_TYPE_SS)) {
      Assertions.checkArgument(
          customCacheKey == null, "customCacheKey must be null for type: $contentType"
      )
    }
    val mutableKeys = ArrayList(streamKeys)
    mutableKeys.sort()
    this.streamKeys = Collections.unmodifiableList(mutableKeys)
    this.keySetId = keySetId?.copyOf()
    this.data = data?.copyOf() ?: Util.EMPTY_BYTE_ARRAY
  }

  /* package */ internal constructor(parcel: Parcel) : this(
      id = parcel.readString()!!,
      uri = Uri.parse(parcel.readString()!!),
      mimeType = parcel.readString(),
      streamKeys = buildList {
        val streamKeyCount = parcel.readInt()
        for (i in 0 until streamKeyCount) {
          add(parcel.readParcelable<StreamKey>(StreamKey::class.java.classLoader)!!)
        }
      },
      keySetId = parcel.createByteArray(),
      customCacheKey = parcel.readString(),
      data = parcel.createByteArray()!!
  )

  /**
   * Returns a copy with the specified ID.
   *
   * @param id The ID of the copy.
   * @return The copy with the specified ID.
   */
  fun copyWithId(id: String): DownloadRequest {
    return DownloadRequest(id, uri, mimeType, streamKeys, keySetId, customCacheKey, data)
  }

  /**
   * Returns a copy with the specified key set ID.
   *
   * @param keySetId The key set ID of the copy.
   * @return The copy with the specified key set ID.
   */
  fun copyWithKeySetId(keySetId: ByteArray?): DownloadRequest {
    return DownloadRequest(id, uri, mimeType, streamKeys, keySetId, customCacheKey, data)
  }

  /**
   * Returns the result of merging [newRequest] into this request. The requests must have the
   * same [id].
   *
   * The resulting request contains the stream keys from both requests. For all other member
   * variables, those in [newRequest] are preferred.
   *
   * @param newRequest The request being merged.
   * @return The merged result.
   * @throws IllegalArgumentException If the requests do not have the same [id].
   */
  fun copyWithMergedRequest(newRequest: DownloadRequest): DownloadRequest {
    Assertions.checkArgument(id == newRequest.id)
    val mergedKeys: List<StreamKey>
    if (streamKeys.isEmpty() || newRequest.streamKeys.isEmpty()) {
      mergedKeys = emptyList()
    } else {
      mergedKeys = ArrayList(streamKeys)
      for (i in newRequest.streamKeys.indices) {
        val newKey = newRequest.streamKeys[i]
        if (!mergedKeys.contains(newKey)) {
          mergedKeys.add(newKey)
        }
      }
    }
    return DownloadRequest(
        id,
        newRequest.uri,
        newRequest.mimeType,
        mergedKeys,
        newRequest.keySetId,
        newRequest.customCacheKey,
        newRequest.data
    )
  }

  /** Returns a [MediaItem] for the content defined by the request. */
  fun toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setCustomCacheKey(customCacheKey)
        .setMimeType(mimeType)
        .setStreamKeys(streamKeys)
        .build()
  }

  override fun toString(): String {
    return "$mimeType:$id"
  }

  override fun equals(other: Any?): Boolean {
    if (other !is DownloadRequest) {
      return false
    }
    return id == other.id &&
        uri == other.uri &&
        Util.areEqual(mimeType, other.mimeType) &&
        streamKeys == other.streamKeys &&
        Arrays.equals(keySetId, other.keySetId) &&
        Util.areEqual(customCacheKey, other.customCacheKey) &&
        Arrays.equals(data, other.data)
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + uri.hashCode()
    result = 31 * result + (mimeType?.hashCode() ?: 0)
    result = 31 * result + streamKeys.hashCode()
    result = 31 * result + Arrays.hashCode(keySetId)
    result = 31 * result + (customCacheKey?.hashCode() ?: 0)
    result = 31 * result + Arrays.hashCode(data)
    return result
  }

  // Parcelable implementation.

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(id)
    dest.writeString(uri.toString())
    dest.writeString(mimeType)
    dest.writeInt(streamKeys.size)
    for (i in streamKeys.indices) {
      dest.writeParcelable(streamKeys[i], 0)
    }
    dest.writeByteArray(keySetId)
    dest.writeString(customCacheKey)
    dest.writeByteArray(data)
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<DownloadRequest> =
        object : Parcelable.Creator<DownloadRequest> {
          override fun createFromParcel(parcel: Parcel): DownloadRequest {
            return DownloadRequest(parcel)
          }

          override fun newArray(size: Int): Array<DownloadRequest?> {
            return arrayOfNulls(size)
          }
        }
  }
}
