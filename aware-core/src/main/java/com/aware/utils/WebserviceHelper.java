
package com.aware.utils;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
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
import java.util.Hashtable;

public class WebserviceHelper extends IntentService {

    public static final String ACTION_AWARE_WEBSERVICE_SYNC_TABLE = "ACTION_AWARE_WEBSERVICE_SYNC_TABLE";
    public static final String ACTION_AWARE_WEBSERVICE_CLEAR_TABLE = "ACTION_AWARE_WEBSERVICE_CLEAR_TABLE";

    public static final String EXTRA_TABLE = "table";
    public static final String EXTRA_FIELDS = "fields";
    public static final String EXTRA_CONTENT_URI = "uri";

    private static final int WEBSERVICES_NOTIFICATION_ID = 98765;

    private NotificationManager notManager;
    private long sync_start = 0;

    public WebserviceHelper() {
        super(Aware.TAG + " Webservice Sync");
    }

    private boolean exists(String[] array, String find) {
        for (String a : array) {
            if (a.equals(find)) return true;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Synching all the databases...");

        if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
            notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyUser("Synching initiated...", false, true);
        }
        sync_start = System.currentTimeMillis();
    }

    private void notifyUser(String message, boolean dismiss, boolean indetermined) {
        if (!dismiss) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_sync);
            mBuilder.setContentTitle(getResources().getString(R.string.app_name));
            mBuilder.setContentText(message);
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS); //we only blink the LED, nothing else.
            mBuilder.setProgress(100, 100, indetermined);

            PendingIntent clickIntent = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

            try { notManager.notify(WEBSERVICES_NOTIFICATION_ID, mBuilder.build()); }
            catch (NullPointerException e) { if(Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e); }
        } else {
            try { notManager.cancel(WEBSERVICES_NOTIFICATION_ID); }
            catch (NullPointerException e) { if(Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e); }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        String WEBSERVER = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);

        //Fixed: not part of a study, do nothing
        if (WEBSERVER.length() == 0 || WEBSERVER.equalsIgnoreCase("https://api.awareframework.com/index.php")) return;

        String protocol = WEBSERVER.substring(0, WEBSERVER.indexOf(":"));
        boolean WEBSERVICE_SIMPLE = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SIMPLE).equals("true");
        boolean WEBSERVICE_REMOVE_DATA = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true");

        /**
         * Max number of rows to place on the HTTP(s) post
         */
        int MAX_POST_SIZE = 10000; //recommended for phones. This loads ~ 1-2MB data worth for HTTP POST as JSON, depending on the fields size (e.g. blobs)
        if (Aware.is_watch(getApplicationContext())) {
            MAX_POST_SIZE = 100; //default for watch (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        String DEVICE_ID = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");
        String DATABASE_TABLE = intent.getStringExtra(EXTRA_TABLE);
        String TABLES_FIELDS = intent.getStringExtra(EXTRA_FIELDS);

        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {

            Uri CONTENT_URI = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI));

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
                Intent batt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = batt.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isCharging = (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB);

                if (!isCharging) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Only synching data if charging...");
                    return;
                }
            }

            //Check if we are supposed to sync over WiFi only
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true")) {
                ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected()) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Synching data only over Wi-Fi and internet is available, let's sync!");
                } else {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Synching data only over Wi-Fi. Will try again later...");
                    return;
                }
            }

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
                        response = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                    } catch (FileNotFoundException e) {
                        response = null;
                    }
                } else {
                    response = new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                }
            }

            if (response != null || WEBSERVICE_SIMPLE) {
                String[] columnsStr = new String[]{};
                Cursor columnsDB = getContentResolver().query(CONTENT_URI, null, null, null, null);
                if (columnsDB != null && columnsDB.moveToFirst()) {
                    columnsStr = columnsDB.getColumnNames();
                }
                if (columnsDB != null && !columnsDB.isClosed()) columnsDB.close();

                try {
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
                                latest = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                            } catch (FileNotFoundException e) {
                                latest = null;
                            }
                        } else {
                            latest = new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                        }
                        if (latest == null) return; //unable to reach the server, cancel this sync
                    }

                    //If in a study, get only data from joined date onwards
                    String study_condition = "";
                    if (Aware.isStudy(getApplicationContext())) {
                        Cursor study = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                        if (study != null && study.moveToFirst()) {
                            study_condition = " AND timestamp >= " + study.getLong(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED));
                        }
                        if (study != null && !study.isClosed()) study.close();
                    }

                    //We always want to sync the device's profile and hardware sensor profiles for any study, no matter when we joined the study
                    if (DATABASE_TABLE.equalsIgnoreCase("aware_device")
                            || DATABASE_TABLE.matches("sensor_.*"))
                        study_condition = "";

                    //FIXED: only load the log of the current enrolled study, nothing more.
                    if (DATABASE_TABLE.equalsIgnoreCase("aware_studies")) {
                        Cursor study_log = getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null, Aware_Provider.Aware_Studies.STUDY_URL + " LIKE '" + Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER) + "'", null, Aware_Provider.Aware_Studies.STUDY_TIMESTAMP + " ASC LIMIT 1");
                        if (study_log != null && study_log.moveToFirst()) {
                            study_condition = " AND timestamp >= " + study_log.getLong(study_log.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP));
                        }
                        if (study_log != null && ! study_log.isClosed()) study_log.close();
                    }

                    JSONArray remoteData = new JSONArray(latest);

                    int TOTAL_RECORDS = 0;
                    if (remoteData.length() == 0) {
                        if (exists(columnsStr, "double_end_timestamp")) {
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        } else {
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "1" + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        }
                    } else {
                        long last;
                        if (exists(columnsStr, "double_end_timestamp")) {
                            last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                            last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        } else {
                            last = remoteData.getJSONObject(0).getLong("timestamp");
                            Cursor counter = getContentResolver().query(CONTENT_URI, new String[]{"count(*) as entries"}, "timestamp > " + last + study_condition, null, "timestamp ASC");
                            if (counter != null && counter.moveToFirst()) {
                                TOTAL_RECORDS = counter.getInt(0);
                                counter.close();
                            }
                        }
                    }

                    if (TOTAL_RECORDS == 0) {
                        return; //nothing to upload, no need to do anything now.
                    }

                    if (DEBUG)
                        Log.d(Aware.TAG, "Syncing " + TOTAL_RECORDS + " records from " + DATABASE_TABLE);

                    if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                        notifyUser("Syncing " + TOTAL_RECORDS + " from " + DATABASE_TABLE, false, true);
                    }

                    long start = System.currentTimeMillis();

                    int UPLOADED = 0;
                    while (UPLOADED < TOTAL_RECORDS) { //paginate cursor so it does not explode the phone's memory
                        Cursor context_data;
                        if (remoteData.length() == 0) {
                            if (exists(columnsStr, "double_end_timestamp")) {
                                context_data = getContentResolver().query(CONTENT_URI, null, "double_end_timestamp != 0" + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                                context_data = getContentResolver().query(CONTENT_URI, null, "double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            } else {
                                context_data = getContentResolver().query(CONTENT_URI, null, "1" + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            }
                        } else {
                            long last;
                            if (exists(columnsStr, "double_end_timestamp")) {
                                last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                                context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                                last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                                context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            } else {
                                last = remoteData.getJSONObject(0).getLong("timestamp");
                                context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "timestamp ASC LIMIT " + UPLOADED + ", " + MAX_POST_SIZE);
                            }
                        }

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

                            request = new Hashtable<>();
                            request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
                            request.put("data", rows.toString());

                            String success;
                            if (protocol.equals("https")) {
                                try {
                                    success = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                                    if (DEBUG)
                                        Log.d(Aware.TAG, "Sync " + DATABASE_TABLE + " OK");
                                } catch (FileNotFoundException e) {
                                    success = null;
                                }
                            } else {
                                success = new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                                if (DEBUG)
                                    Log.d(Aware.TAG, "Sync " + DATABASE_TABLE + " OK");
                            }

                            //Something went wrong, e.g., server is down, lost internet, etc.
                            if (success == null) {
                                if (DEBUG)
                                    Log.d(Aware.TAG, DATABASE_TABLE + " FAILED to upload. Server down?");
                                break;
                            } else { //Are we performing database space maintenance?
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
                                if ((Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0
                                        && Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA)) == 4
                                        && highFrequencySensors.contains(DATABASE_TABLE))
                                        || WEBSERVICE_REMOVE_DATA) {

                                    long last = rows.getJSONObject(rows.length() - 1).getLong("timestamp");
                                    getContentResolver().delete(CONTENT_URI, "timestamp <= " + last, null);

                                    if (DEBUG)
                                        Log.d(Aware.TAG, "Deleted local old records for " + DATABASE_TABLE);

                                    if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
                                        notifyUser("Cleaned old records from " + DATABASE_TABLE, false, true);
                                    }
                                }
                            }
                        }

                        UPLOADED += MAX_POST_SIZE;
                    }

                    if (DEBUG)
                        Log.d(Aware.TAG, DATABASE_TABLE + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        //Clear database table remotely
        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_CLEAR_TABLE)) {
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Clearing data..." + DATABASE_TABLE);

            Hashtable<String, String> request = new Hashtable<>();
            request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);

            if (protocol.equals("https")) {
                try {
                    new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Finished synching all the databases in " + DateUtils.formatElapsedTime((System.currentTimeMillis() - sync_start) / 1000));

        if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT).equals("true")) {
            notifyUser("Finished syncing", true, false);
        }
    }
}
