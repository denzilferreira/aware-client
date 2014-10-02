/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.aware.providers.Magnetometer_Provider;
import com.aware.providers.Magnetometer_Provider.Magnetometer_Data;
import com.aware.providers.Magnetometer_Provider.Magnetometer_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

/**
 * AWARE Magnetometer module
 * - Magnetometer raw data
 * - Magnetometer sensor information
 * @author df
 *
 */
public class Magnetometer extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::Magnetometer")
     */
    private static String TAG = "AWARE::Magnetometer";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mMagnetometer;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new sensor values
     * ContentProvider: MagnetometerProvider
     */
    public static final String ACTION_AWARE_MAGNETOMETER = "ACTION_AWARE_MAGNETOMETER";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Magnetometer_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Magnetometer_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Magnetometer_Data.VALUES_0, event.values[0]);
        rowData.put(Magnetometer_Data.VALUES_1, event.values[1]);
        rowData.put(Magnetometer_Data.VALUES_2, event.values[2]);
        rowData.put(Magnetometer_Data.ACCURACY, event.accuracy);
        
        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		getContentResolver().insert(Magnetometer_Data.CONTENT_URI, rowData);
        	}
            
            Intent magnetoData = new Intent(ACTION_AWARE_MAGNETOMETER);
            magnetoData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(magnetoData);
            
            if( Aware.DEBUG ) Log.d(TAG, "Magnetometer:"+ rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Magnetometer_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorInfo == null || ! sensorInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Magnetometer_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Magnetometer_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Magnetometer_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Magnetometer_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Magnetometer_Sensor.NAME, sensor.getName());
            rowData.put(Magnetometer_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Magnetometer_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Magnetometer_Sensor.TYPE, sensor.getType());
            rowData.put(Magnetometer_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Magnetometer_Sensor.VERSION, sensor.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Magnetometer_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent magneto_dev = new Intent(ACTION_AWARE_MAGNETOMETER);
            	magneto_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(magneto_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Magnetometer sensor: "+ rowData.toString());
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }else sensorInfo.close();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
        if(mMagnetometer == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a magnetometer!");
            stopSelf();
        }
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_MAGNETOMETER));
        } else {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER, SAMPLING_RATE);
        }
        
        DATABASE_TABLES = Magnetometer_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Magnetometer_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Magnetometer_Sensor.CONTENT_URI, Magnetometer_Data.CONTENT_URI };
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mMagnetometer, SAMPLING_RATE, sensorHandler);
        
        saveSensorDevice(mMagnetometer);
        
        if(Aware.DEBUG) Log.d(TAG,"Magnetometer service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mMagnetometer);
        sensorThread.quit();
        
        wakeLock.release();
        
        if(Aware.DEBUG) Log.d(TAG,"Magnetometer service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_MAGNETOMETER))) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_MAGNETOMETER));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mMagnetometer);
            mSensorManager.registerListener(this, mMagnetometer, SAMPLING_RATE, sensorHandler);
        }
        
        if(Aware.DEBUG) Log.d(TAG,"Magnetometer service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Magnetometer magnetometerSrv = Magnetometer.getService();
    
    /**
     * Get singleton instance to service
     * @return Magnetometer obj
     */
    public static Magnetometer getService() {
        if( magnetometerSrv == null ) magnetometerSrv = new Magnetometer();
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