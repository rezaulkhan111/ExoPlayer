/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.media.AudioDeviceInfo
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.audio.AudioSink.SinkFormatSupport
import java.nio.ByteBuffer

/** An overridable [AudioSink] implementation forwarding all methods to another sink. */
open class ForwardingAudioSink(private val sink: AudioSink) : AudioSink {

  override fun setListener(listener: AudioSink.Listener?) {
    sink.setListener(listener)
  }

  override fun setPlayerId(playerId: PlayerId?) {
    sink.setPlayerId(playerId)
  }

  override fun supportsFormat(format: Format?): Boolean {
    return sink.supportsFormat(format)
  }

  @SinkFormatSupport
  override fun getFormatSupport(format: Format?): Int {
    return sink.getFormatSupport(format)
  }

  override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    return sink.getCurrentPositionUs(sourceEnded)
  }

  @Throws(AudioSink.ConfigurationException::class)
  override fun configure(
    inputFormat: Format?,
    specifiedBufferSize: Int,
    outputChannels: IntArray?
  ) {
    sink.configure(inputFormat, specifiedBufferSize, outputChannels)
  }

  override fun play() {
    sink.play()
  }

  override fun handleDiscontinuity() {
    sink.handleDiscontinuity()
  }

  @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
  override fun handleBuffer(
    buffer: ByteBuffer?,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int
  ): Boolean {
    return sink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
  }

  @Throws(AudioSink.WriteException::class)
  override fun playToEndOfStream() {
    sink.playToEndOfStream()
  }

  override fun isEnded(): Boolean {
    return sink.isEnded()
  }

  override fun hasPendingData(): Boolean {
    return sink.hasPendingData()
  }

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters?) {
    sink.setPlaybackParameters(playbackParameters)
  }

  override fun getPlaybackParameters(): PlaybackParameters? {
    return sink.getPlaybackParameters()
  }

  override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
    sink.setSkipSilenceEnabled(skipSilenceEnabled)
  }

  override fun getSkipSilenceEnabled(): Boolean {
    return sink.getSkipSilenceEnabled()
  }

  override fun setAudioAttributes(audioAttributes: AudioAttributes?) {
    sink.setAudioAttributes(audioAttributes)
  }

  override fun getAudioAttributes(): AudioAttributes? {
    return sink.getAudioAttributes()
  }

  override fun setAudioSessionId(audioSessionId: Int) {
    sink.setAudioSessionId(audioSessionId)
  }

  override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo?) {
    sink.setAuxEffectInfo(auxEffectInfo)
  }

  @RequiresApi(23)
  override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
    sink.setPreferredDevice(audioDeviceInfo)
  }

  override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
    sink.setOutputStreamOffsetUs(outputStreamOffsetUs)
  }

  override fun enableTunnelingV21() {
    sink.enableTunnelingV21()
  }

  override fun disableTunneling() {
    sink.disableTunneling()
  }

  override fun setVolume(volume: Float) {
    sink.setVolume(volume)
  }

  override fun pause() {
    sink.pause()
  }

  override fun flush() {
    sink.flush()
  }

  override fun experimentalFlushWithoutAudioTrackRelease() {
    sink.experimentalFlushWithoutAudioTrackRelease()
  }

  override fun reset() {
    sink.reset()
  }
}
