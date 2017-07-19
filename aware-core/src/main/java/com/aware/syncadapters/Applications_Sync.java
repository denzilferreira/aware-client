package com.aware.syncadapters;

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

import com.aware.providers.Applications_Provider;
import com.aware.utils.WebserviceHelper;

/**
 * Created by denzil on 18/07/2017.
 */
public class Applications_Sync extends Service {

    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Applications_Provider.DATABASE_TABLES,
                        Applications_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Applications_Provider.Applications_Foreground.CONTENT_URI,
                                Applications_Provider.Applications_History.CONTENT_URI,
                                Applications_Provider.Applications_Notifications.CONTENT_URI,
                                Applications_Provider.Applications_Crashes.CONTENT_URI
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
