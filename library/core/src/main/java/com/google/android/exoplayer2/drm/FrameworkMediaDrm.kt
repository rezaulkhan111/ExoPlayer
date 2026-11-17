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

import android.annotation.SuppressLint
import android.media.DeniedByServerException
import android.media.MediaCrypto
import android.media.MediaCryptoException
import android.media.MediaDrm
import android.media.MediaDrmException
import android.media.NotProvisionedException
import android.media.UnsupportedSchemeException
import android.media.metrics.LogSessionId
import android.os.PersistableBundle
import android.text.TextUtils
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.ParsableByteArray
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Charsets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList
import java.util.HashMap
import java.util.UUID

/** An [ExoMediaDrm] implementation that wraps the framework [MediaDrm]. */
@RequiresApi(18)
class FrameworkMediaDrm private constructor(uuid: UUID) : ExoMediaDrm {

  private val uuid: UUID
  private val mediaDrm: MediaDrm
  private var referenceCount: Int

  init {
    checkNotNull(uuid)
    checkArgument(C.COMMON_PSSH_UUID != uuid, "Use C.CLEARKEY_UUID instead")
    this.uuid = uuid
    this.mediaDrm = MediaDrm(adjustUuid(uuid))
    // Creators of an instance automatically acquire ownership of the created instance.
    referenceCount = 1
    if ((C.WIDEVINE_UUID == uuid) && needsForceWidevineL3Workaround()) {
      forceWidevineL3(mediaDrm)
    }
  }

  override fun setOnEventListener(listener: ExoMediaDrm.OnEventListener?) {
    mediaDrm.setOnEventListener(
      if (listener == null) null
      else MediaDrm.OnEventListener { _, sessionId, event, extra, data ->
        listener.onEvent(this, sessionId, event, extra, data)
      },
    )
  }

  /**
   * {@inheritDoc}
   *
   * @param listener The listener to receive events, or `null` to stop receiving events.
   * @throws UnsupportedOperationException on API levels lower than 23.
   */
  @RequiresApi(23)
  override fun setOnKeyStatusChangeListener(listener: ExoMediaDrm.OnKeyStatusChangeListener?) {
    if (Util.SDK_INT < 23) {
      throw UnsupportedOperationException()
    }

    mediaDrm.setOnKeyStatusChangeListener(
      if (listener == null) null
      else MediaDrm.OnKeyStatusChangeListener { _, sessionId, keyInfo, hasNewUsableKey ->
        val exoKeyInfo = ArrayList<ExoMediaDrm.KeyStatus>()
        for (keyStatus in keyInfo) {
          exoKeyInfo.add(ExoMediaDrm.KeyStatus(keyStatus.statusCode, keyStatus.keyId))
        }
        listener.onKeyStatusChange(this, sessionId, exoKeyInfo, hasNewUsableKey)
      },
      /* handler= */ null,
    )
  }

  /**
   * {@inheritDoc}
   *
   * @param listener The listener to receive events, or `null` to stop receiving events.
   * @throws UnsupportedOperationException on API levels lower than 23.
   */
  @RequiresApi(23)
  override fun setOnExpirationUpdateListener(listener: ExoMediaDrm.OnExpirationUpdateListener?) {
    if (Util.SDK_INT < 23) {
      throw UnsupportedOperationException()
    }

    mediaDrm.setOnExpirationUpdateListener(
      if (listener == null) null
      else MediaDrm.OnExpirationUpdateListener { _, sessionId, expirationTimeMs ->
        listener.onExpirationUpdate(this, sessionId, expirationTimeMs)
      },
      /* handler= */ null,
    )
  }

  @Throws(MediaDrmException::class)
  override fun openSession(): ByteArray {
    return mediaDrm.openSession()
  }

  override fun closeSession(sessionId: ByteArray?) {
    mediaDrm.closeSession(sessionId!!)
  }

  @SuppressLint("NewApi")
  override fun setPlayerIdForSession(sessionId: ByteArray?, playerId: PlayerId?) {
    if (Util.SDK_INT >= 31) {
      try {
        Api31.setLogSessionIdOnMediaDrmSession(mediaDrm, sessionId!!, playerId!!)
      } catch (_: UnsupportedOperationException) {
        Log.w(TAG, "setLogSessionId failed.")
      }
    }
  }

  // Return values of MediaDrm.KeyRequest.getRequestType are equal to KeyRequest.RequestType.
  @SuppressLint("WrongConstant", "NewApi")
  @Throws(NotProvisionedException::class)
  override fun getKeyRequest(
    scope: ByteArray?,
    schemeDatas: List<SchemeData?>?,
    keyType: Int,
    optionalParameters: HashMap<String?, String?>?,
  ): ExoMediaDrm.KeyRequest {
    var schemeData: SchemeData? = null
    var initData: ByteArray? = null
    var mimeType: String? = null
    if (schemeDatas != null) {
      schemeData = getSchemeData(uuid, schemeDatas)
      initData = adjustRequestInitData(uuid, checkNotNull(schemeData.data)!!)
      mimeType = adjustRequestMimeType(uuid, schemeData.mimeType)
    }
    val request = mediaDrm.getKeyRequest(scope!!, initData, mimeType, keyType, optionalParameters)

    val requestData = adjustRequestData(uuid, request.data)
    var licenseServerUrl = adjustLicenseServerUrl(request.defaultUrl)
    if (TextUtils.isEmpty(licenseServerUrl) && (schemeData != null) && !TextUtils.isEmpty(schemeData.licenseServerUrl)) {
      licenseServerUrl = schemeData.licenseServerUrl
    }

    val requestType = if (Util.SDK_INT >= 23) request.requestType else ExoMediaDrm.KeyRequest.REQUEST_TYPE_UNKNOWN

    return ExoMediaDrm.KeyRequest(requestData, licenseServerUrl, requestType)
  }

  @Throws(NotProvisionedException::class, DeniedByServerException::class)
  override fun provideKeyResponse(scope: ByteArray?, response: ByteArray?): ByteArray? {
    var responseData = response
    if (C.CLEARKEY_UUID == uuid) {
      responseData = ClearKeyUtil.adjustResponseData(responseData!!)
    }

    return mediaDrm.provideKeyResponse(scope!!, responseData!!)
  }

  override fun getProvisionRequest(): ExoMediaDrm.ProvisionRequest {
    val request = mediaDrm.provisionRequest
    return ExoMediaDrm.ProvisionRequest(request.data, request.defaultUrl)
  }

  @Throws(DeniedByServerException::class)
  override fun provideProvisionResponse(response: ByteArray?) {
    mediaDrm.provideProvisionResponse(response!!)
  }

  override fun queryKeyStatus(sessionId: ByteArray?): Map<String?, String?> {
    return mediaDrm.queryKeyStatus(sessionId!!)
  }

  @SuppressLint("NewApi")
  override fun requiresSecureDecoder(sessionId: ByteArray?, mimeType: String?): Boolean {
    if (Util.SDK_INT >= 31) {
      return Api31.requiresSecureDecoder(mediaDrm, mimeType!!)
    }

    val mediaCrypto =
      try {
        MediaCrypto(uuid, sessionId!!)
      } catch (_: MediaCryptoException) {
        // This shouldn't happen, but if it does then assume that a secure decoder may be required.
        return true
      }
    try {
      return mediaCrypto.requiresSecureDecoderComponent(mimeType!!)
    } finally {
      mediaCrypto.release()
    }
  }

  @Synchronized
  override fun acquire() {
    checkState(referenceCount > 0)
    referenceCount++
  }

  @Synchronized
  override fun release() {
    if (--referenceCount == 0) {
      mediaDrm.release()
    }
  }

  override fun restoreKeys(sessionId: ByteArray?, keySetId: ByteArray?) {
    mediaDrm.restoreKeys(sessionId!!, keySetId!!)
  }

  @SuppressLint("NewApi")
  override fun getMetrics(): PersistableBundle? {
    if (Util.SDK_INT < 28) {
      return null
    }
    return mediaDrm.metrics
  }

  override fun getPropertyString(propertyName: String?): String {
    return mediaDrm.getPropertyString(propertyName!!)
  }

  override fun getPropertyByteArray(propertyName: String?): ByteArray {
    return mediaDrm.getPropertyByteArray(propertyName!!)
  }

  override fun setPropertyString(propertyName: String?, value: String?) {
    mediaDrm.setPropertyString(propertyName!!, value!!)
  }

  override fun setPropertyByteArray(propertyName: String?, value: ByteArray?) {
    mediaDrm.setPropertyByteArray(propertyName!!, value!!)
  }

  @Throws(MediaCryptoException::class)
  override fun createCryptoConfig(sessionId: ByteArray?): FrameworkCryptoConfig {
    // Work around a bug prior to Lollipop where L1 Widevine forced into L3 mode would still
    // indicate that it required secure video decoders [Internal ref: b/11428937].
    val forceAllowInsecureDecoderComponents =
      Util.SDK_INT < 21 && C.WIDEVINE_UUID == uuid && "L3" == getPropertyString("securityLevel")
    return FrameworkCryptoConfig(
      adjustUuid(uuid), sessionId!!, forceAllowInsecureDecoderComponents,
    )
  }

  override fun getCryptoType(): Int {
    return C.CRYPTO_TYPE_FRAMEWORK
  }

  companion object {

    private const val TAG = "FrameworkMediaDrm"

    /**
     * [ExoMediaDrm.Provider] that returns a new [FrameworkMediaDrm] for the requested
     * UUID. Returns a [DummyExoMediaDrm] if the protection scheme identified by the given UUID
     * is not supported by the device.
     */
    @JvmField
    val DEFAULT_PROVIDER: ExoMediaDrm.Provider = object : ExoMediaDrm.Provider {
      override fun acquireExoMediaDrm(uuid: UUID?): ExoMediaDrm {
        return try {
          newInstance(uuid!!)
        } catch (e: UnsupportedDrmException) {
          Log.e(TAG, "Failed to instantiate a FrameworkMediaDrm for uuid: $uuid.")
          DummyExoMediaDrm()
        }
      }
    }

    private const val CENC_SCHEME_MIME_TYPE = "cenc"
    private const val MOCK_LA_URL_VALUE = "https://x"
    private const val MOCK_LA_URL = "<LA_URL>$MOCK_LA_URL_VALUE</LA_URL>"
    private const val UTF_16_BYTES_PER_CHARACTER = 2

    /**
     * Returns whether the DRM scheme with the given UUID is supported on this device.
     *
     * @see MediaDrm.isCryptoSchemeSupported
     */
    @JvmStatic
    fun isCryptoSchemeSupported(uuid: UUID): Boolean {
      return MediaDrm.isCryptoSchemeSupported(adjustUuid(uuid))
    }

    /**
     * Creates an instance with an initial reference count of 1. [release] must be called on
     * the instance when it's no longer required.
     *
     * @param uuid The scheme uuid.
     * @return The created instance.
     * @throws UnsupportedDrmException If the DRM scheme is unsupported or cannot be instantiated.
     */
    @JvmStatic
    @Throws(UnsupportedDrmException::class)
    fun newInstance(uuid: UUID): FrameworkMediaDrm {
      return try {
        FrameworkMediaDrm(uuid)
      } catch (e: UnsupportedSchemeException) {
        throw UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME, e)
      } catch (e: Exception) {
        throw UnsupportedDrmException(UnsupportedDrmException.REASON_INSTANTIATION_ERROR, e)
      }
    }

    private fun getSchemeData(uuid: UUID, schemeDatas: List<SchemeData?>): SchemeData {
      if (C.WIDEVINE_UUID != uuid) {
        // For non-Widevine CDMs always use the first scheme data.
        return schemeDatas[0]!!
      }

      if (Util.SDK_INT >= 28 && schemeDatas.size > 1) {
        // For API level 28 and above, concatenate multiple PSSH scheme datas if possible.
        val firstSchemeData = schemeDatas[0]!!
        var concatenatedDataLength = 0
        var canConcatenateData = true
        for (i in schemeDatas.indices) {
          val schemeData = schemeDatas[i]!!
          val schemeDataData = checkNotNull(schemeData.data)!!
          if (Util.areEqual(schemeData.mimeType, firstSchemeData.mimeType) &&
            Util.areEqual(schemeData.licenseServerUrl, firstSchemeData.licenseServerUrl) &&
            PsshAtomUtil.isPsshAtom(schemeDataData)
          ) {
            concatenatedDataLength += schemeDataData.size
          } else {
            canConcatenateData = false
            break
          }
        }
        if (canConcatenateData) {
          val concatenatedData = ByteArray(concatenatedDataLength)
          var concatenatedDataPosition = 0
          for (i in schemeDatas.indices) {
            val schemeData = schemeDatas[i]!!
            val schemeDataData = checkNotNull(schemeData.data)!!
            val schemeDataLength = schemeDataData.size
            System.arraycopy(
              schemeDataData, 0, concatenatedData, concatenatedDataPosition, schemeDataLength,
            )
            concatenatedDataPosition += schemeDataLength
          }
          return firstSchemeData.copyWithData(concatenatedData)
        }
      }

      // For API levels 23 - 27, prefer the first V1 PSSH box. For API levels 22 and earlier, prefer
      // the first V0 box.
      for (i in schemeDatas.indices) {
        val schemeData = schemeDatas[i]!!
        val version = PsshAtomUtil.parseVersion(checkNotNull(schemeData.data)!!)
        if (Util.SDK_INT < 23 && version == 0) {
          return schemeData
        } else if (Util.SDK_INT >= 23 && version == 1) {
          return schemeData
        }
      }

      // If all else fails, use the first scheme data.
      return schemeDatas[0]!!
    }

    private fun adjustLicenseServerUrl(licenseServerUrl: String?): String? {
      return if (MOCK_LA_URL == licenseServerUrl) {
        ""
      } else if (Util.SDK_INT == 33 && "https://default.url" == licenseServerUrl) {
        // Work around b/247808112
        ""
      } else {
        licenseServerUrl
      }
    }

    private fun adjustUuid(uuid: UUID): UUID {
      // ClearKey had to be accessed using the Common PSSH UUID prior to API level 27.
      return if (Util.SDK_INT < 27 && C.CLEARKEY_UUID == uuid) C.COMMON_PSSH_UUID else uuid
    }

    private fun adjustRequestInitData(uuid: UUID, initData: ByteArray): ByteArray {
      var adjustedInitData = initData
      // TODO: Add API level check once [Internal ref: b/112142048] is fixed.
      if (C.PLAYREADY_UUID == uuid) {
        var schemeSpecificData = PsshAtomUtil.parseSchemeSpecificData(adjustedInitData, uuid)
        if (schemeSpecificData == null) {
          // The init data is not contained in a pssh box.
          schemeSpecificData = adjustedInitData
        }
        adjustedInitData = PsshAtomUtil.buildPsshAtom(
          C.PLAYREADY_UUID, addLaUrlAttributeIfMissing(schemeSpecificData),
        )
      }

      if ((Util.SDK_INT < 23 && C.WIDEVINE_UUID == uuid) ||
        (C.PLAYREADY_UUID == uuid &&
          "Amazon" == Util.MANUFACTURER &&
          ("AFTB" == Util.MODEL ||
            "AFTS" == Util.MODEL ||
            "AFTM" == Util.MODEL ||
            "AFTT" == Util.MODEL))
      ) {
        val psshData = PsshAtomUtil.parseSchemeSpecificData(adjustedInitData, uuid)
        if (psshData != null) {
          return psshData
        }
      }
      return adjustedInitData
    }

    private fun adjustRequestMimeType(uuid: UUID, mimeType: String?): String? {
      // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
      if (Util.SDK_INT < 26 &&
        C.CLEARKEY_UUID == uuid &&
        (MimeTypes.VIDEO_MP4 == mimeType || MimeTypes.AUDIO_MP4 == mimeType)
      ) {
        return CENC_SCHEME_MIME_TYPE
      }
      return mimeType
    }

    private fun adjustRequestData(uuid: UUID, requestData: ByteArray): ByteArray {
      if (C.CLEARKEY_UUID == uuid) {
        return ClearKeyUtil.adjustRequestData(requestData)
      }
      return requestData
    }

    private fun forceWidevineL3(mediaDrm: MediaDrm) {
      mediaDrm.setPropertyString("securityLevel", "L3")
    }

    /**
     * Returns whether the device codec is known to fail if security level L1 is used.
     *
     * <p>See <a href="https://github.com/google/ExoPlayer/issues/4413">GitHub issue #4413</a>.
     */
    private fun needsForceWidevineL3Workaround(): Boolean {
      return "ASUS_Z00AD" == Util.MODEL
    }

    /**
     * If the LA_URL tag is missing, injects a mock LA_URL value to avoid causing the CDM to throw
     * when creating the key request. The LA_URL attribute is optional but some Android PlayReady
     * implementations are known to require it. Does nothing it the provided {@code data} already
     * contains an LA_URL value.
     */
    private fun addLaUrlAttributeIfMissing(data: ByteArray): ByteArray {
      val byteArray = ParsableByteArray(data)
      val length = byteArray.readLittleEndianInt()
      val objectRecordCount = byteArray.readLittleEndianShort()
      val recordType = byteArray.readLittleEndianShort()
      if (objectRecordCount.toInt() != 1 || recordType.toInt() != 1) {
        Log.i(TAG, "Unexpected record count or type. Skipping LA_URL workaround.")
        return data
      }
      val recordLength = byteArray.readLittleEndianShort()
      val xml = byteArray.readString(recordLength.toInt(), Charsets.UTF_16LE)
      if (xml.contains("<LA_URL>")) {
        return data
      }
      val endOfDataTagIndex = xml.indexOf("</DATA>")
      if (endOfDataTagIndex == -1) {
        Log.w(TAG, "Could not find the </DATA> tag. Skipping LA_URL workaround.")
      }
      val xmlWithMockLaUrl =
        xml.substring(0, endOfDataTagIndex) + MOCK_LA_URL + xml.substring(endOfDataTagIndex)
      val extraBytes = MOCK_LA_URL.length * UTF_16_BYTES_PER_CHARACTER
      val newData = ByteBuffer.allocate(length + extraBytes)
      newData.order(ByteOrder.LITTLE_ENDIAN)
      newData.putInt(length + extraBytes)
      newData.putShort(objectRecordCount)
      newData.putShort(recordType)
      newData.putShort((xmlWithMockLaUrl.length * UTF_16_BYTES_PER_CHARACTER).toShort())
      newData.put(xmlWithMockLaUrl.toByteArray(Charsets.UTF_16LE))
      return newData.array()
    }
  }

  @RequiresApi(31)
  private object Api31 {
    @DoNotInline
    @JvmStatic
    fun requiresSecureDecoder(mediaDrm: MediaDrm, mimeType: String): Boolean {
      return mediaDrm.requiresSecureDecoder(mimeType)
    }

    @DoNotInline
    @JvmStatic
    @SuppressLint("NewApi")
    fun setLogSessionIdOnMediaDrmSession(
      mediaDrm: MediaDrm, drmSessionId: ByteArray, playerId: PlayerId,
    ) {
      val logSessionId = playerId.getLogSessionId()
      if (logSessionId != LogSessionId.LOG_SESSION_ID_NONE) {
        val playbackComponent = checkNotNull(mediaDrm.getPlaybackComponent(drmSessionId))
        playbackComponent!!.setLogSessionId(logSessionId)
      }
    }
  }
}
