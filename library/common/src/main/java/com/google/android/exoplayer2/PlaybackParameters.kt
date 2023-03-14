/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2import

import android.os.Bundle
import androidx.annotation.*
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.util.*
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

androidx.annotation .IntDef
import com.google.android.exoplayer2.ui.AdOverlayInfo
import android.view.ViewGroup
import com.google.common.collect.ImmutableList
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import android.os.Parcelable
import android.os.Parcel
import android.text.TextUtils
import com.google.android.exoplayer2.text.span.TextAnnotation
import com.google.android.exoplayer2.text.span.LanguageFeatureSpan
import android.text.Spannable
import com.google.android.exoplayer2.text.span.TextEmphasisSpan.MarkFill
import com.google.android.exoplayer2.text.span.TextEmphasisSpan
import android.graphics.Bitmap
import com.google.android.exoplayer2.text.Cue.LineType
import com.google.android.exoplayer2.text.Cue.TextSizeType
import com.google.android.exoplayer2.text.Cue.VerticalType
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.text.Cue
import org.checkerframework.dataflow.qual.Pure
import android.os.Bundle
import android.text.Spanned
import android.text.SpannedString
import com.google.android.exoplayer2.text.CueGroup
import android.content.IntentFilter
import android.content.Intent
import android.content.ComponentName
import android.app.Activity
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration
import android.provider.MediaStore
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.initialization.qual.UnknownInitialization
import android.os.Looper
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Futures
import org.checkerframework.checker.nullness.qual.PolyNull
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import com.google.android.exoplayer2.PlaybackException
import android.telephony.TelephonyManager
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import com.google.android.exoplayer2.Player
import android.Manifest.permission
import android.security.NetworkSecurityPolicy
import android.opengl.EGL14
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.Api17
import android.opengl.GLU
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.SparseBooleanArray
import com.google.android.exoplayer2.util.GlProgram.Uniform
import com.google.android.exoplayer2.util.MimeTypes.CustomMimeType
import com.google.android.exoplayer2.util.MimeTypes.Mp4aObjectType
import com.google.android.exoplayer2.util.AtomicFile.AtomicFileOutputStream
import android.os.IBinder
import kotlin.annotations.jvm.UnderMigration
import kotlin.annotations.jvm.MigrationStatus
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import android.util.SparseArray
import com.google.android.exoplayer2.video.ColorInfo
import android.media.MediaFormat
import android.app.NotificationManager
import com.google.android.exoplayer2.util.NotificationUtil.Importance
import android.app.NotificationChannel
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
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
import android.media.MediaCodec
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
import android.view.SurfaceHolder
import android.view.TextureView
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
import android.media.MediaPlayer

/** Parameters that apply to playback, including speed setting.  */
class PlaybackParameters @JvmOverloads constructor(
        @FloatRange(from = 0, fromInclusive = false) speed: Float,
        @FloatRange(from = 0, fromInclusive = false) pitch: Float =  /* pitch= */1f) : Bundleable {
    /** The factor by which playback will be sped up.  */
    val speed: Float

    /** The factor by which pitch will be shifted.  */
    val pitch: Float
    private val scaledUsPerMs: Int

    /**
     * Returns the media time in microseconds that will elapse in `timeMs` milliseconds of
     * wallclock time.
     *
     * @param timeMs The time to scale, in milliseconds.
     * @return The scaled time, in microseconds.
     */
    fun getMediaTimeUsForPlayoutTimeMs(timeMs: Long): Long {
        return timeMs * scaledUsPerMs
    }

    /**
     * Returns a copy with the given speed.
     *
     * @param speed The new speed. Must be greater than zero.
     * @return The copied playback parameters.
     */
    @CheckResult
    fun withSpeed(@FloatRange(from = 0, fromInclusive = false) speed: Float): PlaybackParameters {
        return PlaybackParameters(speed, pitch)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: PlaybackParameters = obj as PlaybackParameters
        return speed == other.speed && pitch == other.pitch
    }

    public override fun hashCode(): Int {
        var result: Int = 17
        result = 31 * result + java.lang.Float.floatToRawIntBits(speed)
        result = 31 * result + java.lang.Float.floatToRawIntBits(pitch)
        return result
    }

    public override fun toString(): String {
        return Util.formatInvariant("PlaybackParameters(speed=%.2f, pitch=%.2f)", speed, pitch)
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([PlaybackParameters.Companion.FIELD_SPEED, PlaybackParameters.Companion.FIELD_PITCH])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_SPEED), speed)
        bundle.putFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_PITCH), pitch)
        return bundle
    }
    /**
     * Creates new playback parameters that set the playback speed/pitch.
     *
     * @param speed The factor by which playback will be sped up. Must be greater than zero.
     * @param pitch The factor by which the pitch of audio will be adjusted. Must be greater than
     * zero. Useful values are `1` (to time-stretch audio) and the same value as passed in
     * as the `speed` (to resample audio, which is useful for slow-motion videos).
     */
    /**
     * Creates new playback parameters that set the playback speed. The pitch of audio will not be
     * adjusted, so the effect is to time-stretch the audio.
     *
     * @param speed The factor by which playback will be sped up. Must be greater than zero.
     */
    init {
        Assertions.checkArgument(speed > 0)
        Assertions.checkArgument(pitch > 0)
        this.speed = speed
        this.pitch = pitch
        scaledUsPerMs = Math.round(speed * 1000f)
    }

    companion object {
        /** The default playback parameters: real-time playback with no silence skipping.  */
        val DEFAULT: PlaybackParameters = PlaybackParameters( /* speed= */1f)
        private val FIELD_SPEED: Int = 0
        private val FIELD_PITCH: Int = 1

        /** Object that can restore [PlaybackParameters] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<PlaybackParameters> = Bundleable.Creator({ bundle: Bundle ->
            val speed: Float = bundle.getFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_SPEED),  /* defaultValue= */1f)
            val pitch: Float = bundle.getFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_PITCH),  /* defaultValue= */1f)
            PlaybackParameters(speed, pitch)
        })

        private fun keyForField(field: @PlaybackParameters.FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}