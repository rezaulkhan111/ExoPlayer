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
import android.media.ResourceBusyException
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.UUID

/**
 * A [DrmSessionManager] that supports playbacks using [ExoMediaDrm].
 *
 * <p>This implementation supports pre-acquisition of sessions using [preacquireSession].
 */
@RequiresApi(18)
class DefaultDrmSessionManager private constructor(
  private val uuid: UUID,
  private val exoMediaDrmProvider: ExoMediaDrm.Provider,
  /* package */ val callback: MediaDrmCallback,
  private val keyRequestParameters: HashMap<String, String>,
  private val multiSession: Boolean,
  private val useDrmSessionsForClearContentTrackTypes: IntArray,
  private val playClearSamplesWithoutKeys: Boolean,
  private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
  private val sessionKeepaliveMs: Long
) : DrmSessionManager {

  /**
   * Builder for [DefaultDrmSessionManager] instances.
   *
   * <p>See [Builder] for the list of default values.
   */
  class Builder {

    private val keyRequestParameters: HashMap<String, String> = HashMap()
    private var uuid: UUID = C.WIDEVINE_UUID
    private var exoMediaDrmProvider: ExoMediaDrm.Provider = FrameworkMediaDrm.DEFAULT_PROVIDER
    private var multiSession: Boolean = false
    private var useDrmSessionsForClearContentTrackTypes: IntArray = IntArray(0)
    private var playClearSamplesWithoutKeys: Boolean = false
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy()
    private var sessionKeepaliveMs: Long = DEFAULT_SESSION_KEEPALIVE_MS

    /**
     * Sets the key request parameters to pass as the last argument to
     * [ExoMediaDrm.getKeyRequest]. May be null if not parameters need to be passed.
     *
     * <p>Custom data for PlayReady should be set under [PLAYREADY_CUSTOM_DATA_KEY].
     *
     * @param keyRequestParameters A map with parameters.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setKeyRequestParameters(keyRequestParameters: Map<String, String>?): Builder {
      this.keyRequestParameters.clear()
      if (keyRequestParameters != null) {
        this.keyRequestParameters.putAll(keyRequestParameters)
      }
      return this
    }

    /**
     * Sets the UUID of the DRM scheme and the [ExoMediaDrm.Provider] to use.
     *
     * @param uuid The UUID of the DRM scheme.
     * @param exoMediaDrmProvider The [ExoMediaDrm.Provider].
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setUuidAndExoMediaDrmProvider(
      uuid: UUID, exoMediaDrmProvider: ExoMediaDrm.Provider
    ): Builder {
      this.uuid = Assertions.checkNotNull(uuid)!!
      this.exoMediaDrmProvider = Assertions.checkNotNull(exoMediaDrmProvider)!!
      return this
    }

    /**
     * Sets whether this session manager is allowed to acquire multiple simultaneous sessions.
     *
     * <p>Users should pass false when a single key request will obtain all keys required to decrypt
     * the associated content. `multiSession` is required when content uses key rotation.
     *
     * @param multiSession Whether this session manager is allowed to acquire multiple simultaneous
     *     sessions.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setMultiSession(multiSession: Boolean): Builder {
      this.multiSession = multiSession
      return this
    }

    /**
     * Sets whether this session manager should attach [DrmSession]s to the clear
     * sections of the media content.
     *
     * <p>Using [DrmSession]s for clear content avoids the recreation of decoders
     * when transitioning between clear and encrypted sections of content.
     *
     * @param useDrmSessionsForClearContentTrackTypes The track types ([C.TRACK_TYPE_AUDIO]
     *     and/or [C.TRACK_TYPE_VIDEO]) for which to use a [DrmSession] regardless of
     *     whether the content is clear or encrypted.
     * @return This builder.
     * @throws IllegalArgumentException If `useDrmSessionsForClearContentTrackTypes` contains
     *     track types other than [C.TRACK_TYPE_AUDIO] and [C.TRACK_TYPE_VIDEO].
     */
    @CanIgnoreReturnValue
    fun setUseDrmSessionsForClearContent(
      vararg useDrmSessionsForClearContentTrackTypes: Int
    ): Builder {
      for (trackType in useDrmSessionsForClearContentTrackTypes) {
        Assertions.checkArgument(trackType == C.TRACK_TYPE_VIDEO || trackType == C.TRACK_TYPE_AUDIO)
      }
      this.useDrmSessionsForClearContentTrackTypes =
        useDrmSessionsForClearContentTrackTypes.clone()
      return this
    }

    /**
     * Sets whether clear samples within protected content should be played when keys for the
     * encrypted part of the content have yet to be loaded.
     *
     * @param playClearSamplesWithoutKeys Whether clear samples within protected content should be
     *     played when keys for the encrypted part of the content have yet to be loaded.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setPlayClearSamplesWithoutKeys(playClearSamplesWithoutKeys: Boolean): Builder {
      this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys
      return this
    }

    /**
     * Sets the [LoadErrorHandlingPolicy] for key and provisioning requests.
     *
     * @param loadErrorHandlingPolicy A [LoadErrorHandlingPolicy].
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): Builder {
      this.loadErrorHandlingPolicy = Assertions.checkNotNull(loadErrorHandlingPolicy)!!
      return this
    }

    /**
     * Sets the time to keep [DrmSession]s alive when they're not in use.
     *
     * <p>It can be useful to keep sessions alive during playback of short clear sections of media
     * (e.g. ad breaks) to avoid opening new DRM sessions (and re-requesting keys) at the transition
     * back into secure content. This assumes the secure sections before and after the clear section
     * are encrypted with the same keys.
     *
     * <p>Defaults to [DEFAULT_SESSION_KEEPALIVE_MS]. Pass [C.TIME_UNSET] to disable
     * keep-alive.
     *
     * @param sessionKeepaliveMs The time to keep [DrmSession]s alive before fully releasing,
     *     in milliseconds. Must be &gt; 0 or [C.TIME_UNSET] to disable keep-alive.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setSessionKeepaliveMs(sessionKeepaliveMs: Long): Builder {
      Assertions.checkArgument(sessionKeepaliveMs > 0 || sessionKeepaliveMs == C.TIME_UNSET)
      this.sessionKeepaliveMs = sessionKeepaliveMs
      return this
    }

    /** Builds a [DefaultDrmSessionManager] instance. */
    fun build(mediaDrmCallback: MediaDrmCallback): DefaultDrmSessionManager {
      return DefaultDrmSessionManager(
        uuid,
        exoMediaDrmProvider,
        mediaDrmCallback,
        keyRequestParameters,
        multiSession,
        useDrmSessionsForClearContentTrackTypes,
        playClearSamplesWithoutKeys,
        loadErrorHandlingPolicy,
        sessionKeepaliveMs
      )
    }
  }

  /**
   * Signals that the [Format.drmInitData] passed to [acquireSession] does not contain
   * scheme data for the required UUID.
   */
  class MissingSchemeDataException(uuid: UUID) : Exception("Media does not support uuid: $uuid")

  /**
   * Determines the action to be done after a session acquired. One of [MODE_PLAYBACK],
   * [MODE_QUERY], [MODE_DOWNLOAD] or [MODE_RELEASE].
   */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(MODE_PLAYBACK, MODE_QUERY, MODE_DOWNLOAD, MODE_RELEASE)
  annotation class Mode

  private val provisioningManagerImpl: ProvisioningManagerImpl = ProvisioningManagerImpl()
  private val referenceCountListener: ReferenceCountListenerImpl = ReferenceCountListenerImpl()

  private val sessions: MutableList<DefaultDrmSession> = ArrayList()
  private val preacquiredSessionReferences: MutableSet<PreacquiredSessionReference> = Sets.newIdentityHashSet()
  private val keepaliveSessions: MutableSet<DefaultDrmSession> = Sets.newIdentityHashSet()

  private var prepareCallsCount: Int = 0
  private var exoMediaDrm: ExoMediaDrm? = null
  private var placeholderDrmSession: DefaultDrmSession? = null
  private var noMultiSessionDrmSession: DefaultDrmSession? = null
  private var playbackLooper: Looper? = null
  private var playbackHandler: Handler? = null
  private var mode: Int = MODE_PLAYBACK
  private var offlineLicenseKeySetId: ByteArray? = null
  private var playerId: PlayerId? = null

  @JvmField @Volatile var mediaDrmHandler: MediaDrmHandler? = null

  init {
    Assertions.checkArgument(C.COMMON_PSSH_UUID != uuid, "Use C.CLEARKEY_UUID instead")
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param exoMediaDrm An underlying [ExoMediaDrm] for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param keyRequestParameters An optional map of parameters to pass as the last argument to
   *     [ExoMediaDrm.getKeyRequest]. May be null.
   * @deprecated Use [Builder] instead.
   */
  @Deprecated("Use Builder instead.")
  constructor(
    uuid: UUID,
    exoMediaDrm: ExoMediaDrm,
    callback: MediaDrmCallback,
    keyRequestParameters: HashMap<String, String>?
  ) : this(
    uuid,
    exoMediaDrm,
    callback,
    keyRequestParameters,
    /* multiSession= */ false,
    INITIAL_DRM_REQUEST_RETRY_COUNT
  )

  /**
   * @param uuid The UUID of the drm scheme.
   * @param exoMediaDrm An underlying [ExoMediaDrm] for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param keyRequestParameters An optional map of parameters to pass as the last argument to
   *     [ExoMediaDrm.getKeyRequest]. May be null.
   * @param multiSession A boolean that specify whether multiple key session support is enabled.
   *     Default is false.
   * @deprecated Use [Builder] instead.
   */
  @Deprecated("Use Builder instead.")
  constructor(
    uuid: UUID,
    exoMediaDrm: ExoMediaDrm,
    callback: MediaDrmCallback,
    keyRequestParameters: HashMap<String, String>?,
    multiSession: Boolean
  ) : this(
    uuid,
    exoMediaDrm,
    callback,
    keyRequestParameters,
    multiSession,
    INITIAL_DRM_REQUEST_RETRY_COUNT
  )

  /**
   * @param uuid The UUID of the drm scheme.
   * @param exoMediaDrm An underlying [ExoMediaDrm] for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param keyRequestParameters An optional map of parameters to pass as the last argument to
   *     [ExoMediaDrm.getKeyRequest]. May be null.
   * @param multiSession A boolean that specify whether multiple key session support is enabled.
   *     Default is false.
   * @param initialDrmRequestRetryCount The number of times to retry for initial provisioning and
   *     key request before reporting error.
   * @deprecated Use [Builder] instead.
   */
  @Deprecated("Use Builder instead.")
  constructor(
    uuid: UUID,
    exoMediaDrm: ExoMediaDrm,
    callback: MediaDrmCallback,
    keyRequestParameters: HashMap<String, String>?,
    multiSession: Boolean,
    initialDrmRequestRetryCount: Int
  ) : this(
    uuid,
    ExoMediaDrm.AppManagedProvider(exoMediaDrm),
    callback,
    keyRequestParameters ?: HashMap(),
    multiSession,
    /* useDrmSessionsForClearContentTrackTypes= */ IntArray(0),
    /* playClearSamplesWithoutKeys= */ false,
    DefaultLoadErrorHandlingPolicy(initialDrmRequestRetryCount),
    DEFAULT_SESSION_KEEPALIVE_MS
  )

  /**
   * Sets the mode, which determines the role of sessions acquired from the instance. This must be
   * called before [acquireSession] is called.
   *
   * <p>By default, the mode is [MODE_PLAYBACK] and a streaming license is requested when
   * required.
   *
   * <p>`mode` must be one of these:
   *
   * <ul>
   *   <li>[MODE_PLAYBACK]: If `offlineLicenseKeySetId` is null then a streaming
   *       license is requested. Otherwise, the offline license is restored.
   *   <li>[MODE_QUERY]: `offlineLicenseKeySetId` cannot be null. The offline license
   *       is restored to allow its status to be queried.
   *   <li>[MODE_DOWNLOAD]: If `offlineLicenseKeySetId` is null then an offline license
   *       is requested. Otherwise, the offline license is renewed.
   *   <li>[MODE_RELEASE]: `offlineLicenseKeySetId` cannot be null. The offline license
   *       is released.
   * </ul>
   *
   * @param mode The mode to be set.
   * @param offlineLicenseKeySetId The key set id of the license to be used with the given mode.
   */
  fun setMode(@Mode mode: Int, offlineLicenseKeySetId: ByteArray?) {
    Assertions.checkState(sessions.isEmpty())
    if (mode == MODE_QUERY || mode == MODE_RELEASE) {
      Assertions.checkNotNull(offlineLicenseKeySetId)
    }
    this.mode = mode
    this.offlineLicenseKeySetId = offlineLicenseKeySetId
  }

  // DrmSessionManager implementation.

  override fun prepare() {
    if (prepareCallsCount++ != 0) {
      return
    }
    if (exoMediaDrm == null) {
      val exoMediaDrm = exoMediaDrmProvider.acquireExoMediaDrm(uuid)
      this.exoMediaDrm = exoMediaDrm
      exoMediaDrm.setOnEventListener(MediaDrmEventListener())
    } else if (sessionKeepaliveMs != C.TIME_UNSET) {
      for (i in sessions.indices) {
        sessions[i].acquire(/* eventDispatcher= */ null)
      }
    }
  }

  override fun release() {
    if (--prepareCallsCount != 0) {
      return
    }
    if (sessionKeepaliveMs != C.TIME_UNSET) {
      val sessions = ArrayList(this.sessions)
      for (i in sessions.indices) {
        sessions[i].release(/* eventDispatcher= */ null)
      }
    }
    releaseAllPreacquiredSessions()

    maybeReleaseMediaDrm()
  }

  override fun setPlayer(playbackLooper: Looper?, playerId: PlayerId?) {
    initPlaybackLooper(playbackLooper!!)
    this.playerId = playerId
  }

  override fun preacquireSession(
    eventDispatcher: DrmSessionEventListener.EventDispatcher?, format: Format?
  ): DrmSessionManager.DrmSessionReference {
    Assertions.checkState(prepareCallsCount > 0)
    Assertions.checkStateNotNull(playbackLooper)
    val preacquiredSessionReference = PreacquiredSessionReference(eventDispatcher)
    preacquiredSessionReference.acquire(format!!)
    return preacquiredSessionReference
  }

  override fun acquireSession(
    eventDispatcher: DrmSessionEventListener.EventDispatcher?, format: Format
  ): DrmSession? {
    Assertions.checkState(prepareCallsCount > 0)
    Assertions.checkStateNotNull(playbackLooper)
    return acquireSession(
      playbackLooper!!,
      eventDispatcher,
      format,
      /* shouldReleasePreacquiredSessionsBeforeRetrying= */ true
    )
  }

  private fun acquireSession(
    playbackLooper: Looper,
    eventDispatcher: DrmSessionEventListener.EventDispatcher?,
    format: Format,
    shouldReleasePreacquiredSessionsBeforeRetrying: Boolean
  ): DrmSession? {
    maybeCreateMediaDrmHandler(playbackLooper)

    if (format.drmInitData == null) {
      return maybeAcquirePlaceholderSession(
        MimeTypes.getTrackType(format.sampleMimeType),
        shouldReleasePreacquiredSessionsBeforeRetrying
      )
    }

    var schemeDatas: List<SchemeData>? = null
    if (offlineLicenseKeySetId == null) {
      schemeDatas = getSchemeDatas(format.drmInitData!!, uuid, false)
      if (schemeDatas.isEmpty()) {
        val error = MissingSchemeDataException(uuid)
        Log.e(TAG, "DRM error", error)
        eventDispatcher?.drmSessionManagerError(error)
        return ErrorStateDrmSession(
          DrmSessionException(error, PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR)
        )
      }
    }

    var session: DefaultDrmSession? = null
    if (!multiSession) {
      session = noMultiSessionDrmSession
    } else {
      for (existingSession in sessions) {
        if (Util.areEqual(existingSession.schemeDatas, schemeDatas)) {
          session = existingSession
          break
        }
      }
    }

    if (session == null) {
      session =
        createAndAcquireSessionWithRetry(
          schemeDatas,
          /* isPlaceholderSession= */ false,
          eventDispatcher,
          shouldReleasePreacquiredSessionsBeforeRetrying
        )
      if (!multiSession) {
        noMultiSessionDrmSession = session
      }
      sessions.add(session)
    } else {
      session.acquire(eventDispatcher)
    }

    return session
  }

  override fun getCryptoType(format: Format): Int {
    val cryptoType = Assertions.checkNotNull(exoMediaDrm)!!.getCryptoType()
    return if (format.drmInitData == null) {
      val trackType = MimeTypes.getTrackType(format.sampleMimeType)
      if (Util.linearSearch(useDrmSessionsForClearContentTrackTypes, trackType) != C.INDEX_UNSET)
        cryptoType
      else C.CRYPTO_TYPE_NONE
    } else {
      if (canAcquireSession(format.drmInitData!!)) cryptoType else C.CRYPTO_TYPE_UNSUPPORTED
    }
  }

  private fun maybeAcquirePlaceholderSession(
    trackType: Int, shouldReleasePreacquiredSessionsBeforeRetrying: Boolean
  ): DrmSession? {
    val exoMediaDrm = Assertions.checkNotNull(this.exoMediaDrm)!!
    val avoidPlaceholderDrmSessions =
      exoMediaDrm.getCryptoType() == C.CRYPTO_TYPE_FRAMEWORK &&
        FrameworkCryptoConfig.WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC
    if (avoidPlaceholderDrmSessions ||
      Util.linearSearch(useDrmSessionsForClearContentTrackTypes, trackType) == C.INDEX_UNSET ||
      exoMediaDrm.getCryptoType() == C.CRYPTO_TYPE_UNSUPPORTED
    ) {
      return null
    }
    val placeholderDrmSession = this.placeholderDrmSession
    if (placeholderDrmSession == null) {
      val newPlaceholderSession =
        createAndAcquireSessionWithRetry(
          /* schemeDatas= */ ImmutableList.of(),
          /* isPlaceholderSession= */ true,
          /* eventDispatcher= */ null,
          shouldReleasePreacquiredSessionsBeforeRetrying
        )
      sessions.add(newPlaceholderSession)
      this.placeholderDrmSession = newPlaceholderSession
      return newPlaceholderSession
    } else {
      placeholderDrmSession.acquire(/* eventDispatcher= */ null)
      return placeholderDrmSession
    }
  }

  private fun canAcquireSession(drmInitData: DrmInitData): Boolean {
    if (offlineLicenseKeySetId != null) {
      return true
    }
    val schemeDatas = getSchemeDatas(drmInitData, uuid, true)
    if (schemeDatas.isEmpty()) {
      if (drmInitData.schemeDataCount == 1 && drmInitData.get(0).matches(C.COMMON_PSSH_UUID)) {
        Log.w(
          TAG, "DrmInitData only contains common PSSH SchemeData. Assuming support for: $uuid"
        )
      } else {
        return false
      }
    }
    val schemeType = drmInitData.schemeType
    if (schemeType == null || C.CENC_TYPE_cenc == schemeType) {
      return true
    } else if (C.CENC_TYPE_cbcs == schemeType) {
      return Util.SDK_INT >= 25
    } else if (C.CENC_TYPE_cbc1 == schemeType || C.CENC_TYPE_cens == schemeType) {
      return false
    }
    return true
  }

  @Synchronized
  private fun initPlaybackLooper(playbackLooper: Looper) {
    if (this.playbackLooper == null) {
      this.playbackLooper = playbackLooper
      this.playbackHandler = Handler(playbackLooper)
    } else {
      Assertions.checkState(this.playbackLooper == playbackLooper)
      Assertions.checkNotNull(playbackHandler)
    }
  }

  private fun maybeCreateMediaDrmHandler(playbackLooper: Looper) {
    if (mediaDrmHandler == null) {
      mediaDrmHandler = MediaDrmHandler(playbackLooper)
    }
  }

  private fun createAndAcquireSessionWithRetry(
    schemeDatas: List<SchemeData>?,
    isPlaceholderSession: Boolean,
    eventDispatcher: DrmSessionEventListener.EventDispatcher?,
    shouldReleasePreacquiredSessionsBeforeRetrying: Boolean
  ): DefaultDrmSession {
    var session = createAndAcquireSession(schemeDatas, isPlaceholderSession, eventDispatcher)
    if (acquisitionFailedIndicatingResourceShortage(session) && keepaliveSessions.isNotEmpty()) {
      releaseAllKeepaliveSessions()
      undoAcquisition(session, eventDispatcher)
      session = createAndAcquireSession(schemeDatas, isPlaceholderSession, eventDispatcher)
    }

    if (acquisitionFailedIndicatingResourceShortage(session) &&
      shouldReleasePreacquiredSessionsBeforeRetrying &&
      preacquiredSessionReferences.isNotEmpty()
    ) {
      releaseAllPreacquiredSessions()
      if (keepaliveSessions.isNotEmpty()) {
        releaseAllKeepaliveSessions()
      }
      undoAcquisition(session, eventDispatcher)
      session = createAndAcquireSession(schemeDatas, isPlaceholderSession, eventDispatcher)
    }
    return session
  }

  private fun undoAcquisition(
    session: DrmSession, eventDispatcher: DrmSessionEventListener.EventDispatcher?
  ) {
    session.release(eventDispatcher)
    if (sessionKeepaliveMs != C.TIME_UNSET) {
      session.release(/* eventDispatcher= */ null)
    }
  }

  private fun releaseAllKeepaliveSessions() {
    val keepaliveSessions = ImmutableSet.copyOf(this.keepaliveSessions)
    for (keepaliveSession in keepaliveSessions) {
      keepaliveSession.release(/* eventDispatcher= */ null)
    }
  }

  private fun releaseAllPreacquiredSessions() {
    val preacquiredSessionReferences = ImmutableSet.copyOf(this.preacquiredSessionReferences)
    for (preacquiredSessionReference in preacquiredSessionReferences) {
      preacquiredSessionReference.release()
    }
  }

  private fun createAndAcquireSession(
    schemeDatas: List<SchemeData>?,
    isPlaceholderSession: Boolean,
    eventDispatcher: DrmSessionEventListener.EventDispatcher?
  ): DefaultDrmSession {
    Assertions.checkNotNull(exoMediaDrm)
    val playClearSamplesWithoutKeys = this.playClearSamplesWithoutKeys or isPlaceholderSession
    val session =
      DefaultDrmSession(
        uuid,
        exoMediaDrm!!,
        /* provisioningManager= */ provisioningManagerImpl,
        referenceCountListener,
        schemeDatas,
        mode,
        playClearSamplesWithoutKeys,
        isPlaceholderSession,
        offlineLicenseKeySetId,
        keyRequestParameters,
        callback,
        Assertions.checkNotNull(playbackLooper)!!,
        loadErrorHandlingPolicy,
        Assertions.checkNotNull(playerId)!!
      )
    session.acquire(eventDispatcher)
    if (sessionKeepaliveMs != C.TIME_UNSET) {
      session.acquire(/* eventDispatcher= */ null)
    }
    return session
  }

  private fun maybeReleaseMediaDrm() {
    if (exoMediaDrm != null &&
      prepareCallsCount == 0 &&
      sessions.isEmpty() &&
      preacquiredSessionReferences.isEmpty()
    ) {
      Assertions.checkNotNull(exoMediaDrm)!!.release()
      exoMediaDrm = null
    }
  }

  @SuppressLint("HandlerLeak")
  /* package */ inner class MediaDrmHandler(looper: Looper) : Handler(looper) {

    override fun handleMessage(msg: Message) {
      val sessionId = msg.obj as ByteArray? ?: return
      for (session in sessions) {
        if (session.hasSessionId(sessionId)) {
          session.onMediaDrmEvent(msg.what)
          return
        }
      }
    }
  }

  private class ProvisioningManagerImpl : DefaultDrmSession.ProvisioningManager {

    private val sessionsAwaitingProvisioning: HashSet<DefaultDrmSession> = HashSet()
    private var provisioningSession: DefaultDrmSession? = null

    override fun provisionRequired(session: DefaultDrmSession) {
      sessionsAwaitingProvisioning.add(session)
      if (provisioningSession != null) {
        return
      }
      provisioningSession = session
      session.provision()
    }

    override fun onProvisionCompleted() {
      provisioningSession = null
      val sessionsToNotify = ImmutableList.copyOf(sessionsAwaitingProvisioning)
      sessionsAwaitingProvisioning.clear()
      for (session in sessionsToNotify) {
        session.onProvisionCompleted()
      }
    }

    override fun onProvisionError(error: Exception, thrownByExoMediaDrm: Boolean) {
      provisioningSession = null
      val sessionsToNotify = ImmutableList.copyOf(sessionsAwaitingProvisioning)
      sessionsAwaitingProvisioning.clear()
      for (session in sessionsToNotify) {
        session.onProvisionError(error, thrownByExoMediaDrm)
      }
    }

    fun onSessionFullyReleased(session: DefaultDrmSession) {
      sessionsAwaitingProvisioning.remove(session)
      if (provisioningSession == session) {
        provisioningSession = null
        if (sessionsAwaitingProvisioning.isNotEmpty()) {
          provisioningSession = sessionsAwaitingProvisioning.iterator().next()
          provisioningSession!!.provision()
        }
      }
    }
  }

  private inner class ReferenceCountListenerImpl : DefaultDrmSession.ReferenceCountListener {

    override fun onReferenceCountIncremented(session: DefaultDrmSession, newReferenceCount: Int) {
      if (sessionKeepaliveMs != C.TIME_UNSET) {
        keepaliveSessions.remove(session)
        Assertions.checkNotNull(playbackHandler)!!.removeCallbacksAndMessages(session)
      }
    }

    override fun onReferenceCountDecremented(session: DefaultDrmSession, newReferenceCount: Int) {
      if (newReferenceCount == 1 && prepareCallsCount > 0 && sessionKeepaliveMs != C.TIME_UNSET) {
        keepaliveSessions.add(session)
        Assertions.checkNotNull(playbackHandler)!!
          .postAtTime(
            { session.release(/* eventDispatcher= */ null) },
            session,
            /* uptimeMillis= */ SystemClock.uptimeMillis() + sessionKeepaliveMs
          )
      } else if (newReferenceCount == 0) {
        sessions.remove(session)
        if (placeholderDrmSession == session) {
          placeholderDrmSession = null
        }
        if (noMultiSessionDrmSession == session) {
          noMultiSessionDrmSession = null
        }
        provisioningManagerImpl.onSessionFullyReleased(session)
        if (sessionKeepaliveMs != C.TIME_UNSET) {
          Assertions.checkNotNull(playbackHandler)!!.removeCallbacksAndMessages(session)
          keepaliveSessions.remove(session)
        }
      }
      maybeReleaseMediaDrm()
    }
  }

  private inner class MediaDrmEventListener : ExoMediaDrm.OnEventListener {

    override fun onEvent(
      mediaDrm: ExoMediaDrm?, sessionId: ByteArray?, event: Int, extra: Int, data: ByteArray?
    ) {
      Assertions.checkNotNull(mediaDrmHandler)!!.obtainMessage(event, sessionId).sendToTarget()
    }
  }

  private inner class PreacquiredSessionReference(
    private val eventDispatcher: DrmSessionEventListener.EventDispatcher?
  ) : DrmSessionManager.DrmSessionReference {

    private var session: DrmSession? = null
    private var isReleased: Boolean = false

    fun acquire(format: Format) {
      Assertions.checkNotNull(playbackHandler)!!
        .post {
          if (prepareCallsCount == 0 || isReleased) {
            return@post
          }
          this.session =
            acquireSession(
              Assertions.checkNotNull(playbackLooper)!!,
              eventDispatcher,
              format,
              /* shouldReleasePreacquiredSessionsBeforeRetrying= */ false
            )
          preacquiredSessionReferences.add(this)
        }
    }

    override fun release() {
      Util.postOrRun(
        Assertions.checkNotNull(playbackHandler)!!
      ) {
        if (isReleased) {
          return@postOrRun
        }
        session?.release(eventDispatcher)
        preacquiredSessionReferences.remove(this)
        isReleased = true
      }
    }
  }

  companion object {
    const val PLAYREADY_CUSTOM_DATA_KEY = "PRCustomData"
    const val MODE_PLAYBACK = 0
    const val MODE_QUERY = 1
    const val MODE_DOWNLOAD = 2
    const val MODE_RELEASE = 3
    const val INITIAL_DRM_REQUEST_RETRY_COUNT = 3
    const val DEFAULT_SESSION_KEEPALIVE_MS = 5 * 60 * C.MILLIS_PER_SECOND

    private const val TAG = "DefaultDrmSessionMgr"

    private fun acquisitionFailedIndicatingResourceShortage(session: DrmSession): Boolean {
      return session.getState() == DrmSession.STATE_ERROR &&
        (Util.SDK_INT < 19 || Assertions.checkNotNull(session.getError())!!.cause is ResourceBusyException)
    }

    private fun getSchemeDatas(
      drmInitData: DrmInitData, uuid: UUID, allowMissingData: Boolean
    ): List<SchemeData> {
      val matchingSchemeDatas = ArrayList<SchemeData>(drmInitData.schemeDataCount)
      for (i in 0 until drmInitData.schemeDataCount) {
        val schemeData = drmInitData.get(i)
        val uuidMatches =
          schemeData.matches(uuid) ||
            (C.CLEARKEY_UUID == uuid && schemeData.matches(C.COMMON_PSSH_UUID))
        if (uuidMatches && (schemeData.data != null || allowMissingData)) {
          matchingSchemeDatas.add(schemeData)
        }
      }
      return matchingSchemeDatas
    }
  }
}
