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

import android.os.Looper
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException

/** Manages a DRM session.  */
interface DrmSessionManager {
    /**
     * Represents a single reference count of a [DrmSession], while deliberately not giving
     * access to the underlying session.
     */
    interface DrmSessionReference {
        companion object {
            /** A reference that is never populated with an underlying [DrmSession].  */
            @JvmField
            val EMPTY: DrmSessionReference = DrmSessionReference {}
        }

        /**
         * Releases the underlying session at most once.
         *
         *
         * Can be called from any thread. Calling this method more than once will only release the
         * underlying session once.
         */
        fun release()
    }

    /**
     * Acquires any required resources.
     *
     *
     * [.release] must be called to ensure the acquired resources are released. After
     * releasing, an instance may be re-prepared.
     */
    fun prepare() {
        // Do nothing.
    }

    /** Releases any acquired resources.  */
    fun release() {
        // Do nothing.
    }

    /**
     * Sets information about the player using this DRM session manager.
     *
     * @param playbackLooper The [Looper] associated with the player's playback thread.
     * @param playerId The [PlayerId] of the player.
     */
    fun setPlayer(playbackLooper: Looper?, playerId: PlayerId?)

    /**
     * Pre-acquires a DRM session for the specified [Format].
     *
     *
     * This notifies the manager that a subsequent call to [.acquireSession] with the same [Format] is likely,
     * allowing a manager that supports pre-acquisition to get the required [DrmSession] ready
     * in the background.
     *
     *
     * The caller must call [DrmSessionReference.release] on the returned instance when
     * they no longer require the pre-acquisition (i.e. they know they won't be making a matching call
     * to [.acquireSession] in the near
     * future).
     *
     *
     * This manager may silently release the underlying session in order to allow another operation
     * to complete. This will result in a subsequent call to [.acquireSession] re-initializing a new session, including
     * repeating key loads and other async initialization steps.
     *
     *
     * The caller must separately call [.acquireSession] in order to obtain a session suitable for
     * playback. The pre-acquired [DrmSessionReference] and full [DrmSession] instances
     * are distinct. The caller must release both, and can release the [DrmSessionReference]
     * before the [DrmSession] without affecting playback.
     *
     *
     * This can be called from any thread.
     *
     *
     * Implementations that do not support pre-acquisition always return an empty [ ] instance.
     *
     * @param eventDispatcher The [DrmSessionEventListener.EventDispatcher] used to distribute
     * events, and passed on to [     ][DrmSession.acquire].
     * @param format The [Format] for which to pre-acquire a [DrmSession].
     * @return A releaser for the pre-acquired session. Guaranteed to be non-null even if the matching
     * [.acquireSession] would return null.
     */
    fun preacquireSession(eventDispatcher: DrmSessionEventListener.EventDispatcher?, format: Format?): DrmSessionReference? {
        return DrmSessionReference.EMPTY
    }

    /**
     * Returns a [DrmSession] for the specified [Format], with an incremented reference
     * count. May return null if the [Format.drmInitData] is null and the DRM session manager is
     * not configured to attach a [DrmSession] to clear content. When the caller no longer needs
     * to use a returned [DrmSession], it must call [ ][DrmSession.release] to decrement the reference count.
     *
     *
     * If the provided [Format] contains a null [Format.drmInitData], the returned
     * [DrmSession] (if not null) will be a placeholder session which does not execute key
     * requests, and cannot be used to handle encrypted content. However, a placeholder session may be
     * used to configure secure decoders for playback of clear content periods, which can reduce the
     * cost of transitioning between clear and encrypted content.
     *
     * @param eventDispatcher The [DrmSessionEventListener.EventDispatcher] used to distribute
     * events, and passed on to [     ][DrmSession.acquire].
     * @param format The [Format] for which to acquire a [DrmSession].
     * @return The DRM session. May be null if the given [Format.drmInitData] is null.
     */
    fun acquireSession(eventDispatcher: DrmSessionEventListener.EventDispatcher?, format: Format): DrmSession?

    /**
     * Returns the [C.CryptoType] that the DRM session manager will use for a given [ ]. Returns [C.CRYPTO_TYPE_UNSUPPORTED] if the manager does not support any of the
     * DRM schemes defined in the [Format]. Returns [C.CRYPTO_TYPE_NONE] if [ ][Format.drmInitData] is null and [.acquireSession] will return `null` for the given
     * [Format].
     *
     * @param format The [Format].
     * @return The [C.CryptoType] that the manager will use, or @link C#CRYPTO_TYPE_UNSUPPORTED}
     * if the manager does not support any of the DRM schemes defined in the [Format]. Will
     * be [C.CRYPTO_TYPE_NONE] if [Format.drmInitData] is null and [     ][.acquireSession] will return null for the given [Format].
     */
    @CryptoType
    fun getCryptoType(format: Format): Int

    companion object {
        /**
         * Returns [.DRM_UNSUPPORTED].
         *
         */
        @Deprecated("")
        fun getDummyDrmSessionManager(): DrmSessionManager? {
            return DRM_UNSUPPORTED
        }

        /** An instance that supports no DRM schemes.  */
        val DRM_UNSUPPORTED: DrmSessionManager = object : DrmSessionManager {
            override fun setPlayer(playbackLooper: Looper?, playerId: PlayerId?) {}

            override fun acquireSession(
                    eventDispatcher: DrmSessionEventListener.EventDispatcher?, format: Format): DrmSession? {
                return if (format.drmInitData == null) {
                    null
                } else {
                    ErrorStateDrmSession(
                            DrmSessionException(
                                    UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME),
                                    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED))
                }
            }

            @CryptoType
            override fun getCryptoType(format: Format): Int {
                return if (format.drmInitData != null) C.CRYPTO_TYPE_UNSUPPORTED else C.CRYPTO_TYPE_NONE
            }
        }

        /**
         * An instance that supports no DRM schemes.
         *
         */
        @Deprecated("Use {@link #DRM_UNSUPPORTED}.")
        val DUMMY = DRM_UNSUPPORTED
    }
}