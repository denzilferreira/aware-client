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
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Fixes a crash on Android M for Activities with Theme.NoDisplay
        //c.f. https://code.google.com/p/android-developer-preview/issues/detail?id=2353
        setVisible(true);

        if( getIntent() != null && getIntent().getExtras() != null && getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS) != null ) {
            String[] permissions = getIntent().getStringArrayExtra(EXTRA_REQUIRED_PERMISSIONS);
            ActivityCompat.requestPermissions(PermissionsHandler.this, permissions, CODE_PERMISSION_REQUEST );
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }
}
