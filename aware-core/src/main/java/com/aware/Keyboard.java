package com.aware;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.Keyboard_Provider;
import com.aware.ui.PermissionsHandler;
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

        TAG = "AWARE::Keyboard";

        DATABASE_TABLES = Keyboard_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Keyboard_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Keyboard_Provider.Keyboard_Data.CONTENT_URI};

        if (Aware.DEBUG) Log.d(TAG, "Keyboard service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_KEYBOARD, true);

            if (Aware.DEBUG) Log.d(TAG, "Keyboard service active...");
        }

        return START_STICKY;
    }
}
