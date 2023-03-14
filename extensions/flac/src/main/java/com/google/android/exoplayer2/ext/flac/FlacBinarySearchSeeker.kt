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
package com.google.android.exoplayer2.ext.flac

import com.google.android.exoplayer2.ext.flac.FlacDecoderJni.FlacFrameDecodeException
import com.google.android.exoplayer2.extractor.BinarySearchSeeker
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.SeekTimestampConverter
import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.extractor.FlacStreamMetadata
import com.google.android.exoplayer2.util.Assertions
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A [SeekMap] implementation for FLAC stream using binary search.
 *
 *
 * This seeker performs seeking by using binary search within the stream, until it finds the
 * frame that contains the target sample.
 */
/* package */
internal class FlacBinarySearchSeeker(
        streamMetadata: FlacStreamMetadata?,
        firstFramePosition: Long,
        inputLength: Long,
        decoderJni: FlacDecoderJni?,
        outputFrameHolder: OutputFrameHolder) : BinarySearchSeeker(SeekTimestampConverter { timeUs: Long -> streamMetadata!!.getSampleNumber(timeUs) },
        FlacTimestampSeeker(decoderJni, outputFrameHolder),
        streamMetadata!!.durationUs,  /* floorTimePosition= */
        0,  /* ceilingTimePosition= */
        streamMetadata.totalSamples,  /* floorBytePosition= */
        firstFramePosition,  /* ceilingBytePosition= */
        inputLength,  /* approxBytesPerFrame= */
        streamMetadata.approxBytesPerFrame,  /* minimumSearchRange= */
        Math.max(MIN_FRAME_HEADER_SIZE, streamMetadata.minFrameSize)) {
    /**
     * Holds a frame extracted from a stream, together with the time stamp of the frame in
     * microseconds.
     */
    class OutputFrameHolder
    /** Constructs an instance, wrapping the given byte buffer.  */(val byteBuffer: ByteBuffer) {
        var timeUs: Long = 0
    }

    private val decoderJni: FlacDecoderJni

    /**
     * Creates a [FlacBinarySearchSeeker].
     *
     * @param streamMetadata The stream metadata.
     * @param firstFramePosition The byte offset of the first frame in the stream.
     * @param inputLength The length of the stream in bytes.
     * @param decoderJni The FLAC JNI decoder.
     * @param outputFrameHolder A holder used to retrieve the frame found by a seeking operation.
     */
    init {
        this.decoderJni = Assertions.checkNotNull(decoderJni)
    }

    override fun onSeekOperationFinished(foundTargetFrame: Boolean, resultPosition: Long) {
        if (!foundTargetFrame) {
            // If we can't find the target frame (sample), we need to reset the decoder jni so that
            // it can continue from the result position.
            decoderJni.reset(resultPosition)
        }
    }

    private class FlacTimestampSeeker(private val decoderJni: FlacDecoderJni?, private val outputFrameHolder: OutputFrameHolder) : TimestampSeeker {
        @Throws(IOException::class)
        override fun searchForTimestamp(input: ExtractorInput, targetSampleIndex: Long): TimestampSearchResult {
            val outputBuffer = outputFrameHolder.byteBuffer
            val searchPosition = input.position
            decoderJni!!.reset(searchPosition)
            try {
                decoderJni.decodeSampleWithBacktrackPosition(
                        outputBuffer,  /* retryPosition= */searchPosition)
            } catch (e: FlacFrameDecodeException) {
                // For some reasons, the extractor can't find a frame mid-stream.
                // Stop the seeking and let it re-try playing at the last search position.
                return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT
            }
            if (outputBuffer.limit() == 0) {
                return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT
            }
            val lastFrameSampleIndex = decoderJni.lastFrameFirstSampleIndex
            val nextFrameSampleIndex = decoderJni.nextFrameFirstSampleIndex
            val nextFrameSamplePosition = decoderJni.decodePosition
            val targetSampleInLastFrame = lastFrameSampleIndex <= targetSampleIndex && nextFrameSampleIndex > targetSampleIndex
            return if (targetSampleInLastFrame) {
                // We are holding the target frame in outputFrameHolder. Set its presentation time now.
                outputFrameHolder.timeUs = decoderJni.lastFrameTimestamp
                // The input position is passed even though it does not indicate the frame containing the
                // target sample because the extractor must continue to read from this position.
                TimestampSearchResult.targetFoundResult(input.position)
            } else if (nextFrameSampleIndex <= targetSampleIndex) {
                TimestampSearchResult.underestimatedResult(
                        nextFrameSampleIndex, nextFrameSamplePosition)
            } else {
                TimestampSearchResult.overestimatedResult(lastFrameSampleIndex, searchPosition)
            }
        }
    }

    companion object {
        private const val MIN_FRAME_HEADER_SIZE = 6
    }
}