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

import com.aware.providers.Rotation_Provider;
import com.aware.providers.Rotation_Provider.Rotation_Data;
import com.aware.providers.Rotation_Provider.Rotation_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

/**
 * AWARE Rotation module
 * - Rotation raw data
 * - Rotation sensor information
 * @author df
 *
 */
public class Rotation extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::Rotation")
     */
    private static String TAG = "AWARE::Rotation";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mRotation;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new rotation values
     * ContentProvider: RotationProvider
     */
    public static final String ACTION_AWARE_ROTATION = "ACTION_AWARE_ROTATION";
    public static final String EXTRA_SENSOR = "sensor";
    public static final String EXTRA_DATA = "data";
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Rotation_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
        rowData.put(Rotation_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Rotation_Data.VALUES_0, event.values[0]);
        rowData.put(Rotation_Data.VALUES_1, event.values[1]);
        rowData.put(Rotation_Data.VALUES_2, event.values[2]);
        if( event.values.length == 4 ) {
        	rowData.put(Rotation_Data.VALUES_3, event.values[3]);
        }
        rowData.put(Rotation_Data.ACCURACY, event.accuracy);
        
        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		getContentResolver().insert(Rotation_Data.CONTENT_URI, rowData);
        	}
            
            Intent rotData = new Intent(ACTION_AWARE_ROTATION);
            rotData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(rotData);
            
            if( Aware.DEBUG ) Log.d(TAG, "Rotation:"+ rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    private void saveSensorDevice(Sensor sensor) {
        Cursor sensorInfo = getContentResolver().query(Rotation_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorInfo == null || ! sensorInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Rotation_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Rotation_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Rotation_Sensor.MAXIMUM_RANGE, sensor.getMaximumRange());
            rowData.put(Rotation_Sensor.MINIMUM_DELAY, sensor.getMinDelay());
            rowData.put(Rotation_Sensor.NAME, sensor.getName());
            rowData.put(Rotation_Sensor.POWER_MA, sensor.getPower());
            rowData.put(Rotation_Sensor.RESOLUTION, sensor.getResolution());
            rowData.put(Rotation_Sensor.TYPE, sensor.getType());
            rowData.put(Rotation_Sensor.VENDOR, sensor.getVendor());
            rowData.put(Rotation_Sensor.VERSION, sensor.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Rotation_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent rot_dev = new Intent(ACTION_AWARE_ROTATION);
            	rot_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(rot_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Rotation sensor info: "+ rowData.toString());
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
        
        TAG = "Aware::Rotation";
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        
        if(mRotation == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a rotation sensor!");
            stopSelf();
        }
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_ROTATION).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_ROTATION));
        } else {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION, SAMPLING_RATE);
        }
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mRotation, SAMPLING_RATE, sensorHandler);
        
        saveSensorDevice(mRotation);
        
        DATABASE_TABLES = Rotation_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Rotation_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Rotation_Sensor.CONTENT_URI, Rotation_Data.CONTENT_URI };
        
        if(Aware.DEBUG) Log.d(TAG,"Rotation service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mRotation);
        sensorThread.quit();
        
        wakeLock.release();
        
        if(Aware.DEBUG) Log.d(TAG,"Rotation service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_ROTATION))) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_ROTATION));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mRotation);
            mSensorManager.registerListener(this, mRotation, SAMPLING_RATE, sensorHandler);
        }

        if(Aware.DEBUG) Log.d(TAG,"Rotation service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Rotation rotationSrv = Rotation.getService();
    
    /**
     * Get singleton instance to Rotation service
     * @return Rotation obj
     */
    public static Rotation getService() {
        if( rotationSrv == null ) rotationSrv = new Rotation();
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