
package com.aware.utils;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.aware.ui.PermissionsHandler;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Aware_Sensor: Extend to integrate with the framework (extension of Android Service class).
 *
 * @author dferreira
 */
public class Aware_Sensor extends Service {

    /**
     * Debug tag for this sensor
     */
    public static String TAG = "AWARE Sensor";

    /**
     * Debug flag for this sensor
     */
    public static boolean DEBUG = false;

    public ContextProducer CONTEXT_PRODUCER = null;

    /**
     * Sensor database tables
     */
    public String[] DATABASE_TABLES = null;

    /**
     * Sensor table fields
     */
    public String[] TABLES_FIELDS = null;

    /**
     * Context Providers URIs
     */
    public Uri[] CONTEXT_URIS = null;

    /**
     * Permissions needed for this plugin to run
     */
    public ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();

    /**
     * Indicates if permissions were accepted OK
     */
    public boolean PERMISSIONS_OK;

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
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_STOP_SENSORS);
        registerReceiver(contextBroadcaster, filter);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        Log.d(Aware.TAG, "created: " + getClass().getName() + " package: " + getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PERMISSIONS_OK = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED) {
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
            if (Aware.getSetting(this, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                SSLManager.handleUrl(getApplicationContext(), Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER), true);
            }
            Aware.debug(this, "active: " + getClass().getName() + " package: " + getPackageName());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (PERMISSIONS_OK) {
            Aware.debug(this, "destroyed: " + getClass().getName() + " package: " + getPackageName());
        }

        //Unregister Context Broadcaster
        if (contextBroadcaster != null) {
            unregisterReceiver(contextBroadcaster);
        }
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
    public class ContextBroadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Aware.ACTION_AWARE_CURRENT_CONTEXT)) {
                if (CONTEXT_PRODUCER != null) {
                    CONTEXT_PRODUCER.onContext();
                }
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                if (DATABASE_TABLES != null && TABLES_FIELDS != null && CONTEXT_URIS != null) {
                    for (int i = 0; i < DATABASE_TABLES.length; i++) {
                        Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                        webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                        webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                        webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[i]);
                        webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[i].toString());
                        context.startService(webserviceHelper);
                    }
                }
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_CLEAR_DATA)) {
                if (DATABASE_TABLES != null && CONTEXT_URIS != null) {
                    for (int i = 0; i < DATABASE_TABLES.length; i++) {
                        //Clear locally
                        context.getContentResolver().delete(CONTEXT_URIS[i], null, null);
                        if (Aware.DEBUG) Log.d(TAG, "Cleared " + CONTEXT_URIS[i].toString());

                        //Clear remotely
                        if (Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                            Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                            webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE);
                            webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                            context.startService(webserviceHelper);
                        }
                    }
                }
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_STOP_SENSORS)) {
                if (Aware.DEBUG) Log.d(TAG, TAG + " stopped");
                stopSelf();
            }

        }
    }

    private ContextBroadcaster contextBroadcaster = new ContextBroadcaster();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
