package com.aware.phone.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;
import com.aware.utils.StudyUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;

//PreferenceActivity
public class Aware_Activity extends AppCompatPreferenceActivity {

    private DrawerLayout navigationDrawer;
    private ListView navigationList;
    private ActionBarDrawerToggle navigationToggle;
    private Toolbar toolbar;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Aware_Preferences.REQUEST_JOIN_STUDY) {
            if (resultCode == RESULT_OK) {
                Intent study_config = new Intent(this, StudyUtils.class);
                study_config.putExtra("study_url", data.getStringExtra("study_url"));
                startService(study_config);

                Toast.makeText(this, "Joining study...", Toast.LENGTH_LONG).show();

                finish();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        toolbar = (Toolbar) findViewById(R.id.aware_toolbar);
        setSupportActionBar(toolbar);

        if (Aware.is_watch(this)) {
            Menu menu = toolbar.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                item.setVisible(false);
            }
        }

        navigationDrawer = (DrawerLayout) findViewById(R.id.aware_ui_main);
        navigationList = (ListView) findViewById(R.id.aware_navigation);

        navigationToggle = new ActionBarDrawerToggle(Aware_Activity.this, navigationDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        navigationDrawer.setDrawerListener(navigationToggle);

        String[] options = {"Stream", "Sensors", "Plugins", "Studies"};
        NavigationAdapter nav_adapter = new NavigationAdapter(getApplicationContext(), options);
        navigationList.setAdapter(nav_adapter);

        if (navigationToggle != null) navigationToggle.syncState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aware_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().toString().equals("QRCode")) {
            Intent join_study = new Intent(Aware_Activity.this, Aware_QRCode.class);
            startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY);
        }
        if (item.getTitle().toString().equals("Team")) {
            Intent about_us = new Intent(Aware_Activity.this, About.class);
            startActivity(about_us);
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
                            Intent plugin_manager = new Intent(getApplicationContext(), Plugins_Manager.class);
                            startActivity(plugin_manager);
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

    public class Async_StudyData extends AsyncTask<String, Void, JSONObject> {
        private String study_url = "";
        private ProgressDialog loader;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loader = new ProgressDialog(Aware_Activity.this);
            loader.setTitle("Loading study");
            loader.setMessage("Please wait...");
            loader.setCancelable(false);
            loader.setIndeterminate(true);
            loader.show();
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            study_url = params[0];

            String study_api_key = study_url.substring(study_url.lastIndexOf("/") + 1, study_url.length());
            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
            String protocol = study_url.substring(0, study_url.indexOf(":"));

            if (study_api_key.length() == 0) return null;

            String request;
            if (protocol.equals("https")) {
                try {
                    request = new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), study_url)).dataGET(study_host + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
                } catch (FileNotFoundException e) {
                    request = null;
                }
            } else {
                request = new Http(getApplicationContext()).dataGET(study_host + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
            }

            if (request != null) {
                try {
                    if (request.equals("[]")) {
                        return null;
                    }
                    return new JSONObject(request);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);

            try {
                loader.dismiss();
            } catch (IllegalArgumentException e) {
                //It's ok, we might get here if we couldn't get study info.
                return;
            }

            if (result == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //if part of a study, you can't change settings.
                        if (Aware.getSetting(getApplicationContext(), "study_id").length() > 0) {
                            Toast.makeText(getApplicationContext(), "As part of a study, no changes are allowed.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
                builder.setTitle("Study information");
                builder.setMessage("Unable to retrieve study's information. Please, try again later.");
                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Clearing settings... please wait", Toast.LENGTH_LONG).show();
                        Aware.reset(getApplicationContext());

                        Intent preferences = new Intent(getApplicationContext(), Aware_Client.class);
                        preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(preferences);
                    }
                });
                builder.setCancelable(false);
                builder.show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //if part of a study, you can't change settings.
                        if (Aware.getSetting(getApplicationContext(), "study_id").length() > 0) {
                            Toast.makeText(getApplicationContext(), "As part of a study, no changes are allowed.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Clearing settings... please wait", Toast.LENGTH_LONG).show();
                        Aware.reset(getApplicationContext());

                        Intent preferences = new Intent(getApplicationContext(), Aware_Client.class);
                        preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(preferences);
                    }
                });
                builder.setTitle("Study information");
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View study_ui = inflater.inflate(R.layout.study_info, null);
                TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
                TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
                TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);

                try {
                    study_name.setText((result.getString("study_name").length() > 0 ? result.getString("study_name") : "Not available"));
                    study_description.setText((result.getString("study_description").length() > 0 ? result.getString("study_description") : "Not available."));
                    study_pi.setText("PI: " + result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                builder.setView(study_ui);
                builder.setCancelable(false);
                builder.show();
            }
        }
    }
}
