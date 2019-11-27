
package com.aware;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.*;
import android.content.*;
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
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.*;
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

import androidx.core.app.NotificationCompat;
import androidx.core.content.PermissionChecker;

import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Device;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.providers.Aware_Provider.Aware_Settings;
import com.aware.providers.Battery_Provider;
import com.aware.providers.Scheduler_Provider;
import com.aware.utils.*;

import dalvik.system.DexFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

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
     * Used to check if the core library is running or not inside individual plugins
     */
    public static boolean IS_CORE_RUNNING = false;

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
     * Set AWARE as a foreground service. This shows a permanent notification on the screen.
     */
    public static final String ACTION_AWARE_PRIORITY_FOREGROUND = "ACTION_AWARE_PRIORITY_FOREGROUND";

    /**
     * Set AWARE as a standard background service. May be killed or interrupted by Android at any time.
     */
    public static final String ACTION_AWARE_PRIORITY_BACKGROUND = "ACTION_AWARE_PRIORITY_BACKGROUND";

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
     * Broadcasted when joined study successfully
     */
    public static final String ACTION_JOINED_STUDY = "ACTION_JOINED_STUDY";

    /**
     * Used by the AWARE watchdog
     */
    private static final String ACTION_AWARE_KEEP_ALIVE = "ACTION_AWARE_KEEP_ALIVE";

    /**
     * Used by the compliance check scheduler
     */
    private static final String ACTION_AWARE_STUDY_COMPLIANCE = "ACTION_AWARE_STUDY_COMPLIANCE";

    /**
     * Notification ID for AWARE service as foreground (to handle Doze, Android O battery optimizations)
     */
    public static final int AWARE_FOREGROUND_SERVICE = 220882;

    /**
     * Used on the scheduler class to define global schedules for AWARE, SYNC and SPACE MAINTENANCE actions
     */
    //public static final String SCHEDULE_SYNC_DATA = "schedule_aware_sync_data";
    public static final String SCHEDULE_STUDY_COMPLIANCE = "schedule_aware_study_compliance";
    public static final String SCHEDULE_KEEP_ALIVE = "schedule_aware_keep_alive";

    /**
     * Android 8 notification channels support
     */
    public static final String AWARE_NOTIFICATION_CHANNEL_GENERAL = "AWARE_NOTIFICATION_CHANNEL_GENERAL";
    public static final String AWARE_NOTIFICATION_CHANNEL_SILENT = "AWARE_NOTIFICATION_CHANNEL_SILENT";
    public static final String AWARE_NOTIFICATION_CHANNEL_DATASYNC = "AWARE_NOTIFICATION_CHANNEL_DATASYNC";

    public static final int AWARE_NOTIFICATION_IMPORTANCE_GENERAL = NotificationManager.IMPORTANCE_HIGH;
    public static final int AWARE_NOTIFICATION_IMPORTANCE_SILENT = NotificationManager.IMPORTANCE_MIN;
    public static final int AWARE_NOTIFICATION_IMPORTANCE_DATASYNC = NotificationManager.IMPORTANCE_LOW;

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
    private static Intent websocket = null;

    private static AsyncStudyCheck studyCheck = null;

    /**
     * Variable for the Doze ignore list
     */
    private static final int AWARE_BATTERY_OPTIMIZATION_ID = 567567;

    /**
     * Holds a reference to the AWARE account, automatically restore in each plugin.
     */
    private static Account aware_account;

    public String AUTHORITY = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Aware_Provider.getAuthority(this);

        IntentFilter storage = new IntentFilter();
        storage.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storage.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storage.addDataScheme("file");
        registerReceiver(storage_BR, storage);

        IntentFilter boot = new IntentFilter();
        boot.addAction(Intent.ACTION_BOOT_COMPLETED);
        boot.addAction(Intent.ACTION_SHUTDOWN);
        boot.addAction(Intent.ACTION_REBOOT);
        boot.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(awareBoot, boot);

        IntentFilter awareActions = new IntentFilter();
        awareActions.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        awareActions.addAction(Aware.ACTION_QUIT_STUDY);
        registerReceiver(aware_BR, awareActions);

        IntentFilter foreground = new IntentFilter();
        foreground.addAction(Aware.ACTION_AWARE_PRIORITY_FOREGROUND);
        foreground.addAction(Aware.ACTION_AWARE_PRIORITY_BACKGROUND);
        registerReceiver(foregroundMgr, foreground);

        IntentFilter scheduler = new IntentFilter();
        scheduler.addAction(Intent.ACTION_TIME_TICK);
        schedulerTicker.interval_ms = 60000 * getApplicationContext().getResources().getInteger(R.integer.alarm_wakeup_interval_min);
        registerReceiver(schedulerTicker, scheduler);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            stopSelf();
            return;
        }

        //Android 8 specific: create notification channels for AWARE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager not_manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel aware_channel = new NotificationChannel(AWARE_NOTIFICATION_CHANNEL_GENERAL, getResources().getString(R.string.app_name), AWARE_NOTIFICATION_IMPORTANCE_GENERAL);
            aware_channel.setDescription(getResources().getString(R.string.channel_general_description));
            aware_channel.enableLights(true);
            aware_channel.setLightColor(Color.BLUE);
            aware_channel.enableVibration(true);
            not_manager.createNotificationChannel(aware_channel);

            NotificationChannel aware_channel_sync = new NotificationChannel(AWARE_NOTIFICATION_CHANNEL_DATASYNC, getResources().getString(R.string.app_name), AWARE_NOTIFICATION_IMPORTANCE_DATASYNC);
            aware_channel_sync.setDescription(getResources().getString(R.string.channel_datasync_description));
            aware_channel_sync.enableLights(false);
            aware_channel_sync.setLightColor(Color.BLUE);
            aware_channel_sync.enableVibration(false);
            aware_channel_sync.setSound(null, null);
            not_manager.createNotificationChannel(aware_channel_sync);

            NotificationChannel aware_channel_silent = new NotificationChannel(AWARE_NOTIFICATION_CHANNEL_SILENT, getResources().getString(R.string.app_name), AWARE_NOTIFICATION_IMPORTANCE_SILENT);
            aware_channel_silent.setDescription(getResources().getString(R.string.channel_silent_description));
            aware_channel_silent.enableLights(false);
            aware_channel_silent.setLightColor(Color.BLUE);
            aware_channel_silent.enableVibration(false);
            aware_channel_silent.setSound(null, null);
            not_manager.createNotificationChannel(aware_channel_silent);
        }

        // Start the foreground service only if it's the client or a standalone application
        if ((getApplicationContext().getPackageName().equals("com.aware.phone") || getApplicationContext().getApplicationContext().getResources().getBoolean(R.bool.standalone)))
            getApplicationContext().sendBroadcast(new Intent(Aware.ACTION_AWARE_PRIORITY_FOREGROUND));

        if (Aware.DEBUG) Log.d(TAG, "AWARE framework is created!");

        IS_CORE_RUNNING = true;

        aware_account = getAWAREAccount(this);
    }

    /**
     * Return AWARE's account
     *
     * @param context
     * @return
     */
    public static Account getAWAREAccount(Context context) {
        AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);
        Account[] accounts = accountManager.getAccountsByType(Aware_Accounts.Aware_Account.AWARE_ACCOUNT_TYPE);
        if (accounts.length > 0) {
            aware_account = accounts[0];
            return aware_account;
        }
        if (aware_account == null) {
            aware_account = new Account(Aware_Accounts.Aware_Account.AWARE_ACCOUNT, Aware_Accounts.Aware_Account.AWARE_ACCOUNT_TYPE);
            try {
                accountManager.addAccountExplicitly(aware_account, null, null);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
        return aware_account;
    }

    private final Foreground_Priority foregroundMgr = new Foreground_Priority();

    public class Foreground_Priority extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //do nothing unless it's the client or a standalone application
            if (!(context.getPackageName().equals("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone)))
                return;

            if (intent.getAction().equalsIgnoreCase(Aware.ACTION_AWARE_PRIORITY_FOREGROUND)) {
                if (DEBUG) Log.d(TAG, "Setting AWARE with foreground priority");
                foreground(true);
            } else if (intent.getAction().equalsIgnoreCase(Aware.ACTION_AWARE_PRIORITY_BACKGROUND)) {
                if (DEBUG) Log.d(TAG, "Setting AWARE with background priority");
                foreground(false);
            }
        }
    }

    public void foreground(boolean enable) {
        if (enable) {
            Intent aware = new Intent(this, Aware.class);
            PendingIntent onTap = PendingIntent.getService(this, 0, aware, 0);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, Aware.AWARE_NOTIFICATION_CHANNEL_SILENT);
            mBuilder.setSmallIcon(R.drawable.ic_action_aware_studies);
            mBuilder.setContentTitle(getApplicationContext().getResources().getString(R.string.foreground_notification_title));
            mBuilder.setContentText(getApplicationContext().getResources().getString(R.string.foreground_notification_text));
            mBuilder.setOngoing(true);
            mBuilder.setOnlyAlertOnce(true);
            mBuilder.setContentIntent(onTap);
            mBuilder = Aware.setNotificationProperties(mBuilder, AWARE_NOTIFICATION_IMPORTANCE_SILENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                mBuilder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_SILENT);

            startForeground(Aware.AWARE_FOREGROUND_SERVICE, mBuilder.build());
        } else {
            stopForeground(true);
        }
    }

    // set sound/vibration/priority, mainly for android v7 and older as these are handled by channel in 8+
    // TODO potentially add other variables here in the future (e.g., icon, contentTitle, etc.)
    public static NotificationCompat.Builder setNotificationProperties(NotificationCompat.Builder builder, int notificationImportance) {
        switch (notificationImportance) {
            case AWARE_NOTIFICATION_IMPORTANCE_DATASYNC:
                builder.setSound(null);
                builder.setVibrate(null);
                // priority low but still visible
                builder.setPriority(NotificationCompat.PRIORITY_LOW);
                return builder;
            case AWARE_NOTIFICATION_IMPORTANCE_SILENT:
                builder.setSound(null);
                builder.setVibrate(null);
                // priority lowest
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
                return builder;
            case AWARE_NOTIFICATION_IMPORTANCE_GENERAL:
                // default sound and vibration with HIGH priority
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                return builder;
            default:
                return builder;
        }
    }

    private final SchedulerTicker schedulerTicker = new SchedulerTicker();

    public class SchedulerTicker extends BroadcastReceiver {
        long last_time = 0;
        long interval_ms = 60000; // Set in Aware class where we have context

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) { //Executed every 1-minute. OS will send this tickle automatically
                long ts = System.currentTimeMillis();
                // Subtract 30s.  The ticker only is every minute anyway, this gives us some
                // slack in case the interval is slightly less than 60000ms.
                if (ts > last_time + interval_ms - 30000) {
                    last_time = ts;
                    Intent scheduler = new Intent(context, Scheduler.class);
                    scheduler.setAction(Scheduler.ACTION_AWARE_SCHEDULER_CHECK);
                    context.startService(scheduler);
                }
            }
        }
    }

    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // Download the certificate, and block since we are already running in background
            // and we need the certificate immediately.
            SSLManager.handleUrl(getApplicationContext(), "https://api.awareframework.com/index.php", true);

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
            device_ping.put("platform", "android");
            try {
                PackageInfo package_info = getPackageManager().getPackageInfo(getPackageName(), 0);
                device_ping.put("package_name", package_info.packageName);
                if (package_info.packageName.equals("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
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

    /**
     * Checks if current package is not affected by Volte, Doze
     * NOTE: this only works for Android OS native battery savings, not custom ones (e.g., Sony Stamina, etc).
     *
     * @param context
     * @return
     */
    public static boolean isBatteryOptimizationIgnored(Context context, String package_name) {
        boolean is_ignored = true;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            is_ignored = pm.isIgnoringBatteryOptimizations(package_name);
        }

        if (!is_ignored) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_recharge);
            mBuilder.setContentTitle(context.getApplicationContext().getResources().getString(R.string.aware_activate_battery_optimize_ignore_title));
            mBuilder.setContentText(context.getApplicationContext().getResources().getString(R.string.aware_activate_battery_optimize_ignore));
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
            mBuilder = setNotificationProperties(mBuilder, AWARE_NOTIFICATION_IMPORTANCE_GENERAL);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                mBuilder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);

            Intent batteryIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            batteryIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent clickIntent = PendingIntent.getActivity(context, 0, batteryIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

            NotificationManager notManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(Aware.AWARE_BATTERY_OPTIMIZATION_ID, mBuilder.build());
        }

        Log.d(Aware.TAG, "Battery Optimizations: " + is_ignored);

        return is_ignored;
    }

    private class AsyncStudyCheck extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> studyCheck = new Hashtable<>();
            studyCheck.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            studyCheck.put("study_check", "1");

            try {
                String webserver = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);

                Uri url = Uri.parse(webserver);
                String protocol = url.getScheme();

                String study_status;
                if (protocol.equalsIgnoreCase("https")) {
                    study_status = new Https(SSLManager.getHTTPS(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER))).dataPOST(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER), studyCheck, true);
                } else {
                    study_status = new Http().dataPOST(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER), studyCheck, true);
                }

                if (study_status == null)
                    return true; //unable to connect to server, timeout, etc. We do nothing.

                if (DEBUG)
                    Log.d(Aware.TAG, "Study_status: \n" + study_status);

                try {
                    JSONArray status = new JSONArray(study_status);

                    JSONObject study = status.getJSONObject(0);
                    if (!study.optBoolean("status", false)) {
                        return false; //study no longer active, make clients quit the study and reset.
                    }

                    if (!study.getString("config").equalsIgnoreCase("[]")) {
                        JSONObject configJSON = new JSONObject(study.getString("config"));
                        Aware.tweakSettings(getApplicationContext(), new JSONArray().put(configJSON));
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
        return (uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH);
    }

    /**
     * Identifies if the devices is enrolled in a study. We use the latest entry in the study table and check if the participant is still enrolled
     *
     * @param c
     * @return
     */
    public static boolean isStudy(Context c) {
        boolean participant = false;
        Cursor study = c.getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null, null, null,
                Aware_Provider.Aware_Studies.STUDY_TIMESTAMP + " DESC LIMIT 1");

        if (study != null && study.moveToFirst()) {
            participant = (study.getDouble(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_EXIT)) == 0
                    && study.getDouble(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)) > 0); //joined and still enrolled
        }
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
     * Fetch the cursor for a study, given the study URL, that is still enrolled
     *
     * @param c
     * @param study_url
     * @return
     */
    public static Cursor getStudy(Context c, String study_url) {
        return c.getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null, Aware_Provider.Aware_Studies.STUDY_URL + " LIKE '" + study_url + "%' AND " + Aware_Provider.Aware_Studies.STUDY_EXIT + "=0", null, Aware_Provider.Aware_Studies.STUDY_TIMESTAMP + " DESC LIMIT 1");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && PermissionChecker.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED) {
            return START_STICKY;
        }

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
            if (getApplicationContext().getPackageName().equals("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                try {
                    Scheduler.Schedule watchdog = Scheduler.getSchedule(this, SCHEDULE_KEEP_ALIVE);
                    if (watchdog == null) {
                        watchdog = new Scheduler.Schedule(SCHEDULE_KEEP_ALIVE);
                        watchdog.setInterval(getApplicationContext().getResources().getInteger(R.integer.keep_alive_interval_min))
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
            if ((getPackageName().equals("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone)) && isStudy(getApplicationContext())) {
                try {
                    Scheduler.Schedule compliance = Scheduler.getSchedule(this, Aware.SCHEDULE_STUDY_COMPLIANCE);
                    if (compliance == null) {
                        compliance = new Scheduler.Schedule(Aware.SCHEDULE_STUDY_COMPLIANCE);
                        compliance.setInterval(getResources().getInteger(R.integer.study_check_interval_min))
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
                if (intent.getAction().equalsIgnoreCase(ACTION_AWARE_STUDY_COMPLIANCE)) {
                    complianceStatus(getApplicationContext());
                    checkBatteryLeft(getApplicationContext(), false);

                    if (studyCheck == null && Aware.isStudy(getApplicationContext())) {
                        studyCheck = new AsyncStudyCheck();
                        studyCheck.execute();
                    }
                }

                if (intent.getAction().equalsIgnoreCase(ACTION_AWARE_KEEP_ALIVE)) {
                    startAWARE(getApplicationContext());
                    startPlugins(getApplicationContext());
                }

            } else {
                startAWARE(getApplicationContext());
                startPlugins(getApplicationContext());
            }

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Aware_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Aware_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Aware_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }

        } else { //storage is not available, stop plugins and sensors
            stopAWARE(getApplicationContext());
            stopPlugins(getApplicationContext());
        }

        return START_STICKY;
    }

    public static void checkBatteryLeft(Context context, boolean dismiss) {

        if (Aware.getSetting(context, Aware_Preferences.REMIND_TO_CHARGE).equals("true")) {

            final int CHARGE_REMINDER = 5555;
            NotificationManager notManager = (NotificationManager) context.getApplicationContext().getSystemService(NOTIFICATION_SERVICE);

            if (dismiss) {
                notManager.cancel(CHARGE_REMINDER);
            } else {
                Intent batt = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batt != null && batt.getExtras() != null) {
                    Bundle extras = batt.getExtras();
                    if (extras.getInt(BatteryManager.EXTRA_LEVEL) <= 15 && extras.getInt(BatteryManager.EXTRA_STATUS) != BatteryManager.BATTERY_STATUS_CHARGING) {
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context.getApplicationContext(), Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);
                        mBuilder.setSmallIcon(R.drawable.ic_stat_aware_recharge);
                        mBuilder.setContentTitle(context.getApplicationContext().getResources().getString(R.string.app_name));
                        mBuilder.setContentText(context.getApplicationContext().getText(R.string.aware_battery_recharge));
                        mBuilder.setAutoCancel(true);
                        mBuilder.setOnlyAlertOnce(true); //notify the user only once
                        mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
                        mBuilder = Aware.setNotificationProperties(mBuilder, AWARE_NOTIFICATION_IMPORTANCE_GENERAL);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            mBuilder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);

                        PendingIntent clickIntent = PendingIntent.getActivity(context.getApplicationContext(), 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
                        mBuilder.setContentIntent(clickIntent);

                        notManager.notify(CHARGE_REMINDER, mBuilder.build());
                    }
                }
            }
        }
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

            PluginsManager.disablePlugin(context, package_name);

            if (context.getPackageName().equals("com.aware.phone") || context.getResources().getBoolean(R.bool.standalone)) {
                context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
            }

            ComponentName componentName;
            if (packageInfo.versionName.equals("bundled")) {
                componentName = new ComponentName(context.getPackageName(), package_name + ".Plugin");
                if (Aware.DEBUG) Log.d(Aware.TAG, "Stopping bundled: " + componentName.toString());
            } else {
                componentName = new ComponentName(package_name, package_name + ".Plugin");
                if (Aware.DEBUG) Log.d(Aware.TAG, "Stopping external: " + componentName.toString());
            }

            Intent pluginIntent = new Intent();
            pluginIntent.setComponent(componentName);
            context.stopService(pluginIntent);
        }
    }

    /**
     * Starts a plugin using it's package name, both for bundled and unbundled plugins
     *
     * @param context
     * @param package_name
     */
    public synchronized static void startPlugin(final Context context, final String package_name) {
        PackageInfo packageInfo = PluginsManager.isInstalled(context, package_name);
        if (packageInfo != null) {

            PluginsManager.enablePlugin(context, package_name);

            if (context.getPackageName().equals("com.aware.phone") || context.getResources().getBoolean(R.bool.standalone)) {
                context.sendBroadcast(new Intent(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)); //sync the Plugins Manager UI for running statuses
            }

            ComponentName componentName = null;
            if (packageInfo.versionName.equals("bundled")) {
                componentName = new ComponentName(context.getPackageName(), package_name + ".Plugin");
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Initializing bundled: " + componentName.toString());
            } else {
                componentName = new ComponentName(package_name, package_name + ".Plugin");
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Initializing external: " + componentName.toString());
            }

            Intent pluginIntent = new Intent();
            pluginIntent.setComponent(componentName);
            componentName = context.startService(pluginIntent);

            //Try Kotlin compatibility
            if (componentName == null) {
                if (packageInfo.versionName.equals("bundled")) {
                    componentName = new ComponentName(context.getPackageName(), package_name + ".PluginKt");
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Initializing bundled: " + componentName.toString());
                } else {
                    componentName = new ComponentName(package_name, package_name + ".PluginKt");
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Initializing external: " + componentName.toString());
                }

                pluginIntent = new Intent();
                pluginIntent.setComponent(componentName);
                context.startService(pluginIntent);
            }
        }
    }

    private static boolean is_running(Context context, String package_name) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (package_name.equals(service.service.getPackageName()))
                return true;
        }
        return false;
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
        if (study_custom_url != null) pluginIntent.putExtra("study_url", study_custom_url);

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

        try {
            String contextCardClass = ((is_bundled) ? context.getPackageName() + "/" + package_name : package_name) + ".ContextCard";
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
                            open_settings.setComponent(new ComponentName(((is_bundled) ? context.getPackageName() : package_name), package_name + ".Settings"));
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
            context.getApplicationContext().getContentResolver().update(Aware_Provider.Aware_Device.CONTENT_URI, newLabel, Aware_Provider.Aware_Device.DEVICE_ID + " LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + "'", null);
        }

        ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        if (is_global) {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, "com.aware.phone");
        } else {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, context.getPackageName());
        }

        Cursor qry = context.getApplicationContext().getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + ((is_global) ? "'com.aware.phone'" : "'" + context.getPackageName() + "'"), null, null);
        //update
        if (qry != null && qry.moveToFirst()) {
            try {
                if (!qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE)).equals(value.toString())) {
                    context.getApplicationContext().getContentResolver().update(Aware_Settings.CONTENT_URI, setting, Aware_Settings.SETTING_ID + "=" + qry.getInt(qry.getColumnIndex(Aware_Settings.SETTING_ID)), null);
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
                context.getApplicationContext().getContentResolver().insert(Aware_Settings.CONTENT_URI, setting);
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
                ArrayList<JSONArray> sensorSync = sensorDiff(c, sensors, localSensors); //check sensors first
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
                            JSONObject toEnable = enabled.getJSONObject(i);
                            JSONArray localSensorsConfig = localConfig.getJSONObject(0).getJSONArray("sensors");
                            // First, do we need to replace an existing config value?
                            boolean isModification = false;
                            for (int j = 0; j < localSensorsConfig.length(); j++) {
                                if (localSensorsConfig.getJSONObject(j).getString("setting").equalsIgnoreCase(toEnable.getString("setting"))) {
                                    localSensorsConfig.put(j, toEnable);
                                    isModification = true;
                                    break;
                                }
                            }
                            // Add a new config value to the array.
                            if (!isModification) {
                                localSensorsConfig.put(toEnable);
                            }
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

        //Set schedulers
        if (schedulers.length() > 0)
            Scheduler.setSchedules(c, schedulers);

        if (config_changed) {
            ContentValues newCfg = new ContentValues();
            newCfg.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, localConfig.toString());
            c.getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, newCfg, Aware_Provider.Aware_Studies._ID + "=" + study_id, null);

            Intent aware = new Intent(c, Aware.class);
            c.startService(aware);
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
    private static ArrayList<JSONArray> sensorDiff(Context context, JSONArray server, JSONArray local) throws JSONException {

        SensorManager manager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
        Hashtable<Integer, Boolean> listSensorType = new Hashtable<>();
        for (int i = 0; i < sensors.size(); i++) {
            listSensorType.put(sensors.get(i).getType(), true);
        }

        Hashtable<String, Integer> optionalSensors = new Hashtable<>();
        optionalSensors.put(Aware_Preferences.STATUS_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER);
        optionalSensors.put(Aware_Preferences.STATUS_SIGNIFICANT_MOTION, Sensor.TYPE_ACCELEROMETER);
        optionalSensors.put(Aware_Preferences.STATUS_BAROMETER, Sensor.TYPE_PRESSURE);
        optionalSensors.put(Aware_Preferences.STATUS_GRAVITY, Sensor.TYPE_GRAVITY);
        optionalSensors.put(Aware_Preferences.STATUS_GYROSCOPE, Sensor.TYPE_GYROSCOPE);
        optionalSensors.put(Aware_Preferences.STATUS_LIGHT, Sensor.TYPE_LIGHT);
        optionalSensors.put(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION);
        optionalSensors.put(Aware_Preferences.STATUS_MAGNETOMETER, Sensor.TYPE_MAGNETIC_FIELD);
        optionalSensors.put(Aware_Preferences.STATUS_PROXIMITY, Sensor.TYPE_PROXIMITY);
        optionalSensors.put(Aware_Preferences.STATUS_ROTATION, Sensor.TYPE_ROTATION_VECTOR);
        optionalSensors.put(Aware_Preferences.STATUS_TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE);

        Set<String> keys = optionalSensors.keySet();

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

            for (String optionalSensor : keys) {
                if (server_sensor.getString("setting").equalsIgnoreCase(optionalSensor) && !listSensorType.containsKey(optionalSensors.get(optionalSensor)))
                    continue;
            }

            if (immutable_settings.contains(server_sensor.getString("setting"))) {
                continue; //don't do anything
            }

            boolean is_present = false;
            for (int j = 0; j < local.length(); j++) {
                JSONObject local_sensor = local.getJSONObject(j);

                if (local_sensor.getString("setting").equalsIgnoreCase(server_sensor.getString("setting"))
                        && local_sensor.getString("value").equals(server_sensor.getString("value"))) {
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

            for (String optionalSensor : keys) {
                if (local_sensor.getString("setting").equalsIgnoreCase(optionalSensor) && !listSensorType.containsKey(optionalSensors.get(optionalSensor)))
                    continue;
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

            if (path_segments.size() > 0) {
                String study_api_key = path_segments.get(path_segments.size() - 1);
                String study_id = path_segments.get(path_segments.size() - 2);

                String request;
                if (protocol.equals("https")) {
                    SSLManager.handleUrl(getApplicationContext(), full_url, true);

                    while (!SSLManager.hasCertificate(getApplicationContext(), study_uri.getHost())) {
                        //wait until we have the certificate downloaded
                    }

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

                            if (Aware.DEBUG)
                                Log.d(Aware.TAG, "New study data: " + studyData.toString());

                        } else {
                            ContentValues studyData = new ContentValues();
                            studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                            studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                            studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                            studyData.put(Aware_Provider.Aware_Studies.STUDY_EXIT, 0);
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
                                String package_name = "com.aware.phone";
                                if (getApplicationContext().getResources().getBoolean(R.bool.standalone))
                                    package_name = getApplicationContext().getPackageName();
                                Aware.setSetting(getApplicationContext(), sensor_config.getString("setting"), sensor_config.get("value"), package_name);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        //Set the plugins' settings now
                        ArrayList<String> enabled_plugins = new ArrayList<>();
                        for (int i = 0; i < plugins.length(); i++) {
                            try {
                                JSONObject plugin_config = plugins.getJSONObject(i);

                                String package_name = plugin_config.getString("plugin");
                                JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                                for (int j = 0; j < plugin_settings.length(); j++) {
                                    JSONObject plugin_setting = plugin_settings.getJSONObject(j);
                                    if (getApplicationContext().getResources().getBoolean(R.bool.standalone))
                                        package_name = getApplicationContext().getPackageName();
                                    Aware.setSetting(getApplicationContext(), plugin_setting.getString("setting"), plugin_setting.get("value"), package_name);
                                    if (plugin_setting.getString("setting").contains("status_") && plugin_setting.get("value").equals("true")) {
                                        enabled_plugins.add(package_name);
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        //Set schedulers
                        if (schedulers.length() > 0)
                            Scheduler.setSchedules(getApplicationContext(), schedulers);

                        //Start enabled plugins
                        for (String package_name : enabled_plugins) {
                            PackageInfo installed = PluginsManager.isInstalled(getApplicationContext(), package_name);
                            if (installed != null) {
                                Aware.startPlugin(getApplicationContext(), package_name);
                            } else {
                                Aware.downloadPlugin(getApplicationContext(), package_name, null, false);
                            }
                        }

                        resetLogs(getApplicationContext());

                        //Let others know that we just joined a study
                        sendBroadcast(new Intent(Aware.ACTION_JOINED_STUDY));

                        //Send data to server
                        Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
                        sendBroadcast(sync);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        IS_CORE_RUNNING = false;

        try {
            unregisterReceiver(aware_BR);
            unregisterReceiver(storage_BR);
            unregisterReceiver(awareBoot);
            unregisterReceiver(foregroundMgr);
            unregisterReceiver(schedulerTicker);
        } catch (IllegalArgumentException e) {
            //There is no API to check if a broadcast receiver already is registered. Since Aware.java is shared across plugins, the receiver is only registered on the client, not the plugins.
        }
    }

    public static void reset(Context context) {
        String device_id = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        String device_label = Aware.getSetting(context, Aware_Preferences.DEVICE_LABEL);

        //Remove all settings
        context.getContentResolver().delete(Aware_Settings.CONTENT_URI, null, null);

        //Remove all schedulers
        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, null);

        //Read default client settings
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(context.getApplicationContext().getPackageName(), Context.MODE_PRIVATE);
        PreferenceManager.setDefaultValues(context.getApplicationContext(), context.getApplicationContext().getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
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

        Intent aware = new Intent(context, Aware.class);
        context.startService(aware);
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
                    if (current_status != null && !current_status.isClosed())
                        current_status.close();

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
     * Aware#ACTION_AWARE_ACTION_QUIT_STUDY: quits a study
     * Aware#ACTION_AWARE_SYNC_DATA: send the data remotely
     *
     * @author denzil
     */
    public static final Aware_Broadcaster aware_BR = new Aware_Broadcaster();

    public static class Aware_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!(context.getPackageName().equals("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone)))
                return;

            if (intent.getAction().equals(Aware.ACTION_QUIT_STUDY)) {
                Aware.reset(context);
            }
            if (intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA)) {
                Bundle sync = new Bundle();
                sync.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                sync.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(Aware.getAWAREAccount(context), Aware_Provider.getAuthority(context), sync);
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
                Intent aware = new Intent(context, Aware.class);
                context.startService(aware);
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
            boolean logging = false;
            if ((context.getPackageName().equalsIgnoreCase("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone))) {
                logging = true;
            }

            if (logging) {
                try {
                    //Retrieve phone battery info
                    ContentValues rowData = new ContentValues();
                    Intent batt = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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

                    //Remove charging reminder if previously visible
                    if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED) && Aware.getSetting(context, Aware_Preferences.REMIND_TO_CHARGE).equalsIgnoreCase("true")) {
                        if (extras.getInt(BatteryManager.EXTRA_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING) {
                            checkBatteryLeft(context, true);
                        }
                    }
                    if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
                        Aware.debug(context, "phone: on");
                        rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_BOOTED);

                        Intent aware = new Intent(context, Aware.class);
                        context.startService(aware);
                        // Start the foreground service only if it's the client or a standalone application
                        if ((context.getPackageName().equals("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone)))
                            context.sendBroadcast(new Intent(Aware.ACTION_AWARE_PRIORITY_FOREGROUND));
                    }
                    if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
                        Aware.debug(context, "phone: off");
                        rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_SHUTDOWN);
                    }
                    if (intent.getAction().equalsIgnoreCase(Intent.ACTION_REBOOT)) {
                        Aware.debug(context, "phone: reboot");
                        rowData.put(Battery_Provider.Battery_Data.STATUS, Battery.STATUS_PHONE_REBOOT);
                    }
                    if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)
                            || intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)
                            || intent.getAction().equalsIgnoreCase(Intent.ACTION_REBOOT)) {
                        try {
                            if (Aware.DEBUG) Log.d(TAG, "Battery: " + rowData.toString());
                            context.getContentResolver().insert(Battery_Provider.Battery_Data.CONTENT_URI, rowData);
                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }
                    }
                } catch (RuntimeException e) {
                    //Gingerbread does not allow these intents. Disregard for 2.3.3
                }
            }

            //Guarantees that all plugins also come back up again on reboot
            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
                Intent aware = new Intent(context, Aware.class);
                context.startService(aware);
            }
        }
    }

//    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
//        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//            if (serviceClass.getName().equals(service.service.getClassName())) {
//                return true;
//            }
//        }
//        return false;
//    }

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

            boolean airplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;

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
     * Start core and active services
     */
    public static void startAWARE(Context context) {

        startScheduler(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_SIGNIFICANT_MOTION).equals("true")) {
            startSignificant(context);
        } else stopSignificant(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ESM).equals("true")) {
            startESM(context);
        } else stopESM(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_ACCELEROMETER).equals("true")) {
            startAccelerometer(context);
        } else stopAccelerometer(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations(context);
        } else stopInstallations(context);

        if (Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_GPS).equals("true")
                || Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")
                || Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_PASSIVE).equals("true")) {
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

        if (Aware.getSetting(context, Aware_Preferences.STATUS_WEBSOCKET).equals("true")) {
            startWebsocket(context);
        } else stopWebsocket(context);
    }

    public static void startPlugins(Context context) {
        try {
            if (context.getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                ArrayList<String> active_plugins = new ArrayList<>();
                Cursor enabled_plugins = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
                if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
                    do {
                        String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                        active_plugins.add(package_name);
                    } while (enabled_plugins.moveToNext());
                }
                if (enabled_plugins != null && !enabled_plugins.isClosed())
                    enabled_plugins.close();

                if (active_plugins.size() > 0) {
                    for (String package_name : active_plugins) {
                        startPlugin(context, package_name);
                    }
                }
            }
        } catch (Exception e) {
            // Write to file
            File logFile = new File("sdcard/log_aware.file");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
                buf.append("text");
                buf.newLine();
                buf.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public static void stopPlugins(Context context) {
        if (context.getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || context.getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
            ArrayList<String> active_plugins = new ArrayList<>();
            Cursor enabled_plugins = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if (enabled_plugins != null && enabled_plugins.moveToFirst()) {
                do {
                    String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    active_plugins.add(package_name);
                } while (enabled_plugins.moveToNext());
            }
            if (enabled_plugins != null && !enabled_plugins.isClosed())
                enabled_plugins.close();

            if (active_plugins.size() > 0) {
                for (String package_name : active_plugins) {
                    stopPlugin(context, package_name);
                }
            }
        }
    }

    /**
     * Checks if a specific sync adapter is enabled or not
     *
     * @param authority
     * @returns
     */
    public static boolean isSyncEnabled(Context context, String authority) {
        Account aware = Aware.getAWAREAccount(context);
        boolean isAutoSynchable = ContentResolver.getSyncAutomatically(aware, authority);
        boolean isSynchable = (ContentResolver.getIsSyncable(aware, authority) > 0);
        boolean isMasterSyncEnabled = ContentResolver.getMasterSyncAutomatically();
        List<PeriodicSync> periodicSyncs = ContentResolver.getPeriodicSyncs(aware, authority);

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Sync-Adapter Authority: " + authority + " syncable: " + isSynchable + " auto: " + isAutoSynchable + " Periodic: " + !periodicSyncs.isEmpty() + " global: " + isMasterSyncEnabled);
        for (PeriodicSync p : periodicSyncs) {
            if (Aware.DEBUG) Log.d(Aware.TAG, "Every: " + p.period / 60 + " minutes");
        }
        return isSynchable && isAutoSynchable && isMasterSyncEnabled;
    }

    /**
     * Stop all services
     *
     * @param context
     */
    public static void stopAWARE(Context context) {
        if (context == null) return;

        Intent aware = new Intent(context, Aware.class);
        context.stopService(aware);

        stopSignificant(context);
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
        stopWebsocket(context);
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
        if (!Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_GPS).equals("true")
                && !Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")
                && !Aware.getSetting(context, Aware_Preferences.STATUS_LOCATION_PASSIVE).equals("true")) {
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

    /**
     * Start EventBus module
     * @param context
     */
    public static void startWebsocket(Context context) {
        if(context == null) return;
        if(websocket == null) websocket = new Intent(context, Websocket.class);
        context.startService(websocket);
    }

    public static void stopWebsocket(Context context) {
        if (context == null) return;
        if (websocket != null) context.stopService(websocket);
    }
}
