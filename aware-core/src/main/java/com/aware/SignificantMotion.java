package com.aware;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.Significant_Provider;
import com.aware.providers.Temperature_Provider;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Sensor;

/**
 * Created by denzil on 10/01/2017.
 *
 * This sensor is used to track device significant motion.
 * Also used internally by AWARE if available to save battery when the device is still with high-frequency sensors
 */

public class SignificantMotion extends Aware_Sensor {

    public static String TAG = "AWARE::Resource Manager";

    private static SensorManager mSensorManager;
    private static Sensor mSignificantMotion;
    private SignificantMotionTrigger mSignificantTrigger;

    /**
     * Broadcasted when there is significant motion
     */
    public static final String ACTION_AWARE_SIGNIFICANT_MOTION = "ACTION_AWARE_SIGNIFICANT_MOTION";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";

    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Significant_Provider.Significant_Sensor.CONTENT_URI, null, null, null, null);
        if (sensorInfo == null || !sensorInfo.moveToFirst()) {
            ContentValues rowData = new ContentValues();
            rowData.put(Temperature_Provider.Temperature_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Temperature_Provider.Temperature_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Temperature_Provider.Temperature_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Temperature_Provider.Temperature_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Temperature_Provider.Temperature_Sensor.NAME, sensor.getName());
            rowData.put(Temperature_Provider.Temperature_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Temperature_Provider.Temperature_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Temperature_Provider.Temperature_Sensor.TYPE, sensor.getType());
            rowData.put(Temperature_Provider.Temperature_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Temperature_Provider.Temperature_Sensor.VERSION, sensor.getVersion());

            getContentResolver().insert(Significant_Provider.Significant_Sensor.CONTENT_URI, rowData);
            if (Aware.DEBUG) Log.d(TAG, "Significant motion sensor info: " + rowData.toString());

            Intent significant = new Intent(ACTION_AWARE_SIGNIFICANT_MOTION);
            significant.putExtra(EXTRA_SENSOR, rowData);
            sendBroadcast(significant);
        }
        if (sensorInfo != null && !sensorInfo.isClosed()) sensorInfo.close();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSignificantMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

        DATABASE_TABLES = Significant_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Significant_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Significant_Provider.Significant_Sensor.CONTENT_URI, Significant_Provider.Significant_Data.CONTENT_URI};

        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent moving = new Intent();
                moving.setAction(ACTION_AWARE_SIGNIFICANT_MOTION);
                sendBroadcast(moving);
            }
        };

        if (DEBUG) Log.d(TAG, "Significant motion service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            if (mSignificantMotion == null) {
                if (DEBUG) Log.d(TAG, "This device does not have a significant motion sensor.");
                Aware.setSetting(this, Aware_Preferences.STATUS_SIGNIFICANT_MOTION, false);
                stopSelf();

            } else {

                DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
                Aware.setSetting(this, Aware_Preferences.STATUS_SIGNIFICANT_MOTION, true);

                saveSensorDevice(mSignificantMotion);

                if (mSignificantTrigger == null) {
                    mSignificantTrigger = new SignificantMotionTrigger(getApplicationContext());
                    mSensorManager.requestTriggerSensor(mSignificantTrigger, mSignificantMotion);
                }

                if (Aware.DEBUG) Log.d(TAG, "Significant motion service active...");
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

        if (mSignificantMotion != null && mSignificantTrigger != null)
            mSensorManager.cancelTriggerSensor(mSignificantTrigger, mSignificantMotion);

        if (Aware.DEBUG) Log.d(TAG, "Significant motion service destroyed...");
    }

    public class SignificantMotionTrigger extends TriggerEventListener {
        private Context mContext;

        SignificantMotionTrigger(Context context) {
            mContext = context;
        }

        @Override
        public void onTrigger(TriggerEvent event) {

            ContentValues rowData = new ContentValues();
            rowData.put(Significant_Provider.Significant_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Significant_Provider.Significant_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Significant_Provider.Significant_Data.IS_MOVING, event.values[0] == 1);

            try {
                if (SignificantMotion.DEBUG)
                    Log.d(SignificantMotion.TAG, "Significant motion: " + rowData.toString());

                mContext.getContentResolver().insert(Significant_Provider.Significant_Data.CONTENT_URI, rowData);
            } catch (SQLException e) {
                if (SignificantMotion.DEBUG)
                    e.printStackTrace();
            }

            CONTEXT_PRODUCER.onContext();
        }
    }
}
