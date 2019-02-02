
package com.aware;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.aware.providers.Traffic_Provider;
import com.aware.providers.Traffic_Provider.Traffic_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Service that logs I/O traffic from WiFi & mobile network
 *
 * @author denzil
 */
public class Traffic extends Aware_Sensor {

    /**
     * Logging tag (default = "AWARE::Network traffic")
     */
    public static String TAG = "AWARE::Network traffic";

    /**
     * Broadcasted event: updated traffic information is available
     */
    public static final String ACTION_AWARE_NETWORK_TRAFFIC = "ACTION_AWARE_NETWORK_TRAFFIC";

    public static final int NETWORK_TYPE_MOBILE = 1;
    public static final int NETWORK_TYPE_WIFI = 2;

    private TelephonyManager telephonyManager;

    private static Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            long d_mobileRxBytes = TrafficStats.getMobileRxBytes() - mobileRxBytes;
            long d_mobileRxPackets = TrafficStats.getMobileRxPackets() - mobileRxPackets;
            long d_mobileTxBytes = TrafficStats.getMobileTxBytes() - mobileTxBytes;
            long d_mobileTxPackets = TrafficStats.getMobileTxPackets() - mobileTxPackets;

            long d_wifiRxBytes = (TrafficStats.getTotalRxBytes() - TrafficStats.getMobileRxBytes()) - wifiRxBytes;
            long d_wifiRxPackets = (TrafficStats.getTotalRxPackets() - TrafficStats.getMobileRxPackets()) - wifiRxPackets;
            long d_wifiTxBytes = (TrafficStats.getTotalTxBytes() - TrafficStats.getMobileTxBytes()) - wifiTxBytes;
            long d_wifiTxPackets = (TrafficStats.getTotalTxPackets() - TrafficStats.getMobileTxPackets()) - wifiTxPackets;

            ContentValues wifi = new ContentValues();
            wifi.put(Traffic_Data.TIMESTAMP, System.currentTimeMillis());
            wifi.put(Traffic_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            wifi.put(Traffic_Data.NETWORK_TYPE, NETWORK_TYPE_WIFI);
            wifi.put(Traffic_Data.RECEIVED_BYTES, d_wifiRxBytes);
            wifi.put(Traffic_Data.SENT_BYTES, d_wifiTxBytes);
            wifi.put(Traffic_Data.RECEIVED_PACKETS, d_wifiRxPackets);
            wifi.put(Traffic_Data.SENT_PACKETS, d_wifiTxPackets);
            getContentResolver().insert(Traffic_Data.CONTENT_URI, wifi);

            if (awareSensor != null) awareSensor.onWiFiTraffic(wifi);

            if (Aware.DEBUG) Log.d(TAG, "Wifi:" + wifi.toString());

            ContentValues network = new ContentValues();
            network.put(Traffic_Data.TIMESTAMP, System.currentTimeMillis());
            network.put(Traffic_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            network.put(Traffic_Data.NETWORK_TYPE, NETWORK_TYPE_MOBILE);
            network.put(Traffic_Data.RECEIVED_BYTES, d_mobileRxBytes);
            network.put(Traffic_Data.SENT_BYTES, d_mobileTxBytes);
            network.put(Traffic_Data.RECEIVED_PACKETS, d_mobileRxPackets);
            network.put(Traffic_Data.SENT_PACKETS, d_mobileTxPackets);
            getContentResolver().insert(Traffic_Data.CONTENT_URI, network);

            if (awareSensor != null) awareSensor.onNetworkTraffic(network);
            if (Aware.DEBUG) Log.d(TAG, "Network: " + network.toString());

            Intent traffic = new Intent(ACTION_AWARE_NETWORK_TRAFFIC);
            sendBroadcast(traffic);

            //refresh old values
            //mobile
            mobileRxBytes = TrafficStats.getMobileRxBytes();
            mobileRxPackets = TrafficStats.getMobileRxPackets();
            mobileTxBytes = TrafficStats.getMobileTxBytes();
            mobileTxPackets = TrafficStats.getMobileTxPackets();
            //wifi
            wifiRxBytes = TrafficStats.getTotalRxBytes() - mobileRxBytes;
            wifiTxBytes = TrafficStats.getTotalTxBytes() - mobileTxBytes;
            wifiRxPackets = TrafficStats.getTotalRxPackets() - mobileRxPackets;
            wifiTxPackets = TrafficStats.getTotalTxPackets() - mobileTxPackets;
        }
    };

    //All stats
    private long startTotalRxBytes = 0;
    private long startTotalRxPackets = 0;
    private long startTotalTxBytes = 0;
    private long startTotalTxPackets = 0;

    //Mobile stats
    private long mobileRxBytes = 0;
    private long mobileTxBytes = 0;
    private long mobileRxPackets = 0;
    private long mobileTxPackets = 0;

    //WiFi stats
    private long wifiRxBytes = 0;
    private long wifiTxBytes = 0;
    private long wifiRxPackets = 0;
    private long wifiTxPackets = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static Traffic.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Traffic.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Traffic.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onNetworkTraffic(ContentValues data);

        void onWiFiTraffic(ContentValues data);

        void onIdleTraffic();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Traffic_Provider.getAuthority(this);

        startTotalRxBytes = TrafficStats.getTotalRxBytes();
        startTotalTxBytes = TrafficStats.getTotalTxBytes();
        startTotalRxPackets = TrafficStats.getTotalRxPackets();
        startTotalTxPackets = TrafficStats.getTotalTxPackets();

        if (Aware.DEBUG) Log.d(TAG, "Traffic service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            if (startTotalRxBytes == TrafficStats.UNSUPPORTED) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);
                if (Aware.DEBUG)
                    Log.d(TAG, "Device doesn't support traffic statistics! Disabling sensor...");
                Aware.stopTraffic(this);

            } else {

                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_NETWORK_TRAFFIC, true);

                if (telephonyManager == null)
                    telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

                telephonyManager.listen(networkTrafficObserver, PhoneStateListener.LISTEN_DATA_ACTIVITY);

                if (mobileRxBytes == 0) mobileRxBytes = TrafficStats.getMobileRxBytes();
                if (mobileTxBytes == 0) mobileTxBytes = TrafficStats.getMobileTxBytes();
                if (mobileRxPackets == 0) mobileRxPackets = TrafficStats.getMobileRxPackets();
                if (mobileTxPackets == 0) mobileTxPackets = TrafficStats.getMobileTxPackets();

                if (wifiRxBytes == 0) wifiRxBytes = startTotalRxBytes - mobileRxBytes;
                if (wifiTxBytes == 0) wifiTxBytes = startTotalTxBytes - mobileTxBytes;
                if (wifiRxPackets == 0) wifiRxPackets = startTotalRxPackets - mobileRxPackets;
                if (wifiTxPackets == 0) wifiTxPackets = startTotalTxPackets - mobileTxPackets;

                if (Aware.DEBUG) Log.d(TAG, "Traffic service active...");

                if (Aware.isStudy(this)) {
                    ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Traffic_Provider.getAuthority(this), 1);
                    ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Traffic_Provider.getAuthority(this), true);
                    long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                    SyncRequest request = new SyncRequest.Builder()
                            .syncPeriodic(frequency, frequency / 3)
                            .setSyncAdapter(Aware.getAWAREAccount(this), Traffic_Provider.getAuthority(this))
                            .setExtras(new Bundle()).build();
                    ContentResolver.requestSync(request);
                }
            }
        }

        return START_STICKY;
    }

    private NetworkTrafficObserver networkTrafficObserver = new NetworkTrafficObserver();

    public class NetworkTrafficObserver extends PhoneStateListener {
        @Override
        public void onDataActivity(int direction) {
            super.onDataActivity(direction);

            switch (direction) {
                case TelephonyManager.DATA_ACTIVITY_IN:
                    //update stats
                    mHandler.post(mRunnable);
                    break;
                case TelephonyManager.DATA_ACTIVITY_OUT:
                    //update stats
                    mHandler.post(mRunnable);
                    break;
                case TelephonyManager.DATA_ACTIVITY_INOUT:
                    //update stats
                    mHandler.post(mRunnable);
                    break;
                case TelephonyManager.DATA_ACTIVITY_NONE:
                    //no-op.
                    if (awareSensor != null) awareSensor.onIdleTraffic();
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            telephonyManager.listen(networkTrafficObserver, PhoneStateListener.LISTEN_NONE);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Traffic_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Traffic_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Traffic service terminated...");
    }
}
