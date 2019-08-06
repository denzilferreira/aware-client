package com.aware.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.aware.providers.Telephony_Provider;

/**
 * Created by denzil on 22/07/2017.
 */

public class Telephony_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Telephony_Provider.DATABASE_TABLES,
                        Telephony_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Telephony_Provider.Telephony_Data.CONTENT_URI, Telephony_Provider.GSM_Data.CONTENT_URI, Telephony_Provider.GSM_Neighbors_Data.CONTENT_URI, Telephony_Provider.CDMA_Data.CONTENT_URI
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
