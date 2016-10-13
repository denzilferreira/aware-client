
package com.aware;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.providers.ESM_Provider;
import com.aware.providers.ESM_Provider.ESM_Data;
import com.aware.ui.ESM_Queue;
import com.aware.ui.PermissionsHandler;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Question;
import com.aware.utils.Aware_Sensor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * AWARE ESM module
 * Allows a researcher to do ESM's on their studies
 * Listens to:
 * - ACTION_AWARE_QUEUE_ESM
 *
 * @author df
 */
public class ESM extends Aware_Sensor {

    /**
     * Logging tag (default = "AWARE::ESM")
     */
    private static String TAG = "AWARE::ESM";

    /**
     * Received event: queue the specified ESM
     * Extras: (JSONArray as String) esm
     */
    public static final String ACTION_AWARE_QUEUE_ESM = "ACTION_AWARE_QUEUE_ESM";

    /**
     * Received event: try the specified ESM
     * Extras: (JSONArray as String) esm
     */
    public static final String ACTION_AWARE_TRY_ESM = "ACTION_AWARE_TRY_ESM";

    /**
     * Broadcasted event: the user has answered one answer from ESM queue
     */
    public static final String ACTION_AWARE_ESM_ANSWERED = "ACTION_AWARE_ESM_ANSWERED";

    /**
     * Broadcasted event: the user has dismissed one answer from ESM queue
     */
    public static final String ACTION_AWARE_ESM_DISMISSED = "ACTION_AWARE_ESM_DISMISSED";

    /**
     * Broadcasted event: the user did not answer the ESM on time from ESM queue
     */
    public static final String ACTION_AWARE_ESM_EXPIRED = "ACTION_AWARE_ESM_EXPIRED";

    /**
     * Broadcasted event: the notification has timed out and ESM queue is cleared
     */
    public static final String ACTION_AWARE_ESM_TIMEOUT = "ACTION_AWARE_ESM_TIMEOUT";

    /**
     * Broadcasted event: the user has finished answering the ESM queue
     */
    public static final String ACTION_AWARE_ESM_QUEUE_COMPLETE = "ACTION_AWARE_ESM_QUEUE_COMPLETE";

    /**
     * Broadcasted event: the user has started answering the ESM queue
     */
    public static final String ACTION_AWARE_ESM_QUEUE_STARTED = "ACTION_AWARE_ESM_QUEUE_STARTED";

    /**
     * ESM status: new on the queue, but not displayed yet
     */
    public static final int STATUS_NEW = 0;

    /**
     * ESM status: esm dismissed by the user, either by pressed back or home button
     */
    public static final int STATUS_DISMISSED = 1;

    /**
     * ESM status: esm answered by the user by pressing submit button
     */
    public static final int STATUS_ANSWERED = 2;

    /**
     * ESM status: esm not answered in time by the user
     */
    public static final int STATUS_EXPIRED = 3;

    /**
     * ESM status: esm is visible to the user
     */
    public static final int STATUS_VISIBLE = 4;

    /**
     * ESM status: esm was not visible because of flow condition, branching to another esm
     */
    public static final int STATUS_BRANCHED = 5;

    /**
     * ESM status: esm is timed out by the system
     */
    public static final int STATUS_TIMEOUT = 6;

    /**
     * ESM Dialog with free text
     * Example: [{'esm':{'esm_type':1,'esm_title':'ESM Freetext','esm_instructions':'The user can answer an open ended question.','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_TEXT = 1;

    /**
     * ESM Dialog with radio buttons
     * Note: 'Other' will allow free text input from the user
     * Example: [{'esm':{'esm_type':2,'esm_title':'ESM Radio','esm_instructions':'The user can only choose one option','esm_radios':['Option one','Option two','Other'],'esm_submit':'Next','esm_expiration_threshold':30,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_RADIO = 2;

    /**
     * ESM Dialog with checkboxes
     * Note: 'Other' will allow free text input from the user
     * Example: [{'esm':{'esm_type':3,'esm_title':'ESM Checkbox','esm_instructions':'The user can choose multiple options','esm_checkboxes':['One','Two','Other'],'esm_submit':'Next','esm_expiration_threshold':40,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_CHECKBOX = 3;

    /**
     * ESM Dialog with likert scale
     * Example: [{'esm':{'esm_type':4,'esm_title':'ESM Likert','esm_instructions':'User rating 1 to 5 or 7 at 1 step increments','esm_likert_max':5,'esm_likert_max_label':'Great','esm_likert_min_label':'Bad','esm_likert_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_LIKERT = 4;

    /**
     * ESM Dialog with quick answers
     * Example: [{'esm':{'esm_type':5,'esm_title':'ESM Quick Answer','esm_instructions':'One touch answer','esm_quick_answers':['Yes','No'],'esm_expiration_threashold':60,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_QUICK_ANSWERS = 5;

    /**
     * ESM Dialog with a discrete likert scale
     * Example: [{'esm':{'esm_type':6,'esm_title':'ESM Scale','esm_instructions':'User scaled value between minimum and maximum at X increments','esm_scale_min':0,'esm_scale_max':5,'esm_scale_start':3,'esm_scale_max_label':'5','esm_scale_min_label':'0','esm_scale_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     */
    public static final int TYPE_ESM_SCALE = 6;

    /**
     * ESM Dialog with number input only
     * Example: [{'esm':{'esm_type':7,'esm_title':'ESM Number','esm_instructions':'User can answer with any numeric value','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     */

    public static final int TYPE_ESM_NUMBER = 7;


    /**
     * Required String extra for displaying an ESM. It should contain the JSON string that defines the ESM dialog.
     * Examples:<p>
     * Free text: [{'esm':{'esm_type':1,'esm_title':'ESM Freetext','esm_instructions':'The user can answer an open ended question.','esm_submit':'Next','esm_expiration_threshold':20,'esm_trigger':'esm trigger example'}}]
     * Radio: [{'esm':{'esm_type':2,'esm_title':'ESM Radio','esm_instructions':'The user can only choose one option','esm_radios':['Option one','Option two','Other'],'esm_submit':'Next','esm_expiration_threshold':30,'esm_trigger':'esm trigger example'}}]
     * Checkbox: [{'esm':{'esm_type':3,'esm_title':'ESM Checkbox','esm_instructions':'The user can choose multiple options','esm_checkboxes':['One','Two','Other'],'esm_submit':'Next','esm_expiration_threshold':40,'esm_trigger':'esm trigger example'}}]
     * Likert: [{'esm':{'esm_type':4,'esm_title':'ESM Likert','esm_instructions':'User rating 1 to 5 or 7 at 1 step increments','esm_likert_max':5,'esm_likert_max_label':'Great','esm_likert_min_label':'Bad','esm_likert_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     * Quick answer: [{'esm':{'esm_type':5,'esm_title':'ESM Quick Answer','esm_instructions':'One touch answer','esm_quick_answers':['Yes','No'],'esm_expiration_threshold':60,'esm_trigger':'esm trigger example'}}]
     * Scale: [{'esm':{'esm_type':6,'esm_title':'ESM Scale','esm_instructions':'User scaled value between minimum and maximum at X increments','esm_scale_min':0,'esm_scale_max':5,'esm_scale_start':3,'esm_scale_max_label':'5','esm_scale_min_label':'0','esm_scale_step':1,'esm_submit':'OK','esm_expiration_threshold':50,'esm_trigger':'esm trigger example'}}]
     * </p>
     * Furthermore, you can chain several mixed ESM together as a JSON array: [{esm:{}},{esm:{}},...]
     */
    public static final String EXTRA_ESM = "esm";

    public static final String NOTIFICATION_TIMEOUT = "notification_timeout";

    /**
     * Extra for ACTION_AWARE_ESM_ANSWERED as String
     */
    public static final String EXTRA_ANSWER = "answer";

    public static final int ESM_NOTIFICATION_ID = 777;

    private static ArrayList<Long> esm_queue = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        DATABASE_TABLES = ESM_Provider.DATABASE_TABLES;
        TABLES_FIELDS = ESM_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ESM_Data.CONTENT_URI};

        IntentFilter filter = new IntentFilter();
        filter.addAction(ESM.ACTION_AWARE_TRY_ESM);
        filter.addAction(ESM.ACTION_AWARE_QUEUE_ESM);
        filter.addAction(ESM.ACTION_AWARE_ESM_ANSWERED);
        filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);
        registerReceiver(esmMonitor, filter);

        if (Aware.DEBUG) Log.d(TAG, "ESM service created!");

        //Restore pending ESMs back upon service creation. This may happen on rebooting the phone
        if (isESMWaiting(getApplicationContext()) || isESMVisible(getApplicationContext())) {
            notifyESM(getApplicationContext());
        }

        Aware.setSetting(this, ESM.NOTIFICATION_TIMEOUT, false, "com.aware.phone");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(esmMonitor);

        if (Aware.DEBUG) Log.d(TAG, "ESM service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            Aware.setSetting(this, Aware_Preferences.STATUS_ESM, true);

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM).equals("true")) {
                if (isESMWaiting(getApplicationContext()) && !isESMVisible(getApplicationContext())) {
                    notifyESM(getApplicationContext());
                }
            }

            if (DEBUG) Log.d(TAG, "ESM service active... Queue = " + ESM_Queue.getQueueSize(getApplicationContext()));

        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Check if we have NEW ESMs that can be answered at any time
     *
     * @param c
     * @return
     */
    public static boolean isESMWaiting(Context c) {
        boolean is_waiting = false;
        Cursor esms_waiting = c.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW + " AND " + ESM_Data.EXPIRATION_THRESHOLD + "=0", null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if (esms_waiting != null && esms_waiting.moveToFirst()) {
            is_waiting = (esms_waiting.getCount() > 0);
        }
        if (esms_waiting != null && !esms_waiting.isClosed()) esms_waiting.close();
        return is_waiting;
    }

    /**
     * Check if we there is a VISIBLE ESMs that we are answering right now
     *
     * @param c
     * @return
     */
    public static boolean isESMVisible(Context c) {
        boolean is_visible = false;
        Cursor esms_waiting = c.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_VISIBLE, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if (esms_waiting != null && esms_waiting.moveToFirst()) {
            is_visible = (esms_waiting.getCount() > 0);
        }
        if (esms_waiting != null && !esms_waiting.isClosed()) esms_waiting.close();
        return is_visible;
    }

    /**
     * Show notification with ESM waiting
     *
     * @param c
     */
    public static void notifyESM(final Context c) {
        if(Aware.getSetting(c, ESM.NOTIFICATION_TIMEOUT, "com.aware.phone").equals("false")) {
            NotificationManager mNotificationManager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(c);
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_esm);
            mBuilder.setContentTitle("AWARE");
            mBuilder.setContentText(c.getResources().getText(R.string.aware_esm_questions));
            mBuilder.setNumber(ESM_Queue.getQueueSize(c));
            mBuilder.setOnlyAlertOnce(true); //notify the user only once for the same notification ID
            mBuilder.setOngoing(true);
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);

            Intent intent_ESM = new Intent(c, ESM_Queue.class);
            intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pending_ESM = PendingIntent.getActivity(c, 0, intent_ESM, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(pending_ESM);

            mNotificationManager.notify(ESM_NOTIFICATION_ID, mBuilder.build());

            int notificationTimeout = ESM_Queue.getTimeout(c);

            if(notificationTimeout != 0) {
                Aware.setSetting(c, ESM.NOTIFICATION_TIMEOUT, "true", "com.aware.phone");

                // Notification timeout set, dismissing ESM's after timeout
                Intent removeESMNotification = new Intent(c, RemoveESM.class);

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, notificationTimeout);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(c, 710, removeESMNotification, 0);

                AlarmManager alarmManager = (AlarmManager) c.getSystemService(ALARM_SERVICE);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
            }
        }
    }

    public static class RemoveESM extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            Aware.setSetting(c, ESM.NOTIFICATION_TIMEOUT, "false", "com.aware.phone");

            // Remove from queue
            ESM_Question esm_question = new ESM_Question();
            esm_question.timeoutQueue(c);

            // Remove notification
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager nMgr = (NotificationManager) c.getSystemService(ns);
            nMgr.cancel(777);

            // Send intent
            Intent expired = new Intent(ESM.ACTION_AWARE_ESM_EXPIRED);
            c.sendBroadcast(expired);
        }
    }

    //Singleton instance of this service
    private static ESM esmSrv = ESM.getService();

    /**
     * Get singleton instance to service
     *
     * @return ESM obj
     */
    public static ESM getService() {
        if (esmSrv == null) esmSrv = new ESM();
        return esmSrv;
    }

    private final IBinder serviceBinder = new ServiceBinder();

    public class ServiceBinder extends Binder {
        ESM getService() {
            return ESM.getService();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    /**
     * BroadcastReceiver for ESM module
     * - ACTION_AWARE_QUEUE_ESM
     * - ACTION_AWARE_ESM_ANSWERED
     * - ACTION_AWARE_ESM_DISMISSED
     * - ACTION_AWARE_ESM_EXPIRED
     *
     * @author df
     */
    public static class ESMMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Aware.getSetting(context, Aware_Preferences.STATUS_ESM).equals("false")) return;

            if (intent.getAction().equals(ESM.ACTION_AWARE_TRY_ESM)) {
                Intent backgroundService = new Intent(context, BackgroundService.class);
                backgroundService.setAction(ESM.ACTION_AWARE_TRY_ESM);
                backgroundService.putExtra(EXTRA_ESM, intent.getStringExtra(ESM.EXTRA_ESM));
                context.startService(backgroundService);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_QUEUE_ESM)) {
                Intent backgroundService = new Intent(context, BackgroundService.class);
                backgroundService.setAction(ESM.ACTION_AWARE_QUEUE_ESM);
                backgroundService.putExtra(EXTRA_ESM, intent.getStringExtra(ESM.EXTRA_ESM));
                context.startService(backgroundService);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_ANSWERED)) {
                if (ESM_Queue.getQueueSize(context) > 0) {

                    processFlow(context, intent.getStringExtra(EXTRA_ANSWER));

                    Intent intent_ESM = new Intent(context, ESM_Queue.class);
                    intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(intent_ESM);
                } else {
                    if (Aware.DEBUG) Log.d(TAG, "ESM Queue is done!");
                    Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                    context.sendBroadcast(esm_done);
                }
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_DISMISSED)) {

                Cursor esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE + ")", null, null);
                if (esm != null && esm.moveToFirst()) {
                    do {
                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
                        context.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, null, null);
                    } while (esm.moveToNext());
                }
                if (esm != null && !esm.isClosed()) esm.close();

                if (Aware.DEBUG) Log.d(TAG, "Rest of ESM Queue is dismissed!");

                Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                context.sendBroadcast(esm_done);
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_ESM_EXPIRED)) {
                if (ESM_Queue.getQueueSize(context) > 0) {

                    processFlow(context, intent.getStringExtra(EXTRA_ANSWER));

                    Intent intent_ESM = new Intent(context, ESM_Queue.class);
                    intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(intent_ESM);
                } else {
                    if (Aware.DEBUG) Log.d(TAG, "ESM Queue is done!");
                    Intent esm_done = new Intent(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);
                    context.sendBroadcast(esm_done);
                }
            }
        }
    }

    private static void processFlow(Context context, String current_answer) {

        Log.d(ESM.TAG, "Current answer: " + current_answer);

        ESMFactory esmFactory = new ESMFactory();
        try {
            //Check flow
            Cursor last_esm = context.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_ANSWERED + "," + ESM.STATUS_DISMISSED + ")", null, ESM_Data.TIMESTAMP + " DESC LIMIT 1");
            if (last_esm != null && last_esm.moveToFirst()) {

                JSONObject esm_question = new JSONObject(last_esm.getString(last_esm.getColumnIndex(ESM_Data.JSON)));
                ESM_Question esm = esmFactory.getESM(esm_question.getInt(ESM_Question.esm_type), esm_question, last_esm.getInt(last_esm.getColumnIndex(ESM_Data._ID)));

                //Set as branched the flow rules that are not triggered
                JSONArray flows = esm.getFlows();
                for (int i = 0; i < flows.length(); i++) {
                    JSONObject flow = flows.getJSONObject(i);

                    String flowAnswer = flow.getString(ESM_Question.flow_user_answer);

                    boolean is_checks_flow = false;
                    if (esm.getType() == ESM.TYPE_ESM_CHECKBOX) {
                        //This is multiple choice. Check if we are triggering multiple flows
                        String[] multiple = current_answer.split(",");
                        for (String m : multiple) {
                            if (m.trim().equals(flowAnswer)) is_checks_flow = true;
                        }
                        if (is_checks_flow) continue;
                    }

                    if (flowAnswer.equals(current_answer)) continue;

                    if (Aware.DEBUG) {
                        Log.d(ESM.TAG, "Branched split: " + flowAnswer);
                    }
                    ContentValues esmBranched = new ContentValues();
                    esmBranched.put(ESM_Data.STATUS, ESM.STATUS_BRANCHED);
                    context.getContentResolver().update(ESM_Data.CONTENT_URI, esmBranched, ESM_Data._ID + "=" + esm_queue.get(esm.getFlow(flowAnswer) - 1), null);
                }

            }
            if (last_esm != null && !last_esm.isClosed()) last_esm.close();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static final ESMMonitor esmMonitor = new ESMMonitor();

    /**
     * ESM background service
     * - Queue ESM received to the local database
     *
     * @author df
     */
    public static class BackgroundService extends IntentService {
        public BackgroundService() {
            super(TAG + " background service");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            if (intent.getAction().equals(ESM.ACTION_AWARE_TRY_ESM) && intent.getStringExtra(EXTRA_ESM) != null && intent.getStringExtra(EXTRA_ESM).length() > 0) {

                esm_queue.clear();

                try {
                    JSONArray esms = new JSONArray(intent.getStringExtra(EXTRA_ESM));

                    long esm_timestamp = System.currentTimeMillis();
                    boolean is_persistent = false;

                    for (int i = 0; i < esms.length(); i++) {
                        JSONObject esm = esms.getJSONObject(i).getJSONObject(EXTRA_ESM);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.TIMESTAMP, esm_timestamp + i); //fix issue with synching and support ordering
                        rowData.put(ESM_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        rowData.put(ESM_Data.JSON, esm.toString());
                        rowData.put(ESM_Data.EXPIRATION_THRESHOLD, esm.optInt(ESM_Data.EXPIRATION_THRESHOLD)); //optional, defaults to 0
                        rowData.put(ESM_Data.NOTIFICATION_TIMEOUT, esm.optInt(ESM_Data.NOTIFICATION_TIMEOUT)); //optional, defaults to 0
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_NEW);
                        rowData.put(ESM_Data.TRIGGER, "TRIAL"); //we use this TRIAL trigger to remove trials from database at the end of the trial

                        if (rowData.getAsInteger(ESM_Data.EXPIRATION_THRESHOLD) == 0) {
                            is_persistent = true;
                        }

                        try {

                            Uri lastUri = getContentResolver().insert(ESM_Data.CONTENT_URI, rowData);
                            esm_queue.add(Long.valueOf(lastUri.getLastPathSegment()));

                            if (Aware.DEBUG) Log.d(TAG, "ESM: " + rowData.toString());
                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }
                    }

                    if (is_persistent) {
                        notifyESM(getApplicationContext());
                    } else {
                        Intent intent_ESM = new Intent(getApplicationContext(), ESM_Queue.class);
                        intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent_ESM);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (intent.getAction().equals(ESM.ACTION_AWARE_QUEUE_ESM) && intent.getStringExtra(EXTRA_ESM) != null && intent.getStringExtra(EXTRA_ESM).length() > 0) {

                esm_queue.clear();

                try {
                    JSONArray esms = new JSONArray(intent.getStringExtra(EXTRA_ESM));

                    long esm_timestamp = System.currentTimeMillis();
                    boolean is_persistent = false;

                    for (int i = 0; i < esms.length(); i++) {
                        JSONObject esm = esms.getJSONObject(i).getJSONObject(EXTRA_ESM);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Data.TIMESTAMP, esm_timestamp + i); //fix issue with synching and support ordering
                        rowData.put(ESM_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        rowData.put(ESM_Data.JSON, esm.toString());
                        rowData.put(ESM_Data.EXPIRATION_THRESHOLD, esm.optInt(ESM_Data.EXPIRATION_THRESHOLD)); //optional, defaults to 0
                        rowData.put(ESM_Data.NOTIFICATION_TIMEOUT, esm.optInt(ESM_Data.NOTIFICATION_TIMEOUT)); //optional, defaults to 0
                        rowData.put(ESM_Data.STATUS, ESM.STATUS_NEW);
                        rowData.put(ESM_Data.TRIGGER, esm.optString(ESM_Data.TRIGGER)); //optional, defaults to ""

                        if (rowData.getAsInteger(ESM_Data.EXPIRATION_THRESHOLD) == 0) {
                            is_persistent = true;
                        }

                        try {

                            Uri lastUri = getContentResolver().insert(ESM_Data.CONTENT_URI, rowData);
                            esm_queue.add(Long.valueOf(lastUri.getLastPathSegment()));

                            if (Aware.DEBUG) Log.d(TAG, "ESM: " + rowData.toString());
                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }
                    }

                    if (is_persistent) {
                        notifyESM(getApplicationContext());
                    } else {
                        Intent intent_ESM = new Intent(getApplicationContext(), ESM_Queue.class);
                        intent_ESM.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent_ESM);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}