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
package com.google.android.exoplayer2.util

import android.content.Context
import android.view.Surface
import com.google.android.exoplayer2.util.FrameProcessor.Factory
import com.google.android.exoplayer2.video.ColorInfo

/**
 * Interface for a frame processor that applies changes to individual video frames.
 *
 *
 * The changes are specified by [Effect] instances passed to [Factory.create].
 *
 *
 * Manages its input [Surface], which can be accessed via [.getInputSurface]. The
 * output [Surface] must be set by the caller using [ ][.setOutputSurfaceInfo].
 *
 *
 * The caller must [register][.registerInputFrame] input frames before rendering them
 * to the input [Surface].
 */
interface FrameProcessor {

    // TODO(b/243036513): Allow effects to be replaced.
    /** A factory for [FrameProcessor] instances.  */
    interface Factory {
        /**
         * Creates a new [FrameProcessor] instance.
         *
         * @param context A [Context].
         * @param listener A [Listener].
         * @param effects The [Effect] instances to apply to each frame.
         * @param debugViewProvider A [DebugViewProvider].
         * @param colorInfo The [ColorInfo] for input and output frames.
         * @param releaseFramesAutomatically If `true`, the [FrameProcessor] will render
         * output frames to the [output surface][.setOutputSurfaceInfo]
         * automatically as [FrameProcessor] is done processing them. If `false`, the
         * [FrameProcessor] will block until [.releaseOutputFrame] is called, to
         * render or drop the frame.
         * @return A new instance.
         * @throws FrameProcessingException If a problem occurs while creating the [     ].
         */
        @Throws(FrameProcessingException::class)
        fun create(
                context: Context?,
                listener: Listener?,
                effects: List<Effect?>?,
                debugViewProvider: DebugViewProvider?,
                colorInfo: ColorInfo?,
                releaseFramesAutomatically: Boolean): FrameProcessor?
    }

    /**
     * Listener for asynchronous frame processing events.
     *
     *
     * All listener methods must be called from the same thread.
     */
    interface Listener {
        /**
         * Called when the output size changes.
         *
         *
         * The output size is the frame size in pixels after applying all [ effects][Effect].
         *
         *
         * The output size may differ from the size specified using [ ][.setOutputSurfaceInfo].
         */
        fun onOutputSizeChanged(width: Int, height: Int)

        /**
         * Called when an output frame with the given `presentationTimeUs` becomes available.
         *
         * @param presentationTimeUs The presentation time of the frame, in microseconds.
         */
        fun onOutputFrameAvailable(presentationTimeUs: Long)

        /**
         * Called when an exception occurs during asynchronous frame processing.
         *
         *
         * If an error occurred, consuming and producing further frames will not work as expected and
         * the [FrameProcessor] should be released.
         */
        fun onFrameProcessingError(exception: FrameProcessingException?)

        /** Called after the [FrameProcessor] has produced its final output frame.  */
        fun onFrameProcessingEnded()
    }

    companion object {
        /**
         * Indicates the frame should be released immediately after [.releaseOutputFrame] is
         * invoked.
         */
        const val RELEASE_OUTPUT_FRAME_IMMEDIATELY: Long = -1

        /** Indicates the frame should be dropped after [.releaseOutputFrame] is invoked.  */
        const val DROP_OUTPUT_FRAME: Long = -2
    }

    /** Returns the input [Surface], where [FrameProcessor] consumes input frames from.  */
    fun getInputSurface(): Surface?

    /**
     * Sets information about the input frames.
     *
     *
     * The new input information is applied from the next frame [ registered][.registerInputFrame] onwards.
     *
     *
     * Pixels are expanded using the [FrameInfo.pixelWidthHeightRatio] so that the output
     * frames' pixels have a ratio of 1.
     *
     *
     * The caller should update [FrameInfo.streamOffsetUs] when switching input streams to
     * ensure that frame timestamps are always monotonically increasing.
     */
    fun setInputFrameInfo(inputFrameInfo: FrameInfo?)

    /**
     * Informs the `FrameProcessor` that a frame will be queued to its input surface.
     *
     *
     * Must be called before rendering a frame to the frame processor's input surface.
     *
     * @throws IllegalStateException If called after [.signalEndOfInput] or before [     ][.setInputFrameInfo].
     */
    fun registerInputFrame()

    /**
     * Returns the number of input frames that have been [registered][.registerInputFrame]
     * but not processed off the [input surface][.getInputSurface] yet.
     */
    fun getPendingInputFrameCount(): Int

    /**
     * Sets the output surface and supporting information. When output frames are released and not
     * dropped, they will be rendered to this output [SurfaceInfo].
     *
     *
     * The new output [SurfaceInfo] is applied from the next output frame rendered onwards.
     * If the output [SurfaceInfo] is `null`, the `FrameProcessor` will stop
     * rendering pending frames and resume rendering once a non-null [SurfaceInfo] is set.
     *
     *
     * If the dimensions given in [SurfaceInfo] do not match the [ ][Listener.onOutputSizeChanged] the frames
     * are resized before rendering to the surface and letter/pillar-boxing is applied.
     *
     *
     * The caller is responsible for tracking the lifecycle of the [SurfaceInfo.surface]
     * including calling this method with a new surface if it is destroyed. When this method returns,
     * the previous output surface is no longer being used and can safely be released by the caller.
     */
    fun setOutputSurfaceInfo(outputSurfaceInfo: SurfaceInfo?)

    /**
     * Releases the oldest unreleased output frame that has become [ ][Listener.onOutputFrameAvailable] at the given `releaseTimeNs`.
     *
     *
     * This will either render the output frame to the [output][.setOutputSurfaceInfo], or drop the frame, per `releaseTimeNs`.
     *
     *
     * This method must only be called if `releaseFramesAutomatically` was set to `false` using the [Factory] and should be called exactly once for each frame that becomes
     * [available][Listener.onOutputFrameAvailable].
     *
     *
     * The `releaseTimeNs` may be passed to [EGLExt.eglPresentationTimeANDROID]
     * depending on the implementation.
     *
     * @param releaseTimeNs The release time to use for the frame, in nanoseconds. The release time
     * can be before of after the current system time. Use [.DROP_OUTPUT_FRAME] to drop the
     * frame, or [.RELEASE_OUTPUT_FRAME_IMMEDIATELY] to release the frame immediately.
     */
    fun releaseOutputFrame(releaseTimeNs: Long)

    /**
     * Informs the `FrameProcessor` that no further input frames should be accepted.
     *
     * @throws IllegalStateException If called more than once.
     */
    fun signalEndOfInput()

    /**
     * Releases all resources.
     *
     *
     * If the frame processor is released before it has [ ][Listener.onFrameProcessingEnded], it will attempt to cancel processing any input frames
     * that have already become available. Input frames that become available after release are
     * ignored.
     *
     *
     * This method blocks until all resources are released or releasing times out.
     */
    fun release()
}