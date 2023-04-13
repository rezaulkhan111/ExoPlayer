/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.effect

import com.google.android.exoplayer2.effect.GlTextureProcessor.InputListener
import com.google.android.exoplayer2.effect.GlTextureProcessor.OutputListener
import com.google.android.exoplayer2.util.FrameProcessingException

/**
 * Processes frames from one OpenGL 2D texture to another.
 *
 *
 * The `GlTextureProcessor` consumes input frames it accepts via [ ][.queueInputFrame] and surrenders each texture back to the caller via its
 * [listener][InputListener.onInputFrameProcessed] once the texture's
 * contents have been processed.
 *
 *
 * The `GlTextureProcessor` produces output frames asynchronously and notifies its owner
 * when they are available via its [listener][OutputListener.onOutputFrameAvailable]. The `GlTextureProcessor` instance's owner must surrender the texture back
 * to the `GlTextureProcessor` via [.releaseOutputFrame] when it has
 * finished processing it.
 *
 *
 * `GlTextureProcessor` implementations can choose to produce output frames before
 * receiving input frames or process several input frames before producing an output frame. However,
 * `GlTextureProcessor` implementations cannot assume that they will receive more than one
 * input frame at a time, so they must process each input frame they accept even if they cannot
 * produce output yet.
 *
 *
 * The methods in this interface must be called on the thread that owns the parent OpenGL
 * context. If the implementation uses another OpenGL context, e.g., on another thread, it must
 * configure it to share data with the context of thread the interface methods are called on.
 */
interface GlTextureProcessor {
    /**
     * Listener for input-related frame processing events.
     *
     *
     * This listener can be called from any thread.
     */
    interface InputListener {
        /**
         * Called when the [GlTextureProcessor] is ready to accept another input frame.
         *
         *
         * For each time this method is called, [.queueInputFrame] can be
         * called once.
         */
        fun onReadyToAcceptInputFrame() {}

        /**
         * Called when the [GlTextureProcessor] has processed an input frame.
         *
         *
         * The implementation shall not assume the [GlTextureProcessor] is [ ][.onReadyToAcceptInputFrame] when this method is called.
         *
         * @param inputTexture The [TextureInfo] that was used to [     ][.queueInputFrame] the input frame.
         */
        fun onInputFrameProcessed(inputTexture: TextureInfo?) {}
    }

    /**
     * Listener for output-related frame processing events.
     *
     *
     * This listener can be called from any thread.
     */
    interface OutputListener {
        /**
         * Called when the [GlTextureProcessor] has produced an output frame.
         *
         *
         * After the listener's owner has processed the output frame, it must call [ ][.releaseOutputFrame]. The output frame should be released as soon as possible,
         * as there is no guarantee that the [GlTextureProcessor] will produce further output
         * frames before this output frame is released.
         *
         * @param outputTexture A [TextureInfo] describing the texture containing the output
         * frame.
         * @param presentationTimeUs The presentation timestamp of the output frame, in microseconds.
         */
        fun onOutputFrameAvailable(outputTexture: TextureInfo?, presentationTimeUs: Long) {}

        /**
         * Called when the [GlTextureProcessor] will not produce further output frames belonging
         * to the current output stream.
         */
        fun onCurrentOutputStreamEnded() {}
    }

    /**
     * Listener for frame processing errors.
     *
     *
     * This listener can be called from any thread.
     */
    interface ErrorListener {
        /**
         * Called when an exception occurs during asynchronous frame processing.
         *
         *
         * If an error occurred, consuming and producing further frames will not work as expected and
         * the [GlTextureProcessor] should be released.
         */
        fun onFrameProcessingError(e: FrameProcessingException?)
    }

    /** Sets the [InputListener].  */
    fun setInputListener(inputListener: InputListener?)

    /** Sets the [OutputListener].  */
    fun setOutputListener(outputListener: OutputListener?)

    /** Sets the [ErrorListener].  */
    fun setErrorListener(errorListener: ErrorListener?)

    /**
     * Processes an input frame if possible.
     *
     *
     * The `GlTextureProcessor` owns the accepted frame until it calls [ ][InputListener.onInputFrameProcessed]. The caller should not overwrite or release
     * the texture before the `GlTextureProcessor` has finished processing it.
     *
     *
     * This method must only be called when the `GlTextureProcessor` can [ ][InputListener.onReadyToAcceptInputFrame].
     *
     * @param inputTexture A [TextureInfo] describing the texture containing the input frame.
     * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
     */
    fun queueInputFrame(inputTexture: TextureInfo?, presentationTimeUs: Long)

    /**
     * Notifies the texture processor that the frame on the given output texture is no longer used and
     * can be overwritten.
     */
    fun releaseOutputFrame(outputTexture: TextureInfo?)

    /**
     * Notifies the `GlTextureProcessor` that no further input frames belonging to the current
     * input stream will be queued.
     *
     *
     * Input frames that are queued after this method is called belong to a different input stream,
     * so presentation timestamps may reset to start from a smaller presentation timestamp than the
     * last frame of the previous input stream.
     */
    fun signalEndOfCurrentInputStream()

    /**
     * Releases all resources.
     *
     * @throws FrameProcessingException If an error occurs while releasing resources.
     */
    @Throws(FrameProcessingException::class)
    fun release()
}