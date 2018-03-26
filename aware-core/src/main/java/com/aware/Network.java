
package com.aware;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncRequest;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.aware.providers.Network_Provider;
import com.aware.providers.Network_Provider.Network_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Network context
 * - on/off events
 * - internet availability
 *
 * @author denzil
 */
public class Network extends Aware_Sensor {

    private static String TAG = "AWARE::Network";

    /**
     * Network type: airplane (constant = -1 )
     */
    public static final int NETWORK_TYPE_AIRPLANE = -1;

    /**
     * Network type: Wi-Fi ( constant = 1 )
     */
    public static final int NETWORK_TYPE_WIFI = 1;

    /**
     * Network type: Bluetooth ( constant = 2 )
     */
    public static final int NETWORK_TYPE_BLUETOOTH = 2;

    /**
     * Network type: GPS ( constant = 3 )
     */
    public static final int NETWORK_TYPE_GPS = 3;

    /**
     * Network type: Mobile ( constant = 4 )
     */
    public static final int NETWORK_TYPE_MOBILE = 4;

    /**
     * Network type: WIMAX ( constant = 5 )
     */
    public static final int NETWORK_TYPE_WIMAX = 5;

    /**
     * Broadcasted event: airplane is active
     */
    public static final String ACTION_AWARE_AIRPLANE_ON = "ACTION_AWARE_AIRPLANE_ON";

    /**
     * Broadcasted event: airplane is inactive
     */
    public static final String ACTION_AWARE_AIRPLANE_OFF = "ACTION_AWARE_AIRPLANE_OFF";

    /**
     * Broadcasted event: wifi is active
     */
    public static final String ACTION_AWARE_WIFI_ON = "ACTION_AWARE_WIFI_ON";

    /**
     * Broadcasted event: wifi is inactive
     */
    public static final String ACTION_AWARE_WIFI_OFF = "ACTION_AWARE_WIFI_OFF";

    /**
     * Broadcasted event: mobile is active
     */
    public static final String ACTION_AWARE_MOBILE_ON = "ACTION_AWARE_MOBILE_ON";

    /**
     * Broadcasted event: mobile is inactive
     */
    public static final String ACTION_AWARE_MOBILE_OFF = "ACTION_AWARE_MOBILE_OFF";

    /**
     * Broadcasted event: wimax is active
     */
    public static final String ACTION_AWARE_WIMAX_ON = "ACTION_AWARE_WIMAX_ON";

    /**
     * Broadcasted event: wimax is inactive
     */
    public static final String ACTION_AWARE_WIMAX_OFF = "ACTION_AWARE_WIMAX_OFF";

    /**
     * Broadcasted event: bluetooth is active
     */
    public static final String ACTION_AWARE_BLUETOOTH_ON = "ACTION_AWARE_BLUETOOTH_ON";

    /**
     * Broadcasted event: bluetooth is inactive
     */
    public static final String ACTION_AWARE_BLUETOOTH_OFF = "ACTION_AWARE_BLUETOOTH_OFF";

    /**
     * Broadcasted event: GPS is active
     */
    public static final String ACTION_AWARE_GPS_ON = "ACTION_AWARE_GPS_ON";

    /**
     * Broadcasted event: GPS is inactive
     */
    public static final String ACTION_AWARE_GPS_OFF = "ACTION_AWARE_GPS_OFF";

    /**
     * Broadcasted event: internet access is available
     */
    public static final String ACTION_AWARE_INTERNET_AVAILABLE = "ACTION_AWARE_INTERNET_AVAILABLE";

    /**
     * Broadcasted event: internet access is unavailable
     */
    public static final String ACTION_AWARE_INTERNET_UNAVAILABLE = "ACTION_AWARE_INTERNET_UNAVAILABLE";

    /**
     * Network status is ON (constant = 1)
     */
    public static final int STATUS_ON = 1;

    /**
     * Network status is OFF (constant = 0)
     */
    public static final int STATUS_OFF = 0;

    /**
     * Extra for ACTION_AWARE_INTERNET_AVAILABLE
     * String "internet_access"
     */
    public static final String EXTRA_ACCESS = "internet_access";

    private static ConnectivityManager connManager = null; //tracks connectivity to internet
    private static LocationManager locationManager = null; //tracks gps status
    private static TelephonyManager teleManager = null; //tracks phone network availability

    private static Network.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Network.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Network.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onInternetON();

        void onInternetOFF();

        void onGPSON();

        void onGPSOFF();

        void onBluetoothON();

        void onBluetoothOFF();

        void onWimaxON();

        void onWimaxOFF();

        void onNetworkDataON();

        void onNetworkDataOFF();

        void onWiFiON();

        void onWiFiOFF();

        void onAirplaneON();

        void onAirplaneOFF();
    }

    private PhoneStateListener phoneListener = new PhoneStateListener() {
        public void onServiceStateChanged(android.telephony.ServiceState serviceState) {
            if (serviceState.getState() == ServiceState.STATE_POWER_OFF) {
                ContentValues mobile = new ContentValues();
                mobile.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                mobile.put(Network_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                mobile.put(Network_Data.TYPE, NETWORK_TYPE_MOBILE);
                mobile.put(Network_Data.SUBTYPE, "MOBILE");
                mobile.put(Network_Data.STATE, STATUS_OFF);
                try {
                    getContentResolver().insert(Network_Data.CONTENT_URI, mobile);

                    if (awareSensor != null) awareSensor.onNetworkDataOFF();

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_MOBILE_OFF);
                Intent network = new Intent(ACTION_AWARE_MOBILE_OFF);
                sendBroadcast(network);
            } else {
                ContentValues mobile = new ContentValues();
                mobile.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                mobile.put(Network_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                mobile.put(Network_Data.TYPE, NETWORK_TYPE_MOBILE);
                mobile.put(Network_Data.SUBTYPE, "MOBILE");
                mobile.put(Network_Data.STATE, STATUS_ON);
                try {
                    getContentResolver().insert(Network_Data.CONTENT_URI, mobile);

                    if (awareSensor != null) awareSensor.onNetworkDataON();

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_MOBILE_ON);
                Intent network = new Intent(ACTION_AWARE_MOBILE_ON);
                sendBroadcast(network);
            }
        }

        ;
    };

    /**
     * Network Monitor: logs connectivity changes on the device.
     *
     * @author df
     */
    public static class NetworkMonitor extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    ContentValues started = new ContentValues();
                    started.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    started.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    started.put(Network_Data.TYPE, NETWORK_TYPE_GPS);
                    started.put(Network_Data.SUBTYPE, "GPS");
                    started.put(Network_Data.STATE, STATUS_ON);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, started);

                        if (awareSensor != null) awareSensor.onGPSON();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_GPS_ON);
                    Intent gpsOn = new Intent(ACTION_AWARE_GPS_ON);
                    context.sendBroadcast(gpsOn);
                } else {
                    ContentValues stopped = new ContentValues();
                    stopped.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    stopped.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    stopped.put(Network_Data.TYPE, NETWORK_TYPE_GPS);
                    stopped.put(Network_Data.SUBTYPE, "GPS");
                    stopped.put(Network_Data.STATE, STATUS_OFF);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, stopped);

                        if (awareSensor != null) awareSensor.onGPSOFF();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_GPS_OFF);
                    Intent gpsOff = new Intent(ACTION_AWARE_GPS_OFF);
                    context.sendBroadcast(gpsOff);
                }
            }

            if (intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {

                boolean is_airplane = intent.getBooleanExtra("state", false);

                if (is_airplane) {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Network_Data.TYPE, NETWORK_TYPE_AIRPLANE);
                    rowData.put(Network_Data.SUBTYPE, "AIRPLANE");
                    rowData.put(Network_Data.STATE, STATUS_ON);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, rowData);

                        if (awareSensor != null) awareSensor.onAirplaneON();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_AIRPLANE_ON);
                    Intent noNetwork = new Intent(ACTION_AWARE_AIRPLANE_ON);
                    context.sendBroadcast(noNetwork);
                } else {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Network_Data.TYPE, NETWORK_TYPE_AIRPLANE);
                    rowData.put(Network_Data.SUBTYPE, "AIRPLANE");
                    rowData.put(Network_Data.STATE, STATUS_OFF);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, rowData);

                        if (awareSensor != null) awareSensor.onAirplaneOFF();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_AIRPLANE_OFF);
                    Intent noNetwork = new Intent(ACTION_AWARE_AIRPLANE_OFF);
                    context.sendBroadcast(noNetwork);
                }
            }

            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

                int wifi_state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

                if (wifi_state == WifiManager.WIFI_STATE_ENABLED) {
                    ContentValues data = new ContentValues();
                    data.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    data.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    data.put(Network_Data.TYPE, NETWORK_TYPE_WIFI);
                    data.put(Network_Data.SUBTYPE, "WIFI");
                    data.put(Network_Data.STATE, STATUS_ON);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, data);

                        if (awareSensor != null) awareSensor.onWiFiON();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_WIFI_ON);
                    Intent wifiOn = new Intent(ACTION_AWARE_WIFI_ON);
                    context.sendBroadcast(wifiOn);
                } else if (wifi_state == WifiManager.WIFI_STATE_DISABLED) {
                    ContentValues data = new ContentValues();
                    data.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    data.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    data.put(Network_Data.TYPE, NETWORK_TYPE_WIFI);
                    data.put(Network_Data.SUBTYPE, "WIFI");
                    data.put(Network_Data.STATE, STATUS_OFF);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, data);

                        if (awareSensor != null) awareSensor.onWiFiOFF();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_WIFI_OFF);
                    Intent wifiOn = new Intent(ACTION_AWARE_WIFI_OFF);
                    context.sendBroadcast(wifiOn);
                }
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                int bt_state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

                if (bt_state == BluetoothAdapter.STATE_ON) {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Network_Data.TYPE, NETWORK_TYPE_BLUETOOTH);
                    rowData.put(Network_Data.SUBTYPE, "BLUETOOTH");
                    rowData.put(Network_Data.STATE, STATUS_ON);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, rowData);

                        if (awareSensor != null) awareSensor.onBluetoothON();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_ON);
                    Intent bluetooth = new Intent(ACTION_AWARE_BLUETOOTH_ON);
                    context.sendBroadcast(bluetooth);
                } else if (bt_state == BluetoothAdapter.STATE_OFF) {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Network_Data.TYPE, NETWORK_TYPE_BLUETOOTH);
                    rowData.put(Network_Data.SUBTYPE, "BLUETOOTH");
                    rowData.put(Network_Data.STATE, STATUS_OFF);
                    try {
                        context.getContentResolver().insert(Network_Data.CONTENT_URI, rowData);

                        if (awareSensor != null) awareSensor.onBluetoothOFF();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_OFF);
                    Intent bluetooth = new Intent(ACTION_AWARE_BLUETOOTH_OFF);
                    context.sendBroadcast(bluetooth);
                }
            }

            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo wimax = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);
                if (wimax != null && wimax.isAvailable()) {
                    if (wimax.getState() == NetworkInfo.State.CONNECTED) {
                        ContentValues data = new ContentValues();
                        data.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                        data.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        data.put(Network_Data.TYPE, NETWORK_TYPE_WIMAX);
                        data.put(Network_Data.SUBTYPE, "WIMAX");
                        data.put(Network_Data.STATE, STATUS_ON);
                        try {
                            context.getContentResolver().insert(Network_Data.CONTENT_URI, data);

                            if (awareSensor != null) awareSensor.onWimaxON();

                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }

                        if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_WIMAX_ON);
                        Intent wimaxOn = new Intent(ACTION_AWARE_WIMAX_ON);
                        context.sendBroadcast(wimaxOn);
                    } else if (wimax.getState() == NetworkInfo.State.DISCONNECTED) {
                        ContentValues data = new ContentValues();
                        data.put(Network_Data.TIMESTAMP, System.currentTimeMillis());
                        data.put(Network_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        data.put(Network_Data.TYPE, NETWORK_TYPE_WIMAX);
                        data.put(Network_Data.SUBTYPE, "WIMAX");
                        data.put(Network_Data.STATE, STATUS_OFF);
                        try {
                            context.getContentResolver().insert(Network_Data.CONTENT_URI, data);

                            if (awareSensor != null) awareSensor.onWimaxOFF();

                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }

                        if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_WIMAX_OFF);
                        Intent wimaxOn = new Intent(ACTION_AWARE_WIMAX_OFF);
                        context.sendBroadcast(wimaxOn);
                    }
                }

                NetworkInfo internet = connManager.getActiveNetworkInfo();
                if (internet == null) {

                    if (awareSensor != null) awareSensor.onInternetOFF();

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_INTERNET_UNAVAILABLE);
                    Intent not_available = new Intent(ACTION_AWARE_INTERNET_UNAVAILABLE);
                    context.sendBroadcast(not_available);
                } else {

                    if (awareSensor != null) awareSensor.onInternetON();

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_INTERNET_AVAILABLE);
                    Intent available = new Intent(ACTION_AWARE_INTERNET_AVAILABLE);
                    switch (internet.getType()) {
                        case ConnectivityManager.TYPE_BLUETOOTH:
                            available.putExtra(EXTRA_ACCESS, NETWORK_TYPE_BLUETOOTH);
                            break;
                        case ConnectivityManager.TYPE_MOBILE:
                            available.putExtra(EXTRA_ACCESS, NETWORK_TYPE_MOBILE);
                            break;
                        case ConnectivityManager.TYPE_WIFI:
                            available.putExtra(EXTRA_ACCESS, NETWORK_TYPE_WIFI);
                            break;
                        case ConnectivityManager.TYPE_WIMAX:
                            available.putExtra(EXTRA_ACCESS, NETWORK_TYPE_WIMAX);
                            break;
                    }
                    context.sendBroadcast(available);
                }
            }
        }
    }

    private static final NetworkMonitor networkMonitor = new NetworkMonitor();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Network_Provider.getAuthority(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        teleManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(networkMonitor, filter);

        teleManager.listen(phoneListener, PhoneStateListener.LISTEN_SERVICE_STATE);

        if (Aware.DEBUG) Log.d(TAG, "Network service created...");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(networkMonitor);
        teleManager.listen(phoneListener, PhoneStateListener.LISTEN_NONE);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Network_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Network_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Network service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_NETWORK_EVENTS, true);

            if (Aware.DEBUG) Log.d(TAG, "Network service active...");

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Network_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Network_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Network_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }
}
