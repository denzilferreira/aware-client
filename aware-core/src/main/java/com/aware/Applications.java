
package com.aware;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessErrorStateInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.accessibilityservice.AccessibilityServiceInfoCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.aware.providers.Applications_Provider;
import com.aware.providers.Applications_Provider.Applications_Crashes;
import com.aware.providers.Applications_Provider.Applications_Foreground;
import com.aware.providers.Applications_Provider.Applications_History;
import com.aware.providers.Applications_Provider.Applications_Notifications;
import com.aware.providers.Keyboard_Provider;
import com.aware.utils.Encrypter;
import com.aware.utils.WebserviceHelper;

import java.util.Iterator;
import java.util.List;

/**
 * Service that logs application usage on the device.
 * Updates every time the user changes application or accesses a sub activity on the screen.
 * - ACTION_AWARE_APPLICATIONS_FOREGROUND: new application on the screen
 * - ACTION_AWARE_APPLICATIONS_HISTORY: applications running was just updated
 * - ACTION_AWARE_APPLICATIONS_NOTIFICATIONS: new notification received
 * - ACTION_AWARE_APPLICATIONS_CRASHES: an application crashed, error and ANR conditions
 *
 * @author denzil
 */
public class Applications extends AccessibilityService {

    private static String TAG = "AWARE::Applications";

    private static AlarmManager alarmManager = null;
    private static Intent updateApps = null;
    private static PendingIntent repeatingIntent = null;
    public static final int ACCESSIBILITY_NOTIFICATION_ID = 42;

    public static final String STATUS_AWARE_ACCESSIBILITY = "STATUS_AWARE_ACCESSIBILITY";

    /**
     * Broadcasted event: a new application is visible on the foreground
     */
    public static final String ACTION_AWARE_APPLICATIONS_FOREGROUND = "ACTION_AWARE_APPLICATIONS_FOREGROUND";

    /**
     * Broadcasted event: new foreground and background statistics are available
     */
    public static final String ACTION_AWARE_APPLICATIONS_HISTORY = "ACTION_AWARE_APPLICATIONS_HISTORY";

    /**
     * Broadcasted event: new notification is available
     */
    public static final String ACTION_AWARE_APPLICATIONS_NOTIFICATIONS = "ACTION_AWARE_APPLICATIONS_NOTIFICATIONS";

    /**
     * Broadcasted event: application just crashed
     */
    public static final String ACTION_AWARE_APPLICATIONS_CRASHES = "ACTION_AWARE_APPLICATIONS_CRASHES";

    public static final String EXTRA_DATA = "data";

    private static int FREQUENCY = -1;

    /**
     * Given a package name, get application label in the default language of the device
     * @param package_name
     * @return appName
     */
    private String getApplicationName(String package_name) {
        PackageManager packageManager = getPackageManager();
        ApplicationInfo appInfo;
        try {
            appInfo = packageManager.getApplicationInfo(package_name, PackageManager.GET_META_DATA);
        } catch (final NameNotFoundException e) {
            appInfo = null;
        }
        String appName = "";
        if (appInfo != null && packageManager.getApplicationLabel(appInfo) != null) {
            appName = (String) packageManager.getApplicationLabel(appInfo);
        }
        return appName;
    }

    /**
     * Monitors for events of:
     * {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}
     * {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED}
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event.getPackageName() == null) return;

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_NOTIFICATIONS).equals("true") && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {

            Notification notificationDetails = (Notification) event.getParcelableData();

            if (notificationDetails != null) {
                ContentValues rowData = new ContentValues();
                rowData.put(Applications_Notifications.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                rowData.put(Applications_Notifications.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Applications_Notifications.PACKAGE_NAME, event.getPackageName().toString());
                rowData.put(Applications_Notifications.APPLICATION_NAME, getApplicationName(event.getPackageName().toString()));
                rowData.put(Applications_Notifications.TEXT, Encrypter.hash(getApplicationContext(), event.getText().toString()));
                rowData.put(Applications_Notifications.SOUND, ((notificationDetails.sound != null) ? notificationDetails.sound.toString() : ""));
                rowData.put(Applications_Notifications.VIBRATE, ((notificationDetails.vibrate != null) ? notificationDetails.vibrate.toString() : ""));
                rowData.put(Applications_Notifications.DEFAULTS, notificationDetails.defaults);
                rowData.put(Applications_Notifications.FLAGS, notificationDetails.flags);

                if (Aware.DEBUG) Log.d(TAG, "New notification:" + rowData.toString());

                getContentResolver().insert(Applications_Notifications.CONTENT_URI, rowData);
                Intent notification = new Intent(ACTION_AWARE_APPLICATIONS_NOTIFICATIONS);
                notification.putExtra(EXTRA_DATA, rowData);
                sendBroadcast(notification);
            }
        }

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true") && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            PackageManager packageManager = getPackageManager();

            //Fixed: Window State Changed from the same application (showing keyboard within an app) should be ignored
            boolean same_app = false;
            Cursor last_foreground = getContentResolver().query(Applications_Foreground.CONTENT_URI, null, null, null, Applications_Foreground.TIMESTAMP + " DESC LIMIT 1");
            if (last_foreground != null && last_foreground.moveToFirst()) {
                if (last_foreground.getString(last_foreground.getColumnIndex(Applications_Foreground.PACKAGE_NAME)).equals(event.getPackageName())) {
                    same_app = true;
                }
            }
            if (last_foreground != null && !last_foreground.isClosed()) last_foreground.close();

            if (!same_app) {
                ApplicationInfo appInfo;
                try {
                    appInfo = packageManager.getApplicationInfo(event.getPackageName().toString(), PackageManager.GET_META_DATA);
                } catch (NameNotFoundException | NullPointerException | Resources.NotFoundException e) {
                    appInfo = null;
                }

                PackageInfo pkgInfo;
                try {
                    pkgInfo = packageManager.getPackageInfo(event.getPackageName().toString(), PackageManager.GET_META_DATA);
                } catch (NameNotFoundException | NullPointerException | Resources.NotFoundException e) {
                    pkgInfo = null;
                }

                String appName = "";
                try {
                    if (appInfo != null) {
                        appName = packageManager.getApplicationLabel(appInfo).toString();
                    }
                } catch (Resources.NotFoundException | NullPointerException e) {
                    appName = "";
                }

                ContentValues rowData = new ContentValues();
                rowData.put(Applications_Foreground.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Applications_Foreground.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                rowData.put(Applications_Foreground.PACKAGE_NAME, event.getPackageName().toString());
                rowData.put(Applications_Foreground.APPLICATION_NAME, appName);
                rowData.put(Applications_Foreground.IS_SYSTEM_APP, pkgInfo != null && isSystemPackage(pkgInfo));

                if (Aware.DEBUG) Log.d(TAG, "FOREGROUND: " + rowData.toString());

                try {
                    getContentResolver().insert(Applications_Foreground.CONTENT_URI, rowData);
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                Intent newForeground = new Intent(ACTION_AWARE_APPLICATIONS_FOREGROUND);
                newForeground.putExtra(EXTRA_DATA, rowData);
                sendBroadcast(newForeground);
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CRASHES).equals("true")) {
                //Check if there is a crashed application
                ActivityManager activityMng = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                List<ProcessErrorStateInfo> errors = activityMng.getProcessesInErrorState();
                if (errors != null) {

                    Iterator<ActivityManager.ProcessErrorStateInfo> iter = errors.iterator();
                    while (iter.hasNext()) {

                        ActivityManager.ProcessErrorStateInfo error = iter.next();

                        try {
                            PackageInfo pkgInfo = packageManager.getPackageInfo(error.processName, PackageManager.GET_META_DATA);
                            ApplicationInfo appInfo = packageManager.getApplicationInfo(event.getPackageName().toString(), PackageManager.GET_META_DATA);
                            String appName = (appInfo != null) ? (String) packageManager.getApplicationLabel(appInfo) : "";

                            ContentValues crashData = new ContentValues();
                            crashData.put(Applications_Crashes.TIMESTAMP, System.currentTimeMillis());
                            crashData.put(Applications_Crashes.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                            crashData.put(Applications_Crashes.PACKAGE_NAME, error.processName);
                            crashData.put(Applications_Crashes.APPLICATION_NAME, appName);
                            crashData.put(Applications_Crashes.APPLICATION_VERSION, (pkgInfo != null) ? pkgInfo.versionCode : -1); //some prepackages don't have version codes...
                            crashData.put(Applications_Crashes.ERROR_SHORT, error.shortMsg);

                            String error_long = "";
                            error_long += error.longMsg + "\nStack:\n";
                            error_long += (error.stackTrace != null) ? error.stackTrace : "";

                            crashData.put(Applications_Crashes.ERROR_LONG, error_long);
                            crashData.put(Applications_Crashes.ERROR_CONDITION, error.condition);
                            crashData.put(Applications_Crashes.IS_SYSTEM_APP, pkgInfo != null && isSystemPackage(pkgInfo));

                            getContentResolver().insert(Applications_Crashes.CONTENT_URI, crashData);

                            if (Aware.DEBUG) Log.d(TAG, "Crashed: " + crashData.toString());

                            Intent crashed = new Intent(ACTION_AWARE_APPLICATIONS_CRASHES);
                            crashed.putExtra(EXTRA_DATA, crashData);
                            sendBroadcast(crashed);
                        } catch (NameNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            Intent backgroundService = new Intent(this, BackgroundService.class);
            backgroundService.setAction(ACTION_AWARE_APPLICATIONS_HISTORY);
            startService(backgroundService);
        }

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_KEYBOARD).equals("true") && event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            ContentValues keyboard = new ContentValues();
            keyboard.put(Keyboard_Provider.Keyboard_Data.TIMESTAMP, System.currentTimeMillis());
            keyboard.put(Keyboard_Provider.Keyboard_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            keyboard.put(Keyboard_Provider.Keyboard_Data.PACKAGE_NAME, (String) event.getPackageName());
            keyboard.put(Keyboard_Provider.Keyboard_Data.BEFORE_TEXT, (String) event.getBeforeText());
            keyboard.put(Keyboard_Provider.Keyboard_Data.CURRENT_TEXT, event.getText().toString());
            keyboard.put(Keyboard_Provider.Keyboard_Data.IS_PASSWORD, event.isPassword());

            getContentResolver().insert(Keyboard_Provider.Keyboard_Data.CONTENT_URI, keyboard);

            if (Aware.DEBUG) Log.d(TAG, "Keyboard: " + keyboard.toString());

            Intent keyboard_data = new Intent(Keyboard.ACTION_AWARE_KEYBOARD);
            sendBroadcast(keyboard_data);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        updateApps = new Intent(getApplicationContext(), BackgroundService.class);
        updateApps.setAction(ACTION_AWARE_APPLICATIONS_HISTORY);
        repeatingIntent = PendingIntent.getService(getApplicationContext(), 0, updateApps, PendingIntent.FLAG_UPDATE_CURRENT);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        registerReceiver(awareMonitor, filter);

        Aware.debug(this, "created: " + getClass().getName());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (Aware.DEBUG) Log.d(Aware.TAG, "Aware service connected to accessibility services...");

        //This makes sure that plugins and apps can check if the accessibility service is active
        Aware.setSetting(this, Applications.STATUS_AWARE_ACCESSIBILITY, true);

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        registerReceiver(awareMonitor, filter);

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS).length() == 0) {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS, 30);
        }

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS))) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS)) * 1000, repeatingIntent);
                FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS));
                if (Aware.DEBUG) Log.d(TAG, "Applications Background: " + FREQUENCY + "s check");
            }
        }

        //Retro-compatibility with Gingerbread
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 50;
            info.packageNames = null;
            this.setServiceInfo(info);
        }

        //Boot-up AWARE framework
        Intent aware = new Intent(this, Aware.class);
        startService(aware);
    }

    @Override
    public void onInterrupt() {
        if (Aware.getSetting(getApplicationContext(), Applications.STATUS_AWARE_ACCESSIBILITY).equals("true")) {
            if(awareMonitor != null) {
                unregisterReceiver(awareMonitor);
            }
        }
        Log.w(TAG, "Accessibility Service has been interrupted...");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (Aware.getSetting(getApplicationContext(), Applications.STATUS_AWARE_ACCESSIBILITY).equals("true")) {
            if(awareMonitor != null) {
                unregisterReceiver(awareMonitor);
            }
        }
        Aware.setSetting(this, Applications.STATUS_AWARE_ACCESSIBILITY, false);
        Log.e(TAG, "Accessibility Service has been unbound...");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS).length() == 0) {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS, 30);
        }

        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS))) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS)) * 1000, repeatingIntent);
                FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS));
            }

            if (Aware.DEBUG) Log.d(TAG, "Applications Background: " + FREQUENCY + "s check");
        }

        Aware.debug(this, "active: " + getClass().getName());

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        alarmManager.cancel(repeatingIntent);
        try {
            if(awareMonitor != null) {
                unregisterReceiver(awareMonitor);
            }
        } catch (IllegalArgumentException e) {}

        Aware.debug(this, "destroyed: " + getClass().getName());
    }

    private synchronized static boolean isAccessibilityEnabled(Context c) {
        boolean enabled = false;

        AccessibilityManager accessibilityManager = (AccessibilityManager) c.getSystemService(ACCESSIBILITY_SERVICE);

        //Try to fetch active accessibility services directly from Android OS database instead of broken API...
        String settingValue = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (settingValue != null) {
            if (Aware.DEBUG) Log.d("ACCESSIBILITY", "Settings secure: " + settingValue);
            if (settingValue.contains(c.getPackageName())) {
                enabled = true;
            }
        }
        if (!enabled) {
            try {
                List<AccessibilityServiceInfo> enabledServices = AccessibilityManagerCompat.getEnabledAccessibilityServiceList(accessibilityManager, AccessibilityEventCompat.TYPES_ALL_MASK);
                if (!enabledServices.isEmpty()) {
                    for (AccessibilityServiceInfo service : enabledServices) {
                        if (Aware.DEBUG) Log.d("ACCESSIBILITY", "AccessibilityManagerCompat enabled: " + service.toString());
                        if (service.getId().contains(c.getPackageName())) {
                            enabled = true;
                            break;
                        }
                    }
                }
            } catch (NoSuchMethodError e) {
            }
        }
        if (!enabled) {
            try {
                List<AccessibilityServiceInfo> enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
                if (!enabledServices.isEmpty()) {
                    for (AccessibilityServiceInfo service : enabledServices) {
                        if (Aware.DEBUG) Log.d("ACCESSIBILITY", "AccessibilityManager enabled: " + service.toString());
                        if (service.getId().contains(c.getPackageName())) {
                            enabled = true;
                            break;
                        }
                    }
                }
            } catch (NoSuchMethodError e) {
            }
        }

        //Keep the global setting up-to-date
        Aware.setSetting(c, Applications.STATUS_AWARE_ACCESSIBILITY, enabled, "com.aware.phone");

        return enabled;
    }

    /**
     * Check if the accessibility service for AWARE Aware is active
     *
     * @return boolean isActive
     */
    public static boolean isAccessibilityServiceActive(Context c) {
        if (!isAccessibilityEnabled(c)) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(c);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_accessibility);
            mBuilder.setContentTitle("Please enable AWARE");
            mBuilder.setContentText(c.getResources().getString(R.string.aware_activate_accessibility));
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);

            Intent accessibilitySettings = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilitySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent clickIntent = PendingIntent.getActivity(c, 0, accessibilitySettings, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);
            NotificationManager notManager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(Applications.ACCESSIBILITY_NOTIFICATION_ID, mBuilder.build());
            return false;
        }
        return true;
    }

    /**
     * Received AWARE broadcasts
     * - ACTION_AWARE_SYNC_DATA
     * - ACTION_AWARE_CLEAR_DATA
     *
     * @author df
     */
    public class Applications_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            String[] DATABASE_TABLES = Applications_Provider.DATABASE_TABLES;
            String[] TABLES_FIELDS = Applications_Provider.TABLES_FIELDS;
            Uri[] CONTEXT_URIS = new Uri[]{Applications_Foreground.CONTENT_URI, Applications_History.CONTENT_URI, Applications_Notifications.CONTENT_URI, Applications_Crashes.CONTENT_URI};

            if (Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") && intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA)) {
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
                    //Clear locally
                    context.getContentResolver().delete(CONTEXT_URIS[i], null, null);
                    if (Aware.DEBUG) Log.d(TAG, "Cleared " + CONTEXT_URIS[i].toString());

                    //Clear remotely
                    if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
                        Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                        webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE);
                        webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                        context.startService(webserviceHelper);
                    }
                }
            }
        }
    }

    private final Applications_Broadcaster awareMonitor = new Applications_Broadcaster();

    /**
     * Applications background service
     * - Updates the current running applications statistics
     * - Uploads data to the webservice
     *
     * @author df
     */
    public static class BackgroundService extends IntentService {
        public BackgroundService() {
            super(TAG + " background service");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            //Updating list of running applications/services
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true") && intent.getAction().equals(ACTION_AWARE_APPLICATIONS_HISTORY)) {

                ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                PackageManager packageManager = getPackageManager();
                List<RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();

                if (runningApps == null) return;

                if (Aware.DEBUG) Log.d(TAG, "Running " + runningApps.size() + " applications");

                for (RunningAppProcessInfo app : runningApps) {
                    try {
                        PackageInfo appPkg = packageManager.getPackageInfo(app.processName, PackageManager.GET_META_DATA);
                        ApplicationInfo appInfo = packageManager.getApplicationInfo(app.processName, PackageManager.GET_META_DATA);

                        String appName = (appInfo != null) ? (String) packageManager.getApplicationLabel(appInfo) : "";

                        Cursor appUnclosed = getContentResolver().query(Applications_History.CONTENT_URI, null, Applications_History.PACKAGE_NAME + " LIKE '%" + app.processName + "%' AND " + Applications_History.PROCESS_ID + "=" + app.pid + " AND " + Applications_History.END_TIMESTAMP + "=0", null, null);
                        if (appUnclosed == null || !appUnclosed.moveToFirst()) {
                            ContentValues rowData = new ContentValues();
                            rowData.put(Applications_History.TIMESTAMP, System.currentTimeMillis());
                            rowData.put(Applications_History.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                            rowData.put(Applications_History.PACKAGE_NAME, app.processName);
                            rowData.put(Applications_History.APPLICATION_NAME, appName);
                            rowData.put(Applications_History.PROCESS_IMPORTANCE, app.importance);
                            rowData.put(Applications_History.PROCESS_ID, app.pid);
                            rowData.put(Applications_History.END_TIMESTAMP, 0);
                            rowData.put(Applications_History.IS_SYSTEM_APP, isSystemPackage(appPkg));
                            try {
                                getContentResolver().insert(Applications_History.CONTENT_URI, rowData);
                            } catch (SQLiteException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            } catch (SQLException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            }
                        } else if (appUnclosed.getInt(appUnclosed.getColumnIndex(Applications_History.PROCESS_IMPORTANCE)) != app.importance) {
                            //Close last importance
                            ContentValues rowData = new ContentValues();
                            rowData.put(Applications_History.END_TIMESTAMP, System.currentTimeMillis());
                            try {
                                getContentResolver().update(Applications_History.CONTENT_URI, rowData, Applications_History._ID + "=" + appUnclosed.getInt(appUnclosed.getColumnIndex(Applications_History._ID)), null);
                            } catch (SQLiteException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            } catch (SQLException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            }

                            if (!appUnclosed.isClosed()) appUnclosed.close();

                            //Insert new importance
                            rowData = new ContentValues();
                            rowData.put(Applications_History.TIMESTAMP, System.currentTimeMillis());
                            rowData.put(Applications_History.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                            rowData.put(Applications_History.PACKAGE_NAME, app.processName);
                            rowData.put(Applications_History.APPLICATION_NAME, appName);
                            rowData.put(Applications_History.PROCESS_IMPORTANCE, app.importance);
                            rowData.put(Applications_History.PROCESS_ID, app.pid);
                            rowData.put(Applications_History.END_TIMESTAMP, 0);
                            rowData.put(Applications_History.IS_SYSTEM_APP, isSystemPackage(appPkg));
                            try {
                                getContentResolver().insert(Applications_History.CONTENT_URI, rowData);
                            } catch (SQLiteException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            } catch (SQLException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            }
                        }
                        if (appUnclosed != null && !appUnclosed.isClosed()) appUnclosed.close();
                    } catch (PackageManager.NameNotFoundException | IllegalStateException | SQLiteException e) {
                        if (Aware.DEBUG) Log.e(TAG, e.toString());
                    }
                }

                //Close open applications that are not running anymore
                try {
                    Cursor appsOpened = getContentResolver().query(Applications_History.CONTENT_URI, null, Applications_History.END_TIMESTAMP + "=0", null, null);
                    if (appsOpened != null && appsOpened.moveToFirst()) {
                        do {
                            if (!exists(runningApps, appsOpened)) {
                                ContentValues rowData = new ContentValues();
                                rowData.put(Applications_History.END_TIMESTAMP, System.currentTimeMillis());
                                try {
                                    getContentResolver().update(Applications_History.CONTENT_URI, rowData, Applications_History._ID + "=" + appsOpened.getInt(appsOpened.getColumnIndex(Applications_History._ID)), null);
                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }
                        } while (appsOpened.moveToNext());
                    }
                    if (appsOpened != null && !appsOpened.isClosed()) appsOpened.close();
                } catch (IllegalStateException | SQLiteException e) {
                    if (Aware.DEBUG) Log.e(TAG, e.toString());
                }

                Intent statsUpdated = new Intent(ACTION_AWARE_APPLICATIONS_HISTORY);
                sendBroadcast(statsUpdated);
            }
        }

        /**
         * Check if the application on the database, exists on the running applications
         *
         * @param {@link List}<RunningAppProcessInfo> runningApps
         * @param {@link Cursor} row
         * @return boolean
         */
        private boolean exists(List<RunningAppProcessInfo> running, Cursor dbApp) {
            for (RunningAppProcessInfo app : running) {
                if (dbApp.getString(dbApp.getColumnIndexOrThrow(Applications_History.PACKAGE_NAME)).equals(app.processName) &&
                        dbApp.getInt(dbApp.getColumnIndexOrThrow(Applications_History.PROCESS_IMPORTANCE)) == app.importance &&
                        dbApp.getInt(dbApp.getColumnIndexOrThrow(Applications_History.PROCESS_ID)) == app.pid) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Check if a certain application is pre-installed or part of the operating system.
     *
     * @param {@link PackageInfo} obj
     * @return boolean
     */
    public static boolean isSystemPackage(PackageInfo pkgInfo) {
        return pkgInfo != null && ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1);
    }
}
