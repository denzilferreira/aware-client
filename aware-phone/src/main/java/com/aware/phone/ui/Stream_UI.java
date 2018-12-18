package com.aware.phone.ui;

import android.content.*;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import com.aware.Aware;
import com.aware.phone.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.DatabaseHelper;
import com.aware.utils.PluginsManager;
import org.json.JSONArray;
import org.json.JSONException;

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

//  private static MatrixCursor core_cards;

    private StreamAdapter streamAdapter;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);

        setContentView(R.layout.stream_ui);

        ImageButton add_to_stream = (ImageButton) findViewById(R.id.change_stream);
        add_to_stream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent plugin_manager = new Intent(getApplicationContext(), Plugins_Manager.class);
                startActivity(plugin_manager);
            }
        });

        IntentFilter filter = new IntentFilter(Stream_UI.ACTION_AWARE_UPDATE_STREAM);
        registerReceiver(stream_updater, filter);
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

    @Override
    protected void onResume() {
        super.onResume();

        Intent is_visible = new Intent(ACTION_AWARE_STREAM_OPEN);
        sendBroadcast(is_visible);

        ListView stream_container = (ListView) findViewById(R.id.stream_container);
        streamAdapter = new StreamAdapter(getApplicationContext());
        stream_container.setAdapter(streamAdapter);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    static class ViewHolder {
        View card;
    }

    private class StreamAdapter extends BaseAdapter {
        Context mContext;
        JSONArray cards;

        StreamAdapter(Context context) {
            mContext = context;
            Cursor mCursor = mContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " ASC");
            try {
                cards = new JSONArray(DatabaseHelper.cursorToString(mCursor));

                Log.d(Aware.TAG, cards.toString(5));

            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        }

        @Override
        public int getViewTypeCount() {
            if (getCount() == 0) return 1;
            return getCount();
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return cards.length();
        }

        @Override
        public Object getItem(int position) {
            try {
                return cards.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            try {
                return cards.getJSONObject(position).getInt("_id");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder cardHolder;
            if (convertView == null) {
                try {
                    PackageInfo pkg = PluginsManager.isInstalled(mContext, cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    if (pkg.versionName.equals("bundled")) {
                        if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.google.auth")) {
                            convertView = new com.aware.plugin.google.auth.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.device_usage")) {
                            convertView = new com.aware.plugin.device_usage.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.ambient_noise")) {
                            convertView = new com.aware.plugin.ambient_noise.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.fitbit")) {
                            convertView = new com.aware.plugin.fitbit.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.google.activity_recognition")) {
                            convertView = new com.aware.plugin.google.activity_recognition.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.google.fused_location")) {
                            convertView = new com.aware.plugin.google.fused_location.ContextCard().getContextCard(mContext);
                        } else if (cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME).equals("com.aware.plugin.openweather")) {
                            convertView = new com.aware.plugin.openweather.ContextCard().getContextCard(mContext);
                        }
                    } else {
                        convertView = Aware.getContextCard(mContext, cards.getJSONObject(position).getString(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    }

                    if (convertView == null) convertView = new View(getApplicationContext());

                    cardHolder = new ViewHolder();
                    cardHolder.card = convertView;

                    convertView.setTag(cardHolder);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                cardHolder = (ViewHolder) convertView.getTag();
                convertView = cardHolder.card;
            }

            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            Cursor mCursor = mContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, Aware_Plugins.PLUGIN_NAME + " ASC");
            try {
                cards = new JSONArray(DatabaseHelper.cursorToString(mCursor));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        }
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
            streamAdapter.notifyDataSetChanged();
        }
    }
}
