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

import android.net.Uri
import android.text.TextUtils
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSourceInputStream
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.StatsDataSource
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableMap
import java.util.HashMap
import java.util.UUID

/** A [MediaDrmCallback] that makes requests using [DataSource] instances. */
class HttpMediaDrmCallback @JvmOverloads constructor(
  private val defaultLicenseUrl: String?,
  private val forceDefaultLicenseUrl: Boolean = false,
  private val dataSourceFactory: DataSource.Factory,
) : MediaDrmCallback {

  private val keyRequestProperties: MutableMap<String, String>

  init {
    checkArgument(!(forceDefaultLicenseUrl && TextUtils.isEmpty(defaultLicenseUrl)))
    keyRequestProperties = HashMap()
  }

  /**
   * Sets a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  fun setKeyRequestProperty(name: String, value: String) {
    checkNotNull(name)
    checkNotNull(value)
    synchronized(keyRequestProperties) {
      keyRequestProperties[name] = value
    }
  }

  /**
   * Clears a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   */
  fun clearKeyRequestProperty(name: String) {
    checkNotNull(name)
    synchronized(keyRequestProperties) {
      keyRequestProperties.remove(name)
    }
  }

  /** Clears all headers for key requests made by the callback. */
  fun clearAllKeyRequestProperties() {
    synchronized(keyRequestProperties) {
      keyRequestProperties.clear()
    }
  }

  @Throws(MediaDrmCallbackException::class)
  override fun executeProvisionRequest(
    uuid: UUID?,
    request: ExoMediaDrm.ProvisionRequest?,
  ): ByteArray {
    val url = "${request!!.getDefaultUrl()}&signedRequest=${Util.fromUtf8Bytes(request.getData()!!)}"
    return executePost(
      dataSourceFactory,
      url,
      /* httpBody= */ null,
      /* requestProperties= */ emptyMap()
    )
  }

  @Throws(MediaDrmCallbackException::class)
  override fun executeKeyRequest(
    uuid: UUID?,
    request: ExoMediaDrm.KeyRequest?
  ): ByteArray {
    var url = request!!.getLicenseServerUrl()
    if (forceDefaultLicenseUrl || TextUtils.isEmpty(url)) {
      url = defaultLicenseUrl
    }
    if (TextUtils.isEmpty(url)) {
      throw MediaDrmCallbackException(
        DataSpec.Builder().setUri(Uri.EMPTY).build(),
        Uri.EMPTY,
        /* responseHeaders= */ ImmutableMap.of(),
        /* bytesLoaded= */ 0,
        /* cause= */ IllegalStateException("No license URL")
      )
    }
    val requestProperties = HashMap<String, String>()
    // Add standard request properties for supported schemes.
    val contentType =
      if (C.PLAYREADY_UUID == uuid) {
        "text/xml"
      } else if (C.CLEARKEY_UUID == uuid) {
        "application/json"
      } else {
        "application/octet-stream"
      }
    requestProperties["Content-Type"] = contentType
    if (C.PLAYREADY_UUID == uuid) {
      requestProperties["SOAPAction"] =
        "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense"
    }
    // Add additional request properties.
    synchronized(keyRequestProperties) {
      requestProperties.putAll(keyRequestProperties)
    }
    return executePost(dataSourceFactory, url!!, request.getData(), requestProperties)
  }

  companion object {
    private const val MAX_MANUAL_REDIRECTS = 5

    @Throws(MediaDrmCallbackException::class)
    private fun executePost(
      dataSourceFactory: DataSource.Factory,
      url: String,
      httpBody: ByteArray?,
      requestProperties: Map<String, String>
    ): ByteArray {
      val dataSource = StatsDataSource(dataSourceFactory.createDataSource()!!)
      var manualRedirectCount = 0
      var dataSpec =
        DataSpec.Builder()
          .setUri(url)
          .setHttpRequestHeaders(requestProperties)
          .setHttpMethod(DataSpec.HTTP_METHOD_POST)
          .setHttpBody(httpBody)
          .setFlags(DataSpec.FLAG_ALLOW_GZIP)
          .build()
      val originalDataSpec = dataSpec
      try {
        while (true) {
          val inputStream = DataSourceInputStream(dataSource, dataSpec)
          try {
            return Util.toByteArray(inputStream)
          } catch (e: InvalidResponseCodeException) {
            val redirectUrl = getRedirectUrl(e, manualRedirectCount) ?: throw e
            manualRedirectCount++
            dataSpec = dataSpec.buildUpon().setUri(redirectUrl).build()
          } finally {
            Util.closeQuietly(inputStream)
          }
        }
      } catch (e: Exception) {
        throw MediaDrmCallbackException(
          originalDataSpec,
          checkNotNull(dataSource.lastOpenedUri)!!,
          dataSource.getResponseHeaders(),
          dataSource.bytesRead,
          /* cause= */ e
        )
      }
    }

    private fun getRedirectUrl(
      exception: InvalidResponseCodeException,
      manualRedirectCount: Int
    ): String? {
      // For POST requests, the underlying network stack will not normally follow 307 or 308
      // redirects automatically. Do so manually here.
      val manuallyRedirect =
        ((exception.responseCode == 307) || (exception.responseCode == 308)) &&
          (manualRedirectCount < MAX_MANUAL_REDIRECTS)
      if (!manuallyRedirect) {
        return null
      }
      val headerFields = exception.headerFields
      if (headerFields != null) {
        val locationHeaders = headerFields["Location"]
        if (!locationHeaders.isNullOrEmpty()) {
          return locationHeaders[0]
        }
      }
      return null
    }
  }
}
