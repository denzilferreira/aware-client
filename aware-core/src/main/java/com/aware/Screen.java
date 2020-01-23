
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
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

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
     * Used to log the current screen status when starting the sensor
     */
    private static final String ACTION_AWARE_SCREEN_BOOT = "ACTION_AWARE_SCREEN_BOOT";

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

    /**
     * Custom unlock status to complement Display.getState and keep Aware's screen table structure
     * https://developer.android.com/reference/android/view/Display.html#getState()
     * Display.STATE_OFF is 1, Display.STATE_ON is 2, ...
     */
    public static final int NEW_STATUS_SCREEN_UNLOCKED = 7;


    private ScreenMonitor screenMonitor = null;

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

        if (Aware.DEBUG) Log.d(TAG, "Screen service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (screenMonitor != null) unregisterReceiver(screenMonitor);

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
            if (screenMonitor == null) {
                screenMonitor = new ScreenMonitor();

                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                filter.addAction(Screen.ACTION_AWARE_SCREEN_BOOT);
                registerReceiver(screenMonitor, filter);

            }

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

    private class ScreenMonitor extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) || intent.getAction().equals(Intent.ACTION_SCREEN_OFF) || intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                KeyguardManager km = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
                DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);

                for (Display display : dm.getDisplays()) {

                    int displayState = display.getState();
                    if(intent.getAction().equals(Intent.ACTION_USER_PRESENT) && !km.isKeyguardLocked())
                        displayState = NEW_STATUS_SCREEN_UNLOCKED;
                    if (Aware.DEBUG) Log.d(TAG, "Screen status: " + displayState);


                    try {
                        ContentValues rowData = new ContentValues();
                        rowData.put(Screen_Data.TIMESTAMP, System.currentTimeMillis());
                        rowData.put(Screen_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        rowData.put(Screen_Data.SCREEN_STATUS, displayState);
                        context.getContentResolver().insert(Screen_Data.CONTENT_URI, rowData);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }

                    if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) && displayState == Display.STATE_ON){
                        if (awareSensor != null) awareSensor.onScreenOn();
                        sendBroadcast(new Intent(ACTION_AWARE_SCREEN_ON));
                    }
                    else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF) && displayState == Display.STATE_OFF){
                        if (awareSensor != null) awareSensor.onScreenOff();
                        sendBroadcast(new Intent(ACTION_AWARE_SCREEN_OFF));
                    }
                    else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT) && displayState == NEW_STATUS_SCREEN_UNLOCKED){
                        if (awareSensor != null) awareSensor.onScreenUnlocked();
                        sendBroadcast(new Intent(ACTION_AWARE_SCREEN_UNLOCKED));
                    }
                }
            }
        }
    }
}
