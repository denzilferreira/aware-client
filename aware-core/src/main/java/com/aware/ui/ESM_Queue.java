package com.aware.ui;

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
import com.aware.providers.ESM_Provider.ESM_Data;

/**
 * Processes an  ESM queue until it's over.
 * @author denzilferreira
 */
public class ESM_Queue extends FragmentActivity {

    private static String TAG = "AWARE::ESM Queue";
    private final ESM_QueueManager queue_manager = new ESM_QueueManager();
    private static ESM_Queue queue;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        queue = this;

        TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG):TAG;

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ESM.ACTION_AWARE_ESM_ANSWERED);
        filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);
        registerReceiver(queue_manager, filter);

        Intent queue_started = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_STARTED);
        sendBroadcast(queue_started);

        if( getQueueSize(getApplicationContext()) > 0 ) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            DialogFragment esmDialog = new ESM_UI();
            esmDialog.show(fragmentManager, TAG);
            fragmentManager.executePendingTransactions();
            if( ! powerManager.isScreenOn() ) {
                vibrator.vibrate(777);
            }
        }

        //Make sure the ESM service is running
        Intent esm = new Intent(getApplicationContext(), ESM.class);
        esm.setAction(ESM.ACTION_AWARE_ESM_KEEPALIVE);
        startService(esm);
    }

    public class ESM_QueueManager extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ESM.ACTION_AWARE_ESM_ANSWERED) ) {
                if( getQueueSize(context) > 0 || visibleUnanswered(context) ) {
                    FragmentManager fragmentManager = getSupportFragmentManager();
                    DialogFragment esm = new ESM_UI();
                    esm.show(fragmentManager, TAG);
                }
                if( getQueueSize(context) == 0 ) {
                    if(Aware.DEBUG) Log.d(TAG,"ESM Queue is done!");
                    Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                    context.sendBroadcast(esm_done);
                    queue.finish();
                }
            }
            if ( intent.getAction().equals(ESM.ACTION_AWARE_ESM_DISMISSED) || intent.getAction().equals(ESM.ACTION_AWARE_ESM_EXPIRED ) ) {
                if(Aware.DEBUG) Log.d(TAG,"Rest of ESM Queue is dismissed!");
                Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                context.sendBroadcast(esm_done);
                queue.finish();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        //Fixed: if ESM window is dismissed by HOME/Recent Apps buttons, dismiss the whole ESM Queue
        Cursor esm = getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE + ")", null, null);
        if( esm != null && esm.moveToFirst() ) {
            do {
                ContentValues rowData = new ContentValues();
                rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
                getContentResolver().update(ESM_Data.CONTENT_URI, rowData, null, null);
            } while(esm.moveToNext());
        }
        if( esm != null && ! esm.isClosed()) esm.close();

        if( Aware.DEBUG ) Log.d(TAG, "ESM Queue is done!");
    }

    public boolean visibleUnanswered(Context context) {
        boolean visible = false;
        // Check if there is an ESM set as Visible but not answered for some reason. If so, request the FragmentManager to display the ESM again
        Cursor esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " in (" + (ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE)+")", null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if( esm != null && esm.moveToFirst() ) {
            visible = true;
        }
        if( esm!= null && ! esm.isClosed()) esm.close();
        return visible;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(queue_manager);
    }

    /**
     * Get amount of ESMs waiting on database
     * @return int count
     */
    public static int getQueueSize(Context c) {
        int size = 0;
        Cursor onqueue = c.getContentResolver().query(ESM_Data.CONTENT_URI,null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW, null, null);
        if( onqueue != null && onqueue.moveToFirst() ) {
            size = onqueue.getCount();
        }
        if( onqueue != null && ! onqueue.isClosed() ) onqueue.close();
        return size;
    }
}