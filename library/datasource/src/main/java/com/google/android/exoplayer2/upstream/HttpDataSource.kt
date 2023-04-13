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
package com.google.android.exoplayer2.upstream

import androidx.annotation.IntDef
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Ascii
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.net.SocketTimeoutException
import java.util.*

/**
 * An HTTP [DataSource].
 */
interface HttpDataSource : DataSource {
    /**
     * A factory for [HttpDataSource] instances.
     */
    interface Factory : DataSource.Factory {
        override fun createDataSource(): HttpDataSource

        /**
         * Sets the default request headers for [HttpDataSource] instances created by the factory.
         *
         *
         * The new request properties will be used for future requests made by [ HttpDataSources][HttpDataSource] created by the factory, including instances that have already been created.
         * Modifying the `defaultRequestProperties` map after a call to this method will have no
         * effect, and so it's necessary to call this method again each time the request properties need
         * to be updated.
         *
         * @param defaultRequestProperties The default request properties.
         * @return This factory.
         */
        fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>?): Factory
    }

    /**
     * Stores HTTP request properties (aka HTTP headers) and provides methods to modify the headers in
     * a thread safe way to avoid the potential of creating snapshots of an inconsistent or unintended
     * state.
     */
    class RequestProperties {
        private val requestProperties: MutableMap<String, String>
        private var requestPropertiesSnapshot: Map<String, String>? = null

        init {
            requestProperties = HashMap()
        }

        /**
         * Sets the specified property `value` for the specified `name`. If a property for
         * this name previously existed, the old value is replaced by the specified value.
         *
         * @param name  The name of the request property.
         * @param value The value of the request property.
         */
        @Synchronized
        operator fun set(name: String, value: String) {
            requestPropertiesSnapshot = null
            requestProperties[name] = value
        }

        /**
         * Sets the keys and values contained in the map. If a property previously existed, the old
         * value is replaced by the specified value. If a property previously existed and is not in the
         * map, the property is left unchanged.
         *
         * @param properties The request properties.
         */
        @Synchronized
        fun set(properties: Map<String, String>?) {
            requestPropertiesSnapshot = null
            requestProperties.putAll(properties!!)
        }

        /**
         * Removes all properties previously existing and sets the keys and values of the map.
         *
         * @param properties The request properties.
         */
        @Synchronized
        fun clearAndSet(properties: Map<String, String>?) {
            requestPropertiesSnapshot = null
            requestProperties.clear()
            requestProperties.putAll(properties!!)
        }

        /**
         * Removes a request property by name.
         *
         * @param name The name of the request property to remove.
         */
        @Synchronized
        fun remove(name: String) {
            requestPropertiesSnapshot = null
            requestProperties.remove(name)
        }

        /**
         * Clears all request properties.
         */
        @Synchronized
        fun clear() {
            requestPropertiesSnapshot = null
            requestProperties.clear()
        }

        /**
         * Gets a snapshot of the request properties.
         *
         * @return A snapshot of the request properties.
         */
        @get:Synchronized
        val snapshot: Map<String, String>?
            get() {
                if (requestPropertiesSnapshot == null) {
                    requestPropertiesSnapshot =
                        Collections.unmodifiableMap(HashMap(requestProperties))
                }
                return requestPropertiesSnapshot
            }
    }

    /**
     * Base implementation of [Factory] that sets default request properties.
     */
    abstract class BaseFactory : Factory {
        private val defaultRequestProperties: RequestProperties

        init {
            defaultRequestProperties = RequestProperties()
        }

        override fun createDataSource(): HttpDataSource {
            return createDataSourceInternal(defaultRequestProperties)
        }

        @CanIgnoreReturnValue
        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>?): Factory {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
            return this
        }

        /**
         * Called by [.createDataSource] to create a [HttpDataSource] instance.
         *
         * @param defaultRequestProperties The default `RequestProperties` to be used by the
         * [HttpDataSource] instance.
         * @return A [HttpDataSource] instance.
         */
        protected abstract fun createDataSourceInternal(defaultRequestProperties: RequestProperties?): HttpDataSource
    }

    /**
     * Thrown when an error is encountered when trying to read from a [HttpDataSource].
     */
    open class HttpDataSourceException : DataSourceException {
        /**
         * The type of operation that produced the error. One of [.TYPE_READ], [.TYPE_OPEN]
         * [.TYPE_CLOSE].
         */
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef(value = [TYPE_OPEN, TYPE_READ, TYPE_CLOSE])
        annotation class Type

        /**
         * The [DataSpec] associated with the current connection.
         */
        val dataSpec: DataSpec?

        @JvmField
        @Type
        val type: Int

        @Deprecated(
            """Use {@link #HttpDataSourceException(DataSpec, int, int)
         * HttpDataSourceException(DataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}."""
        )
        constructor(dataSpec: DataSpec?, type: @Type Int) : this(
            dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type
        ) {
        }

        /**
         * Constructs an HttpDataSourceException.
         *
         * @param dataSpec  The [DataSpec].
         * @param errorCode Reason of the error, should be one of the `ERROR_CODE_IO_*` in [                  ].
         * @param type      See [Type].
         */
        constructor(
            dataSpec: DataSpec?, @PlaybackException.ErrorCode errorCode: Int, type: @Type Int
        ) : super(
            assignErrorCode(errorCode, type)
        ) {
            this.dataSpec = dataSpec
            this.type = type
        }

        @Deprecated(
            """Use {@link #HttpDataSourceException(String, DataSpec, int, int)
         * HttpDataSourceException(String, DataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
         * int)}."""
        )
        constructor(message: String?, dataSpec: DataSpec?, @Type type: Int) : this(
            message, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type
        ) {
        }

        /**
         * Constructs an HttpDataSourceException.
         *
         * @param message   The error message.
         * @param dataSpec  The [DataSpec].
         * @param errorCode Reason of the error, should be one of the `ERROR_CODE_IO_*` in [                  ].
         * @param type      See [Type].
         */
        constructor(
            message: String?,
            dataSpec: DataSpec?,
            @PlaybackException.ErrorCode errorCode: Int,
            type: @Type Int
        ) : super(message, assignErrorCode(errorCode, type)) {
            this.dataSpec = dataSpec
            this.type = type
        }

        @Deprecated(
            """Use {@link #HttpDataSourceException(IOException, DataSpec, int, int)
         * HttpDataSourceException(IOException, DataSpec,
         * PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}."""
        )
        constructor(cause: IOException?, dataSpec: DataSpec?, type: @Type Int) : this(
            cause, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type
        ) {
        }

        /**
         * Constructs an HttpDataSourceException.
         *
         * @param cause     The error cause.
         * @param dataSpec  The [DataSpec].
         * @param errorCode Reason of the error, should be one of the `ERROR_CODE_IO_*` in [                  ].
         * @param type      See [Type].
         */
        constructor(
            cause: IOException?,
            dataSpec: DataSpec?,
            @PlaybackException.ErrorCode errorCode: Int,
            type: @Type Int
        ) : super(cause, assignErrorCode(errorCode, type)) {
            this.dataSpec = dataSpec
            this.type = type
        }

        @Deprecated(
            """Use {@link #HttpDataSourceException(String, IOException, DataSpec, int, int)
         * HttpDataSourceException(String, IOException, DataSpec,
         * PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}."""
        )
        constructor(
            message: String?, cause: IOException?, dataSpec: DataSpec?, type: @Type Int
        ) : this(message, cause, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type) {
        }

        /**
         * Constructs an HttpDataSourceException.
         *
         * @param message   The error message.
         * @param cause     The error cause.
         * @param dataSpec  The [DataSpec].
         * @param errorCode Reason of the error, should be one of the `ERROR_CODE_IO_*` in [                  ].
         * @param type      See [Type].
         */
        constructor(
            message: String?,
            cause: IOException?,
            dataSpec: DataSpec?,
            @PlaybackException.ErrorCode errorCode: Int,
            type: @Type Int
        ) : super(message, cause, assignErrorCode(errorCode, type)) {
            this.dataSpec = dataSpec
            this.type = type
        }

        companion object {
            /**
             * The error occurred reading data from a `HttpDataSource`.
             */
            const val TYPE_OPEN = 1

            /**
             * The error occurred in opening a `HttpDataSource`.
             */
            const val TYPE_READ = 2

            /**
             * The error occurred in closing a `HttpDataSource`.
             */
            const val TYPE_CLOSE = 3

            /**
             * Returns a `HttpDataSourceException` whose error code is assigned according to the cause
             * and type.
             */
            @JvmStatic
            fun createForIOException(
                cause: IOException, dataSpec: DataSpec?, type: @Type Int
            ): HttpDataSourceException {
                @PlaybackException.ErrorCode val errorCode: Int
                val message = cause.message
                errorCode = if (cause is SocketTimeoutException) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                } else if (cause is InterruptedIOException) {
                    // An interruption means the operation is being cancelled, in which case this exception
                    // should not cause the player to fail. If it does, it likely means that the owner of the
                    // operation is failing to swallow the interruption, which makes us enter an invalid state.
                    PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
                } else if (message != null && Ascii.toLowerCase(message)
                        .matches("cleartext.*not permitted.*")
                ) {
                    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                } else {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                }
                return if (errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) CleartextNotPermittedException(
                    cause, dataSpec
                ) else HttpDataSourceException(cause, dataSpec, errorCode, type)
            }

            @PlaybackException.ErrorCode
            private fun assignErrorCode(
                @PlaybackException.ErrorCode errorCode: Int, type: @Type Int
            ): Int {
                return if (errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED && type == TYPE_OPEN) PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED else errorCode
            }
        }
    }

    /**
     * Thrown when cleartext HTTP traffic is not permitted. For more information including how to
     * enable cleartext traffic, see the [corresponding troubleshooting
 * topic](https://exoplayer.dev/issues/cleartext-not-permitted).
     */
    class CleartextNotPermittedException(cause: IOException?, dataSpec: DataSpec?) :
        HttpDataSourceException(
            "Cleartext HTTP traffic not permitted. See" + " https://exoplayer.dev/issues/cleartext-not-permitted",
            cause,
            dataSpec,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            TYPE_OPEN
        )

    /**
     * Thrown when the content type is invalid.
     */
    class InvalidContentTypeException(val contentType: String, dataSpec: DataSpec?) :
        HttpDataSourceException(
            "Invalid content type: $contentType",
            dataSpec,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            TYPE_OPEN
        )

    /**
     * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
     */
    class InvalidResponseCodeException : HttpDataSourceException {


        /** The response code that was outside of the 2xx range.  */
        var responseCode = 0

        /** The http status message.  */
        var responseMessage: String? = null

        /** An unmodifiable map of the response header fields and values.  */
        var headerFields: Map<String, List<String>>? = null

        /** The response body.  */
        val responseBody: ByteArray


        @Deprecated(
            """Use {@link #InvalidResponseCodeException(int, String, IOException, Map, DataSpec,
     *     byte[])}."""
        )
        constructor(
            responseCode: Int, headerFields: Map<String, List<String>>?, dataSpec: DataSpec?
        ) : this(
            responseCode,  /* responseMessage= */
            null,  /* cause= */
            null, headerFields, dataSpec,  /* responseBody= */
            Util.EMPTY_BYTE_ARRAY
        ) {

        }

        @Deprecated(
            """Use {@link #InvalidResponseCodeException(int, String, IOException, Map, DataSpec,
     *     byte[])}."""
        )
        constructor(
            responseCode: Int,
            responseMessage: String?,
            headerFields: Map<String, List<String>>?,
            dataSpec: DataSpec?
        ) : this(
            responseCode, responseMessage,  /* cause= */
            null, headerFields, dataSpec,  /* responseBody= */
            Util.EMPTY_BYTE_ARRAY
        ) {

        }

        constructor(
            responseCode: Int,
            responseMessage: String?,
            cause: IOException?,
            headerFields: Map<String, List<String>>?,
            dataSpec: DataSpec?,
            responseBody: ByteArray
        ) : super(
            "Response code: $responseCode",
            cause,
            dataSpec,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            TYPE_OPEN
        ) {
            this.responseCode = responseCode
            this.responseMessage = responseMessage
            this.headerFields = headerFields
            this.responseBody = responseBody
        }
    }


    /**
     * Opens the source to read the specified data.
     *
     *
     * Note: [HttpDataSource] implementations are advised to set request headers passed via
     * (in order of decreasing priority) the `dataSpec`, [.setRequestProperty] and the
     * default parameters set in the [Factory].
     */
    @Throws(HttpDataSourceException::class)
    fun open(dataSpec: DataSpec?): Long

    @Throws(HttpDataSourceException::class)
    override fun close()

    @Throws(HttpDataSourceException::class)
    override fun read(buffer: ByteArray?, offset: Int, length: Int): Int

    /**
     * Sets the value of a request header. The value will be used for subsequent connections
     * established by the source.
     *
     *
     * Note: If the same header is set as a default parameter in the [Factory], then the
     * header value set with this method should be preferred when connecting with the data source. See
     * [.open].
     *
     * @param name The name of the header field.
     * @param value The value of the field.
     */
    fun setRequestProperty(name: String?, value: String?)

    /**
     * Clears the value of a request header. The change will apply to subsequent connections
     * established by the source.
     *
     * @param name The name of the header field.
     */
    fun clearRequestProperty(name: String?)

    /** Clears all request headers that were set by [.setRequestProperty].  */
    fun clearAllRequestProperties()

    /**
     * When the source is open, returns the HTTP response status code associated with the last [ ][.open] call. Otherwise, returns a negative value.
     */
    fun getResponseCode(): Int

    override fun getResponseHeaders(): Map<String, List<String>>
}