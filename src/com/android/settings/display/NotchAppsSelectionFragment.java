package com.android.settings.display;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotchAppsSelectionFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private PackageManager mPackageManager;
    private final Set<String> mEnabledApps = new HashSet<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackageManager = requireActivity().getPackageManager();
        loadEnabledApps();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.notch_apps_selection_layout, container, false);
        mRecyclerView = root.findViewById(R.id.apps_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<ApplicationInfo> apps = getUserInstalledApps();
        // Sort alphabetically by label
        Collections.sort(apps, Comparator.comparing(a -> a.loadLabel(mPackageManager).toString(), String.CASE_INSENSITIVE_ORDER));

        AppListAdapter adapter = new AppListAdapter(apps);
        mRecyclerView.setAdapter(adapter);
        return root;
    }

    /**
     * Load the enabled apps list from secure settings.
     */
    private void loadEnabledApps() {
        String enabledAppsString = Settings.Secure.getString(
                requireActivity().getContentResolver(),
                Settings.Secure.NOTCH_ENABLED_APPS);

        mEnabledApps.clear();
        if (!TextUtils.isEmpty(enabledAppsString)) {
            mEnabledApps.addAll(Arrays.asList(enabledAppsString.split(",")));
        }
    }

    /**
     * Returns user-installed apps filtered by whitelist and blacklist.
     */
    private List<ApplicationInfo> getUserInstalledApps() {
        List<ApplicationInfo> allApps = mPackageManager.getInstalledApplications(0);
        List<ApplicationInfo> filteredApps = new ArrayList<>();

        // Whitelist entries for system apps allowed for notch usage
        Set<String> systemPackageWhitelist = new HashSet<>(Arrays.asList(
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music"
        ));

        // Blacklist of system/privileged apps to exclude
        Set<String> systemPackageBlacklist = new HashSet<>(Arrays.asList(
                "com.google.ar.core",
                "com.google.android.contactkeys",
                "com.google.android.safetycore"
        ));

        for (ApplicationInfo app : allApps) {
            boolean isSystem = (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            boolean inWhitelist = systemPackageWhitelist.contains(app.packageName);
            boolean inBlacklist = systemPackageBlacklist.contains(app.packageName);

            // Include if non-system or whitelisted system app, and not blacklisted
            if ((!isSystem || inWhitelist) && !inBlacklist) {
                filteredApps.add(app);
            }
        }
        return filteredApps;
    }

    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private final List<ApplicationInfo> mApps;

        AppListAdapter(List<ApplicationInfo> apps) {
            mApps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.notch_app_list_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApplicationInfo app = mApps.get(position);
            holder.label.setText(app.loadLabel(mPackageManager));
            holder.icon.setImageDrawable(app.loadIcon(mPackageManager));
            holder.switchToggle.setChecked(mEnabledApps.contains(app.packageName));

            holder.itemView.setOnClickListener(v -> {
                boolean enabled = !holder.switchToggle.isChecked();
                holder.switchToggle.setChecked(enabled);
                if (enabled) {
                    mEnabledApps.add(app.packageName);
                } else {
                    mEnabledApps.remove(app.packageName);
                }
                saveEnabledApps();
            });
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            final Switch switchToggle;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                label = itemView.findViewById(R.id.label);
                switchToggle = itemView.findViewById(R.id.switch_toggle);
            }
        }
    }

    /**
     * Persist enabled apps list in secure settings.
     */
    private void saveEnabledApps() {
        String enabledAppsString = TextUtils.join(",", mEnabledApps);
        Settings.Secure.putString(
                requireActivity().getContentResolver(),
                Settings.Secure.NOTCH_ENABLED_APPS,
                enabledAppsString);
    }
}
