/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.media.MediaSessionManager
import androidx.media2.common.*
import androidx.media2.session.MediaSession
import androidx.media2.session.SessionCommand
import androidx.media2.session.SessionCommandGroup
import androidx.media2.session.SessionResult
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.AllowedCommandProvider
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.DefaultAllowedCommandProvider
import com.google.android.exoplayer2.util.Assertions
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * Builds a [MediaSession.SessionCallback] with various collaborators.
 *
 * @see MediaSession.SessionCallback
 */
class SessionCallbackBuilder(context: Context?, sessionPlayerConnector: SessionPlayerConnector?) {
    private val context: Context
    private val sessionPlayerConnector: SessionPlayerConnector
    private var fastForwardMs = 0
    private var rewindMs = 0
    private var seekTimeoutMs: Int
    private var ratingCallback: RatingCallback? = null
    private var customCommandProvider: CustomCommandProvider? = null
    private var mediaItemProvider: MediaItemProvider? = null
    private var allowedCommandProvider: AllowedCommandProvider? = null
    private var skipCallback: SkipCallback? = null
    private var postConnectCallback: PostConnectCallback? = null
    private var disconnectedCallback: DisconnectedCallback? = null

    /** Provides allowed commands for [MediaController].  */
    interface AllowedCommandProvider {
        /**
         * Called to query whether to allow connection from the controller.
         *
         *
         * If it returns `true` to accept connection, then [.getAllowedCommands] will be
         * immediately followed to return initial allowed command.
         *
         *
         * Prefer use [PostConnectCallback] for any extra initialization about controller,
         * where controller is connected and session can send commands to the controller.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that is requesting
         * connect.
         * @return `true` to accept connection. `false` otherwise.
         */
        fun acceptConnection(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo): Boolean

        /**
         * Called to query allowed commands in following cases:
         *
         *
         *  * A [MediaController] requests to connect, and allowed commands is required to tell
         * initial allowed commands.
         *  * Underlying [SessionPlayer] state changes, and allowed commands may be updated via
         * [MediaSession.setAllowedCommands].
         *
         *
         *
         * The provided `baseAllowedSessionCommand` is built automatically based on the state
         * of the [SessionPlayer], [RatingCallback], [MediaItemProvider], [ ], and [SkipCallback] so may be a useful starting point for any
         * required customizations.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller for which allowed
         * commands are being queried.
         * @param baseAllowedSessionCommands Base allowed session commands for customization.
         * @return The allowed commands for the controller.
         * @see MediaSession.SessionCallback.onConnect
         */
        fun getAllowedCommands(
                session: MediaSession?,
                controllerInfo: MediaSession.ControllerInfo?,
                baseAllowedSessionCommands: SessionCommandGroup): SessionCommandGroup

        /**
         * Called when a [MediaController] has called an API that controls [SessionPlayer]
         * set to the [MediaSession].
         *
         * @param session The media session.
         * @param controllerInfo A [ControllerInfo] that needs allowed command update.
         * @param command A [SessionCommand] from the controller.
         * @return A session result code defined in [SessionResult].
         * @see MediaSession.SessionCallback.onCommandRequest
         */
        fun onCommandRequest(
                session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?, command: SessionCommand?): Int
    }

    /** Callback receiving a user rating for a specified media id.  */
    interface RatingCallback {
        /**
         * Called when the specified controller has set a rating for the specified media id.
         *
         * @see MediaSession.SessionCallback.onSetRating
         * @see androidx.media2.session.MediaController.setRating
         * @return One of the [SessionResult] `RESULT_*` constants describing the success or
         * failure of the operation, for example, [SessionResult.RESULT_SUCCESS] if the
         * operation succeeded.
         */
        fun onSetRating(session: MediaSession?, controller: MediaSession.ControllerInfo?, mediaId: String?, rating: Rating?): Int
    }

    /**
     * Callbacks for querying what custom commands are supported, and for handling a custom command
     * when a controller sends it.
     */
    interface CustomCommandProvider {
        /**
         * Called when a controller has sent a custom command.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that sent the custom
         * command.
         * @param customCommand A [SessionCommand] from the controller.
         * @param args A [Bundle] with the extra argument.
         * @see MediaSession.SessionCallback.onCustomCommand
         * @see androidx.media2.session.MediaController.sendCustomCommand
         */
        fun onCustomCommand(
                session: MediaSession?,
                controllerInfo: MediaSession.ControllerInfo?,
                customCommand: SessionCommand?,
                args: Bundle?): SessionResult?

        /**
         * Returns a [SessionCommandGroup] with custom commands to publish to the controller, or
         * `null` if no custom commands should be published.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that is requesting custom
         * commands.
         * @return The custom commands to publish, or `null` if no custom commands should be
         * published.
         */
        fun getCustomCommands(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?): SessionCommandGroup?
    }

    /** Provides the [MediaItem].  */
    interface MediaItemProvider {
        /**
         * Called when [MediaSession.SessionCallback.onCreateMediaItem] is called.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that has requested to
         * create the item.
         * @return A new [MediaItem] that [SessionPlayerConnector] can play.
         * @see MediaSession.SessionCallback.onCreateMediaItem
         * @see androidx.media2.session.MediaController.addPlaylistItem
         * @see androidx.media2.session.MediaController.replacePlaylistItem
         * @see androidx.media2.session.MediaController.setMediaItem
         * @see androidx.media2.session.MediaController.setPlaylist
         */
        fun onCreateMediaItem(
                session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?, mediaId: String?): MediaItem?
    }

    /** Callback receiving skip backward and skip forward.  */
    interface SkipCallback {
        /**
         * Called when the specified controller has sent skip backward.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that has requested to
         * skip backward.
         * @see MediaSession.SessionCallback.onSkipBackward
         * @see MediaController.skipBackward
         * @return One of the [SessionResult] `RESULT_*` constants describing the success or
         * failure of the operation, for example, [SessionResult.RESULT_SUCCESS] if the
         * operation succeeded.
         */
        fun onSkipBackward(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?): Int

        /**
         * Called when the specified controller has sent skip forward.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that has requested to
         * skip forward.
         * @see MediaSession.SessionCallback.onSkipForward
         * @see MediaController.skipForward
         * @return One of the [SessionResult] `RESULT_*` constants describing the success or
         * failure of the operation, for example, [SessionResult.RESULT_SUCCESS] if the
         * operation succeeded.
         */
        fun onSkipForward(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?): Int
    }

    /** Callback for handling extra initialization after the connection.  */
    interface PostConnectCallback {
        /**
         * Called after the specified controller is connected, and you need extra initialization.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the controller that just connected.
         * @see MediaSession.SessionCallback.onPostConnect
         */
        fun onPostConnect(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?)
    }

    /** Callback for handling controller disconnection.  */
    interface DisconnectedCallback {
        /**
         * Called when the specified controller is disconnected.
         *
         * @param session The media session.
         * @param controllerInfo The [ControllerInfo] for the disconnected controller.
         * @see MediaSession.SessionCallback.onDisconnected
         */
        fun onDisconnected(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?)
    }

    /**
     * Default implementation of [AllowedCommandProvider] that behaves as follows:
     *
     *
     *  * Accepts connection requests from controller if any of the following conditions are met:
     *
     *  * Controller is in the same package as the session.
     *  * Controller is allowed via [.setTrustedPackageNames].
     *  * Controller has package name [RemoteUserInfo.LEGACY_CONTROLLER]. See [             ][ControllerInfo.getPackageName] for details.
     *  * Controller is trusted (i.e. has MEDIA_CONTENT_CONTROL permission or has enabled
     * notification manager).
     *
     *  * Allows all commands that the current player can handle.
     *  * Accepts all command requests for allowed commands.
     *
     *
     *
     * Note: this implementation matches the behavior of the ExoPlayer MediaSession extension and
     * [android.support.v4.media.session.MediaSessionCompat].
     */
    class DefaultAllowedCommandProvider(private val context: Context) : AllowedCommandProvider {
        private val trustedPackageNames: MutableList<String>

        init {
            trustedPackageNames = ArrayList()
        }

        override fun acceptConnection(session: MediaSession?, controllerInfo: MediaSession.ControllerInfo): Boolean {
            return (TextUtils.equals(controllerInfo.packageName, context.packageName)
                    || TextUtils.equals(controllerInfo.packageName, MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER)
                    || trustedPackageNames.contains(controllerInfo.packageName)
                    || isTrusted(controllerInfo))
        }

        override fun getAllowedCommands(
                session: MediaSession?,
                controllerInfo: MediaSession.ControllerInfo?,
                baseAllowedSessionCommands: SessionCommandGroup): SessionCommandGroup {
            return baseAllowedSessionCommands
        }

        override fun onCommandRequest(
                session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?, command: SessionCommand?): Int {
            return SessionResult.RESULT_SUCCESS
        }

        /**
         * Sets the package names from which the session will accept incoming connections.
         *
         *
         * Apps that have `android.Manifest.permission.MEDIA_CONTENT_CONTROL`, packages listed
         * in enabled_notification_listeners and the current package are always trusted, even if they
         * are not specified here.
         *
         * @param packageNames Package names from which the session will accept incoming connections.
         * @see MediaSession.SessionCallback.onConnect
         * @see MediaSessionManager.isTrustedForMediaControl
         */
        fun setTrustedPackageNames(packageNames: List<String>?) {
            trustedPackageNames.clear()
            if (packageNames != null && !packageNames.isEmpty()) {
                trustedPackageNames.addAll(packageNames)
            }
        }

        // TODO: Replace with ControllerInfo#isTrusted() when it's unhidden [Internal: b/142835448].
        private fun isTrusted(controllerInfo: MediaSession.ControllerInfo): Boolean {
            // Check whether the controller has granted MEDIA_CONTENT_CONTROL.
            if (context
                            .packageManager
                            .checkPermission(
                                    Manifest.permission.MEDIA_CONTENT_CONTROL, controllerInfo.packageName)
                    == PackageManager.PERMISSION_GRANTED) {
                return true
            }

            // Check whether the app has an enabled notification listener.
            val enabledNotificationListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(enabledNotificationListeners)) {
                val components = enabledNotificationListeners.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (componentString in components) {
                    val component = ComponentName.unflattenFromString(componentString)
                    if (component != null) {
                        if (component.packageName == controllerInfo.packageName) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    /** A [MediaItemProvider] that creates media items containing only a media ID.  */
    class MediaIdMediaItemProvider : MediaItemProvider {
        override fun onCreateMediaItem(
                session: MediaSession?, controllerInfo: MediaSession.ControllerInfo?, mediaId: String?): MediaItem? {
            if (TextUtils.isEmpty(mediaId)) {
                return null
            }
            val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                    .build()
            return MediaItem.Builder().setMetadata(metadata).build()
        }
    }

    /**
     * Creates a new builder.
     *
     *
     * The builder uses the following default values:
     *
     *
     *  * [AllowedCommandProvider]: [DefaultAllowedCommandProvider]
     *  * Seek timeout: [.DEFAULT_SEEK_TIMEOUT_MS]
     *  *
     *
     *
     * Unless stated above, `null` or `0` would be used to disallow relevant features.
     *
     * @param context A context.
     * @param sessionPlayerConnector A session player connector to handle incoming calls from the
     * controller.
     */
    init {
        this.context = Assertions.checkNotNull(context)
        this.sessionPlayerConnector = Assertions.checkNotNull(sessionPlayerConnector)
        seekTimeoutMs = DEFAULT_SEEK_TIMEOUT_MS
    }

    /**
     * Sets the [RatingCallback] to handle user ratings.
     *
     * @param ratingCallback A rating callback.
     * @return This builder.
     * @see MediaSession.SessionCallback.onSetRating
     * @see androidx.media2.session.MediaController.setRating
     */
    @CanIgnoreReturnValue
    fun setRatingCallback(ratingCallback: RatingCallback?): SessionCallbackBuilder {
        this.ratingCallback = ratingCallback
        return this
    }

    /**
     * Sets the [CustomCommandProvider] to handle incoming custom commands.
     *
     * @param customCommandProvider A custom command provider.
     * @return This builder.
     * @see MediaSession.SessionCallback.onCustomCommand
     * @see androidx.media2.session.MediaController.sendCustomCommand
     */
    @CanIgnoreReturnValue
    fun setCustomCommandProvider(
            customCommandProvider: CustomCommandProvider?): SessionCallbackBuilder {
        this.customCommandProvider = customCommandProvider
        return this
    }

    /**
     * Sets the [MediaItemProvider] that will convert media ids to [MediaItems][MediaItem].
     *
     * @param mediaItemProvider The media item provider.
     * @return This builder.
     * @see MediaSession.SessionCallback.onCreateMediaItem
     * @see androidx.media2.session.MediaController.addPlaylistItem
     * @see androidx.media2.session.MediaController.replacePlaylistItem
     * @see androidx.media2.session.MediaController.setMediaItem
     * @see androidx.media2.session.MediaController.setPlaylist
     */
    @CanIgnoreReturnValue
    fun setMediaItemProvider(
            mediaItemProvider: MediaItemProvider?): SessionCallbackBuilder {
        this.mediaItemProvider = mediaItemProvider
        return this
    }

    /**
     * Sets the [AllowedCommandProvider] to provide allowed commands for controllers.
     *
     * @param allowedCommandProvider A allowed command provider.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setAllowedCommandProvider(
            allowedCommandProvider: AllowedCommandProvider?): SessionCallbackBuilder {
        this.allowedCommandProvider = allowedCommandProvider
        return this
    }

    /**
     * Sets the [SkipCallback] to handle skip backward and skip forward.
     *
     * @param skipCallback The skip callback.
     * @return This builder.
     * @see MediaSession.SessionCallback.onSkipBackward
     * @see MediaSession.SessionCallback.onSkipForward
     * @see MediaController.skipBackward
     * @see MediaController.skipForward
     */
    @CanIgnoreReturnValue
    fun setSkipCallback(skipCallback: SkipCallback?): SessionCallbackBuilder {
        this.skipCallback = skipCallback
        return this
    }

    /**
     * Sets the [PostConnectCallback] to handle extra initialization after the connection.
     *
     * @param postConnectCallback The post connect callback.
     * @return This builder.
     * @see MediaSession.SessionCallback.onPostConnect
     */
    @CanIgnoreReturnValue
    fun setPostConnectCallback(
            postConnectCallback: PostConnectCallback?): SessionCallbackBuilder {
        this.postConnectCallback = postConnectCallback
        return this
    }

    /**
     * Sets the [DisconnectedCallback] to handle cleaning up controller.
     *
     * @param disconnectedCallback The disconnected callback.
     * @return This builder.
     * @see MediaSession.SessionCallback.onDisconnected
     */
    @CanIgnoreReturnValue
    fun setDisconnectedCallback(
            disconnectedCallback: DisconnectedCallback?): SessionCallbackBuilder {
        this.disconnectedCallback = disconnectedCallback
        return this
    }

    /**
     * Sets the rewind increment in milliseconds.
     *
     * @param rewindMs The rewind increment in milliseconds. A non-positive value will cause the
     * rewind to be disabled.
     * @return This builder.
     * @see MediaSession.SessionCallback.onRewind
     * @see .setSeekTimeoutMs
     */
    @CanIgnoreReturnValue
    fun setRewindIncrementMs(rewindMs: Int): SessionCallbackBuilder {
        this.rewindMs = rewindMs
        return this
    }

    /**
     * Sets the fast forward increment in milliseconds.
     *
     * @param fastForwardMs The fast forward increment in milliseconds. A non-positive value will
     * cause the fast forward to be disabled.
     * @return This builder.
     * @see MediaSession.SessionCallback.onFastForward
     * @see .setSeekTimeoutMs
     */
    @CanIgnoreReturnValue
    fun setFastForwardIncrementMs(fastForwardMs: Int): SessionCallbackBuilder {
        this.fastForwardMs = fastForwardMs
        return this
    }

    /**
     * Sets the timeout in milliseconds for fast forward and rewind operations, or `0` for no
     * timeout. If a timeout is set, controllers will receive an error if the session's call to [ ][SessionPlayer.seekTo] takes longer than this amount of time.
     *
     * @param seekTimeoutMs A timeout for [SessionPlayer.seekTo]. A non-positive value will wait
     * forever.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    fun setSeekTimeoutMs(seekTimeoutMs: Int): SessionCallbackBuilder {
        this.seekTimeoutMs = seekTimeoutMs
        return this
    }

    /**
     * Builds [MediaSession.SessionCallback].
     *
     * @return A new callback for a media session.
     */
    fun build(): MediaSession.SessionCallback {
        return SessionCallback(
                sessionPlayerConnector,
                fastForwardMs,
                rewindMs,
                seekTimeoutMs,
                (if (allowedCommandProvider == null) DefaultAllowedCommandProvider(context) else allowedCommandProvider)!!,
                ratingCallback,
                customCommandProvider,
                mediaItemProvider,
                skipCallback,
                postConnectCallback,
                disconnectedCallback)
    }

    companion object {
        /** Default timeout value for [.setSeekTimeoutMs].  */
        const val DEFAULT_SEEK_TIMEOUT_MS = 1000
    }
}