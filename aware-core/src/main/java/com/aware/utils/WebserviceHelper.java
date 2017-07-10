
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static int notificationID = 0;
    private static int total_rows_synced = 0;

    private SyncQueue mSyncFastQueue;
    private Looper mServiceLooperFastQueue;
    private ExecutorService executorFastQueue;

    private SyncQueue mSyncSlowQueueA;
    private Looper mServiceLooperSlowQueueA;
    private ExecutorService executorSlowQueueA;

    private SyncQueue mSyncSlowQueueB;
    private Looper mServiceLooperSlowQueueB;
    private ExecutorService executorSlowQueueB;

    private static boolean nextSlowQueue = false;

    private static final ArrayList<String> highFrequencySensors = new ArrayList<>();
    private static final ArrayList<String> dontClearSensors = new ArrayList<>();

    public WebserviceHelper() {
        super("AWARE Sync Helper");
    }

    // Handler that receives messages from the thread
    private final class SyncQueue extends Handler {
        ExecutorService executor;
        int currentMessage;

        SyncQueue(Looper looper, ExecutorService executor) {
            super(looper);
            // One thread to sync data with the server
            this.executor = executor;
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            Context context = (Context) msg.obj;
            SyncTable syncTable = new SyncTable(context, bundle.getBoolean("DEBUG"), bundle.getString("DATABASE_TABLE"), bundle.getString("TABLES_FIELDS"), bundle.getString("ACTION"), bundle.getString("CONTENT_URI_STRING"), bundle.getString("DEVICE_ID"), bundle.getString("WEBSERVER"), bundle.getBoolean("WEBSERVICE_SIMPLE"), bundle.getBoolean("WEBSERVICE_REMOVE_DATA"), bundle.getInt("MAX_POST_SIZE"), bundle.getInt("notificationID"));
            try {
                String result;
                if (!executor.isTerminated()) {
                    currentMessage = msg.what;
                    result = executor.submit(syncTable).get();
                    currentMessage = 0;
                }
            } catch (InterruptedException | ExecutionException e) {
                if (Aware.DEBUG)
                    Log.e(Aware.TAG, e.getMessage());
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
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

        notificationID = 0;

        HandlerThread threadFast = new HandlerThread("SyncFastQueue", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        threadFast.start();

        HandlerThread threadSlowA = new HandlerThread("SyncSlowAQueue", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        threadSlowA.start();

        HandlerThread threadSlowB = new HandlerThread("SyncSlowBQueue", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        threadSlowB.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooperFastQueue = threadFast.getLooper();
        executorFastQueue = Executors.newSingleThreadExecutor();
        mSyncFastQueue = new SyncQueue(mServiceLooperFastQueue, executorFastQueue);

        mServiceLooperSlowQueueA = threadSlowA.getLooper();
        executorSlowQueueA = Executors.newSingleThreadExecutor();
        mSyncSlowQueueA = new SyncQueue(mServiceLooperSlowQueueA, executorSlowQueueA);

        mServiceLooperSlowQueueB = threadSlowB.getLooper();
        executorSlowQueueB = Executors.newSingleThreadExecutor();
        mSyncSlowQueueB = new SyncQueue(mServiceLooperSlowQueueB, executorSlowQueueB);

        if (Aware.DEBUG) Log.d(Aware.TAG, "Synching all the databases...");

        Aware.debug(this, "STUDY-SYNC");

        if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
            notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        sync_start = System.currentTimeMillis();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String WEBSERVER = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);

        //Fixed: not part of a study, do nothing
        if (WEBSERVER.length() == 0 || WEBSERVER.equalsIgnoreCase("https://api.awareframework.com/index.php")) {
            stopSelf();
            return START_REDELIVER_INTENT;
        }


        boolean WEBSERVICE_SIMPLE = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SIMPLE).equals("true");
        boolean WEBSERVICE_REMOVE_DATA = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true");

        /**
         * Max number of rows to place on the HTTP(s) post
         */
        int MAX_POST_SIZE = getBatchSize();

        if (Aware.is_watch(getApplicationContext())) {
            MAX_POST_SIZE = 100; //default for Android Wear (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        if (Aware.DEBUG) Log.d("AWARE::Webservice", "Batch size is: " + MAX_POST_SIZE);

        String DEVICE_ID = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");

        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
                Intent batt = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = batt.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isCharging = (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB);

                if (!isCharging) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Only sync data if charging...");
                    stopSelf();
                    return START_REDELIVER_INTENT;
                }
            }

            //Check if we are supposed to sync over WiFi only
            if (!isWifiNeededAndConnected()) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Sync data only over Wi-Fi. Will try again later...");

                stopSelf();
                return START_REDELIVER_INTENT;
            }

            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job
            String table = intent.getStringExtra(EXTRA_TABLE);

            if (Aware.DEBUG) Log.d(Aware.TAG, "Processing " + table);

            int tableHash = table.hashCode();
            if (mSyncFastQueue.currentMessage != tableHash && mSyncSlowQueueA.currentMessage != tableHash && mSyncSlowQueueB.currentMessage != tableHash
                    && !mSyncFastQueue.hasMessages(tableHash) && !mSyncSlowQueueA.hasMessages(tableHash) && !mSyncSlowQueueB.hasMessages(tableHash)) {
                if (!highFrequencySensors.contains(table)) { //Non High Frequency sensors go together
                    Message msg = buildMessage(mSyncFastQueue, intent, DEBUG, DEVICE_ID, WEBSERVER, WEBSERVICE_SIMPLE, WEBSERVICE_REMOVE_DATA, MAX_POST_SIZE, startId, notificationID++);
                    mSyncFastQueue.sendMessage(msg);
                } else if (nextSlowQueue) { // High frequency sensors are split in two threads
                    Message msg = buildMessage(mSyncSlowQueueA, intent, DEBUG, DEVICE_ID, WEBSERVER, WEBSERVICE_SIMPLE, WEBSERVICE_REMOVE_DATA, MAX_POST_SIZE, startId, notificationID++);
                    mSyncSlowQueueA.sendMessage(msg);
                    nextSlowQueue = !nextSlowQueue;
                } else {
                    Message msg = buildMessage(mSyncSlowQueueB, intent, DEBUG, DEVICE_ID, WEBSERVER, WEBSERVICE_SIMPLE, WEBSERVICE_REMOVE_DATA, MAX_POST_SIZE, startId, notificationID++);
                    mSyncSlowQueueB.sendMessage(msg);
                    nextSlowQueue = !nextSlowQueue;
                }
            }
        }

        // If we get killed, after returning from here, restart
        return START_REDELIVER_INTENT;
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
                    sync = (System.currentTimeMillis()-synched >= Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK)) * 60 * 60 * 1000);
                    lastSynched.close();
                }
            }
            return sync;
        }
        return true;
    }

    private Message buildMessage(SyncQueue queue, Intent intent, boolean DEBUG, String DEVICE_ID, String WEBSERVER, boolean WEBSERVICE_SIMPLE, boolean WEBSERVICE_REMOVE_DATA, int MAX_POST_SIZE, int startId, int notificationID) {
        Message msg = queue.obtainMessage(intent.getStringExtra(EXTRA_TABLE).hashCode());
        Bundle bundle = new Bundle();
        bundle.putBoolean("DEBUG", DEBUG);
        bundle.putString("DEVICE_ID", DEVICE_ID);
        bundle.putString("WEBSERVER", WEBSERVER);
        bundle.putBoolean("WEBSERVICE_SIMPLE", WEBSERVICE_SIMPLE);
        bundle.putBoolean("WEBSERVICE_REMOVE_DATA", WEBSERVICE_REMOVE_DATA);
        bundle.putInt("MAX_POST_SIZE", MAX_POST_SIZE);
        bundle.putInt("notificationID", notificationID);
        bundle.putString("DATABASE_TABLE", intent.getStringExtra(EXTRA_TABLE));
        bundle.putString("TABLES_FIELDS", intent.getStringExtra(EXTRA_FIELDS));
        bundle.putString("ACTION", intent.getAction());
        bundle.putString("CONTENT_URI_STRING", intent.getStringExtra(EXTRA_CONTENT_URI));
        msg.what = intent.getStringExtra(EXTRA_TABLE).hashCode();
        msg.obj = getApplicationContext();
        msg.setData(bundle);
        msg.arg1 = startId;
        return msg;
    }

    /**
     * Asynchronously process the sync of all tables
     */
    private final class SyncTable implements Callable<String> {
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

        private String[] getTableColumnsNames(Uri CONTENT_URI) {
            String[] columnsStr = new String[]{};
            Cursor columnsDB = mContext.getContentResolver().query(CONTENT_URI, null, null, null, null);
            if (columnsDB != null) {
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

        private Cursor getSyncData(JSONArray remoteData, Uri CONTENT_URI, String study_condition, String[] columnsStr, int uploaded_records) throws JSONException {
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

        private void performDatabaseSpaceMaintenance(Uri CONTENT_URI, long last, String[] columnsStr) {
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

        private long syncBatch(Cursor context_data) throws JSONException {
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

        @Override
        public String call() throws Exception {
            if (ACTION.equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {

                Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);
                String response = createRemoteTable();

                if (response != null || WEBSERVICE_SIMPLE) {
                    try {
                        String[] columnsStr = getTableColumnsNames(CONTENT_URI);
                        String latest = getLatestRecordInDatabase();
                        String study_condition = getRemoteSyncCondition();
                        int total_records = getNumberOfRecordsToSync(CONTENT_URI, columnsStr, latest, study_condition);
                        boolean allow_table_maintenance = isTableAllowedForMaintenance(DATABASE_TABLE);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "Sync " + DATABASE_TABLE + " exists: " + (response != null && response.length() == 0));
                            if (!latest.equals("[]")) Log.d(Aware.TAG, "Latest: " + latest);
                            if (study_condition.length() > 0)
                                Log.d(Aware.TAG, "Since: " + study_condition);
                            if (total_records > 0)
                                Log.d(Aware.TAG, "Rows to sync: " + total_records);
                        }

                        if (total_records > 0) {
                            JSONArray remoteLatestData = new JSONArray(latest);
                            long start = System.currentTimeMillis();
                            int uploaded_records = 0;
                            int batches = (int) Math.ceil(total_records / (double) MAX_POST_SIZE);
                            long lastSynced;
                            long removeFrom = 0;

                            do { //paginate cursor so it does not explode the phone's memory

                                if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true"))
                                    notifyUser(mContext, "Syncing batch " + (uploaded_records + MAX_POST_SIZE) / MAX_POST_SIZE + " of " + batches + " from " + DATABASE_TABLE, false, true, NOTIFICATION_ID);

                                lastSynced = syncBatch(getSyncData(remoteLatestData, CONTENT_URI, study_condition, columnsStr, uploaded_records));

                                if (lastSynced > 0) removeFrom = lastSynced;

                                uploaded_records += MAX_POST_SIZE;
                            }
                            while (uploaded_records < total_records && lastSynced > 0 && isWifiNeededAndConnected());

                            //Are we performing database space maintenance?
                            if (removeFrom > 0 && allow_table_maintenance)
                                performDatabaseSpaceMaintenance(CONTENT_URI, removeFrom, columnsStr);

                            if (DEBUG)
                                Log.d(Aware.TAG, DATABASE_TABLE + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

                            if (!Aware.getSetting(mContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                                notifyUser(mContext, "Finished syncing " + DATABASE_TABLE + ". Thanks!", true, false, NOTIFICATION_ID);
                            }
                        } else {
                            return Thread.currentThread().getName(); //nothing to upload, no need to do anything now.
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
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
                        new Https(SSLManager.getHTTPS(mContext, WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    new Http().dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
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
    protected void onHandleIntent(@Nullable Intent intent) {
        //no-op
    }

    @Override
    public void onDestroy() {
        mServiceLooperFastQueue.quitSafely();
        mServiceLooperSlowQueueA.quitSafely();
        mServiceLooperSlowQueueB.quitSafely();
        executorFastQueue.shutdown();
        executorSlowQueueA.shutdown();
        executorSlowQueueB.shutdown();

        long total_seconds = (System.currentTimeMillis() - sync_start) / 1000;

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Syncing all databases finished. Total records: " + total_rows_synced + " Total time: " + DateUtils.formatElapsedTime(total_seconds));

    }
}

