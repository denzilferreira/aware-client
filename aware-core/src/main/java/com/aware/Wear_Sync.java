package com.aware;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aware.providers.Accelerometer_Provider;
import com.aware.providers.Aware_Provider;
import com.aware.providers.Barometer_Provider;
import com.aware.providers.Battery_Provider;
import com.aware.providers.Bluetooth_Provider;
import com.aware.providers.Gravity_Provider;
import com.aware.providers.Gyroscope_Provider;
import com.aware.providers.Installations_Provider;
import com.aware.providers.Light_Provider;
import com.aware.providers.Linear_Accelerometer_Provider;
import com.aware.providers.Magnetometer_Provider;
import com.aware.providers.Processor_Provider;
import com.aware.providers.Proximity_Provider;
import com.aware.providers.Rotation_Provider;
import com.aware.providers.Screen_Provider;
import com.aware.providers.Temperature_Provider;
import com.aware.utils.Aware_Sensor;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class Wear_Sync extends Aware_Sensor implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String ACTION_AWARE_WEAR_MESSAGE = "ACTION_AWARE_WEAR_MESSAGE";
    public static final String EXTRA_TOPIC = "topic";
    public static final String EXTRA_MESSAGE = "message";

    public static String TAG = "AWARE::Android Wear";

    private static GoogleApiClient googleClient;
    private static Node peer;

    /**
     * Keeps track if the phone is connected to watch
     */
    private static boolean is_connected = false;

    public final static PutDataMapRequest accelerometer = PutDataMapRequest.create("/accelerometer");
    public final static PutDataMapRequest installations = PutDataMapRequest.create("/installations");
    public final static PutDataMapRequest barometer = PutDataMapRequest.create("/barometer");
    public final static PutDataMapRequest battery = PutDataMapRequest.create("/battery");
    public final static PutDataMapRequest bluetooth = PutDataMapRequest.create("/bluetooth");
    public final static PutDataMapRequest gravity = PutDataMapRequest.create("/gravity");
    public final static PutDataMapRequest gyroscope = PutDataMapRequest.create("/gyroscope");
    public final static PutDataMapRequest light = PutDataMapRequest.create("/light");
    public final static PutDataMapRequest linear = PutDataMapRequest.create("/linear");
    public final static PutDataMapRequest magnetometer = PutDataMapRequest.create("/magnetometer");
    public final static PutDataMapRequest processor = PutDataMapRequest.create("/processor");
    public final static PutDataMapRequest proximity = PutDataMapRequest.create("/proximity");
    public final static PutDataMapRequest rotation = PutDataMapRequest.create("/rotation");
    public final static PutDataMapRequest screen = PutDataMapRequest.create("/screen");
    public final static PutDataMapRequest temperature = PutDataMapRequest.create("/temperature");

    private AWAREListener settingListener = new AWAREListener();
    private WearMessageListener wearListener = new WearMessageListener();

    private AlarmManager alarmManager;
    private Intent wearBg;
    private PendingIntent wearBgRepeat;

    private static ArrayList<String> databases = new ArrayList<String>();

    private static final long FREQUENCY = 1 * 60 * 1000; //5 minutes

    private void addDatabase(String d) {
        boolean found = false;
        for( String dd : databases ) {
            if( dd.equals(d) ) {
                found = true;
                break;
            }
        }
        if( ! found ) {
            databases.add(d);
        }
    }

    private void removeDatabase(String d) {
        int index = -1;
        for(String dd : databases ) {
            index++;
            if( dd.equals(d) ) {
                databases.remove(index);
                break;
            }
        }
    }

    public static class Wear_Bg extends IntentService {
        public static final String ACTION_WEAR_SYNC = "ACTION_WEAR_SYNC";
        private static final int BUFFER = 100;

        public Wear_Bg() {
            super("Wear Background Sync");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            if( intent.getAction() != null && intent.getAction().equals(ACTION_WEAR_SYNC) ) {

                String database = intent.getStringExtra("content_uri");
                String latest_timestamp = intent.getStringExtra("latest_timestamp");

                Uri CONTENT_URI = Uri.parse(database);
                JSONArray data_array = new JSONArray();

                Cursor watch_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + latest_timestamp, null, "timestamp ASC");
                if( watch_data != null && watch_data.moveToFirst() ) {
                    Log.d(TAG, "Synching " + watch_data.getCount() + " rows from  "+ database + " to phone...");
                    do {
                        JSONObject data = new JSONObject();
                        try {
                            String[] columns = watch_data.getColumnNames();
                            for(String field : columns ) {
                                if( field.contains("_id") ) continue; //we don't need the local id
                                if (field.contains("timestamp") || field.contains("double")) {
                                    data.put(field, watch_data.getDouble(watch_data.getColumnIndex(field)));
                                } else {
                                    data.put(field, watch_data.getString(watch_data.getColumnIndex(field)));
                                }
                            }
                            data_array.put(data);

                            if( data_array.length() == BUFFER) {
                                new WearAsync(CONTENT_URI).execute(data_array);
                                data_array = new JSONArray();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } while( watch_data.moveToNext() );

                    if( data_array.length() > 0 ) {
                        new WearAsync(CONTENT_URI).execute(data_array);
                    }
                }
                if( watch_data != null && ! watch_data.isClosed() ) watch_data.close();
            } else {
                Log.d(TAG, "Background sync running! Databases to sync: " + databases.size());
                for( String database : databases ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/get_latest", database.getBytes());
                }
            }
        }

        /**
         * Send data to the phone, asynchronously
         */
        public class WearAsync extends AsyncTask<JSONArray, Void, Void> {
            private Uri CONTENT_URI;

            public WearAsync(Uri content_uri) {
                CONTENT_URI = content_uri;
            }

            @Override
            protected Void doInBackground(JSONArray... data) {
                JSONArray data_array = data[0];

                PutDataRequest request = null;
                if( CONTENT_URI.toString().equals(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString()) ) {
                    accelerometer.getDataMap().putString("content_uri", Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString());
                    accelerometer.getDataMap().putString("json", data_array.toString());
                    request = accelerometer.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Installations_Provider.Installations_Data.CONTENT_URI.toString()) ) {
                    installations.getDataMap().putString("content_uri", Installations_Provider.Installations_Data.CONTENT_URI.toString());
                    installations.getDataMap().putString("json", data_array.toString());
                    request = installations.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Barometer_Provider.Barometer_Data.CONTENT_URI.toString()) ) {
                    barometer.getDataMap().putString("content_uri", Barometer_Provider.Barometer_Data.CONTENT_URI.toString());
                    barometer.getDataMap().putString("json", data_array.toString());
                    request = barometer.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Battery_Provider.Battery_Data.CONTENT_URI.toString()) ) {
                    battery.getDataMap().putString("content_uri", Battery_Provider.Battery_Data.CONTENT_URI.toString());
                    battery.getDataMap().putString("json", data_array.toString());
                    request = battery.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString()) ) {
                    bluetooth.getDataMap().putString("content_uri", Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString());
                    bluetooth.getDataMap().putString("json", data_array.toString());
                    request = bluetooth.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Gravity_Provider.Gravity_Data.CONTENT_URI.toString()) ) {
                    gravity.getDataMap().putString("content_uri", Gravity_Provider.Gravity_Data.CONTENT_URI.toString());
                    gravity.getDataMap().putString("json", data_array.toString());
                    request = gravity.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString()) ) {
                    gyroscope.getDataMap().putString("content_uri", Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString());
                    gyroscope.getDataMap().putString("json", data_array.toString());
                    request = gyroscope.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Light_Provider.Light_Data.CONTENT_URI.toString()) ) {
                    light.getDataMap().putString("content_uri", Light_Provider.Light_Data.CONTENT_URI.toString());
                    light.getDataMap().putString("json", data_array.toString());
                    request = light.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString()) ) {
                    linear.getDataMap().putString("content_uri", Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString());
                    linear.getDataMap().putString("json", data_array.toString());
                    request = linear.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString()) ) {
                    magnetometer.getDataMap().putString("content_uri", Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString());
                    magnetometer.getDataMap().putString("json", data_array.toString());
                    request = magnetometer.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Processor_Provider.Processor_Data.CONTENT_URI.toString()) ) {
                    processor.getDataMap().putString("content_uri", Processor_Provider.Processor_Data.CONTENT_URI.toString());
                    processor.getDataMap().putString("json", data_array.toString());
                    request = processor.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Proximity_Provider.Proximity_Data.CONTENT_URI.toString()) ) {
                    proximity.getDataMap().putString("content_uri", Proximity_Provider.Proximity_Data.CONTENT_URI.toString());
                    proximity.getDataMap().putString("json", data_array.toString());
                    request = proximity.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Rotation_Provider.Rotation_Data.CONTENT_URI.toString()) ) {
                    rotation.getDataMap().putString("content_uri", Rotation_Provider.Rotation_Data.CONTENT_URI.toString());
                    rotation.getDataMap().putString("json", data_array.toString());
                    request = rotation.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Screen_Provider.Screen_Data.CONTENT_URI.toString()) ) {
                    screen.getDataMap().putString("content_uri", Screen_Provider.Screen_Data.CONTENT_URI.toString());
                    screen.getDataMap().putString("json", data_array.toString());
                    request = screen.asPutDataRequest();
                }
                if( CONTENT_URI.toString().equals(Temperature_Provider.Temperature_Data.CONTENT_URI.toString()) ) {
                    temperature.getDataMap().putString("content_uri", Temperature_Provider.Temperature_Data.CONTENT_URI.toString());
                    temperature.getDataMap().putString("json", data_array.toString());
                    request = temperature.asPutDataRequest();
                }
                Wearable.DataApi.putDataItem(googleClient, request);
                return null;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(this,Aware_Preferences.DEBUG_TAG):TAG;
        if(Aware.DEBUG) Log.d(TAG, "Android Wear synching created!");

        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        IntentFilter filter = new IntentFilter(Aware.ACTION_AWARE_CONFIG_CHANGED);
        registerReceiver(settingListener, filter);

        IntentFilter wearFilter = new IntentFilter(ACTION_AWARE_WEAR_MESSAGE);
        registerReceiver(wearListener, wearFilter);

        if( Aware.is_watch(this) ) {
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            wearBg = new Intent(this, Wear_Bg.class);
            wearBgRepeat = PendingIntent.getService(this, 0, wearBg, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, FREQUENCY, wearBgRepeat);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(this,Aware_Preferences.DEBUG_TAG):TAG;

        if( ! googleClient.isConnected() ) {
            googleClient.connect();
        }

        //Get peers
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if( result.getNodes().size() > 0 ) {
                    peer = result.getNodes().get(0);
                    Log.d( TAG, "Connected to " + ( Aware.is_watch(getApplicationContext() ) ? "smartphone" : "watch") ); //if we are on the watch, show smartphone and vice-versa.
                    is_connected = true;

                    if( Aware.is_watch(getApplicationContext()) ) {
                        Cursor active_sensors = getContentResolver().query(Aware_Provider.Aware_Settings.CONTENT_URI, null, Aware_Provider.Aware_Settings.SETTING_KEY + " LIKE '%STATUS_%' AND " + Aware_Provider.Aware_Settings.SETTING_PACKAGE_NAME +" LIKE 'com.aware' AND " + Aware_Provider.Aware_Settings.SETTING_VALUE +" LIKE 'true'", null, null);
                        if( active_sensors != null && active_sensors.moveToFirst() ) {
                            do{
                                Log.d(TAG,"ACTIVE ON WATCH: " + active_sensors.getString(active_sensors.getColumnIndex(Aware_Provider.Aware_Settings.SETTING_KEY)));

                                Intent wear_change = new Intent(Aware.ACTION_AWARE_CONFIG_CHANGED);
                                wear_change.putExtra(Aware.EXTRA_CONFIG_SETTING, active_sensors.getString(active_sensors.getColumnIndex(Aware_Provider.Aware_Settings.SETTING_KEY)));
                                wear_change.putExtra(Aware.EXTRA_CONFIG_VALUE, active_sensors.getString(active_sensors.getColumnIndex(Aware_Provider.Aware_Settings.SETTING_VALUE)));
                                sendBroadcast(wear_change);

                            }while(active_sensors.moveToNext());
                        }
                        if( active_sensors != null && ! active_sensors.isClosed() ) active_sensors.close();
                    }
                } else {
                    is_connected = false;
                }
            }
        });
        return START_STICKY;
    }

    /**
     * Receives messages from phone
     */
    public class WearMessageListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Wear_Sync.ACTION_AWARE_WEAR_MESSAGE) ) {

                String topic = intent.getStringExtra(EXTRA_TOPIC);
                String message = new String(intent.getStringExtra(EXTRA_MESSAGE));

                if( topic.equals("latest") ) {
                    try{
                        JSONObject data = new JSONObject(message);

                        String content_uri = data.getString("content_uri");
                        String latest_timestamp = data.getString("latest_timestamp");

                        Intent wearbg = new Intent(context, Wear_Bg.class);
                        wearbg.setAction(Wear_Bg.ACTION_WEAR_SYNC);
                        wearbg.putExtra("content_uri", content_uri);
                        wearbg.putExtra("latest_timestamp", latest_timestamp);
                        context.startService(wearbg);

                    } catch(JSONException e) {
                        Log.d(TAG, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Monitors AWARE's sensors enable/disable
     */
    public class AWAREListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String setting = intent.getStringExtra(Aware.EXTRA_CONFIG_SETTING);
            String value = intent.getStringExtra(Aware.EXTRA_CONFIG_VALUE);

            //Notify the phone that we are changing settings on the watch's client
            if( Aware.is_watch(context) ) {
                Log.d(TAG, "Watch received a change in settings");

                try {
                    JSONObject message = new JSONObject();
                    message.put("command","config");
                    message.put(Aware.EXTRA_CONFIG_SETTING, setting);
                    message.put(Aware.EXTRA_CONFIG_VALUE, value);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/wear_sync", message.toString().getBytes());
                } catch( JSONException e ) {}

            } else {
                Log.d(TAG, "Phone received a change in settings");
            }

            if( setting.contains("status") && value.equals("true") ) {
                if( setting.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
                    addDatabase(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
                    addDatabase(Installations_Provider.Installations_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BAROMETER) ) {
                    addDatabase(Barometer_Provider.Barometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BATTERY) ) {
                    addDatabase(Battery_Provider.Battery_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BLUETOOTH)) {
                    addDatabase(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_GRAVITY)  ) {
                    addDatabase(Gravity_Provider.Gravity_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_GYROSCOPE) ) {
                    addDatabase(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_LIGHT) ) {
                    addDatabase(Light_Provider.Light_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER)) {
                    addDatabase(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
                    addDatabase(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_PROCESSOR)  ) {
                    addDatabase(Processor_Provider.Processor_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_PROXIMITY)  ) {
                    addDatabase(Proximity_Provider.Proximity_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_ROTATION) ) {
                    addDatabase(Rotation_Provider.Rotation_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_SCREEN) ) {
                    addDatabase(Screen_Provider.Screen_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
                    addDatabase(Temperature_Provider.Temperature_Data.CONTENT_URI.toString());
                }
            }

            if( setting.contains("status") && value.equals("false") ) {
                if( setting.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
                    removeDatabase(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
                    removeDatabase(Installations_Provider.Installations_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BAROMETER) ) {
                    removeDatabase(Barometer_Provider.Barometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BATTERY) ) {
                    removeDatabase(Battery_Provider.Battery_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_BLUETOOTH)) {
                    removeDatabase(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_GRAVITY)  ) {
                    removeDatabase(Gravity_Provider.Gravity_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_GYROSCOPE) ) {
                    removeDatabase(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_LIGHT) ) {
                    removeDatabase(Light_Provider.Light_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER)) {
                    removeDatabase(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
                    removeDatabase(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_PROCESSOR)  ) {
                    removeDatabase(Processor_Provider.Processor_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_PROXIMITY)  ) {
                    removeDatabase(Proximity_Provider.Proximity_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_ROTATION) ) {
                    removeDatabase(Rotation_Provider.Rotation_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_SCREEN) ) {
                    removeDatabase(Screen_Provider.Screen_Data.CONTENT_URI.toString());
                }
                if( setting.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
                    removeDatabase(Temperature_Provider.Temperature_Data.CONTENT_URI.toString());
                }
            }
        }
    }

    /**
     * Get Android Wear's contextual card
     * @param context
     * @return
     */
    public static View getContextCard(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = inflater.inflate(R.layout.card_android_wear, null);

        TextView wear_status = (TextView) card.findViewById(R.id.wear_status);
        TextView wear_battery = (TextView) card.findViewById(R.id.wear_battery);
        TextView wear_last_sync = (TextView) card.findViewById(R.id.wear_last_sync);

        wear_status.setText("Status: " + ( is_connected ? "Connected" : "Disconnected" ) );

        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(System.currentTimeMillis());
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE,0);
        today.set(Calendar.SECOND,0);

        Cursor last_watch_battery = context.getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI, null, Aware_Preferences.DEVICE_ID + " NOT LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + "' AND timestamp > " + today.getTimeInMillis(), null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
        if( last_watch_battery != null && last_watch_battery.moveToFirst() ) {
            wear_battery.setText("Battery: " + last_watch_battery.getInt(last_watch_battery.getColumnIndex(Battery_Provider.Battery_Data.LEVEL)) + "%");
        } else {
            wear_battery.setText("Battery: N/A");
        }
        if( last_watch_battery != null && ! last_watch_battery.isClosed() ) last_watch_battery.close();

        if( Wear_Service.last_sync != 0 ) {
            SimpleDateFormat formatter = new SimpleDateFormat("MMM.d.yyyy h:m:s a");
            Date date_sync = new Date();
            date_sync.setTime(Wear_Service.last_sync);
            wear_last_sync.setText(formatter.format(date_sync));
        } else {
            wear_last_sync.setText("N/A");
        }
        return card;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( Aware.is_watch( this ) ) {
            alarmManager.cancel(wearBgRepeat);
        }

        if( googleClient != null && googleClient.isConnected() ) {
            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
        unregisterReceiver(settingListener);
        unregisterReceiver(wearListener);
    }

    private final IBinder serviceBinder = new ServiceBinder();

    @Override
    public void onConnected(Bundle bundle) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connected!");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection suspended!");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection failed!");
        }
    }

    public class ServiceBinder extends Binder {
        Wear_Sync getService() { return Wear_Sync.getService(); }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    private static Wear_Sync wear_sync = Wear_Sync.getService();

    public static Wear_Sync getService() {
        if( wear_sync == null ) wear_sync = new Wear_Sync();
        return wear_sync;
    }
}
