package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
     * Ask phone to install a plugin
     */
    public static final String ACTION_AWARE_ANDROID_WEAR_INSTALL_PLUGIN = "ACTION_AWARE_ANDROID_WEAR_INSTALL_PLUGIN";

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
     * Extra for INSTALL PLUGIN requests
     */
    public static final String EXTRA_PACKAGE_NAME = "extra_package";

    /**
     * Extra for JOIN STUDY requests
     */
    public static final String EXTRA_STUDY = "extra_study";

    public static GoogleApiClient googleClient;
    public static Node peer;

    private static String TAG = "AWARE::Android Wear Proxy";

    public volatile static HttpResponse wearResponse;

    @Override
    public void onCreate() {
        super.onCreate();

        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if (result.getNodes().size() > 0) {
                    for( int i = 0; i<result.getNodes().size(); i++ ) {
                        if( ! result.getNodes().get(i).getDisplayName().equals("cloud") ) {
                            peer = result.getNodes().get(i);
                            Log.d(TAG, "Connected to " + peer.getDisplayName());
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        googleClient.connect();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( Aware.is_watch(getApplicationContext()) ) {

            if( Aware.DEBUG ) Log.d(TAG, "Message received from phone!");

                if( messageEvent.getPath().equals("/join/study") ) {
                    String webserver = new String(messageEvent.getData());

                    if(Aware.DEBUG) Log.d(TAG, "Joining study: " + webserver);

                    if( webserver.length() > 0 && ! Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals(webserver) ) { //different study, join study!
                        Intent study_config = new Intent(getApplicationContext(), Aware_Preferences.StudyConfig.class);
                        study_config.putExtra("study_url", new String(messageEvent.getData()));
                        startService(study_config);
                    }
                }
                if( messageEvent.getPath().equals("/quit/study")) {

                    if(Aware.DEBUG) Log.d(TAG, "Quitting study... Resetting watch");

                    Aware.reset(getApplicationContext());
                    Intent preferences = new Intent(getApplicationContext(), Aware_Preferences.class);
                    preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(preferences);
                }

            if( messageEvent.getPath().equals("/https/get") ) {
                HttpResponseFactory factory = new DefaultHttpResponseFactory();
                HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
                response.setHeader("Content-Encoding", "gzip");
                response.setStatusCode(200);
                response.setEntity(new ByteArrayEntity(messageEvent.getData()));

                wearResponse = response;

                if( Aware.DEBUG ) {
                    HttpResponse tmp = response;
                    Log.d(TAG, "Entity content:" + Https.undoGZIP(tmp));
                }
            }

            if( messageEvent.getPath().equals("/https/post") ) {
                HttpResponseFactory factory = new DefaultHttpResponseFactory();
                HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
                response.setHeader("Content-Encoding", "gzip");
                response.setStatusCode(200);
                response.setEntity(new ByteArrayEntity(messageEvent.getData()));

                wearResponse = response;

                if( Aware.DEBUG ) {
                    HttpResponse tmp = response;
                    Log.d(TAG, "Entity content:" + Https.undoGZIP(tmp));
                }
            }

        } else {

            if( Aware.DEBUG ) Log.d(TAG, "Message received from watch!");

            //Fetch plugin
            if( messageEvent.getPath().equals("/install/plugin") ) {
                //If the plugin is not installed, it will ask to install on the phone
                Aware.downloadPlugin(getApplicationContext(), new String(messageEvent.getData()), false);
            }

            //Fetch page from online
            if( messageEvent.getPath().equals("/https/get") ) {
                try {

                    JSONObject request = new JSONObject(new String(messageEvent.getData()));
                    HttpResponse output = new Https(getApplicationContext()).dataGET(request.getString(WearClient.EXTRA_URL), request.getBoolean(WearClient.EXTRA_GZIP));

                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "https/get", EntityUtils.toByteArray(output.getEntity()));

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if( messageEvent.getPath().equals("/https/post") ) {
                try {
                    ArrayList<NameValuePair> data = new ArrayList<>();

                    JSONObject request = new JSONObject(new String(messageEvent.getData()));
                    JSONObject data_json = new JSONObject(request.getString(WearClient.EXTRA_DATA));

                    Iterator<String> iterator = data_json.keys();
                    while( iterator.hasNext() ) {
                        String key = iterator.next();
                        String value = data_json.getString(key);
                        data.add(new BasicNameValuePair(key, value));
                    }
                    HttpResponse output = new Https(getApplicationContext()).dataPOST(request.getString(WearClient.EXTRA_URL), data, request.getBoolean(WearClient.EXTRA_GZIP));

                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "https/post", EntityUtils.toByteArray(output.getEntity()));

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class AndroidWearHTTPClient extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if( Aware.DEBUG ) {
                Log.d(TAG, "Received event... " + intent.getAction());
            }

            //Watch is asking an HTTPS GET
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_HTTP_GET) ) {
                if( Aware.is_watch(context) ) {
                    JSONObject data = new JSONObject();
                    try {
                        data.put(EXTRA_URL, intent.getStringExtra(EXTRA_URL));
                        data.put(EXTRA_GZIP, intent.getBooleanExtra(EXTRA_GZIP, true));
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
                        data.put(EXTRA_GZIP, intent.getBooleanExtra(EXTRA_GZIP, true));
                        data.put(EXTRA_DATA, intent.getStringExtra(EXTRA_DATA));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //Watch sends message to phone
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/https/post", data.toString().getBytes());
                }
            }

            //Watch is asking to download a plugin
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_INSTALL_PLUGIN) ) {
                if( Aware.is_watch(context) ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/install/plugin", intent.getStringExtra(EXTRA_PACKAGE_NAME).getBytes());
                }
            }

            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_JOIN_STUDY) ) {
                if( ! Aware.is_watch(context) ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/join/study", intent.getStringExtra(EXTRA_STUDY).getBytes());
                }
            }

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
    }

    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection suspended to Google API!");
        }
        googleClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection failed to Google API!");
        }
        googleClient.connect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( googleClient != null ) {
            Wearable.MessageApi.removeListener(googleClient, this);

            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
    }
}
