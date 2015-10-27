package com.aware.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.R;
import com.aware.ui.qrcode.BarcodeGraphic;
import com.aware.ui.qrcode.BarcodeTrackerFactory;
import com.aware.ui.qrcode.CameraSource;
import com.aware.ui.qrcode.CameraSourcePreview;
import com.aware.ui.qrcode.GraphicOverlay;
import com.aware.utils.Https;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by denzil on 27/10/15.
 */
public class Aware_QRCode extends Aware_Activity {

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        Snackbar snack = Snackbar.make(mGraphicOverlay, "Tap to scan. Pinch/strech to zoom", Snackbar.LENGTH_LONG);
        ViewGroup group = (ViewGroup) snack.getView();
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.setTextColor(Color.WHITE);
            }
        }
        snack.show();
    }

    @SuppressLint("InlinedApi")
    private void createCameraSource() {
        Context context = getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            if( Aware.DEBUG) Log.w(Aware.TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Storage running low!", Toast.LENGTH_LONG).show();
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        mCameraSource = builder.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH).build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if( mPreview != null ) mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( mPreview != null ) mPreview.release();
    }

    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start( mCameraSource, mGraphicOverlay );
            } catch (IOException e) {
                if(Aware.DEBUG) Log.e(Aware.TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private boolean onTap(float rawX, float rawY) {
        if( Aware.DEBUG ) Log.d(Aware.TAG, "AWARE QRCode scanner tapped");

        BarcodeGraphic graphic = mGraphicOverlay.getFirstGraphic();
        Barcode barcode = null;
        if (graphic != null) {
            barcode = graphic.getBarcode();
            if (barcode != null) {
                String scanned = barcode.rawValue;
                if( scanned.contains("api.awareframework.com") ) {
                    new StudyData().execute(scanned);
                } else {
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
                }
            }
            else {
                if(Aware.DEBUG) Log.d(Aware.TAG, "barcode data is null");
            }
        }
        else {
            if( Aware.DEBUG ) Log.d(Aware.TAG,"no barcode detected");
        }
        return barcode != null;
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
        private String study_url = "";
        private ProgressDialog loader;

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
            String study_api_key = study_url.substring(study_url.lastIndexOf("/")+1, study_url.length());

            String request = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/webservice/client_get_study_info/" + study_api_key, true);
            if( request != null ) {
                try {
                    if( request.equals("[]") ) {
                        return null;
                    }
                    JSONObject study_data = new JSONObject(request);
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

            loader.dismiss();

            if( result == null ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_QRCode.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }
                });
                builder.setTitle("Study information");
                builder.setMessage("This study is no longer available.");
                builder.show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_QRCode.this);
                builder.setPositiveButton("Sign up!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent study_scan = new Intent();
                        study_scan.putExtra("study_url", study_url);
                        setResult(Activity.RESULT_OK, study_scan);
                        finish();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }
                });
                builder.setTitle("Study information");
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View study_ui = inflater.inflate(R.layout.study_info, null);
                TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
                TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
                TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);

                try {
                    study_name.setText((result.getString("study_name").length()>0 ? result.getString("study_name"): "Not available"));
                    study_description.setText((result.getString("study_description").length()>0?result.getString("study_description"):"Not available."));
                    study_pi.setText("PI: " + result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                builder.setView(study_ui);
                builder.show();
            }
        }
    }
}
