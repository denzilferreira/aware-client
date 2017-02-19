
package com.aware;

import android.app.ActivityManager;
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
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;
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
import com.aware.providers.Battery_Provider;
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
//    public static final String ACTION_AWARE_REFRESH = "ACTION_AWARE_REFRESH";

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
     * Used by Plugin Manager to refresh UI
     */
    public static final String ACTION_AWARE_UPDATE_PLUGINS_INFO = "ACTION_AWARE_UPDATE_PLUGINS_INFO";

    /**
     * Used when quitting a study. This will reset the device to default settings.
     */
    public static final String ACTION_QUIT_STUDY = "ACTION_QUIT_STUDY";

    /**
     * Used by the AWARE watchdog
     */
    private static final String ACTION_AWARE_KEEP_ALIVE = "ACTION_AWARE_KEEP_ALIVE";

    /**
     * Used by the compliance check scheduler
     */
    private static final String ACTION_AWARE_STUDY_COMPLIANCE = "ACTION_AWARE_STUDY_COMPLIANCE";

    /**
     * Used on the scheduler class to define global schedules for AWARE, SYNC and SPACE MAINTENANCE actions
     */
    public static final String SCHEDULE_SYNC_DATA = "schedule_aware_sync_data";
    public static final String SCHEDULE_STUDY_COMPLIANCE = "schedule_aware_study_compliance";
    public static final String SCHEDULE_KEEP_ALIVE = "schedule_aware_keep_alive";

    /**
     * Deprecated: will be removed in next version
     */
    private static Context awareContext = null;

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
    private static Intent significantSrv = null;

    private AsyncStudyCheck studyCheck = null;

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
        public Aware getService() {
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

        IntentFilter storage = new IntentFilter();
        storage.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storage.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storage.addDataScheme("file");
        registerReceiver(storage_BR, storage);

        IntentFilter aware_actions = new IntentFilter();
        aware_actions.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        aware_actions.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        aware_actions.addAction(Aware.ACTION_QUIT_STUDY);
        registerReceiver(aware_BR, aware_actions);

        IntentFilter boot = new IntentFilter();
        boot.addAction(Intent.ACTION_BOOT_COMPLETED);
        boot.addAction(Intent.ACTION_SHUTDOWN);
        boot.addAction(Intent.ACTION_REBOOT);
        registerReceiver(awareBoot, boot);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            stopSelf();
            return;
        }

        //Boot core AWARE services
        startAWARE(getApplicationContext());

        //If Android M+ and client or standalone, ask to be added to the whilelist of Doze
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (getPackageName().equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone))) {
//            Intent intent = new Intent();
//            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
//                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
//            } else {
//                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
//                intent.setData(Uri.parse("package:" + getPackageName()));
//            }
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent);
//        }

        if (Aware.DEBUG) Log.d(TAG, "AWARE framework is created!");
    }

    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // Download the certificate, and block since we are already running in background
            // and we need the certificate immediately.
            SSLManager.downloadCertificate(getApplicationContext(), "api.awareframework.com", true);

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
            device_ping.put("platform", "android");
            try {
                PackageInfo package_info = getPackageManager().getPackageInfo(getPackageName(), 0);
                device_ping.put("package_name", package_info.packageName);
                if (package_info.packageName.equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone)) {
                    device_ping.put("package_version_code", String.valueOf(package_info.versionCode));
                    device_ping.put("package_version_name", String.valueOf(package_info.versionName));
                }
            } catch (PackageManager.NameNotFoundException e) {
            }

            try {
                new Https(SSLManager.getHTTPS(getApplicationContext(), "https://api.awareframework.com/index.php")).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping, true);
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
            studyCheck.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            studyCheck.put("study_check", "1");

            try {
                String study_status = new Https(SSLManager.getHTTPS(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER)))
                        .dataPOST(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER), studyCheck, true);

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

                    //Ignored by standalone apps. They handle their own sensors, so server settings do not apply.
                    if (!getResources().getBoolean(R.bool.standalone)) {
                        if (study.getString("config").equalsIgnoreCase("[]")) {
                            Aware.tweakSettings(getApplicationContext(), new JSONArray(study.getString("config")));
                        } else if (!study.getString("config").equalsIgnoreCase("[]")) {
                            JSONObject configJSON = new JSONObject(study.getString("config"));
                            Aware.tweakSettings(getApplicationContext(), new JSONArray().put(configJSON));
                        }
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
        protected void onCancelled(Boolean aBoolean) {
            super.onCancelled(aBoolean);

            studyCheck = null;
        }

        @Override
        protected void onPostExecute(Boolean studyStatus) {
            super.onPostExecute(studyStatus);

            studyCheck = null;

            if (!studyStatus) {
                sendBroadcast(new Intent(Aware.ACTION_QUIT_STUDY));
            }
        }
    }

    private void get_device_info() {
        Cursor awareContextDevice = getContentResolver().query(Aware_Device.CONTENT_URI, null, null, null, null);
        if (awareContextDevice == null || !awareContextDevice.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Device.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Aware_Device.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
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
            rowData.put(Aware_Device.LABEL, Aware.getSetting(this, Aware_Preferences.DEVICE_LABEL));

            try {
                getContentResolver().insert(Aware_Device.CONTENT_URI, rowData);

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

    public static void debug(Context c, String message) {
        //NOTE: only collect this aware_log if in a study for compliance checks
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

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length() > 0 ? Aware.getSetting(this, Aware_Preferences.DEBUG_TAG) : TAG;

            get_device_info();

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.AWARE_DONATE_USAGE).equals("true")) {
                new AsyncPing().execute();
            }

            //only the client and self-contained apps need to run the keep alive. Plugins are handled by them.
            if (getApplicationContext().getPackageName().equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone)) {
                try {
                    Scheduler.Schedule watchdog = Scheduler.getSchedule(this, SCHEDULE_KEEP_ALIVE);
                    if (watchdog == null) {
                        watchdog = new Scheduler.Schedule(SCHEDULE_KEEP_ALIVE);
                        watchdog.setInterval(5)
                                .setActionType(Scheduler.ACTION_TYPE_SERVICE)
                                .setActionIntentAction(ACTION_AWARE_KEEP_ALIVE)
                                .setActionClass(getPackageName() + "/" + getClass().getName());

                        Scheduler.saveSchedule(this, watchdog);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //Set compliance checks if on a study
            if ((getPackageName().equals("com.aware.phone") || getResources().getBoolean(R.bool.standalone)) && isStudy(getApplicationContext())) {
                try {
                    Scheduler.Schedule compliance = Scheduler.getSchedule(this, Aware.SCHEDULE_STUDY_COMPLIANCE);
                    if (compliance == null) {
                        compliance = new Scheduler.Schedule(Aware.SCHEDULE_STUDY_COMPLIANCE);
                        compliance.setInterval(10)
                                .setActionType(Scheduler.ACTION_TYPE_SERVICE)
                                .setActionIntentAction(Aware.ACTION_AWARE_STUDY_COMPLIANCE)
                                .setActionClass(getPackageName() + "/" + getClass().getName());

                        Scheduler.saveSchedule(this, compliance);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (intent != null && intent.getAction() != null) {
                if( intent.getAction().equalsIgnoreCase(ACTION_AWARE_STUDY_COMPLIANCE))
                    complianceStatus(getApplicationContext());

                if (intent.getAction().equalsIgnoreCase(ACTION_AWARE_KEEP_ALIVE)) {

                    //Check if study is ongoing or any changes to the configuration
                    if (studyCheck == null && Aware.isStudy(getApplicationContext())) {
                        studyCheck = new AsyncStudyCheck();
                        studyCheck.execute();
                    }

                    startAWARE(getApplicationContext());

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
                }
            }

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
                                    .setActionIntentAction(Aware.ACTION_AWARE_SYNC_DATA)
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
                                        .setActionIntentAction(Aware.ACTION_AWARE_SYNC_DATA)
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
        } else {
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

            stopAWARE(this);
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
        PackageInfo packageInfo = PluginsManager.isInstalled(context, package_name);
        if (packageInfo != null) {
            if (packageInfo.versionName.equals("bundled")) {
                Intent bundled = new Intent();
                bundled.setComponent(new ComponentName(context.getPackageName(), package_name + ".Plugin"));
                context.stopService(bundled);

                if (Aware.DEBUG) Log.d(TAG, "Bundled " + package_name + ".Plugin stopped...");

            } else {
                Intent external = new Intent();
                external.setComponent(new ComponentName(package_name, package_name + ".Plugin"));
                context.stopService(external);

                if (Aware.DEBUG) Log.d(TAG, package_name + " stopped...");
            }

            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
            context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);

            if (context.getPackageName().equals("com.aware.phone") || context.getResources().getBoolean(R.bool.standalone)) {
                context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
            }
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
        PackageInfo packageInfo = PluginsManager.isInstalled(context, package_name);
        if (packageInfo != null) {
            if (packageInfo.versionName.equals("bundled")) {
                Intent bundled = new Intent();
                bundled.setComponent(new ComponentName(context.getPackageName(), package_name + ".Plugin"));
                context.startService(bundled);

                if (Aware.DEBUG) Log.d(TAG, "Bundled " + package_name + ".Plugin started...");

            } else {
                Intent external = new Intent();
                external.setComponent(new ComponentName(package_name, package_name + ".Plugin"));
                context.startService(external);

                if (Aware.DEBUG) Log.d(TAG, package_name + " started...");
            }

            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
            context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);

            if (context.getPackageName().equals("com.aware.phone") || context.getResources().getBoolean(R.bool.standalone)) {
                context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
            }
        }
    }

    /**
     * Requests the download of a plugin given the package name from AWARE webservices or the Play Store otherwise
     *
     * @param context
     * @param package_name
     * @param is_update
     */
    public static void downloadPlugin(Context context, String package_name, String study_custom_url, boolean is_update) {
        Intent pluginIntent = new Intent(context, DownloadPluginService.class);
        pluginIntent.putExtra("package_name", package_name);
        pluginIntent.putExtra("is_update", is_update);
        if (study_custom_url != null)
            pluginIntent.putExtra("study_url", study_custom_url);
        context.startService(pluginIntent);
    }

    /**
     * Given a plugin's package name, fetch the context card for reuse.
     *
     * @param context:      application context
     * @param package_name: plugin's package name
     * @return View for reuse
     */
    public static View getContextCard(final Context context, final String package_name) {

        boolean is_bundled = false;
        PackageInfo pkg = PluginsManager.isInstalled(context, package_name);
        if (pkg != null && pkg.versionName.equals("bundled")) {
            is_bundled = true;
        }

        if (is_bundled) {
            if (!isClassAvailable(context, context.getPackageName(), "ContextCard")) {
                Log.d(Aware.TAG, "No ContextCard detected for " + context.getPackageName());
                return new View(context);
            }
        } else {
            if (!isClassAvailable(context, package_name, "ContextCard")) {
                Log.d(Aware.TAG, "No ContextCard detected for " + package_name);
                return new View(context);
            }
        }

        try {
            String contextCardClass = ((is_bundled) ? context.getPackageName() + "/" + package_name : package_name ) + ".ContextCard";
            Context reflectedContext = context.createPackageContext(((is_bundled) ? context.getPackageName() : package_name), Context.CONTEXT_INCLUDE_CODE + Context.CONTEXT_IGNORE_SECURITY);
            Class<?> reflectedContextCard = reflectedContext.getClassLoader().loadClass(contextCardClass);
            Object contextCard = reflectedContextCard.newInstance();
            Method getContextCard = contextCard.getClass().getDeclaredMethod("getContextCard", Context.class);
            getContextCard.setAccessible(true);

            View ui = (View) getContextCard.invoke(contextCard, reflectedContext);
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
                plugin_header.setPadding(16, 0, 0, 0);
                params.gravity = android.view.Gravity.CENTER_VERTICAL;
                plugin_header.setLayoutParams(params);
                info.addView(plugin_header);

                //Check if plugin has settings. Add button if it does.
                if (isClassAvailable(context, package_name, "Settings")) {
                    ImageView infoSettings = new ImageView(context);
                    infoSettings.setBackgroundResource(R.drawable.ic_action_plugin_settings);
                    infoSettings.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean is_bundled = false;
                            PackageInfo pkg = PluginsManager.isInstalled(context, package_name);
                            if (pkg != null && pkg.versionName.equals("bundled")) {
                                is_bundled = true;
                            }

                            Intent open_settings = new Intent();
                            open_settings.setComponent(new ComponentName(((is_bundled) ? context.getPackageName() : package_name ), package_name + ".Settings"));
                            open_settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(open_settings);
                        }
                    });
                    ViewGroup.LayoutParams paramsHeader = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    paramsHeader.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 46, context.getResources().getDisplayMetrics());
                    paramsHeader.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 46, context.getResources().getDisplayMetrics());
                    infoSettings.setLayoutParams(paramsHeader);

                    info.addView(infoSettings);

                    //Add settings shortcut to card
                    card.addView(info);
                }

                //Add inflated UI to card
                card.addView(ui);

                return card;
            } else {
                return new View(context);
            }
        } catch (InstantiationException | NoSuchMethodException | NameNotFoundException | IllegalAccessException | ClassNotFoundException | InvocationTargetException e) {
            return new View(context);
        }
    }

    /**
     * Given a package and class name, check if the class exists or not.
     *
     * @param package_name
     * @param class_name
     * @return true if exists, false otherwise
     */
    public static boolean isClassAvailable(Context context, String package_name, String class_name) {
        if (context.getResources().getBoolean(R.bool.standalone)) {
            try {
                Class.forName(package_name + "." + class_name);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        } else {
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
    }

    /**
     * Retrieve setting value given key.
     *
     * @param key
     * @return value
     */
    public static String getSetting(Context context, String key) {

        boolean is_global;

        ArrayList<String> global_settings = new ArrayList<>();
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
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null,
                Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + ((is_global) ? "'com.aware.phone'" : "'" + context.getPackageName() + "'"),
                null, null);
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
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null,
                Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + package_name + "'",
                null, null);
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
        if (key.equals(Aware_Preferences.DEVICE_ID) && Aware.getSetting(context, Aware_Preferences.DEVICE_ID).length() > 0) {
            Log.d(Aware.TAG, "AWARE UUID: " + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + " in " + context.getPackageName());
            return;
        }

        if (key.equals(Aware_Preferences.DEVICE_LABEL) && ((String) value).length() > 0) {
            ContentValues newLabel = new ContentValues();
            newLabel.put(Aware_Provider.Aware_Device.LABEL, (String) value);
            context.getContentResolver().update(Aware_Provider.Aware_Device.CONTENT_URI, newLabel, Aware_Provider.Aware_Device.DEVICE_ID + " LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + "'", null);
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
            context.getContentResolver().update(Aware_Provider.Aware_Device.CONTENT_URI, newLabel, Aware_Provider.Aware_Device.DEVICE_ID + " LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + "'", null);
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
     * NOTE: serverConfig only has active settings. It also does not contain credentials or server info.
     * This function parses the server config and adjusts the local settings to replicate the server latest settings.
     *
     * @param c
     * @param serverConfig
     */
    protected static void tweakSettings(Context c, JSONArray serverConfig) {

        boolean config_changed = false;

        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();
        JSONArray schedulers = new JSONArray();
        for (int i = 0; i < serverConfig.length(); i++) {
            try {
                JSONObject element = serverConfig.getJSONObject(i);
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

        JSONArray localConfig = new JSONArray();
        Cursor study = getStudy(c, Aware.getSetting(c, Aware_Preferences.WEBSERVICE_SERVER));
        int study_id = 0;
        if (study != null && study.moveToFirst()) {
            try {
                localConfig = new JSONArray(study.getString(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                study_id = study.getInt(study.getColumnIndex(Aware_Provider.Aware_Studies._ID));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (study != null && !study.isClosed()) study.close();

        JSONArray localSensors = new JSONArray();
        JSONArray localPlugins = new JSONArray();
        JSONArray localSchedulers = new JSONArray();
        if (localConfig.length() > 0) {
            for (int i = 0; i < localConfig.length(); i++) {
                try {
                    JSONObject element = localConfig.getJSONObject(i);
                    if (element.has("sensors"))
                        localSensors = element.getJSONArray("sensors");
                    if (element.has("plugins"))
                        localPlugins = element.getJSONArray("plugins");
                    if (element.has("schedulers"))
                        localSchedulers = element.getJSONArray("schedulers");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                ArrayList<JSONArray> sensorSync = sensorDiff(sensors, localSensors); //check sensors first
                if (sensorSync.get(0).length() > 0 || sensorSync.get(1).length() > 0) {
                    JSONArray enabled = sensorSync.get(0);
                    for (int i = 0; i < enabled.length(); i++) {
                        try {
                            JSONObject sensor_config = enabled.getJSONObject(i);
                            if (sensor_config.getString("setting").contains("status")) {
                                Aware.setSetting(c, sensor_config.getString("setting"), true, "com.aware.phone");
                            } else
                                Aware.setSetting(c, sensor_config.getString("setting"), sensor_config.getString("value"), "com.aware.phone");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    JSONArray disabled = sensorSync.get(1);
                    for (int i = 0; i < disabled.length(); i++) {
                        try {
                            JSONObject sensor_config = disabled.getJSONObject(i);
                            if (sensor_config.getString("setting").contains("status")) {
                                Aware.setSetting(c, sensor_config.getString("setting"), false, "com.aware.phone");
                            } else
                                Aware.setSetting(c, sensor_config.getString("setting"), sensor_config.getString("value"), "com.aware.phone");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    if (enabled.length() > 0 || disabled.length() > 0) config_changed = true;

                    if (config_changed) {
                        //Update local study configuration
                        for (int i = 0; i < enabled.length(); i++) {
                            localConfig.getJSONObject(0).getJSONArray("sensors").put(enabled.getJSONObject(i));
                        }

                        for (int i = 0; i < disabled.length(); i++) {
                            JSONObject removed = disabled.getJSONObject(i);
                            for (int j = 0; j < localConfig.getJSONObject(0).getJSONArray("sensors").length(); j++) {
                                JSONObject local = localConfig.getJSONObject(0).getJSONArray("sensors").getJSONObject(j);
                                if (removed.getString("setting").equalsIgnoreCase(local.getString("setting")))
                                    localConfig.getJSONObject(0).getJSONArray("sensors").remove(j);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                ArrayList<JSONArray> pluginSync = pluginDiff(plugins, localPlugins);
                if (pluginSync.get(0).length() > 0 || pluginSync.get(1).length() > 0) {
                    JSONArray enabled = pluginSync.get(0);
                    for (int i = 0; i < enabled.length(); i++) {
                        try {
                            JSONObject plugin_config = enabled.getJSONObject(i);
                            String package_name = plugin_config.getString("plugin");
                            JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                            for (int j = 0; j < plugin_settings.length(); j++) {
                                JSONObject plugin_set = plugin_settings.getJSONObject(j);
                                if (plugin_set.getString("setting").contains("status")) {
                                    Aware.setSetting(c, plugin_set.getString("setting"), true, package_name);
                                } else
                                    Aware.setSetting(c, plugin_set.getString("setting"), plugin_set.getString("value"), package_name);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    JSONArray disabled = pluginSync.get(1);
                    for (int i = 0; i < disabled.length(); i++) {
                        try {
                            JSONObject plugin_config = disabled.getJSONObject(i);
                            String package_name = plugin_config.getString("plugin");
                            JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                            for (int j = 0; j < plugin_settings.length(); j++) {
                                JSONObject plugin_set = plugin_settings.getJSONObject(j);
                                if (plugin_set.getString("setting").contains("status")) {
                                    Aware.setSetting(c, plugin_set.getString("setting"), false, package_name);
                                } else
                                    Aware.setSetting(c, plugin_set.getString("setting"), plugin_set.getString("value"), package_name);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    if (enabled.length() > 0 || disabled.length() > 0) config_changed = true;

                    if (config_changed) {
                        if (enabled.length() > 0 && !localConfig.getJSONObject(0).has("plugins")) {
                            localConfig.getJSONObject(0).put("plugins", new JSONArray());
                        }

                        //Update local study configuration
                        for (int i = 0; i < enabled.length(); i++) {
                            JSONObject plugin = enabled.getJSONObject(i);
                            localConfig.getJSONObject(0).getJSONArray("plugins").put(plugin);
                            if (PluginsManager.isInstalled(c, enabled.getJSONObject(i).getString("plugin")) != null) {
                                Aware.startPlugin(c, enabled.getJSONObject(i).getString("plugin"));
                            } else
                                Aware.downloadPlugin(c, enabled.getJSONObject(i).getString("plugin"), null, false);
                        }

                        for (int i = 0; i < disabled.length(); i++) {
                            JSONObject removed = disabled.getJSONObject(i);
                            for (int j = 0; j < localConfig.getJSONObject(0).getJSONArray("plugins").length(); j++) {
                                JSONObject local = localConfig.getJSONObject(0).getJSONArray("plugins").getJSONObject(j);
                                if (removed.getString("plugin").equalsIgnoreCase(local.getString("plugin"))) {
                                    localConfig.getJSONObject(0).getJSONArray("plugins").remove(j);
                                    Aware.stopPlugin(c, removed.getString("plugin"));
                                }
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (config_changed) {
            ContentValues newCfg = new ContentValues();
            newCfg.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, localConfig.toString());
            c.getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, newCfg, Aware_Provider.Aware_Studies._ID + "=" + study_id, null);

            //Set schedulers
            if (schedulers.length() > 0)
                Scheduler.setSchedules(c, schedulers);

            Aware.toggleSensors(c);
        }
    }

    /**
     * This function returns a list of plugins to enable and one to disable because of server side configuration changes
     *
     * @param server
     * @param local
     * @return
     * @throws JSONException
     */
    private static ArrayList<JSONArray> pluginDiff(JSONArray server, JSONArray local) throws JSONException {
        JSONArray to_enable = new JSONArray();
        JSONArray to_disable = new JSONArray();

        //enable new plugins from the server
        for (int i = 0; i < server.length(); i++) {
            JSONObject server_plugin = server.getJSONObject(i);
            boolean is_present = false;
            for (int j = 0; j < local.length(); j++) {
                JSONObject local_plugin = local.getJSONObject(j);
                if (local_plugin.getString("plugin").equalsIgnoreCase(server_plugin.getString("plugin"))) {
                    is_present = true;
                    break;
                }
            }
            if (!is_present) to_enable.put(server_plugin);
        }

        //disable local sensors that are no longer in the server
        for (int j = 0; j < local.length(); j++) {
            JSONObject local_plugin = local.getJSONObject(j);

            boolean remove = true;
            for (int i = 0; i < server.length(); i++) {
                JSONObject server_plugin = server.getJSONObject(i);
                if (local_plugin.getString("plugin").equalsIgnoreCase(server_plugin.getString("plugin"))) {
                    remove = false;
                    break;
                }
            }
            if (remove) to_disable.put(local_plugin);
        }

        ArrayList<JSONArray> output = new ArrayList<>();
        output.add(to_enable);
        output.add(to_disable);

        return output;
    }

    /**
     * This function returns a list of sensors to enable and one to disable because of server side configuration changes
     *
     * @param server
     * @param local
     * @return
     * @throws JSONException
     */
    private static ArrayList<JSONArray> sensorDiff(JSONArray server, JSONArray local) throws JSONException {
        JSONArray to_enable = new JSONArray();
        JSONArray to_disable = new JSONArray();

        ArrayList<String> immutable_settings = new ArrayList<>();
        immutable_settings.add("status_mqtt");
        immutable_settings.add("mqtt_server");
        immutable_settings.add("mqtt_port");
        immutable_settings.add("mqtt_keep_alive");
        immutable_settings.add("mqtt_qos");
        immutable_settings.add("mqtt_username");
        immutable_settings.add("mqtt_password");
        immutable_settings.add("status_esm");
        immutable_settings.add("study_id");
        immutable_settings.add("study_start");
        immutable_settings.add("webservice_server");
        immutable_settings.add("status_webservice");

        //enable new sensors from the server
        for (int i = 0; i < server.length(); i++) {
            JSONObject server_sensor = server.getJSONObject(i);
            boolean is_present = false;
            for (int j = 0; j < local.length(); j++) {
                JSONObject local_sensor = local.getJSONObject(j);
                if (immutable_settings.contains(local_sensor.getString("setting"))) {
                    continue; //don't do anything
                }
                if (local_sensor.getString("setting").equalsIgnoreCase(server_sensor.getString("setting"))) {
                    is_present = true;
                    break;
                }
            }
            if (!is_present) to_enable.put(server_sensor);
        }

        //disable local sensors that are no longer in the server
        for (int j = 0; j < local.length(); j++) {
            JSONObject local_sensor = local.getJSONObject(j);
            if (immutable_settings.contains(local_sensor.getString("setting"))) {
                continue; //don't do anything
            }

            boolean remove = true;
            for (int i = 0; i < server.length(); i++) {
                JSONObject server_sensor = server.getJSONObject(i);
                if (local_sensor.getString("setting").equalsIgnoreCase(server_sensor.getString("setting"))) {
                    remove = false;
                    break;
                }
            }
            if (remove) to_disable.put(local_sensor);
        }

        ArrayList<JSONArray> output = new ArrayList<>();
        output.add(to_enable);
        output.add(to_disable);

        return output;
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
                    request = new Https(SSLManager.getHTTPS(getApplicationContext(), full_url)).dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
                } catch (FileNotFoundException e) {
                    request = null;
                }
            } else {
                request = new Http().dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
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
                            answer = new Https(SSLManager.getHTTPS(getApplicationContext(), full_url)).dataPOST(full_url, data, true);
                        } catch (FileNotFoundException e) {
                            answer = null;
                        }
                    } else {
                        answer = new Http().dataPOST(full_url, data, true);
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

                    if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

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

                    //Start engine
                    Aware.startAWARE(getApplicationContext());

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(aware_BR);
            unregisterReceiver(storage_BR);
            unregisterReceiver(awareBoot);
        } catch (IllegalArgumentException e) {
            //There is no API to check if a broadcast receiver already is registered. Since Aware.java is shared accross plugins, the receiver is only registered on the client, not the plugins.
        }
    }

    public static void reset(Context context) {
        String device_id = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        String device_label = Aware.getSetting(context, Aware_Preferences.DEVICE_LABEL);

        //We were in a study, let's quit now
        if (Aware.isStudy(context)) {
            Cursor study = Aware.getStudy(context, Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER));
            if (study != null && study.moveToFirst()) {
                ContentValues data = new ContentValues();
                data.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                context.getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, data, Aware_Provider.Aware_Studies.STUDY_ID + "=" + study.getInt(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_ID)), null);
            }
            if (study != null && !study.isClosed()) study.close();
        }

        //Remove all settings
        context.getContentResolver().delete(Aware_Settings.CONTENT_URI, null, null);

        //Remove all schedulers
        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, null);

        //Read default client settings
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        PreferenceManager.setDefaultValues(context, context.getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
        prefs.edit().commit();

        Map<String, ?> defaults = prefs.getAll();
        for (Map.Entry<String, ?> entry : defaults.entrySet()) {
            Aware.setSetting(context, entry.getKey(), entry.getValue(), "com.aware.phone");
        }

        //Keep previous AWARE Device ID and label
        Aware.setSetting(context, Aware_Preferences.DEVICE_ID, device_id, "com.aware.phone");
        Aware.setSetting(context, Aware_Preferences.DEVICE_LABEL, device_label, "com.aware.phone");

        ContentValues update_label = new ContentValues();
        update_label.put(Aware_Device.LABEL, device_label);
        context.getContentResolver().update(Aware_Device.CONTENT_URI, update_label, Aware_Device.DEVICE_ID + " LIKE '" + device_id + "'", null);

        //Turn off all active plugins
        ArrayList<String> active_plugins = new ArrayList<>();
        Cursor enabled_plugins = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
        if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
            do {
                String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                active_plugins.add(package_name);
            } while (enabled_plugins.moveToNext());
        }
        if (enabled_plugins != null && !enabled_plugins.isClosed()) enabled_plugins.close();

        if (active_plugins.size() > 0) {
            for (String package_name : active_plugins) {
                stopPlugin(context, package_name);
            }
            if (Aware.DEBUG) Log.w(TAG, "AWARE plugins disabled...");
        }

        Aware.toggleSensors(context);
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
                    }
                    if ( current_status != null && ! current_status.isClosed()) current_status.close();

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
                    rowData.put(Aware_Plugins.PLUGIN_NAME, app.loadLabel(context.getPackageManager()).toString());
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
                Aware.startAWARE(context);
            }
            if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                if (Aware.DEBUG)
                    Log.w(TAG, "Stopping AWARE data logging until the SDCard is available again...");

                Aware.stopAWARE(context);
            }
        }
    }

    /**
     * Checks if we still have the accessibility services active or not
     */
    private static final AwareBoot awareBoot = new AwareBoot();

    public static class AwareBoot extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ContentValues rowData = new ContentValues();

            //Force updated phone battery info
            Intent batt = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            Bundle extras = batt.getExtras();
            if (extras != null) {
                rowData.put(Battery_Provider.Battery_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Battery_Provider.Battery_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Battery_Provider.Battery_Data.LEVEL, extras.getInt(BatteryManager.EXTRA_LEVEL));
                rowData.put(Battery_Provider.Battery_Data.SCALE, extras.getInt(BatteryManager.EXTRA_SCALE));
                rowData.put(Battery_Provider.Battery_Data.VOLTAGE, extras.getInt(BatteryManager.EXTRA_VOLTAGE));
                rowData.put(Battery_Provider.Battery_Data.TEMPERATURE, extras.getInt(BatteryManager.EXTRA_TEMPERATURE) / 10);
                rowData.put(Battery_Provider.Battery_Data.PLUG_ADAPTOR, extras.getInt(BatteryManager.EXTRA_PLUGGED));
                rowData.put(Battery_Provider.Battery_Data.HEALTH, extras.getInt(BatteryManager.EXTRA_HEALTH));
                rowData.put(Battery_Provider.Battery_Data.TECHNOLOGY, extras.getString(BatteryManager.EXTRA_TECHNOLOGY));
            }

            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
                Applications.isAccessibilityServiceActive(context); //This shows notification automatically if the accessibility services are off
                Aware.debug(context, "phone: on");
                rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_BOOTED);
            }

            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
                Aware.debug(context, "phone: off");
                rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_SHUTDOWN);
            }
            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_REBOOT)) {
                Aware.debug(context, "phone: reboot");
                rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_REBOOT);
            }

            try {
                if (Aware.DEBUG) Log.d(TAG, "Battery: " + rowData.toString());
                context.getContentResolver().insert(Battery_Provider.Battery_Data.CONTENT_URI, rowData);
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }

            if (Aware.DEBUG) Log.d(TAG, Battery.ACTION_AWARE_BATTERY_CHANGED);
            Intent battChanged = new Intent(Battery.ACTION_AWARE_BATTERY_CHANGED);
            context.sendBroadcast(battChanged);
        }
    }

    private static void complianceStatus(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);

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
                airplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            } else {
                airplane = Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            }

            complianceStatus.put("airplane", airplane);
            complianceStatus.put("roaming", telephonyManager.isNetworkRoaming());
            complianceStatus.put("location_gps", locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
            complianceStatus.put("location_network", locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

            Aware.debug(context, complianceStatus.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Activate/deactivate sensors according to the saved settings
     *
     * @param context
     */
    public static void toggleSensors(Context context) {
        if (Aware.getSetting(context, Aware_Preferences.STATUS_SIGNIFICANT_MOTION).equals("true")) {
            startSignificant(context);
        } else stopSignificant(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ESM).equals("true")) {
            startESM(context);
        } else stopESM(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            startApplications(context);
        } else stopApplications(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ACCELEROMETER).equals("true")) {
            startAccelerometer(context);
        } else stopAccelerometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations(context);
        } else stopInstallations(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            startLocations(context);
        } else stopLocations(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BLUETOOTH).equals("true")) {
            startBluetooth(context);
        } else stopBluetooth(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_SCREEN).equals("true")) {
            startScreen(context);
        } else stopScreen(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BATTERY).equals("true")) {
            startBattery(context);
        } else stopBattery(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true")) {
            startNetwork(context);
        } else stopNetwork(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true")) {
            startTraffic(context);
        } else stopTraffic(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_CALLS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_MESSAGES).equals("true")) {
            startCommunication(context);
        } else stopCommunication(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_PROCESSOR).equals("true")) {
            startProcessor(context);
        } else stopProcessor(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TIMEZONE).equals("true")) {
            startTimeZone(context);
        } else stopTimeZone(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_MQTT).equals("true")) {
            startMQTT(context);
        } else stopMQTT(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_GYROSCOPE).equals("true")) {
            startGyroscope(context);
        } else stopGyroscope(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_WIFI).equals("true")) {
            startWiFi(context);
        } else stopWiFi(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TELEPHONY).equals("true")) {
            startTelephony(context);
        } else stopTelephony(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ROTATION).equals("true")) {
            startRotation(context);
        } else stopRotation(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LIGHT).equals("true")) {
            startLight(context);
        } else stopLight(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_PROXIMITY).equals("true")) {
            startProximity(context);
        } else stopProximity(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_MAGNETOMETER).equals("true")) {
            startMagnetometer(context);
        } else stopMagnetometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BAROMETER).equals("true")) {
            startBarometer(context);
        } else stopBarometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_GRAVITY).equals("true")) {
            startGravity(context);
        } else stopGravity(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true")) {
            startLinearAccelerometer(context);
        } else stopLinearAccelerometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TEMPERATURE).equals("true")) {
            startTemperature(context);
        } else stopTemperature(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_KEYBOARD).equals("true")) {
            startKeyboard(context);
        } else stopKeyboard(context);
    }

    /**
     * Please replace with startAWARE(Context context) to avoid memory leaks.
     * TODO: remove in future version
     */
    @Deprecated
    public static void startAWARE() {
        startScheduler(awareContext);

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_SIGNIFICANT_MOTION).equals("true")) {
            startSignificant(awareContext);
        } else stopSignificant(awareContext);

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
     * Start active services
     */
    public static void startAWARE(Context context) {
        startScheduler(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_SIGNIFICANT_MOTION).equals("true")) {
            startSignificant(context);
        } else stopSignificant(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ESM).equals("true")) {
            startESM(context);
        } else stopESM(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            startApplications(context);
        } else stopApplications(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ACCELEROMETER).equals("true")) {
            startAccelerometer(context);
        } else stopAccelerometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations(context);
        } else stopInstallations(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            startLocations(context);
        } else stopLocations(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BLUETOOTH).equals("true")) {
            startBluetooth(context);
        } else stopBluetooth(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_SCREEN).equals("true")) {
            startScreen(context);
        } else stopScreen(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BATTERY).equals("true")) {
            startBattery(context);
        } else stopBattery(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true")) {
            startNetwork(context);
        } else stopNetwork(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true")) {
            startTraffic(context);
        } else stopTraffic(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_CALLS).equals("true") || Aware.getSetting(context, Aware_Preferences.STATUS_MESSAGES).equals("true")) {
            startCommunication(context);
        } else stopCommunication(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_PROCESSOR).equals("true")) {
            startProcessor(context);
        } else stopProcessor(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TIMEZONE).equals("true")) {
            startTimeZone(context);
        } else stopTimeZone(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_MQTT).equals("true")) {
            startMQTT(context);
        } else stopMQTT(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_GYROSCOPE).equals("true")) {
            startGyroscope(context);
        } else stopGyroscope(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_WIFI).equals("true")) {
            startWiFi(context);
        } else stopWiFi(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TELEPHONY).equals("true")) {
            startTelephony(context);
        } else stopTelephony(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ROTATION).equals("true")) {
            startRotation(context);
        } else stopRotation(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LIGHT).equals("true")) {
            startLight(context);
        } else stopLight(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_PROXIMITY).equals("true")) {
            startProximity(context);
        } else stopProximity(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_MAGNETOMETER).equals("true")) {
            startMagnetometer(context);
        } else stopMagnetometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_BAROMETER).equals("true")) {
            startBarometer(context);
        } else stopBarometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_GRAVITY).equals("true")) {
            startGravity(context);
        } else stopGravity(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true")) {
            startLinearAccelerometer(context);
        } else stopLinearAccelerometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_TEMPERATURE).equals("true")) {
            startTemperature(context);
        } else stopTemperature(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_KEYBOARD).equals("true")) {
            startKeyboard(context);
        } else stopKeyboard(context);
    }

    /**
     * Stop all services
     * Please replace with stopAWARE(Context context) to avoid memory leaks.
     */
    @Deprecated
    public static void stopAWARE() {
        stopSignificant(awareContext);
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

    /**
     * Stop all services
     *
     * @param context
     */
    public static void stopAWARE(Context context) {
        if (context == null) return;
        stopSignificant(context);
        stopApplications(context);
        stopAccelerometer(context);
        stopBattery(context);
        stopBluetooth(context);
        stopCommunication(context);
        stopLocations(context);
        stopNetwork(context);
        stopTraffic(context);
        stopScreen(context);
        stopProcessor(context);
        stopMQTT(context);
        stopGyroscope(context);
        stopWiFi(context);
        stopTelephony(context);
        stopTimeZone(context);
        stopRotation(context);
        stopLight(context);
        stopProximity(context);
        stopMagnetometer(context);
        stopBarometer(context);
        stopGravity(context);
        stopLinearAccelerometer(context);
        stopTemperature(context);
        stopESM(context);
        stopInstallations(context);
        stopKeyboard(context);
        stopScheduler(context);
    }

    public static boolean is_running(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName()))
                return true;
        }
        return false;
    }

    /**
     * Start the significant motion service
     *
     * @param context
     */
    public static void startSignificant(Context context) {
        if (context == null) return;
        if (significantSrv == null) significantSrv = new Intent(context, SignificantMotion.class);
        context.startService(significantSrv);
    }

    /**
     * Stop the significant motion service
     *
     * @param context
     */
    public static void stopSignificant(Context context) {
        if (context == null) return;
        if (significantSrv != null) context.stopService(significantSrv);
    }

    /**
     * Start the scheduler service
     *
     * @param context
     */
    public static void startScheduler(Context context) {
        if (context == null) return;
        if (scheduler == null) scheduler = new Intent(context, Scheduler.class);
        context.startService(scheduler);
    }

    /**
     * Stop the scheduler service
     *
     * @param context
     */
    public static void stopScheduler(Context context) {
        if (context == null) return;
        if (scheduler != null) context.stopService(scheduler);
    }

    /**
     * Start keyboard module
     */
    public static void startKeyboard(Context context) {
        if (context == null) return;
        if (keyboard == null) keyboard = new Intent(context, Keyboard.class);
        context.startService(keyboard);
    }

    /**
     * Stop keyboard module
     */
    public static void stopKeyboard(Context context) {
        if (context == null) return;
        if (keyboard != null) context.stopService(keyboard);
    }

    /**
     * Start Applications module
     */
    public static void startApplications(Context context) {
        if (context == null) return;
        if (applicationsSrv == null) applicationsSrv = new Intent(context, Applications.class);
        try {
            context.startService(applicationsSrv);
        } catch (RuntimeException e) {
            //Gingerbread and Jelly Bean complain when we start the service explicitly. In these, it is handled by the OS
        }
    }

    /**
     * Stop Applications module
     */
    public static void stopApplications(Context context) {
        if (context == null) return;
        if (applicationsSrv != null) {
            try {
                context.stopService(applicationsSrv);
            } catch (RuntimeException e) {
                //Gingerbread and Jelly Bean complain when we stop the serive explicitly. In these, it is handled by the OS
            }
        }
    }

    /**
     * Start Installations module
     */
    public static void startInstallations(Context context) {
        if (context == null) return;
        if (installationsSrv == null) installationsSrv = new Intent(context, Installations.class);
        context.startService(installationsSrv);
    }

    /**
     * Stop Installations module
     */
    public static void stopInstallations(Context context) {
        if (context == null) return;
        if (installationsSrv != null) context.stopService(installationsSrv);
    }

    /**
     * Start ESM module
     */
    public static void startESM(Context context) {
        if (context == null) return;
        if (esmSrv == null) esmSrv = new Intent(context, ESM.class);
        context.startService(esmSrv);
    }

    /**
     * Stop ESM module
     */
    public static void stopESM(Context context) {
        if (context == null) return;
        if (esmSrv != null) context.stopService(esmSrv);
    }

    /**
     * Start Temperature module
     */
    public static void startTemperature(Context context) {
        if (context == null) return;
        if (temperatureSrv == null) temperatureSrv = new Intent(context, Temperature.class);
        context.startService(temperatureSrv);
    }

    /**
     * Stop Temperature module
     */
    public static void stopTemperature(Context context) {
        if (context == null) return;
        if (temperatureSrv != null) context.stopService(temperatureSrv);
    }

    /**
     * Start Linear Accelerometer module
     */
    public static void startLinearAccelerometer(Context context) {
        if (context == null) return;
        if (linear_accelSrv == null)
            linear_accelSrv = new Intent(context, LinearAccelerometer.class);
        context.startService(linear_accelSrv);
    }

    /**
     * Stop Linear Accelerometer module
     */
    public static void stopLinearAccelerometer(Context context) {
        if (context == null) return;
        if (linear_accelSrv != null) context.stopService(linear_accelSrv);
    }

    /**
     * Start Gravity module
     */
    public static void startGravity(Context context) {
        if (context == null) return;
        if (gravitySrv == null) gravitySrv = new Intent(context, Gravity.class);
        context.startService(gravitySrv);
    }

    /**
     * Stop Gravity module
     */
    public static void stopGravity(Context context) {
        if (context == null) return;
        if (gravitySrv != null) context.stopService(gravitySrv);
    }

    /**
     * Start Barometer module
     */
    public static void startBarometer(Context context) {
        if (context == null) return;
        if (barometerSrv == null) barometerSrv = new Intent(context, Barometer.class);
        context.startService(barometerSrv);
    }

    /**
     * Stop Barometer module
     */
    public static void stopBarometer(Context context) {
        if (context == null) return;
        if (barometerSrv != null) context.stopService(barometerSrv);
    }

    /**
     * Start Magnetometer module
     */
    public static void startMagnetometer(Context context) {
        if (context == null) return;
        if (magnetoSrv == null) magnetoSrv = new Intent(context, Magnetometer.class);
        context.startService(magnetoSrv);
    }

    /**
     * Stop Magnetometer module
     */
    public static void stopMagnetometer(Context context) {
        if (context == null) return;
        if (magnetoSrv != null) context.stopService(magnetoSrv);
    }

    /**
     * Start Proximity module
     */
    public static void startProximity(Context context) {
        if (context == null) return;
        if (proximitySrv == null) proximitySrv = new Intent(context, Proximity.class);
        context.startService(proximitySrv);
    }

    /**
     * Stop Proximity module
     */
    public static void stopProximity(Context context) {
        if (context == null) return;
        if (proximitySrv != null) context.stopService(proximitySrv);
    }

    /**
     * Start Light module
     */
    public static void startLight(Context context) {
        if (context == null) return;
        if (lightSrv == null) lightSrv = new Intent(context, Light.class);
        context.startService(lightSrv);
    }

    /**
     * Stop Light module
     */
    public static void stopLight(Context context) {
        if (context == null) return;
        if (lightSrv != null) context.stopService(lightSrv);
    }

    /**
     * Start Rotation module
     */
    public static void startRotation(Context context) {
        if (context == null) return;
        if (rotationSrv == null) rotationSrv = new Intent(context, Rotation.class);
        context.startService(rotationSrv);
    }

    /**
     * Stop Rotation module
     */
    public static void stopRotation(Context context) {
        if (context == null) return;
        if (rotationSrv != null) context.stopService(rotationSrv);
    }

    /**
     * Start the Telephony module
     */
    public static void startTelephony(Context context) {
        if (context == null) return;
        if (telephonySrv == null) telephonySrv = new Intent(context, Telephony.class);
        context.startService(telephonySrv);
    }

    /**
     * Stop the Telephony module
     */
    public static void stopTelephony(Context context) {
        if (context == null) return;
        if (telephonySrv != null) context.stopService(telephonySrv);
    }

    /**
     * Start the WiFi module
     */
    public static void startWiFi(Context context) {
        if (context == null) return;
        if (wifiSrv == null) wifiSrv = new Intent(context, WiFi.class);
        context.startService(wifiSrv);
    }

    public static void stopWiFi(Context context) {
        if (context == null) return;
        if (wifiSrv != null) context.stopService(wifiSrv);
    }

    /**
     * Start the gyroscope module
     */
    public static void startGyroscope(Context context) {
        if (context == null) return;
        if (gyroSrv == null) gyroSrv = new Intent(context, Gyroscope.class);
        context.startService(gyroSrv);
    }

    /**
     * Stop the gyroscope module
     */
    public static void stopGyroscope(Context context) {
        if (context == null) return;
        if (gyroSrv != null) context.stopService(gyroSrv);
    }

    /**
     * Start the accelerometer module
     */
    public static void startAccelerometer(Context context) {
        if (context == null) return;
        if (accelerometerSrv == null) accelerometerSrv = new Intent(context, Accelerometer.class);
        context.startService(accelerometerSrv);
    }

    /**
     * Stop the accelerometer module
     */
    public static void stopAccelerometer(Context context) {
        if (context == null) return;
        if (accelerometerSrv != null) context.stopService(accelerometerSrv);
    }

    /**
     * Start the Processor module
     */
    public static void startProcessor(Context context) {
        if (context == null) return;
        if (processorSrv == null) processorSrv = new Intent(context, Processor.class);
        context.startService(processorSrv);
    }

    /**
     * Stop the Processor module
     */
    public static void stopProcessor(Context context) {
        if (context == null) return;
        if (processorSrv != null) context.stopService(processorSrv);
    }

    /**
     * Start the locations module
     */
    public static void startLocations(Context context) {
        if (context == null) return;
        if (locationsSrv == null) locationsSrv = new Intent(context, Locations.class);
        context.startService(locationsSrv);
    }

    /**
     * Stop the locations module
     */
    public static void stopLocations(Context context) {
        if (context == null) return;
        if (!Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") && !Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            if (locationsSrv != null) context.stopService(locationsSrv);
        }
    }

    /**
     * Start the bluetooth module
     */
    public static void startBluetooth(Context context) {
        if (context == null) return;
        if (bluetoothSrv == null) bluetoothSrv = new Intent(context, Bluetooth.class);
        context.startService(bluetoothSrv);
    }

    /**
     * Stop the bluetooth module
     */
    public static void stopBluetooth(Context context) {
        if (context == null) return;
        if (bluetoothSrv != null) context.stopService(bluetoothSrv);
    }

    /**
     * Start the screen module
     */
    public static void startScreen(Context context) {
        if (context == null) return;
        if (screenSrv == null) screenSrv = new Intent(context, Screen.class);
        context.startService(screenSrv);
    }

    /**
     * Stop the screen module
     */
    public static void stopScreen(Context context) {
        if (context == null) return;
        if (screenSrv != null) context.stopService(screenSrv);
    }

    /**
     * Start battery module
     */
    public static void startBattery(Context context) {
        if (context == null) return;
        if (batterySrv == null) batterySrv = new Intent(context, Battery.class);
        context.startService(batterySrv);
    }

    /**
     * Stop battery module
     */
    public static void stopBattery(Context context) {
        if (context == null) return;
        if (batterySrv != null) context.stopService(batterySrv);
    }

    /**
     * Start network module
     */
    public static void startNetwork(Context context) {
        if (context == null) return;
        if (networkSrv == null) networkSrv = new Intent(context, Network.class);
        context.startService(networkSrv);
    }

    /**
     * Stop network module
     */
    public static void stopNetwork(Context context) {
        if (context == null) return;
        if (networkSrv != null) context.stopService(networkSrv);
    }

    /**
     * Start traffic module
     */
    public static void startTraffic(Context context) {
        if (context == null) return;
        if (trafficSrv == null) trafficSrv = new Intent(context, Traffic.class);
        context.startService(trafficSrv);
    }

    /**
     * Stop traffic module
     */
    public static void stopTraffic(Context context) {
        if (context == null) return;
        if (trafficSrv != null) context.stopService(trafficSrv);
    }

    /**
     * Start the Timezone module
     */
    public static void startTimeZone(Context context) {
        if (context == null) return;
        if (timeZoneSrv == null) timeZoneSrv = new Intent(context, Timezone.class);
        context.startService(timeZoneSrv);
    }

    /**
     * Stop the Timezone module
     */
    public static void stopTimeZone(Context context) {
        if (context == null) return;
        if (timeZoneSrv != null) context.stopService(timeZoneSrv);
    }

    /**
     * Start communication module
     */
    public static void startCommunication(Context context) {
        if (context == null) return;
        if (communicationSrv == null) communicationSrv = new Intent(context, Communication.class);
        context.startService(communicationSrv);
    }

    /**
     * Stop communication module
     */
    public static void stopCommunication(Context context) {
        if (context == null) return;
        if (!Aware.getSetting(context, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")
                && !Aware.getSetting(context, Aware_Preferences.STATUS_CALLS).equals("true")
                && !Aware.getSetting(context, Aware_Preferences.STATUS_MESSAGES).equals("true")) {
            if (communicationSrv != null) context.stopService(communicationSrv);
        }
    }

    /**
     * Start MQTT module
     */
    public static void startMQTT(Context context) {
        if (context == null) return;
        if (mqttSrv == null) mqttSrv = new Intent(context, Mqtt.class);
        context.startService(mqttSrv);
    }

    /**
     * Stop MQTT module
     */
    public static void stopMQTT(Context context) {
        if (context == null) return;
        if (mqttSrv != null) context.stopService(mqttSrv);
    }
}
