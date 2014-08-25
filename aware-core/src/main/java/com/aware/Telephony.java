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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.aware.providers.Telephony_Provider;
import com.aware.providers.Telephony_Provider.CDMA_Data;
import com.aware.providers.Telephony_Provider.GSM_Data;
import com.aware.providers.Telephony_Provider.GSM_Neighbors_Data;
import com.aware.providers.Telephony_Provider.Telephony_Data;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Encrypter;

/**
 * Telephony module. Keeps track of changes in the network operator and information:
 * - Current network operator information
 * - Cell Location ID's
 * - Neighbor cell towers
 * - Signal strength
 * @author denzil
 *
 */
public class Telephony extends Aware_Sensor {

	private static String TAG = "AWARE::Telephony";
	
    private static TelephonyManager telephonyManager = null;
    private static TelephonyState telephonyState = new TelephonyState();
    private static Context mContext = null;
    private static SignalStrength lastSignalStrength = null;
    
    /**
     * Broadcasted event: new telephony information is available
     */
    public static final String ACTION_AWARE_TELEPHONY = "ACTION_AWARE_TELEPHONY";
    
    /**
     * Broadcasted event: connected to a new CDMA tower
     */
    public static final String ACTION_AWARE_CDMA_TOWER = "ACTION_AWARE_CDMA_TOWER";
    
    /**
     * Broadcasted event: connected to a new GSM tower
     */
    public static final String ACTION_AWARE_GSM_TOWER = "ACTION_AWARE_GSM_TOWER";
    
    /**
     * Broadcasted event: detected GSM tower neighbor
     */
    public static final String ACTION_AWARE_GSM_TOWER_NEIGHBOR = "ACTION_AWARE_GSM_TOWER_NEIGHBOR";
    
    /**
     * Get Singleton instance to Telephony module
     * @return Telephony obj
     */
    public static Telephony getService() {
        if( telephonyService == null ) telephonyService = new Telephony();
        return telephonyService;
    }
    private static Telephony telephonyService = Telephony.getService();
    
    private final IBinder telephonyBinder = new TelephonyBinder();
    
    /**
     * Binder for Telephony module
     * @author df
     *
     */
    public class TelephonyBinder extends Binder {
        Telephony getService() {
            return Telephony.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return telephonyBinder;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mContext = getApplicationContext();
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):"AWARE::Telephony";
        
        DATABASE_TABLES = Telephony_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Telephony_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[] { Telephony_Data.CONTENT_URI, GSM_Data.CONTENT_URI, GSM_Neighbors_Data.CONTENT_URI, CDMA_Data.CONTENT_URI };
        
        telephonyManager.listen(telephonyState, PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        
        if(Aware.DEBUG) Log.d(TAG,"Telephony service created!");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):"AWARE::Telephony";
        
        if(Aware.DEBUG) Log.d(TAG,"Telephony service active...");
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        telephonyManager.listen(telephonyState, PhoneStateListener.LISTEN_NONE);
        
        if(Aware.DEBUG) Log.d(TAG,"Telephony service terminated...");
    }
    
    /**
     * Tracks cell location and telephony information:
     * - GSM: CID, LAC, PSC (UMTS Primary Scrambling Code)
     * - CDMA: base station ID, Latitude, Longitude, Network ID, System ID
     * - Telephony: IMEI/MEID/ESN, software version, line number, network MMC, network code, network name, network type, phone type, sim code, sim operator, sim serial, subscriber ID 
     * @author df
     *
     */
    public static class TelephonyState extends PhoneStateListener {
        
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            lastSignalStrength = signalStrength;
        }
        
        @Override
        public void onCellLocationChanged(CellLocation location) {
            super.onCellLocationChanged(location);
            
            if ( lastSignalStrength == null ) return;
            
            String device_id = Aware.getSetting(mContext,Aware_Preferences.DEVICE_ID);
            
            if( location instanceof GsmCellLocation ) {
                GsmCellLocation loc = (GsmCellLocation) location;
                
                long timestamp = System.currentTimeMillis();
                
                ContentValues rowData = new ContentValues();
                rowData.put(GSM_Data.TIMESTAMP, timestamp);
                rowData.put(GSM_Data.DEVICE_ID, device_id);
                rowData.put(GSM_Data.CID, loc.getCid());
                rowData.put(GSM_Data.LAC, loc.getLac());
                rowData.put(GSM_Data.PSC, loc.getPsc());
                rowData.put(GSM_Data.SIGNAL_STRENGTH, lastSignalStrength.getGsmSignalStrength());
                rowData.put(GSM_Data.GSM_BER, lastSignalStrength.getGsmBitErrorRate());
                
                try {
                    mContext.getContentResolver().insert(GSM_Data.CONTENT_URI, rowData);
                    
                    Intent newGSM = new Intent(Telephony.ACTION_AWARE_GSM_TOWER);
                    mContext.sendBroadcast(newGSM);
                    
                    if( Aware.DEBUG ) Log.d(TAG, "GSM tower:"+ rowData.toString());
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
                
                List<NeighboringCellInfo> neighbors = telephonyManager.getNeighboringCellInfo();
                if( neighbors.size() > 0 ) {
                    for(NeighboringCellInfo neighbor : neighbors ) {
                        rowData = new ContentValues();
                        rowData.put(GSM_Neighbors_Data.TIMESTAMP, timestamp);
                        rowData.put(GSM_Neighbors_Data.DEVICE_ID, device_id);
                        rowData.put(GSM_Neighbors_Data.CID, neighbor.getCid());
                        rowData.put(GSM_Neighbors_Data.LAC, neighbor.getLac());
                        rowData.put(GSM_Neighbors_Data.PSC, neighbor.getPsc());
                        rowData.put(GSM_Neighbors_Data.SIGNAL_STRENGTH, neighbor.getRssi());
                        
                        try {
                            mContext.getContentResolver().insert(GSM_Neighbors_Data.CONTENT_URI, rowData);
                            
                            Intent newGSMNeighbor = new Intent(Telephony.ACTION_AWARE_GSM_TOWER_NEIGHBOR);
                            mContext.sendBroadcast(newGSMNeighbor);
                            
                            if( Aware.DEBUG ) Log.d(TAG, "GSM tower neighbor:"+ rowData.toString());
                        }catch( SQLiteException e ) {
                            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                        }catch( SQLException e ) {
                            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                        }
                    }
                }
                
            } else {
                CdmaCellLocation loc = (CdmaCellLocation) location;
                
                long timestamp = System.currentTimeMillis();
                
                ContentValues rowData = new ContentValues();
                rowData.put(CDMA_Data.TIMESTAMP, timestamp);
                rowData.put(CDMA_Data.DEVICE_ID, device_id);
                rowData.put(CDMA_Data.BASE_STATION_ID, loc.getBaseStationId());
                rowData.put(CDMA_Data.BASE_STATION_LATITUDE, loc.getBaseStationLatitude());
                rowData.put(CDMA_Data.BASE_STATION_LONGITUDE, loc.getBaseStationLongitude());
                rowData.put(CDMA_Data.NETWORK_ID, loc.getNetworkId());
                rowData.put(CDMA_Data.SYSTEM_ID, loc.getSystemId());
                rowData.put(CDMA_Data.SIGNAL_STRENGTH, lastSignalStrength.getCdmaDbm());
                rowData.put(CDMA_Data.CDMA_ECIO, lastSignalStrength.getCdmaEcio());
                rowData.put(CDMA_Data.EVDO_DBM, lastSignalStrength.getEvdoDbm());
                rowData.put(CDMA_Data.EVDO_ECIO, lastSignalStrength.getEvdoEcio());
                rowData.put(CDMA_Data.EVDO_SNR, lastSignalStrength.getEvdoSnr());
                
                try {
                    mContext.getContentResolver().insert(CDMA_Data.CONTENT_URI, rowData);
                    
                    Intent newCDMA = new Intent(Telephony.ACTION_AWARE_CDMA_TOWER);
                    mContext.sendBroadcast(newCDMA);
                    
                    if( Aware.DEBUG ) Log.d(TAG, "CDMA tower:"+ rowData.toString());
                }catch( SQLiteException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }catch( SQLException e ) {
                    if(Aware.DEBUG) Log.d(TAG,e.getMessage());
                }
            }
            
            long timestamp = System.currentTimeMillis();
            
            ContentValues rowData = new ContentValues();
            rowData.put(Telephony_Data.TIMESTAMP, timestamp);
            rowData.put(Telephony_Data.DEVICE_ID, device_id);
            rowData.put(Telephony_Data.DATA_ENABLED, telephonyManager.getDataState());
            rowData.put(Telephony_Data.IMEI_MEID_ESN, Encrypter.hashSHA1(telephonyManager.getDeviceId()));
            rowData.put(Telephony_Data.SOFTWARE_VERSION, telephonyManager.getDeviceSoftwareVersion());
            rowData.put(Telephony_Data.LINE_NUMBER, Encrypter.hashSHA1(telephonyManager.getLine1Number()));
            rowData.put(Telephony_Data.NETWORK_COUNTRY_ISO_MCC, telephonyManager.getNetworkCountryIso());
            rowData.put(Telephony_Data.NETWORK_OPERATOR_CODE, telephonyManager.getNetworkOperator());
            rowData.put(Telephony_Data.NETWORK_OPERATOR_NAME, telephonyManager.getNetworkOperatorName());
            rowData.put(Telephony_Data.NETWORK_TYPE, telephonyManager.getNetworkType());
            rowData.put(Telephony_Data.PHONE_TYPE, telephonyManager.getPhoneType());
            rowData.put(Telephony_Data.SIM_STATE, telephonyManager.getSimState());
            rowData.put(Telephony_Data.SIM_OPERATOR_CODE, telephonyManager.getSimOperator());
            rowData.put(Telephony_Data.SIM_OPERATOR_NAME, telephonyManager.getSimOperatorName());
            rowData.put(Telephony_Data.SIM_SERIAL, Encrypter.hashSHA1(telephonyManager.getSimSerialNumber()));
            rowData.put(Telephony_Data.SUBSCRIBER_ID, Encrypter.hashSHA1(telephonyManager.getSubscriberId()));
            
            try {
                mContext.getContentResolver().insert(Telephony_Data.CONTENT_URI, rowData);
                
                Intent newTelephony = new Intent(Telephony.ACTION_AWARE_TELEPHONY);
                mContext.sendBroadcast(newTelephony);
                
                if( Aware.DEBUG ) Log.d(TAG, "Telephony:"+ rowData.toString());
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }
    }        
}
