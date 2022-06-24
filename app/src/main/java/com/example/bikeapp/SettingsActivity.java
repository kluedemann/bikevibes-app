package com.example.bikeapp;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "Settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        Toolbar myToolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(myToolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private int delete_count;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            if (getActivity() != null) {
                BikeApp app = (BikeApp) getActivity().getApplication();

                // Setup user id preference
                SharedPreferences prefs = app.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                String userID = prefs.getString(getString(R.string.prefs_user_key), "Upload to generate a user ID");
                Preference userIdPref = findPreference("user_id");
                if (userIdPref != null) {
                    userIdPref.setSummary(userID);
                    userIdPref.setOnPreferenceClickListener(preference -> {
                        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("User ID", userID);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(app, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
                        return true;
                    });
                }

                // Setup email preference
                Preference emailPref = findPreference("email");
                if (emailPref != null) {
                    emailPref.setOnPreferenceClickListener(preference -> {
                        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Email", "kluedema@ualberta.ca");
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(app, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
                        return true;
                    });
                }

                Preference deletePref = findPreference("delete_local");
                if (deletePref != null) {
                    deletePref.setOnPreferenceClickListener(preference -> {
                        if (delete_count == 0) {
                            preference.setSummary("Click again to confirm");
                            delete_count++;
                        } else {
                            Toast.makeText(app, "Data Deleted!", Toast.LENGTH_SHORT).show();
                            preference.setSummary("");
                            delete_count = 0;
                            app.getRepository().deleteAll();
                        }
                        return true;
                    });
                }
            }
        }
    }
}