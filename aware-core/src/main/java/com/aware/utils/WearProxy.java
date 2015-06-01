package com.aware.utils;

import android.content.Intent;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.google.android.gms.wearable.MessageEvent;
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
 * Created by denzil on 19/05/15.
 */
public class WearProxy extends WearableListenerService {

    public volatile static HttpResponse wearResponse;

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

                if( webserver.length() > 0 && ! Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals(webserver) ) { //different study, join study!
                    Intent study_config = new Intent(getApplicationContext(), Aware_Preferences.StudyConfig.class);
                    study_config.putExtra("study_url", new String(messageEvent.getData()));
                    startService(study_config);
                }
            }

            /**
             * Watch is asking to quit study
             */
            if( messageEvent.getPath().equals("/quit/study")) {

                if(Aware.DEBUG) Log.d(WearClient.TAG, "Quitting study... Resetting watch");

                Aware.reset(getApplicationContext());
                Intent preferences = new Intent(getApplicationContext(), Aware_Preferences.class);
                preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(preferences);
            }

            /**
             * Watch got the HTTP GET/POST response from the phone
             */
            if( messageEvent.getPath().equals("/https/get") || messageEvent.getPath().equals("/https/post") ) {

                HttpResponseFactory factory = new DefaultHttpResponseFactory();
                HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
                response.setHeader("Content-Encoding", "gzip");
                response.setStatusCode(200);
                response.setEntity(new ByteArrayEntity(messageEvent.getData()));

                if( Aware.DEBUG ) {
                    Log.d(WearClient.TAG, "Entity content:" + Https.undoGZIP(response));
                }
                wearResponse = response;
            }

        } else {

            if( Aware.DEBUG ) Log.d(WearClient.TAG, "Message received from watch! Message:" + messageEvent.toString());

            //Fetch plugin
            if( messageEvent.getPath().equals("/install/plugin") ) {
                //If the plugin is not installed, it will ask to install on the phone
                Aware.downloadPlugin(getApplicationContext(), new String(messageEvent.getData()), false);
            }

            //Fetch page and return it to watch as a GET
            if( messageEvent.getPath().equals("/https/get") ) {
                try {

                    JSONObject request = new JSONObject(new String(messageEvent.getData()));
                    HttpResponse output = new Https(getApplicationContext()).dataGET(request.getString(WearClient.EXTRA_URL), request.getBoolean(WearClient.EXTRA_GZIP));

                    Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/get", EntityUtils.toByteArray(output.getEntity()));

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //Fetch page and return it to watch as a POST (can contain extra data)
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

                    Wearable.MessageApi.sendMessage(WearClient.googleClient, WearClient.peer.getId(), "/https/post", EntityUtils.toByteArray(output.getEntity()));

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
