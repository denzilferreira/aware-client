package com.aware.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;

/**
 * This is an invisible activity used to request the needed permissions from the user from API 23 onwards.
 * Created by denzil on 22/10/15.
 */
public class PermissionsHandler extends Activity {

    public static String EXTRA_REQUIRED_PERMISSIONS = "required_permissions";
    private final int CODE_PERMISSION_REQUEST = 999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if( getIntent() != null && getIntent().getExtras() != null && getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS) != null ) {

            String[] permissions = getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS);

            //Check if we have requested this permission already
            ArrayList<String> missing = new ArrayList<>();
            for( String p : permissions ) {
                int ok = ContextCompat.checkSelfPermission(this, p);
                if( ok != PackageManager.PERMISSION_GRANTED ) missing.add(p);
            }
            if( missing.size() > 0 ) {
                ActivityCompat.requestPermissions(PermissionsHandler.this, missing.toArray(new String[missing.size()]), CODE_PERMISSION_REQUEST);
            }
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }
}
