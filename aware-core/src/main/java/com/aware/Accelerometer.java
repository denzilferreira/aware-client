
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.util.Log;

import com.aware.providers.Accelerometer_Provider;
import com.aware.providers.Accelerometer_Provider.Accelerometer_Data;
import com.aware.providers.Accelerometer_Provider.Accelerometer_Sensor;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;

/**
 * AWARE Accelerometer module
 * - Accelerometer raw data
 * - Accelerometer sensor information
 *
 * @author df
 */
public class Accelerometer extends Aware_Sensor implements SensorEventListener {

    public static String TAG = "AWARE::Accelerometer";

    private static SensorManager mSensorManager;
    private static Sensor mAccelerometer;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;
    private static String LABEL = "";

    private static Float[] LAST_VALUES = null;

    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;

    /**
     * Broadcasted event: new accelerometer values
     * extra: context (ContentValues)
     * extra: sensor (ContentValues)
     */
    public static final String ACTION_AWARE_ACCELEROMETER = "ACTION_AWARE_ACCELEROMETER";
    public static final String EXTRA_SENSOR = "sensor";
    public static final String EXTRA_DATA = "data";

    public static final String ACTION_AWARE_ACCELEROMETER_LABEL = "ACTION_AWARE_ACCELEROMETER_LABEL";
    public static final String EXTRA_LABEL = "label";

    private List<ContentValues> data_values = new ArrayList<>();

    private static DataLabel dataLabeler = new DataLabel();

    public static class DataLabel extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_AWARE_ACCELEROMETER_LABEL)) {
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
        if (SignificantMotion.isSignificantMotionActive && !SignificantMotion.CURRENT_SIGMOTION_STATE) {
            if (data_values.size() > 0) {
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
            return;
        }

        if (LAST_VALUES != null && THRESHOLD > 0 && Math.abs(event.values[0] - LAST_VALUES[0]) < THRESHOLD
                && Math.abs(event.values[1] - LAST_VALUES[1]) < THRESHOLD
                && Math.abs(event.values[2] - LAST_VALUES[2]) < THRESHOLD) {
            return;
        }

        LAST_VALUES = new Float[]{event.values[0], event.values[1], event.values[2]};

        ContentValues rowData = new ContentValues();
        rowData.put(Accelerometer_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Accelerometer_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Accelerometer_Data.VALUES_0, event.values[0]);
        rowData.put(Accelerometer_Data.VALUES_1, event.values[1]);
        rowData.put(Accelerometer_Data.VALUES_2, event.values[2]);
        rowData.put(Accelerometer_Data.ACCURACY, event.accuracy);
        rowData.put(Accelerometer_Data.LABEL, LABEL);

        if (data_values.size() < 250) {
            data_values.add(rowData);

            Intent accelData = new Intent(ACTION_AWARE_ACCELEROMETER);
            accelData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(accelData);

            if (Aware.DEBUG) Log.d(TAG, "Accelerometer: " + rowData.toString());

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
            getContentResolver().bulkInsert(Accelerometer_Data.CONTENT_URI, data[0]);
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
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Accelerometer_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Accelerometer_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveAccelerometerDevice(Sensor acc) {
        if (acc == null) return;

        Cursor accelInfo = getContentResolver().query(Accelerometer_Sensor.CONTENT_URI, null, null, null, null);
        if (accelInfo == null || !accelInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Accelerometer_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Accelerometer_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Accelerometer_Sensor.MAXIMUM_RANGE, acc.getMaximumRange());
            rowData.put(Accelerometer_Sensor.MINIMUM_DELAY, acc.getMinDelay());
            rowData.put(Accelerometer_Sensor.NAME, acc.getName());
            rowData.put(Accelerometer_Sensor.POWER_MA, acc.getPower());
            rowData.put(Accelerometer_Sensor.RESOLUTION, acc.getResolution());
            rowData.put(Accelerometer_Sensor.TYPE, acc.getType());
            rowData.put(Accelerometer_Sensor.VENDOR, acc.getVendor());
            rowData.put(Accelerometer_Sensor.VERSION, acc.getVersion());

            getContentResolver().insert(Accelerometer_Sensor.CONTENT_URI, rowData);

            Intent accel_dev = new Intent(ACTION_AWARE_ACCELEROMETER);
            accel_dev.putExtra(EXTRA_SENSOR, rowData);
            sendBroadcast(accel_dev);

            if (Aware.DEBUG) Log.d(TAG, "Accelerometer device:" + rowData.toString());
        }
        if (accelInfo != null && !accelInfo.isClosed()) accelInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_ACCELEROMETER_LABEL);
        registerReceiver(dataLabeler, filter);

        DATABASE_TABLES = Accelerometer_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Accelerometer_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Accelerometer_Sensor.CONTENT_URI, Accelerometer_Data.CONTENT_URI};

        if (Aware.DEBUG) Log.d(TAG, "Accelerometer service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mAccelerometer);
        sensorThread.quit();
        wakeLock.release();

        unregisterReceiver(dataLabeler);

        if (Aware.DEBUG) Log.d(TAG, "Accelerometer service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (mAccelerometer == null) {
                if (Aware.DEBUG) Log.w(TAG, "This device does not have an accelerometer!");
                Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, false);
                stopSelf();
            } else {
                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, true);
                saveAccelerometerDevice(mAccelerometer);

                if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000);
                }

                if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_ACCELEROMETER).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.THRESHOLD_ACCELEROMETER, 0.0);
                }

                if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER))
                        || THRESHOLD != Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_ACCELEROMETER))) {

                    sensorHandler.removeCallbacksAndMessages(null);
                    mSensorManager.unregisterListener(this, mAccelerometer);

                    FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER));
                    THRESHOLD = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_ACCELEROMETER));
                }

                mSensorManager.registerListener(this, mAccelerometer, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER)), sensorHandler);

                if (Aware.DEBUG) Log.d(TAG, "Accelerometer service active: " + FREQUENCY + "ms");
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}