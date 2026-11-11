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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.util.custom.KeyboxPackagesUtils;
import com.android.settings.R;
import com.android.settings.core.InstrumentedFragment;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyboxConfigurationSettings extends InstrumentedFragment {

    private static final String TAG = "KeyboxConfigSettings";

    private static final int TAB_USER_APPS = 0;
    private static final int TAB_SYSTEM_APPS = 1;
    private static final int TAB_SELECTED_APPS = 2;
    private static final String KEY_CURRENT_TAB = "current_tab";

    private ActivityManager mActivityManager;
    private List<UserInfo> mUserInfos = new ArrayList<>();
    private final Set<String> mSelectedPackages = new HashSet<>();

    private View mBadgeContainer;
    private ImageView mBadgeIcon;
    private TextView mBadgeText;
    private TextView mKeyboxStatusSummary;
    private View mBtnImportKeybox;
    private TextView mBtnImportText;
    private View mBtnRemoveKeybox;

    private View mPerAppContainer;
    private TextView mDisabledHint;

    private View mTabUserApps;
    private View mTabSystemApps;
    private View mTabSelectedApps;
    private TextView mTextUserApps;
    private TextView mTextSystemApps;
    private TextView mTextSelectedApps;
    private ImageView mIconUserApps;
    private ImageView mIconSystemApps;
    private ImageView mIconSelectedApps;

    private EditText mSearchInput;
    private ImageView mSearchClear;

    private RecyclerView mRecyclerView;
    private ProgressBar mLoadingProgress;
    private TextView mEmptyView;
    private AppListAdapter mAdapter;

    private List<AppItem> mAllApps = new ArrayList<>();
    private int mCurrentTab = TAB_USER_APPS;
    private String mSearchText = "";
    private boolean mIsKeyboxImported = false;

    private ActivityResultLauncher<Intent> mFilePickerLauncher;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = requireContext();
        mActivityManager = context.getSystemService(ActivityManager.class);
        UserManager userManager = UserManager.get(context);
        mUserInfos = userManager.getUsers();

        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(KEY_CURRENT_TAB, TAB_USER_APPS);
        }

        mFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        handleFileSelected(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.keybox_configuration_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBadgeContainer = view.findViewById(R.id.keybox_badge_container);
        mBadgeIcon = view.findViewById(R.id.keybox_badge_icon);
        mBadgeText = view.findViewById(R.id.keybox_badge_text);
        mKeyboxStatusSummary = view.findViewById(R.id.keybox_status_summary);

        mBtnImportKeybox = view.findViewById(R.id.btn_import_keybox);
        mBtnImportText = view.findViewById(R.id.btn_import_text);
        mBtnRemoveKeybox = view.findViewById(R.id.btn_remove_keybox);

        mPerAppContainer = view.findViewById(R.id.per_app_selection_container);
        mDisabledHint = view.findViewById(R.id.keybox_apps_disabled_hint);

        mTabUserApps = view.findViewById(R.id.tab_user_apps);
        mTabSystemApps = view.findViewById(R.id.tab_system_apps);
        mTabSelectedApps = view.findViewById(R.id.tab_selected_apps);
        mTextUserApps = view.findViewById(R.id.text_user_apps);
        mTextSystemApps = view.findViewById(R.id.text_system_apps);
        mTextSelectedApps = view.findViewById(R.id.text_selected_apps);
        mIconUserApps = view.findViewById(R.id.icon_user_apps);
        mIconSystemApps = view.findViewById(R.id.icon_system_apps);
        mIconSelectedApps = view.findViewById(R.id.icon_selected_apps);

        mSearchInput = view.findViewById(R.id.search_input);
        mSearchClear = view.findViewById(R.id.search_clear);

        mRecyclerView = view.findViewById(R.id.apps_list);
        mLoadingProgress = view.findViewById(R.id.loading_progress);
        mEmptyView = view.findViewById(R.id.empty_view);

        mBtnImportKeybox.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("text/xml");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            mFilePickerLauncher.launch(intent);
        });

        mBtnRemoveKeybox.setOnClickListener(v -> showRemoveKeyboxDialog());

        mTabUserApps.setOnClickListener(v -> selectTab(TAB_USER_APPS));
        mTabSystemApps.setOnClickListener(v -> selectTab(TAB_SYSTEM_APPS));
        if (mTabSelectedApps != null) {
            mTabSelectedApps.setOnClickListener(v -> selectTab(TAB_SELECTED_APPS));
        }

        if (mSearchInput != null) {
            mSearchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    mSearchText = s != null ? s.toString() : "";
                    if (mSearchClear != null) {
                        mSearchClear.setVisibility(mSearchText.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    filterAndSubmitList();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (mSearchClear != null) {
            mSearchClear.setOnClickListener(v -> {
                if (mSearchInput != null) {
                    mSearchInput.setText("");
                }
            });
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()) {
            @Override
            public boolean canScrollVertically() {
                return mIsKeyboxImported && super.canScrollVertically();
            }
        });
        mAdapter = new AppListAdapter();
        mRecyclerView.setAdapter(mAdapter);

        updateTabStyles();
        updateKeyboxState();
        loadApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.keybox_configuration_title);
        }
        updateKeyboxState();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_TAB, mCurrentTab);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private void selectTab(int tab) {
        if (!mIsKeyboxImported) return;
        if (mCurrentTab == tab) return;
        mCurrentTab = tab;
        updateTabStyles();
        filterAndSubmitList();
    }

    private void updateTabStyles() {
        if (mTabUserApps == null || mTabSystemApps == null) return;

        Context context = getContext();
        if (context == null) return;

        int selectedTextColor = context.getColor(R.color.keybox_tab_selected_text_color);
        int unselectedTextColor = context.getColor(R.color.keybox_tab_unselected_text_color);

        mTabUserApps.setBackgroundResource(0);
        mTabSystemApps.setBackgroundResource(0);
        if (mTabSelectedApps != null) mTabSelectedApps.setBackgroundResource(0);

        if (mTextUserApps != null) mTextUserApps.setTextColor(unselectedTextColor);
        if (mIconUserApps != null) mIconUserApps.setColorFilter(unselectedTextColor);

        if (mTextSystemApps != null) mTextSystemApps.setTextColor(unselectedTextColor);
        if (mIconSystemApps != null) mIconSystemApps.setColorFilter(unselectedTextColor);

        if (mTextSelectedApps != null) mTextSelectedApps.setTextColor(unselectedTextColor);
        if (mIconSelectedApps != null) mIconSelectedApps.setColorFilter(unselectedTextColor);

        if (mCurrentTab == TAB_USER_APPS) {
            mTabUserApps.setBackgroundResource(R.drawable.keybox_tab_selected_bg);
            if (mTextUserApps != null) mTextUserApps.setTextColor(selectedTextColor);
            if (mIconUserApps != null) mIconUserApps.setColorFilter(selectedTextColor);
        } else if (mCurrentTab == TAB_SYSTEM_APPS) {
            mTabSystemApps.setBackgroundResource(R.drawable.keybox_tab_selected_bg);
            if (mTextSystemApps != null) mTextSystemApps.setTextColor(selectedTextColor);
            if (mIconSystemApps != null) mIconSystemApps.setColorFilter(selectedTextColor);
        } else if (mCurrentTab == TAB_SELECTED_APPS) {
            if (mTabSelectedApps != null) mTabSelectedApps.setBackgroundResource(R.drawable.keybox_tab_selected_bg);
            if (mTextSelectedApps != null) mTextSelectedApps.setTextColor(selectedTextColor);
            if (mIconSelectedApps != null) mIconSelectedApps.setColorFilter(selectedTextColor);
        }
    }

    private void updateAppCounts() {
        int userCount = 0;
        int systemCount = 0;
        if (mAllApps != null) {
            for (AppItem app : mAllApps) {
                if (app.isLauncherApp) {
                    userCount++;
                } else {
                    systemCount++;
                }
            }
        }
        int selectedCount = mSelectedPackages.size();

        if (mTextUserApps != null) {
            mTextUserApps.setText(getString(R.string.keybox_packages_user_apps_count, userCount));
        }
        if (mTextSystemApps != null) {
            mTextSystemApps.setText(getString(R.string.keybox_packages_system_apps_count, systemCount));
        }
        if (mTextSelectedApps != null) {
            mTextSelectedApps.setText(getString(R.string.keybox_packages_selected_apps_count, selectedCount));
        }
    }

    private void updateKeyboxState() {
        if (!isAdded()) return;

        Context context = requireContext();
        String xml = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.KEYBOX_DATA);
        mIsKeyboxImported = !TextUtils.isEmpty(xml);

        int activeTextColor = context.getColor(R.color.keybox_badge_active_text_color);
        int inactiveTextColor = context.getColor(R.color.keybox_badge_inactive_text_color);

        if (mIsKeyboxImported) {
            if (mBadgeContainer != null) mBadgeContainer.setBackgroundResource(R.drawable.keybox_badge_active_bg);
            if (mBadgeIcon != null) {
                mBadgeIcon.setImageResource(R.drawable.ic_check_24dp);
                mBadgeIcon.setColorFilter(activeTextColor);
            }
            if (mBadgeText != null) {
                mBadgeText.setText(R.string.keybox_status_active);
                mBadgeText.setTextColor(activeTextColor);
            }
            if (mKeyboxStatusSummary != null) {
                mKeyboxStatusSummary.setVisibility(View.GONE);
            }
            if (mBtnImportText != null) mBtnImportText.setText(R.string.keybox_replace_action);

            if (mBtnRemoveKeybox != null) {
                mBtnRemoveKeybox.setEnabled(true);
                mBtnRemoveKeybox.setAlpha(1.0f);
            }
            if (mPerAppContainer != null) mPerAppContainer.setAlpha(1.0f);
            if (mDisabledHint != null) mDisabledHint.setVisibility(View.GONE);

            setPerAppControlsEnabled(true);
        } else {
            if (mBadgeContainer != null) mBadgeContainer.setBackgroundResource(R.drawable.keybox_badge_inactive_bg);
            if (mBadgeIcon != null) {
                mBadgeIcon.setImageResource(R.drawable.ic_settings_close);
                mBadgeIcon.setColorFilter(inactiveTextColor);
            }
            if (mBadgeText != null) {
                mBadgeText.setText(R.string.keybox_status_inactive);
                mBadgeText.setTextColor(inactiveTextColor);
            }
            if (mKeyboxStatusSummary != null) {
                mKeyboxStatusSummary.setText(R.string.keybox_status_not_imported);
                mKeyboxStatusSummary.setVisibility(View.VISIBLE);
            }
            if (mBtnImportText != null) mBtnImportText.setText(R.string.keybox_import_action);

            if (mBtnRemoveKeybox != null) {
                mBtnRemoveKeybox.setEnabled(false);
                mBtnRemoveKeybox.setAlpha(0.4f);
            }
            if (mPerAppContainer != null) mPerAppContainer.setAlpha(0.4f);
            if (mDisabledHint != null) mDisabledHint.setVisibility(View.VISIBLE);

            setPerAppControlsEnabled(false);
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void setPerAppControlsEnabled(boolean enabled) {
        if (mTabUserApps != null) mTabUserApps.setEnabled(enabled);
        if (mTabSystemApps != null) mTabSystemApps.setEnabled(enabled);
        if (mTabSelectedApps != null) mTabSelectedApps.setEnabled(enabled);

        if (mSearchInput != null) {
            mSearchInput.setEnabled(enabled);
        }
        if (mSearchClear != null) {
            mSearchClear.setEnabled(enabled);
        }

        if (mRecyclerView != null) {
            mRecyclerView.setEnabled(enabled);
        }
    }

    private void showRemoveKeyboxDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.keybox_remove_dialog_title)
                .setMessage(R.string.keybox_remove_dialog_message)
                .setPositiveButton(R.string.keybox_remove_action, (dialog, which) -> {
                    Settings.Secure.putString(requireContext().getContentResolver(),
                            Settings.Secure.KEYBOX_DATA, null);
                    Toast.makeText(requireContext(), R.string.keybox_file_cleared, Toast.LENGTH_SHORT).show();
                    updateKeyboxState();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void handleFileSelected(Uri uri) {
        if (uri == null ||
            (!uri.toString().endsWith(".xml") &&
             !"text/xml".equals(requireContext().getContentResolver().getType(uri)))) {
            Toast.makeText(requireContext(), R.string.keybox_invalid_file, Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder xmlContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                xmlContent.append(line).append('\n');
            }

            String xml = xmlContent.toString();
            if (!validateXml(xml)) {
                Toast.makeText(requireContext(), R.string.keybox_invalid_xml, Toast.LENGTH_SHORT).show();
                return;
            }

            Settings.Secure.putString(requireContext().getContentResolver(),
                    Settings.Secure.KEYBOX_DATA, xml);
            Toast.makeText(requireContext(), R.string.keybox_file_loaded, Toast.LENGTH_SHORT).show();
            updateKeyboxState();

        } catch (IOException e) {
            Log.e(TAG, "Failed to read XML file", e);
            Toast.makeText(requireContext(), R.string.keybox_read_error, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateXml(String xml) {
        boolean hasEcdsaKey = false, hasRsaKey = false;
        boolean hasEcdsaPrivKey = false, hasRsaPrivKey = false;
        int ecdsaCertCount = 0, rsaCertCount = 0;
        int numberOfKeyboxes = -1;

        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));

            String currentAlg = null;

            for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    switch (name) {
                        case "NumberOfKeyboxes":
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                try {
                                    numberOfKeyboxes = Integer.parseInt(parser.getText().trim());
                                } catch (NumberFormatException e) {
                                    numberOfKeyboxes = -1;
                                }
                            }
                            break;

                        case "Key":
                            currentAlg = parser.getAttributeValue(null, "algorithm");
                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                hasEcdsaKey = true;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                hasRsaKey = true;
                            } else {
                                currentAlg = null;
                            }
                            break;

                        case "PrivateKey": {
                            String format = parser.getAttributeValue(null, "format");
                            if (!"pem".equalsIgnoreCase(format)) {
                                Log.w(TAG, "Invalid or missing format for PrivateKey");
                                return false;
                            }
                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                hasEcdsaPrivKey = true;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                hasRsaPrivKey = true;
                            }
                            break;
                        }

                        case "Certificate": {
                            String format = parser.getAttributeValue(null, "format");
                            if (!"pem".equalsIgnoreCase(format)) {
                                Log.w(TAG, "Invalid or missing format for Certificate");
                                return false;
                            }

                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                ecdsaCertCount++;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                rsaCertCount++;
                            }
                            break;
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG && "Key".equals(parser.getName())) {
                    currentAlg = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "XML validation failed", e);
            return false;
        }

        if (numberOfKeyboxes != -1) {
            return numberOfKeyboxes == 1
                    ? (hasEcdsaKey && hasEcdsaPrivKey && ecdsaCertCount >= 1)
                    || (hasRsaKey && hasRsaPrivKey && rsaCertCount >= 1)
                    : (hasEcdsaKey && hasEcdsaPrivKey && ecdsaCertCount >= 1)
                    && (hasRsaKey && hasRsaPrivKey && rsaCertCount >= 1);
        }

        return (hasEcdsaKey && hasEcdsaPrivKey && ecdsaCertCount >= 1)
                || (hasRsaKey && hasRsaPrivKey && rsaCertCount >= 1);
    }

    private void loadApps() {
        if (mLoadingProgress != null) {
            mLoadingProgress.setVisibility(View.VISIBLE);
        }
        if (mEmptyView != null) {
            mEmptyView.setVisibility(View.GONE);
        }

        mExecutor.execute(() -> {
            Context context = getContext();
            if (context == null) return;

            PackageManager pm = context.getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> launcherActivities = pm.queryIntentActivities(
                    launcherIntent, PackageManager.MATCH_ALL);
            Set<String> launcherPackages = new HashSet<>();
            for (ResolveInfo ri : launcherActivities) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launcherPackages.add(ri.activityInfo.packageName);
                }
            }

            List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.MATCH_ANY_USER);
            List<AppItem> allApps = new ArrayList<>();
            for (PackageInfo pkgInfo : installedPackages) {
                ApplicationInfo appInfo = pkgInfo.applicationInfo;
                if (appInfo == null || pkgInfo.packageName == null) {
                    continue;
                }
                if (appInfo.isResourceOverlay() || "com.android.settings".equals(pkgInfo.packageName)) {
                    continue;
                }
                String label = appInfo.loadLabel(pm).toString();
                Drawable icon = appInfo.loadIcon(pm);
                boolean isLauncher = launcherPackages.contains(pkgInfo.packageName);
                allApps.add(new AppItem(pkgInfo.packageName, label, icon, isLauncher));
            }

            allApps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

            mMainHandler.post(() -> {
                if (!isAdded()) return;
                mAllApps = allApps;
                mSelectedPackages.clear();
                mSelectedPackages.addAll(KeyboxPackagesUtils.getPackages(requireContext()));
                if (mLoadingProgress != null) {
                    mLoadingProgress.setVisibility(View.GONE);
                }
                updateAppCounts();
                filterAndSubmitList();
            });
        });
    }

    private void filterAndSubmitList() {
        if (mAllApps == null) return;

        List<AppItem> filtered = new ArrayList<>();
        String query = mSearchText.trim().toLowerCase(Locale.getDefault());

        for (AppItem app : mAllApps) {
            if (mCurrentTab == TAB_USER_APPS) {
                if (!app.isLauncherApp) continue;
            } else if (mCurrentTab == TAB_SYSTEM_APPS) {
                if (app.isLauncherApp) continue;
            } else if (mCurrentTab == TAB_SELECTED_APPS) {
                if (!mSelectedPackages.contains(app.packageName)) continue;
            }

            if (!query.isEmpty()) {
                boolean matchLabel = app.label.toLowerCase(Locale.getDefault()).contains(query);
                boolean matchPkg = app.packageName.toLowerCase(Locale.getDefault()).contains(query);
                if (!matchLabel && !matchPkg) {
                    continue;
                }
            }

            filtered.add(app);
        }

        mAdapter.submitList(filtered);
        if (mEmptyView != null) {
            mEmptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void onItemClicked(AppItem item, int position) {
        String pkg = item.packageName;
        boolean nowChecked;
        if (mSelectedPackages.contains(pkg)) {
            mSelectedPackages.remove(pkg);
            nowChecked = false;
        } else {
            mSelectedPackages.add(pkg);
            nowChecked = true;
        }

        Context ctx = requireContext();
        for (UserInfo info : mUserInfos) {
            if (nowChecked) {
                KeyboxPackagesUtils.addPackage(ctx, pkg, info.id);
            } else {
                KeyboxPackagesUtils.removePackage(ctx, pkg, info.id);
            }
        }

        updateAppCounts();
        if (mCurrentTab == TAB_SELECTED_APPS) {
            filterAndSubmitList();
        } else {
            mAdapter.notifyItemChanged(position);
        }
    }

    private static class AppItem {
        final String packageName;
        final String label;
        final Drawable icon;
        final boolean isLauncherApp;

        AppItem(String packageName, String label, Drawable icon, boolean isLauncherApp) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isLauncherApp = isLauncherApp;
        }
    }

    private static final DiffUtil.ItemCallback<AppItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AppItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppItem oldItem, @NonNull AppItem newItem) {
                    return Objects.equals(oldItem.packageName, newItem.packageName);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppItem oldItem, @NonNull AppItem newItem) {
                    return Objects.equals(oldItem.packageName, newItem.packageName)
                            && Objects.equals(oldItem.label, newItem.label);
                }
            };

    private class AppListAdapter extends ListAdapter<AppItem, AppViewHolder> {

        AppListAdapter() {
            super(DIFF_CALLBACK);
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.keybox_packages_list_item, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppItem item = getItem(position);
            holder.label.setText(item.label);
            holder.icon.setImageDrawable(item.icon);

            boolean isChecked = mSelectedPackages.contains(item.packageName);
            if (holder.switchWidget instanceof CompoundButton) {
                CompoundButton sw = (CompoundButton) holder.switchWidget;
                sw.setChecked(isChecked);
                sw.setEnabled(mIsKeyboxImported);
            }

            holder.itemView.setEnabled(mIsKeyboxImported);

            holder.itemView.setOnClickListener(v -> {
                if (!mIsKeyboxImported) {
                    Toast.makeText(requireContext(), R.string.keybox_apps_disabled_hint, Toast.LENGTH_SHORT).show();
                    return;
                }
                onItemClicked(item, holder.getBindingAdapterPosition());
            });
        }
    }

    private static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final View switchWidget;

        AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            View sw = itemView.findViewById(R.id.switchWidget);
            if (sw == null) {
                sw = itemView.findViewById(android.R.id.switch_widget);
            }
            switchWidget = sw;
        }
    }
}
