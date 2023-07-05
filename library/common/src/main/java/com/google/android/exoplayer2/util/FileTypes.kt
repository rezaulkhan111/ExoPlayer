/*
 * Copyright 2020 The Android Open Source Project
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

import android.net.Uri
import androidx.annotation.IntDef
import com.google.common.annotations.VisibleForTesting
import java.lang.annotation.Documented

/** Defines common file type constants and helper methods.  */
object FileTypes {
    /** Unknown file type.  */
    const val UNKNOWN: Int = -1

    /** File type for the AC-3 and E-AC-3 formats.  */
    const val AC3: Int = 0

    /** File type for the AC-4 format.  */
    const val AC4: Int = 1

    /** File type for the ADTS format.  */
    const val ADTS: Int = 2

    /** File type for the AMR format.  */
    const val AMR: Int = 3

    /** File type for the FLAC format.  */
    const val FLAC: Int = 4

    /** File type for the FLV format.  */
    const val FLV: Int = 5

    /** File type for the Matroska and WebM formats.  */
    const val MATROSKA: Int = 6

    /** File type for the MP3 format.  */
    const val MP3: Int = 7

    /** File type for the MP4 format.  */
    const val MP4: Int = 8

    /** File type for the Ogg format.  */
    const val OGG: Int = 9

    /** File type for the MPEG-PS format.  */
    const val PS: Int = 10

    /** File type for the MPEG-TS format.  */
    const val TS: Int = 11

    /** File type for the WAV format.  */
    const val WAV: Int = 12

    /** File type for the WebVTT format.  */
    const val WEBVTT: Int = 13

    /** File type for the JPEG format.  */
    const val JPEG: Int = 14

    /** File type for the MIDI format.  */
    const val MIDI: Int = 15

    /** File type for the AVI format.  */
    const val AVI: Int = 16

    @VisibleForTesting /* package */
    val HEADER_CONTENT_TYPE: String = "Content-Type"
    private const val EXTENSION_AC3: String = ".ac3"
    private const val EXTENSION_EC3: String = ".ec3"
    private const val EXTENSION_AC4: String = ".ac4"
    private const val EXTENSION_ADTS: String = ".adts"
    private const val EXTENSION_AAC: String = ".aac"
    private const val EXTENSION_AMR: String = ".amr"
    private const val EXTENSION_FLAC: String = ".flac"
    private const val EXTENSION_FLV: String = ".flv"
    private const val EXTENSION_MID: String = ".mid"
    private const val EXTENSION_MIDI: String = ".midi"
    private const val EXTENSION_SMF: String = ".smf"
    private const val EXTENSION_PREFIX_MK: String = ".mk"
    private const val EXTENSION_WEBM: String = ".webm"
    private const val EXTENSION_PREFIX_OG: String = ".og"
    private const val EXTENSION_OPUS: String = ".opus"
    private const val EXTENSION_MP3: String = ".mp3"
    private const val EXTENSION_MP4: String = ".mp4"
    private const val EXTENSION_PREFIX_M4: String = ".m4"
    private const val EXTENSION_PREFIX_MP4: String = ".mp4"
    private const val EXTENSION_PREFIX_CMF: String = ".cmf"
    private const val EXTENSION_PS: String = ".ps"
    private const val EXTENSION_MPEG: String = ".mpeg"
    private const val EXTENSION_MPG: String = ".mpg"
    private const val EXTENSION_M2P: String = ".m2p"
    private const val EXTENSION_TS: String = ".ts"
    private const val EXTENSION_PREFIX_TS: String = ".ts"
    private const val EXTENSION_WAV: String = ".wav"
    private const val EXTENSION_WAVE: String = ".wave"
    private const val EXTENSION_VTT: String = ".vtt"
    private const val EXTENSION_WEBVTT: String = ".webvtt"
    private const val EXTENSION_JPG: String = ".jpg"
    private const val EXTENSION_JPEG: String = ".jpeg"
    private const val EXTENSION_AVI: String = ".avi"

    /** Returns the [Type] corresponding to the response headers provided.  */
    fun inferFileTypeFromResponseHeaders(responseHeaders: Map<String?, List<String?>?>): @FileTypes.Type Int {
        val contentTypes: List<String?>? = responseHeaders.get(HEADER_CONTENT_TYPE)
        val mimeType: String? = if (contentTypes == null || contentTypes.isEmpty()) null else contentTypes.get(0)
        return inferFileTypeFromMimeType(mimeType)
    }

    /**
     * Returns the [Type] corresponding to the MIME type provided.
     *
     *
     * Returns [.UNKNOWN] if the mime type is `null`.
     */
    fun inferFileTypeFromMimeType(mimeType: String?): @FileTypes.Type Int {
        var mimeType: String? = mimeType
        if (mimeType == null) {
            return UNKNOWN
        }
        mimeType = MimeTypes.normalizeMimeType(mimeType)
        when (mimeType) {
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> return AC3
            MimeTypes.AUDIO_AC4 -> return AC4
            MimeTypes.AUDIO_AMR, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB -> return AMR
            MimeTypes.AUDIO_FLAC -> return FLAC
            MimeTypes.VIDEO_FLV -> return FLV
            MimeTypes.AUDIO_MIDI -> return MIDI
            MimeTypes.VIDEO_MATROSKA, MimeTypes.AUDIO_MATROSKA, MimeTypes.VIDEO_WEBM, MimeTypes.AUDIO_WEBM, MimeTypes.APPLICATION_WEBM -> return MATROSKA
            MimeTypes.AUDIO_MPEG -> return MP3
            MimeTypes.VIDEO_MP4, MimeTypes.AUDIO_MP4, MimeTypes.APPLICATION_MP4 -> return MP4
            MimeTypes.AUDIO_OGG -> return OGG
            MimeTypes.VIDEO_PS -> return PS
            MimeTypes.VIDEO_MP2T -> return TS
            MimeTypes.AUDIO_WAV -> return WAV
            MimeTypes.TEXT_VTT -> return WEBVTT
            MimeTypes.IMAGE_JPEG -> return JPEG
            MimeTypes.VIDEO_AVI -> return AVI
            else -> return UNKNOWN
        }
    }

    /** Returns the [Type] corresponding to the [Uri] provided.  */
    fun inferFileTypeFromUri(uri: Uri): @FileTypes.Type Int {
        val filename: String? = uri.getLastPathSegment()
        if (filename == null) {
            return UNKNOWN
        } else if (filename.endsWith(EXTENSION_AC3) || filename.endsWith(EXTENSION_EC3)) {
            return AC3
        } else if (filename.endsWith(EXTENSION_AC4)) {
            return AC4
        } else if (filename.endsWith(EXTENSION_ADTS) || filename.endsWith(EXTENSION_AAC)) {
            return ADTS
        } else if (filename.endsWith(EXTENSION_AMR)) {
            return AMR
        } else if (filename.endsWith(EXTENSION_FLAC)) {
            return FLAC
        } else if (filename.endsWith(EXTENSION_FLV)) {
            return FLV
        } else if ((filename.endsWith(EXTENSION_MID) || filename.endsWith(EXTENSION_MIDI) || filename.endsWith(EXTENSION_SMF))) {
            return MIDI
        } else if ((filename.startsWith(EXTENSION_PREFIX_MK,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_MK.length + 1)) || filename.endsWith(EXTENSION_WEBM))) {
            return MATROSKA
        } else if (filename.endsWith(EXTENSION_MP3)) {
            return MP3
        } else if ((filename.endsWith(EXTENSION_MP4) || filename.startsWith(EXTENSION_PREFIX_M4,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_M4.length + 1)) || filename.startsWith(EXTENSION_PREFIX_MP4,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_MP4.length + 1)) || filename.startsWith(EXTENSION_PREFIX_CMF,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_CMF.length + 1)))) {
            return MP4
        } else if ((filename.startsWith(EXTENSION_PREFIX_OG,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_OG.length + 1)) || filename.endsWith(EXTENSION_OPUS))) {
            return OGG
        } else if ((filename.endsWith(EXTENSION_PS) || filename.endsWith(EXTENSION_MPEG) || filename.endsWith(EXTENSION_MPG) || filename.endsWith(EXTENSION_M2P))) {
            return PS
        } else if ((filename.endsWith(EXTENSION_TS) || filename.startsWith(EXTENSION_PREFIX_TS,  /* toffset= */
                        filename.length - (EXTENSION_PREFIX_TS.length + 1)))) {
            return TS
        } else if (filename.endsWith(EXTENSION_WAV) || filename.endsWith(EXTENSION_WAVE)) {
            return WAV
        } else if (filename.endsWith(EXTENSION_VTT) || filename.endsWith(EXTENSION_WEBVTT)) {
            return WEBVTT
        } else if (filename.endsWith(EXTENSION_JPG) || filename.endsWith(EXTENSION_JPEG)) {
            return JPEG
        } else if (filename.endsWith(EXTENSION_AVI)) {
            return AVI
        } else {
            return UNKNOWN
        }
    }

    /**
     * File types. One of [.UNKNOWN], [.AC3], [.AC4], [.ADTS], [.AMR],
     * [.FLAC], [.FLV], [.MATROSKA], [.MP3], [.MP4], [.OGG],
     * [.PS], [.TS], [.WAV], [.WEBVTT], [.JPEG] and [.MIDI].
     */
    @Documented
    @Retention(AnnotationRetention.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [UNKNOWN, AC3, AC4, ADTS, AMR, FLAC, FLV, MATROSKA, MP3, MP4, OGG, PS, TS, WAV, WEBVTT, JPEG, MIDI, AVI])
    annotation class Type {}
}