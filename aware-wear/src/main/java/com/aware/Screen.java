/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Screen_Provider;
import com.aware.providers.Screen_Provider.Screen_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Service that logs users' interactions with the screen
 * - on/off events
 * - locked/unlocked events
 * @author denzil
 *
 */
public class Screen extends Aware_Sensor {
	
	private static String TAG = "AWARE::Screen";
	
	/**
	 * Broadcasted event: screen is on
	 */
	public static final String ACTION_AWARE_SCREEN_ON = "ACTION_AWARE_SCREEN_ON";
	
	/**
	 * Broadcasted event: screen is off
	 */
	public static final String ACTION_AWARE_SCREEN_OFF = "ACTION_AWARE_SCREEN_OFF";
	
	/**
	 * Broadcasted event: screen is locked
	 */
	public static final String ACTION_AWARE_SCREEN_LOCKED = "ACTION_AWARE_SCREEN_LOCKED";
	
	/**
	 * Broadcasted event: screen is unlocked
	 */
	public static final String ACTION_AWARE_SCREEN_UNLOCKED = "ACTION_AWARE_SCREEN_UNLOCKED";
	
	/**
	 * Screen status: OFF = 0
	 */
	public static final int STATUS_SCREEN_OFF = 0;
	
	/**
	 * Screen status: ON = 1
	 */
	public static final int STATUS_SCREEN_ON = 1;
	
	/**
	 * Screen status: LOCKED = 2
	 */
	public static final int STATUS_SCREEN_LOCKED = 2;
	
	/**
	 * Screen status: UNLOCKED = 3
	 */
	public static final int STATUS_SCREEN_UNLOCKED = 3;
	
	/**
	 * Activity-Service binder
	 */
	private final IBinder serviceBinder = new ServiceBinder();
	public class ServiceBinder extends Binder {
		Screen getService() {
			return Screen.getService();
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return serviceBinder;
	}
	
    private static Screen screenSrv = Screen.getService();
    
    /**
     * Singleton instance to service
     * @return Screen
     */
    public static Screen getService() {
    	if ( screenSrv == null ) screenSrv = new Screen();
        return screenSrv;
    }    

	@Override
	public void onCreate() {
		super.onCreate();
		
		TAG = "AWARE::Screen";
		TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        
		DATABASE_TABLES = Screen_Provider.DATABASE_TABLES;
		TABLES_FIELDS = Screen_Provider.TABLES_FIELDS;
		CONTEXT_URIS = new Uri[]{ Screen_Data.CONTENT_URI };
		
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenMonitor, filter);
        
		if(Aware.DEBUG) Log.d(TAG, "Screen service created!");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		unregisterReceiver(screenMonitor);
		
		if(Aware.DEBUG) Log.d(TAG,"Screen service terminated...");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    
	    TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        
        if(Aware.DEBUG) Log.d(TAG, "Screen service active...");
        
	    return START_STICKY;
	}
	
	public static class ScreenMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            
            if(intent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {
                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context,Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_ON);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
                
                if(Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_ON);
                Intent screenOn = new Intent(ACTION_AWARE_SCREEN_ON);
                context.sendBroadcast(screenOn);
            }
            if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                
                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context,Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_OFF);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
            
                if(Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_OFF);
                Intent screenOff = new Intent(ACTION_AWARE_SCREEN_OFF);
                context.sendBroadcast(screenOff);
                
                //If the screen is off, we need to check if the phone is really locked, as some users don't use it at all.
                KeyguardManager km = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
                if( km.inKeyguardRestrictedInputMode() ) {
                    rowData = new ContentValues();
                    rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context,Aware_Preferences.DEVICE_ID));
                    rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_LOCKED);
                    try {
                        context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);
                    }catch( SQLiteException e ) {
                        if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                    }catch( SQLException e ) {
                        if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                    }
                
                    if(Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_LOCKED);
                    Intent screenLocked = new Intent(ACTION_AWARE_SCREEN_LOCKED);
                    context.sendBroadcast(screenLocked);
                }
            }
            if(intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context,Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_UNLOCKED);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
                
                if(Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_UNLOCKED);
                Intent screenUnlocked = new Intent(ACTION_AWARE_SCREEN_UNLOCKED);
                context.sendBroadcast(screenUnlocked);
            }
        }
    }
    private static final ScreenMonitor screenMonitor = new ScreenMonitor();
}
