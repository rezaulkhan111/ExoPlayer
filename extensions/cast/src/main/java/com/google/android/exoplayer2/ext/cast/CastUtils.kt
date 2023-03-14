/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.cast

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaTrack

/** Utility methods for Cast integration.  */ /* package */
internal object CastUtils {
    /** The duration returned by [MediaInfo.getStreamDuration] for live streams.  */ // TODO: Remove once [Internal ref: b/171657375] is fixed.
    private const val LIVE_STREAM_DURATION: Long = -1000

    /**
     * Returns the duration in microseconds advertised by a media info, or [C.TIME_UNSET] if
     * unknown or not applicable.
     *
     * @param mediaInfo The media info to get the duration from.
     * @return The duration in microseconds, or [C.TIME_UNSET] if unknown or not applicable.
     */
    fun getStreamDurationUs(mediaInfo: MediaInfo?): Long {
        if (mediaInfo == null) {
            return C.TIME_UNSET
        }
        val durationMs = mediaInfo.streamDuration
        return if (durationMs != MediaInfo.UNKNOWN_DURATION && durationMs != LIVE_STREAM_DURATION) Util.msToUs(durationMs) else C.TIME_UNSET
    }

    /**
     * Returns a descriptive log string for the given `statusCode`, or "Unknown." if not one of
     * [CastStatusCodes].
     *
     * @param statusCode A Cast API status code.
     * @return A descriptive log string for the given `statusCode`, or "Unknown." if not one of
     * [CastStatusCodes].
     */
    fun getLogString(statusCode: Int): String {
        return when (statusCode) {
            CastStatusCodes.APPLICATION_NOT_FOUND -> "A requested application could not be found."
            CastStatusCodes.APPLICATION_NOT_RUNNING -> "A requested application is not currently running."
            CastStatusCodes.AUTHENTICATION_FAILED -> "Authentication failure."
            CastStatusCodes.CANCELED -> ("An in-progress request has been canceled, most likely because another action has "
                    + "preempted it.")
            CastStatusCodes.ERROR_SERVICE_CREATION_FAILED -> "The Cast Remote Display service could not be created."
            CastStatusCodes.ERROR_SERVICE_DISCONNECTED -> "The Cast Remote Display service was disconnected."
            CastStatusCodes.FAILED -> "The in-progress request failed."
            CastStatusCodes.INTERNAL_ERROR -> "An internal error has occurred."
            CastStatusCodes.INTERRUPTED -> "A blocking call was interrupted while waiting and did not run to completion."
            CastStatusCodes.INVALID_REQUEST -> "An invalid request was made."
            CastStatusCodes.MESSAGE_SEND_BUFFER_TOO_FULL -> ("A message could not be sent because there is not enough room in the send buffer at "
                    + "this time.")
            CastStatusCodes.MESSAGE_TOO_LARGE -> "A message could not be sent because it is too large."
            CastStatusCodes.NETWORK_ERROR -> "Network I/O error."
            CastStatusCodes.NOT_ALLOWED -> "The request was disallowed and could not be completed."
            CastStatusCodes.REPLACED -> ("The request's progress is no longer being tracked because another request of the "
                    + "same type has been made before the first request completed.")
            CastStatusCodes.SUCCESS -> "Success."
            CastStatusCodes.TIMEOUT -> "An operation has timed out."
            CastStatusCodes.UNKNOWN_ERROR -> "An unknown, unexpected error has occurred."
            else -> CastStatusCodes.getStatusCodeString(statusCode)
        }
    }

    /**
     * Creates a [Format] instance containing all information contained in the given [ ] object.
     *
     * @param mediaTrack The [MediaTrack].
     * @return The equivalent [Format].
     */
    fun mediaTrackToFormat(mediaTrack: MediaTrack): Format {
        return Format.Builder()
                .setId(mediaTrack.contentId)
                .setContainerMimeType(mediaTrack.contentType)
                .setLanguage(mediaTrack.language)
                .build()
    }
}