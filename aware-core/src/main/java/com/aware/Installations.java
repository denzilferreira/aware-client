
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncRequest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Applications_Provider.Applications_History;
import com.aware.providers.Installations_Provider;
import com.aware.providers.Installations_Provider.Installations_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Service that logs application installations on the device.
 * - ACTION_AWARE_APPLICATION_ADDED : new application installed
 * - ACTION_AWARE_APPLICATION_REMOVED: application removed
 * - ACTION_AWARE_APPLICATION_UPDATED: application updated
 *
 * @author denzil
 */

public class Installations extends Aware_Sensor {

    private static String TAG = "AWARE::Installations";

    /**
     * Broadcasted event: new application has been installed
     * Extra: package_name, application_name
     */
    public static final String ACTION_AWARE_APPLICATION_ADDED = "ACTION_AWARE_APPLICATION_ADDED";

    /**
     * Broadcasted event: an existing application has been removed
     * Extra: package_name, application_name
     */
    public static final String ACTION_AWARE_APPLICATION_REMOVED = "ACTION_AWARE_APPLICATION_REMOVED";

    /**
     * Broadcasted event: an existing application has been updated
     * Extra: package_name, application_name
     */
    public static final String ACTION_AWARE_APPLICATION_UPDATED = "ACTION_AWARE_APPLICATION_UPDATED";

    /**
     * The application package name of event
     * Broadcasted extra: package_name
     */
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    /**
     * The application's name of the event
     * Broadcasted extra: application_name
     */
    public static final String EXTRA_APPLICATION_NAME = "application_name";

    /**
     * Status for application removed = 0
     */
    public static final int STATUS_REMOVED = 0;

    /**
     * Status for application added = 1
     */
    public static final int STATUS_ADDED = 1;

    /**
     * Status for application updated = 2
     */
    public static final int STATUS_UPDATED = 2;

    private static String package_name;
    private static String application_name;
    private static String current_context;
    private static ContextProducer sContext_producer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Installations_Provider.getAuthority(this);

        CONTEXT_PRODUCER = new Aware_Sensor.ContextProducer() {
            @Override
            public void onContext() {
                Intent sharedContext = new Intent(current_context);
                sharedContext.putExtra(EXTRA_PACKAGE_NAME, package_name);
                sharedContext.putExtra(EXTRA_APPLICATION_NAME, application_name);
                sendBroadcast(sharedContext);
            }
        };
        sContext_producer = CONTEXT_PRODUCER;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(installationsMonitor, filter);

        if (Aware.DEBUG) Log.d(TAG, "Installations service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, true);

            if (Aware.DEBUG) Log.d(TAG, "Installations service active...");

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Installations_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Installations_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Installations_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(installationsMonitor);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Installations_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Installations_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Installations service terminated...");
    }

    /**
     * BroadcastReceiver for Installations module
     * - Monitor for changes in installations on the device:
     * {@link Intent#ACTION_PACKAGE_ADDED}
     * {@link Intent#ACTION_PACKAGE_REPLACED}
     * {@link Intent#ACTION_PACKAGE_REMOVED}
     *
     * @author denzil
     */
    public static class Packages_Monitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (Aware.getSetting(context, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {

                PackageManager packageManager = context.getPackageManager();

                Bundle extras = intent.getExtras();

                if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {

                    if (extras.getBoolean(Intent.EXTRA_REPLACING)) return; //this is an update!

                    Uri packageUri = intent.getData();
                    if (packageUri == null) return;
                    String packageName = packageUri.getSchemeSpecificPart();
                    if (packageName == null) return;

                    ApplicationInfo appInfo;
                    try {
                        appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                    } catch (final NameNotFoundException e) {
                        appInfo = null;
                    }

                    String appName = (appInfo != null) ? (String) packageManager.getApplicationLabel(appInfo) : "";

                    PackageInfo pkgInfo;
                    try {
                        pkgInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                    } catch (PackageManager.NameNotFoundException e) {
                        pkgInfo = null;
                    }

                    ContentValues rowData = new ContentValues();
                    rowData.put(Installations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Installations_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Installations_Data.PACKAGE_NAME, packageName);
                    rowData.put(Installations_Data.APPLICATION_NAME, appName);
                    rowData.put(Installations_Data.INSTALLATION_STATUS, STATUS_ADDED);
                    rowData.put(Installations_Data.PACKAGE_VERSION_NAME, (pkgInfo != null) ? pkgInfo.versionName : "");
                    rowData.put(Installations_Data.PACKAGE_VERSION_CODE, (pkgInfo != null) ? pkgInfo.versionCode : -1);

                    try {
                        context.getContentResolver().insert(Installations_Data.CONTENT_URI, rowData);

                        package_name = packageName;
                        application_name = appName;
                        current_context = ACTION_AWARE_APPLICATION_ADDED;
                        sContext_producer.onContext();

                        if (Aware.DEBUG) {
                            Log.d(TAG, "Installed application:" + packageName);
                        }
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                }

                if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {

                    if (extras.getBoolean(Intent.EXTRA_REPLACING)) return; //this is an update!

                    Uri packageUri = intent.getData();
                    if (packageUri == null) return;
                    String packageName = packageUri.getSchemeSpecificPart();
                    if (packageName == null) return;

                    String appName = "";
                    Cursor get_application_info = context.getContentResolver().query(Installations_Data.CONTENT_URI, new String[]{Installations_Data.APPLICATION_NAME}, Installations_Data.PACKAGE_NAME + " like '" + packageName + "'", null, Installations_Data.TIMESTAMP + " DESC LIMIT 1");
                    if (get_application_info != null && get_application_info.moveToFirst()) {
                        appName = get_application_info.getString(get_application_info.getColumnIndex(Applications_History.APPLICATION_NAME));
                    }
                    if (get_application_info != null && !get_application_info.isClosed())
                        get_application_info.close();

                    if (appName.length() == 0) {
                        //try application history as last resort
                        get_application_info = context.getContentResolver().query(Applications_History.CONTENT_URI, new String[]{Applications_History.APPLICATION_NAME}, Applications_History.PACKAGE_NAME + " like '" + packageName + "'", null, Applications_History.TIMESTAMP + " DESC LIMIT 1");
                        if (get_application_info != null && get_application_info.moveToFirst()) {
                            appName = get_application_info.getString(get_application_info.getColumnIndex(Applications_History.APPLICATION_NAME));
                        }
                        if (get_application_info != null && !get_application_info.isClosed())
                            get_application_info.close();
                    }

                    ContentValues rowData = new ContentValues();
                    rowData.put(Installations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Installations_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Installations_Data.PACKAGE_NAME, packageName);
                    rowData.put(Installations_Data.APPLICATION_NAME, appName);
                    rowData.put(Installations_Data.INSTALLATION_STATUS, STATUS_REMOVED);

                    try {
                        context.getContentResolver().insert(Installations_Data.CONTENT_URI, rowData);

                        package_name = packageName;
                        application_name = appName;
                        current_context = ACTION_AWARE_APPLICATION_REMOVED;
                        sContext_producer.onContext();

                        if (Aware.DEBUG) {
                            Log.d(TAG, "Removed application:" + packageName + " Name " + appName);
                        }
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                }

                if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)) {
                    Uri packageUri = intent.getData();
                    if (packageUri == null) return;
                    String packageName = packageUri.getSchemeSpecificPart();
                    if (packageName == null) return;

                    ApplicationInfo appInfo;
                    try {
                        appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                    } catch (final NameNotFoundException e) {
                        appInfo = null;
                    }
                    String appName = (appInfo != null) ? (String) packageManager.getApplicationLabel(appInfo) : "";

                    PackageInfo pkgInfo;
                    try {
                        pkgInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                    } catch (PackageManager.NameNotFoundException e) {
                        pkgInfo = null;
                    }

                    ContentValues rowData = new ContentValues();
                    rowData.put(Installations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Installations_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Installations_Data.PACKAGE_NAME, packageName);
                    rowData.put(Installations_Data.APPLICATION_NAME, appName);
                    rowData.put(Installations_Data.INSTALLATION_STATUS, STATUS_UPDATED);
                    rowData.put(Installations_Data.PACKAGE_VERSION_NAME, (pkgInfo != null) ? pkgInfo.versionName : "");
                    rowData.put(Installations_Data.PACKAGE_VERSION_CODE, (pkgInfo != null) ? pkgInfo.versionCode : -1);

                    try {
                        context.getContentResolver().insert(Installations_Data.CONTENT_URI, rowData);

                        package_name = packageName;
                        application_name = appName;
                        current_context = ACTION_AWARE_APPLICATION_UPDATED;
                        sContext_producer.onContext();

                        if (Aware.DEBUG) {
                            Log.d(TAG, "Updated application:" + packageName);
                        }
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                }
            }
        }
    }

    private static final Packages_Monitor installationsMonitor = new Packages_Monitor();
}
