/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselectionimport

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Looper
import android.view.accessibility.CaptioningManager
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.*
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.primitives.Ints
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import java.util.*

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
import com.google.android.exoplayer2.text.Cue
import androidx.annotation.ColorInt
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
import androidx.annotation.RequiresApi
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import android.telephony.TelephonyManager
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
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
import androidx.annotation.StringRes
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
 * Parameters for controlling track selection.
 *
 *
 * Parameters can be queried and set on a [Player]. For example the following code modifies
 * the parameters to restrict video track selections to SD, and to select a German audio track if
 * there is one:
 *
 * <pre>`// Build on the current parameters.
 * TrackSelectionParameters currentParameters = player.getTrackSelectionParameters()
 * // Build the resulting parameters.
 * TrackSelectionParameters newParameters = currentParameters
 * .buildUpon()
 * .setMaxVideoSizeSd()
 * .setPreferredAudioLanguage("deu")
 * .build();
 * // Set the new parameters.
 * player.setTrackSelectionParameters(newParameters);
`</pre> *
 */
open class TrackSelectionParameters protected constructor(builder: TrackSelectionParameters.Builder) : Bundleable {
    /**
     * A builder for [TrackSelectionParameters]. See the [TrackSelectionParameters]
     * documentation for explanations of the parameters that can be configured using this builder.
     */
    open class Builder {
        // Video
        private var maxVideoWidth: Int = 0
        private var maxVideoHeight: Int = 0
        private var maxVideoFrameRate: Int = 0
        private var maxVideoBitrate: Int = 0
        private var minVideoWidth: Int = 0
        private var minVideoHeight: Int = 0
        private var minVideoFrameRate: Int = 0
        private var minVideoBitrate: Int = 0
        private var viewportWidth: Int = 0
        private var viewportHeight: Int = 0
        private var viewportOrientationMayChange: Boolean = false
        private var preferredVideoMimeTypes: ImmutableList<String?>? = null
        private var preferredVideoRoleFlags: @RoleFlags Int = 0

        // Audio
        private var preferredAudioLanguages: ImmutableList<String>? = null
        private var preferredAudioRoleFlags: @RoleFlags Int = 0
        private var maxAudioChannelCount: Int = 0
        private var maxAudioBitrate: Int = 0
        private var preferredAudioMimeTypes: ImmutableList<String?>? = null

        // Text
        private var preferredTextLanguages: ImmutableList<String?>? = null
        private var preferredTextRoleFlags: @RoleFlags Int = 0
        private var ignoredTextSelectionFlags: @SelectionFlags Int = 0
        private var selectUndeterminedTextLanguage: Boolean = false

        // General
        private var forceLowestBitrate: Boolean = false
        private var forceHighestSupportedBitrate: Boolean = false
        private var overrides: HashMap<TrackGroup, TrackSelectionOverride>? = null
        private var disabledTrackTypes: HashSet<Int>? = null

        @Deprecated("{@link Context} constraints will not be set using this constructor. Use {@link\n" + "     *     #Builder(Context)} instead.")
        constructor() {
            // Video
            maxVideoWidth = Int.MAX_VALUE
            maxVideoHeight = Int.MAX_VALUE
            maxVideoFrameRate = Int.MAX_VALUE
            maxVideoBitrate = Int.MAX_VALUE
            viewportWidth = Int.MAX_VALUE
            viewportHeight = Int.MAX_VALUE
            viewportOrientationMayChange = true
            preferredVideoMimeTypes = ImmutableList.of()
            preferredVideoRoleFlags = 0
            // Audio
            preferredAudioLanguages = ImmutableList.of()
            preferredAudioRoleFlags = 0
            maxAudioChannelCount = Int.MAX_VALUE
            maxAudioBitrate = Int.MAX_VALUE
            preferredAudioMimeTypes = ImmutableList.of()
            // Text
            preferredTextLanguages = ImmutableList.of()
            preferredTextRoleFlags = 0
            ignoredTextSelectionFlags = 0
            selectUndeterminedTextLanguage = false
            // General
            forceLowestBitrate = false
            forceHighestSupportedBitrate = false
            overrides = HashMap()
            disabledTrackTypes = HashSet()
        }

        /**
         * Creates a builder with default initial values.
         *
         * @param context Any context.
         */
        // Methods invoked are setter only.
        constructor(context: Context) : this() {
            setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context)
            setViewportSizeToPhysicalDisplaySize(context,  /* viewportOrientationMayChange= */true)
        }

        /** Creates a builder with the initial values specified in `initialValues`.  */
        constructor(initialValues: TrackSelectionParameters) {
            init(initialValues)
        }

        /** Creates a builder with the initial values specified in `bundle`.  */
        constructor(bundle: Bundle) {
            // Video
            maxVideoWidth = bundle.getInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_WIDTH), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxVideoWidth)
            maxVideoHeight = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_HEIGHT), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxVideoHeight)
            maxVideoFrameRate = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_FRAMERATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxVideoFrameRate)
            maxVideoBitrate = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_BITRATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxVideoBitrate)
            minVideoWidth = bundle.getInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_WIDTH), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.minVideoWidth)
            minVideoHeight = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_HEIGHT), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.minVideoHeight)
            minVideoFrameRate = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_FRAMERATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.minVideoFrameRate)
            minVideoBitrate = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_BITRATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.minVideoBitrate)
            viewportWidth = bundle.getInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_WIDTH), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.viewportWidth)
            viewportHeight = bundle.getInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_HEIGHT), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.viewportHeight)
            viewportOrientationMayChange = bundle.getBoolean(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.viewportOrientationMayChange)
            preferredVideoMimeTypes = ImmutableList.copyOf(
                    MoreObjects.firstNonNull(
                            bundle.getStringArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_VIDEO_MIMETYPES)), arrayOfNulls(0)))
            preferredVideoRoleFlags = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_VIDEO_ROLE_FLAGS),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.preferredVideoRoleFlags)
            // Audio
            val preferredAudioLanguages1: Array<String?> = MoreObjects.firstNonNull(
                    bundle.getStringArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_LANGUAGES)), arrayOfNulls(0))
            preferredAudioLanguages = TrackSelectionParameters.Builder.Companion.normalizeLanguageCodes(preferredAudioLanguages1)
            preferredAudioRoleFlags = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_ROLE_FLAGS),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.preferredAudioRoleFlags)
            maxAudioChannelCount = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_AUDIO_CHANNEL_COUNT),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxAudioChannelCount)
            maxAudioBitrate = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_AUDIO_BITRATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.maxAudioBitrate)
            preferredAudioMimeTypes = ImmutableList.copyOf(
                    MoreObjects.firstNonNull(
                            bundle.getStringArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_MIME_TYPES)), arrayOfNulls(0)))
            // Text
            preferredTextLanguages = TrackSelectionParameters.Builder.Companion.normalizeLanguageCodes(
                    MoreObjects.firstNonNull(
                            bundle.getStringArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_TEXT_LANGUAGES)), arrayOfNulls(0)))
            preferredTextRoleFlags = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_TEXT_ROLE_FLAGS),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.preferredTextRoleFlags)
            ignoredTextSelectionFlags = bundle.getInt(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_IGNORED_TEXT_SELECTION_FLAGS),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.ignoredTextSelectionFlags)
            selectUndeterminedTextLanguage = bundle.getBoolean(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.selectUndeterminedTextLanguage)
            // General
            forceLowestBitrate = bundle.getBoolean(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_FORCE_LOWEST_BITRATE), TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.forceLowestBitrate)
            forceHighestSupportedBitrate = bundle.getBoolean(
                    TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE),
                    TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT.forceHighestSupportedBitrate)
            val overrideBundleList: List<Bundle>? = bundle.getParcelableArrayList(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_SELECTION_OVERRIDES))
            val overrideList: List<TrackSelectionOverride> = if (overrideBundleList == null) ImmutableList.of() else BundleableUtil.fromBundleList(TrackSelectionOverride.Companion.CREATOR, overrideBundleList)
            overrides = HashMap()
            for (i in overrideList.indices) {
                val override: TrackSelectionOverride = overrideList.get(i)
                overrides!!.put(override.mediaTrackGroup, override)
            }
            val disabledTrackTypeArray: IntArray = MoreObjects.firstNonNull(bundle.getIntArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_DISABLED_TRACK_TYPE)), IntArray(0))
            disabledTrackTypes = HashSet()
            for (disabledTrackType: @TrackType Int in disabledTrackTypeArray) {
                disabledTrackTypes!!.add(disabledTrackType)
            }
        }

        /** Overrides the value of the builder with the value of [TrackSelectionParameters].  */
        @EnsuresNonNull("preferredVideoMimeTypes", "preferredAudioLanguages", "preferredAudioMimeTypes", "preferredTextLanguages", "overrides", "disabledTrackTypes")
        private fun init(parameters: TrackSelectionParameters) {
            // Video
            maxVideoWidth = parameters.maxVideoWidth
            maxVideoHeight = parameters.maxVideoHeight
            maxVideoFrameRate = parameters.maxVideoFrameRate
            maxVideoBitrate = parameters.maxVideoBitrate
            minVideoWidth = parameters.minVideoWidth
            minVideoHeight = parameters.minVideoHeight
            minVideoFrameRate = parameters.minVideoFrameRate
            minVideoBitrate = parameters.minVideoBitrate
            viewportWidth = parameters.viewportWidth
            viewportHeight = parameters.viewportHeight
            viewportOrientationMayChange = parameters.viewportOrientationMayChange
            preferredVideoMimeTypes = parameters.preferredVideoMimeTypes
            preferredVideoRoleFlags = parameters.preferredVideoRoleFlags
            // Audio
            preferredAudioLanguages = parameters.preferredAudioLanguages
            preferredAudioRoleFlags = parameters.preferredAudioRoleFlags
            maxAudioChannelCount = parameters.maxAudioChannelCount
            maxAudioBitrate = parameters.maxAudioBitrate
            preferredAudioMimeTypes = parameters.preferredAudioMimeTypes
            // Text
            preferredTextLanguages = parameters.preferredTextLanguages
            preferredTextRoleFlags = parameters.preferredTextRoleFlags
            ignoredTextSelectionFlags = parameters.ignoredTextSelectionFlags
            selectUndeterminedTextLanguage = parameters.selectUndeterminedTextLanguage
            // General
            forceLowestBitrate = parameters.forceLowestBitrate
            forceHighestSupportedBitrate = parameters.forceHighestSupportedBitrate
            disabledTrackTypes = HashSet(parameters.disabledTrackTypes)
            overrides = HashMap(parameters.overrides)
        }

        /** Overrides the value of the builder with the value of [TrackSelectionParameters].  */
        @CanIgnoreReturnValue
        protected open fun set(parameters: TrackSelectionParameters): TrackSelectionParameters.Builder? {
            init(parameters)
            return this
        }
        // Video
        /**
         * Equivalent to [setMaxVideoSize(1279, 719)][.setMaxVideoSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoSizeSd(): TrackSelectionParameters.Builder? {
            return setMaxVideoSize(1279, 719)
        }

        /**
         * Equivalent to [setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)][.setMaxVideoSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun clearVideoSizeConstraints(): TrackSelectionParameters.Builder? {
            return setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        /**
         * Sets the maximum allowed video width and height.
         *
         * @param maxVideoWidth Maximum allowed video width in pixels.
         * @param maxVideoHeight Maximum allowed video height in pixels.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoSize(maxVideoWidth: Int, maxVideoHeight: Int): TrackSelectionParameters.Builder? {
            this.maxVideoWidth = maxVideoWidth
            this.maxVideoHeight = maxVideoHeight
            return this
        }

        /**
         * Sets the maximum allowed video frame rate.
         *
         * @param maxVideoFrameRate Maximum allowed video frame rate in hertz.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoFrameRate(maxVideoFrameRate: Int): TrackSelectionParameters.Builder? {
            this.maxVideoFrameRate = maxVideoFrameRate
            return this
        }

        /**
         * Sets the maximum allowed video bitrate.
         *
         * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoBitrate(maxVideoBitrate: Int): TrackSelectionParameters.Builder? {
            this.maxVideoBitrate = maxVideoBitrate
            return this
        }

        /**
         * Sets the minimum allowed video width and height.
         *
         * @param minVideoWidth Minimum allowed video width in pixels.
         * @param minVideoHeight Minimum allowed video height in pixels.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoSize(minVideoWidth: Int, minVideoHeight: Int): TrackSelectionParameters.Builder? {
            this.minVideoWidth = minVideoWidth
            this.minVideoHeight = minVideoHeight
            return this
        }

        /**
         * Sets the minimum allowed video frame rate.
         *
         * @param minVideoFrameRate Minimum allowed video frame rate in hertz.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoFrameRate(minVideoFrameRate: Int): TrackSelectionParameters.Builder? {
            this.minVideoFrameRate = minVideoFrameRate
            return this
        }

        /**
         * Sets the minimum allowed video bitrate.
         *
         * @param minVideoBitrate Minimum allowed video bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoBitrate(minVideoBitrate: Int): TrackSelectionParameters.Builder? {
            this.minVideoBitrate = minVideoBitrate
            return this
        }

        /**
         * Equivalent to calling [.setViewportSize] with the viewport size
         * obtained from [Util.getCurrentDisplayModeSize].
         *
         * @param context Any context.
         * @param viewportOrientationMayChange Whether the viewport orientation may change during
         * playback.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setViewportSizeToPhysicalDisplaySize(
                context: Context, viewportOrientationMayChange: Boolean): TrackSelectionParameters.Builder? {
            // Assume the viewport is fullscreen.
            val viewportSize: Point? = Util.getCurrentDisplayModeSize(context)
            return setViewportSize(viewportSize!!.x, viewportSize.y, viewportOrientationMayChange)
        }

        /**
         * Equivalent to [setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE,][.setViewportSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun clearViewportSizeConstraints(): TrackSelectionParameters.Builder? {
            return setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, true)
        }

        /**
         * Sets the viewport size to constrain adaptive video selections so that only tracks suitable
         * for the viewport are selected.
         *
         * @param viewportWidth Viewport width in pixels.
         * @param viewportHeight Viewport height in pixels.
         * @param viewportOrientationMayChange Whether the viewport orientation may change during
         * playback.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setViewportSize(
                viewportWidth: Int, viewportHeight: Int, viewportOrientationMayChange: Boolean): TrackSelectionParameters.Builder? {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            this.viewportOrientationMayChange = viewportOrientationMayChange
            return this
        }

        /**
         * Sets the preferred sample MIME type for video tracks.
         *
         * @param mimeType The preferred MIME type for video tracks, or `null` to clear a
         * previously set preference.
         * @return This builder.
         */
        open fun setPreferredVideoMimeType(mimeType: String?): TrackSelectionParameters.Builder? {
            return if (mimeType == null) setPreferredVideoMimeTypes() else setPreferredVideoMimeTypes(mimeType)
        }

        /**
         * Sets the preferred sample MIME types for video tracks.
         *
         * @param mimeTypes The preferred MIME types for video tracks in order of preference, or an
         * empty list for no preference.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredVideoMimeTypes(vararg mimeTypes: String?): TrackSelectionParameters.Builder? {
            preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for video tracks.
         *
         * @param preferredVideoRoleFlags Preferred video role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredVideoRoleFlags(preferredVideoRoleFlags: @RoleFlags Int): TrackSelectionParameters.Builder? {
            this.preferredVideoRoleFlags = preferredVideoRoleFlags
            return this
        }
        // Audio
        /**
         * Sets the preferred language for audio and forced text tracks.
         *
         * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
         * `null` to select the default track, or the first track if there's no default.
         * @return This builder.
         */
        open fun setPreferredAudioLanguage(preferredAudioLanguage: String?): TrackSelectionParameters.Builder? {
            return if (preferredAudioLanguage == null) setPreferredAudioLanguages() else setPreferredAudioLanguages(preferredAudioLanguage)
        }

        /**
         * Sets the preferred languages for audio and forced text tracks.
         *
         * @param preferredAudioLanguages Preferred audio languages as IETF BCP 47 conformant tags in
         * order of preference, or an empty array to select the default track, or the first track if
         * there's no default.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioLanguages(vararg preferredAudioLanguages: String?): TrackSelectionParameters.Builder? {
            this.preferredAudioLanguages = TrackSelectionParameters.Builder.Companion.normalizeLanguageCodes(preferredAudioLanguages)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for audio tracks.
         *
         * @param preferredAudioRoleFlags Preferred audio role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioRoleFlags(preferredAudioRoleFlags: @RoleFlags Int): TrackSelectionParameters.Builder? {
            this.preferredAudioRoleFlags = preferredAudioRoleFlags
            return this
        }

        /**
         * Sets the maximum allowed audio channel count.
         *
         * @param maxAudioChannelCount Maximum allowed audio channel count.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxAudioChannelCount(maxAudioChannelCount: Int): TrackSelectionParameters.Builder? {
            this.maxAudioChannelCount = maxAudioChannelCount
            return this
        }

        /**
         * Sets the maximum allowed audio bitrate.
         *
         * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxAudioBitrate(maxAudioBitrate: Int): TrackSelectionParameters.Builder? {
            this.maxAudioBitrate = maxAudioBitrate
            return this
        }

        /**
         * Sets the preferred sample MIME type for audio tracks.
         *
         * @param mimeType The preferred MIME type for audio tracks, or `null` to clear a
         * previously set preference.
         * @return This builder.
         */
        open fun setPreferredAudioMimeType(mimeType: String?): TrackSelectionParameters.Builder? {
            return if (mimeType == null) setPreferredAudioMimeTypes() else setPreferredAudioMimeTypes(mimeType)
        }

        /**
         * Sets the preferred sample MIME types for audio tracks.
         *
         * @param mimeTypes The preferred MIME types for audio tracks in order of preference, or an
         * empty list for no preference.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioMimeTypes(vararg mimeTypes: String?): TrackSelectionParameters.Builder? {
            preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes)
            return this
        }
        // Text
        /**
         * Sets the preferred language and role flags for text tracks based on the accessibility
         * settings of [CaptioningManager].
         *
         *
         * Does nothing for API levels &lt; 19 or when the [CaptioningManager] is disabled.
         *
         * @param context A [Context].
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
                context: Context): TrackSelectionParameters.Builder? {
            if (Util.SDK_INT >= 19) {
                setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context)
            }
            return this
        }

        /**
         * Sets the preferred language for text tracks.
         *
         * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
         * `null` to select the default track if there is one, or no track otherwise.
         * @return This builder.
         */
        open fun setPreferredTextLanguage(preferredTextLanguage: String?): TrackSelectionParameters.Builder? {
            return if (preferredTextLanguage == null) setPreferredTextLanguages() else setPreferredTextLanguages(preferredTextLanguage)
        }

        /**
         * Sets the preferred languages for text tracks.
         *
         * @param preferredTextLanguages Preferred text languages as IETF BCP 47 conformant tags in
         * order of preference, or an empty array to select the default track if there is one, or no
         * track otherwise.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredTextLanguages(vararg preferredTextLanguages: String?): TrackSelectionParameters.Builder? {
            this.preferredTextLanguages = TrackSelectionParameters.Builder.Companion.normalizeLanguageCodes(preferredTextLanguages)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for text tracks.
         *
         * @param preferredTextRoleFlags Preferred text role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredTextRoleFlags(preferredTextRoleFlags: @RoleFlags Int): TrackSelectionParameters.Builder? {
            this.preferredTextRoleFlags = preferredTextRoleFlags
            return this
        }

        /**
         * Sets a bitmask of selection flags that are ignored for text track selections.
         *
         * @param ignoredTextSelectionFlags A bitmask of [C.SelectionFlags] that are ignored for
         * text track selections.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setIgnoredTextSelectionFlags(ignoredTextSelectionFlags: @SelectionFlags Int): TrackSelectionParameters.Builder? {
            this.ignoredTextSelectionFlags = ignoredTextSelectionFlags
            return this
        }

        /**
         * Sets whether a text track with undetermined language should be selected if no track with
         * [a preferred language][.setPreferredTextLanguages] is available, or if the
         * preferred language is unset.
         *
         * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
         * be selected if no preferred language track is available.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage: Boolean): TrackSelectionParameters.Builder? {
            this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage
            return this
        }
        // General
        /**
         * Sets whether to force selection of the single lowest bitrate audio and video tracks that
         * comply with all other constraints.
         *
         * @param forceLowestBitrate Whether to force selection of the single lowest bitrate audio and
         * video tracks.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setForceLowestBitrate(forceLowestBitrate: Boolean): TrackSelectionParameters.Builder? {
            this.forceLowestBitrate = forceLowestBitrate
            return this
        }

        /**
         * Sets whether to force selection of the highest bitrate audio and video tracks that comply
         * with all other constraints.
         *
         * @param forceHighestSupportedBitrate Whether to force selection of the highest bitrate audio
         * and video tracks.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setForceHighestSupportedBitrate(forceHighestSupportedBitrate: Boolean): TrackSelectionParameters.Builder? {
            this.forceHighestSupportedBitrate = forceHighestSupportedBitrate
            return this
        }

        /** Adds an override, replacing any override for the same [TrackGroup].  */
        @CanIgnoreReturnValue
        open fun addOverride(override: TrackSelectionOverride): TrackSelectionParameters.Builder? {
            overrides!!.put(override.mediaTrackGroup, override)
            return this
        }

        /** Sets an override, replacing all existing overrides with the same track type.  */
        @CanIgnoreReturnValue
        open fun setOverrideForType(override: TrackSelectionOverride): TrackSelectionParameters.Builder? {
            clearOverridesOfType(override.getType())
            overrides!!.put(override.mediaTrackGroup, override)
            return this
        }

        /** Removes the override for the provided media [TrackGroup], if there is one.  */
        @CanIgnoreReturnValue
        open fun clearOverride(mediaTrackGroup: TrackGroup): TrackSelectionParameters.Builder? {
            overrides!!.remove(mediaTrackGroup)
            return this
        }

        /** Removes all overrides of the provided track type.  */
        @CanIgnoreReturnValue
        open fun clearOverridesOfType(trackType: @TrackType Int): TrackSelectionParameters.Builder? {
            val it: MutableIterator<TrackSelectionOverride> = overrides!!.values.iterator()
            while (it.hasNext()) {
                val override: TrackSelectionOverride = it.next()
                if (override.getType() == trackType) {
                    it.remove()
                }
            }
            return this
        }

        /** Removes all overrides.  */
        @CanIgnoreReturnValue
        open fun clearOverrides(): TrackSelectionParameters.Builder? {
            overrides!!.clear()
            return this
        }

        /**
         * Sets the disabled track types, preventing all tracks of those types from being selected for
         * playback. Any previously disabled track types are cleared.
         *
         * @param disabledTrackTypes The track types to disable.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setTrackTypeDisabled(int, boolean)}.")
        open fun setDisabledTrackTypes(disabledTrackTypes: Set<Int>?): TrackSelectionParameters.Builder? {
            this.disabledTrackTypes!!.clear()
            this.disabledTrackTypes!!.addAll((disabledTrackTypes)!!)
            return this
        }

        /**
         * Sets whether a track type is disabled. If disabled, no tracks of the specified type will be
         * selected for playback.
         *
         * @param trackType The track type.
         * @param disabled Whether the track type should be disabled.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setTrackTypeDisabled(trackType: @TrackType Int, disabled: Boolean): TrackSelectionParameters.Builder? {
            if (disabled) {
                disabledTrackTypes!!.add(trackType)
            } else {
                disabledTrackTypes!!.remove(trackType)
            }
            return this
        }

        /** Builds a [TrackSelectionParameters] instance with the selected values.  */
        open fun build(): TrackSelectionParameters? {
            return TrackSelectionParameters(this)
        }

        @RequiresApi(19)
        private fun setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(
                context: Context) {
            if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
                // Android platform bug (pre-Marshmallow) that causes RuntimeExceptions when
                // CaptioningService is instantiated from a non-Looper thread. See [internal: b/143779904].
                return
            }
            val captioningManager: CaptioningManager? = context.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager?
            if (captioningManager == null || !captioningManager.isEnabled()) {
                return
            }
            preferredTextRoleFlags = C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
            val preferredLocale: Locale? = captioningManager.getLocale()
            if (preferredLocale != null) {
                preferredTextLanguages = ImmutableList.of(Util.getLocaleLanguageTag(preferredLocale))
            }
        }

        companion object {
            private fun normalizeLanguageCodes(preferredTextLanguages: Array<String>): ImmutableList<String?> {
                val listBuilder: ImmutableList.Builder<String?> = ImmutableList.builder()
                for (language: String in Assertions.checkNotNull(preferredTextLanguages)) {
                    listBuilder.add(Util.normalizeLanguageCode(Assertions.checkNotNull(language)))
                }
                return listBuilder.build()
            }
        }
    }
    // Video
    /**
     * Maximum allowed video width in pixels. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     *
     *
     * To constrain adaptive video track selections to be suitable for a given viewport (the region
     * of the display within which video will be played), use ([.viewportWidth], [ ][.viewportHeight] and [.viewportOrientationMayChange]) instead.
     */
    val maxVideoWidth: Int

    /**
     * Maximum allowed video height in pixels. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     *
     *
     * To constrain adaptive video track selections to be suitable for a given viewport (the region
     * of the display within which video will be played), use ([.viewportWidth], [ ][.viewportHeight] and [.viewportOrientationMayChange]) instead.
     */
    val maxVideoHeight: Int

    /**
     * Maximum allowed video frame rate in hertz. The default value is [Integer.MAX_VALUE] (i.e.
     * no constraint).
     */
    val maxVideoFrameRate: Int

    /**
     * Maximum allowed video bitrate in bits per second. The default value is [ ][Integer.MAX_VALUE] (i.e. no constraint).
     */
    val maxVideoBitrate: Int

    /** Minimum allowed video width in pixels. The default value is 0 (i.e. no constraint).  */
    val minVideoWidth: Int

    /** Minimum allowed video height in pixels. The default value is 0 (i.e. no constraint).  */
    val minVideoHeight: Int

    /** Minimum allowed video frame rate in hertz. The default value is 0 (i.e. no constraint).  */
    val minVideoFrameRate: Int

    /**
     * Minimum allowed video bitrate in bits per second. The default value is 0 (i.e. no constraint).
     */
    val minVideoBitrate: Int

    /**
     * Viewport width in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is the physical width of the
     * primary display, in pixels.
     */
    val viewportWidth: Int

    /**
     * Viewport height in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is the physical height of the
     * primary display, in pixels.
     */
    val viewportHeight: Int

    /**
     * Whether the viewport orientation may change during playback. Constrains video track selections
     * for adaptive content so that only tracks suitable for the viewport are selected. The default
     * value is `true`.
     */
    val viewportOrientationMayChange: Boolean

    /**
     * The preferred sample MIME types for video tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    val preferredVideoMimeTypes: ImmutableList<String>

    /**
     * The preferred [C.RoleFlags] for video tracks. `0` selects the default track if
     * there is one, or the first track if there's no default. The default value is `0`.
     */
    val preferredVideoRoleFlags: @RoleFlags Int
    // Audio
    /**
     * The preferred languages for audio and forced text tracks as IETF BCP 47 conformant tags in
     * order of preference. An empty list selects the default track, or the first track if there's no
     * default. The default value is an empty list.
     */
    val preferredAudioLanguages: ImmutableList<String>

    /**
     * The preferred [C.RoleFlags] for audio tracks. `0` selects the default track if
     * there is one, or the first track if there's no default. The default value is `0`.
     */
    val preferredAudioRoleFlags: @RoleFlags Int

    /**
     * Maximum allowed audio channel count. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     */
    val maxAudioChannelCount: Int

    /**
     * Maximum allowed audio bitrate in bits per second. The default value is [ ][Integer.MAX_VALUE] (i.e. no constraint).
     */
    val maxAudioBitrate: Int

    /**
     * The preferred sample MIME types for audio tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    val preferredAudioMimeTypes: ImmutableList<String>
    // Text
    /**
     * The preferred languages for text tracks as IETF BCP 47 conformant tags in order of preference.
     * An empty list selects the default track if there is one, or no track otherwise. The default
     * value is an empty list, or the language of the accessibility [CaptioningManager] if
     * enabled.
     */
    val preferredTextLanguages: ImmutableList<String>

    /**
     * The preferred [C.RoleFlags] for text tracks. `0` selects the default track if there
     * is one, or no track otherwise. The default value is `0`, or [C.ROLE_FLAG_SUBTITLE]
     * | [C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND] if the accessibility [CaptioningManager]
     * is enabled.
     */
    val preferredTextRoleFlags: @RoleFlags Int

    /**
     * Bitmask of selection flags that are ignored for text track selections. See [ ]. The default value is `0` (i.e., no flags are ignored).
     */
    val ignoredTextSelectionFlags: @SelectionFlags Int

    /**
     * Whether a text track with undetermined language should be selected if no track with [ ][.preferredTextLanguages] is available, or if [.preferredTextLanguages] is unset. The
     * default value is `false`.
     */
    val selectUndeterminedTextLanguage: Boolean
    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply with
     * all other constraints. The default value is `false`.
     */
    val forceLowestBitrate: Boolean

    /**
     * Whether to force selection of the highest bitrate audio and video tracks that comply with all
     * other constraints. The default value is `false`.
     */
    val forceHighestSupportedBitrate: Boolean

    /** Overrides to force selection of specific tracks.  */
    val overrides: ImmutableMap<TrackGroup, TrackSelectionOverride>

    /**
     * The track types that are disabled. No track of a disabled type will be selected, thus no track
     * type contained in the set will be played. The default value is that no track type is disabled
     * (empty set).
     */
    val disabledTrackTypes: ImmutableSet<Int>

    /** Creates a new [Builder], copying the initial values from this instance.  */
    open fun buildUpon(): TrackSelectionParameters.Builder? {
        return TrackSelectionParameters.Builder(this)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: TrackSelectionParameters = obj as TrackSelectionParameters
        // Video
        return ((maxVideoWidth == other.maxVideoWidth
                ) && (maxVideoHeight == other.maxVideoHeight
                ) && (maxVideoFrameRate == other.maxVideoFrameRate
                ) && (maxVideoBitrate == other.maxVideoBitrate
                ) && (minVideoWidth == other.minVideoWidth
                ) && (minVideoHeight == other.minVideoHeight
                ) && (minVideoFrameRate == other.minVideoFrameRate
                ) && (minVideoBitrate == other.minVideoBitrate
                ) && (viewportOrientationMayChange == other.viewportOrientationMayChange
                ) && (viewportWidth == other.viewportWidth
                ) && (viewportHeight == other.viewportHeight
                ) && (preferredVideoMimeTypes == other.preferredVideoMimeTypes) && (preferredVideoRoleFlags == other.preferredVideoRoleFlags // Audio
                ) && (preferredAudioLanguages == other.preferredAudioLanguages) && (preferredAudioRoleFlags == other.preferredAudioRoleFlags
                ) && (maxAudioChannelCount == other.maxAudioChannelCount
                ) && (maxAudioBitrate == other.maxAudioBitrate
                ) && (preferredAudioMimeTypes == other.preferredAudioMimeTypes) && (preferredTextLanguages == other.preferredTextLanguages) && (preferredTextRoleFlags == other.preferredTextRoleFlags
                ) && (ignoredTextSelectionFlags == other.ignoredTextSelectionFlags
                ) && (selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage // General
                ) && (forceLowestBitrate == other.forceLowestBitrate
                ) && (forceHighestSupportedBitrate == other.forceHighestSupportedBitrate
                ) && (overrides == other.overrides) && (disabledTrackTypes == other.disabledTrackTypes))
    }

    public override fun hashCode(): Int {
        var result: Int = 1
        // Video
        result = 31 * result + maxVideoWidth
        result = 31 * result + maxVideoHeight
        result = 31 * result + maxVideoFrameRate
        result = 31 * result + maxVideoBitrate
        result = 31 * result + minVideoWidth
        result = 31 * result + minVideoHeight
        result = 31 * result + minVideoFrameRate
        result = 31 * result + minVideoBitrate
        result = 31 * result + (if (viewportOrientationMayChange) 1 else 0)
        result = 31 * result + viewportWidth
        result = 31 * result + viewportHeight
        result = 31 * result + preferredVideoMimeTypes.hashCode()
        result = 31 * result + preferredVideoRoleFlags
        // Audio
        result = 31 * result + preferredAudioLanguages.hashCode()
        result = 31 * result + preferredAudioRoleFlags
        result = 31 * result + maxAudioChannelCount
        result = 31 * result + maxAudioBitrate
        result = 31 * result + preferredAudioMimeTypes.hashCode()
        // Text
        result = 31 * result + preferredTextLanguages.hashCode()
        result = 31 * result + preferredTextRoleFlags
        result = 31 * result + ignoredTextSelectionFlags
        result = 31 * result + (if (selectUndeterminedTextLanguage) 1 else 0)
        // General
        result = 31 * result + (if (forceLowestBitrate) 1 else 0)
        result = 31 * result + (if (forceHighestSupportedBitrate) 1 else 0)
        result = 31 * result + overrides.hashCode()
        result = 31 * result + disabledTrackTypes.hashCode()
        return result
    }

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()

        // Video
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_WIDTH), maxVideoWidth)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_HEIGHT), maxVideoHeight)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_FRAMERATE), maxVideoFrameRate)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_VIDEO_BITRATE), maxVideoBitrate)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_WIDTH), minVideoWidth)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_HEIGHT), minVideoHeight)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_FRAMERATE), minVideoFrameRate)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MIN_VIDEO_BITRATE), minVideoBitrate)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_WIDTH), viewportWidth)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_HEIGHT), viewportHeight)
        bundle.putBoolean(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE), viewportOrientationMayChange)
        bundle.putStringArray(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_VIDEO_MIMETYPES),
                preferredVideoMimeTypes.toTypedArray())
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_VIDEO_ROLE_FLAGS), preferredVideoRoleFlags)
        // Audio
        bundle.putStringArray(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_LANGUAGES),
                preferredAudioLanguages.toTypedArray())
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_ROLE_FLAGS), preferredAudioRoleFlags)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_AUDIO_CHANNEL_COUNT), maxAudioChannelCount)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_MAX_AUDIO_BITRATE), maxAudioBitrate)
        bundle.putStringArray(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_AUDIO_MIME_TYPES),
                preferredAudioMimeTypes.toTypedArray())
        // Text
        bundle.putStringArray(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_TEXT_LANGUAGES), preferredTextLanguages.toTypedArray())
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_PREFERRED_TEXT_ROLE_FLAGS), preferredTextRoleFlags)
        bundle.putInt(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_IGNORED_TEXT_SELECTION_FLAGS), ignoredTextSelectionFlags)
        bundle.putBoolean(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE), selectUndeterminedTextLanguage)
        // General
        bundle.putBoolean(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_FORCE_LOWEST_BITRATE), forceLowestBitrate)
        bundle.putBoolean(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE), forceHighestSupportedBitrate)
        bundle.putParcelableArrayList(
                TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_SELECTION_OVERRIDES), BundleableUtil.toBundleArrayList(overrides.values))
        bundle.putIntArray(TrackSelectionParameters.Companion.keyForField(TrackSelectionParameters.Companion.FIELD_DISABLED_TRACK_TYPE), Ints.toArray(disabledTrackTypes))
        return bundle
    }

    init {
        // Video
        maxVideoWidth = builder.maxVideoWidth
        maxVideoHeight = builder.maxVideoHeight
        maxVideoFrameRate = builder.maxVideoFrameRate
        maxVideoBitrate = builder.maxVideoBitrate
        minVideoWidth = builder.minVideoWidth
        minVideoHeight = builder.minVideoHeight
        minVideoFrameRate = builder.minVideoFrameRate
        minVideoBitrate = builder.minVideoBitrate
        viewportWidth = builder.viewportWidth
        viewportHeight = builder.viewportHeight
        viewportOrientationMayChange = builder.viewportOrientationMayChange
        preferredVideoMimeTypes = builder.preferredVideoMimeTypes
        preferredVideoRoleFlags = builder.preferredVideoRoleFlags
        // Audio
        preferredAudioLanguages = builder.preferredAudioLanguages
        preferredAudioRoleFlags = builder.preferredAudioRoleFlags
        maxAudioChannelCount = builder.maxAudioChannelCount
        maxAudioBitrate = builder.maxAudioBitrate
        preferredAudioMimeTypes = builder.preferredAudioMimeTypes
        // Text
        preferredTextLanguages = builder.preferredTextLanguages
        preferredTextRoleFlags = builder.preferredTextRoleFlags
        ignoredTextSelectionFlags = builder.ignoredTextSelectionFlags
        selectUndeterminedTextLanguage = builder.selectUndeterminedTextLanguage
        // General
        forceLowestBitrate = builder.forceLowestBitrate
        forceHighestSupportedBitrate = builder.forceHighestSupportedBitrate
        overrides = ImmutableMap.copyOf(builder.overrides)
        disabledTrackTypes = ImmutableSet.copyOf(builder.disabledTrackTypes)
    }

    companion object {
        /**
         * An instance with default values, except those obtained from the [Context].
         *
         *
         * If possible, use [.getDefaults] instead.
         *
         *
         * This instance will not have the following settings:
         *
         *
         *  * [Viewport][Builder.setViewportSizeToPhysicalDisplaySize] configured for the primary display.
         *  * [       Preferred text language and role flags][Builder.setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings] configured to the accessibility settings of
         * [CaptioningManager].
         *
         */
        val DEFAULT_WITHOUT_CONTEXT: TrackSelectionParameters = TrackSelectionParameters.Builder().build()

        @Deprecated("This instance is not configured using {@link Context} constraints. Use {@link\n" + "   *     #getDefaults(Context)} instead.")
        val DEFAULT: TrackSelectionParameters = TrackSelectionParameters.Companion.DEFAULT_WITHOUT_CONTEXT

        /** Returns an instance configured with default values.  */
        fun getDefaults(context: Context?): TrackSelectionParameters {
            return TrackSelectionParameters.Builder((context)!!).build()
        }

        // Bundleable implementation
        private val FIELD_PREFERRED_AUDIO_LANGUAGES: Int = 1
        private val FIELD_PREFERRED_AUDIO_ROLE_FLAGS: Int = 2
        private val FIELD_PREFERRED_TEXT_LANGUAGES: Int = 3
        private val FIELD_PREFERRED_TEXT_ROLE_FLAGS: Int = 4
        private val FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE: Int = 5
        private val FIELD_MAX_VIDEO_WIDTH: Int = 6
        private val FIELD_MAX_VIDEO_HEIGHT: Int = 7
        private val FIELD_MAX_VIDEO_FRAMERATE: Int = 8
        private val FIELD_MAX_VIDEO_BITRATE: Int = 9
        private val FIELD_MIN_VIDEO_WIDTH: Int = 10
        private val FIELD_MIN_VIDEO_HEIGHT: Int = 11
        private val FIELD_MIN_VIDEO_FRAMERATE: Int = 12
        private val FIELD_MIN_VIDEO_BITRATE: Int = 13
        private val FIELD_VIEWPORT_WIDTH: Int = 14
        private val FIELD_VIEWPORT_HEIGHT: Int = 15
        private val FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE: Int = 16
        private val FIELD_PREFERRED_VIDEO_MIMETYPES: Int = 17
        private val FIELD_MAX_AUDIO_CHANNEL_COUNT: Int = 18
        private val FIELD_MAX_AUDIO_BITRATE: Int = 19
        private val FIELD_PREFERRED_AUDIO_MIME_TYPES: Int = 20
        private val FIELD_FORCE_LOWEST_BITRATE: Int = 21
        private val FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE: Int = 22
        private val FIELD_SELECTION_OVERRIDES: Int = 23
        private val FIELD_DISABLED_TRACK_TYPE: Int = 24
        private val FIELD_PREFERRED_VIDEO_ROLE_FLAGS: Int = 25
        private val FIELD_IGNORED_TEXT_SELECTION_FLAGS: Int = 26

        /**
         * Defines a minimum field ID value for subclasses to use when implementing [.toBundle]
         * and [Bundleable.Creator].
         *
         *
         * Subclasses should obtain keys for their [Bundle] representation by applying a
         * non-negative offset on this constant and passing the result to [.keyForField].
         */
        protected val FIELD_CUSTOM_ID_BASE: Int = 1000

        /** Construct an instance from a [Bundle] produced by [.toBundle].  */
        fun fromBundle(bundle: Bundle?): TrackSelectionParameters {
            return TrackSelectionParameters.Builder((bundle)!!).build()
        }

        @Deprecated("Use {@link #fromBundle(Bundle)} instead.")
        val CREATOR: Bundleable.Creator<TrackSelectionParameters> = Bundleable.Creator({ bundle: Bundle? -> TrackSelectionParameters.Companion.fromBundle(bundle) })

        /**
         * Converts the given field number to a string which can be used as a field key when implementing
         * [.toBundle] and [Bundleable.Creator].
         *
         *
         * Subclasses should use `field` values greater than or equal to [ ][.FIELD_CUSTOM_ID_BASE].
         */
        protected fun keyForField(field: Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}