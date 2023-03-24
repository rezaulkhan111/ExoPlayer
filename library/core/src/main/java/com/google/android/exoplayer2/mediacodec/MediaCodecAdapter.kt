/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.view.Surface
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C.VideoScalingMode
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.decoder.CryptoInfo
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Abstracts [MediaCodec] operations.
 *
 *
 * `MediaCodecAdapter` offers a common interface to interact with a [MediaCodec]
 * regardless of the mode the [MediaCodec] is operating in.
 */
interface MediaCodecAdapter {
    /** Configuration parameters for a [MediaCodecAdapter].  */
    class Configuration {

        /**
         * Creates a configuration for audio decoding.
         *
         * @param codecInfo See [.codecInfo].
         * @param mediaFormat See [.mediaFormat].
         * @param format See [.format].
         * @param crypto See [.crypto].
         * @return The created instance.
         */
        fun createForAudioDecoding(
                codecInfo: MediaCodecInfo,
                mediaFormat: MediaFormat,
                format: Format,
                crypto: MediaCrypto?): Configuration {
            return Configuration(
                    codecInfo, mediaFormat, format,  /* surface= */null, crypto,  /* flags= */0)
        }

        /**
         * Creates a configuration for video decoding.
         *
         * @param codecInfo See [.codecInfo].
         * @param mediaFormat See [.mediaFormat].
         * @param format See [.format].
         * @param surface See [.surface].
         * @param crypto See [.crypto].
         * @return The created instance.
         */
        fun createForVideoDecoding(
                codecInfo: MediaCodecInfo,
                mediaFormat: MediaFormat,
                format: Format,
                surface: Surface?,
                crypto: MediaCrypto?): Configuration {
            return Configuration(codecInfo, mediaFormat, format, surface, crypto,  /* flags= */0)
        }

        /** Information about the [MediaCodec] being configured.  */
        var codecInfo: MediaCodecInfo? = null

        /** The [MediaFormat] for which the codec is being configured.  */
        var mediaFormat: MediaFormat? = null

        /** The [Format] for which the codec is being configured.  */
        var format: Format? = null

        /**
         * For video decoding, the output where the object will render the decoded frames. This must be
         * null if the codec is not a video decoder, or if it is configured for [ByteBuffer]
         * output.
         */
        var surface: Surface? = null

        /** For DRM protected playbacks, a [MediaCrypto] to use for decryption.  */
        var crypto: MediaCrypto? = null

        /** See [MediaCodec.configure].  */
        var flags = 0

        private constructor(
                codecInfo: MediaCodecInfo,
                mediaFormat: MediaFormat,
                format: Format,
                surface: Surface?,
                crypto: MediaCrypto?,
                flags: Int) {
            this.codecInfo = codecInfo
            this.mediaFormat = mediaFormat
            this.format = format
            this.surface = surface
            this.crypto = crypto
            this.flags = flags
        }
    }

    /** A factory for [MediaCodecAdapter] instances.  */
    interface Factory {
        companion object {
            /** Default factory used in most cases.  */
            @JvmField
            val DEFAULT: Factory = DefaultMediaCodecAdapterFactory()
        }

        /** Creates a [MediaCodecAdapter] instance.  */
        @Throws(IOException::class)
        fun createAdapter(configuration: Configuration?): MediaCodecAdapter?
    }

    /**
     * Listener to be called when an output frame has rendered on the output surface.
     *
     * @see MediaCodec.OnFrameRenderedListener
     */
    interface OnFrameRenderedListener {
        fun onFrameRendered(codec: MediaCodecAdapter?, presentationTimeUs: Long, nanoTime: Long)
    }

    /**
     * Returns the next available input buffer index from the underlying [MediaCodec] or [ ][MediaCodec.INFO_TRY_AGAIN_LATER] if no such buffer exists.
     *
     * @throws IllegalStateException If the underlying [MediaCodec] raised an error.
     */
    fun dequeueInputBufferIndex(): Int

    /**
     * Returns the next available output buffer index from the underlying [MediaCodec]. If the
     * next available output is a MediaFormat change, it will return [ ][MediaCodec.INFO_OUTPUT_FORMAT_CHANGED] and you should call [.getOutputFormat] to get
     * the format. If there is no available output, this method will return [ ][MediaCodec.INFO_TRY_AGAIN_LATER].
     *
     * @throws IllegalStateException If the underlying [MediaCodec] raised an error.
     */
    fun dequeueOutputBufferIndex(bufferInfo: MediaCodec.BufferInfo?): Int

    /**
     * Gets the [MediaFormat] that was output from the [MediaCodec].
     *
     *
     * Call this method if a previous call to [.dequeueOutputBufferIndex] returned [ ][MediaCodec.INFO_OUTPUT_FORMAT_CHANGED].
     */
    fun getOutputFormat(): MediaFormat?

    /**
     * Returns a writable ByteBuffer object for a dequeued input buffer index.
     *
     * @see MediaCodec.getInputBuffer
     */
    fun getInputBuffer(index: Int): ByteBuffer?

    /**
     * Returns a read-only ByteBuffer for a dequeued output buffer index.
     *
     * @see MediaCodec.getOutputBuffer
     */
    fun getOutputBuffer(index: Int): ByteBuffer?

    /**
     * Submit an input buffer for decoding.
     *
     *
     * The `index` must be an input buffer index that has been obtained from a previous call
     * to [.dequeueInputBufferIndex].
     *
     * @see MediaCodec.queueInputBuffer
     */
    fun queueInputBuffer(index: Int, offset: Int, size: Int, presentationTimeUs: Long, flags: Int)

    /**
     * Submit an input buffer that is potentially encrypted for decoding.
     *
     *
     * The `index` must be an input buffer index that has been obtained from a previous call
     * to [.dequeueInputBufferIndex].
     *
     *
     * This method behaves like [MediaCodec.queueSecureInputBuffer], with the difference that
     * `info` is of type [CryptoInfo] and not [android.media.MediaCodec.CryptoInfo].
     *
     * @see MediaCodec.queueSecureInputBuffer
     */
    fun queueSecureInputBuffer(
            index: Int, offset: Int, info: CryptoInfo?, presentationTimeUs: Long, flags: Int)

    /**
     * Returns the buffer to the [MediaCodec]. If the [MediaCodec] was configured with an
     * output surface, setting `render` to `true` will first send the buffer to the output
     * surface. The surface will release the buffer back to the codec once it is no longer
     * used/displayed.
     *
     * @see MediaCodec.releaseOutputBuffer
     */
    fun releaseOutputBuffer(index: Int, render: Boolean)

    /**
     * Updates the output buffer's surface timestamp and sends it to the [MediaCodec] to render
     * it on the output surface. If the [MediaCodec] is not configured with an output surface,
     * this call will simply return the buffer to the [MediaCodec].
     *
     * @see MediaCodec.releaseOutputBuffer
     */
    @RequiresApi(21)
    fun releaseOutputBuffer(index: Int, renderTimeStampNs: Long)

    /** Flushes the adapter and the underlying [MediaCodec].  */
    fun flush()

    /** Releases the adapter and the underlying [MediaCodec].  */
    fun release()

    /**
     * Registers a callback to be invoked when an output frame is rendered on the output surface.
     *
     * @see MediaCodec.setOnFrameRenderedListener
     */
    @RequiresApi(23)
    fun setOnFrameRenderedListener(listener: OnFrameRenderedListener?, handler: Handler?)

    /**
     * Dynamically sets the output surface of a [MediaCodec].
     *
     * @see MediaCodec.setOutputSurface
     */
    @RequiresApi(23)
    fun setOutputSurface(surface: Surface?)

    /**
     * Communicate additional parameter changes to the [MediaCodec] instance.
     *
     * @see MediaCodec.setParameters
     */
    @RequiresApi(19)
    fun setParameters(params: Bundle?)

    /**
     * Specifies the scaling mode to use, if a surface was specified when the codec was created.
     *
     * @see MediaCodec.setVideoScalingMode
     */
    fun setVideoScalingMode(@VideoScalingMode scalingMode: Int)

    /** Whether the adapter needs to be reconfigured before it is used.  */
    fun needsReconfiguration(): Boolean

    /**
     * Returns metrics data about the current codec instance.
     *
     * @see MediaCodec.getMetrics
     */
    @RequiresApi(26)
    fun getMetrics(): PersistableBundle?
}