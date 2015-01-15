
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.aware.providers.Gravity_Provider;
import com.aware.providers.Gyroscope_Provider;
import com.aware.providers.Gyroscope_Provider.Gyroscope_Data;
import com.aware.providers.Gyroscope_Provider.Gyroscope_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that logs gyroscope readings from the device
 * @author df
 *
 */
public class Gyroscope extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::Gyroscope")
     */
    private static String TAG = "AWARE::Gyroscope";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mGyroscope;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new gyroscope values
     * ContentProvider: Gyroscope_Provider
     */
    public static final String ACTION_AWARE_GYROSCOPE = "ACTION_AWARE_GYROSCOPE";
    public static final String EXTRA_SENSOR = "sensor";
    public static final String EXTRA_DATA = "data";

    public static final String ACTION_AWARE_GYROSCOPE_LABEL = "ACTION_AWARE_GYROSCOPE_LABEL";
    public static final String EXTRA_LABEL = "label";

    /**
     * Until today, no available Android phone samples higher than 208Hz (Nexus 7).
     * http://ilessendata.blogspot.com/2012/11/android-accelerometer-sampling-rates.html
     */
    private static ContentValues[] data_buffer;
    private static List<ContentValues> data_values = new ArrayList<ContentValues>();

    private static String LABEL = "";

    private static DataLabel dataLabeler = new DataLabel();
    public static class DataLabel extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_GYROSCOPE_LABEL)) {
                LABEL = intent.getStringExtra(EXTRA_LABEL);
            }
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //we log accuracy on the sensor changed values
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Gyroscope_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
        rowData.put(Gyroscope_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Gyroscope_Data.VALUES_0, event.values[0]);
        rowData.put(Gyroscope_Data.VALUES_1, event.values[1]);
        rowData.put(Gyroscope_Data.VALUES_2, event.values[2]);
        rowData.put(Gyroscope_Data.ACCURACY, event.accuracy);
        rowData.put(Gyroscope_Data.LABEL, LABEL);

        if( data_values.size() < 250 ) {
            data_values.add(rowData);

            Intent gyroData = new Intent(ACTION_AWARE_GYROSCOPE);
            gyroData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(gyroData);

            if( Aware.DEBUG ) Log.d(TAG, "Gyroscope:"+ rowData.toString());

            return;
        }

        data_buffer = new ContentValues[data_values.size()];
        data_values.toArray(data_buffer);

        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		new AsyncStore().execute( data_buffer );
        	}
        }catch( SQLiteException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }catch( SQLException e ) {
            if(Aware.DEBUG) Log.d(TAG,e.getMessage());
        }

        data_values.clear();
    }

    /**
     * Database I/O on different thread
     */
    private class AsyncStore extends AsyncTask<ContentValues[], Void, Void> {
        @Override
        protected Void doInBackground(ContentValues[]... data) {
            getContentResolver().bulkInsert(Gyroscope_Data.CONTENT_URI, data[0]);
            return null;
        }
    }

    /**
     * Calculates the sampling rate in Hz (i.e., how many samples did we collect in the past second)
     * @param context
     * @return hz
     */
    public static int getFrequency(Context context) {
        int hz = 0;
        String[] columns = new String[]{ "count(*) as frequency", "datetime("+ Gyroscope_Data.TIMESTAMP+"/1000, 'unixepoch','localtime') as sample_time" };
        Cursor qry = context.getContentResolver().query(Gyroscope_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if( qry != null && qry.moveToFirst() ) {
            hz = qry.getInt(0);
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
        return hz;
    }

    private void saveGyroscopeDevice(Sensor gyro) {
        Cursor gyroInfo = getContentResolver().query(Gyroscope_Sensor.CONTENT_URI, null, null, null, null);
        if( gyroInfo == null || ! gyroInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Gyroscope_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Gyroscope_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Gyroscope_Sensor.MAXIMUM_RANGE, gyro.getMaximumRange());
            rowData.put(Gyroscope_Sensor.MINIMUM_DELAY, gyro.getMinDelay());
            rowData.put(Gyroscope_Sensor.NAME, gyro.getName());
            rowData.put(Gyroscope_Sensor.POWER_MA, gyro.getPower());
            rowData.put(Gyroscope_Sensor.RESOLUTION, gyro.getResolution());
            rowData.put(Gyroscope_Sensor.TYPE, gyro.getType());
            rowData.put(Gyroscope_Sensor.VENDOR, gyro.getVendor());
            rowData.put(Gyroscope_Sensor.VERSION, gyro.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Gyroscope_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent gyro_dev = new Intent(ACTION_AWARE_GYROSCOPE);
            	gyro_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(gyro_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Gyroscope info: "+ rowData.toString());
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        } else gyroInfo.close();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG):TAG;
        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_GYROSCOPE ).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GYROSCOPE));
        } else {
            Aware.setSetting(this, Aware_Preferences.FREQUENCY_GYROSCOPE, SAMPLING_RATE);
        }

        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mGyroscope, SAMPLING_RATE, sensorHandler);

        DATABASE_TABLES = Gyroscope_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Gyroscope_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Gyroscope_Sensor.CONTENT_URI, Gyroscope_Data.CONTENT_URI };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_GYROSCOPE_LABEL);
        registerReceiver(dataLabeler, filter);

        if(mGyroscope == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a gyroscope!");
            Aware.setSetting(this, Aware_Preferences.STATUS_GYROSCOPE, false);
            stopSelf();
            return;
        } else {
            saveGyroscopeDevice(mGyroscope);
        }

        if(Aware.DEBUG) Log.d(TAG,"Gyroscope service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mGyroscope);
        sensorThread.quit();
        
        wakeLock.release();

        unregisterReceiver(dataLabeler);
        
        if(Aware.DEBUG) Log.d(TAG,"Gyroscope service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
    	TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG):TAG;

        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_GYROSCOPE).length() == 0 ) {
            Aware.setSetting(this, Aware_Preferences.FREQUENCY_GYROSCOPE, SAMPLING_RATE);
        }

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GYROSCOPE))) { //changed setting
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GYROSCOPE));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mGyroscope);
            mSensorManager.registerListener(this, mGyroscope, SAMPLING_RATE, sensorHandler);
        }

        if(Aware.DEBUG) Log.d(TAG,"Gyroscope service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static Gyroscope gyroSrv = Gyroscope.getService();
    
    /**
     * Get singleton instance to Gyroscope service
     * @return Gyroscope obj
     */
    public static Gyroscope getService() {
        if( gyroSrv == null ) gyroSrv = new Gyroscope();
        return gyroSrv;
    }
    
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        Gyroscope getService() {
            return Gyroscope.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}