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
package com.google.android.exoplayer2import

import android.os.Bundle
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.Assertions
import com.google.common.base.Objects
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
import com.google.android.exoplayer2.util.Assertions
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
import com.google.android.exoplayer2.text.Cue
import androidx.annotation.ColorInt
import org.checkerframework.dataflow.qual.Pure
import android.os.Bundle
import android.text.Spanned
import android.text.SpannedString
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.util.BundleableUtil
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
import androidx.annotation.RequiresApi
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import android.telephony.TelephonyManager
import com.google.android.exoplayer2.util.ParsableByteArray
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import android.Manifest.permission
import android.security.NetworkSecurityPolicy
import com.google.android.exoplayer2.util.HandlerWrapper
import android.opengl.EGL14
import com.google.android.exoplayer2.util.GlUtil
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.Api17
import android.opengl.GLU
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.SparseBooleanArray
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.util.UriUtil
import com.google.android.exoplayer2.util.FileTypes
import com.google.android.exoplayer2.util.GlProgram.Uniform
import com.google.android.exoplayer2.util.GlProgram
import com.google.android.exoplayer2.util.MimeTypes.CustomMimeType
import com.google.android.exoplayer2.util.MimeTypes.Mp4aObjectType
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.AtomicFile.AtomicFileOutputStream
import android.os.IBinder
import com.google.android.exoplayer2.util.BundleUtil
import kotlin.annotations.jvm.UnderMigration
import kotlin.annotations.jvm.MigrationStatus
import com.google.android.exoplayer2.util.ColorParser
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import com.google.android.exoplayer2.util.ListenerSet
import com.google.android.exoplayer2.util.SurfaceInfo
import com.google.android.exoplayer2.util.SystemHandlerWrapper
import android.util.SparseArray
import com.google.android.exoplayer2.util.FrameProcessingException
import com.google.android.exoplayer2.util.DebugViewProvider
import com.google.android.exoplayer2.video.ColorInfo
import com.google.android.exoplayer2.util.RepeatModeUtil
import android.media.MediaFormat
import com.google.android.exoplayer2.util.MediaFormatUtil
import com.google.android.exoplayer2.util.TimedValueQueue
import android.app.NotificationManager
import androidx.annotation.StringRes
import com.google.android.exoplayer2.util.NotificationUtil.Importance
import android.app.NotificationChannel
import com.google.android.exoplayer2.util.NotificationUtil
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import com.google.android.exoplayer2.util.ParsableBitArray
import com.google.android.exoplayer2.util.TimestampAdjuster
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import com.google.android.exoplayer2.util.XmlPullParserUtil
import com.google.android.exoplayer2.util.UnknownNull
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
import com.google.android.exoplayer2.util.CodecSpecificDataUtil
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Api32
import com.google.android.exoplayer2.audio.AudioAttributes.AudioAttributesV21
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import androidx.annotation.FloatRange
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
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import android.view.SurfaceHolder
import android.view.TextureView
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import androidx.annotation.CallSuper
import android.media.MediaPlayer

/**
 * A rating expressed as "heart" or "no heart". It can be used to indicate whether the content is a
 * favorite.
 */
class HeartRating : Rating {
    override val isRated: Boolean
    /** Returns whether the rating is "heart".  */
    val isHeart: Boolean

    /** Creates a unrated instance.  */
    constructor() {
        isRated = false
        isHeart = false
    }

    /**
     * Creates a rated instance.
     *
     * @param isHeart `true` for "heart", `false` for "no heart".
     */
    constructor(isHeart: Boolean) {
        isRated = true
        this.isHeart = isHeart
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(isRated, isHeart)
    }

    public override fun equals(obj: Any?): Boolean {
        if (!(obj is HeartRating)) {
            return false
        }
        val other: HeartRating = obj
        return isHeart == other.isHeart && isRated == other.rated
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Rating.Companion.FIELD_RATING_TYPE, HeartRating.Companion.FIELD_RATED, HeartRating.Companion.FIELD_IS_HEART])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putInt(HeartRating.Companion.keyForField(Rating.Companion.FIELD_RATING_TYPE), HeartRating.Companion.TYPE)
        bundle.putBoolean(HeartRating.Companion.keyForField(HeartRating.Companion.FIELD_RATED), isRated)
        bundle.putBoolean(HeartRating.Companion.keyForField(HeartRating.Companion.FIELD_IS_HEART), isHeart)
        return bundle
    }

    companion object {
        // Bundleable implementation.
        private val TYPE: @RatingType Int = Rating.Companion.RATING_TYPE_HEART
        private val FIELD_RATED: Int = 1
        private val FIELD_IS_HEART: Int = 2

        /** Object that can restore a [HeartRating] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<HeartRating> = Bundleable.Creator({ bundle: Bundle? -> HeartRating.Companion.fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): HeartRating {
            Assertions.checkArgument((
                    bundle.getInt(HeartRating.Companion.keyForField(Rating.Companion.FIELD_RATING_TYPE),  /* defaultValue= */Rating.Companion.RATING_TYPE_UNSET)
                            == HeartRating.Companion.TYPE))
            val isRated: Boolean = bundle.getBoolean(HeartRating.Companion.keyForField(HeartRating.Companion.FIELD_RATED),  /* defaultValue= */false)
            return if (isRated) HeartRating(bundle.getBoolean(HeartRating.Companion.keyForField(HeartRating.Companion.FIELD_IS_HEART),  /* defaultValue= */false)) else HeartRating()
        }

        private fun keyForField(field: @HeartRating.FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}