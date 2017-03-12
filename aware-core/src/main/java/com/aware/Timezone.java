
package com.aware;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.TimeZone_Provider;
import com.aware.providers.TimeZone_Provider.TimeZone_Data;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Sensor;

/**
 * Timezone module. Keeps track of changes in the device Timezone.
 *
 * @author Nikola
 */
public class Timezone extends Aware_Sensor {

    /**
     * Broadcasted event: when there is new timezone information
     */
    public static final String ACTION_AWARE_TIMEZONE = "ACTION_AWARE_TIMEZONE";
    public static final String EXTRA_DATA = "data";

    private static int FREQUENCY = -1;

    private static Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            String timeZone = java.util.TimeZone.getDefault().getID();
            ContentValues rowData = new ContentValues();
            rowData.put(TimeZone_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(TimeZone_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(TimeZone_Data.TIMEZONE, timeZone);

            try {
                getContentResolver().insert(TimeZone_Data.CONTENT_URI, rowData);
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            } catch (IllegalStateException e) {
                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
            }

            Intent newTimeZone = new Intent(ACTION_AWARE_TIMEZONE);
            newTimeZone.putExtra(EXTRA_DATA, rowData);
            sendBroadcast(newTimeZone);

            mHandler.postDelayed(mRunnable, Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE)) * 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DATABASE_TABLES = TimeZone_Provider.DATABASE_TABLES;
        TABLES_FIELDS = TimeZone_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{TimeZone_Data.CONTENT_URI};

        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent newTimeZone = new Intent(ACTION_AWARE_TIMEZONE);
                sendBroadcast(newTimeZone);
            }
        };

        if (Aware.DEBUG) Log.d(TAG, "Timezone service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_TIMEZONE, true);

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE).length() == 0) {
                Aware.setSetting(this, Aware_Preferences.FREQUENCY_TIMEZONE, 3600);
            }

            if (FREQUENCY != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE))) {
                mHandler.removeCallbacks(mRunnable);
                mHandler.post(mRunnable);
                FREQUENCY = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE));
            }
            if (Aware.DEBUG) Log.d(TAG, "Timezone service active...");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacks(mRunnable);

        if (Aware.DEBUG) Log.d(TAG, "Timezone service terminated...");
    }
}
