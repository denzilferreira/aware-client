/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Bluetooth_Provider;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Data;
import com.aware.providers.Bluetooth_Provider.Bluetooth_Sensor;
import com.aware.utils.Aware_Sensor;

/**
 * Bluetooth Module. For now, scans and returns surrounding bluetooth devices and RSSI dB values.
 * @author denzil
 */
public class Bluetooth extends Aware_Sensor {
	
    private static String TAG = "AWARE::Bluetooth";
	
	private static AlarmManager alarmManager = null;
	private static PendingIntent bluetoothScan = null;
	private static Intent backgroundService = null;
	private static long scanTimestamp = 0;
	
    /**
     * This device's bluetooth adapter
     */
	private static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	
	/**
	 * Bluetooth scanning interval in seconds (default = 60)
	 */
	private static int UPDATE_BLUETOOTH_INTERVAL = 60;
	
	/**
	 * Broadcasted event: new bluetooth device detected
	 */
	public static final String ACTION_AWARE_BLUETOOTH_NEW_DEVICE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE";
	
	/**
	 * Broadcasted event: bluetooth scan started
	 */
	public static final String ACTION_AWARE_BLUETOOTH_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_SCAN_STARTED";
	
	/**
	 * Broadcasted event: bluetooth scan ended
	 */
	public static final String ACTION_AWARE_BLUETOOTH_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_SCAN_ENDED";
	
	/**
	 * Broadcast receiving event: request a bluetooth scan
	 */
	public static final String ACTION_AWARE_BLUETOOTH_REQUEST_SCAN = "ACTION_AWARE_BLUETOOTH_REQUEST_SCAN";
	
    /**
     * Bluetooth Service singleton object
     */
    private static Bluetooth bluetoothService = Bluetooth.getService();
    
    /**
     * Get an instance for the Bluetooth Service
     * @return Bluetooth_Service singleton
     */
    public static Bluetooth getService() {
        if( bluetoothService == null ) bluetoothService = new Bluetooth();
        return bluetoothService;
    }
    
    @Override
	public void onCreate() {
		super.onCreate();
		
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
		
		UPDATE_BLUETOOTH_INTERVAL = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_BLUETOOTH));
        
		if( bluetoothAdapter == null ) {
		    if(Aware.DEBUG) Log.w(TAG,"No bluetooth is detected on this device");
		    stopSelf();
		}
		
        if( ! bluetoothAdapter.isEnabled() ) {
            Intent makeEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            makeEnabled.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            startActivity(makeEnabled);
        } 
        
        save_bluetooth_device(bluetoothAdapter);
        
        DATABASE_TABLES = Bluetooth_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Bluetooth_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Bluetooth_Sensor.CONTENT_URI, Bluetooth_Data.CONTENT_URI };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Bluetooth.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(bluetoothMonitor, filter);
        
        backgroundService = new Intent(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
        bluetoothScan = PendingIntent.getBroadcast(this, 0, backgroundService, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000, UPDATE_BLUETOOTH_INTERVAL * 1000, bluetoothScan);
        
        if( Aware.DEBUG ) Log.d(TAG, "Bluetooth service created!");
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        
        if( Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH)) != UPDATE_BLUETOOTH_INTERVAL ) {
            UPDATE_BLUETOOTH_INTERVAL = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH));
            if( ! bluetoothAdapter.isEnabled() ) {
                Intent makeEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                makeEnabled.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                startActivity(makeEnabled);
            }
            alarmManager.cancel(bluetoothScan);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000, UPDATE_BLUETOOTH_INTERVAL * 1000, bluetoothScan);
        }
        
        if( Aware.DEBUG ) Log.d(TAG, "Bluetooth service active...");
        
        return START_STICKY;
    }
    
    @Override
	public void onDestroy() {
		super.onDestroy();
		
		unregisterReceiver(bluetoothMonitor);
		alarmManager.cancel(bluetoothScan);
		
		if( Aware.DEBUG ) Log.d(TAG,"Bluetooth service terminated...");
	}
	
	private final IBinder bluetoothBinder = new BluetoothBinder();
	
    /**
     * Binder for Bluetooth module
     * @author denzil
     */
	public class BluetoothBinder extends Binder {
        Bluetooth getService() {
            return Bluetooth.getService();
        }
    }
	
	@Override
    public IBinder onBind(Intent intent) {
        return bluetoothBinder;
    }
    
	/**
     * BroadcastReceiver for Bluetooth module
     * - ACTION_AWARE_BLUETOOTH_REQUEST_SCAN: request a bluetooth scan
     * - {@link BluetoothDevice#ACTION_FOUND}: a new bluetooth device was detected
     * - {@link BluetoothAdapter#ACTION_DISCOVERY_STARTED}: discovery has started
     * - {@link BluetoothAdapter#ACTION_DISCOVERY_FINISHED}: discovery has finished
     * - ACTION_AWARE_WEBSERVICE: request for webservice remote backup
     * @author denzil
     */
    public static class Bluetooth_Broadcaster extends BroadcastReceiver {
        
    	@Override
        public void onReceive(Context context, Intent intent) {
            
            if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                Bundle extras = intent.getExtras();
                if(extras == null) return;
                
                BluetoothDevice btDevice = (BluetoothDevice) extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                if(btDevice == null) return;
                
                Short btDeviceRSSI = extras.getShort(BluetoothDevice.EXTRA_RSSI);
                
                ContentValues rowData = new ContentValues();
                rowData.put(Bluetooth_Data.DEVICE_ID, Aware.getSetting(context,Aware_Preferences.DEVICE_ID));
                rowData.put(Bluetooth_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Bluetooth_Data.BT_ADDRESS, btDevice.getAddress());
                rowData.put(Bluetooth_Data.BT_NAME, btDevice.getName());
                rowData.put(Bluetooth_Data.BT_RSSI, btDeviceRSSI);
                rowData.put(Bluetooth_Data.BT_LABEL, scanTimestamp);
                
                try{
                    context.getContentResolver().insert(Bluetooth_Data.CONTENT_URI, rowData);
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
            
                if( Aware.DEBUG ) Log.d(TAG, ACTION_AWARE_BLUETOOTH_NEW_DEVICE + rowData.toString());
                Intent detectedBT = new Intent(ACTION_AWARE_BLUETOOTH_NEW_DEVICE);
                context.sendBroadcast(detectedBT);
            }   
            
            if(intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if( Aware.DEBUG ) Log.d(TAG,ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                Intent scanEnd = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_ENDED);
                context.sendBroadcast(scanEnd);
            }
            
            if(intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                scanTimestamp = System.currentTimeMillis();
                if( Aware.DEBUG ) Log.d(TAG,ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                Intent scanStart = new Intent(ACTION_AWARE_BLUETOOTH_SCAN_STARTED);
                context.sendBroadcast(scanStart);
            }
            
            if( intent.getAction().equals(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)) {
                Intent backgroundService = new Intent(context, BackgroundService.class);
                backgroundService.setAction(Bluetooth.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN);
                context.startService(backgroundService);
            }
        }       
    }
    private static final Bluetooth_Broadcaster bluetoothMonitor = new Bluetooth_Broadcaster();
    
    private void save_bluetooth_device( BluetoothAdapter btAdapter ) {
        
        if( btAdapter == null ) return;
        
        Cursor sensorBT = getContentResolver().query(Bluetooth_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorBT !=null && sensorBT.moveToFirst() ) {
            sensorBT.close();
        } else {
            ContentValues rowData = new ContentValues();
            rowData.put(Bluetooth_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Bluetooth_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Bluetooth_Sensor.BT_ADDRESS, btAdapter.getAddress());
            rowData.put(Bluetooth_Sensor.BT_NAME, btAdapter.getName());
            
            try{
                
                getContentResolver().insert(Bluetooth_Sensor.CONTENT_URI, rowData);
                
                if( Aware.DEBUG ) Log.d(TAG,"Bluetooth local information: " + rowData.toString());
                
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }
    }
    
    public static class BackgroundService extends IntentService {
        public BackgroundService() {
            super(TAG+ " background service");
        }
        
        @Override
        protected void onHandleIntent(Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN) ) {
                if( ! bluetoothAdapter.isDiscovering() ) {
                    if( ! bluetoothAdapter.isEnabled() ) {
                    	bluetoothAdapter.enable();
                    }
                    bluetoothAdapter.startDiscovery();
                    
                    if( Aware.DEBUG ) Log.d(TAG,"Bluetooth scan request...");
                }
            }
        }
    }
}
