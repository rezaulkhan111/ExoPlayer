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

import com.google.android.exoplayer2.util.Util
import kotlin.math.max

/**
 * Maintains decoder event counts, for debugging purposes only.
 *
 * <p>Counters should be written from the playback thread only. Counters may be read from any
 * thread. To ensure that the counter values are made visible across threads, users of this class
 * should invoke [ensureUpdated] prior to reading and after writing.
 */
class DecoderCounters {

  /** The number of times a decoder has been initialized. */
  @JvmField var decoderInitCount: Int = 0

  /** The number of times a decoder has been released. */
  @JvmField var decoderReleaseCount: Int = 0

  /** The number of input buffers queued to the decoder. */
  @JvmField var queuedInputBufferCount: Int = 0

  /**
   * The number of skipped input buffers.
   *
   * <p>A skipped input buffer is an input buffer that was deliberately not queued to the decoder.
   */
  @JvmField var skippedInputBufferCount: Int = 0

  /** The number of rendered output buffers. */
  @JvmField var renderedOutputBufferCount: Int = 0

  /**
   * The number of skipped output buffers.
   *
   * <p>A skipped output buffer is an output buffer that was deliberately not rendered. This
   * includes buffers that were never dequeued from the decoder and instead skipped while 'inside'
   * the codec due to a flush.
   */
  @JvmField var skippedOutputBufferCount: Int = 0

  /**
   * The number of dropped buffers.
   *
   * <p>A dropped buffer is a buffer that was supposed to be decoded/rendered, but was instead
   * dropped because it could not be rendered in time.
   *
   * <p>This includes all of [droppedInputBufferCount] in addition to buffers dropped after
   * being queued to the decoder.
   */
  @JvmField var droppedBufferCount: Int = 0

  /**
   * The number of input buffers dropped.
   *
   * <p>A dropped input buffer is a buffer that was not queued to the decoder because it would not
   * be rendered in time.
   */
  @JvmField var droppedInputBufferCount: Int = 0

  /**
   * The maximum number of dropped buffers without an interleaving rendered output buffer.
   *
   * <p>Skipped buffers are ignored for the purposes of calculating this value.
   */
  @JvmField var maxConsecutiveDroppedBufferCount: Int = 0

  /**
   * The number of times all buffers to a keyframe were dropped.
   *
   * <p>Each time buffers to a keyframe are dropped:
   *
   * <ul>
   *   <li>This counter is incremented by one.
   *   <li>[droppedInputBufferCount] is incremented by the number of buffers dropped from the
   *       source to advance to the keyframe.
   *   <li>[droppedBufferCount] is incremented by the sum of the number of buffers dropped
   *       from the source to advance to the keyframe and the number of buffers 'inside' the
   *       decoder.
   * </ul>
   */
  @JvmField var droppedToKeyframeCount: Int = 0

  /**
   * The sum of the video frame processing offsets in microseconds.
   *
   * <p>The processing offset for a video frame is the difference between the time at which the
   * frame became available to render, and the time at which it was scheduled to be rendered. A
   * positive value indicates the frame became available early enough, whereas a negative value
   * indicates that the frame wasn't available until after the time at which it should have been
   * rendered.
   *
   * <p>Note: Use [addVideoFrameProcessingOffset] to update this field instead of
   * updating it directly.
   */
  @JvmField var totalVideoFrameProcessingOffsetUs: Long = 0

  /**
   * The number of video frame processing offsets added.
   *
   * <p>Note: Use [addVideoFrameProcessingOffset] to update this field instead of
   * updating it directly.
   */
  @JvmField var videoFrameProcessingOffsetCount: Int = 0

  /**
   * Should be called to ensure counter values are made visible across threads. The playback thread
   * should call this method after updating the counter values. Any other thread should call this
   * method before reading the counters.
   */
  @Synchronized
  fun ensureUpdated() {
    // Do nothing. The use of synchronized ensures a memory barrier should another thread also
    // call this method.
  }

  /**
   * Merges the counts from `other` into this instance.
   *
   * @param other The [DecoderCounters] to merge into this instance.
   */
  fun merge(other: DecoderCounters) {
    decoderInitCount += other.decoderInitCount
    decoderReleaseCount += other.decoderReleaseCount
    queuedInputBufferCount += other.queuedInputBufferCount
    skippedInputBufferCount += other.skippedInputBufferCount
    renderedOutputBufferCount += other.renderedOutputBufferCount
    skippedOutputBufferCount += other.skippedOutputBufferCount
    droppedBufferCount += other.droppedBufferCount
    droppedInputBufferCount += other.droppedInputBufferCount
    maxConsecutiveDroppedBufferCount =
      max(maxConsecutiveDroppedBufferCount, other.maxConsecutiveDroppedBufferCount)
    droppedToKeyframeCount += other.droppedToKeyframeCount
    addVideoFrameProcessingOffsets(
      other.totalVideoFrameProcessingOffsetUs, other.videoFrameProcessingOffsetCount
    )
  }

  /**
   * Adds a video frame processing offset to [totalVideoFrameProcessingOffsetUs] and
   * increases [videoFrameProcessingOffsetCount] by one.
   *
   * <p>Convenience method to ensure both fields are updated when adding a single offset.
   *
   * @param processingOffsetUs The video frame processing offset in microseconds.
   */
  fun addVideoFrameProcessingOffset(processingOffsetUs: Long) {
    addVideoFrameProcessingOffsets(processingOffsetUs, /* count= */ 1)
  }

  private fun addVideoFrameProcessingOffsets(totalProcessingOffsetUs: Long, count: Int) {
    totalVideoFrameProcessingOffsetUs += totalProcessingOffsetUs
    videoFrameProcessingOffsetCount += count
  }

  override fun toString(): String {
    return Util.formatInvariant(
      "DecoderCounters {\n "
        + "decoderInits=%s,\n "
        + "decoderReleases=%s\n "
        + "queuedInputBuffers=%s\n "
        + "skippedInputBuffers=%s\n "
        + "renderedOutputBuffers=%s\n "
        + "skippedOutputBuffers=%s\n "
        + "droppedBuffers=%s\n "
        + "droppedInputBuffers=%s\n "
        + "maxConsecutiveDroppedBuffers=%s\n "
        + "droppedToKeyframeEvents=%s\n "
        + "totalVideoFrameProcessingOffsetUs=%s\n "
        + "videoFrameProcessingOffsetCount=%s\n}",
      decoderInitCount,
      decoderReleaseCount,
      queuedInputBufferCount,
      skippedInputBufferCount,
      renderedOutputBufferCount,
      skippedOutputBufferCount,
      droppedBufferCount,
      droppedInputBufferCount,
      maxConsecutiveDroppedBufferCount,
      droppedToKeyframeCount,
      totalVideoFrameProcessingOffsetUs,
      videoFrameProcessingOffsetCount
    )
  }
}
