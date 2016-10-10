package com.aware.phone.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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
import com.aware.providers.Aware_Provider;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;
import com.aware.utils.StudyUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

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
                            playStore.setData(Uri.parse("market://search?q=awareframework plugin&c=apps"));
                            startActivity(playStore);
                            break;
                        case 3: //Join study
                            //TODO: make ui for listing available studies
                            Intent join_study = new Intent(getApplicationContext(), Aware_QRCode.class);
                            startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY);
                            break;
                    }
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

//    public class Async_StudyData extends AsyncTask<String, Void, JSONObject> {
//
//        private ProgressDialog loader;
//
//        private String study_url = "";
//        private String study_api_key = "";
//        private String study_id = "";
//        private String study_config = "";
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            loader = new ProgressDialog(Aware_Activity.this);
//            loader.setTitle("Study information");
//            loader.setMessage("Please wait...");
//            loader.setCancelable(false);
//            loader.setIndeterminate(true);
//            loader.show();
//        }
//
//        @Override
//        protected JSONObject doInBackground(String... params) {
//            study_url = params[0];
//
//            if (Aware.DEBUG) Log.d(Aware.TAG, "Aware_QRCode study_url: " + study_url);
//            Uri study_uri = Uri.parse(study_url);
//            String protocol = study_uri.getScheme();
//            String study_host = protocol + "://" + study_uri.getHost();  // misnomer: protocol+host
//            List<String> path_segments = study_uri.getPathSegments();
//
//            study_api_key = path_segments.get(path_segments.size() - 1);
//            study_id = path_segments.get(path_segments.size() - 2);
//
//            // Get SSL certificates in a blocking manner, since we are already in background.
//            // We don't need to sleep to wait for certs.
//            SSLManager.handleUrl(getApplicationContext(), study_url, true);
//
//            String request;
//            if (protocol.equals("https")) {
//                try {
//                    request = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataGET(study_host + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
//                } catch (FileNotFoundException e) {
//                    request = null;
//                }
//            } else {
//                request = new Http(getApplicationContext()).dataGET(study_host + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
//            }
//
//            if (request != null) {
//                try {
//                    if (request.equals("[]")) {
//                        return null;
//                    }
//                    JSONObject study_data = new JSONObject(request);
//
//                    //Request study settings: note that this will automatically register this device on the study. If user does not sign-up, we clean remotely
//                    Hashtable<String, String> data = new Hashtable<>();
//                    data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
//
//                    String answer;
//                    if (protocol.equals("https")) {
//                        try {
//                            answer = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataPOST(study_url, data, true);
//                        } catch (FileNotFoundException e) {
//                            answer = null;
//                        }
//                    } else {
//                        answer = new Http(getApplicationContext()).dataPOST(study_url, data, true);
//                    }
//
//                    if (answer != null) {
//                        try {
//                            JSONArray configs_study = new JSONArray(answer);
//                            if (!configs_study.getJSONObject(0).has("message")) {
//                                study_config = configs_study.toString();
//                            }
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    } else return null;
//
//                    return study_data;
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(JSONObject result) {
//            super.onPostExecute(result);
//
//            try {
//                loader.dismiss();
//            } catch (IllegalArgumentException e) {
//                //It's ok, we might get here if we couldn't get study info.
//                return;
//            }
//
//            if (result != null) {
//                try {
//                    Cursor dbStudy = Aware.getStudy(getApplicationContext(), study_url);
//                    if (dbStudy == null || !dbStudy.moveToFirst()) {
//                        ContentValues studyData = new ContentValues();
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, study_url);
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config);
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, result.getString("study_name"));
//                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, result.getString("study_description"));
//
//                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);
//
//                        if (Aware.DEBUG) {
//                            Log.d(Aware.TAG, "Study data: " + studyData.toString());
//                        }
//                    } else {
//                        dbStudy.close();
//                    }
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            Intent studyInfo = new Intent(getApplicationContext(), Aware_Join_Study.class);
//            studyInfo.putExtra("study_url", study_url);
//            startActivity(studyInfo);

//            if (result == null) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
//                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                        //if part of a study, you can't change settings.
//                        if (Aware.getSetting(getApplicationContext(), "study_id").length() > 0) {
//                            Snackbar noChanges = Snackbar.make(aware_container, "Ongoing study, no changes allowed.", Snackbar.LENGTH_LONG);
//                            TextView output = (TextView) noChanges.getView().findViewById(android.support.design.R.id.snackbar_text);
//                            output.setTextColor(Color.WHITE);
//                            noChanges.show();
//                        }
//                    }
//                });
//                builder.setTitle("Study information");
//                builder.setMessage("Unable to retrieve study's information. Please, try again later.");
//                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//
//                        Snackbar clear = Snackbar.make(aware_container, "Clearing settings, please wait.", Snackbar.LENGTH_LONG);
//                        TextView output = (TextView) clear.getView().findViewById(android.support.design.R.id.snackbar_text);
//                        output.setTextColor(Color.WHITE);
//                        clear.show();
//
//                        Aware.reset(getApplicationContext());
//
//                        Intent preferences = new Intent(getApplicationContext(), Aware_Client.class);
//                        preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        startActivity(preferences);
//                    }
//                });
//                builder.setCancelable(false);
//                builder.show();
//            } else {
//                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
//                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                        //if part of a study, you can't change settings.
//                        if (Aware.getSetting(getApplicationContext(), "study_id").length() > 0) {
//                            Snackbar noChanges = Snackbar.make(aware_container, "Ongoing study, no changes allowed.", Snackbar.LENGTH_LONG);
//                            TextView output = (TextView) noChanges.getView().findViewById(android.support.design.R.id.snackbar_text);
//                            output.setTextColor(Color.WHITE);
//                            noChanges.show();
//                        }
//                    }
//                });
//                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//
//                        Snackbar clear = Snackbar.make(aware_container, "Clearing settings, please wait.", Snackbar.LENGTH_LONG);
//                        TextView output = (TextView) clear.getView().findViewById(android.support.design.R.id.snackbar_text);
//                        output.setTextColor(Color.WHITE);
//                        clear.show();
//
//                        Aware.reset(getApplicationContext());
//
//                        Intent preferences = new Intent(getApplicationContext(), Aware_Client.class);
//                        preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        startActivity(preferences);
//                    }
//                });
//                builder.setTitle("Study information");
//                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//                View study_ui = inflater.inflate(R.layout.study_info, null);
//                TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
//                TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
//                TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);
//
//                try {
//                    study_name.setText((result.getString("study_name").length() > 0 ? result.getString("study_name") : "Not available"));
//                    study_description.setText((result.getString("study_description").length() > 0 ? result.getString("study_description") : "Not available."));
//                    study_pi.setText(result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//                builder.setView(study_ui);
//                builder.setCancelable(false);
//                builder.show();
//            }
//        }
//    }
}
