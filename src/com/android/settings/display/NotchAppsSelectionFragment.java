package com.android.settings.display;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotchAppsSelectionFragment extends Fragment {

    private static final Set<String> SYSTEM_PACKAGE_WHITELIST = new HashSet<>(Arrays.asList(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
    ));

    private static final Set<String> SYSTEM_PACKAGE_BLACKLIST = new HashSet<>(Arrays.asList(
            "com.google.ar.core",
            "com.google.android.contactkeys",
            "com.google.android.safetycore"
    ));

    private RecyclerView mRecyclerView;
    private PackageManager mPackageManager;
    private final Set<String> mEnabledApps = new HashSet<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBackgroundExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Drawable> mIconCache = new LruCache<>(64);

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
        mRecyclerView.setHasFixedSize(true);

        loadAppsAsync();
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBackgroundExecutor.shutdownNow();
    }

    private void loadAppsAsync() {
        mBackgroundExecutor.execute(() -> {
            List<ApplicationInfo> apps = getUserInstalledApps();
            // Sort alphabetically by label in background to avoid UI jank.
            Collections.sort(apps, Comparator.comparing(
                    a -> a.loadLabel(mPackageManager).toString(),
                    String.CASE_INSENSITIVE_ORDER));

            if (!isAdded()) {
                return;
            }

            mMainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                mRecyclerView.setAdapter(new AppListAdapter(apps));
            });
        });
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

        for (ApplicationInfo app : allApps) {
            boolean isSystem = (app.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            boolean inWhitelist = SYSTEM_PACKAGE_WHITELIST.contains(app.packageName);
            boolean inBlacklist = SYSTEM_PACKAGE_BLACKLIST.contains(app.packageName);

            // Include if non-system or whitelisted system app, and not blacklisted
            if ((!isSystem || inWhitelist) && !inBlacklist) {
                filteredApps.add(app);
            }
        }
        return filteredApps;
    }

    private Drawable getAppIcon(ApplicationInfo appInfo) {
        Drawable cached = mIconCache.get(appInfo.packageName);
        if (cached != null) {
            return cached;
        }
        Drawable loaded = appInfo.loadIcon(mPackageManager);
        mIconCache.put(appInfo.packageName, loaded);
        return loaded;
    }

    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private final List<ApplicationInfo> mApps;

        AppListAdapter(List<ApplicationInfo> apps) {
            mApps = apps;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return mApps.get(position).packageName.hashCode();
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
            holder.icon.setImageDrawable(getAppIcon(app));
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
