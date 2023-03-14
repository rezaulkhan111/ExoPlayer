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
package com.google.android.exoplayer2.ext.cronet

import android.content.Context
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import java.util.*

/** Cronet utility methods.  */
object CronetUtil {
    private const val TAG = "CronetUtil"
    /**
     * Builds a [CronetEngine] suitable for use with [CronetDataSource]. When choosing a
     * [Cronet provider][CronetProvider] to build the [CronetEngine], disabled providers
     * are not considered. Neither are fallback providers, since it's more efficient to use [ ] than it is to use [CronetDataSource] with a fallback [ ].
     *
     *
     * Note that it's recommended for applications to create only one instance of [ ], so if your application already has an instance for performing other networking,
     * then that instance should be used and calling this method is unnecessary. See the [Android developer
 * guide](https://developer.android.com/guide/topics/connectivity/cronet/start) to learn more about using Cronet for network operations.
     *
     * @param context A context.
     * @param userAgent A default user agent, or `null` to use a default user agent of the
     * [CronetEngine].
     * @param preferGooglePlayServices Whether Cronet from Google Play Services should be preferred
     * over Cronet Embedded, if both are available.
     * @return The [CronetEngine], or `null` if no suitable engine could be built.
     */
    /**
     * Builds a [CronetEngine] suitable for use with [CronetDataSource]. When choosing a
     * [Cronet provider][CronetProvider] to build the [CronetEngine], disabled providers
     * are not considered. Neither are fallback providers, since it's more efficient to use [ ] than it is to use [CronetDataSource] with a fallback [ ].
     *
     *
     * Note that it's recommended for applications to create only one instance of [ ], so if your application already has an instance for performing other networking,
     * then that instance should be used and calling this method is unnecessary. See the [Android developer
 * guide](https://developer.android.com/guide/topics/connectivity/cronet/start) to learn more about using Cronet for network operations.
     *
     * @param context A context.
     * @return The [CronetEngine], or `null` if no suitable engine could be built.
     */
    @JvmStatic
    @JvmOverloads
    fun buildCronetEngine(
            context: Context?, userAgent: String? =  /* userAgent= */null, preferGooglePlayServices: Boolean =  /* preferGooglePlayServices= */false): CronetEngine? {
        val cronetProviders: List<CronetProvider> = ArrayList(CronetProvider.getAllProviders(context))
        // Remove disabled and fallback Cronet providers from list.
        for (i in cronetProviders.indices.reversed()) {
            if (!cronetProviders[i].isEnabled || CronetProvider.PROVIDER_NAME_FALLBACK == cronetProviders[i].name) {
                cronetProviders.removeAt(i)
            }
        }
        // Sort remaining providers by type and version.
        val providerComparator = CronetProviderComparator(preferGooglePlayServices)
        Collections.sort(cronetProviders, providerComparator)
        for (i in cronetProviders.indices) {
            val providerName = cronetProviders[i].name
            try {
                val cronetEngineBuilder = cronetProviders[i].createBuilder()
                if (userAgent != null) {
                    cronetEngineBuilder.setUserAgent(userAgent)
                }
                val cronetEngine = cronetEngineBuilder.build()
                Log.d(TAG, "CronetEngine built using $providerName")
                return cronetEngine
            } catch (e: SecurityException) {
                Log.w(
                        TAG, "Failed to build CronetEngine. Please check that the process has "
                        + "android.permission.ACCESS_NETWORK_STATE.")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(
                        TAG, "Failed to link Cronet binaries. Please check that native Cronet binaries are"
                        + "bundled into your app.")
            }
        }
        Log.w(TAG, "CronetEngine could not be built.")
        return null
    }

    private class CronetProviderComparator(private val preferGooglePlayServices: Boolean) : Comparator<CronetProvider> {
        override fun compare(providerLeft: CronetProvider, providerRight: CronetProvider): Int {
            val providerComparison = getPriority(providerLeft) - getPriority(providerRight)
            return if (providerComparison != 0) {
                providerComparison
            } else -compareVersionStrings(providerLeft.version, providerRight.version)
        }

        /**
         * Returns the priority score for a Cronet provider, where a smaller score indicates higher
         * priority.
         */
        private fun getPriority(provider: CronetProvider): Int {
            val providerName = provider.name
            return if (CronetProvider.PROVIDER_NAME_APP_PACKAGED == providerName) {
                1
            } else if (GOOGLE_PLAY_SERVICES_PROVIDER_NAME == providerName) {
                if (preferGooglePlayServices) 0 else 2
            } else {
                3
            }
        }

        companion object {
            /*
     * Copy of com.google.android.gms.net.CronetProviderInstaller.PROVIDER_NAME. We have our own
     * copy because GMSCore CronetProvider classes are unavailable in some (internal to Google)
     * build configurations.
     */
            private const val GOOGLE_PLAY_SERVICES_PROVIDER_NAME = "Google-Play-Services-Cronet-Provider"

            /** Compares version strings of format "12.123.35.23".  */
            private fun compareVersionStrings(
                    versionLeft: String?, versionRight: String?): Int {
                if (versionLeft == null || versionRight == null) {
                    return 0
                }
                val versionStringsLeft = Util.split(versionLeft, "\\.")
                val versionStringsRight = Util.split(versionRight, "\\.")
                val minLength = Math.min(versionStringsLeft.size, versionStringsRight.size)
                for (i in 0 until minLength) {
                    if (versionStringsLeft[i] != versionStringsRight[i]) {
                        return try {
                            val versionIntLeft = versionStringsLeft[i].toInt()
                            val versionIntRight = versionStringsRight[i].toInt()
                            versionIntLeft - versionIntRight
                        } catch (e: NumberFormatException) {
                            0
                        }
                    }
                }
                return 0
            }
        }
    }
}