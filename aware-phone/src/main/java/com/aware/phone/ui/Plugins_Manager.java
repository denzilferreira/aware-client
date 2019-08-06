
package com.aware.phone.ui;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.content.ContextCompat;
import com.aware.Aware;
import com.aware.phone.R;
import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.DatabaseHelper;
import com.aware.utils.PluginsManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

        restoreBundled();

        pluginsAdapter = new PluginsAdapter(this);
        store_grid.setAdapter(pluginsAdapter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_UPDATE_PLUGINS_INFO);
        registerReceiver(plugins_listener, filter);
    }

    /**
     * For bundle client/standalone apps, this will load back the plugins that packaged into the Plugins_Manager UI
     */
    private void restoreBundled() {
        if (!getApplicationContext().getResources().getBoolean(R.bool.standalone)) return;

        //If we already restored bundled, do nothing
        boolean bundledRestored = false;
        Cursor bundled = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_AUTHOR + " LIKE 'Bundle'", null, null);
        if (bundled!= null && bundled.moveToFirst()) {
            if (bundled.getCount() > 0) {
                bundledRestored = true;
            }
            bundled.close();
        }
        if (bundledRestored) return;

        PackageManager pkgManager = getApplicationContext().getPackageManager();
        try {
            PackageInfo bundle = pkgManager.getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_SERVICES);
            if (bundle.services == null) return;

            StringBuilder pluginsPackagesInstalled = new StringBuilder();
            pluginsPackagesInstalled.append("(");

            for(ServiceInfo serviceInfo : bundle.services) {
                if (serviceInfo.name.contains(".Plugin") || serviceInfo.name.contains(".PluginKt")) {
                    String package_name = serviceInfo.name.subSequence(0, serviceInfo.name.indexOf(".Plugin")).toString();

                    if (pluginsPackagesInstalled.length() > 1)
                        pluginsPackagesInstalled.append(',');

                    pluginsPackagesInstalled.append("'" + package_name + "'");

                    Cursor cached = getContentResolver().query(Aware_Provider.Aware_Plugins.CONTENT_URI, null, Aware_Provider.Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
                    if (cached == null || !cached.moveToFirst()) {
                        ContentValues rowData = new ContentValues();
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_AUTHOR, "Bundle");
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_DESCRIPTION, "Bundled with " + getApplicationContext().getPackageName());
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_NAME, serviceInfo.nonLocalizedLabel.toString());
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_PACKAGE_NAME, package_name);
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
                        rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_VERSION, 1);
                        getContentResolver().insert(Aware_Provider.Aware_Plugins.CONTENT_URI, rowData);
                        if (Aware.DEBUG) Log.d(Aware.TAG, "Added bundled plugin: " + package_name + " to " + getApplicationContext().getPackageName());
                    }
                    if (cached != null && !cached.isClosed()) cached.close();
                }
            }
            pluginsPackagesInstalled.append(")");

        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
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

                if (plugin.optString(Aware_Plugins.PLUGIN_ICON, "").length() > 0) {
                    try {
                        icon = Base64.decode(plugin.getString(Aware_Plugins.PLUGIN_ICON), Base64.DEFAULT);
                    } catch (IllegalArgumentException e) {
                        icon = PluginsManager.getPluginIcon(getApplicationContext(), package_name);
                    }
                }

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
                                        Aware.downloadPlugin(getApplicationContext(), package_name, null, true);
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
                                            Aware.downloadPlugin(getApplicationContext(), package_name, null, false);
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
}
