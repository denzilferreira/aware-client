
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

import com.aware.providers.Rotation_Provider;
import com.aware.providers.Rotation_Provider.Rotation_Data;
import com.aware.providers.Rotation_Provider.Rotation_Sensor;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

/**
 * AWARE Rotation module
 * - Rotation raw data
 * - Rotation sensor information
 *
 * @author df
 */
public class Rotation extends Aware_Sensor implements SensorEventListener {

    /**
     * Logging tag (default = "AWARE::Rotation")
     */
    private static String TAG = "AWARE::Rotation";

    private static SensorManager mSensorManager;
    private static Sensor mRotation;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;

    private static Float[] LAST_VALUES = null;

    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;

    /**
     * Broadcasted event: new rotation values
     * ContentProvider: RotationProvider
     */
    public static final String ACTION_AWARE_ROTATION = "ACTION_AWARE_ROTATION";
    public static final String EXTRA_SENSOR = "sensor";
    public static final String EXTRA_DATA = "data";

    public static final String ACTION_AWARE_ROTATION_LABEL = "ACTION_AWARE_ROTATION_LABEL";
    public static final String EXTRA_LABEL = "label";

    /**
     * Until today, no available Android phone samples higher than 208Hz (Nexus 7).
     * http://ilessendata.blogspot.com/2012/11/android-accelerometer-sampling-rates.html
     */
    private List<ContentValues> data_values = new ArrayList<>();

    private static String LABEL = "";

    private static DataLabel dataLabeler = new DataLabel();

    public static class DataLabel extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_AWARE_ROTATION_LABEL)) {
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
        rowData.put(Rotation_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Rotation_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Rotation_Data.VALUES_0, event.values[0]);
        rowData.put(Rotation_Data.VALUES_1, event.values[1]);
        rowData.put(Rotation_Data.VALUES_2, event.values[2]);
        if (event.values.length == 4) {
            rowData.put(Rotation_Data.VALUES_3, event.values[3]);
        }
        rowData.put(Rotation_Data.ACCURACY, event.accuracy);
        rowData.put(Rotation_Data.LABEL, LABEL);

        if (data_values.size() < 250) {
            data_values.add(rowData);

            Intent rotData = new Intent(ACTION_AWARE_ROTATION);
            rotData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(rotData);

            if (Aware.DEBUG) Log.d(TAG, "Rotation:" + rowData.toString());

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
            getContentResolver().bulkInsert(Rotation_Data.CONTENT_URI, data[0]);
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
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Rotation_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Rotation_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Rotation_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Rotation_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Rotation_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Rotation_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Rotation_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Rotation_Sensor.NAME, sensor.getName());
            rowData.put(Rotation_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Rotation_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Rotation_Sensor.TYPE, sensor.getType());
            rowData.put(Rotation_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Rotation_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Rotation_Sensor.CONTENT_URI, rowData);

            Intent rot_dev = new Intent(ACTION_AWARE_ROTATION);
            rot_dev.putExtra(EXTRA_SENSOR, rowData);
            sendBroadcast(rot_dev);

            if (Aware.DEBUG) Log.d(TAG, "Rotation sensor info: " + rowData.toString());
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "Aware::Rotation";

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        DATABASE_TABLES = Rotation_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Rotation_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Rotation_Sensor.CONTENT_URI, Rotation_Data.CONTENT_URI};

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_ROTATION_LABEL);
        registerReceiver(dataLabeler, filter);

        if (Aware.DEBUG) Log.d(TAG, "Rotation service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mRotation);
        sensorThread.quit();

        wakeLock.release();

        unregisterReceiver(dataLabeler);

        if (Aware.DEBUG) Log.d(TAG, "Rotation service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mRotation == null) {
            if (Aware.DEBUG) Log.w(TAG, "This device does not have a rotation sensor!");
            Aware.setSetting(this, Aware_Preferences.STATUS_ROTATION, false);
            stopSelf();
        } else {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_ROTATION, true);
            saveSensorDevice(mRotation);

            if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_ROTATION).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.FREQUENCY_ROTATION, 200000);
            }

            if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_ROTATION).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.THRESHOLD_ROTATION, 0.0);
            }

            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION))
                    || THRESHOLD != Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_ROTATION))) {

                sensorHandler.removeCallbacksAndMessages(null);
                mSensorManager.unregisterListener(this, mRotation);

                FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION));
                THRESHOLD = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_ROTATION));
            }

            mSensorManager.registerListener(this, mRotation, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION)), sensorHandler);

            if (Aware.DEBUG) Log.d(TAG, "Rotation service active...");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    //Singleton instance of this service
    private static Rotation rotationSrv = Rotation.getService();

    /**
     * Get singleton instance to Rotation service
     *
     * @return Rotation obj
     */
    public static Rotation getService() {
        if (rotationSrv == null) rotationSrv = new Rotation();
        return rotationSrv;
    }

    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        Rotation getService() {
            return Rotation.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}