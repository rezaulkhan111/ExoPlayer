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

import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.*
import com.google.common.base.Objects
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interface for audio processors, which take audio data as input and transform it, potentially
 * modifying its channel count, encoding and/or sample rate.
 *
 *
 * In addition to being able to modify the format of audio, implementations may allow parameters
 * to be set that affect the output audio and whether the processor is active/inactive.
 */
interface AudioProcessor {

    /** PCM audio format that may be handled by an audio processor.  */
    class AudioFormat {
        companion object {
            val NOT_SET: AudioFormat = AudioFormat( /* sampleRate= */
                    Format.NO_VALUE,  /* channelCount= */
                    Format.NO_VALUE,  /* encoding= */
                    Format.NO_VALUE)
        }

        /** The sample rate in Hertz.  */
        var sampleRate = 0

        /** The number of interleaved channels.  */
        var channelCount = 0

        /** The type of linear PCM encoding.  */
        @C.PcmEncoding
        var encoding = 0

        /** The number of bytes used to represent one audio frame.  */
        var bytesPerFrame = 0

        constructor(
                sampleRate: Int,
                channelCount: Int,
                encoding: Int) {
            this.sampleRate = sampleRate
            this.channelCount = channelCount
            this.encoding = encoding
            bytesPerFrame = if (Util.isEncodingLinearPcm(encoding)) Util.getPcmFrameSize(encoding, channelCount) else Format.Companion.NO_VALUE
        }

        public override fun toString(): String {
            return ("AudioFormat["
                    + "sampleRate="
                    + sampleRate
                    + ", channelCount="
                    + channelCount
                    + ", encoding="
                    + encoding
                    + ']')
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (!(o is AudioFormat)) {
                return false
            }
            val that: AudioFormat = o
            return (sampleRate == that.sampleRate
                    ) && (channelCount == that.channelCount
                    ) && (encoding == that.encoding)
        }

        public override fun hashCode(): Int {
            return Objects.hashCode(sampleRate, channelCount, encoding)
        }
    }

    /** Exception thrown when a processor can't be configured for a given input audio format.  */
    class UnhandledAudioFormatException constructor(inputAudioFormat: AudioFormat) : Exception("Unhandled format: " + inputAudioFormat)

    /**
     * Configures the processor to process input audio with the specified format. After calling this
     * method, call [.isActive] to determine whether the audio processor is active. Returns
     * the configured output audio format if this instance is active.
     *
     *
     * After calling this method, it is necessary to [.flush] the processor to apply the
     * new configuration. Before applying the new configuration, it is safe to queue input and get
     * output in the old input/output formats. Call [.queueEndOfStream] when no more input
     * will be supplied in the old input format.
     *
     * @param inputAudioFormat The format of audio that will be queued after the next call to [     ][.flush].
     * @return The configured output audio format if this instance is [active][.isActive].
     * @throws UnhandledAudioFormatException Thrown if the specified format can't be handled as input.
     */
    @CanIgnoreReturnValue
    @Throws(UnhandledAudioFormatException::class)
    fun configure(inputAudioFormat: AudioFormat?): AudioFormat?

    /** Returns whether the processor is configured and will process input buffers.  */
    fun isActive(): Boolean

    /**
     * Queues audio data between the position and limit of the `inputBuffer` for processing.
     * After calling this method, processed output may be available via [.getOutput]. Calling
     * `queueInput(ByteBuffer)` again invalidates any pending output.
     *
     * @param inputBuffer The input buffer to process. It must be a direct byte buffer with native
     * byte order. Its contents are treated as read-only. Its position will be advanced by the
     * number of bytes consumed (which may be zero). The caller retains ownership of the provided
     * buffer.
     */
    fun queueInput(inputBuffer: ByteBuffer?)

    /**
     * Queues an end of stream signal. After this method has been called, [ ][.queueInput] may not be called until after the next call to [.flush].
     * Calling [.getOutput] will return any remaining output data. Multiple calls may be
     * required to read all of the remaining output data. [.isEnded] will return `true`
     * once all remaining output data has been read.
     */
    fun queueEndOfStream()

    /**
     * Returns a buffer containing processed output data between its position and limit. The buffer
     * will always be a direct byte buffer with native byte order. Calling this method invalidates any
     * previously returned buffer. The buffer will be empty if no output is available.
     *
     * @return A buffer containing processed output data between its position and limit.
     */
    fun getOutput(): ByteBuffer?

    /**
     * Returns whether this processor will return no more output from [.getOutput] until
     * [.flush] has been called and more input has been queued.
     */
    fun isEnded(): Boolean

    /**
     * Clears any buffered data and pending output. If the audio processor is active, also prepares
     * the audio processor to receive a new stream of input in the last configured (pending) format.
     */
    fun flush()

    /** Resets the processor to its unconfigured state, releasing any resources.  */
    fun reset()

    companion object {
        /** An empty, direct [ByteBuffer].  */
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}