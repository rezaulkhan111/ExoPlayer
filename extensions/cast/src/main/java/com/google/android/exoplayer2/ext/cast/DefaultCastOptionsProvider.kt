/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** A convenience [OptionsProvider] to target the default cast receiver app.  */
class DefaultCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
                .setResumeSavedSession(false)
                .setEnableReconnectionService(false)
                .setReceiverApplicationId(APP_ID_DEFAULT_RECEIVER_WITH_DRM)
                .setStopReceiverApplicationWhenEndingSession(true)
                .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return emptyList()
    }

    companion object {
        /**
         * App id that points to the Default Media Receiver app with basic DRM support.
         *
         *
         * Applications that require more complex DRM authentication should [create a
 * custom receiver application](https://developers.google.com/cast/docs/web_receiver/streaming_protocols#drm).
         */
        const val APP_ID_DEFAULT_RECEIVER_WITH_DRM = "A12D4273"
    }
}