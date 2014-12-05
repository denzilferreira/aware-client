package com.aware;

import android.net.Uri;

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

        TAG = "AWARE::Keyboard";

        DATABASE_TABLES = Keyboard_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Keyboard_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Keyboard_Provider.Keyboard_Data.CONTENT_URI };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
