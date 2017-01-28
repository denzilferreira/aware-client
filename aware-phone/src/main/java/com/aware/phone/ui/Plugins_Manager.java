
package com.aware.phone.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import com.aware.phone.R;
import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.DatabaseHelper;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.PluginsManager;
import com.aware.utils.SSLManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * UI to manage installed plugins.
 *
 * @author denzil
 */
public class Plugins_Manager extends Aware_Activity {

    private static LayoutInflater inflater;
    private GridView store_grid;
    private PluginsAdapter pluginsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.plugins_store_ui);

        inflater = getLayoutInflater();
        store_grid = (GridView) findViewById(R.id.plugins_store_grid);
        pluginsAdapter = new PluginsAdapter(this);
        store_grid.setAdapter(pluginsAdapter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO);
        registerReceiver(plugins_listener, filter);
    }

    //Monitors for external changes in plugin's states and refresh the UI
    private Plugins_Listener plugins_listener = new Plugins_Listener();

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    public class Plugins_Listener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)) {
                pluginsAdapter.notifyDataSetChanged();
            }
        }
    }

    private AlertDialog.Builder getPluginInfoDialog(String name, String version, String description, String developer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(Plugins_Manager.this);
        View plugin_info_view = inflater.inflate(R.layout.plugins_store_pkg_detail, null);
        TextView plugin_name = (TextView) plugin_info_view.findViewById(R.id.plugin_name);
        TextView plugin_version = (TextView) plugin_info_view.findViewById(R.id.plugin_version);
        TextView plugin_description = (TextView) plugin_info_view.findViewById(R.id.plugin_description);
        TextView plugin_developer = (TextView) plugin_info_view.findViewById(R.id.plugin_developer);

        plugin_name.setText(name);
        plugin_version.setText("Version: " + version);
        plugin_description.setText(description);
        plugin_developer.setText("Developer: " + developer);
        builder.setView(plugin_info_view);

        return builder;
    }

    private class PluginsAdapter extends BaseAdapter {

        private Context mContext;
        private JSONArray plugins;

        PluginsAdapter(Context context) {
            mContext = context;
            Cursor installed_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
            try {
                plugins = new JSONArray(DatabaseHelper.cursorToString(installed_plugins));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (installed_plugins != null && !installed_plugins.isClosed())
                installed_plugins.close();
        }

        @Override
        public int getCount() {
            return plugins.length();
        }

        @Override
        public Object getItem(int position) {
            try {
                return plugins.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            try {
                return plugins.getJSONObject(position).getInt("_id");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.plugins_store_pkg_list_item, parent, false);
            }

            JSONObject plugin = (JSONObject) getItem(position);

            try {
                final String package_name = plugin.getString(Aware_Plugins.PLUGIN_PACKAGE_NAME);
                final String name = plugin.getString(Aware_Plugins.PLUGIN_NAME);
                final String description = plugin.getString(Aware_Plugins.PLUGIN_DESCRIPTION);
                final String developer = plugin.getString(Aware_Plugins.PLUGIN_AUTHOR);
                final String version = plugin.getString(Aware_Plugins.PLUGIN_VERSION);

                int status = plugin.getInt(Aware_Plugins.PLUGIN_STATUS);
                byte[] icon = null;

                if (plugin.optString(Aware_Plugins.PLUGIN_ICON, "").length() > 0)
                    icon = Base64.decode(plugin.getString(Aware_Plugins.PLUGIN_ICON), Base64.DEFAULT);

                final ImageView pkg_icon = (ImageView) convertView.findViewById(R.id.pkg_icon);
                final TextView pkg_title = (TextView) convertView.findViewById(R.id.pkg_title);
                final ImageView pkg_state = (ImageView) convertView.findViewById(R.id.pkg_state);

                if (status != PluginsManager.PLUGIN_NOT_INSTALLED) {
                    PackageInfo pkg = PluginsManager.isInstalled(getApplicationContext(), package_name);
                    if (pkg != null && pkg.versionName.equals("bundled")) {
                        pkg_icon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_launcher_aware));
                    } else {
                        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(package_name, PackageManager.GET_META_DATA);
                        pkg_icon.setImageDrawable(appInfo.loadIcon(getPackageManager()));
                    }
                } else {
                    if (icon != null)
                        pkg_icon.setImageBitmap(BitmapFactory.decodeByteArray(icon, 0, icon.length));
                }

                pkg_title.setText(name);

                switch (status) {
                    case PluginsManager.PLUGIN_DISABLED:
                        pkg_state.setVisibility(View.INVISIBLE);
                        convertView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            String bundled_package = "";
                                            PackageInfo pkg = PluginsManager.isInstalled(getApplicationContext(), package_name);
                                            if (pkg != null && pkg.versionName.equals("bundled")) {
                                                bundled_package = getApplicationContext().getPackageName();
                                            }

                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(((bundled_package.length() > 0) ? bundled_package : package_name), package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.startPlugin(getApplicationContext(), package_name);
                                        notifyDataSetChanged();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PluginsManager.PLUGIN_ACTIVE:
                        pkg_state.setImageResource(R.drawable.ic_pkg_active);
                        convertView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            String bundled_package = "";
                                            PackageInfo pkg = PluginsManager.isInstalled(getApplicationContext(), package_name);
                                            if (pkg != null && pkg.versionName.equals("bundled")) {
                                                bundled_package = getApplicationContext().getPackageName();
                                            }

                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(((bundled_package.length() > 0) ? bundled_package : package_name), package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        notifyDataSetChanged();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PluginsManager.PLUGIN_UPDATED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_updated);
                        convertView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            String bundled_package = "";
                                            PackageInfo pkg = PluginsManager.isInstalled(getApplicationContext(), package_name);
                                            if (pkg != null && pkg.versionName.equals("bundled")) {
                                                bundled_package = getApplicationContext().getPackageName();
                                            }

                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(((bundled_package.length() > 0) ? bundled_package : package_name), package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setNeutralButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        notifyDataSetChanged();
                                    }
                                });
                                builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        pkg_title.setText("Updating...");
                                        Aware.downloadPlugin(getApplicationContext(), package_name, true);
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PluginsManager.PLUGIN_NOT_INSTALLED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_download);
                        convertView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                                builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        if (!Aware.is_watch(getApplicationContext())) {
                                            Toast.makeText(getApplicationContext(), "Downloading... please wait.", Toast.LENGTH_SHORT).show();
                                            Aware.downloadPlugin(getApplicationContext(), package_name, false);
                                        } else {
                                            Toast.makeText(getApplicationContext(), "Please, use the phone to install plugins.", Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                }
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            Cursor installed_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
            try {
                plugins = new JSONArray(DatabaseHelper.cursorToString(installed_plugins));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (installed_plugins != null && !installed_plugins.isClosed())
                installed_plugins.close();

            store_grid.setAdapter(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pluginsAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(plugins_listener);
    }

    /**
     * Checks for changes on the server side and updates database.
     * If changes were detected, result is true and a refresh of UI is requested.
     *
     * @author denzil
     */
//    public class Async_PluginUpdater extends AsyncTask<Void, View, Boolean> {
//
//        boolean needsRefresh = false;
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//
//            //First, clean plugins on the database if they are not installed on the device anymore
//            ArrayList<String> to_clean = new ArrayList<>();
//            Cursor on_database = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, null);
//            if (on_database != null && on_database.moveToFirst()) {
//                do {
//                    String package_name = on_database.getString(on_database.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
//                    PackageInfo installed = PluginsManager.isInstalled(getApplicationContext(), package_name);
//
//                    //Clean
//                    if (installed == null) {
//                        to_clean.add(package_name);
//                    }
//                } while (on_database.moveToNext());
//            }
//            if (on_database != null && !on_database.isClosed()) on_database.close();
//
//            if (to_clean.size() > 0) {
//                String to_clean_txt = "";
//                for (int i = 0; i < to_clean.size(); i++) {
//                    to_clean_txt += "'" + to_clean.get(i) + "',";
//                }
//                to_clean_txt = to_clean_txt.substring(0, to_clean_txt.length() - 1);
//                getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " in (" + to_clean_txt + ")", null);
//
//                needsRefresh = true;
//            }
//        }
//
//        @Override
//        protected Boolean doInBackground(Void... params) {
//            JSONArray plugins = null;
//
//            String study_url = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);
//            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
//            String protocol = study_url.substring(0, study_url.indexOf(":"));
//
//            int study_key = 0;
//            Cursor studyInfo = Aware.getStudy(getApplicationContext(), study_url);
//            if (studyInfo != null && studyInfo.moveToFirst()) {
//                study_key = studyInfo.getInt(studyInfo.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY));
//            }
//            if (studyInfo != null && !studyInfo.isClosed()) studyInfo.close();
//
//            //Check for updates on the server side
//            String response;
//            if (protocol.equals("https")) {
//                try {
//                    response = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataGET(study_host + "/index.php/plugins/get_plugins" + ((study_key > 0) ? "/" + study_key : ""), true);
//                } catch (FileNotFoundException e) {
//                    response = null;
//                }
//            } else {
//                response = new Http(getApplicationContext()).dataGET(study_host + "/index.php/plugins/get_plugins" + ((study_key > 0) ? "/" + study_key : ""), true);
//            }
//
//            if (response != null) {
//                try {
//                    plugins = new JSONArray(response);
//
//                    for (int i = 0; i < plugins.length(); i++) {
//                        JSONObject plugin = plugins.getJSONObject(i);
//                        PackageInfo installed = PluginsManager.isInstalled(getApplicationContext(), plugin.getString("package"));
//                        if (installed != null) {
//                            //Installed, lets check if it is updated
//                            if (plugin.getString("version").compareTo(installed.versionName) != 0) {
//                                ContentValues data = new ContentValues();
//                                data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
//                                data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getString("version"));
//                                data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
//                                data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
//                                data.put(Aware_Plugins.PLUGIN_STATUS, PluginsManager.PLUGIN_UPDATED);
//                                getContentResolver().update(Aware_Plugins.CONTENT_URI, data, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + installed.packageName + "'", null);
//                                needsRefresh = true;
//                            }
//                        } else {
//                            //this is a new plugin available on the server
//                            ContentValues data = new ContentValues();
//                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
//                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
//                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getString("version"));
//                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
//                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
//                            data.put(Aware_Plugins.PLUGIN_STATUS, PluginsManager.PLUGIN_NOT_INSTALLED);
//                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
//                            needsRefresh = true;
//                        }
//                    }
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            //Restore: not on local database, but installed
//            ArrayList<PackageInfo> installed_plugins = PluginsManager.getInstalledPlugins(getApplicationContext());
//            for (PackageInfo pkg : installed_plugins) {
//                //Installed on device, but not on AWARE's database
//                if (!PluginsManager.isLocal(getApplicationContext(), pkg.packageName)) {
//                    try {
//                        if (PluginsManager.isOnServerRepository(plugins, pkg.packageName)) {
//                            JSONObject plugin = PluginsManager.getPluginOnlineInfo(plugins, pkg.packageName);
//
//                            ContentValues data = new ContentValues();
//                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
//                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
//                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getString("version"));
//                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
//                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
//                            data.put(Aware_Plugins.PLUGIN_STATUS, PluginsManager.PLUGIN_DISABLED);
//
//                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
//                            needsRefresh = true;
//                        } else {
//                            //Users' own plugins, add them back to the list
//                            ContentValues data = new ContentValues();
//                            data.put(Aware_Plugins.PLUGIN_NAME, pkg.applicationInfo.loadLabel(getPackageManager()).toString());
//                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, "Your plugin!");
//                            data.put(Aware_Plugins.PLUGIN_VERSION, pkg.versionName);
//                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, pkg.packageName);
//                            data.put(Aware_Plugins.PLUGIN_AUTHOR, "You");
//                            data.put(Aware_Plugins.PLUGIN_STATUS, PluginsManager.PLUGIN_DISABLED);
//                            data.put(Aware_Plugins.PLUGIN_ICON, PluginsManager.getPluginIcon(getApplicationContext(), pkg.packageName));
//
//                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
//                            needsRefresh = true;
//                        }
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            return needsRefresh;
//        }
//
//        @Override
//        protected void onPostExecute(Boolean refresh) {
//            super.onPostExecute(refresh);
//            if (swipeToRefresh != null) {
//                swipeToRefresh.setRefreshing(false);
//            }
//            if (refresh) pluginsAdapter.notifyDataSetChanged();
//        }
//    }
}
