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
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.TimeZone_Provider;
import com.aware.providers.TimeZone_Provider.TimeZone_Data;
import com.aware.utils.Aware_Sensor;

/**
* TimeZone module. Keeps track of changes in the device TimeZone.
* @author Nikola
* Changes log:
* 17 June 2013
* - Added copyright notice, AWARE device ID to timezone context provider (@author Denzil Ferreira <denzil.ferreira@ee.oulu.fi>)
*/
public class TimeZone extends Aware_Sensor {

	/**
     * Frequency of update of timeZone information. (default = 3600) seconds (=1 hour)
     */
    private static int TIMEZONE_UPDATE = 3600;
    
    /**
     * Broadcasted event: when there is new timezone information
     */
    public static final String ACTION_AWARE_TIMEZONE = "ACTION_AWARE_TIMEZONE";
    
    private static Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			
			String timeZone = java.util.TimeZone.getDefault().getID();
			ContentValues rowData = new ContentValues();
            rowData.put(TimeZone_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(TimeZone_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(TimeZone_Data.TIMEZONE, timeZone);
            
            try{
                getContentResolver().insert(TimeZone_Data.CONTENT_URI, rowData);
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( IllegalStateException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
            
            Intent newTimeZone = new Intent(ACTION_AWARE_TIMEZONE);
            sendBroadcast(newTimeZone);
            
            TIMEZONE_UPDATE = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE));
            mHandler.postDelayed(mRunnable, TIMEZONE_UPDATE * 1000);
		}
	};
    
    private final IBinder serviceBinder = new ServiceBinder();
    /**
     * Activity-Service binder
     */
    public class ServiceBinder extends Binder {
        TimeZone getService() {
            return TimeZone.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
    
    private static TimeZone timeZoneSrv = TimeZone.getService();
    
    /**
     * Singleton instance of this service
     * @return {@link TimeZone} obj
     */
    public static TimeZone getService() {
        if( timeZoneSrv == null ) timeZoneSrv = new TimeZone();
        return timeZoneSrv;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        TAG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):"AWARE::TimeZone";
        
        TIMEZONE_UPDATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_TIMEZONE));
        
        DATABASE_TABLES = TimeZone_Provider.DATABASE_TABLES;
        TABLES_FIELDS = TimeZone_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ TimeZone_Data.CONTENT_URI };
        
        CONTEXT_PRODUCER = new ContextProducer() {
			@Override
			public void onContext() {
				Intent newTimeZone = new Intent(ACTION_AWARE_TIMEZONE);
	            sendBroadcast(newTimeZone);
			}
		};
        
        mHandler.postDelayed(mRunnable, 1000);
        if(Aware.DEBUG) Log.d(TAG,"TimeZone service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TAG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG):"AWARE::TimeZone";
        
        if( Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_TIMEZONE)) != TIMEZONE_UPDATE ) {
            TIMEZONE_UPDATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_TIMEZONE));
        }
        
        if(Aware.DEBUG) Log.d(TAG,"TimeZone service active...");
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        mHandler.removeCallbacks(mRunnable);
        
        if(Aware.DEBUG) Log.d(TAG,"TimeZone service terminated...");
    }
}
