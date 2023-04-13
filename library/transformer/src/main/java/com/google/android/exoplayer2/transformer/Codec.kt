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
package com.google.android.exoplayer2.transformer

import android.media.MediaCodec.BufferInfo
import android.view.Surface
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import java.nio.ByteBuffer

/**
 * Provides a layer of abstraction for interacting with decoders and encoders.
 *
 *
 * [DecoderInputBuffers][DecoderInputBuffer] are used as both decoders' and encoders' input
 * buffers.
 */
interface Codec {
    companion object {
        /**
         * Returns the maximum number of frames that may be pending in the output `Codec` at a time,
         * or [.UNLIMITED_PENDING_FRAME_COUNT] if it's not necessary to enforce a limit.
         */
        /** Default value for the pending frame count, which represents applying no limit.  */
        const val UNLIMITED_PENDING_FRAME_COUNT: Int = Int.MAX_VALUE
    }

    /** A factory for [decoder][Codec] instances.  */
    interface DecoderFactory {
        /**
         * Returns a [Codec] for audio decoding.
         *
         * @param format The [Format] (of the input data) used to determine the underlying decoder
         * and its configuration values.
         * @return A [Codec] for audio decoding.
         * @throws TransformationException If no suitable [Codec] can be created.
         */
        @Throws(TransformationException::class)
        fun createForAudioDecoding(format: Format?): Codec?

        /**
         * Returns a [Codec] for video decoding.
         *
         * @param format The [Format] (of the input data) used to determine the underlying decoder
         * and its configuration values.
         * @param outputSurface The [Surface] to which the decoder output is rendered.
         * @param enableRequestSdrToneMapping Whether to request tone-mapping to SDR.
         * @return A [Codec] for video decoding.
         * @throws TransformationException If no suitable [Codec] can be created.
         */
        @Throws(TransformationException::class)
        fun createForVideoDecoding(
            format: Format?, outputSurface: Surface?, enableRequestSdrToneMapping: Boolean
        ): Codec?
    }

    /** A factory for [encoder][Codec] instances.  */
    interface EncoderFactory {
        /**
         * Returns a [Codec] for audio encoding.
         *
         *
         * This method must validate that the [Codec] is configured to produce one of the
         * `allowedMimeTypes`. The [sample MIME type][Format.sampleMimeType] given in
         * `format` is not necessarily allowed.
         *
         * @param format The [Format] (of the output data) used to determine the underlying
         * encoder and its configuration values.
         * @param allowedMimeTypes The non-empty list of allowed output sample [     MIME types][MimeTypes].
         * @return A [Codec] for audio encoding.
         * @throws TransformationException If no suitable [Codec] can be created.
         */
        @Throws(TransformationException::class)
        fun createForAudioEncoding(format: Format?, allowedMimeTypes: List<String?>?): Codec?

        /**
         * Returns a [Codec] for video encoding.
         *
         *
         * This method must validate that the [Codec] is configured to produce one of the
         * `allowedMimeTypes`. The [sample MIME type][Format.sampleMimeType] given in
         * `format` is not necessarily allowed.
         *
         * @param format The [Format] (of the output data) used to determine the underlying
         * encoder and its configuration values. [Format.sampleMimeType], [Format.width]
         * and [Format.height] are set to those of the desired output video format. [     ][Format.rotationDegrees] is 0 and [Format.width] `>=` [Format.height],
         * therefore the video is always in landscape orientation. [Format.frameRate] is set
         * to the output video's frame rate, if available.
         * @param allowedMimeTypes The non-empty list of allowed output sample [     MIME types][MimeTypes].
         * @return A [Codec] for video encoding.
         * @throws TransformationException If no suitable [Codec] can be created.
         */
        @Throws(TransformationException::class)
        fun createForVideoEncoding(format: Format?, allowedMimeTypes: List<String?>?): Codec?

        /** Returns whether the audio needs to be encoded because of encoder specific configuration.  */
        fun audioNeedsEncoding(): Boolean {
            return false
        }

        /** Returns whether the video needs to be encoded because of encoder specific configuration.  */
        fun videoNeedsEncoding(): Boolean {
            return false
        }
    }

    /**
     * Returns the [Format] used for configuring the `Codec`.
     *
     *
     * The configuration [Format] is the input [Format] used by the [ ] or output [Format] used by the [EncoderFactory] for selecting and
     * configuring the underlying decoder or encoder.
     */
    fun getConfigurationFormat(): Format?

    /** Returns the name of the underlying codec.  */
    fun getName(): String?

    /**
     * Returns the input [Surface] of an underlying video encoder.
     *
     *
     * This method must only be called on video encoders because audio/video decoders and audio
     * encoders don't use a [Surface] as input.
     */
    fun getInputSurface(): Surface?

    /**
     * Dequeues a writable input buffer, if available.
     *
     *
     * This method must not be called from video encoders because they must use a [Surface]
     * to receive input.
     *
     * @param inputBuffer The buffer where the dequeued buffer data is stored, at [     ][DecoderInputBuffer.data].
     * @return Whether an input buffer is ready to be used.
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun maybeDequeueInputBuffer(inputBuffer: DecoderInputBuffer?): Boolean

    /**
     * Queues an input buffer to the `Codec`. No buffers may be queued after [ ][DecoderInputBuffer.isEndOfStream] buffer has been queued.
     *
     *
     * This method must not be called from video encoders because they must use a [Surface]
     * to receive input.
     *
     * @param inputBuffer The [input buffer][DecoderInputBuffer].
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun queueInputBuffer(inputBuffer: DecoderInputBuffer?)

    /**
     * Signals end-of-stream on input to a video encoder.
     *
     *
     * This method must only be called on video encoders because they must use a [Surface] as
     * input. For audio/video decoders or audio encoders, the [C.BUFFER_FLAG_END_OF_STREAM] flag
     * should be set on the last input buffer [ queued][.queueInputBuffer].
     *
     * @throws TransformationException If the underlying video encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun signalEndOfInputStream()

    /**
     * Returns the current output format, or `null` if unavailable.
     *
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun getOutputFormat(): Format?

    /**
     * Returns the current output [ByteBuffer], or `null` if unavailable.
     *
     *
     * This method must not be called on video decoders because they must output to a [ ].
     *
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun getOutputBuffer(): ByteBuffer?

    /**
     * Returns the [BufferInfo] associated with the current output buffer, or `null` if
     * there is no output buffer available.
     *
     *
     * This method returns `null` if and only if [.getOutputBuffer] returns null.
     *
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun getOutputBufferInfo(): BufferInfo?

    /**
     * Releases the current output buffer.
     *
     *
     * Only set `render` to `true` when the `Codec` is a video decoder. Setting
     * `render` to `true` will first render the buffer to the output surface. In this
     * case, the surface will release the buffer back to the `Codec` once it is no longer
     * used/displayed.
     *
     *
     * This should be called after the buffer has been processed. The next output buffer will not
     * be available until the current output buffer has been released.
     *
     * @param render Whether the buffer needs to be rendered to the output [Surface].
     * @throws TransformationException If the underlying decoder or encoder encounters a problem.
     */
    @Throws(TransformationException::class)
    fun releaseOutputBuffer(render: Boolean)

    /**
     * Returns whether the `Codec`'s output stream has ended, and no more data can be dequeued.
     */
    fun isEnded(): Boolean

    /** Releases the `Codec`.  */
    fun release()
}