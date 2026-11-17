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

import androidx.annotation.IntDef

/** Thrown when the requested DRM scheme is not supported.  */
class UnsupportedDrmException : Exception {
    /**
     * The reason for the exception. One of [.REASON_UNSUPPORTED_SCHEME] or [ ][.REASON_INSTANTIATION_ERROR].
     */
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
        AnnotationTarget.CLASS,
        AnnotationTarget.TYPE,
        AnnotationTarget.TYPE_PARAMETER
    )
    @IntDef([REASON_UNSUPPORTED_SCHEME, REASON_INSTANTIATION_ERROR])
    annotation class Reason

    /** Either [.REASON_UNSUPPORTED_SCHEME] or [.REASON_INSTANTIATION_ERROR].  */
    @Reason
    val reason: @Reason Int

    /**
     * @param reason [.REASON_UNSUPPORTED_SCHEME] or [.REASON_INSTANTIATION_ERROR].
     */
    constructor(@Reason reason: @Reason Int) {
        this.reason = reason
    }

    /**
     * @param reason [.REASON_UNSUPPORTED_SCHEME] or [.REASON_INSTANTIATION_ERROR].
     * @param cause The cause of this exception.
     */
    constructor(@Reason reason: @Reason Int, cause: Exception?) : super(cause) {
        this.reason = reason
    }

    companion object {
        /** The requested DRM scheme is unsupported by the device.  */
        const val REASON_UNSUPPORTED_SCHEME: Int = 1

        /**
         * There device advertises support for the requested DRM scheme, but there was an error
         * instantiating it. The cause can be retrieved using [.getCause].
         */
        const val REASON_INSTANTIATION_ERROR: Int = 2
    }
}
