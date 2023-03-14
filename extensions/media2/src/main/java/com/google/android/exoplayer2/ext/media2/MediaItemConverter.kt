/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.media2.common.MediaItem

/**
 * Converts between [Media2 MediaItem][androidx.media2.common.MediaItem] and [ ExoPlayer MediaItem][MediaItem].
 */
interface MediaItemConverter {
    /**
     * Converts a [Media2 MediaItem][androidx.media2.common.MediaItem] to an [ ExoPlayer MediaItem][MediaItem].
     */
    fun convertToExoPlayerMediaItem(media2MediaItem: MediaItem): com.google.android.exoplayer2.MediaItem

    /**
     * Converts an [ExoPlayer MediaItem][MediaItem] to a [ Media2 MediaItem][androidx.media2.common.MediaItem].
     */
    fun convertToMedia2MediaItem(exoPlayerMediaItem: com.google.android.exoplayer2.MediaItem): MediaItem
}