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

import com.aware.providers.Proximity_Provider;
import com.aware.providers.Proximity_Provider.Proximity_Data;
import com.aware.providers.Proximity_Provider.Proximity_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

/**
 * AWARE Proximity module
 * - Proximity raw data in centimeters / (binary far/near for some sensors)
 * - Proximity sensor information
 * @author df
 *
 */
public class Proximity extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mProximity;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new sensor values
     * ContentProvider: ProximityProvider
     */
    public static final String ACTION_AWARE_PROXIMITY = "ACTION_AWARE_PROXIMITY";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Proximity_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
        rowData.put(Proximity_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Proximity_Data.PROXIMITY, event.values[0]);
        rowData.put(Proximity_Data.ACCURACY, event.accuracy);
        
        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		getContentResolver().insert(Proximity_Data.CONTENT_URI, rowData);
        	}
            
            Intent proxyData = new Intent(ACTION_AWARE_PROXIMITY);
            proxyData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(proxyData);
            
            if( Aware.DEBUG ) Log.d(TAG, "Proximity:"+ rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Proximity_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorInfo == null || ! sensorInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Proximity_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Proximity_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Proximity_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Proximity_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Proximity_Sensor.NAME, sensor.getName());
            rowData.put(Proximity_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Proximity_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Proximity_Sensor.TYPE, sensor.getType());
            rowData.put(Proximity_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Proximity_Sensor.VERSION, sensor.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Proximity_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent proxy_dev = new Intent(ACTION_AWARE_PROXIMITY);
            	proxy_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(proxy_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Proximity sensor: "+ rowData.toString());
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
        
        TAG = "AWARE::Proximity";
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        
        if(mProximity == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a proximity sensor!");
            stopSelf();
        }
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_PROXIMITY).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_PROXIMITY));
        } else {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY, SAMPLING_RATE);
        }
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mProximity, SAMPLING_RATE, sensorHandler);
        
        saveSensorDevice(mProximity);
        
        DATABASE_TABLES = Proximity_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Proximity_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Proximity_Sensor.CONTENT_URI, Proximity_Data.CONTENT_URI };
        
        if(Aware.DEBUG) Log.d(TAG,"Proximity service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mProximity);
        sensorThread.quit();
        
        wakeLock.release();
        
        if(Aware.DEBUG) Log.d(TAG,"Proximity service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):"AWARE::Proximity";

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_PROXIMITY))) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_PROXIMITY));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mProximity);
            mSensorManager.registerListener(this, mProximity, SAMPLING_RATE, sensorHandler);
        }

        if(Aware.DEBUG) Log.d(TAG,"Proximity service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Proximity proximitySrv = Proximity.getService();
    
    /**
     * Get singleton instance to service
     * @return Proximity obj
     */
    public static Proximity getService() {
        if( proximitySrv == null ) proximitySrv = new Proximity();
        return proximitySrv;
    }
    
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        Proximity getService() {
            return Proximity.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}