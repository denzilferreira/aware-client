package com.aware;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
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

import com.aware.providers.Significant_Provider;
import com.aware.utils.Aware_Sensor;

import java.util.ArrayList;

/**
 * Created by denzil on 10/01/2017.
 * <p>
 * This sensor is used to track device significant motion.
 * Also used internally by AWARE if available to save battery when the device is still with high-frequency sensors
 * Based of:
 * https://github.com/sensorplatforms/open-sensor-platform/blob/master/embedded/common/alg/significantmotiondetector.c
 */

public class SignificantMotion extends Aware_Sensor implements SensorEventListener {

    public static String TAG = "AWARE::Significant";

    private static SensorManager mSensorManager;
    private static Sensor mAccelerometer;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager.WakeLock wakeLock = null;

    private static boolean LAST_SIGMOTION_STATE = false;
    public static boolean CURRENT_SIGMOTION_STATE = false;
    public static boolean isSignificantMotionActive = false;
    private static final double SIGMOTION_THRESHOLD = 1.0f;

    /**
     * Broadcasted when there is significant motion
     */
    public static final String ACTION_AWARE_SIGNIFICANT_MOTION_START = "ACTION_AWARE_SIGNIFICANT_MOTION_START";
    public static final String ACTION_AWARE_SIGNIFICANT_MOTION_END = "ACTION_AWARE_SIGNIFICANT_MOTION_END";

    private static SignificantMotion.AWARESensorObserver awareSensor;

    public static void setSensorObserver(SignificantMotion.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static SignificantMotion.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onSignificantMotionStart();

        void onSignificantMotionEnd();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Significant_Provider.getAuthority(this);

        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        sensorHandler = new Handler(sensorThread.getLooper());

        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                ContentValues rowData = new ContentValues();
                rowData.put(Significant_Provider.Significant_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                rowData.put(Significant_Provider.Significant_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Significant_Provider.Significant_Data.IS_MOVING, CURRENT_SIGMOTION_STATE);
                getContentResolver().insert(Significant_Provider.Significant_Data.CONTENT_URI, rowData);

                if (DEBUG)
                    Log.d(SignificantMotion.TAG, "Significant motion: " + rowData.toString());

                Intent sigmotion = new Intent();
                if (CURRENT_SIGMOTION_STATE) {
                    sigmotion.setAction(ACTION_AWARE_SIGNIFICANT_MOTION_START);
                    if (awareSensor != null) awareSensor.onSignificantMotionStart();
                } else {
                    sigmotion.setAction(ACTION_AWARE_SIGNIFICANT_MOTION_END);
                    if (awareSensor != null) awareSensor.onSignificantMotionEnd();
                }
                sendBroadcast(sigmotion);
            }
        };

        if (DEBUG) Log.d(TAG, "Significant motion service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            if (mAccelerometer == null) {
                if (DEBUG)
                    Log.d(TAG, "This device does not have an accelerometer sensor. Can't detect significant motion");
                Aware.setSetting(this, Aware_Preferences.STATUS_SIGNIFICANT_MOTION, false);
                stopSelf();

            } else {

                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_SIGNIFICANT_MOTION, true);

                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI, sensorHandler);
                isSignificantMotionActive = true;

                if (Aware.DEBUG) Log.d(TAG, "Significant motion service active...");
            }

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Significant_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Significant_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Significant_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isSignificantMotionActive = false;
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mAccelerometer);
        sensorThread.quit();
        wakeLock.release();

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Significant_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Significant_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Significant motion service destroyed...");
    }

    private ArrayList<Double> buffer = new ArrayList<>();

    @Override
    public void onSensorChanged(SensorEvent event) {
        double x = event.values[0];
        double y = event.values[1];
        double z = event.values[2];

        double mSignificantEnergy = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        buffer.add(Math.abs(mSignificantEnergy));

        if (buffer.size() == 40) {
            //remove oldest value
            buffer.remove(0);

            double max_energy = -1;
            for (double e : buffer) {
                if (e >= max_energy) max_energy = e;
            }

            if (max_energy >= SIGMOTION_THRESHOLD) {
                CURRENT_SIGMOTION_STATE = true;
            } else if (max_energy < SIGMOTION_THRESHOLD) {
                CURRENT_SIGMOTION_STATE = false;
            }

            if (CURRENT_SIGMOTION_STATE != LAST_SIGMOTION_STATE)
                CONTEXT_PRODUCER.onContext();

            LAST_SIGMOTION_STATE = CURRENT_SIGMOTION_STATE;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
