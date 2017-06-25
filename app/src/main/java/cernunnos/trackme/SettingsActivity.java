package cernunnos.trackme;

import android.annotation.TargetApi;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {
    // tag for logging
    private static final String TAG = SettingsActivity.class.getSimpleName();

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        /// TODO XXX FIXME show currennt value _and_ summary from EditTextPreference
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            Log.v(TAG, "onPreferenceChange() for key " + preference.getKey() + " value " + value.toString());

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);
                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");

        // set up custom tool bar
        //Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        setupActionBar();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        Log.v(TAG, "setupActionBar()");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            /// TODO XXX FIXME
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || UploadPreferenceFragment.class.getName().equals(fragmentName)
                || RecordingPreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || DataSyncPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        // tag for logging
        private static final String TAG = GeneralPreferenceFragment.class.getSimpleName();
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.v(TAG, "onCreate()");
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("example_text"));
            bindPreferenceSummaryToValue(findPreference("example_list"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /** Show upload preferences */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class UploadPreferenceFragment extends PreferenceFragment {
        // tag for logging
        private static final String TAG = UploadPreferenceFragment.class.getSimpleName();

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.v(TAG, "onCreate()");
            addPreferencesFromResource(R.xml.pref_upload);
            setHasOptionsMenu(true);

            // show current values as summary (except for the password)
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_ftp_user_name)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_ftp_server)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_ftp_port)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_ftp_dir)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_ftp_filename)));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            Log.v(TAG, "onOptionsItemSelected()");
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.v(TAG, "onDestroy()");
            // tell FTP service to update its settings
            FTPService.startActionReReadSettings(getActivity().getApplicationContext());
        }
    }

    /** Show location recording preferences */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class RecordingPreferenceFragment extends PreferenceFragment {
        // tag for logging
        private static final String TAG = RecordingPreferenceFragment.class.getSimpleName();

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.v(TAG, "onCreate()");
            addPreferencesFromResource(R.xml.pref_recording);
            setHasOptionsMenu(true);

            // show current values as summary
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_recording_min_distance)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.preference_recording_min_time)));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            Log.v(TAG, "onOptionsItemSelected()");
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.v(TAG, "onDestroy()");
            // tell GPS receiver service to update its settings
            GPSReceiver.startActionReReadSettings(getActivity().getApplicationContext());
        }
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {
        // tag for logging
        private static final String TAG = NotificationPreferenceFragment.class.getSimpleName();
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.v(TAG, "onCreate()");
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("notifications_new_message_ringtone"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataSyncPreferenceFragment extends PreferenceFragment {
        // tag for logging
        private static final String TAG = DataSyncPreferenceFragment.class.getSimpleName();
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.v(TAG, "onCreate()");
            addPreferencesFromResource(R.xml.pref_data_sync);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("sync_frequency"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
