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
package com.google.android.exoplayer2.drm

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import java.util.UUID

/** A [DrmSession] that's in a terminal error state. */
class ErrorStateDrmSession(error: DrmSessionException) : DrmSession {

  private val error: DrmSessionException = checkNotNull(error)!!

  override fun getState(): Int {
    return DrmSession.STATE_ERROR
  }

  override fun playClearSamplesWithoutKeys(): Boolean {
    return false
  }

  override fun getError(): DrmSessionException {
    return error
  }

  override fun getSchemeUuid(): UUID {
    return C.UUID_NIL
  }

  override fun getCryptoConfig(): CryptoConfig? {
    return null
  }

  override fun queryKeyStatus(): Map<String?, String?>? {
    return null
  }

  override fun getOfflineLicenseKeySetId(): ByteArray? {
    return null
  }

  override fun requiresSecureDecoder(mimeType: String?): Boolean {
    return false
  }

  override fun acquire(eventDispatcher: DrmSessionEventListener.EventDispatcher?) {
    // Do nothing.
  }

  override fun release(eventDispatcher: DrmSessionEventListener.EventDispatcher?) {
    // Do nothing.
  }
}
