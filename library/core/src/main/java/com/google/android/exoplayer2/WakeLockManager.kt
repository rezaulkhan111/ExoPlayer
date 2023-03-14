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
package com.google.android.exoplayer2

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import com.google.android.exoplayer2.util.Log.w

/**
 * Handles a [WakeLock].
 *
 *
 * The handling of wake locks requires the [android.Manifest.permission.WAKE_LOCK]
 * permission.
 */
/* package */
internal class WakeLockManager(context: Context) {
    private val powerManager: PowerManager?
    private var wakeLock: WakeLock? = null
    private var enabled = false
    private var stayAwake = false

    init {
        powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    /**
     * Sets whether to enable the acquiring and releasing of the [WakeLock].
     *
     *
     * By default, wake lock handling is not enabled. Enabling this will acquire the wake lock if
     * necessary. Disabling this will release the wake lock if it is held.
     *
     *
     * Enabling [WakeLock] requires the [android.Manifest.permission.WAKE_LOCK].
     *
     * @param enabled True if the player should handle a [WakeLock], false otherwise.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (wakeLock == null) {
                if (powerManager == null) {
                    w(TAG, "PowerManager is null, therefore not creating the WakeLock.")
                    return
                }
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                wakeLock.setReferenceCounted(false)
            }
        }
        this.enabled = enabled
        updateWakeLock()
    }

    /**
     * Sets whether to acquire or release the [WakeLock].
     *
     *
     * Please note this method requires wake lock handling to be enabled through setEnabled(boolean
     * enable) to actually have an impact on the [WakeLock].
     *
     * @param stayAwake True if the player should acquire the [WakeLock]. False if the player
     * should release.
     */
    fun setStayAwake(stayAwake: Boolean) {
        this.stayAwake = stayAwake
        updateWakeLock()
    }

    // WakelockTimeout suppressed because the time the wake lock is needed for is unknown (could be
    // listening to radio with screen off for multiple hours), therefore we can not determine a
    // reasonable timeout that would not affect the user.
    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock() {
        if (wakeLock == null) {
            return
        }
        if (enabled && stayAwake) {
            wakeLock!!.acquire()
        } else {
            wakeLock!!.release()
        }
    }

    companion object {
        private const val TAG = "WakeLockManager"
        private const val WAKE_LOCK_TAG = "ExoPlayer:WakeLockManager"
    }
}