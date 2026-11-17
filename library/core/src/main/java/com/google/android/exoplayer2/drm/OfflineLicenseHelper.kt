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

import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.util.Pair
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Mode
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import java.util.UUID

/** Helper class to download, renew and release offline licenses. */
@RequiresApi(18)
class OfflineLicenseHelper(
  private val drmSessionManager: DefaultDrmSessionManager,
  private val eventDispatcher: DrmSessionEventListener.EventDispatcher,
) {

  private val conditionVariable: ConditionVariable
  private val handlerThread: HandlerThread = HandlerThread("ExoPlayer:OfflineLicenseHelper")

  /**
   * @deprecated Use [OfflineLicenseHelper(DefaultDrmSessionManager,
   *     DrmSessionEventListener.EventDispatcher)][OfflineLicenseHelper] instead.
   */
  @Deprecated(
    "Use OfflineLicenseHelper(DefaultDrmSessionManager, DrmSessionEventListener.EventDispatcher) instead."
  )
  constructor(
    uuid: UUID,
    mediaDrmProvider: ExoMediaDrm.Provider,
    callback: MediaDrmCallback,
    optionalKeyRequestParameters: Map<String, String>?,
    eventDispatcher: DrmSessionEventListener.EventDispatcher,
  ) : this(
    DefaultDrmSessionManager.Builder()
      .setUuidAndExoMediaDrmProvider(uuid, mediaDrmProvider)
      .setKeyRequestParameters(optionalKeyRequestParameters)
      .build(callback),
    eventDispatcher,
  )

  init {
    handlerThread.start()
    conditionVariable = ConditionVariable()
    val eventListener =
      object : DrmSessionEventListener {
        override fun onDrmKeysLoaded(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
          conditionVariable.open()
        }

        override fun onDrmSessionManagerError(
          windowIndex: Int,
          mediaPeriodId: MediaPeriodId?,
          error: Exception?,
        ) {
          conditionVariable.open()
        }

        override fun onDrmKeysRestored(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
          conditionVariable.open()
        }

        override fun onDrmKeysRemoved(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
          conditionVariable.open()
        }
      }
    eventDispatcher.addEventListener(Handler(handlerThread.looper), eventListener)
  }

  /**
   * Downloads an offline license.
   *
   * @param format The [Format] of the content whose license is to be downloaded. Must contain
   *     a non-null [Format.drmInitData].
   * @return The key set id for the downloaded license.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  @Synchronized
  @Throws(DrmSessionException::class)
  fun downloadLicense(format: Format): ByteArray {
    checkArgument(format.drmInitData != null)
    return blockingKeyRequest(DefaultDrmSessionManager.MODE_DOWNLOAD, null, format)
  }

  /**
   * Renews an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be renewed.
   * @return The renewed offline license key set id.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  @Synchronized
  @Throws(DrmSessionException::class)
  fun renewLicense(offlineLicenseKeySetId: ByteArray): ByteArray {
    checkNotNull(offlineLicenseKeySetId)
    return blockingKeyRequest(
      DefaultDrmSessionManager.MODE_DOWNLOAD,
      offlineLicenseKeySetId,
      FORMAT_WITH_EMPTY_DRM_INIT_DATA,
    )
  }

  /**
   * Releases an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be released.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  @Synchronized
  @Throws(DrmSessionException::class)
  fun releaseLicense(offlineLicenseKeySetId: ByteArray) {
    checkNotNull(offlineLicenseKeySetId)
    blockingKeyRequest(
      DefaultDrmSessionManager.MODE_RELEASE,
      offlineLicenseKeySetId,
      FORMAT_WITH_EMPTY_DRM_INIT_DATA,
    )
  }

  /**
   * Returns the remaining license and playback durations in seconds, for an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license.
   * @return The remaining license and playback durations, in seconds.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  @Synchronized
  @Throws(DrmSessionException::class)
  fun getLicenseDurationRemainingSec(
    offlineLicenseKeySetId: ByteArray,
  ): Pair<Long, Long> {
    checkNotNull(offlineLicenseKeySetId)
    drmSessionManager.setPlayer(handlerThread.looper, PlayerId.UNSET)
    drmSessionManager.prepare()
    val drmSession =
      openBlockingKeyRequest(
        DefaultDrmSessionManager.MODE_QUERY,
        offlineLicenseKeySetId,
        FORMAT_WITH_EMPTY_DRM_INIT_DATA,
      )
    val error = drmSession.getError()
    val licenseDurationRemainingSec =
      WidevineUtil.getLicenseDurationRemainingSec(drmSession)
    drmSession.release(eventDispatcher)
    drmSessionManager.release()
    if (error != null) {
      if (error.cause is KeysExpiredException) {
        return Pair.create(0L, 0L)
      }
      throw error
    }
    return checkNotNull(licenseDurationRemainingSec)!!
  }

  /** Releases the helper. Should be called when the helper is no longer required. */
  fun release() {
    handlerThread.quit()
  }

  @Throws(DrmSessionException::class)
  private fun blockingKeyRequest(
    @Mode licenseMode: Int,
    offlineLicenseKeySetId: ByteArray?,
    format: Format,
  ): ByteArray {
    drmSessionManager.setPlayer(handlerThread.looper, PlayerId.UNSET)
    drmSessionManager.prepare()
    val drmSession = openBlockingKeyRequest(licenseMode, offlineLicenseKeySetId, format)
    val error = drmSession.getError()
    val keySetId = drmSession.getOfflineLicenseKeySetId()
    drmSession.release(eventDispatcher)
    drmSessionManager.release()
    if (error != null) {
      throw error
    }
    return checkNotNull(keySetId)!!
  }

  private fun openBlockingKeyRequest(
    @Mode licenseMode: Int,
    offlineLicenseKeySetId: ByteArray?,
    format: Format,
  ): DrmSession {
    checkNotNull(format.drmInitData)
    drmSessionManager.setMode(licenseMode, offlineLicenseKeySetId)
    conditionVariable.close()
    val drmSession = drmSessionManager.acquireSession(eventDispatcher, format)
    // Block current thread until key loading is finished
    conditionVariable.block()
    return checkNotNull(drmSession)!!
  }

  companion object {

    private val FORMAT_WITH_EMPTY_DRM_INIT_DATA =
      Format.Builder().setDrmInitData(DrmInitData()).build()

    /**
     * Instantiates a new instance which uses Widevine CDM. Call [release] when the instance
     * is no longer required.
     *
     * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
     *     their own license URL.
     * @param dataSourceFactory A factory from which to obtain [DataSource] instances.
     * @param eventDispatcher A [DrmSessionEventListener.EventDispatcher] used to distribute
     *     DRM-related events.
     * @return A new instance which uses Widevine CDM.
     */
    @JvmStatic
    fun newWidevineInstance(
      defaultLicenseUrl: String,
      dataSourceFactory: DataSource.Factory,
      eventDispatcher: DrmSessionEventListener.EventDispatcher,
    ): OfflineLicenseHelper {
      return newWidevineInstance(
        defaultLicenseUrl,
        /* forceDefaultLicenseUrl= */ false,
        dataSourceFactory,
        eventDispatcher,
      )
    }

    /**
     * Instantiates a new instance which uses Widevine CDM. Call [release] when the instance
     * is no longer required.
     *
     * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
     *     their own license URL.
     * @param forceDefaultLicenseUrl Whether to use `defaultLicenseUrl` for key requests that
     *     include their own license URL.
     * @param dataSourceFactory A factory from which to obtain [DataSource] instances.
     * @param eventDispatcher A [DrmSessionEventListener.EventDispatcher] used to distribute
     *     DRM-related events.
     * @return A new instance which uses Widevine CDM.
     */
    @JvmStatic
    fun newWidevineInstance(
      defaultLicenseUrl: String,
      forceDefaultLicenseUrl: Boolean,
      dataSourceFactory: DataSource.Factory,
      eventDispatcher: DrmSessionEventListener.EventDispatcher,
    ): OfflineLicenseHelper {
      return newWidevineInstance(
        defaultLicenseUrl,
        forceDefaultLicenseUrl,
        dataSourceFactory,
        /* optionalKeyRequestParameters= */ null,
        eventDispatcher,
      )
    }

    /**
     * Instantiates a new instance which uses Widevine CDM. Call [release] when the instance
     * is no longer required.
     *
     * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
     *     their own license URL.
     * @param forceDefaultLicenseUrl Whether to use `defaultLicenseUrl` for key requests that
     *     include their own license URL.
     * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
     *     to [android.media.MediaDrm.getKeyRequest]. May be null.
     * @param eventDispatcher A [DrmSessionEventListener.EventDispatcher] used to distribute
     *     DRM-related events.
     * @return A new instance which uses Widevine CDM.
     * @see DefaultDrmSessionManager.Builder
     */
    @JvmStatic
    fun newWidevineInstance(
      defaultLicenseUrl: String,
      forceDefaultLicenseUrl: Boolean,
      dataSourceFactory: DataSource.Factory,
      optionalKeyRequestParameters: Map<String, String>?,
      eventDispatcher: DrmSessionEventListener.EventDispatcher,
    ): OfflineLicenseHelper {
      return OfflineLicenseHelper(
        DefaultDrmSessionManager.Builder()
          .setKeyRequestParameters(optionalKeyRequestParameters)
          .build(
            HttpMediaDrmCallback(
              defaultLicenseUrl, forceDefaultLicenseUrl, dataSourceFactory
            )
          ),
        eventDispatcher,
      )
    }
  }
}
