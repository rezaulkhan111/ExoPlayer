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
package com.google.android.exoplayer2

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import com.google.android.exoplayer2.util.Log.w

/**
 * Handles a [WifiLock]
 *
 *
 * The handling of wifi locks requires the [android.Manifest.permission.WAKE_LOCK]
 * permission.
 */
/* package */
internal class WifiLockManager(context: Context) {
    private val wifiManager: WifiManager?
    private var wifiLock: WifiLock? = null
    private var enabled = false
    private var stayAwake = false

    init {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Sets whether to enable the usage of a [WifiLock].
     *
     *
     * By default, wifi lock handling is not enabled. Enabling will acquire the wifi lock if
     * necessary. Disabling will release the wifi lock if held.
     *
     *
     * Enabling [WifiLock] requires the [android.Manifest.permission.WAKE_LOCK].
     *
     * @param enabled True if the player should handle a [WifiLock].
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled && wifiLock == null) {
            if (wifiManager == null) {
                w(TAG, "WifiManager is null, therefore not creating the WifiLock.")
                return
            }
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
            wifiLock.setReferenceCounted(false)
        }
        this.enabled = enabled
        updateWifiLock()
    }

    /**
     * Sets whether to acquire or release the [WifiLock].
     *
     *
     * The wifi lock will not be acquired unless handling has been enabled through [ ][.setEnabled].
     *
     * @param stayAwake True if the player should acquire the [WifiLock]. False if it should
     * release.
     */
    fun setStayAwake(stayAwake: Boolean) {
        this.stayAwake = stayAwake
        updateWifiLock()
    }

    private fun updateWifiLock() {
        if (wifiLock == null) {
            return
        }
        if (enabled && stayAwake) {
            wifiLock!!.acquire()
        } else {
            wifiLock!!.release()
        }
    }

    companion object {
        private const val TAG = "WifiLockManager"
        private const val WIFI_LOCK_TAG = "ExoPlayer:WifiLockManager"
    }
}