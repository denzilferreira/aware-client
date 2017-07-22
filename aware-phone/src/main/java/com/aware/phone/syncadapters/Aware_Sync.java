package com.aware.phone.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.aware.providers.Aware_Provider;
import com.aware.syncadapters.AwareSyncAdapter;

/**
 * Created by denzil on 22/07/2017.
 */

public class Aware_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        String[] DATABASE_TABLES = new String[]{Aware_Provider.DATABASE_TABLES[0], Aware_Provider.DATABASE_TABLES[3], Aware_Provider.DATABASE_TABLES[4]};
        String[] TABLES_FIELDS = new String[]{Aware_Provider.TABLES_FIELDS[0], Aware_Provider.TABLES_FIELDS[3], Aware_Provider.TABLES_FIELDS[4]};
        Uri[] CONTEXT_URIS = new Uri[]{Aware_Provider.Aware_Device.CONTENT_URI, Aware_Provider.Aware_Studies.CONTENT_URI, Aware_Provider.Aware_Log.CONTENT_URI};

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(DATABASE_TABLES, TABLES_FIELDS, CONTEXT_URIS);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
