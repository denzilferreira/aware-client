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

import com.aware.providers.Light_Provider;
import com.aware.providers.Light_Provider.Light_Data;
import com.aware.providers.Light_Provider.Light_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

/**
 * AWARE Light module
 * - Light raw data
 * - Light sensor information
 * @author df
 *
 */
public class Light extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::Light")
     */
    private static String TAG = "AWARE::Light";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mLight;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new light values
     * ContentProvider: LightProvider
     */
    public static final String ACTION_AWARE_LIGHT = "ACTION_AWARE_LIGHT";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Light_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
        rowData.put(Light_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Light_Data.LIGHT_LUX, event.values[0]);
        rowData.put(Light_Data.ACCURACY, event.accuracy);
        
        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		getContentResolver().insert(Light_Data.CONTENT_URI, rowData);
        	}
            
            Intent lightData = new Intent(ACTION_AWARE_LIGHT);
            lightData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(lightData);
            
            if( Aware.DEBUG ) Log.d(TAG, "Light:"+ rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Light_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorInfo == null || ! sensorInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Light_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Light_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Light_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Light_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Light_Sensor.NAME, sensor.getName());
            rowData.put(Light_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Light_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Light_Sensor.TYPE, sensor.getType());
            rowData.put(Light_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Light_Sensor.VERSION, sensor.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Light_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent light_dev = new Intent(ACTION_AWARE_LIGHT);
            	light_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(light_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Light sensor info: "+ rowData.toString());
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
        
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        
        if(mLight == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a light sensor!");
            stopSelf();
        }
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_LIGHT).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LIGHT));
        } else {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT, SAMPLING_RATE);
        }
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mLight, SAMPLING_RATE, sensorHandler);
        
        saveSensorDevice(mLight);
        
        DATABASE_TABLES = Light_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Light_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Light_Sensor.CONTENT_URI, Light_Data.CONTENT_URI };
        
        if(Aware.DEBUG) Log.d(TAG,"Light service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mLight);
        sensorThread.quit();
        
        wakeLock.release();
        
        if(Aware.DEBUG) Log.d(TAG,"Light service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LIGHT))) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LIGHT));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mLight);
            mSensorManager.registerListener(this, mLight, SAMPLING_RATE, sensorHandler);
        }
        if(Aware.DEBUG) Log.d(TAG,"Light service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Light lightSrv = Light.getService();
    
    /**
     * Get singleton instance to service
     * @return Light obj
     */
    public static Light getService() {
        if( lightSrv == null ) lightSrv = new Light();
        return lightSrv;
    }
    
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        Light getService() {
            return Light.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}