
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

import com.aware.providers.Magnetometer_Provider;
import com.aware.providers.Magnetometer_Provider.Magnetometer_Data;
import com.aware.providers.Magnetometer_Provider.Magnetometer_Sensor;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

/**
 * AWARE Magnetometer module
 * - Magnetometer raw data
 * - Magnetometer sensor information
 *
 * @author df
 */
public class Magnetometer extends Aware_Sensor implements SensorEventListener {

    /**
     * Logging tag (default = "AWARE::Magnetometer")
     */
    private static String TAG = "AWARE::Magnetometer";

    private static SensorManager mSensorManager;
    private static Sensor mMagnetometer;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;

    private static Float[] LAST_VALUES = null;

    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;

    /**
     * Broadcasted event: new sensor values
     * ContentProvider: MagnetometerProvider
     */
    public static final String ACTION_AWARE_MAGNETOMETER = "ACTION_AWARE_MAGNETOMETER";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";

    public static final String ACTION_AWARE_MAGNETOMETER_LABEL = "ACTION_AWARE_MAGNETOMETER_LABEL";
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
            if (intent.getAction().equals(ACTION_AWARE_MAGNETOMETER_LABEL)) {
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
        if (LAST_VALUES != null && THRESHOLD > 0 &&
                Math.abs(event.values[0] - LAST_VALUES[0]) < THRESHOLD &&
                Math.abs(event.values[0] - LAST_VALUES[1]) < THRESHOLD &&
                Math.abs(event.values[0] - LAST_VALUES[2]) < THRESHOLD) {
            return;
        }

        LAST_VALUES = new Float[]{event.values[0], event.values[1], event.values[2]};

        ContentValues rowData = new ContentValues();
        rowData.put(Magnetometer_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Magnetometer_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Magnetometer_Data.VALUES_0, event.values[0]);
        rowData.put(Magnetometer_Data.VALUES_1, event.values[1]);
        rowData.put(Magnetometer_Data.VALUES_2, event.values[2]);
        rowData.put(Magnetometer_Data.ACCURACY, event.accuracy);
        rowData.put(Magnetometer_Data.LABEL, LABEL);

        if (data_values.size() < 250) {
            data_values.add(rowData);

            Intent magnetoData = new Intent(ACTION_AWARE_MAGNETOMETER);
            magnetoData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(magnetoData);

            if (Aware.DEBUG) Log.d(TAG, "Magnetometer:" + rowData.toString());

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
            getContentResolver().bulkInsert(Magnetometer_Data.CONTENT_URI, data[0]);
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
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Magnetometer_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Magnetometer_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Magnetometer_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Magnetometer_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Magnetometer_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Magnetometer_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Magnetometer_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Magnetometer_Sensor.NAME, sensor.getName());
            rowData.put(Magnetometer_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Magnetometer_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Magnetometer_Sensor.TYPE, sensor.getType());
            rowData.put(Magnetometer_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Magnetometer_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Magnetometer_Sensor.CONTENT_URI, rowData);

            Intent magneto_dev = new Intent(ACTION_AWARE_MAGNETOMETER);
            magneto_dev.putExtra(EXTRA_SENSOR, rowData);
            sendBroadcast(magneto_dev);

            if (Aware.DEBUG) Log.d(TAG, "Magnetometer sensor: " + rowData.toString());
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        DATABASE_TABLES = Magnetometer_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Magnetometer_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Magnetometer_Sensor.CONTENT_URI, Magnetometer_Data.CONTENT_URI};

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_MAGNETOMETER_LABEL);
        registerReceiver(dataLabeler, filter);

        if (Aware.DEBUG) Log.d(TAG, "Magnetometer service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mMagnetometer);
        sensorThread.quit();

        wakeLock.release();

        unregisterReceiver(dataLabeler);

        if (Aware.DEBUG) Log.d(TAG, "Magnetometer service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mMagnetometer == null) {
            if (Aware.DEBUG) Log.w(TAG, "This device does not have a magnetometer!");
            Aware.setSetting(this, Aware_Preferences.STATUS_MAGNETOMETER, false);
            stopSelf();
        } else {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_MAGNETOMETER, true);
            saveSensorDevice(mMagnetometer);

            if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER, 200000);
            }

            if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_MAGNETOMETER).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.THRESHOLD_MAGNETOMETER, 0.0);
            }

            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER))
                    || THRESHOLD != Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_MAGNETOMETER))) {

                sensorHandler.removeCallbacksAndMessages(null);
                mSensorManager.unregisterListener(this, mMagnetometer);

                FREQUENCY = Integer.parseInt(Aware.getSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER));
                THRESHOLD = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_MAGNETOMETER));
            }

            mSensorManager.registerListener(this, mMagnetometer, Integer.parseInt(Aware.getSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER)), sensorHandler);

            if (Aware.DEBUG) Log.d(TAG, "Magnetometer service active...");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    //Singleton instance of this service
    private static Magnetometer magnetometerSrv = Magnetometer.getService();

    /**
     * Get singleton instance to service
     *
     * @return Magnetometer obj
     */
    public static Magnetometer getService() {
        if (magnetometerSrv == null) magnetometerSrv = new Magnetometer();
        return magnetometerSrv;
    }

    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        Magnetometer getService() {
            return Magnetometer.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}