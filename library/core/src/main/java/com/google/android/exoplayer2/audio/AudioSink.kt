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
package com.google.android.exoplayer2.audio

import android.media.AudioDeviceInfo
import android.media.AudioTrack
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2import.PlaybackParameters
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.nio.ByteBuffer

/**
 * A sink that consumes audio data.
 *
 *
 * Before starting playback, specify the input audio format by calling [.configure].
 *
 *
 * Call [.handleBuffer] to write data, and [ ][.handleDiscontinuity] when the data being fed is discontinuous. Call [.play] to start
 * playing the written data.
 *
 *
 * Call [.configure] whenever the input format changes. The sink will
 * be reinitialized on the next call to [.handleBuffer].
 *
 *
 * Call [.flush] to prepare the sink to receive audio data from a new playback position.
 *
 *
 * Call [.playToEndOfStream] repeatedly to play out all data when no more input buffers
 * will be provided via [.handleBuffer] until the next [ ][.flush]. Call [.reset] when the instance is no longer required.
 *
 *
 * The implementation may be backed by a platform [AudioTrack]. In this case, [ ][.setAudioSessionId], [.setAudioAttributes], [ ][.enableTunnelingV21] and [.disableTunneling] may be called before writing data to the
 * sink. These methods may also be called after writing data to the sink, in which case it will be
 * reinitialized as required. For implementations that are not based on platform [ ]s, calling methods relating to audio sessions, audio attributes, and tunneling may
 * have no effect.
 */
interface AudioSink {
    /** Listener for audio sink events.  */
    interface Listener {
        /**
         * Called when the audio sink handles a buffer whose timestamp is discontinuous with the last
         * buffer handled since it was reset.
         */
        fun onPositionDiscontinuity()

        /**
         * Called when the audio sink's position has increased for the first time since it was last
         * paused or flushed.
         *
         * @param playoutStartSystemTimeMs The approximate derived [System.currentTimeMillis] at
         * which playout started. Only valid if the audio track has not underrun.
         */
        fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {}

        /**
         * Called when the audio sink runs out of data.
         *
         *
         * An audio sink implementation may never call this method (for example, if audio data is
         * consumed in batches rather than based on the sink's own clock).
         *
         * @param bufferSize The size of the sink's buffer, in bytes.
         * @param bufferSizeMs The size of the sink's buffer, in milliseconds, if it is configured for
         * PCM output. [C.TIME_UNSET] if it is configured for encoded audio output, as the
         * buffered media can have a variable bitrate so the duration may be unknown.
         * @param elapsedSinceLastFeedMs The time since the sink was last fed data, in milliseconds.
         */
        fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long)

        /**
         * Called when skipping silences is enabled or disabled.
         *
         * @param skipSilenceEnabled Whether skipping silences is enabled.
         */
        fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean)

        /** Called when the offload buffer has been partially emptied.  */
        fun onOffloadBufferEmptying() {}

        /** Called when the offload buffer has been filled completely.  */
        fun onOffloadBufferFull() {}

        /**
         * Called when [AudioSink] has encountered an error.
         *
         *
         * If the sink writes to a platform [AudioTrack], this will called for all [ ] errors.
         *
         *
         * This method being called does not indicate that playback has failed, or that it will fail.
         * The player may be able to recover from the error (for example by recreating the AudioTrack,
         * possibly with different settings) and continue. Hence applications should *not*
         * implement this method to display a user visible error or initiate an application level retry
         * ([Player.Listener.onPlayerError] is the appropriate place to implement such behavior).
         * This method is called to provide the application with an opportunity to log the error if it
         * wishes to do so.
         *
         *
         * Fatal errors that cannot be recovered will be reported wrapped in a [ ] by [Player.Listener.onPlayerError].
         *
         * @param audioSinkError The error that occurred. Typically an [InitializationException],
         * a [WriteException], or an [UnexpectedDiscontinuityException].
         */
        fun onAudioSinkError(audioSinkError: Exception?) {}
    }

    /** Thrown when a failure occurs configuring the sink.  */
    class ConfigurationException : Exception {
        /** Input [Format] of the sink when the configuration failure occurs.  */
        var format: Format? = null

        /** Creates a new configuration exception with the specified `cause` and no message.  */
        constructor(cause: Throwable?, format: Format) : super(cause) {
            this.format = format
        }

        /** Creates a new configuration exception with the specified `message` and no cause.  */
        constructor(message: String?, format: Format) : super(message) {
            this.format = format
        }
    }

    /** Thrown when a failure occurs initializing the sink.  */
    class InitializationException : Exception {

        /** The underlying [AudioTrack]'s state.  */
        var audioTrackState = 0

        /** If the exception can be recovered by recreating the sink.  */
        var isRecoverable = false

        /** The input [Format] of the sink when the error occurs.  */
        var format: Format? = null

        /**
         * Creates a new instance.
         *
         * @param audioTrackState The underlying [AudioTrack]'s state.
         * @param sampleRate The requested sample rate in Hz.
         * @param channelConfig The requested channel configuration.
         * @param bufferSize The requested buffer size in bytes.
         * @param format The input format of the sink when the error occurs.
         * @param isRecoverable Whether the exception can be recovered by recreating the sink.
         * @param audioTrackException Exception thrown during the creation of the [AudioTrack].
         */
        constructor(
                audioTrackState: Int,
                sampleRate: Int,
                channelConfig: Int,
                bufferSize: Int,
                format: Format?,
                isRecoverable: Boolean,
                audioTrackException: Exception?) : super(
                "AudioTrack init failed "
                        + audioTrackState
                        + " "
                        + "Config($sampleRate, $channelConfig, $bufferSize)"
                        + if (isRecoverable) " (recoverable)" else "",
                audioTrackException) {

            this.audioTrackState = audioTrackState
            this.isRecoverable = isRecoverable
            this.format = format
        }
    }

    /** Thrown when a failure occurs writing to the sink.  */
    class WriteException : Exception {
        /**
         * The error value returned from the sink implementation. If the sink writes to a platform
         * [AudioTrack], this will be the error value returned from [ ][AudioTrack.write] or [AudioTrack.write].
         * Otherwise, the meaning of the error code depends on the sink implementation.
         */
        var errorCode = 0

        /** If the exception can be recovered by recreating the sink.  */
        var isRecoverable = false

        /** The input [Format] of the sink when the error occurs.  */
        var format: Format? = null

        /**
         * Creates an instance.
         *
         * @param errorCode The error value returned from the sink implementation.
         * @param format The input format of the sink when the error occurs.
         * @param isRecoverable Whether the exception can be recovered by recreating the sink.
         */
        constructor(errorCode: Int, format: Format?, isRecoverable: Boolean) : super("AudioTrack write failed: $errorCode") {
            this.isRecoverable = isRecoverable
            this.errorCode = errorCode
            this.format = format
        }
    }

    /** Thrown when the sink encounters an unexpected timestamp discontinuity.  */
    class UnexpectedDiscontinuityException : Exception {
        /** The actual presentation time of a sample, in microseconds.  */
        private var actualPresentationTimeUs: Long = 0

        /** The expected presentation time of a sample, in microseconds.  */
        private var expectedPresentationTimeUs: Long = 0

        /**
         * Creates an instance.
         *
         * @param actualPresentationTimeUs The actual presentation time of a sample, in microseconds.
         * @param expectedPresentationTimeUs The expected presentation time of a sample, in
         * microseconds.
         */
        constructor(actualPresentationTimeUs: Long, expectedPresentationTimeUs: Long) : super(
                "Unexpected audio track timestamp discontinuity: expected "
                        + expectedPresentationTimeUs
                        + ", got "
                        + actualPresentationTimeUs) {
            this.actualPresentationTimeUs = actualPresentationTimeUs
            this.expectedPresentationTimeUs = expectedPresentationTimeUs
        }
    }

    /**
     * The level of support the sink provides for a format. One of [ ][.SINK_FORMAT_SUPPORTED_DIRECTLY], [.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING] or [ ][.SINK_FORMAT_UNSUPPORTED].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([SINK_FORMAT_SUPPORTED_DIRECTLY, SINK_FORMAT_SUPPORTED_WITH_TRANSCODING, SINK_FORMAT_UNSUPPORTED])
    annotation class SinkFormatSupport

    /**
     * Sets the listener for sink events, which should be the audio renderer.
     *
     * @param listener The listener for sink events, which should be the audio renderer.
     */
    fun setListener(listener: Listener?)

    /**
     * Sets the [PlayerId] of the player using this audio sink.
     *
     * @param playerId The [PlayerId], or null to clear a previously set id.
     */
    fun setPlayerId(playerId: PlayerId?) {}

    /**
     * Returns whether the sink supports a given [Format].
     *
     * @param format The format.
     * @return Whether the sink supports the format.
     */
    fun supportsFormat(format: Format?): Boolean

    /**
     * Returns the level of support that the sink provides for a given [Format].
     *
     * @param format The format.
     * @return The level of support provided.
     */
    @SinkFormatSupport
    fun getFormatSupport(format: Format?): Int

    /**
     * Returns the playback position in the stream starting at zero, in microseconds, or [ ][.CURRENT_POSITION_NOT_SET] if it is not yet available.
     *
     * @param sourceEnded Specify `true` if no more input buffers will be provided.
     * @return The playback position relative to the start of playback, in microseconds.
     */
    fun getCurrentPositionUs(sourceEnded: Boolean): Long

    /**
     * Configures (or reconfigures) the sink.
     *
     * @param inputFormat The format of audio data provided in the input buffers.
     * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to infer a
     * suitable buffer size.
     * @param outputChannels A mapping from input to output channels that is applied to this sink's
     * input as a preprocessing step, if handling PCM input. Specify `null` to leave the
     * input unchanged. Otherwise, the element at index `i` specifies index of the input
     * channel to map to output channel `i` when preprocessing input buffers. After the map
     * is applied the audio data will have `outputChannels.length` channels.
     * @throws ConfigurationException If an error occurs configuring the sink.
     */
    @Throws(ConfigurationException::class)
    fun configure(inputFormat: Format?, specifiedBufferSize: Int, outputChannels: IntArray?)

    /** Starts or resumes consuming audio if initialized.  */
    fun play()

    /** Signals to the sink that the next buffer may be discontinuous with the previous buffer.  */
    fun handleDiscontinuity()

    /**
     * Attempts to process data from a [ByteBuffer], starting from its current position and
     * ending at its limit (exclusive). The position of the [ByteBuffer] is advanced by the
     * number of bytes that were handled. [Listener.onPositionDiscontinuity] will be called if
     * `presentationTimeUs` is discontinuous with the last buffer handled since the last reset.
     *
     *
     * Returns whether the data was handled in full. If the data was not handled in full then the
     * same [ByteBuffer] must be provided to subsequent calls until it has been fully consumed,
     * except in the case of an intervening call to [.flush] (or to [.configure] that causes the sink to be flushed).
     *
     * @param buffer The buffer containing audio data.
     * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
     * @param encodedAccessUnitCount The number of encoded access units in the buffer, or 1 if the
     * buffer contains PCM audio. This allows batching multiple encoded access units in one
     * buffer.
     * @return Whether the buffer was handled fully.
     * @throws InitializationException If an error occurs initializing the sink.
     * @throws WriteException If an error occurs writing the audio data.
     */
    @Throws(InitializationException::class, WriteException::class)
    fun handleBuffer(buffer: ByteBuffer?, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean

    /**
     * Processes any remaining data. [.isEnded] will return `true` when no data remains.
     *
     * @throws WriteException If an error occurs draining data to the sink.
     */
    @Throws(WriteException::class)
    fun playToEndOfStream()

    /**
     * Returns whether [.playToEndOfStream] has been called and all buffers have been processed.
     */
    open fun isEnded(): Boolean

    /** Returns whether the sink has data pending that has not been consumed yet.  */
    fun hasPendingData(): Boolean

    /**
     * Attempts to set the playback parameters. The audio sink may override these parameters if they
     * are not supported.
     *
     * @param playbackParameters The new playback parameters to attempt to set.
     */
    fun setPlaybackParameters(playbackParameters: PlaybackParameters?)

    /** Returns the active [PlaybackParameters].  */
    fun getPlaybackParameters(): PlaybackParameters?

    /** Sets whether silences should be skipped in the audio stream.  */
    fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean)

    /** Returns whether silences are skipped in the audio stream.  */
    fun getSkipSilenceEnabled(): Boolean

    /**
     * Sets attributes for audio playback. If the attributes have changed and if the sink is not
     * configured for use with tunneling, then it is reset and the audio session id is cleared.
     *
     *
     * If the sink is configured for use with tunneling then the audio attributes are ignored. The
     * sink is not reset and the audio session id is not cleared. The passed attributes will be used
     * if the sink is later re-configured into non-tunneled mode.
     *
     * @param audioAttributes The attributes for audio playback.
     */
    fun setAudioAttributes(audioAttributes: AudioAttributes?)

    /**
     * Returns the audio attributes used for audio playback, or `null` if the sink does not use
     * audio attributes.
     */
    fun getAudioAttributes(): AudioAttributes?

    /** Sets the audio session id.  */
    fun setAudioSessionId(audioSessionId: Int)

    /** Sets the auxiliary effect.  */
    fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo?)

    /**
     * Sets the preferred audio device.
     *
     * @param audioDeviceInfo The preferred [audio device][AudioDeviceInfo], or null to
     * restore the default.
     */
    @RequiresApi(23)
    fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
    }

    /**
     * Sets the offset that is added to the media timestamp before it is passed as `presentationTimeUs` in [.handleBuffer].
     *
     * @param outputStreamOffsetUs The output stream offset in microseconds.
     */
    fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {}

    /**
     * Enables tunneling, if possible. The sink is reset if tunneling was previously disabled.
     * Enabling tunneling is only possible if the sink is based on a platform [AudioTrack], and
     * requires platform API version 21 onwards.
     *
     * @throws IllegalStateException Thrown if enabling tunneling on platform API version &lt; 21.
     */
    fun enableTunnelingV21()

    /**
     * Disables tunneling. If tunneling was previously enabled then the sink is reset and any audio
     * session id is cleared.
     */
    fun disableTunneling()

    /**
     * Sets the playback volume.
     *
     * @param volume Linear output gain to apply to all channels. Should be in the range [0.0, 1.0].
     */
    fun setVolume(volume: Float)

    /** Pauses playback.  */
    fun pause()

    /**
     * Flushes the sink, after which it is ready to receive buffers from a new playback position.
     *
     *
     * The audio session may remain active until [.reset] is called.
     */
    fun flush()

    /**
     * Flushes the sink, after which it is ready to receive buffers from a new playback position.
     *
     *
     * Does not release the [AudioTrack] held by the sink.
     *
     *
     * This method is experimental, and will be renamed or removed in a future release.
     *
     *
     * Only for experimental use as part of [ ][MediaCodecAudioRenderer.experimentalSetEnableKeepAudioTrackOnSeek].
     */
    fun experimentalFlushWithoutAudioTrackRelease()

    /** Resets the sink, releasing any resources that it currently holds.  */
    fun reset()

    companion object {
        /** The sink supports the format directly, without the need for internal transcoding.  */
        const val SINK_FORMAT_SUPPORTED_DIRECTLY = 2

        /**
         * The sink supports the format, but needs to transcode it internally to do so. Internal
         * transcoding may result in lower quality and higher CPU load in some cases.
         */
        const val SINK_FORMAT_SUPPORTED_WITH_TRANSCODING = 1

        /** The sink does not support the format.  */
        const val SINK_FORMAT_UNSUPPORTED = 0

        /** Returned by [.getCurrentPositionUs] when the position is not set.  */
        const val CURRENT_POSITION_NOT_SET = Long.MIN_VALUE
    }
}