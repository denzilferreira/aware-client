
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.Proximity_Provider;
import com.aware.providers.Proximity_Provider.Proximity_Data;
import com.aware.providers.Proximity_Provider.Proximity_Sensor;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

/**
 * AWARE Proximity module
 * - Proximity raw data in centimeters / (binary far/near for some sensors)
 * - Proximity sensor information
 *
 * @author df
 */
public class Proximity extends Aware_Sensor implements SensorEventListener {

    private static SensorManager mSensorManager;
    private static Sensor mProximity;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;

    private static Float LAST_VALUE = null;
    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;

    /**
     * Broadcasted event: new sensor values
     * ContentProvider: ProximityProvider
     */
    public static final String ACTION_AWARE_PROXIMITY = "ACTION_AWARE_PROXIMITY";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";

    public static final String ACTION_AWARE_PROXIMITY_LABEL = "ACTION_AWARE_PROXIMITY_LABEL";
    public static final String EXTRA_LABEL = "label";

    /**
     * Until today, no available Android phone samples higher than 208Hz (Nexus 7).
     * http://ilessendata.blogspot.com/2012/11/android-accelerometer-sampling-rates.html
     */
    private List<ContentValues> data_values = new ArrayList<ContentValues>();

    private static String LABEL = "";

    private static DataLabel dataLabeler = new DataLabel();

    public static class DataLabel extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_AWARE_PROXIMITY_LABEL)) {
                LABEL = intent.getStringExtra(EXTRA_LABEL);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (LAST_VALUE != null && THRESHOLD > 0 && Math.abs(event.values[0] - LAST_VALUE) < THRESHOLD) {
            return;
        }

        LAST_VALUE = event.values[0];

        ContentValues rowData = new ContentValues();
        rowData.put(Proximity_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Proximity_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Proximity_Data.PROXIMITY, event.values[0]);
        rowData.put(Proximity_Data.ACCURACY, event.accuracy);
        rowData.put(Proximity_Data.LABEL, LABEL);

        if (data_values.size() < 250) {
            data_values.add(rowData);

            Intent proxyData = new Intent(ACTION_AWARE_PROXIMITY);
            proxyData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(proxyData);

            if (Aware.DEBUG) Log.d(TAG, "Proximity:" + rowData.toString());

            return;
        }

        ContentValues[] data_buffer = new ContentValues[data_values.size()];
        data_values.toArray(data_buffer);

        try {
            if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("true")) {
                new AsyncStore().execute(data_buffer);
            }
        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }
        data_values.clear();
    }

    /**
     * Database I/O on different thread
     */
    private class AsyncStore extends AsyncTask<ContentValues[], Void, Void> {
        @Override
        protected Void doInBackground(ContentValues[]... data) {
            getContentResolver().bulkInsert(Proximity_Data.CONTENT_URI, data[0]);
            return null;
        }
    }

    /**
     * Calculates the sampling rate in Hz (i.e., how many samples did we collect in the past second)
     *
     * @param context
     * @return hz
     */
    public static int getFrequency(Context context) {
        int hz = 0;
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Proximity_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Proximity_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Proximity_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Proximity_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Proximity_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Proximity_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Proximity_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Proximity_Sensor.NAME, sensor.getName());
            rowData.put(Proximity_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Proximity_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Proximity_Sensor.TYPE, sensor.getType());
            rowData.put(Proximity_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Proximity_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Proximity_Sensor.CONTENT_URI, rowData);

            Intent proxy_dev = new Intent(ACTION_AWARE_PROXIMITY);
            proxy_dev.putExtra(EXTRA_SENSOR, rowData);
            sendBroadcast(proxy_dev);

            if (Aware.DEBUG) Log.d(TAG, "Proximity sensor: " + rowData.toString());
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::Proximity";

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        DATABASE_TABLES = Proximity_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Proximity_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Proximity_Sensor.CONTENT_URI, Proximity_Data.CONTENT_URI};

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_PROXIMITY_LABEL);
        registerReceiver(dataLabeler, filter);

        if (Aware.DEBUG) Log.d(TAG, "Proximity service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mProximity);
        sensorThread.quit();

        wakeLock.release();

        unregisterReceiver(dataLabeler);

        if (Aware.DEBUG) Log.d(TAG, "Proximity service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (mProximity == null) {
                if (Aware.DEBUG) Log.w(TAG, "This device does not have a proximity sensor!");
                Aware.setSetting(this, Aware_Preferences.STATUS_PROXIMITY, false);
                stopSelf();
            } else {

                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_PROXIMITY, true);
                saveSensorDevice(mProximity);

                if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_PROXIMITY).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.FREQUENCY_PROXIMITY, 200000);
                }

                if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_PROXIMITY).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.THRESHOLD_PROXIMITY, 0.0);
                }

                if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY))
                        || THRESHOLD != Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_PROXIMITY))) {

                    sensorHandler.removeCallbacksAndMessages(null);
                    mSensorManager.unregisterListener(this, mProximity);

                    FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY));
                    THRESHOLD = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_PROXIMITY));
                }

                mSensorManager.registerListener(this, mProximity, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY)), sensorHandler);

                if (Aware.DEBUG) Log.d(TAG, "Proximity service active: " + FREQUENCY + "ms");
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}