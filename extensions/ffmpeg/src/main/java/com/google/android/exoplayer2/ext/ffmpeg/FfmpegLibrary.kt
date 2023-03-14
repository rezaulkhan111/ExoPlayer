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
package com.google.android.exoplayer2.ext.ffmpeg

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.util.LibraryLoader
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import org.checkerframework.checker.nullness.qual.MonotonicNonNull

/** Configures and queries the underlying native library.  */
object FfmpegLibrary {
    init {
        ExoPlayerLibraryInfo.registerModule("goog.exo.ffmpeg")
    }

    private const val TAG = "FfmpegLibrary"
    private val LOADER: LibraryLoader = object : LibraryLoader("ffmpegJNI") {
        override fun loadLibrary(name: String) {
            System.loadLibrary(name)
        }
    }
    /** Returns the version of the underlying library if available, or null otherwise.  */
    var version: @MonotonicNonNull String? = null
        get() {
            if (!isAvailable) {
                return null
            }
            if (field == null) {
                field = ffmpegGetVersion()
            }
            return field
        }
        private set

    /**
     * Returns the required amount of padding for input buffers in bytes, or [C.LENGTH_UNSET] if
     * the underlying library is not available.
     */
    var inputBufferPaddingSize = C.LENGTH_UNSET
        get() {
            if (!isAvailable) {
                return C.LENGTH_UNSET
            }
            if (field == C.LENGTH_UNSET) {
                field = ffmpegGetInputBufferPaddingSize()
            }
            return field
        }
        private set

    /**
     * Override the names of the FFmpeg native libraries. If an application wishes to call this
     * method, it must do so before calling any other method defined by this class, and before
     * instantiating a [FfmpegAudioRenderer] instance.
     *
     * @param libraries The names of the FFmpeg native libraries.
     */
    fun setLibraries(vararg libraries: String?) {
        LOADER.setLibraries(*libraries)
    }

    /** Returns whether the underlying library is available, loading it if necessary.  */
    val isAvailable: Boolean
        get() = LOADER.isAvailable

    /**
     * Returns whether the underlying library supports the specified MIME type.
     *
     * @param mimeType The MIME type to check.
     */
    fun supportsFormat(mimeType: String?): Boolean {
        if (!isAvailable) {
            return false
        }
        val codecName = getCodecName(mimeType) ?: return false
        if (!ffmpegHasDecoder(codecName)) {
            Log.w(TAG, "No $codecName decoder available. Check the FFmpeg build configuration.")
            return false
        }
        return true
    }

    /**
     * Returns the name of the FFmpeg decoder that could be used to decode the format, or `null`
     * if it's unsupported.
     */
    fun getCodecName(mimeType: String?): String? {
        return when (mimeType) {
            MimeTypes.AUDIO_AAC -> "aac"
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> "mp3"
            MimeTypes.AUDIO_AC3 -> "ac3"
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
            MimeTypes.AUDIO_TRUEHD -> "truehd"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "dca"
            MimeTypes.AUDIO_VORBIS -> "vorbis"
            MimeTypes.AUDIO_OPUS -> "opus"
            MimeTypes.AUDIO_AMR_NB -> "amrnb"
            MimeTypes.AUDIO_AMR_WB -> "amrwb"
            MimeTypes.AUDIO_FLAC -> "flac"
            MimeTypes.AUDIO_ALAC -> "alac"
            MimeTypes.AUDIO_MLAW -> "pcm_mulaw"
            MimeTypes.AUDIO_ALAW -> "pcm_alaw"
            else -> null
        }
    }

    private external fun ffmpegGetVersion(): String?
    private external fun ffmpegGetInputBufferPaddingSize(): Int
    private external fun ffmpegHasDecoder(codecName: String): Boolean
}