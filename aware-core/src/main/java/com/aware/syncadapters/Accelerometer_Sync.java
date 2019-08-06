package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.aware.providers.Accelerometer_Provider;

/**
 * Created by denzil on 18/07/2017.
 */
public class Accelerometer_Sync extends Service {

    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Accelerometer_Provider.DATABASE_TABLES, Accelerometer_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Accelerometer_Provider.Accelerometer_Sensor.CONTENT_URI,
                                Accelerometer_Provider.Accelerometer_Data.CONTENT_URI
                        }
                );
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
