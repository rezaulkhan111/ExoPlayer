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
import android.view.Surface
import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.ConditionVariable
import com.google.android.exoplayer2.util.Size
import com.google.android.exoplayer2.video.VideoSize
import com.google.errorprone.annotations.CanIgnoreReturnValue

@Deprecated("Use {@link ExoPlayer} instead.")
class SimpleExoPlayer internal constructor(builder: ExoPlayer.Builder?) : BasePlayer(), ExoPlayer, AudioComponent, VideoComponent, ExoPlayer.TextComponent, DeviceComponent {

    @Deprecated("Use {@link ExoPlayer.Builder} instead.")
    class Builder {
        val wrappedBuilder: ExoPlayer.Builder

        @Deprecated("Use {@link ExoPlayer.Builder#Builder(Context)} instead.")
        constructor(context: Context?) {
            wrappedBuilder = ExoPlayer.Builder(context)
        }

        @Deprecated("Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory)} instead.")
        constructor(context: Context?, renderersFactory: RenderersFactory?) {
            wrappedBuilder = ExoPlayer.Builder(context, renderersFactory)
        }

        @Deprecated("""Use {@link ExoPlayer.Builder#Builder(Context, MediaSource.Factory)} and {@link
     *     DefaultMediaSourceFactory#DefaultMediaSourceFactory(Context, ExtractorsFactory)} instead.""")
        constructor(context: Context?, extractorsFactory: ExtractorsFactory?) {
            wrappedBuilder = ExoPlayer.Builder(context, DefaultMediaSourceFactory(context, extractorsFactory))
        }

        @Deprecated("""Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory,
     *     MediaSource.Factory)} and {@link
     *     DefaultMediaSourceFactory#DefaultMediaSourceFactory(Context, ExtractorsFactory)} instead.""")
        constructor(
                context: Context?, renderersFactory: RenderersFactory?, extractorsFactory: ExtractorsFactory?) {
            wrappedBuilder = ExoPlayer.Builder(
                    context, renderersFactory, DefaultMediaSourceFactory(context, extractorsFactory))
        }

        @Deprecated("""Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory,
     *     MediaSource.Factory, TrackSelector, LoadControl, BandwidthMeter, AnalyticsCollector)}
          instead.""")
        constructor(
                context: Context?,
                renderersFactory: RenderersFactory?,
                trackSelector: TrackSelector?,
                mediaSourceFactory: MediaSource.Factory?,
                loadControl: LoadControl?,
                bandwidthMeter: BandwidthMeter?,
                analyticsCollector: AnalyticsCollector?) {
            wrappedBuilder = ExoPlayer.Builder(
                    context,
                    renderersFactory,
                    mediaSourceFactory,
                    trackSelector,
                    loadControl,
                    bandwidthMeter,
                    analyticsCollector)
        }

        @CanIgnoreReturnValue
        @Deprecated("""Use {@link ExoPlayer.Builder#experimentalSetForegroundModeTimeoutMs(long)}
          instead.""")
        fun experimentalSetForegroundModeTimeoutMs(timeoutMs: Long): Builder {
            wrappedBuilder.experimentalSetForegroundModeTimeoutMs(timeoutMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setTrackSelector(TrackSelector)} instead.")
        fun setTrackSelector(trackSelector: TrackSelector?): Builder {
            wrappedBuilder.setTrackSelector(trackSelector)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setMediaSourceFactory(MediaSource.Factory)} instead.")
        fun setMediaSourceFactory(mediaSourceFactory: MediaSource.Factory?): Builder {
            wrappedBuilder.setMediaSourceFactory(mediaSourceFactory)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setLoadControl(LoadControl)} instead.")
        fun setLoadControl(loadControl: LoadControl?): Builder {
            wrappedBuilder.setLoadControl(loadControl)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setBandwidthMeter(BandwidthMeter)} instead.")
        fun setBandwidthMeter(bandwidthMeter: BandwidthMeter?): Builder {
            wrappedBuilder.setBandwidthMeter(bandwidthMeter)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setLooper(Looper)} instead.")
        fun setLooper(looper: Looper?): Builder {
            wrappedBuilder.setLooper(looper)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setAnalyticsCollector(AnalyticsCollector)} instead.")
        fun setAnalyticsCollector(analyticsCollector: AnalyticsCollector?): Builder {
            wrappedBuilder.setAnalyticsCollector(analyticsCollector)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("""Use {@link ExoPlayer.Builder#setPriorityTaskManager(PriorityTaskManager)}
          instead.""")
        fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?): Builder {
            wrappedBuilder.setPriorityTaskManager(priorityTaskManager)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("""Use {@link ExoPlayer.Builder#setAudioAttributes(AudioAttributes, boolean)}
          instead.""")
        fun setAudioAttributes(audioAttributes: AudioAttributes?, handleAudioFocus: Boolean): Builder {
            wrappedBuilder.setAudioAttributes(audioAttributes, handleAudioFocus)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setWakeMode(int)} instead.")
        fun setWakeMode(@WakeMode wakeMode: Int): Builder {
            wrappedBuilder.setWakeMode(wakeMode)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setHandleAudioBecomingNoisy(boolean)} instead.")
        fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean): Builder {
            wrappedBuilder.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setSkipSilenceEnabled(boolean)} instead.")
        fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean): Builder {
            wrappedBuilder.setSkipSilenceEnabled(skipSilenceEnabled)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setVideoScalingMode(int)} instead.")
        fun setVideoScalingMode(@VideoScalingMode videoScalingMode: Int): Builder {
            wrappedBuilder.setVideoScalingMode(videoScalingMode)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setVideoChangeFrameRateStrategy(int)} instead.")
        fun setVideoChangeFrameRateStrategy(
                @VideoChangeFrameRateStrategy videoChangeFrameRateStrategy: Int): Builder {
            wrappedBuilder.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setUseLazyPreparation(boolean)} instead.")
        fun setUseLazyPreparation(useLazyPreparation: Boolean): Builder {
            wrappedBuilder.setUseLazyPreparation(useLazyPreparation)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setSeekParameters(SeekParameters)} instead.")
        fun setSeekParameters(seekParameters: SeekParameters?): Builder {
            wrappedBuilder.setSeekParameters(seekParameters)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setSeekBackIncrementMs(long)} instead.")
        fun setSeekBackIncrementMs(@IntRange(from = 1) seekBackIncrementMs: Long): Builder {
            wrappedBuilder.setSeekBackIncrementMs(seekBackIncrementMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setSeekForwardIncrementMs(long)} instead.")
        fun setSeekForwardIncrementMs(@IntRange(from = 1) seekForwardIncrementMs: Long): Builder {
            wrappedBuilder.setSeekForwardIncrementMs(seekForwardIncrementMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setReleaseTimeoutMs(long)} instead.")
        fun setReleaseTimeoutMs(releaseTimeoutMs: Long): Builder {
            wrappedBuilder.setReleaseTimeoutMs(releaseTimeoutMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setDetachSurfaceTimeoutMs(long)} instead.")
        fun setDetachSurfaceTimeoutMs(detachSurfaceTimeoutMs: Long): Builder {
            wrappedBuilder.setDetachSurfaceTimeoutMs(detachSurfaceTimeoutMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link ExoPlayer.Builder#setPauseAtEndOfMediaItems(boolean)} instead.")
        fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean): Builder {
            wrappedBuilder.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("""Use {@link
     *     ExoPlayer.Builder#setLivePlaybackSpeedControl(LivePlaybackSpeedControl)} instead.""")
        fun setLivePlaybackSpeedControl(livePlaybackSpeedControl: LivePlaybackSpeedControl?): Builder {
            wrappedBuilder.setLivePlaybackSpeedControl(livePlaybackSpeedControl)
            return this
        }

        @CanIgnoreReturnValue
        @VisibleForTesting
        @Deprecated("Use {@link ExoPlayer.Builder#setClock(Clock)} instead.")
        fun setClock(clock: Clock?): Builder {
            wrappedBuilder.setClock(clock)
            return this
        }

        @Deprecated("Use {@link ExoPlayer.Builder#build()} instead.")
        fun build(): SimpleExoPlayer {
            return wrappedBuilder.buildSimpleExoPlayer()
        }
    }

    private var player: ExoPlayerImpl? = null
    private val constructorFinished: ConditionVariable

    @Deprecated("Use the {@link ExoPlayer.Builder}.")
    protected constructor(
            context: Context?,
            renderersFactory: RenderersFactory?,
            trackSelector: TrackSelector?,
            mediaSourceFactory: MediaSource.Factory?,
            loadControl: LoadControl?,
            bandwidthMeter: BandwidthMeter?,
            analyticsCollector: AnalyticsCollector?,
            useLazyPreparation: Boolean,
            clock: Clock?,
            applicationLooper: Looper?) : this(
            ExoPlayer.Builder(
                    context,
                    renderersFactory,
                    mediaSourceFactory,
                    trackSelector,
                    loadControl,
                    bandwidthMeter,
                    analyticsCollector)
                    .setUseLazyPreparation(useLazyPreparation)
                    .setClock(clock)
                    .setLooper(applicationLooper)) {
    }

    /**
     * @param builder The [Builder] to obtain all construction parameters.
     */
    protected constructor(builder: Builder) : this(builder.wrappedBuilder) {}

    /**
     * @param builder The [ExoPlayer.Builder] to obtain all construction parameters.
     */
    /* package */
    init {
        constructorFinished = ConditionVariable()
        player = try {
            ExoPlayerImpl(builder,  /* wrappingPlayer= */this)
        } finally {
            constructorFinished.open()
        }
    }

    override fun experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled: Boolean) {
        blockUntilConstructorFinished()
        player!!.experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled)
    }

    override fun experimentalIsSleepingForOffload(): Boolean {
        blockUntilConstructorFinished()
        return player!!.experimentalIsSleepingForOffload()
    }

    @Deprecated("""Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
        interface.""")
    override fun getAudioComponent(): AudioComponent? {
        return this
    }

    @Deprecated("""Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
        interface.""")
    override fun getVideoComponent(): VideoComponent? {
        return this
    }

    @Deprecated("""Use {@link Player}, as the {@link TextComponent} methods are defined by that
        interface.""")
    override fun getTextComponent(): ExoPlayer.TextComponent? {
        return this
    }

    @Deprecated("""Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
        interface.""")
    override fun getDeviceComponent(): DeviceComponent? {
        return this
    }

    override fun setVideoScalingMode(@VideoScalingMode videoScalingMode: Int) {
        blockUntilConstructorFinished()
        player!!.videoScalingMode = videoScalingMode
    }

    @VideoScalingMode
    override fun getVideoScalingMode(): Int {
        blockUntilConstructorFinished()
        return player!!.videoScalingMode
    }

    override fun setVideoChangeFrameRateStrategy(
            @VideoChangeFrameRateStrategy videoChangeFrameRateStrategy: Int) {
        blockUntilConstructorFinished()
        player!!.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy
    }

    @VideoChangeFrameRateStrategy
    override fun getVideoChangeFrameRateStrategy(): Int {
        blockUntilConstructorFinished()
        return player!!.videoChangeFrameRateStrategy
    }

    override val videoSize: VideoSize
        get() {
            blockUntilConstructorFinished()
            return player.getVideoSize()
        }
    override val surfaceSize: Size
        get() {
            blockUntilConstructorFinished()
            return player.getSurfaceSize()
        }

    override fun clearVideoSurface() {
        blockUntilConstructorFinished()
        player!!.clearVideoSurface()
    }

    override fun clearVideoSurface(surface: Surface?) {
        blockUntilConstructorFinished()
        player!!.clearVideoSurface(surface)
    }

    override fun setVideoSurface(surface: Surface?) {
        blockUntilConstructorFinished()
        player!!.setVideoSurface(surface)
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        blockUntilConstructorFinished()
        player!!.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        blockUntilConstructorFinished()
        player!!.clearVideoSurfaceHolder(surfaceHolder)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        blockUntilConstructorFinished()
        player!!.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        blockUntilConstructorFinished()
        player!!.clearVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        blockUntilConstructorFinished()
        player!!.setVideoTextureView(textureView)
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        blockUntilConstructorFinished()
        player!!.clearVideoTextureView(textureView)
    }

    override fun addAudioOffloadListener(listener: AudioOffloadListener) {
        blockUntilConstructorFinished()
        player!!.addAudioOffloadListener(listener)
    }

    override fun removeAudioOffloadListener(listener: AudioOffloadListener) {
        blockUntilConstructorFinished()
        player!!.removeAudioOffloadListener(listener)
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        blockUntilConstructorFinished()
        player!!.setAudioAttributes(audioAttributes, handleAudioFocus)
    }

    override val audioAttributes: AudioAttributes
        get() {
            blockUntilConstructorFinished()
            return player.getAudioAttributes()
        }

    override fun setAudioSessionId(audioSessionId: Int) {
        blockUntilConstructorFinished()
        player!!.audioSessionId = audioSessionId
    }

    override fun getAudioSessionId(): Int {
        blockUntilConstructorFinished()
        return player!!.audioSessionId
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        blockUntilConstructorFinished()
        player!!.setAuxEffectInfo(auxEffectInfo)
    }

    override fun clearAuxEffectInfo() {
        blockUntilConstructorFinished()
        player!!.clearAuxEffectInfo()
    }

    @RequiresApi(23)
    override fun setPreferredAudioDevice(audioDeviceInfo: AudioDeviceInfo?) {
        blockUntilConstructorFinished()
        player!!.setPreferredAudioDevice(audioDeviceInfo)
    }

    override var volume: Float
        get() {
            blockUntilConstructorFinished()
            return player!!.getVolume()
        }
        set(volume) {
            blockUntilConstructorFinished()
            player!!.setVolume(volume)
        }

    override fun getSkipSilenceEnabled(): Boolean {
        blockUntilConstructorFinished()
        return player!!.skipSilenceEnabled
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        blockUntilConstructorFinished()
        player!!.skipSilenceEnabled = skipSilenceEnabled
    }

    override fun getAnalyticsCollector(): AnalyticsCollector {
        blockUntilConstructorFinished()
        return player!!.analyticsCollector
    }

    override fun addAnalyticsListener(listener: AnalyticsListener) {
        blockUntilConstructorFinished()
        player!!.addAnalyticsListener(listener)
    }

    override fun removeAnalyticsListener(listener: AnalyticsListener) {
        blockUntilConstructorFinished()
        player!!.removeAnalyticsListener(listener)
    }

    override fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean) {
        blockUntilConstructorFinished()
        player!!.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
    }

    override fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?) {
        blockUntilConstructorFinished()
        player!!.setPriorityTaskManager(priorityTaskManager)
    }

    override fun getVideoFormat(): Format? {
        blockUntilConstructorFinished()
        return player!!.videoFormat
    }

    override fun getAudioFormat(): Format? {
        blockUntilConstructorFinished()
        return player!!.audioFormat
    }

    override fun getVideoDecoderCounters(): DecoderCounters? {
        blockUntilConstructorFinished()
        return player!!.videoDecoderCounters
    }

    override fun getAudioDecoderCounters(): DecoderCounters? {
        blockUntilConstructorFinished()
        return player!!.audioDecoderCounters
    }

    override fun setVideoFrameMetadataListener(listener: VideoFrameMetadataListener) {
        blockUntilConstructorFinished()
        player!!.setVideoFrameMetadataListener(listener)
    }

    override fun clearVideoFrameMetadataListener(listener: VideoFrameMetadataListener) {
        blockUntilConstructorFinished()
        player!!.clearVideoFrameMetadataListener(listener)
    }

    override fun setCameraMotionListener(listener: CameraMotionListener) {
        blockUntilConstructorFinished()
        player!!.setCameraMotionListener(listener)
    }

    override fun clearCameraMotionListener(listener: CameraMotionListener) {
        blockUntilConstructorFinished()
        player!!.clearCameraMotionListener(listener)
    }

    override fun getCurrentCues(): CueGroup {
        blockUntilConstructorFinished()
        return player.getCurrentCues()
    }

    // ExoPlayer implementation
    override fun getPlaybackLooper(): Looper {
        blockUntilConstructorFinished()
        return player!!.playbackLooper
    }

    override val applicationLooper: Looper
        get() {
            blockUntilConstructorFinished()
            return player.getApplicationLooper()
        }

    override fun getClock(): Clock {
        blockUntilConstructorFinished()
        return player!!.clock
    }

    override fun addListener(listener: Player.Listener?) {
        blockUntilConstructorFinished()
        player!!.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener?) {
        blockUntilConstructorFinished()
        player!!.removeListener(listener)
    }

    @get:Player.State
    override val playbackState: Int
        get() {
            blockUntilConstructorFinished()
            return player.getPlaybackState()
        }

    @get:PlaybackSuppressionReason
    override val playbackSuppressionReason: Int
        get() {
            blockUntilConstructorFinished()
            return player.getPlaybackSuppressionReason()
        }
    override val playerError: ExoPlaybackException?
        get() {
            blockUntilConstructorFinished()
            return player.getPlayerError()
        }

    @Deprecated("Use {@link #prepare()} instead.")  // Calling deprecated method.
    override fun retry() {
        blockUntilConstructorFinished()
        player!!.retry()
    }

    override val availableCommands: Player.Commands
        get() {
            blockUntilConstructorFinished()
            return player.getAvailableCommands()
        }

    override fun prepare() {
        blockUntilConstructorFinished()
        player!!.prepare()
    }

    @Deprecated("Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.")  // Forwarding deprecated method.
    override fun prepare(mediaSource: MediaSource) {
        blockUntilConstructorFinished()
        player!!.prepare(mediaSource)
    }

    @Deprecated("""Use {@link #setMediaSource(MediaSource, boolean)} and {@link ExoPlayer#prepare()}
        instead.""")  // Forwarding deprecated method.
    override fun prepare(mediaSource: MediaSource, resetPosition: Boolean, resetState: Boolean) {
        blockUntilConstructorFinished()
        player!!.prepare(mediaSource, resetPosition, resetState)
    }

    override fun setMediaItems(mediaItems: List<MediaItem?>?, resetPosition: Boolean) {
        blockUntilConstructorFinished()
        player!!.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: List<MediaItem?>?, startIndex: Int, startPositionMs: Long) {
        blockUntilConstructorFinished()
        player!!.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaSources(mediaSources: List<MediaSource>) {
        blockUntilConstructorFinished()
        player!!.setMediaSources(mediaSources)
    }

    override fun setMediaSources(mediaSources: List<MediaSource>, resetPosition: Boolean) {
        blockUntilConstructorFinished()
        player!!.setMediaSources(mediaSources, resetPosition)
    }

    override fun setMediaSources(
            mediaSources: List<MediaSource>, startMediaItemIndex: Int, startPositionMs: Long) {
        blockUntilConstructorFinished()
        player!!.setMediaSources(mediaSources, startMediaItemIndex, startPositionMs)
    }

    override fun setMediaSource(mediaSource: MediaSource) {
        blockUntilConstructorFinished()
        player!!.setMediaSource(mediaSource)
    }

    override fun setMediaSource(mediaSource: MediaSource, resetPosition: Boolean) {
        blockUntilConstructorFinished()
        player!!.setMediaSource(mediaSource, resetPosition)
    }

    override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) {
        blockUntilConstructorFinished()
        player!!.setMediaSource(mediaSource, startPositionMs)
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem?>?) {
        blockUntilConstructorFinished()
        player!!.addMediaItems(index, mediaItems)
    }

    override fun addMediaSource(mediaSource: MediaSource) {
        blockUntilConstructorFinished()
        player!!.addMediaSource(mediaSource)
    }

    override fun addMediaSource(index: Int, mediaSource: MediaSource) {
        blockUntilConstructorFinished()
        player!!.addMediaSource(index, mediaSource)
    }

    override fun addMediaSources(mediaSources: List<MediaSource>) {
        blockUntilConstructorFinished()
        player!!.addMediaSources(mediaSources)
    }

    override fun addMediaSources(index: Int, mediaSources: List<MediaSource>) {
        blockUntilConstructorFinished()
        player!!.addMediaSources(index, mediaSources)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        blockUntilConstructorFinished()
        player!!.moveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        blockUntilConstructorFinished()
        player!!.removeMediaItems(fromIndex, toIndex)
    }

    override fun setShuffleOrder(shuffleOrder: ShuffleOrder) {
        blockUntilConstructorFinished()
        player!!.setShuffleOrder(shuffleOrder)
    }

    override var playWhenReady: Boolean
        get() {
            blockUntilConstructorFinished()
            return player.getPlayWhenReady()
        }
        set(playWhenReady) {
            blockUntilConstructorFinished()
            player.setPlayWhenReady(playWhenReady)
        }

    override fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean) {
        blockUntilConstructorFinished()
        player!!.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems
    }

    override fun getPauseAtEndOfMediaItems(): Boolean {
        blockUntilConstructorFinished()
        return player!!.pauseAtEndOfMediaItems
    }

    @get:Player.RepeatMode
    override var repeatMode: Int
        get() {
            blockUntilConstructorFinished()
            return player.getRepeatMode()
        }
        set(repeatMode) {
            blockUntilConstructorFinished()
            player.setRepeatMode(repeatMode)
        }
    override var shuffleModeEnabled: Boolean
        get() {
            blockUntilConstructorFinished()
            return player.getShuffleModeEnabled()
        }
        set(shuffleModeEnabled) {
            blockUntilConstructorFinished()
            player.setShuffleModeEnabled(shuffleModeEnabled)
        }
    override val isLoading: Boolean
        get() {
            blockUntilConstructorFinished()
            return player!!.isLoading()
        }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        blockUntilConstructorFinished()
        player!!.seekTo(mediaItemIndex, positionMs)
    }

    override val seekBackIncrement: Long
        get() {
            blockUntilConstructorFinished()
            return player.getSeekBackIncrement()
        }
    override val seekForwardIncrement: Long
        get() {
            blockUntilConstructorFinished()
            return player.getSeekForwardIncrement()
        }
    override val maxSeekToPreviousPosition: Long
        get() {
            blockUntilConstructorFinished()
            return player.getMaxSeekToPreviousPosition()
        }
    override var playbackParameters: PlaybackParameters
        get() {
            blockUntilConstructorFinished()
            return player!!.getPlaybackParameters()
        }
        set(playbackParameters) {
            blockUntilConstructorFinished()
            player!!.setPlaybackParameters(playbackParameters)
        }

    override fun setSeekParameters(seekParameters: SeekParameters?) {
        blockUntilConstructorFinished()
        player!!.setSeekParameters(seekParameters)
    }

    override fun getSeekParameters(): SeekParameters {
        blockUntilConstructorFinished()
        return player!!.seekParameters
    }

    override fun setForegroundMode(foregroundMode: Boolean) {
        blockUntilConstructorFinished()
        player!!.setForegroundMode(foregroundMode)
    }

    override fun stop() {
        blockUntilConstructorFinished()
        player!!.stop()
    }

    @Deprecated("""Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
        just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
        {@link #prepare() re-preparing} the player.""")
    override fun stop(reset: Boolean) {
        blockUntilConstructorFinished()
        player!!.stop(reset)
    }

    override fun release() {
        blockUntilConstructorFinished()
        player!!.release()
    }

    override fun createMessage(target: PlayerMessage.Target): PlayerMessage {
        blockUntilConstructorFinished()
        return player!!.createMessage(target)
    }

    override fun getRendererCount(): Int {
        blockUntilConstructorFinished()
        return player!!.rendererCount
    }

    @TrackType
    override fun getRendererType(index: Int): Int {
        blockUntilConstructorFinished()
        return player!!.getRendererType(index)
    }

    override fun getRenderer(index: Int): Renderer {
        blockUntilConstructorFinished()
        return player!!.getRenderer(index)
    }

    override fun getTrackSelector(): TrackSelector? {
        blockUntilConstructorFinished()
        return player!!.trackSelector
    }

    @Deprecated("Use {@link #getCurrentTracks()}.")
    override fun getCurrentTrackGroups(): TrackGroupArray {
        blockUntilConstructorFinished()
        return player!!.currentTrackGroups
    }

    @Deprecated("Use {@link #getCurrentTracks()}.")
    override fun getCurrentTrackSelections(): TrackSelectionArray {
        blockUntilConstructorFinished()
        return player!!.currentTrackSelections
    }

    override val currentTracks: Tracks
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentTracks()
        }
    override var trackSelectionParameters: TrackSelectionParameters
        get() {
            blockUntilConstructorFinished()
            return player!!.getTrackSelectionParameters()
        }
        set(parameters) {
            blockUntilConstructorFinished()
            player!!.setTrackSelectionParameters(parameters)
        }
    override val mediaMetadata: MediaMetadata
        get() {
            blockUntilConstructorFinished()
            return player.getMediaMetadata()
        }
    override var playlistMetadata: MediaMetadata
        get() {
            blockUntilConstructorFinished()
            return player!!.getPlaylistMetadata()
        }
        set(mediaMetadata) {
            blockUntilConstructorFinished()
            player!!.setPlaylistMetadata(mediaMetadata)
        }
    override val currentTimeline: Timeline
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentTimeline()
        }
    override val currentPeriodIndex: Int
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentPeriodIndex()
        }
    override val currentMediaItemIndex: Int
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentMediaItemIndex()
        }
    override val duration: Long
        get() {
            blockUntilConstructorFinished()
            return player.getDuration()
        }
    override val currentPosition: Long
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentPosition()
        }
    override val bufferedPosition: Long
        get() {
            blockUntilConstructorFinished()
            return player.getBufferedPosition()
        }
    override val totalBufferedDuration: Long
        get() {
            blockUntilConstructorFinished()
            return player.getTotalBufferedDuration()
        }
    override val isPlayingAd: Boolean
        get() {
            blockUntilConstructorFinished()
            return player!!.isPlayingAd()
        }
    override val currentAdGroupIndex: Int
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentAdGroupIndex()
        }
    override val currentAdIndexInAdGroup: Int
        get() {
            blockUntilConstructorFinished()
            return player.getCurrentAdIndexInAdGroup()
        }
    override val contentPosition: Long
        get() {
            blockUntilConstructorFinished()
            return player.getContentPosition()
        }
    override val contentBufferedPosition: Long
        get() {
            blockUntilConstructorFinished()
            return player.getContentBufferedPosition()
        }

    @Deprecated("Use {@link #setWakeMode(int)} instead.")
    override fun setHandleWakeLock(handleWakeLock: Boolean) {
        blockUntilConstructorFinished()
        player!!.setHandleWakeLock(handleWakeLock)
    }

    override fun setWakeMode(@WakeMode wakeMode: Int) {
        blockUntilConstructorFinished()
        player!!.setWakeMode(wakeMode)
    }

    override val deviceInfo: DeviceInfo
        get() {
            blockUntilConstructorFinished()
            return player.getDeviceInfo()
        }
    override var deviceVolume: Int
        get() {
            blockUntilConstructorFinished()
            return player!!.getDeviceVolume()
        }
        set(volume) {
            blockUntilConstructorFinished()
            player!!.setDeviceVolume(volume)
        }
    override var isDeviceMuted: Boolean
        get() {
            blockUntilConstructorFinished()
            return player!!.isDeviceMuted()
        }
        set(muted) {
            blockUntilConstructorFinished()
            player!!.setDeviceMuted(muted)
        }

    override fun increaseDeviceVolume() {
        blockUntilConstructorFinished()
        player!!.increaseDeviceVolume()
    }

    override fun decreaseDeviceVolume() {
        blockUntilConstructorFinished()
        player!!.decreaseDeviceVolume()
    }

    override fun isTunnelingEnabled(): Boolean {
        blockUntilConstructorFinished()
        return player!!.isTunnelingEnabled
    }

    /* package */
    fun setThrowsWhenUsingWrongThread(throwsWhenUsingWrongThread: Boolean) {
        blockUntilConstructorFinished()
        player!!.setThrowsWhenUsingWrongThread(throwsWhenUsingWrongThread)
    }

    private fun blockUntilConstructorFinished() {
        // The constructor may be executed on a background thread. Wait with accessing the player from
        // the app thread until the constructor finished executing.
        constructorFinished.blockUninterruptible()
    }
}