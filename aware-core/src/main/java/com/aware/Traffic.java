
package com.aware;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.Traffic_Provider;
import com.aware.providers.Traffic_Provider.Traffic_Data;
import com.aware.ui.PermissionsHandler;
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

    private static int FREQUENCY = -1;

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

            ContentValues network = new ContentValues();
            network.put(Traffic_Data.TIMESTAMP, System.currentTimeMillis());
            network.put(Traffic_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            network.put(Traffic_Data.NETWORK_TYPE, NETWORK_TYPE_MOBILE);
            network.put(Traffic_Data.RECEIVED_BYTES, d_mobileRxBytes);
            network.put(Traffic_Data.SENT_BYTES, d_mobileTxBytes);
            network.put(Traffic_Data.RECEIVED_PACKETS, d_mobileRxPackets);
            network.put(Traffic_Data.SENT_PACKETS, d_mobileTxPackets);
            getContentResolver().insert(Traffic_Data.CONTENT_URI, network);

            Intent traffic = new Intent(ACTION_AWARE_NETWORK_TRAFFIC);
            sendBroadcast(traffic);

            if (Aware.DEBUG) {
                Log.d(TAG, "Mobile RX-bytes: " + d_mobileRxBytes + " TX-bytes: " + d_mobileTxBytes + " RxPack: " + d_mobileRxPackets + " TxPack: " + d_mobileTxPackets);
                Log.d(TAG, "Wifi RX-bytes: " + d_wifiRxBytes + " TX-bytes: " + d_wifiTxBytes + " RxPack: " + d_wifiRxPackets + " TxPack: " + d_wifiTxPackets);
            }

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

            mHandler.postDelayed(mRunnable, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC)) * 1000);
        }
    };

    //All stats
    private static long startTotalRxBytes = 0;
    private static long startTotalRxPackets = 0;
    private static long startTotalTxBytes = 0;
    private static long startTotalTxPackets = 0;

    //Mobile stats
    private static long mobileRxBytes = 0;
    private static long mobileTxBytes = 0;
    private static long mobileRxPackets = 0;
    private static long mobileTxPackets = 0;

    //WiFi stats
    private static long wifiRxBytes = 0;
    private static long wifiTxBytes = 0;
    private static long wifiRxPackets = 0;
    private static long wifiTxPackets = 0;

    /**
     * Activity-Service binder
     */
    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        Traffic getService() {
            return Traffic.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    private static Traffic trafficSrv = Traffic.getService();

    /**
     * Singleton instance to service
     *
     * @return Network
     */
    public static Traffic getService() {
        if (trafficSrv == null) trafficSrv = new Traffic();
        return trafficSrv;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        startTotalRxBytes = TrafficStats.getTotalRxBytes();
        startTotalTxBytes = TrafficStats.getTotalTxBytes();
        startTotalRxPackets = TrafficStats.getTotalRxPackets();
        startTotalTxPackets = TrafficStats.getTotalTxPackets();

        DATABASE_TABLES = Traffic_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Traffic_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Traffic_Data.CONTENT_URI};

        if (Aware.DEBUG) Log.d(TAG, "Traffic service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (startTotalRxBytes == TrafficStats.UNSUPPORTED) {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);
            if (Aware.DEBUG)
                Log.d(TAG, "Device doesn't support traffic statistics! Disabling sensor...");
            stopSelf();
        } else {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_NETWORK_TRAFFIC, true);

            if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC, 60);
            }

            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC))) {
                mobileRxBytes = TrafficStats.getMobileRxBytes();
                mobileTxBytes = TrafficStats.getMobileTxBytes();
                mobileRxPackets = TrafficStats.getMobileRxPackets();
                mobileTxPackets = TrafficStats.getMobileTxPackets();

                wifiRxBytes = startTotalRxBytes - mobileRxBytes;
                wifiTxBytes = startTotalTxBytes - mobileTxBytes;
                wifiRxPackets = startTotalRxPackets - mobileRxPackets;
                wifiTxPackets = startTotalTxPackets - mobileTxPackets;

                mHandler.removeCallbacks(mRunnable);
                mHandler.post(mRunnable);

                FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC));
            }

            if (Aware.DEBUG) Log.d(TAG, "Traffic service active...");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacks(mRunnable);

        if (Aware.DEBUG) Log.d(TAG, "Traffic service terminated...");
    }
}
