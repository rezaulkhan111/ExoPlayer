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

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.view.Surface
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2import.PlaybackException
import com.google.errorprone.annotations.InlineMe
import java.lang.annotation.Documented
import java.lang.annotation.RetentionPolicy
import java.util.*

android.content.Context
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTextureimport

android.media.*
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfoimport

android.view.*
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Api32
import com.google.android.exoplayer2.audio.AudioAttributes.AudioAttributesV21
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import androidx.annotation.FloatRange
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdState
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.C.StereoMode
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.DeviceInfo
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
import com.google.android.exoplayer2.BundleListRetriever
import com.google.android.exoplayer2.Timeline.RemotableTimeline
import com.google.android.exoplayer2.MediaItem.ClippingProperties
import com.google.android.exoplayer2.MediaItem.PlaybackProperties
import com.google.android.exoplayer2.MediaItem.RequestMetadata
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.common.primitives.Ints
import android.view.accessibility.CaptioningManager
import com.google.android.exoplayer2.DeviceInfo.PlaybackType
import com.google.android.exoplayer2.MediaMetadata.PictureType
import com.google.android.exoplayer2.MediaMetadata.FolderType
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.BasePlayer
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import com.google.android.exoplayer2.SimpleBasePlayer
import androidx.annotation.CallSuperimport

import com.google.android.exoplayer2.util.*
import com.google.errorprone.annotations.InlineMeimport

/** Defines constants used by the library.  */
object C {
    /**
     * Special constant representing a time corresponding to the end of a source. Suitable for use in
     * any time base.
     */
    const val TIME_END_OF_SOURCE: Long = Long.MIN_VALUE

    /**
     * Special constant representing an unset or unknown time or duration. Suitable for use in any
     * time base.
     */
    const val TIME_UNSET: Long = Long.MIN_VALUE + 1

    /** Represents an unset or unknown index.  */
    const val INDEX_UNSET: Int = -1

    /** Represents an unset or unknown position.  */
    const val POSITION_UNSET: Int = -1

    /** Represents an unset or unknown rate.  */
    const val RATE_UNSET: Float = -Float.MAX_VALUE

    /** Represents an unset or unknown integer rate.  */
    const val RATE_UNSET_INT: Int = Int.MIN_VALUE + 1

    /** Represents an unset or unknown length.  */
    const val LENGTH_UNSET: Int = -1

    /** Represents an unset or unknown percentage.  */
    const val PERCENTAGE_UNSET: Int = -1

    /** The number of milliseconds in one second.  */
    const val MILLIS_PER_SECOND: Long = 1000L

    /** The number of microseconds in one second.  */
    const val MICROS_PER_SECOND: Long = 1000000L

    /** The number of nanoseconds in one second.  */
    const val NANOS_PER_SECOND: Long = 1000000000L

    /** The number of bits per byte.  */
    const val BITS_PER_BYTE: Int = 8

    /** The number of bytes per float.  */
    const val BYTES_PER_FLOAT: Int = 4

    @Deprecated("Use {@link java.nio.charset.StandardCharsets} or {@link\n" + "   *     com.google.common.base.Charsets} instead.")
    val ASCII_NAME: String = "US-ASCII"

    @Deprecated("Use {@link java.nio.charset.StandardCharsets} or {@link\n" + "   *     com.google.common.base.Charsets} instead.")
    val UTF8_NAME: String = "UTF-8"

    @Deprecated("Use {@link java.nio.charset.StandardCharsets} or {@link\n" + "   *     com.google.common.base.Charsets} instead.")
    val ISO88591_NAME: String = "ISO-8859-1"

    @Deprecated("Use {@link java.nio.charset.StandardCharsets} or {@link\n" + "   *     com.google.common.base.Charsets} instead.")
    val UTF16_NAME: String = "UTF-16"

    @Deprecated("Use {@link java.nio.charset.StandardCharsets} or {@link\n" + "   *     com.google.common.base.Charsets} instead.")
    val UTF16LE_NAME: String = "UTF-16LE"

    /** The name of the serif font family.  */
    const val SERIF_NAME: String = "serif"

    /** The name of the sans-serif font family.  */
    const val SANS_SERIF_NAME: String = "sans-serif"

    /** The [URI scheme][Uri.getScheme] used for content with server side ad insertion.  */
    const val SSAI_SCHEME: String = "ssai"

    /** No crypto.  */
    const val CRYPTO_TYPE_NONE: Int = 0

    /** An unsupported crypto type.  */
    const val CRYPTO_TYPE_UNSUPPORTED: Int = 1

    /** Framework crypto in which a [MediaCodec] is configured with a [MediaCrypto].  */
    const val CRYPTO_TYPE_FRAMEWORK: Int = 2

    /**
     * Applications or extensions may define custom `CRYPTO_TYPE_*` constants greater than or
     * equal to this value.
     */
    const val CRYPTO_TYPE_CUSTOM_BASE: Int = 10000

    /**
     * @see MediaCodec.CRYPTO_MODE_UNENCRYPTED
     */
    const val CRYPTO_MODE_UNENCRYPTED: Int = MediaCodec.CRYPTO_MODE_UNENCRYPTED

    /**
     * @see MediaCodec.CRYPTO_MODE_AES_CTR
     */
    const val CRYPTO_MODE_AES_CTR: Int = MediaCodec.CRYPTO_MODE_AES_CTR

    /**
     * @see MediaCodec.CRYPTO_MODE_AES_CBC
     */
    const val CRYPTO_MODE_AES_CBC: Int = MediaCodec.CRYPTO_MODE_AES_CBC

    /**
     * Represents an unset [android.media.AudioTrack] session identifier. Equal to [ ][AudioManager.AUDIO_SESSION_ID_GENERATE].
     */
    const val AUDIO_SESSION_ID_UNSET: Int = AudioManager.AUDIO_SESSION_ID_GENERATE

    /**
     * @see AudioFormat.ENCODING_INVALID
     */
    const val ENCODING_INVALID: Int = AudioFormat.ENCODING_INVALID

    /**
     * @see AudioFormat.ENCODING_PCM_8BIT
     */
    const val ENCODING_PCM_8BIT: Int = AudioFormat.ENCODING_PCM_8BIT

    /**
     * @see AudioFormat.ENCODING_PCM_16BIT
     */
    const val ENCODING_PCM_16BIT: Int = AudioFormat.ENCODING_PCM_16BIT

    /** Like [.ENCODING_PCM_16BIT], but with the bytes in big endian order.  */
    const val ENCODING_PCM_16BIT_BIG_ENDIAN: Int = 0x10000000

    /** PCM encoding with 24 bits per sample.  */
    const val ENCODING_PCM_24BIT: Int = 0x20000000

    /** PCM encoding with 32 bits per sample.  */
    const val ENCODING_PCM_32BIT: Int = 0x30000000

    /**
     * @see AudioFormat.ENCODING_PCM_FLOAT
     */
    const val ENCODING_PCM_FLOAT: Int = AudioFormat.ENCODING_PCM_FLOAT

    /**
     * @see AudioFormat.ENCODING_MP3
     */
    const val ENCODING_MP3: Int = AudioFormat.ENCODING_MP3

    /**
     * @see AudioFormat.ENCODING_AAC_LC
     */
    const val ENCODING_AAC_LC: Int = AudioFormat.ENCODING_AAC_LC

    /**
     * @see AudioFormat.ENCODING_AAC_HE_V1
     */
    const val ENCODING_AAC_HE_V1: Int = AudioFormat.ENCODING_AAC_HE_V1

    /**
     * @see AudioFormat.ENCODING_AAC_HE_V2
     */
    const val ENCODING_AAC_HE_V2: Int = AudioFormat.ENCODING_AAC_HE_V2

    /**
     * @see AudioFormat.ENCODING_AAC_XHE
     */
    const val ENCODING_AAC_XHE: Int = AudioFormat.ENCODING_AAC_XHE

    /**
     * @see AudioFormat.ENCODING_AAC_ELD
     */
    const val ENCODING_AAC_ELD: Int = AudioFormat.ENCODING_AAC_ELD

    /** AAC Error Resilient Bit-Sliced Arithmetic Coding.  */
    const val ENCODING_AAC_ER_BSAC: Int = 0x40000000

    /**
     * @see AudioFormat.ENCODING_AC3
     */
    const val ENCODING_AC3: Int = AudioFormat.ENCODING_AC3

    /**
     * @see AudioFormat.ENCODING_E_AC3
     */
    const val ENCODING_E_AC3: Int = AudioFormat.ENCODING_E_AC3

    /**
     * @see AudioFormat.ENCODING_E_AC3_JOC
     */
    const val ENCODING_E_AC3_JOC: Int = AudioFormat.ENCODING_E_AC3_JOC

    /**
     * @see AudioFormat.ENCODING_AC4
     */
    const val ENCODING_AC4: Int = AudioFormat.ENCODING_AC4

    /**
     * @see AudioFormat.ENCODING_DTS
     */
    const val ENCODING_DTS: Int = AudioFormat.ENCODING_DTS

    /**
     * @see AudioFormat.ENCODING_DTS_HD
     */
    const val ENCODING_DTS_HD: Int = AudioFormat.ENCODING_DTS_HD

    /**
     * @see AudioFormat.ENCODING_DOLBY_TRUEHD
     */
    const val ENCODING_DOLBY_TRUEHD: Int = AudioFormat.ENCODING_DOLBY_TRUEHD

    /**
     * @see AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO
     */
    const val SPATIALIZATION_BEHAVIOR_AUTO: Int = AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO

    /**
     * @see AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER
     */
    const val SPATIALIZATION_BEHAVIOR_NEVER: Int = AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER

    /**
     * @see AudioManager.STREAM_ALARM
     */
    const val STREAM_TYPE_ALARM: Int = AudioManager.STREAM_ALARM

    /**
     * @see AudioManager.STREAM_DTMF
     */
    const val STREAM_TYPE_DTMF: Int = AudioManager.STREAM_DTMF

    /**
     * @see AudioManager.STREAM_MUSIC
     */
    const val STREAM_TYPE_MUSIC: Int = AudioManager.STREAM_MUSIC

    /**
     * @see AudioManager.STREAM_NOTIFICATION
     */
    const val STREAM_TYPE_NOTIFICATION: Int = AudioManager.STREAM_NOTIFICATION

    /**
     * @see AudioManager.STREAM_RING
     */
    const val STREAM_TYPE_RING: Int = AudioManager.STREAM_RING

    /**
     * @see AudioManager.STREAM_SYSTEM
     */
    const val STREAM_TYPE_SYSTEM: Int = AudioManager.STREAM_SYSTEM

    /**
     * @see AudioManager.STREAM_VOICE_CALL
     */
    const val STREAM_TYPE_VOICE_CALL: Int = AudioManager.STREAM_VOICE_CALL

    /** The default stream type used by audio renderers. Equal to [.STREAM_TYPE_MUSIC].  */
    val STREAM_TYPE_DEFAULT: Int = STREAM_TYPE_MUSIC

    /** See [AudioAttributes.CONTENT_TYPE_MOVIE].  */
    const val AUDIO_CONTENT_TYPE_MOVIE: Int = AudioAttributes.CONTENT_TYPE_MOVIE

    @Deprecated("Use {@link #AUDIO_CONTENT_TYPE_MOVIE} instead.")
    val CONTENT_TYPE_MOVIE: Int = AUDIO_CONTENT_TYPE_MOVIE

    /** See [AudioAttributes.CONTENT_TYPE_MUSIC].  */
    const val AUDIO_CONTENT_TYPE_MUSIC: Int = AudioAttributes.CONTENT_TYPE_MUSIC

    @Deprecated("Use {@link #AUDIO_CONTENT_TYPE_MUSIC} instead.")
    val CONTENT_TYPE_MUSIC: Int = AUDIO_CONTENT_TYPE_MUSIC

    /** See [AudioAttributes.CONTENT_TYPE_SONIFICATION].  */
    const val AUDIO_CONTENT_TYPE_SONIFICATION: Int = AudioAttributes.CONTENT_TYPE_SONIFICATION

    @Deprecated("Use {@link #AUDIO_CONTENT_TYPE_SONIFICATION} instead.")
    val CONTENT_TYPE_SONIFICATION: Int = AUDIO_CONTENT_TYPE_SONIFICATION

    /** See [AudioAttributes.CONTENT_TYPE_SPEECH].  */
    const val AUDIO_CONTENT_TYPE_SPEECH: Int = AudioAttributes.CONTENT_TYPE_SPEECH

    @Deprecated("Use {@link #AUDIO_CONTENT_TYPE_SPEECH} instead.")
    val CONTENT_TYPE_SPEECH: Int = AUDIO_CONTENT_TYPE_SPEECH

    /** See [AudioAttributes.CONTENT_TYPE_UNKNOWN].  */
    const val AUDIO_CONTENT_TYPE_UNKNOWN: Int = AudioAttributes.CONTENT_TYPE_UNKNOWN

    @Deprecated("Use {@link #AUDIO_CONTENT_TYPE_UNKNOWN} instead.")
    val CONTENT_TYPE_UNKNOWN: Int = AUDIO_CONTENT_TYPE_UNKNOWN

    /**
     * @see android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED
     */
    const val FLAG_AUDIBILITY_ENFORCED: Int = AudioAttributes.FLAG_AUDIBILITY_ENFORCED

    /**
     * @see android.media.AudioAttributes.USAGE_ALARM
     */
    const val USAGE_ALARM: Int = AudioAttributes.USAGE_ALARM

    /**
     * @see android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
     */
    const val USAGE_ASSISTANCE_ACCESSIBILITY: Int = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY

    /**
     * @see android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
     */
    const val USAGE_ASSISTANCE_NAVIGATION_GUIDANCE: Int = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE

    /**
     * @see android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
     */
    const val USAGE_ASSISTANCE_SONIFICATION: Int = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION

    /**
     * @see android.media.AudioAttributes.USAGE_ASSISTANT
     */
    const val USAGE_ASSISTANT: Int = AudioAttributes.USAGE_ASSISTANT

    /**
     * @see android.media.AudioAttributes.USAGE_GAME
     */
    const val USAGE_GAME: Int = AudioAttributes.USAGE_GAME

    /**
     * @see android.media.AudioAttributes.USAGE_MEDIA
     */
    const val USAGE_MEDIA: Int = AudioAttributes.USAGE_MEDIA

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION
     */
    const val USAGE_NOTIFICATION: Int = AudioAttributes.USAGE_NOTIFICATION

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED
     */
    const val USAGE_NOTIFICATION_COMMUNICATION_DELAYED: Int = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT
     */
    const val USAGE_NOTIFICATION_COMMUNICATION_INSTANT: Int = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST
     */
    const val USAGE_NOTIFICATION_COMMUNICATION_REQUEST: Int = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT
     */
    const val USAGE_NOTIFICATION_EVENT: Int = AudioAttributes.USAGE_NOTIFICATION_EVENT

    /**
     * @see android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE
     */
    const val USAGE_NOTIFICATION_RINGTONE: Int = AudioAttributes.USAGE_NOTIFICATION_RINGTONE

    /**
     * @see android.media.AudioAttributes.USAGE_UNKNOWN
     */
    const val USAGE_UNKNOWN: Int = AudioAttributes.USAGE_UNKNOWN

    /**
     * @see android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
     */
    const val USAGE_VOICE_COMMUNICATION: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION

    /**
     * @see android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING
     */
    const val USAGE_VOICE_COMMUNICATION_SIGNALLING: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING

    /** See [android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL].  */
    const val ALLOW_CAPTURE_BY_ALL: Int = AudioAttributes.ALLOW_CAPTURE_BY_ALL

    /** See [android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE].  */
    const val ALLOW_CAPTURE_BY_NONE: Int = AudioAttributes.ALLOW_CAPTURE_BY_NONE

    /** See [android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM].  */
    const val ALLOW_CAPTURE_BY_SYSTEM: Int = AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM

    /** Indicates that a buffer holds a synchronization sample.  */
    const val BUFFER_FLAG_KEY_FRAME: Int = MediaCodec.BUFFER_FLAG_KEY_FRAME

    /** Flag for empty buffers that signal that the end of the stream was reached.  */
    const val BUFFER_FLAG_END_OF_STREAM: Int = MediaCodec.BUFFER_FLAG_END_OF_STREAM

    /** Indicates that a buffer is known to contain the first media sample of the stream.  */
    const val BUFFER_FLAG_FIRST_SAMPLE: Int = 1 shl 27 // 0x08000000

    /** Indicates that a buffer has supplemental data.  */
    const val BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA: Int = 1 shl 28 // 0x10000000

    /** Indicates that a buffer is known to contain the last media sample of the stream.  */
    const val BUFFER_FLAG_LAST_SAMPLE: Int = 1 shl 29 // 0x20000000

    /** Indicates that a buffer is (at least partially) encrypted.  */
    const val BUFFER_FLAG_ENCRYPTED: Int = 1 shl 30 // 0x40000000

    /** Indicates that a buffer should be decoded but not rendered.  */
    const val BUFFER_FLAG_DECODE_ONLY: Int = 1 shl 31 // 0x80000000

    /** Video decoder output mode is not set.  */
    const val VIDEO_OUTPUT_MODE_NONE: Int = -1

    /** Video decoder output mode that outputs raw 4:2:0 YUV planes.  */
    const val VIDEO_OUTPUT_MODE_YUV: Int = 0

    /** Video decoder output mode that renders 4:2:0 YUV planes directly to a surface.  */
    const val VIDEO_OUTPUT_MODE_SURFACE_YUV: Int = 1

    /** See [MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT].  */
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT: Int = MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT

    /** See [MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING].  */
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING: Int = MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING

    /** A default video scaling mode for [MediaCodec]-based renderers.  */
    val VIDEO_SCALING_MODE_DEFAULT: Int = VIDEO_SCALING_MODE_SCALE_TO_FIT

    /**
     * Strategy to never call [Surface.setFrameRate]. Use this strategy if you prefer to call
     * [Surface.setFrameRate] directly from application code.
     */
    const val VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF: Int = Int.MIN_VALUE

    /**
     * Strategy to call [Surface.setFrameRate] with [ ][Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS] when the output frame rate is known.
     */
    const val VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS: Int = Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
    // LINT.IfChange(selection_flags)
    /** Indicates that the track should be selected if user preferences do not state otherwise.  */
    const val SELECTION_FLAG_DEFAULT: Int = 1

    /**
     * Indicates that the track should be selected if its language matches the language of the
     * selected audio track and user preferences do not state otherwise. Only applies to text tracks.
     *
     *
     * Tracks with this flag generally provide translation for elements that don't match the
     * declared language of the selected audio track (e.g. speech in an alien language). See [Netflix's summary](https://partnerhelp.netflixstudios.com/hc/en-us/articles/217558918)
     * for more info.
     */
    const val SELECTION_FLAG_FORCED: Int = 1 shl 1 // 2

    /**
     * Indicates that the player may choose to play the track in absence of an explicit user
     * preference.
     */
    const val SELECTION_FLAG_AUTOSELECT: Int = 1 shl 2 // 4

    /** Represents an undetermined language as an ISO 639-2 language code.  */
    const val LANGUAGE_UNDETERMINED: String = "und"

    /** Value representing a DASH manifest.  */
    const val CONTENT_TYPE_DASH: Int = 0

    @Deprecated("Use {@link #CONTENT_TYPE_DASH} instead.")
    val TYPE_DASH: Int = CONTENT_TYPE_DASH

    /** Value representing a Smooth Streaming manifest.  */
    const val CONTENT_TYPE_SS: Int = 1

    @Deprecated("Use {@link #CONTENT_TYPE_SS} instead.")
    val TYPE_SS: Int = CONTENT_TYPE_SS

    /** Value representing an HLS manifest.  */
    const val CONTENT_TYPE_HLS: Int = 2

    @Deprecated("Use {@link #CONTENT_TYPE_HLS} instead.")
    val TYPE_HLS: Int = CONTENT_TYPE_HLS

    /** Value representing an RTSP stream.  */
    const val CONTENT_TYPE_RTSP: Int = 3

    @Deprecated("Use {@link #CONTENT_TYPE_RTSP} instead.")
    val TYPE_RTSP: Int = CONTENT_TYPE_RTSP

    /** Value representing files other than DASH, HLS or Smooth Streaming manifests, or RTSP URIs.  */
    const val CONTENT_TYPE_OTHER: Int = 4

    @Deprecated("Use {@link #CONTENT_TYPE_OTHER} instead.")
    val TYPE_OTHER: Int = CONTENT_TYPE_OTHER

    /** A return value for methods where the end of an input was encountered.  */
    const val RESULT_END_OF_INPUT: Int = -1

    /**
     * A return value for methods where the length of parsed data exceeds the maximum length allowed.
     */
    const val RESULT_MAX_LENGTH_EXCEEDED: Int = -2

    /** A return value for methods where nothing was read.  */
    const val RESULT_NOTHING_READ: Int = -3

    /** A return value for methods where a buffer was read.  */
    const val RESULT_BUFFER_READ: Int = -4

    /** A return value for methods where a format was read.  */
    const val RESULT_FORMAT_READ: Int = -5

    /** A data type constant for data of unknown or unspecified type.  */
    const val DATA_TYPE_UNKNOWN: Int = 0

    /** A data type constant for media, typically containing media samples.  */
    const val DATA_TYPE_MEDIA: Int = 1

    /** A data type constant for media, typically containing only initialization data.  */
    const val DATA_TYPE_MEDIA_INITIALIZATION: Int = 2

    /** A data type constant for drm or encryption data.  */
    const val DATA_TYPE_DRM: Int = 3

    /** A data type constant for a manifest file.  */
    const val DATA_TYPE_MANIFEST: Int = 4

    /** A data type constant for time synchronization data.  */
    const val DATA_TYPE_TIME_SYNCHRONIZATION: Int = 5

    /** A data type constant for ads loader data.  */
    const val DATA_TYPE_AD: Int = 6

    /**
     * A data type constant for live progressive media streams, typically containing media samples.
     */
    const val DATA_TYPE_MEDIA_PROGRESSIVE_LIVE: Int = 7

    /**
     * Applications or extensions may define custom `DATA_TYPE_*` constants greater than or
     * equal to this value.
     */
    const val DATA_TYPE_CUSTOM_BASE: Int = 10000

    /** A type constant for a fake or empty track.  */
    const val TRACK_TYPE_NONE: Int = -2

    /** A type constant for tracks of unknown type.  */
    const val TRACK_TYPE_UNKNOWN: Int = -1

    /** A type constant for tracks of some default type, where the type itself is unknown.  */
    const val TRACK_TYPE_DEFAULT: Int = 0

    /** A type constant for audio tracks.  */
    const val TRACK_TYPE_AUDIO: Int = 1

    /** A type constant for video tracks.  */
    const val TRACK_TYPE_VIDEO: Int = 2

    /** A type constant for text tracks.  */
    const val TRACK_TYPE_TEXT: Int = 3

    /** A type constant for image tracks.  */
    const val TRACK_TYPE_IMAGE: Int = 4

    /** A type constant for metadata tracks.  */
    const val TRACK_TYPE_METADATA: Int = 5

    /** A type constant for camera motion tracks.  */
    const val TRACK_TYPE_CAMERA_MOTION: Int = 6

    /**
     * Applications or extensions may define custom `TRACK_TYPE_*` constants greater than or
     * equal to this value.
     */
    const val TRACK_TYPE_CUSTOM_BASE: Int = 10000

    /** A selection reason constant for selections whose reasons are unknown or unspecified.  */
    const val SELECTION_REASON_UNKNOWN: Int = 0

    /** A selection reason constant for an initial track selection.  */
    const val SELECTION_REASON_INITIAL: Int = 1

    /** A selection reason constant for an manual (i.e. user initiated) track selection.  */
    const val SELECTION_REASON_MANUAL: Int = 2

    /** A selection reason constant for an adaptive track selection.  */
    const val SELECTION_REASON_ADAPTIVE: Int = 3

    /** A selection reason constant for a trick play track selection.  */
    const val SELECTION_REASON_TRICK_PLAY: Int = 4

    /**
     * Applications or extensions may define custom `SELECTION_REASON_*` constants greater than
     * or equal to this value.
     */
    const val SELECTION_REASON_CUSTOM_BASE: Int = 10000

    /** A default size in bytes for an individual allocation that forms part of a larger buffer.  */
    val DEFAULT_BUFFER_SEGMENT_SIZE: Int = 64 * 1024

    /** A default seek back increment, in milliseconds.  */
    val DEFAULT_SEEK_BACK_INCREMENT_MS: Long = 5000

    /** A default seek forward increment, in milliseconds.  */
    val DEFAULT_SEEK_FORWARD_INCREMENT_MS: Long = 15000

    /**
     * A default maximum position for which a seek to previous will seek to the previous window, in
     * milliseconds.
     */
    val DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS: Long = 3000

    /** "cenc" scheme type name as defined in ISO/IEC 23001-7:2016.  */
    val CENC_TYPE_cenc: String = "cenc"

    /** "cbc1" scheme type name as defined in ISO/IEC 23001-7:2016.  */
    val CENC_TYPE_cbc1: String = "cbc1"

    /** "cens" scheme type name as defined in ISO/IEC 23001-7:2016.  */
    val CENC_TYPE_cens: String = "cens"

    /** "cbcs" scheme type name as defined in ISO/IEC 23001-7:2016.  */
    val CENC_TYPE_cbcs: String = "cbcs"

    /**
     * The Nil UUID as defined by [RFC4122](https://tools.ietf.org/html/rfc4122#section-4.1.7).
     */
    val UUID_NIL: UUID = UUID(0L, 0L)

    /**
     * UUID for the W3C [Common PSSH
 * box](https://w3c.github.io/encrypted-media/format-registry/initdata/cenc.html).
     */
    val COMMON_PSSH_UUID: UUID = UUID(0x1077EFECC0B24D02L, -0x531cc3e1ad1d04b5L)

    /**
     * UUID for the ClearKey DRM scheme.
     *
     *
     * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
     */
    val CLEARKEY_UUID: UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

    /**
     * UUID for the Widevine DRM scheme.
     *
     *
     * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
     */
    val WIDEVINE_UUID: UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    /**
     * UUID for the PlayReady DRM scheme.
     *
     *
     * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
     * provide PlayReady support.
     */
    val PLAYREADY_UUID: UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

    /** Indicates Monoscopic stereo layout, used with 360/3D/VR videos.  */
    const val STEREO_MODE_MONO: Int = 0

    /** Indicates Top-Bottom stereo layout, used with 360/3D/VR videos.  */
    const val STEREO_MODE_TOP_BOTTOM: Int = 1

    /** Indicates Left-Right stereo layout, used with 360/3D/VR videos.  */
    const val STEREO_MODE_LEFT_RIGHT: Int = 2

    /**
     * Indicates a stereo layout where the left and right eyes have separate meshes, used with
     * 360/3D/VR videos.
     */
    const val STEREO_MODE_STEREO_MESH: Int = 3

    /**
     * @see MediaFormat.COLOR_STANDARD_BT601_PAL
     */
    const val COLOR_SPACE_BT601: Int = MediaFormat.COLOR_STANDARD_BT601_PAL

    /**
     * @see MediaFormat.COLOR_STANDARD_BT709
     */
    const val COLOR_SPACE_BT709: Int = MediaFormat.COLOR_STANDARD_BT709

    /**
     * @see MediaFormat.COLOR_STANDARD_BT2020
     */
    const val COLOR_SPACE_BT2020: Int = MediaFormat.COLOR_STANDARD_BT2020

    /**
     * @see MediaFormat.COLOR_TRANSFER_SDR_VIDEO
     */
    const val COLOR_TRANSFER_SDR: Int = MediaFormat.COLOR_TRANSFER_SDR_VIDEO

    /**
     * @see MediaFormat.COLOR_TRANSFER_ST2084
     */
    const val COLOR_TRANSFER_ST2084: Int = MediaFormat.COLOR_TRANSFER_ST2084

    /**
     * @see MediaFormat.COLOR_TRANSFER_HLG
     */
    const val COLOR_TRANSFER_HLG: Int = MediaFormat.COLOR_TRANSFER_HLG

    /**
     * @see MediaFormat.COLOR_RANGE_LIMITED
     */
    const val COLOR_RANGE_LIMITED: Int = MediaFormat.COLOR_RANGE_LIMITED

    /**
     * @see MediaFormat.COLOR_RANGE_FULL
     */
    const val COLOR_RANGE_FULL: Int = MediaFormat.COLOR_RANGE_FULL

    /** Conventional rectangular projection.  */
    const val PROJECTION_RECTANGULAR: Int = 0

    /** Equirectangular spherical projection.  */
    const val PROJECTION_EQUIRECTANGULAR: Int = 1

    /** Cube map projection.  */
    const val PROJECTION_CUBEMAP: Int = 2

    /** 3-D mesh projection.  */
    const val PROJECTION_MESH: Int = 3

    /**
     * Priority for media playback.
     *
     *
     * Larger values indicate higher priorities.
     */
    const val PRIORITY_PLAYBACK: Int = 0

    /**
     * Priority for media downloading.
     *
     *
     * Larger values indicate higher priorities.
     */
    val PRIORITY_DOWNLOAD: Int = PRIORITY_PLAYBACK - 1000

    /** Unknown network type.  */
    const val NETWORK_TYPE_UNKNOWN: Int = 0

    /** No network connection.  */
    const val NETWORK_TYPE_OFFLINE: Int = 1

    /** Network type for a Wifi connection.  */
    const val NETWORK_TYPE_WIFI: Int = 2

    /** Network type for a 2G cellular connection.  */
    const val NETWORK_TYPE_2G: Int = 3

    /** Network type for a 3G cellular connection.  */
    const val NETWORK_TYPE_3G: Int = 4

    /** Network type for a 4G cellular connection.  */
    const val NETWORK_TYPE_4G: Int = 5

    /** Network type for a 5G stand-alone (SA) cellular connection.  */
    const val NETWORK_TYPE_5G_SA: Int = 9

    /** Network type for a 5G non-stand-alone (NSA) cellular connection.  */
    const val NETWORK_TYPE_5G_NSA: Int = 10

    /**
     * Network type for cellular connections which cannot be mapped to one of [ ][.NETWORK_TYPE_2G], [.NETWORK_TYPE_3G], or [.NETWORK_TYPE_4G].
     */
    const val NETWORK_TYPE_CELLULAR_UNKNOWN: Int = 6

    /** Network type for an Ethernet connection.  */
    const val NETWORK_TYPE_ETHERNET: Int = 7

    /** Network type for other connections which are not Wifi or cellular (e.g. VPN, Bluetooth).  */
    const val NETWORK_TYPE_OTHER: Int = 8

    /**
     * A wake mode that will not cause the player to hold any locks.
     *
     *
     * This is suitable for applications that do not play media with the screen off.
     */
    const val WAKE_MODE_NONE: Int = 0

    /**
     * A wake mode that will cause the player to hold a [android.os.PowerManager.WakeLock]
     * during playback.
     *
     *
     * This is suitable for applications that play media with the screen off and do not load media
     * over wifi.
     */
    const val WAKE_MODE_LOCAL: Int = 1

    /**
     * A wake mode that will cause the player to hold a [android.os.PowerManager.WakeLock] and a
     * [android.net.wifi.WifiManager.WifiLock] during playback.
     *
     *
     * This is suitable for applications that play media with the screen off and may load media
     * over wifi.
     */
    const val WAKE_MODE_NETWORK: Int = 2
    // LINT.IfChange(role_flags)
    /** Indicates a main track.  */
    const val ROLE_FLAG_MAIN: Int = 1

    /**
     * Indicates an alternate track. For example a video track recorded from an different view point
     * than the main track(s).
     */
    const val ROLE_FLAG_ALTERNATE: Int = 1 shl 1

    /**
     * Indicates a supplementary track, meaning the track has lower importance than the main track(s).
     * For example a video track that provides a visual accompaniment to a main audio track.
     */
    const val ROLE_FLAG_SUPPLEMENTARY: Int = 1 shl 2

    /** Indicates the track contains commentary, for example from the director.  */
    const val ROLE_FLAG_COMMENTARY: Int = 1 shl 3

    /**
     * Indicates the track is in a different language from the original, for example dubbed audio or
     * translated captions.
     */
    const val ROLE_FLAG_DUB: Int = 1 shl 4

    /** Indicates the track contains information about a current emergency.  */
    const val ROLE_FLAG_EMERGENCY: Int = 1 shl 5

    /**
     * Indicates the track contains captions. This flag may be set on video tracks to indicate the
     * presence of burned in captions.
     */
    const val ROLE_FLAG_CAPTION: Int = 1 shl 6

    /**
     * Indicates the track contains subtitles. This flag may be set on video tracks to indicate the
     * presence of burned in subtitles.
     */
    const val ROLE_FLAG_SUBTITLE: Int = 1 shl 7

    /** Indicates the track contains a visual sign-language interpretation of an audio track.  */
    const val ROLE_FLAG_SIGN: Int = 1 shl 8

    /** Indicates the track contains an audio or textual description of a video track.  */
    const val ROLE_FLAG_DESCRIBES_VIDEO: Int = 1 shl 9

    /** Indicates the track contains a textual description of music and sound.  */
    const val ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND: Int = 1 shl 10

    /** Indicates the track is designed for improved intelligibility of dialogue.  */
    const val ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY: Int = 1 shl 11

    /** Indicates the track contains a transcription of spoken dialog.  */
    const val ROLE_FLAG_TRANSCRIBES_DIALOG: Int = 1 shl 12

    /** Indicates the track contains a text that has been edited for ease of reading.  */
    const val ROLE_FLAG_EASY_TO_READ: Int = 1 shl 13

    /** Indicates the track is intended for trick play.  */
    const val ROLE_FLAG_TRICK_PLAY: Int = 1 shl 14
    // TODO(b/172315872) Renderer was a link. Link to equivalent concept or remove @code.
    /** The `Renderer` is capable of rendering the format.  */
    const val FORMAT_HANDLED: Int = 4

    /**
     * The `Renderer` is capable of rendering formats with the same MIME type, but the
     * properties of the format exceed the renderer's capabilities. There is a chance the renderer
     * will be able to play the format in practice because some renderers report their capabilities
     * conservatively, but the expected outcome is that playback will fail.
     *
     *
     * Example: The `Renderer` is capable of rendering H264 and the format's MIME type is
     * `MimeTypes#VIDEO_H264`, but the format's resolution exceeds the maximum limit supported
     * by the underlying H264 decoder.
     */
    const val FORMAT_EXCEEDS_CAPABILITIES: Int = 3

    /**
     * The `Renderer` is capable of rendering formats with the same MIME type, but is not
     * capable of rendering the format because the format's drm protection is not supported.
     *
     *
     * Example: The `Renderer` is capable of rendering H264 and the format's MIME type is
     * [MimeTypes.VIDEO_H264], but the format indicates PlayReady drm protection whereas the
     * renderer only supports Widevine.
     */
    const val FORMAT_UNSUPPORTED_DRM: Int = 2

    /**
     * The `Renderer` is a general purpose renderer for formats of the same top-level type, but
     * is not capable of rendering the format or any other format with the same MIME type because the
     * sub-type is not supported.
     *
     *
     * Example: The `Renderer` is a general purpose audio renderer and the format's MIME type
     * matches audio/[subtype], but there does not exist a suitable decoder for [subtype].
     */
    const val FORMAT_UNSUPPORTED_SUBTYPE: Int = 1

    /**
     * The `Renderer` is not capable of rendering the format, either because it does not support
     * the format's top-level type, or because it's a specialized renderer for a different MIME type.
     *
     *
     * Example: The `Renderer` is a general purpose video renderer, but the format has an
     * audio MIME type.
     */
    const val FORMAT_UNSUPPORTED_TYPE: Int = 0

    @InlineMe(replacement = "Util.usToMs(timeUs)", imports = ["com.google.android.exoplayer2.util.Util"])
    @Deprecated("Use {@link Util#usToMs(long)}.")
    fun usToMs(timeUs: Long): Long {
        return Util.usToMs(timeUs)
    }

    @InlineMe(replacement = "Util.msToUs(timeMs)", imports = ["com.google.android.exoplayer2.util.Util"])
    @Deprecated("Use {@link Util#msToUs(long)}.")
    fun msToUs(timeMs: Long): Long {
        return Util.msToUs(timeMs)
    }

    @InlineMe(replacement = "Util.generateAudioSessionIdV21(context)", imports = ["com.google.android.exoplayer2.util.Util"])
    @RequiresApi(21)
    @Deprecated("Use {@link Util#generateAudioSessionIdV21(Context)}.")
    fun generateAudioSessionIdV21(context: Context): Int {
        return Util.generateAudioSessionIdV21(context)
    }

    @InlineMe(replacement = "Util.getFormatSupportString(formatSupport)", imports = ["com.google.android.exoplayer2.util.Util"])
    @Deprecated("Use {@link Util#getFormatSupportString(int)}.")
    fun getFormatSupportString(@FormatSupport formatSupport: Int): String? {
        return Util.getFormatSupportString(formatSupport)
    }

    @InlineMe(replacement = "Util.getErrorCodeForMediaDrmErrorCode(mediaDrmErrorCode)", imports = ["com.google.android.exoplayer2.util.Util"])
    @Deprecated("Use {@link Util#getErrorCodeForMediaDrmErrorCode(int)}.")
    fun getErrorCodeForMediaDrmErrorCode(
            mediaDrmErrorCode: Int): @PlaybackException.ErrorCode Int {
        return Util.getErrorCodeForMediaDrmErrorCode(mediaDrmErrorCode)
    }

    /**
     * Types of crypto implementation. May be one of [.CRYPTO_TYPE_NONE], [ ][.CRYPTO_TYPE_UNSUPPORTED] or [.CRYPTO_TYPE_FRAMEWORK]. May also be an app-defined value
     * (see [.CRYPTO_TYPE_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [CRYPTO_TYPE_UNSUPPORTED, CRYPTO_TYPE_NONE, CRYPTO_TYPE_FRAMEWORK])
    annotation class CryptoType constructor()

    /**
     * Crypto modes for a codec. One of [.CRYPTO_MODE_UNENCRYPTED], [.CRYPTO_MODE_AES_CTR]
     * or [.CRYPTO_MODE_AES_CBC].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([CRYPTO_MODE_UNENCRYPTED, CRYPTO_MODE_AES_CTR, CRYPTO_MODE_AES_CBC])
    annotation class CryptoMode constructor()

    /**
     * Represents an audio encoding, or an invalid or unset value. One of [Format.NO_VALUE],
     * [.ENCODING_INVALID], [.ENCODING_PCM_8BIT], [.ENCODING_PCM_16BIT], [ ][.ENCODING_PCM_16BIT_BIG_ENDIAN], [.ENCODING_PCM_24BIT], [.ENCODING_PCM_32BIT],
     * [.ENCODING_PCM_FLOAT], [.ENCODING_MP3], [.ENCODING_AC3], [ ][.ENCODING_E_AC3], [.ENCODING_E_AC3_JOC], [.ENCODING_AC4], [.ENCODING_DTS],
     * [.ENCODING_DTS_HD] or [.ENCODING_DOLBY_TRUEHD].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, ENCODING_INVALID, ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, ENCODING_PCM_16BIT_BIG_ENDIAN, ENCODING_PCM_24BIT, ENCODING_PCM_32BIT, ENCODING_PCM_FLOAT, ENCODING_MP3, ENCODING_AAC_LC, ENCODING_AAC_HE_V1, ENCODING_AAC_HE_V2, ENCODING_AAC_XHE, ENCODING_AAC_ELD, ENCODING_AAC_ER_BSAC, ENCODING_AC3, ENCODING_E_AC3, ENCODING_E_AC3_JOC, ENCODING_AC4, ENCODING_DTS, ENCODING_DTS_HD, ENCODING_DOLBY_TRUEHD])
    annotation class Encoding constructor()

    /**
     * Represents a PCM audio encoding, or an invalid or unset value. One of [Format.NO_VALUE],
     * [.ENCODING_INVALID], [.ENCODING_PCM_8BIT], [.ENCODING_PCM_16BIT], [ ][.ENCODING_PCM_16BIT_BIG_ENDIAN], [.ENCODING_PCM_24BIT], [.ENCODING_PCM_32BIT],
     * [.ENCODING_PCM_FLOAT].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, ENCODING_INVALID, ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, ENCODING_PCM_16BIT_BIG_ENDIAN, ENCODING_PCM_24BIT, ENCODING_PCM_32BIT, ENCODING_PCM_FLOAT])
    annotation class PcmEncoding constructor()

    /** Represents the behavior affecting whether spatialization will be used.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([SPATIALIZATION_BEHAVIOR_AUTO, SPATIALIZATION_BEHAVIOR_NEVER])
    annotation class SpatializationBehavior constructor()

    /**
     * Stream types for an [android.media.AudioTrack]. One of [.STREAM_TYPE_ALARM], [ ][.STREAM_TYPE_DTMF], [.STREAM_TYPE_MUSIC], [.STREAM_TYPE_NOTIFICATION], [ ][.STREAM_TYPE_RING], [.STREAM_TYPE_SYSTEM], [.STREAM_TYPE_VOICE_CALL] or [ ][.STREAM_TYPE_DEFAULT].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @SuppressLint("UniqueConstants") // Intentional duplication to set STREAM_TYPE_DEFAULT.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([STREAM_TYPE_ALARM, STREAM_TYPE_DTMF, STREAM_TYPE_MUSIC, STREAM_TYPE_NOTIFICATION, STREAM_TYPE_RING, STREAM_TYPE_SYSTEM, STREAM_TYPE_VOICE_CALL, STREAM_TYPE_DEFAULT])
    annotation class StreamType constructor()

    /**
     * Content types for audio attributes. One of:
     *
     *
     *  * [.AUDIO_CONTENT_TYPE_MOVIE]
     *  * [.AUDIO_CONTENT_TYPE_MUSIC]
     *  * [.AUDIO_CONTENT_TYPE_SONIFICATION]
     *  * [.AUDIO_CONTENT_TYPE_SPEECH]
     *  * [.AUDIO_CONTENT_TYPE_UNKNOWN]
     *
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([AUDIO_CONTENT_TYPE_MOVIE, AUDIO_CONTENT_TYPE_MUSIC, AUDIO_CONTENT_TYPE_SONIFICATION, AUDIO_CONTENT_TYPE_SPEECH, AUDIO_CONTENT_TYPE_UNKNOWN])
    annotation class AudioContentType constructor()

    /**
     * Flags for audio attributes. Possible flag value is [.FLAG_AUDIBILITY_ENFORCED].
     *
     *
     * Note that `FLAG_HW_AV_SYNC` is not available because the player takes care of setting
     * the flag when tunneling is enabled via a track selector.
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(flag = true, value = [FLAG_AUDIBILITY_ENFORCED])
    annotation class AudioFlags constructor()

    /**
     * Usage types for audio attributes. One of [.USAGE_ALARM], [ ][.USAGE_ASSISTANCE_ACCESSIBILITY], [.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE], [ ][.USAGE_ASSISTANCE_SONIFICATION], [.USAGE_ASSISTANT], [.USAGE_GAME], [ ][.USAGE_MEDIA], [.USAGE_NOTIFICATION], [.USAGE_NOTIFICATION_COMMUNICATION_DELAYED],
     * [.USAGE_NOTIFICATION_COMMUNICATION_INSTANT], [ ][.USAGE_NOTIFICATION_COMMUNICATION_REQUEST], [.USAGE_NOTIFICATION_EVENT], [ ][.USAGE_NOTIFICATION_RINGTONE], [.USAGE_UNKNOWN], [.USAGE_VOICE_COMMUNICATION] or
     * [.USAGE_VOICE_COMMUNICATION_SIGNALLING].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([USAGE_ALARM, USAGE_ASSISTANCE_ACCESSIBILITY, USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, USAGE_ASSISTANCE_SONIFICATION, USAGE_ASSISTANT, USAGE_GAME, USAGE_MEDIA, USAGE_NOTIFICATION, USAGE_NOTIFICATION_COMMUNICATION_DELAYED, USAGE_NOTIFICATION_COMMUNICATION_INSTANT, USAGE_NOTIFICATION_COMMUNICATION_REQUEST, USAGE_NOTIFICATION_EVENT, USAGE_NOTIFICATION_RINGTONE, USAGE_UNKNOWN, USAGE_VOICE_COMMUNICATION, USAGE_VOICE_COMMUNICATION_SIGNALLING])
    annotation class AudioUsage constructor()

    /**
     * Capture policies for audio attributes. One of [.ALLOW_CAPTURE_BY_ALL], [ ][.ALLOW_CAPTURE_BY_NONE] or [.ALLOW_CAPTURE_BY_SYSTEM].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([ALLOW_CAPTURE_BY_ALL, ALLOW_CAPTURE_BY_NONE, ALLOW_CAPTURE_BY_SYSTEM])
    annotation class AudioAllowedCapturePolicy constructor()

    /**
     * Flags which can apply to a buffer containing a media sample. Possible flag values are [ ][.BUFFER_FLAG_KEY_FRAME], [.BUFFER_FLAG_END_OF_STREAM], [.BUFFER_FLAG_FIRST_SAMPLE],
     * [.BUFFER_FLAG_LAST_SAMPLE], [.BUFFER_FLAG_ENCRYPTED] and [ ][.BUFFER_FLAG_DECODE_ONLY].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(flag = true, value = [BUFFER_FLAG_KEY_FRAME, BUFFER_FLAG_END_OF_STREAM, BUFFER_FLAG_FIRST_SAMPLE, BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA, BUFFER_FLAG_LAST_SAMPLE, BUFFER_FLAG_ENCRYPTED, BUFFER_FLAG_DECODE_ONLY])
    annotation class BufferFlags constructor()

    /**
     * Video decoder output modes. Possible modes are [.VIDEO_OUTPUT_MODE_NONE], [ ][.VIDEO_OUTPUT_MODE_YUV] and [.VIDEO_OUTPUT_MODE_SURFACE_YUV].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [VIDEO_OUTPUT_MODE_NONE, VIDEO_OUTPUT_MODE_YUV, VIDEO_OUTPUT_MODE_SURFACE_YUV])
    annotation class VideoOutputMode constructor()

    /**
     * Video scaling modes for [MediaCodec]-based renderers. One of [ ][.VIDEO_SCALING_MODE_SCALE_TO_FIT], [.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING] or
     * [.VIDEO_SCALING_MODE_DEFAULT].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @SuppressLint("UniqueConstants") // Intentional duplication to set VIDEO_SCALING_MODE_DEFAULT.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([VIDEO_SCALING_MODE_SCALE_TO_FIT, VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING, VIDEO_SCALING_MODE_DEFAULT])
    annotation class VideoScalingMode constructor()

    /** Strategies for calling [Surface.setFrameRate].  */ // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF, VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS])
    annotation class VideoChangeFrameRateStrategy constructor()

    /**
     * Track selection flags. Possible flag values are [.SELECTION_FLAG_DEFAULT], [ ][.SELECTION_FLAG_FORCED] and [.SELECTION_FLAG_AUTOSELECT].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(flag = true, value = [SELECTION_FLAG_DEFAULT, SELECTION_FLAG_FORCED, SELECTION_FLAG_AUTOSELECT])
    annotation class SelectionFlags constructor()

    /**
     * Represents a streaming or other media type. One of:
     *
     *
     *  * [.CONTENT_TYPE_DASH]
     *  * [.CONTENT_TYPE_SS]
     *  * [.CONTENT_TYPE_HLS]
     *  * [.CONTENT_TYPE_RTSP]
     *  * [.CONTENT_TYPE_OTHER]
     *
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([CONTENT_TYPE_DASH, CONTENT_TYPE_SS, CONTENT_TYPE_HLS, CONTENT_TYPE_RTSP, CONTENT_TYPE_OTHER])
    annotation class ContentType constructor()

    /**
     * Represents a type of data. May be one of [.DATA_TYPE_UNKNOWN], [.DATA_TYPE_MEDIA],
     * [.DATA_TYPE_MEDIA_INITIALIZATION], [.DATA_TYPE_DRM], [.DATA_TYPE_MANIFEST],
     * [.DATA_TYPE_TIME_SYNCHRONIZATION], [.DATA_TYPE_AD], or [ ][.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE]. May also be an app-defined value (see [ ][.DATA_TYPE_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [DATA_TYPE_UNKNOWN, DATA_TYPE_MEDIA, DATA_TYPE_MEDIA_INITIALIZATION, DATA_TYPE_DRM, DATA_TYPE_MANIFEST, DATA_TYPE_TIME_SYNCHRONIZATION, DATA_TYPE_AD, DATA_TYPE_MEDIA_PROGRESSIVE_LIVE])
    annotation class DataType constructor()

    /**
     * Represents a type of media track. May be one of [.TRACK_TYPE_UNKNOWN], [ ][.TRACK_TYPE_DEFAULT], [.TRACK_TYPE_AUDIO], [.TRACK_TYPE_VIDEO], [ ][.TRACK_TYPE_TEXT], [.TRACK_TYPE_IMAGE], [.TRACK_TYPE_METADATA], [ ][.TRACK_TYPE_CAMERA_MOTION] or [.TRACK_TYPE_NONE]. May also be an app-defined value (see
     * [.TRACK_TYPE_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [TRACK_TYPE_UNKNOWN, TRACK_TYPE_DEFAULT, TRACK_TYPE_AUDIO, TRACK_TYPE_VIDEO, TRACK_TYPE_TEXT, TRACK_TYPE_IMAGE, TRACK_TYPE_METADATA, TRACK_TYPE_CAMERA_MOTION, TRACK_TYPE_NONE])
    annotation class TrackType constructor()

    /**
     * Represents a reason for selection. May be one of [.SELECTION_REASON_UNKNOWN], [ ][.SELECTION_REASON_INITIAL], [.SELECTION_REASON_MANUAL], [ ][.SELECTION_REASON_ADAPTIVE] or [.SELECTION_REASON_TRICK_PLAY]. May also be an app-defined
     * value (see [.SELECTION_REASON_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [SELECTION_REASON_UNKNOWN, SELECTION_REASON_INITIAL, SELECTION_REASON_MANUAL, SELECTION_REASON_ADAPTIVE, SELECTION_REASON_TRICK_PLAY])
    annotation class SelectionReason constructor()

    /**
     * The stereo mode for 360/3D/VR videos. One of [Format.NO_VALUE], [ ][.STEREO_MODE_MONO], [.STEREO_MODE_TOP_BOTTOM], [.STEREO_MODE_LEFT_RIGHT] or [ ][.STEREO_MODE_STEREO_MESH].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, STEREO_MODE_MONO, STEREO_MODE_TOP_BOTTOM, STEREO_MODE_LEFT_RIGHT, STEREO_MODE_STEREO_MESH])
    annotation class StereoMode constructor()
    // LINT.IfChange(color_space)
    /**
     * Video colorspaces. One of [Format.NO_VALUE], [.COLOR_SPACE_BT601], [ ][.COLOR_SPACE_BT709] or [.COLOR_SPACE_BT2020].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, COLOR_SPACE_BT601, COLOR_SPACE_BT709, COLOR_SPACE_BT2020])
    annotation class ColorSpace constructor()
    // LINT.IfChange(color_transfer)
    /**
     * Video color transfer characteristics. One of [Format.NO_VALUE], [ ][.COLOR_TRANSFER_SDR], [.COLOR_TRANSFER_ST2084] or [.COLOR_TRANSFER_HLG].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, COLOR_TRANSFER_SDR, COLOR_TRANSFER_ST2084, COLOR_TRANSFER_HLG])
    annotation class ColorTransfer constructor()
    // LINT.IfChange(color_range)
    /**
     * Video color range. One of [Format.NO_VALUE], [.COLOR_RANGE_LIMITED] or [ ][.COLOR_RANGE_FULL].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, COLOR_RANGE_LIMITED, COLOR_RANGE_FULL])
    annotation class ColorRange constructor()

    /** Video projection types.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Format.Companion.NO_VALUE, PROJECTION_RECTANGULAR, PROJECTION_EQUIRECTANGULAR, PROJECTION_CUBEMAP, PROJECTION_MESH])
    annotation class Projection constructor()

    /**
     * Network connection type. One of [.NETWORK_TYPE_UNKNOWN], [.NETWORK_TYPE_OFFLINE],
     * [.NETWORK_TYPE_WIFI], [.NETWORK_TYPE_2G], [.NETWORK_TYPE_3G], [ ][.NETWORK_TYPE_4G], [.NETWORK_TYPE_5G_SA], [.NETWORK_TYPE_5G_NSA], [ ][.NETWORK_TYPE_CELLULAR_UNKNOWN], [.NETWORK_TYPE_ETHERNET] or [.NETWORK_TYPE_OTHER].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([NETWORK_TYPE_UNKNOWN, NETWORK_TYPE_OFFLINE, NETWORK_TYPE_WIFI, NETWORK_TYPE_2G, NETWORK_TYPE_3G, NETWORK_TYPE_4G, NETWORK_TYPE_5G_SA, NETWORK_TYPE_5G_NSA, NETWORK_TYPE_CELLULAR_UNKNOWN, NETWORK_TYPE_ETHERNET, NETWORK_TYPE_OTHER])
    annotation class NetworkType constructor()

    /**
     * Mode specifying whether the player should hold a WakeLock and a WifiLock. One of [ ][.WAKE_MODE_NONE], [.WAKE_MODE_LOCAL] or [.WAKE_MODE_NETWORK].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([WAKE_MODE_NONE, WAKE_MODE_LOCAL, WAKE_MODE_NETWORK])
    annotation class WakeMode constructor()

    /**
     * Track role flags. Possible flag values are [.ROLE_FLAG_MAIN], [ ][.ROLE_FLAG_ALTERNATE], [.ROLE_FLAG_SUPPLEMENTARY], [.ROLE_FLAG_COMMENTARY], [ ][.ROLE_FLAG_DUB], [.ROLE_FLAG_EMERGENCY], [.ROLE_FLAG_CAPTION], [ ][.ROLE_FLAG_SUBTITLE], [.ROLE_FLAG_SIGN], [.ROLE_FLAG_DESCRIBES_VIDEO], [ ][.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND], [.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY],
     * [.ROLE_FLAG_TRANSCRIBES_DIALOG], [.ROLE_FLAG_EASY_TO_READ] and [ ][.ROLE_FLAG_TRICK_PLAY].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(flag = true, value = [ROLE_FLAG_MAIN, ROLE_FLAG_ALTERNATE, ROLE_FLAG_SUPPLEMENTARY, ROLE_FLAG_COMMENTARY, ROLE_FLAG_DUB, ROLE_FLAG_EMERGENCY, ROLE_FLAG_CAPTION, ROLE_FLAG_SUBTITLE, ROLE_FLAG_SIGN, ROLE_FLAG_DESCRIBES_VIDEO, ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND, ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY, ROLE_FLAG_TRANSCRIBES_DIALOG, ROLE_FLAG_EASY_TO_READ, ROLE_FLAG_TRICK_PLAY])
    annotation class RoleFlags constructor()

    /**
     * Level of renderer support for a format. One of [.FORMAT_HANDLED], [ ][.FORMAT_EXCEEDS_CAPABILITIES], [.FORMAT_UNSUPPORTED_DRM], [ ][.FORMAT_UNSUPPORTED_SUBTYPE] or [.FORMAT_UNSUPPORTED_TYPE].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([FORMAT_HANDLED, FORMAT_EXCEEDS_CAPABILITIES, FORMAT_UNSUPPORTED_DRM, FORMAT_UNSUPPORTED_SUBTYPE, FORMAT_UNSUPPORTED_TYPE])
    annotation class FormatSupport constructor()
}