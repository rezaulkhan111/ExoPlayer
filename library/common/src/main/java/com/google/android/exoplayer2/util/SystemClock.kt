/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.*
import android.os.SystemClock

/**
 * The standard implementation of [Clock], an instance of which is available via [ ][SystemClock.DEFAULT].
 */
open class SystemClock constructor() : Clock {
    public override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    public override fun elapsedRealtime(): Long {
        return SystemClock.elapsedRealtime()
    }

    public override fun uptimeMillis(): Long {
        return SystemClock.uptimeMillis()
    }

    public override fun createHandler(looper: Looper?, callback: Handler.Callback?): HandlerWrapper {
        return SystemHandlerWrapper(Handler((looper)!!, callback))
    }

    public override fun onThreadBlocked() {
        // Do nothing.
    }
}