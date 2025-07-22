/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.safetycenter;

import com.android.settings.security.KeyboxDataPreference;
import com.android.settings.security.PifDataPreference;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.security.LockUnificationPreferenceController;
import com.android.settings.security.trustagent.TrustAgentListPreferenceController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.drawer.CategoryKey;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

/**
 * An overflow menu for {@code SecuritySettings} containing advanced security and privacy settings.
 *
 * <p>This also includes all work-profile related settings.
 */
@SearchIndexable
public class MoreSecurityPrivacyFragment extends DashboardFragment {
    private static final String TAG = "MoreSecurityPrivacyFragment";
    private static final String KEY_NOTIFICATION_WORK_PROFILE_NOTIFICATIONS =
            "privacy_lock_screen_work_profile_notifications";

    // Keybox preference integration
    private static final String KEYBOX_DATA_KEY = "keybox_data_setting";
    private ActivityResultLauncher<Intent> mKeyboxFilePickerLauncher;
    private KeyboxDataPreference mKeyboxDataPreference;

    // Pif preference integration
    private static final String PIF_DATA_KEY = "pif_data_setting";
    private ActivityResultLauncher<Intent> mPifFilePickerLauncher;
    private PifDataPreference mPifDataPreference;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MORE_SECURITY_PRIVACY_SETTINGS;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.more_security_privacy_settings;
    }

    @Override
    public String getCategoryKey() {
        return CategoryKey.CATEGORY_MORE_SECURITY_PRIVACY_SETTINGS;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        SafetyCenterUtils.replaceEnterpriseStringsForPrivacyEntries(this);
        SafetyCenterUtils.replaceEnterpriseStringsForSecurityEntries(this);

        // Setup Keybox file picker
        mKeyboxFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        Preference pref = findPreference(KEYBOX_DATA_KEY);
                        if (pref instanceof KeyboxDataPreference) {
                            ((KeyboxDataPreference) pref).handleFileSelected(uri);
                        }
                    }
                }
        );

        // Setup Pif file picker
        mPifFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    Preference pref = findPreference(PIF_DATA_KEY);
                    if (pref instanceof PifDataPreference) {
                        ((PifDataPreference) pref).handleFileSelected(uri);
                    }
                }
            }
        );
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Connect Keybox preference to launcher
        mKeyboxDataPreference = findPreference(KEYBOX_DATA_KEY);
        mPifDataPreference = findPreference(PIF_DATA_KEY);
        if (mKeyboxDataPreference != null) {
            mKeyboxDataPreference.setFilePickerLauncher(mKeyboxFilePickerLauncher);
        }
        if (mPifDataPreference != null) {
            mPifDataPreference.setFilePickerLauncher(mPifFilePickerLauncher);
        }
    }

    /** see confirmPatternThenDisableAndClear */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (use(TrustAgentListPreferenceController.class)
                .handleActivityResult(requestCode, resultCode)) {
            return;
        }
        if (use(LockUnificationPreferenceController.class)
                .handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle(), this /* host*/);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, Lifecycle lifecycle, DashboardFragment host) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.addAll(SafetyCenterUtils.getControllersForAdvancedPrivacy(context, lifecycle));
        controllers.addAll(
                SafetyCenterUtils.getControllersForAdvancedSecurity(context, lifecycle, host));
        return controllers;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.more_security_privacy_settings) {
                /**
                 * If SafetyCenter is disabled, all of these entries will be in the More Security
                 * Settings and the Privacy page, and we don't want to index these entries.
                 */
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    // NOTE: This check likely should be moved to the super method. This is done
                    // here to avoid potentially undesired side effects for existing implementors.
                    if (!isPageSearchEnabled(context)) {
                        return null;
                    }
                    return super.getXmlResourcesToIndex(context, enabled);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null, null);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    final int profileUserId =
                            Utils.getManagedProfileId(
                                    UserManager.get(context), UserHandle.myUserId());
                    // If work profile is supported, we should keep the search result.
                    if (profileUserId != UserHandle.USER_NULL) {
                        return keys;
                    }

                    // Otherwise, we should hide the search result.
                    keys.add(KEY_NOTIFICATION_WORK_PROFILE_NOTIFICATIONS);
                    return keys;
                }

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SafetyCenterManagerWrapper.get().isEnabled(context);
                }
            };
}
