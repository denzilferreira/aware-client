package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.aware.ui.Stream_UI;
import com.aware.utils.Aware_Sensor;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class Wear_Sync extends Aware_Sensor implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public static String TAG = "AWARE::Android Wear";

    private static GoogleApiClient googleClient;
    private static Node peer;
    private static boolean is_watch = false;
    private AWAREListener awareListener;

    private static long last_sync = 0;

    private ArrayList<AWAREContentObserver> contentObservers = new ArrayList<AWAREContentObserver>();

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(this,Aware_Preferences.DEBUG_TAG):TAG;
        if(Aware.DEBUG) Log.d(TAG, "Android Wear synching created!");

        Cursor device = getContentResolver().query(Aware_Provider.Aware_Device.CONTENT_URI, null, null, null, "1 LIMIT 1");
        if( device != null && device.moveToFirst() ) {
            is_watch = device.getInt(device.getColumnIndex(Aware_Provider.Aware_Device.SDK))==20; //TODO: check if there is a better way to detect a watch...
        }
        if( device != null && ! device.isClosed() ) device.close();

        if( ! is_watch ) { //The phone will receive messages from the watch
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        } else { //The watch will just send messages
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();

            //Watch will monitor for changes in AWARE's settings
            awareListener = new AWAREListener();
            IntentFilter filter = new IntentFilter(Aware.ACTION_AWARE_CONFIG_CHANGED);
            registerReceiver(awareListener, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(this,Aware_Preferences.DEBUG_TAG):TAG;

        if( ! googleClient.isConnected() ) {
            googleClient.connect();

            //Get peers
            PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
            nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(NodeApi.GetConnectedNodesResult result) {
                    if( result.getNodes().size() > 0 ) {
                        peer = result.getNodes().get(0);
                        Log.d(TAG, "Connected to " + (is_watch?"smartphone":"watch")); //if we are on the watch, show smartphone and vice-versa.
                    }
                }
            });
        }

        return START_STICKY;
    }

    private boolean is_registered(AWAREContentObserver observer) {
        boolean found = false;
        for( AWAREContentObserver obs : contentObservers ) {
            if( obs.getContentProvider().toString().equals(observer.getContentProvider().toString())) {
                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * Will monitor changes in AWARE's settings
     */
    public class AWAREListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String setting = intent.getStringExtra(Aware.EXTRA_CONFIG_SETTING);
            String value = intent.getStringExtra(Aware.EXTRA_CONFIG_VALUE);

            if( setting.contains("status") && value.equals("true") ) {
                if( setting.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Installations_Provider.Installations_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Installations_Provider.Installations_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_BAROMETER) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Barometer_Provider.Barometer_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Barometer_Provider.Barometer_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_BATTERY) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Battery_Provider.Battery_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Battery_Provider.Battery_Data.CONTENT_URI, true, observer);
                    }
                    AWAREContentObserver charges = new AWAREContentObserver(new Handler());
                    charges.setContentProvider(Battery_Provider.Battery_Charges.CONTENT_URI);
                    if( ! is_registered(charges) ) {
                        contentObservers.add(charges);
                        getContentResolver().registerContentObserver(Battery_Provider.Battery_Charges.CONTENT_URI, true, charges);
                    }
                    AWAREContentObserver discharges = new AWAREContentObserver(new Handler());
                    discharges.setContentProvider(Battery_Provider.Battery_Discharges.CONTENT_URI);
                    if( ! is_registered(discharges) ) {
                        contentObservers.add(discharges);
                        getContentResolver().registerContentObserver(Battery_Provider.Battery_Discharges.CONTENT_URI, true, discharges);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_BLUETOOTH) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI, true, observer);
                    }
                    AWAREContentObserver device = new AWAREContentObserver(new Handler());
                    device.setContentProvider(Bluetooth_Provider.Bluetooth_Sensor.CONTENT_URI);
                    if( ! is_registered(device) ) {
                        contentObservers.add(device);
                        getContentResolver().registerContentObserver(Bluetooth_Provider.Bluetooth_Sensor.CONTENT_URI, true, device);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_GRAVITY) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Gravity_Provider.Gravity_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Gravity_Provider.Gravity_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_GRAVITY) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Gravity_Provider.Gravity_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Gravity_Provider.Gravity_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_GYROSCOPE) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_LIGHT) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Light_Provider.Light_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Light_Provider.Light_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_PROCESSOR) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Processor_Provider.Processor_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Processor_Provider.Processor_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_PROXIMITY) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Proximity_Provider.Proximity_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Proximity_Provider.Proximity_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_ROTATION) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Rotation_Provider.Rotation_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Rotation_Provider.Rotation_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_SCREEN) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Screen_Provider.Screen_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Screen_Provider.Screen_Data.CONTENT_URI, true, observer);
                    }
                }
                if( setting.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
                    AWAREContentObserver observer = new AWAREContentObserver(new Handler());
                    observer.setContentProvider(Temperature_Provider.Temperature_Data.CONTENT_URI);
                    if( ! is_registered(observer) ) {
                        contentObservers.add(observer);
                        getContentResolver().registerContentObserver(Temperature_Provider.Temperature_Data.CONTENT_URI, true, observer);
                    }
                }
            }

            if( setting.contains("status") && value.equals("false") ) {
                if (setting.equals(Aware_Preferences.STATUS_ACCELEROMETER)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_INSTALLATIONS)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Installations_Provider.Installations_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_BAROMETER)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Barometer_Provider.Barometer_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_BATTERY)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Battery_Provider.Battery_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Battery_Provider.Battery_Charges.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Battery_Provider.Battery_Discharges.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_BLUETOOTH)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Bluetooth_Provider.Bluetooth_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Bluetooth_Provider.Bluetooth_Sensor.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_GRAVITY)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Gravity_Provider.Gravity_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_GYROSCOPE)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Gyroscope_Provider.Gyroscope_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_LIGHT)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Light_Provider.Light_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Linear_Accelerometer_Provider.Linear_Accelerometer_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_MAGNETOMETER)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_PROCESSOR)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Processor_Provider.Processor_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_PROXIMITY)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Proximity_Provider.Proximity_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_ROTATION)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Rotation_Provider.Rotation_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_SCREEN)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Screen_Provider.Screen_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
                if (setting.equals(Aware_Preferences.STATUS_TEMPERATURE)) {
                    for(AWAREContentObserver obs : contentObservers ) {
                        if ( obs.getContentProvider().toString().equals(Temperature_Provider.Temperature_Data.CONTENT_URI.toString()) ) {
                            getContentResolver().unregisterContentObserver(obs);
                            contentObservers.remove(obs);
                            break;
                        }
                    }
                }
            }
        }
    }

    public static View getContextCard(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = inflater.inflate(R.layout.card_android_wear, null);

        TextView wear_status = (TextView) card.findViewById(R.id.wear_status);
        TextView wear_battery = (TextView) card.findViewById(R.id.wear_battery);
        TextView wear_last_sync = (TextView) card.findViewById(R.id.wear_last_sync);

        wear_status.setText("Status: " + (googleClient.isConnected()?"Connected":"Disconnected"));

        Cursor last_watch_battery = context.getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI, null, Aware_Preferences.DEVICE_ID + " NOT LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID) + "'", null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
        if( last_watch_battery != null && last_watch_battery.moveToFirst() ) {
            wear_battery.setText("Battery: " + last_watch_battery.getInt(last_watch_battery.getColumnIndex(Battery_Provider.Battery_Data.LEVEL)) + "%");
        } else {
            wear_battery.setVisibility(View.INVISIBLE);
        }
        if( last_watch_battery != null && ! last_watch_battery.isClosed() ) last_watch_battery.close();

        if( last_sync != 0 ) {
            SimpleDateFormat formatter = new SimpleDateFormat("MMM.d.yyyy h:m:s a");
            Date date_sync = new Date();
            date_sync.setTime(last_sync);
            wear_last_sync.setText(formatter.format(date_sync));
        } else {
            wear_last_sync.setText("N/A");
        }
        return card;
    }

    /**
     * AWARE sensor data observer
     */
    public class AWAREContentObserver extends ContentObserver {
        private Uri CONTENT_URI;

        public AWAREContentObserver(Handler handler) {
           super(handler);
        }

        public void setContentProvider(Uri uri) {
            CONTENT_URI = uri;
        }

        public Uri getContentProvider() {
            return CONTENT_URI;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            Cursor latest = getContentResolver().query(CONTENT_URI, null, null, null, "timestamp DESC LIMIT 1");
            if( latest != null && latest.moveToFirst() ) {
                JSONObject data = new JSONObject();
                try {
                    data.put("content_uri", CONTENT_URI.toString());
                    String[] columns = latest.getColumnNames();
                    for(String field : columns ) {
                        if( field.contains("timestamp") || field.contains("double") ) {
                            data.put(field, latest.getDouble(latest.getColumnIndex(field)));
                        } else {
                            data.put(field, latest.getString(latest.getColumnIndex(field)));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String message = data.toString();

                if( Aware.DEBUG ) Log.d(TAG,"Sending: " + message);
                Wearable.MessageApi.sendMessage(googleClient, peer.getId(),"/aware/data", message.getBytes());
            }
            if( latest != null && ! latest.isClosed() ) latest.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if( awareListener != null ) {
            unregisterReceiver(awareListener);
        }
        if( ! is_watch && googleClient != null && googleClient.isConnected() ) {
            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
    }

    private final IBinder serviceBinder = new ServiceBinder();

    //On Android Wear connected
    @Override
    public void onConnected(Bundle bundle) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connected!");
        }
    }

    //On Android Wear suspended
    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection suspended!");
        }
    }

    //On Android Wear failed
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

    /**
     * Service that listens to events from Android Wear
     */
    public static class WearListener extends WearableListenerService {
        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            super.onDataChanged(dataEvents);
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            super.onMessageReceived(messageEvent);

            try {
                JSONObject json = new JSONObject(new String(messageEvent.getData(), "UTF-8"));

                Uri content_uri = Uri.parse(json.getString("content_uri"));

                Iterator<String> keys = json.keys();
                ContentValues watch_data = new ContentValues();
                while( keys.hasNext() ) {

                    String key = (String)keys.next();

                    if( key.equals("_id") || key.equals("content_uri") ) continue;
                    if( key.contains("timestamp") || key.contains("double") ) {
                        watch_data.put(key, json.getDouble(key));
                    } else {
                        watch_data.put(key, json.getString(key));
                    }
                }
                try {
                    getContentResolver().insert(content_uri, watch_data);
                } catch(android.database.SQLException e ) {}

                if( Aware.DEBUG ) Log.d(TAG,"Received: Database = " + content_uri.toString() + " Data = " + watch_data.toString());

                last_sync = System.currentTimeMillis();

                Intent refresh_stream = new Intent(Stream_UI.ACTION_AWARE_UPDATE_STREAM);
                sendBroadcast(refresh_stream);
            }
            catch (UnsupportedEncodingException e) {}
            catch (JSONException e ) {}
        }

        @Override
        public void onPeerConnected(Node peer) {
            super.onPeerConnected(peer);
        }

        @Override
        public void onPeerDisconnected(Node peer) {
            super.onPeerDisconnected(peer);
            //Try to reconnect
            googleClient.connect();
        }
    }
}
