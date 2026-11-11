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

package com.android.settings.security;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.internal.util.custom.KeyboxPackagesUtils;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import java.util.Set;

public class KeyboxConfigurationPreferenceController extends BasePreferenceController {

    private static final String PREF_KEY = "keybox_configuration_settings";

    public KeyboxConfigurationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    public KeyboxConfigurationPreferenceController(Context context) {
        super(context, PREF_KEY);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        String xml = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.KEYBOX_DATA);
        boolean isImported = !TextUtils.isEmpty(xml);

        if (!isImported) {
            return mContext.getString(R.string.keybox_configuration_summary_not_imported);
        }

        Set<String> apps = KeyboxPackagesUtils.getPackages(mContext);
        int count = apps.size();
        if (count == 0) {
            return mContext.getString(R.string.keybox_configuration_summary_imported_no_apps);
        } else {
            return mContext.getString(R.string.keybox_configuration_summary_imported, count);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setSummary(getSummary());
    }
}
