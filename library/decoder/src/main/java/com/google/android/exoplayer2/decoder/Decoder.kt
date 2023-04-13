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
package com.google.android.exoplayer2.decoder

/**
 * A media decoder.
 *
 * @param <I> The type of buffer input to the decoder.
 * @param <O> The type of buffer output from the decoder.
 * @param <E> The type of exception thrown from the decoder.
</E></O></I> */
interface Decoder<I, O, E : DecoderException?> {

    /**
     * Returns the name of the decoder.
     *
     * @return The name of the decoder.
     */
    fun getName(): String?

    /**
     * Dequeues the next input buffer to be filled and queued to the decoder.
     *
     * @return The input buffer, which will have been cleared, or null if a buffer isn't available.
     * @throws E If a decoder error has occurred.
     */
    @Throws(E::class)
    fun dequeueInputBuffer(): I?

    /**
     * Queues an input buffer to the decoder.
     *
     * @param inputBuffer The input buffer.
     * @throws E If a decoder error has occurred.
     */
    @Throws(E::class)
    fun queueInputBuffer(inputBuffer: I)

    /**
     * Dequeues the next output buffer from the decoder.
     *
     * @return The output buffer, or null if an output buffer isn't available.
     * @throws E If a decoder error has occurred.
     */
    @Throws(E::class)
    fun dequeueOutputBuffer(): O?

    /**
     * Flushes the decoder. Ownership of dequeued input buffers is returned to the decoder. The caller
     * is still responsible for releasing any dequeued output buffers.
     */
    fun flush()

    /** Releases the decoder. Must be called when the decoder is no longer needed.  */
    fun release()
}