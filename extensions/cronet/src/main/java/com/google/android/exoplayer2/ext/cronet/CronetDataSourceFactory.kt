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
package com.google.android.exoplayer2.ext.cronet

import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties
import com.google.android.exoplayer2.upstream.TransferListener
import org.chromium.net.CronetEngine
import org.chromium.net.UrlRequest
import java.util.concurrent.Executor

@Deprecated("Use {@link CronetDataSource.Factory} instead.")
class CronetDataSourceFactory
/**
 * Creates an instance.
 *
 *
 * If the [CronetEngineWrapper] fails to provide a [CronetEngine], the provided
 * fallback [HttpDataSource.Factory] will be used instead.
 *
 * @param cronetEngineWrapper A [CronetEngineWrapper].
 * @param executor The [java.util.concurrent.Executor] that will perform the requests.
 * @param transferListener An optional listener.
 * @param connectTimeoutMs The connection timeout, in milliseconds.
 * @param readTimeoutMs The read timeout, in milliseconds.
 * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
 * @param fallbackFactory A [HttpDataSource.Factory] which is used as a fallback in case no
 * suitable CronetEngine can be build.
 */(
        private val cronetEngineWrapper: CronetEngineWrapper,
        private val executor: Executor?,
        private val transferListener: TransferListener?,
        private val connectTimeoutMs: Int,
        private val readTimeoutMs: Int,
        private val resetTimeoutOnRedirects: Boolean,
        private val fallbackFactory: HttpDataSource.Factory) : BaseFactory() {
    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], the provided
     * fallback [HttpDataSource.Factory] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param fallbackFactory A [HttpDataSource.Factory] which is used as a fallback in case no
     * suitable CronetEngine can be build.
     */
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            fallbackFactory: HttpDataSource.Factory) : this(
            cronetEngineWrapper,
            executor,  /* transferListener= */
            null,
            DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DEFAULT_READ_TIMEOUT_MILLIS,
            false,
            fallbackFactory) {
    }
    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param userAgent The user agent that will be used by the fallback [HttpDataSource] if
     * needed, or `null` for the fallback to use the default user agent of the underlying
     * platform.
     */
    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     */
    @JvmOverloads
    constructor(
            cronetEngineWrapper: CronetEngineWrapper, executor: Executor?, userAgent: String? =  /* userAgent= */null as String?) : this(
            cronetEngineWrapper,
            executor,  /* transferListener= */
            null,
            DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DEFAULT_READ_TIMEOUT_MILLIS,
            false,
            DefaultHttpDataSource.Factory().setUserAgent(userAgent)) {
    }

    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param connectTimeoutMs The connection timeout, in milliseconds.
     * @param readTimeoutMs The read timeout, in milliseconds.
     * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
     * @param userAgent The user agent that will be used by the fallback [HttpDataSource] if
     * needed, or `null` for the fallback to use the default user agent of the underlying
     * platform.
     */
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            resetTimeoutOnRedirects: Boolean,
            userAgent: String?) : this(
            cronetEngineWrapper,
            executor,  /* transferListener= */
            null,
            connectTimeoutMs,
            readTimeoutMs,
            resetTimeoutOnRedirects,
            DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setConnectTimeoutMs(connectTimeoutMs)
                    .setReadTimeoutMs(readTimeoutMs)) {
    }

    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], the provided
     * fallback [HttpDataSource.Factory] will be used instead.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param connectTimeoutMs The connection timeout, in milliseconds.
     * @param readTimeoutMs The read timeout, in milliseconds.
     * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
     * @param fallbackFactory A [HttpDataSource.Factory] which is used as a fallback in case no
     * suitable CronetEngine can be build.
     */
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            resetTimeoutOnRedirects: Boolean,
            fallbackFactory: HttpDataSource.Factory) : this(
            cronetEngineWrapper,
            executor,  /* transferListener= */
            null,
            connectTimeoutMs,
            readTimeoutMs,
            resetTimeoutOnRedirects,
            fallbackFactory) {
    }

    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], the provided
     * fallback [HttpDataSource.Factory] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param transferListener An optional listener.
     * @param fallbackFactory A [HttpDataSource.Factory] which is used as a fallback in case no
     * suitable CronetEngine can be build.
     */
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            transferListener: TransferListener?,
            fallbackFactory: HttpDataSource.Factory) : this(
            cronetEngineWrapper,
            executor,
            transferListener,
            DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DEFAULT_READ_TIMEOUT_MILLIS,
            false,
            fallbackFactory) {
    }
    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param transferListener An optional listener.
     * @param userAgent The user agent that will be used by the fallback [HttpDataSource] if
     * needed, or `null` for the fallback to use the default user agent of the underlying
     * platform.
     */
    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     *
     * Sets [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS] as the connection timeout,
     * [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS] as the read timeout.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param transferListener An optional listener.
     */
    @JvmOverloads
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            transferListener: TransferListener?,
            userAgent: String? =  /* userAgent= */null as String?) : this(
            cronetEngineWrapper,
            executor,
            transferListener,
            DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DEFAULT_READ_TIMEOUT_MILLIS,
            false,
            DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setTransferListener(transferListener)) {
    }

    /**
     * Creates an instance.
     *
     *
     * If the [CronetEngineWrapper] fails to provide a [CronetEngine], a [ ] will be used instead.
     *
     * @param cronetEngineWrapper A [CronetEngineWrapper].
     * @param executor The [java.util.concurrent.Executor] that will perform the requests.
     * @param transferListener An optional listener.
     * @param connectTimeoutMs The connection timeout, in milliseconds.
     * @param readTimeoutMs The read timeout, in milliseconds.
     * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
     * @param userAgent The user agent that will be used by the fallback [HttpDataSource] if
     * needed, or `null` for the fallback to use the default user agent of the underlying
     * platform.
     */
    constructor(
            cronetEngineWrapper: CronetEngineWrapper,
            executor: Executor?,
            transferListener: TransferListener?,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            resetTimeoutOnRedirects: Boolean,
            userAgent: String?) : this(
            cronetEngineWrapper,
            executor,
            transferListener,
            connectTimeoutMs,
            readTimeoutMs,
            resetTimeoutOnRedirects,
            DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setTransferListener(transferListener)
                    .setConnectTimeoutMs(connectTimeoutMs)
                    .setReadTimeoutMs(readTimeoutMs)) {
    }

    override fun createDataSourceInternal(
            defaultRequestProperties: RequestProperties): HttpDataSource {
        val cronetEngine = cronetEngineWrapper.cronetEngine
                ?: return fallbackFactory.createDataSource()
        val dataSource = CronetDataSource(
                cronetEngine,
                executor,
                UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM,
                connectTimeoutMs,
                readTimeoutMs,
                resetTimeoutOnRedirects,  /* handleSetCookieRequests= */
                false,  /* userAgent= */
                null,
                defaultRequestProperties,  /* contentTypePredicate= */
                null,  /* keepPostFor302Redirects */
                false)
        if (transferListener != null) {
            dataSource.addTransferListener(transferListener)
        }
        return dataSource
    }

    companion object {
        /** The default connection timeout, in milliseconds.  */
        val DEFAULT_CONNECT_TIMEOUT_MILLIS: Int = CronetDataSource.Companion.DEFAULT_CONNECT_TIMEOUT_MILLIS

        /** The default read timeout, in milliseconds.  */
        val DEFAULT_READ_TIMEOUT_MILLIS: Int = CronetDataSource.Companion.DEFAULT_READ_TIMEOUT_MILLIS
    }
}