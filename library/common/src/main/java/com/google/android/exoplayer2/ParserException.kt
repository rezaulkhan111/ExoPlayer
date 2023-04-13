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
package com.google.android.exoplayer2

import com.google.android.exoplayer2.*
import java.io.IOException

/** Thrown when an error occurs parsing media data and metadata.  */
open class ParserException protected constructor(
    message: String?,
    cause: Throwable?,
    /**
     * Whether the parsing error was caused by a bitstream not following the expected format. May be
     * false when a parser encounters a legal condition which it does not support.
     */
    val contentIsMalformed: Boolean,
    /** The [data type][DataType] of the parsed bitstream.  */
    val dataType: @C.DataType Int
) : IOException(message, cause) {
    companion object {
        /**
         * Creates a new instance for which [.contentIsMalformed] is true and [.dataType] is
         * [C.DATA_TYPE_UNKNOWN].
         *
         * @param message See [.getMessage].
         * @param cause See [.getCause].
         * @return The created instance.
         */
        fun createForMalformedDataOfUnknownType(
            message: String?, cause: Throwable?
        ): ParserException {
            return com.google.android.exoplayer2.ParserException(
                message,
                cause,  /* contentIsMalformed= */
                true,
                C.DATA_TYPE_UNKNOWN
            )
        }

        /**
         * Creates a new instance for which [.contentIsMalformed] is true and [.dataType] is
         * [C.DATA_TYPE_MEDIA].
         *
         * @param message See [.getMessage].
         * @param cause See [.getCause].
         * @return The created instance.
         */
        fun createForMalformedContainer(
            message: String?, cause: Throwable?
        ): ParserException {
            return com.google.android.exoplayer2.ParserException(
                message,
                cause,  /* contentIsMalformed= */
                true,
                C.DATA_TYPE_MEDIA
            )
        }

        /**
         * Creates a new instance for which [.contentIsMalformed] is true and [.dataType] is
         * [C.DATA_TYPE_MANIFEST].
         *
         * @param message See [.getMessage].
         * @param cause See [.getCause].
         * @return The created instance.
         */
        fun createForMalformedManifest(
            message: String?, cause: Throwable?
        ): ParserException {
            return com.google.android.exoplayer2.ParserException(
                message, cause,  /* contentIsMalformed= */true, C.DATA_TYPE_MANIFEST
            )
        }

        /**
         * Creates a new instance for which [.contentIsMalformed] is false and [.dataType] is
         * [C.DATA_TYPE_MANIFEST].
         *
         * @param message See [.getMessage].
         * @param cause See [.getCause].
         * @return The created instance.
         */
        fun createForManifestWithUnsupportedFeature(
            message: String?, cause: Throwable?
        ): ParserException {
            return com.google.android.exoplayer2.ParserException(
                message, cause,  /* contentIsMalformed= */false, C.DATA_TYPE_MANIFEST
            )
        }

        /**
         * Creates a new instance for which [.contentIsMalformed] is false and [.dataType] is
         * [C.DATA_TYPE_MEDIA].
         *
         * @param message See [.getMessage].
         * @return The created instance.
         */
        fun createForUnsupportedContainerFeature(message: String?): ParserException {
            return com.google.android.exoplayer2.ParserException(
                message,  /* cause= */null,  /* contentIsMalformed= */false, C.DATA_TYPE_MEDIA
            )
        }
    }
}