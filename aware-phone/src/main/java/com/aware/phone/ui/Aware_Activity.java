package com.aware.phone.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.PermissionChecker;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.Aware_Client;
import com.aware.phone.R;
import com.aware.ui.PermissionsHandler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

public abstract class Aware_Activity extends AppCompatPreferenceActivity {

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
        }

        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.aware_bottombar);
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.aware_sensors: //Sensors
                            Intent sensors_ui = new Intent(getApplicationContext(), Aware_Client.class);
                            startActivity(sensors_ui);
                            break;
                        case R.id.aware_plugins: //Plugins
                            Intent pluginsManager = new Intent(getApplicationContext(), Plugins_Manager.class);
                            startActivity(pluginsManager);
                            break;
                        case R.id.aware_stream: //Stream
                            Intent stream_ui = new Intent(getApplicationContext(), Stream_UI.class);
                            startActivity(stream_ui);
                            break;
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aware_menu, menu);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_qrcode)) && Aware.is_watch(this))
                item.setVisible(false);
            if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_team)) && Aware.is_watch(this))
                item.setVisible(false);
            if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_study)) && Aware.is_watch(this))
                item.setVisible(false);
            if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_sync)) && !Aware.getSetting(this, Aware_Preferences.STATUS_WEBSERVICE).equals("true"))
                item.setVisible(false);
            if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_study)) && !Aware.getSetting(this, Aware_Preferences.STATUS_WEBSERVICE).equals("true"))
                item.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_qrcode))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ArrayList<String> permission = new ArrayList<>();
                permission.add(Manifest.permission.CAMERA);

                Intent permissions = new Intent(this, PermissionsHandler.class);
                permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, permission);
                permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getPackageName() + ".ui.Aware_QRCode");
                permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(permissions);
            } else {
                Intent qrcode = new Intent(Aware_Activity.this, Aware_QRCode.class);
                qrcode.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(qrcode);
            }
        }
        if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_study))) {
            Intent studyInfo = new Intent(Aware_Activity.this, Aware_Join_Study.class);
            studyInfo.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER));
            startActivity(studyInfo);
        }
        if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_team))) {
            Intent about_us = new Intent(Aware_Activity.this, About.class);
            startActivity(about_us);
        }
        if (item.getTitle().toString().equalsIgnoreCase(getResources().getString(R.string.aware_sync))) {
            Toast.makeText(getApplicationContext(), "Syncing data...", Toast.LENGTH_SHORT).show();
            Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
            sendBroadcast(sync);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTitle("AWARE");
    }
}
