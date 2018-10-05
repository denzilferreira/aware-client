
package com.aware;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Aware_Provider;
import com.aware.providers.Mqtt_Provider;
import com.aware.providers.Mqtt_Provider.Mqtt_Messages;
import com.aware.providers.Mqtt_Provider.Mqtt_Subscriptions;
import com.aware.utils.Aware_Sensor;
import com.aware.utils.SSLUtils;
import com.aware.utils.Scheduler;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;

import javax.net.SocketFactory;

/**
 * Service that connects to the MQTT P2P network for AWARE
 *
 * @author denzil
 */
public class Mqtt extends Aware_Sensor implements MqttCallback {
    /**
     * Logging tag (default = "AWARE::MQTT")
     */
    public static String TAG = "AWARE::MQTT";

    /**
     * MQTT persistence messages
     */
    private static MemoryPersistence MQTT_MESSAGES_PERSISTENCE = null;

    /**
     * The MQTT server
     */
    private static String MQTT_SERVER = "";

    /**
     * The MQTT server options port
     */
    private static String MQTT_PORT = "8883";

    /**
     * The user that is allowed to connect to the MQTT server
     */
    private static String MQTT_USERNAME = "";

    /**
     * The password of the user that is allowed to connect to the MQTT server
     */
    private static String MQTT_PASSWORD = "";

    /**
     * How frequently the device will ping the server, in seconds (default = 600)
     */
    private static String MQTT_KEEPALIVE = "600";

    /**
     * MQTT message QoS (default = 2)
     * 0 - no guarantees
     * 1 - At least once
     * 2 - Exacly once
     */
    private static String MQTT_QoS = "2";

    /**
     * MQTT options protocol (default = ssl)
     * Options:
     * tcp: unencrypted options protocol
     * ssl: encrypted options protocol
     */
    private static String MQTT_PROTOCOL = "ssl";

    /**
     * MQTT message published ID
     */
    public static final int MQTT_MSG_PUBLISHED = 1;

    /**
     * MQTT message received ID
     */
    public static final int MQTT_MSG_RECEIVED = 2;

    /**
     * Broadcast event when a new MQTT message is received from any of the topics subscribed
     */
    public static final String ACTION_AWARE_MQTT_MSG_RECEIVED = "ACTION_AWARE_MQTT_MSG_RECEIVED";

    /**
     * Receive broadcast event: request to publish message to a topic
     * Extras:
     * {@link Mqtt#EXTRA_TOPIC}
     * {@link Mqtt#EXTRA_MESSAGE}
     */
    public static final String ACTION_AWARE_MQTT_MSG_PUBLISH = "ACTION_AWARE_MQTT_MSG_PUBLISH";

    /**
     * Receive broadcast event: subscribe to a topic.
     * Extras:
     * {@link Mqtt#EXTRA_TOPIC}
     */
    public static final String ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE = "ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE";

    /**
     * Receive broadcast event: unsubscribe from a topic.
     * Extras:
     * {@link Mqtt#EXTRA_TOPIC}
     */
    public static final String ACTION_AWARE_MQTT_TOPIC_UNSUBSCRIBE = "ACTION_AWARE_MQTT_TOPIC_UNSUBSCRIBE";

    /**
     * Extra for Mqtt broadcast as "topic"
     */
    public static final String EXTRA_TOPIC = "topic";

    /**
     * Extra for Mqtt broadcast as "message"
     */
    public static final String EXTRA_MESSAGE = "message";

    public static MqttClient MQTT_CLIENT;

    private static MQTTAsync mqttAsync;

    @Override
    public void connectionLost(Throwable throwable) {
        if (awareSensor != null) awareSensor.onDisconnected();
        if (Aware.DEBUG) Log.d(TAG, "MQTT: Connection lost to server...");
        if (Aware.DEBUG) Log.d(TAG, "Disabling MQTT temporarily...");
        Aware.stopMQTT(getApplicationContext());
    }

    private static Mqtt.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Mqtt.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Mqtt.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        /**
         * Connected successfully to the server
         */
        void onConnected();

        /**
         * Disconnected from the server
         */
        void onDisconnected();

        /**
         * New message received from the server
         *
         * @param data
         */
        void onMessage(ContentValues data);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        ContentValues rowData = new ContentValues();
        rowData.put(Mqtt_Messages.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Mqtt_Messages.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Mqtt_Messages.TOPIC, topic);
        rowData.put(Mqtt_Messages.MESSAGE, message.toString());
        rowData.put(Mqtt_Messages.STATUS, MQTT_MSG_RECEIVED);

        try {
            getContentResolver().insert(Mqtt_Messages.CONTENT_URI, rowData);

            if (awareSensor != null) awareSensor.onMessage(rowData);

        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }

        Intent mqttMsg = new Intent(ACTION_AWARE_MQTT_MSG_RECEIVED);
        mqttMsg.putExtra(EXTRA_TOPIC, topic);
        mqttMsg.putExtra(EXTRA_MESSAGE, message.toString());
        sendBroadcast(mqttMsg);

        if (Aware.DEBUG)
            Log.d(TAG, "MQTT: Message received: \n topic = " + topic + "\n message = " + message.toString());

        String study_id = "";
        if (Aware.isStudy(getApplicationContext())) {
            Cursor studyInfo = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
            if (studyInfo != null && studyInfo.moveToFirst()) {
                study_id = String.valueOf(studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
            }
            if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
        }

        if (topic.equalsIgnoreCase(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/broadcasts") || topic.equalsIgnoreCase(study_id + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/broadcasts")) {
            Intent broadcast = new Intent(message.toString());
            sendBroadcast(broadcast);
        }

        if (topic.equalsIgnoreCase(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/esm") || topic.equalsIgnoreCase(study_id + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/esm")) {
            Intent queueESM = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queueESM.putExtra(ESM.EXTRA_ESM, message.toString());
            sendBroadcast(queueESM);
        }

        if (topic.equalsIgnoreCase(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/configuration") || topic.equalsIgnoreCase(study_id + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/configuration")) {
            JSONArray configs = new JSONArray(message.toString());
            Aware.tweakSettings(getApplicationContext(), configs);
        }

        if (topic.equalsIgnoreCase(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/schedulers") || topic.equalsIgnoreCase(study_id + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/schedulers")) {
            JSONArray schedules = new JSONArray(message.toString());
            try {
                Log.d(TAG, "Setting schedules: " + schedules.toString(5));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Scheduler.setSchedules(getApplicationContext(), schedules);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        if (Aware.DEBUG)
            Log.d(TAG, "MQTT: Message delivered. Delivery Token: " + iMqttDeliveryToken.toString());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final MQTTReceiver mqttReceiver = new MQTTReceiver();

    /**
     * MQTT broadcast receiver. Allows other services and applications to publish and subscribe to content on MQTT broker:
     * - ACTION_AWARE_MQTT_MSG_PUBLISH - publish a new message to a specified topic - extras: (String) topic; message
     * - ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE - subscribe to a topic - extras: (String) topic
     * - ACTION_AWARE_MQTT_TOPIC_UNSUBSCRIBE - unsubscribe from a topic - extras: (String) topic
     *
     * @author df
     */
    public static class MQTTReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_AWARE_MQTT_MSG_PUBLISH)) {
                String topic = intent.getStringExtra(EXTRA_TOPIC);
                String message = intent.getStringExtra(EXTRA_MESSAGE);
                if (topic != null && message != null && topic.length() > 0 && message.length() > 0) {
                    if (publish(topic, message)) {
                        ContentValues rowData = new ContentValues();
                        rowData.put(Mqtt_Messages.TIMESTAMP, System.currentTimeMillis());
                        rowData.put(Mqtt_Messages.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                        rowData.put(Mqtt_Messages.TOPIC, topic);
                        rowData.put(Mqtt_Messages.MESSAGE, message);
                        rowData.put(Mqtt_Messages.STATUS, MQTT_MSG_PUBLISHED);

                        try {
                            context.getContentResolver().insert(Mqtt_Messages.CONTENT_URI, rowData);
                            if (Aware.DEBUG)
                                Log.w(TAG, "Published: " + topic + " message: " + message);
                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }
                    }
                }
            }
            if (intent.getAction().equals(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE)) {
                String topic = intent.getStringExtra(EXTRA_TOPIC);
                if (topic != null && topic.length() > 0) {
                    if (subscribe(topic)) {
                        Cursor subscriptions = context.getContentResolver().query(Mqtt_Subscriptions.CONTENT_URI, null, Mqtt_Subscriptions.TOPIC + " LIKE '" + topic + "'", null, null);
                        if (subscriptions == null || !subscriptions.moveToFirst()) {
                            ContentValues rowData = new ContentValues();
                            rowData.put(Mqtt_Subscriptions.TIMESTAMP, System.currentTimeMillis());
                            rowData.put(Mqtt_Subscriptions.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                            rowData.put(Mqtt_Subscriptions.TOPIC, topic);

                            try {
                                context.getContentResolver().insert(Mqtt_Subscriptions.CONTENT_URI, rowData);
                                if (Aware.DEBUG) Log.w(TAG, "Subscribed: " + topic);
                            } catch (SQLiteException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            } catch (SQLException e) {
                                if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                            }
                        }
                        if (subscriptions != null && !subscriptions.isClosed())
                            subscriptions.close();
                    } else {
                        if (Aware.DEBUG) Log.w(TAG, "Failed to subscribe: " + topic);
                    }
                }
            }
            if (intent.getAction().equals(ACTION_AWARE_MQTT_TOPIC_UNSUBSCRIBE)) {
                String topic = intent.getStringExtra(EXTRA_TOPIC);
                if (topic != null && topic.length() > 0) {
                    if (unsubscribe(topic)) {
                        try {
                            context.getContentResolver().delete(Mqtt_Subscriptions.CONTENT_URI, Mqtt_Subscriptions.TOPIC + " LIKE '" + topic + "'", null);
                            if (Aware.DEBUG) Log.w(TAG, "Unsubscribed: " + topic);
                        } catch (SQLiteException e) {
                            if (Aware.DEBUG) Log.w(TAG, e.getMessage());
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.w(TAG, e.getMessage());
                        }
                    } else {
                        if (Aware.DEBUG) Log.w(TAG, "Failed to unsubscribe: " + topic);
                    }
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Mqtt_Provider.getAuthority(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Mqtt.ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
        filter.addAction(Mqtt.ACTION_AWARE_MQTT_TOPIC_UNSUBSCRIBE);
        filter.addAction(Mqtt.ACTION_AWARE_MQTT_MSG_PUBLISH);
        registerReceiver(mqttReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            if (Aware.is_watch(this)) {
                Log.d(TAG, "This is an Android Wear device, we can't connect to MQTT. Disabling it!");
                Aware.setSetting(this, Aware_Preferences.STATUS_MQTT, false);
                stopSelf();
            } else {
                Aware.setSetting(this, Aware_Preferences.STATUS_MQTT, true);

                if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
                    if (DEBUG)
                        Log.d(TAG, "MQTT: Client ID=" + MQTT_CLIENT.getClientId() + "\n Server:" + MQTT_CLIENT.getServerURI());
                } else {
                    initializeMQTT();
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mqttReceiver);

        if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
            try {
                MQTT_MESSAGES_PERSISTENCE.close();
                MQTT_CLIENT.disconnect();
                if (Aware.DEBUG)
                    Log.e(TAG, "Disconnected by demand successfully from the server...");
            } catch (MqttException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
            }
        }

        if (Aware.DEBUG) Log.d(TAG, "MQTT service terminated...");
    }

    private void initializeMQTT() {

        if (mqttAsync != null) return; //already initialised

        String server = Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_SERVER);
        if (server == null || server.length() == 0) return;

        if (server.contains("http") || server.contains("https")) {
            Uri serverUri = Uri.parse(server);
            server = serverUri.getHost();
        }

        MQTT_SERVER = server;
        MQTT_PORT = Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PORT);
        MQTT_USERNAME = Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_USERNAME);
        MQTT_PASSWORD = Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PASSWORD);
        MQTT_KEEPALIVE = (Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE).length() > 0 ? Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE) : "600");
        MQTT_QoS = Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_QOS);

        if (Integer.parseInt(MQTT_PORT) == 1883) MQTT_PROTOCOL = "tcp";
        if (Integer.parseInt(MQTT_PORT) == 8883) MQTT_PROTOCOL = "ssl";

        String MQTT_URL = MQTT_PROTOCOL + "://" + MQTT_SERVER + ":" + MQTT_PORT;

        if (MQTT_MESSAGES_PERSISTENCE == null)
            MQTT_MESSAGES_PERSISTENCE = new MemoryPersistence();

        MqttConnectOptions MQTT_OPTIONS = new MqttConnectOptions();
        MQTT_OPTIONS.setCleanSession(false); //resume pending messages from server
        MQTT_OPTIONS.setConnectionTimeout(Integer.parseInt(MQTT_KEEPALIVE) + 10); //add 10 seconds to keep alive as options timeout
        MQTT_OPTIONS.setKeepAliveInterval(Integer.parseInt(MQTT_KEEPALIVE));
        MQTT_OPTIONS.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        MQTT_OPTIONS.setAutomaticReconnect(true);

        if (MQTT_USERNAME.length() > 0)
            MQTT_OPTIONS.setUserName(MQTT_USERNAME);

        if (MQTT_PASSWORD.length() > 0)
            MQTT_OPTIONS.setPassword(MQTT_PASSWORD.toCharArray());


        try {
            if (MQTT_PROTOCOL.equalsIgnoreCase("ssl")) {
                SocketFactory factory = new SSLUtils(this).getSocketFactory(MQTT_SERVER);
                MQTT_OPTIONS.setSocketFactory(factory);
            }

            MQTT_CLIENT = new MqttClient(
                    MQTT_URL,
                    String.valueOf(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).hashCode()),
                    MQTT_MESSAGES_PERSISTENCE);

            MQTT_CLIENT.setCallback(this);

            mqttAsync = new MQTTAsync();
            mqttAsync.execute(MQTT_OPTIONS);

        } catch (NullPointerException | MqttException | IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());

            Aware.setSetting(this, Aware_Preferences.STATUS_MQTT, false);
            stopSelf();
        }
    }

    /**
     * UI Thread safe background MQTT options attempt!
     *
     * @author denzil
     */
    private class MQTTAsync extends AsyncTask<MqttConnectOptions, Void, Boolean> {
        private MqttConnectOptions options;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (Aware.DEBUG) Log.d(TAG, "MQTT service connecting...");
        }

        @Override
        protected Boolean doInBackground(MqttConnectOptions... params) {
            options = params[0];
            try {
                MQTT_CLIENT.connect(options);
            } catch (MqttSecurityException e) {
                if (Aware.DEBUG) Log.e(TAG, "SecurityException: " + e.getMessage());
                return false;
            } catch (MqttException e) {
                if (Aware.DEBUG) Log.e(TAG, "MqttException: " + e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (result) {
                //Study specific subscribes
                if (Aware.isStudy(getApplicationContext())) {
                    Cursor studyInfo = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                    if (studyInfo != null && studyInfo.moveToFirst()) {
                        Intent studySubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                        studySubscribe.putExtra(EXTRA_TOPIC, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)) + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/broadcasts");
                        sendBroadcast(studySubscribe);

                        studySubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                        studySubscribe.putExtra(EXTRA_TOPIC, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)) + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/esm");
                        sendBroadcast(studySubscribe);

                        studySubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                        studySubscribe.putExtra(EXTRA_TOPIC, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)) + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/configuration");
                        sendBroadcast(studySubscribe);

                        studySubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                        studySubscribe.putExtra(EXTRA_TOPIC, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)) + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/schedulers");
                        sendBroadcast(studySubscribe);

                        studySubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                        studySubscribe.putExtra(EXTRA_TOPIC, studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)) + "/" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/#");
                        sendBroadcast(studySubscribe);
                    }
                    if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
                }

                //Self-subscribes
                Intent selfSubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                selfSubscribe.putExtra(EXTRA_TOPIC, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/broadcasts");
                sendBroadcast(selfSubscribe);

                selfSubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                selfSubscribe.putExtra(EXTRA_TOPIC, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/esm");
                sendBroadcast(selfSubscribe);

                selfSubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                selfSubscribe.putExtra(EXTRA_TOPIC, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/configuration");
                sendBroadcast(selfSubscribe);

                selfSubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                selfSubscribe.putExtra(EXTRA_TOPIC, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/schedulers");
                sendBroadcast(selfSubscribe);

                selfSubscribe = new Intent(ACTION_AWARE_MQTT_TOPIC_SUBSCRIBE);
                selfSubscribe.putExtra(EXTRA_TOPIC, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID) + "/#");
                sendBroadcast(selfSubscribe);

                if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
                    if (awareSensor != null) awareSensor.onConnected();
                    Log.d(TAG, "MQTT: Client ID=" + MQTT_CLIENT.getClientId() + "\n Server:" + MQTT_CLIENT.getServerURI());
                }

            } else {
                if (Aware.DEBUG)
                    Log.d(TAG, "MQTT Client failed to connect... Parameters used: " + options.toString());
                if (Aware.DEBUG) Log.d(TAG, "Disabling MQTT...");
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MQTT, false);
                Aware.stopMQTT(getApplicationContext());
            }
        }
    }

    /**
     * Publish message to topic
     *
     * @param topicName
     * @param messageText
     */
    public static boolean publish(String topicName, String messageText) {
        if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
            try {
                MqttMessage message = new MqttMessage();
                message.setPayload(messageText.getBytes());
                message.setQos(Integer.parseInt(MQTT_QoS));
                message.setRetained(true);

                MQTT_CLIENT.publish(topicName, message);
            } catch (MqttPersistenceException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                return false;
            } catch (MqttException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Subscribe to a topic
     *
     * @param topicName
     */
    public static boolean subscribe(String topicName) {
        if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
            try {
                MQTT_CLIENT.subscribe(topicName, Integer.parseInt(MQTT_QoS));
            } catch (MqttSecurityException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                return false;
            } catch (MqttException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Unsubscribe a topic
     *
     * @param topicName
     */
    public static boolean unsubscribe(String topicName) {
        if (MQTT_CLIENT != null && MQTT_CLIENT.isConnected()) {
            try {
                MQTT_CLIENT.unsubscribe(topicName);
            } catch (MqttException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                return false;
            }
        }
        return true;
    }
}