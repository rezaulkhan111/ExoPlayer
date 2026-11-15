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
package com.google.android.exoplayer2.audio

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util

/**
 * Receives broadcast events indicating changes to the device's audio capabilities, notifying a
 * [Listener] when audio capability changes occur.
 */
class AudioCapabilitiesReceiver(
  context: Context,
  private val listener: Listener
) {

  /** Listener notified when audio capabilities change. */
  fun interface Listener {
    /**
     * Called when the audio capabilities change.
     *
     * @param audioCapabilities The current audio capabilities for the device.
     */
    fun onAudioCapabilitiesChanged(audioCapabilities: AudioCapabilities)
  }

  private val context: Context = context.applicationContext
  private val handler: Handler = Util.createHandlerForCurrentOrMainLooper()
  private val receiver: BroadcastReceiver? =
    if (Util.SDK_INT >= 21) HdmiAudioPlugBroadcastReceiver() else null
  private val externalSurroundSoundSettingObserver: ExternalSurroundSoundSettingObserver?

  internal var audioCapabilities: AudioCapabilities? = null
  private var registered = false

  init {
    val externalSurroundSoundUri = AudioCapabilities.getExternalSurroundSoundGlobalSettingUri()
    externalSurroundSoundSettingObserver =
      if (externalSurroundSoundUri != null) {
        ExternalSurroundSoundSettingObserver(
          handler, this.context.contentResolver, externalSurroundSoundUri
        )
      } else {
        null
      }
  }

  /**
   * Registers the receiver, meaning it will notify the listener when audio capability changes
   * occur. The current audio capabilities will be returned. It is important to call [unregister]
   * when the receiver is no longer required.
   *
   * @return The current audio capabilities for the device.
   */
  @Suppress("InlinedApi")
  fun register(): AudioCapabilities {
    if (registered) {
      return checkNotNull(audioCapabilities)!!
    }
    registered = true
    externalSurroundSoundSettingObserver?.register()
    var stickyIntent: Intent? = null
    if (receiver != null) {
      val intentFilter = IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG)
      stickyIntent = Util.registerReceiverNotExported(context, receiver, intentFilter, handler)
    }
    val capabilities = AudioCapabilities.getCapabilities(context, stickyIntent)
    audioCapabilities = capabilities
    return capabilities
  }

  /**
   * Unregisters the receiver, meaning it will no longer notify the listener when audio capability
   * changes occur.
   */
  fun unregister() {
    if (!registered) {
      return
    }
    audioCapabilities = null
    if (receiver != null) {
      context.unregisterReceiver(receiver)
    }
    externalSurroundSoundSettingObserver?.unregister()
    registered = false
  }

  private fun onNewAudioCapabilities(newAudioCapabilities: AudioCapabilities) {
    if (registered && newAudioCapabilities != audioCapabilities) {
      audioCapabilities = newAudioCapabilities
      listener.onAudioCapabilitiesChanged(newAudioCapabilities)
    }
  }

  private inner class HdmiAudioPlugBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (!isInitialStickyBroadcast) {
        onNewAudioCapabilities(AudioCapabilities.getCapabilities(context, intent))
      }
    }
  }

  private inner class ExternalSurroundSoundSettingObserver(
    handler: Handler,
    private val resolver: ContentResolver,
    private val settingUri: Uri
  ) : ContentObserver(handler) {

    fun register() {
      resolver.registerContentObserver(settingUri, /* notifyForDescendants= */ false, this)
    }

    fun unregister() {
      resolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
      onNewAudioCapabilities(AudioCapabilities.getCapabilities(context))
    }
  }
}
