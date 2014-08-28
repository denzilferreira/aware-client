/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/

package com.aware;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.providers.Aware_Provider.Aware_Settings;
import com.aware.ui.CameraStudy;
import com.aware.ui.Plugins_Manager;
import com.aware.ui.Stream_UI;
import com.aware.utils.Https;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Preferences dashboard for the AWARE Aware
 * Allows the researcher to configure all the modules, start and stop modules and where logging happens.
 * @author df
 *
 */
public class Aware_Preferences extends PreferenceActivity {

    private static final Aware framework = Aware.getService();

    private static SensorManager mSensorMgr;
    private static DrawerLayout navigationDrawer;
    private static ListView navigationList;
    private static ActionBarDrawerToggle navigationToggle;
    
    private static final int DIALOG_ERROR_ACCESSIBILITY = 1;
    private static final int DIALOG_ERROR_MISSING_PARAMETERS = 2;
    private static final int DIALOG_ERROR_MISSING_SENSOR = 3;
    
    //Request ID for joining study
    public static final int REQUEST_JOIN_STUDY = 1;
    
    /**
     * Activate/deactive AWARE debug messages (boolean)
     */
    public static final String DEBUG_FLAG = "debug_flag";
    
    /**
     * Debug tag on Logcat
     */
    public static final String DEBUG_TAG = "debug_tag";
    
    /**
     * Disables database writing
     */
    public static final String DEBUG_DB_SLOW = "debug_db_slow";
    
    /**
     * AWARE Device ID (UUID)
     */
    public static final String DEVICE_ID = "device_id";
    
    /**
     * Automatically check for updates on the client
     */
    public static final String AWARE_AUTO_UPDATE = "aware_auto_update";
    
    /**
     * Activate/deactivate accelerometer log (boolean)
     */
    public static final String STATUS_ACCELEROMETER = "status_accelerometer";
    
    /**
     * Accelerometer frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ACCELEROMETER = "frequency_accelerometer";
    
    /**
     * Activate/deactivate application usage log (boolean)
     */
    public static final String STATUS_APPLICATIONS = "status_applications";
    
    /**
     * Background applications update frequency (default = 30) seconds
     */
    public static final String FREQUENCY_APPLICATIONS = "frequency_applications";
    
    /**
     * Activate/deactivate application installation log (boolean)
     */
    public static final String STATUS_INSTALLATIONS = "status_installations";
    
    /**
     * Activate/deactivate device notifications log (boolean)
     */
    public static final String STATUS_NOTIFICATIONS = "status_notifications";
    
    /**
     * Activate/deactivate application crashes (boolean)
     */
    public static final String STATUS_CRASHES = "status_crashes";
    
    /**
     * Activate/deactivate battery log (boolean)
     */
    public static final String STATUS_BATTERY = "status_battery";
    
    /**
     * Activate/deactivate bluetooth scan log (boolean)
     */
    public static final String STATUS_BLUETOOTH = "status_bluetooth";
    
    /**
     * Frequency of bluetooth scans, in seconds (default = 60)
     */
    public static final String FREQUENCY_BLUETOOTH = "frequency_bluetooth";
    
    /**
     * Activate/deactivate communication events (boolean)
     */
    public static final String STATUS_COMMUNICATION_EVENTS = "status_communication_events";
    
    /**
     * Activate/deactivate calls log (boolean)
     */
    public static final String STATUS_CALLS = "status_calls";
    
    /**
     * Activate/deactivate messages log (boolean)
     */
    public static final String STATUS_MESSAGES = "status_messages";
    
    /**
     * Activate/deactivate gravity log (boolean)
     */
    public static final String STATUS_GRAVITY = "status_gravity";
    
    /**
     * Gravity frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GRAVITY = "frequency_gravity";
    
    /**
     * Activate/deactivate gyroscope log (boolean)
     */
    public static final String STATUS_GYROSCOPE = "status_gyroscope";
    
    /**
     * Gyroscope frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GYROSCOPE = "frequency_gyroscope";
    
    /**
     * Activate/deactivate GPS location log (boolean)
     */
    public static final String STATUS_LOCATION_GPS = "status_location_gps";
    
    /**
     * GPS location frequency in seconds (default = 180). 0 is always on.
     */
    public static final String FREQUENCY_LOCATION_GPS = "frequency_location_gps";
    
    /**
     * GPS location minimum acceptable accuracy (default = 150), in meters
     */
    public static final String MIN_LOCATION_GPS_ACCURACY = "min_location_gps_accuracy";
    
    /**
     * Activate/deactivate network location log (boolean)
     */
    public static final String STATUS_LOCATION_NETWORK = "status_location_network";
    
    /**
     * Network location frequency in seconds (default = 300). 0 is always on.
     */
    public static final String FREQUENCY_LOCATION_NETWORK = "frequency_location_network";
    
    /**
     * Network location minimum acceptable accuracy (default = 1500), in meters
     */
    public static final String MIN_LOCATION_NETWORK_ACCURACY = "min_location_network_accuracy";
    
    /**
     * Location expiration time (default = 300), in seconds
     */
    public static final String LOCATION_EXPIRATION_TIME = "location_expiration_time";
    
    /**
     * Activate/deactivate light sensor log (boolean)
     */
    public static final String STATUS_LIGHT = "status_light";
    
    /**
     * Light frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LIGHT = "frequency_light";
    
    /**
     * Activate/deactivate linear accelerometer log (boolean)
     */
    public static final String STATUS_LINEAR_ACCELEROMETER = "status_linear_accelerometer";
    
    /**
     * Linear accelerometer frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LINEAR_ACCELEROMETER = "frequency_linear_accelerometer";
    
    /**
     * Activate/deactivate network usage events (boolean)
     */
    public static final String STATUS_NETWORK_EVENTS = "status_network_events";
    
    /**
     * Activate/deactivate network traffic log (boolean)
     */
    public static final String STATUS_NETWORK_TRAFFIC = "status_network_traffic";
    
    /**
     * Network traffic frequency (default = 60), in seconds
     */
    public static final String FREQUENCY_NETWORK_TRAFFIC = "frequency_network_traffic";
    
    /**
     * Activate/deactivate magnetometer log (boolean)
     */
    public static final String STATUS_MAGNETOMETER = "status_magnetometer";
    
    /**
     * Magnetometer frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_MAGNETOMETER = "frequency_magnetometer";
    
    /**
     * Activate/deactivate barometer log (boolean)
     */
    public static final String STATUS_BAROMETER = "status_barometer";
    
    /**
     * Barometer frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_BAROMETER = "frequency_barometer";
    
    /**
     * Activate/deactivate processor log (boolean)
     */
    public static final String STATUS_PROCESSOR = "status_processor";
    
    /**
     * Processor frequency (default = 10), in seconds
     */
    public static final String FREQUENCY_PROCESSOR = "frequency_processor";
    
    /**
     * Activate/deactivate timezone log (boolean)
     */
    public static final String STATUS_TIMEZONE = "status_timezone";
    
    /**
     * Timezone frequency (default = 3600) in seconds
     */
    public static final String FREQUENCY_TIMEZONE = "frequency_timezone";
    
    /**
     * Activate/deactivate proximity log (boolean)
     */
    public static final String STATUS_PROXIMITY = "status_proximity";
    
    /**
     * Proximity frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_PROXIMITY = "frequency_proximity";
    
    /**
     * Activate/deactivate rotation log (boolean)
     */
    public static final String STATUS_ROTATION = "status_rotation";
    
    /**
     * Rotation frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ROTATION = "frequency_rotation";
    
    /**
     * Activate/deactivate screen usage log (boolean)
     */
    public static final String STATUS_SCREEN = "status_screen";
    
    /**
     * Activate/deactivate temperature sensor log (boolean)
     */
    public static final String STATUS_TEMPERATURE = "status_temperature";
    
    /**
     * Temperature frequency in milliseconds: e.g., <br/>
     * 0 - fastest <br/>
     * 20000 - game <br/>
     * 60000 - UI <br/>
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_TEMPERATURE = "frequency_temperature";
    
    /**
     * Activate/deactivate telephony log (boolean)
     */
    public static final String STATUS_TELEPHONY = "status_telephony";
    
    /**
     * Activate/deactivate wifi scanning log (boolean)
     */
    public static final String STATUS_WIFI = "status_wifi";
    
    /**
     * Wifi scan frequency (default = 60), in seconds.
     */
    public static final String FREQUENCY_WIFI = "frequency_wifi";
    
    /**
     * Activate/deactivate mobile ESM (boolean)
     */
    public static final String STATUS_ESM = "status_esm";
    
    /**
     * Activate/deactivate MQTT client (boolean)
     */
    public static final String STATUS_MQTT = "status_mqtt";
    
    /**
     * MQTT Server IP/URL
     */
    public static final String MQTT_SERVER = "mqtt_server";
    
    /**
     * MQTT Server port (default = 1883)
     */
    public static final String MQTT_PORT = "mqtt_port";
    
    /**
     * MQTT Client username
     */
    public static final String MQTT_USERNAME = "mqtt_username";
    
    /**
     * MQTT Client password
     */
    public static final String MQTT_PASSWORD = "mqtt_password";
    
    /**
     * MQTT Client keep alive (default = 600), in seconds
     */
    public static final String MQTT_KEEP_ALIVE = "mqtt_keep_alive";
    
    /**
     * MQTT QoS (default = 2)<br/>
     * 0 - no guarantee <br/>
     * 1 - at least once <br/>
     * 2 - exactly once
     */
    public static final String MQTT_QOS = "mqtt_qos";
    
    /**
     * MQTT Connection protocol (default = tcp)<br/>
     * tcp - unsecure <br/>
     * ssl - secure 
     */
    public static final String MQTT_PROTOCOL = "mqtt_protocol";
    
    /**
     * Activate/deactivate AWARE webservice (boolean)
     */
    public static final String STATUS_WEBSERVICE = "status_webservice";
    
    /**
     * AWARE webservice URL
     */
    public static final String WEBSERVICE_SERVER = "webservice_server";
    
    /**
     * AWARE webservice sync only over Wi-Fi connection
     */
    public static final String WEBSERVICE_WIFI_ONLY = "webservice_wifi_only";
    
    /**
     * AWARE webservice frequency (default = 30), in minutes
     */
    public static final String FREQUENCY_WEBSERVICE = "frequency_webservice";
    
    /**
     * How frequently to clean old data?<br/>
     * 0 - never<br/>
     * 1 - weekly<br/>
     * 2 - monthly<br/>
     */
    public static final String FREQUENCY_CLEAN_OLD_DATA = "frequency_clean_old_data";
    
    private static boolean is_refreshing = false;
    private static boolean is_first_time = false;

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        switch(id) {
            case DIALOG_ERROR_ACCESSIBILITY:
                builder.setMessage("Please activate AWARE on the Accessibility Services!");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent accessibilitySettings = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        accessibilitySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
                        startActivity(accessibilitySettings);
                    }
                });
                dialog = builder.create();
            break;
            case DIALOG_ERROR_MISSING_PARAMETERS:
                builder.setMessage("Some parameters are missing...");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog = builder.create();
            break;
            case DIALOG_ERROR_MISSING_SENSOR:
                builder.setMessage("This device is missing this sensor.");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog = builder.create();
            break;
        }
        return dialog;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        //Start the Aware
        Intent startAware = new Intent( getApplicationContext(), Aware.class );
        startService(startAware);
        
        addPreferencesFromResource(R.xml.aware_preferences);
        setContentView(R.layout.aware_ui);
        
        navigationDrawer = (DrawerLayout) findViewById(R.id.aware_ui_main);
        navigationList = (ListView) findViewById(R.id.aware_navigation);
        navigationToggle = new ActionBarDrawerToggle( this, navigationDrawer, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if ( Build.VERSION.SDK_INT > 11 ) {
                    getActionBar().setTitle(getTitle());
                    invalidateOptionsMenu();
                }
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if( Build.VERSION.SDK_INT > 11 ) {
                    getActionBar().setTitle(getTitle());
                    invalidateOptionsMenu();
                }
            }
        };
        
        navigationDrawer.setDrawerListener(navigationToggle);
        
        String[] options = {"Stream", "Sensors", "Plugins", "Studies"};
        NavigationAdapter nav_adapter = new NavigationAdapter( getApplicationContext(), options);
        navigationList.setAdapter(nav_adapter);
        navigationList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            	
            	LinearLayout item_container = (LinearLayout) view.findViewById(R.id.nav_container);
            	item_container.setBackgroundColor(Color.DKGRAY);
            	
            	for( int i=0; i< navigationList.getChildCount(); i++ ) {
            		if( i != position ) {
            			LinearLayout other = (LinearLayout) navigationList.getChildAt(i);
            			LinearLayout other_item = (LinearLayout) other.findViewById(R.id.nav_container);
                    	other_item.setBackgroundColor(Color.TRANSPARENT);
            		}
            	}
            	
            	Bundle animations = ActivityOptions.makeCustomAnimation(Aware_Preferences.this, R.anim.anim_slide_in_left, R.anim.anim_slide_out_left).toBundle();
            	switch( position ) {
	            	case 0: //Stream
	            		Intent stream_ui = new Intent( Aware_Preferences.this, Stream_UI.class);
	            		startActivity(stream_ui, animations);
	            		break;
	            	case 1: //Sensors
	            		Intent sensors_ui = new Intent( Aware_Preferences.this, Aware_Preferences.class );
	            		startActivity(sensors_ui, animations);
	            		break;
            		case 2: //Plugins
            			Intent plugin_manager = new Intent( Aware_Preferences.this, Plugins_Manager.class );
            			startActivity(plugin_manager, animations);
            			break;
	            	case 3: //Studies
	            		if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) {
                            new Async_StudyData().execute(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                        } else {
                            Intent join_study = new Intent( Aware_Preferences.this, CameraStudy.class );
                            startActivityForResult(join_study, REQUEST_JOIN_STUDY, animations);
                        }
	            		break;
            	}
            	
                navigationDrawer.closeDrawer(navigationList);
            }
        });

        if( getActionBar() != null ) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        SharedPreferences prefs = getSharedPreferences( getPackageName(), Context.MODE_PRIVATE );
    	if( prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0 ) {
            is_first_time = true;
    		PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
    		prefs.edit().commit(); //commit changes
    	} else {
    		PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, false);
    	}
    	
    	Map<String,?> defaults = prefs.getAll();
        for(Map.Entry<String, ?> entry : defaults.entrySet()) {
            if( Aware.getSetting(getApplicationContext(), entry.getKey()).length() == 0 ) {
                Aware.setSetting(getApplicationContext(), entry.getKey(), entry.getValue());
            }
        }
        
    	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0 ) {
        	UUID uuid = UUID.randomUUID();
            Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, uuid.toString());
    	}
    }

    private class Async_StudyData extends AsyncTask<String, Void, JSONObject> {

        private String study_url = "";
        private ProgressDialog loader;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loader = new ProgressDialog(Aware_Preferences.this);
            loader.setTitle("Loading study");
            loader.setMessage("Please wait...");
            loader.setCancelable(false);
            loader.setIndeterminate(true);
            loader.show();
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            study_url = params[0];
            String study_api_key = study_url.substring(study_url.lastIndexOf("/")+1, study_url.length());

            HttpResponse request = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/webservice/client_get_study_info/" + study_api_key);
            if( request != null && request.getStatusLine().getStatusCode() == 200 ) {
                try {
                    String json_str = EntityUtils.toString(request.getEntity());
                    if( json_str.equals("[]") ) {
                        return null;
                    }
                    JSONObject study_data = new JSONObject(json_str);
                    return study_data;
                } catch (ParseException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);

            loader.dismiss();

            if( result == null ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Preferences.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.setTitle("Study information");
                builder.setMessage("Unable to retrieve study's information. Please, try again later.");
                builder.show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Preferences.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Clearing settings... please wait", Toast.LENGTH_LONG).show();
                        Aware.reset(getApplicationContext());
                    }
                });
                builder.setTitle("Study information");
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View study_ui = inflater.inflate(R.layout.study_info, null);
                TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
                TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
                TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);

                try {
                    study_name.setText((result.getString("study_name").length()>0 ? result.getString("study_name"): "Not available"));
                    study_description.setText((result.getString("study_description").length()>0?result.getString("study_description"):"Not available."));
                    study_pi.setText("PI: " + result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                builder.setView(study_ui);
                builder.show();
            }
        }
    }

    private class Async_SensorLoading extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected void onPreExecute() {
    		super.onPreExecute();
    		is_refreshing = true;
    	}
    	
    	@Override
    	protected Void doInBackground(Void... params) {
    		publishProgress();
    		return null;
    	}
    	
    	@Override
    	protected void onProgressUpdate(Void... values) {
    		super.onProgressUpdate(values);
    		developerOptions();
    		servicesOptions();
    	}
    	
    	@Override
    	protected void onPostExecute(Void result) {
    		super.onPostExecute(result);
    		is_refreshing = false; 		
    	}
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	if( ! is_refreshing ) {
    		new Async_SensorLoading().execute();
    	}
    	if( Aware.getSetting(getApplicationContext(), "study_id").length() == 0 ) {
            if( is_first_time ) navigationDrawer.openDrawer(android.view.Gravity.LEFT);
        }

        if( Aware.getSetting( getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true") && ! isAccessibilityServiceActive() ) {
            Intent accessibilitySettings = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilitySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(accessibilitySettings);
            Toast.makeText(getApplicationContext(), getResources().getText(R.string.aware_activate_accessibility), Toast.LENGTH_LONG).show();
    	}
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == REQUEST_JOIN_STUDY ) {
            if( resultCode == RESULT_OK ) {
            	Intent study_config = new Intent(this, StudyConfig.class);
                study_config.putExtra("study_url", data.getStringExtra("study_url"));
                startService(study_config);
            }
        }
    }
    
    @Override
    @Deprecated
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    	super.onPreferenceTreeClick(preferenceScreen, preference);
        if( getActionBar() != null ) {
            getActionBar().show();
            getActionBar().setDisplayUseLogoEnabled(true);
            getActionBar().setDisplayShowHomeEnabled(true);
        }
    	return true;
    }
    
    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     * @param context
     * @param configs
     */
    protected static void applySettings( Context context, JSONArray configs ) {
    	JSONArray plugins = null;
    	
    	//Apply all settings in AWARE Client
		for( int i=0; i<configs.length(); i++ ) {
			try {
				JSONObject conf = configs.getJSONObject(i);
				if( conf.has("plugins") ) {
					plugins = conf.getJSONArray("plugins");
				} else {
					Aware.setSetting( context, conf.getString("setting"), conf.get("value") );
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		if( plugins != null ) {
			//set all the configs also on the plugins...
			for( int j=0; j<plugins.length(); j++) {
				for( int i = 0; i < configs.length(); i++) {
					try {
						JSONObject conf = configs.getJSONObject(i);
						if( conf.has("plugins") ) continue;
						
						ContentValues newSettings = new ContentValues();
						newSettings.put(Aware_Settings.SETTING_KEY, conf.getString("setting"));
						newSettings.put(Aware_Settings.SETTING_VALUE, conf.get("value").toString() );
						newSettings.put(Aware_Settings.SETTING_PACKAGE_NAME, plugins.getString(j));
						context.getContentResolver().insert(Aware_Settings.CONTENT_URI, newSettings);
					}catch(JSONException e) {
						e.printStackTrace();
					}
				}
			}
			
			//Now start plugins
			for( int j=0;j<plugins.length();j++ ) {
				try {
					Aware.startPlugin( context, plugins.getString(j) );
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
    }
    
    public static class StudyConfig extends IntentService {
    	public StudyConfig() {
			super("Study Config Service");
		}

		@Override
    	protected void onHandleIntent(Intent intent) {
			String study_url = intent.getStringExtra("study_url");
			
			if( Aware.DEBUG ) Log.d(Aware.TAG, "Scanned: " + study_url);
			
			if( study_url.startsWith("https://api.awareframework.com/") ) {

				//Request study settings
				ArrayList<NameValuePair> data = new ArrayList<NameValuePair>();
				data.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID)));
				HttpResponse answer = new Https(getApplicationContext()).dataPOST(study_url, data);
				try {
                    String json_str = EntityUtils.toString(answer.getEntity());
                    Log.d(Aware.TAG, "Server answer: " + json_str );

					JSONArray configs_study = new JSONArray(json_str);
					if( configs_study.getJSONObject(0).has("message") ) {
                        Toast.makeText(getApplicationContext(), "This study is not available.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
                    mBuilder.setSmallIcon(R.drawable.ic_action_aware_studies);
                    mBuilder.setContentTitle("AWARE");
                    mBuilder.setContentText("Thanks for joining!");
                    mBuilder.setDefaults(Notification.DEFAULT_ALL);
                    mBuilder.setAutoCancel(true);

                    NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notManager.notify(new Random(System.currentTimeMillis()).nextInt(), mBuilder.build());

					if( Aware.DEBUG ) Log.d(Aware.TAG, "Study configs: " + configs_study.toString(5));
					
					//add webservice automatically to configs
					JSONObject webservice_server = new JSONObject();
					webservice_server.put("setting", Aware_Preferences.WEBSERVICE_SERVER);
					webservice_server.put("value", study_url);
					
					JSONObject status_webservice = new JSONObject();
					status_webservice.put("setting", Aware_Preferences.STATUS_WEBSERVICE);
					status_webservice.put("value", true);
					
					JSONObject study_start = new JSONObject();
					study_start.put("setting", "study_start");
					study_start.put("value", System.currentTimeMillis());
					
					configs_study.put(status_webservice);
					configs_study.put(webservice_server);
					configs_study.put(study_start);
					
					//Apply new configurations in AWARE Client
					applySettings(getApplicationContext(), configs_study);
					
					Intent apply_settings = new Intent(Aware.ACTION_AWARE_REFRESH);
					sendBroadcast(apply_settings);
					
					Intent preferences = new Intent( getApplicationContext(), Aware_Preferences.class);
					preferences.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK );
					startActivity(preferences);
					
					//Send data to server
					Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
					sendBroadcast(sync);
					
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
          	}
    	}
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        navigationToggle.syncState();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        navigationToggle.onConfigurationChanged(newConfig);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if( navigationToggle.onOptionsItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }
    
    public class UIUpdater extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if( ! is_refreshing ) {
    			new Async_SensorLoading().execute();
    		}
    	}
    }
    
    /**
     * Navigation adapter
     * @author denzil
     *
     */
    public class NavigationAdapter extends ArrayAdapter<String> {
        private final String[] items;
        private final LayoutInflater inflater;
        
        public NavigationAdapter(Context context, String[] items) {
            super(context, R.layout.aware_navigation_item, items);
            this.items = items;
            this.inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.aware_navigation_item, parent, false);
            ImageView nav_icon = (ImageView) row.findViewById(R.id.nav_placeholder);
            TextView nav_title = (TextView) row.findViewById(R.id.nav_title);
            
            switch( position ) {
                case 0:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_stream);
                    break;
                case 1:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_sensors);
                    row.setBackgroundColor(Color.DKGRAY);
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
    
    /**
     * Check if the accessibility service for AWARE Aware is active
     * @return boolean isActive
     */
    private boolean isAccessibilityServiceActive() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if( accessibilityManager.isEnabled() ) {
            List<ServiceInfo> accessibilityServices = accessibilityManager.getAccessibilityServiceList();
            for( ServiceInfo s : accessibilityServices ) {
                if( s.name.equalsIgnoreCase("com.aware.Applications") || s.name.equalsIgnoreCase("com.aware.ApplicationsJB") ) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Developer UI options
     * - Debug flag
     * - Debug tag
     * - AWARE updates
     * - Device ID
     */
    private void developerOptions() {
        final CheckBoxPreference debug_flag = (CheckBoxPreference) findPreference(Aware_Preferences.DEBUG_FLAG);
        debug_flag.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_FLAG).equals("true"));
        debug_flag.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.DEBUG = debug_flag.isChecked();
                Aware.setSetting(getApplicationContext(),Aware_Preferences.DEBUG_FLAG, debug_flag.isChecked());
                return true;
            }
        });
        
        final EditTextPreference debug_tag = (EditTextPreference) findPreference( Aware_Preferences.DEBUG_TAG );
        debug_tag.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG));
        debug_tag.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.TAG = (String) newValue;
                Aware.setSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG, (String) newValue);
                return true;
            }
        });
        
        final CheckBoxPreference auto_update = (CheckBoxPreference) findPreference(Aware_Preferences.AWARE_AUTO_UPDATE);
        auto_update.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.AWARE_AUTO_UPDATE).equals("true"));
        
        PackageInfo awareInfo = null;
		try {
			awareInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		auto_update.setSummary("Current version is " + ((awareInfo != null)?awareInfo.versionCode:"???"));
        auto_update.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.AWARE_AUTO_UPDATE, auto_update.isChecked());
                return true;
            }
        });
        
        final CheckBoxPreference debug_db_slow = (CheckBoxPreference) findPreference(Aware_Preferences.DEBUG_DB_SLOW);
        debug_db_slow.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_DB_SLOW).equals("true"));
        debug_db_slow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.DEBUG_DB_SLOW, debug_db_slow.isChecked());
                return true;
            }
        });
        
        final EditTextPreference device_id = (EditTextPreference) findPreference(Aware_Preferences.DEVICE_ID);
        device_id.setSummary("UUID: " + Aware.getSetting(getApplicationContext(), DEVICE_ID));
        device_id.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        device_id.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, (String) newValue);
                device_id.setSummary("UUID: " + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                return true;
            }
        });
    }
    
    /**
     * AWARE services UI components
     */
    private void servicesOptions() {
        esm();
        accelerometer();
        applications();
        barometer();
        battery();
        bluetooth();
        communication();
        gyroscope();
        light();
        linear_accelerometer();
        locations();
        magnetometer();
        network();
        screen();
        wifi();
        processor();
        timeZone();
        proximity();
        rotation();
        telephony();
        logging();
        gravity();
        temperature();
    }
    
    /**
     * ESM module settings UI
     */
    private void esm() {
        final CheckBoxPreference esm = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ESM);
        esm.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM).equals("true"));
        esm.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM, esm.isChecked());
                if(esm.isChecked()) {
                    framework.startESM();
                }else {
                    framework.stopESM();
                }
                return true;
            }
        });
    }
    
    /**
     * Temperature module settings UI
     */
    private void temperature() {
    	final PreferenceScreen temp_pref = (PreferenceScreen) findPreference("temperature");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
    	if( temp != null ) {
    		temp_pref.setSummary(temp_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		temp_pref.setSummary(temp_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference temperature = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_TEMPERATURE );
        temperature.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_TEMPERATURE).equals("true"));
        temperature.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_TEMPERATURE) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    temperature.setChecked(false);
                    Aware.setSetting( getApplicationContext(), Aware_Preferences.STATUS_TEMPERATURE, false);
                    return false;
                }
                
                Aware.setSetting( getApplicationContext(), Aware_Preferences.STATUS_TEMPERATURE, temperature.isChecked());
                if( temperature.isChecked() ) {
                    framework.startTemperature();
                }else {
                    framework.stopTemperature();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_temperature = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_TEMPERATURE);
        frequency_temperature.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TEMPERATURE));
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TEMPERATURE).length() > 0 ) {
        	frequency_temperature.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TEMPERATURE) + " microseconds");
        }
        frequency_temperature.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TEMPERATURE, (String) newValue);
                frequency_temperature.setSummary((String) newValue + " microseconds");
                framework.startTemperature();
                return true;
            }
        });
    }
    
    /**
     * Accelerometer module settings UI
     */
    private void accelerometer() {
        
    	final PreferenceScreen accel_pref = (PreferenceScreen) findPreference("accelerometer");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    	if( temp != null ) {
    		accel_pref.setSummary(accel_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		accel_pref.setSummary(accel_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ACCELEROMETER);
        accelerometer.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER).equals("true"));
        accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    accelerometer.setChecked(false);
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, accelerometer.isChecked());
                if(accelerometer.isChecked()) {
                    framework.startAccelerometer();
                }else {
                    framework.stopAccelerometer();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_accelerometer = (EditTextPreference) findPreference( Aware_Preferences.FREQUENCY_ACCELEROMETER );
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER).length() > 0 ) {
        	frequency_accelerometer.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER) + " microseconds");
        }
        frequency_accelerometer.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_ACCELEROMETER));
        frequency_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_ACCELEROMETER, (String) newValue);
                frequency_accelerometer.setSummary((String) newValue + " microseconds");
                framework.startAccelerometer();
                return true;
            }
        });
        
    }
    
    /**
     * Linear Accelerometer module settings UI
     */
    private void linear_accelerometer() {
        
    	final PreferenceScreen linear_pref = (PreferenceScreen) findPreference("linear_accelerometer");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    	if( temp != null ) {
    		linear_pref.setSummary(linear_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		linear_pref.setSummary(linear_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference linear_accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER);
        linear_accelerometer.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true"));
        linear_accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    linear_accelerometer.setChecked(false);
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, false);
                    return false;
                }
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, linear_accelerometer.isChecked());
                if(linear_accelerometer.isChecked()) {
                    framework.startLinearAccelerometer();
                }else {
                    framework.stopLinearAccelerometer();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_linear_accelerometer = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER).length() > 0 ) {
        	frequency_linear_accelerometer.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER) + " microseconds");
        }
        frequency_linear_accelerometer.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER));
        frequency_linear_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER, (String) newValue);
                frequency_linear_accelerometer.setSummary((String) newValue + " microseconds");
                framework.startLinearAccelerometer();
                return true;
            }
        });
        
    }
    
    /**
     * Applications module settings UI
     */
    private void applications() {
    	final CheckBoxPreference notifications = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NOTIFICATIONS);
        notifications.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_NOTIFICATIONS).equals("true"));
        notifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if( isAccessibilityServiceActive() && notifications.isChecked() ) {
					Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NOTIFICATIONS, notifications.isChecked());
					notifications.setChecked(true);
					framework.startApplications();
					return true;
				}
				if (! isAccessibilityServiceActive() ) {
					showDialog(Aware_Preferences.DIALOG_ERROR_ACCESSIBILITY);
				}
				Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NOTIFICATIONS, false);
				notifications.setChecked(false);
				return false;
			}
		});
        final CheckBoxPreference crashes = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_CRASHES);
        crashes.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CRASHES).equals("true"));
        crashes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if( isAccessibilityServiceActive() && crashes.isChecked() ) {
					Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_CRASHES, crashes.isChecked());
					crashes.setChecked(true);
					framework.startApplications();
					return true;
				}
				if (! isAccessibilityServiceActive() ) {
					showDialog(Aware_Preferences.DIALOG_ERROR_ACCESSIBILITY);
				}
				Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_CRASHES, false);
				crashes.setChecked(false);
				return false;
			}
		});
    	final CheckBoxPreference applications = (CheckBoxPreference) findPreference("status_applications");
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true") && ! isAccessibilityServiceActive() ) {
            showDialog(Aware_Preferences.DIALOG_ERROR_ACCESSIBILITY);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS, false );
            framework.stopApplications();
        }
        applications.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true"));
        applications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( isAccessibilityServiceActive() && applications.isChecked() ) {
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS, true);
                    applications.setChecked(true);
                    framework.startApplications();
                    return true;
                }else {
                    if( ! isAccessibilityServiceActive() ) {
                        showDialog(Aware_Preferences.DIALOG_ERROR_ACCESSIBILITY);
                    }  
                    
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS, false);
                    applications.setChecked(false);
                    
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NOTIFICATIONS, false);
                    notifications.setChecked(false);
                    
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_CRASHES, false);
                    crashes.setChecked(false);
                    
                    framework.stopApplications();
                    return false;
                }
            }
        });
        
        final EditTextPreference frequency_applications = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_APPLICATIONS);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS).length() > 0 ) {
        	frequency_applications.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS) + " seconds");
        }
        frequency_applications.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS));
        frequency_applications.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_APPLICATIONS, (String) newValue);
                frequency_applications.setSummary( (String) newValue + " seconds");
                framework.startApplications();
                return true;
            }
        });
        
        final CheckBoxPreference installations = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_INSTALLATIONS);
        installations.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_INSTALLATIONS).equals("true"));
        installations.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_INSTALLATIONS,installations.isChecked());
                if(installations.isChecked()) {
                    framework.startInstallations();
                }else {
                    framework.stopInstallations();
                }
                return true;
            }
        });
    }
    
    /**
     * Battery module settings UI
     */
    private void battery() {
        final CheckBoxPreference battery = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_BATTERY );
        battery.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY).equals("true"));
        battery.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_BATTERY, battery.isChecked());
                if(battery.isChecked()) {
                    framework.startBattery();
                }else {
                    framework.stopBattery();
                }
                return true;
            }
        });
    }
    
    /**
     * Bluetooth module settings UI
     */
    private void bluetooth() {
        final CheckBoxPreference bluetooth = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_BLUETOOTH );
        bluetooth.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_BLUETOOTH).equals("true"));
        bluetooth.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if( btAdapter == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    bluetooth.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_BLUETOOTH, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_BLUETOOTH, bluetooth.isChecked());
                if(bluetooth.isChecked()) {
                    framework.startBluetooth();
                }else {
                    framework.stopBluetooth();
                }
                return true;
            }
        });
        
        final EditTextPreference bluetoothInterval = (EditTextPreference) findPreference( Aware_Preferences.FREQUENCY_BLUETOOTH );
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH).length() > 0 ) {
        	bluetoothInterval.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH) + " seconds");
        }
        bluetoothInterval.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH));
        bluetoothInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BLUETOOTH, (String) newValue);
                bluetoothInterval.setSummary((String) newValue + " seconds");
                framework.startBluetooth();
                return true;
            }
        });
    }
    
    /**
     * Communication module settings UI
     */
    private void communication() {
        final CheckBoxPreference calls = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_CALLS );
        calls.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS).equals("true"));
        calls.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_CALLS, calls.isChecked());
                if(calls.isChecked()) {
                    framework.startCommunication();
                } else {
                    framework.stopCommunication();
                }
                return true;
            }
        });
        
        final CheckBoxPreference messages = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_MESSAGES );
        messages.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_MESSAGES).equals("true"));
        messages.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_MESSAGES, messages.isChecked());
                if(messages.isChecked()) {
                    framework.startCommunication();
                } else {
                    framework.stopCommunication();
                }
                return true;
            }
        });
        
        final CheckBoxPreference communication = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_COMMUNICATION_EVENTS);
        communication.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true"));
        communication.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_COMMUNICATION_EVENTS,communication.isChecked());
                if(communication.isChecked()) {
                    framework.startCommunication();
                } else {
                    framework.stopCommunication();
                }
                return true;
            }
        });
    }
    
    /**
     * Gravity module settings UI
     */
    private void gravity() {
        final PreferenceScreen grav_pref = (PreferenceScreen) findPreference("gravity");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY);
    	if( temp != null ) {
    		grav_pref.setSummary(grav_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		grav_pref.setSummary(grav_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference gravity = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GRAVITY);
        gravity.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_GRAVITY).equals("true"));
        gravity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gravity.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_GRAVITY, false);
                    return false;
                }
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_GRAVITY, gravity.isChecked());
                if(gravity.isChecked()) {
                    framework.startGravity();
                }else {
                    framework.stopGravity();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_gravity = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_GRAVITY);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY).length() > 0 ) {
        	frequency_gravity.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GRAVITY) + " microseconds");
        }
        frequency_gravity.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_GRAVITY));
        frequency_gravity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_GRAVITY, (String) newValue);
                frequency_gravity.setSummary((String) newValue + " microseconds");
                framework.startGravity();
                return true;
            }
        });
    }
    
    /**
     * Gyroscope module settings UI
     */
    private void gyroscope() {
        final PreferenceScreen gyro_pref = (PreferenceScreen) findPreference("gyroscope");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    	if( temp != null ) {
    		gyro_pref.setSummary(gyro_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		gyro_pref.setSummary(gyro_pref.getSummary().toString().replace("*", ""));
    	}
        
    	final CheckBoxPreference gyroscope = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GYROSCOPE);
        gyroscope.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_GYROSCOPE).equals("true"));
        gyroscope.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gyroscope.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_GYROSCOPE, false);
                    return false;
                }
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_GYROSCOPE, gyroscope.isChecked());
                if(gyroscope.isChecked()) {
                    framework.startGyroscope();
                }else {
                    framework.stopGyroscope();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_gyroscope = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_GYROSCOPE);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GYROSCOPE).length() > 0 ) {
        	frequency_gyroscope.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_GYROSCOPE) + " microseconds");
        }
        frequency_gyroscope.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_GYROSCOPE));
        frequency_gyroscope.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_GYROSCOPE, (String) newValue);
                frequency_gyroscope.setSummary((String) newValue + " microseconds");
                framework.startGyroscope();
                return true;
            }
        });
        
    }
    
    /**
     * Location module settings UI
     */
    private void locations() {
        final CheckBoxPreference location_gps = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_GPS);
        location_gps.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS).equals("true"));
        location_gps.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                LocationManager localMng = (LocationManager) getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();
                
                if( ! providers.contains(LocationManager.GPS_PROVIDER) ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_LOCATION_GPS, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS,location_gps.isChecked());
                if(location_gps.isChecked()) {
                    framework.startLocations();
                }else {
                    framework.stopLocations();
                }
                return true;
            }
        });
        
        final CheckBoxPreference location_network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_NETWORK);
        location_network.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true"));
        location_network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                LocationManager localMng = (LocationManager) getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();
                
                if( ! providers.contains(LocationManager.NETWORK_PROVIDER) ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_LOCATION_NETWORK, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_LOCATION_NETWORK, location_network.isChecked());
                if(location_network.isChecked()) {
                    framework.startLocations();
                }else {
                    framework.stopLocations();
                }
                return true;
            }
        });
        
        final EditTextPreference gpsInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_GPS);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS).length() > 0 ) {
        	gpsInterval.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS) + " seconds");
        }
        gpsInterval.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LOCATION_GPS));
        gpsInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LOCATION_GPS, (String) newValue);
                gpsInterval.setSummary((String) newValue + " seconds");
                framework.startLocations();
                return true;
            }
        });
        
        final EditTextPreference networkInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_NETWORK);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK).length() > 0 ) {
        	networkInterval.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK) + " seconds");
        }
        networkInterval.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LOCATION_NETWORK));
        networkInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LOCATION_NETWORK, (String) newValue);
                networkInterval.setSummary((String) newValue + " seconds");
                framework.startLocations();
                return true;
            }
        });
        
        final EditTextPreference gpsAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_GPS_ACCURACY);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY).length() > 0 ) {
        	gpsAccuracy.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY) + " meters");
        }
        gpsAccuracy.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY));
        gpsAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY, (String) newValue);
                gpsAccuracy.setSummary((String) newValue + " meters");
                framework.startLocations();
                return true;
            }
        });
        
        final EditTextPreference networkAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY).length() > 0 ) {
        	networkAccuracy.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY) + " meters");
        }
        networkAccuracy.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY));
        networkAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY, (String) newValue);
                networkAccuracy.setSummary((String) newValue + " meters");
                framework.startLocations();
                return true;
            }
        });
        
        final EditTextPreference expirateTime = (EditTextPreference) findPreference(Aware_Preferences.LOCATION_EXPIRATION_TIME);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME).length() > 0 ) {
        	expirateTime.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME) + " seconds");
        }
        expirateTime.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME));
        expirateTime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME, (String) newValue);
                expirateTime.setSummary((String) newValue + " seconds");
                framework.startLocations();
                return true;
            }
        });
    }

    /**
     * Network module settings UI
     */
    private void network() {
        final CheckBoxPreference network_traffic = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_TRAFFIC);
        network_traffic.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true"));
        network_traffic.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_TRAFFIC,network_traffic.isChecked());
                if(network_traffic.isChecked()) {
                    framework.startTraffic();
                }else {
                    framework.stopTraffic();
                }
                return true;
            }
        });
        
        final EditTextPreference frequencyTraffic = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC).length() > 0 ) {
        	frequencyTraffic.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC) + " seconds");
        }
        frequencyTraffic.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC));
        frequencyTraffic.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC, (String) newValue);
                frequencyTraffic.setSummary((String) newValue + " seconds");
                if( network_traffic.isChecked() ) {
                    framework.startTraffic();
                }
                return true;
            }
        });
        
        final CheckBoxPreference network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_EVENTS);
        network.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true"));
        network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_NETWORK_EVENTS,network.isChecked());
                if(network.isChecked()) {
                    framework.startNetwork();
                }else {
                    framework.stopNetwork();
                }
                return true;
            }
        });
    }
    
    /**
     * Screen module settings UI
     */
    private void screen () {
        final CheckBoxPreference screen = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_SCREEN);
        screen.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_SCREEN).equals("true"));
        screen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_SCREEN, screen.isChecked());
                if(screen.isChecked()) {
                    framework.startScreen();
                }else {
                    framework.stopScreen();
                }
                return true;
            }
        });
    }
    
    /**
     * WiFi module settings UI
     */
    private void wifi() {
        final CheckBoxPreference wifi = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_WIFI );
        wifi.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WIFI).equals("true"));
        wifi.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_WIFI,wifi.isChecked());
                if(wifi.isChecked()) {
                    framework.startWiFi();
                }else {
                    framework.stopWiFi();
                }
                return true;
            }
        });
        
        final EditTextPreference wifiInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WIFI);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WIFI).length() > 0 ) {
        	wifiInterval.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WIFI) + " seconds");
        }
        wifiInterval.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WIFI));
        wifiInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WIFI, (String) newValue);
                wifiInterval.setSummary((String) newValue + " seconds");
                framework.startWiFi();
                return true;
            }
        });
    }
    
    /**
     * Processor module settings UI
     */
    private void processor() {
        final CheckBoxPreference processor = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_PROCESSOR);
        processor.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_PROCESSOR).equals("true"));
        processor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_PROCESSOR, processor.isChecked());
                if(processor.isChecked()) {
                    framework.startProcessor();
                }else {
                    framework.stopProcessor();
                }
                return true;
            }
        });
        
        final EditTextPreference frequencyProcessor = (EditTextPreference) findPreference( Aware_Preferences.FREQUENCY_PROCESSOR );
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROCESSOR).length() > 0 ) {
        	frequencyProcessor.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROCESSOR) + " seconds");
        }
        frequencyProcessor.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROCESSOR));
        frequencyProcessor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_PROCESSOR, (String) newValue);
                frequencyProcessor.setSummary((String) newValue + " seconds");
                framework.startProcessor();
                return true;
            }
        });
    }
    
    /**
     * TimeZone module settings UI
     */
    private void timeZone() {
        final CheckBoxPreference timeZone = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TIMEZONE);
        timeZone.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_TIMEZONE).equals("true"));
        timeZone.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_TIMEZONE,timeZone.isChecked());
                if(timeZone.isChecked()) {
                    framework.startTimeZone();
                }else {
                    framework.stopTimeZone();
                }
                return true;
            }
        });
        
        final EditTextPreference frequencyTimeZone = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_TIMEZONE);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE).length() > 0 ) {
        	frequencyTimeZone.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE) + " seconds");
        }
        frequencyTimeZone.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE));
        frequencyTimeZone.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_TIMEZONE, (String) newValue);
                frequencyTimeZone.setSummary((String) newValue + " seconds");
                framework.startTimeZone();
                return true;
            }
        });
    }
    
    /**
     * Light module settings UI
     */
    private void light() {
        
    	final PreferenceScreen light_pref = (PreferenceScreen) findPreference("light");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);
    	if( temp != null ) {
    		light_pref.setSummary(light_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		light_pref.setSummary(light_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference light = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LIGHT);
        light.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_LIGHT).equals("true"));
        light.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    light.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_LIGHT, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_LIGHT, light.isChecked());
                if(light.isChecked()) {
                    framework.startLight();
                }else {
                    framework.stopLight();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_light = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LIGHT);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT).length() > 0 ) {
        	frequency_light.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT) + " microseconds");
        }
        frequency_light.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LIGHT));
        frequency_light.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.FREQUENCY_LIGHT, (String) newValue);
                frequency_light.setSummary((String) newValue + " microseconds");
                framework.startLight();
                return true;
            }
        });
        
    }
    
    /**
     * Magnetometer module settings UI
     */
    private void magnetometer() {
        final PreferenceScreen magno_pref = (PreferenceScreen) findPreference("magnetometer");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    	if( temp != null ) {
    		magno_pref.setSummary(magno_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		magno_pref.setSummary(magno_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference magnetometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MAGNETOMETER);
        magnetometer.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_MAGNETOMETER).equals("true"));
        magnetometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    magnetometer.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_MAGNETOMETER, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MAGNETOMETER, magnetometer.isChecked());
                if(magnetometer.isChecked()) {
                    framework.startMagnetometer();
                }else {
                    framework.stopMagnetometer();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_magnetometer = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_MAGNETOMETER);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER).length() > 0 ) {
        	frequency_magnetometer.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER) + " microseconds");
        }
        frequency_magnetometer.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER));
        frequency_magnetometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_MAGNETOMETER, (String) newValue);
                frequency_magnetometer.setSummary((String) newValue + " microseconds");
                framework.startMagnetometer();
                return true;
            }
        });
        
    }
    
    /**
     * Atmospheric Pressure module settings UI
     */
    private void barometer() {
        final PreferenceScreen baro_pref = (PreferenceScreen) findPreference("barometer");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);
    	if( temp != null ) {
    		baro_pref.setSummary(baro_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		baro_pref.setSummary(baro_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference pressure = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_BAROMETER);
        pressure.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_BAROMETER).equals("true"));
        pressure.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    pressure.setChecked(false);
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BAROMETER, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BAROMETER, pressure.isChecked());
                if(pressure.isChecked()) {
                    framework.startBarometer();
                }else {
                    framework.stopBarometer();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_pressure = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_BAROMETER);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BAROMETER).length() > 0 ) {
        	frequency_pressure.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BAROMETER) + " microseconds");
        }
        frequency_pressure.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BAROMETER));
        frequency_pressure.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_BAROMETER, (String) newValue);
                frequency_pressure.setSummary((String) newValue + " microseconds");
                framework.startBarometer();
                return true;
            }
        });
        
    }
    
    /**
     * Proximity module settings UI
     */
    private void proximity() {
        
    	final PreferenceScreen proxi_pref = (PreferenceScreen) findPreference("proximity");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    	if( temp != null ) {
    		proxi_pref.setSummary(proxi_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		proxi_pref.setSummary(proxi_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference proximity = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_PROXIMITY);
        proximity.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_PROXIMITY).equals("true"));
        proximity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    proximity.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_PROXIMITY, false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_PROXIMITY, proximity.isChecked());
                if(proximity.isChecked()) {
                    framework.startProximity();
                }else {
                    framework.stopProximity();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_proximity = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_PROXIMITY);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY).length() > 0 ) {
        	frequency_proximity.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY) + " microseconds");
        }
        frequency_proximity.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY));
        frequency_proximity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_PROXIMITY, (String) newValue);
                frequency_proximity.setSummary((String) newValue + " microseconds");
                framework.startProximity();
                return true;
            }
        });
    }
    
    /**
     * Rotation module settings UI
     */
    private void rotation() {
        
    	final PreferenceScreen rotation_pref = (PreferenceScreen) findPreference("rotation");
    	Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    	if( temp != null ) {
    		rotation_pref.setSummary(rotation_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() +" mA"));
    	} else {
    		rotation_pref.setSummary(rotation_pref.getSummary().toString().replace("*", ""));
    	}
    	
    	final CheckBoxPreference rotation = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ROTATION);
        rotation.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_ROTATION).equals("true"));
        rotation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null ) {
                    showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    rotation.setChecked(false);
                    Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_ROTATION,false);
                    return false;
                }
                
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_ROTATION, rotation.isChecked());
                if(rotation.isChecked()) {
                    framework.startRotation();
                }else {
                    framework.stopRotation();
                }
                return true;
            }
        });
        
        final EditTextPreference frequency_rotation = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_ROTATION);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION).length() > 0 ) {
        	frequency_rotation.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION) + " microseconds");
        }
        frequency_rotation.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION));
        frequency_rotation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ROTATION, (String) newValue);
                frequency_rotation.setSummary((String) newValue + " microseconds");
                framework.startRotation();
                return true;
            }
        });
        
    }
    
    /**
     * Telephony module settings UI
     */
    private void telephony() {
        final CheckBoxPreference telephony = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TELEPHONY);
        telephony.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_TELEPHONY).equals("true"));
        telephony.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.STATUS_TELEPHONY, telephony.isChecked());
                if(telephony.isChecked()) {
                    framework.startTelephony();
                }else {
                    framework.stopTelephony();
                }
                return true;
            }
        });
    }
    
    /**
     * Logging module settings UI components
     */
    private void logging() {
        webservices();
        mqtt();
    }
    
    /**
     * Webservices module settings UI
     */
    private void webservices() {
    	final CheckBoxPreference webservice = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_WEBSERVICE);
        webservice.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.STATUS_WEBSERVICE).equals("true"));
        webservice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                
                if( Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER).length() == 0 ) {
                    showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE, false);
                    webservice.setChecked(false);
                    return false;
                } else {
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE, webservice.isChecked());
                    if( webservice.isChecked() && Aware.getSetting(getApplicationContext(), WEBSERVICE_SERVER).length() > 0 ) {
                    	//setup and send data
                    	Intent study_config = new Intent(getApplicationContext(), StudyConfig.class);
                        study_config.putExtra("study_url", Aware.getSetting(getApplicationContext(), WEBSERVICE_SERVER));
                        startService(study_config);
                    }
                    return true;
                }
            }
        });
        
        final EditTextPreference webservice_server = (EditTextPreference) findPreference(Aware_Preferences.WEBSERVICE_SERVER);
        webservice_server.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER ).length() > 0 ) {
        	webservice_server.setSummary("Server: " + Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
        }
        webservice_server.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER, (String) newValue );
                webservice_server.setSummary("Server: " + (String) newValue );
                return true;
            }
        });
        
    	final CheckBoxPreference webservice_wifi_only = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
    	webservice_wifi_only.setChecked(Aware.getSetting(getApplicationContext(),Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true"));
    	webservice_wifi_only.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.WEBSERVICE_WIFI_ONLY, webservice_wifi_only.isChecked());
                return true;
            }
        });
    	
    	final EditTextPreference frequency_webservice = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WEBSERVICE);
    	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE).length() > 0 ) {
        	frequency_webservice.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE) + " minutes");
        }
    	frequency_webservice.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE));
    	frequency_webservice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE, (String) newValue);
                frequency_webservice.setSummary((String) newValue + " minutes");
                return true;
            }
        });
    	
    	final ListPreference clean_old_data = (ListPreference) findPreference( FREQUENCY_CLEAN_OLD_DATA );
    	if( Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0 ) {
        	String freq = Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA);
        	if(freq.equals("0")) {
        		clean_old_data.setSummary("Never");
        	} else if ( freq.equals("1") ) {
        		clean_old_data.setSummary("Weekly");
        	} else if ( freq.equals("2") ) {
        		clean_old_data.setSummary("Monthly");
        	}
        }
    	clean_old_data.setDefaultValue( Aware.getSetting(getApplicationContext(), FREQUENCY_CLEAN_OLD_DATA) );
    	clean_old_data.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),FREQUENCY_CLEAN_OLD_DATA, (String) newValue);
                if(((String)newValue).equals("0")) {
            		clean_old_data.setSummary("Never");
            	} else if ( ((String)newValue).equals("1") ) {
            		clean_old_data.setSummary("Weekly");
            	} else if ( ((String)newValue).equals("2") ) {
            		clean_old_data.setSummary("Monthly");
            	}
                return true;
            }
        });
    }
    
    /**
     * MQTT module settings UI
     */
    private void mqtt() {
        
        final CheckBoxPreference mqtt = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MQTT);
        mqtt.setChecked(Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_MQTT).equals("true"));
        mqtt.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_SERVER).length() == 0 ) {
                    showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    mqtt.setChecked(false);
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MQTT, false);
                    return false;
                } else {
                    Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_MQTT, mqtt.isChecked());
                    if(mqtt.isChecked()) {
                        framework.startMQTT();
                    }else {
                        framework.stopMQTT();
                    }
                    return true;
                }
            }
        });
        
        final EditTextPreference mqttServer = (EditTextPreference) findPreference( Aware_Preferences.MQTT_SERVER );
        mqttServer.setText(Aware.getSetting(getApplicationContext(),Aware_Preferences.MQTT_SERVER));
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_SERVER).length() > 0 ) {
        	mqttServer.setSummary("Server: " + Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_SERVER));
        }
        mqttServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(),Aware_Preferences.MQTT_SERVER, (String) newValue);
                mqttServer.setSummary("Server: " + (String) newValue);
                return true;
            }
        });
        
        final EditTextPreference mqttPort = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PORT);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PORT).length() > 0 ) {
        	mqttPort.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PORT));
        }
        mqttPort.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PORT));
        mqttPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_PORT, (String) newValue);
                return true;
            }
        });
        
        final EditTextPreference mqttUsername = (EditTextPreference) findPreference(Aware_Preferences.MQTT_USERNAME);
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_USERNAME).length() > 0 ) {
        	mqttUsername.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_USERNAME));
        }
        mqttUsername.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_USERNAME));
        mqttUsername.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_USERNAME, (String) newValue);
                return true;
            }
        });
        
        final EditTextPreference mqttPassword = (EditTextPreference) findPreference( Aware_Preferences.MQTT_PASSWORD);
        mqttPassword.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PASSWORD));
        mqttPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_PASSWORD, (String) newValue);
                return true;
            }
        });
        
        final EditTextPreference mqttKeepAlive = (EditTextPreference) findPreference( Aware_Preferences.MQTT_KEEP_ALIVE );
        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE).length() > 0 ) {
        	mqttKeepAlive.setSummary(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE) + " seconds");
        }
        mqttKeepAlive.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE));
        mqttKeepAlive.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_KEEP_ALIVE, (String) newValue);
                mqttKeepAlive.setSummary((String) newValue + " seconds");
                return true;
            }
        });
        
        final EditTextPreference mqttQoS = (EditTextPreference) findPreference(Aware_Preferences.MQTT_QOS);
        mqttQoS.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_QOS));
        mqttQoS.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_QOS, (String) newValue);
                return true;
            }
        });
        
        final EditTextPreference mqttProtocol = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PROTOCOL);
        mqttProtocol.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.MQTT_PROTOCOL));
        mqttProtocol.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MQTT_PROTOCOL, (String) newValue);
                return true;
            }
        });
    }
}