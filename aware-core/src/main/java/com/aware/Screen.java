
package com.aware;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncRequest;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Screen_Provider;
import com.aware.providers.Screen_Provider.Screen_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Service that logs users' interactions with the screen
 * - on/off events
 * - locked/unlocked events
 *
 * @author denzil
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

    public static final String ACTION_AWARE_TOUCH_CLICKED = "ACTION_AWARE_TOUCH_CLICKED";
    public static final String ACTION_AWARE_TOUCH_LONG_CLICKED = "ACTION_AWARE_TOUCH_LONG_CLICKED";
    public static final String ACTION_AWARE_TOUCH_SCROLLED_UP = "ACTION_AWARE_TOUCH_SCROLLED_UP";
    public static final String ACTION_AWARE_TOUCH_SCROLLED_DOWN = "ACTION_AWARE_TOUCH_SCROLLED_DOWN";

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static Screen.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Screen.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Screen.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onScreenOn();

        void onScreenOff();

        void onScreenLocked();

        void onScreenUnlocked();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Screen_Provider.getAuthority(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenMonitor, filter);

        if (Aware.DEBUG) Log.d(TAG, "Screen service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(screenMonitor);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Screen_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Screen_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Screen service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_SCREEN, true);
            if (Aware.DEBUG) Log.d(TAG, "Screen service active...");

            //We can only get the touch events if accessibility service is enabled.
            if (Aware.getSetting(this, Aware_Preferences.STATUS_TOUCH).equals("true")) {
                Applications.isAccessibilityServiceActive(this);
            }

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Screen_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Screen_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Screen_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    public class ScreenMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {
                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_ON);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);

                    if (awareSensor != null) awareSensor.onScreenOn();

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_ON);
                Intent screenOn = new Intent(ACTION_AWARE_SCREEN_ON);
                context.sendBroadcast(screenOn);
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {

                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_OFF);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);

                    if (awareSensor != null) awareSensor.onScreenOff();

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_OFF);
                Intent screenOff = new Intent(ACTION_AWARE_SCREEN_OFF);
                context.sendBroadcast(screenOff);

                //If the screen is off, we need to check if the phone is really locked, as some users don't use it at all.
                KeyguardManager km = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
                if (km.inKeyguardRestrictedInputMode()) {
                    rowData = new ContentValues();
                    rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_LOCKED);
                    try {
                        context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);

                        if (awareSensor != null) awareSensor.onScreenLocked();

                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_LOCKED);
                    Intent screenLocked = new Intent(ACTION_AWARE_SCREEN_LOCKED);
                    context.sendBroadcast(screenLocked);
                }
            }
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                ContentValues rowData = new ContentValues();
                rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                rowData.put(Screen_Data.SCREEN_STATUS, Screen.STATUS_SCREEN_UNLOCKED);
                try {
                    context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);

                    if (awareSensor != null) awareSensor.onScreenUnlocked();

                } catch (SQLiteException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                } catch (SQLException e) {
                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                }

                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_SCREEN_UNLOCKED);
                Intent screenUnlocked = new Intent(ACTION_AWARE_SCREEN_UNLOCKED);
                context.sendBroadcast(screenUnlocked);
            }
        }
    }

    private final ScreenMonitor screenMonitor = new ScreenMonitor();
}
