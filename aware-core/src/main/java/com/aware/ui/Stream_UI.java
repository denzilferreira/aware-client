package com.aware.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;

public class Stream_UI extends Aware_Activity {
	
	/**
	 * Received broadcast to request an update on the stream
	 */
	public static final String ACTION_AWARE_UPDATE_STREAM = "ACTION_AWARE_UPDATE_STREAM";
	
	private static LinearLayout stream_container;
	private static ProgressBar loading_stream;
	
	@Override
	protected void onCreate(Bundle arg0) {
		
		setContentView(R.layout.stream_ui);
		
		//Fix for the navigation drawer consistency across all activities in AWARE
		super.onCreate(arg0);
		
		stream_container = (LinearLayout) findViewById(R.id.stream_container);
		loading_stream = (ProgressBar) findViewById(R.id.loading_stream);
		
		IntentFilter filter = new IntentFilter(ACTION_AWARE_UPDATE_STREAM);
		registerReceiver(stream_updater, filter);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		loadStream();
	}
	
	private void loadStream() {
		stream_container.removeAllViews();
		stream_container.addView(loading_stream);
		loading_stream.setVisibility(View.VISIBLE);
		
		Cursor get_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " DESC");
		if( get_plugins != null && get_plugins.moveToFirst() ) {
			do {
				View card = Aware.getContextCard( this, get_plugins.getString( get_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME)) );
				if( card != null ) {
					stream_container.addView(card);
				}
			} while(get_plugins.moveToNext());			
		} else {
			LinearLayout empty = new LinearLayout( this );
			TextView empty_message = new TextView( this );
			empty_message.setText("No plugins available. Tap to activate/download some!");
			empty.setBackgroundColor(Color.WHITE);
			empty.setPadding(20, 20, 20, 20);
			empty.addView(empty_message);
			empty.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class);
					startActivity(plugin_manager);
				}
			});
			stream_container.addView(empty);
			
			LinearLayout shadow = new LinearLayout( this );
			LayoutParams params_shadow = new LayoutParams(LayoutParams.MATCH_PARENT, 5);
			params_shadow.setMargins(0, 0, 0, 10);
			shadow.setBackgroundColor( getResources().getColor(R.color.card_shadow) );
			shadow.setMinimumHeight(5);
			shadow.setLayoutParams(params_shadow);
			stream_container.addView(shadow);
		}
		if( get_plugins != null && ! get_plugins.isClosed() ) get_plugins.close();
		
		loading_stream.setVisibility(View.GONE);
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
