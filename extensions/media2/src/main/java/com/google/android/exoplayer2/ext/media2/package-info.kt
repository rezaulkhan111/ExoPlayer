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

import android.annotation.SuppressLint
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.media2.PlayerWrapper.PollBufferRunnable
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.ext.media2.PlayerWrapper
import androidx.media2.common.CallbackMediaItem
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ext.media2.SessionPlayerConnector
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.AllowedCommandProvider
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.CustomCommandProvider
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.MediaItemProvider
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.SkipCallback
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.PostConnectCallback
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.DisconnectedCallback
import androidx.media2.session.SessionCommandGroup
import androidx.media2.session.SessionCommand
import androidx.media2.session.SessionResult
import android.os.Bundle
import com.google.common.util.concurrent.ListenableFuture
import androidx.media2.common.SessionPlayer.PlayerResult
import androidx.annotation.IntDef
import com.google.android.exoplayer2.ext.media2.PlayerCommandQueue
import com.google.android.exoplayer2.ext.media2.PlayerCommandQueue.AsyncPlayerCommandResult
import com.google.android.exoplayer2.ext.media2.PlayerCommandQueue.AsyncCommandCode
import android.text.TextUtils
import android.content.pm.PackageManager
import android.content.ComponentName
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.DefaultAllowedCommandProvider
import com.google.android.exoplayer2.ext.media2.SessionPlayerConnector.ExoPlayerWrapperListener
import androidx.annotation.FloatRange
import androidx.media2.common.FileMediaItem
import com.google.android.exoplayer2.ext.media2.SessionPlayerConnector.SessionPlayerCallbackNotifier
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import androidx.media2.common.UriMediaItem
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
