package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * Created by denzil on 11/05/15.
 */
public class WearClient extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    /**
     * Ask phone to do a HTTP(S) POST
     */
    public static final String ACTION_AWARE_ANDROID_WEAR_HTTP_POST = "ACTION_AWARE_ANDROID_WEAR_HTTP_POST";

    /**
     * Ask phone to do a HTTP(S) GET
     */
    public static final String ACTION_AWARE_ANDROID_WEAR_HTTP_GET = "ACTION_AWARE_ANDROID_WEAR_HTTP_GET";

    /**
     * Ask watch to join a study
     */
    public static final String ACTION_AWARE_ANDROID_WEAR_JOIN_STUDY = "ACTION_AWARE_ANDROID_WEAR_JOIN_STUDY";

    /**
     * Ask watch to quit a study
     */
    public static final String ACTION_AWARE_ANDROID_WEAR_QUIT_STUDY = "ACTION_AWARE_ANDROID_WEAR_QUIT_STUDY";

    /**
     * Extra for HTTP POST/GET
     */
    public static final String EXTRA_URL = "extra_url";

    /**
     * Extra for HTTP POST
     */
    public static final String EXTRA_DATA = "extra_data";

    /**
     * Extra for HTTP(S) POST/GET if request is compressed
     */
    public static final String EXTRA_GZIP = "extra_gzip";

    /**
     * Extra for JOIN STUDY requests
     */
    public static final String EXTRA_STUDY = "extra_study";

    public static GoogleApiClient googleClient;
    public static Node peer;

    public static String TAG = "AWARE::Android Wear Client";

    public volatile static String wearResponse;

    @Override
    public void onCreate() {
        super.onCreate();

        googleClient = new GoogleApiClient.Builder(this)
                .addApiIfAvailable(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        googleClient.connect();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( Aware.is_watch(getApplicationContext()) ) {

            if( Aware.DEBUG ) Log.d(WearClient.TAG, "Message received from phone! Message:" + messageEvent.toString());

            /**
             * Watch is joining study
             */
            if( messageEvent.getPath().equals("/join/study") ) {
                String webserver = new String(messageEvent.getData());

                if(Aware.DEBUG) Log.d(WearClient.TAG, "Joining study: " + webserver);

                //Check if we are on different study, join study!
                if( webserver.length() > 0 && ! Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals(webserver) ) {
                    Intent study_config = new Intent(getApplicationContext(), Aware_Preferences.StudyConfig.class);
                    study_config.putExtra("study_url", webserver);
                    startService(study_config);
                }
            }

            /**
             * Watch is asking to quit study
             */
            if( messageEvent.getPath().equals("/quit/study")) {
                if(Aware.DEBUG) Log.d(WearClient.TAG, "Quitting study... Resetting watch");
                Aware.reset(getApplicationContext());
                if( getPackageName().equals("com.aware") ) {
                    Intent preferences = new Intent(getApplicationContext(), Aware_Preferences.class);
                    preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(preferences);
                }
            }

            /**
             * Watch got the HTTP GET/POST response from the phone
             */
            if( messageEvent.getPath().equals("/https/get") || messageEvent.getPath().equals("/https/post") ) {

                if( Aware.DEBUG ) {
                    Log.d(WearClient.TAG, "Entity content:" + new String(messageEvent.getData()));
                }
                wearResponse = new String(messageEvent.getData());
            }

        } else {

            if( Aware.DEBUG ) Log.d(WearClient.TAG, "Message received from watch! Message:" + messageEvent.toString());

            //Fetch page and return it to watch as a GET
            if( messageEvent.getPath().equals("/https/get") ) {
                try {
                    JSONObject request = new JSONObject(new String(messageEvent.getData()));

                    String request_url = request.getString(WearClient.EXTRA_URL);
                    String protocol = request_url.substring(0, request_url.indexOf(":"));

                    String output;
                    if( protocol.equals("https")) {
                        try {
                            output = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), request_url)).dataGET(request_url, request.getBoolean(WearClient.EXTRA_GZIP));
                        } catch (FileNotFoundException e ) {
                            output = null;
                        }
                    } else {
                        output = new Http(getApplicationContext()).dataGET(request_url, request.getBoolean(WearClient.EXTRA_GZIP));
                    }

                    if( output != null ) {
                        Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/get", output.getBytes());
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //Fetch page and return it to watch as a POST (can contain extra data)
            if( messageEvent.getPath().equals("/https/post") ) {
                try {
                    Hashtable<String, String> data = new Hashtable<>();

                    JSONObject request = new JSONObject(new String(messageEvent.getData()));
                    JSONObject data_json = new JSONObject(request.getString(WearClient.EXTRA_DATA));

                    Iterator<String> iterator = data_json.keys();
                    while( iterator.hasNext() ) {
                        String key = iterator.next();
                        String value = data_json.getString(key);
                        data.put(key, value);
                    }

                    String request_url = request.getString(WearClient.EXTRA_URL);
                    String protocol = request_url.substring(0, request_url.indexOf(":"));

                    String output;
                    if( protocol.equals("https")) {
                        try {
                            output = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), request_url)).dataPOST(request_url, data, request.getBoolean(WearClient.EXTRA_GZIP));
                        } catch (FileNotFoundException e ) {
                            output = null;
                        }
                    } else {
                        output = new Http(getApplicationContext()).dataPOST(request.getString(WearClient.EXTRA_URL), data, request.getBoolean(WearClient.EXTRA_GZIP));
                    }
                    if( output != null ) {
                        Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/post", output.getBytes());
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class AndroidWearHTTPClient extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if( peer == null ) return;

            if( Aware.DEBUG ) {
                Log.d(TAG, "Received event... " + intent.getAction());
                if( peer != null ) {
                    Log.d(TAG, "Sending event to " + peer.getDisplayName());
                }
            }

            //Watch is asking an HTTPS GET
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_HTTP_GET) ) {
                if( Aware.is_watch(context) ) {
                    JSONObject data = new JSONObject();
                    try {
                        data.put(EXTRA_URL, intent.getStringExtra(EXTRA_URL));
                        data.put(EXTRA_GZIP, intent.getBooleanExtra(EXTRA_GZIP, true)); //by default, all requests are gzipped for bandwith savings
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //Watch sends message to phone
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/https/get", data.toString().getBytes());
                }
            }

            //Watch is asking an HTTPS POST
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_HTTP_POST)) {
                if( Aware.is_watch(context) ) {
                    JSONObject data = new JSONObject();
                    try {
                        data.put(EXTRA_URL, intent.getStringExtra(EXTRA_URL));
                        data.put(EXTRA_GZIP, intent.getBooleanExtra(EXTRA_GZIP, true)); //by default, all requests are gzipped for bandwith savings
                        data.put(EXTRA_DATA, intent.getStringExtra(EXTRA_DATA));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //Watch sends message to phone
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/https/post", data.toString().getBytes());
                }
            }

            //Phone is asking watch to join a study
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_JOIN_STUDY) ) {
                if( ! Aware.is_watch(context) ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/join/study", intent.getStringExtra(EXTRA_STUDY).getBytes());
                }
            }

            //Phone is asking watch to quit a study
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_QUIT_STUDY) ) {
                if( ! Aware.is_watch(context) ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/quit/study", null);
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connected to Google API!");
        }

        Wearable.MessageApi.addListener(googleClient, this);

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for (Node n : result.getNodes()) {
                    if (n.isNearby() && !n.getDisplayName().equals("cloud")) {
                        peer = n;
                        Log.d(TAG, "Connected to " + peer.getDisplayName());
                    }
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection suspended to Google API!");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection failed to Google API!");
        }
        if( connectionResult.getErrorCode() == ConnectionResult.API_UNAVAILABLE ) {
            if( Aware.DEBUG ) {
                Log.d(TAG, "Android Wear API is not installed! Self-destroy!");
            }
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( googleClient != null ) {
            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
    }
}
