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

    private static WearMessageListener wearListener = new WearMessageListener();

    private AlarmManager alarmManager;
    private Intent wearBg;
    private PendingIntent wearBgRepeat;

    private static final long FREQUENCY = 5 * 60 * 1000; //sync data to phone every 5 minutes

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
                double latest_timestamp = intent.getDoubleExtra("latest_timestamp", 0);

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
                //Get AWARE's content providers' data
                ArrayList<String> databases = new ArrayList<String>();
                databases.add(Aware_Provider.Aware_Device.CONTENT_URI.toString());
                databases.add(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString());
                databases.add(Accelerometer_Provider.Accelerometer_Sensor.CONTENT_URI.toString());
                databases.add(Installations_Provider.Installations_Data.CONTENT_URI.toString());
                databases.add(Barometer_Provider.Barometer_Data.CONTENT_URI.toString());
                databases.add(Barometer_Provider.Barometer_Sensor.CONTENT_URI.toString());
                databases.add(Battery_Provider.Battery_Data.CONTENT_URI.toString());
                databases.add(Battery_Provider.Battery_Charges.CONTENT_URI.toString());
                databases.add(Battery_Provider.Battery_Discharges.CONTENT_URI.toString());
                databases.add(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString());
                databases.add(Bluetooth_Provider.Bluetooth_Sensor.CONTENT_URI.toString());
                databases.add(Gravity_Provider.Gravity_Data.CONTENT_URI.toString());
                databases.add(Gravity_Provider.Gravity_Sensor.CONTENT_URI.toString());
                databases.add(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString());
                databases.add(Gyroscope_Provider.Gyroscope_Sensor.CONTENT_URI.toString());
                databases.add(Light_Provider.Light_Data.CONTENT_URI.toString());
                databases.add(Light_Provider.Light_Sensor.CONTENT_URI.toString());
                databases.add(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString());
                databases.add(Linear_Accelerometer_Provider.Linear_Accelerometer_Sensor.CONTENT_URI.toString());
                databases.add(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString());
                databases.add(Magnetometer_Provider.Magnetometer_Sensor.CONTENT_URI.toString());
                databases.add(Processor_Provider.Processor_Data.CONTENT_URI.toString());
                databases.add(Proximity_Provider.Proximity_Data.CONTENT_URI.toString());
                databases.add(Proximity_Provider.Proximity_Sensor.CONTENT_URI.toString());
                databases.add(Rotation_Provider.Rotation_Data.CONTENT_URI.toString());
                databases.add(Rotation_Provider.Rotation_Sensor.CONTENT_URI.toString());
                databases.add(Screen_Provider.Screen_Data.CONTENT_URI.toString());
                databases.add(Temperature_Provider.Temperature_Data.CONTENT_URI.toString());
                databases.add(Temperature_Provider.Temperature_Sensor.CONTENT_URI.toString());

                for( String database : databases ) {
                    if( DEBUG ) Log.d(TAG,"Wear sync running: " + database);
                    if( googleClient != null && peer != null ) {
                        Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/get_latest", database.getBytes());
                    }
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
                if( googleClient != null && request != null ){
                    Wearable.DataApi.putDataItem(googleClient, request);
                }
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

        IntentFilter wearFilter = new IntentFilter(ACTION_AWARE_WEAR_MESSAGE);
        registerReceiver(wearListener, wearFilter);

        if( Aware.is_watch(this) ) {
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            wearBg = new Intent(this, Wear_Bg.class);
            wearBgRepeat = PendingIntent.getService(this, 0, wearBg, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 1000, FREQUENCY, wearBgRepeat);
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
                } else {
                    is_connected = false;
                }
            }
        });
        return START_STICKY;
    }

    /**
     * Phone needs to tell the watch what he has...
     */
    public static class WearMessageListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Wear_Sync.ACTION_AWARE_WEAR_MESSAGE) ) {
                String topic = intent.getStringExtra(EXTRA_TOPIC);
                String message = new String(intent.getStringExtra(EXTRA_MESSAGE));

                if( topic.equals("latest") ) {
                    try{
                        JSONObject data = new JSONObject(message);
                        if( googleClient != null && peer != null ) {
                            Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/start_sync", data.toString().getBytes());
                        }
                    } catch(JSONException e) {
                        Log.d(TAG, e.getMessage());
                    }
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
