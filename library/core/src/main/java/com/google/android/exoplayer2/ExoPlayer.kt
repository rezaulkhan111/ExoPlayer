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

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.VideoChangeFrameRateStrategy
import com.google.android.exoplayer2.C.VideoScalingMode
import com.google.android.exoplayer2.C.WakeMode
import com.google.android.exoplayer2.ExoPlayer.Builder
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.DefaultAnalyticsCollector
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ShuffleOrder
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.PriorityTaskManager
import com.google.android.exoplayer2.util.Util.currentOrMainLooper
import com.google.android.exoplayer2.video.VideoFrameMetadataListener
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2.video.spherical.CameraMotionListener
import com.google.android.exoplayer2import.DeviceInfo
import com.google.common.base.Function
import com.google.common.base.Supplier
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * An extensible media player that plays [MediaSource]s. Instances can be obtained from [ ].
 *
 * <h2>Player components</h2>
 *
 *
 * ExoPlayer is designed to make few assumptions about (and hence impose few restrictions on) the
 * type of the media being played, how and where it is stored, and how it is rendered. Rather than
 * implementing the loading and rendering of media directly, ExoPlayer implementations delegate this
 * work to components that are injected when a player is created or when it's prepared for playback.
 * Components common to all ExoPlayer implementations are:
 *
 *
 *  * **[MediaSources][MediaSource]** that define the media to be played, load the media,
 * and from which the loaded media can be read. MediaSources are created from [       MediaItems][MediaItem] by the [MediaSource.Factory] injected into the player [       ][Builder.setMediaSourceFactory], or can be added directly by methods like [       ][.setMediaSource]. The library provides a [DefaultMediaSourceFactory] for
 * progressive media files, DASH, SmoothStreaming and HLS, which also includes functionality
 * for side-loading subtitle files and clipping media.
 *  * **[Renderer]**s that render individual components of the media. The library
 * provides default implementations for common media types ([MediaCodecVideoRenderer],
 * [MediaCodecAudioRenderer], [TextRenderer] and [MetadataRenderer]). A
 * Renderer consumes media from the MediaSource being played. Renderers are injected when the
 * player is created. The number of renderers and their respective track types can be obtained
 * by calling [.getRendererCount] and [.getRendererType].
 *  * A **[TrackSelector]** that selects tracks provided by the MediaSource to be
 * consumed by each of the available Renderers. The library provides a default implementation
 * ([DefaultTrackSelector]) suitable for most use cases. A TrackSelector is injected
 * when the player is created.
 *  * A **[LoadControl]** that controls when the MediaSource buffers more media, and how
 * much media is buffered. The library provides a default implementation ([       ]) suitable for most use cases. A LoadControl is injected when the player
 * is created.
 *
 *
 *
 * An ExoPlayer can be built using the default components provided by the library, but may also
 * be built using custom implementations if non-standard behaviors are required. For example a
 * custom LoadControl could be injected to change the player's buffering strategy, or a custom
 * Renderer could be injected to add support for a video codec not supported natively by Android.
 *
 *
 * The concept of injecting components that implement pieces of player functionality is present
 * throughout the library. The default component implementations listed above delegate work to
 * further injected components. This allows many sub-components to be individually replaced with
 * custom implementations. For example the default MediaSource implementations require one or more
 * [DataSource] factories to be injected via their constructors. By providing a custom factory
 * it's possible to load data from a non-standard source, or through a different network stack.
 *
 * <h2>Threading model</h2>
 *
 *
 * The figure below shows ExoPlayer's threading model.
 *
 *
 * <img src="doc-files/exoplayer-threading-model.svg" alt="ExoPlayer's
threading model"></img>
 *
 *
 *  * ExoPlayer instances must be accessed from a single application thread. For the vast
 * majority of cases this should be the application's main thread. Using the application's
 * main thread is also a requirement when using ExoPlayer's UI components or the IMA
 * extension. The thread on which an ExoPlayer instance must be accessed can be explicitly
 * specified by passing a `Looper` when creating the player. If no `Looper` is specified, then
 * the `Looper` of the thread that the player is created on is used, or if that thread does
 * not have a `Looper`, the `Looper` of the application's main thread is used. In all cases
 * the `Looper` of the thread from which the player must be accessed can be queried using
 * [.getApplicationLooper].
 *  * Registered listeners are called on the thread associated with [       ][.getApplicationLooper]. Note that this means registered listeners are called on the same
 * thread which must be used to access the player.
 *  * An internal playback thread is responsible for playback. Injected player components such as
 * Renderers, MediaSources, TrackSelectors and LoadControls are called by the player on this
 * thread.
 *  * When the application performs an operation on the player, for example a seek, a message is
 * delivered to the internal playback thread via a message queue. The internal playback thread
 * consumes messages from the queue and performs the corresponding operations. Similarly, when
 * a playback event occurs on the internal playback thread, a message is delivered to the
 * application thread via a second message queue. The application thread consumes messages
 * from the queue, updating the application visible state and calling corresponding listener
 * methods.
 *  * Injected player components may use additional background threads. For example a MediaSource
 * may use background threads to load data. These are implementation specific.
 *
 */
interface ExoPlayer : Player {

    @Deprecated("""Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
        interface.""")
    interface AudioComponent {

        @Deprecated("Use {@link ExoPlayer#setAudioAttributes(AudioAttributes, boolean)} instead.")
        fun setAudioAttributes(audioAttributes: AudioAttributes?, handleAudioFocus: Boolean)

        @Deprecated("Use {@link Player#getAudioAttributes()} instead.")
        fun getAudioAttributes(): AudioAttributes?

        @get:Deprecated("Use {@link ExoPlayer#getAudioSessionId()} instead.")
        @set:Deprecated("Use {@link ExoPlayer#setAudioSessionId(int)} instead.")
        var audioSessionId: Int

        @Deprecated("Use {@link ExoPlayer#setAuxEffectInfo(AuxEffectInfo)} instead.")
        fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo?)

        @Deprecated("Use {@link ExoPlayer#clearAuxEffectInfo()} instead.")
        fun clearAuxEffectInfo()

        @Deprecated("Use {@link Player#setVolume(float)} instead.")
        fun setVolume(audioVolume: Float)

        @Deprecated("Use {@link Player#getVolume()} instead.")
        fun getVolume(): Float

        @get:Deprecated("Use {@link ExoPlayer#getSkipSilenceEnabled()} instead.")
        @set:Deprecated("Use {@link ExoPlayer#setSkipSilenceEnabled(boolean)} instead.")
        var skipSilenceEnabled: Boolean
    }

    @Deprecated("""Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
        interface.""")
    interface VideoComponent {
        /**
         * @deprecated Use {@link ExoPlayer#setVideoScalingMode(int)} instead.
         */
        @Deprecated("")
        fun setVideoScalingMode(@VideoScalingMode videoScalingMode: Int)

        /**
         * @deprecated Use {@link ExoPlayer#getVideoScalingMode()} instead.
         */
        @Deprecated("")
        @VideoScalingMode
        fun getVideoScalingMode(): Int

        @Deprecated("""Use {@link ExoPlayer#setVideoFrameMetadataListener(VideoFrameMetadataListener)}
          instead.""")
        fun setVideoFrameMetadataListener(listener: VideoFrameMetadataListener?)

        @Deprecated("""Use {@link ExoPlayer#clearVideoFrameMetadataListener(VideoFrameMetadataListener)}
          instead.""")
        fun clearVideoFrameMetadataListener(listener: VideoFrameMetadataListener?)

        @Deprecated("Use {@link ExoPlayer#setCameraMotionListener(CameraMotionListener)} instead.")
        fun setCameraMotionListener(listener: CameraMotionListener?)

        @Deprecated("Use {@link ExoPlayer#clearCameraMotionListener(CameraMotionListener)} instead.")
        fun clearCameraMotionListener(listener: CameraMotionListener?)

        @Deprecated("Use {@link Player#clearVideoSurface()} instead.")
        fun clearVideoSurface()

        @Deprecated("Use {@link Player#clearVideoSurface(Surface)} instead.")
        fun clearVideoSurface(surface: Surface?)

        @Deprecated("Use {@link Player#setVideoSurface(Surface)} instead.")
        fun setVideoSurface(surface: Surface?)

        @Deprecated("Use {@link Player#setVideoSurfaceHolder(SurfaceHolder)} instead.")
        fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?)

        @Deprecated("Use {@link Player#clearVideoSurfaceHolder(SurfaceHolder)} instead.")
        fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?)

        @Deprecated("Use {@link Player#setVideoSurfaceView(SurfaceView)} instead.")
        fun setVideoSurfaceView(surfaceView: SurfaceView?)

        @Deprecated("Use {@link Player#clearVideoSurfaceView(SurfaceView)} instead.")
        fun clearVideoSurfaceView(surfaceView: SurfaceView?)

        @Deprecated("Use {@link Player#setVideoTextureView(TextureView)} instead.")
        fun setVideoTextureView(textureView: TextureView?)

        @Deprecated("Use {@link Player#clearVideoTextureView(TextureView)} instead.")
        fun clearVideoTextureView(textureView: TextureView?)

        @Deprecated("Use {@link Player#getVideoSize()} instead.")
        fun getVideoSize(): VideoSize?
    }

    @Deprecated("""Use {@link Player}, as the {@link TextComponent} methods are defined by that
        interface.""")
    interface TextComponent {

        @Deprecated("Use {@link Player#getCurrentCues()} instead.")
        fun getCurrentCues(): CueGroup?
    }

    @Deprecated("""Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
        interface.""")
    interface DeviceComponent {

        @Deprecated("Use {@link Player#getDeviceInfo()} instead.")
        fun getDeviceInfo(): DeviceInfo?

        @Deprecated("Use {@link Player#getDeviceVolume()} instead.")
        fun getDeviceVolume(): Int

        @Deprecated("Use {@link Player#isDeviceMuted()} instead.")
        fun isDeviceMuted(): Boolean

        @Deprecated("Use {@link Player#setDeviceVolume(int)} instead.")
        fun setDeviceVolume(volume: Int)

        @Deprecated("Use {@link Player#increaseDeviceVolume()} instead.")
        fun increaseDeviceVolume()

        @Deprecated("Use {@link Player#decreaseDeviceVolume()} instead.")
        fun decreaseDeviceVolume()

        @Deprecated("Use {@link Player#setDeviceMuted(boolean)} instead.")
        fun setDeviceMuted(muted: Boolean)
    }

    /**
     * A listener for audio offload events.
     *
     *
     * This class is experimental, and might be renamed, moved or removed in a future release.
     */
    interface AudioOffloadListener {
        /**
         * Called when the player has started or stopped offload scheduling using [ ][.experimentalSetOffloadSchedulingEnabled].
         *
         *
         * This method is experimental, and will be renamed or removed in a future release.
         */
        fun onExperimentalOffloadSchedulingEnabledChanged(offloadSchedulingEnabled: Boolean) {}

        /**
         * Called when the player has started or finished sleeping for offload.
         *
         *
         * This method is experimental, and will be renamed or removed in a future release.
         */
        fun onExperimentalSleepingForOffloadChanged(sleepingForOffload: Boolean) {}

        /**
         * Called when the value of [AudioTrack.isOffloadedPlayback] changes.
         *
         *
         * This should not be generally required to be acted upon. But when offload is critical for
         * efficiency, or audio features (gapless, playback speed), this will let the app know.
         *
         *
         * This method is experimental, and will be renamed or removed in a future release.
         */
        fun onExperimentalOffloadedPlayback(offloadedPlayback: Boolean) {}
    }

    /**
     * A builder for [ExoPlayer] instances.
     *
     *
     * See [.Builder] for the list of default values.
     */
    class Builder private constructor(
            context: Context,
            renderersFactorySupplier: Supplier<RenderersFactory>,
            mediaSourceFactorySupplier: Supplier<MediaSource.Factory>,
            trackSelectorSupplier: Supplier<TrackSelector> =
                    Supplier { DefaultTrackSelector(context) },
            loadControlSupplier: Supplier<LoadControl> = Supplier { DefaultLoadControl() },
            bandwidthMeterSupplier: Supplier<BandwidthMeter> =
                    Supplier { DefaultBandwidthMeter.getSingletonInstance(context) },
            analyticsCollectorFunction: Function<Clock, AnalyticsCollector> = Function { clock: Clock? -> DefaultAnalyticsCollector(clock) }) {
        /* package */
        @JvmField
        val context: Context

        /* package */
        @JvmField
        var clock: Clock? = null

        /* package */
        @JvmField
        var foregroundModeTimeoutMs: Long = 0

        /* package */
        @JvmField
        var renderersFactorySupplier: Supplier<RenderersFactory>? = null

        /* package */
        @JvmField
        var mediaSourceFactorySupplier: Supplier<MediaSource.Factory>? = null

        /* package */
        @JvmField
        var trackSelectorSupplier: Supplier<TrackSelector>? = null

        /* package */
        @JvmField
        var loadControlSupplier: Supplier<LoadControl>? = null

        /* package */
        @JvmField
        var bandwidthMeterSupplier: Supplier<BandwidthMeter>? = null

        /* package */
        @JvmField
        var analyticsCollectorFunction: Function<Clock, AnalyticsCollector>? = null

        /* package */
        @JvmField
        var looper: Looper? = null

        @JvmField
        var priorityTaskManager: /* package */PriorityTaskManager? = null

        /* package */
        @JvmField
        var audioAttributes: AudioAttributes? = null

        /* package */
        @JvmField
        var handleAudioFocus = false

        @JvmField
        @WakeMode
        var wakeMode/* package */ = 0

        /* package */
        @JvmField
        var handleAudioBecomingNoisy = false

        /* package */
        @JvmField
        var skipSilenceEnabled = false

        @JvmField
        @VideoScalingMode
        var videoScalingMode/* package */ = 0

        @JvmField
        @VideoChangeFrameRateStrategy
        var videoChangeFrameRateStrategy/* package */ = 0

        /* package */
        @JvmField
        var useLazyPreparation = false

        /* package */
        @JvmField
        var seekParameters: SeekParameters? = null

        /* package */
        @JvmField
        var seekBackIncrementMs: Long = 0

        /* package */
        @JvmField
        var seekForwardIncrementMs: Long = 0

        /* package */
        @JvmField
        var livePlaybackSpeedControl: LivePlaybackSpeedControl? = null

        /* package */
        @JvmField
        var releaseTimeoutMs: Long = 0

        /* package */
        @JvmField
        var detachSurfaceTimeoutMs: Long = 0

        /* package */
        @JvmField
        var pauseAtEndOfMediaItems = false

        /* package */
        @JvmField
        var usePlatformDiagnostics = false

        /* package */
        var buildCalled = false

        /**
         * Creates a builder.
         *
         *
         * Use [.Builder], [.Builder] or [.Builder]
         * instead, if you intend to provide a custom [RenderersFactory], [ ] or [DefaultMediaSourceFactory]. This is to ensure that ProGuard or
         * R8 can remove ExoPlayer's [DefaultRenderersFactory], [DefaultExtractorsFactory]
         * and [DefaultMediaSourceFactory] from the APK.
         *
         *
         * The builder uses the following default values:
         *
         *
         *  * [RenderersFactory]: [DefaultRenderersFactory]
         *  * [TrackSelector]: [DefaultTrackSelector]
         *  * [MediaSource.Factory]: [DefaultMediaSourceFactory]
         *  * [LoadControl]: [DefaultLoadControl]
         *  * [BandwidthMeter]: [DefaultBandwidthMeter.getSingletonInstance]
         *  * [LivePlaybackSpeedControl]: [DefaultLivePlaybackSpeedControl]
         *  * [Looper]: The [Looper] associated with the current thread, or the [       ] of the application's main thread if the current thread doesn't have a [       ]
         *  * [AnalyticsCollector]: [AnalyticsCollector] with [Clock.DEFAULT]
         *  * [PriorityTaskManager]: `null` (not used)
         *  * [AudioAttributes]: [AudioAttributes.DEFAULT], not handling audio focus
         *  * [C.WakeMode]: [C.WAKE_MODE_NONE]
         *  * `handleAudioBecomingNoisy`: `false`
         *  * `skipSilenceEnabled`: `false`
         *  * [C.VideoScalingMode]: [C.VIDEO_SCALING_MODE_DEFAULT]
         *  * [C.VideoChangeFrameRateStrategy]: [       ][C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS]
         *  * `useLazyPreparation`: `true`
         *  * [SeekParameters]: [SeekParameters.DEFAULT]
         *  * `seekBackIncrementMs`: [C.DEFAULT_SEEK_BACK_INCREMENT_MS]
         *  * `seekForwardIncrementMs`: [C.DEFAULT_SEEK_FORWARD_INCREMENT_MS]
         *  * `releaseTimeoutMs`: [.DEFAULT_RELEASE_TIMEOUT_MS]
         *  * `detachSurfaceTimeoutMs`: [.DEFAULT_DETACH_SURFACE_TIMEOUT_MS]
         *  * `pauseAtEndOfMediaItems`: `false`
         *  * `usePlatformDiagnostics`: `true`
         *  * [Clock]: [Clock.DEFAULT]
         *
         *
         * @param context A [Context].
         */
        constructor(context: Context) : this(
                context,
                Supplier<RenderersFactory> { DefaultRenderersFactory(context) },
                Supplier<MediaSource.Factory> { DefaultMediaSourceFactory(context, DefaultExtractorsFactory()) }) {
        }

        /**
         * Creates a builder with a custom [RenderersFactory].
         *
         *
         * See [.Builder] for a list of default values.
         *
         *
         * Note that this constructor is only useful to try and ensure that ExoPlayer's [ ] can be removed by ProGuard or R8.
         *
         * @param context A [Context].
         * @param renderersFactory A factory for creating [Renderers][Renderer] to be used by the
         * player.
         */
        constructor(context: Context, renderersFactory: RenderersFactory) : this(
                context,
                Supplier<RenderersFactory> { renderersFactory },
                Supplier<MediaSource.Factory> { DefaultMediaSourceFactory(context, DefaultExtractorsFactory()) }) {
            checkNotNull(renderersFactory)
        }

        /**
         * Creates a builder with a custom [MediaSource.Factory].
         *
         *
         * See [.Builder] for a list of default values.
         *
         *
         * Note that this constructor is only useful to try and ensure that ExoPlayer's [ ] (and therefore [DefaultExtractorsFactory]) can be removed by
         * ProGuard or R8.
         *
         * @param context A [Context].
         * @param mediaSourceFactory A factory for creating a [MediaSource] from a [     ].
         */
        constructor(context: Context, mediaSourceFactory: MediaSource.Factory) : this(context, Supplier<RenderersFactory> { DefaultRenderersFactory(context) }, Supplier<MediaSource.Factory> { mediaSourceFactory }) {
            checkNotNull(mediaSourceFactory)
        }

        /**
         * Creates a builder with a custom [RenderersFactory] and [MediaSource.Factory].
         *
         *
         * See [.Builder] for a list of default values.
         *
         *
         * Note that this constructor is only useful to try and ensure that ExoPlayer's [ ], [DefaultMediaSourceFactory] (and therefore [ ]) can be removed by ProGuard or R8.
         *
         * @param context A [Context].
         * @param renderersFactory A factory for creating [Renderers][Renderer] to be used by the
         * player.
         * @param mediaSourceFactory A factory for creating a [MediaSource] from a [     ].
         */
        constructor(
                context: Context,
                renderersFactory: RenderersFactory,
                mediaSourceFactory: MediaSource.Factory) : this(context, Supplier<RenderersFactory> { renderersFactory }, Supplier<MediaSource.Factory> { mediaSourceFactory }) {
            checkNotNull(renderersFactory)
            checkNotNull(mediaSourceFactory)
        }

        /**
         * Creates a builder with the specified custom components.
         *
         *
         * Note that this constructor is only useful to try and ensure that ExoPlayer's default
         * components can be removed by ProGuard or R8.
         *
         * @param context A [Context].
         * @param renderersFactory A factory for creating [Renderers][Renderer] to be used by the
         * player.
         * @param mediaSourceFactory A [MediaSource.Factory].
         * @param trackSelector A [TrackSelector].
         * @param loadControl A [LoadControl].
         * @param bandwidthMeter A [BandwidthMeter].
         * @param analyticsCollector An [AnalyticsCollector].
         */
        constructor(
                context: Context,
                renderersFactory: RenderersFactory,
                mediaSourceFactory: MediaSource.Factory,
                trackSelector: TrackSelector,
                loadControl: LoadControl,
                bandwidthMeter: BandwidthMeter,
                analyticsCollector: AnalyticsCollector) : this(
                context,
                Supplier<RenderersFactory> { renderersFactory },
                Supplier<MediaSource.Factory> { mediaSourceFactory },
                Supplier<TrackSelector> { trackSelector },
                Supplier<LoadControl> { loadControl },
                Supplier<BandwidthMeter> { bandwidthMeter },
                Function<Clock, AnalyticsCollector> { clock: Clock? -> analyticsCollector }) {
            checkNotNull(renderersFactory)
            checkNotNull(mediaSourceFactory)
            checkNotNull(trackSelector)
            checkNotNull(bandwidthMeter)
            checkNotNull(analyticsCollector)
        }

        init {
            this.context = checkNotNull(context)
            this.renderersFactorySupplier = renderersFactorySupplier
            this.mediaSourceFactorySupplier = mediaSourceFactorySupplier
            this.trackSelectorSupplier = trackSelectorSupplier
            this.loadControlSupplier = loadControlSupplier
            this.bandwidthMeterSupplier = bandwidthMeterSupplier
            this.analyticsCollectorFunction = analyticsCollectorFunction
            looper = currentOrMainLooper
            audioAttributes = AudioAttributes.DEFAULT
            wakeMode = C.WAKE_MODE_NONE
            videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            useLazyPreparation = true
            seekParameters = SeekParameters.DEFAULT
            seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS
            seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
            livePlaybackSpeedControl = DefaultLivePlaybackSpeedControl.Builder().build()
            clock = Clock.DEFAULT
            releaseTimeoutMs = DEFAULT_RELEASE_TIMEOUT_MS
            detachSurfaceTimeoutMs = DEFAULT_DETACH_SURFACE_TIMEOUT_MS
            usePlatformDiagnostics = true
        }

        /**
         * Sets a limit on the time a call to [.setForegroundMode] can spend. If a call to [ ][.setForegroundMode] takes more than `timeoutMs` milliseconds to complete, the player
         * will raise an error via [Player.Listener.onPlayerError].
         *
         *
         * This method is experimental, and will be renamed or removed in a future release.
         *
         * @param timeoutMs The time limit in milliseconds.
         */
        @CanIgnoreReturnValue
        fun experimentalSetForegroundModeTimeoutMs(timeoutMs: Long): Builder {
            checkState(!buildCalled)
            foregroundModeTimeoutMs = timeoutMs
            return this
        }

        /**
         * Sets the [RenderersFactory] that will be used by the player.
         *
         * @param renderersFactory A [RenderersFactory].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setRenderersFactory(renderersFactory: RenderersFactory?): Builder {
            checkState(!buildCalled)
            checkNotNull(renderersFactory)
            renderersFactorySupplier = Supplier { renderersFactory }
            return this
        }

        /**
         * Sets the [MediaSource.Factory] that will be used by the player.
         *
         * @param mediaSourceFactory A [MediaSource.Factory].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setMediaSourceFactory(mediaSourceFactory: MediaSource.Factory?): Builder {
            checkState(!buildCalled)
            checkNotNull(mediaSourceFactory)
            mediaSourceFactorySupplier = Supplier { mediaSourceFactory }
            return this
        }

        /**
         * Sets the [TrackSelector] that will be used by the player.
         *
         * @param trackSelector A [TrackSelector].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setTrackSelector(trackSelector: TrackSelector?): Builder {
            checkState(!buildCalled)
            checkNotNull(trackSelector)
            trackSelectorSupplier = Supplier { trackSelector }
            return this
        }

        /**
         * Sets the [LoadControl] that will be used by the player.
         *
         * @param loadControl A [LoadControl].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setLoadControl(loadControl: LoadControl?): Builder {
            checkState(!buildCalled)
            checkNotNull(loadControl)
            loadControlSupplier = Supplier { loadControl }
            return this
        }

        /**
         * Sets the [BandwidthMeter] that will be used by the player.
         *
         * @param bandwidthMeter A [BandwidthMeter].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setBandwidthMeter(bandwidthMeter: BandwidthMeter?): Builder {
            checkState(!buildCalled)
            checkNotNull(bandwidthMeter)
            bandwidthMeterSupplier = Supplier { bandwidthMeter }
            return this
        }

        /**
         * Sets the [Looper] that must be used for all calls to the player and that is used to
         * call listeners on.
         *
         * @param looper A [Looper].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setLooper(looper: Looper?): Builder {
            checkState(!buildCalled)
            checkNotNull(looper)
            this.looper = looper
            return this
        }

        /**
         * Sets the [AnalyticsCollector] that will collect and forward all player events.
         *
         * @param analyticsCollector An [AnalyticsCollector].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setAnalyticsCollector(analyticsCollector: AnalyticsCollector?): Builder {
            checkState(!buildCalled)
            checkNotNull(analyticsCollector)
            analyticsCollectorFunction = Function { clock: Clock? -> analyticsCollector }
            return this
        }

        /**
         * Sets an [PriorityTaskManager] that will be used by the player.
         *
         *
         * The priority [C.PRIORITY_PLAYBACK] will be set while the player is loading.
         *
         * @param priorityTaskManager A [PriorityTaskManager], or null to not use one.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?): Builder {
            checkState(!buildCalled)
            this.priorityTaskManager = priorityTaskManager
            return this
        }

        /**
         * Sets [AudioAttributes] that will be used by the player and whether to handle audio
         * focus.
         *
         *
         * If audio focus should be handled, the [AudioAttributes.usage] must be [ ][C.USAGE_MEDIA] or [C.USAGE_GAME]. Other usages will throw an [ ].
         *
         * @param audioAttributes [AudioAttributes].
         * @param handleAudioFocus Whether the player should handle audio focus.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setAudioAttributes(audioAttributes: AudioAttributes?, handleAudioFocus: Boolean): Builder {
            checkState(!buildCalled)
            this.audioAttributes = checkNotNull(audioAttributes)
            this.handleAudioFocus = handleAudioFocus
            return this
        }

        /**
         * Sets the [C.WakeMode] that will be used by the player.
         *
         *
         * Enabling this feature requires the [android.Manifest.permission.WAKE_LOCK]
         * permission. It should be used together with a foreground [android.app.Service] for use
         * cases where playback occurs and the screen is off (e.g. background audio playback). It is not
         * useful when the screen will be kept on during playback (e.g. foreground video playback).
         *
         *
         * When enabled, the locks ([android.os.PowerManager.WakeLock] / [ ]) will be held whenever the player is in the [ ][.STATE_READY] or [.STATE_BUFFERING] states with `playWhenReady = true`. The locks
         * held depend on the specified [C.WakeMode].
         *
         * @param wakeMode A [C.WakeMode].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setWakeMode(@WakeMode wakeMode: Int): Builder {
            checkState(!buildCalled)
            this.wakeMode = wakeMode
            return this
        }

        /**
         * Sets whether the player should pause automatically when audio is rerouted from a headset to
         * device speakers. See the [
 * audio becoming noisy](https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy) documentation for more information.
         *
         * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
         * rerouted from a headset to device speakers.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean): Builder {
            checkState(!buildCalled)
            this.handleAudioBecomingNoisy = handleAudioBecomingNoisy
            return this
        }

        /**
         * Sets whether silences silences in the audio stream is enabled.
         *
         * @param skipSilenceEnabled Whether skipping silences is enabled.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean): Builder {
            checkState(!buildCalled)
            this.skipSilenceEnabled = skipSilenceEnabled
            return this
        }

        /**
         * Sets the [C.VideoScalingMode] that will be used by the player.
         *
         *
         * The scaling mode only applies if a [MediaCodec]-based video [Renderer] is
         * enabled and if the output surface is owned by a [SurfaceView].
         *
         * @param videoScalingMode A [C.VideoScalingMode].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setVideoScalingMode(@VideoScalingMode videoScalingMode: Int): Builder {
            checkState(!buildCalled)
            this.videoScalingMode = videoScalingMode
            return this
        }

        /**
         * Sets a [C.VideoChangeFrameRateStrategy] that will be used by the player when provided
         * with a video output [Surface].
         *
         *
         * The strategy only applies if a [MediaCodec]-based video [Renderer] is enabled.
         * Applications wishing to use [Surface.CHANGE_FRAME_RATE_ALWAYS] should set the mode to
         * [C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF] to disable calls to [ ][Surface.setFrameRate] from ExoPlayer, and should then call [Surface.setFrameRate]
         * directly from application code.
         *
         * @param videoChangeFrameRateStrategy A [C.VideoChangeFrameRateStrategy].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setVideoChangeFrameRateStrategy(
                @VideoChangeFrameRateStrategy videoChangeFrameRateStrategy: Int): Builder {
            checkState(!buildCalled)
            this.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy
            return this
        }

        /**
         * Sets whether media sources should be initialized lazily.
         *
         *
         * If false, all initial preparation steps (e.g., manifest loads) happen immediately. If
         * true, these initial preparations are triggered only when the player starts buffering the
         * media.
         *
         * @param useLazyPreparation Whether to use lazy preparation.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setUseLazyPreparation(useLazyPreparation: Boolean): Builder {
            checkState(!buildCalled)
            this.useLazyPreparation = useLazyPreparation
            return this
        }

        /**
         * Sets the parameters that control how seek operations are performed.
         *
         * @param seekParameters The [SeekParameters].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setSeekParameters(seekParameters: SeekParameters?): Builder {
            checkState(!buildCalled)
            this.seekParameters = checkNotNull(seekParameters)
            return this
        }

        /**
         * Sets the [.seekBack] increment.
         *
         * @param seekBackIncrementMs The seek back increment, in milliseconds.
         * @return This builder.
         * @throws IllegalArgumentException If `seekBackIncrementMs` is non-positive.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setSeekBackIncrementMs(@IntRange(from = 1) seekBackIncrementMs: Long): Builder {
            checkArgument(seekBackIncrementMs > 0)
            checkState(!buildCalled)
            this.seekBackIncrementMs = seekBackIncrementMs
            return this
        }

        /**
         * Sets the [.seekForward] increment.
         *
         * @param seekForwardIncrementMs The seek forward increment, in milliseconds.
         * @return This builder.
         * @throws IllegalArgumentException If `seekForwardIncrementMs` is non-positive.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setSeekForwardIncrementMs(@IntRange(from = 1) seekForwardIncrementMs: Long): Builder {
            checkArgument(seekForwardIncrementMs > 0)
            checkState(!buildCalled)
            this.seekForwardIncrementMs = seekForwardIncrementMs
            return this
        }

        /**
         * Sets a timeout for calls to [.release] and [.setForegroundMode].
         *
         *
         * If a call to [.release] or [.setForegroundMode] takes more than `timeoutMs` to complete, the player will report an error via [ ][Player.Listener.onPlayerError].
         *
         * @param releaseTimeoutMs The release timeout, in milliseconds.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setReleaseTimeoutMs(releaseTimeoutMs: Long): Builder {
            checkState(!buildCalled)
            this.releaseTimeoutMs = releaseTimeoutMs
            return this
        }

        /**
         * Sets a timeout for detaching a surface from the player.
         *
         *
         * If detaching a surface or replacing a surface takes more than `detachSurfaceTimeoutMs` to complete, the player will report an error via [ ][Player.Listener.onPlayerError].
         *
         * @param detachSurfaceTimeoutMs The timeout for detaching a surface, in milliseconds.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setDetachSurfaceTimeoutMs(detachSurfaceTimeoutMs: Long): Builder {
            checkState(!buildCalled)
            this.detachSurfaceTimeoutMs = detachSurfaceTimeoutMs
            return this
        }

        /**
         * Sets whether to pause playback at the end of each media item.
         *
         *
         * This means the player will pause at the end of each window in the current [ ][.getCurrentTimeline]. Listeners will be informed by a call to [ ][Player.Listener.onPlayWhenReadyChanged] with the reason [ ][Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM] when this happens.
         *
         * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean): Builder {
            checkState(!buildCalled)
            this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems
            return this
        }

        /**
         * Sets the [LivePlaybackSpeedControl] that will control the playback speed when playing
         * live streams, in order to maintain a steady target offset from the live stream edge.
         *
         * @param livePlaybackSpeedControl The [LivePlaybackSpeedControl].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setLivePlaybackSpeedControl(livePlaybackSpeedControl: LivePlaybackSpeedControl?): Builder {
            checkState(!buildCalled)
            this.livePlaybackSpeedControl = checkNotNull(livePlaybackSpeedControl)
            return this
        }

        /**
         * Sets whether the player reports diagnostics data to the Android platform.
         *
         *
         * If enabled, the player will use the [android.media.metrics.MediaMetricsManager] to
         * create a [android.media.metrics.PlaybackSession] and forward playback events and
         * performance data to this session. This helps to provide system performance and debugging
         * information for media playback on the device. This data may also be collected by Google [if sharing usage and diagnostics
 * data is enabled](https://support.google.com/accounts/answer/6078260) by the user of the device.
         *
         * @param usePlatformDiagnostics Whether the player reports diagnostics data to the Android
         * platform.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun setUsePlatformDiagnostics(usePlatformDiagnostics: Boolean): Builder {
            checkState(!buildCalled)
            this.usePlatformDiagnostics = usePlatformDiagnostics
            return this
        }

        /**
         * Sets the [Clock] that will be used by the player. Should only be set for testing
         * purposes.
         *
         * @param clock A [Clock].
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        @VisibleForTesting
        fun setClock(clock: Clock?): Builder {
            checkState(!buildCalled)
            this.clock = clock
            return this
        }

        /**
         * Builds an [ExoPlayer] instance.
         *
         * @throws IllegalStateException If this method has already been called.
         */
        fun build(): ExoPlayer {
            checkState(!buildCalled)
            buildCalled = true
            return ExoPlayerImpl( /* builder= */this,  /* wrappingPlayer= */null)
        }

        /* package */
        fun buildSimpleExoPlayer(): SimpleExoPlayer {
            checkState(!buildCalled)
            buildCalled = true
            return SimpleExoPlayer( /* builder= */this)
        }
    }

    /**
     * Equivalent to [Player.getPlayerError], except the exception is guaranteed to be an
     * [ExoPlaybackException].
     */
    abstract override val playerError: ExoPlaybackException?

    @get:Deprecated("""Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
        interface.""")
    val audioComponent: AudioComponent?

    @get:Deprecated("""Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
        interface.""")
    val videoComponent: VideoComponent?

    @get:Deprecated("""Use {@link Player}, as the {@link TextComponent} methods are defined by that
        interface.""")
    val textComponent: TextComponent?

    @get:Deprecated("""Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
        interface.""")
    val deviceComponent: DeviceComponent?

    /**
     * Adds a listener to receive audio offload events.
     *
     * @param listener The listener to register.
     */
    fun addAudioOffloadListener(listener: AudioOffloadListener?)

    /**
     * Removes a listener of audio offload events.
     *
     * @param listener The listener to unregister.
     */
    fun removeAudioOffloadListener(listener: AudioOffloadListener?)

    /** Returns the [AnalyticsCollector] used for collecting analytics events.  */
    val analyticsCollector: AnalyticsCollector?

    /**
     * Adds an [AnalyticsListener] to receive analytics events.
     *
     * @param listener The listener to be added.
     */
    fun addAnalyticsListener(listener: AnalyticsListener?)

    /**
     * Removes an [AnalyticsListener].
     *
     * @param listener The listener to be removed.
     */
    fun removeAnalyticsListener(listener: AnalyticsListener?)

    /** Returns the number of renderers.  */
    val rendererCount: Int

    /**
     * Returns the track type that the renderer at a given index handles.
     *
     *
     * For example, a video renderer will return [C.TRACK_TYPE_VIDEO], an audio renderer will
     * return [C.TRACK_TYPE_AUDIO] and a text renderer will return [C.TRACK_TYPE_TEXT].
     *
     * @param index The index of the renderer.
     * @return The [track type][C.TrackType] that the renderer handles.
     */
    @TrackType
    fun getRendererType(index: Int): Int

    /**
     * Returns the renderer at the given index.
     *
     * @param index The index of the renderer.
     * @return The renderer at this index.
     */
    fun getRenderer(index: Int): Renderer?

    /**
     * Returns the track selector that this player uses, or null if track selection is not supported.
     */
    val trackSelector: TrackSelector?

    /**
     * Returns the available track groups.
     *
     * @see Listener.onTracksChanged
     */
    @get:Deprecated("Use {@link #getCurrentTracks()}.")
    val currentTrackGroups: TrackGroupArray?

    /**
     * Returns the current track selections for each renderer, which may include `null` elements
     * if some renderers do not have any selected tracks.
     *
     * @see Listener.onTracksChanged
     */
    @get:Deprecated("Use {@link #getCurrentTracks()}.")
    val currentTrackSelections: TrackSelectionArray?

    /** Returns the [Looper] associated with the playback thread.  */
    val playbackLooper: Looper?

    /** Returns the [Clock] used for playback.  */
    val clock: Clock?

    @Deprecated("Use {@link #prepare()} instead.")
    fun retry()

    @Deprecated("Use {@link #setMediaSource(MediaSource)} and {@link #prepare()} instead.")
    fun prepare(mediaSource: MediaSource?)

    @Deprecated("Use {@link #setMediaSource(MediaSource, boolean)} and {@link #prepare()} instead.")
    fun prepare(mediaSource: MediaSource?, resetPosition: Boolean, resetState: Boolean)

    /**
     * Clears the playlist, adds the specified [MediaSources][MediaSource] and resets the
     * position to the default position.
     *
     * @param mediaSources The new [MediaSources][MediaSource].
     */
    fun setMediaSources(mediaSources: List<MediaSource?>?)

    /**
     * Clears the playlist and adds the specified [MediaSources][MediaSource].
     *
     * @param mediaSources The new [MediaSources][MediaSource].
     * @param resetPosition Whether the playback position should be reset to the default position in
     * the first [Timeline.Window]. If false, playback will start from the position defined
     * by [.getCurrentMediaItemIndex] and [.getCurrentPosition].
     */
    fun setMediaSources(mediaSources: List<MediaSource?>?, resetPosition: Boolean)

    /**
     * Clears the playlist and adds the specified [MediaSources][MediaSource].
     *
     * @param mediaSources The new [MediaSources][MediaSource].
     * @param startMediaItemIndex The media item index to start playback from. If [     ][C.INDEX_UNSET] is passed, the current position is not reset.
     * @param startPositionMs The position in milliseconds to start playback from. If [     ][C.TIME_UNSET] is passed, the default position of the given media item is used. In any case,
     * if `startMediaItemIndex` is set to [C.INDEX_UNSET], this parameter is ignored
     * and the position is not reset at all.
     */
    fun setMediaSources(
            mediaSources: List<MediaSource?>?, startMediaItemIndex: Int, startPositionMs: Long)

    /**
     * Clears the playlist, adds the specified [MediaSource] and resets the position to the
     * default position.
     *
     * @param mediaSource The new [MediaSource].
     */
    fun setMediaSource(mediaSource: MediaSource?)

    /**
     * Clears the playlist and adds the specified [MediaSource].
     *
     * @param mediaSource The new [MediaSource].
     * @param startPositionMs The position in milliseconds to start playback from.
     */
    fun setMediaSource(mediaSource: MediaSource?, startPositionMs: Long)

    /**
     * Clears the playlist and adds the specified [MediaSource].
     *
     * @param mediaSource The new [MediaSource].
     * @param resetPosition Whether the playback position should be reset to the default position. If
     * false, playback will start from the position defined by [.getCurrentMediaItemIndex]
     * and [.getCurrentPosition].
     */
    fun setMediaSource(mediaSource: MediaSource?, resetPosition: Boolean)

    /**
     * Adds a media source to the end of the playlist.
     *
     * @param mediaSource The [MediaSource] to add.
     */
    fun addMediaSource(mediaSource: MediaSource?)

    /**
     * Adds a media source at the given index of the playlist.
     *
     * @param index The index at which to add the source.
     * @param mediaSource The [MediaSource] to add.
     */
    fun addMediaSource(index: Int, mediaSource: MediaSource?)

    /**
     * Adds a list of media sources to the end of the playlist.
     *
     * @param mediaSources The [MediaSources][MediaSource] to add.
     */
    fun addMediaSources(mediaSources: List<MediaSource?>?)

    /**
     * Adds a list of media sources at the given index of the playlist.
     *
     * @param index The index at which to add the media sources.
     * @param mediaSources The [MediaSources][MediaSource] to add.
     */
    fun addMediaSources(index: Int, mediaSources: List<MediaSource?>?)

    /**
     * Sets the shuffle order.
     *
     * @param shuffleOrder The shuffle order.
     */
    fun setShuffleOrder(shuffleOrder: ShuffleOrder?)

    /**
     * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
     * default audio attributes will be used. They are suitable for general media playback.
     *
     *
     * Setting the audio attributes during playback may introduce a short gap in audio output as
     * the audio track is recreated. A new audio session id will also be generated.
     *
     *
     * If tunneling is enabled by the track selector, the specified audio attributes will be
     * ignored, but they will take effect if audio is later played without tunneling.
     *
     *
     * If the device is running a build before platform API version 21, audio attributes cannot be
     * set directly on the underlying audio track. In this case, the usage will be mapped onto an
     * equivalent stream type using [Util.getStreamTypeForAudioUsage].
     *
     *
     * If audio focus should be handled, the [AudioAttributes.usage] must be [ ][C.USAGE_MEDIA] or [C.USAGE_GAME]. Other usages will throw an [ ].
     *
     * @param audioAttributes The attributes to use for audio playback.
     * @param handleAudioFocus True if the player should handle audio focus, false otherwise.
     */
    fun setAudioAttributes(audioAttributes: AudioAttributes?, handleAudioFocus: Boolean)
    /** Returns the audio session identifier, or [C.AUDIO_SESSION_ID_UNSET] if not set.  */
    /**
     * Sets the ID of the audio session to attach to the underlying [android.media.AudioTrack].
     *
     *
     * The audio session ID can be generated using [Util.generateAudioSessionIdV21]
     * for API 21+.
     *
     * @param audioSessionId The audio session ID, or [C.AUDIO_SESSION_ID_UNSET] if it should be
     * generated by the framework.
     */
    var audioSessionId: Int

    /** Sets information on an auxiliary audio effect to attach to the underlying audio track.  */
    fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo?)

    /** Detaches any previously attached auxiliary audio effect from the underlying audio track.  */
    fun clearAuxEffectInfo()

    /**
     * Sets the preferred audio device.
     *
     * @param audioDeviceInfo The preferred [audio device][AudioDeviceInfo], or null to
     * restore the default.
     */
    @RequiresApi(23)
    fun setPreferredAudioDevice(audioDeviceInfo: AudioDeviceInfo?)
    /** Returns whether skipping silences in the audio stream is enabled.  */
    /**
     * Sets whether skipping silences in the audio stream is enabled.
     *
     * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
     */
    var skipSilenceEnabled: Boolean
    /** Returns the [C.VideoScalingMode].  */
    /**
     * Sets the [C.VideoScalingMode].
     *
     *
     * The scaling mode only applies if a [MediaCodec]-based video [Renderer] is
     * enabled and if the output surface is owned by a [SurfaceView].
     *
     * @param videoScalingMode The [C.VideoScalingMode].
     */
    @get:VideoScalingMode
    var videoScalingMode: Int
    /** Returns the [C.VideoChangeFrameRateStrategy].  */
    /**
     * Sets a [C.VideoChangeFrameRateStrategy] that will be used by the player when provided
     * with a video output [Surface].
     *
     *
     * The strategy only applies if a [MediaCodec]-based video [Renderer] is enabled.
     * Applications wishing to use [Surface.CHANGE_FRAME_RATE_ALWAYS] should set the mode to
     * [C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF] to disable calls to [Surface.setFrameRate]
     * from ExoPlayer, and should then call [Surface.setFrameRate] directly from application
     * code.
     *
     * @param videoChangeFrameRateStrategy A [C.VideoChangeFrameRateStrategy].
     */
    @get:VideoChangeFrameRateStrategy
    var videoChangeFrameRateStrategy: Int

    /**
     * Sets a listener to receive video frame metadata events.
     *
     *
     * This method is intended to be called by the same component that sets the [Surface]
     * onto which video will be rendered. If using ExoPlayer's standard UI components, this method
     * should not be called directly from application code.
     *
     * @param listener The listener.
     */
    fun setVideoFrameMetadataListener(listener: VideoFrameMetadataListener?)

    /**
     * Clears the listener which receives video frame metadata events if it matches the one passed.
     * Else does nothing.
     *
     * @param listener The listener to clear.
     */
    fun clearVideoFrameMetadataListener(listener: VideoFrameMetadataListener?)

    /**
     * Sets a listener of camera motion events.
     *
     * @param listener The listener.
     */
    fun setCameraMotionListener(listener: CameraMotionListener?)

    /**
     * Clears the listener which receives camera motion events if it matches the one passed. Else does
     * nothing.
     *
     * @param listener The listener to clear.
     */
    fun clearCameraMotionListener(listener: CameraMotionListener?)

    /**
     * Creates a message that can be sent to a [PlayerMessage.Target]. By default, the message
     * will be delivered immediately without blocking on the playback thread. The default [ ][PlayerMessage.getType] is 0 and the default [PlayerMessage.getPayload] is null. If a
     * position is specified with [PlayerMessage.setPosition], the message will be
     * delivered at this position in the current media item defined by [ ][.getCurrentMediaItemIndex]. Alternatively, the message can be sent at a specific mediaItem
     * using [PlayerMessage.setPosition].
     */
    fun createMessage(target: PlayerMessage.Target?): PlayerMessage?
    /** Returns the currently active [SeekParameters] of the player.  */
    /**
     * Sets the parameters that control how seek operations are performed.
     *
     * @param seekParameters The seek parameters, or `null` to use the defaults.
     */
    var seekParameters: SeekParameters?

    /**
     * Sets whether the player is allowed to keep holding limited resources such as video decoders,
     * even when in the idle state. By doing so, the player may be able to reduce latency when
     * starting to play another piece of content for which the same resources are required.
     *
     *
     * This mode should be used with caution, since holding limited resources may prevent other
     * players of media components from acquiring them. It should only be enabled when *both*
     * of the following conditions are true:
     *
     *
     *  * The application that owns the player is in the foreground.
     *  * The player is used in a way that may benefit from foreground mode. For this to be true,
     * the same player instance must be used to play multiple pieces of content, and there must
     * be gaps between the playbacks (i.e. [.stop] is called to halt one playback, and
     * [.prepare] is called some time later to start a new one).
     *
     *
     *
     * Note that foreground mode is *not* useful for switching between content without gaps
     * between the playbacks. For this use case [.stop] does not need to be called, and simply
     * calling [.prepare] for the new media will cause limited resources to be retained even if
     * foreground mode is not enabled.
     *
     *
     * If foreground mode is enabled, it's the application's responsibility to disable it when the
     * conditions described above no longer hold.
     *
     * @param foregroundMode Whether the player is allowed to keep limited resources even when in the
     * idle state.
     */
    fun setForegroundMode(foregroundMode: Boolean)
    /**
     * Returns whether the player pauses playback at the end of each media item.
     *
     * @see .setPauseAtEndOfMediaItems
     */
    /**
     * Sets whether to pause playback at the end of each media item.
     *
     *
     * This means the player will pause at the end of each window in the current [ ][.getCurrentTimeline]. Listeners will be informed by a call to [ ][Player.Listener.onPlayWhenReadyChanged] with the reason [ ][Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM] when this happens.
     *
     * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
     */
    var pauseAtEndOfMediaItems: Boolean

    /** Returns the audio format currently being played, or null if no audio is being played.  */
    val audioFormat: Format?

    /** Returns the video format currently being played, or null if no video is being played.  */
    val videoFormat: Format?

    /** Returns [DecoderCounters] for audio, or null if no audio is being played.  */
    val audioDecoderCounters: DecoderCounters?

    /** Returns [DecoderCounters] for video, or null if no video is being played.  */
    val videoDecoderCounters: DecoderCounters?

    /**
     * Sets whether the player should pause automatically when audio is rerouted from a headset to
     * device speakers. See the [audio
 * becoming noisy](https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy) documentation for more information.
     *
     * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
     * rerouted from a headset to device speakers.
     */
    fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean)

    @Deprecated("Use {@link #setWakeMode(int)} instead.")
    fun setHandleWakeLock(handleWakeLock: Boolean)

    /**
     * Sets how the player should keep the device awake for playback when the screen is off.
     *
     *
     * Enabling this feature requires the [android.Manifest.permission.WAKE_LOCK] permission.
     * It should be used together with a foreground [android.app.Service] for use cases where
     * playback occurs and the screen is off (e.g. background audio playback). It is not useful when
     * the screen will be kept on during playback (e.g. foreground video playback).
     *
     *
     * When enabled, the locks ([android.os.PowerManager.WakeLock] / [ ]) will be held whenever the player is in the [ ][.STATE_READY] or [.STATE_BUFFERING] states with `playWhenReady = true`. The locks
     * held depends on the specified [C.WakeMode].
     *
     * @param wakeMode The [C.WakeMode] option to keep the device awake during playback.
     */
    fun setWakeMode(@WakeMode wakeMode: Int)

    /**
     * Sets a [PriorityTaskManager], or null to clear a previously set priority task manager.
     *
     *
     * The priority [C.PRIORITY_PLAYBACK] will be set while the player is loading.
     *
     * @param priorityTaskManager The [PriorityTaskManager], or null to clear a previously set
     * priority task manager.
     */
    fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?)

    /**
     * Sets whether audio offload scheduling is enabled. If enabled, ExoPlayer's main loop will run as
     * rarely as possible when playing an audio stream using audio offload.
     *
     *
     * Only use this scheduling mode if the player is not displaying anything to the user. For
     * example when the application is in the background, or the screen is off. The player state
     * (including position) is rarely updated (roughly between every 10 seconds and 1 minute).
     *
     *
     * While offload scheduling is enabled, player events may be delivered severely delayed and
     * apps should not interact with the player. When returning to the foreground, disable offload
     * scheduling and wait for [ ][AudioOffloadListener.onExperimentalOffloadSchedulingEnabledChanged] to be called with
     * `offloadSchedulingEnabled = false` before interacting with the player.
     *
     *
     * This mode should save significant power when the phone is playing offload audio with the
     * screen off.
     *
     *
     * This mode only has an effect when playing an audio track in offload mode, which requires all
     * the following:
     *
     *
     *  * Audio offload rendering is enabled in [       ][DefaultRenderersFactory.setEnableAudioOffload] or the equivalent option passed to [       ][DefaultAudioSink.Builder.setOffloadMode].
     *  * An audio track is playing in a format that the device supports offloading (for example,
     * MP3 or AAC).
     *  * The [AudioSink] is playing with an offload [AudioTrack].
     *
     *
     *
     * The state where ExoPlayer main loop has been paused to save power during offload playback
     * can be queried with [.experimentalIsSleepingForOffload].
     *
     *
     * This method is experimental, and will be renamed or removed in a future release.
     *
     * @param offloadSchedulingEnabled Whether to enable offload scheduling.
     */
    fun experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled: Boolean)

    /**
     * Returns whether the player has paused its main loop to save power in offload scheduling mode.
     *
     * @see .experimentalSetOffloadSchedulingEnabled
     * @see AudioOffloadListener.onExperimentalSleepingForOffloadChanged
     */
    fun experimentalIsSleepingForOffload(): Boolean

    /**
     * Returns whether [tunneling](https://source.android.com/devices/tv/multimedia-tunneling) is enabled for
     * the currently selected tracks.
     *
     * @see Player.Listener.onTracksChanged
     */
    val isTunnelingEnabled: Boolean

    companion object {
        /**
         * The default timeout for calls to [.release] and [.setForegroundMode], in
         * milliseconds.
         */
        const val DEFAULT_RELEASE_TIMEOUT_MS: Long = 500

        /** The default timeout for detaching a surface from the player, in milliseconds.  */
        const val DEFAULT_DETACH_SURFACE_TIMEOUT_MS: Long = 2000
    }
}