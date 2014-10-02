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

import com.aware.providers.Gravity_Provider;
import com.aware.providers.Gravity_Provider.Gravity_Data;
import com.aware.providers.Gravity_Provider.Gravity_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

/**
 * AWARE Gravity module
 * - Gravity raw data
 * - Gravity sensor information
 * @author df
 *
 */
public class Gravity extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::Gravity")
     */
    private static String TAG = "AWARE::Gravity";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mGravity;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new sensor values
     * ContentProvider: Gravity_Provider
     */
    public static final String ACTION_AWARE_GRAVITY = "ACTION_AWARE_GRAVITY";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Gravity_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Gravity_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Gravity_Data.VALUES_0, event.values[0]);
        rowData.put(Gravity_Data.VALUES_1, event.values[1]);
        rowData.put(Gravity_Data.VALUES_2, event.values[2]);
        rowData.put(Gravity_Data.ACCURACY, event.accuracy);
        
        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		getContentResolver().insert(Gravity_Data.CONTENT_URI, rowData);
        	}
            
            Intent gravityData = new Intent(ACTION_AWARE_GRAVITY);
            gravityData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(gravityData);
            
            if( Aware.DEBUG ) Log.d(TAG, "Gravity:"+ rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Gravity_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorInfo == null || ! sensorInfo.moveToFirst() ) {
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
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Gravity_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent grav_dev = new Intent(ACTION_AWARE_GRAVITY);
            	grav_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(grav_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Gravity sensor: "+ rowData.toString());
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
        
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        
        if(mGravity == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a gravity sensor!");
            stopSelf();
        }
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_GRAVITY).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY));
        } else {
            Aware.setSetting(this, Aware_Preferences.FREQUENCY_GRAVITY, SAMPLING_RATE);
        }
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mGravity, SAMPLING_RATE, sensorHandler);
        
        saveSensorDevice(mGravity);
        
        DATABASE_TABLES = Gravity_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Gravity_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Gravity_Sensor.CONTENT_URI, Gravity_Data.CONTENT_URI };
        
        if(Aware.DEBUG) Log.d(TAG,"Gravity service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mGravity);
        
        sensorThread.quit();
        
        wakeLock.release();
        
        if(Aware.DEBUG) Log.d(TAG,"Gravity service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if( SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY))) { //changed setting
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mGravity);
            mSensorManager.registerListener(this, mGravity, SAMPLING_RATE, sensorHandler);
        }

        if(Aware.DEBUG) Log.d(TAG,"Gravity service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Gravity gravitySrv = Gravity.getService();
    
    /**
     * Get singleton instance to service
     * @return Gravity obj
     */
    public static Gravity getService() {
        if( gravitySrv == null ) gravitySrv = new Gravity();
        return gravitySrv;
    }
    
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        Gravity getService() {
            return Gravity.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
    	return serviceBinder;
    }
}