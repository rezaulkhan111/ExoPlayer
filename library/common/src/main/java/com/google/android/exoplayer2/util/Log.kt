/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.text.TextUtilsimport

org.checkerframework.dataflow.qual.Pure androidx.annotation .*import androidx.annotation.Sizeimport

java.lang.annotation .Documentedimport java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicyimport java.net.UnknownHostException
/**
 * Wrapper around [android.util.Log] which allows to set the log level and to specify a custom
 * log output.
 */
object Log {
    /** Log level to log all messages.  */
    const val LOG_LEVEL_ALL = 0

    /** Log level to only log informative, warning and error messages.  */
    const val LOG_LEVEL_INFO = 1

    /** Log level to only log warning and error messages.  */
    const val LOG_LEVEL_WARNING = 2

    /** Log level to only log error messages.  */
    const val LOG_LEVEL_ERROR = 3

    /** Log level to disable all logging.  */
    const val LOG_LEVEL_OFF = Int.MAX_VALUE
    private val lock = Any()

    @GuardedBy("lock")
    private var logLevel = LOG_LEVEL_ALL

    @GuardedBy("lock")
    private var logStackTraces = true

    @GuardedBy("lock")
    private var logger = Logger.DEFAULT

    /** Returns current [LogLevel] for ExoPlayer logcat logging.  */
    @Pure
    fun getLogLevel(): @LogLevel Int {
        synchronized(lock) { return logLevel }
    }

    /**
     * Sets the [LogLevel] for ExoPlayer logcat logging.
     *
     * @param logLevel The new [LogLevel].
     */
    fun setLogLevel(logLevel: @LogLevel Int) {
        synchronized(lock) { Log.logLevel = logLevel }
    }

    /**
     * Sets whether stack traces of [Throwable]s will be logged to logcat. Stack trace logging
     * is enabled by default.
     *
     * @param logStackTraces Whether stack traces will be logged.
     */
    fun setLogStackTraces(logStackTraces: Boolean) {
        synchronized(lock) { Log.logStackTraces = logStackTraces }
    }

    /**
     * Sets a custom [Logger] as the output.
     *
     * @param logger The [Logger].
     */
    fun setLogger(logger: Logger) {
        synchronized(lock) { Log.logger = logger }
    }

    /**
     * @see android.util.Log.d
     */
    @Pure
    fun d(@Size(max = 23) tag: String?, message: String?) {
        synchronized(lock) {
            if (logLevel == LOG_LEVEL_ALL) {
                logger.d(tag, message)
            }
        }
    }

    /**
     * @see android.util.Log.d
     */
    @Pure
    fun d(@Size(max = 23) tag: String?, message: String, throwable: Throwable?) {
        d(tag, appendThrowableString(message, throwable))
    }

    /**
     * @see android.util.Log.i
     */
    @Pure
    fun i(@Size(max = 23) tag: String?, message: String?) {
        synchronized(lock) {
            if (logLevel <= LOG_LEVEL_INFO) {
                logger.i(tag, message)
            }
        }
    }

    /**
     * @see android.util.Log.i
     */
    @Pure
    fun i(@Size(max = 23) tag: String?, message: String, throwable: Throwable?) {
        i(tag, appendThrowableString(message, throwable))
    }

    /**
     * @see android.util.Log.w
     */
    @Pure
    fun w(@Size(max = 23) tag: String?, message: String?) {
        synchronized(lock) {
            if (logLevel <= LOG_LEVEL_WARNING) {
                logger.w(tag, message)
            }
        }
    }

    /**
     * @see android.util.Log.w
     */
    @Pure
    fun w(@Size(max = 23) tag: String?, message: String, throwable: Throwable?) {
        w(tag, appendThrowableString(message, throwable))
    }

    /**
     * @see android.util.Log.e
     */
    @Pure
    fun e(@Size(max = 23) tag: String?, message: String?) {
        synchronized(lock) {
            if (logLevel <= LOG_LEVEL_ERROR) {
                logger.e(tag, message)
            }
        }
    }

    /**
     * @see android.util.Log.e
     */
    @Pure
    fun e(@Size(max = 23) tag: String?, message: String, throwable: Throwable?) {
        e(tag, appendThrowableString(message, throwable))
    }

    /**
     * Returns a string representation of a [Throwable] suitable for logging, taking into
     * account whether [.setLogStackTraces] stack trace logging} is enabled.
     *
     *
     * Stack trace logging may be unconditionally suppressed for some expected failure modes (e.g.,
     * [Throwables][Throwable] that are expected if the device doesn't have network connectivity)
     * to avoid log spam.
     *
     * @param throwable The [Throwable].
     * @return The string representation of the [Throwable].
     */
    @Pure
    fun getThrowableString(throwable: Throwable?): String? {
        synchronized(lock) {
            return if (throwable == null) {
                null
            } else if (isCausedByUnknownHostException(throwable)) {
                // UnknownHostException implies the device doesn't have network connectivity.
                // UnknownHostException.getMessage() may return a string that's more verbose than desired
                // for
                // logging an expected failure mode. Conversely, android.util.Log.getStackTraceString has
                // special handling to return the empty string, which can result in logging that doesn't
                // indicate the failure mode at all. Hence we special case this exception to always return a
                // concise but useful message.
                "UnknownHostException (no network)"
            } else if (!logStackTraces) {
                throwable.message
            } else {
                android.util.Log.getStackTraceString(throwable).trim { it <= ' ' }.replace("\t", "    ")
            }
        }
    }

    @Pure
    private fun appendThrowableString(message: String, throwable: Throwable?): String {
        var message = message
        val throwableString = getThrowableString(throwable)
        if (!TextUtils.isEmpty(throwableString)) {
            message += """
  ${throwableString!!.replace("\n", "\n  ")}
"""
        }
        return message
    }

    @Pure
    private fun isCausedByUnknownHostException(throwable: Throwable?): Boolean {
        var throwable = throwable
        while (throwable != null) {
            if (throwable is UnknownHostException) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }

    /**
     * Log level for ExoPlayer logcat logging. One of [.LOG_LEVEL_ALL], [.LOG_LEVEL_INFO],
     * [.LOG_LEVEL_WARNING], [.LOG_LEVEL_ERROR] or [.LOG_LEVEL_OFF].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([LOG_LEVEL_ALL, LOG_LEVEL_INFO, LOG_LEVEL_WARNING, LOG_LEVEL_ERROR, LOG_LEVEL_OFF])
    annotation class LogLevel

    /**
     * Interface for a logger that can output messages with a tag.
     *
     *
     * Use [.DEFAULT] to output to [android.util.Log].
     */
    interface Logger {
        /**
         * Logs a debug-level message.
         *
         * @param tag The tag of the message.
         * @param message The message.
         */
        fun d(tag: String?, message: String?)

        /**
         * Logs an information-level message.
         *
         * @param tag The tag of the message.
         * @param message The message.
         */
        fun i(tag: String?, message: String?)

        /**
         * Logs a warning-level message.
         *
         * @param tag The tag of the message.
         * @param message The message.
         */
        fun w(tag: String?, message: String?)

        /**
         * Logs an error-level message.
         *
         * @param tag The tag of the message.
         * @param message The message.
         */
        fun e(tag: String?, message: String?)

        companion object {
            /** The default instance logging to [android.util.Log].  */
            val DEFAULT: Logger = object : Logger {
                override fun d(tag: String?, message: String?) {
                    android.util.Log.d(tag, message!!)
                }

                override fun i(tag: String?, message: String?) {
                    android.util.Log.i(tag, message!!)
                }

                override fun w(tag: String?, message: String?) {
                    android.util.Log.w(tag, message!!)
                }

                override fun e(tag: String?, message: String?) {
                    android.util.Log.e(tag, message!!)
                }
            }
        }
    }
}