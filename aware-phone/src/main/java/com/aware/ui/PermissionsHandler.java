package com.aware.ui;

import android.app.Activity;
import android.content.Intent;
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

    private ArrayList<String> missing = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if( getIntent() != null && getIntent().getExtras() != null && getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS) != null ) {
            String[] permissions = getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS);
            for( String p : permissions ) {
                int ok = ContextCompat.checkSelfPermission(this, p);
                if( ok != PackageManager.PERMISSION_GRANTED && ! is_missing(p) ) missing.add(p);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if( intent != null && intent.getExtras() != null && intent.getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS) != null ) {
            String[] permissions = intent.getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS);
            //Check if we have requested this permission already
            for( String p : permissions ) {
                int ok = ContextCompat.checkSelfPermission(this, p);
                if( ok != PackageManager.PERMISSION_GRANTED && ! is_missing(p) ) missing.add(p);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //Done with the requested permissions
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if( missing.size() > 0 ) {
            ActivityCompat.requestPermissions(PermissionsHandler.this, missing.toArray(new String[missing.size()]), CODE_PERMISSION_REQUEST);
        } else {
            finish();
        }
    }

    /**
     * Check if we are already asking this permission
     * @param p
     * @return
     */
    private boolean is_missing( String p ) {
        for( String pp : missing ) {
            if( pp.equals(p) ) return true;
        }
        return false;
    }
}
