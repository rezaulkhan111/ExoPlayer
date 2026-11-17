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

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.util.Util
import java.util.UUID

/**
 * A [CryptoConfig] for [C.CRYPTO_TYPE_FRAMEWORK]. Contains the necessary information to
 * build or update a framework [android.media.MediaCrypto] that can be used to configure a [android.media.MediaCodec].
 *
 * @param uuid The DRM scheme UUID.
 * @param sessionId The DRM session id.
 * @param forceAllowInsecureDecoderComponents Whether to allow use of insecure decoder components
 *     even if the underlying platform says otherwise.
 */
class FrameworkCryptoConfig(
  @JvmField val uuid: UUID,
  @JvmField val sessionId: ByteArray,
  @JvmField val forceAllowInsecureDecoderComponents: Boolean,
) : CryptoConfig {

  companion object {
    /**
     * Whether the device needs keys to have been loaded into the [DrmSession] before codec
     * configuration.
     */
    @JvmField
    val WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC: Boolean =
      (("Amazon" == Util.MANUFACTURER) &&
        (("AFTM" == Util.MODEL) || ("AFTB" == Util.MODEL)))
  }
}
