
package com.aware;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.Bluetooth_Provider;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Data;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Sensor;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Sensor;

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
    private static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    /**
     * Broadcasted event: new bluetooth device detected
     */
    public static final String ACTION_AWARE_BLUETOOTH_NEW_DEVICE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE";
    public static final String EXTRA_DEVICE = "extra_device";

    /**
     * Broadcasted event: bluetooth scan started
     */
    public static final String ACTION_AWARE_BLUETOOTH_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_SCAN_STARTED";

    /**
     * Broadcasted event: bluetooth scan ended
     */
    public static final String ACTION_AWARE_BLUETOOTH_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_SCAN_ENDED";

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

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        DATABASE_TABLES = Bluetooth_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Bluetooth_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Bluetooth_Sensor.CONTENT_URI, Bluetooth_Data.CONTENT_URI};

        IntentFilter filter = new IntentFilter();
        filter.addAction(Bluetooth.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(bluetoothMonitor, filter);

        Intent backgroundService = new Intent(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        bluetoothScan = PendingIntent.getBroadcast(this, 0, backgroundService, PendingIntent.FLAG_UPDATE_CURRENT);

        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION); //we need this permission for BT scanning to work

        enableBT = new Intent(this, Bluetooth.class);
        enableBT.putExtra("action", ACTION_AWARE_ENABLE_BT);

        if (!bluetoothAdapter.isEnabled()) {
            notifyMissingBluetooth(getApplicationContext());
        }

        if (Aware.DEBUG) Log.d(TAG, "Bluetooth service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("action") && intent.getStringExtra("action").equalsIgnoreCase(ACTION_AWARE_ENABLE_BT)) {
            bluetoothAdapter.enable();
            bluetoothAdapter.startDiscovery();
        }

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_BLUETOOTH, true);

            if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_BLUETOOTH).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.FREQUENCY_BLUETOOTH, 60);
            }

            if (bluetoothAdapter == null) {
                if (Aware.DEBUG) Log.w(TAG, "No bluetooth is detected on this device");
                stopSelf();
            } else {
                save_bluetooth_device(bluetoothAdapter);

                if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH))) {
                    alarmManager.cancel(bluetoothScan);
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 1000,
                            Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH)) * 1000,
                            bluetoothScan);

                    FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH));
                }

                if (Aware.DEBUG) Log.d(TAG, "Bluetooth service active: " + FREQUENCY + "s");
            }
        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(bluetoothMonitor);
        alarmManager.cancel(bluetoothScan);
        notificationManager.cancel(123);

        if (Aware.DEBUG) Log.d(TAG, "Bluetooth service terminated...");
    }

    private final IBinder bluetoothBinder = new BluetoothBinder();

    /**
     * Binder for Bluetooth module
     *
     * @author denzil
     */
    public class BluetoothBinder extends Binder {
        Bluetooth getService() {
            return Bluetooth.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return bluetoothBinder;
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
    public static class Bluetooth_Broadcaster extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                Bundle extras = intent.getExtras();
                if (extras == null) return;

                BluetoothDevice btDevice = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice == null) return;

                Short btDeviceRSSI = extras.getShort(BluetoothDevice.EXTRA_RSSI);

                ContentValues rowData = new ContentValues();
                rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Bluetooth_Data.BT_ADDRESS, btDevice.getAddress());
                rowData.put(Bluetooth_Data.BT_NAME, ((btDevice.getName()!=null)?btDevice.getName():""));
                rowData.put(Bluetooth_Data.BT_RSSI, btDeviceRSSI);
                rowData.put(Bluetooth_Data.BT_LABEL, scanTimestamp);

                try {
                    context.getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);
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
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                Intent scanEnd = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                context.sendBroadcast(scanEnd);
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                scanTimestamp = System.currentTimeMillis();
                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                Intent scanStart = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                context.sendBroadcast(scanStart);
            }

            if (intent.getAction().equals(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)) {
                Intent backgroundService = new Intent(context, BackgroundService.class);
                backgroundService.setAction(Bluetooth.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
                context.startService(backgroundService);
            }
        }
    }

    private static final Bluetooth_Broadcaster bluetoothMonitor = new Bluetooth_Broadcaster();

    private void save_bluetooth_device(BluetoothAdapter btAdapter) {
        if (btAdapter == null) return;

        Cursor sensorBT = getContentResolver().query(Bluetooth_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorBT == null || !sensorBT.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Bluetooth_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Bluetooth_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Bluetooth_Sensor.BT_ADDRESS, btAdapter.getAddress());
            rowData.put(Bluetooth_Sensor.BT_NAME, ((btAdapter.getName()!=null)?btAdapter.getName():""));

            getContentResolver().insert(Bluetooth_Sensor.CONTENT_URI, rowData);

            if (Aware.DEBUG) Log.d(TAG, "Bluetooth local information: " + rowData.toString());
        }
        if (sensorBT != null && ! sensorBT.isClosed()) sensorBT.close();
    }

    public static class BackgroundService extends IntentService {
        public BackgroundService() {
            super(TAG + " background service");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            if (intent.getAction().equals(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)) {
                if (!bluetoothAdapter.isDiscovering()) {
                    if (bluetoothAdapter.isEnabled()) {
                        bluetoothAdapter.startDiscovery();
                    } else {
                        //Bluetooth is off
                        if (Aware.DEBUG) Log.d(TAG, "Bluetooth is turned off...");
                        ContentValues rowData = new ContentValues();
                        rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
                        rowData.put(Bluetooth_Data.BT_NAME, "disabled");
                        rowData.put(Bluetooth_Data.BT_ADDRESS, "disabled");
                        rowData.put(Bluetooth_Data.BT_LABEL, "disabled");
                        getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);
                    }
                }
            }
        }
    }

    private static void notifyMissingBluetooth(Context c) {
        //Remind the user that we need Bluetooth on for data collection
        NotificationCompat.Builder builder = new NotificationCompat.Builder(c)
                .setSmallIcon(R.drawable.ic_stat_aware_accessibility)
                .setContentTitle("AWARE: Bluetooth needed")
                .setContentText("Tap to enable Bluetooth for nearby scanning.")
                .setDefaults(Notification.DEFAULT_ALL)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getService(c, 123, enableBT, PendingIntent.FLAG_UPDATE_CURRENT));
        notificationManager.notify(123, builder.build());
    }
}
