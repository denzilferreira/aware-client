package com.aware.phone.syncadapters;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.aware.Aware;
import com.aware.providers.Accelerometer_Provider;
import com.aware.providers.Aware_Provider;
import com.aware.syncadapters.AwareSyncAdapter;
import com.aware.utils.WebserviceHelper;

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
            if (sSyncAdapter == null)
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
