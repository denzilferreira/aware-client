package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.aware.providers.Bluetooth_Provider;

/**
 * Created by denzil on 22/07/2017.
 */

public class Bluetooth_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Bluetooth_Provider.DATABASE_TABLES,
                        Bluetooth_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Bluetooth_Provider.Bluetooth_Sensor.CONTENT_URI, Bluetooth_Provider.Bluetooth_Data.CONTENT_URI
                        });
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
