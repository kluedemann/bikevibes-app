package com.bikevibes.bikeapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.ServerError;
import com.android.volley.toolbox.JsonObjectRequest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * Handles the UI logic for the settings menu.
 * Created when the settings menu is opened and destroyed when it is closed.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "Settings";

    /**
     * Set up the settings menu and toolbar.
     * Called when the settings menu is opened
     * @param savedInstanceState - the saved state of the activity
     */
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

    /**
     * The settings fragment containing all of the individual preferences.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        private int delete_count;

        /**
         * Set up the preferences. Create onClick listeners for interactive preferences.
         * @param savedInstanceState - the fragment's saved state
         * @param rootKey - indicates where the preference fragment is rooted
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            if (getActivity() != null) {
                BikeApp app = (BikeApp) getActivity().getApplication();

                // Setup alias preference
                Preference aliasPref = findPreference(getString(R.string.alias_pref_key));
                if (aliasPref != null) {
                    // Set summary to current alias value
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
                    String alias = prefs.getString(getString(R.string.alias_pref_key), getString(R.string.no_alias));
                    aliasPref.setSummary(alias);

                    // Allow user to change alias on the remote server
                    aliasPref.setOnPreferenceChangeListener((preference, newValue) -> {
                        String userID = getUserId(app);
                        String newAlias = String.valueOf(newValue);
                        app.getQueue().add(createRequest(userID, newAlias));
                        return false;
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

                // Allow user to delete local data by clicking twice
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

                // Link to Privacy Policy
                Preference privacyPref = findPreference(getString(R.string.privacy_policy_key));
                if (privacyPref != null) {
                    privacyPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.privacy_policy_url));
                        return true;
                    });
                }

                // Link to Website
                Preference websitePref = findPreference(getString(R.string.website_pref_key));
                if (websitePref != null) {
                    websitePref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.website_pref_url));
                        return true;
                    });
                }

                // Link to Cybera website
                Preference cyberaPref = findPreference(getString(R.string.cybera_key));
                if (cyberaPref != null) {
                    cyberaPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.cybera_url));
                        return true;
                    });
                }

                // Link to Thunderforest website
                Preference tforestPref = findPreference(getString(R.string.tforest_key));
                if (tforestPref != null) {
                    tforestPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.tforest_url));
                        return true;
                    });
                }

                // Link to OSM website
                Preference osmPref = findPreference(getString(R.string.osm_key));
                if (osmPref != null) {
                    osmPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.osm_url));
                        return true;
                    });
                }

                // Link to NSERC website
                Preference nsercPref = findPreference(getString(R.string.nserc_key));
                if (nsercPref != null) {
                    nsercPref.setOnPreferenceClickListener(preference -> {
                        openURL(getString(R.string.nserc_url));
                        return true;
                    });
                }
            }
        }

        /**
         * Open a given URL in a browser
         * @param url - the URL string to open
         */
        private void openURL(String url) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                startActivity(browserIntent);
            } catch (ActivityNotFoundException e) {
                Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity.getApplicationContext(), "Error: Could not open link", Toast.LENGTH_SHORT).show();
                }
            }
        }

        /**
         * Get the user ID or create one if needed
         * @param app - the application object
         * @return the user ID
         */
        @NonNull
        private String getUserId(@NonNull BikeApp app) {
            final String USER_KEY = getString(R.string.prefs_user_key);
            SharedPreferences sharedPrefs = app.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            String userID = sharedPrefs.getString(USER_KEY, null);
            if (userID == null) {
                userID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(USER_KEY, userID);
                editor.apply();
            }
            return userID;
        }

        /**
         * Create a JsonObjectRequest to set an alias for a given userID.
         * This request can then be added to a Volley RequestQueue to send to the server
         * @param userID - the string user ID
         * @param alias - the new alias to be set
         * @return - the JsonObjectRequest to add to the queue
         */
        @NonNull
        private JsonObjectRequest createRequest(String userID, String alias) {
            String newAlias = alias;
            try {
                newAlias = URLEncoder.encode(alias, "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            String url = String.format("http://162.246.157.171:8080/upload/alias?user_id=%s&alias=%s", userID, newAlias);

            // Create the HTTP request
            JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, url, null, response -> {
                // onResponse: Called upon receiving a response from the server
                //Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
                boolean isSuccess = response.optBoolean(getString(R.string.HTTP_success_key), false);
                requestCompleted(isSuccess, alias);
            }, error -> {
                // onErrorResponse: Called upon receiving an error response
                //Log.e(TAG, error.toString());
                Activity activity = getActivity();
                if (activity != null) {
                    Context ctx = activity.getApplicationContext();
                    if (error instanceof ServerError && error.networkResponse.statusCode == 409) {
                        Toast.makeText(ctx, "Error: Alias already exists", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Error: Alias not updated", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Add request to the queue
            jORequest.setTag(TAG);
            return jORequest;
        }

        /**
         * Update the alias if the request was successful.
         * Display a message indicating whether it was successful or not.
         * Called when the Volley request to change alias receives a response.
         * @param success - true if the alias was changed successfully; false otherwise
         * @param alias - the new alias
         */
        private void requestCompleted(boolean success, String alias) {
            Activity activity = getActivity();
            if (activity != null) {
                Context ctx = activity.getApplicationContext();
                if (success) {
                    // Update alias
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(getString(R.string.alias_pref_key), alias);
                    editor.apply();

                    // Update summary
                    Preference pref = findPreference(getString(R.string.alias_pref_key));
                    if (pref != null) {
                        pref.setSummary(alias);
                    }

                    Toast.makeText(ctx, "Alias updated successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, "Error: Alias not updated", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}