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

import androidx.annotation.IntDef
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.source.SampleStream
import com.google.android.exoplayer2.util.MediaClock
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Renders media read from a [SampleStream].
 *
 *
 * Internally, a renderer's lifecycle is managed by the owning [ExoPlayer]. The renderer is
 * transitioned through various states as the overall playback state and enabled tracks change. The
 * valid state transitions are shown below, annotated with the methods that are called during each
 * transition.
 *
 *
 * <img src="doc-files/renderer-states.svg" alt="Renderer state
transitions"></img>
 */
interface Renderer : PlayerMessage.Target {
    /**
     * Some renderers can signal when [.render] should be called.
     *
     *
     * That allows the player to sleep until the next wakeup, instead of calling [ ][.render] in a tight loop. The aim of this interrupt based scheduling is to save
     * power.
     */
    interface WakeupListener {
        /**
         * The renderer no longer needs to render until the next wakeup.
         *
         *
         * Must be called from the thread ExoPlayer invokes the renderer from.
         */
        fun onSleep()

        /**
         * The renderer needs to render some frames. The client should call [.render]
         * at its earliest convenience.
         *
         *
         * Can be called from any thread.
         */
        fun onWakeup()
    }

    /**
     * Represents a type of message that can be passed to a renderer. May be one of [ ][.MSG_SET_VIDEO_OUTPUT], [.MSG_SET_VOLUME], [.MSG_SET_AUDIO_ATTRIBUTES], [ ][.MSG_SET_SCALING_MODE], [.MSG_SET_CHANGE_FRAME_RATE_STRATEGY], [ ][.MSG_SET_AUX_EFFECT_INFO], [.MSG_SET_VIDEO_FRAME_METADATA_LISTENER], [ ][.MSG_SET_CAMERA_MOTION_LISTENER], [.MSG_SET_SKIP_SILENCE_ENABLED], [ ][.MSG_SET_AUDIO_SESSION_ID] or [.MSG_SET_WAKEUP_LISTENER]. May also be an app-defined
     * value (see [.MSG_CUSTOM_BASE]).
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(open = true, value = [
        MSG_SET_VIDEO_OUTPUT,
        MSG_SET_VOLUME,
        MSG_SET_AUDIO_ATTRIBUTES,
        MSG_SET_SCALING_MODE,
        MSG_SET_CHANGE_FRAME_RATE_STRATEGY,
        MSG_SET_AUX_EFFECT_INFO,
        MSG_SET_VIDEO_FRAME_METADATA_LISTENER,
        MSG_SET_CAMERA_MOTION_LISTENER,
        MSG_SET_SKIP_SILENCE_ENABLED,
        MSG_SET_AUDIO_SESSION_ID,
        MSG_SET_WAKEUP_LISTENER
    ])
    annotation class MessageType

    /**
     * The renderer states. One of [.STATE_DISABLED], [.STATE_ENABLED] or [ ][.STATE_STARTED].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([STATE_DISABLED, STATE_ENABLED, STATE_STARTED])
    annotation class State

    /**
     * Returns the name of this renderer, for logging and debugging purposes. Should typically be the
     * renderer's (un-obfuscated) class name.
     *
     * @return The name of this renderer.
     */
    open fun getName(): String?

    /**
     * Returns the track type that the renderer handles.
     *
     * @see ExoPlayer.getRendererType
     * @return The [track type][C.TrackType].
     */
    @C.TrackType
    open fun getTrackType(): Int

    /**
     * Returns the capabilities of the renderer.
     *
     * @return The capabilities of the renderer.
     */
    open fun getCapabilities(): RendererCapabilities?

    /**
     * Initializes the renderer for playback with a player.
     *
     * @param index The renderer index within the player.
     * @param playerId The [PlayerId] of the player.
     */
    fun init(index: Int, playerId: PlayerId?)

    /**
     * If the renderer advances its own playback position then this method returns a corresponding
     * [MediaClock]. If provided, the player will use the returned [MediaClock] as its
     * source of time during playback. A player may have at most one renderer that returns a [ ] from this method.
     *
     * @return The [MediaClock] tracking the playback position of the renderer, or null.
     */
    open fun getMediaClock(): MediaClock?

    /**
     * Returns the current state of the renderer.
     *
     * @return The current state. One of [.STATE_DISABLED], [.STATE_ENABLED] and [     ][.STATE_STARTED].
     */
    @State
    open fun getState(): Int

    /**
     * Enables the renderer to consume from the specified [SampleStream].
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_DISABLED].
     *
     * @param configuration The renderer configuration.
     * @param formats The enabled formats.
     * @param stream The [SampleStream] from which the renderer should consume.
     * @param positionUs The player's current position.
     * @param joining Whether this renderer is being enabled to join an ongoing playback.
     * @param mayRenderStartOfStream Whether this renderer is allowed to render the start of the
     * stream even if the state is not [.STATE_STARTED] yet.
     * @param startPositionUs The start position of the stream in renderer time (microseconds).
     * @param offsetUs The offset to be added to timestamps of buffers read from `stream` before
     * they are rendered.
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun enable(
            configuration: RendererConfiguration?,
            formats: Array<Format?>?,
            stream: SampleStream?,
            positionUs: Long,
            joining: Boolean,
            mayRenderStartOfStream: Boolean,
            startPositionUs: Long,
            offsetUs: Long)

    /**
     * Starts the renderer, meaning that calls to [.render] will cause media to be
     * rendered.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED].
     *
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun start()

    /**
     * Replaces the [SampleStream] from which samples will be consumed.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @param formats The enabled formats.
     * @param stream The [SampleStream] from which the renderer should consume.
     * @param startPositionUs The start position of the new stream in renderer time (microseconds).
     * @param offsetUs The offset to be added to timestamps of buffers read from `stream` before
     * they are rendered.
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun replaceStream(formats: Array<Format?>?, stream: SampleStream?, startPositionUs: Long, offsetUs: Long)

    /** Returns the [SampleStream] being consumed, or null if the renderer is disabled.  */
    open fun getStream(): SampleStream?

    /**
     * Returns whether the renderer has read the current [SampleStream] to the end.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     */
    fun hasReadStreamToEnd(): Boolean

    /**
     * Returns the renderer time up to which the renderer has read samples, in microseconds, or [ ][C.TIME_END_OF_SOURCE] if the renderer has read the current [SampleStream] to the end.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     */
    open fun getReadingPositionUs(): Long

    /**
     * Signals to the renderer that the current [SampleStream] will be the final one supplied
     * before it is next disabled or reset.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     */
    fun setCurrentStreamFinal()

    /**
     * Returns whether the current [SampleStream] will be the final one supplied before the
     * renderer is next disabled or reset.
     */
    open fun isCurrentStreamFinal(): Boolean

    /**
     * Throws an error that's preventing the renderer from reading from its [SampleStream]. Does
     * nothing if no such error exists.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @throws IOException An error that's preventing the renderer from making progress or buffering
     * more data.
     */
    @Throws(IOException::class)
    fun maybeThrowStreamError()

    /**
     * Signals to the renderer that a position discontinuity has occurred.
     *
     *
     * After a position discontinuity, the renderer's [SampleStream] is guaranteed to provide
     * samples starting from a key frame.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @param positionUs The new playback position in microseconds.
     * @throws ExoPlaybackException If an error occurs handling the reset.
     */
    @Throws(ExoPlaybackException::class)
    fun resetPosition(positionUs: Long)

    /**
     * Indicates the playback speed to this renderer.
     *
     *
     * The default implementation is a no-op.
     *
     * @param currentPlaybackSpeed The factor by which playback is currently sped up.
     * @param targetPlaybackSpeed The target factor by which playback should be sped up. This may be
     * different from `currentPlaybackSpeed`, for example, if the speed is temporarily
     * adjusted for live playback.
     * @throws ExoPlaybackException If an error occurs handling the playback speed.
     */
    @Throws(ExoPlaybackException::class)
    fun setPlaybackSpeed(currentPlaybackSpeed: Float, targetPlaybackSpeed: Float) {
    }

    /**
     * Incrementally renders the [SampleStream].
     *
     *
     * If the renderer is in the [.STATE_ENABLED] state then each call to this method will do
     * work toward being ready to render the [SampleStream] when the renderer is started. If the
     * renderer is in the [.STATE_STARTED] state then calls to this method will render the
     * [SampleStream] in sync with the specified media positions.
     *
     *
     * The renderer may also render the very start of the media at the current position (e.g. the
     * first frame of a video stream) while still in the [.STATE_ENABLED] state, unless it's the
     * initial start of the media after calling [.enable] with `mayRenderStartOfStream` set to
     * `false`.
     *
     *
     * This method should return quickly, and should not block if the renderer is unable to make
     * useful progress.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @param positionUs The current media time in microseconds, measured at the start of the current
     * iteration of the rendering loop.
     * @param elapsedRealtimeUs [android.os.SystemClock.elapsedRealtime] in microseconds,
     * measured at the start of the current iteration of the rendering loop.
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun render(positionUs: Long, elapsedRealtimeUs: Long)

    /**
     * Whether the renderer is able to immediately render media from the current position.
     *
     *
     * If the renderer is in the [.STATE_STARTED] state then returning true indicates that
     * the renderer has everything that it needs to continue playback. Returning false indicates that
     * the player should pause until the renderer is ready.
     *
     *
     * If the renderer is in the [.STATE_ENABLED] state then returning true indicates that
     * the renderer is ready for playback to be started. Returning false indicates that it is not.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @return Whether the renderer is ready to render media.
     */
    open fun isReady(): Boolean

    /**
     * Whether the renderer is ready for the [ExoPlayer] instance to transition to [ ][Player.STATE_ENDED]. The player will make this transition as soon as `true` is returned
     * by all of its renderers.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED], [.STATE_STARTED].
     *
     * @return Whether the renderer is ready for the player to transition to the ended state.
     */
    open fun isEnded(): Boolean

    /**
     * Stops the renderer, transitioning it to the [.STATE_ENABLED] state.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_STARTED].
     */
    fun stop()

    /**
     * Disable the renderer, transitioning it to the [.STATE_DISABLED] state.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_ENABLED].
     */
    fun disable()

    /**
     * Forces the renderer to give up any resources (e.g. media decoders) that it may be holding. If
     * the renderer is not holding any resources, the call is a no-op.
     *
     *
     * This method may be called when the renderer is in the following states: [ ][.STATE_DISABLED].
     */
    fun reset()

    companion object {
        /**
         * The type of a message that can be passed to a video renderer via [ ][ExoPlayer.createMessage]. The message payload is normally a [ ], however some video renderers may accept other outputs (e.g., [ ]).
         *
         *
         * If the receiving renderer does not support the payload type as an output, then it will clear
         * any existing output that it has.
         */
        const val MSG_SET_VIDEO_OUTPUT = 1

        /**
         * A type of a message that can be passed to an audio renderer via [ ][ExoPlayer.createMessage]. The message payload should be a [Float]
         * with 0 being silence and 1 being unity gain.
         */
        const val MSG_SET_VOLUME = 2

        /**
         * A type of a message that can be passed to an audio renderer via [ ][ExoPlayer.createMessage]. The message payload should be an [ ] instance that will configure the underlying audio track. If not set, the
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
         * To get audio attributes that are equivalent to a legacy stream type, pass the stream type to
         * [Util.getAudioUsageForStreamType] and use the returned [C.AudioUsage] to build
         * an audio attributes instance.
         */
        const val MSG_SET_AUDIO_ATTRIBUTES = 3

        /**
         * The type of a message that can be passed to a [MediaCodec]-based video renderer via
         * [ExoPlayer.createMessage]. The message payload should be one of the
         * integer scaling modes in [C.VideoScalingMode].
         *
         *
         * Note that the scaling mode only applies if the [Surface] targeted by the renderer is
         * owned by a [android.view.SurfaceView].
         */
        const val MSG_SET_SCALING_MODE = 4

        /**
         * The type of a message that can be passed to a video renderer via [ ][ExoPlayer.createMessage]. The message payload should be one of the
         * integer strategy constants in [C.VideoChangeFrameRateStrategy].
         */
        const val MSG_SET_CHANGE_FRAME_RATE_STRATEGY = 5

        /**
         * A type of a message that can be passed to an audio renderer via [ ][ExoPlayer.createMessage]. The message payload should be an [ ] instance representing an auxiliary audio effect for the underlying audio track.
         */
        const val MSG_SET_AUX_EFFECT_INFO = 6

        /**
         * The type of a message that can be passed to a video renderer via [ ][ExoPlayer.createMessage]. The message payload should be a [ ] instance, or null.
         */
        const val MSG_SET_VIDEO_FRAME_METADATA_LISTENER = 7

        /**
         * The type of a message that can be passed to a camera motion renderer via [ ][ExoPlayer.createMessage]. The message payload should be a [ ] instance, or null.
         */
        const val MSG_SET_CAMERA_MOTION_LISTENER = 8

        /**
         * The type of a message that can be passed to an audio renderer via [ ][ExoPlayer.createMessage]. The message payload should be a [Boolean]
         * instance telling whether to enable or disable skipping silences in the audio stream.
         */
        const val MSG_SET_SKIP_SILENCE_ENABLED = 9

        /**
         * The type of a message that can be passed to audio and video renderers via [ ][ExoPlayer.createMessage]. The message payload should be an [ ] instance representing the audio session ID that will be attached to the underlying
         * audio track. Video renderers that support tunneling will use the audio session ID when
         * tunneling is enabled.
         */
        const val MSG_SET_AUDIO_SESSION_ID = 10

        /**
         * The type of a message that can be passed to a [Renderer] via [ ][ExoPlayer.createMessage], to inform the renderer that it can schedule
         * waking up another component.
         *
         *
         * The message payload must be a [WakeupListener] instance.
         */
        const val MSG_SET_WAKEUP_LISTENER = 11

        /**
         * The type of a message that can be passed to audio renderers via [ ][ExoPlayer.createMessage]. The message payload should be an [ ] instance representing the preferred audio device, or null to
         * restore the default.
         */
        const val MSG_SET_PREFERRED_AUDIO_DEVICE = 12

        /**
         * Applications or extensions may define custom `MSG_*` constants that can be passed to
         * renderers. These custom constants must be greater than or equal to this value.
         */
        const val MSG_CUSTOM_BASE = 10000

        /**
         * The renderer is disabled. A renderer in this state will not proactively acquire resources that
         * it requires for rendering (e.g., media decoders), but may continue to hold any that it already
         * has. [.reset] can be called to force the renderer to release such resources.
         */
        const val STATE_DISABLED = 0

        /**
         * The renderer is enabled but not started. A renderer in this state may render media at the
         * current position (e.g. an initial video frame), but the position will not advance. A renderer
         * in this state will typically hold resources that it requires for rendering (e.g. media
         * decoders).
         */
        const val STATE_ENABLED = 1

        /**
         * The renderer is started. Calls to [.render] will cause media to be rendered.
         */
        const val STATE_STARTED = 2
    }
}