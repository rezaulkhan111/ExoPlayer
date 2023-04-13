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
package com.google.android.exoplayer2.extractor

import androidx.annotation.IntDef
import com.google.android.exoplayer2.C.BufferFlags
import com.google.android.exoplayer2.C.CryptoMode
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.upstream.DataReader
import com.google.android.exoplayer2.util.ParsableByteArray
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/** Receives track level data extracted by an [Extractor].  */
interface TrackOutput {
    /** Holds data required to decrypt a sample.  */
    class CryptoData {
        /** The encryption mode used for the sample.  */
        @CryptoMode
        var cryptoMode = 0

        /** The encryption key associated with the sample. Its contents must not be modified.  */
        val encryptionKey: ByteArray

        /**
         * The number of encrypted blocks in the encryption pattern, 0 if pattern encryption does not
         * apply.
         */
        var encryptedBlocks = 0

        /**
         * The number of clear blocks in the encryption pattern, 0 if pattern encryption does not apply.
         */
        var clearBlocks = 0

        /**
         * @param cryptoMode See [.cryptoMode].
         * @param encryptionKey See [.encryptionKey].
         * @param encryptedBlocks See [.encryptedBlocks].
         * @param clearBlocks See [.clearBlocks].
         */
        constructor(
            @CryptoMode cryptoMode: Int,
            encryptionKey: ByteArray,
            encryptedBlocks: Int,
            clearBlocks: Int
        ) {
            this.cryptoMode = cryptoMode
            this.encryptionKey = encryptionKey
            this.encryptedBlocks = encryptedBlocks
            this.clearBlocks = clearBlocks
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null || javaClass != obj.javaClass) {
                return false
            }
            val other = obj as CryptoData
            return cryptoMode == other.cryptoMode && encryptedBlocks == other.encryptedBlocks && clearBlocks == other.clearBlocks && Arrays.equals(
                encryptionKey, other.encryptionKey
            )
        }

        override fun hashCode(): Int {
            var result: Int = cryptoMode
            result = 31 * result + Arrays.hashCode(encryptionKey)
            result = 31 * result + encryptedBlocks
            result = 31 * result + clearBlocks
            return result
        }
    }

    /** Defines the part of the sample data to which a call to [.sampleData] corresponds.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [SAMPLE_DATA_PART_MAIN, SAMPLE_DATA_PART_ENCRYPTION, SAMPLE_DATA_PART_SUPPLEMENTAL])
    annotation class SampleDataPart

    /**
     * Called when the [Format] of the track has been extracted from the stream.
     *
     * @param format The extracted [Format].
     */
    fun format(format: Format?)

    /**
     * Equivalent to [sampleData(input, length,][.sampleData].
     */
    @Throws(IOException::class)
    fun sampleData(input: DataReader?, length: Int, allowEndOfInput: Boolean): Int {
        return sampleData(input, length, allowEndOfInput, SAMPLE_DATA_PART_MAIN)
    }

    /**
     * Equivalent to [.sampleData] sampleData(data, length,
     * SAMPLE_DATA_PART_MAIN)}.
     */
    fun sampleData(data: ParsableByteArray?, length: Int) {
        sampleData(data, length, SAMPLE_DATA_PART_MAIN)
    }

    /**
     * Called to write sample data to the output.
     *
     * @param input A [DataReader] from which to read the sample data.
     * @param length The maximum length to read from the input.
     * @param allowEndOfInput True if encountering the end of the input having read no data is
     * allowed, and should result in [C.RESULT_END_OF_INPUT] being returned. False if it
     * should be considered an error, causing an [EOFException] to be thrown.
     * @param sampleDataPart The part of the sample data to which this call corresponds.
     * @return The number of bytes appended.
     * @throws IOException If an error occurred reading from the input.
     */
    @Throws(IOException::class)
    fun sampleData(
        input: DataReader?,
        length: Int,
        allowEndOfInput: Boolean,
        @SampleDataPart sampleDataPart: Int
    ): Int

    /**
     * Called to write sample data to the output.
     *
     * @param data A [ParsableByteArray] from which to read the sample data.
     * @param length The number of bytes to read, starting from `data.getPosition()`.
     * @param sampleDataPart The part of the sample data to which this call corresponds.
     */
    fun sampleData(data: ParsableByteArray?, length: Int, @SampleDataPart sampleDataPart: Int)

    /**
     * Called when metadata associated with a sample has been extracted from the stream.
     *
     *
     * The corresponding sample data will have already been passed to the output via calls to
     * [.sampleData] or [.sampleData].
     *
     * @param timeUs The media timestamp associated with the sample, in microseconds.
     * @param flags Flags associated with the sample. See `C.BUFFER_FLAG_*`.
     * @param size The size of the sample data, in bytes.
     * @param offset The number of bytes that have been passed to [.sampleData] or [.sampleData] since the last byte belonging to
     * the sample whose metadata is being passed.
     * @param cryptoData The encryption data required to decrypt the sample. May be null.
     */
    fun sampleMetadata(
        timeUs: Long, @BufferFlags flags: Int, size: Int, offset: Int, cryptoData: CryptoData?
    )

    companion object {
        /** Main media sample data.  */
        const val SAMPLE_DATA_PART_MAIN = 0

        /**
         * Sample encryption data.
         *
         *
         * The format for encryption information is:
         *
         *
         *  * (1 byte) `encryption_signal_byte`: Most significant bit signals whether the
         * encryption data contains subsample encryption data. The remaining bits contain `initialization_vector_size`.
         *  * (`initialization_vector_size` bytes) Initialization vector.
         *  * If subsample encryption data is present, as per `encryption_signal_byte`, the
         * encryption data also contains:
         *
         *  * (2 bytes) `subsample_encryption_data_length`.
         *  * (`subsample_encryption_data_length * 6` bytes) Subsample encryption data
         * (repeated `subsample_encryption_data_length` times:
         *
         *  * (2 bytes) Size of a clear section in sample.
         *  * (4 bytes) Size of an encryption section in sample.
         *
         *
         *
         */
        const val SAMPLE_DATA_PART_ENCRYPTION = 1

        /**
         * Sample supplemental data.
         *
         *
         * If a sample contains supplemental data, the format of the entire sample data will be:
         *
         *
         *  * If the sample has the [C.BUFFER_FLAG_ENCRYPTED] flag set, all encryption
         * information.
         *  * (4 bytes) `sample_data_size`: The size of the actual sample data, not including
         * supplemental data or encryption information.
         *  * (`sample_data_size` bytes): The media sample data.
         *  * (remaining bytes) The supplemental data.
         *
         */
        const val SAMPLE_DATA_PART_SUPPLEMENTAL = 2
    }
}