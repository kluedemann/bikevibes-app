package com.example.bikeapp;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
                String userID = prefs.getString(getString(R.string.prefs_user_key), getString(R.string.no_user_id));
                Preference userIdPref = findPreference(getString(R.string.user_id_pref_key));
                if (userIdPref != null) {
                    userIdPref.setSummary(userID);
                    userIdPref.setOnPreferenceClickListener(preference -> {
                        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("User ID", userID);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(app, getString(R.string.clipboard_text), Toast.LENGTH_SHORT).show();
                        return true;
                    });
                }

                // Setup email preference
                Preference emailPref = findPreference(getString(R.string.email_key));
                if (emailPref != null) {
                    emailPref.setOnPreferenceClickListener(preference -> {
                        Intent i = new Intent(Intent.ACTION_SENDTO);
                        i.setData(Uri.parse("mailto:" + getString(R.string.email_address)));
                        try {
                            startActivity(i);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(app, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }

                Preference deletePref = findPreference(getString(R.string.delete_local_key));
                if (deletePref != null) {
                    deletePref.setOnPreferenceClickListener(preference -> {
                        if (delete_count == 0) {
                            preference.setSummary(getString(R.string.confirmation_text));
                            delete_count++;
                        } else {
                            Toast.makeText(app, getString(R.string.deleted_text), Toast.LENGTH_SHORT).show();
                            preference.setSummary("");
                            delete_count = 0;
                            app.getRepository().deleteAll();
                        }
                        return true;
                    });
                }

                Preference cyberaPref = findPreference(getString(R.string.cybera_key));
                if (cyberaPref != null) {
                    cyberaPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.cybera_url));
                        return true;
                    });
                }

                Preference tforestPref = findPreference(getString(R.string.tforest_key));
                if (tforestPref != null) {
                    tforestPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.tforest_url));
                        return true;
                    });
                }

                Preference nsercPref = findPreference(getString(R.string.nserc_key));
                if (nsercPref != null) {
                    nsercPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.nserc_url));
                        return true;
                    });
                }
            }
        }

        public void openURL(String url) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }
}