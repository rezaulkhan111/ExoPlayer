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
package com.google.android.exoplayer2.drm

import android.annotation.SuppressLint
import android.media.DeniedByServerException
import android.media.MediaDrm
import android.media.MediaDrmResetException
import android.media.NotProvisionedException
import androidx.annotation.DoNotInline
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.util.Util
import kotlin.annotation.MustBeDocumented

/** DRM-related utility methods. */
object DrmUtil {

  /** Identifies the operation which caused a DRM-related error. */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.TYPE,
  )
  @IntDef(
    ERROR_SOURCE_EXO_MEDIA_DRM,
    ERROR_SOURCE_LICENSE_ACQUISITION,
    ERROR_SOURCE_PROVISIONING,
  )
  annotation class ErrorSource

  /** Corresponds to failures caused by an [ExoMediaDrm] method call. */
  const val ERROR_SOURCE_EXO_MEDIA_DRM = 1

  /** Corresponds to failures caused by an operation related to obtaining DRM licenses. */
  const val ERROR_SOURCE_LICENSE_ACQUISITION = 2

  /** Corresponds to failures caused by an operation related to provisioning the device. */
  const val ERROR_SOURCE_PROVISIONING = 3

  /**
   * Returns the [PlaybackException.ErrorCode] that corresponds to the given DRM-related
   * exception.
   *
   * @param exception The DRM-related exception for which to obtain a corresponding [PlaybackException.ErrorCode].
   * @param errorSource The [ErrorSource] for the given `exception`.
   * @return The [PlaybackException.ErrorCode] that corresponds to the given DRM-related
   *     exception.
   */
  @JvmStatic
  @PlaybackException.ErrorCode
  @SuppressLint("NewApi")
  fun getErrorCodeForMediaDrmException(
    exception: Exception,
    @ErrorSource errorSource: Int,
  ): Int {
    if ((Util.SDK_INT >= 21) && Api21.isMediaDrmStateException(exception)) {
      return Api21.mediaDrmStateExceptionToErrorCode(exception)
    } else if ((Util.SDK_INT >= 23) && Api23.isMediaDrmResetException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
    } else if ((Util.SDK_INT >= 18) && Api18.isNotProvisionedException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
    } else if ((Util.SDK_INT >= 18) && Api18.isDeniedByServerException(exception)) {
      return PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
    } else if (exception is UnsupportedDrmException) {
      return PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED
    } else if (exception is DefaultDrmSessionManager.MissingSchemeDataException) {
      return PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
    } else if (exception is KeysExpiredException) {
      return PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
    } else if (errorSource == ERROR_SOURCE_EXO_MEDIA_DRM) {
      // A MediaDrm exception was thrown but it was impossible to determine the cause. Because no
      // better diagnosis tools were provided, we treat this as a system error.
      return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
    } else if (errorSource == ERROR_SOURCE_LICENSE_ACQUISITION) {
      return PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
    } else if (errorSource == ERROR_SOURCE_PROVISIONING) {
      return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
    } else {
      // Should never happen.
      throw IllegalArgumentException()
    }
  }

  // Internal classes.

  @RequiresApi(18)
  private object Api18 {
    @DoNotInline
    @JvmStatic
    fun isNotProvisionedException(throwable: Throwable?): Boolean {
      return throwable is NotProvisionedException
    }

    @DoNotInline
    @JvmStatic
    fun isDeniedByServerException(throwable: Throwable?): Boolean {
      return throwable is DeniedByServerException
    }
  }

  @RequiresApi(21)
  private object Api21 {
    @DoNotInline
    @JvmStatic
    fun isMediaDrmStateException(throwable: Throwable?): Boolean {
      return throwable is MediaDrm.MediaDrmStateException
    }

    @DoNotInline
    @JvmStatic
    @PlaybackException.ErrorCode
    fun mediaDrmStateExceptionToErrorCode(throwable: Throwable?): Int {
      val diagnosticsInfo = (throwable as MediaDrm.MediaDrmStateException).diagnosticInfo
      val drmErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo)
      return Util.getErrorCodeForMediaDrmErrorCode(drmErrorCode)
    }
  }

  @RequiresApi(23)
  private object Api23 {
    @DoNotInline
    @JvmStatic
    fun isMediaDrmResetException(throwable: Throwable?): Boolean {
      return throwable is MediaDrmResetException
    }
  }
}
