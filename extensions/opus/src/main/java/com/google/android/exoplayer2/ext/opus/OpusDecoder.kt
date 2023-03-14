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
package com.google.android.exoplayer2.ext.opus

import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.*
import com.google.android.exoplayer2.ext.opus.OpusDecoderException
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opus decoder.  */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
class OpusDecoder(
        numInputBuffers: Int,
        numOutputBuffers: Int,
        initialInputBufferSize: Int,
        initializationData: List<ByteArray>,
        cryptoConfig: CryptoConfig?,
        outputFloat: Boolean) : SimpleDecoder<DecoderInputBuffer?, SimpleDecoderOutputBuffer?, OpusDecoderException?>(arrayOfNulls(numInputBuffers), arrayOfNulls(numOutputBuffers)) {
    val outputFloat: Boolean
    val channelCount: Int
    private val cryptoConfig: CryptoConfig?
    private val preSkipSamples: Int
    private val seekPreRollSamples: Int
    private val nativeDecoderContext: Long
    private var experimentalDiscardPaddingEnabled = false
    private var skipSamples: Int

    /**
     * Creates an Opus decoder.
     *
     * @param numInputBuffers The number of input buffers.
     * @param numOutputBuffers The number of output buffers.
     * @param initialInputBufferSize The initial size of each input buffer.
     * @param initializationData Codec-specific initialization data. The first element must contain an
     * opus header. Optionally, the list may contain two additional buffers, which must contain
     * the encoder delay and seek pre roll values in nanoseconds, encoded as longs.
     * @param cryptoConfig The [CryptoConfig] object required for decoding encrypted content.
     * May be null and can be ignored if decoder does not handle encrypted content.
     * @param outputFloat Forces the decoder to output float PCM samples when set
     * @throws OpusDecoderException Thrown if an exception occurs when initializing the decoder.
     */
    init {
        if (!OpusLibrary.isAvailable()) {
            throw OpusDecoderException("Failed to load decoder native libraries")
        }
        this.cryptoConfig = cryptoConfig
        if (cryptoConfig != null && !OpusLibrary.opusIsSecureDecodeSupported()) {
            throw OpusDecoderException("Opus decoder does not support secure decode")
        }
        val initializationDataSize = initializationData.size
        if (initializationDataSize != 1 && initializationDataSize != 3) {
            throw OpusDecoderException("Invalid initialization data size")
        }
        if (initializationDataSize == 3
                && (initializationData[1].size != 8 || initializationData[2].size != 8)) {
            throw OpusDecoderException("Invalid pre-skip or seek pre-roll")
        }
        preSkipSamples = getPreSkipSamples(initializationData)
        seekPreRollSamples = getSeekPreRollSamples(initializationData)
        skipSamples = preSkipSamples
        val headerBytes = initializationData[0]
        if (headerBytes.size < 19) {
            throw OpusDecoderException("Invalid header length")
        }
        channelCount = getChannelCount(headerBytes)
        if (channelCount > 8) {
            throw OpusDecoderException("Invalid channel count: $channelCount")
        }
        val gain = readSignedLittleEndian16(headerBytes, 16)
        val streamMap = ByteArray(8)
        val numStreams: Int
        val numCoupled: Int
        if (headerBytes[18].toInt() == 0) { // Channel mapping
            // If there is no channel mapping, use the defaults.
            if (channelCount > 2) { // Maximum channel count with default layout.
                throw OpusDecoderException("Invalid header, missing stream map")
            }
            numStreams = 1
            numCoupled = if (channelCount == 2) 1 else 0
            streamMap[0] = 0
            streamMap[1] = 1
        } else {
            if (headerBytes.size < 21 + channelCount) {
                throw OpusDecoderException("Invalid header length")
            }
            // Read the channel mapping.
            numStreams = headerBytes[19].toInt() and 0xFF
            numCoupled = headerBytes[20].toInt() and 0xFF
            System.arraycopy(headerBytes, 21, streamMap, 0, channelCount)
        }
        nativeDecoderContext = opusInit(SAMPLE_RATE, channelCount, numStreams, numCoupled, gain, streamMap)
        if (nativeDecoderContext == 0L) {
            throw OpusDecoderException("Failed to initialize decoder")
        }
        setInitialInputBufferSize(initialInputBufferSize)
        this.outputFloat = outputFloat
        if (outputFloat) {
            opusSetFloatOutput()
        }
    }

    /**
     * Sets whether discard padding is enabled. When enabled, discard padding samples (provided as
     * supplemental data on the input buffer) will be removed from the end of the decoder output.
     *
     *
     * This method is experimental, and will be renamed or removed in a future release.
     */
    fun experimentalSetDiscardPaddingEnabled(enabled: Boolean) {
        experimentalDiscardPaddingEnabled = enabled
    }

    override fun getName(): String {
        return "libopus" + OpusLibrary.getVersion()
    }

    public override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    }

    public override fun createOutputBuffer(): SimpleDecoderOutputBuffer {
        return SimpleDecoderOutputBuffer { outputBuffer: SimpleDecoderOutputBuffer? -> releaseOutputBuffer(outputBuffer!!) }
    }

    override fun createUnexpectedDecodeException(error: Throwable): OpusDecoderException {
        return OpusDecoderException("Unexpected decode error", error)
    }

    public override fun decode(
            inputBuffer: DecoderInputBuffer, outputBuffer: SimpleDecoderOutputBuffer, reset: Boolean): OpusDecoderException? {
        if (reset) {
            opusReset(nativeDecoderContext)
            // When seeking to 0, skip number of samples as specified in opus header. When seeking to
            // any other time, skip number of samples as specified by seek preroll.
            skipSamples = if (inputBuffer.timeUs == 0L) preSkipSamples else seekPreRollSamples
        }
        val inputData = Util.castNonNull(inputBuffer.data)
        val cryptoInfo = inputBuffer.cryptoInfo
        val result = if (inputBuffer.isEncrypted) opusSecureDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer,
                SAMPLE_RATE,
                cryptoConfig,
                cryptoInfo.mode,
                Assertions.checkNotNull(cryptoInfo.key),
                Assertions.checkNotNull(cryptoInfo.iv),
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData) else opusDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer)
        if (result < 0) {
            return if (result == DRM_ERROR) {
                val message = "Drm error: " + opusGetErrorMessage(nativeDecoderContext)
                val cause = CryptoException(opusGetErrorCode(nativeDecoderContext), message)
                OpusDecoderException(message, cause)
            } else {
                OpusDecoderException("Decode error: " + opusGetErrorMessage(result.toLong()))
            }
        }
        val outputData = Util.castNonNull(outputBuffer.data)
        outputData.position(0)
        outputData.limit(result)
        if (skipSamples > 0) {
            val bytesPerSample = samplesToBytes(1, channelCount, outputFloat)
            val skipBytes = skipSamples * bytesPerSample
            if (result <= skipBytes) {
                skipSamples -= result / bytesPerSample
                outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY)
                outputData.position(result)
            } else {
                skipSamples = 0
                outputData.position(skipBytes)
            }
        } else if (experimentalDiscardPaddingEnabled && inputBuffer.hasSupplementalData()) {
            val discardPaddingSamples = getDiscardPaddingSamples(inputBuffer.supplementalData)
            if (discardPaddingSamples > 0) {
                val discardBytes = samplesToBytes(discardPaddingSamples, channelCount, outputFloat)
                if (result >= discardBytes) {
                    outputData.limit(result - discardBytes)
                }
            }
        }
        return null
    }

    override fun release() {
        super.release()
        opusClose(nativeDecoderContext)
    }

    private external fun opusInit(
            sampleRate: Int, channelCount: Int, numStreams: Int, numCoupled: Int, gain: Int, streamMap: ByteArray): Long

    private external fun opusDecode(
            decoder: Long,
            timeUs: Long,
            inputBuffer: ByteBuffer,
            inputSize: Int,
            outputBuffer: SimpleDecoderOutputBuffer): Int

    private external fun opusSecureDecode(
            decoder: Long,
            timeUs: Long,
            inputBuffer: ByteBuffer,
            inputSize: Int,
            outputBuffer: SimpleDecoderOutputBuffer,
            sampleRate: Int,
            mediaCrypto: CryptoConfig?,
            inputMode: Int,
            key: ByteArray,
            iv: ByteArray,
            numSubSamples: Int,
            numBytesOfClearData: IntArray?,
            numBytesOfEncryptedData: IntArray?): Int

    private external fun opusClose(decoder: Long)
    private external fun opusReset(decoder: Long)
    private external fun opusGetErrorCode(decoder: Long): Int
    private external fun opusGetErrorMessage(decoder: Long): String
    private external fun opusSetFloatOutput()

    companion object {
        /** Opus streams are always 48000 Hz.  */ /* package */
        const val SAMPLE_RATE = 48000
        private const val DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840
        private const val FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT = 3
        private const val NO_ERROR = 0
        private const val DECODE_ERROR = -1
        private const val DRM_ERROR = -2

        /**
         * Parses the channel count from an Opus Identification Header.
         *
         * @param header An Opus Identification Header, as defined by RFC 7845.
         * @return The parsed channel count.
         */
        @JvmStatic
        @VisibleForTesting /* package */    fun getChannelCount(header: ByteArray): Int {
            return header[9].toInt() and 0xFF
        }

        /**
         * Returns the number of pre-skip samples specified by the given Opus codec initialization data.
         *
         * @param initializationData The codec initialization data.
         * @return The number of pre-skip samples.
         */
        @JvmStatic
        @VisibleForTesting /* package */    fun getPreSkipSamples(initializationData: List<ByteArray>): Int {
            if (initializationData.size == FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT) {
                val codecDelayNs = ByteBuffer.wrap(initializationData[1]).order(ByteOrder.nativeOrder()).long
                return (codecDelayNs * SAMPLE_RATE / C.NANOS_PER_SECOND).toInt()
            }
            // Fall back to parsing directly from the Opus Identification header.
            val headerData = initializationData[0]
            return headerData[11].toInt() and 0xFF shl 8 or (headerData[10].toInt() and 0xFF)
        }

        /**
         * Returns the number of seek per-roll samples specified by the given Opus codec initialization
         * data.
         *
         * @param initializationData The codec initialization data.
         * @return The number of seek pre-roll samples.
         */
        @JvmStatic
        @VisibleForTesting /* package */    fun getSeekPreRollSamples(initializationData: List<ByteArray>): Int {
            if (initializationData.size == FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT) {
                val seekPreRollNs = ByteBuffer.wrap(initializationData[2]).order(ByteOrder.nativeOrder()).long
                return (seekPreRollNs * SAMPLE_RATE / C.NANOS_PER_SECOND).toInt()
            }
            // Fall back to returning the default seek pre-roll.
            return DEFAULT_SEEK_PRE_ROLL_SAMPLES
        }

        /**
         * Returns the number of discard padding samples specified by the supplemental data attached to an
         * input buffer.
         *
         * @param supplementalData Supplemental data related to the an input buffer.
         * @return The number of discard padding samples to remove from the decoder output.
         */
        @JvmStatic
        @VisibleForTesting /* package */    fun getDiscardPaddingSamples(supplementalData: ByteBuffer?): Int {
            if (supplementalData == null || supplementalData.remaining() != 8) {
                return 0
            }
            val discardPaddingNs = supplementalData.order(ByteOrder.LITTLE_ENDIAN).long
            return if (discardPaddingNs < 0) {
                0
            } else (discardPaddingNs * SAMPLE_RATE / C.NANOS_PER_SECOND).toInt()
        }

        /** Returns number of bytes to represent `samples`.  */
        private fun samplesToBytes(samples: Int, channelCount: Int, outputFloat: Boolean): Int {
            val bytesPerChannel = if (outputFloat) 4 else 2
            return samples * channelCount * bytesPerChannel
        }

        private fun readSignedLittleEndian16(input: ByteArray, offset: Int): Int {
            var value = input[offset].toInt() and 0xFF
            value = value or (input[offset + 1].toInt() and 0xFF shl 8)
            return value.toShort().toInt()
        }
    }
}