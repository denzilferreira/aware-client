
package com.aware.utils;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
 * Aware_Plugin: Extend to integrate with the framework (extension of Android Service class).
 *
 * @author denzil
 */
public class Aware_Plugin extends Service {

    /**
     * Debug tag for this plugin
     */
    public String TAG = "AWARE Plugin";

    /**
     * Debug flag for this plugin
     */
    public boolean DEBUG = false;

    /**
     * Context producer for this plugin
     */
    public ContextProducer CONTEXT_PRODUCER = null;

    /**
     * Context ContentProvider tables
     */
    public String[] DATABASE_TABLES = null;

    /**
     * Context ContentProvider fields
     */
    public String[] TABLES_FIELDS = null;

    /**
     * Context ContentProvider Uris
     */
    public Uri[] CONTEXT_URIS = null;

    /**
     * Permissions needed for this plugin to run
     */
    public ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();

    /**
     * Plugin is inactive
     */
    public static final int STATUS_PLUGIN_OFF = 0;

    /**
     * Plugin is active
     */
    public static final int STATUS_PLUGIN_ON = 1;

    /**
     * Indicates if permissions were accepted OK
     */
    public boolean PERMISSIONS_OK = true;

    @Override
    public void onCreate() {
        super.onCreate();

        //Register Context Broadcaster
        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CURRENT_CONTEXT);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_STOP_PLUGINS);
        registerReceiver(contextBroadcaster, filter);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

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
            permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_SERVICE, getApplicationContext().getPackageName() + "/" + getClass().getName()); //restarts plugin once permissions are accepted
            startActivity(permissions);
        } else {

            PERMISSIONS_OK = true;

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

        if (contextBroadcaster != null) unregisterReceiver(contextBroadcaster);
    }

    /**
     * Interface to share context with other applications/plugins<br/>
     * You are encouraged to broadcast your contexts here for reusability in other plugins and apps!
     *
     * @author denzil
     */
    public interface ContextProducer {
        void onContext();
    }

    /**
     * AWARE Context Broadcaster<br/>
     * - ACTION_AWARE_CURRENT_CONTEXT: returns current plugin's context
     * - ACTION_AWARE_WEBSERVICE: push content provider data remotely
     * - ACTION_AWARE_CLEAN_DATABASES: clears local and remote database
     * - ACTION_AWARE_STOP_SENSORS: stops this plugin
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
            if (intent.getAction().equals(Aware.ACTION_AWARE_STOP_PLUGINS)) {
                if (Aware.DEBUG) Log.d(TAG, TAG + " stopped");
                try {
                    Intent self = new Intent(context, Class.forName(context.getApplicationContext().getClass().getName()));
                    context.stopService(self);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private ContextBroadcaster contextBroadcaster = new ContextBroadcaster();

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
