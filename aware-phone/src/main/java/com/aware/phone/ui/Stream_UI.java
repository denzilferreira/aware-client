package com.aware.phone.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ListView;

import com.aware.Aware;

import com.aware.phone.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;

public class Stream_UI extends Aware_Activity {
	
	/**
	 * Received broadcast to request an update on the stream
	 */
	public static final String ACTION_AWARE_UPDATE_STREAM = "ACTION_AWARE_UPDATE_STREAM";

    /**
     * Broadcast to let cards know that the stream is visible to the user
     */
    public static final String ACTION_AWARE_STREAM_OPEN = "ACTION_AWARE_STREAM_OPEN";

    /**
     * Broadcast to let cards know that the stream is not visible to the user
     */
    public static final String ACTION_AWARE_STREAM_CLOSED = "ACTION_AWARE_STREAM_CLOSED";
	
	private static ListView stream_container;

//  private static MatrixCursor core_cards;
    private static CardAdapter card_adapter;
    private static Cursor cards;

	@Override
	protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);

        setContentView(R.layout.stream_ui);

        stream_container = (ListView) findViewById(R.id.stream_container);

        ImageButton add_to_stream = (ImageButton) findViewById(R.id.change_stream);
        add_to_stream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class);
                startActivity(plugin_manager);
            }
        });

		IntentFilter filter = new IntentFilter(Stream_UI.ACTION_AWARE_UPDATE_STREAM);
		registerReceiver(stream_updater, filter);
	}

    public class CardAdapter extends CursorAdapter {
        public CardAdapter( Context context, Cursor c, int flags ) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            String package_name = cursor.getString( cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME) );
            View card;
            card = Aware.getContextCard(getApplicationContext(), package_name);
            if( card == null ) card = new View(context);
            return card;
        }

        @Override
        public void bindView(View card, Context context, Cursor cursor) {
            String package_name = cursor.getString( cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
            card = Aware.getContextCard(getApplicationContext(), package_name);
        }
    }

//    private void updateCore() {
//        core_cards = new MatrixCursor(new String[]{
//                Aware_Plugins.PLUGIN_ID,
//                Aware_Plugins.PLUGIN_PACKAGE_NAME,
//                Aware_Plugins.PLUGIN_NAME,
//                Aware_Plugins.PLUGIN_VERSION,
//                Aware_Plugins.PLUGIN_STATUS,
//                Aware_Plugins.PLUGIN_AUTHOR,
//                Aware_Plugins.PLUGIN_ICON,
//                Aware_Plugins.PLUGIN_DESCRIPTION
//        });
//
//        //Aware-core cards
//        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ANDROID_WEAR).equals("true") ) {
//            Object[] wear_card = new Object[] {
//                    Aware_Preferences.STATUS_ANDROID_WEAR.hashCode(),
//                    Wear_Sync.class.getName(),
//                    "Android Wear",
//                    BuildConfig.VERSION_CODE,
//                    Aware_Plugin.STATUS_PLUGIN_ON,
//                    "AWARE",
//                    null,
//                    "Android Wear synching"
//            };
//            core_cards.addRow(wear_card);
//            cards = new MergeCursor(new Cursor[]{ core_cards, cards });
//        }
//    }

    private void updateCards() {
        cards = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " ASC");

        if (Aware.DEBUG)
            Log.d(Aware.TAG, "ContextCards: " + DatabaseUtils.dumpCursorToString(cards));

//        updateCore();
        card_adapter = new CardAdapter(this, cards, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        stream_container.setAdapter(card_adapter);
    }
	
	@Override
	protected void onResume() {
		super.onResume();
        Intent is_visible = new Intent(ACTION_AWARE_STREAM_OPEN);
        sendBroadcast(is_visible);
        updateCards();
	}

    @Override
    protected void onPause() {
        super.onPause();

        Intent not_visible = new Intent(ACTION_AWARE_STREAM_CLOSED);
        sendBroadcast(not_visible);

        //Fixed: leak on stream cursor
        if( cards != null && ! cards.isClosed()) cards.close();
        card_adapter.changeCursor(null);
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(stream_updater);

        //Fixed: leak on stream cursor
        if( cards != null && ! cards.isClosed()) cards.close();
        card_adapter.changeCursor(null);
	}
	
	private StreamUpdater stream_updater = new StreamUpdater();
	public class StreamUpdater extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateCards();
		}
	}
}
