/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.scheduler

/** Schedules a service to be started in the foreground when some [Requirements] are met.  */
interface Scheduler {
    /**
     * Schedules a service to be started in the foreground when some [Requirements] are met.
     * Anything that was previously scheduled will be canceled.
     *
     *
     * The service to be started must be declared in the manifest of `servicePackage` with an
     * intent filter containing `serviceAction`. Note that when started with `serviceAction`, the service must call [Service.startForeground] to
     * make itself a foreground service, as documented by [ ][Service.startForegroundService].
     *
     * @param requirements The requirements.
     * @param servicePackage The package name.
     * @param serviceAction The action with which the service will be started.
     * @return Whether scheduling was successful.
     */
    fun schedule(requirements: Requirements?, servicePackage: String?, serviceAction: String?): Boolean

    /**
     * Cancels anything that was previously scheduled, or else does nothing.
     *
     * @return Whether cancellation was successful.
     */
    fun cancel(): Boolean

    /**
     * Checks whether this [Scheduler] supports the provided [Requirements]. If all of the
     * requirements are supported then the same [Requirements] instance is returned. If not then
     * a new instance is returned containing the subset of the requirements that are supported.
     *
     * @param requirements The requirements to check.
     * @return The supported requirements.
     */
    fun getSupportedRequirements(requirements: Requirements?): Requirements?
}