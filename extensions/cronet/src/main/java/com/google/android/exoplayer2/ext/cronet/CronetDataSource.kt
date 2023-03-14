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

import android.net.Uri
import android.text.TextUtils
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.HttpDataSource.*
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.ConditionVariable
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Ascii
import com.google.common.base.Predicate
import com.google.common.net.HttpHeaders
import com.google.common.primitives.Longs
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.chromium.net.*
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executor

/**
 * DataSource without intermediate buffer based on Cronet API set using UrlRequest.
 *
 *
 * Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the `dataSpec`, [.setRequestProperty] and the default parameters used to
 * construct the instance.
 */
class CronetDataSource(
        cronetEngine: CronetEngine?,
        executor: Executor?,
        requestPriority: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        resetTimeoutOnRedirects: Boolean,
        handleSetCookieRequests: Boolean,
        userAgent: String?,
        defaultRequestProperties: RequestProperties?,
        contentTypePredicate: Predicate<String>?,
        keepPostFor302Redirects: Boolean) : BaseDataSource( /* isNetwork= */true), HttpDataSource {
    /** [DataSource.Factory] for [CronetDataSource] instances.  */
    class Factory : HttpDataSource.Factory {
        // TODO: Remove @Nullable annotation when CronetEngineWrapper is deleted.
        private val cronetEngine: CronetEngine?
        private val executor: Executor
        private val defaultRequestProperties: RequestProperties

        // TODO: Remove when CronetEngineWrapper is deleted.
        private val internalFallbackFactory: DefaultHttpDataSource.Factory?

        // TODO: Remove when CronetEngineWrapper is deleted.
        private var fallbackFactory: HttpDataSource.Factory? = null
        private var contentTypePredicate: Predicate<String>? = null
        private var transferListener: TransferListener? = null
        private var userAgent: String? = null
        private var requestPriority = 0
        private var connectTimeoutMs: Int
        private var readTimeoutMs: Int
        private var resetTimeoutOnRedirects = false
        private var handleSetCookieRequests = false
        private var keepPostFor302Redirects = false

        /**
         * Creates an instance.
         *
         * @param cronetEngine A [CronetEngine] to make the requests. This should *not* be
         * a fallback instance obtained from `JavaCronetProvider`. It's more efficient to use
         * [DefaultHttpDataSource] instead in this case.
         * @param executor The [java.util.concurrent.Executor] that will handle responses. This
         * may be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a
         * thread hop from Cronet's internal network thread to the response handling thread.
         * However, to avoid slowing down overall network performance, care must be taken to make
         * sure response handling is a fast operation when using a direct executor.
         */
        constructor(cronetEngine: CronetEngine?, executor: Executor) {
            this.cronetEngine = Assertions.checkNotNull(cronetEngine)
            this.executor = executor
            defaultRequestProperties = RequestProperties()
            internalFallbackFactory = null
            requestPriority = UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS
        }

        /**
         * Creates an instance.
         *
         * @param cronetEngineWrapper A [CronetEngineWrapper].
         * @param executor The [java.util.concurrent.Executor] that will handle responses. This
         * may be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a
         * thread hop from Cronet's internal network thread to the response handling thread.
         * However, to avoid slowing down overall network performance, care must be taken to make
         * sure response handling is a fast operation when using a direct executor.
         */
        @Deprecated("""Use {@link #Factory(CronetEngine, Executor)} with an instantiated {@link
     *     CronetEngine}, or {@link DefaultHttpDataSource} for cases where {@link
     *     CronetEngineWrapper#getCronetEngine()} would have returned {@code null}.""")
        constructor(cronetEngineWrapper: CronetEngineWrapper, executor: Executor) {
            cronetEngine = cronetEngineWrapper.cronetEngine
            this.executor = executor
            defaultRequestProperties = RequestProperties()
            internalFallbackFactory = DefaultHttpDataSource.Factory()
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS
        }

        @CanIgnoreReturnValue
        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
            internalFallbackFactory?.setDefaultRequestProperties(defaultRequestProperties)
            return this
        }

        /**
         * Sets the user agent that will be used.
         *
         *
         * The default is `null`, which causes the default user agent of the underlying [ ] to be used.
         *
         * @param userAgent The user agent that will be used, or `null` to use the default user
         * agent of the underlying [CronetEngine].
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setUserAgent(userAgent: String?): Factory {
            this.userAgent = userAgent
            internalFallbackFactory?.setUserAgent(userAgent)
            return this
        }

        /**
         * Sets the priority of requests made by [CronetDataSource] instances created by this
         * factory.
         *
         *
         * The default is [UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM].
         *
         * @param requestPriority The request priority, which should be one of Cronet's `UrlRequest.Builder#REQUEST_PRIORITY_*` constants.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setRequestPriority(requestPriority: Int): Factory {
            this.requestPriority = requestPriority
            return this
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         *
         * The default is [CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS].
         *
         * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setConnectionTimeoutMs(connectTimeoutMs: Int): Factory {
            this.connectTimeoutMs = connectTimeoutMs
            internalFallbackFactory?.setConnectTimeoutMs(connectTimeoutMs)
            return this
        }

        /**
         * Sets whether the connect timeout is reset when a redirect occurs.
         *
         *
         * The default is `false`.
         *
         * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setResetTimeoutOnRedirects(resetTimeoutOnRedirects: Boolean): Factory {
            this.resetTimeoutOnRedirects = resetTimeoutOnRedirects
            return this
        }

        /**
         * Sets whether "Set-Cookie" requests on redirect should be forwarded to the redirect url in the
         * "Cookie" header.
         *
         *
         * The default is `false`.
         *
         * @param handleSetCookieRequests Whether "Set-Cookie" requests on redirect should be forwarded
         * to the redirect url in the "Cookie" header.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setHandleSetCookieRequests(handleSetCookieRequests: Boolean): Factory {
            this.handleSetCookieRequests = handleSetCookieRequests
            return this
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         *
         * The default is [CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS].
         *
         * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setReadTimeoutMs(readTimeoutMs: Int): Factory {
            this.readTimeoutMs = readTimeoutMs
            internalFallbackFactory?.setReadTimeoutMs(readTimeoutMs)
            return this
        }

        /**
         * Sets a content type [Predicate]. If a content type is rejected by the predicate then a
         * [HttpDataSource.InvalidContentTypeException] is thrown from [.open].
         *
         *
         * The default is `null`.
         *
         * @param contentTypePredicate The content type [Predicate], or `null` to clear a
         * predicate that was previously set.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setContentTypePredicate(contentTypePredicate: Predicate<String>?): Factory {
            this.contentTypePredicate = contentTypePredicate
            internalFallbackFactory?.setContentTypePredicate(contentTypePredicate)
            return this
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for a
         * POST request.
         */
        @CanIgnoreReturnValue
        fun setKeepPostFor302Redirects(keepPostFor302Redirects: Boolean): Factory {
            this.keepPostFor302Redirects = keepPostFor302Redirects
            internalFallbackFactory?.setKeepPostFor302Redirects(keepPostFor302Redirects)
            return this
        }

        /**
         * Sets the [TransferListener] that will be used.
         *
         *
         * The default is `null`.
         *
         *
         * See [DataSource.addTransferListener].
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setTransferListener(transferListener: TransferListener?): Factory {
            this.transferListener = transferListener
            internalFallbackFactory?.setTransferListener(transferListener)
            return this
        }

        /**
         * Sets the fallback [HttpDataSource.Factory] that is used as a fallback if the [ ] fails to provide a [CronetEngine].
         *
         *
         * By default a [DefaultHttpDataSource] is used as fallback factory.
         *
         * @param fallbackFactory The fallback factory that will be used.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        @Deprecated("""Do not use {@link CronetDataSource} or its factory in cases where a suitable
          {@link CronetEngine} is not available. Use the fallback factory directly in such cases.""")
        fun setFallbackFactory(fallbackFactory: HttpDataSource.Factory?): Factory {
            this.fallbackFactory = fallbackFactory
            return this
        }

        override fun createDataSource(): HttpDataSource {
            if (cronetEngine == null) {
                return if (fallbackFactory != null) fallbackFactory!!.createDataSource() else Assertions.checkNotNull(internalFallbackFactory).createDataSource()
            }
            val dataSource = CronetDataSource(
                    cronetEngine,
                    executor,
                    requestPriority,
                    connectTimeoutMs,
                    readTimeoutMs,
                    resetTimeoutOnRedirects,
                    handleSetCookieRequests,
                    userAgent,
                    defaultRequestProperties,
                    contentTypePredicate,
                    keepPostFor302Redirects)
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }
    }

    /** Thrown when an error is encountered when trying to open a [CronetDataSource].  */
    class OpenException : HttpDataSourceException {
        /**
         * Returns the status of the connection establishment at the moment when the error occurred, as
         * defined by [UrlRequest.Status].
         */
        @JvmField
        val cronetConnectionStatus: Int

        @Deprecated("Use {@link #OpenException(IOException, DataSpec, int, int)}.")
        constructor(cause: IOException?, dataSpec: DataSpec?, cronetConnectionStatus: Int) : super(cause!!, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, TYPE_OPEN) {
            this.cronetConnectionStatus = cronetConnectionStatus
        }

        constructor(
                cause: IOException?,
                dataSpec: DataSpec?,
                errorCode: @PlaybackException.ErrorCode Int,
                cronetConnectionStatus: Int) : super(cause!!, dataSpec!!, errorCode, TYPE_OPEN) {
            this.cronetConnectionStatus = cronetConnectionStatus
        }

        @Deprecated("Use {@link #OpenException(String, DataSpec, int, int)}.")
        constructor(errorMessage: String?, dataSpec: DataSpec?, cronetConnectionStatus: Int) : super(errorMessage!!, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, TYPE_OPEN) {
            this.cronetConnectionStatus = cronetConnectionStatus
        }

        constructor(
                errorMessage: String?,
                dataSpec: DataSpec?,
                errorCode: @PlaybackException.ErrorCode Int,
                cronetConnectionStatus: Int) : super(errorMessage!!, dataSpec!!, errorCode, TYPE_OPEN) {
            this.cronetConnectionStatus = cronetConnectionStatus
        }

        constructor(
                dataSpec: DataSpec?, errorCode: @PlaybackException.ErrorCode Int, cronetConnectionStatus: Int) : super(dataSpec!!, errorCode, TYPE_OPEN) {
            this.cronetConnectionStatus = cronetConnectionStatus
        }
    }

    /* package */
    @JvmField
    val urlRequestCallback: UrlRequest.Callback
    private val cronetEngine: CronetEngine
    private val executor: Executor
    private val requestPriority: Int
    private val connectTimeoutMs: Int
    private val readTimeoutMs: Int
    private val resetTimeoutOnRedirects: Boolean
    private val handleSetCookieRequests: Boolean
    private val userAgent: String?
    private val defaultRequestProperties: RequestProperties?
    private val requestProperties: RequestProperties
    private val operation: ConditionVariable
    private val clock: Clock
    private var contentTypePredicate: Predicate<String>?
    private val keepPostFor302Redirects: Boolean

    // Accessed by the calling thread only.
    private var opened = false
    private var bytesRemaining: Long = 0

    /** Returns current [UrlRequest]. May be null if the data source is not opened.  */
    // Written from the calling thread only. currentUrlRequest.start() calls ensure writes are visible
    // to reads made by the Cronet thread.
    protected var currentUrlRequest: UrlRequest? = null
        private set
    private var currentDataSpec: DataSpec? = null

    // Reference written and read by calling thread only. Passed to Cronet thread as a local variable.
    // operation.open() calls ensure writes into the buffer are visible to reads made by the calling
    // thread.
    private var readBuffer: ByteBuffer? = null

    /** Returns current [UrlResponseInfo]. May be null if the data source is not opened.  */
    // Written from the Cronet thread only. operation.open() calls ensure writes are visible to reads
    // made by the calling thread.
    protected var currentUrlResponseInfo: UrlResponseInfo? = null
        private set
    private var exception: IOException? = null
    private var finished = false

    @Volatile
    private var currentConnectTimeoutMs: Long = 0

    init {
        this.cronetEngine = Assertions.checkNotNull(cronetEngine)
        this.executor = Assertions.checkNotNull(executor)
        this.requestPriority = requestPriority
        this.connectTimeoutMs = connectTimeoutMs
        this.readTimeoutMs = readTimeoutMs
        this.resetTimeoutOnRedirects = resetTimeoutOnRedirects
        this.handleSetCookieRequests = handleSetCookieRequests
        this.userAgent = userAgent
        this.defaultRequestProperties = defaultRequestProperties
        this.contentTypePredicate = contentTypePredicate
        this.keepPostFor302Redirects = keepPostFor302Redirects
        clock = Clock.DEFAULT
        urlRequestCallback = UrlRequestCallback()
        requestProperties = RequestProperties()
        operation = ConditionVariable()
    }

    @Deprecated("Use {@link CronetDataSource.Factory#setContentTypePredicate(Predicate)} instead.")
    fun setContentTypePredicate(contentTypePredicate: Predicate<String>?) {
        this.contentTypePredicate = contentTypePredicate
    }

    // HttpDataSource implementation.
    override fun setRequestProperty(name: String, value: String) {
        requestProperties[name] = value
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun getResponseCode(): Int {
        return if (currentUrlResponseInfo == null || currentUrlResponseInfo!!.httpStatusCode <= 0) -1 else currentUrlResponseInfo!!.httpStatusCode
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return if (currentUrlResponseInfo == null) emptyMap() else currentUrlResponseInfo!!.allHeaders
    }

    override fun getUri(): Uri? {
        return if (currentUrlResponseInfo == null) null else Uri.parse(currentUrlResponseInfo!!.url)
    }

    @Throws(HttpDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        Assertions.checkNotNull(dataSpec)
        Assertions.checkState(!opened)
        operation.close()
        resetConnectTimeout()
        currentDataSpec = dataSpec
        val urlRequest: UrlRequest
        try {
            urlRequest = buildRequestBuilder(dataSpec).build()
            currentUrlRequest = urlRequest
        } catch (e: IOException) {
            if (e is HttpDataSourceException) {
                throw e
            } else {
                throw OpenException(
                        e, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, UrlRequest.Status.IDLE)
            }
        }
        urlRequest.start()
        transferInitializing(dataSpec)
        try {
            val connectionOpened = blockUntilConnectTimeout()
            val connectionOpenException = exception
            if (connectionOpenException != null) {
                val message = connectionOpenException.message
                if (message != null && Ascii.toLowerCase(message).contains("err_cleartext_not_permitted")) {
                    throw CleartextNotPermittedException(connectionOpenException, dataSpec)
                }
                throw OpenException(
                        connectionOpenException,
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        getStatus(urlRequest))
            } else if (!connectionOpened) {
                // The timeout was reached before the connection was opened.
                throw OpenException(
                        SocketTimeoutException(),
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        getStatus(urlRequest))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OpenException(
                    InterruptedIOException(),
                    dataSpec,
                    PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
                    UrlRequest.Status.INVALID)
        }

        // Check for a valid response code.
        val responseInfo = Assertions.checkNotNull(currentUrlResponseInfo)
        val responseCode = responseInfo.httpStatusCode
        val responseHeaders = responseInfo.allHeaders
        if (responseCode < 200 || responseCode > 299) {
            if (responseCode == 416) {
                val documentSize = HttpUtil.getDocumentSize(getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE))
                if (dataSpec.position == documentSize) {
                    opened = true
                    transferStarted(dataSpec)
                    return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else 0
                }
            }
            val responseBody: ByteArray
            responseBody = try {
                readResponseBody()
            } catch (e: IOException) {
                Util.EMPTY_BYTE_ARRAY
            }
            val cause: IOException? = if (responseCode == 416) DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) else null
            throw InvalidResponseCodeException(
                    responseCode,
                    responseInfo.httpStatusText,
                    cause,
                    responseHeaders,
                    dataSpec,
                    responseBody)
        }

        // Check for a valid content type.
        val contentTypePredicate = contentTypePredicate
        if (contentTypePredicate != null) {
            val contentType = getFirstHeader(responseHeaders, HttpHeaders.CONTENT_TYPE)
            if (contentType != null && !contentTypePredicate.apply(contentType)) {
                throw InvalidContentTypeException(contentType, dataSpec)
            }
        }

        // If we requested a range starting from a non-zero position and received a 200 rather than a
        // 206, then the server does not support partial requests. We'll need to manually skip to the
        // requested position.
        val bytesToSkip = if (responseCode == 200 && dataSpec.position != 0L) dataSpec.position else 0

        // Calculate the content length.
        bytesRemaining = if (!isCompressed(responseInfo)) {
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                val contentLength = HttpUtil.getContentLength(
                        getFirstHeader(responseHeaders, HttpHeaders.CONTENT_LENGTH),
                        getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE))
                if (contentLength != C.LENGTH_UNSET.toLong()) contentLength - bytesToSkip else C.LENGTH_UNSET.toLong()
            }
        } else {
            // If the response is compressed then the content length will be that of the compressed data
            // which isn't what we want. Always use the dataSpec length in this case.
            dataSpec.length
        }
        opened = true
        transferStarted(dataSpec)
        skipFully(bytesToSkip, dataSpec)
        return bytesRemaining
    }

    @Throws(HttpDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        Assertions.checkState(opened)
        if (length == 0) {
            return 0
        } else if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }
        val readBuffer = orCreateReadBuffer
        if (!readBuffer!!.hasRemaining()) {
            // Fill readBuffer with more data from Cronet.
            operation.close()
            readBuffer.clear()
            readInternal(readBuffer, Util.castNonNull(currentDataSpec))
            if (finished) {
                bytesRemaining = 0
                return C.RESULT_END_OF_INPUT
            }

            // The operation didn't time out, fail or finish, and therefore data must have been read.
            readBuffer.flip()
            Assertions.checkState(readBuffer.hasRemaining())
        }

        // Ensure we read up to bytesRemaining, in case this was a Range request with finite end, but
        // the server does not support Range requests and transmitted the entire resource.
        val bytesRead = Longs.min(
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining else Long.MAX_VALUE,
                readBuffer.remaining().toLong(),
                length.toLong()).toInt()
        readBuffer[buffer, offset, bytesRead]
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    /**
     * Reads up to `buffer.remaining()` bytes of data and stores them into `buffer`,
     * starting at `buffer.position()`. Advances the position of the buffer by the number of
     * bytes read and returns this length.
     *
     *
     * If there is an error, a [HttpDataSourceException] is thrown and the contents of `buffer` should be ignored. If the exception has error code `HttpDataSourceException.TYPE_READ`, note that Cronet may continue writing into `buffer`
     * after the method has returned. Thus the caller should not attempt to reuse the buffer.
     *
     *
     * If `buffer.remaining()` is zero then 0 is returned. Otherwise, if no data is available
     * because the end of the opened range has been reached, then [C.RESULT_END_OF_INPUT] is
     * returned. Otherwise, the call will block until at least one byte of data has been read and the
     * number of bytes read is returned.
     *
     *
     * Passed buffer must be direct ByteBuffer. If you have a non-direct ByteBuffer, consider the
     * alternative read method with its backed array.
     *
     * @param buffer The ByteBuffer into which the read data should be stored. Must be a direct
     * ByteBuffer.
     * @return The number of bytes read, or [C.RESULT_END_OF_INPUT] if no data is available
     * because the end of the opened range has been reached.
     * @throws HttpDataSourceException If an error occurs reading from the source.
     * @throws IllegalArgumentException If `buffer` is not a direct ByteBuffer.
     */
    @Throws(HttpDataSourceException::class)
    fun read(buffer: ByteBuffer): Int {
        Assertions.checkState(opened)
        require(buffer.isDirect) { "Passed buffer is not a direct ByteBuffer" }
        if (!buffer.hasRemaining()) {
            return 0
        } else if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }
        val readLength = buffer.remaining()
        if (readBuffer != null) {
            // If there is existing data in the readBuffer, read as much as possible. Return if any read.
            val copyBytes = copyByteBuffer( /* src= */readBuffer!!,  /* dst= */buffer)
            if (copyBytes != 0) {
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= copyBytes.toLong()
                }
                bytesTransferred(copyBytes)
                return copyBytes
            }
        }

        // Fill buffer with more data from Cronet.
        operation.close()
        readInternal(buffer, Util.castNonNull(currentDataSpec))
        if (finished) {
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }

        // The operation didn't time out, fail or finish, and therefore data must have been read.
        Assertions.checkState(readLength > buffer.remaining())
        val bytesRead = readLength - buffer.remaining()
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    @Synchronized
    override fun close() {
        if (currentUrlRequest != null) {
            currentUrlRequest!!.cancel()
            currentUrlRequest = null
        }
        if (readBuffer != null) {
            readBuffer!!.limit(0)
        }
        currentDataSpec = null
        currentUrlResponseInfo = null
        exception = null
        finished = false
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    @Throws(IOException::class)
    protected fun buildRequestBuilder(dataSpec: DataSpec): UrlRequest.Builder {
        val requestBuilder = cronetEngine
                .newUrlRequestBuilder(dataSpec.uri.toString(), urlRequestCallback, executor)
                .setPriority(requestPriority)
                .allowDirectExecutor()

        // Set the headers.
        val requestHeaders: MutableMap<String, String> = HashMap()
        if (defaultRequestProperties != null) {
            requestHeaders.putAll(defaultRequestProperties.snapshot)
        }
        requestHeaders.putAll(requestProperties.snapshot)
        requestHeaders.putAll(dataSpec.httpRequestHeaders)
        for ((key, value) in requestHeaders) {
            requestBuilder.addHeader(key, value)
        }
        if (dataSpec.httpBody != null && !requestHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            throw OpenException(
                    "HTTP request with non-empty body must set Content-Type",
                    dataSpec,
                    PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
                    UrlRequest.Status.IDLE)
        }
        val rangeHeader = HttpUtil.buildRangeRequestHeader(dataSpec.position, dataSpec.length)
        if (rangeHeader != null) {
            requestBuilder.addHeader(HttpHeaders.RANGE, rangeHeader)
        }
        if (userAgent != null) {
            requestBuilder.addHeader(HttpHeaders.USER_AGENT, userAgent)
        }
        // TODO: Uncomment when https://bugs.chromium.org/p/chromium/issues/detail?id=711810 is fixed
        // (adjusting the code as necessary).
        // Force identity encoding unless gzip is allowed.
        // if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
        //   requestBuilder.addHeader("Accept-Encoding", "identity");
        // }
        // Set the method and (if non-empty) the body.
        requestBuilder.setHttpMethod(dataSpec.httpMethodString)
        if (dataSpec.httpBody != null) {
            requestBuilder.setUploadDataProvider(
                    ByteArrayUploadDataProvider(dataSpec.httpBody), executor)
        }
        return requestBuilder
    }

    // Internal methods.
    @Throws(InterruptedException::class)
    private fun blockUntilConnectTimeout(): Boolean {
        var now = clock.elapsedRealtime()
        var opened = false
        while (!opened && now < currentConnectTimeoutMs) {
            opened = operation.block(currentConnectTimeoutMs - now + 5 /* fudge factor */)
            now = clock.elapsedRealtime()
        }
        return opened
    }

    private fun resetConnectTimeout() {
        currentConnectTimeoutMs = clock.elapsedRealtime() + connectTimeoutMs
    }

    /**
     * Attempts to skip the specified number of bytes in full.
     *
     *
     * The methods throws an [OpenException] with [OpenException.reason] set to [ ][PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE] when the data ended before the
     * specified number of bytes were skipped.
     *
     * @param bytesToSkip The number of bytes to skip.
     * @param dataSpec The [DataSpec].
     * @throws HttpDataSourceException If the thread is interrupted during the operation, or an error
     * occurs reading from the source; or when the data ended before the specified number of bytes
     * were skipped.
     */
    @Throws(HttpDataSourceException::class)
    private fun skipFully(bytesToSkip: Long, dataSpec: DataSpec) {
        var bytesToSkip = bytesToSkip
        if (bytesToSkip == 0L) {
            return
        }
        val readBuffer = orCreateReadBuffer
        try {
            while (bytesToSkip > 0) {
                // Fill readBuffer with more data from Cronet.
                operation.close()
                readBuffer!!.clear()
                readInternal(readBuffer, dataSpec)
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedIOException()
                }
                bytesToSkip -= if (finished) {
                    throw OpenException(
                            dataSpec,
                            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                            UrlRequest.Status.READING_RESPONSE)
                } else {
                    // The operation didn't time out, fail or finish, and therefore data must have been read.
                    readBuffer.flip()
                    Assertions.checkState(readBuffer.hasRemaining())
                    val bytesSkipped = Math.min(readBuffer.remaining().toLong(), bytesToSkip).toInt()
                    readBuffer.position(readBuffer.position() + bytesSkipped)
                    bytesSkipped.toLong()
                }
            }
        } catch (e: IOException) {
            if (e is HttpDataSourceException) {
                throw e
            } else {
                throw OpenException(
                        e,
                        dataSpec,
                        if (e is SocketTimeoutException) PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT else PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        UrlRequest.Status.READING_RESPONSE)
            }
        }
    }

    /**
     * Reads the whole response body.
     *
     * @return The response body.
     * @throws IOException If an error occurs reading from the source.
     */
    @Throws(IOException::class)
    private fun readResponseBody(): ByteArray {
        var responseBody = Util.EMPTY_BYTE_ARRAY
        val readBuffer = orCreateReadBuffer
        while (!finished) {
            operation.close()
            readBuffer!!.clear()
            readInternal(readBuffer, Util.castNonNull(currentDataSpec))
            readBuffer.flip()
            if (readBuffer.remaining() > 0) {
                val existingResponseBodyEnd = responseBody.size
                responseBody = Arrays.copyOf(responseBody, responseBody.size + readBuffer.remaining())
                readBuffer[responseBody, existingResponseBodyEnd, readBuffer.remaining()]
            }
        }
        return responseBody
    }

    /**
     * Reads up to `buffer.remaining()` bytes of data from `currentUrlRequest` and stores
     * them into `buffer`. If there is an error and `buffer == readBuffer`, then it resets
     * the current `readBuffer` object so that it is not reused in the future.
     *
     * @param buffer The ByteBuffer into which the read data is stored. Must be a direct ByteBuffer.
     * @throws HttpDataSourceException If an error occurs reading from the source.
     */
    @Throws(HttpDataSourceException::class)
    private fun readInternal(buffer: ByteBuffer?, dataSpec: DataSpec) {
        Util.castNonNull(currentUrlRequest).read(buffer)
        try {
            if (!operation.block(readTimeoutMs.toLong())) {
                throw SocketTimeoutException()
            }
        } catch (e: InterruptedException) {
            // The operation is ongoing so replace buffer to avoid it being written to by this
            // operation during a subsequent request.
            if (buffer === readBuffer) {
                readBuffer = null
            }
            Thread.currentThread().interrupt()
            exception = InterruptedIOException()
        } catch (e: SocketTimeoutException) {
            // The operation is ongoing so replace buffer to avoid it being written to by this
            // operation during a subsequent request.
            if (buffer === readBuffer) {
                readBuffer = null
            }
            exception = HttpDataSourceException(
                    e,
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    HttpDataSourceException.TYPE_READ)
        }
        if (exception != null) {
            if (exception is HttpDataSourceException) {
                throw (exception as HttpDataSourceException?)!!
            } else {
                throw HttpDataSourceException.createForIOException(
                        exception!!, dataSpec, HttpDataSourceException.TYPE_READ)
            }
        }
    }

    private val orCreateReadBuffer: ByteBuffer?
        private get() {
            if (readBuffer == null) {
                readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES)
                readBuffer.limit(0)
            }
            return readBuffer
        }

    private inner class UrlRequestCallback : UrlRequest.Callback() {
        @Synchronized
        override fun onRedirectReceived(
                request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            if (request !== currentUrlRequest) {
                return
            }
            val urlRequest = Assertions.checkNotNull(currentUrlRequest)
            val dataSpec = Assertions.checkNotNull(currentDataSpec)
            val responseCode = info.httpStatusCode
            if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
                // The industry standard is to disregard POST redirects when the status code is 307 or 308.
                if (responseCode == 307 || responseCode == 308) {
                    exception = InvalidResponseCodeException(
                            responseCode,
                            info.httpStatusText,  /* cause= */
                            null,
                            info.allHeaders,
                            dataSpec,  /* responseBody= */
                            Util.EMPTY_BYTE_ARRAY)
                    operation.open()
                    return
                }
            }
            if (resetTimeoutOnRedirects) {
                resetConnectTimeout()
            }
            val shouldKeepPost = keepPostFor302Redirects && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST && responseCode == 302

            // request.followRedirect() transforms a POST request into a GET request, so if we want to
            // keep it as a POST we need to fall through to the manual redirect logic below.
            if (!shouldKeepPost && !handleSetCookieRequests) {
                request.followRedirect()
                return
            }
            val cookieHeadersValue = parseCookies(info.allHeaders[HttpHeaders.SET_COOKIE])
            if (!shouldKeepPost && TextUtils.isEmpty(cookieHeadersValue)) {
                request.followRedirect()
                return
            }
            urlRequest.cancel()
            val redirectUrlDataSpec: DataSpec
            redirectUrlDataSpec = if (!shouldKeepPost && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
                // For POST redirects that aren't 307 or 308, the redirect is followed but request is
                // transformed into a GET unless shouldKeepPost is true.
                dataSpec
                        .buildUpon()
                        .setUri(newLocationUrl)
                        .setHttpMethod(DataSpec.HTTP_METHOD_GET)
                        .setHttpBody(null)
                        .build()
            } else {
                dataSpec.withUri(Uri.parse(newLocationUrl))
            }
            val requestBuilder: UrlRequest.Builder
            try {
                requestBuilder = buildRequestBuilder(redirectUrlDataSpec)
            } catch (e: IOException) {
                exception = e
                return
            }
            attachCookies(requestBuilder, cookieHeadersValue)
            currentUrlRequest = requestBuilder.build()
            currentUrlRequest.start()
        }

        @Synchronized
        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (request !== currentUrlRequest) {
                return
            }
            currentUrlResponseInfo = info
            operation.open()
        }

        @Synchronized
        override fun onReadCompleted(
                request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer) {
            if (request !== currentUrlRequest) {
                return
            }
            operation.open()
        }

        @Synchronized
        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            if (request !== currentUrlRequest) {
                return
            }
            finished = true
            operation.open()
        }

        @Synchronized
        override fun onFailed(
                request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
            if (request !== currentUrlRequest) {
                return
            }
            exception = if (error is NetworkException
                    && error.errorCode
                    == NetworkException.ERROR_HOSTNAME_NOT_RESOLVED) {
                UnknownHostException()
            } else {
                error
            }
            operation.open()
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.cronet")
        }

        /** The default connection timeout, in milliseconds.  */
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000

        /** The default read timeout, in milliseconds.  */
        const val DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000

        // The size of read buffer passed to cronet UrlRequest.read().
        private const val READ_BUFFER_SIZE_BYTES = 32 * 1024
        private fun isCompressed(info: UrlResponseInfo): Boolean {
            for ((key, value) in info.allHeadersAsList) {
                if (key.equals("Content-Encoding", ignoreCase = true)) {
                    return !value.equals("identity", ignoreCase = true)
                }
            }
            return false
        }

        private fun parseCookies(setCookieHeaders: List<String?>?): String? {
            return if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
                null
            } else TextUtils.join(";", setCookieHeaders)
        }

        private fun attachCookies(requestBuilder: UrlRequest.Builder, cookies: String?) {
            if (TextUtils.isEmpty(cookies)) {
                return
            }
            requestBuilder.addHeader(HttpHeaders.COOKIE, cookies)
        }

        @Throws(InterruptedException::class)
        private fun getStatus(request: UrlRequest): Int {
            val conditionVariable = ConditionVariable()
            val statusHolder = IntArray(1)
            request.getStatus(
                    object : UrlRequest.StatusListener() {
                        override fun onStatus(status: Int) {
                            statusHolder[0] = status
                            conditionVariable.open()
                        }
                    })
            conditionVariable.block()
            return statusHolder[0]
        }

        private fun getFirstHeader(allHeaders: Map<String, List<String>>, headerName: String): String? {
            val headers = allHeaders[headerName]
            return if (headers != null && !headers.isEmpty()) headers[0] else null
        }

        // Copy as much as possible from the src buffer into dst buffer.
        // Returns the number of bytes copied.
        private fun copyByteBuffer(src: ByteBuffer, dst: ByteBuffer): Int {
            val remaining = Math.min(src.remaining(), dst.remaining())
            val limit = src.limit()
            src.limit(src.position() + remaining)
            dst.put(src)
            src.limit(limit)
            return remaining
        }
    }
}