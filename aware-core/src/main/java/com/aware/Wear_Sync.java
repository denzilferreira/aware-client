package com.aware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aware.providers.Accelerometer_Provider;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Wear_Sync extends Aware_Sensor implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String ACTION_AWARE_WEAR_MESSAGE_RECEIVED = "ACTION_AWARE_WEAR_MESSAGE_RECEIVED";
    public static final String EXTRA_MESSAGE = "message";

    public static String TAG = "AWARE::Android Wear";

    private static GoogleApiClient googleClient;
    private static Node peer;

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

    private static AWAREContentObserver accelerometerObs,
            installationsObs, barometerObs, batteryObs,
            bluetoothObs, gravityObs, gyroscopeObs, lightObs,
            linearObs, magnetometerObs, processorObs, proximityObs,
            rotationObs, screenObs, temperatureObs;

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(this,Aware_Preferences.DEBUG_TAG):TAG;
        if(Aware.DEBUG) Log.d(TAG, "Android Wear synching created!");

        //Phone manages the connection
        if( ! Aware.is_watch(getApplicationContext()) ) {
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

        //Watch listens for changes in AWARE's settings to replicate data to phone.
        } else {
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();

            //Report battery to smartphone
            Aware.setSetting(this, Aware_Preferences.STATUS_BATTERY, true);
            Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
            sendBroadcast(apply);
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
                    Log.d(TAG, "Connected to " + (Aware.is_watch(getApplicationContext())?"smartphone":"watch")); //if we are on the watch, show smartphone and vice-versa.
                    is_connected = true;
                } else {
                    is_connected = false;
                }
            }
        });
        return START_STICKY;
    }

    /**
     * Monitors AWARE's sensors enable/disable
     */
    public static class AWAREListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( ! intent.getAction().equals(Aware.ACTION_AWARE_CONFIG_CHANGED) ) return;

            String setting = intent.getStringExtra(Aware.EXTRA_CONFIG_SETTING);
            String value = intent.getStringExtra(Aware.EXTRA_CONFIG_VALUE);

            if( setting.contains("status") && value.equals("true") ) {
                if( setting.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
                    accelerometerObs = new AWAREContentObserver(new Handler(), Accelerometer_Provider.Accelerometer_Data.CONTENT_URI, "accelerometer", context);
                    context.getContentResolver().registerContentObserver(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI, true, accelerometerObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
                    installationsObs = new AWAREContentObserver(new Handler(), Installations_Provider.Installations_Data.CONTENT_URI, "installations", context);
                    context.getContentResolver().registerContentObserver(Installations_Provider.Installations_Data.CONTENT_URI, true, installationsObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_BAROMETER) ) {
                    barometerObs = new AWAREContentObserver(new Handler(),Barometer_Provider.Barometer_Data.CONTENT_URI,"barometer", context);
                    context.getContentResolver().registerContentObserver(Barometer_Provider.Barometer_Data.CONTENT_URI, true, barometerObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_BATTERY) ) {
                    batteryObs = new AWAREContentObserver(new Handler(), Battery_Provider.Battery_Data.CONTENT_URI, "battery", context);
                    context.getContentResolver().registerContentObserver(Battery_Provider.Battery_Data.CONTENT_URI, true, batteryObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_BLUETOOTH) ) {
                    bluetoothObs = new AWAREContentObserver(new Handler(), Bluetooth_Provider.Bluetooth_Data.CONTENT_URI, "bluetooth", context);
                    context.getContentResolver().registerContentObserver(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI, true, bluetoothObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_GRAVITY) ) {
                    gravityObs = new AWAREContentObserver(new Handler(), Gravity_Provider.Gravity_Data.CONTENT_URI, "gravity", context);
                    context.getContentResolver().registerContentObserver(Gravity_Provider.Gravity_Data.CONTENT_URI, true, gravityObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_GYROSCOPE) ) {
                    gyroscopeObs = new AWAREContentObserver(new Handler(), Gyroscope_Provider.Gyroscope_Data.CONTENT_URI, "gyroscope", context);
                    context.getContentResolver().registerContentObserver(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI, true, gyroscopeObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_LIGHT) ) {
                    lightObs = new AWAREContentObserver(new Handler(), Light_Provider.Light_Data.CONTENT_URI, "light", context);
                    context.getContentResolver().registerContentObserver(Light_Provider.Light_Data.CONTENT_URI, true, lightObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER) ) {
                    linearObs = new AWAREContentObserver(new Handler(), Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI, "linear", context);
                    context.getContentResolver().registerContentObserver(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI, true, linearObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
                    magnetometerObs = new AWAREContentObserver(new Handler(), Magnetometer_Provider.Magnetometer_Data.CONTENT_URI, "magnetometer", context);
                    context.getContentResolver().registerContentObserver(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI, true, magnetometerObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_PROCESSOR) ) {
                    processorObs = new AWAREContentObserver(new Handler(), Processor_Provider.Processor_Data.CONTENT_URI, "processor", context);
                    context.getContentResolver().registerContentObserver(Processor_Provider.Processor_Data.CONTENT_URI, true, processorObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_PROXIMITY) ) {
                    proximityObs = new AWAREContentObserver(new Handler(), Proximity_Provider.Proximity_Data.CONTENT_URI, "proximity", context);
                    context.getContentResolver().registerContentObserver(Proximity_Provider.Proximity_Data.CONTENT_URI, true, proximityObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_ROTATION) ) {
                    rotationObs = new AWAREContentObserver(new Handler(), Rotation_Provider.Rotation_Data.CONTENT_URI, "rotation", context);
                    context.getContentResolver().registerContentObserver(Rotation_Provider.Rotation_Data.CONTENT_URI, true, rotationObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_SCREEN) ) {
                    screenObs = new AWAREContentObserver(new Handler(), Screen_Provider.Screen_Data.CONTENT_URI, "screen", context);
                    context.getContentResolver().registerContentObserver(Screen_Provider.Screen_Data.CONTENT_URI, true, screenObs);
                }
                if( setting.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
                    temperatureObs = new AWAREContentObserver(new Handler(), Temperature_Provider.Temperature_Data.CONTENT_URI, "temperature", context);
                    context.getContentResolver().registerContentObserver(Temperature_Provider.Temperature_Data.CONTENT_URI, true, temperatureObs);
                }
            }

            if( setting.contains("status") && value.equals("false") ) {
                if (setting.equals(Aware_Preferences.STATUS_ACCELEROMETER)) {
                    if( accelerometerObs != null ) context.getContentResolver().unregisterContentObserver(accelerometerObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_INSTALLATIONS)) {
                    if( installationsObs != null ) context.getContentResolver().unregisterContentObserver(installationsObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_BAROMETER)) {
                    if( barometerObs != null ) context.getContentResolver().unregisterContentObserver(barometerObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_BATTERY)) {
                    if( batteryObs != null ) context.getContentResolver().unregisterContentObserver(batteryObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_BLUETOOTH)) {
                    if( bluetoothObs != null ) context.getContentResolver().unregisterContentObserver(bluetoothObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_GRAVITY)) {
                    if( gravityObs != null ) context.getContentResolver().unregisterContentObserver(gravityObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_GYROSCOPE)) {
                    if( gyroscopeObs != null ) context.getContentResolver().unregisterContentObserver(gyroscopeObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_LIGHT)) {
                    if( lightObs != null ) context.getContentResolver().unregisterContentObserver(lightObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER)) {
                    if( linearObs != null ) context.getContentResolver().unregisterContentObserver(linearObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_MAGNETOMETER)) {
                    if( magnetometerObs != null ) context.getContentResolver().unregisterContentObserver(magnetometerObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_PROCESSOR)) {
                    if( processorObs != null ) context.getContentResolver().unregisterContentObserver(processorObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_PROXIMITY)) {
                    if( proximityObs != null ) context.getContentResolver().unregisterContentObserver(proximityObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_ROTATION)) {
                    if( rotationObs != null ) context.getContentResolver().unregisterContentObserver(rotationObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_SCREEN)) {
                    if( screenObs != null ) context.getContentResolver().unregisterContentObserver(screenObs);
                }
                if (setting.equals(Aware_Preferences.STATUS_TEMPERATURE)) {
                    if( temperatureObs != null ) context.getContentResolver().unregisterContentObserver(temperatureObs);
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

    /**
     * AWARE sensor data observer
     * - Sends data from watch to smartphone as we record it.
     */
    public static class AWAREContentObserver extends ContentObserver {
        private final Uri CONTENT_URI;
        private final String PATH;
        private final Context CONTEXT;

        public AWAREContentObserver(Handler handler, Uri content_uri, String watch_path, Context context) {
           super(handler);
           CONTENT_URI = content_uri;
           PATH = watch_path;
           CONTEXT = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            Cursor latest = CONTEXT.getContentResolver().query(CONTENT_URI, null, null, null, "timestamp DESC LIMIT 1");
            if( latest != null && latest.moveToFirst() ) {
                JSONObject data = new JSONObject();
                try {
                    data.put("content_uri", CONTENT_URI.toString());
                    String[] columns = latest.getColumnNames();
                    for(String field : columns ) {
                        if (field.contains("timestamp") || field.contains("double")) {
                            data.put(field, latest.getDouble(latest.getColumnIndex(field)));
                        } else {
                            data.put(field, latest.getString(latest.getColumnIndex(field)));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                PutDataRequest request = null;
                if( PATH.equals("accelerometer") ) {
                    accelerometer.getDataMap().putString("json", data.toString());
                    request = accelerometer.asPutDataRequest();
                }
                if( PATH.equals("installations") ) {
                    installations.getDataMap().putString("json", data.toString());
                    request = installations.asPutDataRequest();
                }
                if( PATH.equals("barometer") ) {
                    barometer.getDataMap().putString("json", data.toString());
                    request = barometer.asPutDataRequest();
                }
                if( PATH.equals("battery") ) {
                    battery.getDataMap().putString("json", data.toString());
                    request = battery.asPutDataRequest();
                }
                if( PATH.equals("bluetooth") ) {
                    bluetooth.getDataMap().putString("json", data.toString());
                    request = bluetooth.asPutDataRequest();
                }
                if( PATH.equals("gravity") ) {
                    gravity.getDataMap().putString("json", data.toString());
                    request = gravity.asPutDataRequest();
                }
                if( PATH.equals("gyroscope") ) {
                    gyroscope.getDataMap().putString("json", data.toString());
                    request = gyroscope.asPutDataRequest();
                }
                if( PATH.equals("light") ) {
                    light.getDataMap().putString("json", data.toString());
                    request = light.asPutDataRequest();
                }
                if( PATH.equals("linear") ) {
                    linear.getDataMap().putString("json", data.toString());
                    request = linear.asPutDataRequest();
                }
                if( PATH.equals("magnetometer") ) {
                    magnetometer.getDataMap().putString("json", data.toString());
                    request = magnetometer.asPutDataRequest();
                }
                if( PATH.equals("processor") ) {
                    processor.getDataMap().putString("json", data.toString());
                    request = processor.asPutDataRequest();
                }
                if( PATH.equals("proximity") ) {
                    proximity.getDataMap().putString("json", data.toString());
                    request = proximity.asPutDataRequest();
                }
                if( PATH.equals("rotation") ) {
                    rotation.getDataMap().putString("json", data.toString());
                    request = rotation.asPutDataRequest();
                }
                if( PATH.equals("screen") ) {
                    screen.getDataMap().putString("json", data.toString());
                    request = screen.asPutDataRequest();
                }
                if( PATH.equals("temperature") ) {
                    temperature.getDataMap().putString("json", data.toString());
                    request = temperature.asPutDataRequest();
                }
                Wearable.DataApi.putDataItem(googleClient, request);
            }
            if( latest != null && ! latest.isClosed() ) latest.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if( ! Aware.is_watch(this) && googleClient != null && googleClient.isConnected() ) {
            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
    }

    private final IBinder serviceBinder = new ServiceBinder();

    @Override
    public void onConnected(Bundle bundle) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connected!");
        }
        is_connected = true;
    }

    //On Android Wear suspended
    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection suspended!");
        }
        is_connected = false;
    }

    //On Android Wear failed
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection failed!");
        }
        is_connected = false;
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
