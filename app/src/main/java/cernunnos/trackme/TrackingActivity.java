package cernunnos.trackme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

/* TODO:
 * - app icon
 * - own class for FTP access, allows to check FTP access in main activity
 * / FTP intent service:
 *   - rework this pile of crap
 * - settings:
 *   - calculate max expected data consumption (1/2/4/6 hours?)
 *     - login/logout or just emptying locations file: 2006 bytes
 *     - single location: 18 bytes
 *   - disable automatic ftp upload
 *   / ftp settings:
 *     / user, password, server, port, dir, filename
 *     - FTPS? active mode?
 *     - upload interval (always, time interval)?
 * - TrackingActivity:
 *   / average speed, max speed -> calculate avg between locations, max also additionally
 *   - display time of last upload
 *   - button for "force upload"?
 * - show satellite information in TrackingActivity and status notification (periodic broadcast from GPSReceiver?)
 * - horizontal layout, rework main layout
 * - toolbar in SettingsActivity
 * - support for encoded polyline?
 * - support for location updates via wifi?
 * - additional upload mechanisms (e.g. HTTP)
 * - setting for keeping the display on? (FLAG_KEEP_SCREEN_ON)
 * - avgSpeed: ignore idle times (easy) and tracking restarts (difficult, needs dummy location?)
 * - re-check activity lifecycle:
 *   - onPause(), onResume() https://developer.android.com/training/basics/activity-lifecycle/pausing.html
 *   - onStop(), onStart() https://developer.android.com/training/basics/activity-lifecycle/stopping.html
 *   - onSaveInstanceState(), onRestoreInstanceState() https://developer.android.com/training/basics/activity-lifecycle/recreating.html
 */

/** Main activity */
public class TrackingActivity extends AppCompatActivity {
    // tag for logging
    private static final String TAG = TrackingActivity.class.getSimpleName();

    // unique ID for request permission callback
    private static final int LOCATION_PERMISSION_CB_ID = 42;

    // keeps state about whether tracking is currently enabled,
    // i.e. GPSReceiver service is currently running
    private boolean trackingEnabled = false;

    // toolbar menu
    private Menu optionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");

        setContentView(R.layout.activity_tracking);

        // set up custom tool bar
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // receive broadcast messages from GPSReceiver service
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mGPSReceiver, new IntentFilter(GPSReceiver.class.getSimpleName()));

        // check if GPSReceiver is recording (i.e. we got killed but the GPS service not)
        trackingEnabled = GPSReceiver.isRecording;

        // get previously recorded locations
        GPSReceiver.startActionBroadcastLocations(this);
    }

    /** Populate tool bar */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);

        final boolean firstRun = (optionsMenu == null);
        optionsMenu = menu;
        if (firstRun) {
            updateStartStopAction();
        }
        return true;
    }

    /** Handle tool bar selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "onOptionsItemSelected()");
        switch (item.getItemId()) {
            case R.id.action_start_stop_tracking:
                Log.v(TAG, "onOptionsItemSelected(): action_start_stop_tracking");
                startStopTracking();
                return true;
            case R.id.action_add_single_location:
                Log.v(TAG, "onOptionsItemSelected(): action_add_single_location");
                addSingleLocation();
                return true;
            case R.id.action_clear_all_locations:
                Log.v(TAG, "onOptionsItemSelected(): action_delete_locations");
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title_clear_all_locations)
                        .setMessage(R.string.dialog_body_clear_all_locations)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                GPSReceiver.startActionDeleteAllLocations(TrackingActivity.this);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .create();
                dialog.show();
                return true;
            case R.id.action_settings:
                Log.v(TAG, "onOptionsItemSelected(): action_settings");
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Handle permission request callback */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.v(TAG, "onRequestPermissionsResult()");
        switch (requestCode) {
            case LOCATION_PERMISSION_CB_ID: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startTracking();
                } else {
                    Log.v(TAG, "No location permission granted by user :(");
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.toast_location_permissions_missing, Toast.LENGTH_LONG);
                    toast.show();
                }
                break;
            }
        }
    }

    /** Handle broadcast messages from GPSReceiver */
    private BroadcastReceiver mGPSReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(GPSReceiver.EXTRA_PARAM_LOCATION_LIST)) {
                final MyLocationList locations = intent.getParcelableExtra(GPSReceiver.EXTRA_PARAM_LOCATION_LIST);
                handleLocationChanged(locations);
            } else if (intent.hasExtra(GPSReceiver.EXTRA_PARAM_GPS_PROVIDER)) {
                showGPSProviderHint(intent.getExtras().getBoolean(GPSReceiver.EXTRA_PARAM_GPS_PROVIDER));
            }
        }
    };

    /** Toggle location recording */
    protected void startStopTracking() {
        Log.v(TAG, "startStopTracking()");
        if (trackingEnabled) {
            stopTracking();
        } else {
            // request permissions during runtime if necessary (Android >= 6)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CB_ID);
            } else {
                startTracking();
            }
        }
    }

    /** Start tracking */
    protected void startTracking() {
        Log.v(TAG, "startTracking()");
        trackingEnabled = true;

        updateStartStopAction();

        GPSReceiver.startActionReceiveLocations(this);
    }

    /** Stop tracking */
    protected void stopTracking() {
        Log.v(TAG, "stopTracking()");
        trackingEnabled = false;

        updateStartStopAction();

        Intent gpsIntent = new Intent(this, GPSReceiver.class);
        stopService(gpsIntent);
    }

    /** Update label and appearance of start/stop action */
    protected void updateStartStopAction() {
        Log.v(TAG, "setStartStopActionLabel()");
        MenuItem item = optionsMenu.findItem(R.id.action_start_stop_tracking);
        item.setTitle(trackingEnabled ?
                getString(R.string.action_stop_tracking) :
                getString(R.string.action_start_tracking));
        item.setIcon(trackingEnabled ?
                R.drawable.ic_my_location_recording_white_24dp :
                R.drawable.ic_my_location_white_24dp);
        item.setChecked(trackingEnabled);
    }

    protected void addSingleLocation() {
        Log.v(TAG, "addSingleLocation()");

        GPSReceiver.startActionAddSingleLocation(this);
    }

    /** Handle location updates */
    private void handleLocationChanged(final MyLocationList locations) {
        updateGeneralStatistics(locations);
        updateBacklogStatistics(locations);
    }

    /** Update general location statistics such as lat/lon, current speed etc. */
    @SuppressLint({"DefaultLocale", "SimpleDateFormat"})
    private void updateGeneralStatistics(final MyLocationList locations) {
        final MyLocationList.Statistics stats = locations.getStatistics();
        final MyLocation curLocation = locations.getLast();

        // position
        TextView lat = (TextView)findViewById(R.id.Lat);
        lat.setText(curLocation == null ? getString(R.string.value_not_available_short) :
                String.format("%.5f", curLocation.latitude));
        TextView lon = (TextView)findViewById(R.id.Lon);
        lon.setText(curLocation == null ? getString(R.string.value_not_available_short) :
                String.format("%.5f", curLocation.longitude));

        // time of last location
        TextView timestamp = (TextView)findViewById(R.id.timestamp);
        timestamp.setText(curLocation == null ? getString(R.string.value_not_available_short) :
                new SimpleDateFormat("HH:mm:ss").format(new Date(curLocation.time)));

        // current speed
        TextView speed = (TextView) findViewById(R.id.speed);
        if (curLocation != null && curLocation.hasSpeed) {
            float speed_kmh = curLocation.speed * 3.6f;
            speed.setText(String.format("%.1f %s",
                    speed_kmh, getString(R.string.speed_unit_kilometers_per_hour)));
        } else {
            speed.setText(getString(R.string.value_not_available_short));
        }

        // distance to previous location
        TextView distance = (TextView)findViewById(R.id.distance);
        distance.setText(Helper.humanReadableDistance(this, stats.distanceLast));
        if (curLocation != null && locations.size() >= 2) {
            distance.setText(Helper.humanReadableDistance(this, stats.distanceLast));
        } else {
            distance.setText(getString(R.string.value_not_available_short));
        }

        // accuracy
        TextView accuracy = (TextView)findViewById(R.id.accuracy);
        accuracy.setText((curLocation == null || !curLocation.hasAccuracy) ? getString(R.string.value_not_available_short) :
                Helper.humanReadableDistance(this, curLocation.accuracy));
    }

    /** Update backlog statistics such as number of locations, total distance etc. */
    @SuppressLint("DefaultLocale")
    private void updateBacklogStatistics(final MyLocationList locations) {
        final MyLocationList.Statistics stats = locations.getStatistics();
        final boolean noBacklog = (locations.size() <= 1);

        // number of locations
        TextView numLocations = (TextView)findViewById(R.id.backlog_locations);
        numLocations.setText(String.valueOf(locations.size()));

        // total distance
        TextView distance = (TextView)findViewById(R.id.backlog_distance);
        distance.setText(noBacklog ? getString(R.string.value_not_available_short) :
                Helper.humanReadableDistance(this, stats.distanceTotal));

        // speed
        TextView speedAvgV = (TextView)findViewById(R.id.backlog_speed_avg);
        speedAvgV.setText(noBacklog ? getString(R.string.value_not_available_short) :
                String.format("%.1f %s", stats.speedAvg, getString(R.string.speed_unit_kilometers_per_hour)));
        TextView speedMaxV = (TextView)findViewById(R.id.backlog_speed_max);
        speedMaxV.setText(noBacklog ? getString(R.string.value_not_available_short) :
                String.format("%.1f %s", stats.speedMax, getString(R.string.speed_unit_kilometers_per_hour)));

        // total duration
        TextView duration = (TextView)findViewById(R.id.backlog_duration);
        duration.setText(noBacklog ? getString(R.string.value_not_available_short) :
                Helper.humanReadableDuration(this, stats.duration));
    }

    /** Show information about disabled/enabled GPS provider */
    private void showGPSProviderHint(boolean gpsEnabled) {
        if (!gpsEnabled) {
            // warn about disabled GPS provider
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_gps_provider_disabled)
                    .setMessage(R.string.dialog_body_gps_provider_disabled)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
            dialog.show();
        } else {
            // notify about GPS provider being enabled now
            Toast toast = Toast.makeText(getApplicationContext(), R.string.toast_gps_provider_enabled, Toast.LENGTH_LONG);
            toast.show();
        }
    }
}
