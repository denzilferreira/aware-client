package com.aware.utils;

/**
 * Created by denzilferreira on 16/02/16.
 */

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Service that allows plugins/applications to send data to AWARE's dashboard study
 */
public class StudyUtils extends IntentService {

    /**
     * Received broadcast to join a study
     */
    public static final String EXTRA_JOIN_STUDY = "study_url";

    public StudyUtils() {
        super("StudyUtils Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String study_url = intent.getStringExtra(EXTRA_JOIN_STUDY);

        if (Aware.DEBUG) Log.d(Aware.TAG, "Joining: " + study_url);

        //Load server SSL certificates
        Intent aware_SSL = new Intent(this, SSLManager.class);
        aware_SSL.putExtra(SSLManager.EXTRA_SERVER, study_url);
        startService(aware_SSL);

        //Request study settings
        Hashtable<String, String> data = new Hashtable<>();
        data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));

        String protocol = study_url.substring(0, study_url.indexOf(":"));
        String answer;
        if (protocol.equals("https")) {
            try {
                answer = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataPOST(study_url, data, true);
            } catch (FileNotFoundException e) {
                answer = null;
            }
        } else {
            answer = new Http(getApplicationContext()).dataPOST(study_url, data, true);
        }

        if (answer == null) {
            Toast.makeText(getApplicationContext(), "Failed to connect to server... try again.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONArray configs_study = new JSONArray(answer);
            if (configs_study.getJSONObject(0).has("message")) {
                Toast.makeText(getApplicationContext(), "This study is no longer available.", Toast.LENGTH_LONG).show();
                return;
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
            mBuilder.setSmallIcon(R.drawable.ic_action_aware_studies);
            mBuilder.setContentTitle("AWARE");
            mBuilder.setContentText("Thanks for joining the study!");
            mBuilder.setAutoCancel(true);

            NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(33, mBuilder.build());

            if (Aware.DEBUG) Log.d(Aware.TAG, "Study configs: " + configs_study.toString(5));

            //Apply new configurations in AWARE Client
            applySettings(getApplicationContext(), configs_study);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     *
     * @param context
     * @param configs
     */
    protected static void applySettings(Context context, JSONArray configs) {

        boolean is_developer = Aware.getSetting(context, Aware_Preferences.DEBUG_FLAG).equals("true");

        //First reset the client to default settings...
        Aware.reset(context);

        if (is_developer) Aware.setSetting(context, Aware_Preferences.DEBUG_FLAG, true);

        //Now apply the new settings
        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();

        for (int i = 0; i < configs.length(); i++) {
            try {
                JSONObject element = configs.getJSONObject(i);
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

        //Set the sensors' settings first
        for (int i = 0; i < sensors.length(); i++) {
            try {
                JSONObject sensor_config = sensors.getJSONObject(i);
                Aware.setSetting(context, sensor_config.getString("setting"), sensor_config.get("value"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Set the plugins' settings now
        ArrayList<String> active_plugins = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);

                String package_name = plugin_config.getString("plugin");
                active_plugins.add(package_name);

                JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                for (int j = 0; j < plugin_settings.length(); j++) {
                    JSONObject plugin_setting = plugin_settings.getJSONObject(j);
                    Aware.setSetting(context, plugin_setting.getString("setting"), plugin_setting.get("value"), package_name);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Now check plugins
        new CheckPlugins(context).execute(active_plugins);

        //Send data to server
        Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        context.sendBroadcast(sync);

//        Intent applyNew = new Intent(Aware.ACTION_AWARE_REFRESH);
//        context.sendBroadcast(applyNew);
    }

    public static class CheckPlugins extends AsyncTask<ArrayList<String>, Void, Void> {
        private Context context;

        public CheckPlugins(Context c) {
            this.context = c;
        }

        @Override
        protected Void doInBackground(ArrayList<String>... params) {

            String study_url = Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER);
            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
            String protocol = study_url.substring(0, study_url.indexOf(":"));

            for (final String package_name : params[0]) {

                String http_request;
                if (protocol.equals("https")) {
                    try {
                        http_request = new Https(context, SSLManager.getHTTPS(context, study_url)).dataGET(study_host + "/index.php/plugins/get_plugin/" + package_name, true);
                    } catch (FileNotFoundException e) {
                        http_request = null;
                    }
                } else {
                    http_request = new Http(context).dataGET(study_host + "/index.php/plugins/get_plugin/" + package_name, true);
                }

                if (http_request != null) {
                    try {
                        if (!http_request.equals("[]")) {
                            JSONObject json_package = new JSONObject(http_request);
                            if (json_package.getInt("version") > PluginsManager.getVersion(context, package_name)) {
                                Aware.downloadPlugin(context, package_name, true); //update the existing plugin
                            } else {
                                PackageInfo installed = PluginsManager.isInstalled(context, package_name);
                                if (installed != null) {
                                    Aware.startPlugin(context, package_name); //start plugin
                                } else {

                                    //We don't have the plugin installed or bundled. Ask to install?
                                    if (Aware.DEBUG)
                                        Log.d(Aware.TAG, package_name + " is not installed yet!");

                                    android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);
                                    builder.setTitle("AWARE")
                                            .setMessage("Install necessary plugin(s)?")
                                            .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Aware.downloadPlugin(context, package_name, false);
                                                }
                                            })
                                            .setNegativeButton("Cancel", null).show();
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }
    }
}
