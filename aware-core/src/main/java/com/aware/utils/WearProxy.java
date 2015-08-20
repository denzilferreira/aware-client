package com.aware.utils;

import android.content.Intent;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Hashtable;
import java.util.Iterator;

/**
 * Created by denzil on 19/05/15.
 */
public class WearProxy extends WearableListenerService {

    public volatile static String wearResponse;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if( WearClient.peer == null ) return;

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
                    String output = new Https(getApplicationContext()).dataGET(request.getString(WearClient.EXTRA_URL), request.getBoolean(WearClient.EXTRA_GZIP));

                    Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/get", output.getBytes());

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
                    String output = new Https(getApplicationContext()).dataPOST(request.getString(WearClient.EXTRA_URL), data, request.getBoolean(WearClient.EXTRA_GZIP));
                    Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/post", output.getBytes());

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
