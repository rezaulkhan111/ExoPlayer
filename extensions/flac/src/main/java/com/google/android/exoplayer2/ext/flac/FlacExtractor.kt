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

import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.ext.flac.FlacBinarySearchSeeker.OutputFrameHolder
import com.google.android.exoplayer2.ext.flac.FlacDecoderJni.FlacFrameDecodeException
import com.google.android.exoplayer2.extractor.*
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints
import com.google.android.exoplayer2.extractor.SeekMap.Unseekable
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.util.*
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.nio.ByteBuffer

/** Facilitates the extraction of data from the FLAC container format.  */
class FlacExtractor @JvmOverloads constructor(flags: Int =  /* flags= */0) : Extractor {
    /*
   * Flags in the two FLAC extractors should be kept in sync. If we ever change this then
   * DefaultExtractorsFactory will need modifying, because it currently assumes this is the case.
   */
    /**
     * Flags controlling the behavior of the extractor. Possible flag value is [ ][.FLAG_DISABLE_ID3_METADATA].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(flag = true, value = [FLAG_DISABLE_ID3_METADATA])
    annotation class Flags

    private val outputBuffer: ParsableByteArray
    private val id3MetadataDisabled: Boolean
    private var decoderJni: FlacDecoderJni? = null
    private var extractorOutput: @MonotonicNonNull ExtractorOutput? = null
    private var trackOutput: @MonotonicNonNull TrackOutput? = null
    private var streamMetadataDecoded = false
    private var streamMetadata: @MonotonicNonNull FlacStreamMetadata? = null
    private var outputFrameHolder: @MonotonicNonNull OutputFrameHolder? = null
    private var id3Metadata: Metadata? = null
    private var binarySearchSeeker: FlacBinarySearchSeeker? = null
    /**
     * Constructs an instance.
     *
     * @param flags Flags that control the extractor's behavior. Possible flags are described by
     * [Flags].
     */
    /** Constructs an instance with `flags = 0`.  */
    init {
        outputBuffer = ParsableByteArray()
        id3MetadataDisabled = flags and FLAG_DISABLE_ID3_METADATA != 0
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
        trackOutput = extractorOutput!!.track(0, C.TRACK_TYPE_AUDIO)
        extractorOutput!!.endTracks()
        decoderJni = try {
            FlacDecoderJni()
        } catch (e: FlacDecoderException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    override fun sniff(input: ExtractorInput): Boolean {
        id3Metadata = FlacMetadataReader.peekId3Metadata(input,  /* parseData= */!id3MetadataDisabled)
        return FlacMetadataReader.checkAndPeekStreamMarker(input)
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (input.position == 0L && !id3MetadataDisabled && id3Metadata == null) {
            id3Metadata = FlacMetadataReader.peekId3Metadata(input,  /* parseData= */true)
        }
        val decoderJni = initDecoderJni(input)
        return try {
            decodeStreamMetadata(input)
            if (binarySearchSeeker != null && binarySearchSeeker!!.isSeeking) {
                return handlePendingSeek(input, seekPosition, outputBuffer, outputFrameHolder, trackOutput)
            }
            val outputByteBuffer = outputFrameHolder!!.byteBuffer
            val lastDecodePosition = decoderJni.decodePosition
            try {
                decoderJni.decodeSampleWithBacktrackPosition(outputByteBuffer, lastDecodePosition)
            } catch (e: FlacFrameDecodeException) {
                throw IOException("Cannot read frame at position $lastDecodePosition", e)
            }
            val outputSize = outputByteBuffer.limit()
            if (outputSize == 0) {
                return Extractor.RESULT_END_OF_INPUT
            }
            outputSample(outputBuffer, outputSize, decoderJni.lastFrameTimestamp, trackOutput)
            if (decoderJni.isEndOfData) Extractor.RESULT_END_OF_INPUT else Extractor.RESULT_CONTINUE
        } finally {
            decoderJni.clearData()
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        if (position == 0L) {
            streamMetadataDecoded = false
        }
        if (decoderJni != null) {
            decoderJni!!.reset(position)
        }
        if (binarySearchSeeker != null) {
            binarySearchSeeker!!.setSeekTargetUs(timeUs)
        }
    }

    override fun release() {
        binarySearchSeeker = null
        if (decoderJni != null) {
            decoderJni!!.release()
            decoderJni = null
        }
    }

    @EnsuresNonNull("decoderJni", "extractorOutput", "trackOutput") // Ensures initialized.
    private fun initDecoderJni(input: ExtractorInput): FlacDecoderJni {
        val decoderJni = Assertions.checkNotNull(decoderJni)
        decoderJni.setData(input)
        return decoderJni
    }

    @RequiresNonNull("decoderJni", "extractorOutput", "trackOutput") // Requires initialized.
    @EnsuresNonNull("streamMetadata", "outputFrameHolder") // Ensures stream metadata decoded.
    @Throws(IOException::class)
    private fun decodeStreamMetadata(input: ExtractorInput) {
        if (streamMetadataDecoded) {
            return
        }
        val flacDecoderJni = decoderJni
        val streamMetadata: FlacStreamMetadata
        streamMetadata = try {
            flacDecoderJni!!.decodeStreamMetadata()
        } catch (e: IOException) {
            flacDecoderJni!!.reset( /* newPosition= */0)
            input.setRetryPosition( /* position= */0, e)
            throw e
        }
        streamMetadataDecoded = true
        if (this.streamMetadata == null) {
            this.streamMetadata = streamMetadata
            outputBuffer.reset(streamMetadata.maxDecodedFrameSize)
            outputFrameHolder = OutputFrameHolder(ByteBuffer.wrap(outputBuffer.data))
            binarySearchSeeker = outputSeekMap(
                    flacDecoderJni,
                    streamMetadata,
                    input.length,
                    extractorOutput,
                    outputFrameHolder!!)
            val metadata = streamMetadata.getMetadataCopyWithAppendedEntriesFrom(id3Metadata)
            outputFormat(streamMetadata, metadata, trackOutput)
        }
    }

    @RequiresNonNull("binarySearchSeeker")
    @Throws(IOException::class)
    private fun handlePendingSeek(
            input: ExtractorInput,
            seekPosition: PositionHolder,
            outputBuffer: ParsableByteArray,
            outputFrameHolder: OutputFrameHolder?,
            trackOutput: TrackOutput?): Int {
        val seekResult = binarySearchSeeker!!.handlePendingSeek(input, seekPosition)
        val outputByteBuffer = outputFrameHolder!!.byteBuffer
        if (seekResult == Extractor.RESULT_CONTINUE && outputByteBuffer.limit() > 0) {
            outputSample(outputBuffer, outputByteBuffer.limit(), outputFrameHolder.timeUs, trackOutput)
        }
        return seekResult
    }

    /** A [SeekMap] implementation using a SeekTable within the Flac stream.  */
    private class FlacSeekMap(private val durationUs: Long, private val decoderJni: FlacDecoderJni?) : SeekMap {
        override fun isSeekable(): Boolean {
            return true
        }

        override fun getSeekPoints(timeUs: Long): SeekPoints {
            val seekPoints = decoderJni!!.getSeekPoints(timeUs)
            return seekPoints ?: SeekPoints(SeekPoint.START)
        }

        override fun getDurationUs(): Long {
            return durationUs
        }
    }

    companion object {
        /** Factory that returns one extractor which is a [FlacExtractor].  */
        val FACTORY = ExtractorsFactory { arrayOf<Extractor>(FlacExtractor()) }

        /**
         * Flag to disable parsing of ID3 metadata. Can be set to save memory if ID3 metadata is not
         * required.
         */
        const val FLAG_DISABLE_ID3_METADATA = com.google.android.exoplayer2.extractor.flac.FlacExtractor.FLAG_DISABLE_ID3_METADATA

        /**
         * Outputs a [SeekMap] and returns a [FlacBinarySearchSeeker] if one is required to
         * handle seeks.
         */
        private fun outputSeekMap(
                decoderJni: FlacDecoderJni?,
                streamMetadata: FlacStreamMetadata?,
                streamLength: Long,
                output: ExtractorOutput?,
                outputFrameHolder: OutputFrameHolder): FlacBinarySearchSeeker? {
            val haveSeekTable = decoderJni!!.getSeekPoints( /* timeUs= */0) != null
            var binarySearchSeeker: FlacBinarySearchSeeker? = null
            val seekMap: SeekMap
            if (haveSeekTable) {
                seekMap = FlacSeekMap(streamMetadata!!.durationUs, decoderJni)
            } else if (streamLength != C.LENGTH_UNSET.toLong() && streamMetadata!!.totalSamples > 0) {
                val firstFramePosition = decoderJni.decodePosition
                binarySearchSeeker = FlacBinarySearchSeeker(
                        streamMetadata, firstFramePosition, streamLength, decoderJni, outputFrameHolder)
                seekMap = binarySearchSeeker.seekMap
            } else {
                seekMap = Unseekable(streamMetadata!!.durationUs)
            }
            output!!.seekMap(seekMap)
            return binarySearchSeeker
        }

        private fun outputFormat(
                streamMetadata: FlacStreamMetadata?, metadata: Metadata?, output: TrackOutput?) {
            val mediaFormat = Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setAverageBitrate(streamMetadata!!.decodedBitrate)
                    .setPeakBitrate(streamMetadata.decodedBitrate)
                    .setMaxInputSize(streamMetadata.maxDecodedFrameSize)
                    .setChannelCount(streamMetadata.channels)
                    .setSampleRate(streamMetadata.sampleRate)
                    .setPcmEncoding(Util.getPcmEncoding(streamMetadata.bitsPerSample))
                    .setMetadata(metadata)
                    .build()
            output!!.format(mediaFormat)
        }

        private fun outputSample(
                sampleData: ParsableByteArray, size: Int, timeUs: Long, output: TrackOutput?) {
            sampleData.position = 0
            output!!.sampleData(sampleData, size)
            output.sampleMetadata(
                    timeUs, C.BUFFER_FLAG_KEY_FRAME, size,  /* offset= */0,  /* cryptoData= */null)
        }
    }
}