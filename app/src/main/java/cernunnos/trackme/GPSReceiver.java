package cernunnos.trackme;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

/** Receives and manages locations */
public class GPSReceiver extends Service implements LocationListener {
    // tag for logging
    private static final String TAG = GPSReceiver.class.getSimpleName();

    // unique ID for status bar notifications
    private static final int NOTIFICATION_ID = 23;

    // actions
    // start receiving location updates
    private static final String ACTION_RECEIVE_LOCATIONS = "action.receive_locations";
    // request broadcast message about current locations
    private static final String ACTION_BROADCAST_LOCATIONS = "action.broadcast_locations";
    // delete all locations
    private static final String ACTION_DELETE_ALL_LOCATIONS = "action.delete_all_locations";
    // re-read settings
    private static final String ACTION_REREAD_SETTINGS = "action.reread_settings";

    // parameters
    // a list of locations
    public static final String EXTRA_PARAM_LOCATION_LIST = "extra.location_list";
    // boolean about the current GPS provider state
    public static final String EXTRA_PARAM_GPS_PROVIDER = "extra.gps_provider";

    // files
    // location backlog
    private static final String FILE_LOCATION_BACKLOG = "locationBacklog";

    // keeps state about whether this service is currently receiving location updates
    public static boolean isRecording = false;

    protected LocationManager locationManager;

    // minimum time interval between location updates, in seconds
    // can be 0 for considering only cMinDistanceMeters
    protected int cMinTimeSecs;

    // minimum distance between location updates, in meters
    // can be 0 for considering only cMinTimeSecs
    protected float cMinDistanceMeters;

    // maximum number of locations to store in backlog,
    // can be 0 for no upper limit
    protected int cMaxLocations;

    // minimum time between uploading if locations in seconds
    protected long cUploadIntervalSecs;

    // minimum time between saving of locations to storage in minutes
    protected final int saveIntervalMins = 2;

    // backlog of locations (first entry = oldest, last entry = newest)
    protected MyLocationList lastLocations = new MyLocationList();

    // time of last upload of locations
    protected long lastUploadMillis = 0;

    // time of last saving of locations to storage
    protected long lastSaveMillis = 0;

    public GPSReceiver() {
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate()");

        // read settings
        readSettings();

        // (try to) read last location backlog from internal storage
        restoreProgressFromStorage();

        // send broadcast message with location backlog from storage
        sendLocationBroadcast();
    }

    /** Initialize location recording settings */
    protected void readSettings() {
        Log.v(TAG, "readSettings()");
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        cMinTimeSecs = Integer.parseInt(sharedPref.getString(getString(R.string.preference_recording_min_time),
                getString(R.string.pref_recording_default_min_time)));
        cMinDistanceMeters = Float.parseFloat(sharedPref.getString(getString(R.string.preference_recording_min_distance),
                getString(R.string.pref_recording_default_min_distance)));
        cMaxLocations = Integer.parseInt(sharedPref.getString(getString(R.string.preference_recording_max_locations),
                getString(R.string.pref_recording_default_max_locations)));
        cUploadIntervalSecs = Long.parseLong(sharedPref.getString(getString(R.string.preference_uploading_interval),
                getString(R.string.pref_upload_default_interval)));

        Log.v(TAG, "readSettings(): minTime: " + cMinTimeSecs + "s, minDist: " + cMinDistanceMeters + "m, " +
                "max locations: " + cMaxLocations + ", upload interval: " + cUploadIntervalSecs + "s");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        // as a service we must call this ourselves
        if (intent != null && intent.getAction() != null) {
            onHandleIntent(intent);
        }
        return Service.START_STICKY;
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public void onDestroy() {
        Log.v(TAG, "onDestroy()");

        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

        // force saving location backlog to internal storage
        saveProgressToStorage(true);

        // force uploading locations to server
        uploadProgress(true);

        isRecording = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind()");
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Handle intents
     * Note: for services this is no override, instead we have to explicitly call it during onStartCommand()
     */
    protected void onHandleIntent(final Intent intent) {
        Log.v(TAG, "onHandleIntent()");
        if (intent == null) {
            Log.w(TAG, "onHandleIntent(): could not handle intent: null");
            return;
        }

        final String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "onHandleIntent(): could not handle intent action: null");
            return;
        }

        Log.v(TAG, "onHandleIntent(): action " + action);
        switch (action) {
            case ACTION_RECEIVE_LOCATIONS:
                startForeground(NOTIFICATION_ID, buildNotification());
                requestLocationUpdates();
                break;
            case ACTION_BROADCAST_LOCATIONS:
                sendLocationBroadcast();
                break;
            case ACTION_DELETE_ALL_LOCATIONS:
                deleteLocations();
                break;
            case ACTION_REREAD_SETTINGS:
                readSettings();
                if (isRecording) {
                    requestLocationUpdates();
                }
                break;
            default:
                Log.e(TAG, "onHandleIntent(): invalid action: " + action);
                break;
        }
    }

    /** Handle location updates */
    @SuppressLint({"DefaultLocale", "SimpleDateFormat"})
    @Override
    public void onLocationChanged(Location location) {
        Log.v(TAG, "onLocationChanged()");
        if (location != null) {
            // update backlog
            if (cMaxLocations > 0 && lastLocations.size() + 1 >= cMaxLocations) {
                lastLocations.removeFirst();
            }
            lastLocations.addLast(new MyLocation(location));
        } // else continue, we have been called from deleteLocations()

        // save to storage if save interval reached
        saveProgressToStorage(false);

        // send broadcast message with location backlog
        sendLocationBroadcast();

        // update status bar if we have a new location
        if (location != null) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, buildNotification());
        }

        // trigger ftp upload if upload interval reached
        uploadProgress(false);
    }

    /** Send broadcast message with location backlog */
    protected void sendLocationBroadcast() {
        Log.v(TAG, "sendLocationBroadcast()");
        Intent intent = new Intent(GPSReceiver.class.getSimpleName());
        intent.putExtra(EXTRA_PARAM_LOCATION_LIST, (Parcelable)lastLocations);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /** Delete all locations and trigger FTP upload */
    protected void deleteLocations() {
        Log.v(TAG, "deleteLocations()");
        lastLocations.clear();
        onLocationChanged(null);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        // don't care about provider status changes since we already
        // handle onProviderEnabled() and onProviderDisabled()
    }

    /** Handle enabling of GPS provider */
    @Override
    public void onProviderEnabled(String s) {
        Log.v(TAG, "onProviderEnabled()");
        // send broadcast about enabled GPS provider
        Intent intent = new Intent(GPSReceiver.class.getSimpleName());
        intent.putExtra(EXTRA_PARAM_GPS_PROVIDER, true);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /** Handle disabling of GPS provider */
    @Override
    public void onProviderDisabled(String s) {
        Log.v(TAG, "onProviderDisabled()");
        // send broadcast about disabled GPS provider
        Intent intent = new Intent(GPSReceiver.class.getSimpleName());
        intent.putExtra(EXTRA_PARAM_GPS_PROVIDER, false);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Start receiving location updates or change update interval
     * We don't check for permissions here. This must be handled by the main activity!
     */
    @SuppressWarnings("MissingPermission")
    protected void requestLocationUpdates() {
        Log.v(TAG, "requestLocationUpdates()");

        isRecording = true;

        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, cMinTimeSecs * 1000, cMinDistanceMeters, this);
    }

    /**
     * Start receiving location updates.
     */
    public static void startActionReceiveLocations(final Context context) {
        Log.v(TAG, "startActionReceiveLocations()");
        Intent intent = new Intent(context, GPSReceiver.class);
        intent.setAction(ACTION_RECEIVE_LOCATIONS);
        context.startService(intent);
    }

    /**
     * Request a broadcast message about current locations.
     */
    public static void startActionBroadcastLocations(final Context context) {
        Log.v(TAG, "startActionBroadcastLocations()");
        Intent intent = new Intent(context, GPSReceiver.class);
        intent.setAction(ACTION_BROADCAST_LOCATIONS);
        context.startService(intent);
    }

    /**
     * Request a broadcast message about current locations.
     */
    public static void startActionDeleteAllLocations(final Context context) {
        Log.v(TAG, "startActionDeleteAllLocations()");
        Intent intent = new Intent(context, GPSReceiver.class);
        intent.setAction(ACTION_DELETE_ALL_LOCATIONS);
        context.startService(intent);
    }

    /**
     * Re-read settings.
     * Required if location recording settings change after GPSReceiver service has been started.
     */
    public static void startActionReReadSettings(final Context context) {
        Log.v(TAG, "startActionReReadSettings()");
        Intent intent = new Intent(context, GPSReceiver.class);
        intent.setAction(ACTION_REREAD_SETTINGS);
        context.startService(intent);
    }

    /**
     * Save current location backlog to internal storage.
     * Set force to true for saving even if saveIntervalMins hasn't been reached yet
     */
    protected void saveProgressToStorage(boolean force) {
        Log.v(TAG, "saveProgressToStorage(): force " + force);
        if (lastLocations == null) {
            Log.v(TAG, "saveProgressToStorage(): lastLocations is null");
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && (now - lastSaveMillis) / 1000 / 60 < saveIntervalMins) {
            return;
        }
        lastSaveMillis = now;

        try {
            FileOutputStream outFile = openFileOutput(FILE_LOCATION_BACKLOG, Context.MODE_PRIVATE);
            ObjectOutput out = new ObjectOutputStream(outFile);
            out.writeObject(lastLocations);
            out.close();
            Log.i(TAG, "saveProgressToStorage(): saved " + lastLocations.size() + " locations to storage");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /** Restore last location backlog from internal storage */
    protected void restoreProgressFromStorage() {
        Log.v(TAG, "restoreProgressFromStorage()");
        try {
            FileInputStream inFile = openFileInput(FILE_LOCATION_BACKLOG);
            ObjectInput in = new ObjectInputStream(inFile);
            lastLocations = (MyLocationList)in.readObject();
            in.close();
            Log.i(TAG, "restoreProgressFromStorage(): read " + lastLocations.size() + " previous locations from storage");
        } catch (FileNotFoundException e) {
            Log.v(TAG, "restoreProgressFromStorage(): no previous backlog on storage");
        } catch (java.io.IOException | ClassNotFoundException e) {
            Log.v(TAG, "restoreProgressFromStorage(): unknown exception");
            // clear
            lastLocations = new MyLocationList();
            e.printStackTrace();
        }
    }

    /**
     * Upload locations to server.
     * Set force to true for saving even if cUploadIntervalSecs hasn't been reached yet
     */
    protected void uploadProgress(boolean force) {
        Log.v(TAG, "uploadProgress(): force " + force);
        if (lastLocations == null) {
            Log.v(TAG, "uploadProgress(): lastLocations is null");
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && (now - lastUploadMillis) / 1000 < cUploadIntervalSecs) {
            return;
        }
        lastUploadMillis = now;

        FTPService.startActionStoreLocationList(this, lastLocations);
    }

    /** Build status bar notification */
    @SuppressLint("DefaultLocale")
    protected Notification buildNotification() {
        final MyLocationList.Statistics stats = lastLocations.getStatistics();
        final String title = String.format("# %d %s %s",
                lastLocations.size(),
                getString(R.string.notification_distance_short),
                Helper.humanReadableDistance(this, stats.distanceTotal));
        final String description = String.format("%s %.1f %s %s %.1f %s",
                getString(R.string.notification_speed_average_short),
                stats.speedAvg, getString(R.string.speed_unit_kilometers_per_hour),
                getString(R.string.notification_speed_max_short),
                stats.speedMax, getString(R.string.speed_unit_kilometers_per_hour));

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_pin_trace_black_24dp)
                .setContentTitle(title)
                .setContentText(description);

        // open TrackingActivity if user taps on notification
        Intent resultIntent = new Intent(this, TrackingActivity.class);
        // create an artificial back stack so that navigating backward
        // from the activity leads to the home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(TrackingActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
            stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT
            );
        builder.setContentIntent(resultPendingIntent);

        return builder.build();
    }
}
