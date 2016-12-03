package com.aware.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.Aware_Client;
import com.aware.phone.R;
import com.aware.ui.PermissionsHandler;

import java.util.ArrayList;

public class Aware_Activity extends AppCompatPreferenceActivity {

    private DrawerLayout navigationDrawer;
    private ListView navigationList;
    private ActionBarDrawerToggle navigationToggle;
    public CoordinatorLayout aware_container;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Aware_Preferences.REQUEST_JOIN_STUDY) {
            if (resultCode == RESULT_OK) {
                //Load join study wizard. We already have the study info on the database.
                Intent studyInfo = new Intent(this, Aware_Join_Study.class);
                studyInfo.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, data.getStringExtra(Aware_Join_Study.EXTRA_STUDY_URL));
                startActivity(studyInfo);
                finish();
            }
        }
        if (requestCode == PermissionsHandler.RC_PERMISSIONS) {
            if (resultCode == Activity.RESULT_OK) {
                finish();
                Intent preferences = new Intent(this, Aware_Client.class);
                startActivity(preferences);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        aware_container = (CoordinatorLayout) findViewById(R.id.aware_container);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        aware_container = (CoordinatorLayout) findViewById(R.id.aware_container);

        Toolbar toolbar = (Toolbar) findViewById(R.id.aware_toolbar);
        toolbar.setTitle(getTitle() != null ? getTitle() : "");
        toolbar.inflateMenu(R.menu.aware_menu);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        navigationDrawer = (DrawerLayout) findViewById(R.id.aware_ui_main);
        navigationList = (ListView) findViewById(R.id.aware_navigation);

        navigationToggle = new ActionBarDrawerToggle(Aware_Activity.this, navigationDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);

        if (navigationDrawer != null && navigationToggle != null) {
            navigationDrawer.setDrawerListener(navigationToggle);
            navigationToggle.syncState();
        }

        String[] options = {"Stream", "Sensors", "Plugins", "Studies"};
        NavigationAdapter nav_adapter = new NavigationAdapter(getApplicationContext(), options);
        if (navigationList != null)
            navigationList.setAdapter(nav_adapter);

        if (Aware.isStudy(this)) {
            navigationToggle.setDrawerIndicatorEnabled(false);
            navigationDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ArrayList<String> permission = new ArrayList<>();
                permission.add(Manifest.permission.CAMERA);

                Intent permissions = new Intent(this, PermissionsHandler.class);
                permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, permission);
                permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getPackageName() + ".ui.Aware_QRCode");
                permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(permissions);
            } else {
                Intent join_study = new Intent(Aware_Activity.this, Aware_QRCode.class);
                startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY);
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (navigationToggle != null) navigationToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Navigation adapter
     *
     * @author denzil
     */
    public class NavigationAdapter extends ArrayAdapter<String> {
        private final String[] items;
        private final LayoutInflater inflater;

        public NavigationAdapter(Context context, String[] items) {
            super(context, R.layout.aware_navigation_item, items);
            this.items = items;
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.aware_navigation_item, parent, false);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (position) {
                        case 0: //Stream
                            Intent stream_ui = new Intent(getApplicationContext(), Stream_UI.class);
                            startActivity(stream_ui);
                            break;
                        case 1: //Sensors
                            Intent sensors_ui = new Intent(getApplicationContext(), Aware_Client.class);
                            startActivity(sensors_ui);
                            break;
                        case 2: //Plugins
                            Intent playStore = new Intent(Intent.ACTION_VIEW);
                            playStore.setData(Uri.parse("market://search?q=awareframework&c=apps"));
                            startActivity(playStore);
                            break;
                        case 3: //Join study
                            //TODO: make ui for listing available studies
                            if (ContextCompat.checkSelfPermission(Aware_Activity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                ArrayList<String> permission = new ArrayList<>();
                                permission.add(Manifest.permission.CAMERA);

                                Intent permissions = new Intent(Aware_Activity.this, PermissionsHandler.class);
                                permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, permission);
                                permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getPackageName() + ".ui.Aware_QRCode");
                                permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(permissions);
                            } else {
                                Intent join_study = new Intent(Aware_Activity.this, Aware_QRCode.class);
                                startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY);
                            }
                            break;
                    }
                    if (navigationDrawer != null && navigationList != null)
                        navigationDrawer.closeDrawer(navigationList);
                }
            });
            ImageView nav_icon = (ImageView) row.findViewById(R.id.nav_placeholder);
            TextView nav_title = (TextView) row.findViewById(R.id.nav_title);

            switch (position) {
                case 0:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_stream);
                    break;
                case 1:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_sensors);
                    break;
                case 2:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_plugins);
                    break;
                case 3:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_studies);
                    break;
            }
            String item = items[position];
            nav_title.setText(item);

            return row;
        }
    }
}
