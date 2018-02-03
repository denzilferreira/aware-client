package com.aware.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dteixeir on 01/02/2018.
 */

public class Jenkins {

    private String TAG = "AWARE:Jenkins";

    /**
     * Creates a Jenkins API client.
     * @param context your application context
     * @param jenkins_job_endpoint as http://jenkins_main_domain/job/JOB_NAME/
     */
    public Jenkins(Context context, String jenkins_job_endpoint, boolean is_gzipped, boolean isHttps) {
        if (!isHttps) {
            Http http = new Http();
            String jsonString = http.dataGET(jenkins_job_endpoint+"/api/json", is_gzipped);
            try {
                JSONObject jenkinsJob = new JSONObject(jsonString);
                Log.d(TAG, jenkinsJob.toString(5));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
