package com.aware.utils;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.aware.Aware;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Created by denzil on 17/01/15.
 */
public class DownloadPluginService extends IntentService {

    public DownloadPluginService() { super(Aware.TAG + " Plugin Downloader"); }

    @Override
    protected void onHandleIntent(Intent intent) {

        String package_name = intent.getStringExtra("package_name");
        boolean is_update = intent.getBooleanExtra("is_update", false);

        Log.d(Aware.TAG, "Trying to download: " + package_name);

        HttpResponse response = new Https(this).dataGET("https://api.awareframework.com/index.php/plugins/get_plugin/" + package_name, true);
        if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
            try {
                String input = Https.undoGZIP(response);

                if(input.trim().equalsIgnoreCase("[]")) return;

                JSONObject json_package = new JSONObject(input);

                //Create the folder where all the databases will be stored on external storage
                File folders = new File(Environment.getExternalStorageDirectory()+"/AWARE/plugins/");
                folders.mkdirs();

                String package_url = "http://plugins.awareframework.com/" + json_package.getString("package_path").replace("/uploads/", "") + json_package.getString("package_name");
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(package_url));
                if( ! is_update ) {
                    request.setDescription("Downloading...");
                } else {
                    request.setDescription("Updating...");
                }
                request.setTitle(json_package.getString("title"));
                request.setDestinationInExternalPublicDir("/", "AWARE/plugins/" + json_package.getString("package_name"));

                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                Aware.AWARE_PLUGIN_DOWNLOAD_IDS.add(manager.enqueue(request));
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
