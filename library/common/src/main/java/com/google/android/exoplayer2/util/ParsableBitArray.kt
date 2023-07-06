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
package com.google.android.exoplayer2.util

import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Util.toLong
import com.google.android.exoplayer2.util.Util.toUnsignedLong
import com.google.common.base.Charsets
import java.nio.charset.Charset
import kotlin.math.min

/** Wraps a byte array, providing methods that allow it to be read as a bitstream.  */
class ParsableBitArray {
    var data: ByteArray? = null

    // The offset within the data, stored as the current byte offset, and the bit offset within that
    // byte (from 0 to 7).
    private var byteOffset = 0
    private var bitOffset = 0
    private var byteLimit = 0

    /** Creates a new instance that initially has no backing data.  */
    constructor() {
        data = Util.EMPTY_BYTE_ARRAY
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     */
    constructor(data: ByteArray) {
        ParsableBitArray(data, data.size)
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     * @param limit The limit in bytes.
     */
    constructor(data: ByteArray?, limit: Int) {
        this.data = data
        byteLimit = limit
    }

    /**
     * Updates the instance to wrap `data`, and resets the position to zero.
     *
     * @param data The array to wrap.
     */
    fun reset(data: ByteArray) {
        reset(data, data.size)
    }

    /**
     * Sets this instance's data, position and limit to match the provided `parsableByteArray`.
     * Any modifications to the underlying data array will be visible in both instances
     *
     * @param parsableByteArray The [ParsableByteArray].
     */
    fun reset(parsableByteArray: ParsableByteArray) {
        reset(parsableByteArray.data, parsableByteArray.limit())
        setPosition(parsableByteArray.getPosition() * 8)
    }

    /**
     * Updates the instance to wrap `data`, and resets the position to zero.
     *
     * @param data The array to wrap.
     * @param limit The limit in bytes.
     */
    fun reset(data: ByteArray?, limit: Int) {
        this.data = data
        byteOffset = 0
        bitOffset = 0
        byteLimit = limit
    }

    /** Returns the number of bits yet to be read.  */
    fun bitsLeft(): Int {
        return (byteLimit - byteOffset) * 8 - bitOffset
    }

    /** Returns the current bit offset.  */
    fun getPosition(): Int {
        return byteOffset * 8 + bitOffset
    }

    /**
     * Returns the current byte offset. Must only be called when the position is byte aligned.
     *
     * @throws IllegalStateException If the position isn't byte aligned.
     */
    fun getBytePosition(): Int {
        checkState(bitOffset == 0)
        return byteOffset
    }

    /**
     * Sets the current bit offset.
     *
     * @param position The position to set.
     */
    fun setPosition(position: Int) {
        byteOffset = position / 8
        bitOffset = position - byteOffset * 8
        assertValidOffset()
    }

    /** Skips a single bit.  */
    fun skipBit() {
        if (++bitOffset == 8) {
            bitOffset = 0
            byteOffset++
        }
        assertValidOffset()
    }

    /**
     * Skips bits and moves current reading position forward.
     *
     * @param numBits The number of bits to skip.
     */
    fun skipBits(numBits: Int) {
        val numBytes = numBits / 8
        byteOffset += numBytes
        bitOffset += numBits - numBytes * 8
        if (bitOffset > 7) {
            byteOffset++
            bitOffset -= 8
        }
        assertValidOffset()
    }

    /**
     * Reads a single bit.
     *
     * @return Whether the bit is set.
     */
    fun readBit(): Boolean {
        val returnValue = data!![byteOffset].toInt() and (0x80 shr bitOffset) != 0
        skipBit()
        return returnValue
    }

    /**
     * Reads up to 32 bits.
     *
     * @param numBits The number of bits to read.
     * @return An integer whose bottom `numBits` bits hold the read data.
     */
    fun readBits(numBits: Int): Int {
        if (numBits == 0) {
            return 0
        }
        var returnValue = 0
        bitOffset += numBits
        while (bitOffset > 8) {
            bitOffset -= 8
            returnValue = returnValue or (data!![byteOffset++].toInt() and 0xFF shl bitOffset)
        }
        returnValue = returnValue or (data!![byteOffset].toInt() and 0xFF shr 8) - bitOffset
        returnValue = returnValue and (-0x1 ushr 32) - numBits
        if (bitOffset == 8) {
            bitOffset = 0
            byteOffset++
        }
        assertValidOffset()
        return returnValue
    }

    /**
     * Reads up to 64 bits.
     *
     * @param numBits The number of bits to read.
     * @return A long whose bottom `numBits` bits hold the read data.
     */
    fun readBitsToLong(numBits: Int): Long {
        return if (numBits <= 32) {
            toUnsignedLong(readBits(numBits))
        } else toLong(readBits(numBits - 32), readBits(32))
    }

    /**
     * Reads `numBits` bits into `buffer`.
     *
     * @param buffer The array into which the read data should be written. The trailing `numBits
     * % 8` bits are written into the most significant bits of the last modified `buffer`
     * byte. The remaining ones are unmodified.
     * @param offset The offset in `buffer` at which the read data should be written.
     * @param numBits The number of bits to read.
     */
    fun readBits(buffer: ByteArray, offset: Int, numBits: Int) {
        // Whole bytes.
        val to = offset + (numBits shr 3) /* numBits / 8 */
        for (i in offset until to) {
            buffer[i] = (data!![byteOffset++].toInt() shl bitOffset).toByte()
            buffer[i] = (buffer[i].toInt() or (data!![byteOffset].toInt() and 0xFF shr 8) - bitOffset).toByte()
        }
        // Trailing bits.
        val bitsLeft = numBits and 7 /* numBits % 8 */
        if (bitsLeft == 0) {
            return
        }
        // Set bits that are going to be overwritten to 0.
        buffer[to] = (buffer[to].toInt() and (0xFF shr bitsLeft)).toByte()
        if (bitOffset + bitsLeft > 8) {
            // We read the rest of data[byteOffset] and increase byteOffset.
            buffer[to] = (buffer[to].toInt() or (data!![byteOffset++].toInt() and 0xFF shl bitOffset)).toByte()
            bitOffset -= 8
        }
        bitOffset += bitsLeft
        val lastDataByteTrailingBits = data!![byteOffset].toInt() and 0xFF shr 8 - bitOffset
        buffer[to] = (buffer[to].toInt() or (lastDataByteTrailingBits shl 8 - bitsLeft).toByte().toInt()).toByte()
        if (bitOffset == 8) {
            bitOffset = 0
            byteOffset++
        }
        assertValidOffset()
    }

    /**
     * Aligns the position to the next byte boundary. Does nothing if the position is already aligned.
     */
    fun byteAlign() {
        if (bitOffset == 0) {
            return
        }
        bitOffset = 0
        byteOffset++
        assertValidOffset()
    }

    /**
     * Reads the next `length` bytes into `buffer`. Must only be called when the position
     * is byte aligned.
     *
     * @see System.arraycopy
     * @param buffer The array into which the read data should be written.
     * @param offset The offset in `buffer` at which the read data should be written.
     * @param length The number of bytes to read.
     * @throws IllegalStateException If the position isn't byte aligned.
     */
    fun readBytes(buffer: ByteArray?, offset: Int, length: Int) {
        checkState(bitOffset == 0)
        System.arraycopy(data, byteOffset, buffer, offset, length)
        byteOffset += length
        assertValidOffset()
    }

    /**
     * Skips the next `length` bytes. Must only be called when the position is byte aligned.
     *
     * @param length The number of bytes to read.
     * @throws IllegalStateException If the position isn't byte aligned.
     */
    fun skipBytes(length: Int) {
        checkState(bitOffset == 0)
        byteOffset += length
        assertValidOffset()
    }

    /**
     * Reads the next `length` bytes as a UTF-8 string. Must only be called when the position is
     * byte aligned.
     *
     * @param length The number of bytes to read.
     * @return The string encoded by the bytes in UTF-8.
     */
    fun readBytesAsString(length: Int): String? {
        return readBytesAsString(length, Charsets.UTF_8)
    }

    /**
     * Reads the next `length` bytes as a string encoded in [Charset]. Must only be called
     * when the position is byte aligned.
     *
     * @param length The number of bytes to read.
     * @param charset The character set of the encoded characters.
     * @return The string encoded by the bytes in the specified character set.
     */
    fun readBytesAsString(length: Int, charset: Charset?): String? {
        val bytes = ByteArray(length)
        readBytes(bytes, 0, length)
        return String(bytes, charset!!)
    }

    /**
     * Overwrites `numBits` from this array using the `numBits` least significant bits
     * from `value`. Bits are written in order from most significant to least significant. The
     * read position is advanced by `numBits`.
     *
     * @param value The integer whose `numBits` least significant bits are written into [     ][.data].
     * @param numBits The number of bits to write.
     */
    fun putInt(value: Int, numBits: Int) {
        var valueIn = value
        var remainingBitsToRead = numBits
        if (numBits < 32) {
            valueIn = valueIn and (1 shl numBits) - 1
        }
        val firstByteReadSize = min(8 - bitOffset, numBits)
        val firstByteRightPaddingSize = 8 - bitOffset - firstByteReadSize
        val firstByteBitmask = 0xFF00 shr bitOffset or (1 shl firstByteRightPaddingSize) - 1
        data!![byteOffset] = (data!![byteOffset].toInt() and firstByteBitmask).toByte()
        val firstByteInputBits = valueIn ushr numBits - firstByteReadSize
        data!![byteOffset] = (data!![byteOffset].toInt() or (firstByteInputBits shl firstByteRightPaddingSize)).toByte()
        remainingBitsToRead -= firstByteReadSize
        var currentByteIndex = byteOffset + 1
        while (remainingBitsToRead > 8) {
            data!![currentByteIndex++] = (valueIn ushr remainingBitsToRead - 8).toByte()
            remainingBitsToRead -= 8
        }
        val lastByteRightPaddingSize = 8 - remainingBitsToRead
        data!![currentByteIndex] = (data!![currentByteIndex].toInt() and (1 shl lastByteRightPaddingSize) - 1).toByte()
        val lastByteInput = valueIn and (1 shl remainingBitsToRead) - 1
        data!![currentByteIndex] = (data!![currentByteIndex].toInt() or (lastByteInput shl lastByteRightPaddingSize)).toByte()
        skipBits(numBits)
        assertValidOffset()
    }

    private fun assertValidOffset() {
        // It is fine for position to be at the end of the array, but no further.
        checkState(byteOffset >= 0 && (byteOffset < byteLimit || byteOffset == byteLimit) && bitOffset == 0)
    }
}