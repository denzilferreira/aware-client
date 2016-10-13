package com.aware.ui;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.providers.ESM_Provider.ESM_Data;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Question;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Processes an  ESM queue until it's over.
 *
 * @author denzilferreira
 */
public class ESM_Queue extends FragmentActivity {

    private static String TAG = "AWARE::ESM Queue";

    public ESM_State esmStateListener = new ESM_State();

    private ESMFactory esmFactory = new ESMFactory();

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        //Clear notification if it exists, since we are going through the ESMs
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(ESM.ESM_NOTIFICATION_ID);

        TAG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG).length() > 0 ? Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG) : TAG;

        Intent queue_started = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_STARTED);
        sendBroadcast(queue_started);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
        filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);
        filter.addAction(ESM.ACTION_AWARE_ESM_TIMEOUT);
        registerReceiver(esmStateListener, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            FragmentManager fragmentManager = getSupportFragmentManager();

            Cursor current_esm;
            if (ESM.isESMVisible(getApplicationContext())) {
                current_esm = getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_VISIBLE, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
            } else {
                current_esm = getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
            }
            if (current_esm != null && current_esm.moveToFirst()) {

                int _id = current_esm.getInt(current_esm.getColumnIndex(ESM_Data._ID));

                //Fixed: set the esm as VISIBLE, to avoid displaying the same ESM twice due to changes in orientation
                ContentValues update_state = new ContentValues();
                update_state.put(ESM_Data.STATUS, ESM.STATUS_VISIBLE);
                getContentResolver().update(ESM_Data.CONTENT_URI, update_state, ESM_Data._ID + "=" + _id, null);
                //--

                //Load esm question JSON from database
                JSONObject esm_question = new JSONObject(current_esm.getString(current_esm.getColumnIndex(ESM_Data.JSON)));
                ESM_Question esm = esmFactory.getESM(esm_question.getInt(ESM_Question.esm_type), esm_question, current_esm.getInt(current_esm.getColumnIndex(ESM_Data._ID)));
                if (esm != null) {
                    esm.show(fragmentManager, TAG);
                }
            }
            if (current_esm != null && !current_esm.isClosed()) current_esm.close();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class ESM_State extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE)) {
                Aware.setSetting(c, ESM.NOTIFICATION_TIMEOUT, "false", "com.aware.phone");
                //Clean-up trials from database
                getContentResolver().delete(ESM_Data.CONTENT_URI, ESM_Data.TRIGGER + " LIKE 'TRIAL'", null);
            }
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
     *
     * @return int count
     */
    public static int getQueueSize(Context c) {
        int size = 0;
        Cursor onqueue = c.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_VISIBLE + "," + ESM.STATUS_NEW + ")", null, null);
        if (onqueue != null && onqueue.moveToFirst()) {
            size = onqueue.getCount();
        }
        if (onqueue != null && !onqueue.isClosed()) onqueue.close();
        return size;
    }

    public static int getTimeout(Context c) {
        int timeout = 0;

        String[] projection = { ESM_Data.NOTIFICATION_TIMEOUT };

        Cursor onqueue = c.getContentResolver().query(ESM_Data.CONTENT_URI, projection, ESM_Data.STATUS + " IN (" + ESM.STATUS_VISIBLE + "," + ESM.STATUS_NEW + ")", null, null);

        int index = onqueue.getColumnIndex(ESM_Data.NOTIFICATION_TIMEOUT);

        if (onqueue != null && onqueue.moveToFirst()) {
            timeout = Integer.parseInt(onqueue.getString(index));
        }
        if (onqueue != null && !onqueue.isClosed()) onqueue.close();
        return timeout;
    }
}