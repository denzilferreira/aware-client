package com.aware.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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
		
		setContentView(R.layout.stream_ui);
		
		//This is a fix for the navigation drawer consistency across all activities in AWARE
		super.onCreate(arg0);
		
		stream_container = (LinearLayout) findViewById(R.id.stream_container);

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
        TextView empty_message = new TextView( this );
        empty_message.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class);
                startActivity(plugin_manager);
            }
        });
        empty_message.setText("Tap to activate/download more!");

        stream_container.addView(buildCard(empty_message));

        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ANDROID_WEAR).equals("true") ) {
            stream_container.addView(buildCard(Wear_Sync.getContextCard(getApplicationContext())));
        }
		
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
	}

    private View buildCard(View content) {

        LinearLayout card = new LinearLayout( this );

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);

        content.setBackgroundColor(Color.WHITE);
        content.setPadding(20, 20, 20, 20);

        card.addView(content);

        LinearLayout shadow = new LinearLayout(this);
        LayoutParams params_shadow = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params_shadow.setMargins(0, 0, 0, 10);
        shadow.setBackgroundColor(this.getResources().getColor(R.color.card_shadow));
        shadow.setMinimumHeight(5);
        shadow.setLayoutParams(params_shadow);

        card.addView(shadow);

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
