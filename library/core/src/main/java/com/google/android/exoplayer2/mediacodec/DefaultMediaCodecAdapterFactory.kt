/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec

import android.annotation.SuppressLint
import androidx.annotation.IntDef
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.io.IOException
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.MustBeDocumented

/**
 * The default [MediaCodecAdapter.Factory].
 *
 * <p>By default, this factory [createAdapter] [AsynchronousMediaCodecAdapter]
 * instances on devices with API level &gt;= 31 (Android 12+). For devices with older API versions,
 * the default behavior is to create [SynchronousMediaCodecAdapter] instances. The factory
 * offers APIs to force the creation of [AsynchronousMediaCodecAdapter] (applicable for
 * devices with API &gt;= 23) or [SynchronousMediaCodecAdapter] instances.
 */
class DefaultMediaCodecAdapterFactory : MediaCodecAdapter.Factory {

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD)
  @IntDef(MODE_DEFAULT, MODE_ENABLED, MODE_DISABLED)
  private annotation class Mode

  @field:Mode
  private var asynchronousMode: Int = MODE_DEFAULT
  private var enableSynchronizeCodecInteractionsWithQueueing: Boolean = false

  /**
   * Forces this factory to always create [AsynchronousMediaCodecAdapter] instances, provided
   * the device API level is &gt;= 23. For devices with API level &lt; 23, the factory will create
   * [SynchronousMediaCodecAdapter] instances.
   *
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  fun forceEnableAsynchronous(): DefaultMediaCodecAdapterFactory {
    asynchronousMode = MODE_ENABLED
    return this
  }

  /**
   * Forces the factory to always create [SynchronousMediaCodecAdapter] instances.
   *
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  fun forceDisableAsynchronous(): DefaultMediaCodecAdapterFactory {
    asynchronousMode = MODE_DISABLED
    return this
  }

  /**
   * Enable synchronizing codec interactions with asynchronous buffer queueing.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enabled Whether codec interactions will be synchronized with asynchronous buffer
   *     queueing.
   */
  fun experimentalSetSynchronizeCodecInteractionsWithQueueingEnabled(
    enabled: Boolean,
  ) {
    enableSynchronizeCodecInteractionsWithQueueing = enabled
  }

  @SuppressLint("NewApi")
  @Throws(IOException::class)
  override fun createAdapter(
    configuration: MediaCodecAdapter.Configuration?,
  ): MediaCodecAdapter {
    if (Util.SDK_INT >= 23 &&
      (asynchronousMode == MODE_ENABLED ||
        (asynchronousMode == MODE_DEFAULT && Util.SDK_INT >= 31))
    ) {
      val trackType = MimeTypes.getTrackType(configuration!!.format!!.sampleMimeType)
      Log.i(
        TAG,
        "Creating an asynchronous MediaCodec adapter for track type " +
          Util.getTrackTypeString(trackType),
      )
      val factory =
        AsynchronousMediaCodecAdapter.Factory(
          trackType,
          enableSynchronizeCodecInteractionsWithQueueing,
        )
      return factory.createAdapter(configuration)
    }
    return SynchronousMediaCodecAdapter.Factory().createAdapter(configuration)
  }

  private companion object {
    private const val MODE_DEFAULT = 0
    private const val MODE_ENABLED = 1
    private const val MODE_DISABLED = 2

    private const val TAG = "DMCodecAdapterFactory"
  }
}
