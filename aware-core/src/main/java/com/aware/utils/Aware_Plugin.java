
package com.aware.utils;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
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
    public static String TAG = "AWARE Plugin";

    /**
     * Debug flag for this plugin
     */
    public static boolean DEBUG = false;

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

    private Intent aware;

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG).length() > 0 ? Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG) : TAG;
        DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");

        //Register Context Broadcaster
        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CURRENT_CONTEXT);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_STOP_PLUGINS);
        filter.addAction(Aware.ACTION_AWARE_SPACE_MAINTENANCE);
        registerReceiver(contextBroadcaster, filter);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (!getResources().getBoolean(R.bool.standalone)) {
            aware = new Intent(getApplicationContext(), Aware.class);
            startService(aware);
        }

        if (Aware.getSetting(this, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
            Intent study_SSL = new Intent(this, SSLManager.class);
            study_SSL.putExtra(SSLManager.EXTRA_SERVER, Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER));
            startService(study_SSL);
        }

        Aware.debug(this, "created: " + getClass().getName() + " package: " + getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Aware.debug(this, "active: " + getClass().getName() + " package: " + getPackageName());
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Aware.debug(this, "destroyed: " + getClass().getName() + " package: " + getPackageName());

        //Unregister Context Broadcaster
        if (contextBroadcaster != null) {
            unregisterReceiver(contextBroadcaster);
        }
        if (aware != null) stopService(aware);
    }

    /**
     * Interface to share context with other applications/plugins<br/>
     * You MUST broadcast your contexts here!
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
            if (intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
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
                stopSelf();
            }

            if (intent.getAction().equals(Aware.ACTION_AWARE_SPACE_MAINTENANCE) && Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());

                switch (Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA))) {
                    case 1: //weekly
                        if (DATABASE_TABLES != null && CONTEXT_URIS != null) {
                            cal.add(Calendar.DAY_OF_YEAR, -7);
                            if (Aware.DEBUG)
                                Log.d(TAG, TAG + " cleaning locally any data older than last week (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH));
                            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                                //Clear locally
                                String where = "timestamp < " + cal.getTimeInMillis();
                                int rowsDeleted = context.getContentResolver().delete(CONTEXT_URIS[i], where, null);
                                if (Aware.DEBUG)
                                    Log.d(TAG, "Cleaned " + rowsDeleted + " from " + CONTEXT_URIS[i].toString());
                            }
                        }
                        break;
                    case 2: //monthly
                        if (DATABASE_TABLES != null && CONTEXT_URIS != null) {
                            cal.add(Calendar.MONTH, -1);
                            if (Aware.DEBUG)
                                Log.d(TAG, TAG + " cleaning locally any data older than last month (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH));
                            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                                //Clear locally
                                String where = "timestamp < " + cal.getTimeInMillis();
                                int rowsDeleted = context.getContentResolver().delete(CONTEXT_URIS[i], where, null);
                                if (Aware.DEBUG)
                                    Log.d(TAG, "Cleaned " + rowsDeleted + " from " + CONTEXT_URIS[i].toString());
                            }
                        }
                        break;
                    case 3: //daily
                        if (DATABASE_TABLES != null && CONTEXT_URIS != null) {
                            cal.add(Calendar.DAY_OF_YEAR, -1);
                            if (Aware.DEBUG)
                                Log.d(TAG, TAG + " cleaning locally any data older than today (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH));
                            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                                //Clear locally
                                String where = "timestamp < " + cal.getTimeInMillis();
                                int rowsDeleted = context.getContentResolver().delete(CONTEXT_URIS[i], where, null);
                                if (Aware.DEBUG)
                                    Log.d(TAG, "Cleaned " + rowsDeleted + " from " + CONTEXT_URIS[i].toString());
                            }
                        }
                        break;
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
