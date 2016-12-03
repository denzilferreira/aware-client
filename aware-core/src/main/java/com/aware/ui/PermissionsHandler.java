package com.aware.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.aware.Aware;

import java.util.ArrayList;

/**
 * This is an invisible activity used to request the needed permissions from the user from API 23 onwards.
 * Created by denzil on 22/10/15.
 */
public class PermissionsHandler extends Activity {

    /**
     * Extra ArrayList<String> with Manifest.permission that require explicit users' permission on Android API 23+
     */
    public static final String EXTRA_REQUIRED_PERMISSIONS = "required_permissions";
    /**
     * e.g., package/package.Activity
     */
    public static final String EXTRA_REDIRECT_ACTIVITY = "redirect_activity";

    /**
     * The request code for the permissions
     */
    public static final int RC_PERMISSIONS = 112;

    private Intent redirect;

    private ArrayList<String> permissionsNeeded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionsNeeded = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (permissionsNeeded == null)
            permissionsNeeded = new ArrayList<>();

        if (getIntent() != null && getIntent().getExtras() != null && getIntent().getSerializableExtra(EXTRA_REQUIRED_PERMISSIONS) != null) {
            if (permissionsNeeded.size() == 0) {
                permissionsNeeded = (ArrayList<String>) getIntent().getSerializableExtra(EXTRA_REQUIRED_PERMISSIONS);
            } else {
                //Append new permissions to the old permissions if they are not present
                ArrayList<String> newPermissions = (ArrayList<String>) getIntent().getSerializableExtra(EXTRA_REQUIRED_PERMISSIONS);
                for(String p : newPermissions) {
                    if (!permissionsNeeded.contains(p))
                        permissionsNeeded.add(p);
                }
            }

            ActivityCompat.requestPermissions(PermissionsHandler.this, permissionsNeeded.toArray(new String[permissionsNeeded.size()]), RC_PERMISSIONS);
            if (getIntent().hasExtra(EXTRA_REDIRECT_ACTIVITY)) {
                redirect = new Intent();
                String[] component = getIntent().getStringExtra(EXTRA_REDIRECT_ACTIVITY).split("/");
                redirect.setComponent(new ComponentName(component[0], component[1]));
                redirect.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
                if (redirect == null) {
                    Intent activity = new Intent();
                    setResult(Activity.RESULT_CANCELED, activity);
                } else {
                    setResult(Activity.RESULT_CANCELED, redirect);
                    startActivity(redirect);
                }
                finish();
            } else {
                if (redirect == null) {
                    Intent activity = new Intent();
                    setResult(Activity.RESULT_OK, activity);
                } else {
                    setResult(Activity.RESULT_OK, redirect);
                    startActivity(redirect);
                }
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
