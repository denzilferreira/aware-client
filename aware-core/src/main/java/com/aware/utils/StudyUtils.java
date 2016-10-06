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
import android.content.pm.PackageManager;
import android.net.Uri;
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

    // Toast upon joining, must save to dismiss onDestroy.
    private static Toast JOIN_TOAST;

    public StudyUtils() {
        super("StudyUtils Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String full_url = intent.getStringExtra(EXTRA_JOIN_STUDY);

        if (Aware.DEBUG) Log.d(Aware.TAG, "Joining: " + full_url);

        Uri study_uri = Uri.parse(full_url);
        // New study URL, chopping off query parameters.
        String study_url = study_uri.getScheme()+"://"+study_uri.getHost()+study_uri.getPath();
        String protocol = study_uri.getScheme();

        //Request study settings
        Hashtable<String, String> data = new Hashtable<>();
        data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put("platform", "android");
        try {
            PackageInfo package_info = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            data.put("package_name", package_info.packageName);
            data.put("package_version_code", String.valueOf(package_info.versionCode));
            data.put("package_version_name", String.valueOf(package_info.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(Aware.TAG, "Failed to put package info: " + e);
            e.printStackTrace();
        }

        String answer;
        if (protocol.equals("https")) {
            // Get SSL certs.  Block since we are already in background.
            SSLManager.handleUrl(getApplicationContext(), full_url, true);

            try {
                answer = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataPOST(study_url, data, true);
            } catch (FileNotFoundException e) {
                answer = null;
            }
        } else {
            answer = new Http(getApplicationContext()).dataPOST(study_url, data, true);
        }

        if (answer == null) {
            JOIN_TOAST = Toast.makeText(getApplicationContext(), "Failed to connect to server... try again.", Toast.LENGTH_LONG);
            JOIN_TOAST.show();
            return;
        }

        try {
            JSONArray configs_study = new JSONArray(answer);
            if (configs_study.getJSONObject(0).has("message")) {
                Toast.makeText(getApplicationContext(), "This study is no longer available.", Toast.LENGTH_LONG).show();
                return;
            }

            JOIN_TOAST = Toast.makeText(getApplicationContext(), "Thanks for joining the study!", Toast.LENGTH_LONG);
            JOIN_TOAST.show();

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
    public static void applySettings(Context context, JSONArray configs) {

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

        for(String package_name : active_plugins) {
            PackageInfo installed = PluginsManager.isInstalled(context, package_name);
            if (installed != null) {
                Aware.startPlugin(context, package_name);
            } else {
                //TODO: wizard to install plugins, step by step
                Aware.downloadPlugin(context, package_name, false);
            }
        }

        //Send data to server
        Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        context.sendBroadcast(sync);
    }

    public void onDestroy() {
        // The toast may stay living forever if the service is destroyed before it starts.
        if (JOIN_TOAST != null)
            JOIN_TOAST.cancel();
    }
}
