package com.aware.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

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
            ArrayList<String> pending = new ArrayList<>();
            String[] permissions = getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS);
            for( String p : permissions ) {
                if( ActivityCompat.shouldShowRequestPermissionRationale(this, p) ) pending.add(p);
            }
            if( pending.size() > 0 ) {
                ActivityCompat.requestPermissions(PermissionsHandler.this, pending.toArray(new String[pending.size()]), CODE_PERMISSION_REQUEST );
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Fixes a crash on Android M for Activities with Theme.NoDisplay
        //c.f. https://code.google.com/p/android-developer-preview/issues/detail?id=2353
        setVisible(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }
}
