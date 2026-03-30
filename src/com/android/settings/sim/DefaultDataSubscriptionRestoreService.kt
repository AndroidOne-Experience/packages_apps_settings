/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settings.sim

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.settings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores the saved default data subscription after telephony state transitions. */
class DefaultDataSubscriptionRestoreService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                DefaultDataSubscriptionSelectionUtils.restoreSelectionIfNeeded(
                    this@DefaultDataSubscriptionRestoreService,
                    getSystemService(SubscriptionManager::class.java),
                )
            } catch (exception: Throwable) {
                Log.e(TAG, "Exception running job", exception)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return false
    }

    companion object {
        private const val TAG = "DefaultDataRestoreSvc"

        @VisibleForTesting
        const val AIRPLANE_MODE_RESTORE_DELAY_MILLIS: Long = 5_000L

        @JvmStatic
        fun scheduleJob(context: Context) {
            scheduleJob(context, AIRPLANE_MODE_RESTORE_DELAY_MILLIS)
        }

        @JvmStatic
        fun scheduleJob(
            context: Context,
            minLatencyMillis: Long,
        ) {
            val component = ComponentName(context, DefaultDataSubscriptionRestoreService::class.java)
            val jobScheduler = context.getSystemService(JobScheduler::class.java)!!
            val jobInfo =
                JobInfo.Builder(R.integer.default_data_subscription_restore, component)
                    .setMinimumLatency(minLatencyMillis)
                    .build()
            jobScheduler.schedule(jobInfo)
        }
    }
}
