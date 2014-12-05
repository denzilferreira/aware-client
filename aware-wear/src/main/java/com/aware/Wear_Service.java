package com.aware;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by denzil on 29/10/14.
 */
public class Wear_Service extends WearableListenerService {

    private ContentValues[] watch_data_buffer;
    private List<ContentValues> watch_data_values = new ArrayList<ContentValues>();

    public static long last_sync;

    private Node peer;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        if( Aware.is_watch(this) ) {
            //The watch doesn't replicate data locally from the phone
            return;
        }

        for( DataEvent event : dataEvents ) {
            DataMapItem datamapItem = DataMapItem.fromDataItem(event.getDataItem());
            Uri content_uri = Uri.parse(datamapItem.getDataMap().getString("content_uri"));
            saveData(datamapItem.getDataMap().getString("json"), content_uri);
        }
    }

    public void saveData( String data, Uri content_uri ) {
        try {
            JSONArray data_buffer = new JSONArray(data);

            if( Aware.DEBUG ) Log.d(Wear_Sync.TAG, "Saving to phone: " + data_buffer.length() + " records");

            for( int i = 0; i<data_buffer.length(); i++ ) {

                JSONObject json = data_buffer.getJSONObject(i);

                Iterator<String> keys = json.keys();
                ContentValues watch_data = new ContentValues();
                while( keys.hasNext() ) {
                    String key = keys.next();
                    if( key.contains("timestamp") || key.contains("double") ) {
                        watch_data.put(key, json.getDouble(key));
                    } else {
                        watch_data.put(key, json.getString(key));
                    }
                }
                watch_data_values.add(watch_data);
            }

            watch_data_buffer = new ContentValues[watch_data_values.size()];
            watch_data_values.toArray(watch_data_buffer);

            new AsyncStore(content_uri).execute(watch_data_buffer);

            watch_data_values.clear();

            last_sync = System.currentTimeMillis();
        }
        catch (JSONException e ) {}
    }

    /**
     * Asynchronous data storage on database.
     */
    private class AsyncStore extends AsyncTask<ContentValues[], Void, Void> {
        private Uri content_uri;

        public AsyncStore(Uri uri) {
            content_uri = uri;
        }

        @Override
        protected Void doInBackground(ContentValues[]... data) {
            getContentResolver().bulkInsert( content_uri, data[0] );
            return null;
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if(Aware.DEBUG) Log.d(Wear_Sync.TAG, "Message received: " + messageEvent.toString());

        if( messageEvent.getPath().equals("/get_latest") ) {
            try{
                Uri content_uri = Uri.parse(new String(messageEvent.getData()));

                double latest_timestamp = 0;
                Cursor last_entry = getContentResolver().query(content_uri, new String[]{"timestamp"}, Aware_Preferences.DEVICE_ID + " NOT LIKE '" + Aware.getSetting(this, Aware_Preferences.DEVICE_ID) + "'", null, "timestamp DESC LIMIT 1");
                if( last_entry != null && last_entry.moveToFirst() ) {
                    latest_timestamp = last_entry.getDouble(0);
                }
                if( last_entry != null && ! last_entry.isClosed() ) last_entry.close();

                //This broadcast will let the watch know whats the latest data we have on the phone
                Intent broadcast = new Intent(Wear_Sync.ACTION_AWARE_WEAR_MESSAGE);
                broadcast.putExtra(Wear_Sync.EXTRA_TOPIC, "latest");

                JSONObject obj = new JSONObject();
                obj.put("content_uri", content_uri.toString());
                obj.put("latest_timestamp", latest_timestamp);

                broadcast.putExtra(Wear_Sync.EXTRA_MESSAGE, obj.toString());
                sendBroadcast(broadcast);

            } catch( JSONException e ) {
                e.printStackTrace();
            }
        }

        if( messageEvent.getPath().equals("/start_sync") ) {
            try {
                JSONObject data = new JSONObject(new String(messageEvent.getData()));

                String content_uri = data.getString("content_uri");
                double latest_timestamp = data.getDouble("latest_timestamp");

                Intent wearbg = new Intent(this, Wear_Sync.Wear_Bg.class);
                wearbg.setAction(Wear_Sync.Wear_Bg.ACTION_WEAR_SYNC);
                wearbg.putExtra("content_uri", content_uri);
                wearbg.putExtra("latest_timestamp", latest_timestamp);
                startService(wearbg);

            } catch(JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        if(Aware.DEBUG) Log.d(Aware.TAG, "Connected to: " + peer.getDisplayName());
        this.peer = peer;
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        if(Aware.DEBUG) Log.d(Aware.TAG, "Disconnected from " + peer.getDisplayName());
    }
}

