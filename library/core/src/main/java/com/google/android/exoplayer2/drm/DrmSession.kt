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
package com.google.android.exoplayer2.drm

import androidx.annotation.IntDef
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.decoder.CryptoConfig
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * A DRM session.
 */
interface DrmSession {

    /** Wraps the throwable which is the cause of the error state.  */
    class DrmSessionException : IOException {
        /** The [PlaybackException.ErrorCode] that corresponds to the failure.  */
        @PlaybackException.ErrorCode
        var errorCode = 0

        constructor(cause: Throwable?, @PlaybackException.ErrorCode errorCode: Int) : super(cause) {
            this.errorCode = errorCode
        }
    }

    /**
     * The state of the DRM session. One of [.STATE_RELEASED], [.STATE_ERROR], [ ][.STATE_OPENING], [.STATE_OPENED] or [.STATE_OPENED_WITH_KEYS].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([STATE_RELEASED, STATE_ERROR, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS])
    annotation class State

    /**
     * Returns the current state of the session, which is one of [.STATE_ERROR], [ ][.STATE_RELEASED], [.STATE_OPENING], [.STATE_OPENED] and [ ][.STATE_OPENED_WITH_KEYS].
     */
    @State
    fun getState(): Int

    /**
     * Returns whether this session allows playback of clear samples prior to keys being loaded.
     */
    fun playClearSamplesWithoutKeys(): Boolean {
        return false
    }

    /**
     * Returns the cause of the error state, or null if [.getState] is not [ ][.STATE_ERROR].
     */
    fun getError(): DrmSessionException?

    /**
     * Returns the DRM scheme UUID for this session.
     */
    fun getSchemeUuid(): UUID?

    /**
     * Returns a [CryptoConfig] for the open session, or null if called before the session has
     * been opened or after it's been released.
     */
    fun getCryptoConfig(): CryptoConfig?

    /**
     * Returns a map describing the key status for the session, or null if called before the session
     * has been opened or after it's been released.
     *
     *
     * Since DRM license policies vary by vendor, the specific status field names are determined by
     * each DRM vendor. Refer to your DRM provider documentation for definitions of the field names
     * for a particular DRM engine plugin.
     *
     * @return A map describing the key status for the session, or null if called before the session
     * has been opened or after it's been released.
     * @see MediaDrm.queryKeyStatus
     */
    fun queryKeyStatus(): Map<String?, String?>?

    /**
     * Returns the key set id of the offline license loaded into this session, or null if there isn't
     * one.
     */
    fun getOfflineLicenseKeySetId(): ByteArray?

    /**
     * Returns whether this session requires use of a secure decoder for the given MIME type. Assumes
     * a license policy that requires the highest level of security supported by the session.
     *
     *
     * The session must be in [state][.getState] [.STATE_OPENED] or [ ][.STATE_OPENED_WITH_KEYS].
     */
    fun requiresSecureDecoder(mimeType: String?): Boolean

    /**
     * Increments the reference count. When the caller no longer needs to use the instance, it must
     * call [.release] to decrement the reference
     * count.
     *
     * @param eventDispatcher The [DrmSessionEventListener.EventDispatcher] used to route
     * DRM-related events dispatched from this session, or null if no event handling is needed.
     */
    fun acquire(eventDispatcher: DrmSessionEventListener.EventDispatcher?)

    /**
     * Decrements the reference count. If the reference count drops to 0 underlying resources are
     * released, and the instance cannot be re-used.
     *
     * @param eventDispatcher The [DrmSessionEventListener.EventDispatcher] to disconnect when
     * the session is released (the same instance (possibly null) that was passed by the caller to
     * [.acquire]).
     */
    fun release(eventDispatcher: DrmSessionEventListener.EventDispatcher?)

    companion object {
        /**
         * Acquires `newSession` then releases `previousSession`.
         *
         * Invokes `newSession's` [.acquire] and
         * `previousSession's` [.release] in that
         * order (passing `eventDispatcher = null`). Null arguments are ignored. Does nothing if
         * `previousSession` and `newSession` are the same session.
         */
        @JvmStatic
        fun replaceSession(
                previousSession: DrmSession?, newSession: DrmSession?) {
            if (previousSession === newSession) {
                // Do nothing.
                return
            }
            newSession?.acquire( /* eventDispatcher= */null)
            previousSession?.release( /* eventDispatcher= */null)
        }

        /**
         * The session has been released. This is a terminal state.
         */
        const val STATE_RELEASED = 0

        /**
         * The session has encountered an error. [.getError] can be used to retrieve the cause.
         * This is a terminal state.
         */
        const val STATE_ERROR = 1

        /**
         * The session is being opened.
         */
        const val STATE_OPENING = 2

        /**
         * The session is open, but does not have keys required for decryption.
         */
        const val STATE_OPENED = 3

        /**
         * The session is open and has keys required for decryption.
         */
        const val STATE_OPENED_WITH_KEYS = 4
    }
}