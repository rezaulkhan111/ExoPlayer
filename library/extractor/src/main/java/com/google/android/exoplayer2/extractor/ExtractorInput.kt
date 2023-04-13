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

import com.google.android.exoplayer2.upstream.DataReader
import java.io.IOException

/**
 * Provides data to be consumed by an [Extractor].
 *
 *
 * This interface provides two modes of accessing the underlying input. See the subheadings below
 * for more info about each mode.
 *
 *
 *  * The `read()/peek()` and `skip()` methods provide [InputStream]-like
 * byte-level access operations.
 *  * The `read/skip/peekFully()` and `advancePeekPosition()` methods assume the user
 * wants to read an entire block/frame/header of known length.
 *
 *
 * <h2>[InputStream]-like methods</h2>
 *
 *
 * The `read()/peek()` and `skip()` methods provide [InputStream]-like
 * byte-level access operations. The `length` parameter is a maximum, and each method returns
 * the number of bytes actually processed. This may be less than `length` because the end of
 * the input was reached, or the method was interrupted, or the operation was aborted early for
 * another reason.
 *
 * <h2>Block-based methods</h2>
 *
 *
 * The `read/skip/peekFully()` and `advancePeekPosition()` methods assume the user
 * wants to read an entire block/frame/header of known length.
 *
 *
 * These methods all have a variant that takes a boolean `allowEndOfInput` parameter. This
 * parameter is intended to be set to true when the caller believes the input might be fully
 * exhausted before the call is made (i.e. they've previously read/skipped/peeked the final
 * block/frame/header). It's **not** intended to allow a partial read (i.e. greater than 0 bytes,
 * but less than `length`) to succeed - this will always throw an [EOFException] from
 * these methods (a partial read is assumed to indicate a malformed block/frame/header - and
 * therefore a malformed file).
 *
 *
 * The expected behaviour of the block-based methods is therefore:
 *
 *
 *  * Already at end-of-input and `allowEndOfInput=false`: Throw [EOFException].
 *  * Already at end-of-input and `allowEndOfInput=true`: Return `false`.
 *  * Encounter end-of-input during read/skip/peek/advance: Throw [EOFException]
 * (regardless of `allowEndOfInput`).
 *
 */
interface ExtractorInput : DataReader {
    /**
     * Reads up to `length` bytes from the input and resets the peek position.
     *
     *
     * This method blocks until at least one byte of data can be read, the end of the input is
     * detected, or an exception is thrown.
     *
     * @param buffer A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The maximum number of bytes to read from the input.
     * @return The number of bytes read, or [C.RESULT_END_OF_INPUT] if the input has ended.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    override fun read(buffer: ByteArray?, offset: Int, length: Int): Int

    /**
     * Like [.read], but reads the requested `length` in full.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to read from the input.
     * @param allowEndOfInput True if encountering the end of the input having read no data is
     * allowed, and should result in `false` being returned. False if it should be
     * considered an error, causing an [EOFException] to be thrown. See note in class
     * Javadoc.
     * @return True if the read was successful. False if `allowEndOfInput=true` and the end of
     * the input was encountered having read no data.
     * @throws EOFException If the end of input was encountered having partially satisfied the read
     * (i.e. having read at least one byte, but fewer than `length`), or if no bytes were
     * read and `allowEndOfInput` is false.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun readFully(target: ByteArray?, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean

    /**
     * Equivalent to [readFully(target, offset, length,][.readFully].
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to read from the input.
     * @throws EOFException If the end of input was encountered.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun readFully(target: ByteArray?, offset: Int, length: Int)

    /**
     * Like [.read], except the data is skipped instead of read.
     *
     * @param length The maximum number of bytes to skip from the input.
     * @return The number of bytes skipped, or [C.RESULT_END_OF_INPUT] if the input has ended.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun skip(length: Int): Int

    /**
     * Like [.readFully], except the data is skipped instead of read.
     *
     * @param length The number of bytes to skip from the input.
     * @param allowEndOfInput True if encountering the end of the input having skipped no data is
     * allowed, and should result in `false` being returned. False if it should be
     * considered an error, causing an [EOFException] to be thrown. See note in class
     * Javadoc.
     * @return True if the skip was successful. False if `allowEndOfInput=true` and the end of
     * the input was encountered having skipped no data.
     * @throws EOFException If the end of input was encountered having partially satisfied the skip
     * (i.e. having skipped at least one byte, but fewer than `length`), or if no bytes were
     * skipped and `allowEndOfInput` is false.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean

    /**
     * Like [.readFully], except the data is skipped instead of read.
     *
     *
     * Encountering the end of input is always considered an error, and will result in an [ ] being thrown.
     *
     * @param length The number of bytes to skip from the input.
     * @throws EOFException If the end of input was encountered.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun skipFully(length: Int)

    /**
     * Peeks up to `length` bytes from the peek position. The current read position is left
     * unchanged.
     *
     *
     * This method blocks until at least one byte of data can be peeked, the end of the input is
     * detected, or an exception is thrown.
     *
     *
     * Calling [.resetPeekPosition] resets the peek position to equal the current read
     * position, so the caller can peek the same data again. Reading or skipping also resets the peek
     * position.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The maximum number of bytes to peek from the input.
     * @return The number of bytes peeked, or [C.RESULT_END_OF_INPUT] if the input has ended.
     * @throws IOException If an error occurs peeking from the input.
     */
    @Throws(IOException::class)
    fun peek(target: ByteArray?, offset: Int, length: Int): Int

    /**
     * Like [.peek], but peeks the requested `length` in full.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to peek from the input.
     * @param allowEndOfInput True if encountering the end of the input having peeked no data is
     * allowed, and should result in `false` being returned. False if it should be
     * considered an error, causing an [EOFException] to be thrown. See note in class
     * Javadoc.
     * @return True if the peek was successful. False if `allowEndOfInput=true` and the end of
     * the input was encountered having peeked no data.
     * @throws EOFException If the end of input was encountered having partially satisfied the peek
     * (i.e. having peeked at least one byte, but fewer than `length`), or if no bytes were
     * peeked and `allowEndOfInput` is false.
     * @throws IOException If an error occurs peeking from the input.
     */
    @Throws(IOException::class)
    fun peekFully(target: ByteArray?, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean

    /**
     * Equivalent to [peekFully(target, offset, length,][.peekFully].
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to peek from the input.
     * @throws EOFException If the end of input was encountered.
     * @throws IOException If an error occurs peeking from the input.
     */
    @Throws(IOException::class)
    fun peekFully(target: ByteArray?, offset: Int, length: Int)

    /**
     * Advances the peek position by `length` bytes. Like [.peekFully] except the data is skipped instead of read.
     *
     * @param length The number of bytes by which to advance the peek position.
     * @param allowEndOfInput True if encountering the end of the input before advancing is allowed,
     * and should result in `false` being returned. False if it should be considered an
     * error, causing an [EOFException] to be thrown. See note in class Javadoc.
     * @return True if advancing the peek position was successful. False if `allowEndOfInput=true` and the end of the input was encountered before advancing over any
     * data.
     * @throws EOFException If the end of input was encountered having partially advanced (i.e. having
     * advanced by at least one byte, but fewer than `length`), or if the end of input was
     * encountered before advancing and `allowEndOfInput` is false.
     * @throws IOException If an error occurs advancing the peek position.
     */
    @Throws(IOException::class)
    fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean

    /**
     * Advances the peek position by `length` bytes. Like [.peekFully]
     * except the data is skipped instead of read.
     *
     * @param length The number of bytes to peek from the input.
     * @throws EOFException If the end of input was encountered.
     * @throws IOException If an error occurs peeking from the input.
     */
    @Throws(IOException::class)
    fun advancePeekPosition(length: Int)

    /** Resets the peek position to equal the current read position.  */
    fun resetPeekPosition()

    /**
     * Returns the current peek position (byte offset) in the stream.
     *
     * @return The peek position (byte offset) in the stream.
     */
    val peekPosition: Long

    /**
     * Returns the current read position (byte offset) in the stream.
     *
     * @return The read position (byte offset) in the stream.
     */
    val position: Long

    /**
     * Returns the length of the source stream, or [C.LENGTH_UNSET] if it is unknown.
     *
     * @return The length of the source stream, or [C.LENGTH_UNSET].
     */
    val length: Long

    /**
     * Called when reading fails and the required retry position is different from the last position.
     * After setting the retry position it throws the given [Throwable].
     *
     * @param <E> Type of [Throwable] to be thrown.
     * @param position The required retry position.
     * @param e [Throwable] to be thrown.
     * @throws E The given [Throwable] object.
    </E> */
    @Throws(E::class)
    fun <E : Throwable?> setRetryPosition(position: Long, e: E)
}