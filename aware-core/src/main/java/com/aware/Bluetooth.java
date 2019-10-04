
package com.aware;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.aware.providers.Bluetooth_Provider;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Data;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Encrypter;

import java.util.HashMap;

/**
 * Bluetooth Module. For now, scans and returns surrounding bluetooth devices and RSSI dB values.
 *
 * @author denzil
 */
public class Bluetooth extends Aware_Sensor {

    private static String TAG = "AWARE::Bluetooth";

    private static AlarmManager alarmManager = null;
    private static PendingIntent bluetoothScan = null;

    private static long scanTimestamp = 0;
    private static int FREQUENCY = -1;

    /**
     * This device's bluetooth adapter
     */
    private static BluetoothAdapter bluetoothAdapter;

    /**
     * Broadcasted event: new bluetooth device detected
     */
    public static final String ACTION_AWARE_BLUETOOTH_NEW_DEVICE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE";
    public static final String ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE";
    public static final String EXTRA_DEVICE = "extra_device";

    /**
     * Broadcasted event: bluetooth scan started
     */
    public static final String ACTION_AWARE_BLUETOOTH_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_SCAN_STARTED";
    public static final String ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED";

    /**
     * Broadcasted event: bluetooth scan ended
     */
    public static final String ACTION_AWARE_BLUETOOTH_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_SCAN_ENDED";
    public static final String ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED";

    /**
     * Broadcast receiving event: request a bluetooth scan
     */
    public static final String ACTION_AWARE_BLUETOOTH_REQUEST_SCAN = "ACTION_AWARE_BLUETOOTH_REQUEST_SCAN";

    /**
     * Bluetooth Service singleton object
     */
    private static Bluetooth bluetoothService = Bluetooth.getService();

    /**
     * Request user permission for bt scanning
     */
    private static String ACTION_AWARE_ENABLE_BT = "ACTION_AWARE_ENABLE_BT";

    private static NotificationManager notificationManager = null;
    private static Intent enableBT = null;

    private boolean BLE_SUPPORT = false;

    private static Handler mBLEHandler;

    private ScanSettings scanSettings;
    private boolean isBLEScanning = false;
    private HashMap<String, BluetoothDevice> discoveredBLE = new HashMap<String, BluetoothDevice>();

    /**
     * Get an instance for the Bluetooth Service
     *
     * @return Bluetooth_Service singleton
     */
    public static Bluetooth getService() {
        if (bluetoothService == null) bluetoothService = new Bluetooth();
        return bluetoothService;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Bluetooth_Provider.getAuthority(this);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Bluetooth.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothMonitor, filter);

        Intent backgroundService = new Intent(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        bluetoothScan = PendingIntent.getBroadcast(this, 0, backgroundService, PendingIntent.FLAG_UPDATE_CURRENT);

        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION); //we need this permission for BT scanning to work

        bluetoothAdapter = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) ? ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter() : BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            stopSelf();
            return;
        }

        //Check if the device can do BLE scans.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            BLE_SUPPORT = true;

        enableBT = new Intent(this, Bluetooth.class);
        enableBT.putExtra("action", ACTION_AWARE_ENABLE_BT);

        if (Aware.DEBUG) Log.d(TAG, "Bluetooth service created!");
    }

    private static Bluetooth.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Bluetooth.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Bluetooth.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onBluetoothDetected(ContentValues data);

        void onBluetoothBLEDetected(ContentValues data);

        void onScanStarted();

        void onScanEnded();

        void onBLEScanStarted();

        void onBLEScanEnded();

        void onBluetoothDisabled();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (intent != null && intent.hasExtra("action") && intent.getStringExtra("action").equalsIgnoreCase(ACTION_AWARE_ENABLE_BT)) {
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBT.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(enableBT);
            }

            if (bluetoothAdapter == null) {
                if (Aware.DEBUG) Log.w(TAG, "No bluetooth is detected on this device");
                stopSelf();
            } else {

                if (!bluetoothAdapter.isEnabled()) {
                    notifyMissingBluetooth(getApplicationContext(), false);
                }

                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_BLUETOOTH, true);

                if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_BLUETOOTH).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.FREQUENCY_BLUETOOTH, 60);
                }

                save_bluetooth_device(bluetoothAdapter);

                if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH))) {
                    alarmManager.cancel(bluetoothScan);
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH)) * 1000,
                            Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH)) * 2 * 1000,
                            bluetoothScan);

                    FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH));
                }
                if (Aware.DEBUG) Log.d(TAG, "Bluetooth service active: " + FREQUENCY + "s");

                if (BLE_SUPPORT) {
                    if (mBLEHandler == null)
                        mBLEHandler = new Handler();

                    if (scanSettings == null) {
                        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
                    }
                }
            }

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Bluetooth_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Bluetooth_Provider.getAuthority(this), true);

                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Bluetooth_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null && !isBLEScanning) {
                mBLEHandler.postDelayed(stopScan, 3000);
                scanner.startScan(null, scanSettings, scanCallback);
                if (awareSensor != null) awareSensor.onBLEScanStarted();
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED);
                Intent scanStart = new Intent(ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED);
                sendBroadcast(scanStart);
                isBLEScanning = !isBLEScanning;
            }
        }
    };

    private Runnable stopScan = new Runnable() {
        @Override
        public void run() {
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null && isBLEScanning) {
                scanner.stopScan(scanCallback);
                if (awareSensor != null) awareSensor.onBLEScanEnded();
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED);
                Intent scanEnd = new Intent(ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED);
                sendBroadcast(scanEnd);
                discoveredBLE.clear();
                isBLEScanning = !isBLEScanning;
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bluetoothDevice = result.getDevice();
            if (discoveredBLE.containsKey(bluetoothDevice.getAddress())) return;

            discoveredBLE.put(bluetoothDevice.getAddress(), bluetoothDevice);

            ContentValues rowData = new ContentValues();
            rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Bluetooth_Data.BT_ADDRESS, Encrypter.hashMac(getApplicationContext(), bluetoothDevice.getAddress()));
            rowData.put(Bluetooth_Data.BT_NAME, Encrypter.hashSsid(getApplicationContext(), bluetoothDevice.getName()));
            rowData.put(Bluetooth_Data.BT_RSSI, result.getRssi());
            rowData.put(Bluetooth_Data.BT_LABEL, scanTimestamp);

            try {
                getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);

                if (awareSensor != null) awareSensor.onBluetoothBLEDetected(rowData);

            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }

            if (Aware.DEBUG)
                Log.d(Aware.TAG, ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE + ": " + rowData.toString());

            Intent detectedBT = new Intent(ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE);
            detectedBT.putExtra(EXTRA_DEVICE, rowData);
            sendBroadcast(detectedBT);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(bluetoothMonitor);

        if (bluetoothAdapter != null) {

            bluetoothAdapter.cancelDiscovery();
            if (BLE_SUPPORT) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            }

            if (mBLEHandler != null) {
                mBLEHandler.removeCallbacks(scanRunnable);
                mBLEHandler.removeCallbacksAndMessages(null);
            }

            alarmManager.cancel(bluetoothScan);
            notificationManager.cancel(123);


            ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Bluetooth_Provider.getAuthority(this), false);
            ContentResolver.removePeriodicSync(
                    Aware.getAWAREAccount(this),
                    Bluetooth_Provider.getAuthority(this),
                    Bundle.EMPTY
            );

        }

        if (Aware.DEBUG) Log.d(TAG, "Bluetooth service terminated...");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * BroadcastReceiver for Bluetooth module
     * - ACTION_AWARE_BLUETOOTH_REQUEST_SCAN: request a bluetooth scan
     * - {@link BluetoothDevice#ACTION_FOUND}: a new bluetooth device was detected
     * - {@link BluetoothAdapter#ACTION_DISCOVERY_STARTED}: discovery has started
     * - {@link BluetoothAdapter#ACTION_DISCOVERY_FINISHED}: discovery has finished
     * - ACTION_AWARE_WEBSERVICE: request for webservice remote backup
     *
     * @author denzil
     */
    public class Bluetooth_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                Bundle extras = intent.getExtras();
                if (extras == null) return;

                BluetoothDevice btDevice = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice == null) {
                    if (Aware.DEBUG)
                        Log.d(TAG, "No Bluetooth device was discovered during the scan");
                    return;
                }

                Short btDeviceRSSI = extras.getShort(BluetoothDevice.EXTRA_RSSI);

                ContentValues rowData = new ContentValues();
                rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Bluetooth_Data.BT_ADDRESS, Encrypter.hashMac(context, btDevice.getAddress()));
                rowData.put(Bluetooth_Data.BT_NAME, Encrypter.hashSsid(context, btDevice.getName()));
                rowData.put(Bluetooth_Data.BT_RSSI, btDeviceRSSI);
                rowData.put(Bluetooth_Data.BT_LABEL, scanTimestamp);

                try {
                    context.getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);

                    if (awareSensor != null) awareSensor.onBluetoothDetected(rowData);

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, ACTION_AWARE_BLUETOOTH_NEW_DEVICE + ": " + rowData.toString());

                Intent detectedBT = new Intent(ACTION_AWARE_BLUETOOTH_NEW_DEVICE);
                detectedBT.putExtra(EXTRA_DEVICE, rowData);
                context.sendBroadcast(detectedBT);
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if (awareSensor != null) awareSensor.onScanEnded();
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                Intent scanEnd = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                context.sendBroadcast(scanEnd);

                // Start BLE scanning when normal BT scanning is finished
                if (mBLEHandler == null) mBLEHandler = new Handler();
                mBLEHandler.post(scanRunnable);
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                if (awareSensor != null) awareSensor.onScanStarted();
                scanTimestamp = System.currentTimeMillis();
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                Intent scanStart = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                context.sendBroadcast(scanStart);
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        == BluetoothAdapter.STATE_OFF) {
                    notifyMissingBluetooth(context.getApplicationContext(), false);

                } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        == BluetoothAdapter.STATE_ON) {
                    notifyMissingBluetooth(context.getApplicationContext(), true);
                }
            }

            if (intent.getAction().equals(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)) {
                //interrupt ongoing scans
                if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
                if (!bluetoothAdapter.isDiscovering()) {
                    if (bluetoothAdapter.isEnabled()) {
                        bluetoothAdapter.startDiscovery();
                    } else {
                        //Bluetooth is off
                        if (Aware.DEBUG) Log.d(TAG, "Bluetooth is turned off...");
                        ContentValues rowData = new ContentValues();
                        rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
                        rowData.put(Bluetooth_Data.BT_NAME, "disabled");
                        rowData.put(Bluetooth_Data.BT_ADDRESS, "disabled");
                        rowData.put(Bluetooth_Data.BT_LABEL, "disabled");
                        try {
                            context.getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);

                            if (awareSensor != null) awareSensor.onBluetoothDisabled();

                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private final Bluetooth_Broadcaster bluetoothMonitor = new Bluetooth_Broadcaster();

    private void save_bluetooth_device(BluetoothAdapter btAdapter) {
        if (btAdapter == null) return;

        Cursor sensorBT = getContentResolver().query(Bluetooth_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorBT == null || !sensorBT.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Bluetooth_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Bluetooth_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Bluetooth_Sensor.BT_ADDRESS, Encrypter.hashMac(getApplicationContext(), btAdapter.getAddress()));
            rowData.put(Bluetooth_Sensor.BT_NAME, Encrypter.hashSsid(getApplicationContext(), btAdapter.getName()));

            getContentResolver().insert(Bluetooth_Sensor.CONTENT_URI, rowData);

            if (Aware.DEBUG) Log.d(TAG, "Bluetooth local information: " + rowData.toString());
        }
        if (sensorBT != null && !sensorBT.isClosed()) sensorBT.close();
    }

    private static void notifyMissingBluetooth(Context c, boolean dismiss) {
        if (!dismiss) {
            //Remind the user that we need Bluetooth on for data collection
            NotificationCompat.Builder builder = new NotificationCompat.Builder(c, Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL)
                    .setSmallIcon(R.drawable.ic_stat_aware_accessibility)
                    .setContentTitle("AWARE: Bluetooth needed")
                    .setContentText("Tap to enable Bluetooth for nearby scanning.")
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getService(c, 123, enableBT, PendingIntent.FLAG_UPDATE_CURRENT));

            builder = Aware.setNotificationProperties(builder, Aware.AWARE_NOTIFICATION_IMPORTANCE_GENERAL);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                builder.setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL);

            try {
                notificationManager.notify(123, builder.build());
            } catch (NullPointerException e) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e);
            }
        } else {
            try {
                notificationManager.cancel(123);
            } catch (NullPointerException e) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Notification exception: " + e);
            }
        }
    }
}
