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

import com.google.common.base.Charsetsimport

java.nio.ByteBufferimport java.nio.charset.Charsetimport java.util.*
/**
 * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
 * parsed with the assumption that their constituent bytes are in big endian order.
 */
class ParsableByteArray {
    /**
     * Returns the underlying array.
     *
     *
     * Changes to this array are reflected in the results of the `read...()` methods.
     *
     *
     * This reference must be assumed to become invalid when [.reset] or [ ][.ensureCapacity] are called (because the array might get reallocated).
     */
    var data: ByteArray?
        private set
    private var position: Int = 0

    // TODO(internal b/147657250): Enforce this limit on all read methods.
    private var limit: Int = 0

    /** Creates a new instance that initially has no backing data.  */
    constructor() {
        data = Util.EMPTY_BYTE_ARRAY
    }

    /**
     * Creates a new instance with `limit` bytes and sets the limit.
     *
     * @param limit The limit to set.
     */
    constructor(limit: Int) {
        data = ByteArray(limit)
        this.limit = limit
    }

    /**
     * Creates a new instance wrapping `data`, and sets the limit to `data.length`.
     *
     * @param data The array to wrap.
     */
    constructor(data: ByteArray) {
        this.data = data
        limit = data.size
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     * @param limit The limit to set.
     */
    constructor(data: ByteArray?, limit: Int) {
        this.data = data
        this.limit = limit
    }

    /**
     * Resets the position to zero and the limit to the specified value. This might replace or wipe
     * the [underlying array][.getData], potentially invalidating any local references.
     *
     * @param limit The limit to set.
     */
    fun reset(limit: Int) {
        reset((if (capacity() < limit) ByteArray(limit) else data)!!, limit)
    }
    /**
     * Updates the instance to wrap `data`, and resets the position to zero.
     *
     * @param data The array to wrap.
     * @param limit The limit to set.
     */
    /**
     * Updates the instance to wrap `data`, and resets the position to zero and the limit to
     * `data.length`.
     *
     * @param data The array to wrap.
     */
    @JvmOverloads
    fun reset(data: ByteArray, limit: Int = data.length) {
        this.data = data
        this.limit = limit
        position = 0
    }

    /**
     * Ensures the backing array is at least `requiredCapacity` long.
     *
     *
     * [position][.getPosition], [limit][.limit], and all data in the underlying
     * array (including that beyond [.limit]) are preserved.
     *
     *
     * This might replace or wipe the [underlying array][.getData], potentially invalidating
     * any local references.
     */
    fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > capacity()) {
            data = Arrays.copyOf(data, requiredCapacity)
        }
    }

    /** Returns the number of bytes yet to be read.  */
    fun bytesLeft(): Int {
        return limit - position
    }

    /** Returns the limit.  */
    fun limit(): Int {
        return limit
    }

    /**
     * Sets the limit.
     *
     * @param limit The limit to set.
     */
    fun setLimit(limit: Int) {
        Assertions.checkArgument(limit >= 0 && limit <= data!!.size)
        this.limit = limit
    }

    /** Returns the current offset in the array, in bytes.  */
    fun getPosition(): Int {
        return position
    }

    /**
     * Sets the reading offset in the array.
     *
     * @param position Byte offset in the array from which to read.
     * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
     * array.
     */
    fun setPosition(position: Int) {
        // It is fine for position to be at the end of the array.
        Assertions.checkArgument(position >= 0 && position <= limit)
        this.position = position
    }

    /** Returns the capacity of the array, which may be larger than the limit.  */
    fun capacity(): Int {
        return data!!.size
    }

    /**
     * Moves the reading offset by `bytes`.
     *
     * @param bytes The number of bytes to skip.
     * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
     * array.
     */
    fun skipBytes(bytes: Int) {
        setPosition(position + bytes)
    }

    /**
     * Reads the next `length` bytes into `bitArray`, and resets the position of `bitArray` to zero.
     *
     * @param bitArray The [ParsableBitArray] into which the bytes should be read.
     * @param length The number of bytes to write.
     */
    fun readBytes(bitArray: ParsableBitArray, length: Int) {
        readBytes(bitArray.data, 0, length)
        bitArray.setPosition(0)
    }

    /**
     * Reads the next `length` bytes into `buffer` at `offset`.
     *
     * @see System.arraycopy
     * @param buffer The array into which the read data should be written.
     * @param offset The offset in `buffer` at which the read data should be written.
     * @param length The number of bytes to read.
     */
    fun readBytes(buffer: ByteArray?, offset: Int, length: Int) {
        System.arraycopy(data, position, buffer, offset, length)
        position += length
    }

    /**
     * Reads the next `length` bytes into `buffer`.
     *
     * @see ByteBuffer.put
     * @param buffer The [ByteBuffer] into which the read data should be written.
     * @param length The number of bytes to read.
     */
    fun readBytes(buffer: ByteBuffer, length: Int) {
        buffer.put(data, position, length)
        position += length
    }

    /** Peeks at the next byte as an unsigned value.  */
    fun peekUnsignedByte(): Int {
        return (data!!.get(position).toInt() and 0xFF)
    }

    /** Peeks at the next char.  */
    fun peekChar(): Char {
        return (((data!!.get(position).toInt() and 0xFF) shl 8) or (data!!.get(position + 1).toInt() and 0xFF)).toChar()
    }

    /** Reads the next byte as an unsigned value.  */
    fun readUnsignedByte(): Int {
        return (data!!.get(position++).toInt() and 0xFF)
    }

    /** Reads the next two bytes as an unsigned value.  */
    fun readUnsignedShort(): Int {
        return ((data!!.get(position++).toInt() and 0xFF) shl 8) or (data!!.get(position++).toInt() and 0xFF)
    }

    /** Reads the next two bytes as an unsigned value.  */
    fun readLittleEndianUnsignedShort(): Int {
        return (data!!.get(position++).toInt() and 0xFF) or ((data!!.get(position++).toInt() and 0xFF) shl 8)
    }

    /** Reads the next two bytes as a signed value.  */
    fun readShort(): Short {
        return (((data!!.get(position++).toInt() and 0xFF) shl 8) or (data!!.get(position++).toInt() and 0xFF)).toShort()
    }

    /** Reads the next two bytes as a signed value.  */
    fun readLittleEndianShort(): Short {
        return ((data!!.get(position++).toInt() and 0xFF) or ((data!!.get(position++).toInt() and 0xFF) shl 8)).toShort()
    }

    /** Reads the next three bytes as an unsigned value.  */
    fun readUnsignedInt24(): Int {
        return ((data!!.get(position++).toInt() and 0xFF) shl 16
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or (data!!.get(position++).toInt() and 0xFF)
    }

    /** Reads the next three bytes as a signed value.  */
    fun readInt24(): Int {
        return (((data!!.get(position++).toInt() and 0xFF) shl 24) shr 8
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or (data!!.get(position++).toInt() and 0xFF)
    }

    /** Reads the next three bytes as a signed value in little endian order.  */
    fun readLittleEndianInt24(): Int {
        return ((data!!.get(position++).toInt() and 0xFF)
                or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 16))
    }

    /** Reads the next three bytes as an unsigned value in little endian order.  */
    fun readLittleEndianUnsignedInt24(): Int {
        return ((data!!.get(position++).toInt() and 0xFF)
                or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 16))
    }

    /** Reads the next four bytes as an unsigned value.  */
    fun readUnsignedInt(): Long {
        return ((data!!.get(position++).toLong() and 0xFFL) shl 24
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 16
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 8
                ) or (data!!.get(position++).toLong() and 0xFFL)
    }

    /** Reads the next four bytes as an unsigned value in little endian order.  */
    fun readLittleEndianUnsignedInt(): Long {
        return ((data!!.get(position++).toLong() and 0xFFL)
                or ((data!!.get(position++).toLong() and 0xFFL) shl 8
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 16
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 24))
    }

    /** Reads the next four bytes as a signed value  */
    fun readInt(): Int {
        return ((data!!.get(position++).toInt() and 0xFF) shl 24
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 16
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or (data!!.get(position++).toInt() and 0xFF)
    }

    /** Reads the next four bytes as a signed value in little endian order.  */
    fun readLittleEndianInt(): Int {
        return ((data!!.get(position++).toInt() and 0xFF)
                or ((data!!.get(position++).toInt() and 0xFF) shl 8
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 16
                ) or ((data!!.get(position++).toInt() and 0xFF) shl 24))
    }

    /** Reads the next eight bytes as a signed value.  */
    fun readLong(): Long {
        return ((data!!.get(position++).toLong() and 0xFFL) shl 56
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 48
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 40
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 32
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 24
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 16
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 8
                ) or (data!!.get(position++).toLong() and 0xFFL)
    }

    /** Reads the next eight bytes as a signed value in little endian order.  */
    fun readLittleEndianLong(): Long {
        return ((data!!.get(position++).toLong() and 0xFFL)
                or ((data!!.get(position++).toLong() and 0xFFL) shl 8
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 16
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 24
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 32
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 40
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 48
                ) or ((data!!.get(position++).toLong() and 0xFFL) shl 56))
    }

    /** Reads the next four bytes, returning the integer portion of the fixed point 16.16 integer.  */
    fun readUnsignedFixedPoint1616(): Int {
        val result: Int = ((data!!.get(position++).toInt() and 0xFF) shl 8) or (data!!.get(position++).toInt() and 0xFF)
        position += 2 // Skip the non-integer portion.
        return result
    }

    /**
     * Reads a Synchsafe integer.
     *
     *
     * Synchsafe integers keep the highest bit of every byte zeroed. A 32 bit synchsafe integer can
     * store 28 bits of information.
     *
     * @return The parsed value.
     */
    fun readSynchSafeInt(): Int {
        val b1: Int = readUnsignedByte()
        val b2: Int = readUnsignedByte()
        val b3: Int = readUnsignedByte()
        val b4: Int = readUnsignedByte()
        return (b1 shl 21) or (b2 shl 14) or (b3 shl 7) or b4
    }

    /**
     * Reads the next four bytes as an unsigned integer into an integer, if the top bit is a zero.
     *
     * @throws IllegalStateException Thrown if the top bit of the input data is set.
     */
    fun readUnsignedIntToInt(): Int {
        val result: Int = readInt()
        if (result < 0) {
            throw IllegalStateException("Top bit not zero: " + result)
        }
        return result
    }

    /**
     * Reads the next four bytes as a little endian unsigned integer into an integer, if the top bit
     * is a zero.
     *
     * @throws IllegalStateException Thrown if the top bit of the input data is set.
     */
    fun readLittleEndianUnsignedIntToInt(): Int {
        val result: Int = readLittleEndianInt()
        if (result < 0) {
            throw IllegalStateException("Top bit not zero: " + result)
        }
        return result
    }

    /**
     * Reads the next eight bytes as an unsigned long into a long, if the top bit is a zero.
     *
     * @throws IllegalStateException Thrown if the top bit of the input data is set.
     */
    fun readUnsignedLongToLong(): Long {
        val result: Long = readLong()
        if (result < 0) {
            throw IllegalStateException("Top bit not zero: " + result)
        }
        return result
    }

    /** Reads the next four bytes as a 32-bit floating point value.  */
    fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readInt())
    }

    /** Reads the next eight bytes as a 64-bit floating point value.  */
    fun readDouble(): Double {
        return java.lang.Double.longBitsToDouble(readLong())
    }
    /**
     * Reads the next `length` bytes as characters in the specified [Charset].
     *
     * @param length The number of bytes to read.
     * @param charset The character set of the encoded characters.
     * @return The string encoded by the bytes in the specified character set.
     */
    /**
     * Reads the next `length` bytes as UTF-8 characters.
     *
     * @param length The number of bytes to read.
     * @return The string encoded by the bytes.
     */
    @JvmOverloads
    fun readString(length: Int, charset: Charset? = Charsets.UTF_8): String {
        val result: String = String((data)!!, position, length, (charset)!!)
        position += length
        return result
    }

    /**
     * Reads the next `length` bytes as UTF-8 characters. A terminating NUL byte is discarded,
     * if present.
     *
     * @param length The number of bytes to read.
     * @return The string, not including any terminating NUL byte.
     */
    fun readNullTerminatedString(length: Int): String? {
        if (length == 0) {
            return ""
        }
        var stringLength: Int = length
        val lastIndex: Int = position + length - 1
        if (lastIndex < limit && data!!.get(lastIndex).toInt() == 0) {
            stringLength--
        }
        val result: String? = Util.fromUtf8Bytes(data, position, stringLength)
        position += length
        return result
    }

    /**
     * Reads up to the next NUL byte (or the limit) as UTF-8 characters.
     *
     * @return The string not including any terminating NUL byte, or null if the end of the data has
     * already been reached.
     */
    fun readNullTerminatedString(): String? {
        return readDelimiterTerminatedString('\u0000')
    }

    /**
     * Reads up to the next delimiter byte (or the limit) as UTF-8 characters.
     *
     * @return The string not including any terminating delimiter byte, or null if the end of the data
     * has already been reached.
     */
    fun readDelimiterTerminatedString(delimiter: Char): String? {
        if (bytesLeft() == 0) {
            return null
        }
        var stringLimit: Int = position
        while (stringLimit < limit && data!!.get(stringLimit) != delimiter.code.toByte()) {
            stringLimit++
        }
        val string: String? = Util.fromUtf8Bytes(data, position, stringLimit - position)
        position = stringLimit
        if (position < limit) {
            position++
        }
        return string
    }

    /**
     * Reads a line of text.
     *
     *
     * A line is considered to be terminated by any one of a carriage return ('\r'), a line feed
     * ('\n'), or a carriage return followed immediately by a line feed ('\r\n'). The UTF-8 charset is
     * used. This method discards leading UTF-8 byte order marks, if present.
     *
     * @return The line not including any line-termination characters, or null if the end of the data
     * has already been reached.
     */
    fun readLine(): String? {
        if (bytesLeft() == 0) {
            return null
        }
        var lineLimit: Int = position
        while (lineLimit < limit && !Util.isLinebreak(data!!.get(lineLimit).toInt())) {
            lineLimit++
        }
        if ((lineLimit - position >= 3
                        ) && (data!!.get(position) == 0xEF.toByte()
                        ) && (data!!.get(position + 1) == 0xBB.toByte()
                        ) && (data!!.get(position + 2) == 0xBF.toByte())) {
            // There's a UTF-8 byte order mark at the start of the line. Discard it.
            position += 3
        }
        val line: String? = Util.fromUtf8Bytes(data, position, lineLimit - position)
        position = lineLimit
        if (position == limit) {
            return line
        }
        if (data!!.get(position) == '\r'.code.toByte()) {
            position++
            if (position == limit) {
                return line
            }
        }
        if (data!!.get(position) == '\n'.code.toByte()) {
            position++
        }
        return line
    }

    /**
     * Reads a long value encoded by UTF-8 encoding
     *
     * @throws NumberFormatException if there is a problem with decoding
     * @return Decoded long value
     */
    fun readUtf8EncodedLong(): Long {
        var length: Int = 0
        var value: Long = data!!.get(position).toLong()
        // find the high most 0 bit
        for (j in 7 downTo 0) {
            if ((value and (1 shl j).toLong()) == 0L) {
                if (j < 6) {
                    value = value and ((1 shl j) - 1).toLong()
                    length = 7 - j
                } else if (j == 7) {
                    length = 1
                }
                break
            }
        }
        if (length == 0) {
            throw NumberFormatException("Invalid UTF-8 sequence first byte: " + value)
        }
        for (i in 1 until length) {
            val x: Int = data!!.get(position + i).toInt()
            if ((x and 0xC0) != 0x80) { // if the high most 0 bit not 7th
                throw NumberFormatException("Invalid UTF-8 sequence continuation byte: " + value)
            }
            value = (value shl 6) or (x and 0x3F).toLong()
        }
        position += length
        return value
    }
}