package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.aware.providers.Gyroscope_Provider;

/**
 * Created by denzil on 22/07/2017.
 */

public class Gyroscope_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Gyroscope_Provider.DATABASE_TABLES,
                        Gyroscope_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Gyroscope_Provider.Gyroscope_Sensor.CONTENT_URI, Gyroscope_Provider.Gyroscope_Data.CONTENT_URI
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
