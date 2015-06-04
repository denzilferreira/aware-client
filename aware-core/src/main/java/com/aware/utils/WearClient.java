package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by denzil on 11/05/15.
 */
public class WearClient extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
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

    public static String TAG = "AWARE::Android Wear Proxy";

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        googleClient.connect();

        return super.onStartCommand(intent, flags, startId);
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

            //Watch is asking to download a plugin. This makes the phone install the plugin on itself. If there is a wear package, it also gets installed on the watch
            if( intent.getAction().equals(ACTION_AWARE_ANDROID_WEAR_INSTALL_PLUGIN) ) {
                if( Aware.is_watch(context) ) {
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/install/plugin", intent.getStringExtra(EXTRA_PACKAGE_NAME).getBytes());
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

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                for( Node n : result.getNodes() ) {
                    if( n.isNearby() && ! n.getDisplayName().equals("cloud") ) {
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
            if(Aware.DEBUG) Log.d(TAG, "Android Wear service terminated...");
            googleClient.disconnect();
        }
    }
}
