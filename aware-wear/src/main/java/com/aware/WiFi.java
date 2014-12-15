/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

import java.util.List;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.WiFi_Provider;
import com.aware.providers.WiFi_Provider.WiFi_Data;
import com.aware.providers.WiFi_Provider.WiFi_Sensor;
import com.aware.utils.Aware_Sensor;

/**
 * WiFi Module. Scans and returns surrounding WiFi AccessPoints devices information and RSSI dB values.
 * @author denzil
 */
public class WiFi extends Aware_Sensor {
	
    private static String TAG = "AWARE::WiFi";
	
	private static AlarmManager alarmManager = null;
	private static WifiManager wifiManager = null;
	private static PendingIntent wifiScan = null;
	private static Intent backgroundService = null;
	
	/**
	 * WiFi scanning interval in seconds (default = 60)
	 */
	private static int UPDATE_WIFI_INTERVAL = 60;
	
	/**
	 * Broadcasted event: new WiFi AP device detected
	 */
	public static final String ACTION_AWARE_WIFI_NEW_DEVICE = "ACTION_AWARE_WIFI_NEW_DEVICE";
	
	/**
	 * Broadcasted event: WiFi scan started
	 */
	public static final String ACTION_AWARE_WIFI_SCAN_STARTED = "ACTION_AWARE_WIFI_SCAN_STARTED";
	
	/**
	 * Broadcasted event: WiFi scan ended
	 */
	public static final String ACTION_AWARE_WIFI_SCAN_ENDED = "ACTION_AWARE_WIFI_SCAN_ENDED";
	
	/**
	 * Broadcast receiving event: request a WiFi scan
	 */
	public static final String ACTION_AWARE_WIFI_REQUEST_SCAN = "ACTION_AWARE_WIFI_REQUEST_SCAN";
	
    /**
     * Bluetooth Service singleton object
     */
    private static WiFi wifiService = WiFi.getService();
    
    /**
     * Get an instance for the WiFi Service
     * @return WiFi obj
     */
    public static WiFi getService() {
        if( wifiService == null ) wifiService = new WiFi();
        return wifiService;
    }
    
    @Override
	public void onCreate() {
		super.onCreate();
		
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		
		TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
		
		UPDATE_WIFI_INTERVAL = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_WIFI));
        
		IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiMonitor, filter);
		
        DATABASE_TABLES = WiFi_Provider.DATABASE_TABLES;
		TABLES_FIELDS = WiFi_Provider.TABLES_FIELDS;
		CONTEXT_URIS = new Uri[] { WiFi_Data.CONTENT_URI, WiFi_Sensor.CONTENT_URI };
        
		if( wifiManager.isWifiEnabled() ) {
			save_wifi_device(wifiManager.getConnectionInfo());
			backgroundService = new Intent(this,BackgroundService.class);
	        backgroundService.setAction(ACTION_AWARE_WIFI_REQUEST_SCAN);
	        wifiScan = PendingIntent.getService(this, 0, backgroundService, 0);
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000, UPDATE_WIFI_INTERVAL * 1000, wifiScan);
	        if( Aware.DEBUG ) Log.d(TAG, "WiFi service created!");
		} else {
			stopSelf();
		}
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( wifiManager.isWifiEnabled() ) {
    		TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
	        if( Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_WIFI)) != UPDATE_WIFI_INTERVAL ) {
	            UPDATE_WIFI_INTERVAL = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_WIFI));
	            alarmManager.cancel(wifiScan);
	            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000, UPDATE_WIFI_INTERVAL * 1000, wifiScan);
	        }
	        if( Aware.DEBUG ) Log.d(TAG, "WiFi service active...");
    	}
        
        return START_STICKY;
    }
    
    @Override
	public void onDestroy() {
		super.onDestroy();
		
		if( wifiMonitor != null ) unregisterReceiver(wifiMonitor);
		if( wifiScan != null ) alarmManager.cancel(wifiScan);
		
		if( Aware.DEBUG ) Log.d(TAG,"WiFi service terminated...");
	}
	
	private final IBinder wifiBinder = new WiFiBinder();
	
    /**
     * Binder for WiFi module
     * @author denzil
     */
	public class WiFiBinder extends Binder {
        WiFi getService() {
            return WiFi.getService();
        }
    }
	
	@Override
    public IBinder onBind(Intent intent) {
        return wifiBinder;
    }
	
	public static class WiFiMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) ) {
                Intent backgroundService = new Intent(context, BackgroundService.class);
                backgroundService.setAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                context.startService(backgroundService);
            }
        }
    }
    private static final WiFiMonitor wifiMonitor = new WiFiMonitor();
    
    /**
     * Save this device's wifi information
     * @param wifi
     */
    private void save_wifi_device( WifiInfo wifi ) {
        if( wifi == null ) return;
        
        Cursor sensorWifi = getContentResolver().query(WiFi_Sensor.CONTENT_URI, null, null, null, null);
        if( sensorWifi != null && sensorWifi.moveToFirst() ) {
            sensorWifi.close();
            return;
        }else sensorWifi.close();
        
        ContentValues rowData = new ContentValues();
        rowData.put(WiFi_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(WiFi_Sensor.TIMESTAMP, System.currentTimeMillis());
        rowData.put(WiFi_Sensor.MAC_ADDRESS, wifi.getMacAddress());
        rowData.put(WiFi_Sensor.BSSID, ((wifi.getBSSID()!=null)?wifi.getBSSID():""));
        rowData.put(WiFi_Sensor.SSID, ((wifi.getSSID()!=null)?wifi.getSSID():""));
        
        try{
            getContentResolver().insert(WiFi_Sensor.CONTENT_URI, rowData);
            if( Aware.DEBUG ) Log.d(TAG,"WiFi local sensor information: " + rowData.toString());
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }
    }
    
    /**
     * Background service for WiFi module
     * - ACTION_AWARE_WIFI_REQUEST_SCAN
     * - {@link WifiManager#SCAN_RESULTS_AVAILABLE_ACTION}
     * - ACTION_AWARE_WEBSERVICE
     * 
     * @author df
     */
    public static class BackgroundService extends IntentService {
        public BackgroundService() {
            super(TAG+ " background service");
        }
        
        @Override
        protected void onHandleIntent(Intent intent) {
        	if(wifiManager == null) wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        	
            if( intent.getAction().equals(WiFi.ACTION_AWARE_WIFI_REQUEST_SCAN)) {
            	if(!wifiManager.isWifiEnabled() ) wifiManager.setWifiEnabled(true);
                wifiManager.startScan();
                
                Intent scanStart = new Intent(ACTION_AWARE_WIFI_SCAN_STARTED);
                sendBroadcast(scanStart);
            }
            
            if( intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                
            	// NB: added writing of the current connected WiFi.
            	WifiInfo wifi = wifiManager.getConnectionInfo();
            	if( wifi != null ) {
            		
            		ContentValues rowData = new ContentValues();
	                rowData.put(WiFi_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
	                rowData.put(WiFi_Sensor.TIMESTAMP, System.currentTimeMillis());
	                rowData.put(WiFi_Sensor.MAC_ADDRESS, wifi.getMacAddress());
	                rowData.put(WiFi_Sensor.BSSID, ((wifi.getBSSID()!=null)?wifi.getBSSID():""));
	                rowData.put(WiFi_Sensor.SSID, ((wifi.getSSID()!=null)?wifi.getSSID():""));
	                
	                try{
	                    
	                    getContentResolver().insert(WiFi_Sensor.CONTENT_URI, rowData);
	                    
	                    if( Aware.DEBUG ) Log.d(TAG,"WiFi local sensor information: " + rowData.toString());
	                    
	                }catch( SQLiteException e ) {
	                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
	                }catch( SQLException e ) {
	                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
	                }
            	}
                
                // NB: done adding connected WiFi.
            	
                List<ScanResult> aps = wifiManager.getScanResults();
                if( Aware.DEBUG ) Log.d(TAG,"Found " + aps.size()+" access points");
                
                long currentScan = System.currentTimeMillis();
                
                for(ScanResult ap : aps) {
                    ContentValues rowData = new ContentValues();
                    rowData.put(WiFi_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
                    rowData.put(WiFi_Data.TIMESTAMP, currentScan);
                    rowData.put(WiFi_Data.BSSID, ap.BSSID);
                    rowData.put(WiFi_Data.SSID, ap.SSID);
                    rowData.put(WiFi_Data.SECURITY, ap.capabilities);
                    rowData.put(WiFi_Data.FREQUENCY, ap.frequency);
                    rowData.put(WiFi_Data.RSSI, ap.level);
                    
                    try {
                        getContentResolver().insert(WiFi_Data.CONTENT_URI, rowData);
                    } catch (SQLiteException e) {
                        if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                    } catch (SQLException e) {
                        if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                    }
                    
                    if( Aware.DEBUG ) Log.d(TAG, ACTION_AWARE_WIFI_NEW_DEVICE + ">>" + rowData.toString());
                    Intent detectedAP = new Intent(ACTION_AWARE_WIFI_NEW_DEVICE);
                    sendBroadcast(detectedAP);
                }
                
                Intent scanEnd = new Intent(ACTION_AWARE_WIFI_SCAN_ENDED);
                sendBroadcast(scanEnd);
            }   
        }
    }
}
