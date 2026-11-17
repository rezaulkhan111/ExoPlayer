/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.media.MediaDrmException
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.util.Util
import java.util.HashMap

/** An [ExoMediaDrm] that does not support any protection schemes. */
@RequiresApi(18)
class DummyExoMediaDrm : ExoMediaDrm {

  override fun setOnEventListener(listener: ExoMediaDrm.OnEventListener?) {
    // Do nothing.
  }

  override fun setOnKeyStatusChangeListener(listener: ExoMediaDrm.OnKeyStatusChangeListener?) {
    // Do nothing.
  }

  override fun setOnExpirationUpdateListener(listener: ExoMediaDrm.OnExpirationUpdateListener?) {
    // Do nothing.
  }

  @Throws(MediaDrmException::class)
  override fun openSession(): ByteArray {
    throw MediaDrmException("Attempting to open a session using a dummy ExoMediaDrm.")
  }

  override fun closeSession(sessionId: ByteArray?) {
    // Do nothing.
  }

  override fun getKeyRequest(
    scope: ByteArray?,
    schemeDatas: List<DrmInitData.SchemeData?>?,
    keyType: Int,
    optionalParameters: HashMap<String?, String?>?,
  ): ExoMediaDrm.KeyRequest {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun provideKeyResponse(scope: ByteArray?, response: ByteArray?): ByteArray? {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun getProvisionRequest(): ExoMediaDrm.ProvisionRequest {
    // Should not be invoked. No provision should be required.
    throw IllegalStateException()
  }

  override fun provideProvisionResponse(response: ByteArray?) {
    // Should not be invoked. No provision should be required.
    throw IllegalStateException()
  }

  override fun queryKeyStatus(sessionId: ByteArray?): Map<String?, String?> {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun requiresSecureDecoder(sessionId: ByteArray?, mimeType: String?): Boolean {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun acquire() {
    // Do nothing.
  }

  override fun release() {
    // Do nothing.
  }

  override fun restoreKeys(sessionId: ByteArray?, keySetId: ByteArray?) {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun getMetrics(): PersistableBundle? {
    return null
  }

  override fun getPropertyString(propertyName: String?): String {
    return ""
  }

  override fun getPropertyByteArray(propertyName: String?): ByteArray {
    return Util.EMPTY_BYTE_ARRAY
  }

  override fun setPropertyString(propertyName: String?, value: String?) {
    // Do nothing.
  }

  override fun setPropertyByteArray(propertyName: String?, value: ByteArray?) {
    // Do nothing.
  }

  override fun createCryptoConfig(sessionId: ByteArray?): CryptoConfig {
    // Should not be invoked. No session should exist.
    throw IllegalStateException()
  }

  override fun getCryptoType(): Int {
    return C.CRYPTO_TYPE_UNSUPPORTED
  }

  companion object {
    /** Returns a new instance. */
    @JvmStatic
    fun getInstance(): DummyExoMediaDrm {
      return DummyExoMediaDrm()
    }
  }
}
