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
package com.google.android.exoplayer2.util

import android.annotation.SuppressLint
import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.*
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.Util.registerReceiverNotExported
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Observer for network type changes.
 *
 *
 * [Registered][.register] listeners are informed at registration and whenever the network
 * type changes.
 *
 *
 * The current network type can also be [queried][.getNetworkType] without registration.
 */
class NetworkTypeObserver {
    /** A listener for network type changes.  */
    open interface Listener {
        /**
         * Called when the network type changed or when the listener is first registered.
         *
         *
         * This method is always called on the main thread.
         */
        fun onNetworkTypeChanged(@C.NetworkType networkType: Int)
    }

    private var staticInstance: NetworkTypeObserver? = null

    private var mainHandler: Handler? = null

    // This class needs to hold weak references as it doesn't require listeners to unregister.
    private var listeners: CopyOnWriteArrayList<WeakReference<Listener?>>? = null
    private var networkTypeLock: Any? = null

    @GuardedBy("networkTypeLock")
    @C.NetworkType
    private var networkType = 0

    /**
     * Returns a network type observer instance.
     *
     * @param context A [Context].
     */
    @Synchronized
    fun getInstance(context: Context): NetworkTypeObserver? {
        if (staticInstance == null) {
            staticInstance = NetworkTypeObserver(context)
        }
        return staticInstance
    }

    /** Resets the network type observer for tests.  */
    @VisibleForTesting
    @Synchronized
    fun resetForTests() {
        staticInstance = null
    }

    private constructor(context: Context) {
        mainHandler = Handler(Looper.getMainLooper())
        listeners = CopyOnWriteArrayList()
        networkTypeLock = Any()
        networkType = C.NETWORK_TYPE_UNKNOWN
        val filter = IntentFilter()
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiverNotExported(context, Receiver(), filter)
    }

    /**
     * Registers a listener.
     *
     *
     * The current network type will be reported to the listener after registration.
     *
     * @param listener The [Listener].
     */
    fun register(listener: Listener) {
        removeClearedReferences()
        listeners!!.add(WeakReference(listener))
        // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
        // we were to register a separate broadcast receiver for each listener).
        mainHandler!!.post { listener.onNetworkTypeChanged(getNetworkType()) }
    }

    /** Returns the current network type.  */
    @C.NetworkType
    fun getNetworkType(): Int {
        synchronized(networkTypeLock!!) { return networkType }
    }

    private fun removeClearedReferences() {
        for (listenerReference in listeners!!) {
            if (listenerReference.get() == null) {
                listeners!!.remove(listenerReference)
            }
        }
    }

    fun updateNetworkType(@C.NetworkType networkType: Int) {
        synchronized(networkTypeLock!!) {
            if (this.networkType == networkType) {
                return
            }
            this.networkType = networkType
        }
        for (listenerReference in listeners!!) {
            val listener = listenerReference.get()
            listener?.onNetworkTypeChanged(networkType) ?: listeners!!.remove(listenerReference)
        }
    }

    companion object {
        @C.NetworkType
        private fun getNetworkTypeFromConnectivityManager(context: Context): Int {
            val networkInfo: NetworkInfo
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    ?: return C.NETWORK_TYPE_UNKNOWN
            try {
                networkInfo = connectivityManager.activeNetworkInfo!!
            } catch (e: SecurityException) {
                // Expected if permission was revoked.
                return C.NETWORK_TYPE_UNKNOWN
            }
            return if (networkInfo == null || !networkInfo.isConnected) {
                C.NETWORK_TYPE_OFFLINE
            } else when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> C.NETWORK_TYPE_WIFI
                ConnectivityManager.TYPE_WIMAX -> C.NETWORK_TYPE_4G
                ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_MOBILE_DUN, ConnectivityManager.TYPE_MOBILE_HIPRI -> getMobileNetworkType(networkInfo)
                ConnectivityManager.TYPE_ETHERNET -> C.NETWORK_TYPE_ETHERNET
                else -> C.NETWORK_TYPE_OTHER
            }
        }

        @C.NetworkType
        private fun getMobileNetworkType(networkInfo: NetworkInfo): Int {
            return when (networkInfo.subtype) {
                TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> C.NETWORK_TYPE_2G
                TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> C.NETWORK_TYPE_3G
                TelephonyManager.NETWORK_TYPE_LTE -> C.NETWORK_TYPE_4G
                TelephonyManager.NETWORK_TYPE_NR -> if (Util.SDK_INT >= 29) C.NETWORK_TYPE_5G_SA else C.NETWORK_TYPE_UNKNOWN
                TelephonyManager.NETWORK_TYPE_IWLAN -> C.NETWORK_TYPE_WIFI
                TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_UNKNOWN -> C.NETWORK_TYPE_CELLULAR_UNKNOWN
                else -> C.NETWORK_TYPE_CELLULAR_UNKNOWN
            }
        }
    }

    private class Receiver : BroadcastReceiver() {
        @SuppressLint("NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            @C.NetworkType val networkType: Int = getNetworkTypeFromConnectivityManager(context)
            if (Util.SDK_INT >= 31 && networkType == C.NETWORK_TYPE_4G) {
                // Delay update of the network type to check whether this is actually 5G-NSA.
                Api31.disambiguate4gAnd5gNsa(context,  /* instance= */this@NetworkTypeObserver)
            } else {
                updateNetworkType(networkType)
            }
        }
    }

    @RequiresApi(31)
    private object Api31 {
        fun disambiguate4gAnd5gNsa(context: Context, instance: NetworkTypeObserver) {
            try {
                val telephonyManager = checkNotNull(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                val callback = DisplayInfoCallback(instance)
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                // We are only interested in the initial response with the current state, so unregister
                // the listener immediately.
                telephonyManager.unregisterTelephonyCallback(callback)
            } catch (e: RuntimeException) {
                // Ignore problems with listener registration and keep reporting as 4G.
                instance.updateNetworkType(C.NETWORK_TYPE_4G)
            }
        }

        private class DisplayInfoCallback(private val instance: NetworkTypeObserver) : TelephonyCallback(), DisplayInfoListener {
            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                val overrideNetworkType = telephonyDisplayInfo.overrideNetworkType
                val is5gNsa = overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                instance.updateNetworkType(if (is5gNsa) C.NETWORK_TYPE_5G_NSA else C.NETWORK_TYPE_4G)
            }
        }
    }
}