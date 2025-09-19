package com.android.settings.display;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.R;

public class NotchAppsPreferenceController extends BasePreferenceController {

    private static final String KEY = "notch_enabled_apps";

    public NotchAppsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        // Only show on devices with display cutout
        return hasDisplayCutout() ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    private boolean hasDisplayCutout() {
        String cutoutSpec = mContext.getResources().getString(
                com.android.internal.R.string.config_mainBuiltInDisplayCutout);
        return !TextUtils.isEmpty(cutoutSpec);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (KEY.equals(preference.getKey())) {
            mContext.startActivity(new Intent(mContext, NotchAppsSelectionActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public void updateState(Preference preference) {
        String enabledApps = Settings.Secure.getString(
                mContext.getContentResolver(), Settings.Secure.NOTCH_ENABLED_APPS);

        int count = 0;
        if (!TextUtils.isEmpty(enabledApps)) {
            for (String pkg : enabledApps.split(",")) {
                if (!pkg.trim().isEmpty()) {
                    count++;
                }
            }
        }

        preference.setSummary(mContext.getResources().getQuantityString(
                R.plurals.notch_apps_summary, count, count));
    }
}
