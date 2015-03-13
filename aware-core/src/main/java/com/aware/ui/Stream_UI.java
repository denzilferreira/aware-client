package com.aware.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.aware.Wear_Sync;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;

public class Stream_UI extends Aware_Activity {
	
	/**
	 * Received broadcast to request an update on the stream
	 */
	public static final String ACTION_AWARE_UPDATE_STREAM = "ACTION_AWARE_UPDATE_STREAM";

    /**
     * Broadcast to let plugins know that the stream is visible to the user
     */
    public static final String ACTION_AWARE_STREAM_OPEN = "ACTION_AWARE_STREAM_OPEN";

    /**
     * Broadcast to let plugins know that the stream is not visible to the user
     */
    public static final String ACTION_AWARE_STREAM_CLOSED = "ACTION_AWARE_STREAM_CLOSED";
	
	private static ListView stream_container;
    private static LinearLayout core_container;
    private static CardAdapter card_adapter;

	@Override
	protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);

        setContentView(R.layout.stream_ui);

        stream_container = (ListView) findViewById(R.id.stream_container);
        core_container = (LinearLayout) findViewById(R.id.core_cards);
        updateCore();

        Cursor plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " ASC");
        card_adapter = new CardAdapter(this, plugins, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        stream_container.setAdapter(card_adapter);

        ImageButton add_to_stream = (ImageButton) findViewById(R.id.change_stream);
        add_to_stream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class);
                startActivity(plugin_manager);
            }
        });

		IntentFilter filter = new IntentFilter(ACTION_AWARE_UPDATE_STREAM);
		registerReceiver(stream_updater, filter);
	}

    public class CardAdapter extends CursorAdapter {
        public CardAdapter( Context context, Cursor c, int flags ) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            String package_name = cursor.getString( cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
            View card = Aware.getContextCard( getApplicationContext(), package_name );
            card.setTag( package_name );
            return card;
        }

        @Override
        public void bindView(View card, Context context, Cursor cursor) {
            String package_name = cursor.getString( cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
            card = Aware.getContextCard( getApplicationContext(), package_name );
            card.setTag(package_name);
        }
    }

    private View buildCard(View content) {
        CardView card = new CardView(this);
        LayoutParams params = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        params.setMargins( 0,0,0,10 );
        card.setLayoutParams(params);

        content.setBackgroundColor(Color.WHITE);
        content.setPadding(20, 20, 20, 20);
        card.addView(content);
        return card;
    }

    private void updateCore() {
        //Aware-core cards
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ANDROID_WEAR).equals("true") ) {
            core_container.addView(buildCard(Wear_Sync.getContextCard(getApplicationContext())), 0);
        }
    }

    private void updateCards() {
        updateCore();

        Cursor plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " ASC");
        card_adapter = new CardAdapter(this, plugins, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        stream_container.setAdapter(card_adapter);
    }
	
	@Override
	protected void onResume() {
		super.onResume();
        Intent is_visible = new Intent(ACTION_AWARE_STREAM_OPEN);
        sendBroadcast(is_visible);
	}

    @Override
    protected void onPause() {
        super.onPause();
        Intent not_visible = new Intent(ACTION_AWARE_STREAM_CLOSED);
        sendBroadcast(not_visible);
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(stream_updater);
	}
	
	private StreamUpdater stream_updater = new StreamUpdater();
	public class StreamUpdater extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateCards();
		}
	}
}
