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
package com.google.android.exoplayer2.ext.workmanager

import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import androidx.work.*
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.scheduler.Requirements
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util

/** A [Scheduler] that uses [WorkManager].  */
class WorkManagerScheduler : Scheduler {
    private val workManager: WorkManager
    private val workName: String

    @Deprecated("Call {@link #WorkManagerScheduler(Context, String)} instead.")
    constructor(workName: String) {
        this.workName = workName
        workManager = WorkManager.getInstance()
    }

    /**
     * @param context A context.
     * @param workName A name for work scheduled by this instance. If the same name was used by a
     * previous instance, anything scheduled by the previous instance will be canceled by this
     * instance if [.schedule] or [.cancel] are
     * called.
     */
    constructor(context: Context, workName: String) {
        this.workName = workName
        workManager = WorkManager.getInstance(context.applicationContext)
    }

    override fun schedule(requirements: Requirements, servicePackage: String, serviceAction: String): Boolean {
        val constraints = buildConstraints(requirements)
        val inputData = buildInputData(requirements, servicePackage, serviceAction)
        val workRequest = buildWorkRequest(constraints, inputData)
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
        return true
    }

    override fun cancel(): Boolean {
        workManager.cancelUniqueWork(workName)
        return true
    }

    override fun getSupportedRequirements(requirements: Requirements): Requirements {
        return requirements.filterRequirements(SUPPORTED_REQUIREMENTS)
    }

    /** A [Worker] that starts the target service if the requirements are met.  */ // This class needs to be public so that WorkManager can instantiate it.
    class SchedulerWorker(private val context: Context, private val workerParams: WorkerParameters) : Worker(context, workerParams) {
        override fun doWork(): Result {
            val inputData = Assertions.checkNotNull(workerParams.inputData)
            val requirements = Requirements(inputData.getInt(KEY_REQUIREMENTS, 0))
            val notMetRequirements = requirements.getNotMetRequirements(context)
            return if (notMetRequirements == 0) {
                val serviceAction = Assertions.checkNotNull(inputData.getString(KEY_SERVICE_ACTION))
                val servicePackage = Assertions.checkNotNull(inputData.getString(KEY_SERVICE_PACKAGE))
                val intent = Intent(serviceAction).setPackage(servicePackage)
                Util.startForegroundService(context, intent)
                Result.success()
            } else {
                Log.w(TAG, "Requirements not met: $notMetRequirements")
                Result.retry()
            }
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.workmanager")
        }

        private const val TAG = "WorkManagerScheduler"
        private const val KEY_SERVICE_ACTION = "service_action"
        private const val KEY_SERVICE_PACKAGE = "service_package"
        private const val KEY_REQUIREMENTS = "requirements"
        private val SUPPORTED_REQUIREMENTS = (Requirements.NETWORK
                or Requirements.NETWORK_UNMETERED
                or (if (Util.SDK_INT >= 23) Requirements.DEVICE_IDLE else 0)
                or Requirements.DEVICE_CHARGING
                or Requirements.DEVICE_STORAGE_NOT_LOW)

        private fun buildConstraints(requirements: Requirements): Constraints {
            val filteredRequirements = requirements.filterRequirements(SUPPORTED_REQUIREMENTS)
            if (filteredRequirements != requirements) {
                Log.w(
                        TAG, "Ignoring unsupported requirements: "
                        + (filteredRequirements.requirements xor requirements.requirements))
            }
            val builder = Constraints.Builder()
            if (requirements.isUnmeteredNetworkRequired) {
                builder.setRequiredNetworkType(NetworkType.UNMETERED)
            } else if (requirements.isNetworkRequired) {
                builder.setRequiredNetworkType(NetworkType.CONNECTED)
            } else {
                builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            }
            if (Util.SDK_INT >= 23 && requirements.isIdleRequired) {
                setRequiresDeviceIdle(builder)
            }
            if (requirements.isChargingRequired) {
                builder.setRequiresCharging(true)
            }
            if (requirements.isStorageNotLowRequired) {
                builder.setRequiresStorageNotLow(true)
            }
            return builder.build()
        }

        @RequiresApi(23)
        private fun setRequiresDeviceIdle(builder: Constraints.Builder) {
            builder.setRequiresDeviceIdle(true)
        }

        private fun buildInputData(
                requirements: Requirements, servicePackage: String, serviceAction: String): Data {
            val builder = Data.Builder()
            builder.putInt(KEY_REQUIREMENTS, requirements.requirements)
            builder.putString(KEY_SERVICE_PACKAGE, servicePackage)
            builder.putString(KEY_SERVICE_ACTION, serviceAction)
            return builder.build()
        }

        private fun buildWorkRequest(constraints: Constraints, inputData: Data): OneTimeWorkRequest {
            val builder = OneTimeWorkRequest.Builder(SchedulerWorker::class.java)
            builder.setConstraints(constraints)
            builder.setInputData(inputData)
            return builder.build()
        }
    }
}