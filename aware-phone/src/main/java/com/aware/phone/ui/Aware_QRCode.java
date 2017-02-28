package com.aware.phone.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Aware_Provider;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Hashtable;
import java.util.List;

import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;

/**
 * Created by denzil on 27/10/15.
 */
public class Aware_QRCode extends Aware_Activity implements ZBarScannerView.ResultHandler {

    private ZBarScannerView mScannerView;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mScannerView = new ZBarScannerView(this);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        ListView list = new ListView(this);
        list.setId(android.R.id.list);
        list.setVisibility(View.GONE);
        main.addView(mScannerView);
        main.addView(list);
        setContentView(main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mScannerView.setResultHandler(this);
        mScannerView.startCamera(-1);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //Zbar QRCode handler
    @Override
    public void handleResult(Result result) {
        new StudyData().execute(result.getContents());
    }

    /**
     * Fetch study information and ask user to join the study
     */
    private class StudyData extends AsyncTask<String, Void, JSONObject> {

        private ProgressDialog loader;

        private String study_url = "";
        private String study_api_key = "";
        private String study_id = "";
        private String study_config = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loader = new ProgressDialog(Aware_QRCode.this);
            loader.setTitle("Loading study");
            loader.setMessage("Please wait...");
            loader.setCancelable(false);
            loader.setIndeterminate(true);
            loader.show();
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            study_url = params[0];

            if (Aware.DEBUG) Log.d(Aware.TAG, "Aware_QRCode study_url: " + study_url);
            Uri study_uri = Uri.parse(study_url);
            String protocol = study_uri.getScheme();
            List<String> path_segments = study_uri.getPathSegments();

            study_api_key = path_segments.get(path_segments.size() - 1);
            study_id = path_segments.get(path_segments.size() - 2);

            String request;
            if (protocol.equals("https")) {

                //Note: Joining a study always downloads the certificate.
                SSLManager.downloadCertificate(getApplicationContext(), study_uri.getHost(), true);

//                try {
//                    Intent installHTTPS = KeyChain.createInstallIntent();
//                    installHTTPS.putExtra(KeyChain.EXTRA_NAME, study_host);
//
//                    //Convert .crt to X.509 so Android knows what it is.
//                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
//                    InputStream caInput = SSLManager.getHTTPS(getApplicationContext(), study_url);
//                    Certificate ca = cf.generateCertificate(caInput);
//
//                    installHTTPS.putExtra(KeyChain.EXTRA_CERTIFICATE, ca.getEncoded());
//                    installHTTPS.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    startActivity(installHTTPS);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                } catch (CertificateException e) {
//                    e.printStackTrace();
//                }

                try {
                    request = new Https(SSLManager.getHTTPS(getApplicationContext(), study_url)).dataGET(study_url.substring(0, study_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
                } catch (FileNotFoundException e) {
                    request = null;
                }
            } else {
                request = new Http().dataGET(study_url.substring(0, study_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
            }

            if (request != null) {
                try {
                    if (request.equals("[]")) {
                        return null;
                    }
                    JSONObject study_data = new JSONObject(request);

                    //Automatically register this device on the study and create credentials for this device ID!
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
                        try {
                            answer = new Https(SSLManager.getHTTPS(getApplicationContext(), study_url)).dataPOST(study_url, data, true);
                        } catch (FileNotFoundException e) {
                            answer = null;
                        }
                    } else {
                        answer = new Http().dataPOST(study_url, data, true);
                    }

                    if (answer != null) {
                        try {
                            JSONArray configs_study = new JSONArray(answer);
                            if (!configs_study.getJSONObject(0).has("message")) {
                                study_config = configs_study.toString();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else return null;

                    return study_data;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);

            try {
                loader.dismiss();
            } catch (IllegalArgumentException e) {
                //It's ok, we might get here if we couldn't get study info.
                return;
            }

            if (result == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_QRCode.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }
                });
                builder.setTitle("Study information");
                builder.setMessage("Unable to retrieve this study information. Try again later.");
                builder.show();
            } else {
                try {
                    Cursor dbStudy = Aware.getStudy(getApplicationContext(), study_url);
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, DatabaseUtils.dumpCursorToString(dbStudy));

                    if (dbStudy == null || !dbStudy.moveToFirst()) {
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, study_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, result.getString("study_name"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, result.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "New study data: " + studyData.toString());
                        }
                    } else {

                        //User rejoined a study he was already part of. Mark as abandoned.
                        ContentValues complianceEntry = new ContentValues();
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                        complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "rejoined study. abandoning previous");

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);

                        //Update the information to the latest
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, study_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, result.getString("study_name"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, result.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "Rejoined study data: " + studyData.toString());
                        }
                    }

                    if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                    Intent study_scan = new Intent();
                    study_scan.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, study_url);
                    setResult(Activity.RESULT_OK, study_scan);
                    finish();

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
