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

import android.content.*
import android.net.ConnectivityManagerimport

android.net.NetworkInfoimport android.os.*import android.telephony.TelephonyCallbackimport

android.telephony.TelephonyCallback.DisplayInfoListenerimport android.telephony.TelephonyDisplayInfoimport android.telephony.TelephonyManagerimport androidx.annotation .*import com.google.android.exoplayer2.*
import java.lang.ref.WeakReferenceimport

java.util.concurrent.CopyOnWriteArrayList
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
class NetworkTypeObserver private constructor(context: Context) {
    /** A listener for network type changes.  */
    open interface Listener {
        /**
         * Called when the network type changed or when the listener is first registered.
         *
         *
         * This method is always called on the main thread.
         */
        fun onNetworkTypeChanged(networkType: @C.NetworkType Int)
    }

    private val mainHandler: Handler

    // This class needs to hold weak references as it doesn't require listeners to unregister.
    private val listeners: CopyOnWriteArrayList<WeakReference<Listener?>>
    private val networkTypeLock: Any

    @GuardedBy("networkTypeLock")
    private var networkType: @C.NetworkType Int

    init {
        mainHandler = Handler(Looper.getMainLooper())
        listeners = CopyOnWriteArrayList()
        networkTypeLock = Any()
        networkType = C.NETWORK_TYPE_UNKNOWN
        val filter: IntentFilter = IntentFilter()
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        Util.registerReceiverNotExported(context, Receiver(), filter)
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
        listeners.add(WeakReference(listener))
        // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
        // we were to register a separate broadcast receiver for each listener).
        mainHandler.post(Runnable({ listener.onNetworkTypeChanged(getNetworkType()) }))
    }

    /** Returns the current network type.  */
    fun getNetworkType(): @C.NetworkType Int {
        synchronized(networkTypeLock, { return networkType })
    }

    private fun removeClearedReferences() {
        for (listenerReference: WeakReference<Listener?> in listeners) {
            if (listenerReference.get() == null) {
                listeners.remove(listenerReference)
            }
        }
    }

    private fun updateNetworkType(networkType: @C.NetworkType Int) {
        synchronized(networkTypeLock, {
            if (this.networkType == networkType) {
                return
            }
            this.networkType = networkType
        })
        for (listenerReference: WeakReference<Listener?> in listeners) {
            val listener: Listener? = listenerReference.get()
            if (listener != null) {
                listener.onNetworkTypeChanged(networkType)
            } else {
                listeners.remove(listenerReference)
            }
        }
    }

    private inner class Receiver constructor() : BroadcastReceiver() {
        public override fun onReceive(context: Context, intent: Intent) {
            val networkType: @C.NetworkType Int = getNetworkTypeFromConnectivityManager(context)
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
                val telephonyManager: TelephonyManager? = Assertions.checkNotNull(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                val callback: DisplayInfoCallback = DisplayInfoCallback(instance)
                telephonyManager!!.registerTelephonyCallback(context.getMainExecutor(), callback)
                // We are only interested in the initial response with the current state, so unregister
                // the listener immediately.
                telephonyManager.unregisterTelephonyCallback(callback)
            } catch (e: RuntimeException) {
                // Ignore problems with listener registration and keep reporting as 4G.
                instance.updateNetworkType(C.NETWORK_TYPE_4G)
            }
        }

        private class DisplayInfoCallback constructor(private val instance: NetworkTypeObserver) : TelephonyCallback(), DisplayInfoListener {
            public override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                val overrideNetworkType: Int = telephonyDisplayInfo.getOverrideNetworkType()
                val is5gNsa: Boolean = (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                        ) || (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                        ) || (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED)
                instance.updateNetworkType(if (is5gNsa) C.NETWORK_TYPE_5G_NSA else C.NETWORK_TYPE_4G)
            }
        }
    }

    companion object {
        private var staticInstance: NetworkTypeObserver? = null

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

        private fun getNetworkTypeFromConnectivityManager(context: Context): @C.NetworkType Int {
            val networkInfo: NetworkInfo?
            val connectivityManager: ConnectivityManager? = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivityManager == null) {
                return C.NETWORK_TYPE_UNKNOWN
            }
            try {
                networkInfo = connectivityManager.getActiveNetworkInfo()
            } catch (e: SecurityException) {
                // Expected if permission was revoked.
                return C.NETWORK_TYPE_UNKNOWN
            }
            if (networkInfo == null || !networkInfo.isConnected()) {
                return C.NETWORK_TYPE_OFFLINE
            }
            when (networkInfo.getType()) {
                ConnectivityManager.TYPE_WIFI -> return C.NETWORK_TYPE_WIFI
                ConnectivityManager.TYPE_WIMAX -> return C.NETWORK_TYPE_4G
                ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_MOBILE_DUN, ConnectivityManager.TYPE_MOBILE_HIPRI -> return getMobileNetworkType(networkInfo)
                ConnectivityManager.TYPE_ETHERNET -> return C.NETWORK_TYPE_ETHERNET
                else -> return C.NETWORK_TYPE_OTHER
            }
        }

        private fun getMobileNetworkType(networkInfo: NetworkInfo): @C.NetworkType Int {
            when (networkInfo.getSubtype()) {
                TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> return C.NETWORK_TYPE_2G
                TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> return C.NETWORK_TYPE_3G
                TelephonyManager.NETWORK_TYPE_LTE -> return C.NETWORK_TYPE_4G
                TelephonyManager.NETWORK_TYPE_NR -> return if (Util.SDK_INT >= 29) C.NETWORK_TYPE_5G_SA else C.NETWORK_TYPE_UNKNOWN
                TelephonyManager.NETWORK_TYPE_IWLAN -> return C.NETWORK_TYPE_WIFI
                TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_UNKNOWN -> return C.NETWORK_TYPE_CELLULAR_UNKNOWN
                else -> return C.NETWORK_TYPE_CELLULAR_UNKNOWN
            }
        }
    }
}