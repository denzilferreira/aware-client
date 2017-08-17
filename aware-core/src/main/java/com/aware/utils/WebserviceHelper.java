
package com.aware.utils;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.aware.providers.Aware_Provider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notes:
 * Denzil: This is a wakeful service called by the sync wakefulbroadcastreceiver. This guarantees the service is not interrupted by deep sleep.
 */
public class WebserviceHelper extends IntentService {

    public static final String ACTION_AWARE_WEBSERVICE_SYNC_TABLE = "ACTION_AWARE_WEBSERVICE_SYNC_TABLE";
    public static final String ACTION_AWARE_WEBSERVICE_CLEAR_TABLE = "ACTION_AWARE_WEBSERVICE_CLEAR_TABLE";

    public static final String EXTRA_TABLE = "table";
    public static final String EXTRA_FIELDS = "fields";
    public static final String EXTRA_CONTENT_URI = "uri";

    private static NotificationManager notManager;

    private static long sync_start = 0;
    private static int notificationID = 99990;
    private int total_rows_synced = 0;

    private static final ArrayList<String> highFrequencySensors = new ArrayList<>();
    private static final ArrayList<String> dontClearSensors = new ArrayList<>();

    private String web_server;
    private String protocol;
    private String database_table;

    public WebserviceHelper() {
        super("AWARE WebserviceHelper");
    }

    @Override
    public void onCreate() {
        super.onCreate();

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

        Aware.debug(this, "STUDY-SYNC");

        if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
            notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        sync_start = System.currentTimeMillis();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        web_server = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);
        if (web_server.length() == 0) return; //no webservice URL defined?

        protocol = web_server.substring(0, web_server.indexOf(":"));
        database_table = intent.getStringExtra(EXTRA_TABLE);

        if (Aware.DEBUG) Log.d(Aware.TAG, "Synching " + database_table);

        //Fixed: not part of a study, do nothing
        if (web_server.length() == 0 || web_server.equalsIgnoreCase("https://api.awareframework.com/index.php")) {
            stopSelf();
            return;
        }

        boolean web_service_simple = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SIMPLE).equals("true");
        boolean web_service_remove_data = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true");

        /**
         * Max number of rows to place on the HTTP(s) post
         */
        int MAX_POST_SIZE = getBatchSize();

        if (Aware.is_watch(getApplicationContext())) {
            MAX_POST_SIZE = 100; //default for Android Wear (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        if (Aware.DEBUG) Log.d("AWARE::Webservice", "Batch size is: " + MAX_POST_SIZE);

        String device_id = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");

        // Syncing tables
        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {

            String table_fields = intent.getStringExtra(EXTRA_FIELDS);
            String URI = intent.getStringExtra(EXTRA_CONTENT_URI);
            Context mContext = getApplicationContext();


            // Check if we need the phone to be plugged
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
                Intent batt = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = batt.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isCharging = (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB);

                if (!isCharging) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Only sync data if charging...");
                    stopSelf();
                    return;
                }
            }

            //Check if we are supposed to sync over WiFi only
            if (!isWifiNeededAndConnected()) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Sync data only over Wi-Fi. Will try again later...");

                stopSelf();
                return;
            }

            // Start syncing
            if (Aware.DEBUG) Log.d(Aware.TAG, "Processing " + database_table);
            Uri CONTENT_URI = Uri.parse(URI);
            String response = createRemoteTable(device_id, table_fields, web_service_simple, protocol, mContext, web_server, database_table);

            if (response != null || web_service_simple) {
                try {
                    String[] columnsStr = getTableColumnsNames(CONTENT_URI, mContext);
                    String latest = getLatestRecordInDatabase(device_id, web_service_simple, web_service_remove_data, database_table, protocol, mContext, web_server);
                    String study_condition = getRemoteSyncCondition(mContext, database_table);
                    int total_records = getNumberOfRecordsToSync(CONTENT_URI, columnsStr, latest, study_condition, mContext);
                    boolean allow_table_maintenance = isTableAllowedForMaintenance(database_table);

                    if (Aware.DEBUG) {
                        Log.d(Aware.TAG, "Sync " + database_table + " exists: " + (response != null && response.length() == 0));
                        if (!latest.equals("[]")) Log.d(Aware.TAG, "Latest: " + latest);
                        if (study_condition.length() > 0)
                            Log.d(Aware.TAG, "Since: " + study_condition);
                        if (total_records > 0)
                            Log.d(Aware.TAG, "Rows to sync: " + total_records);
                    }

                    // If we have records to sync
                    if (total_records > 0) {
                        JSONArray remoteLatestData = new JSONArray(latest);
                        long start = System.currentTimeMillis();
                        int uploaded_records = 0;
                        int batches = (int) Math.ceil(total_records / (double) MAX_POST_SIZE);
                        long lastSynced;
                        long removeFrom = 0;

                        do { //paginate cursor so it does not explode the phone's memory

                            if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
                                notifyUser(mContext, "Syncing batch " + (uploaded_records + MAX_POST_SIZE) / MAX_POST_SIZE + " of " + batches + " from " + database_table, false, true, notificationID);

                            Cursor sync_data = getSyncData(remoteLatestData, CONTENT_URI, study_condition, columnsStr, uploaded_records, mContext, MAX_POST_SIZE);

                            lastSynced = syncBatch(sync_data, database_table, device_id, mContext, protocol, web_server, DEBUG);

                            if (lastSynced > 0) removeFrom = lastSynced;

                            uploaded_records += MAX_POST_SIZE;
                        }
                        while (uploaded_records < total_records && lastSynced > 0 && isWifiNeededAndConnected());

                        //Are we performing database space maintenance?
                        if (removeFrom > 0 && allow_table_maintenance)
                            performDatabaseSpaceMaintenance(CONTENT_URI, removeFrom, columnsStr, web_service_remove_data, mContext, database_table, DEBUG);

                        if (DEBUG)
                            Log.d(Aware.TAG, database_table + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

                        if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                            notifyUser(mContext, "Finished syncing " + database_table + ". Thanks!", true, false, notificationID);
                        }
                    } else {
                        //nothing to upload, no need to do anything now.
                        return;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_CLEAR_TABLE)) {
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Clearing data..." + database_table);

            Hashtable<String, String> request = new Hashtable<>();
            request.put(Aware_Preferences.DEVICE_ID, device_id);

            if (protocol.equals("https")) {
                try {
                    new Https(SSLManager.getHTTPS(getApplicationContext(), web_server)).dataPOST(web_server + "/" + database_table + "/clear_table", request, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                new Http().dataPOST(web_server + "/" + database_table + "/clear_table", request, true);
            }
        }
    }

    private void notifyUser(Context mContext, String message, boolean dismiss, boolean indetermined, int id) {
        if (!dismiss) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_sync);
            mBuilder.setContentTitle(mContext.getResources().getString(R.string.app_name));
            mBuilder.setContentText(message);
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS); //we only blink the LED, nothing else.
            mBuilder.setProgress(100, 100, indetermined);

            PendingIntent clickIntent = PendingIntent.getActivity(mContext, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

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
        double availableRam;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            String load;
            try (RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r")) {
                load = reader.readLine();
            } catch (IOException ex) {
                ex.printStackTrace();
                load = "0";
            }

            // Get the Number value from the string
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(load);
            String value = "";
            while (m.find())
                value = m.group(1);

            availableRam = Double.parseDouble(value) / 1048576.0;
        } else {
            ActivityManager actManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            availableRam = memInfo.totalMem / 1048576000.0;
        }

        if (availableRam <= 1.0)
            return 1000;
        else if (availableRam <= 2.0)
            return 3000;
        else if (availableRam <= 4.0)
            return 10000;
        else
            return 20000;
    }


    public boolean isWifiNeededAndConnected() {
        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true")) {
            ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
            boolean sync = (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected());

            //we are connected to WiFi, we are done here.
            if (sync) return sync;

            //Fallback to 3G if no wifi for x hours
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK).length() > 0
                    && !Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK).equals("0")) {

                Cursor lastSynched = getContentResolver().query(Aware_Provider.Aware_Log.CONTENT_URI, null, Aware_Provider.Aware_Log.LOG_MESSAGE + " LIKE 'STUDY-SYNC'", null, Aware_Provider.Aware_Log.LOG_TIMESTAMP + " DESC LIMIT 1");
                if (lastSynched != null && lastSynched.moveToFirst()) {
                    long synched = lastSynched.getLong(lastSynched.getColumnIndex(Aware_Provider.Aware_Log.LOG_TIMESTAMP));
                    sync = (System.currentTimeMillis() - synched >= Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK)) * 60 * 60 * 1000);
                    lastSynched.close();
                }
            }
            return sync;
        }
        return true;
    }


    private String createRemoteTable(String DEVICE_ID, String TABLES_FIELDS, Boolean WEBSERVICE_SIMPLE, String protocol, Context mContext, String WEBSERVER, String DATABASE_TABLE) {
        //Check first if we have database table remotely, otherwise create it!
        Hashtable<String, String> fields = new Hashtable<>();
        fields.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
        fields.put(EXTRA_FIELDS, TABLES_FIELDS);

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

    private String getLatestRecordInDatabase(String DEVICE_ID, Boolean WEBSERVICE_SIMPLE, Boolean WEBSERVICE_REMOVE_DATA, String DATABASE_TABLE, String protocol, Context mContext, String WEBSERVER) {
        Hashtable<String, String> request = new Hashtable<>();
        request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);

        //check the latest entry in remote database
        String latest = "[]";
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
                    return "[]";
                }
            } else {
                latest = new Http().dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
            }
        }
        if (latest == null) return "[]";
        return latest;
    }

    private String getRemoteSyncCondition(Context mContext, String DATABASE_TABLE) {
        //If in a study, get only data from joined date onwards
        String study_condition = "";
        if (Aware.isStudy(mContext)) {
            Cursor study = Aware.getStudy(mContext, Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SERVER));
            if (study != null && study.moveToFirst()) {
                study_condition += " AND timestamp >= " + study.getLong(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP));
            }
            if (study != null && !study.isClosed()) study.close();
        }

        //We always want to sync the device's profile and hardware sensor profiles for any study, no matter when we joined the study
        if (DATABASE_TABLE.equalsIgnoreCase("aware_device")
                || DATABASE_TABLE.matches("sensor_.*"))
            study_condition = "";

        return study_condition;
    }

    private int getNumberOfRecordsToSync(Uri CONTENT_URI, String[] columnsStr, String latest, String study_condition, Context mContext) throws JSONException {
        if (latest == null) return 0;

        JSONArray remoteData = new JSONArray(latest);

        int TOTAL_RECORDS = 0;
        if (remoteData.length() == 0) {
            if (exists(columnsStr, "double_end_timestamp")) {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "double_end_timestamp != 0" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getInt(0);
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getInt(0);
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();
            } else {
                Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "1" + study_condition, null, "_id ASC");
                if (counter != null && counter.moveToFirst()) {
                    TOTAL_RECORDS = counter.getInt(0);
                    counter.close();
                }
                if (counter != null && !counter.isClosed()) counter.close();
            }
        } else {
            long last;
            if (exists(columnsStr, "double_end_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_end_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                if (remoteData.getJSONObject(0).has("double_esm_user_answer_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            } else {
                if (remoteData.getJSONObject(0).has("timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            }
        }

        total_rows_synced += TOTAL_RECORDS;

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

    private long syncBatch(Cursor context_data, String DATABASE_TABLE, String DEVICE_ID, Context mContext, String protocol, String WEBSERVER, Boolean DEBUG) throws JSONException {
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
                return 0;
            } else {
                if (DEBUG)
                    Log.d(Aware.TAG, "Sync OK into " + DATABASE_TABLE + " [ " + rows.length() + " rows ]");
            }
        }

        return lastSynced;
    }

    private boolean isTableAllowedForMaintenance(String table_name) {
        //we need to keep the schedulers and aware_studies tables and on those tables that contain
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

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {

        long total_seconds = (System.currentTimeMillis() - sync_start) / 1000;

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Syncing " + database_table + " finished. Total records: " + total_rows_synced + " Total time: " + DateUtils.formatElapsedTime(total_seconds));

    }
}

