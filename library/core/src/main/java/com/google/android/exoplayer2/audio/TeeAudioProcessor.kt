/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.audio

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio processor that outputs its input unmodified and also outputs its input to a given sink.
 * This is intended to be used for diagnostics and debugging.
 *
 * <p>This audio processor can be inserted into the audio processor chain to access audio data
 * before/after particular processing steps have been applied. For example, to get audio output
 * after playback speed adjustment and silence skipping have been applied it is necessary to pass a
 * custom [AudioProcessorChain] when creating the audio sink, and include this audio processor
 * after all other audio processors.
 */
class TeeAudioProcessor(audioBufferSink: AudioBufferSink) : BaseAudioProcessor() {

  /** A sink for audio buffers handled by the audio processor. */
  interface AudioBufferSink {

    /** Called when the audio processor is flushed with a format of subsequent input. */
    fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int)

    /**
     * Called when data is written to the audio processor.
     *
     * @param buffer A read-only buffer containing input which the audio processor will handle.
     */
    fun handleBuffer(buffer: ByteBuffer)
  }

  private val audioBufferSink: AudioBufferSink = Assertions.checkNotNull(audioBufferSink)!!

  @CanIgnoreReturnValue
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    // This processor is always active (if passed to the sink) and outputs its input.
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null) {
      return
    }
    val remaining = inputBuffer.remaining()
    if (remaining == 0) {
      return
    }
    audioBufferSink.handleBuffer(inputBuffer.asReadOnlyBuffer())
    replaceOutputBuffer(remaining).put(inputBuffer).flip()
  }

  override fun onFlush() {
    flushSinkIfActive()
  }

  override fun onQueueEndOfStream() {
    flushSinkIfActive()
  }

  override fun onReset() {
    flushSinkIfActive()
  }

  private fun flushSinkIfActive() {
    if (isActive()) {
      audioBufferSink.flush(
        inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.encoding
      )
    }
  }

  /**
   * A sink for audio buffers that writes output audio as .wav files with a given path prefix. When
   * new audio data is handled after flushing the audio processor, a counter is incremented and its
   * value is appended to the output file name.
   *
   * <p>Note: if writing to external storage it's necessary to grant the `WRITE_EXTERNAL_STORAGE`
   * permission.
   */
  class WavFileAudioBufferSink(
    private val outputFileNamePrefix: String
  ) : AudioBufferSink {

    private val scratchBuffer: ByteArray = ByteArray(1024)
    private val scratchByteBuffer: ByteBuffer =
      ByteBuffer.wrap(scratchBuffer).order(ByteOrder.LITTLE_ENDIAN)

    private var sampleRateHz: Int = 0
    private var channelCount: Int = 0
    private var encoding: Int = C.ENCODING_INVALID
    private var randomAccessFile: RandomAccessFile? = null
    private var counter: Int = 0
    private var bytesWritten: Int = 0

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
      try {
        reset()
      } catch (e: IOException) {
        Log.e(TAG, "Error resetting", e)
      }
      this.sampleRateHz = sampleRateHz
      this.channelCount = channelCount
      this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
      try {
        maybePrepareFile()
        writeBuffer(buffer)
      } catch (e: IOException) {
        Log.e(TAG, "Error writing data", e)
      }
    }

    @Throws(IOException::class)
    private fun maybePrepareFile() {
      if (randomAccessFile != null) {
        return
      }
      val randomAccessFile = RandomAccessFile(getNextOutputFileName(), "rw")
      writeFileHeader(randomAccessFile)
      this.randomAccessFile = randomAccessFile
      bytesWritten = HEADER_LENGTH
    }

    @Throws(IOException::class)
    private fun writeFileHeader(randomAccessFile: RandomAccessFile) {
      // Write the start of the header as big endian data.
      randomAccessFile.writeInt(WavUtil.RIFF_FOURCC)
      randomAccessFile.writeInt(-1)
      randomAccessFile.writeInt(WavUtil.WAVE_FOURCC)
      randomAccessFile.writeInt(WavUtil.FMT_FOURCC)

      // Write the rest of the header as little endian data.
      scratchByteBuffer.clear()
      scratchByteBuffer.putInt(16)
      scratchByteBuffer.putShort(WavUtil.getTypeForPcmEncoding(encoding).toShort())
      scratchByteBuffer.putShort(channelCount.toShort())
      scratchByteBuffer.putInt(sampleRateHz)
      val bytesPerSample = Util.getPcmFrameSize(encoding, channelCount)
      scratchByteBuffer.putInt(bytesPerSample * sampleRateHz)
      scratchByteBuffer.putShort(bytesPerSample.toShort())
      scratchByteBuffer.putShort((8 * bytesPerSample / channelCount).toShort())
      randomAccessFile.write(scratchBuffer, 0, scratchByteBuffer.position())

      // Write the start of the data chunk as big endian data.
      randomAccessFile.writeInt(WavUtil.DATA_FOURCC)
      randomAccessFile.writeInt(-1)
    }

    @Throws(IOException::class)
    private fun writeBuffer(buffer: ByteBuffer) {
      val randomAccessFile = Assertions.checkNotNull(this.randomAccessFile)!!
      while (buffer.hasRemaining()) {
        val bytesToWrite = minOf(buffer.remaining(), scratchBuffer.size)
        buffer.get(scratchBuffer, 0, bytesToWrite)
        randomAccessFile.write(scratchBuffer, 0, bytesToWrite)
        bytesWritten += bytesToWrite
      }
    }

    @Throws(IOException::class)
    private fun reset() {
      val randomAccessFile = this.randomAccessFile ?: return

      try {
        scratchByteBuffer.clear()
        scratchByteBuffer.putInt(bytesWritten - 8)
        randomAccessFile.seek(FILE_SIZE_MINUS_8_OFFSET.toLong())
        randomAccessFile.write(scratchBuffer, 0, 4)

        scratchByteBuffer.clear()
        scratchByteBuffer.putInt(bytesWritten - 44)
        randomAccessFile.seek(FILE_SIZE_MINUS_44_OFFSET.toLong())
        randomAccessFile.write(scratchBuffer, 0, 4)
      } catch (e: IOException) {
        // The file may still be playable, so just log a warning.
        Log.w(TAG, "Error updating file size", e)
      }

      try {
        randomAccessFile.close()
      } finally {
        this.randomAccessFile = null
      }
    }

    private fun getNextOutputFileName(): String {
      return Util.formatInvariant("%s-%04d.wav", outputFileNamePrefix, counter++)
    }

    companion object {
      private const val TAG = "WaveFileAudioBufferSink"

      private const val FILE_SIZE_MINUS_8_OFFSET = 4
      private const val FILE_SIZE_MINUS_44_OFFSET = 40
      private const val HEADER_LENGTH = 44
    }
  }
}
