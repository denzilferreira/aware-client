package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.aware.Accelerometer;

/**
 * Created by denzil on 18/07/2017.
 */
public class Accelerometer_Sync extends Service {

    private static Accelerometer_Adapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null)
                sSyncAdapter = new Accelerometer_Adapter(getApplicationContext(), true);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
