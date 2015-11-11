package com.aware.ui;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.WindowManager;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.Screen;
import com.aware.providers.ESM_Provider.ESM_Data;

/**
 * Processes an  ESM queue until it's over.
 * @author denzilferreira
 */
public class ESM_Queue extends FragmentActivity {

    private static String TAG = "AWARE::ESM Queue";

    public ESM_State esmStateListener = new ESM_State();

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        //Clear notification if it exists, since we are going through the ESMs
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(ESM.ESM_NOTIFICATION_ID);

        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        Intent queue_started = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_STARTED);
        sendBroadcast(queue_started);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
        filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);
        registerReceiver(esmStateListener, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        FragmentManager fragmentManager = getSupportFragmentManager();
        DialogFragment esmDialog = new ESM_UI();
        esmDialog.show(fragmentManager, TAG);
    }

    public class ESM_State extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(esmStateListener);
    }

    /**
     * Get amount of ESMs waiting on database (visible or new)
     * @return int count
     */
    public static int getQueueSize(Context c) {
        int size = 0;
        Cursor onqueue = c.getContentResolver().query(ESM_Data.CONTENT_URI,null, ESM_Data.STATUS + " IN (" + ESM.STATUS_VISIBLE +","+ ESM.STATUS_NEW + ")", null, null);
        if( onqueue != null && onqueue.moveToFirst() ) {
            size = onqueue.getCount();
        }
        if( onqueue != null && ! onqueue.isClosed() ) onqueue.close();
        return size;
    }
}