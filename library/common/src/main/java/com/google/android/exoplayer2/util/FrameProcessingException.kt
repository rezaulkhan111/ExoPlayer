/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.util

import com.google.android.exoplayer2.*
import java.lang.Exception

/** Thrown when an exception occurs while applying effects to video frames.  */
class FrameProcessingException : Exception {
    /**
     * The microsecond timestamp of the frame being processed while the exception occurred or [ ][C.TIME_UNSET] if unknown.
     */
    val presentationTimeUs: Long
    /**
     * Creates an instance.
     *
     * @param message The detail message for this exception.
     * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
     */
    /**
     * Creates an instance.
     *
     * @param message The detail message for this exception.
     */
    @JvmOverloads
    constructor(message: String?, presentationTimeUs: Long =  /* presentationTimeUs= */C.TIME_UNSET) : super(message) {
        this.presentationTimeUs = presentationTimeUs
    }
    /**
     * Creates an instance.
     *
     * @param message The detail message for this exception.
     * @param cause The cause of this exception.
     * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
     */
    /**
     * Creates an instance.
     *
     * @param message The detail message for this exception.
     * @param cause The cause of this exception.
     */
    @JvmOverloads
    constructor(message: String?, cause: Throwable?, presentationTimeUs: Long =  /* presentationTimeUs= */C.TIME_UNSET) : super(message, cause) {
        this.presentationTimeUs = presentationTimeUs
    }
    /**
     * Creates an instance.
     *
     * @param cause The cause of this exception.
     * @param presentationTimeUs The timestamp of the frame for which the exception occurred.
     */
    /**
     * Creates an instance.
     *
     * @param cause The cause of this exception.
     */
    @JvmOverloads
    constructor(cause: Throwable?, presentationTimeUs: Long =  /* presentationTimeUs= */C.TIME_UNSET) : super(cause) {
        this.presentationTimeUs = presentationTimeUs
    }

    companion object {
        /**
         * Wraps the given exception in a `FrameProcessingException` with the given timestamp if it
         * is not already a `FrameProcessingException` and returns the exception otherwise.
         */
        /**
         * Wraps the given exception in a `FrameProcessingException` if it is not already a `FrameProcessingException` and returns the exception otherwise.
         */
        @JvmOverloads
        fun from(exception: Exception?, presentationTimeUs: Long =  /* presentationTimeUs= */C.TIME_UNSET): FrameProcessingException {
            return if (exception is FrameProcessingException) {
                exception
            } else {
                FrameProcessingException(exception, presentationTimeUs)
            }
        }
    }
}