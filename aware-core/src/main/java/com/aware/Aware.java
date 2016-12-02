
package com.aware;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Device;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.providers.Aware_Provider.Aware_Settings;
import com.aware.providers.Scheduler_Provider;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.DownloadPluginService;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.PluginsManager;
import com.aware.utils.SSLManager;
import com.aware.utils.Scheduler;
import com.aware.utils.StudyUtils;
import com.aware.utils.WebserviceHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dalvik.system.DexFile;

/**
 * Main AWARE framework service. awareContext will start and manage all the services and settings.
 *
 * @author denzil
 */
public class Aware extends Service {
    /**
     * Debug flag (default = false).
     */
    public static boolean DEBUG = false;

    /**
     * Debug tag (default = "AWARE").
     */
    public static String TAG = "AWARE";

    /**
     * Broadcasted event: awareContext device information is available
     */
    public static final String ACTION_AWARE_DEVICE_INFORMATION = "ACTION_AWARE_DEVICE_INFORMATION";

    /**
     * Received broadcast on all modules
     * - Sends the data to the defined webserver
     */
    public static final String ACTION_AWARE_SYNC_DATA = "ACTION_AWARE_SYNC_DATA";

    /**
     * Received broadcast on all modules
     * - Cleans the data collected on the device
     */
    public static final String ACTION_AWARE_CLEAR_DATA = "ACTION_AWARE_CLEAR_DATA";

    /**
     * Received broadcast: refresh the framework active sensors.
     */
    public static final String ACTION_AWARE_REFRESH = "ACTION_AWARE_REFRESH";

    /**
     * Received broadcast: this broadcast will trigger plugins that implement the CONTEXT_PRODUCER callback.
     */
    public static final String ACTION_AWARE_CURRENT_CONTEXT = "ACTION_AWARE_CURRENT_CONTEXT";

    /**
     * Stop all plugins
     */
    public static final String ACTION_AWARE_STOP_PLUGINS = "ACTION_AWARE_STOP_PLUGINS";

    /**
     * Stop all sensors
     */
    public static final String ACTION_AWARE_STOP_SENSORS = "ACTION_AWARE_STOP_SENSORS";

    /**
     * Used to check users' compliance in a study
     */
    public static final String ACTION_AWARE_PLUGIN_INSTALLED = "ACTION_AWARE_PLUGIN_INSTALLED";
    public static final String ACTION_AWARE_PLUGIN_UNINSTALLED = "ACTION_AWARE_PLUGIN_UNINSTALLED";
    public static final String EXTRA_PLUGIN = "extra_plugin";

    /**
     * Received broadcast on all modules
     * - Cleans old data from the content providers
     */
    public static final String ACTION_AWARE_SPACE_MAINTENANCE = "ACTION_AWARE_SPACE_MAINTENANCE";

    /**
     * Used by Plugin Manager to refresh UI
     */
    public static final String ACTION_AWARE_UPDATE_PLUGINS_INFO = "ACTION_AWARE_UPDATE_PLUGINS_INFO";

    /**
     * Used when quitting a study. This will reset the device to default settings.
     */
    public static final String ACTION_QUIT_STUDY = "ACTION_QUIT_STUDY";

    /**
     * Used on the scheduler class to define global schedules for AWARE, SYNC and SPACE MAINTENANCE actions
     */
    public static final String SCHEDULE_SPACE_MAINTENANCE = "schedule_aware_space_maintenance";
    public static final String SCHEDULE_SYNC_DATA = "schedule_aware_sync_data";

    private static AlarmManager alarmManager = null;
    private static PendingIntent repeatingIntent = null;
    private static Context awareContext = null;

    private static Intent awareStatusMonitor = null;
    private static Intent applicationsSrv = null;
    private static Intent accelerometerSrv = null;
    private static Intent locationsSrv = null;
    private static Intent bluetoothSrv = null;
    private static Intent screenSrv = null;
    private static Intent batterySrv = null;
    private static Intent networkSrv = null;
    private static Intent trafficSrv = null;
    private static Intent communicationSrv = null;
    private static Intent processorSrv = null;
    private static Intent mqttSrv = null;
    private static Intent gyroSrv = null;
    private static Intent wifiSrv = null;
    private static Intent telephonySrv = null;
    private static Intent timeZoneSrv = null;
    private static Intent rotationSrv = null;
    private static Intent lightSrv = null;
    private static Intent proximitySrv = null;
    private static Intent magnetoSrv = null;
    private static Intent barometerSrv = null;
    private static Intent gravitySrv = null;
    private static Intent linear_accelSrv = null;
    private static Intent temperatureSrv = null;
    private static Intent esmSrv = null;
    private static Intent installationsSrv = null;
    private static Intent keyboard = null;
    private static Intent scheduler = null;

    private final static String PREF_FREQUENCY_WATCHDOG = "frequency_watchdog";
    private final static String PREF_LAST_UPDATE = "last_update";
    private final static int CONST_FREQUENCY_WATCHDOG = 5 * 60; //5 minutes check

    private static SharedPreferences aware_preferences;

    /**
     * Singleton instance of the framework
     */
    private static Aware awareSrv = Aware.getService();

    /**
     * Get the singleton instance to the AWARE framework
     *
     * @return {@link Aware} obj
     */
    public static Aware getService() {
        if (awareSrv == null) awareSrv = new Aware();
        return awareSrv;

    }

    /**
     * Activity-Service binder
     */
    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        Aware getService() {
            return Aware.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        awareContext = getApplicationContext();
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        IntentFilter storage = new IntentFilter();
        storage.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storage.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storage.addDataScheme("file");
        awareContext.registerReceiver(storage_BR, storage);

        IntentFilter aware_actions = new IntentFilter();
        aware_actions.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        aware_actions.addAction(Aware.ACTION_AWARE_REFRESH);
        aware_actions.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        aware_actions.addAction(Aware.ACTION_QUIT_STUDY);
        awareContext.registerReceiver(aware_BR, aware_actions);

        IntentFilter boot = new IntentFilter();
        boot.addAction(Intent.ACTION_BOOT_COMPLETED);
        awareContext.registerReceiver(awareBoot, boot);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            stopSelf();
            return;
        }

        //If Android M+ and client or standalone, ask to be added to the whilelist of Doze
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (getPackageName().equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone))) {
            Intent intent = new Intent();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName()))
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        if (Aware.DEBUG) Log.d(TAG, "AWARE framework is created!");
    }

    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // Download the certificate, and block since we are already running in background
            // and we need the certificate immediately.
            SSLManager.downloadCertificate(awareContext, "api.awareframework.com", true);

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
            device_ping.put("platform", "android");
            try {
                PackageInfo package_info = awareContext.getPackageManager().getPackageInfo(awareContext.getPackageName(), 0);
                device_ping.put("package_name", package_info.packageName);
                if (package_info.packageName.equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone)) {
                    device_ping.put("package_version_code", String.valueOf(package_info.versionCode));
                    device_ping.put("package_version_name", String.valueOf(package_info.versionName));
                }
            } catch (PackageManager.NameNotFoundException e) {
            }

            try {
                new Https(awareContext, SSLManager.getHTTPS(getApplicationContext(), "https://api.awareframework.com/index.php")).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private class AsyncStudyCheck extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> studyCheck = new Hashtable<>();
            studyCheck.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            studyCheck.put("study_check", "1");

            try {
                String study_status = new Https(awareContext, SSLManager.getHTTPS(getApplicationContext(), Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER)))
                        .dataPOST(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER), studyCheck, true);

                if (study_status == null)
                    return true; //unable to connect to server, timeout, etc. We do nothing.

                if (DEBUG)
                    Log.d(Aware.TAG, "Study_status: \n" + study_status);

                try {
                    JSONArray status = new JSONArray(study_status);
                    JSONObject study = status.getJSONObject(0);

                    if (!study.getBoolean("status")) {
                        return false; //study no longer active, make clients quit the study and reset.
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean studyStatus) {
            super.onPostExecute(studyStatus);

            if (!studyStatus) {
                sendBroadcast(new Intent(Aware.ACTION_QUIT_STUDY));
            }
        }
    }

    private void get_device_info() {
        Cursor awareContextDevice = awareContext.getContentResolver().query(Aware_Device.CONTENT_URI, null, null, null, null);
        if (awareContextDevice == null || !awareContextDevice.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Device.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Aware_Device.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            rowData.put(Aware_Device.BOARD, Build.BOARD);
            rowData.put(Aware_Device.BRAND, Build.BRAND);
            rowData.put(Aware_Device.DEVICE, Build.DEVICE);
            rowData.put(Aware_Device.BUILD_ID, Build.DISPLAY);
            rowData.put(Aware_Device.HARDWARE, Build.HARDWARE);
            rowData.put(Aware_Device.MANUFACTURER, Build.MANUFACTURER);
            rowData.put(Aware_Device.MODEL, Build.MODEL);
            rowData.put(Aware_Device.PRODUCT, Build.PRODUCT);
            rowData.put(Aware_Device.SERIAL, Build.SERIAL);
            rowData.put(Aware_Device.RELEASE, Build.VERSION.RELEASE);
            rowData.put(Aware_Device.RELEASE_TYPE, Build.TYPE);
            rowData.put(Aware_Device.SDK, String.valueOf(Build.VERSION.SDK_INT));
            rowData.put(Aware_Device.LABEL, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_LABEL));

            try {
                awareContext.getContentResolver().insert(Aware_Device.CONTENT_URI, rowData);

                Intent deviceData = new Intent(ACTION_AWARE_DEVICE_INFORMATION);
                sendBroadcast(deviceData);

                if (Aware.DEBUG) Log.d(TAG, "Device information:" + rowData.toString());

            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }
        }
        if (awareContextDevice != null && !awareContextDevice.isClosed())
            awareContextDevice.close();
    }

    /**
     * Identifies if the device is a watch or a phone.
     *
     * @param c
     * @return boolean
     */
    public static boolean is_watch(Context c) {
        UiModeManager uiManager = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        if (uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH) {
            return true;
        }
        return false;
    }

    /**
     * Identifies if the devices is enrolled in a study
     *
     * @param c
     * @return
     */
    public static boolean isStudy(Context c) {
        boolean participant = false;

        Cursor study = c.getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null,
                Aware_Provider.Aware_Studies.STUDY_URL + " LIKE '" + Aware.getSetting(c, Aware_Preferences.WEBSERVICE_SERVER) +
                        "' AND " + Aware_Provider.Aware_Studies.STUDY_JOINED + ">0" +
                        " AND " + Aware_Provider.Aware_Studies.STUDY_EXIT + "=0",
                null, null);

        if (study != null && study.moveToFirst())
            participant = true;

        if (study != null && !study.isClosed()) study.close();
        return participant;
    }

    /**
     * Identifies if the user can modify the sensors while on a study
     * TODO
     *
     * @param c
     * @return
     */
    public static boolean isLocked(Context c) {

        return false;
    }

    public static void debug(Context c, String message) {
        //Only collect this log if in a study
        if (!Aware.isStudy(c)) return;

        ContentValues log = new ContentValues();
        log.put(Aware_Provider.Aware_Log.LOG_TIMESTAMP, System.currentTimeMillis());
        log.put(Aware_Provider.Aware_Log.LOG_DEVICE_ID, Aware.getSetting(c, Aware_Preferences.DEVICE_ID));
        log.put(Aware_Provider.Aware_Log.LOG_MESSAGE, message);

        if (Aware.DEBUG) Log.d(TAG, "Aware_Log: \n" + log.toString());

        c.getContentResolver().insert(Aware_Provider.Aware_Log.CONTENT_URI, log);
    }

    /**
     * Fetch the cursor for a study, given the study URL
     *
     * @param c
     * @param study_url
     * @return
     */
    public static Cursor getStudy(Context c, String study_url) {
        return c.getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null, Aware_Provider.Aware_Studies.STUDY_URL + " LIKE '" + study_url + "'", null, Aware_Provider.Aware_Studies.STUDY_TIMESTAMP + " DESC LIMIT 1");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

            if (Aware.DEBUG) Log.d(TAG, "AWARE framework is active...");

            aware_preferences = getSharedPreferences("aware_core_prefs", MODE_PRIVATE);
            if (aware_preferences.getAll().isEmpty()) {
                SharedPreferences.Editor editor = aware_preferences.edit();
                editor.putInt(PREF_FREQUENCY_WATCHDOG, CONST_FREQUENCY_WATCHDOG);
                editor.putLong(PREF_LAST_UPDATE, 0);
                editor.commit();
            }

            //this sets the default settings to all plugins too
            SharedPreferences prefs = getSharedPreferences("com.aware.phone", Context.MODE_PRIVATE);
            if (prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                PreferenceManager.setDefaultValues(getApplicationContext(), "com.aware.phone", Context.MODE_PRIVATE, R.xml.aware_preferences, true);
                prefs.edit().commit(); //commit changes
            } else {
                PreferenceManager.setDefaultValues(getApplicationContext(), "com.aware.phone", Context.MODE_PRIVATE, R.xml.aware_preferences, false);
            }

            //this sets the default settings to all plugins too
            Map<String, ?> defaults = prefs.getAll();
            for (Map.Entry<String, ?> entry : defaults.entrySet()) {
                if (Aware.getSetting(getApplicationContext(), entry.getKey(), "com.aware.phone").length() == 0) {
                    Aware.setSetting(getApplicationContext(), entry.getKey(), entry.getValue(), "com.aware.phone"); //default AWARE settings
                }
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                UUID uuid = UUID.randomUUID();
                Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, uuid.toString(), "com.aware.phone");
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER, "https://api.awareframework.com/index.php");
            }

            DEBUG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_FLAG).equals("true");
            TAG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_TAG).length() > 0 ? Aware.getSetting(awareContext, Aware_Preferences.DEBUG_TAG) : TAG;

            get_device_info();

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.AWARE_DONATE_USAGE).equals("true")) {
                new AsyncPing().execute();
            }

            if (awareStatusMonitor == null) {
                awareStatusMonitor = new Intent(this, Aware.class);
                repeatingIntent = PendingIntent.getService(getApplicationContext(), 0, awareStatusMonitor, PendingIntent.FLAG_UPDATE_CURRENT);
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, aware_preferences.getInt(PREF_FREQUENCY_WATCHDOG, 300) * 1000, repeatingIntent);
            }

            //Boot AWARE services
            startAWARE();

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {

                int frequency_webservice = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE));
                if (frequency_webservice == 0) {
                    if (DEBUG)
                        Log.d(TAG, "Data sync is disabled.");

                    Scheduler.removeSchedule(getApplicationContext(), SCHEDULE_SYNC_DATA);

                } else {
                    Scheduler.Schedule sync = Scheduler.getSchedule(this, SCHEDULE_SYNC_DATA);
                    if (sync == null) { //Set the sync schedule for the first time
                        try {
                            Scheduler.Schedule schedule = new Scheduler.Schedule(SCHEDULE_SYNC_DATA)
                                    .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                                    .setActionClass(Aware.ACTION_AWARE_SYNC_DATA)
                                    .setInterval(frequency_webservice);

                            Scheduler.saveSchedule(getApplicationContext(), schedule);

                            if (DEBUG) {
                                Log.d(TAG, "Data sync every " + schedule.getInterval() + " minute(s)");
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else { //check the sync schedule for changes
                        try {
                            long interval = sync.getInterval();
                            if (interval != frequency_webservice) {
                                Scheduler.Schedule schedule = new Scheduler.Schedule(SCHEDULE_SYNC_DATA)
                                        .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                                        .setActionClass(Aware.ACTION_AWARE_SYNC_DATA)
                                        .setInterval(frequency_webservice);

                                Scheduler.saveSchedule(getApplicationContext(), schedule);

                                if (DEBUG) {
                                    Log.d(TAG, "Data sync at " + schedule.getInterval() + " minute(s)");
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0) {
                String[] frequency = new String[]{"never", "weekly", "monthly", "daily", "always"};
                int frequency_space_maintenance = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA));

                if (DEBUG && frequency_space_maintenance != 0)
                    Log.d(TAG, "Space maintenance is: " + frequency[frequency_space_maintenance]);

                try {
                    if (frequency_space_maintenance == 0 || frequency_space_maintenance == 4) { //if always, we clear old data as soon as we upload to server
                        Scheduler.removeSchedule(getApplicationContext(), SCHEDULE_SPACE_MAINTENANCE);
                    } else {
                        Scheduler.Schedule cleanup = new Scheduler.Schedule(SCHEDULE_SPACE_MAINTENANCE);
                        switch (frequency_space_maintenance) {
                            case 1: //weekly, by default every Sunday
                                cleanup.addWeekday("Sunday")
                                        .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                                        .setActionClass(Aware.ACTION_AWARE_SPACE_MAINTENANCE);
                                break;
                            case 2: //monthly
                                cleanup.addMonth("January")
                                        .addMonth("February")
                                        .addMonth("March")
                                        .addMonth("April")
                                        .addMonth("May")
                                        .addMonth("June")
                                        .addMonth("July")
                                        .addMonth("August")
                                        .addMonth("September")
                                        .addMonth("October")
                                        .addMonth("November")
                                        .addMonth("December")
                                        .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                                        .setActionClass(Aware.ACTION_AWARE_SPACE_MAINTENANCE);
                                break;
                            case 3: //daily
                                cleanup.addWeekday("Monday")
                                        .addWeekday("Tuesday")
                                        .addWeekday("Wednesday")
                                        .addWeekday("Thursday")
                                        .addWeekday("Friday")
                                        .addWeekday("Saturday")
                                        .addWeekday("Sunday")
                                        .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                                        .setActionClass(Aware.ACTION_AWARE_SPACE_MAINTENANCE);
                                break;
                        }
                        Scheduler.saveSchedule(getApplicationContext(), cleanup);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //Get the active plugins
            ArrayList<String> active_plugins = new ArrayList<>();
            Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
                do {
                    String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    active_plugins.add(package_name);
                } while (enabled_plugins.moveToNext());
            }
            if (enabled_plugins != null && !enabled_plugins.isClosed()) enabled_plugins.close();

            if (active_plugins.size() > 0) {
                for (String package_name : active_plugins) {
                    startPlugin(getApplicationContext(), package_name);
                }
            }

            //Client checks
            if (isStudy(this)) {
                //Check if study is ongoing or any changes to the configuration
                new AsyncStudyCheck().execute();
            }
        } else {
            stopAWARE();

            ArrayList<String> active_plugins = new ArrayList<>();
            Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
                do {
                    String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    active_plugins.add(package_name);
                } while (enabled_plugins.moveToNext());
            }
            if (enabled_plugins != null && !enabled_plugins.isClosed()) enabled_plugins.close();

            if (active_plugins.size() > 0) {
                for (String package_name : active_plugins) {
                    stopPlugin(getApplicationContext(), package_name);
                }
                if (Aware.DEBUG) Log.w(TAG, "AWARE plugins disabled...");
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Stops a plugin. Expects the package name of the plugin.
     *
     * @param context
     * @param package_name
     */
    public synchronized static void stopPlugin(final Context context, final String package_name) {
        if (awareContext == null) awareContext = context;

        if (Aware.DEBUG) Log.d(TAG, "Stopping " + package_name);

        //Check if plugin is bundled within an application/plugin
        Intent bundled = new Intent();
        bundled.setComponent(new ComponentName(awareContext.getPackageName(), package_name + ".Plugin"));
        boolean result = awareContext.stopService(bundled);

        if (result) {
            if (Aware.DEBUG)
                Log.d(TAG, "Bundled " + package_name + " stopped.");
        }

        boolean is_installed = false;
        Cursor cached = awareContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if (cached != null && cached.moveToFirst()) {
            is_installed = true;
        }
        if (cached != null && !cached.isClosed()) cached.close();

        if (is_installed) {
            Intent plugin = new Intent();
            plugin.setComponent(new ComponentName(package_name, package_name + ".Plugin"));
            awareContext.stopService(plugin);

            if (Aware.DEBUG)
                Log.d(TAG, package_name + " stopped.");
        }

        ContentValues rowData = new ContentValues();
        rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
        int updated = awareContext.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);

        if (Aware.DEBUG)
            Log.d(TAG, "Plugin " + package_name + " stopped: " + updated);

        if (context.getPackageName().equals("com.aware.phone")) {
            context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
        }
    }

    /**
     * Starts a plugin. Expects the package name of the plugin.
     * It checks if the plugin does exist on the phone. If it doesn't, it will request the user to install it automatically.
     *
     * @param context
     * @param package_name
     */
    public synchronized static void startPlugin(final Context context, final String package_name) {
        if (awareContext == null) awareContext = context;

        if (Aware.DEBUG) Log.d(TAG, "Starting " + package_name);

        //Check if plugin is bundled within an application/plugin
        Intent bundled = new Intent();
        bundled.setComponent(new ComponentName(awareContext.getPackageName(), package_name + ".Plugin"));
        ComponentName bundledResult = awareContext.startService(bundled);
        if (bundledResult != null) {
            if (Aware.DEBUG) Log.d(TAG, "Bundled " + package_name + ".Plugin started...");

            //Check if plugin is cached
            Cursor cached = awareContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
            if (cached == null || !cached.moveToFirst()) {
                //Fixed: add a bundled plugin to the list of installed plugins on the self-contained apps
                ContentValues rowData = new ContentValues();
                rowData.put(Aware_Plugins.PLUGIN_AUTHOR, "Self-packaged");
                rowData.put(Aware_Plugins.PLUGIN_DESCRIPTION, "Bundled with " + context.getPackageName());
                rowData.put(Aware_Plugins.PLUGIN_NAME, "Self-packaged");
                rowData.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, package_name);
                rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                rowData.put(Aware_Plugins.PLUGIN_VERSION, 1);
                awareContext.getContentResolver().insert(Aware_Plugins.CONTENT_URI, rowData);
                if (Aware.DEBUG)
                    Log.d(TAG, "Added self-package " + package_name + " to " + awareContext.getPackageName());
            }
            if (cached != null && !cached.isClosed()) cached.close();
        }

        //set the plugin as active
        Cursor cached = awareContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if (cached != null && cached.moveToFirst()) {
            //Installed on the phone
            if (isClassAvailable(context, package_name, "Plugin")) {
                Intent plugin = new Intent();
                plugin.setComponent(new ComponentName(package_name, package_name + ".Plugin"));
                ComponentName cachedResult = awareContext.startService(plugin);
                if (cachedResult != null) {
                    if (Aware.DEBUG)
                        Log.d(TAG, package_name + " started...");
                }
            }
        }
        if (cached != null && !cached.isClosed()) cached.close();

        ContentValues rowData = new ContentValues();
        rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
        int updated = awareContext.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);

        if (Aware.DEBUG)
            Log.d(TAG, "Plugin " + package_name + " started: " + updated);

        if (context.getPackageName().equals("com.aware.phone")) {
            context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
        }
    }

    /**
     * Requests the download of a plugin given the package name from AWARE webservices or the Play Store otherwise
     *
     * @param context
     * @param package_name
     * @param is_update
     */
    public static void downloadPlugin(Context context, String package_name, boolean is_update) {
        Intent pluginIntent = new Intent(context, DownloadPluginService.class);
        pluginIntent.putExtra("package_name", package_name);
        pluginIntent.putExtra("is_update", is_update);
        context.startService(pluginIntent);
    }

    /**
     * Given a plugin's package name, fetch the context card for reuse.
     *
     * @param context:      application context
     * @param package_name: plugin's package name
     * @return View for reuse (instance of LinearLayout)
     */
    public static View getContextCard(final Context context, final String package_name) {

        if (!isClassAvailable(context, package_name, "ContextCard")) {
            Log.d(Aware.TAG, "No ContextCard: " + package_name);
            return null;
        }

        String ui_class = package_name + ".ContextCard";
        try {
            Context packageContext = context.createPackageContext(package_name, Context.CONTEXT_INCLUDE_CODE + Context.CONTEXT_IGNORE_SECURITY);
            Class<?> fragment_loader = packageContext.getClassLoader().loadClass(ui_class);
            Object fragment = fragment_loader.newInstance();
            Method[] allMethods = fragment_loader.getDeclaredMethods();
            Method m = null;
            for (Method mItem : allMethods) {
                String mName = mItem.getName();
                if (mName.contains("getContextCard")) {
                    mItem.setAccessible(true);
                    m = mItem;
                    break;
                }
            }

            View ui = (View) m.invoke(fragment, packageContext);
            if (ui != null) {
                ui.setBackgroundColor(Color.WHITE);
                ui.setPadding(0, 0, 0, 10);

                LinearLayout card = new LinearLayout(context);
                LinearLayout.LayoutParams card_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                card.setLayoutParams(card_params);
                card.setOrientation(LinearLayout.VERTICAL);

                LinearLayout info = new LinearLayout(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                info.setLayoutParams(params);
                info.setOrientation(LinearLayout.HORIZONTAL);
                info.setBackgroundColor(Color.parseColor("#33B5E5"));

                TextView plugin_header = new TextView(context);
                plugin_header.setText(PluginsManager.getPluginName(context, package_name));
                plugin_header.setTextColor(Color.WHITE);
                plugin_header.setPadding(10, 0, 0, 0);
                params.gravity = android.view.Gravity.CENTER_VERTICAL;
                plugin_header.setLayoutParams(params);
                info.addView(plugin_header);

                //Check if plugin has settings. Add button if it does.
                if (isClassAvailable(context, package_name, "Settings")) {
                    ImageView infoSettings = new ImageView(context);
                    infoSettings.setBackgroundResource(R.drawable.ic_action_plugin_settings);
                    infoSettings.setAdjustViewBounds(true);
                    infoSettings.setMaxWidth(10);
                    infoSettings.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent open_settings = new Intent();
                            open_settings.setComponent(new ComponentName(package_name, package_name + ".Settings"));
                            open_settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(open_settings);
                        }
                    });
                    info.addView(infoSettings);

                    //Add settings shortcut to card
                    card.addView(info);
                }

                //Add inflated UI to card
                card.addView(ui);

                return card;
            } else {
                return null;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Given a package and class name, check if the class exists or not.
     *
     * @param package_name
     * @param class_name
     * @return true if exists, false otherwise
     */
    public static boolean isClassAvailable(Context context, String package_name, String class_name) {
        try {
            Context package_context = context.createPackageContext(package_name, Context.CONTEXT_IGNORE_SECURITY + Context.CONTEXT_INCLUDE_CODE);
            DexFile df = new DexFile(package_context.getPackageCodePath());
            for (Enumeration<String> iter = df.entries(); iter.hasMoreElements(); ) {
                String className = iter.nextElement();
                if (className.contains(class_name)) return true;
            }
            return false;
        } catch (IOException | NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Retrieve setting value given key.
     *
     * @param key
     * @return value
     */
    public static String getSetting(Context context, String key) {

        boolean is_global;

        ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware_Preferences.DEBUG_FLAG);
        global_settings.add(Aware_Preferences.DEBUG_TAG);
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.DEVICE_LABEL);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Aware_Preferences.WEBSERVICE_SIMPLE);
        global_settings.add(Aware_Preferences.WEBSERVICE_REMOVE_DATA);
        global_settings.add(Aware_Preferences.WEBSERVICE_SILENT);
        global_settings.add(Aware_Preferences.STATUS_APPLICATIONS);
        global_settings.add(Applications.STATUS_AWARE_ACCESSIBILITY);

        if (context.getResources().getBoolean(R.bool.standalone)) {
            global_settings.add(Aware_Preferences.STATUS_MQTT);
            global_settings.add(Aware_Preferences.MQTT_USERNAME);
            global_settings.add(Aware_Preferences.MQTT_PASSWORD);
            global_settings.add(Aware_Preferences.MQTT_SERVER);
            global_settings.add(Aware_Preferences.MQTT_PORT);
            global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
            global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
            global_settings.add(Aware_Preferences.MQTT_QOS);
        }

        is_global = global_settings.contains(key);

        if (context.getResources().getBoolean(R.bool.standalone))
            is_global = false;

        String value = "";
        if (key.equals(Aware_Preferences.DEVICE_ID)) { //we will query the database from the library
            Cursor device_info = context.getContentResolver().query(Uri.parse("content://" + context.getPackageName() + ".provider.aware/aware_device"), null, null, null, Aware_Device.TIMESTAMP + " DESC LIMIT 1");
            if (device_info != null && device_info.moveToFirst()) {
                value = device_info.getString(device_info.getColumnIndex(Aware_Device.DEVICE_ID));
            }
            if (device_info != null && ! device_info.isClosed()) device_info.close();

            if (value.length() > 0)
                return value;
        }

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null,
                Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + ((is_global) ? "'com.aware.phone'" : "'" + context.getPackageName() + "'"), null, null);
        if (qry != null && qry.moveToFirst()) {
            value = qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE));
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return value;
    }

    /**
     * Retrieve setting value given a key of a plugin's settings
     *
     * @param context
     * @param key
     * @param package_name
     * @return value
     */
    public static String getSetting(Context context, String key, String package_name) {
        if (context.getResources().getBoolean(R.bool.standalone))
            package_name = context.getPackageName(); //use the package name from the context

        String value = "";
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if (qry != null && qry.moveToFirst()) {
            value = qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE));
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return value;
    }

    /**
     * Insert / Update settings of the framework
     *
     * @param key
     * @param value
     */
    public static void setSetting(Context context, String key, Object value) {
        boolean is_global;

        ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware_Preferences.DEBUG_FLAG);
        global_settings.add(Aware_Preferences.DEBUG_TAG);
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.DEVICE_LABEL);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Aware_Preferences.WEBSERVICE_SIMPLE);
        global_settings.add(Aware_Preferences.WEBSERVICE_REMOVE_DATA);
        global_settings.add(Aware_Preferences.WEBSERVICE_SILENT);
        global_settings.add(Aware_Preferences.STATUS_APPLICATIONS);
        global_settings.add(Applications.STATUS_AWARE_ACCESSIBILITY);

        //allow standalone apps to react to MQTT
        if (context.getResources().getBoolean(R.bool.standalone)) {
            global_settings.add(Aware_Preferences.STATUS_MQTT);
            global_settings.add(Aware_Preferences.MQTT_USERNAME);
            global_settings.add(Aware_Preferences.MQTT_PASSWORD);
            global_settings.add(Aware_Preferences.MQTT_SERVER);
            global_settings.add(Aware_Preferences.MQTT_PORT);
            global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
            global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
            global_settings.add(Aware_Preferences.MQTT_QOS);
        }

        is_global = global_settings.contains(key);

        if (context.getResources().getBoolean(R.bool.standalone))
            is_global = false;

        //We already have a Device ID, do nothing!
        if (key.equals(Aware_Preferences.DEVICE_ID) && Aware.getSetting(context, Aware_Preferences.DEVICE_ID).length() > 0)
            return;

        if (key.equals(Aware_Preferences.DEVICE_LABEL) && ((String) value).length() > 0) {
            ContentValues newLabel = new ContentValues();
            newLabel.put(Aware_Provider.Aware_Device.LABEL, (String) value);
            context.getContentResolver().update(Aware_Provider.Aware_Device.CONTENT_URI, newLabel, Aware_Provider.Aware_Device.DEVICE_ID + " LIKE '" + Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID) + "'", null);
        }

        ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        if (is_global) {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, "com.aware.phone");
        } else {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, context.getPackageName());
        }

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + ((is_global) ? "'com.aware.phone'" : "'" + context.getPackageName() + "'"), null, null);
        //update
        if (qry != null && qry.moveToFirst()) {
            try {
                if (!qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE)).equals(value.toString())) {
                    context.getContentResolver().update(Aware_Settings.CONTENT_URI, setting, Aware_Settings.SETTING_ID + "=" + qry.getInt(qry.getColumnIndex(Aware_Settings.SETTING_ID)), null);
                    if (Aware.DEBUG) Log.d(Aware.TAG, "Updated: " + key + "=" + value);
                }
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }
            //insert
        } else {
            try {
                context.getContentResolver().insert(Aware_Settings.CONTENT_URI, setting);
                if (Aware.DEBUG) Log.d(Aware.TAG, "Added: " + key + "=" + value);
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }
        }
        if (qry != null && !qry.isClosed()) qry.close();
    }

    /**
     * Insert / Update settings of a plugin
     *
     * @param key
     * @param value
     * @param package_name
     */
    public static void setSetting(Context context, String key, Object value, String package_name) {
        if (context.getResources().getBoolean(R.bool.standalone)) //use the package name from the context
            package_name = context.getPackageName();

        //We already have a device ID, bail-out!
        if (key.equals(Aware_Preferences.DEVICE_ID) && Aware.getSetting(context, Aware_Preferences.DEVICE_ID).length() > 0) {
            Log.d(Aware.TAG, "AWARE UUID: " + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + " in " + package_name);
            return;
        }

        if (key.equals(Aware_Preferences.DEVICE_LABEL) && ((String) value).length() > 0) {
            ContentValues newLabel = new ContentValues();
            newLabel.put(Aware_Provider.Aware_Device.LABEL, (String) value);
            context.getContentResolver().update(Aware_Provider.Aware_Device.CONTENT_URI, newLabel, Aware_Provider.Aware_Device.DEVICE_ID + " LIKE '" + Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID) + "'", null);
        }

        ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        setting.put(Aware_Settings.SETTING_PACKAGE_NAME, package_name);

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        //update
        if (qry != null && qry.moveToFirst()) {
            try {
                if (!qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE)).equals(value.toString())) {
                    context.getContentResolver().update(Aware_Settings.CONTENT_URI, setting, Aware_Settings.SETTING_ID + "=" + qry.getInt(qry.getColumnIndex(Aware_Settings.SETTING_ID)), null);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Updated: " + key + "=" + value + " in " + package_name);
                }
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }
            //insert
        } else {
            try {
                context.getContentResolver().insert(Aware_Settings.CONTENT_URI, setting);
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Added: " + key + "=" + value + " in " + package_name);
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }
        }
        if (qry != null && !qry.isClosed()) qry.close();
    }

    /**
     * Allows self-contained apps to join a study
     *
     * @param context
     * @param study_url
     */
    public static void joinStudy(Context context, String study_url) {
        Intent join = new Intent(context, JoinStudy.class);
        join.putExtra(StudyUtils.EXTRA_JOIN_STUDY, study_url);
        context.startService(join);
    }

    /**
     * Allows the dashboard to modify unitary settings for tweaking a configuration for devices.
     *
     * @param c
     * @param configs
     */
    protected static void tweakSettings(Context c, JSONArray configs) {
        //Apply study settings
        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();
        JSONArray schedulers = new JSONArray();
        for (int i = 0; i < configs.length(); i++) {
            try {
                JSONObject element = configs.getJSONObject(i);
                if (element.has("plugins")) {
                    plugins = element.getJSONArray("plugins");
                }
                if (element.has("sensors")) {
                    sensors = element.getJSONArray("sensors");
                }
                if (element.has("schedulers")) {
                    schedulers = element.getJSONArray("schedulers");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Set the sensors' settings first
        for (int i = 0; i < sensors.length(); i++) {
            try {
                JSONObject sensor_config = sensors.getJSONObject(i);
                Aware.setSetting(c, sensor_config.getString("setting"), sensor_config.get("value"), "com.aware.phone");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);
                String package_name = plugin_config.getString("plugin");
                JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                for (int j = 0; j < plugin_settings.length(); j++) {
                    JSONObject plugin_setting = plugin_settings.getJSONObject(j);
                    Aware.setSetting(c, plugin_setting.getString("setting"), plugin_setting.get("value"), package_name);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Set schedulers
        if (schedulers.length() > 0)
            Scheduler.setSchedules(c, schedulers);

        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        c.sendBroadcast(apply);
    }

    /**
     * Used by self-contained apps to join a study
     */
    public static class JoinStudy extends StudyUtils {

        @Override
        protected void onHandleIntent(Intent intent) {
            String full_url = intent.getStringExtra(EXTRA_JOIN_STUDY);

            if (Aware.DEBUG) Log.d(Aware.TAG, "Joining: " + full_url);

            Uri study_uri = Uri.parse(full_url);
            // New study URL, chopping off query parameters.
            String protocol = study_uri.getScheme();
            List<String> path_segments = study_uri.getPathSegments();

            String study_api_key = path_segments.get(path_segments.size() - 1);
            String study_id = path_segments.get(path_segments.size() - 2);

            String request;
            if (protocol.equals("https")) {
                SSLManager.handleUrl(getApplicationContext(), full_url, true);

//                try {
//                    Intent installHTTPS = KeyChain.createInstallIntent();
//                    installHTTPS.putExtra(KeyChain.EXTRA_NAME, study_host);
//
//                    //Convert .crt to X.509 so Android knows what it is.
//                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
//                    InputStream caInput = SSLManager.getHTTPS(getApplicationContext(), full_url);
//                    Certificate ca = cf.generateCertificate(caInput);
//
//                    installHTTPS.putExtra(KeyChain.EXTRA_CERTIFICATE, ca.getEncoded());
//                    installHTTPS.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    startActivity(installHTTPS);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                } catch (CertificateException e) {
//                    e.printStackTrace();
//                }

                try {
                    request = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), full_url)).dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
                } catch (FileNotFoundException e) {
                    request = null;
                }
            } else {
                request = new Http(getApplicationContext()).dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
            }

            if (request != null) {

                if (request.equals("[]")) return;

                try {
                    JSONObject studyInfo = new JSONObject(request);

                    if (DEBUG)
                        Log.d(TAG, "Study info: " + studyInfo.toString(5));

                    //Request study settings
                    Hashtable<String, String> data = new Hashtable<>();
                    data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    data.put("platform", "android");
                    try {
                        PackageInfo package_info = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
                        data.put("package_name", package_info.packageName);
                        data.put("package_version_code", String.valueOf(package_info.versionCode));
                        data.put("package_version_name", String.valueOf(package_info.versionName));
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.d(Aware.TAG, "Failed to put package info: " + e);
                        e.printStackTrace();
                    }

                    String answer;
                    if (protocol.equals("https")) {
                        // Get SSL certs
                        SSLManager.handleUrl(getApplicationContext(), full_url, true);

                        try {
                            answer = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), full_url)).dataPOST(full_url, data, true);
                        } catch (FileNotFoundException e) {
                            answer = null;
                        }
                    } else {
                        answer = new Http(getApplicationContext()).dataPOST(full_url, data, true);
                    }

                    if (answer == null) {
                        Toast.makeText(getApplicationContext(), "Failed to connect to server, try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    JSONArray study_config = new JSONArray(answer);

                    if (DEBUG)
                        Log.d(TAG, "Study config: " + study_config.toString(5));

                    if (study_config.getJSONObject(0).has("message")) {
                        Toast.makeText(getApplicationContext(), study_config.getJSONObject(0).getString("message"), Toast.LENGTH_LONG).show();
                        return;
                    }

                    Cursor dbStudy = Aware.getStudy(getApplicationContext(), full_url);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, DatabaseUtils.dumpCursorToString(dbStudy));

                    if (dbStudy == null || !dbStudy.moveToFirst()) {
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, full_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config.toString());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_name"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "New study data: " + studyData.toString());
                        }
                    } else {
                        //User rejoined a study he was already part of. Mark as abandoned.
                        ContentValues complianceEntry = new ContentValues();
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "rejoined study. abandoning previous");

                        dbStudy.close();

                        //Update the information to the latest
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, full_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config.toString());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_name"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "Rejoined study data: " + studyData.toString());
                        }
                    }

                    //Apply study settings
                    JSONArray plugins = new JSONArray();
                    JSONArray sensors = new JSONArray();
                    JSONArray schedulers = new JSONArray();

                    for (int i = 0; i < study_config.length(); i++) {
                        try {
                            JSONObject element = study_config.getJSONObject(i);
                            if (element.has("plugins")) {
                                plugins = element.getJSONArray("plugins");
                            }
                            if (element.has("sensors")) {
                                sensors = element.getJSONArray("sensors");
                            }
                            if (element.has("schedulers")) {
                                schedulers = element.getJSONArray("schedulers");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    //Set the sensors' settings first
                    for (int i = 0; i < sensors.length(); i++) {
                        try {
                            JSONObject sensor_config = sensors.getJSONObject(i);
                            Aware.setSetting(getApplicationContext(), sensor_config.getString("setting"), sensor_config.get("value"), "com.aware.phone");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    //Set the plugins' settings now
                    ArrayList<String> active_plugins = new ArrayList<>();
                    for (int i = 0; i < plugins.length(); i++) {
                        try {
                            JSONObject plugin_config = plugins.getJSONObject(i);

                            String package_name = plugin_config.getString("plugin");
                            active_plugins.add(package_name);

                            JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                            for (int j = 0; j < plugin_settings.length(); j++) {
                                JSONObject plugin_setting = plugin_settings.getJSONObject(j);
                                Aware.setSetting(getApplicationContext(), plugin_setting.getString("setting"), plugin_setting.get("value"), package_name);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    //Set schedulers
                    if (schedulers.length() > 0)
                        Scheduler.setSchedules(getApplicationContext(), schedulers);

                    //Start plugins
                    for (String p : active_plugins) {
                        Aware.startPlugin(getApplicationContext(), p);
                    }

                    //Send data to server for the first time, so that this device is immediately visible on the dashboard
                    Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
                    sendBroadcast(sync);

                    Intent applyNew = new Intent(Aware.ACTION_AWARE_REFRESH);
                    sendBroadcast(applyNew);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (repeatingIntent != null) alarmManager.cancel(repeatingIntent);

        try {
            awareContext.unregisterReceiver(aware_BR);
            awareContext.unregisterReceiver(storage_BR);
            awareContext.unregisterReceiver(awareBoot);
        } catch (IllegalArgumentException e) {
            //There is no API to check if a broadcast receiver already is registered. Since Aware.java is shared accross plugins, the receiver is only registered on the client, not the plugins.
        }
    }

    public static void reset(Context c) {
        String device_id = Aware.getSetting(c, Aware_Preferences.DEVICE_ID);
        String device_label = Aware.getSetting(c, Aware_Preferences.DEVICE_LABEL);

        //We were in a study, let's quit now
        if (Aware.isStudy(c)) {
            Cursor study = Aware.getStudy(c, Aware.getSetting(c, Aware_Preferences.WEBSERVICE_SERVER));
            if (study != null && study.moveToFirst()) {
                ContentValues data = new ContentValues();
                data.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                c.getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, data, Aware_Provider.Aware_Studies.STUDY_ID + "=" + study.getInt(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_ID)), null);
            }
            if (study != null && !study.isClosed()) study.close();
        }

        //Remove all settings
        c.getContentResolver().delete(Aware_Settings.CONTENT_URI, null, null);

        //Remove all schedulers
        c.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, null);

        //Read default client settings
        SharedPreferences prefs = c.getSharedPreferences(c.getPackageName(), Context.MODE_PRIVATE);
        PreferenceManager.setDefaultValues(c, c.getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
        prefs.edit().commit();

        Map<String, ?> defaults = prefs.getAll();
        for (Map.Entry<String, ?> entry : defaults.entrySet()) {
            Aware.setSetting(c, entry.getKey(), entry.getValue(), "com.aware.phone");
        }

        //Keep previous AWARE Device ID and label
        Aware.setSetting(c, Aware_Preferences.DEVICE_ID, device_id, "com.aware.phone");
        Aware.setSetting(c, Aware_Preferences.DEVICE_LABEL, device_label, "com.aware.phone");

        ContentValues update_label = new ContentValues();
        update_label.put(Aware_Device.LABEL, device_label);
        c.getContentResolver().update(Aware_Device.CONTENT_URI, update_label, Aware_Device.DEVICE_ID + " LIKE '" + device_id + "'", null);

        //Turn off all active plugins
        ArrayList<String> active_plugins = new ArrayList<>();
        Cursor enabled_plugins = c.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
        if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
            do {
                String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                active_plugins.add(package_name);
            } while (enabled_plugins.moveToNext());
        }
        if (enabled_plugins != null && !enabled_plugins.isClosed()) enabled_plugins.close();
        if (active_plugins.size() > 0) {
            for (String package_name : active_plugins) {
                stopPlugin(c, package_name);
            }
            if (Aware.DEBUG) Log.w(TAG, "AWARE plugins disabled...");
        }

        Intent applyNew = new Intent(Aware.ACTION_AWARE_REFRESH);
        c.sendBroadcast(applyNew);
    }

    /**
     * AWARE Android Package Monitor
     * 1) Checks if a package is a plugin or not
     * 2) Installs a plugin that was just downloaded
     *
     * @author denzilferreira
     */
    public static class AndroidPackageMonitor extends BroadcastReceiver {
        private static PackageManager mPkgManager;

        @Override
        public void onReceive(Context context, Intent intent) {
            mPkgManager = context.getPackageManager();

            Bundle extras = intent.getExtras();
            Uri packageUri = intent.getData();
            if (packageUri == null) return;
            String packageName = packageUri.getSchemeSpecificPart();
            if (packageName == null) return;

            if (!packageName.matches("com.aware.plugin.*")) return;

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {

                Intent installed = new Intent(ACTION_AWARE_PLUGIN_INSTALLED);
                installed.putExtra(EXTRA_PLUGIN, packageName);
                context.sendBroadcast(installed);

                //Updating a package
                if (extras.getBoolean(Intent.EXTRA_REPLACING)) {
                    if (Aware.DEBUG) Log.d(TAG, packageName + " is updating!");

                    //Check study compliance
                    if (Aware.isStudy(context)) {
                        Cursor studyInfo = Aware.getStudy(context, Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER));
                        if (studyInfo != null && studyInfo.moveToFirst()) {
                            try {
                                JSONArray studyConfig = new JSONArray(studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                JSONArray plugins = new JSONArray();
                                for (int i = 0; i < studyConfig.length(); i++) {
                                    try {
                                        JSONObject element = studyConfig.getJSONObject(i);
                                        if (element.has("plugins")) {
                                            plugins = element.getJSONArray("plugins");
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                for (int j = 0; j < plugins.length(); j++) {
                                    JSONObject plugin_config = plugins.getJSONObject(j);
                                    String package_name = plugin_config.getString("plugin");

                                    //Log the updated plugin
                                    if (package_name.equalsIgnoreCase(packageName)) {
                                        ContentValues complianceEntry = new ContentValues();
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, studyInfo.getLong(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "updated plugin: " + package_name);

                                        context.getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);

                                        if (Aware.DEBUG)
                                            Log.d(Aware.TAG, "Study compliance check: " + complianceEntry.toString());
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
                    }

                    ContentValues rowData = new ContentValues();
                    rowData.put(Aware_Plugins.PLUGIN_VERSION, PluginsManager.getPluginVersion(context, packageName));
                    rowData.put(Aware_Plugins.PLUGIN_ICON, PluginsManager.getPluginIcon(context, packageName));
                    rowData.put(Aware_Plugins.PLUGIN_NAME, PluginsManager.getPluginName(context, packageName));

                    Cursor current_status = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, new String[]{Aware_Plugins.PLUGIN_STATUS}, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null, null);
                    if (current_status != null && current_status.moveToFirst()) {
                        if (current_status.getInt(current_status.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == PluginsManager.PLUGIN_UPDATED) { //was updated, set to active now
                            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                        }
                        current_status.close();
                    }

                    context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);

                    //Start plugin
                    Aware.startPlugin(context, packageName);
                    return;
                }

                //Installing new
                try {
                    ApplicationInfo app = mPkgManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);

                    ContentValues rowData = new ContentValues();
                    rowData.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, app.packageName);
                    rowData.put(Aware_Plugins.PLUGIN_NAME, app.loadLabel(awareContext.getPackageManager()).toString());
                    rowData.put(Aware_Plugins.PLUGIN_VERSION, PluginsManager.getPluginVersion(context, app.packageName));
                    rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                    rowData.put(Aware_Plugins.PLUGIN_ICON, PluginsManager.getPluginIcon(context, app.packageName));

                    if (PluginsManager.isLocal(context, app.packageName)) {
                        context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + app.packageName + "'", null);
                    } else {
                        context.getContentResolver().insert(Aware_Plugins.CONTENT_URI, rowData);
                    }

                    if (Aware.DEBUG)
                        Log.d(TAG, "AWARE plugin added and activated:" + app.packageName);

                    //Check study compliance
                    if (Aware.isStudy(context)) {
                        Cursor studyInfo = Aware.getStudy(context, Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER));
                        if (studyInfo != null && studyInfo.moveToFirst()) {
                            try {
                                JSONArray studyConfig = new JSONArray(studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                JSONArray plugins = new JSONArray();
                                for (int i = 0; i < studyConfig.length(); i++) {
                                    try {
                                        JSONObject element = studyConfig.getJSONObject(i);
                                        if (element.has("plugins")) {
                                            plugins = element.getJSONArray("plugins");
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                for (int j = 0; j < plugins.length(); j++) {
                                    JSONObject plugin_config = plugins.getJSONObject(j);
                                    String package_name = plugin_config.getString("plugin");

                                    //Participant installed necessary plugin
                                    if (package_name.equalsIgnoreCase(packageName)) {
                                        ContentValues complianceEntry = new ContentValues();
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, studyInfo.getLong(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "installed plugin: " + package_name);

                                        context.getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);

                                        if (Aware.DEBUG)
                                            Log.d(Aware.TAG, "Study compliance check: " + complianceEntry.toString());
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
                    }

                    Aware.startPlugin(context, app.packageName);

                } catch (final NameNotFoundException e) {
                    e.printStackTrace();
                }
            }

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                //Updating
                if (extras.getBoolean(Intent.EXTRA_REPLACING)) {
                    //this is an update, bail out.
                    return;
                }

                Intent installed = new Intent(ACTION_AWARE_PLUGIN_UNINSTALLED);
                installed.putExtra(EXTRA_PLUGIN, packageName);
                context.sendBroadcast(installed);

                //Check study compliance
                if (Aware.isStudy(context)) {
                    Cursor studyInfo = Aware.getStudy(context, Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER));
                    if (studyInfo != null && studyInfo.moveToFirst()) {
                        try {
                            JSONArray studyConfig = new JSONArray(studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                            JSONArray plugins = new JSONArray();
                            for (int i = 0; i < studyConfig.length(); i++) {
                                try {
                                    JSONObject element = studyConfig.getJSONObject(i);
                                    if (element.has("plugins")) {
                                        plugins = element.getJSONArray("plugins");
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            for (int j = 0; j < plugins.length(); j++) {
                                JSONObject plugin_config = plugins.getJSONObject(j);
                                String package_name = plugin_config.getString("plugin");

                                //Participant is breaking compliance, just uninstalled a plugin we have as needed for the study!
                                if (package_name.equalsIgnoreCase(packageName)) {
                                    ContentValues complianceEntry = new ContentValues();
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, studyInfo.getLong(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "uninstalled plugin: " + package_name);

                                    context.getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);

                                    if (Aware.DEBUG)
                                        Log.d(Aware.TAG, "Study compliance check: " + complianceEntry.toString());
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
                }

                //clean-up settings & schedules
                context.getContentResolver().delete(Aware_Settings.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);
                context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);

                //Deleting
                context.getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);
                if (Aware.DEBUG) Log.d(TAG, "AWARE plugin removed:" + packageName);
            }
        }
    }

    /**
     * BroadcastReceiver that monitors for AWARE framework actions:
     * - ACTION_AWARE_SYNC_DATA = upload data to remote webservice server.
     * - ACTION_AWARE_CLEAR_DATA = clears local device's AWARE modules databases.
     * - ACTION_AWARE_REFRESH - apply changes to the configuration.
     *
     * @author denzil
     */
    private static final Aware_Broadcaster aware_BR = new Aware_Broadcaster();

    public static class Aware_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //We are only synching the device information, study compliance and overall framework execution logs.
            String[] DATABASE_TABLES = new String[]{Aware_Provider.DATABASE_TABLES[0], Aware_Provider.DATABASE_TABLES[3], Aware_Provider.DATABASE_TABLES[4]};
            String[] TABLES_FIELDS = new String[]{Aware_Provider.TABLES_FIELDS[0], Aware_Provider.TABLES_FIELDS[3], Aware_Provider.TABLES_FIELDS[4]};
            Uri[] CONTEXT_URIS = new Uri[]{Aware_Device.CONTENT_URI, Aware_Provider.Aware_Studies.CONTENT_URI, Aware_Provider.Aware_Log.CONTENT_URI};

            if (intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                for (int i = 0; i < DATABASE_TABLES.length; i++) {
                    Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                    webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                    webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                    webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[i]);
                    webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[i].toString());
                    context.startService(webserviceHelper);
                }
            }

            if (intent.getAction().equals(Aware.ACTION_AWARE_CLEAR_DATA)) {
                for (int i = 0; i < DATABASE_TABLES.length; i++) {
                    context.getContentResolver().delete(Aware_Provider.Aware_Device.CONTENT_URI, null, null);
                    if (Aware.DEBUG) Log.d(TAG, "Cleared " + CONTEXT_URIS[i]);

                    //Clear remotely if webservices are active
                    if (Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                        Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                        webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE);
                        webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                        context.startService(webserviceHelper);
                    }
                }
            }

            if (intent.getAction().equals(Aware.ACTION_QUIT_STUDY)) {
                Aware.reset(context);
            }

            if (intent.getAction().equals(Aware.ACTION_AWARE_REFRESH)) {
                Intent refresh = new Intent(context, com.aware.Aware.class);
                context.startService(refresh);
            }
        }
    }

    /**
     * Checks if we have access to the storage of the device. Turns off AWARE when we don't, turns it back on when available again.
     */
    private static final Storage_Broadcaster storage_BR = new Storage_Broadcaster();

    public static class Storage_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
                if (Aware.DEBUG) Log.d(TAG, "Resuming AWARE data logging...");
            }
            if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                if (Aware.DEBUG)
                    Log.w(TAG, "Stopping AWARE data logging until the SDCard is available again...");
            }
            Intent aware = new Intent(context, Aware.class);
            context.startService(aware);
        }
    }

    /**
     * Checks if we still have the accessibility services active or not
     */
    private static final AwareBoot awareBoot = new AwareBoot();

    public static class AwareBoot extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Applications.isAccessibilityServiceActive(context); //This shows notification automatically if the accessibility services are off
        }
    }

    private static void complianceStatus() {
        ConnectivityManager connManager = (ConnectivityManager) awareContext.getSystemService(CONNECTIVITY_SERVICE);
        LocationManager locationManager = (LocationManager) awareContext.getSystemService(LOCATION_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) awareContext.getSystemService(TELEPHONY_SERVICE);

        JSONObject complianceStatus = new JSONObject();

        try {
            NetworkInfo active = connManager.getActiveNetworkInfo();
            if (active != null && active.isConnectedOrConnecting()) {
                complianceStatus.put("internet", true);
            } else {
                complianceStatus.put("internet", false);
            }

            NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi != null && wifi.isAvailable()) {
                complianceStatus.put("wifi", true);
            } else {
                complianceStatus.put("wifi", false);
            }

            NetworkInfo bt = connManager.getNetworkInfo(ConnectivityManager.TYPE_BLUETOOTH);
            if (bt != null && bt.isAvailable()) {
                complianceStatus.put("bt", true);
            } else {
                complianceStatus.put("bt", false);
            }

            NetworkInfo network = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (network != null && network.isAvailable()) {
                complianceStatus.put("network", true);
            } else {
                complianceStatus.put("network", false);
            }

            boolean airplane;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                airplane = Settings.Global.getInt(awareContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            } else {
                airplane = Settings.System.getInt(awareContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            }

            complianceStatus.put("airplane", airplane);
            complianceStatus.put("roaming", telephonyManager.isNetworkRoaming());
            complianceStatus.put("location_gps", locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
            complianceStatus.put("location_network", locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

            Aware.debug(awareContext, complianceStatus.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start active services
     */
    public static void startAWARE() {

        //Fixed: client or standalone apps can check the compliance if part of a study
        if ((awareContext.getPackageName().equals("com.aware.phone") || awareContext.getResources().getBoolean(R.bool.standalone)) && isStudy(awareContext))
            complianceStatus();

        startScheduler(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ESM).equals("true")) {
            startESM(awareContext);
        } else stopESM(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            startApplications(awareContext);
        } else stopApplications(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true")) {
            startAccelerometer(awareContext);
        } else stopAccelerometer(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations(awareContext);
        } else stopInstallations(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            startLocations(awareContext);
        } else stopLocations(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH).equals("true")) {
            startBluetooth(awareContext);
        } else stopBluetooth(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_SCREEN).equals("true")) {
            startScreen(awareContext);
        } else stopScreen(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BATTERY).equals("true")) {
            startBattery(awareContext);
        } else stopBattery(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true")) {
            startNetwork(awareContext);
        } else stopNetwork(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true")) {
            startTraffic(awareContext);
        } else stopTraffic(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("true")) {
            startCommunication(awareContext);
        } else stopCommunication(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR).equals("true")) {
            startProcessor(awareContext);
        } else stopProcessor(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE).equals("true")) {
            startTimeZone(awareContext);
        } else stopTimeZone(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_MQTT).equals("true")) {
            startMQTT(awareContext);
        } else stopMQTT(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE).equals("true")) {
            startGyroscope(awareContext);
        } else stopGyroscope(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_WIFI).equals("true")) {
            startWiFi(awareContext);
        } else stopWiFi(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY).equals("true")) {
            startTelephony(awareContext);
        } else stopTelephony(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ROTATION).equals("true")) {
            startRotation(awareContext);
        } else stopRotation(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LIGHT).equals("true")) {
            startLight(awareContext);
        } else stopLight(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY).equals("true")) {
            startProximity(awareContext);
        } else stopProximity(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER).equals("true")) {
            startMagnetometer(awareContext);
        } else stopMagnetometer(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BAROMETER).equals("true")) {
            startBarometer(awareContext);
        } else stopBarometer(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_GRAVITY).equals("true")) {
            startGravity(awareContext);
        } else stopGravity(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true")) {
            startLinearAccelerometer(awareContext);
        } else stopLinearAccelerometer(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true")) {
            startTemperature(awareContext);
        } else stopTemperature(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD).equals("true")) {
            startKeyboard(awareContext);
        } else stopKeyboard(awareContext);
    }

    /**
     * Stop all services
     */
    public static void stopAWARE() {
        stopApplications(awareContext);
        stopAccelerometer(awareContext);
        stopBattery(awareContext);
        stopBluetooth(awareContext);
        stopCommunication(awareContext);
        stopLocations(awareContext);
        stopNetwork(awareContext);
        stopTraffic(awareContext);
        stopScreen(awareContext);
        stopProcessor(awareContext);
        stopMQTT(awareContext);
        stopGyroscope(awareContext);
        stopWiFi(awareContext);
        stopTelephony(awareContext);
        stopTimeZone(awareContext);
        stopRotation(awareContext);
        stopLight(awareContext);
        stopProximity(awareContext);
        stopMagnetometer(awareContext);
        stopBarometer(awareContext);
        stopGravity(awareContext);
        stopLinearAccelerometer(awareContext);
        stopTemperature(awareContext);
        stopESM(awareContext);
        stopInstallations(awareContext);
        stopKeyboard(awareContext);
        stopScheduler(awareContext);
    }

    public static void startScheduler(Context context) {
        awareContext = context;
        if (scheduler == null) scheduler = new Intent(awareContext, Scheduler.class);
        awareContext.startService(scheduler);
    }

    public static void stopScheduler(Context context) {
        awareContext = context;
        if (scheduler != null) awareContext.stopService(scheduler);
    }

    /**
     * Start keyboard module
     */
    public static void startKeyboard(Context context) {
        awareContext = context;
        if (keyboard == null) keyboard = new Intent(awareContext, Keyboard.class);
        awareContext.startService(keyboard);
    }

    /**
     * Stop keyboard module
     */
    public static void stopKeyboard(Context context) {
        awareContext = context;
        if (keyboard != null) awareContext.stopService(keyboard);
    }

    /**
     * Start Applications module
     */
    public static void startApplications(Context context) {
        awareContext = context;
        if (applicationsSrv == null) {
            applicationsSrv = new Intent(awareContext, Applications.class);
        }
        try {
            ComponentName service = awareContext.startService(applicationsSrv);
        } catch (RuntimeException e) {
            //Gingerbread and Jelly Bean complain when we start the service explicitly. In these, it is handled by the OS
        }
    }

    /**
     * Stop Applications module
     */
    public static void stopApplications(Context context) {
        awareContext = context;
        if (applicationsSrv != null) {
            try {
                awareContext.stopService(applicationsSrv);
            } catch (RuntimeException e) {
                //Gingerbread and Jelly Bean complain when we stop the serive explicitly. In these, it is handled by the OS
            }
        }
    }

    /**
     * Start Installations module
     */
    public static void startInstallations(Context context) {
        awareContext = context;
        if (installationsSrv == null)
            installationsSrv = new Intent(awareContext, Installations.class);
        awareContext.startService(installationsSrv);
    }

    /**
     * Stop Installations module
     */
    public static void stopInstallations(Context context) {
        awareContext = context;
        if (installationsSrv != null) awareContext.stopService(installationsSrv);
    }

    /**
     * Start ESM module
     */
    public static void startESM(Context context) {
        awareContext = context;
        if (esmSrv == null) esmSrv = new Intent(awareContext, ESM.class);
        awareContext.startService(esmSrv);
    }

    /**
     * Stop ESM module
     */
    public static void stopESM(Context context) {
        awareContext = context;
        if (esmSrv != null) awareContext.stopService(esmSrv);
    }

    /**
     * Start Temperature module
     */
    public static void startTemperature(Context context) {
        awareContext = context;
        if (temperatureSrv == null)
            temperatureSrv = new Intent(awareContext, Temperature.class);
        awareContext.startService(temperatureSrv);
    }

    /**
     * Stop Temperature module
     */
    public static void stopTemperature(Context context) {
        awareContext = context;
        if (temperatureSrv != null) awareContext.stopService(temperatureSrv);
    }

    /**
     * Start Linear Accelerometer module
     */
    public static void startLinearAccelerometer(Context context) {
        awareContext = context;
        if (linear_accelSrv == null)
            linear_accelSrv = new Intent(awareContext, LinearAccelerometer.class);
        awareContext.startService(linear_accelSrv);
    }

    /**
     * Stop Linear Accelerometer module
     */
    public static void stopLinearAccelerometer(Context context) {
        awareContext = context;
        if (linear_accelSrv != null) awareContext.stopService(linear_accelSrv);
    }

    /**
     * Start Gravity module
     */
    public static void startGravity(Context context) {
        awareContext = context;
        if (gravitySrv == null) gravitySrv = new Intent(awareContext, Gravity.class);
        awareContext.startService(gravitySrv);
    }

    /**
     * Stop Gravity module
     */
    public static void stopGravity(Context context) {
        awareContext = context;
        if (gravitySrv != null) awareContext.stopService(gravitySrv);
    }

    /**
     * Start Barometer module
     */
    public static void startBarometer(Context context) {
        awareContext = context;
        if (barometerSrv == null) barometerSrv = new Intent(awareContext, Barometer.class);
        awareContext.startService(barometerSrv);
    }

    /**
     * Stop Barometer module
     */
    public static void stopBarometer(Context context) {
        awareContext = context;
        if (barometerSrv != null) awareContext.stopService(barometerSrv);
    }

    /**
     * Start Magnetometer module
     */
    public static void startMagnetometer(Context context) {
        awareContext = context;
        if (magnetoSrv == null) magnetoSrv = new Intent(awareContext, Magnetometer.class);
        awareContext.startService(magnetoSrv);
    }

    /**
     * Stop Magnetometer module
     */
    public static void stopMagnetometer(Context context) {
        awareContext = context;
        if (magnetoSrv != null) awareContext.stopService(magnetoSrv);
    }

    /**
     * Start Proximity module
     */
    public static void startProximity(Context context) {
        awareContext = context;
        if (proximitySrv == null) proximitySrv = new Intent(awareContext, Proximity.class);
        awareContext.startService(proximitySrv);
    }

    /**
     * Stop Proximity module
     */
    public static void stopProximity(Context context) {
        awareContext = context;
        if (proximitySrv != null) awareContext.stopService(proximitySrv);
    }

    /**
     * Start Light module
     */
    public static void startLight(Context context) {
        awareContext = context;
        if (lightSrv == null) lightSrv = new Intent(awareContext, Light.class);
        awareContext.startService(lightSrv);
    }

    /**
     * Stop Light module
     */
    public static void stopLight(Context context) {
        awareContext = context;
        if (lightSrv != null) awareContext.stopService(lightSrv);
    }

    /**
     * Start Rotation module
     */
    public static void startRotation(Context context) {
        awareContext = context;
        if (rotationSrv == null) rotationSrv = new Intent(awareContext, Rotation.class);
        awareContext.startService(rotationSrv);
    }

    /**
     * Stop Rotation module
     */
    public static void stopRotation(Context context) {
        awareContext = context;
        if (rotationSrv != null) awareContext.stopService(rotationSrv);
    }

    /**
     * Start the Telephony module
     */
    public static void startTelephony(Context context) {
        awareContext = context;
        if (telephonySrv == null) telephonySrv = new Intent(awareContext, Telephony.class);
        awareContext.startService(telephonySrv);
    }

    /**
     * Stop the Telephony module
     */
    public static void stopTelephony(Context context) {
        awareContext = context;
        if (telephonySrv != null) awareContext.stopService(telephonySrv);
    }

    /**
     * Start the WiFi module
     */
    public static void startWiFi(Context context) {
        awareContext = context;
        if (wifiSrv == null) wifiSrv = new Intent(awareContext, WiFi.class);
        awareContext.startService(wifiSrv);
    }

    public static void stopWiFi(Context context) {
        awareContext = context;
        if (wifiSrv != null) awareContext.stopService(wifiSrv);
    }

    /**
     * Start the gyroscope module
     */
    public static void startGyroscope(Context context) {
        awareContext = context;
        if (gyroSrv == null) gyroSrv = new Intent(awareContext, Gyroscope.class);
        awareContext.startService(gyroSrv);
    }

    /**
     * Stop the gyroscope module
     */
    public static void stopGyroscope(Context context) {
        awareContext = context;
        if (gyroSrv != null) awareContext.stopService(gyroSrv);
    }

    /**
     * Start the accelerometer module
     */
    public static void startAccelerometer(Context context) {
        awareContext = context;
        if (accelerometerSrv == null)
            accelerometerSrv = new Intent(awareContext, Accelerometer.class);
        awareContext.startService(accelerometerSrv);
    }

    /**
     * Stop the accelerometer module
     */
    public static void stopAccelerometer(Context context) {
        awareContext = context;
        if (accelerometerSrv != null) awareContext.stopService(accelerometerSrv);
    }

    /**
     * Start the Processor module
     */
    public static void startProcessor(Context context) {
        awareContext = context;
        if (processorSrv == null) processorSrv = new Intent(awareContext, Processor.class);
        awareContext.startService(processorSrv);
    }

    /**
     * Stop the Processor module
     */
    public static void stopProcessor(Context context) {
        awareContext = context;
        if (processorSrv != null) awareContext.stopService(processorSrv);
    }

    /**
     * Start the locations module
     */
    public static void startLocations(Context context) {
        awareContext = context;
        if (locationsSrv == null) locationsSrv = new Intent(awareContext, Locations.class);
        awareContext.startService(locationsSrv);
    }

    /**
     * Stop the locations module
     */
    public static void stopLocations(Context context) {
        awareContext = context;
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("false") && Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("false")) {
            if (locationsSrv != null) awareContext.stopService(locationsSrv);
        }
    }

    /**
     * Start the bluetooth module
     */
    public static void startBluetooth(Context context) {
        awareContext = context;
        if (bluetoothSrv == null) bluetoothSrv = new Intent(awareContext, Bluetooth.class);
        awareContext.startService(bluetoothSrv);
    }

    /**
     * Stop the bluetooth module
     */
    public static void stopBluetooth(Context context) {
        awareContext = context;
        if (bluetoothSrv != null) awareContext.stopService(bluetoothSrv);
    }

    /**
     * Start the screen module
     */
    public static void startScreen(Context context) {
        awareContext = context;
        if (screenSrv == null) screenSrv = new Intent(awareContext, Screen.class);
        awareContext.startService(screenSrv);
    }

    /**
     * Stop the screen module
     */
    public static void stopScreen(Context context) {
        awareContext = context;
        if (screenSrv != null) awareContext.stopService(screenSrv);
    }

    /**
     * Start battery module
     */
    public static void startBattery(Context context) {
        awareContext = context;
        if (batterySrv == null) batterySrv = new Intent(awareContext, Battery.class);
        awareContext.startService(batterySrv);
    }

    /**
     * Stop battery module
     */
    public static void stopBattery(Context context) {
        awareContext = context;
        if (batterySrv != null) awareContext.stopService(batterySrv);
    }

    /**
     * Start network module
     */
    public static void startNetwork(Context context) {
        awareContext = context;
        if (networkSrv == null) networkSrv = new Intent(awareContext, Network.class);
        awareContext.startService(networkSrv);
    }

    /**
     * Stop network module
     */
    public static void stopNetwork(Context context) {
        awareContext = context;
        if (networkSrv != null) awareContext.stopService(networkSrv);
    }

    /**
     * Start traffic module
     */
    public static void startTraffic(Context context) {
        awareContext = context;
        if (trafficSrv == null) trafficSrv = new Intent(awareContext, Traffic.class);
        awareContext.startService(trafficSrv);
    }

    /**
     * Stop traffic module
     */
    public static void stopTraffic(Context context) {
        awareContext = context;
        if (trafficSrv != null) awareContext.stopService(trafficSrv);
    }

    /**
     * Start the Timezone module
     */
    public static void startTimeZone(Context context) {
        awareContext = context;
        if (timeZoneSrv == null) timeZoneSrv = new Intent(awareContext, Timezone.class);
        awareContext.startService(timeZoneSrv);
    }

    /**
     * Stop the Timezone module
     */
    public static void stopTimeZone(Context context) {
        awareContext = context;
        if (timeZoneSrv != null) awareContext.stopService(timeZoneSrv);
    }

    /**
     * Start communication module
     */
    public static void startCommunication(Context context) {
        awareContext = context;
        if (communicationSrv == null)
            communicationSrv = new Intent(awareContext, Communication.class);
        awareContext.startService(communicationSrv);
    }

    /**
     * Stop communication module
     */
    public static void stopCommunication(Context context) {
        awareContext = context;
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("false")
                && Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("false")
                && Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("false")) {
            if (communicationSrv != null) awareContext.stopService(communicationSrv);
        }
    }

    /**
     * Start MQTT module
     */
    public static void startMQTT(Context context) {
        awareContext = context;
        if (mqttSrv == null) mqttSrv = new Intent(awareContext, Mqtt.class);
        awareContext.startService(mqttSrv);
    }

    /**
     * Stop MQTT module
     */
    public static void stopMQTT(Context context) {
        awareContext = context;
        if (mqttSrv != null) awareContext.stopService(mqttSrv);
    }
}
