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
package com.google.android.exoplayer2.video

import android.os.Handler
import android.os.SystemClock
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util.castNonNull

/**
 * Listener of video [Renderer] events. All methods have no-op default implementations to
 * allow selective overrides.
 */
interface VideoRendererEventListener {
    /**
     * Called when the renderer is enabled.
     *
     * @param counters [DecoderCounters] that will be updated by the renderer for as long as it
     * remains enabled.
     */
    fun onVideoEnabled(counters: DecoderCounters?) {}

    /**
     * Called when a decoder is created.
     *
     * @param decoderName The decoder that was created.
     * @param initializedTimestampMs [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onVideoDecoderInitialized(
        decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long
    ) {
    }

    @Deprecated("Use {@link #onVideoInputFormatChanged(Format, DecoderReuseEvaluation)}.")
    fun onVideoInputFormatChanged(format: Format?) {
    }

    /**
     * Called when the format of the media being consumed by the renderer changes.
     *
     * @param format The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onVideoInputFormatChanged(
        format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
    }

    /**
     * Called to report the number of frames dropped by the renderer. Dropped frames are reported
     * whenever the renderer is stopped having dropped frames, and optionally, whenever the count
     * reaches a specified threshold whilst the renderer is started.
     *
     * @param count The number of dropped frames.
     * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
     * is timed from when the renderer was started or from when dropped frames were last reported
     * (whichever was more recent), and not from when the first of the reported drops occurred.
     */
    fun onDroppedFrames(count: Int, elapsedMs: Long) {}

    /**
     * Called to report the video processing offset of video frames processed by the video renderer.
     *
     *
     * Video processing offset represents how early a video frame is processed compared to the
     * player's current position. For each video frame, the offset is calculated as *P<sub>vf</sub>
     * - P<sub>pl</sub>* where *P<sub>vf</sub>* is the presentation timestamp of the video
     * frame and *P<sub>pl</sub>* is the current position of the player. Positive values
     * indicate the frame was processed early enough whereas negative values indicate that the
     * player's position had progressed beyond the frame's timestamp when the frame was processed (and
     * the frame was probably dropped).
     *
     *
     * The renderer reports the sum of video processing offset samples (one sample per processed
     * video frame: dropped, skipped or rendered) and the total number of samples.
     *
     * @param totalProcessingOffsetUs The sum of all video frame processing offset samples for the
     * video frames processed by the renderer in microseconds.
     * @param frameCount The number of samples included in the `totalProcessingOffsetUs`.
     */
    fun onVideoFrameProcessingOffset(totalProcessingOffsetUs: Long, frameCount: Int) {}

    /**
     * Called before a frame is rendered for the first time since setting the surface, and each time
     * there's a change in the size, rotation or pixel aspect ratio of the video being rendered.
     *
     * @param videoSize The new size of the video.
     */
    fun onVideoSizeChanged(videoSize: VideoSize?) {}

    /**
     * Called when a frame is rendered for the first time since setting the output, or since the
     * renderer was reset, or since the stream being rendered was changed.
     *
     * @param output The output of the video renderer. Normally a [Surface], however some video
     * renderers may have other output types (e.g., a [VideoDecoderOutputBufferRenderer]).
     * @param renderTimeMs The [SystemClock.elapsedRealtime] when the frame was rendered.
     */
    fun onRenderedFirstFrame(output: Any?, renderTimeMs: Long) {}

    /**
     * Called when a decoder is released.
     *
     * @param decoderName The decoder that was released.
     */
    fun onVideoDecoderReleased(decoderName: String?) {}

    /**
     * Called when the renderer is disabled.
     *
     * @param counters [DecoderCounters] that were updated by the renderer.
     */
    fun onVideoDisabled(counters: DecoderCounters?) {}

    /**
     * Called when a video decoder encounters an error.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param videoCodecError The error. Typically a [CodecException] if the renderer uses
     * [MediaCodec], or a [DecoderException] if the renderer uses a software decoder.
     */
    fun onVideoCodecError(videoCodecError: Exception?) {}

    /** Dispatches events to a [VideoRendererEventListener].  */
    class EventDispatcher(
        handler: Handler?, listener: VideoRendererEventListener?
    ) {
        private val handler: Handler?
        private val listener: VideoRendererEventListener?

        /**
         * @param handler A handler for dispatching events, or null if events should not be dispatched.
         * @param listener The listener to which events should be dispatched, or null if events should
         * not be dispatched.
         */
        init {
            this.handler = if (listener != null) checkNotNull(handler) else null
            this.listener = listener
        }

        /** Invokes [VideoRendererEventListener.onVideoEnabled].  */
        fun enabled(decoderCounters: DecoderCounters?) {
            handler?.post { castNonNull(listener).onVideoEnabled(decoderCounters) }
        }

        /** Invokes [VideoRendererEventListener.onVideoDecoderInitialized].  */
        fun decoderInitialized(
            decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long
        ) {
            handler?.post {
                castNonNull(listener)
                    .onVideoDecoderInitialized(
                        decoderName, initializedTimestampMs, initializationDurationMs
                    )
            }
        }

        /**
         * Invokes [VideoRendererEventListener.onVideoInputFormatChanged].
         */
        // Calling deprecated listener method.
        fun inputFormatChanged(
            format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            handler?.post {
                castNonNull(listener).onVideoInputFormatChanged(format)
                castNonNull(listener).onVideoInputFormatChanged(format, decoderReuseEvaluation)
            }
        }

        /** Invokes [VideoRendererEventListener.onDroppedFrames].  */
        fun droppedFrames(droppedFrameCount: Int, elapsedMs: Long) {
            handler?.post { castNonNull(listener).onDroppedFrames(droppedFrameCount, elapsedMs) }
        }

        /** Invokes [VideoRendererEventListener.onVideoFrameProcessingOffset].  */
        fun reportVideoFrameProcessingOffset(totalProcessingOffsetUs: Long, frameCount: Int) {
            handler?.post {
                castNonNull(listener)
                    .onVideoFrameProcessingOffset(totalProcessingOffsetUs, frameCount)
            }
        }

        /** Invokes [VideoRendererEventListener.onVideoSizeChanged].  */
        fun videoSizeChanged(videoSize: VideoSize?) {
            handler?.post { castNonNull(listener).onVideoSizeChanged(videoSize) }
        }

        /** Invokes [VideoRendererEventListener.onRenderedFirstFrame].  */
        fun renderedFirstFrame(output: Any?) {
            if (handler != null) {
                // TODO: Replace this timestamp with the actual frame release time.
                val renderTimeMs = SystemClock.elapsedRealtime()
                handler.post(Runnable {
                    castNonNull(listener).onRenderedFirstFrame(
                        output,
                        renderTimeMs
                    )
                })
            }
        }

        /** Invokes [VideoRendererEventListener.onVideoDecoderReleased].  */
        fun decoderReleased(decoderName: String?) {
            handler?.post { castNonNull(listener).onVideoDecoderReleased(decoderName) }
        }

        /** Invokes [VideoRendererEventListener.onVideoDisabled].  */
        fun disabled(counters: DecoderCounters) {
            counters.ensureUpdated()
            handler?.post {
                counters.ensureUpdated()
                castNonNull(listener).onVideoDisabled(counters)
            }
        }

        /** Invokes [VideoRendererEventListener.onVideoCodecError].  */
        fun videoCodecError(videoCodecError: Exception?) {
            handler?.post { castNonNull(listener).onVideoCodecError(videoCodecError) }
        }
    }
}