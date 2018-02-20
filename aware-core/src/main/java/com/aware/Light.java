
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncRequest;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.aware.providers.Light_Provider;
import com.aware.providers.Light_Provider.Light_Data;
import com.aware.providers.Light_Provider.Light_Sensor;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;

/**
 * AWARE Light module
 * - Light raw data
 * - Light sensor information
 *
 * @author df
 */
public class Light extends Aware_Sensor implements SensorEventListener {

    /**
     * Logging tag (default = "AWARE::Light")
     */
    private static String TAG = "AWARE::Light";

    private static SensorManager mSensorManager;
    private static Sensor mLight;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;

    private static PowerManager.WakeLock wakeLock = null;

    private static Float LAST_VALUE = null;
    private static long LAST_TS = 0;
    private static long LAST_SAVE = 0;

    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;
    // Reject any data points that come in more often than frequency
    private static boolean ENFORCE_FREQUENCY = false;

    /**
     * Broadcasted event: new light values
     * ContentProvider: LightProvider
     */
    public static final String ACTION_AWARE_LIGHT = "ACTION_AWARE_LIGHT";
    public static final String ACTION_AWARE_LIGHT_LABEL = "ACTION_AWARE_LIGHT_LABEL";
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
            if (intent.getAction().equals(ACTION_AWARE_LIGHT_LABEL)) {
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
        long TS = System.currentTimeMillis();
        if (ENFORCE_FREQUENCY && TS < LAST_TS + FREQUENCY / 1000)
            return;
        if (LAST_VALUE != null && THRESHOLD > 0 && Math.abs(event.values[0] - LAST_VALUE) < THRESHOLD) {
            return;
        }

        LAST_TS = TS;
        LAST_VALUE = event.values[0];

        ContentValues rowData = new ContentValues();
        rowData.put(Light_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Light_Data.TIMESTAMP, TS);
        rowData.put(Light_Data.LIGHT_LUX, event.values[0]);
        rowData.put(Light_Data.ACCURACY, event.accuracy);
        rowData.put(Light_Data.LABEL, LABEL);

        if (awareSensor != null) awareSensor.onLightChanged(rowData);

        data_values.add(rowData);
        LAST_TS = TS;

        if (data_values.size() < 250 && TS < LAST_SAVE + 300000) {
            return;
        }

        final ContentValues[] data_buffer = new ContentValues[data_values.size()];
        data_values.toArray(data_buffer);

        try {
            if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("true")) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getContentResolver().bulkInsert(Light_Provider.Light_Data.CONTENT_URI, data_buffer);

                        Intent newData = new Intent(ACTION_AWARE_LIGHT);
                        sendBroadcast(newData);
                    }
                }).run();
            }
        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }
        data_values.clear();
        LAST_SAVE = TS;
    }

    private static Light.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Light.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Light.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onLightChanged(ContentValues data);
    }

    /**
     * Calculates the sampling rate in Hz (i.e., how many samples did we collect in the past second)
     *
     * @param context
     * @return hz
     */
    public static int getFrequency(Context context) {
        int hz = 0;
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Light_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Light_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Light_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Light_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Light_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Light_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Light_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Light_Sensor.NAME, sensor.getName());
            rowData.put(Light_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Light_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Light_Sensor.TYPE, sensor.getType());
            rowData.put(Light_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Light_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Light_Sensor.CONTENT_URI, rowData);

            if (Aware.DEBUG) Log.d(TAG, "Light sensor info: " + rowData.toString());
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Light_Provider.getAuthority(this);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_LIGHT_LABEL);
        registerReceiver(dataLabeler, filter);


        if (Aware.DEBUG) Log.d(TAG, "Light service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mLight);
        sensorThread.quit();

        wakeLock.release();

        unregisterReceiver(dataLabeler);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Light_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Light_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Light service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (mLight == null) {
                if (Aware.DEBUG) Log.w(TAG, "This device does not have a light sensor!");
                Aware.setSetting(this, Aware_Preferences.STATUS_LIGHT, false);
                stopSelf();
            } else {
                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_LIGHT, true);
                saveSensorDevice(mLight);

                if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_LIGHT).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.FREQUENCY_LIGHT, 200000);
                }

                if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_LIGHT).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.THRESHOLD_LIGHT, 0.0);
                }

                int new_frequency = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT));
                double new_threshold = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_LIGHT));
                boolean new_enforce_frequency = (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT_ENFORCE).equals("true")
                        || Aware.getSetting(getApplicationContext(), Aware_Preferences.ENFORCE_FREQUENCY_ALL).equals("true"));

                if (FREQUENCY != new_frequency
                        || THRESHOLD != new_threshold
                        || ENFORCE_FREQUENCY != new_enforce_frequency) {

                    sensorHandler.removeCallbacksAndMessages(null);
                    mSensorManager.unregisterListener(this, mLight);

                    FREQUENCY = new_frequency;
                    THRESHOLD = new_threshold;
                    ENFORCE_FREQUENCY = new_enforce_frequency;
                }

                mSensorManager.registerListener(this, mLight, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT)), sensorHandler);

                if (Aware.isStudy(this)) {
                    ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Light_Provider.getAuthority(this), 1);
                    ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Light_Provider.getAuthority(this), true);
                    long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                    SyncRequest request = new SyncRequest.Builder()
                            .syncPeriodic(frequency, frequency / 3)
                            .setSyncAdapter(Aware.getAWAREAccount(this), Light_Provider.getAuthority(this))
                            .setExtras(new Bundle()).build();
                    ContentResolver.requestSync(request);
                }

                if (Aware.DEBUG) Log.d(TAG, "Light service active: " + FREQUENCY + "ms");
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}