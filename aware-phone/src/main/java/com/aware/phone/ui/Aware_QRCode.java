package com.aware.phone.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.Aware_Client;
import com.aware.phone.R;
import com.aware.phone.ui.qrcode.BarcodeGraphic;
import com.aware.phone.ui.qrcode.BarcodeTrackerFactory;
import com.aware.phone.ui.qrcode.CameraSource;
import com.aware.phone.ui.qrcode.CameraSourcePreview;
import com.aware.phone.ui.qrcode.GraphicOverlay;
import com.aware.providers.Aware_Provider;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Hashtable;
import java.util.List;

import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;

/**
 * Created by denzil on 27/10/15.
 */
public class Aware_QRCode extends Aware_Activity implements ZBarScannerView.ResultHandler {

    private static final int RC_HANDLE_GMS = 9001;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    private BarcodeDetector barcodeDetector;

    private ZBarScannerView mScannerView;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        barcodeDetector = new BarcodeDetector.Builder(this).build();

        if (barcodeDetector.isOperational()) {
            setContentView(R.layout.aware_qrcode);

            mPreview = (CameraSourcePreview) findViewById(R.id.preview);
            mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.overlay);

            createCameraSource();

            gestureDetector = new GestureDetector(this, new CaptureGestureListener());
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

            mPreview.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    boolean b = scaleGestureDetector.onTouchEvent(e);
                    boolean c = gestureDetector.onTouchEvent(e);
                    return b || c;
                }
            });

            Snackbar snack = Snackbar.make(mGraphicOverlay, "Tap QRCode to scan. Pinch/strech to zoom", Snackbar.LENGTH_LONG);
            ViewGroup group = (ViewGroup) snack.getView();
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    t.setTextColor(Color.WHITE);
                }
            }
            snack.show();
        } else {
            mScannerView = new ZBarScannerView(this);

            LinearLayout main = new LinearLayout(this);
            main.setOrientation(LinearLayout.VERTICAL);

            ListView list = new ListView(this);
            list.setId(android.R.id.list);
            list.setVisibility(View.GONE);

//            Toolbar toolbar = new Toolbar(this);
//            toolbar.setId(R.id.aware_toolbar);
//            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    finish();
//                }
//            });
//            toolbar.setBackgroundColor(Color.parseColor("#33B5E5"));
//            toolbar.setTitleTextColor(Color.parseColor("#FFFFFF"));

//            main.addView(toolbar);
            main.addView(mScannerView);
            main.addView(list);
            setContentView(main);
        }
    }

    @SuppressLint("InlinedApi")
    private void createCameraSource() {
        Context context = getApplicationContext();

        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            if (Aware.DEBUG) Log.w(Aware.TAG, "Detector dependencies are not yet available.");

            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Storage running low!", Toast.LENGTH_LONG).show();
            }
        }

        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        mCameraSource = builder.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH).build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (barcodeDetector.isOperational()) {
            startCameraSource();
        } else {
            mScannerView.setResultHandler(this);
            mScannerView.startCamera(-1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeDetector.isOperational()) {
            if (mPreview != null) mPreview.stop();
        } else {
            mScannerView.stopCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) mPreview.release();
    }

    private void startCameraSource() throws SecurityException {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());

        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                if (Aware.DEBUG) Log.e(Aware.TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private boolean onTap(float rawX, float rawY) {
        if (Aware.DEBUG) Log.d(Aware.TAG, "AWARE QRCode scanner tapped");

        BarcodeGraphic graphic = mGraphicOverlay.getFirstGraphic();
        Barcode barcode = null;
        if (graphic != null) {
            barcode = graphic.getBarcode();
            if (barcode != null) {
                String scanned = barcode.rawValue;
                new StudyData().execute(scanned);

                Snackbar snack = Snackbar.make(mGraphicOverlay, scanned, Snackbar.LENGTH_LONG);
                ViewGroup group = (ViewGroup) snack.getView();
                for (int i = 0; i < group.getChildCount(); i++) {
                    View v = group.getChildAt(i);
                    if (v instanceof TextView) {
                        TextView t = (TextView) v;
                        t.setTextColor(Color.WHITE);
                    }
                }
                snack.show();

            } else {
                if (Aware.DEBUG) Log.d(Aware.TAG, "barcode data is null");
            }
        } else {
            if (Aware.DEBUG) Log.d(Aware.TAG, "no barcode detected");
        }
        return barcode != null;
    }

    //Zbar QRCode handler
    @Override
    public void handleResult(Result result) {
        new StudyData().execute(result.getContents());
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
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

                SSLManager.handleUrl(getApplicationContext(), study_url, true);

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
