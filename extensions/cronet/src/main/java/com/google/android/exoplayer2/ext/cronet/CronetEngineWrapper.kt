/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.chromium.net.CronetEngine

/**
 * A wrapper class for a [CronetEngine].
 *
 */
@Deprecated("""Use {@link CronetEngine} directly. See the <a
      href=""" https ://developer.android.com/guide/topics/connectivity/cronet/start">Android developer\n" + "      guide</a> to learn how to instantiate a {@link CronetEngine} for use by your application. You\n" + "      can also use {@link CronetUtil#buildCronetEngine} to build a {@link CronetEngine} suitable\n" + "      for use with {@link CronetDataSource}.")
        class CronetEngineWrapper {
    var cronetEngine: CronetEngine?
    /**
     * Creates a wrapper for a [CronetEngine] built using the most suitable [ ]. When natively bundled Cronet and GMSCore Cronet are both available, `preferGMSCoreCronet` determines which is preferred.
     *
     * @param context A context.
     * @param userAgent A default user agent, or `null` to use a default user agent of the
     * [CronetEngine].
     * @param preferGooglePlayServices Whether Cronet from Google Play Services should be preferred
     * over Cronet Embedded, if both are available.
     */
    /**
     * Creates a wrapper for a [CronetEngine] built using the most suitable [ ]. When natively bundled Cronet and GMSCore Cronet are both available, the
     * natively bundled provider is preferred.
     *
     * @param context A context.
     */
    @JvmOverloads
    constructor(
            context:android. content . Context ?, userAgent:kotlin.String? =  /* userAgent= */null, preferGooglePlayServices:kotlin.Boolean =  /* preferGMSCoreCronet= */false){
        cronetEngine = CronetUtil.buildCronetEngine(context, userAgent, preferGooglePlayServices)
    }
    /**
     * Creates a wrapper for an existing [CronetEngine].
     *
     * @param cronetEngine The CronetEngine to wrap.
     */
    constructor(cronetEngine:CronetEngine?){
        this.cronetEngine = cronetEngine
    }
    /**
     * Returns the wrapped [CronetEngine].
     *
     * @return The CronetEngine, or null if no CronetEngine is available.
     */
    fun  /* package */getCronetEngine(): CronetEngine? {
        return cronetEngine
    }
}