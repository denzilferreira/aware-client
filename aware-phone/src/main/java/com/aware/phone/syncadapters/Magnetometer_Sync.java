package com.aware.phone.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.aware.providers.Gravity_Provider;
import com.aware.providers.Magnetometer_Provider;
import com.aware.syncadapters.AwareSyncAdapter;

/**
 * Created by denzil on 22/07/2017.
 */

public class Magnetometer_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Magnetometer_Provider.DATABASE_TABLES,
                        Magnetometer_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Magnetometer_Provider.Magnetometer_Sensor.CONTENT_URI,
                                Magnetometer_Provider.Magnetometer_Data.CONTENT_URI
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
