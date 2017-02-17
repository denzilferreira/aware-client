package com.aware.phone.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.R;
import com.aware.providers.Aware_Provider;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.PluginsManager;
import com.aware.utils.StudyUtils;
import com.aware.utils.WebserviceHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Aware_Join_Study extends Aware_Activity {

    private ArrayList<PluginInfo> active_plugins;

    private RecyclerView pluginsRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private boolean pluginsInstalled;
    private Button btnAction, btnQuit;
    private LinearLayout llPluginsRequired;

    public static final String EXTRA_STUDY_URL = "study_url";

    private static String study_url;
    private JSONArray study_configs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.aware_join_study);

        pluginsInstalled = false;

        TextView txtStudyTitle = (TextView) findViewById(R.id.txt_title);
        TextView txtStudyDescription = (TextView) findViewById(R.id.txt_description);
        TextView txtStudyResearcher = (TextView) findViewById(R.id.txt_researcher);
        btnAction = (Button) findViewById(R.id.btn_sign_up);
        btnQuit = (Button) findViewById(R.id.btn_quit_study);

        pluginsRecyclerView = (RecyclerView) findViewById(R.id.rv_plugins);
        mLayoutManager = new LinearLayoutManager(this);
        pluginsRecyclerView.setLayoutManager(mLayoutManager);

        llPluginsRequired = (LinearLayout) findViewById(R.id.ll_plugins_required);

        study_url = getIntent().getStringExtra(EXTRA_STUDY_URL);

        Cursor qry = Aware.getStudy(this, study_url);
        if (qry == null || !qry.moveToFirst()) {
            Toast.makeText(Aware_Join_Study.this, "Error getting study information.", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (qry != null && qry.moveToFirst()) {
            try {
                study_configs = new JSONArray(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                txtStudyTitle.setText(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                txtStudyDescription.setText(Html.fromHtml(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)), null, null));
                txtStudyResearcher.setText(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (qry != null && !qry.isClosed()) qry.close();

        if (study_configs != null) {
            populateStudyInfo(study_configs);
        }

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Toast.makeText(Aware_Join_Study.this, "Joining study!", Toast.LENGTH_LONG).show();

                btnAction.setEnabled(false);
                btnAction.setAlpha(0.5f);

                Cursor study = Aware.getStudy(getApplicationContext(), study_url);
                if (study != null && study.moveToFirst()) {
                    ContentValues studyData = new ContentValues();
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                    getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, studyData, Aware_Provider.Aware_Studies.STUDY_ID + "=" + study.getInt(study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_ID)), null);
                }
                if (study != null && !study.isClosed()) study.close();

                StudyUtils.applySettings(getApplicationContext(), study_configs);
                finish();
            }
        });

        btnQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //User is trying to exit the study he was enrolled.
                Cursor dbStudy = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                if (dbStudy != null && dbStudy.moveToFirst()) {
                    ContentValues complianceEntry = new ContentValues();
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "attempt to quit study");

                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                }
                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                //Sync immediately just this record, skipping all others
                Intent webserviceHelper = new Intent(getApplicationContext(), WebserviceHelper.class);
                webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, Aware_Provider.DATABASE_TABLES[3]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, Aware_Provider.TABLES_FIELDS[3]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, Aware_Provider.Aware_Studies.CONTENT_URI.toString());
                startService(webserviceHelper);

                new AlertDialog.Builder(Aware_Join_Study.this)
                        .setMessage("Are you sure you want to quit the study?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                btnQuit.setEnabled(false);
                                btnQuit.setAlpha(0.5f);

                                Cursor dbStudy = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                                if (dbStudy != null && dbStudy.moveToFirst()) {
                                    ContentValues complianceEntry = new ContentValues();
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "quit study");

                                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                                }
                                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                                //Sync immediately just this record, skipping all others
                                Intent webserviceHelper = new Intent(getApplicationContext(), WebserviceHelper.class);
                                webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                                webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, Aware_Provider.DATABASE_TABLES[3]);
                                webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, Aware_Provider.TABLES_FIELDS[3]);
                                webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, Aware_Provider.Aware_Studies.CONTENT_URI.toString());
                                startService(webserviceHelper);

                                Aware.reset(getApplicationContext());
                                dialogInterface.dismiss();

                                Intent preferences = new Intent(getApplicationContext(), com.aware.phone.Aware_Client.class);
                                preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(preferences);

                                finish();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();

                                Cursor dbStudy = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                                if (dbStudy != null && dbStudy.moveToFirst()) {
                                    ContentValues complianceEntry = new ContentValues();
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, 0); //still on study
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "canceled quit");

                                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                                }
                                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();
                            }
                        })
                        .show();
            }
        });

        IntentFilter pluginStatuses = new IntentFilter();
        pluginStatuses.addAction(Aware.ACTION_AWARE_PLUGIN_INSTALLED);
        pluginStatuses.addAction(Aware.ACTION_AWARE_PLUGIN_UNINSTALLED);
        registerReceiver(pluginCompliance, pluginStatuses);
    }

    private static PluginCompliance pluginCompliance = new PluginCompliance();

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        
    }

    public static class PluginCompliance extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Aware.ACTION_AWARE_PLUGIN_INSTALLED)) {
                Intent joinStudy = new Intent(context, Aware_Join_Study.class);
                joinStudy.putExtra(EXTRA_STUDY_URL, study_url);
                context.startActivity(joinStudy);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pluginCompliance != null) {
            unregisterReceiver(pluginCompliance);
        }
    }

    private void populateStudyInfo(JSONArray study_config) {

        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();

        for (int i = 0; i < study_config.length(); i++) {
            try {
                JSONObject element = study_config.getJSONObject(i);
                if (element.has("plugins")) {
                    plugins = element.getJSONArray("plugins");
                }
                if (element.has("sensors")) {
                    sensors = element.getJSONArray("sensors");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Show the plugins' information
        active_plugins = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);
                String package_name = plugin_config.getString("plugin");

                PackageInfo installed = PluginsManager.isInstalled(this, package_name);
                if (installed == null) {
                    active_plugins.add(new PluginInfo(package_name, package_name, false));
                } else {
                    active_plugins.add(new PluginInfo(PluginsManager.getPluginName(getApplicationContext(), package_name), package_name, true));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mAdapter = new PluginsAdapter(active_plugins);
        pluginsRecyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (active_plugins.size() == 0) {
            pluginsInstalled = true;
            llPluginsRequired.setVisibility(View.GONE);
        } else {
            pluginsInstalled = verifyInstalledPlugins();
            llPluginsRequired.setVisibility(View.VISIBLE);
        }
        if (pluginsInstalled) {
            btnAction.setAlpha(1f);
            btnAction.setEnabled(true);
        } else {
            btnAction.setEnabled(false);
            btnAction.setAlpha(.3f);
        }

        if (Aware.isStudy(getApplicationContext())) {
            btnQuit.setVisibility(View.VISIBLE);
            btnAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
            btnAction.setText("OK");
        } else {
            btnQuit.setVisibility(View.GONE);
        }
    }

    private boolean verifyInstalledPlugins() {
        boolean result = true;
        for (PluginInfo plugin : active_plugins) {
            PackageInfo installed = PluginsManager.isInstalled(this, plugin.packageName);
            if (installed != null) {
                plugin.installed = true;
            } else {
                plugin.installed = false;
                result = false;
            }
        }
        mAdapter.notifyDataSetChanged();
        return result;
    }

    public class PluginsAdapter extends RecyclerView.Adapter<PluginsAdapter.ViewHolder> {
        private ArrayList<PluginInfo> mDataset;

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView txtPackageName;
            public Button btnInstall;
            public CheckBox cbInstalled;

            public ViewHolder(View v) {
                super(v);
                txtPackageName = (TextView) v.findViewById(R.id.txt_package_name);
                btnInstall = (Button) v.findViewById(R.id.btn_install);
                cbInstalled = (CheckBox) v.findViewById(R.id.cb_installed);
            }
        }

        public PluginsAdapter(ArrayList<PluginInfo> myDataset) {
            mDataset = myDataset;
        }

        @Override
        public PluginsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.plugins_installation_list_item, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            holder.txtPackageName.setText(mDataset.get(position).pluginName);
            holder.btnInstall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(Aware_Join_Study.this, "Installing...", Toast.LENGTH_SHORT).show();
                    Aware.downloadPlugin(getApplicationContext(), mDataset.get(position).packageName, study_url, false);
                }
            });
            if (mDataset.get(position).installed) {
                holder.btnInstall.setVisibility(View.INVISIBLE);
                holder.cbInstalled.setVisibility(View.VISIBLE);
            } else {
                holder.btnInstall.setVisibility(View.VISIBLE);
                holder.cbInstalled.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    public class PluginInfo {
        public String pluginName;
        public String packageName;
        public boolean installed;

        public PluginInfo(String pluginName, String packageName, boolean installed) {
            this.pluginName = pluginName;
            this.packageName = packageName;
            this.installed = installed;
        }
    }
}
