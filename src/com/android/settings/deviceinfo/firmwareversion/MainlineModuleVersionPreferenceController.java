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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.flags.Flags;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

// LINT.IfChange
public class MainlineModuleVersionPreferenceController extends BasePreferenceController {

    private static final String TAG = "MainlineModuleControl";
    private static final List<String> VERSION_NAME_DATE_PATTERNS = Arrays.asList("yyyy-MM-dd",
            "yyyy-MM");

    private final PackageManager mPackageManager;

    private String mModuleVersion;

    public MainlineModuleVersionPreferenceController(Context context, String key) {
        super(context, key);
        mPackageManager = mContext.getPackageManager();
        if (Flags.mainlineModuleExplicitIntent()) {
            String packageName = mContext
                    .getString(com.android.settings.R.string.config_mainline_module_update_package);
        }
        initModules();
    }

    @Override
    public int getAvailabilityStatus() {
        return !TextUtils.isEmpty(mModuleVersion) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    private void initModules() {
        final String moduleProvider = mContext.getString(
                com.android.internal.R.string.config_defaultModuleMetadataProvider);
        if (!TextUtils.isEmpty(moduleProvider)) {
            try {
                mModuleVersion =
                        mPackageManager.getPackageInfo(moduleProvider, 0 /* flags */).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to get mainline version.", e);
                mModuleVersion = null;
            }
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);

        // Create an intent to open the Play Store directly to update Google Play Services
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.google.android.gms")); // Play Store URL for GMS
        intent.setPackage("com.android.vending");  // Ensures Play Store app is opened

        // Check if Play Store is installed and available
        if (intent.resolveActivity(mPackageManager) != null) {
            preference.setIntent(intent);
            preference.setSelectable(true);
        } else {
            // Handle the case where Play Store is not installed
            Log.e(TAG, "Google Play Store is not installed.");
            preference.setIntent(null);
            preference.setSelectable(false);
        }
    }

    @Override
    public CharSequence getSummary() {
        if (TextUtils.isEmpty(mModuleVersion)) {
            return mModuleVersion;
        }

        final Optional<Date> parsedDate = parseDateFromVersionName(mModuleVersion);
        if (!parsedDate.isPresent()) {
            Log.w("Could not parse mainline versionName (%s) as date.", mModuleVersion);
            return mModuleVersion;
        }

        String format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy");
        return DateFormat.format(format, parsedDate.get());
    }

    private Optional<Date> parseDateFromVersionName(String text) {
        for (String pattern : VERSION_NAME_DATE_PATTERNS) {
            try {
                final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern,
                        Locale.getDefault());
                simpleDateFormat.setTimeZone(TimeZone.getDefault());
                return Optional.of(simpleDateFormat.parse(text));
            } catch (ParseException e) {
                // ignore and try next pattern
            }
        }
        return Optional.empty();
    }
}
// LINT.ThenChange(MainlineModuleVersionPreference.kt)