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
package com.google.android.exoplayer2.ext.flac

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.extractor.*
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints
import com.google.android.exoplayer2.util.*
import java.io.IOException
import java.nio.ByteBuffer

/** JNI wrapper for the libflac Flac decoder.  */ /* package */
internal class FlacDecoderJni {
    /** Exception to be thrown if [.decodeSample] fails to decode a frame.  */
    class FlacFrameDecodeException(message: String?, val errorCode: Int) : Exception(message)

    private val nativeDecoderContext: Long
    private var byteBufferData: ByteBuffer? = null
    private var extractorInput: ExtractorInput? = null
    private var tempBuffer: ByteArray?
    private var endOfExtractorInput = false

    init {
        if (!FlacLibrary.isAvailable()) {
            throw FlacDecoderException("Failed to load decoder native libraries.")
        }
        nativeDecoderContext = flacInit()
        if (nativeDecoderContext == 0L) {
            throw FlacDecoderException("Failed to initialize decoder")
        }
    }

    /**
     * Sets the data to be parsed.
     *
     * @param byteBufferData Source [ByteBuffer].
     */
    fun setData(byteBufferData: ByteBuffer?) {
        this.byteBufferData = byteBufferData
        extractorInput = null
    }

    /**
     * Sets the data to be parsed.
     *
     * @param extractorInput Source [ExtractorInput].
     */
    fun setData(extractorInput: ExtractorInput?) {
        byteBufferData = null
        this.extractorInput = extractorInput
        endOfExtractorInput = false
        if (tempBuffer == null) {
            tempBuffer = ByteArray(TEMP_BUFFER_SIZE)
        }
    }

    /**
     * Returns whether the end of the data to be parsed has been reached, or true if no data was set.
     */
    val isEndOfData: Boolean
        get() = if (byteBufferData != null) {
            byteBufferData!!.remaining() == 0
        } else if (extractorInput != null) {
            endOfExtractorInput
        } else {
            true
        }

    /** Clears the data to be parsed.  */
    fun clearData() {
        byteBufferData = null
        extractorInput = null
    }

    /**
     * Reads up to `length` bytes from the data source.
     *
     *
     * This method blocks until at least one byte of data can be read, the end of the input is
     * detected or an exception is thrown.
     *
     * @param target A target [ByteBuffer] into which data should be written.
     * @return Returns the number of bytes read, or -1 on failure. If all of the data has already been
     * read from the source, then 0 is returned.
     */
    @Throws(IOException::class)  // Called from native code.
    fun read(target: ByteBuffer): Int {
        var byteCount = target.remaining()
        if (byteBufferData != null) {
            byteCount = Math.min(byteCount, byteBufferData!!.remaining())
            val originalLimit = byteBufferData!!.limit()
            byteBufferData!!.limit(byteBufferData!!.position() + byteCount)
            target.put(byteBufferData)
            byteBufferData!!.limit(originalLimit)
        } else if (extractorInput != null) {
            val extractorInput = extractorInput
            val tempBuffer = Util.castNonNull(tempBuffer)
            byteCount = Math.min(byteCount, TEMP_BUFFER_SIZE)
            var read = readFromExtractorInput(extractorInput, tempBuffer,  /* offset= */0, byteCount)
            if (read < 4) {
                // Reading less than 4 bytes, most of the time, happens because of getting the bytes left in
                // the buffer of the input. Do another read to reduce the number of calls to this method
                // from the native code.
                read += readFromExtractorInput(
                        extractorInput, tempBuffer, read,  /* length= */byteCount - read)
            }
            byteCount = read
            target.put(tempBuffer, 0, byteCount)
        } else {
            return -1
        }
        return byteCount
    }

    /** Decodes and consumes the metadata from the FLAC stream.  */
    @Throws(IOException::class)
    fun decodeStreamMetadata(): FlacStreamMetadata {
        return flacDecodeMetadata(nativeDecoderContext)
                ?: throw ParserException.createForMalformedContainer(
                        "Failed to decode stream metadata",  /* cause= */null)
    }

    /**
     * Decodes and consumes the next frame from the FLAC stream into the given byte buffer. If any IO
     * error occurs, resets the stream and input to the given `retryPosition`.
     *
     * @param output The byte buffer to hold the decoded frame.
     * @param retryPosition If any error happens, the input will be rewound to `retryPosition`.
     */
    @Throws(IOException::class, FlacFrameDecodeException::class)
    fun decodeSampleWithBacktrackPosition(output: ByteBuffer?, retryPosition: Long) {
        try {
            decodeSample(output)
        } catch (e: IOException) {
            if (retryPosition >= 0) {
                reset(retryPosition)
                if (extractorInput != null) {
                    extractorInput!!.setRetryPosition(retryPosition, e)
                }
            }
            throw e
        }
    }

    /** Decodes and consumes the next sample from the FLAC stream into the given byte buffer.  */
    @Throws(IOException::class, FlacFrameDecodeException::class)
    fun decodeSample(output: ByteBuffer?) {
        output!!.clear()
        val frameSize = if (output.isDirect) flacDecodeToBuffer(nativeDecoderContext, output) else flacDecodeToArray(nativeDecoderContext, output.array())
        if (frameSize < 0) {
            if (!isDecoderAtEndOfInput) {
                throw FlacFrameDecodeException("Cannot decode FLAC frame", frameSize)
            }
            // The decoder has read to EOI. Return a 0-size frame to indicate the EOI.
            output.limit(0)
        } else {
            output.limit(frameSize)
        }
    }

    /** Returns the position of the next data to be decoded, or -1 in case of error.  */
    val decodePosition: Long
        get() = flacGetDecodePosition(nativeDecoderContext)

    /** Returns the timestamp for the first sample in the last decoded frame.  */
    val lastFrameTimestamp: Long
        get() = flacGetLastFrameTimestamp(nativeDecoderContext)

    /** Returns the first sample index of the last extracted frame.  */
    val lastFrameFirstSampleIndex: Long
        get() = flacGetLastFrameFirstSampleIndex(nativeDecoderContext)

    /** Returns the first sample index of the frame to be extracted next.  */
    val nextFrameFirstSampleIndex: Long
        get() = flacGetNextFrameFirstSampleIndex(nativeDecoderContext)

    /**
     * Maps a seek position in microseconds to the corresponding [SeekMap.SeekPoints] in the
     * stream.
     *
     * @param timeUs A seek position in microseconds.
     * @return The corresponding [SeekMap.SeekPoints] obtained from the seek table, or `null` if the stream doesn't have a seek table.
     */
    fun getSeekPoints(timeUs: Long): SeekPoints? {
        val seekPoints = LongArray(4)
        if (!flacGetSeekPoints(nativeDecoderContext, timeUs, seekPoints)) {
            return null
        }
        val firstSeekPoint = SeekPoint(seekPoints[0], seekPoints[1])
        val secondSeekPoint = if (seekPoints[2] == seekPoints[0]) firstSeekPoint else SeekPoint(seekPoints[2], seekPoints[3])
        return SeekPoints(firstSeekPoint, secondSeekPoint)
    }

    val stateString: String
        get() = flacGetStateString(nativeDecoderContext)

    /** Returns whether the decoder has read to the end of the input.  */
    val isDecoderAtEndOfInput: Boolean
        get() = flacIsDecoderAtEndOfStream(nativeDecoderContext)

    fun flush() {
        flacFlush(nativeDecoderContext)
    }

    /**
     * Resets internal state of the decoder and sets the stream position.
     *
     * @param newPosition Stream's new position.
     */
    fun reset(newPosition: Long) {
        flacReset(nativeDecoderContext, newPosition)
    }

    fun release() {
        flacRelease(nativeDecoderContext)
    }

    @Throws(IOException::class)
    private fun readFromExtractorInput(
            extractorInput: ExtractorInput?, tempBuffer: ByteArray, offset: Int, length: Int): Int {
        var read = extractorInput!!.read(tempBuffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            endOfExtractorInput = true
            read = 0
        }
        return read
    }

    private external fun flacInit(): Long
    @Throws(IOException::class)
    private external fun flacDecodeMetadata(context: Long): FlacStreamMetadata?
    @Throws(IOException::class)
    private external fun flacDecodeToBuffer(context: Long, outputBuffer: ByteBuffer?): Int
    @Throws(IOException::class)
    private external fun flacDecodeToArray(context: Long, outputArray: ByteArray): Int
    private external fun flacGetDecodePosition(context: Long): Long
    private external fun flacGetLastFrameTimestamp(context: Long): Long
    private external fun flacGetLastFrameFirstSampleIndex(context: Long): Long
    private external fun flacGetNextFrameFirstSampleIndex(context: Long): Long
    private external fun flacGetSeekPoints(context: Long, timeUs: Long, outSeekPoints: LongArray): Boolean
    private external fun flacGetStateString(context: Long): String
    private external fun flacIsDecoderAtEndOfStream(context: Long): Boolean
    private external fun flacFlush(context: Long)
    private external fun flacReset(context: Long, newPosition: Long)
    private external fun flacRelease(context: Long)

    companion object {
        private const val TEMP_BUFFER_SIZE = 8192 // The same buffer size as libflac.
    }
}