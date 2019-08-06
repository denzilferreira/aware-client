package com.aware.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.aware.Aware;

import java.util.ArrayList;

/**
 * This is an invisible activity used to request the needed permissions from the user from API 23 onwards.
 * Created by denzil on 22/10/15.
 */
public class PermissionsHandler extends Activity {

    private String TAG = "PermissionsHandler";

    /**
     * Extra ArrayList<String> with Manifest.permission that require explicit users' permission on Android API 23+
     */
    public static final String EXTRA_REQUIRED_PERMISSIONS = "required_permissions";

    /**
     * Class name of the Activity redirect, e.g., Class.getClass().getName();
     */
    public static final String EXTRA_REDIRECT_ACTIVITY = "redirect_activity";

    /**
     * Class name of the Service redirect, e.g., Class.getClass().getName();
     */
    public static final String EXTRA_REDIRECT_SERVICE = "redirect_service";

    /**
     * Used on redirect service to know when permissions have been accepted
     */
    public static final String ACTION_AWARE_PERMISSIONS_CHECK = "ACTION_AWARE_PERMISSIONS_CHECK";

    /**
     * The request code for the permissions
     */
    public static final int RC_PERMISSIONS = 112;

    private Intent redirect_activity, redirect_service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("Permissions", "Permissions request for " + getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null && getIntent().getExtras() != null && getIntent().getSerializableExtra(EXTRA_REQUIRED_PERMISSIONS) != null) {
            ArrayList<String> permissionsNeeded = (ArrayList<String>) getIntent().getSerializableExtra(EXTRA_REQUIRED_PERMISSIONS);
            ActivityCompat.requestPermissions(PermissionsHandler.this, permissionsNeeded.toArray(new String[permissionsNeeded.size()]), RC_PERMISSIONS);
            if (getIntent().hasExtra(EXTRA_REDIRECT_ACTIVITY)) {
                redirect_activity = new Intent();
                String[] component = getIntent().getStringExtra(EXTRA_REDIRECT_ACTIVITY).split("/");
                redirect_activity.setComponent(new ComponentName(component[0], component[1]));
                redirect_activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else if (getIntent().hasExtra(EXTRA_REDIRECT_SERVICE)) {
                redirect_service = new Intent();
                redirect_service.setAction(ACTION_AWARE_PERMISSIONS_CHECK);
                String[] component = getIntent().getStringExtra(EXTRA_REDIRECT_SERVICE).split("/");
                redirect_service.setComponent(new ComponentName(component[0], component[1]));
            }
        } else {
            Intent activity = new Intent();
            setResult(Activity.RESULT_OK, activity);
            finish();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == RC_PERMISSIONS) {
            int not_granted = 0;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    not_granted++;
                    Log.d(Aware.TAG, permissions[i] + " was not granted");
                } else {
                    Log.d(Aware.TAG, permissions[i] + " was granted");
                }
            }

            if (not_granted > 0) {
                if (redirect_activity == null) {
                    Intent activity = new Intent();
                    setResult(Activity.RESULT_CANCELED, activity);
                }
                if (redirect_activity != null) {
                    setResult(Activity.RESULT_CANCELED, redirect_activity);
                    startActivity(redirect_activity);
                }
                if (redirect_service != null) {
                    startService(redirect_service);
                }
                finish();
            } else {
                if (redirect_activity == null) {
                    Intent activity = new Intent();
                    setResult(Activity.RESULT_OK, activity);
                }
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (redirect_service != null) {
            Log.d(TAG, "Redirecting to Service: " + redirect_service.getComponent().toString());
            redirect_service.setAction(ACTION_AWARE_PERMISSIONS_CHECK);
            startService(redirect_service);
        }
        if (redirect_activity != null) {
            Log.d(TAG, "Redirecting to Activity: " + redirect_activity.getComponent().toString());
            setResult(Activity.RESULT_OK, redirect_activity);
            startActivity(redirect_activity);
        }
        Log.d("Permissions", "Handled permissions for " + getPackageName());
    }
}
