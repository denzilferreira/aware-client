package com.aware.syncadapters;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;

import com.aware.providers.Accelerometer_Provider;
import com.aware.utils.WebserviceHelper;

/**
 * Created by denzil on 18/07/2017.
 */

public class Accelerometer_Adapter extends AbstractThreadedSyncAdapter {

    /**
     * Sensor database tables
     */
    private String[] DATABASE_TABLES = null;

    /**
     * Sensor table fields
     */
    private String[] TABLES_FIELDS = null;

    /**
     * Context Providers URIs
     */
    private Uri[] CONTEXT_URIS = null;

    private Context mContext;

    public Accelerometer_Adapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;

        DATABASE_TABLES = Accelerometer_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Accelerometer_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Accelerometer_Provider.Accelerometer_Sensor.CONTENT_URI, Accelerometer_Provider.Accelerometer_Data.CONTENT_URI};
    }

    public Accelerometer_Adapter(Context context, boolean autoInitialize, boolean allowParallelSyncs, ContentResolver mContentResolver) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;

        DATABASE_TABLES = Accelerometer_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Accelerometer_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Accelerometer_Provider.Accelerometer_Sensor.CONTENT_URI, Accelerometer_Provider.Accelerometer_Data.CONTENT_URI};
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority, ContentProviderClient provider, SyncResult syncResult) {
        if (DATABASE_TABLES != null && TABLES_FIELDS != null && CONTEXT_URIS != null) {
            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                Intent webserviceHelper = new Intent(getContext(), WebserviceHelper.class);
                webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[i]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[i].toString());
                mContext.startService(webserviceHelper);
            }
        }
    }
}
