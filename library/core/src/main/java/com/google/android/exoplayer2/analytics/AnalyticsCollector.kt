/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics

import android.os.Looper
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.drm.DrmSessionEventListener
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.upstream.BandwidthMeter

/**
 * Interface for data collectors that forward analytics events to [ AnalyticsListeners][AnalyticsListener].
 */
interface AnalyticsCollector :
        Player.Listener,
        MediaSourceEventListener,
        BandwidthMeter.EventListener,
        DrmSessionEventListener {
    /**
     * Adds a listener for analytics events.
     *
     * @param listener The listener to add.
     */
    fun addListener(listener: AnalyticsListener?)

    /**
     * Removes a previously added analytics event listener.
     *
     * @param listener The listener to remove.
     */
    fun removeListener(listener: AnalyticsListener?)

    /**
     * Sets the player for which data will be collected. Must only be called if no player has been set
     * yet or the current player is idle.
     *
     * @param player The [Player] for which data will be collected.
     * @param looper The [Looper] used for listener callbacks.
     */
    fun setPlayer(player: Player?, looper: Looper?)

    /**
     * Releases the collector. Must be called after the player for which data is collected has been
     * released.
     */
    fun release()

    /**
     * Updates the playback queue information used for event association.
     *
     *
     * Should only be called by the player controlling the queue and not from app code.
     *
     * @param queue         The playback queue of media periods identified by their [MediaPeriodId].
     * @param readingPeriod The media period in the queue that is currently being read by renderers,
     * or null if the queue is empty.
     */
    fun updateMediaPeriodQueueInfo(queue: List<MediaSource.MediaPeriodId?>?, readingPeriod: MediaSource.MediaPeriodId?)

    /**
     * Notify analytics collector that a seek operation will start. Should be called before the player
     * adjusts its state and position to the seek.
     */
    fun notifySeekStarted()
    // Audio events.
    /**
     * Called when the audio renderer is enabled.
     *
     * @param counters [DecoderCounters] that will be updated by the audio renderer for as long
     * as it remains enabled.
     */
    fun onAudioEnabled(counters: DecoderCounters?)

    /**
     * Called when a audio decoder is created.
     *
     * @param decoderName              The audio decoder that was created.
     * @param initializedTimestampMs   [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onAudioDecoderInitialized(
            decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long)

    /**
     * Called when the format of the media being consumed by the audio renderer changes.
     *
     * @param format                 The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onAudioInputFormatChanged(
            format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?)

    /**
     * Called when the audio position has increased for the first time since the last pause or
     * position reset.
     *
     * @param playoutStartSystemTimeMs The approximate derived [System.currentTimeMillis] at
     * which playout started.
     */
    fun onAudioPositionAdvancing(playoutStartSystemTimeMs: Long)

    /**
     * Called when an audio underrun occurs.
     *
     * @param bufferSize             The size of the audio output buffer, in bytes.
     * @param bufferSizeMs           The size of the audio output buffer, in milliseconds, if it contains PCM
     * encoded audio. [C.TIME_UNSET] if the output buffer contains non-PCM encoded audio.
     * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
     */
    fun onAudioUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long)

    /**
     * Called when a audio decoder is released.
     *
     * @param decoderName The audio decoder that was released.
     */
    fun onAudioDecoderReleased(decoderName: String?)

    /**
     * Called when the audio renderer is disabled.
     *
     * @param counters [DecoderCounters] that were updated by the audio renderer.
     */
    fun onAudioDisabled(counters: DecoderCounters?)

    /**
     * Called when [AudioSink] has encountered an error.
     *
     *
     * If the sink writes to a platform [AudioTrack], this will be called for all [ ] errors.
     *
     * @param audioSinkError The error that occurred. Typically an [                       ], a [AudioSink.WriteException], or an [                       ].
     */
    fun onAudioSinkError(audioSinkError: Exception?)

    /**
     * Called when an audio decoder encounters an error.
     *
     * @param audioCodecError The error. Typically a [CodecException] if the renderer uses
     * [MediaCodec], or a [DecoderException] if the renderer uses a software decoder.
     */
    fun onAudioCodecError(audioCodecError: Exception?)
    // Video events.
    /**
     * Called when the video renderer is enabled.
     *
     * @param counters [DecoderCounters] that will be updated by the video renderer for as long
     * as it remains enabled.
     */
    fun onVideoEnabled(counters: DecoderCounters?)

    /**
     * Called when a video decoder is created.
     *
     * @param decoderName              The decoder that was created.
     * @param initializedTimestampMs   [SystemClock.elapsedRealtime] when initialization
     * finished.
     * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
     */
    fun onVideoDecoderInitialized(
            decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long)

    /**
     * Called when the format of the media being consumed by the video renderer changes.
     *
     * @param format                 The new format.
     * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
     * decoder instance can be reused for the new format, or `null` if the renderer did not
     * have a decoder.
     */
    fun onVideoInputFormatChanged(
            format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?)

    /**
     * Called to report the number of frames dropped by the video renderer. Dropped frames are
     * reported whenever the renderer is stopped having dropped frames, and optionally, whenever the
     * count reaches a specified threshold whilst the renderer is started.
     *
     * @param count     The number of dropped frames.
     * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
     * is timed from when the renderer was started or from when dropped frames were last reported
     * (whichever was more recent), and not from when the first of the reported drops occurred.
     */
    fun onDroppedFrames(count: Int, elapsedMs: Long)

    /**
     * Called when a video decoder is released.
     *
     * @param decoderName The video decoder that was released.
     */
    fun onVideoDecoderReleased(decoderName: String?)

    /**
     * Called when the video renderer is disabled.
     *
     * @param counters [DecoderCounters] that were updated by the video renderer.
     */
    fun onVideoDisabled(counters: DecoderCounters?)

    /**
     * Called when a frame is rendered for the first time since setting the output, or since the
     * renderer was reset, or since the stream being rendered was changed.
     *
     * @param output       The output of the video renderer. Normally a [Surface], however some video
     * renderers may have other output types (e.g., a [VideoDecoderOutputBufferRenderer]).
     * @param renderTimeMs The [SystemClock.elapsedRealtime] when the frame was rendered.
     */
    fun onRenderedFirstFrame(output: Any?, renderTimeMs: Long)

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
     * @param frameCount              The number of samples included in the `totalProcessingOffsetUs`.
     */
    fun onVideoFrameProcessingOffset(totalProcessingOffsetUs: Long, frameCount: Int)

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
    fun onVideoCodecError(videoCodecError: Exception?)
}