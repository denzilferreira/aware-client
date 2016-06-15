
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

    public WebserviceHelper() {
        super(Aware.TAG + " Webservice Sync");
    }

    private static long sync_start = 0;

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

        WebserviceHelper.sync_start = System.currentTimeMillis();

        notifyUser("Synching data to server...", false);
    }

    private void notifyUser(String message, boolean dismiss) {
        if (!dismiss) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_sync);
            mBuilder.setContentTitle("AWARE Sync");
            mBuilder.setContentText(message);
            mBuilder.setAutoCancel(true);
            mBuilder.setOnlyAlertOnce(true); //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS); //we only blink the LED, nothing else.
            mBuilder.setProgress(100, 100, true);

            PendingIntent clickIntent = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);

            NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(WEBSERVICES_NOTIFICATION_ID, mBuilder.build());
        } else {
            NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.cancel(WEBSERVICES_NOTIFICATION_ID);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        String WEBSERVER = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);
        String protocol = WEBSERVER.substring(0, WEBSERVER.indexOf(":"));

        //Fixed: not using webservices
        if (WEBSERVER.length() == 0) return;

        int batch_size = 10000; //default for phones
        if (Aware.is_watch(getApplicationContext())) {
            batch_size = 100; //default for watch (we have a limit of 100KB of data packet size (Message API restrictions)
        }

        String DEVICE_ID = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");
        String DATABASE_TABLE = intent.getStringExtra(EXTRA_TABLE);
        String TABLES_FIELDS = intent.getStringExtra(EXTRA_FIELDS);
        Uri CONTENT_URI = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI));

        if (intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE)) {

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_CHARGING).equals("true")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

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
                        Log.d(Aware.TAG, "Synching data only over Wi-Fi and no internet. Will try again later...");
                    return;
                }
            }

            if (Aware.DEBUG) Log.d(Aware.TAG, "Synching: " + DATABASE_TABLE);

            //Check first if we have database table remotely, otherwise create it!
            Hashtable<String, String> fields = new Hashtable<>();
            fields.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
            fields.put(EXTRA_FIELDS, TABLES_FIELDS);

            //Create table if doesn't exist on the remote webservice server
            String response;
            if (protocol.equals("https")) {
                try {
                    response = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
                } catch (FileNotFoundException e) {
                    response = null;
                }
            } else {
                response = new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
            }

            if (response != null) {
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
                    String latest;
                    if (protocol.equals("https")) {
                        try {
                            latest = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                        } catch (FileNotFoundException e) {
                            latest = null;
                        }
                    } else {
                        latest = new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
                    }
                    if (latest == null) return;

                    //If in a study, get from joined date onwards
                    String study_condition = "";
                    if (Aware.getSetting(getApplicationContext(), "study_id").length() > 0 && Aware.getSetting(getApplicationContext(), "study_start").length() > 0) {
                        study_condition = " AND timestamp > " + Long.parseLong(Aware.getSetting(getApplicationContext(), "study_start"));
                    }

                    //We always want to sync the device's profile
                    if (DATABASE_TABLE.equalsIgnoreCase("aware_device")) study_condition = "";

                    JSONArray remoteData = new JSONArray(latest);

                    Cursor context_data;
                    if (remoteData.length() == 0) {
                        if (exists(columnsStr, "double_end_timestamp")) {
                            context_data = getContentResolver().query(CONTENT_URI, null, "double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
                        } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                            context_data = getContentResolver().query(CONTENT_URI, null, "double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
                        } else {
                            context_data = getContentResolver().query(CONTENT_URI, null, "1" + study_condition, null, "timestamp ASC");
                        }
                    } else {
                        long last;
                        if (exists(columnsStr, "double_end_timestamp")) {
                            last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
                            context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
                        } else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
                            last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
                            context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
                        } else {
                            last = remoteData.getJSONObject(0).getLong("timestamp");
                            context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "timestamp ASC");
                        }
                    }

                    JSONArray context_data_entries = new JSONArray();
                    if (context_data != null && context_data.moveToFirst()) {

                        notifyUser("Syncing " + context_data.getCount() + " from " + DATABASE_TABLE, false);

                        if (DEBUG)
                            Log.d(Aware.TAG, "Syncing " + context_data.getCount() + " records from " + DATABASE_TABLE);

                        long start = System.currentTimeMillis();

                        do {
                            JSONObject entry = new JSONObject();

                            String[] columns = context_data.getColumnNames();
                            for (String c_name : columns) {

                                //Skip local database ID
                                if (c_name.equals("_id")) continue;

                                if (c_name.equals("timestamp") || c_name.contains("double")) {
                                    entry.put(c_name, context_data.getDouble(context_data.getColumnIndex(c_name)));
                                } else if (c_name.contains("float")) {
                                    entry.put(c_name, context_data.getFloat(context_data.getColumnIndex(c_name)));
                                } else if (c_name.contains("long")) {
                                    entry.put(c_name, context_data.getLong(context_data.getColumnIndex(c_name)));
                                } else if (c_name.contains("blob")) {
                                    entry.put(c_name, context_data.getBlob(context_data.getColumnIndex(c_name)));
                                } else if (c_name.contains("integer")) {
                                    entry.put(c_name, context_data.getInt(context_data.getColumnIndex(c_name)));
                                } else {
                                    entry.put(c_name, context_data.getString(context_data.getColumnIndex(c_name)));
                                }
                            }
                            context_data_entries.put(entry);

                            if (context_data_entries.length() == batch_size) {

                                request = new Hashtable<>();
                                request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
                                request.put("data", context_data_entries.toString());

                                if (protocol.equals("https")) {
                                    try {
                                        new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                                }

                                context_data_entries = new JSONArray();
                            }
                        } while (context_data.moveToNext());

                        if (context_data_entries.length() > 0) {

                            request = new Hashtable<>();
                            request.put(Aware_Preferences.DEVICE_ID, DEVICE_ID);
                            request.put("data", context_data_entries.toString());

                            if (protocol.equals("https")) {
                                try {
                                    new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), WEBSERVER)).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                new Http(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
                            }
                        }

                        if (DEBUG)
                            Log.d(Aware.TAG, DATABASE_TABLE + " sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis() - start) / 1000));

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

                        //Clean the local database, now that it is uploaded to the server, if required
                        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0 && Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA)) == 4
                                && highFrequencySensors.contains(DATABASE_TABLE)) {

                            context_data.moveToFirst();

                            double last = context_data.getDouble(context_data.getColumnIndex("timestamp"));
                            getContentResolver().delete(CONTENT_URI, "timestamp <= " + last, null);

                            if (DEBUG)
                                Log.d(Aware.TAG, "Deleted local old records for " + DATABASE_TABLE);

                            notifyUser("Cleaned old records from " + DATABASE_TABLE, false);
                        }
                    }
                    if (context_data != null && !context_data.isClosed()) context_data.close();

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
            Log.d(Aware.TAG, "Finished synching all the databases.");

        notifyUser("Sync complete.", true);
    }
}
