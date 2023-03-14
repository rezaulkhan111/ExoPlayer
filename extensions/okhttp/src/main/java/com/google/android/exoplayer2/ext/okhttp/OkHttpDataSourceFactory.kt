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
package com.google.android.exoplayer2.ext.okhttp

import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties
import com.google.android.exoplayer2.upstream.TransferListener
import okhttp3.CacheControl

@Deprecated("Use {@link OkHttpDataSource.Factory} instead.")
class OkHttpDataSourceFactory @JvmOverloads constructor(
        callFactory: Factory,
        userAgent: String? =  /* userAgent= */null,
        listener: TransferListener? =  /* listener= */null,
        cacheControl: CacheControl? =  /* cacheControl= */null) : BaseFactory() {
    private val callFactory: Factory
    private val userAgent: String?
    private val listener: TransferListener?
    private val cacheControl: CacheControl?

    /**
     * Creates an instance.
     *
     * @param callFactory A [Call.Factory] (typically an [okhttp3.OkHttpClient]) for use
     * by the sources created by the factory.
     * @param userAgent An optional User-Agent string.
     * @param cacheControl An optional [CacheControl] for setting the Cache-Control header.
     */
    constructor(
            callFactory: Factory, userAgent: String?, cacheControl: CacheControl?) : this(callFactory, userAgent,  /* listener= */null, cacheControl) {
    }
    /**
     * Creates an instance.
     *
     * @param callFactory A [Call.Factory] (typically an [okhttp3.OkHttpClient]) for use
     * by the sources created by the factory.
     * @param userAgent An optional User-Agent string.
     * @param listener An optional listener.
     * @param cacheControl An optional [CacheControl] for setting the Cache-Control header.
     */
    /**
     * Creates an instance.
     *
     * @param callFactory A [Call.Factory] (typically an [okhttp3.OkHttpClient]) for use
     * by the sources created by the factory.
     */
    /**
     * Creates an instance.
     *
     * @param callFactory A [Call.Factory] (typically an [okhttp3.OkHttpClient]) for use
     * by the sources created by the factory.
     * @param userAgent An optional User-Agent string.
     */
    /**
     * Creates an instance.
     *
     * @param callFactory A [Call.Factory] (typically an [okhttp3.OkHttpClient]) for use
     * by the sources created by the factory.
     * @param userAgent An optional User-Agent string.
     * @param listener An optional listener.
     */
    init {
        this.callFactory = callFactory
        this.userAgent = userAgent
        this.listener = listener
        this.cacheControl = cacheControl
    }

    // Calls deprecated constructor.
    override fun createDataSourceInternal(
            defaultRequestProperties: RequestProperties): OkHttpDataSource {
        val dataSource = OkHttpDataSource(callFactory, userAgent, cacheControl, defaultRequestProperties)
        if (listener != null) {
            dataSource.addTransferListener(listener)
        }
        return dataSource
    }
}