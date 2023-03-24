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
package com.google.android.exoplayer2.audio

import android.os.Handler
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util.castNonNull

/**
 * Listener of audio [Renderer] events. All methods have no-op default implementations to
 * allow selective overrides.
 */
interface AudioRendererEventListener {
    /**
     * Called when the renderer is enabled.
     *
     * @param counters [DecoderCounters] that will be updated by the renderer for as long as it
     * remains enabled.
     */
    fun onAudioEnabled(counters: DecoderCounters?) {}

    /**
     * Called when a decoder is created.
     *
     * @param decoderName The decoder that was created.
     * @param initializedTimestampMs [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onAudioDecoderInitialized(
            decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long) {
    }

    @Deprecated("Use {@link #onAudioInputFormatChanged(Format, DecoderReuseEvaluation)}.")
    fun onAudioInputFormatChanged(format: Format?) {
    }

    /**
     * Called when the format of the media being consumed by the renderer changes.
     *
     * @param format The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onAudioInputFormatChanged(
            format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?) {
    }

    /**
     * Called when the audio position has increased for the first time since the last pause or
     * position reset.
     *
     * @param playoutStartSystemTimeMs The approximate derived [System.currentTimeMillis] at
     * which playout started.
     */
    fun onAudioPositionAdvancing(playoutStartSystemTimeMs: Long) {}

    /**
     * Called when an audio underrun occurs.
     *
     * @param bufferSize The size of the audio output buffer, in bytes.
     * @param bufferSizeMs The size of the audio output buffer, in milliseconds, if it contains PCM
     * encoded audio. [C.TIME_UNSET] if the output buffer contains non-PCM encoded audio.
     * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
     */
    fun onAudioUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {}

    /**
     * Called when a decoder is released.
     *
     * @param decoderName The decoder that was released.
     */
    fun onAudioDecoderReleased(decoderName: String?) {}

    /**
     * Called when the renderer is disabled.
     *
     * @param counters [DecoderCounters] that were updated by the renderer.
     */
    fun onAudioDisabled(counters: DecoderCounters?) {}

    /**
     * Called when skipping silences is enabled or disabled in the audio stream.
     *
     * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
     */
    fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}

    /**
     * Called when an audio decoder encounters an error.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param audioCodecError The error. Typically a [CodecException] if the renderer uses
     * [MediaCodec], or a [DecoderException] if the renderer uses a software decoder.
     */
    fun onAudioCodecError(audioCodecError: Exception?) {}

    /**
     * Called when [AudioSink] has encountered an error.
     *
     *
     * If the sink writes to a platform [AudioTrack], this will be called for all [ ] errors.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param audioSinkError The error that occurred. Typically an [     ], a [AudioSink.WriteException], or an [     ].
     */
    fun onAudioSinkError(audioSinkError: Exception?) {}

    /** Dispatches events to an [AudioRendererEventListener].  */
    class EventDispatcher {
        private var handler: Handler? = null
        private var listener: AudioRendererEventListener? = null

        /**
         * @param handler A handler for dispatching events, or null if events should not be dispatched.
         * @param listener The listener to which events should be dispatched, or null if events should
         * not be dispatched.
         */
        constructor(
                handler: Handler?, listener: AudioRendererEventListener?) {
            this.handler = if (listener != null) checkNotNull(handler) else null
            this.listener = listener
        }

        /** Invokes [AudioRendererEventListener.onAudioEnabled].  */
        fun enabled(decoderCounters: DecoderCounters?) {
            handler?.post { castNonNull(listener)?.onAudioEnabled(decoderCounters) }
        }

        /** Invokes [AudioRendererEventListener.onAudioDecoderInitialized].  */
        fun decoderInitialized(
                decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long) {
            handler?.post {
                castNonNull(listener)?.onAudioDecoderInitialized(
                        decoderName, initializedTimestampMs, initializationDurationMs)
            }
        }

        /** Invokes [AudioRendererEventListener.onAudioInputFormatChanged].  */
        // Calling deprecated listener method.
        fun inputFormatChanged(
                format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            handler?.post {
                castNonNull(listener)?.onAudioInputFormatChanged(format)
                castNonNull(listener)?.onAudioInputFormatChanged(format, decoderReuseEvaluation)
            }
        }

        /** Invokes [AudioRendererEventListener.onAudioPositionAdvancing].  */
        fun positionAdvancing(playoutStartSystemTimeMs: Long) {
            handler?.post { castNonNull(listener).onAudioPositionAdvancing(playoutStartSystemTimeMs) }
        }

        /** Invokes [AudioRendererEventListener.onAudioUnderrun].  */
        fun underrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
            handler?.post {
                castNonNull(listener)
                        .onAudioUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
            }
        }

        /** Invokes [AudioRendererEventListener.onAudioDecoderReleased].  */
        fun decoderReleased(decoderName: String?) {
            handler?.post { castNonNull(listener).onAudioDecoderReleased(decoderName) }
        }

        /** Invokes [AudioRendererEventListener.onAudioDisabled].  */
        fun disabled(counters: DecoderCounters) {
            counters.ensureUpdated()
            handler?.post {
                counters.ensureUpdated()
                castNonNull(listener).onAudioDisabled(counters)
            }
        }

        /** Invokes [AudioRendererEventListener.onSkipSilenceEnabledChanged].  */
        fun skipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
            handler?.post { castNonNull(listener).onSkipSilenceEnabledChanged(skipSilenceEnabled) }
        }

        /** Invokes [AudioRendererEventListener.onAudioSinkError].  */
        fun audioSinkError(audioSinkError: Exception?) {
            handler?.post { castNonNull(listener).onAudioSinkError(audioSinkError) }
        }

        /** Invokes [AudioRendererEventListener.onAudioCodecError].  */
        fun audioCodecError(audioCodecError: Exception?) {
            handler?.post { castNonNull(listener).onAudioCodecError(audioCodecError) }
        }
    }
}