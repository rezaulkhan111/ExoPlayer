/*
 * Copyright 2022 The Android Open Source Project
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

import com.google.android.exoplayer2.util.Assertions.checkArgument

/** Value class specifying information about a decoded video frame.  */
class FrameInfo {
    /** The width of the frame, in pixels.  */
    var width = 0

    /** The height of the frame, in pixels.  */
    var height = 0

    /** The ratio of width over height for each pixel.  */
    var pixelWidthHeightRatio = 0f

    /**
     * An offset in microseconds that is part of the input timestamps and should be ignored for
     * processing but added back to the output timestamps.
     *
     *
     * The offset stays constant within a stream but changes in between streams to ensure that
     * frame timestamps are always monotonically increasing.
     */
    var streamOffsetUs: Long = 0

    // TODO(b/227624622): Add color space information for HDR.
    /**
     * Creates a new instance.
     *
     * @param width The width of the frame, in pixels.
     * @param height The height of the frame, in pixels.
     * @param pixelWidthHeightRatio The ratio of width over height for each pixel.
     * @param streamOffsetUs An offset in microseconds that is part of the input timestamps and should
     * be ignored for processing but added back to the output timestamps.
     */
    constructor(width: Int, height: Int, pixelWidthHeightRatio: Float, streamOffsetUs: Long) {
        checkArgument(width > 0, "width must be positive, but is: $width")
        checkArgument(height > 0, "height must be positive, but is: $height")
        this.width = width
        this.height = height
        this.pixelWidthHeightRatio = pixelWidthHeightRatio
        this.streamOffsetUs = streamOffsetUs
    }
}