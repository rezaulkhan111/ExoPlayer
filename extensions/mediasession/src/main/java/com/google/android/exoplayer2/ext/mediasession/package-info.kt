/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.mediasession

import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.QueueDataAdapter
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.MediaDescriptionConverter
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.MediaDescriptionEqualityChecker
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor.MediaIdEqualityChecker
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueEditor
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CommandReceiver
import com.google.android.exoplayer2.Player
import android.os.Bundle
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor
import androidx.annotation.LongDef
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.MediaMetadataProvider
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.PlaybackPreparer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CaptionCallback
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.MediaButtonEventHandler
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.DefaultMediaMetadataProvider
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.PlaybackActions
import com.google.android.exoplayer2.Timeline
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.util.RepeatModeUtil.RepeatToggleModes
import com.google.android.exoplayer2.ext.mediasession.RepeatModeActionProvider
import com.google.android.exoplayer2.util.RepeatModeUtil
