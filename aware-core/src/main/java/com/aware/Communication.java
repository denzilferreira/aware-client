
package com.aware;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog.Calls;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.aware.providers.Communication_Provider;
import com.aware.providers.Communication_Provider.Calls_Data;
import com.aware.providers.Communication_Provider.Messages_Data;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.Encrypter;

/**
 * Capture users' communications (calls and messages) events
 *
 * @author denzil
 */
public class Communication extends Aware_Sensor {

    /**
     * Logging tag (default = "AWARE::Communication")
     */
    public static String TAG = "AWARE::Communication";

    /**
     * Broadcasted event: call accepted by the user
     */
    public static final String ACTION_AWARE_CALL_ACCEPTED = "ACTION_AWARE_CALL_ACCEPTED";

    /**
     * Broadcasted event: phone is ringing
     */
    public static final String ACTION_AWARE_CALL_RINGING = "ACTION_AWARE_CALL_RINGING";

    /**
     * Broadcasted event: call unanswered
     */
    public static final String ACTION_AWARE_CALL_MISSED = "ACTION_AWARE_CALL_MISSED";

    /**
     * Broadcasted event: call attempt by the user
     */
    public static final String ACTION_AWARE_CALL_MADE = "ACTION_AWARE_CALL_MADE";

    /**
     * Broadcasted event: user IS in a call at the moment
     */
    public static final String ACTION_AWARE_USER_IN_CALL = "ACTION_AWARE_USER_IN_CALL";

    /**
     * Broadcasted event: user is NOT in a call
     */
    public static final String ACTION_AWARE_USER_NOT_IN_CALL = "ACTION_AWARE_USER_NOT_IN_CALL";

    /**
     * Broadcasted event: message received
     */
    public static final String ACTION_AWARE_MESSAGE_RECEIVED = "ACTION_AWARE_MESSAGE_RECEIVED";

    /**
     * Broadcasted event: message sent
     */
    public static final String ACTION_AWARE_MESSAGE_SENT = "ACTION_AWARE_MESSAGE_SENT";

    /**
     * Un-official and un-supported SMS provider
     * BEWARE: Might have to change in the future API's as Android evolves...
     */
    private static final Uri MESSAGES_CONTENT_URI = Uri.parse("content://sms");
    private static final int MESSAGE_INBOX = 1;
    private static final int MESSAGE_SENT = 2;

    private static TelephonyManager telephonyManager = null;
    private static CallsObserver callsObs = null;
    private static MessagesObserver msgsObs = null;

    /**
     * ContentObserver for internal call log of Android. When there is a change,
     * it logs up-to-date information of the calls received, made and missed.
     *
     * @author df
     */
    private class CallsObserver extends ContentObserver {
        public CallsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            Cursor lastCall = getContentResolver().query(Calls.CONTENT_URI, null, null, null, Calls.DATE + " DESC LIMIT 1");
            if (lastCall != null && lastCall.moveToFirst()) {

                Cursor exists = getContentResolver().query(Calls_Data.CONTENT_URI, null, Calls_Data.TIMESTAMP + "=" + lastCall.getLong(lastCall.getColumnIndex(Calls.DATE)), null, null);
                if (exists == null || exists.moveToFirst() == false) {

                    switch (lastCall.getInt(lastCall.getColumnIndex(Calls.TYPE))) {
                        case Calls.INCOMING_TYPE:

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS).equals("true")) {
                                ContentValues received = new ContentValues();
                                received.put(Calls_Data.TIMESTAMP, lastCall.getLong(lastCall.getColumnIndex(Calls.DATE)));
                                received.put(Calls_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                received.put(Calls_Data.TYPE, Calls.INCOMING_TYPE);
                                received.put(Calls_Data.DURATION, lastCall.getInt(lastCall.getColumnIndex(Calls.DURATION)));
                                received.put(Calls_Data.TRACE, Encrypter.hashPhone(getApplicationContext(), lastCall.getString(lastCall.getColumnIndex(Calls.NUMBER))));

                                try {
                                    getContentResolver().insert(Calls_Data.CONTENT_URI, received);

                                    if (awareSensor != null) awareSensor.onCall(received);

                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
                                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_CALL_ACCEPTED);
                                Intent callAccepted = new Intent(ACTION_AWARE_CALL_ACCEPTED);
                                sendBroadcast(callAccepted);
                            }

                            break;
                        case Calls.MISSED_TYPE:
                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS).equals("true")) {
                                ContentValues missed = new ContentValues();
                                missed.put(Calls_Data.TIMESTAMP, lastCall.getLong(lastCall.getColumnIndex(Calls.DATE)));
                                missed.put(Calls_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                missed.put(Calls_Data.TYPE, Calls.MISSED_TYPE);
                                missed.put(Calls_Data.DURATION, lastCall.getInt(lastCall.getColumnIndex(Calls.DURATION)));
                                missed.put(Calls_Data.TRACE, Encrypter.hashPhone(getApplicationContext(), lastCall.getString(lastCall.getColumnIndex(Calls.NUMBER))));
                                try {
                                    getContentResolver().insert(Calls_Data.CONTENT_URI, missed);

                                    if (awareSensor != null) awareSensor.onCall(missed);

                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
                                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_CALL_MISSED);
                                Intent callMissed = new Intent(ACTION_AWARE_CALL_MISSED);
                                sendBroadcast(callMissed);
                            }
                            break;
                        case Calls.OUTGOING_TYPE:
                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS).equals("true")) {
                                ContentValues outgoing = new ContentValues();
                                outgoing.put(Calls_Data.TIMESTAMP, lastCall.getLong(lastCall.getColumnIndex(Calls.DATE)));
                                outgoing.put(Calls_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                outgoing.put(Calls_Data.TYPE, Calls.OUTGOING_TYPE);
                                outgoing.put(Calls_Data.DURATION, lastCall.getInt(lastCall.getColumnIndex(Calls.DURATION)));
                                outgoing.put(Calls_Data.TRACE, Encrypter.hashPhone(getApplicationContext(), lastCall.getString(lastCall.getColumnIndex(Calls.NUMBER))));
                                try {
                                    getContentResolver().insert(Calls_Data.CONTENT_URI, outgoing);

                                    if (awareSensor != null) awareSensor.onCall(outgoing);

                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
                                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_CALL_MADE);
                                Intent callOutgoing = new Intent(ACTION_AWARE_CALL_MADE);
                                sendBroadcast(callOutgoing);
                            }
                            break;
                    }
                }
                if (exists != null && !exists.isClosed()) exists.close();
            }
            if (lastCall != null && !lastCall.isClosed()) lastCall.close();
        }
    }

    private class MessagesObserver extends ContentObserver {
        public MessagesObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            Cursor lastMessage = getContentResolver().query(MESSAGES_CONTENT_URI, null, null, null, "date DESC LIMIT 1");
            if (lastMessage != null && lastMessage.moveToFirst()) {

                Cursor exists = getContentResolver().query(Messages_Data.CONTENT_URI, null, Messages_Data.TIMESTAMP + "=" + lastMessage.getLong(lastMessage.getColumnIndex("date")), null, null);
                if (exists == null || !exists.moveToFirst()) {

                    switch (lastMessage.getInt(lastMessage.getColumnIndex("type"))) {
                        case MESSAGE_INBOX:
                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_MESSAGES).equals("true")) {
                                ContentValues inbox = new ContentValues();
                                inbox.put(Messages_Data.TIMESTAMP, lastMessage.getLong(lastMessage.getColumnIndex("date")));
                                inbox.put(Messages_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                inbox.put(Messages_Data.TYPE, MESSAGE_INBOX);
                                inbox.put(Messages_Data.TRACE, Encrypter.hashPhone(getApplicationContext(), lastMessage.getString(lastMessage.getColumnIndex("address"))));

                                try {
                                    getContentResolver().insert(Messages_Data.CONTENT_URI, inbox);

                                    if (awareSensor != null) awareSensor.onMessage(inbox);

                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
                                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_MESSAGE_RECEIVED);
                                Intent messageReceived = new Intent(ACTION_AWARE_MESSAGE_RECEIVED);
                                sendBroadcast(messageReceived);
                            }
                            break;
                        case MESSAGE_SENT:
                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_MESSAGES).equals("true")) {
                                ContentValues sent = new ContentValues();
                                sent.put(Messages_Data.TIMESTAMP, lastMessage.getLong(lastMessage.getColumnIndex("date")));
                                sent.put(Messages_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                sent.put(Messages_Data.TYPE, MESSAGE_SENT);
                                sent.put(Messages_Data.TRACE, Encrypter.hashPhone(getApplicationContext(), lastMessage.getString(lastMessage.getColumnIndex("address"))));

                                try {
                                    getContentResolver().insert(Messages_Data.CONTENT_URI, sent);

                                    if (awareSensor != null) awareSensor.onMessage(sent);

                                } catch (SQLiteException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                } catch (SQLException e) {
                                    if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                                }
                            }

                            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
                                if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_MESSAGE_SENT);
                                Intent messageSent = new Intent(ACTION_AWARE_MESSAGE_SENT);
                                sendBroadcast(messageSent);
                            }
                            break;
                    }
                }
                if (exists != null && !exists.isClosed()) exists.close();
            }
            if (lastMessage != null && !lastMessage.isClosed()) lastMessage.close();
        }
    }

    private PhoneState phoneState = new PhoneState();

    private class PhoneState extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_CALL_RINGING);
                    Intent callRinging = new Intent(ACTION_AWARE_CALL_RINGING);
                    sendBroadcast(callRinging);

                    if (awareSensor != null) awareSensor.onRinging(incomingNumber);

                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_USER_IN_CALL);
                    Intent inCall = new Intent(ACTION_AWARE_USER_IN_CALL);
                    sendBroadcast(inCall);

                    if (awareSensor != null) awareSensor.onBusy(incomingNumber);

                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_USER_NOT_IN_CALL);
                    Intent userFree = new Intent(ACTION_AWARE_USER_NOT_IN_CALL);
                    sendBroadcast(userFree);

                    if (awareSensor != null) awareSensor.onFree(incomingNumber);

                    break;
            }
        }
    }

    private static Communication.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Communication.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Communication.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        /**
         * Callback when a call event is recorded (received, made, missed)
         *
         * @param data
         */
        void onCall(ContentValues data);

        /**
         * Callback when a text message event is recorded (received, sent)
         *
         * @param data
         */
        void onMessage(ContentValues data);

        /**
         * Callback when the phone is ringing
         *
         * @param number
         */
        void onRinging(String number);

        /**
         * Callback when the user answered and is busy with a call
         *
         * @param number
         */
        void onBusy(String number);

        /**
         * Callback when the user hangup an ongoing call and is now free
         *
         * @param number
         */
        void onFree(String number);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Communication_Provider.getAuthority(this);

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        callsObs = new CallsObserver(new Handler());
        msgsObs = new MessagesObserver(new Handler());

        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CONTACTS);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_PHONE_STATE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CALL_LOG);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_SMS);

        if (Aware.DEBUG) Log.d(TAG, "Communication service created!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS).equals("true")) {
                getContentResolver().registerContentObserver(Calls.CONTENT_URI, true, callsObs);
                telephonyManager.listen(phoneState, PhoneStateListener.LISTEN_CALL_STATE);
            } else {
                getContentResolver().unregisterContentObserver(callsObs);
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_MESSAGES).equals("true")) {
                getContentResolver().registerContentObserver(MESSAGES_CONTENT_URI, true, msgsObs);
            } else {
                getContentResolver().unregisterContentObserver(msgsObs);
            }

            if (Aware.DEBUG) Log.d(TAG, TAG + " service active...");

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Communication_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Communication_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Communication_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getContentResolver().unregisterContentObserver(callsObs);
        getContentResolver().unregisterContentObserver(msgsObs);
        telephonyManager.listen(phoneState, PhoneStateListener.LISTEN_NONE);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Communication_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Communication_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, TAG + " service terminated...");
    }
}
