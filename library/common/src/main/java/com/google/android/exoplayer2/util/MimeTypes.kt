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
package com.google.android.exoplayer2.util

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.TrackType
import com.google.common.base.Ascii
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Defines common MIME types and helper methods.  */
object MimeTypes {
    val BASE_TYPE_VIDEO: String = "video"
    val BASE_TYPE_AUDIO: String = "audio"
    val BASE_TYPE_TEXT: String = "text"
    val BASE_TYPE_IMAGE: String = "image"
    val BASE_TYPE_APPLICATION: String = "application"

    // video/ MIME types
    val VIDEO_MP4: String = BASE_TYPE_VIDEO + "/mp4"
    val VIDEO_MATROSKA: String = BASE_TYPE_VIDEO + "/x-matroska"
    val VIDEO_WEBM: String = BASE_TYPE_VIDEO + "/webm"
    val VIDEO_H263: String = BASE_TYPE_VIDEO + "/3gpp"
    val VIDEO_H264: String = BASE_TYPE_VIDEO + "/avc"
    val VIDEO_H265: String = BASE_TYPE_VIDEO + "/hevc"
    val VIDEO_VP8: String = BASE_TYPE_VIDEO + "/x-vnd.on2.vp8"
    val VIDEO_VP9: String = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9"
    val VIDEO_AV1: String = BASE_TYPE_VIDEO + "/av01"
    val VIDEO_MP2T: String = BASE_TYPE_VIDEO + "/mp2t"
    val VIDEO_MP4V: String = BASE_TYPE_VIDEO + "/mp4v-es"
    val VIDEO_MPEG: String = BASE_TYPE_VIDEO + "/mpeg"
    val VIDEO_PS: String = BASE_TYPE_VIDEO + "/mp2p"
    val VIDEO_MPEG2: String = BASE_TYPE_VIDEO + "/mpeg2"
    val VIDEO_VC1: String = BASE_TYPE_VIDEO + "/wvc1"
    val VIDEO_DIVX: String = BASE_TYPE_VIDEO + "/divx"
    val VIDEO_FLV: String = BASE_TYPE_VIDEO + "/x-flv"
    val VIDEO_DOLBY_VISION: String = BASE_TYPE_VIDEO + "/dolby-vision"
    val VIDEO_OGG: String = BASE_TYPE_VIDEO + "/ogg"
    val VIDEO_AVI: String = BASE_TYPE_VIDEO + "/x-msvideo"
    val VIDEO_MJPEG: String = BASE_TYPE_VIDEO + "/mjpeg"
    val VIDEO_MP42: String = BASE_TYPE_VIDEO + "/mp42"
    val VIDEO_MP43: String = BASE_TYPE_VIDEO + "/mp43"
    val VIDEO_UNKNOWN: String = BASE_TYPE_VIDEO + "/x-unknown"

    // audio/ MIME types
    val AUDIO_MP4: String = BASE_TYPE_AUDIO + "/mp4"
    val AUDIO_AAC: String = BASE_TYPE_AUDIO + "/mp4a-latm"
    val AUDIO_MATROSKA: String = BASE_TYPE_AUDIO + "/x-matroska"
    val AUDIO_WEBM: String = BASE_TYPE_AUDIO + "/webm"
    val AUDIO_MPEG: String = BASE_TYPE_AUDIO + "/mpeg"
    val AUDIO_MPEG_L1: String = BASE_TYPE_AUDIO + "/mpeg-L1"
    val AUDIO_MPEG_L2: String = BASE_TYPE_AUDIO + "/mpeg-L2"
    val AUDIO_MPEGH_MHA1: String = BASE_TYPE_AUDIO + "/mha1"
    val AUDIO_MPEGH_MHM1: String = BASE_TYPE_AUDIO + "/mhm1"
    val AUDIO_RAW: String = BASE_TYPE_AUDIO + "/raw"
    val AUDIO_ALAW: String = BASE_TYPE_AUDIO + "/g711-alaw"
    val AUDIO_MLAW: String = BASE_TYPE_AUDIO + "/g711-mlaw"
    val AUDIO_AC3: String = BASE_TYPE_AUDIO + "/ac3"
    val AUDIO_E_AC3: String = BASE_TYPE_AUDIO + "/eac3"
    val AUDIO_E_AC3_JOC: String = BASE_TYPE_AUDIO + "/eac3-joc"
    val AUDIO_AC4: String = BASE_TYPE_AUDIO + "/ac4"
    val AUDIO_TRUEHD: String = BASE_TYPE_AUDIO + "/true-hd"
    val AUDIO_DTS: String = BASE_TYPE_AUDIO + "/vnd.dts"
    val AUDIO_DTS_HD: String = BASE_TYPE_AUDIO + "/vnd.dts.hd"
    val AUDIO_DTS_EXPRESS: String = BASE_TYPE_AUDIO + "/vnd.dts.hd;profile=lbr"
    val AUDIO_DTS_X: String = BASE_TYPE_AUDIO + "/vnd.dts.uhd;profile=p2"
    val AUDIO_VORBIS: String = BASE_TYPE_AUDIO + "/vorbis"
    val AUDIO_OPUS: String = BASE_TYPE_AUDIO + "/opus"
    val AUDIO_AMR: String = BASE_TYPE_AUDIO + "/amr"
    val AUDIO_AMR_NB: String = BASE_TYPE_AUDIO + "/3gpp"
    val AUDIO_AMR_WB: String = BASE_TYPE_AUDIO + "/amr-wb"
    val AUDIO_FLAC: String = BASE_TYPE_AUDIO + "/flac"
    val AUDIO_ALAC: String = BASE_TYPE_AUDIO + "/alac"
    val AUDIO_MSGSM: String = BASE_TYPE_AUDIO + "/gsm"
    val AUDIO_OGG: String = BASE_TYPE_AUDIO + "/ogg"
    val AUDIO_WAV: String = BASE_TYPE_AUDIO + "/wav"
    val AUDIO_MIDI: String = BASE_TYPE_AUDIO + "/midi"
    val AUDIO_EXOPLAYER_MIDI: String = BASE_TYPE_AUDIO + "/x-exoplayer-midi"
    val AUDIO_UNKNOWN: String = BASE_TYPE_AUDIO + "/x-unknown"

    // text/ MIME types
    val TEXT_VTT: String = BASE_TYPE_TEXT + "/vtt"
    val TEXT_SSA: String = BASE_TYPE_TEXT + "/x-ssa"
    val TEXT_EXOPLAYER_CUES: String = BASE_TYPE_TEXT + "/x-exoplayer-cues"
    val TEXT_UNKNOWN: String = BASE_TYPE_TEXT + "/x-unknown"

    // application/ MIME types
    val APPLICATION_MP4: String = BASE_TYPE_APPLICATION + "/mp4"
    val APPLICATION_WEBM: String = BASE_TYPE_APPLICATION + "/webm"
    val APPLICATION_MATROSKA: String = BASE_TYPE_APPLICATION + "/x-matroska"
    val APPLICATION_MPD: String = BASE_TYPE_APPLICATION + "/dash+xml"
    val APPLICATION_M3U8: String = BASE_TYPE_APPLICATION + "/x-mpegURL"
    val APPLICATION_SS: String = BASE_TYPE_APPLICATION + "/vnd.ms-sstr+xml"
    val APPLICATION_ID3: String = BASE_TYPE_APPLICATION + "/id3"
    val APPLICATION_CEA608: String = BASE_TYPE_APPLICATION + "/cea-608"
    val APPLICATION_CEA708: String = BASE_TYPE_APPLICATION + "/cea-708"
    val APPLICATION_SUBRIP: String = BASE_TYPE_APPLICATION + "/x-subrip"
    val APPLICATION_TTML: String = BASE_TYPE_APPLICATION + "/ttml+xml"
    val APPLICATION_TX3G: String = BASE_TYPE_APPLICATION + "/x-quicktime-tx3g"
    val APPLICATION_MP4VTT: String = BASE_TYPE_APPLICATION + "/x-mp4-vtt"
    val APPLICATION_MP4CEA608: String = BASE_TYPE_APPLICATION + "/x-mp4-cea-608"
    val APPLICATION_RAWCC: String = BASE_TYPE_APPLICATION + "/x-rawcc"
    val APPLICATION_VOBSUB: String = BASE_TYPE_APPLICATION + "/vobsub"
    val APPLICATION_PGS: String = BASE_TYPE_APPLICATION + "/pgs"
    val APPLICATION_SCTE35: String = BASE_TYPE_APPLICATION + "/x-scte35"
    val APPLICATION_CAMERA_MOTION: String = BASE_TYPE_APPLICATION + "/x-camera-motion"
    val APPLICATION_EMSG: String = BASE_TYPE_APPLICATION + "/x-emsg"
    val APPLICATION_DVBSUBS: String = BASE_TYPE_APPLICATION + "/dvbsubs"
    val APPLICATION_EXIF: String = BASE_TYPE_APPLICATION + "/x-exif"
    val APPLICATION_ICY: String = BASE_TYPE_APPLICATION + "/x-icy"
    val APPLICATION_AIT: String = BASE_TYPE_APPLICATION + "/vnd.dvb.ait"
    val APPLICATION_RTSP: String = BASE_TYPE_APPLICATION + "/x-rtsp"

    // image/ MIME types
    val IMAGE_JPEG: String = BASE_TYPE_IMAGE + "/jpeg"

    /**
     * A non-standard codec string for E-AC3-JOC. Use of this constant allows for disambiguation
     * between regular E-AC3 ("ec-3") and E-AC3-JOC ("ec+3") streams from the codec string alone. The
     * standard is to use "ec-3" for both, as per the [MP4RA
 * registered codec types](https://mp4ra.org/#/codecs).
     */
    val CODEC_E_AC3_JOC: String = "ec+3"
    private val customMimeTypes: ArrayList<CustomMimeType> = ArrayList()
    private val MP4A_RFC_6381_CODEC_PATTERN: Pattern =
            Pattern.compile("^mp4a\\.([a-zA-Z0-9]{2})(?:\\.([0-9]{1,2}))?$")

    /**
     * Registers a custom MIME type. Most applications do not need to call this method, as handling of
     * standard MIME types is built in. These built-in MIME types take precedence over any registered
     * via this method. If this method is used, it must be called before creating any player(s).
     *
     * @param mimeType The custom MIME type to register.
     * @param codecPrefix The RFC 6381 codec string prefix associated with the MIME type.
     * @param trackType The [track type][C.TrackType] associated with the MIME type. This value
     * is ignored if the top-level type of `mimeType` is audio, video or text.
     */
    fun registerCustomMimeType(
            mimeType: String, codecPrefix: String?, @C.TrackType trackType: Int
    ) {
        val customMimeType: CustomMimeType = CustomMimeType(mimeType, codecPrefix, trackType)
        val customMimeTypeCount: Int = customMimeTypes.size
        for (i in 0 until customMimeTypeCount) {
            if ((mimeType == customMimeTypes.get(i).mimeType)) {
                customMimeTypes.removeAt(i)
                break
            }
        }
        customMimeTypes.add(customMimeType)
    }

    /** Returns whether the given string is an audio MIME type.  */
    fun isAudio(mimeType: String?): Boolean {
        return (BASE_TYPE_AUDIO == getTopLevelType(mimeType))
    }

    /** Returns whether the given string is a video MIME type.  */
    fun isVideo(mimeType: String?): Boolean {
        return (BASE_TYPE_VIDEO == getTopLevelType(mimeType))
    }

    /**
     * Returns whether the given string is a text MIME type, including known text types that use
     * &quot;application&quot; as their base type.
     */
    fun isText(mimeType: String?): Boolean {
        return ((BASE_TYPE_TEXT == getTopLevelType(mimeType)) || (APPLICATION_CEA608 == mimeType) || (APPLICATION_CEA708 == mimeType) || (APPLICATION_MP4CEA608 == mimeType) || (APPLICATION_SUBRIP == mimeType) || (APPLICATION_TTML == mimeType) || (APPLICATION_TX3G == mimeType) || (APPLICATION_MP4VTT == mimeType) || (APPLICATION_RAWCC == mimeType) || (APPLICATION_VOBSUB == mimeType) || (APPLICATION_PGS == mimeType) || (APPLICATION_DVBSUBS == mimeType))
    }

    /** Returns whether the given string is an image MIME type.  */
    fun isImage(mimeType: String?): Boolean {
        return (BASE_TYPE_IMAGE == getTopLevelType(mimeType))
    }

    /**
     * Returns true if it is known that all samples in a stream of the given MIME type and codec are
     * guaranteed to be sync samples (i.e., [C.BUFFER_FLAG_KEY_FRAME] is guaranteed to be set on
     * every sample).
     *
     * @param mimeType The MIME type of the stream.
     * @param codec The RFC 6381 codec string of the stream, or `null` if unknown.
     * @return Whether it is known that all samples in the stream are guaranteed to be sync samples.
     */
    fun allSamplesAreSyncSamples(
            mimeType: String?, codec: String?
    ): Boolean {
        if (mimeType == null) {
            return false
        }
        when (mimeType) {
            AUDIO_MPEG, AUDIO_MPEG_L1, AUDIO_MPEG_L2, AUDIO_RAW, AUDIO_ALAW, AUDIO_MLAW, AUDIO_FLAC, AUDIO_AC3, AUDIO_E_AC3, AUDIO_E_AC3_JOC -> return true
            AUDIO_AAC -> {
                if (codec == null) {
                    return false
                }
                val objectType: Mp4aObjectType? = getObjectTypeFromMp4aRFC6381CodecString(codec)
                if (objectType == null) {
                    return false
                }
                @C.Encoding
                val encoding: Int = objectType.getEncoding()
                // xHE-AAC is an exception in which it's not true that all samples will be sync samples.
                // Also return false for ENCODING_INVALID, which indicates we weren't able to parse the
                // encoding from the codec string.
                return encoding != C.ENCODING_INVALID && encoding != C.ENCODING_AAC_XHE
            }

            else -> return false
        }
    }

    /**
     * Returns the first video MIME type derived from an RFC 6381 codecs string.
     *
     * @param codecs An RFC 6381 codecs string.
     * @return The first derived video MIME type, or `null`.
     */
    fun getVideoMediaMimeType(codecs: String?): String? {
        if (codecs == null) {
            return null
        }
        val codecList: Array<String?>? = Util.splitCodecs(codecs)
        for (codec: String? in codecList!!) {
            val mimeType: String? = getMediaMimeType(codec)
            if (mimeType != null && isVideo(mimeType)) {
                return mimeType
            }
        }
        return null
    }

    /**
     * Returns whether the given `codecs` string contains a codec which corresponds to the given
     * `mimeType`.
     *
     * @param codecs An RFC 6381 codecs string.
     * @param mimeType A MIME type to look for.
     * @return Whether the given `codecs` string contains a codec which corresponds to the given
     * `mimeType`.
     */
    fun containsCodecsCorrespondingToMimeType(
            codecs: String?, mimeType: String?
    ): Boolean {
        return getCodecsCorrespondingToMimeType(codecs, mimeType) != null
    }

    /**
     * Returns a subsequence of `codecs` containing the codec strings that correspond to the
     * given `mimeType`. Returns null if `mimeType` is null, `codecs` is null, or
     * `codecs` does not contain a codec that corresponds to `mimeType`.
     *
     * @param codecs An RFC 6381 codecs string.
     * @param mimeType A MIME type to look for.
     * @return A subsequence of `codecs` containing the codec strings that correspond to the
     * given `mimeType`. Returns null if `mimeType` is null, `codecs` is null,
     * or `codecs` does not contain a codec that corresponds to `mimeType`.
     */
    fun getCodecsCorrespondingToMimeType(
            codecs: String?, mimeType: String?
    ): String? {
        if (codecs == null || mimeType == null) {
            return null
        }
        val codecList: Array<String?>? = Util.splitCodecs(codecs)
        val builder: StringBuilder = StringBuilder()
        for (codec: String? in codecList!!) {
            if ((mimeType == getMediaMimeType(codec))) {
                if (builder.length > 0) {
                    builder.append(",")
                }
                builder.append(codec)
            }
        }
        return if (builder.length > 0) builder.toString() else null
    }

    /**
     * Returns the first audio MIME type derived from an RFC 6381 codecs string.
     *
     * @param codecs An RFC 6381 codecs string.
     * @return The first derived audio MIME type, or `null`.
     */
    fun getAudioMediaMimeType(codecs: String?): String? {
        if (codecs == null) {
            return null
        }
        val codecList: Array<String?>? = Util.splitCodecs(codecs)
        for (codec: String? in codecList!!) {
            val mimeType: String? = getMediaMimeType(codec)
            if (mimeType != null && isAudio(mimeType)) {
                return mimeType
            }
        }
        return null
    }

    /**
     * Returns the first text MIME type derived from an RFC 6381 codecs string.
     *
     * @param codecs An RFC 6381 codecs string.
     * @return The first derived text MIME type, or `null`.
     */
    fun getTextMediaMimeType(codecs: String?): String? {
        if (codecs == null) {
            return null
        }
        val codecList: Array<String?>? = Util.splitCodecs(codecs)
        for (codec: String? in codecList!!) {
            val mimeType: String? = getMediaMimeType(codec)
            if (mimeType != null && isText(mimeType)) {
                return mimeType
            }
        }
        return null
    }

    /**
     * Returns the MIME type corresponding to an RFC 6381 codec string, or `null` if it could
     * not be determined.
     *
     * @param codec An RFC 6381 codec string.
     * @return The corresponding MIME type, or `null` if it could not be determined.
     */
    fun getMediaMimeType(codec: String?): String? {
        var codec: String? = codec
        if (codec == null) {
            return null
        }
        codec = Ascii.toLowerCase(codec.trim({ it <= ' ' }))
        if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
            return VIDEO_H264
        } else if (codec.startsWith("hev1") || codec.startsWith("hvc1")) {
            return VIDEO_H265
        } else if ((codec.startsWith("dvav") || codec.startsWith("dva1") || codec.startsWith("dvhe") || codec.startsWith(
                        "dvh1"
                ))
        ) {
            return VIDEO_DOLBY_VISION
        } else if (codec.startsWith("av01")) {
            return VIDEO_AV1
        } else if (codec.startsWith("vp9") || codec.startsWith("vp09")) {
            return VIDEO_VP9
        } else if (codec.startsWith("vp8") || codec.startsWith("vp08")) {
            return VIDEO_VP8
        } else if (codec.startsWith("mp4a")) {
            var mimeType: String? = null
            if (codec.startsWith("mp4a.")) {
                val objectType: Mp4aObjectType? = getObjectTypeFromMp4aRFC6381CodecString(codec)
                if (objectType != null) {
                    mimeType = getMimeTypeFromMp4ObjectType(objectType.objectTypeIndication)
                }
            }
            return if (mimeType == null) AUDIO_AAC else mimeType
        } else if (codec.startsWith("mha1")) {
            return AUDIO_MPEGH_MHA1
        } else if (codec.startsWith("mhm1")) {
            return AUDIO_MPEGH_MHM1
        } else if (codec.startsWith("ac-3") || codec.startsWith("dac3")) {
            return AUDIO_AC3
        } else if (codec.startsWith("ec-3") || codec.startsWith("dec3")) {
            return AUDIO_E_AC3
        } else if (codec.startsWith(CODEC_E_AC3_JOC)) {
            return AUDIO_E_AC3_JOC
        } else if (codec.startsWith("ac-4") || codec.startsWith("dac4")) {
            return AUDIO_AC4
        } else if (codec.startsWith("dtsc")) {
            return AUDIO_DTS
        } else if (codec.startsWith("dtse")) {
            return AUDIO_DTS_EXPRESS
        } else if (codec.startsWith("dtsh") || codec.startsWith("dtsl")) {
            return AUDIO_DTS_HD
        } else if (codec.startsWith("dtsx")) {
            return AUDIO_DTS_X
        } else if (codec.startsWith("opus")) {
            return AUDIO_OPUS
        } else if (codec.startsWith("vorbis")) {
            return AUDIO_VORBIS
        } else if (codec.startsWith("flac")) {
            return AUDIO_FLAC
        } else if (codec.startsWith("stpp")) {
            return APPLICATION_TTML
        } else if (codec.startsWith("wvtt")) {
            return TEXT_VTT
        } else if (codec.contains("cea708")) {
            return APPLICATION_CEA708
        } else if (codec.contains("eia608") || codec.contains("cea608")) {
            return APPLICATION_CEA608
        } else {
            return getCustomMimeTypeForCodec(codec)
        }
    }

    /**
     * Returns the MIME type corresponding to an MP4 object type identifier, as defined in RFC 6381
     * and https://mp4ra.org/#/object_types.
     *
     * @param objectType An MP4 object type identifier.
     * @return The corresponding MIME type, or `null` if it could not be determined.
     */
    fun getMimeTypeFromMp4ObjectType(objectType: Int): String? {
        when (objectType) {
            0x20 -> return VIDEO_MP4V
            0x21 -> return VIDEO_H264
            0x23 -> return VIDEO_H265
            0x60, 0x61, 0x62, 0x63, 0x64, 0x65 -> return VIDEO_MPEG2
            0x6A -> return VIDEO_MPEG
            0x69, 0x6B -> return AUDIO_MPEG
            0xA3 -> return VIDEO_VC1
            0xB1 -> return VIDEO_VP9
            0x40, 0x66, 0x67, 0x68 -> return AUDIO_AAC
            0xA5 -> return AUDIO_AC3
            0xA6 -> return AUDIO_E_AC3
            0xA9, 0xAC -> return AUDIO_DTS
            0xAA, 0xAB -> return AUDIO_DTS_HD
            0xAD -> return AUDIO_OPUS
            0xAE -> return AUDIO_AC4
            else -> return null
        }
    }

    /**
     * Returns the [track type][C.TrackType] constant corresponding to a specified MIME type,
     * which may be [C.TRACK_TYPE_UNKNOWN] if it could not be determined.
     *
     * @param mimeType A MIME type.
     * @return The corresponding [track type][C.TrackType], which may be [     ][C.TRACK_TYPE_UNKNOWN] if it could not be determined.
     */
    @C.TrackType
    fun getTrackType(mimeType: String?): Int {
        if (TextUtils.isEmpty(mimeType)) {
            return C.TRACK_TYPE_UNKNOWN
        } else if (isAudio(mimeType)) {
            return C.TRACK_TYPE_AUDIO
        } else if (isVideo(mimeType)) {
            return C.TRACK_TYPE_VIDEO
        } else if (isText(mimeType)) {
            return C.TRACK_TYPE_TEXT
        } else if (isImage(mimeType)) {
            return C.TRACK_TYPE_IMAGE
        } else if (((APPLICATION_ID3 == mimeType) || (APPLICATION_EMSG == mimeType) || (APPLICATION_SCTE35 == mimeType))) {
            return C.TRACK_TYPE_METADATA
        } else if ((APPLICATION_CAMERA_MOTION == mimeType)) {
            return C.TRACK_TYPE_CAMERA_MOTION
        } else {
            return getTrackTypeForCustomMimeType(mimeType)
        }
    }

    /**
     * Returns the [C.Encoding] constant corresponding to the specified audio MIME type and RFC
     * 6381 codec string, or [C.ENCODING_INVALID] if the corresponding [C.Encoding] cannot
     * be determined.
     *
     * @param mimeType A MIME type.
     * @param codec An RFC 6381 codec string, or `null` if unknown or not applicable.
     * @return The corresponding [C.Encoding], or [C.ENCODING_INVALID].
     */
    @C.Encoding
    fun getEncoding(mimeType: String?, codec: String?): Int {
        when (mimeType) {
            AUDIO_MPEG -> return C.ENCODING_MP3
            AUDIO_AAC -> {
                if (codec == null) {
                    return C.ENCODING_INVALID
                }
                val objectType: Mp4aObjectType? = getObjectTypeFromMp4aRFC6381CodecString(codec)
                if (objectType == null) {
                    return C.ENCODING_INVALID
                }
                return objectType.getEncoding()
            }

            AUDIO_AC3 -> return C.ENCODING_AC3
            AUDIO_E_AC3 -> return C.ENCODING_E_AC3
            AUDIO_E_AC3_JOC -> return C.ENCODING_E_AC3_JOC
            AUDIO_AC4 -> return C.ENCODING_AC4
            AUDIO_DTS -> return C.ENCODING_DTS
            AUDIO_DTS_HD -> return C.ENCODING_DTS_HD
            AUDIO_TRUEHD -> return C.ENCODING_DOLBY_TRUEHD
            else -> return C.ENCODING_INVALID
        }
    }

    /**
     * Equivalent to `getTrackType(getMediaMimeType(codec))`.
     *
     * @param codec An RFC 6381 codec string.
     * @return The corresponding [track type][C.TrackType], which may be [     ][C.TRACK_TYPE_UNKNOWN] if it could not be determined.
     */
    @C.TrackType
    fun getTrackTypeOfCodec(codec: String?): Int {
        return getTrackType(getMediaMimeType(codec))
    }

    /**
     * Normalizes the MIME type provided so that equivalent MIME types are uniquely represented.
     *
     * @param mimeType A MIME type to normalize.
     * @return The normalized MIME type, or the argument MIME type if its normalized form is unknown.
     */
    fun normalizeMimeType(mimeType: String): String {
        when (mimeType) {
            BASE_TYPE_AUDIO + "/x-flac" -> return AUDIO_FLAC
            BASE_TYPE_AUDIO + "/mp3" -> return AUDIO_MPEG
            BASE_TYPE_AUDIO + "/x-wav" -> return AUDIO_WAV
            else -> return mimeType
        }
    }

    /** Returns whether the given `mimeType` is a Matroska MIME type, including WebM.  */
    fun isMatroska(mimeType: String?): Boolean {
        if (mimeType == null) {
            return false
        }
        return (mimeType.startsWith(VIDEO_WEBM) || mimeType.startsWith(AUDIO_WEBM) || mimeType.startsWith(
                APPLICATION_WEBM
        ) || mimeType.startsWith(VIDEO_MATROSKA) || mimeType.startsWith(AUDIO_MATROSKA) || mimeType.startsWith(
                APPLICATION_MATROSKA
        ))
    }

    /**
     * Returns the top-level type of `mimeType`, or null if `mimeType` is null or does not
     * contain a forward slash character (`'/'`).
     */
    private fun getTopLevelType(mimeType: String?): String? {
        if (mimeType == null) {
            return null
        }
        val indexOfSlash: Int = mimeType.indexOf('/')
        if (indexOfSlash == -1) {
            return null
        }
        return mimeType.substring(0, indexOfSlash)
    }

    private fun getCustomMimeTypeForCodec(codec: String?): String? {
        val customMimeTypeCount: Int = customMimeTypes.size
        for (i in 0 until customMimeTypeCount) {
            val customMimeType: CustomMimeType = customMimeTypes.get(i)
            if (codec!!.startsWith((customMimeType.codecPrefix)!!)) {
                return customMimeType.mimeType
            }
        }
        return null
    }

    @C.TrackType
    private fun getTrackTypeForCustomMimeType(mimeType: String?): Int {
        val customMimeTypeCount: Int = customMimeTypes.size
        for (i in 0 until customMimeTypeCount) {
            val customMimeType: CustomMimeType = customMimeTypes.get(i)
            if ((mimeType == customMimeType.mimeType)) {
                return customMimeType.trackType
            }
        }
        return C.TRACK_TYPE_UNKNOWN
    }

    /**
     * Returns the [Mp4aObjectType] of an RFC 6381 MP4 audio codec string.
     *
     *
     * Per https://mp4ra.org/#/object_types and https://tools.ietf.org/html/rfc6381#section-3.3, an
     * MP4 codec string has the form:
     *
     * <pre>
     * ~~~~~~~~~~~~~~ Object Type Indication (OTI) byte in hex
     * mp4a.[a-zA-Z0-9]{2}(.[0-9]{1,2})?
     * ~~~~~~~~~~ audio OTI, decimal. Only for certain OTI.
    </pre> *
     *
     * For example, mp4a.40.2 has an OTI of 0x40 and an audio OTI of 2.
     *
     * @param codec An RFC 6381 MP4 audio codec string.
     * @return The [Mp4aObjectType], or `null` if the input was invalid.
     */
    @VisibleForTesting
    fun getObjectTypeFromMp4aRFC6381CodecString(codec: String?): Mp4aObjectType? {
        val matcher: Matcher = MP4A_RFC_6381_CODEC_PATTERN.matcher(codec)
        if (!matcher.matches()) {
            return null
        }
        val objectTypeIndicationHex: String? = Assertions.checkNotNull(matcher.group(1))
        val audioObjectTypeIndicationDec: String? = matcher.group(2)
        val objectTypeIndication: Int
        var audioObjectTypeIndication: Int = 0
        try {
            objectTypeIndication = objectTypeIndicationHex!!.toInt(16)
            if (audioObjectTypeIndicationDec != null) {
                audioObjectTypeIndication = audioObjectTypeIndicationDec.toInt()
            }
        } catch (e: NumberFormatException) {
            return null
        }
        return Mp4aObjectType(objectTypeIndication, audioObjectTypeIndication)
    }

    /** An MP4A Object Type Indication (OTI) and its optional audio OTI is defined by RFC 6381.  */
    @VisibleForTesting /* package */
    class Mp4aObjectType {
        /** The Object Type Indication of the MP4A codec.  */
        var objectTypeIndication = 0

        /** The Audio Object Type Indication of the MP4A codec, or 0 if it is absent.  */
        var audioObjectTypeIndication = 0

        constructor(objectTypeIndication: Int, audioObjectTypeIndication: Int) {
            this.objectTypeIndication = objectTypeIndication
            this.audioObjectTypeIndication = audioObjectTypeIndication
        }

        /** Returns the encoding for [.audioObjectTypeIndication].  */
        @C.Encoding
        fun getEncoding(): Int {
            // See AUDIO_OBJECT_TYPE_AAC_* constants in AacUtil.
            return when (audioObjectTypeIndication) {
                2 -> C.ENCODING_AAC_LC
                5 -> C.ENCODING_AAC_HE_V1
                29 -> C.ENCODING_AAC_HE_V2
                42 -> C.ENCODING_AAC_XHE
                23 -> C.ENCODING_AAC_ELD
                22 -> C.ENCODING_AAC_ER_BSAC
                else -> C.ENCODING_INVALID
            }
        }
    }

    private class CustomMimeType {
        var mimeType: String? = null
        var codecPrefix: String? = null
        var trackType = 0

        constructor(mimeType: String?, codecPrefix: String?, trackType: @TrackType Int) {
            this.mimeType = mimeType
            this.codecPrefix = codecPrefix
            this.trackType = trackType
        }
    }
}