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
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
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
	
	private static LinearLayout stream_container;

    private Handler refreshHandler = new Handler();
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            loadStream();
            refreshHandler.postDelayed(refresher, 1000);
        }
    };
	
	@Override
	protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);

        setContentView(R.layout.stream_ui);

		stream_container = (LinearLayout) findViewById(R.id.stream_container);
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
	
	@Override
	protected void onResume() {
		super.onResume();
		loadStream();
        refreshHandler.post(refresher);
        Intent is_visible = new Intent(ACTION_AWARE_STREAM_OPEN);
        sendBroadcast(is_visible);
	}
	
	private void loadStream() {
		stream_container.removeAllViews();

        Cursor get_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " DESC");
		if( get_plugins != null && get_plugins.moveToFirst() ) {
			do {
				View card = Aware.getContextCard( this, get_plugins.getString( get_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME)) );
				if( card != null ) {
					stream_container.addView(card);
				}
			} while(get_plugins.moveToNext());			
		}
        if( get_plugins != null && ! get_plugins.isClosed() ) get_plugins.close();

        //Aware-core cards
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ANDROID_WEAR).equals("true") ) {
            stream_container.addView(buildCard(Wear_Sync.getContextCard(getApplicationContext())));
        }
	}

    private View buildCard(View content) {
        CardView card = new CardView( this );
        LayoutParams params = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        params.setMargins( 0,0,0,10 );
        card.setLayoutParams(params);

        content.setBackgroundColor(Color.WHITE);
        content.setPadding(20, 20, 20, 20);
        card.addView(content);
        return card;
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacksAndMessages(null);

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
			loadStream();
		}
	}
}
