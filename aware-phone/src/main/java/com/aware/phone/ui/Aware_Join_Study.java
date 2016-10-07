package com.aware.phone.ui;

import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.phone.R;
import com.aware.providers.Aware_Provider;
import com.aware.utils.PluginsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Aware_Join_Study extends Aware_Activity {

    private String study_url;
    private JSONObject study_json;
    private ArrayList<PluginInfo> active_plugins;
    //TODO: Test when the view has many items
    private RecyclerView pluginsRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private boolean pluginsInstalled;
    private Button btnSignUp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.aware_join_study);
        pluginsInstalled = false;
        TextView txtStudyTitle = (TextView) findViewById(R.id.txt_title);
        TextView txtStudyDescription = (TextView) findViewById(R.id.txt_description);
        TextView txtStudyResearcher = (TextView) findViewById(R.id.txt_researcher);
        btnSignUp = (Button) findViewById(R.id.btn_sign_up);

        pluginsRecyclerView = (RecyclerView) findViewById(R.id.rv_plugins);
        mLayoutManager = new LinearLayoutManager(this);
        pluginsRecyclerView.setLayoutManager(mLayoutManager);

        study_url = getIntent().getStringExtra("study_url");
        try {
            study_json = new JSONObject(getIntent().getStringExtra("study_json"));
            txtStudyTitle.setText((study_json.getString("study_name").length() > 0 ? study_json.getString("study_name") : "Not available"));
            txtStudyDescription.setText((study_json.getString("study_description").length() > 0 ? study_json.getString("study_description") : "Not available."));
            txtStudyResearcher.setText("PI: " + study_json.getString("researcher_first") + " " + study_json.getString("researcher_last") + "\nContact: " + study_json.getString("researcher_contact"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JSONObject study_config = null;

        //TODO: To make it compile, change it to the below one when changes to the aware provider are merged
        Cursor qry = this.getContentResolver().query(Aware_Provider.Aware_Plugins.CONTENT_URI, null, null, null, "study_joined DESC LIMIT 1");
        if (qry != null && qry.moveToFirst()) {
            try {
                study_config = new JSONObject(qry.getString(qry.getColumnIndex("study_config")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (qry != null && !qry.isClosed()) qry.close();
        } else {
            //TODO: Error get json get it again?
        }

//        //TODO: Includes future changes
//        Cursor qry = this.getContentResolver().query(Aware_Provider.Aware_Studies.CONTENT_URI, null, null, null, Aware_Provider.Aware_Studies.STUDY_JOINED + " DESC LIMIT 1");
//        if (qry != null && qry.moveToFirst()) {
//            try {
//                study_config = new JSONObject(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//            if (qry != null && !qry.isClosed()) qry.close();
//        } else {
//            //Error get json get it again?
//        }


        if(study_config!=null) {
            populateStudyInfo(study_config);
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

        //Show the sensors' icons
//        for (int i = 0; i < sensors.length(); i++) {
//            try {
//                JSONObject sensor_config = sensors.getJSONObject(i);
//                Aware.setSetting(context, sensor_config.getString("setting"), sensor_config.get("value"));
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }

        //Show the plugins' information
        active_plugins = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);

                String package_name = plugin_config.getString("plugin");


                PackageInfo installed = PluginsManager.isInstalled(this, package_name);
                if (installed != null) {
                    active_plugins.add(new PluginInfo(package_name, package_name, "", true));
                } else {
                    active_plugins.add(new PluginInfo(package_name, package_name, "", false));
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
        pluginsInstalled = verifyInstalledPlugins();
        if(pluginsInstalled) {
            btnSignUp.setEnabled(true);
        } else {
            btnSignUp.setEnabled(false);
        }
    }

    private boolean verifyInstalledPlugins() {
        boolean result = true;
        for(PluginInfo plugin : active_plugins) {
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

    private void downloadPlugin(String package_name) {
        Aware.downloadPlugin(this, package_name, false);
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
        public PluginsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
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
                    //TODO: Test that it works
                    downloadPlugin(mDataset.get(position).packageName);
                }
            });
            if(mDataset.get(position).installed) {
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
        public String img;
        public boolean installed;

        public PluginInfo(String pluginName, String packageName, String img, boolean installed) {
            this.pluginName = pluginName;
            this.packageName = packageName;
            this.img = img;
            this.installed = installed;
        }
    }
}
