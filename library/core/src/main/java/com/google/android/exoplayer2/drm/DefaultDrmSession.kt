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
import android.media.NotProvisionedException
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Pair
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.CopyOnWriteMultiset
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.Collections
import java.util.UUID
import kotlin.math.min

/** A [DrmSession] that supports playbacks using [ExoMediaDrm]. */
@RequiresApi(18)
internal class DefaultDrmSession(
  @JvmField val uuid: UUID,
  private val mediaDrm: ExoMediaDrm,
  private val provisioningManager: ProvisioningManager,
  private val referenceCountListener: ReferenceCountListener,
  schemeDatas: List<SchemeData>?,
  private val mode: Int,
  private val playClearSamplesWithoutKeys: Boolean,
  private val isPlaceholderSession: Boolean,
  offlineLicenseKeySetId: ByteArray?,
  private val keyRequestParameters: HashMap<String, String>,
  @JvmField val callback: MediaDrmCallback,
  playbackLooper: Looper,
  private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
  private val playerId: PlayerId
) : DrmSession {

  /** Thrown when an unexpected exception or error is thrown during provisioning or key requests. */
  class UnexpectedDrmSessionException(cause: Throwable?) : IOException(cause)

  /** Manages provisioning requests. */
  interface ProvisioningManager {

    /**
     * Called when a session requires provisioning. The manager *may* call [provision] to have
     * this session perform the provisioning operation. The manager *will* call [onProvisionCompleted]
     * when provisioning has completed, or [onProvisionError] if provisioning fails.
     *
     * @param session The session.
     */
    fun provisionRequired(session: DefaultDrmSession)

    /**
     * Called by a session when it fails to perform a provisioning operation.
     *
     * @param error The error that occurred.
     * @param thrownByExoMediaDrm Whether the error originated in an [ExoMediaDrm] operation. False
     *     when the error originated in the provisioning request.
     */
    fun onProvisionError(error: Exception, thrownByExoMediaDrm: Boolean)

    /** Called by a session when it successfully completes a provisioning operation. */
    fun onProvisionCompleted()
  }

  /** Callback to be notified when the reference count of this session changes. */
  interface ReferenceCountListener {

    /**
     * Called when the internal reference count of this session is incremented.
     *
     * @param session This session.
     * @param newReferenceCount The reference count after being incremented.
     */
    fun onReferenceCountIncremented(session: DefaultDrmSession, newReferenceCount: Int)

    /**
     * Called when the internal reference count of this session is decremented.
     *
     * `<p>newReferenceCount == 0` indicates this session is in [DrmSession.STATE_RELEASED].
     *
     * @param session This session.
     * @param newReferenceCount The reference count after being decremented.
     */
    fun onReferenceCountDecremented(session: DefaultDrmSession, newReferenceCount: Int)
  }

  /** The DRM scheme datas, or null if this session uses offline keys. */
  @JvmField val schemeDatas: List<SchemeData>?

  @JvmField val responseHandler: ResponseHandler

  private val eventDispatchers: CopyOnWriteMultiset<DrmSessionEventListener.EventDispatcher> =
    CopyOnWriteMultiset()

  private var state: Int = DrmSession.STATE_OPENING
  private var referenceCount: Int = 0
  private var requestHandlerThread: HandlerThread? = null
  private var requestHandler: RequestHandler? = null
  private var cryptoConfig: CryptoConfig? = null
  private var lastException: DrmSessionException? = null
  private var sessionId: ByteArray? = null

  @JvmField var offlineLicenseKeySetId: ByteArray? = null

  private var currentKeyRequest: KeyRequest? = null
  private var currentProvisionRequest: ProvisionRequest? = null

  init {
    if (mode == DefaultDrmSessionManager.MODE_QUERY ||
      mode == DefaultDrmSessionManager.MODE_RELEASE
    ) {
      Assertions.checkNotNull(offlineLicenseKeySetId)
    }
    if (offlineLicenseKeySetId != null) {
      this.offlineLicenseKeySetId = offlineLicenseKeySetId
      this.schemeDatas = null
    } else {
      this.schemeDatas = Collections.unmodifiableList(schemeDatas!!)
    }
    responseHandler = ResponseHandler(playbackLooper)
  }

  fun hasSessionId(sessionId: ByteArray): Boolean {
    return this.sessionId.contentEquals(sessionId)
  }

  fun onMediaDrmEvent(what: Int) {
    when (what) {
      ExoMediaDrm.EVENT_KEY_REQUIRED -> onKeysRequired()
      else -> {}
    }
  }

  // Provisioning implementation.

  fun provision() {
    currentProvisionRequest = mediaDrm.getProvisionRequest()
    requestHandler?.post(
      MSG_PROVISION,
      Assertions.checkNotNull(currentProvisionRequest)!!,
      /* allowRetry= */ true
    )
  }

  fun onProvisionCompleted() {
    if (openInternal()) {
      doLicense(true)
    }
  }

  fun onProvisionError(error: Exception, thrownByExoMediaDrm: Boolean) {
    onError(
      error,
      if (thrownByExoMediaDrm) DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM
      else DrmUtil.ERROR_SOURCE_PROVISIONING
    )
  }

  // DrmSession implementation.

  override fun getState(): Int {
    return state
  }

  override fun playClearSamplesWithoutKeys(): Boolean {
    return playClearSamplesWithoutKeys
  }

  override fun getError(): DrmSessionException? {
    return if (state == DrmSession.STATE_ERROR) lastException else null
  }

  override fun getSchemeUuid(): UUID {
    return uuid
  }

  override fun getCryptoConfig(): CryptoConfig? {
    return cryptoConfig
  }

  override fun queryKeyStatus(): Map<String?, String?>? {
    val sessionId = this.sessionId ?: return null
    return mediaDrm.queryKeyStatus(sessionId)
  }

  override fun getOfflineLicenseKeySetId(): ByteArray? {
    return offlineLicenseKeySetId
  }

  override fun requiresSecureDecoder(mimeType: String?): Boolean {
    return mediaDrm.requiresSecureDecoder(Assertions.checkStateNotNull(sessionId), mimeType)
  }

  override fun acquire(eventDispatcher: DrmSessionEventListener.EventDispatcher?) {
    if (referenceCount < 0) {
      Log.e(TAG, "Session reference count less than zero: $referenceCount")
      referenceCount = 0
    }
    if (eventDispatcher != null) {
      eventDispatchers.add(eventDispatcher)
    }
    if (++referenceCount == 1) {
      Assertions.checkState(state == DrmSession.STATE_OPENING)
      val thread = HandlerThread("ExoPlayer:DrmRequestHandler")
      requestHandlerThread = thread
      thread.start()
      requestHandler = RequestHandler(thread.looper)
      if (openInternal()) {
        doLicense(true)
      }
    } else if (eventDispatcher != null &&
      isOpen() &&
      eventDispatchers.count(eventDispatcher) == 1
    ) {
      eventDispatcher.drmSessionAcquired(state)
    }
    referenceCountListener.onReferenceCountIncremented(this, referenceCount)
  }

  override fun release(eventDispatcher: DrmSessionEventListener.EventDispatcher?) {
    if (referenceCount <= 0) {
      Log.e(TAG, "release() called on a session that's already fully released.")
      return
    }
    if (--referenceCount == 0) {
      state = DrmSession.STATE_RELEASED
      responseHandler.removeCallbacksAndMessages(null)
      requestHandler?.release()
      requestHandler = null
      requestHandlerThread?.quit()
      requestHandlerThread = null
      cryptoConfig = null
      lastException = null
      currentKeyRequest = null
      currentProvisionRequest = null
      if (sessionId != null) {
        mediaDrm.closeSession(sessionId!!)
        sessionId = null
      }
    }
    if (eventDispatcher != null) {
      eventDispatchers.remove(eventDispatcher)
      if (eventDispatchers.count(eventDispatcher) == 0) {
        eventDispatcher.drmSessionReleased()
      }
    }
    referenceCountListener.onReferenceCountDecremented(this, referenceCount)
  }

  // Internal methods.

  private fun openInternal(): Boolean {
    if (isOpen()) {
      return true
    }

    try {
      val sessionId = mediaDrm.openSession()
      this.sessionId = sessionId
      mediaDrm.setPlayerIdForSession(sessionId, playerId)
      cryptoConfig = mediaDrm.createCryptoConfig(sessionId)
      state = DrmSession.STATE_OPENED
      val localState = state
      dispatchEvent { it.drmSessionAcquired(localState) }
      Assertions.checkNotNull(this.sessionId)
      return true
    } catch (_: NotProvisionedException) {
      provisioningManager.provisionRequired(this)
    } catch (e: Exception) {
      onError(e, DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM)
    }

    return false
  }

  private fun onProvisionResponse(request: Any?, response: Any?) {
    if (request != currentProvisionRequest || (!isOpen() && state != DrmSession.STATE_OPENING)) {
      return
    }
    currentProvisionRequest = null

    if (response is Exception) {
      provisioningManager.onProvisionError(response, /* thrownByExoMediaDrm= */ false)
      return
    }

    try {
      mediaDrm.provideProvisionResponse((response as ByteArray))
    } catch (e: Exception) {
      provisioningManager.onProvisionError(e, /* thrownByExoMediaDrm= */ true)
      return
    }

    provisioningManager.onProvisionCompleted()
  }

  private fun doLicense(allowRetry: Boolean) {
    if (isPlaceholderSession) {
      return
    }
    val sessionId = Util.castNonNull(this.sessionId)!!
    when (mode) {
      DefaultDrmSessionManager.MODE_PLAYBACK,
      DefaultDrmSessionManager.MODE_QUERY ->
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_STREAMING, allowRetry)
        } else if (state == DrmSession.STATE_OPENED_WITH_KEYS || restoreKeys()) {
          val licenseDurationRemainingSec = licenseDurationRemainingSec
          if (mode == DefaultDrmSessionManager.MODE_PLAYBACK &&
            licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW_SECONDS
          ) {
            Log.d(
              TAG,
              "Offline license has expired or will expire soon. Remaining seconds: $licenseDurationRemainingSec"
            )
            postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry)
          } else if (licenseDurationRemainingSec <= 0) {
            onError(KeysExpiredException(), DrmUtil.ERROR_SOURCE_LICENSE_ACQUISITION)
          } else {
            state = DrmSession.STATE_OPENED_WITH_KEYS
            dispatchEvent { it.drmKeysRestored() }
          }
        }
      DefaultDrmSessionManager.MODE_DOWNLOAD ->
        if (offlineLicenseKeySetId == null || restoreKeys()) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry)
        }
      DefaultDrmSessionManager.MODE_RELEASE -> {
        Assertions.checkNotNull(offlineLicenseKeySetId)
        Assertions.checkNotNull(this.sessionId)
        postKeyRequest(offlineLicenseKeySetId!!, ExoMediaDrm.KEY_TYPE_RELEASE, allowRetry)
      }
    }
  }

  private fun restoreKeys(): Boolean {
    return try {
      mediaDrm.restoreKeys(sessionId!!, offlineLicenseKeySetId!!)
      true
    } catch (e: Exception) {
      onError(e, DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM)
      false
    }
  }

  private val licenseDurationRemainingSec: Long
    get() {
      if (C.WIDEVINE_UUID != uuid) {
        return Long.MAX_VALUE
      }
      val pair = Assertions.checkNotNull(WidevineUtil.getLicenseDurationRemainingSec(this))!!
      return min(pair.first, pair.second)
    }

  private fun postKeyRequest(scope: ByteArray, type: Int, allowRetry: Boolean) {
    try {
      @Suppress("UNCHECKED_CAST")
      currentKeyRequest = mediaDrm.getKeyRequest(
        scope,
        schemeDatas as List<SchemeData?>?,
        type,
        keyRequestParameters as HashMap<String?, String?>?
      )
      requestHandler?.post(MSG_KEYS, Assertions.checkNotNull(currentKeyRequest)!!, allowRetry)
    } catch (e: Exception) {
      onKeysError(e, /* thrownByExoMediaDrm= */ true)
    }
  }

  private fun onKeyResponse(request: Any?, response: Any?) {
    if (request != currentKeyRequest || !isOpen()) {
      return
    }
    currentKeyRequest = null

    if (response is Exception) {
      onKeysError(response, /* thrownByExoMediaDrm= */ false)
      return
    }

    try {
      val responseData = response as ByteArray
      if (mode == DefaultDrmSessionManager.MODE_RELEASE) {
        mediaDrm.provideKeyResponse(Util.castNonNull(offlineLicenseKeySetId), responseData)
        dispatchEvent { it.drmKeysRemoved() }
      } else {
        val keySetId = mediaDrm.provideKeyResponse(sessionId!!, responseData)
        if ((mode == DefaultDrmSessionManager.MODE_DOWNLOAD ||
            (mode == DefaultDrmSessionManager.MODE_PLAYBACK && offlineLicenseKeySetId != null)) &&
          keySetId != null &&
          keySetId.isNotEmpty()
        ) {
          offlineLicenseKeySetId = keySetId
        }
        state = DrmSession.STATE_OPENED_WITH_KEYS
        dispatchEvent { it.drmKeysLoaded() }
      }
    } catch (e: Exception) {
      onKeysError(e, /* thrownByExoMediaDrm= */ true)
    }
  }

  private fun onKeysRequired() {
    if (mode == DefaultDrmSessionManager.MODE_PLAYBACK && state == DrmSession.STATE_OPENED_WITH_KEYS) {
      Util.castNonNull(sessionId)
      doLicense(/* allowRetry= */ false)
    }
  }

  private fun onKeysError(e: Exception, thrownByExoMediaDrm: Boolean) {
    if (e is NotProvisionedException) {
      provisioningManager.provisionRequired(this)
    } else {
      onError(
        e,
        if (thrownByExoMediaDrm) DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM
        else DrmUtil.ERROR_SOURCE_LICENSE_ACQUISITION
      )
    }
  }

  private fun onError(e: Exception, @DrmUtil.ErrorSource errorSource: Int) {
    lastException =
      DrmSessionException(e, DrmUtil.getErrorCodeForMediaDrmException(e, errorSource))
    Log.e(TAG, "DRM session error", e)
    dispatchEvent { it.drmSessionManagerError(e) }
    if (state != DrmSession.STATE_OPENED_WITH_KEYS) {
      state = DrmSession.STATE_ERROR
    }
  }

  private fun isOpen(): Boolean {
    return state == DrmSession.STATE_OPENED || state == DrmSession.STATE_OPENED_WITH_KEYS
  }

  private fun dispatchEvent(action: (DrmSessionEventListener.EventDispatcher) -> Unit) {
    for (eventDispatcher in eventDispatchers.elementSet()) {
      action(eventDispatcher)
    }
  }

  // Internal classes.

  @SuppressLint("HandlerLeak")
  inner class ResponseHandler(looper: Looper) : Handler(looper) {

    override fun handleMessage(msg: Message) {
      @Suppress("UNCHECKED_CAST")
      val requestAndResponse = msg.obj as Pair<Any, Any>
      val request = requestAndResponse.first
      val response = requestAndResponse.second
      when (msg.what) {
        MSG_PROVISION -> onProvisionResponse(request, response)
        MSG_KEYS -> onKeyResponse(request, response)
        else -> {}
      }
    }
  }

  @SuppressLint("HandlerLeak")
  private inner class RequestHandler(backgroundLooper: Looper) : Handler(backgroundLooper) {

    @GuardedBy("this")
    private var isReleased: Boolean = false

    fun post(what: Int, request: Any, allowRetry: Boolean) {
      val requestTask =
        RequestTask(
          LoadEventInfo.getNewId(),
          allowRetry,
          /* startTimeMs= */ SystemClock.elapsedRealtime(),
          request
        )
      obtainMessage(what, requestTask).sendToTarget()
    }

    override fun handleMessage(msg: Message) {
      val requestTask = msg.obj as RequestTask
      var response: Any?
      try {
        response = when (msg.what) {
          MSG_PROVISION -> callback.executeProvisionRequest(uuid, requestTask.request as ProvisionRequest)
          MSG_KEYS -> callback.executeKeyRequest(uuid, requestTask.request as KeyRequest)
          else -> throw RuntimeException()
        }
      } catch (e: MediaDrmCallbackException) {
        if (maybeRetryRequest(msg, e)) {
          return
        }
        response = e
      } catch (e: Exception) {
        Log.w(TAG, "Key/provisioning request produced an unexpected exception. Not retrying.", e)
        response = e
      }
      loadErrorHandlingPolicy.onLoadTaskConcluded(requestTask.taskId)
      synchronized(this) {
        if (!isReleased) {
          responseHandler
            .obtainMessage(msg.what, Pair.create(requestTask.request, response))
            .sendToTarget()
        }
      }
    }

    private fun maybeRetryRequest(originalMsg: Message, exception: MediaDrmCallbackException): Boolean {
      val requestTask = originalMsg.obj as RequestTask
      if (!requestTask.allowRetry) {
        return false
      }
      requestTask.errorCount++
      if (requestTask.errorCount >
        loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_DRM)
      ) {
        return false
      }
      val loadEventInfo =
        LoadEventInfo(
          requestTask.taskId,
          exception.dataSpec,
          exception.uriAfterRedirects,
          exception.responseHeaders,
          SystemClock.elapsedRealtime(),
          /* loadDurationMs= */ SystemClock.elapsedRealtime() - requestTask.startTimeMs,
          exception.bytesLoaded
        )
      val mediaLoadData = MediaLoadData(C.DATA_TYPE_DRM)
      val loadErrorCause: IOException =
        if (exception.cause is IOException) exception.cause as IOException
        else UnexpectedDrmSessionException(exception.cause)
      val retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
          LoadErrorInfo(
            loadEventInfo, mediaLoadData, loadErrorCause, requestTask.errorCount
          )
        )
      if (retryDelayMs == C.TIME_UNSET) {
        return false
      }
      synchronized(this) {
        if (!isReleased) {
          sendMessageDelayed(Message.obtain(originalMsg), retryDelayMs)
          return true
        }
      }
      return false
    }

    @Synchronized
    fun release() {
      removeCallbacksAndMessages(null)
      isReleased = true
    }
  }

  private class RequestTask(
    @JvmField val taskId: Long,
    @JvmField val allowRetry: Boolean,
    @JvmField val startTimeMs: Long,
    @JvmField val request: Any
  ) {
    @JvmField var errorCount: Int = 0
  }

  companion object {
    private const val TAG = "DefaultDrmSession"

    private const val MSG_PROVISION = 0
    private const val MSG_KEYS = 1
    private const val MAX_LICENSE_DURATION_TO_RENEW_SECONDS = 60
  }
}
