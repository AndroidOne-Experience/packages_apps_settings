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

package com.android.settings.sim;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
public class DefaultDataSubscriptionRestoreServiceTest {

    @Test
    public void scheduleJob_addSchedule_containsConfiguredDelay() {
        Robolectric.setupService(DefaultDataSubscriptionRestoreService.class);
        Context context = ApplicationProvider.getApplicationContext();
        Objects.requireNonNull(context.getSystemService(JobScheduler.class)).cancelAll();

        DefaultDataSubscriptionRestoreService.scheduleJob(context);

        List<JobInfo> jobs = Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                .getAllPendingJobs();
        assertThat(jobs).hasSize(1);
        JobInfo job = jobs.get(0);
        assertThat(job.isPersisted()).isFalse();
        assertThat(job.getMinLatencyMillis())
                .isEqualTo(DefaultDataSubscriptionRestoreService.AIRPLANE_MODE_RESTORE_DELAY_MILLIS);
    }

    @Test
    public void scheduleJob_addSchedule_whenInvoked() {
        Context context = spy(ApplicationProvider.getApplicationContext());
        JobScheduler jobScheduler = mock(JobScheduler.class);
        when(context.getSystemService(JobScheduler.class)).thenReturn(jobScheduler);

        DefaultDataSubscriptionRestoreService.scheduleJob(context);

        verify(jobScheduler).schedule(any());
    }
}
