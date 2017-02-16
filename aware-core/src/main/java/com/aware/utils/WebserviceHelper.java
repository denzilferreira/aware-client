
package com.aware.utils;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebserviceHelper extends Service {

    public static final String ACTION_AWARE_WEBSERVICE_SYNC_TABLE = "ACTION_AWARE_WEBSERVICE_SYNC_TABLE";
    public static final String ACTION_AWARE_WEBSERVICE_CLEAR_TABLE = "ACTION_AWARE_WEBSERVICE_CLEAR_TABLE";

    public static final String EXTRA_TABLE = "table";
    public static final String EXTRA_FIELDS = "fields";
    public static final String EXTRA_CONTENT_URI = "uri";

    private static final int WEBSERVICES_NOTIFICATION_ID = 98765;

    private static NotificationManager notManager;
    private long sync_start = 0;

    private Looper mServiceLooper;
    ExecutorService executor;

    private static int notificationID = 0;

    private ServiceHandler mServiceHandler;


    private static final Map<String, Boolean> SYNCED_TABLES = Collections.synchronizedMap(new HashMap<String, Boolean>());

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper) {
            super(looper);
            // Two threads to sync data with the server
            executor = Executors.newFixedThreadPool(2);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();


            synchronized (SYNCED_TABLES) {
                if (!SYNCED_TABLES.containsKey(bundle.getString("DATABASE_TABLE")) || (SYNCED_TABLES.containsKey(bundle.getString("DATABASE_TABLE")) && SYNCED_TABLES.get(bundle.getString("DATABASE_TABLE")))) {
                    Log.d(Aware.TAG, "Tried to sync for the first time for " + bundle.getString("DATABASE_TABLE"));
                    SYNCED_TABLES.put(bundle.getString("DATABASE_TABLE"), false);
                    Context context = (Context) msg.obj;
                    notificationID++;
                    SyncTable syncTable = new SyncTable(context, bundle.getBoolean("DEBUG"), bundle.getString("DATABASE_TABLE"), bundle.getString("TABLES_FIELDS"), bundle.getString("ACTION"), bundle.getString("CONTENT_URI_STRING"), bundle.getString("DEVICE_ID"), bundle.getString("WEBSERVER"), bundle.getBoolean("WEBSERVICE_SIMPLE"), bundle.getBoolean("WEBSERVICE_REMOVE_DATA"), bundle.getInt("MAX_POST_SIZE"), notificationID);
                    executor.submit(syncTable);
                } else {
                    Log.d(Aware.TAG, "Tried to sync again for " + bundle.getString("DATABASE_TABLE"));
                }
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }


    }

    @Override
    public void onCreate() {

        synchronized (this) {

        }

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        super.onCreate();


        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Synching all the databases...");

        if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
            notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // notifyUser(getApplicationContext(), "Synching initiated...", false, true, WEBSERVICES_NOTIFICATION_ID);
        }
        sync_start = System.currentTimeMillis();
    }

    private static void notifyUser(Context mContext, String message, boolean dismiss, boolean indetermined, int id) {
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


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String WEBSERVER = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);

        //Fixed: not part of a study, do nothing
        if (WEBSERVER.length() == 0 || WEBSERVER.equalsIgnoreCase("https://api.awareframework.com/index.php")) {
            stopSelf();
            return START_NOT_STICKY;
        }


        boolean WEBSERVICE_SIMPLE = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SIMPLE).equals("true");
        boolean WEBSERVICE_REMOVE_DATA = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true");

        /**
         * Max number of rows to place on the HTTP(s) post
         */
        int MAX_POST_SIZE = 10000;
        if (Aware.is_watch(getApplicationContext())) {
            MAX_POST_SIZE = 100; //default for Android Wear (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        String DEVICE_ID = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");

        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
                Intent batt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = batt.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isCharging = (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB);

                if (!isCharging) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Only sync data if charging...");
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }

            //Check if we are supposed to sync over WiFi only
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true")) {
                ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected()) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Sync data only over Wi-Fi and internet is available, let's sync!");
                } else {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Sync data only over Wi-Fi. Will try again later...");
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }

            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job

            Message msg = mServiceHandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putBoolean("DEBUG", DEBUG);
            bundle.putString("DEVICE_ID", DEVICE_ID);
            bundle.putString("WEBSERVER", WEBSERVER);
            bundle.putBoolean("WEBSERVICE_SIMPLE", WEBSERVICE_SIMPLE);
            bundle.putBoolean("WEBSERVICE_REMOVE_DATA", WEBSERVICE_REMOVE_DATA);
            bundle.putInt("MAX_POST_SIZE", MAX_POST_SIZE);
            bundle.putString("DATABASE_TABLE", intent.getStringExtra(EXTRA_TABLE));
            bundle.putString("TABLES_FIELDS", intent.getStringExtra(EXTRA_FIELDS));
            bundle.putString("ACTION", intent.getAction());
            bundle.putString("CONTENT_URI_STRING", intent.getStringExtra(EXTRA_CONTENT_URI));
            msg.obj = getApplicationContext();
            msg.setData(bundle);
            msg.arg1 = startId;
            mServiceHandler.sendMessage(msg);


        }

        // If we get killed, after returning from here, restart
        return START_NOT_STICKY;
    }

    /**
     * Asynchronously process the sync of all tables
     */
    private static class SyncTable implements Callable<String> {
        private Context mContext;
        private boolean DEBUG;
        private String TABLES_FIELDS;
        private String ACTION;
        private String CONTENT_URI_STRING;
        private String DEVICE_ID;
        private String DATABASE_TABLE;
        private String WEBSERVER;
        private String protocol;
        private boolean WEBSERVICE_SIMPLE;
        private boolean WEBSERVICE_REMOVE_DATA;
        private int MAX_POST_SIZE;
        private int NOTIFICATION_ID;


        SyncTable(Context c, boolean debug, String table, String fields, String action, String uri, String deviceID, String webServer, boolean webServiceSimple, boolean webServiceRemoveData, int maxPostSize, int notificationID) {
            mContext = c;
            DEVICE_ID = deviceID;
            DEBUG = debug;
            DATABASE_TABLE = table;
            TABLES_FIELDS = fields;
            ACTION = action;
            CONTENT_URI_STRING = uri;
            WEBSERVICE_SIMPLE = webServiceSimple;
            WEBSERVER = webServer;
            protocol = WEBSERVER.substring(0, WEBSERVER.indexOf(":"));
            WEBSERVICE_REMOVE_DATA = webServiceRemoveData;
            MAX_POST_SIZE = maxPostSize;
            NOTIFICATION_ID = notificationID;
        }


        private String createRemoteTable() {
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
                        response = new Https(mContext, SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                    } catch (FileNotFoundException e) {
                        response = null;
                    }
                } else {
                    response = new Http(mContext).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                }
            }
            return response;

        }

        private String[] getTableColumnsNames(Uri CONTENT_URI) {
            String[] columnsStr = new String[]{};
            Cursor columnsDB = mContext.getContentResolver().query(CONTENT_URI, null, null, null, null);
            if (columnsDB != null && columnsDB.moveToFirst()) {
                columnsStr = columnsDB.getColumnNames();
            }
            if (columnsDB != null && !columnsDB.isClosed()) columnsDB.close();
            return columnsStr;
        }

        private String getLatestRecordInDatabase() {
            Hashtable<String, String> request = new Hashtable<>();
            request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);

            //check the latest entry in remote database
            String latest = "[]";
            // Default aware has this condition as TRUE and does the /latest call.
            // Only if WEBSERVICE_REMOVE_DATA is true can we safely do this, and
            // we also require WEBSERVICE_SIMPLE so that we can remove data while
            // still having safe commits.
            if (!(WEBSERVICE_SIMPLE && WEBSERVICE_REMOVE_DATA)) {
                // Normal AWARE API always gets here.
                if (protocol.equals("https")) {
                    try {
                        latest = new Https(mContext, SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                    } catch (FileNotFoundException e) {
                        latest = null;
                    }
                } else {
                    latest = new Http(mContext).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                }
                if (latest == null)
                    return Thread.currentThread().getName(); //unable to reach the server, cancel this sync
            }
            return latest;
        }

        private String getRemoteSyncCondition() {
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

        private int getNumberOfRecordsToSync(Uri CONTENT_URI, String[] columnsStr, String latest, String study_condition) throws JSONException {
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
                    last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                } else {
                    last = remoteData.getJSONObject(0).getLong("timestamp");
                    Cursor counter = mContext.getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + study_condition, null, "_id ASC");
                    if (counter != null && counter.moveToFirst()) {
                        TOTAL_RECORDS = counter.getInt(0);
                        counter.close();
                    }
                    if (counter != null && !counter.isClosed()) counter.close();
                }
            }


            if (DEBUG)
                Log.d(Aware.TAG, "Syncing " + TOTAL_RECORDS + " records from " + DATABASE_TABLE);


            return TOTAL_RECORDS;
        }

        private Cursor getSyncContextData(JSONArray remoteData, Uri CONTENT_URI, String study_condition, String[] columnsStr, int uploaded_records) throws JSONException {
            Cursor context_data;
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
                    last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                    last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                } else {
                    last = remoteData.getJSONObject(0).getLong("timestamp");
                    context_data = mContext.getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "_id ASC LIMIT " + uploaded_records + ", " + MAX_POST_SIZE);
                }
            }
            return context_data;
        }

        private void performDatabaseSpaceMaintenance(Uri CONTENT_URI, long last) {
            ArrayList<String> highFrequencySensors = new ArrayList<>();
            highFrequencySensors.add("accelerometer");
            highFrequencySensors.add("gyroscope");
            highFrequencySensors.add("barometer");
            highFrequencySensors.add("gravity");
            highFrequencySensors.add("light");
            highFrequencySensors.add("linear_accelerometer");
            highFrequencySensors.add("magnetometer");
            highFrequencySensors.add("rotation");
            highFrequencySensors.add("temperature");
            highFrequencySensors.add("proximity");

            //Clean the local database, now that it is uploaded to the server, if required
            if ((Aware.getSetting(mContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0
                    && Integer.parseInt(Aware.getSetting(mContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA)) == 4
                    && highFrequencySensors.contains(DATABASE_TABLE))
                    || WEBSERVICE_REMOVE_DATA) {

                mContext.getContentResolver().delete(CONTENT_URI, "timestamp <= " + last, null);

                if (DEBUG)
                    Log.d(Aware.TAG, "Sync finished. Deleted local old records for " + DATABASE_TABLE);

                if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                    notifyUser(mContext, "Sync finished. Cleaned old records from " + DATABASE_TABLE, false, true, NOTIFICATION_ID);
                }
            }
        }

        private boolean syncBatch(Cursor context_data, Uri CONTENT_URI) throws JSONException {
            JSONArray rows = new JSONArray();
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
                            row.put(c_name, context_data.getString(context_data.getColumnIndex(c_name)));
                        }
                    }
                    rows.put(row);
                } while (context_data.moveToNext());

                context_data.close(); //clear phone's memory immediately
                Hashtable<String, String> request = new Hashtable<>();
                request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
                request.put("data", rows.toString());

                String success;
                if (protocol.equals("https")) {
                    try {
                        success = new Https(mContext, SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                        if (DEBUG)
                            Log.d(Aware.TAG, "Sync " + DATABASE_TABLE + " OK");
                    } catch (FileNotFoundException e) {
                        success = null;
                    }
                } else {
                    success = new Http(mContext).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                    if (DEBUG)
                        Log.d(Aware.TAG, "Sync " + DATABASE_TABLE + " OK");
                }

                //Something went wrong, e.g., server is down, lost internet, etc.
                if (success == null) {
                    if (DEBUG)
                        Log.d(Aware.TAG, DATABASE_TABLE + " FAILED to sync. Server down?");
                    return false;
                } else { //Are we performing database space maintenance?

                    performDatabaseSpaceMaintenance(CONTENT_URI, rows.getJSONObject(rows.length() - 1).getLong("timestamp"));

                }
            }

            return true;
        }

        @Override
        public String call() throws Exception {


            if (ACTION.equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {
                Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);
                String response = "";
                response = createRemoteTable();

                if (response != null || WEBSERVICE_SIMPLE) {

                    try {
                        String[] columnsStr = getTableColumnsNames(CONTENT_URI);
                        String latest = getLatestRecordInDatabase();
                        String study_condition = getRemoteSyncCondition();
                        int total_records = getNumberOfRecordsToSync(CONTENT_URI, columnsStr, latest, study_condition);

                        if (total_records > 0) {

                            JSONArray remoteData = new JSONArray(latest);
                            long start = System.currentTimeMillis();
                            int uploaded_records = 0;
                            int batches = (int) Math.ceil(total_records / (double) MAX_POST_SIZE);
                            boolean syncSuccess = true;

                            while (uploaded_records < total_records && syncSuccess) { //paginate cursor so it does not explode the phone's memory

                                if (Aware.DEBUG)
                                    Log.d(Aware.TAG, "Syncing " + uploaded_records + " out of " + total_records + " from table " + DATABASE_TABLE);

                                if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                                    notifyUser(mContext, "Syncing batch " + (uploaded_records + MAX_POST_SIZE) / MAX_POST_SIZE + " of " + batches + " from " + DATABASE_TABLE, false, true, NOTIFICATION_ID);
                                }

                                syncSuccess = syncBatch(getSyncContextData(remoteData, CONTENT_URI, study_condition, columnsStr, uploaded_records), CONTENT_URI);
                                uploaded_records += MAX_POST_SIZE;
                            }

                            if (DEBUG)
                                Log.d(Aware.TAG, DATABASE_TABLE + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

                            if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                                notifyUser(mContext, "Finished syncing " + DATABASE_TABLE + ". Thanks!", true, false, NOTIFICATION_ID);
                            }
                        } else {
                            if (DEBUG)
                                Log.d(Aware.TAG, "Nothing to sync: " + total_records + " records from " + DATABASE_TABLE);

                            return Thread.currentThread().getName(); //nothing to upload, no need to do anything now.
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    } finally {
                        synchronized (SYNCED_TABLES) {
                            SYNCED_TABLES.put(DATABASE_TABLE, true);
                        }
                    }
                }
            }


            if (ACTION.equals(ACTION_AWARE_WEBSERVICE_CLEAR_TABLE)) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Clearing data..." + DATABASE_TABLE);

                Hashtable<String, String> request = new Hashtable<>();
                request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);

                if (protocol.equals("https")) {
                    try {
                        new Https(mContext, SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    new Http(mContext).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
                }
            }

            return Thread.currentThread().getName();
        }
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

        mServiceLooper.quitSafely();
        executor.shutdown();

    }
}

