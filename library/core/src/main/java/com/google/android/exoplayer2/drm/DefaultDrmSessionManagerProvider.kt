/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.SuppressLint
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util
import com.google.common.primitives.Ints

/** Default implementation of [DrmSessionManagerProvider]. */
class DefaultDrmSessionManagerProvider : DrmSessionManagerProvider {

  private val lock = Any()

  @GuardedBy("lock")
  private var drmConfiguration: MediaItem.DrmConfiguration? = null

  @GuardedBy("lock")
  private var manager: DrmSessionManager? = null

  private var drmHttpDataSourceFactory: DataSource.Factory? = null
  private var userAgent: String? = null

  /**
   * Sets the [DataSource.Factory] which is used to create [HttpMediaDrmCallback]
   * instances. If `null` is passed a [DefaultHttpDataSource.Factory] is used.
   *
   * @param drmDataSourceFactory The data source factory or `null` to use [DefaultHttpDataSource.Factory].
   */
  fun setDrmHttpDataSourceFactory(drmDataSourceFactory: DataSource.Factory?) {
    this.drmHttpDataSourceFactory = drmDataSourceFactory
  }

  /**
   * @deprecated Pass a custom [DataSource.Factory] to [setDrmHttpDataSourceFactory]
   *     which sets the desired user agent on outgoing requests.
   */
  @Deprecated(
    message =
      "Pass a custom DataSource.Factory to setDrmHttpDataSourceFactory(DataSource.Factory) which sets the desired user agent on outgoing requests.",
  )
  fun setDrmUserAgent(userAgent: String?) {
    this.userAgent = userAgent
  }

  @SuppressLint("NewApi")
  override fun get(mediaItem: MediaItem?): DrmSessionManager {
    checkNotNull(mediaItem)
    checkNotNull(mediaItem!!.localConfiguration)
    val drmConfiguration = mediaItem.localConfiguration!!.drmConfiguration
    if ((drmConfiguration == null) || (Util.SDK_INT < 18)) {
      return DrmSessionManager.DRM_UNSUPPORTED
    }

    synchronized(lock) {
      if (!Util.areEqual(drmConfiguration, this.drmConfiguration)) {
        this.drmConfiguration = drmConfiguration
        this.manager = createManager(drmConfiguration)
      }
      return checkNotNull(this.manager)!!
    }
  }

  @RequiresApi(18)
  private fun createManager(drmConfiguration: MediaItem.DrmConfiguration): DrmSessionManager {
    val dataSourceFactory =
      drmHttpDataSourceFactory
        ?: DefaultHttpDataSource.Factory().setUserAgent(userAgent)
    val httpDrmCallback =
      HttpMediaDrmCallback(
        drmConfiguration.licenseUri?.toString(),
        drmConfiguration.forceDefaultLicenseUri,
        dataSourceFactory,
      )
    for ((key, value) in drmConfiguration.licenseRequestHeaders) {
      httpDrmCallback.setKeyRequestProperty(key, value)
    }
    val drmSessionManager =
      DefaultDrmSessionManager.Builder()
        .setUuidAndExoMediaDrmProvider(
          checkNotNull(drmConfiguration.scheme)!!, FrameworkMediaDrm.DEFAULT_PROVIDER,
        )
        .setMultiSession(drmConfiguration.multiSession)
        .setPlayClearSamplesWithoutKeys(drmConfiguration.playClearContentWithoutKey)
        .setUseDrmSessionsForClearContent(
          *Ints.toArray(drmConfiguration.forcedSessionTrackTypes),
        )
        .build(httpDrmCallback)
    drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, drmConfiguration.getKeySetId())
    return drmSessionManager
  }
}
