package com.aware;

import android.content.ContentValues;
import android.content.Intent;
import android.database.SQLException;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Created by denzil on 29/10/14.
 */
public class Wear_Service extends WearableListenerService {

    public static long last_sync = 0;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        if( Aware.is_watch(getApplicationContext()) ) {
            if( Aware.DEBUG ) Log.d(Aware.TAG, "This is a watch, not doing backup");
            return;
        }

        for(DataEvent event : dataEvents ) {
            DataMapItem datamapItem = DataMapItem.fromDataItem(event.getDataItem());
            saveData(datamapItem.getDataMap().getString("json"));
        }
    }

    public void saveData( String data ) {
        try {
            JSONObject json = new JSONObject(data);
            if( Aware.DEBUG ) Log.d(Aware.TAG, "Saving: " + json.toString(5));

            Uri content_uri = Uri.parse(json.getString("content_uri"));

            Iterator<String> keys = json.keys();
            ContentValues watch_data = new ContentValues();
            while( keys.hasNext() ) {
                String key = keys.next();
                if( key.equals("_id") || key.equals("content_uri") ) continue;
                if( key.contains("timestamp") || key.contains("double") ) {
                    watch_data.put(key, json.getDouble(key));
                } else {
                    watch_data.put(key, json.getString(key));
                }
            }

            try {
                getContentResolver().insert( content_uri, watch_data );
                if( Aware.DEBUG ) Log.d(Aware.TAG, "Saved on the phone!");
            } catch( SQLException e ) {
                Log.e( Aware.TAG, "ERROR:" + e.getMessage() );
            }
            last_sync = System.currentTimeMillis();
        }
        catch (JSONException e ) {}
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if(Aware.DEBUG) Log.d(Aware.TAG, "Message received!");
        Intent broadcast = new Intent(Wear_Sync.ACTION_AWARE_WEAR_MESSAGE_RECEIVED);
        broadcast.putExtra(Wear_Sync.EXTRA_MESSAGE, messageEvent.getData());
        sendBroadcast(broadcast);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        if(Aware.DEBUG) Log.d(Aware.TAG, "Connected to: " + peer.getDisplayName());
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        if(Aware.DEBUG) Log.d(Aware.TAG, "Disconnected from " + peer.getDisplayName());
    }
}

