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
package com.google.android.exoplayer2

import android.net.ConnectivityManager
import android.os.*
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.*
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.util.*
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Thrown when a non locally recoverable playback failure occurs.  */
open class PlaybackException
/**
 * Creates an instance.
 *
 * @param errorCode A number which identifies the cause of the error. May be one of the [     ].
 * @param cause See [.getCause].
 * @param message See [.getMessage].
 */ @JvmOverloads constructor(
    message: String?, cause: Throwable?,
    /** An error code which identifies the cause of the playback failure.  */
    val errorCode: @PlaybackException.ErrorCode Int,
    /** The value of [SystemClock.elapsedRealtime] when this exception was created.  */
    val timestampMs: Long = Clock.Companion.DEFAULT.elapsedRealtime()
) : Exception(message, cause), Bundleable {
    /**
     * Codes that identify causes of player errors.
     *
     *
     * This list of errors may be extended in future versions, and [Player] implementations
     * may define custom error codes.
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.LOCAL_VARIABLE,
        TYPE_USE
    )
    @IntDef(
        open = true,
        value = [PlaybackException.Companion.ERROR_CODE_UNSPECIFIED, PlaybackException.Companion.ERROR_CODE_REMOTE_ERROR, PlaybackException.Companion.ERROR_CODE_BEHIND_LIVE_WINDOW, PlaybackException.Companion.ERROR_CODE_TIMEOUT, PlaybackException.Companion.ERROR_CODE_FAILED_RUNTIME_CHECK, PlaybackException.Companion.ERROR_CODE_IO_UNSPECIFIED, PlaybackException.Companion.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.Companion.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.Companion.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, PlaybackException.Companion.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.Companion.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.Companion.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.Companion.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, PlaybackException.Companion.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, PlaybackException.Companion.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.Companion.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.Companion.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.Companion.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, PlaybackException.Companion.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.Companion.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.Companion.ERROR_CODE_DECODING_FAILED, PlaybackException.Companion.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.Companion.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.Companion.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, PlaybackException.Companion.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.Companion.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.Companion.ERROR_CODE_DRM_SCHEME_UNSUPPORTED, PlaybackException.Companion.ERROR_CODE_DRM_PROVISIONING_FAILED, PlaybackException.Companion.ERROR_CODE_DRM_CONTENT_ERROR, PlaybackException.Companion.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, PlaybackException.Companion.ERROR_CODE_DRM_DISALLOWED_OPERATION, PlaybackException.Companion.ERROR_CODE_DRM_SYSTEM_ERROR, PlaybackException.Companion.ERROR_CODE_DRM_DEVICE_REVOKED, PlaybackException.Companion.ERROR_CODE_DRM_LICENSE_EXPIRED]
    )
    annotation class ErrorCode constructor()

    /**
     * Equivalent to [ PlaybackException.getErrorCodeName(this.errorCode)][PlaybackException.getErrorCodeName].
     */
    val errorCodeName: String
        get() {
            return PlaybackException.Companion.getErrorCodeName(errorCode)
        }

    /** Creates a new instance using the fields obtained from the given [Bundle].  */
    protected constructor(bundle: Bundle) : this( /* message= */
        bundle.getString(PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_MESSAGE)),  /* cause= */
        PlaybackException.Companion.getCauseFromBundle(bundle),  /* errorCode= */
        bundle.getInt(
            PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_INT_ERROR_CODE),  /* defaultValue= */
            PlaybackException.Companion.ERROR_CODE_UNSPECIFIED
        ),  /* timestampMs= */
        bundle.getLong(
            PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_LONG_TIMESTAMP_MS),  /* defaultValue= */
            SystemClock.elapsedRealtime()
        )
    ) {
    }

    /**
     * Returns whether the error data associated to this exception equals the error data associated to
     * `other`.
     *
     *
     * Note that this method does not compare the exceptions' stacktraces.
     */
    @CallSuper
    open fun errorInfoEquals(other: PlaybackException?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val thisCause: Throwable? = cause
        val thatCause: Throwable? = other.cause
        if (thisCause != null && thatCause != null) {
            if (!Util.areEqual(thisCause.message, thatCause.message)) {
                return false
            }
            if (!Util.areEqual(thisCause.javaClass, thatCause.javaClass)) {
                return false
            }
        } else if (thisCause != null || thatCause != null) {
            return false
        }
        return ((errorCode == other.errorCode) && Util.areEqual(
            message,
            other.message
        ) && (timestampMs == other.timestampMs))
    }

    /** Creates a new instance using the given values.  */
    @CallSuper
    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putInt(
            PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_INT_ERROR_CODE),
            errorCode
        )
        bundle.putLong(
            PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_LONG_TIMESTAMP_MS),
            timestampMs
        )
        bundle.putString(
            PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_MESSAGE),
            message
        )
        val cause: Throwable? = cause
        if (cause != null) {
            bundle.putString(
                PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_CAUSE_CLASS_NAME),
                cause.javaClass.getName()
            )
            bundle.putString(
                PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_CAUSE_MESSAGE),
                cause.message
            )
        }
        return bundle
    }

    companion object {
        // Miscellaneous errors (1xxx).
        /** Caused by an error whose cause could not be identified.  */
        val ERROR_CODE_UNSPECIFIED: Int = 1000

        /**
         * Caused by an unidentified error in a remote Player, which is a Player that runs on a different
         * host or process.
         */
        val ERROR_CODE_REMOTE_ERROR: Int = 1001

        /** Caused by the loading position falling behind the sliding window of available live content.  */
        val ERROR_CODE_BEHIND_LIVE_WINDOW: Int = 1002

        /** Caused by a generic timeout.  */
        val ERROR_CODE_TIMEOUT: Int = 1003

        /**
         * Caused by a failed runtime check.
         *
         *
         * This can happen when the application fails to comply with the player's API requirements (for
         * example, by passing invalid arguments), or when the player reaches an invalid state.
         */
        val ERROR_CODE_FAILED_RUNTIME_CHECK: Int = 1004
        // Input/Output errors (2xxx).
        /** Caused by an Input/Output error which could not be identified.  */
        val ERROR_CODE_IO_UNSPECIFIED: Int = 2000

        /**
         * Caused by a network connection failure.
         *
         *
         * The following is a non-exhaustive list of possible reasons:
         *
         *
         *  * There is no network connectivity (you can check this by querying [       ][ConnectivityManager.getActiveNetwork]).
         *  * The URL's domain is misspelled or does not exist.
         *  * The target host is unreachable.
         *  * The server unexpectedly closes the connection.
         *
         */
        val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: Int = 2001

        /** Caused by a network timeout, meaning the server is taking too long to fulfill a request.  */
        val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: Int = 2002

        /**
         * Caused by a server returning a resource with an invalid "Content-Type" HTTP header value.
         *
         *
         * For example, this can happen when the player is expecting a piece of media, but the server
         * returns a paywall HTML page, with content type "text/html".
         */
        val ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE: Int = 2003

        /** Caused by an HTTP server returning an unexpected HTTP response status code.  */
        val ERROR_CODE_IO_BAD_HTTP_STATUS: Int = 2004

        /** Caused by a non-existent file.  */
        val ERROR_CODE_IO_FILE_NOT_FOUND: Int = 2005

        /**
         * Caused by lack of permission to perform an IO operation. For example, lack of permission to
         * access internet or external storage.
         */
        val ERROR_CODE_IO_NO_PERMISSION: Int = 2006

        /**
         * Caused by the player trying to access cleartext HTTP traffic (meaning http:// rather than
         * https://) when the app's Network Security Configuration does not permit it.
         *
         *
         * See [this corresponding
 * troubleshooting topic](https://exoplayer.dev/issues/cleartext-not-permitted).
         */
        val ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED: Int = 2007

        /** Caused by reading data out of the data bound.  */
        val ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE: Int = 2008
        // Content parsing errors (3xxx).
        /** Caused by a parsing error associated with a media container format bitstream.  */
        val ERROR_CODE_PARSING_CONTAINER_MALFORMED: Int = 3001

        /**
         * Caused by a parsing error associated with a media manifest. Examples of a media manifest are a
         * DASH or a SmoothStreaming manifest, or an HLS playlist.
         */
        val ERROR_CODE_PARSING_MANIFEST_MALFORMED: Int = 3002

        /**
         * Caused by attempting to extract a file with an unsupported media container format, or an
         * unsupported media container feature.
         */
        val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED: Int = 3003

        /**
         * Caused by an unsupported feature in a media manifest. Examples of a media manifest are a DASH
         * or a SmoothStreaming manifest, or an HLS playlist.
         */
        val ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED: Int = 3004
        // Decoding errors (4xxx).
        /** Caused by a decoder initialization failure.  */
        val ERROR_CODE_DECODER_INIT_FAILED: Int = 4001

        /** Caused by a decoder query failure.  */
        val ERROR_CODE_DECODER_QUERY_FAILED: Int = 4002

        /** Caused by a failure while trying to decode media samples.  */
        val ERROR_CODE_DECODING_FAILED: Int = 4003

        /** Caused by trying to decode content whose format exceeds the capabilities of the device.  */
        val ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES: Int = 4004

        /** Caused by trying to decode content whose format is not supported.  */
        val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED: Int = 4005
        // AudioTrack errors (5xxx).
        /** Caused by an AudioTrack initialization failure.  */
        val ERROR_CODE_AUDIO_TRACK_INIT_FAILED: Int = 5001

        /** Caused by an AudioTrack write operation failure.  */
        val ERROR_CODE_AUDIO_TRACK_WRITE_FAILED: Int = 5002
        // DRM errors (6xxx).
        /** Caused by an unspecified error related to DRM protection.  */
        val ERROR_CODE_DRM_UNSPECIFIED: Int = 6000

        /**
         * Caused by a chosen DRM protection scheme not being supported by the device. Examples of DRM
         * protection schemes are ClearKey and Widevine.
         */
        val ERROR_CODE_DRM_SCHEME_UNSUPPORTED: Int = 6001

        /** Caused by a failure while provisioning the device.  */
        val ERROR_CODE_DRM_PROVISIONING_FAILED: Int = 6002

        /**
         * Caused by attempting to play incompatible DRM-protected content.
         *
         *
         * For example, this can happen when attempting to play a DRM protected stream using a scheme
         * (like Widevine) for which there is no corresponding license acquisition data (like a pssh box).
         */
        val ERROR_CODE_DRM_CONTENT_ERROR: Int = 6003

        /** Caused by a failure while trying to obtain a license.  */
        val ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED: Int = 6004

        /** Caused by an operation being disallowed by a license policy.  */
        val ERROR_CODE_DRM_DISALLOWED_OPERATION: Int = 6005

        /** Caused by an error in the DRM system.  */
        val ERROR_CODE_DRM_SYSTEM_ERROR: Int = 6006

        /** Caused by the device having revoked DRM privileges.  */
        val ERROR_CODE_DRM_DEVICE_REVOKED: Int = 6007

        /** Caused by an expired DRM license being loaded into an open DRM session.  */
        val ERROR_CODE_DRM_LICENSE_EXPIRED: Int = 6008

        /**
         * Player implementations that want to surface custom errors can use error codes greater than this
         * value, so as to avoid collision with other error codes defined in this class.
         */
        val CUSTOM_ERROR_CODE_BASE: Int = 1000000

        /** Returns the name of a given `errorCode`.  */
        fun getErrorCodeName(errorCode: @PlaybackException.ErrorCode Int): String {
            when (errorCode) {
                PlaybackException.Companion.ERROR_CODE_UNSPECIFIED -> return "ERROR_CODE_UNSPECIFIED"
                PlaybackException.Companion.ERROR_CODE_REMOTE_ERROR -> return "ERROR_CODE_REMOTE_ERROR"
                PlaybackException.Companion.ERROR_CODE_BEHIND_LIVE_WINDOW -> return "ERROR_CODE_BEHIND_LIVE_WINDOW"
                PlaybackException.Companion.ERROR_CODE_TIMEOUT -> return "ERROR_CODE_TIMEOUT"
                PlaybackException.Companion.ERROR_CODE_FAILED_RUNTIME_CHECK -> return "ERROR_CODE_FAILED_RUNTIME_CHECK"
                PlaybackException.Companion.ERROR_CODE_IO_UNSPECIFIED -> return "ERROR_CODE_IO_UNSPECIFIED"
                PlaybackException.Companion.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> return "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
                PlaybackException.Companion.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> return "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"
                PlaybackException.Companion.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> return "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE"
                PlaybackException.Companion.ERROR_CODE_IO_BAD_HTTP_STATUS -> return "ERROR_CODE_IO_BAD_HTTP_STATUS"
                PlaybackException.Companion.ERROR_CODE_IO_FILE_NOT_FOUND -> return "ERROR_CODE_IO_FILE_NOT_FOUND"
                PlaybackException.Companion.ERROR_CODE_IO_NO_PERMISSION -> return "ERROR_CODE_IO_NO_PERMISSION"
                PlaybackException.Companion.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> return "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED"
                PlaybackException.Companion.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> return "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE"
                PlaybackException.Companion.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> return "ERROR_CODE_PARSING_CONTAINER_MALFORMED"
                PlaybackException.Companion.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> return "ERROR_CODE_PARSING_MANIFEST_MALFORMED"
                PlaybackException.Companion.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> return "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
                PlaybackException.Companion.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> return "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED"
                PlaybackException.Companion.ERROR_CODE_DECODER_INIT_FAILED -> return "ERROR_CODE_DECODER_INIT_FAILED"
                PlaybackException.Companion.ERROR_CODE_DECODER_QUERY_FAILED -> return "ERROR_CODE_DECODER_QUERY_FAILED"
                PlaybackException.Companion.ERROR_CODE_DECODING_FAILED -> return "ERROR_CODE_DECODING_FAILED"
                PlaybackException.Companion.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> return "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES"
                PlaybackException.Companion.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> return "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
                PlaybackException.Companion.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> return "ERROR_CODE_AUDIO_TRACK_INIT_FAILED"
                PlaybackException.Companion.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> return "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED"
                PlaybackException.Companion.ERROR_CODE_DRM_UNSPECIFIED -> return "ERROR_CODE_DRM_UNSPECIFIED"
                PlaybackException.Companion.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> return "ERROR_CODE_DRM_SCHEME_UNSUPPORTED"
                PlaybackException.Companion.ERROR_CODE_DRM_PROVISIONING_FAILED -> return "ERROR_CODE_DRM_PROVISIONING_FAILED"
                PlaybackException.Companion.ERROR_CODE_DRM_CONTENT_ERROR -> return "ERROR_CODE_DRM_CONTENT_ERROR"
                PlaybackException.Companion.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> return "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED"
                PlaybackException.Companion.ERROR_CODE_DRM_DISALLOWED_OPERATION -> return "ERROR_CODE_DRM_DISALLOWED_OPERATION"
                PlaybackException.Companion.ERROR_CODE_DRM_SYSTEM_ERROR -> return "ERROR_CODE_DRM_SYSTEM_ERROR"
                PlaybackException.Companion.ERROR_CODE_DRM_DEVICE_REVOKED -> return "ERROR_CODE_DRM_DEVICE_REVOKED"
                PlaybackException.Companion.ERROR_CODE_DRM_LICENSE_EXPIRED -> return "ERROR_CODE_DRM_LICENSE_EXPIRED"
                else -> if (errorCode >= PlaybackException.Companion.CUSTOM_ERROR_CODE_BASE) {
                    return "custom error code"
                } else {
                    return "invalid error code"
                }
            }
        }

        // Bundleable implementation.
        private val FIELD_INT_ERROR_CODE: Int = 0
        private val FIELD_LONG_TIMESTAMP_MS: Int = 1
        private val FIELD_STRING_MESSAGE: Int = 2
        private val FIELD_STRING_CAUSE_CLASS_NAME: Int = 3
        private val FIELD_STRING_CAUSE_MESSAGE: Int = 4

        /**
         * Defines a minimum field ID value for subclasses to use when implementing [.toBundle]
         * and [Bundleable.Creator].
         *
         *
         * Subclasses should obtain their [Bundle&#39;s][Bundle] field keys by applying a non-negative
         * offset on this constant and passing the result to [.keyForField].
         */
        protected val FIELD_CUSTOM_ID_BASE: Int = 1000

        /** Object that can create a [PlaybackException] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<PlaybackException> =
            Bundleable.Creator({ bundle: Bundle -> PlaybackException(bundle) })

        /**
         * Converts the given field number to a string which can be used as a field key when implementing
         * [.toBundle] and [Bundleable.Creator].
         *
         *
         * Subclasses should use `field` values greater than or equal to [ ][.FIELD_CUSTOM_ID_BASE].
         */
        protected fun keyForField(field: Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        // Creates a new {@link Throwable} with possibly {@code null} message.
        @Throws(Exception::class)
        private fun createThrowable(clazz: Class<*>, message: String?): Throwable {
            return clazz.getConstructor(String::class.java).newInstance(message) as Throwable
        }

        // Creates a new {@link RemoteException} with possibly {@code null} message.
        private fun createRemoteException(message: String?): RemoteException {
            return RemoteException(message)
        }

        private fun getCauseFromBundle(bundle: Bundle): Throwable? {
            val causeClassName: String? =
                bundle.getString(PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_CAUSE_CLASS_NAME))
            val causeMessage: String? =
                bundle.getString(PlaybackException.Companion.keyForField(PlaybackException.Companion.FIELD_STRING_CAUSE_MESSAGE))
            var cause: Throwable? = null
            if (!TextUtils.isEmpty(causeClassName)) {
                try {
                    val clazz: Class<*> = Class.forName(
                        causeClassName,  /* initialize= */
                        true, PlaybackException::class.java.getClassLoader()
                    )
                    if (Throwable::class.java.isAssignableFrom(clazz)) {
                        cause = PlaybackException.Companion.createThrowable(clazz, causeMessage)
                    }
                } catch (e: Throwable) {
                    // There was an error while creating the cause using reflection, do nothing here and let the
                    // finally block handle the issue.
                } finally {
                    if (cause == null) {
                        // The bundle has fields to represent the cause, but we were unable to re-create the
                        // exception using reflection. We instantiate a RemoteException to reflect this problem.
                        cause = PlaybackException.Companion.createRemoteException(causeMessage)
                    }
                }
            }
            return cause
        }
    }
}