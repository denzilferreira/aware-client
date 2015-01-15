
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

import com.aware.providers.Accelerometer_Provider;
import com.aware.providers.Linear_Accelerometer_Provider;
import com.aware.providers.Linear_Accelerometer_Provider.Linear_Accelerometer_Data;
import com.aware.providers.Linear_Accelerometer_Provider.Linear_Accelerometer_Sensor;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Converters;

import java.util.ArrayList;
import java.util.List;

/**
 * AWARE Linear-accelerometer module: 
 * A three dimensional vector indicating acceleration along each device axis, not including gravity. All values have units of m/s^2. The coordinate system is the same as is used by the acceleration sensor.
 * The output of the accelerometer, gravity and linear-acceleration sensors must obey the following relation: acceleration = gravity + linear-acceleration
 * - Linear Accelerometer raw data
 * - Linear Accelerometer sensor information
 * @author df
 *
 */
public class LinearAccelerometer extends Aware_Sensor implements SensorEventListener {
    
    /**
     * Logging tag (default = "AWARE::LinearAccelerometer")
     */
    private static String TAG = "AWARE::Linear Accelerometer";
    
    /**
     * Sensor update frequency in microseconds, default 200000
     */
    private static int SAMPLING_RATE = 200000;
    
    private static SensorManager mSensorManager;
    private static Sensor mLinearAccelerator;
    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static PowerManager powerManager = null;
    private static PowerManager.WakeLock wakeLock = null;
    
    /**
     * Broadcasted event: new sensor values
     * ContentProvider: LinearAccelerationProvider
     */
    public static final String ACTION_AWARE_LINEAR_ACCELEROMETER = "ACTION_AWARE_LINEAR_ACCELEROMETER";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR = "sensor";

    public static final String ACTION_AWARE_LINEAR_LABEL = "ACTION_AWARE_LINEAR_LABEL";
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
            if( intent.getAction().equals(ACTION_AWARE_LINEAR_LABEL)) {
                LABEL = intent.getStringExtra(EXTRA_LABEL);
            }
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We log current accuracy on the sensor changed event
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        ContentValues rowData = new ContentValues();
        rowData.put(Linear_Accelerometer_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
        rowData.put(Linear_Accelerometer_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Linear_Accelerometer_Data.VALUES_0, event.values[0]);
        rowData.put(Linear_Accelerometer_Data.VALUES_1, event.values[1]);
        rowData.put(Linear_Accelerometer_Data.VALUES_2, event.values[2]);
        rowData.put(Linear_Accelerometer_Data.ACCURACY, event.accuracy);
        rowData.put(Linear_Accelerometer_Data.LABEL, LABEL);

        if( data_values.size() < 250 ) {
            data_values.add(rowData);

            Intent accelData = new Intent(ACTION_AWARE_LINEAR_ACCELEROMETER);
            accelData.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(accelData);

            if( Aware.DEBUG ) Log.d(TAG, "Linear-accelerometer:"+ rowData.toString());

            return;
        }

        data_buffer = new ContentValues[data_values.size()];
        data_values.toArray(data_buffer);

        try {
        	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
        		new AsyncStore().execute(data_buffer);
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
            getContentResolver().bulkInsert(Linear_Accelerometer_Data.CONTENT_URI, data[0]);
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
        String[] columns = new String[]{ "count(*) as frequency", "datetime("+ Linear_Accelerometer_Data.TIMESTAMP+"/1000, 'unixepoch','localtime') as sample_time" };
        Cursor qry = context.getContentResolver().query(Linear_Accelerometer_Data.CONTENT_URI, columns, "1) group by (sample_time", null, "sample_time DESC LIMIT 1 OFFSET 2");
        if( qry != null && qry.moveToFirst() ) {
            hz = qry.getInt(0);
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
        return hz;
    }

    private void saveAccelerometerDevice(Sensor acc) {
        Cursor accelInfo = getContentResolver().query(Linear_Accelerometer_Sensor.CONTENT_URI, null, null, null, null);
        if( accelInfo == null || ! accelInfo.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Linear_Accelerometer_Sensor.DEVICE_ID, Aware.getSetting(getApplicationContext(),Aware_Preferences.DEVICE_ID));
            rowData.put(Linear_Accelerometer_Sensor.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Linear_Accelerometer_Sensor.MAXIMUM_RANGE, acc.getMaximumRange());
            rowData.put(Linear_Accelerometer_Sensor.MINIMUM_DELAY, acc.getMinDelay());
            rowData.put(Linear_Accelerometer_Sensor.NAME, acc.getName());
            rowData.put(Linear_Accelerometer_Sensor.POWER_MA, acc.getPower());
            rowData.put(Linear_Accelerometer_Sensor.RESOLUTION, acc.getResolution());
            rowData.put(Linear_Accelerometer_Sensor.TYPE, acc.getType());
            rowData.put(Linear_Accelerometer_Sensor.VENDOR, acc.getVendor());
            rowData.put(Linear_Accelerometer_Sensor.VERSION, acc.getVersion());
            
            try {
            	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_DB_SLOW).equals("false") ) {
            		getContentResolver().insert(Linear_Accelerometer_Sensor.CONTENT_URI, rowData);
            	}
            	
            	Intent accel_dev = new Intent(ACTION_AWARE_LINEAR_ACCELEROMETER);
            	accel_dev.putExtra(EXTRA_SENSOR, rowData);
            	sendBroadcast(accel_dev);
            	
                if( Aware.DEBUG ) Log.d(TAG, "Linear-accelerometer sensor: "+ rowData.toString());
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }else accelInfo.close();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        mLinearAccelerator = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;
        if(Aware.getSetting(this, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER).length() > 0 ) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER));
        } else {
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER, SAMPLING_RATE);
        }
        
        sensorThread = new HandlerThread(TAG);
        sensorThread.start();
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
        
        sensorHandler = new Handler(sensorThread.getLooper());
        mSensorManager.registerListener(this, mLinearAccelerator, SAMPLING_RATE, sensorHandler);

        DATABASE_TABLES = Linear_Accelerometer_Provider.DATABASE_TABLES;
    	TABLES_FIELDS = Linear_Accelerometer_Provider.TABLES_FIELDS;
    	CONTEXT_URIS = new Uri[]{ Linear_Accelerometer_Sensor.CONTENT_URI, Linear_Accelerometer_Data.CONTENT_URI };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AWARE_LINEAR_LABEL);
        registerReceiver(dataLabeler, filter);

        if(mLinearAccelerator == null) {
            if(Aware.DEBUG) Log.w(TAG,"This device does not have a linear-accelerometer!");
            Aware.setSetting(this, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, false);
            stopSelf();
            return;
        } else {
            saveAccelerometerDevice(mLinearAccelerator);
        }

        if(Aware.DEBUG) Log.d(TAG,"Linear-accelerometer service created!");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        sensorHandler.removeCallbacksAndMessages(null);
        mSensorManager.unregisterListener(this, mLinearAccelerator);
        sensorThread.quit();
        
        wakeLock.release();

        unregisterReceiver(dataLabeler);
        
        if(Aware.DEBUG) Log.d(TAG,"Linear-accelerometer service terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        if( Aware.getSetting(this, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER).length() == 0 ) {
            Aware.setSetting(this, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER, SAMPLING_RATE);
        }

        if(SAMPLING_RATE != Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER))) {
            SAMPLING_RATE = Integer.parseInt(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER));
            sensorHandler.removeCallbacksAndMessages(null);
            mSensorManager.unregisterListener(this, mLinearAccelerator);
            mSensorManager.registerListener(this, mLinearAccelerator, SAMPLING_RATE, sensorHandler);
        }

        if(Aware.DEBUG) Log.d(TAG,"Linear-accelerometer service active...");
        
        return START_STICKY;
    }

    //Singleton instance of this service
    private static LinearAccelerometer linearaccelerometerSrv = LinearAccelerometer.getService();
    
    /**
     * Get singleton instance to service
     * @return Linear_Accelerometer obj
     */
    public static LinearAccelerometer getService() {
        if( linearaccelerometerSrv == null ) linearaccelerometerSrv = new LinearAccelerometer();
        return linearaccelerometerSrv;
    }
    
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        LinearAccelerometer getService() {
            return LinearAccelerometer.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}