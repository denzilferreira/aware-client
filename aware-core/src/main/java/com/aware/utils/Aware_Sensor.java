
package com.aware.utils;

import android.Manifest;
import android.app.Service;
import android.content.*;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import androidx.core.content.PermissionChecker;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;

import java.util.ArrayList;

/**
 * Aware_Sensor: Extend to integrate with the framework (extension of Android Service class).
 *
 * @author dferreira
 */
public class Aware_Sensor extends Service {

    /**
     * Debug tag for this sensor
     */
    public String TAG = "AWARE Sensor";

    /**
     * Debug flag for this sensor
     */
    public boolean DEBUG = false;

    public ContextProducer CONTEXT_PRODUCER = null;

    /**
     * Permissions needed for this plugin to run
     */
    public ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();

    /**
     * Indicates if permissions were accepted OK
     */
    public boolean PERMISSIONS_OK = true;


    /**
     * Integration with sync adapters
     */
    public String AUTHORITY = "";

    /**
     * Interface to share context with other applications/addons<br/>
     * You MUST broadcast your contexts here!
     *
     * @author denzil
     */
    public interface ContextProducer {
        void onContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //Register Context Broadcaster
        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CURRENT_CONTEXT);
        filter.addAction(Aware.ACTION_AWARE_STOP_SENSORS);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);

        if (contextBroadcaster == null) {
            contextBroadcaster = new ContextBroadcaster(CONTEXT_PRODUCER, TAG, AUTHORITY);
        }

        registerReceiver(contextBroadcaster, filter);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.GET_ACCOUNTS);
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_SYNC_SETTINGS);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_SYNC_SETTINGS);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_SYNC_STATS);

        Log.d(Aware.TAG, "created: " + getClass().getName() + " package: " + getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PERMISSIONS_OK = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : REQUIRED_PERMISSIONS) {
                if (PermissionChecker.checkSelfPermission(this, p) != PermissionChecker.PERMISSION_GRANTED) {
                    PERMISSIONS_OK = false;
                    break;
                }
            }
        }

        if (!PERMISSIONS_OK) {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_SERVICE, getPackageName() + "/" + getClass().getName()); //restarts plugin once permissions are accepted
            startActivity(permissions);
        } else {
            PERMISSIONS_OK = true;
            if (Aware.getSetting(this, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                downloadCertificate(this);
            }
            //Aware.debug(this, "active: " + getClass().getName() + " package: " + getPackageName());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (PERMISSIONS_OK) {
            //Aware.debug(this, "destroyed: " + getClass().getName() + " package: " + getPackageName());
        }

        //Unregister Context Broadcaster
        if (contextBroadcaster != null) unregisterReceiver(contextBroadcaster);
    }

    /**
     * AWARE Context Broadcaster<br/>
     * - ACTION_AWARE_CURRENT_CONTEXT: returns current plugin's context
     * - ACTION_AWARE_SYNC_DATA: push content provider data remotely
     * - ACTION_AWARE_CLEAR_DATA: clears local and remote database
     * - ACTION_AWARE_STOP_SENSORS: stops this sensor
     * - ACTION_AWARE_SPACE_MAINTENANCE: clears old data from content providers
     *
     * @author denzil
     */
    public static class ContextBroadcaster extends BroadcastReceiver {

        private ContextProducer cp;
        private String tag;
        private String provider;

        public ContextBroadcaster(ContextProducer contextProducer, String logcatTag, String providerAuthority) {
            this.cp = contextProducer;
            this.tag = logcatTag;
            this.provider = providerAuthority;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Aware.ACTION_AWARE_CURRENT_CONTEXT)) {
                if (cp != null) {
                    cp.onContext();
                }
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_STOP_SENSORS)) {
                if (Aware.DEBUG) Log.d(tag, tag + " stopped");
                try {
                    Intent self = new Intent(context, Class.forName(context.getApplicationContext().getClass().getName()));
                    context.stopService(self);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && provider.length() > 0) {
                Bundle sync = new Bundle();
                sync.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                sync.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(Aware.getAWAREAccount(context), provider, sync);
            }
        }
    }

    private void downloadCertificate(Context context) {
        new SSLDownloadTask().execute(context);
    }

    class SSLDownloadTask extends AsyncTask<Context, Void, Void>
    {
        @Override
        protected Void doInBackground(Context... params) {
            SSLManager.handleUrl(getApplicationContext(), Aware.getSetting(params[0], Aware_Preferences.WEBSERVICE_SERVER), true);
            return null;
        }
    }

    private static ContextBroadcaster contextBroadcaster = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
