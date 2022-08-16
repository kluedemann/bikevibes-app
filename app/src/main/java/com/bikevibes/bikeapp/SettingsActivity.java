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
import androidx.preference.EditTextPreference;
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

        // Set toolbar
        Toolbar myToolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(myToolbar);

        // Enable back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * The settings fragment containing all of the individual preferences.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        private int delete_count = 0;
        private int delete_remote = 0;

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
                initializeAlias(app);
                initializeEmail(app);
                initializeDeleteLocal(app);
                initializeDeleteRemote(app);
            }
            initializeLinks();
        }

        /**
         * Initialize alias preference.
         * Send a request to the server to update the user's alias upon being clicked.
         * @param app - the application object
         */
        private void initializeAlias(BikeApp app) {
            // Setup alias preference
            EditTextPreference aliasPref = findPreference(getString(R.string.alias_pref_key));
            if (aliasPref != null) {
                // Set summary to current alias value
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
                String alias = prefs.getString(getString(R.string.alias_pref_key), getString(R.string.no_alias));
                aliasPref.setSummary(alias);

                // Allow user to change alias on the remote server
                aliasPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String userID = getUserId(app);
                    String newAlias = String.valueOf(newValue);
                    app.getQueue().add(createRequest(userID, newAlias, app));
                    return false;
                });

                // Set a hint for the text box
                aliasPref.setOnBindEditTextListener(editText -> editText.setHint(R.string.alias_pref_hint));
            }
        }

        /**
         * Create a JsonObjectRequest to set an alias for a given userID.
         * This request can then be added to a Volley RequestQueue to send to the server
         * @param userID - the string user ID
         * @param alias - the new alias to be set
         * @param app - the application object
         * @return - the JsonObjectRequest to add to the queue
         */
        @NonNull
        private JsonObjectRequest createRequest(String userID, String alias, BikeApp app) {
            // Encode the alias so it can be added to the URL
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
                boolean isSuccess = response.optBoolean(getString(R.string.HTTP_success_key), false);
                if (isSuccess) {
                    updateAlias(alias);
                    Toast.makeText(app, app.getString(R.string.alias_success), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(app, app.getString(R.string.alias_error), Toast.LENGTH_SHORT).show();
                }
            }, error -> {
                // onErrorResponse: Called upon receiving an error response
                if (error instanceof ServerError && error.networkResponse.statusCode == 409) {
                    Toast.makeText(app, app.getString(R.string.duplicate_alias), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(app, app.getString(R.string.alias_error), Toast.LENGTH_SHORT).show();
                }
            });

            jORequest.setTag(TAG);
            return jORequest;
        }

        /**
         * Initialize email preference.
         * Open an app that prepares an email upon being clicked.
         * @param app - the application object
         */
        private void initializeEmail(BikeApp app) {
            // Setup email preference
            Preference emailPref = findPreference(getString(R.string.email_key));
            if (emailPref != null) {
                emailPref.setOnPreferenceClickListener(preference -> {
                    // Create an intent to draft an email
                    Intent i = new Intent(Intent.ACTION_SENDTO);
                    i.setData(Uri.parse("mailto:" + getString(R.string.email_address)));

                    // Open the default email app if it exists
                    try {
                        startActivity(i);
                    } catch (ActivityNotFoundException ex) {
                        // Exception if user has no email app
                        Toast.makeText(app, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }
        }

        /**
         * Initialize local deletion preference.
         * Delete the user's data from the local database after 3 clicks.
         * @param app - the application object
         */
        private void initializeDeleteLocal(BikeApp app) {
            // Allow user to delete local data by clicking twice
            Preference deletePref = findPreference(getString(R.string.delete_local_key));
            if (deletePref != null) {
                deletePref.setOnPreferenceClickListener(preference -> {
                    if (delete_count == 0) {
                        // User clicks for the first time
                        preference.setSummary(getString(R.string.twice_confirmation));
                        delete_count++;
                    } else if (delete_count == 1) {
                        // User clicks a second time
                        preference.setSummary(getString(R.string.confirmation_text));
                        delete_count++;
                    } else {
                        // Delete data on third click
                        Toast.makeText(app, getString(R.string.deleted_text), Toast.LENGTH_SHORT).show();
                        preference.setSummary("");
                        delete_count = 0;
                        app.getRepository().deleteAll();
                    }
                    return true;
                });
            }
        }

        /**
         * Initialize remote deletion preference.
         * Send a request to the server to delete the user's data after 3 clicks.
         * @param app - the application object
         */
        private void initializeDeleteRemote(BikeApp app) {
            // Allow user to delete local data by clicking twice
            Preference remotePref = findPreference(getString(R.string.delete_remote_key));
            if (remotePref != null) {
                remotePref.setOnPreferenceClickListener(preference -> {
                    if (delete_remote == 0) {
                        // User clicked once
                        preference.setSummary(getString(R.string.twice_confirmation));
                        delete_remote++;
                    } else if (delete_remote == 1) {
                        // User clicked twice
                        preference.setSummary(getString(R.string.confirmation_text));
                        delete_remote++;
                    } else {
                        // Delete data upon third click
                        Toast.makeText(app, getString(R.string.deleted_text), Toast.LENGTH_SHORT).show();
                        preference.setSummary("");
                        delete_remote = 0;
                        app.getQueue().add(getDeleteRequest(app));
                    }
                    return true;
                });
            }
        }

        /**
         * Initialize the link preferences.
         * Each URL will be opened in a browser upon clicking on the preference.
         */
        private void initializeLinks() {
            final int numLinks = 6;
            final int[] keys = {R.string.privacy_policy_key, R.string.website_pref_key, R.string.cybera_key, R.string.tforest_key, R.string.osm_key, R.string.nserc_key};
            final int[] urls = {R.string.privacy_policy_url, R.string.website_pref_url, R.string.cybera_url, R.string.tforest_url, R.string.osm_url, R.string.nserc_url};

            // Create a click listener for each link that opens a URL
            for (int i = 0; i < numLinks; i++) {
                // Find the correct preference
                Preference pref = findPreference(getString(keys[i]));
                if (pref != null) {
                    // Open URL upon click
                    final String url = getString(urls[i]);
                    pref.setOnPreferenceClickListener(preference -> {
                        openURL(url);
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
            // Open the link in the default app
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                startActivity(browserIntent);
            } catch (ActivityNotFoundException e) {
                // Catch exception if there is no suitable app to open links
                Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity.getApplicationContext(), R.string.link_error, Toast.LENGTH_SHORT).show();
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
            // Get the user ID
            final String USER_KEY = getString(R.string.prefs_user_key);
            SharedPreferences sharedPrefs = app.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            String userID = sharedPrefs.getString(USER_KEY, null);

            // Set userID if there is none
            if (userID == null) {
                userID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(USER_KEY, userID);
                editor.apply();
            }
            return userID;
        }

        /**
         * Create a JsonObjectRequest to delete the data for the user.
         * This request can then be added to a Volley RequestQueue to send to the server
         * @param app - the application object
         * @return - the JsonObjectRequest to add to the queue
         */
        @NonNull
        private JsonObjectRequest getDeleteRequest(BikeApp app) {
            // Create query URL
            String userID = getUserId(app);
            String url = String.format("http://162.246.157.171:8080/delete/%s", userID);

            // Create volley request
            JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.DELETE, url, null, response -> {
                // onResponse: Called upon receiving a response from the server
                boolean isSuccess = response.optBoolean(getString(R.string.HTTP_success_key), false);
                if (isSuccess) {
                    updateAlias(null);
                    Toast.makeText(app, app.getString(R.string.deleted_text), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(app, app.getString(R.string.delete_error), Toast.LENGTH_SHORT).show();
                }
            }, error -> {
                // onErrorResponse: Called upon receiving an error response
                Toast.makeText(app, app.getString(R.string.delete_error), Toast.LENGTH_SHORT).show();
            });

            jORequest.setTag(TAG);
            return jORequest;
        }

        /**
         * Update the user's alias and set the summary.
         * @param alias - the new alias
         */
        private void updateAlias(String alias) {
            // Write the new alias to shared preferences
            Activity activity = getActivity();
            if (activity != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(getString(R.string.alias_pref_key), alias);
                editor.apply();
            }

            // Update the summary to reflect the change
            Preference aliasPref = findPreference(getString(R.string.alias_pref_key));
            if (aliasPref != null) {
                if (alias == null) {
                    aliasPref.setSummary(getString(R.string.no_alias));
                } else {
                    aliasPref.setSummary(alias);
                }
            }
        }
    }
}