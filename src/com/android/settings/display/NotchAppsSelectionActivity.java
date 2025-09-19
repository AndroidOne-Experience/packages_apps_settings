package com.android.settings.display;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

public class NotchAppsSelectionActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the NotchAppsSelectionFragment as content
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new NotchAppsSelectionFragment())
                .commit();
    }
}
