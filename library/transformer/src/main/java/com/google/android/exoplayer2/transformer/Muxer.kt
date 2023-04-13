/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer

import android.os.ParcelFileDescriptor
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.transformer.Muxer.Factory
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * Abstracts media muxing operations.
 *
 *
 * Query whether [sample MIME types][Factory.getSupportedSampleMimeTypes] are
 * supported and [add all tracks][.addTrack], then [ ][.writeSampleData] to mux samples. Once any
 * sample data has been written, it is not possible to add tracks. After writing all sample data,
 * [release][.release] the instance to finish writing to the output and return
 * any resources to the system.
 */
interface Muxer {

    /** Thrown when a muxing failure occurs.  */
    class MuxerException : Exception {
        /**
         * Creates an instance.
         *
         * @param message See [.getMessage].
         * @param cause See [.getCause].
         */
        constructor(message: String?, cause: Throwable?) : super(message, cause) {
        }
    }

    /** Factory for muxers.  */
    interface Factory {
        /**
         * Returns a new muxer writing to a file.
         *
         * @param path The path to the output file.
         * @throws IllegalArgumentException If the path is invalid.
         * @throws MuxerException If an error occurs opening the output file for writing.
         */
        @Throws(MuxerException::class)
        fun create(path: String?): Muxer?

        /**
         * Returns a new muxer writing to a file descriptor.
         *
         * @param parcelFileDescriptor A readable and writable [ParcelFileDescriptor] of the
         * output. The file referenced by this ParcelFileDescriptor should not be used before the
         * muxer is released. It is the responsibility of the caller to close the
         * ParcelFileDescriptor. This can be done after this method returns.
         * @throws IllegalArgumentException If the file descriptor is invalid.
         * @throws MuxerException If an error occurs opening the output file descriptor for writing.
         */
        @Throws(MuxerException::class)
        fun create(parcelFileDescriptor: ParcelFileDescriptor?): Muxer?

        /**
         * Returns the supported sample [MIME types][MimeTypes] for the given [ ].
         */
        fun getSupportedSampleMimeTypes(trackType: @TrackType Int): ImmutableList<String?>?
    }

    /**
     * Adds a track with the specified format, and returns its index (to be passed in subsequent calls
     * to [.writeSampleData]).
     *
     * @param format The [Format] of the track.
     * @throws MuxerException If the muxer encounters a problem while adding the track.
     */
    @Throws(MuxerException::class)
    fun addTrack(format: Format?): Int

    /**
     * Writes the specified sample.
     *
     * @param trackIndex The index of the track, previously returned by [.addTrack].
     * @param data A buffer containing the sample data to write to the container.
     * @param isKeyFrame Whether the sample is a key frame.
     * @param presentationTimeUs The presentation time of the sample in microseconds.
     * @throws MuxerException If the muxer fails to write the sample.
     */
    @Throws(MuxerException::class)
    fun writeSampleData(
        trackIndex: Int,
        data: ByteBuffer?,
        isKeyFrame: Boolean,
        presentationTimeUs: Long
    )

    /**
     * Finishes writing the output and releases any resources associated with muxing.
     *
     *
     * The muxer cannot be used anymore once this method has been called.
     *
     * @param forCancellation Whether the reason for releasing the resources is the transformation
     * cancellation.
     * @throws MuxerException If the muxer fails to finish writing the output and `forCancellation` is false.
     */
    @Throws(MuxerException::class)
    fun release(forCancellation: Boolean)

    /**
     * Returns the maximum delay allowed between output samples, in milliseconds, or [ ][C.TIME_UNSET] if there is no maximum.
     *
     *
     * This is the maximum delay between samples of any track. They can be of the same or of
     * different track types.
     *
     *
     * This value is used to abort the transformation when the maximum delay is reached. Note that
     * there is no guarantee that the transformation will be aborted exactly at that time.
     */
    fun getMaxDelayBetweenSamplesMs(): Long
}