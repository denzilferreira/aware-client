
package com.aware.phone.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import com.aware.phone.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Http;
import com.aware.utils.Https;
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

    /**
     * Plugin installed but disabled
     */
    public static final int PLUGIN_DISABLED = 0;

    /**
     * Plugin installed and active
     */
    public static final int PLUGIN_ACTIVE = 1;

    /**
     * Plugin installed but there is a new version on the server
     */
    public static final int PLUGIN_UPDATED = 2;

    /**
     * Plugin not installed but available on the server
     */
    public static final int PLUGIN_NOT_INSTALLED = 3;

    private static LayoutInflater inflater;
    private static GridView store_grid;
    private static SwipeRefreshLayout swipeToRefresh;

    private static Cursor installed_plugins;
    private static PluginAdapter pluginAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.plugins_store_ui);

        inflater = getLayoutInflater();
        store_grid = (GridView) findViewById(R.id.plugins_store_grid);

        swipeToRefresh = (SwipeRefreshLayout) findViewById(R.id.refresh_plugins);
        swipeToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Async_PluginUpdater().execute();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO);
        registerReceiver(plugins_listener, filter);
    }

    //Monitors for external changes in plugin's states and refresh the UI
    private Plugins_Listener plugins_listener = new Plugins_Listener();

    public class Plugins_Listener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO)) {
                //Update grid
                updateGrid();
            }
        }
    }

    private void updateGrid() {
        installed_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
        pluginAdapter = new PluginAdapter(getApplicationContext(), installed_plugins, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        store_grid.setAdapter(pluginAdapter);
    }

    public class PluginAdapter extends CursorAdapter {
        public PluginAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.plugins_store_pkg_list_item, parent, false);
        }

        @Override
        public void bindView(final View pkg_view, Context context, Cursor cursor) {
            final String package_name = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
            final String name = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_NAME));
            final String description = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_DESCRIPTION));
            final String developer = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_AUTHOR));
            final String version = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_VERSION));
            final int status = cursor.getInt(cursor.getColumnIndex(Aware_Plugins.PLUGIN_STATUS));
            final byte[] icon = cursor.getBlob(cursor.getColumnIndex(Aware_Plugins.PLUGIN_ICON));
            final ImageView pkg_icon = (ImageView) pkg_view.findViewById(R.id.pkg_icon);
            final TextView pkg_title = (TextView) pkg_view.findViewById(R.id.pkg_title);
            final ImageView pkg_state = (ImageView) pkg_view.findViewById(R.id.pkg_state);

            try {
                if (status != PLUGIN_NOT_INSTALLED) {
                    ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(package_name, PackageManager.GET_META_DATA);
                    pkg_icon.setImageDrawable(appInfo.loadIcon(getPackageManager()));
                } else {
                    if (icon != null && icon.length > 0)
                        pkg_icon.setImageBitmap(BitmapFactory.decodeByteArray(icon, 0, icon.length));
                }

                pkg_title.setText(name);

                switch (status) {
                    case PLUGIN_DISABLED:
                        pkg_state.setVisibility(View.INVISIBLE);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(package_name, package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.startPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_ACTIVE:
                        pkg_state.setImageResource(R.drawable.ic_pkg_active);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(package_name, package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_UPDATED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_updated);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (Aware.isClassAvailable(getApplicationContext(), package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setComponent(new ComponentName(package_name, package_name + ".Settings"));
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setNeutralButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        pkg_view.setAlpha(0.5f);
                                        pkg_title.setText("Updating...");
                                        pkg_view.setOnClickListener(null);
                                        Aware.downloadPlugin(getApplicationContext(), package_name, true);
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_NOT_INSTALLED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_download);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
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
                pkg_view.refreshDrawableState();
            } catch (NameNotFoundException e) {
                e.printStackTrace();
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

    @Override
    protected void onResume() {
        super.onResume();
        updateGrid();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        //Fixed: leak when leaving plugin manager
        if (installed_plugins != null && !installed_plugins.isClosed()) installed_plugins.close();
        pluginAdapter.changeCursor(null);
    }

    @Override
    protected void onPause() {
        super.onPause();

        //Fixed: leak when leaving plugin manager
        if (installed_plugins != null && !installed_plugins.isClosed()) installed_plugins.close();
        pluginAdapter.changeCursor(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(plugins_listener);
    }

    /**
     * Downloads and compresses image for optimized icon caching
     *
     * @param image_url
     * @return
     */
    public static byte[] cacheImage(String image_url, Context sContext) {

        String study_url = Aware.getSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER);
        String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
        String protocol = study_url.substring(0, study_url.indexOf(":"));

        image_url = study_host + image_url;

        if (protocol.equals("https")) {
            try {
                //Load SSL public certificate so we can talk with server
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                InputStream caInput = SSLManager.getHTTPS(sContext, study_url);
                Certificate ca = cf.generateCertificate(caInput);
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null); //initialize as empty keystore
                keyStore.setCertificateEntry("ca", ca); //add our certificate to keystore

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores

                //Initialize a SSL connection context
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

                //Fix for known-bug on <= JellyBean (4.x)
                System.setProperty("http.keepAlive", "false");

                //Fetch image now that we recognise SSL
                URL image_path = new URL(image_url);
                HttpsURLConnection image_connection = (HttpsURLConnection) image_path.openConnection();
                image_connection.setSSLSocketFactory(sslContext.getSocketFactory());

                InputStream in_stream = image_connection.getInputStream();
                Bitmap tmpBitmap = BitmapFactory.decodeStream(in_stream);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                tmpBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);

                return output.toByteArray();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        } else {
            try {
                URL image_path = new URL(image_url);
                HttpURLConnection image_connection = (HttpURLConnection) image_path.openConnection();
                InputStream in_stream = image_connection.getInputStream();
                Bitmap tmpBitmap = BitmapFactory.decodeStream(in_stream);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                tmpBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                return output.toByteArray();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Checks if a plugin is installed on the device
     *
     * @param context
     * @param package_name
     * @return
     */
    public static PackageInfo isInstalled(Context context, String package_name) {
        PackageManager pkgManager = context.getPackageManager();
        List<PackageInfo> packages = pkgManager.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : packages) {
            if (pkg.packageName.equals(package_name)) return pkg;
        }
        return null;
    }

    /**
     * Check if plugin is on AWARE's database already
     *
     * @param context
     * @param pkg_name
     * @return
     */
    private static boolean isLocal(Context context, String pkg_name) {
        boolean is_local = false;
        Cursor in_db = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null, null);
        if (in_db != null && in_db.getCount() > 0) {
            is_local = true;
        }
        if (in_db != null && !in_db.isClosed()) in_db.close();
        return is_local;
    }

    /**
     * Get a list of all plugins' PackageInfo installed on the device
     *
     * @param c
     * @return
     */
    public static ArrayList<PackageInfo> getInstalledPlugins(Context c) {
        ArrayList<PackageInfo> installed = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<PackageInfo> all_packages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : all_packages) {
            if (pkg.packageName.matches("com.aware.plugin.*")) installed.add(pkg);
        }
        return installed;
    }

    /**
     * Returns the currently installed plugin's version
     *
     * @param context
     * @param package_name
     * @return
     */
    public static int getVersion(Context context, String package_name) {
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(package_name, PackageManager.GET_META_DATA);
            return pkgInfo.versionCode;
        } catch (NameNotFoundException e) {
            if (Aware.DEBUG) Log.d(Aware.TAG, e.getMessage());
        }
        return 0;
    }

    private JSONObject getPluginOnlineInfo(JSONArray server_pkgs, String package_name) {
        try {
            for (int i = 0; i < server_pkgs.length(); i++) {
                JSONObject pkg = server_pkgs.getJSONObject(i);
                if (pkg.getString("package").equalsIgnoreCase(package_name))
                    return pkg;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Get a plugins' icon as byte[] for database storage
    public static byte[] getPluginIcon(Context c, PackageInfo pkg) {
        Drawable d = pkg.applicationInfo.loadIcon(c.getPackageManager());
        Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    private boolean isOnServerRepository(JSONArray server_plugins, String local_package) throws JSONException {
        if (server_plugins == null) return false;
        for (int i = 0; i < server_plugins.length(); i++) {
            JSONObject server_pkg = server_plugins.getJSONObject(i);
            if (server_pkg.getString("package").equals(local_package)) return true;
        }
        return false;
    }

    /**
     * Checks for changes on the server side and updates database.
     * If changes were detected, result is true and a refresh of UI is requested.
     *
     * @author denzil
     */
    public class Async_PluginUpdater extends AsyncTask<Void, View, Boolean> {

        boolean needsRefresh = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            //First, clean plugins on the database if they are not installed on the device anymore
            ArrayList<String> to_clean = new ArrayList<>();
            Cursor on_database = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, null);
            if (on_database != null && on_database.moveToFirst()) {
                do {
                    String package_name = on_database.getString(on_database.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    PackageInfo installed = isInstalled(getApplicationContext(), package_name);

                    //Clean
                    if (installed == null) {
                        to_clean.add(package_name);
                    }
                } while (on_database.moveToNext());
            }
            if (on_database != null && !on_database.isClosed()) on_database.close();

            if (to_clean.size() > 0) {
                String to_clean_txt = "";
                for (int i = 0; i < to_clean.size(); i++) {
                    to_clean_txt += "'" + to_clean.get(i) + "',";
                }
                to_clean_txt = to_clean_txt.substring(0, to_clean_txt.length() - 1);
                getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " in (" + to_clean_txt + ")", null);

                needsRefresh = true;
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            JSONArray plugins = null;

            String study_url = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);
            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
            String protocol = study_url.substring(0, study_url.indexOf(":"));

            //Check for updates on the server side
            String response;
            if (protocol.equals("https")) {
                try {
                    response = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataGET(study_host + "/index.php/plugins/get_plugins" + ((Aware.getSetting(getApplicationContext(), "study_id").length() > 0) ? "/" + Aware.getSetting(getApplicationContext(), "study_id") : ""), true);
                } catch (FileNotFoundException e) {
                    response = null;
                }
            } else {
                response = new Http(getApplicationContext()).dataGET(study_host + "/index.php/plugins/get_plugins" + ((Aware.getSetting(getApplicationContext(), "study_id").length() > 0) ? "/" + Aware.getSetting(getApplicationContext(), "study_id") : ""), true);
            }

            if (response != null) {
                try {
                    plugins = new JSONArray(response);
                    for (int i = 0; i < plugins.length(); i++) {
                        JSONObject plugin = plugins.getJSONObject(i);
                        PackageInfo installed = isInstalled(getApplicationContext(), plugin.getString("package"));
                        if (installed != null) {
                            //Installed, lets check if it is updated
                            if (plugin.getInt("version") > installed.versionCode) {
                                ContentValues data = new ContentValues();
                                data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                                data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                                data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                                data.put(Aware_Plugins.PLUGIN_ICON, !Aware.is_watch(getApplicationContext()) ? cacheImage(plugin.getString("iconpath"), getApplicationContext()) : null);
                                data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_UPDATED);
                                getContentResolver().update(Aware_Plugins.CONTENT_URI, data, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + installed.packageName + "'", null);
                                needsRefresh = true;
                            }
                        } else {
                            //this is a new plugin available on the server
                            ContentValues data = new ContentValues();
                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getInt("version"));
                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                            data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_NOT_INSTALLED);
                            data.put(Aware_Plugins.PLUGIN_ICON, !Aware.is_watch(getApplicationContext()) ? cacheImage(plugin.getString("iconpath"), getApplicationContext()) : null);
                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
                            needsRefresh = true;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //Restore: not on local database, but installed
            ArrayList<PackageInfo> installed_plugins = getInstalledPlugins(getApplicationContext());
            for (PackageInfo pkg : installed_plugins) {
                //Installed on device, but not on AWARE's database
                if (!isLocal(getApplicationContext(), pkg.packageName)) {
                    try {
                        if (isOnServerRepository(plugins, pkg.packageName)) {
                            JSONObject plugin = getPluginOnlineInfo(plugins, pkg.packageName);

                            ContentValues data = new ContentValues();
                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getInt("version"));
                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                            data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_DISABLED);
                            data.put(Aware_Plugins.PLUGIN_ICON, !Aware.is_watch(getApplicationContext()) ? cacheImage(plugin.getString("iconpath"), getApplicationContext()) : null);

                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
                            needsRefresh = true;
                        } else {
                            //Users' own plugins, add them back to the list
                            ContentValues data = new ContentValues();
                            data.put(Aware_Plugins.PLUGIN_NAME, pkg.applicationInfo.loadLabel(getPackageManager()).toString());
                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, "Your plugin!");
                            data.put(Aware_Plugins.PLUGIN_VERSION, pkg.versionCode);
                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, pkg.packageName);
                            data.put(Aware_Plugins.PLUGIN_AUTHOR, "You");
                            data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_DISABLED);
                            data.put(Aware_Plugins.PLUGIN_ICON, getPluginIcon(getApplicationContext(), pkg));

                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
                            needsRefresh = true;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            return needsRefresh;
        }

        @Override
        protected void onPostExecute(Boolean refresh) {
            super.onPostExecute(refresh);
            if (swipeToRefresh != null) {
                swipeToRefresh.setRefreshing(false);
            }
            if (refresh) updateGrid();
        }
    }
}
