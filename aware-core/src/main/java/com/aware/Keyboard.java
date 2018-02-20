package com.aware;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncRequest;
import android.os.Bundle;
import android.util.Log;

import com.aware.providers.Keyboard_Provider;
import com.aware.utils.Aware_Sensor;

/**
 * Created by denzil on 23/10/14.
 */
public class Keyboard extends Aware_Sensor {

    /**
     * Broadcasted event: keyboard input detected
     */
    public static final String ACTION_AWARE_KEYBOARD = "ACTION_AWARE_KEYBOARD";

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Keyboard_Provider.getAuthority(this);

        TAG = "AWARE::Keyboard";

        if (Aware.DEBUG) Log.d(TAG, "Keyboard service created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Keyboard_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Keyboard_Provider.getAuthority(this),
                Bundle.EMPTY
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_KEYBOARD, true);

            if (Aware.DEBUG) Log.d(TAG, "Keyboard service active...");

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Keyboard_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Keyboard_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Keyboard_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }
}
