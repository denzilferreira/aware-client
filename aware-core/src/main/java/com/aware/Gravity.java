
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

import com.aware.providers.Gravity_Provider;
import com.aware.providers.Gravity_Provider.Gravity_Data;
import com.aware.providers.Gravity_Provider.Gravity_Sensor;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;
import java.util.List;

/**
 * AWARE Gravity module
 * - Gravity raw data
 * - Gravity sensor information
 *
 * @author df
 */
public class Gravity extends Aware_Sensor implements SensorEventListener {

    /**
     * Logging tag (default = "AWARE::Gravity")
     */
    private static String TAG = "AWARE::Gravity";

    private static SensorManager mSensorManager;
    private static Sensor mGravity;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;

    private static Float[] LAST_VALUES = null;
    private static long LAST_TS = 0;
    private static long LAST_SAVE = 0;

    private static int FREQUENCY = -1;
    private static double THRESHOLD = 0;
    // Reject any data points that come in more often than frequency
    private static boolean ENFORCE_FREQUENCY = false;

    /**
     * Broadcasted event: new sensor values
     * ContentProvider: Gravity_Provider
     */
    public static final String ACTION_AWARE_GRAVITY = "ACTION_AWARE_GRAVITY";
    public static final String ACTION_AWARE_GRAVITY_LABEL = "ACTION_AWARE_GRAVITY_LABEL";
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
            if (intent.getAction().equals(ACTION_AWARE_GRAVITY_LABEL)) {
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
                final ContentValues[] data_buffer = new ContentValues[data_values.size()];
                data_values.toArray(data_buffer);
                try {
                    if (!Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("true")) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                getContentResolver().bulkInsert(Gravity_Provider.Gravity_Data.CONTENT_URI, data_buffer);

                                Intent newData = new Intent(ACTION_AWARE_GRAVITY);
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
            }
            return;
        }

        long TS = System.currentTimeMillis();
        if (ENFORCE_FREQUENCY && TS < LAST_TS + FREQUENCY / 1000)
            return;
        if (LAST_VALUES != null && THRESHOLD > 0 && Math.abs(event.values[0] - LAST_VALUES[0]) < THRESHOLD
                && Math.abs(event.values[1] - LAST_VALUES[1]) < THRESHOLD
                && Math.abs(event.values[2] - LAST_VALUES[2]) < THRESHOLD) {
            return;
        }

        LAST_VALUES = new Float[]{event.values[0], event.values[1], event.values[2]};

        ContentValues rowData = new ContentValues();
        rowData.put(Gravity_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Gravity_Data.TIMESTAMP, TS);
        rowData.put(Gravity_Data.VALUES_0, event.values[0]);
        rowData.put(Gravity_Data.VALUES_1, event.values[1]);
        rowData.put(Gravity_Data.VALUES_2, event.values[2]);
        rowData.put(Gravity_Data.ACCURACY, event.accuracy);
        rowData.put(Gravity_Data.LABEL, LABEL);

        if (awareSensor != null) awareSensor.onGravityChanged(rowData);

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
                        getContentResolver().bulkInsert(Gravity_Provider.Gravity_Data.CONTENT_URI, data_buffer);

                        Intent newData = new Intent(ACTION_AWARE_GRAVITY);
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

    private static Gravity.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Gravity.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Gravity.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onGravityChanged(ContentValues data);
    }

    /**
     * Calculates the sampling rate in Hz (i.e., how many samples did we collect in the past second)
     *
     * @param context
     * @return hz
     */
    public static int getFrequency(Context context) {
        int hz = 0;
        String[] columns = new String[]{"count(*) as frequency", "datetime(" + Gravity_Data.TIMESTAMP + "/1000, 'unixepoch','localtime') as sample_time"};
        Cursor qry = context.getContentResolver().query(Gravity_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if (qry != null && qry.moveToFirst()) {
            hz = qry.getInt(0);
        }
        if (qry != null && !qry.isClosed()) qry.close();
        return hz;
    }

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Gravity_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Gravity_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Gravity_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Gravity_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Gravity_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Gravity_Sensor.NAME, sensor.getName());
            rowData.put(Gravity_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Gravity_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Gravity_Sensor.TYPE, sensor.getType());
            rowData.put(Gravity_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Gravity_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Gravity_Sensor.CONTENT_URI, rowData);

            if (Aware.DEBUG) Log.d(TAG, "Gravity sensor: " + rowData.toString());
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Gravity_Provider.getAuthority(this);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_GRAVITY_LABEL);
        registerReceiver(dataLabeler, filter);

        if (Aware.DEBUG) Log.d(TAG, "Gravity service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mGravity);

        sensorThread.quit();

        wakeLock.release();

        unregisterReceiver(dataLabeler);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Gravity_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Gravity_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Gravity service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (mGravity == null) {
                if (Aware.DEBUG) Log.w(TAG, "This device does not have a gravity sensor!");
                Aware.setSetting(this, Aware_Preferences.STATUS_GRAVITY, false);
                stopSelf();
            } else {
                Aware.setSetting(this, Aware_Preferences.STATUS_GRAVITY, true);
                saveSensorDevice(mGravity);

                if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_GRAVITY).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.FREQUENCY_GRAVITY, 200000);
                }

                if (Aware.getSetting(this, Aware_Preferences.THRESHOLD_GRAVITY).length() == 0) {
                    Aware.setSetting(this, Aware_Preferences.THRESHOLD_GRAVITY, 0.0);
                }

                int new_frequency = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY));
                double new_threshold = Double.parseDouble(Aware.getSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_GRAVITY));
                boolean new_enforce_frequency = (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY_ENFORCE).equals("true")
                        || Aware.getSetting(getApplicationContext(), Aware_Preferences.ENFORCE_FREQUENCY_ALL).equals("true"));

                if (FREQUENCY != new_frequency
                        || THRESHOLD != new_threshold
                        || ENFORCE_FREQUENCY != new_enforce_frequency) {

                    sensorHandler.removeCallbacksAndMessages(null);
                    mSensorManager.unregisterListener(this, mGravity);

                    FREQUENCY = new_frequency;
                    THRESHOLD = new_threshold;
                    ENFORCE_FREQUENCY = new_enforce_frequency;
                }

                mSensorManager.registerListener(this, mGravity, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY)), sensorHandler);
                LAST_SAVE = System.currentTimeMillis();

                if (Aware.DEBUG) Log.d(TAG, "Gravity service active: " + FREQUENCY + "ms");

                if (Aware.isStudy(this)) {
                    ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Gravity_Provider.getAuthority(this), 1);
                    ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Gravity_Provider.getAuthority(this), true);
                    long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                    SyncRequest request = new SyncRequest.Builder()
                            .syncPeriodic(frequency, frequency / 3)
                            .setSyncAdapter(Aware.getAWAREAccount(this), Gravity_Provider.getAuthority(this))
                            .setExtras(new Bundle()).build();
                    ContentResolver.requestSync(request);
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}