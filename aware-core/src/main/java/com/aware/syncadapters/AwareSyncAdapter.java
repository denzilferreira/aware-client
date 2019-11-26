package com.aware.syncadapters;

import android.accounts.Account;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.aware.providers.Aware_Provider;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;

/**
 * Created by denzilferreira on 19/07/2017.
 */
public class AwareSyncAdapter extends AbstractThreadedSyncAdapter {

    private String[] DATABASE_TABLES;
    private String[] TABLES_FIELDS;
    private Uri[] CONTEXT_URIS;

    private Context mContext;
    private NotificationManager notManager;

    private final ArrayList<String> highFrequencySensors = new ArrayList<>();
    private final ArrayList<String> dontClearSensors = new ArrayList<>();

    private int notificationID = 99990;

    public void init(String[] DATABASE_TABLES, String[] TABLES_FIELDS, Uri[] CONTEXT_URIS) {
        this.DATABASE_TABLES = DATABASE_TABLES;
        this.TABLES_FIELDS = TABLES_FIELDS;
        this.CONTEXT_URIS = CONTEXT_URIS;
    }

    public AwareSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        this.mContext = context;

        highFrequencySensors.add("accelerometer");
        highFrequencySensors.add("gyroscope");
        highFrequencySensors.add("barometer");
        highFrequencySensors.add("gravity");
        highFrequencySensors.add("linear_accelerometer");
        highFrequencySensors.add("magnetometer");
        highFrequencySensors.add("rotation");
        highFrequencySensors.add("temperature");
        highFrequencySensors.add("proximity");

        dontClearSensors.add("aware_studies");
    }

    /**
     * Sends the data to AWARE server of the study
     *
     * @param account
     * @param extras
     * @param authority
     * @param provider
     * @param syncResult
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {

        if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
            notManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (DATABASE_TABLES != null && TABLES_FIELDS != null && CONTEXT_URIS != null) {
            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                offloadData(mContext, DATABASE_TABLES[i], Aware.getSetting(getContext(), Aware_Preferences.WEBSERVICE_SERVER), TABLES_FIELDS[i], CONTEXT_URIS[i]);
            }
        }
    }

    private void offloadData(Context context, String database_table, String web_server, String table_fields, Uri CONTENT_URI) {

        //Fixed: not part of a study, do nothing
        if (web_server.length() == 0 || web_server.equalsIgnoreCase("https://api.awareframework.com/index.php")) {
            return;
        }

        //Do we need to be charging?
        if (Aware.getSetting(context, Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
            Intent batt = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = batt.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean isCharging = (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB);

            if (!isCharging) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Only sync data if charging...");
                return;
            }
        }

        //Do we need WiFi?
        if (!isWifiNeededAndConnected()) {
            if (!isForce3G(database_table)) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Sync data only over Wi-Fi. Will try again later...");
                return;
            }
        }

        Aware.debug(mContext, "STUDY-SYNC: " + database_table);

        String protocol = web_server.substring(0, web_server.indexOf(":"));
        boolean web_service_simple = Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SIMPLE).equals("true");
        boolean web_service_remove_data = Aware.getSetting(context, Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true");

        /**
         * Max number of rows to place on the HTTP(s) post
         */
        int MAX_POST_SIZE = getBatchSize();
        if (MAX_POST_SIZE == 0) {
            Log.d(Aware.TAG, "Device without available memory left for sync.");
            return;
        }

        if (Aware.is_watch(context)) {
            MAX_POST_SIZE = 100; //default for Android Wear (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Syncing " + database_table + " to: " + web_server + " in batches of " + MAX_POST_SIZE);

        String device_id = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(context, Aware_Preferences.DEBUG_FLAG).equals("true");

        String response = createRemoteTable(device_id, table_fields, web_service_simple, protocol, context, web_server, database_table);
        if (response != null || web_service_simple) {
            try {
                String[] columnsStr = getTableColumnsNames(CONTENT_URI, context);
                String study_condition = getStudySyncCondition(context, database_table);
                String latest = getLatestRecordSynced(database_table, columnsStr);

                int total_records = getNumberOfRecordsToSync(CONTENT_URI, columnsStr, latest, study_condition, context);
                boolean allow_table_maintenance = isTableAllowedForMaintenance(database_table);

                // If we have records to sync
                if (total_records > 0) {
                    JSONArray remoteLatestData = new JSONArray(latest);
                    long start = System.currentTimeMillis();
                    int uploaded_records = 0;
                    int batches = (int) Math.ceil(total_records / (double) MAX_POST_SIZE);

                    long removeFrom = 0;
                    Long lastSynced;

                    do {
                        if (!Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
                            notifyUser(context, "Table: " + database_table + " syncing batch " + (uploaded_records + MAX_POST_SIZE) / MAX_POST_SIZE + " of " + batches, false, true, notificationID);

                        Cursor sync_data = getSyncData(remoteLatestData, CONTENT_URI, study_condition, columnsStr, uploaded_records, context, MAX_POST_SIZE);
                        lastSynced = syncBatch(sync_data, database_table, device_id, context, protocol, web_server, DEBUG);
                        if (lastSynced == null) {
                            removeFrom = 0;
                            Log.d(Aware.TAG, "Connection to server interrupted. Will try again later.");
                            break;
                        } else {
                            removeFrom = lastSynced;
                        }
                        uploaded_records += MAX_POST_SIZE;
                    }
                    while (uploaded_records < total_records && lastSynced > 0 && isWifiNeededAndConnected());

                    //Are we performing database space maintenance?
                    if (removeFrom > 0 && allow_table_maintenance)
                        performDatabaseSpaceMaintenance(CONTENT_URI, removeFrom, columnsStr, web_service_remove_data, context, database_table, DEBUG);

                    if (DEBUG)
                        Log.d(Aware.TAG, database_table + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

                    if (!Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                        notifyUser(context, "Finished syncing " + database_table + ". Thanks!", true, false, notificationID);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyUser(Context mContext, String message, boolean dismiss, boolean indetermined, int id) {
        if (!dismiss) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext, Aware.AWARE_NOTIFICATION_CHANNEL_DATASYNC);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_sync);
            mBuilder.setContentTitle(mContext.getResources().getString(R.string.app_name));
            mBuilder.setContentText(message);
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS); //we only blink the LED, nothing else.
            mBuilder.setProgress(100, 100, indetermined);

            mBuilder = Aware.setNotificationProperties(mBuilder, Aware.AWARE_NOTIFICATION_IMPORTANCE_DATASYNC);

            PendingIntent clickIntent = PendingIntent.getActivity(mContext, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                mBuilder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_DATASYNC);

            try {
                notManager.notify(id, mBuilder.build());
            } catch (NullPointerException e) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e);
            }
        } else {
            try {
                notManager.cancel(id);
            } catch (NullPointerException e) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e);
            }
        }
    }

    private int getBatchSize() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        ActivityManager actManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        actManager.getMemoryInfo(memInfo);

        if (memInfo.lowMemory) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getContext(), Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_plugin_dependency);
            mBuilder.setContentTitle("Low memory detected...");
            mBuilder.setContentText("Tap and swipe to clear recently used applications.");
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true);
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
            mBuilder = Aware.setNotificationProperties(mBuilder, Aware.AWARE_NOTIFICATION_IMPORTANCE_GENERAL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                mBuilder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);

            Intent intent = new Intent("com.android.systemui.recent.action.TOGGLE_RECENTS");
            intent.setComponent(new ComponentName("com.android.systemui", "com.android.systemui.recent.RecentsActivity"));

            PendingIntent clickIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(Applications.ACCESSIBILITY_NOTIFICATION_ID, mBuilder.build());

            return 0;
        }

        double availableRam = memInfo.totalMem / 1048576000.0;
        if (availableRam <= 1.0) return 500;
        if (availableRam <= 2.0) return 1500;
        if (availableRam <= 4.0) return 5000;
        return 10000;
    }


    /**
     * Check the current connection is WiFi and we are connected
     *
     * @return
     */
    public boolean isWifiNeededAndConnected() {
        if (Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true")) {
            ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
            return (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected());
        }
        return true;
    }

    /**
     * Fallback to 3G if no wifi for x hours
     */
    public boolean isForce3G(String database_table) {
        if (Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK).length() > 0 && !Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK).equals("0")) {
            Cursor lastSynched = mContext.getContentResolver().query(Aware_Provider.Aware_Log.CONTENT_URI, null, Aware_Provider.Aware_Log.LOG_MESSAGE + " LIKE 'STUDY-SYNC: " + database_table + "'", null, Aware_Provider.Aware_Log.LOG_TIMESTAMP + " DESC LIMIT 1");
            if (lastSynched != null && lastSynched.moveToFirst()) {
                long synched = lastSynched.getLong(lastSynched.getColumnIndex(Aware_Provider.Aware_Log.LOG_TIMESTAMP));

                Log.d(Aware.TAG, "Checking forced sync over 3G...");
                Log.d(Aware.TAG, "Last sync: " + synched + " elapsed: " + (System.currentTimeMillis() - synched) + " force: " + (System.currentTimeMillis() - synched >= Integer.parseInt(Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK)) * 60 * 60 * 1000));

                lastSynched.close();
                return (System.currentTimeMillis() - synched >= Integer.parseInt(Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK)) * 60 * 60 * 1000);
            } else
                return true; //first time synching.
        }
        return false;
    }

    private String createRemoteTable(String DEVICE_ID, String TABLES_FIELDS, Boolean WEBSERVICE_SIMPLE, String protocol, Context mContext, String WEBSERVER, String DATABASE_TABLE) {
        //Check first if we have database table remotely, otherwise create it!
        Hashtable<String, String> fields = new Hashtable<>();
        fields.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
        fields.put("fields", TABLES_FIELDS);

        String response = null;
        // Do not run /create_table if webservice_simple == true
        if (!WEBSERVICE_SIMPLE) {
            //Create table if doesn't exist on the remote webservice server
            if (protocol.equals("https")) {
                try {
                    response = new Https(SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                } catch (FileNotFoundException e) {
                    response = null;
                }
            } else {
                response = new Http().dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
            }
        }
        return response;
    }

    private String[] getTableColumnsNames(Uri CONTENT_URI, Context mContext) {
        String[] columnsStr = new String[]{};
        Cursor columnsDB = mContext.getContentResolver().query(CONTENT_URI, null, null, null, null);
        if (columnsDB != null) {
            columnsStr = columnsDB.getColumnNames();
        }
        if (columnsDB != null && !columnsDB.isClosed()) columnsDB.close();
        return columnsStr;
    }

    private String getLatestRecordSynced(String database_table, String[] columnsStr) {

        JSONObject latest = new JSONObject();
        long last_sync_timestamp = 0;

        Cursor lastSynced = mContext.getContentResolver().query(Aware_Provider.Aware_Log.CONTENT_URI, null, Aware_Provider.Aware_Log.LOG_MESSAGE + " LIKE '{\"table\":\"" + database_table + "\",\"last_sync_timestamp\":%'", null, Aware_Provider.Aware_Log.LOG_TIMESTAMP + " DESC LIMIT 1");
        if (lastSynced != null && lastSynced.moveToFirst()) {
            try {

                JSONObject logSyncData = new JSONObject(lastSynced.getString(lastSynced.getColumnIndex(Aware_Provider.Aware_Log.LOG_MESSAGE)));
                last_sync_timestamp = logSyncData.getLong("last_sync_timestamp");

                if (exists(columnsStr, "double_end_timestamp")) {
                    latest = new JSONObject().put("double_end_timestamp", last_sync_timestamp);
                } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                    latest = new JSONObject().put("double_esm_user_answer_timestamp", last_sync_timestamp);
                } else {
                    latest = new JSONObject().put("timestamp", last_sync_timestamp);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            lastSynced.close();

        } else {
            return new JSONArray().toString();
        }

        return new JSONArray().put(latest).toString();
    }

    /**
     * @param DEVICE_ID
     * @param WEBSERVICE_SIMPLE
     * @param WEBSERVICE_REMOVE_DATA
     * @param DATABASE_TABLE
     * @param protocol
     * @param mContext
     * @param WEBSERVER
     * @return
     * @deprecated We are dropping this because of overhead and scalability when millions of records on the server side. We now keep track locally of the last successful record timestamp.
     */
    private String getLatestRecordFromServer(String DEVICE_ID, Boolean WEBSERVICE_SIMPLE, Boolean WEBSERVICE_REMOVE_DATA, String DATABASE_TABLE, String protocol, Context mContext, String WEBSERVER) {
        Hashtable<String, String> request = new Hashtable<>();
        request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);

        //check the latest entry in remote database
        String latest;
        // Default aware has this condition as TRUE and does the /latest call.
        // Only if WEBSERVICE_REMOVE_DATA is true can we safely do this, and
        // we also require WEBSERVICE_SIMPLE so that we can remove data while
        // still having safe commits.
        if (!(WEBSERVICE_SIMPLE && WEBSERVICE_REMOVE_DATA) || dontClearSensors.contains(DATABASE_TABLE)) {
            // Normal AWARE API always gets here.
            if (protocol.equals("https")) {
                try {
                    latest = new Https(SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                } catch (FileNotFoundException e) {
                    return null;
                }
            } else {
                latest = new Http().dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
            }
        } else {
            // Simplified API - no remote server contacting
            latest = "[]";
        }
        return latest;
    }

    private String getStudySyncCondition(Context mContext, String DATABASE_TABLE) {
        //If in a study, get only data from joined date onwards
        String study_condition = "";
        if (Aware.isStudy(mContext)) {
            Cursor study = Aware.getStudy(mContext, Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SERVER));
            if (study != null && study.moveToFirst()) {
                study_condition += " AND timestamp >= " + study.getLong(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED));
            }
            if (study != null && !study.isClosed()) study.close();
        }

        //We always want to sync the device's profile and hardware sensor profiles for any study, no matter when we joined the study
        if (DATABASE_TABLE.equalsIgnoreCase("aware_device") || DATABASE_TABLE.matches("sensor_.*"))
            study_condition = "";

        return study_condition;
    }

    private int getNumberOfRecordsToSync(Uri CONTENT_URI, String[] columnsStr, String latest, String study_condition, Context mContext) throws JSONException {
        if (latest == null) return 0;

        JSONArray remoteData = new JSONArray(latest);

        int TOTAL_RECORDS = 0;
        if (remoteData.length() == 0) {
            if (exists(columnsStr, "double_end_timestamp")) {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "double_end_timestamp != 0" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getCount();
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getCount();
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();
            } else {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "1" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getCount();
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();

            }
        } else {
            long last;
            if (exists(columnsStr, "double_end_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_end_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getCount();
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_esm_user_answer_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getCount();
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            } else {
                if (remoteData.getJSONObject(0).has("timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getCount();
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            }
        }
        return TOTAL_RECORDS;
    }

    private Cursor getSyncData(JSONArray remoteData, Uri CONTENT_URI, String study_condition, String[] columnsStr, int uploaded_records, Context mContext, int MAX_POST_SIZE) throws JSONException {
        Cursor context_data = null;
        if (remoteData.length() == 0) {
            if (exists(columnsStr, "double_end_timestamp")) {
                context_data = mContext.getContentResolver().query(CONTENT_URI, null, "double_end_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                context_data = mContext.getContentResolver().query(CONTENT_URI, null, "double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
            } else {
                context_data = mContext.getContentResolver().query(CONTENT_URI, null, "1" + study_condition, null, "timestamp ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
            }
        } else {
            long last;
            if (exists(columnsStr, "double_end_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_end_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                }
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_esm_user_answer_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                }
            } else {
                if (remoteData.getJSONObject(0).has("timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                }
            }
        }
        return context_data;
    }

    private void performDatabaseSpaceMaintenance(Uri CONTENT_URI, long last, String[] columnsStr, Boolean WEBSERVICE_REMOVE_DATA, Context mContext, String DATABASE_TABLE, Boolean DEBUG) {
        // keep records when contain end_timestamp (session-based entries), only remove the rows where the end_timestamp > 0
        String deleteSessionBasedSensors = "";
        if (exists(columnsStr, "double_end_timestamp")) {
            deleteSessionBasedSensors = " and double_end_timestamp > 0";
        }

        if (WEBSERVICE_REMOVE_DATA) {
            mContext.getContentResolver().delete(CONTENT_URI, "timestamp <= " + last, null);

        } else if (Aware.getSetting(mContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(last);
            int rowsDeleted = 0;
            switch (Integer.parseInt(Aware.getSetting(mContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA))) {
                case 1: //Weekly
                    cal.add(Calendar.DAY_OF_YEAR, -7);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, " Cleaning locally any data older than last week (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH));
                    rowsDeleted = mContext.getContentResolver().delete(CONTENT_URI, "timestamp < " + cal.getTimeInMillis() + deleteSessionBasedSensors, null);
                    break;
                case 2: //Monthly
                    cal.add(Calendar.MONTH, -1);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, " Cleaning locally any data older than last month (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH));
                    rowsDeleted = mContext.getContentResolver().delete(CONTENT_URI, "timestamp < " + cal.getTimeInMillis() + deleteSessionBasedSensors, null);
                    break;
                case 3: //Daily
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Cleaning locally any data older than today (yyyy/mm/dd): " + cal.get(Calendar.YEAR) + '/' + (cal.get(Calendar.MONTH) + 1) + '/' + cal.get(Calendar.DAY_OF_MONTH) + " from " + CONTENT_URI.toString());
                    rowsDeleted = mContext.getContentResolver().delete(CONTENT_URI, "timestamp < " + cal.getTimeInMillis() + deleteSessionBasedSensors, null);
                    break;
                case 4: //Always (experimental)
                    if (highFrequencySensors.contains(DATABASE_TABLE))
                        rowsDeleted = mContext.getContentResolver().delete(CONTENT_URI, "timestamp <= " + last, null);
                    break;
            }

            if (DEBUG && rowsDeleted > 0)
                Log.d(Aware.TAG, "Cleaned " + rowsDeleted + " from " + CONTENT_URI.toString());
        }
    }

    private Long syncBatch(Cursor context_data, String DATABASE_TABLE, String DEVICE_ID, Context mContext, String protocol, String WEBSERVER, Boolean DEBUG) throws JSONException {
        JSONArray rows = new JSONArray();
        long lastSynced = 0;
        if (context_data != null && context_data.moveToFirst()) {
            do {
                JSONObject row = new JSONObject();
                String[] columns = context_data.getColumnNames();
                for (String c_name : columns) {
                    if (c_name.equals("_id")) continue; //Skip local database ID
                    if (c_name.equals("timestamp") || c_name.contains("double")) {
                        row.put(c_name, context_data.getDouble(context_data.getColumnIndex(c_name)));
                    } else if (c_name.contains("float")) {
                        row.put(c_name, context_data.getFloat(context_data.getColumnIndex(c_name)));
                    } else if (c_name.contains("long")) {
                        row.put(c_name, context_data.getLong(context_data.getColumnIndex(c_name)));
                    } else if (c_name.contains("blob")) {
                        row.put(c_name, context_data.getBlob(context_data.getColumnIndex(c_name)));
                    } else if (c_name.contains("integer")) {
                        row.put(c_name, context_data.getInt(context_data.getColumnIndex(c_name)));
                    } else {
                        String str = "";
                        if (!context_data.isNull(context_data.getColumnIndex(c_name))) { //fixes nulls and batch inserts not being possible
                            str = context_data.getString(context_data.getColumnIndex(c_name));
                        }
                        row.put(c_name, str);
                    }
                }
                rows.put(row);
            } while (context_data.moveToNext());

            context_data.close(); //clear phone's memory immediately

            lastSynced = rows.getJSONObject(rows.length() - 1).getLong("timestamp"); //last record to be synced
            // For some tables, we must not clear everything.  Leave one row of these tables.
            if (dontClearSensors.contains(DATABASE_TABLE)) {
                if (rows.length() >= 2) {
                    lastSynced = rows.getJSONObject(rows.length() - 2).getLong("timestamp"); //last record to be synced
                } else {
                    lastSynced = 0;
                }
            }

            Hashtable<String, String> request = new Hashtable<>();
            request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
            request.put("data", rows.toString());

            String success;
            if (protocol.equals("https")) {
                try {
                    success = new Https(SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                } catch (FileNotFoundException e) {
                    success = null;
                }
            } else {
                success = new Http().dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
            }

            //Something went wrong, e.g., server is down, lost internet, etc.
            if (success == null) {
                if (DEBUG) Log.d(Aware.TAG, DATABASE_TABLE + " FAILED to sync. Server down?");
                return null;
            } else {

                try {
                    Aware.debug(mContext, new JSONObject()
                            .put("table", DATABASE_TABLE)
                            .put("last_sync_timestamp", lastSynced)
                            .toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (DEBUG)
                    Log.d(Aware.TAG, "Sync OK into " + DATABASE_TABLE + " [ " + rows.length() + " rows ]");
            }
        }

        return lastSynced;
    }

    private boolean isTableAllowedForMaintenance(String table_name) {
        //we always keep locally the information of the study and defined schedulers.
        if (table_name.equalsIgnoreCase("aware_studies") || table_name.equalsIgnoreCase("scheduler"))
            return false;
        return true;
    }

    private static boolean exists(String[] array, String find) {
        for (String a : array) {
            if (a.equals(find)) return true;
        }
        return false;
    }
}
