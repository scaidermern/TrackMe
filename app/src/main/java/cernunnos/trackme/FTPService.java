package cernunnos.trackme;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Locale;

/** Uploads locations via FTP */
public class FTPService extends IntentService {
    // tag for logging
    private static final String TAG = FTPService.class.getSimpleName();

    // actions
    // store (overwrite) a list of locations
    protected static final String ACTION_STORE_LOCATION_LIST = "action.store_location_list";
    // re-read settings
    protected static final String ACTION_REREAD_SETTINGS = "action.reread_settings";

    // parameters
    // a list of locations
    protected static final String EXTRA_PARAM_LOCATION_LIST = "extra.location_list";

    // settings
    protected String ftpUserName;
    protected String ftpPassword;
    protected String ftpServer;
    protected int    ftpPort;
    protected String ftpDir;
    protected String ftpFilename;
    protected boolean settingsInitialized = false;

    public FTPService() {
        super("FTPService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
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
            case ACTION_STORE_LOCATION_LIST: {
                if (!settingsInitialized) {
                    readSettings();
                }
                if (!intent.hasExtra(EXTRA_PARAM_LOCATION_LIST)) {
                    Log.e(TAG, "onHandleIntent(): action " + action + " lacks extra parameter");
                    return;
                }
                final MyLocationList locations = intent.getParcelableExtra(EXTRA_PARAM_LOCATION_LIST);
                doFTPStuff(locations.get());
                break;
            }
            case ACTION_REREAD_SETTINGS:
                readSettings();
                break;
            default:
                Log.e(TAG, "onHandleIntent(): invalid action: " + action);
                break;
        }
    }

    /** Initializes FTP settings */
    protected void readSettings() {
        Log.v(TAG, "readSettings()");
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        ftpUserName = sharedPref.getString(getString(R.string.preference_ftp_user_name), "");
        ftpPassword = sharedPref.getString(getString(R.string.preference_ftp_password), "");
        ftpServer = sharedPref.getString(getString(R.string.preference_ftp_server), "");
        ftpPort = Integer.parseInt(sharedPref.getString(getString(R.string.preference_ftp_port), "21"));
        ftpDir = sharedPref.getString(getString(R.string.preference_ftp_dir),
                getString(R.string.pref_upload_default_ftp_filename));
        ftpFilename = sharedPref.getString(getString(R.string.preference_ftp_filename),
                getString(R.string.pref_upload_default_ftp_filename));

        Log.v(TAG, "readSettings(): user: " + ftpUserName + ", server: " + ftpServer + ", port: " + ftpPort +
                ", dir: " + ftpDir + ", file name: " + ftpFilename);

        settingsInitialized = true;
    }

    /**
     * Starts this service to store (overwrite) a list of locations on the server.
     * If the service is already performing a task this action will be queued.
     */
    public static void startActionStoreLocationList(final Context context, final MyLocationList locations) {
        Intent intent = new Intent(context, FTPService.class);
        intent.setAction(ACTION_STORE_LOCATION_LIST);
        intent.putExtra(EXTRA_PARAM_LOCATION_LIST, (Parcelable)locations);
        context.startService(intent);
    }

    /**
     * Re-reads settings.
     * Required if FTP settings change after FTPService has been started.
     */
    public static void startActionReReadSettings(final Context context) {
        Intent intent = new Intent(context, FTPService.class);
        intent.setAction(ACTION_REREAD_SETTINGS);
        context.startService(intent);
    }

    @SuppressLint("DefaultLocale")
    protected void doFTPStuff(final ArrayDeque<MyLocation> locations) {
        final FTPClient ftp = new FTPClient();
        ftp.setDataTimeout(5 * 1000); // 5 seconds
        try {
            ftp.connect(ftpServer, ftpPort);
            Log.v(TAG, "onLocationChanged(): connected to FTP server");

            int ret = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(ret)) {
                Log.e(TAG, "onLocationChanged(): FTP server refused connection, code: " + ret);
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): could not connect to FTP server: " + e.getMessage());
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
            return;
        }

        try {
            if (!ftp.login(ftpUserName, ftpPassword)) {
                Log.e(TAG, "onLocationChanged(): FTP login failed, wrong credentials? code: " + ftp.getReplyCode());
                ftp.logout();
                if (ftp.isConnected()) {
                    try {
                        ftp.disconnect();
                    } catch (IOException f) {
                        // fuck you
                    }
                }
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): FTP login failed: " + e.getMessage());
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
            return;
        }

        try {
            if (!ftp.setFileType(FTP.ASCII_FILE_TYPE)) {
                Log.e(TAG, "onLocationChanged(): could not set file type. code: " + ftp.getReplyCode());
                ftp.logout();
                if (ftp.isConnected()) {
                    try {
                        ftp.disconnect();
                    } catch (IOException f) {
                        // fuck you
                    }
                }
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): could not set file type: " + e.getMessage());
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
            return;
        }

        try {
            if (!ftp.changeWorkingDirectory(ftpDir)) {
                Log.e(TAG, "onLocationChanged(): could not change working directory to " + ftpDir + ", wrong path? code: " + ftp.getReplyCode());
                ftp.logout();
                if (ftp.isConnected()) {
                    try {
                        ftp.disconnect();
                    } catch (IOException f) {
                        // fuck you
                    }
                }
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): could not change working directory to " + ftpDir + ": " + e.getMessage());
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
            return;
        }

        ftp.enterLocalPassiveMode();

        try {
            OutputStream file = ftp.storeFileStream(ftpFilename);
            if (file == null) {
                Log.e(TAG, "onLocationChanged(): could not open file " + ftpFilename + ": code: " + ftp.getReplyCode());
                if (ftp.isConnected()) {
                    try {
                        ftp.disconnect();
                    } catch (IOException f) {
                        // fuck you
                    }
                }
                return;
            }
            for (final MyLocation loc : locations) {
                // five decimal points represents an accuracy of one meter (roughly), should be enough
                file.write(String.format(Locale.US, "%.5f %.5f\n", loc.latitude, loc.longitude).getBytes("UTF-8"));
            }
            file.close();
            if (!ftp.completePendingCommand()) {
                Log.e(TAG, "onLocationChanged(): could not upload file: code: " + ftp.getReplyCode());
            }
            Log.v(TAG, "onLocationChanged(): file upload succeeded");
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): could not upload file: " + e.getMessage());
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
            return;
        }

        try {
            ftp.noop();
            ftp.logout();
        } catch (IOException e) {
            Log.e(TAG, "onLocationChanged(): error after uploading file: " + e.getMessage());
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException f) {
                    // fuck you
                }
            }
        }
    }
}
