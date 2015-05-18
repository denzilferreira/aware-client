
package com.aware;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.aware.providers.Aware_Provider;
import com.aware.ui.Aware_Activity;
import com.aware.ui.Plugins_Manager;
import com.aware.utils.Https;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preferences dashboard for the AWARE Aware
 * Allows the researcher to configure all the modules, start and stop modules and where logging happens.
 * @author df
 *
 */
public class Aware_Preferences extends Aware_Activity {

    private static final int DIALOG_ERROR_MISSING_PARAMETERS = 2;
    private static final int DIALOG_ERROR_MISSING_SENSOR = 3;

    /**
     * Used to disable sensors that are not applicable
     */
    private static boolean is_watch = false;
    
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
     * Accelerometer frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * Gravity frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GRAVITY = "frequency_gravity";
    
    /**
     * Activate/deactivate gyroscope log (boolean)
     */
    public static final String STATUS_GYROSCOPE = "status_gyroscope";
    
    /**
     * Gyroscope frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * Light frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LIGHT = "frequency_light";
    
    /**
     * Activate/deactivate linear accelerometer log (boolean)
     */
    public static final String STATUS_LINEAR_ACCELEROMETER = "status_linear_accelerometer";
    
    /**
     * Linear accelerometer frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * Magnetometer frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_MAGNETOMETER = "frequency_magnetometer";
    
    /**
     * Activate/deactivate barometer log (boolean)
     */
    public static final String STATUS_BAROMETER = "status_barometer";
    
    /**
     * Barometer frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * Proximity frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_PROXIMITY = "frequency_proximity";
    
    /**
     * Activate/deactivate rotation log (boolean)
     */
    public static final String STATUS_ROTATION = "status_rotation";
    
    /**
     * Rotation frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * Temperature frequency in milliseconds: e.g., 
     * 0 - fastest 
     * 20000 - game 
     * 60000 - UI 
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
     * MQTT QoS (default = 2)
     * 0 - no guarantee 
     * 1 - at least once 
     * 2 - exactly once
     */
    public static final String MQTT_QOS = "mqtt_qos";
    
    /**
     * MQTT Connection protocol (default = tcp)
     * tcp - unsecure 
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
     * How frequently to clean old data?
     * 0 - never
     * 1 - weekly
     * 2 - monthly
     */
    public static final String FREQUENCY_CLEAN_OLD_DATA = "frequency_clean_old_data";

    /**
     * Activate/deactivate Android Wear data synching
     */
//    public static final String STATUS_ANDROID_WEAR = "status_android_wear";

    /**
     * Activate/deactivate keyboard logging
     */
    public static final String STATUS_KEYBOARD = "status_keyboard";

    private static final Aware framework = Aware.getService();
    private static SensorManager mSensorMgr;
    private static Context sContext;
    private static PreferenceActivity sPreferences;

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        switch(id) {
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
        is_watch = Aware.is_watch(this);
        sContext = getApplicationContext();
        sPreferences = this;

        //Start the Aware
        Intent startAware = new Intent( getApplicationContext(), Aware.class );
        startService(startAware);

        SharedPreferences prefs = getSharedPreferences( getPackageName(), Context.MODE_PRIVATE );
    	if( prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0 ) {
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

        addPreferencesFromResource(R.xml.aware_preferences);
        setContentView(R.layout.aware_ui);

        developerOptions();
        servicesOptions();
        logging();
    }

    /**
     * AWARE services UI components
     */
    public void servicesOptions() {
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
        gravity();
        temperature();
    }

    /**
     * ESM module settings UI
     */
    private void esm() {
        final PreferenceScreen mobile_esm = (PreferenceScreen) findPreference("esm");
        if( is_watch ) {
            mobile_esm.setEnabled(false);
            return;
        }

        final CheckBoxPreference esm = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ESM);
        esm.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_ESM).equals("true"));
        esm.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_ESM, esm.isChecked());
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
            temp_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference temperature = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_TEMPERATURE );
        temperature.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true"));
        temperature.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_TEMPERATURE) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    temperature.setChecked(false);
                    Aware.setSetting( sContext, Aware_Preferences.STATUS_TEMPERATURE, false);
                    return false;
                }

                Aware.setSetting( sContext, Aware_Preferences.STATUS_TEMPERATURE, temperature.isChecked());
                if( temperature.isChecked() ) {
                    framework.startTemperature();
                }else {
                    framework.stopTemperature();
                }
                return true;
            }
        });

        final ListPreference frequency_temperature = (ListPreference) findPreference( FREQUENCY_TEMPERATURE );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_TEMPERATURE).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_TEMPERATURE);
            frequency_temperature.setSummary(freq);
        }
        frequency_temperature.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_TEMPERATURE) );
        frequency_temperature.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_TEMPERATURE, (String) newValue);
                frequency_temperature.setSummary( (String)newValue);
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
            accel_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ACCELEROMETER);
        accelerometer.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true"));
        accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    accelerometer.setChecked(false);
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_ACCELEROMETER, false);
                    return false;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_ACCELEROMETER, accelerometer.isChecked());
                if(accelerometer.isChecked()) {
                    framework.startAccelerometer();
                }else {
                    framework.stopAccelerometer();
                }
                return true;
            }
        });

        final ListPreference frequency_accelerometer = (ListPreference) findPreference( FREQUENCY_ACCELEROMETER );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_ACCELEROMETER).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_ACCELEROMETER);
            frequency_accelerometer.setSummary(freq);
        }
        frequency_accelerometer.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_ACCELEROMETER) );
        frequency_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_ACCELEROMETER, (String) newValue);
                frequency_accelerometer.setSummary( (String)newValue);
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
            linear_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference linear_accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER);
        linear_accelerometer.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true"));
        linear_accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    linear_accelerometer.setChecked(false);
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, false);
                    return false;
                }
                Aware.setSetting(sContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, linear_accelerometer.isChecked());
                if(linear_accelerometer.isChecked()) {
                    framework.startLinearAccelerometer();
                }else {
                    framework.stopLinearAccelerometer();
                }
                return true;
            }
        });

        final ListPreference frequency_linear_accelerometer = (ListPreference) findPreference( FREQUENCY_LINEAR_ACCELEROMETER );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER);
            frequency_linear_accelerometer.setSummary(freq);
        }
        frequency_linear_accelerometer.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_LINEAR_ACCELEROMETER) );
        frequency_linear_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_LINEAR_ACCELEROMETER, (String) newValue);
                frequency_linear_accelerometer.setSummary( (String)newValue);
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
        notifications.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_NOTIFICATIONS).equals("true"));
        notifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Applications.isAccessibilityServiceActive(sContext) && notifications.isChecked() ) {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_NOTIFICATIONS, notifications.isChecked());
                    notifications.setChecked(true);
                    framework.startApplications();
                    return true;
                }
                Applications.isAccessibilityServiceActive(sContext);
                Aware.setSetting(sContext, Aware_Preferences.STATUS_NOTIFICATIONS, false);
                notifications.setChecked(false);
                return false;
            }
        });
        final CheckBoxPreference keyboard = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_KEYBOARD);
        keyboard.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_KEYBOARD).equals("true"));
        keyboard.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Applications.isAccessibilityServiceActive(sContext) && keyboard.isChecked() ) {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_KEYBOARD, keyboard.isChecked());
                    keyboard.setChecked(true);
                    framework.startApplications();
                    framework.startKeyboard();
                    return true;
                }
                Applications.isAccessibilityServiceActive(sContext);
                Aware.setSetting(sContext, Aware_Preferences.STATUS_KEYBOARD, false);
                keyboard.setChecked(false);
                framework.stopKeyboard();
                return false;
            }
        });
        final CheckBoxPreference crashes = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_CRASHES);
        crashes.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_CRASHES).equals("true"));
        crashes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Applications.isAccessibilityServiceActive(sContext) && crashes.isChecked() ) {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_CRASHES, crashes.isChecked());
                    crashes.setChecked(true);
                    framework.startApplications();
                    return true;
                }
                Applications.isAccessibilityServiceActive(sContext);
                Aware.setSetting(sContext, Aware_Preferences.STATUS_CRASHES, false);
                crashes.setChecked(false);
                return false;
            }
        });
        final CheckBoxPreference applications = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_APPLICATIONS);
        if( Aware.getSetting(sContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true") && ! Applications.isAccessibilityServiceActive(sContext) ) {
            Aware.setSetting(sContext, Aware_Preferences.STATUS_APPLICATIONS, false );
            framework.stopApplications();
        }
        applications.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true"));
        applications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Applications.isAccessibilityServiceActive(sContext) && applications.isChecked() ) {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_APPLICATIONS, true);
                    applications.setChecked(true);
                    framework.startApplications();
                    return true;
                }else {
                    Applications.isAccessibilityServiceActive(sContext);

                    Aware.setSetting(sContext, Aware_Preferences.STATUS_APPLICATIONS, false);
                    applications.setChecked(false);

                    Aware.setSetting(sContext, Aware_Preferences.STATUS_NOTIFICATIONS, false);
                    notifications.setChecked(false);

                    Aware.setSetting(sContext, Aware_Preferences.STATUS_CRASHES, false);
                    crashes.setChecked(false);

                    framework.stopApplications();
                    return false;
                }
            }
        });

        final EditTextPreference frequency_applications = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_APPLICATIONS);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_APPLICATIONS).length() > 0 ) {
            frequency_applications.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_APPLICATIONS) + " seconds");
        }
        frequency_applications.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_APPLICATIONS));
        frequency_applications.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_APPLICATIONS, (String) newValue);
                frequency_applications.setSummary( (String) newValue + " seconds");
                framework.startApplications();
                return true;
            }
        });

        final CheckBoxPreference installations = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_INSTALLATIONS);
        installations.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_INSTALLATIONS).equals("true"));
        installations.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_INSTALLATIONS,installations.isChecked());
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
        battery.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_BATTERY).equals("true"));
        battery.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_BATTERY, battery.isChecked());
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
        bluetooth.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_BLUETOOTH).equals("true"));
        bluetooth.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if( btAdapter == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    bluetooth.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_BLUETOOTH, false);
                    return false;
                }

                Aware.setSetting(sContext,Aware_Preferences.STATUS_BLUETOOTH, bluetooth.isChecked());
                if(bluetooth.isChecked()) {
                    framework.startBluetooth();
                }else {
                    framework.stopBluetooth();
                }
                return true;
            }
        });

        final EditTextPreference bluetoothInterval = (EditTextPreference) findPreference( Aware_Preferences.FREQUENCY_BLUETOOTH );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_BLUETOOTH).length() > 0 ) {
            bluetoothInterval.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_BLUETOOTH) + " seconds");
        }
        bluetoothInterval.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_BLUETOOTH));
        bluetoothInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_BLUETOOTH, (String) newValue);
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
        final PreferenceScreen communications = (PreferenceScreen) findPreference("communication");
        if( is_watch ) {
            communications.setEnabled(false);
            return;
        }

        final CheckBoxPreference calls = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_CALLS );
        calls.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_CALLS).equals("true"));
        calls.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_CALLS, calls.isChecked());
                if(calls.isChecked()) {
                    framework.startCommunication();
                } else {
                    framework.stopCommunication();
                }
                return true;
            }
        });

        final CheckBoxPreference messages = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_MESSAGES );
        messages.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_MESSAGES).equals("true"));
        messages.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_MESSAGES, messages.isChecked());
                if(messages.isChecked()) {
                    framework.startCommunication();
                } else {
                    framework.stopCommunication();
                }
                return true;
            }
        });

        final CheckBoxPreference communication = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_COMMUNICATION_EVENTS);
        communication.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true"));
        communication.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_COMMUNICATION_EVENTS,communication.isChecked());
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
            grav_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference gravity = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GRAVITY);
        gravity.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_GRAVITY).equals("true"));
        gravity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gravity.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_GRAVITY, false);
                    return false;
                }
                Aware.setSetting(sContext,Aware_Preferences.STATUS_GRAVITY, gravity.isChecked());
                if(gravity.isChecked()) {
                    framework.startGravity();
                }else {
                    framework.stopGravity();
                }
                return true;
            }
        });

        final ListPreference frequency_gravity = (ListPreference) findPreference( FREQUENCY_GRAVITY );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_GRAVITY).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_GRAVITY);
            frequency_gravity.setSummary(freq);
        }
        frequency_gravity.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_GRAVITY) );
        frequency_gravity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_GRAVITY, (String) newValue);
                frequency_gravity.setSummary( (String)newValue);
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
            gyro_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference gyroscope = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GYROSCOPE);
        gyroscope.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_GYROSCOPE).equals("true"));
        gyroscope.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gyroscope.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_GYROSCOPE, false);
                    return false;
                }
                Aware.setSetting(sContext,Aware_Preferences.STATUS_GYROSCOPE, gyroscope.isChecked());
                if(gyroscope.isChecked()) {
                    framework.startGyroscope();
                }else {
                    framework.stopGyroscope();
                }
                return true;
            }
        });

        final ListPreference frequency_gyroscope = (ListPreference) findPreference( FREQUENCY_GYROSCOPE );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_GYROSCOPE).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_GYROSCOPE);
            frequency_gyroscope.setSummary(freq);
        }
        frequency_gyroscope.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_GYROSCOPE) );
        frequency_gyroscope.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_GYROSCOPE, (String) newValue);
                frequency_gyroscope.setSummary( (String)newValue);
                framework.startGyroscope();
                return true;
            }
        });
    }

    /**
     * Location module settings UI
     */
    private void locations() {
        final PreferenceScreen locations = (PreferenceScreen) findPreference("locations");
        if( is_watch ) {
            locations.setEnabled(false);
            return;
        }

        final CheckBoxPreference location_gps = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_GPS);
        location_gps.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true"));
        location_gps.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                LocationManager localMng = (LocationManager) sContext.getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();

                if( ! providers.contains(LocationManager.GPS_PROVIDER) ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_LOCATION_GPS, false);
                    return false;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_GPS,location_gps.isChecked());
                if(location_gps.isChecked()) {
                    framework.startLocations();
                }else {
                    framework.stopLocations();
                }
                return true;
            }
        });

        final CheckBoxPreference location_network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_NETWORK);
        location_network.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true"));
        location_network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                LocationManager localMng = (LocationManager) sContext.getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();

                if( ! providers.contains(LocationManager.NETWORK_PROVIDER) ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_LOCATION_NETWORK, false);
                    return false;
                }

                Aware.setSetting(sContext,Aware_Preferences.STATUS_LOCATION_NETWORK, location_network.isChecked());
                if(location_network.isChecked()) {
                    framework.startLocations();
                }else {
                    framework.stopLocations();
                }
                return true;
            }
        });

        final EditTextPreference gpsInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_GPS);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LOCATION_GPS).length() > 0 ) {
            gpsInterval.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LOCATION_GPS) + " seconds");
        }
        gpsInterval.setText(Aware.getSetting(sContext,Aware_Preferences.FREQUENCY_LOCATION_GPS));
        gpsInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,Aware_Preferences.FREQUENCY_LOCATION_GPS, (String) newValue);
                gpsInterval.setSummary((String) newValue + " seconds");
                framework.startLocations();
                return true;
            }
        });

        final EditTextPreference networkInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_NETWORK);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK).length() > 0 ) {
            networkInterval.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK) + " seconds");
        }
        networkInterval.setText(Aware.getSetting(sContext,Aware_Preferences.FREQUENCY_LOCATION_NETWORK));
        networkInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,Aware_Preferences.FREQUENCY_LOCATION_NETWORK, (String) newValue);
                networkInterval.setSummary((String) newValue + " seconds");
                framework.startLocations();
                return true;
            }
        });

        final EditTextPreference gpsAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_GPS_ACCURACY);
        if( Aware.getSetting(sContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY).length() > 0 ) {
            gpsAccuracy.setSummary(Aware.getSetting(sContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY) + " meters");
        }
        gpsAccuracy.setText(Aware.getSetting(sContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY));
        gpsAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY, (String) newValue);
                gpsAccuracy.setSummary((String) newValue + " meters");
                framework.startLocations();
                return true;
            }
        });

        final EditTextPreference networkAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY);
        if( Aware.getSetting(sContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY).length() > 0 ) {
            networkAccuracy.setSummary(Aware.getSetting(sContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY) + " meters");
        }
        networkAccuracy.setText(Aware.getSetting(sContext,Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY));
        networkAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY, (String) newValue);
                networkAccuracy.setSummary((String) newValue + " meters");
                framework.startLocations();
                return true;
            }
        });

        final EditTextPreference expirateTime = (EditTextPreference) findPreference(Aware_Preferences.LOCATION_EXPIRATION_TIME);
        if( Aware.getSetting(sContext, Aware_Preferences.LOCATION_EXPIRATION_TIME).length() > 0 ) {
            expirateTime.setSummary(Aware.getSetting(sContext, Aware_Preferences.LOCATION_EXPIRATION_TIME) + " seconds");
        }
        expirateTime.setText(Aware.getSetting(sContext, Aware_Preferences.LOCATION_EXPIRATION_TIME));
        expirateTime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.LOCATION_EXPIRATION_TIME, (String) newValue);
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
        final PreferenceScreen networks = (PreferenceScreen) findPreference("network");
        if( is_watch ) {
            networks.setEnabled(false);
            return;
        }

        final CheckBoxPreference network_traffic = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_TRAFFIC);
        network_traffic.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true"));
        network_traffic.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC,network_traffic.isChecked());
                if(network_traffic.isChecked()) {
                    framework.startTraffic();
                }else {
                    framework.stopTraffic();
                }
                return true;
            }
        });

        final EditTextPreference frequencyTraffic = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC).length() > 0 ) {
            frequencyTraffic.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC) + " seconds");
        }
        frequencyTraffic.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC));
        frequencyTraffic.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC, (String) newValue);
                frequencyTraffic.setSummary((String) newValue + " seconds");
                if( network_traffic.isChecked() ) {
                    framework.startTraffic();
                }
                return true;
            }
        });

        final CheckBoxPreference network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_EVENTS);
        network.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true"));
        network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_EVENTS,network.isChecked());
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
        screen.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_SCREEN).equals("true"));
        screen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_SCREEN, screen.isChecked());
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
        final PreferenceScreen wifis = (PreferenceScreen) findPreference("wifi");
        if( is_watch ) {
            wifis.setEnabled(false);
            return;
        }

        final CheckBoxPreference wifi = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_WIFI );
        wifi.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_WIFI).equals("true"));
        wifi.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.STATUS_WIFI,wifi.isChecked());
                if(wifi.isChecked()) {
                    framework.startWiFi();
                }else {
                    framework.stopWiFi();
                }
                return true;
            }
        });

        final EditTextPreference wifiInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WIFI);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WIFI).length() > 0 ) {
            wifiInterval.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WIFI) + " seconds");
        }
        wifiInterval.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WIFI));
        wifiInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_WIFI, (String) newValue);
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
        processor.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_PROCESSOR).equals("true"));
        processor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_PROCESSOR, processor.isChecked());
                if(processor.isChecked()) {
                    framework.startProcessor();
                }else {
                    framework.stopProcessor();
                }
                return true;
            }
        });

        final EditTextPreference frequencyProcessor = (EditTextPreference) findPreference( Aware_Preferences.FREQUENCY_PROCESSOR );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_PROCESSOR).length() > 0 ) {
            frequencyProcessor.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_PROCESSOR) + " seconds");
        }
        frequencyProcessor.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_PROCESSOR));
        frequencyProcessor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,Aware_Preferences.FREQUENCY_PROCESSOR, (String) newValue);
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
        final PreferenceScreen timezones = (PreferenceScreen) findPreference("timezone");
        if( is_watch ) {
            timezones.setEnabled(false);
            return;
        }

        final CheckBoxPreference timeZone = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TIMEZONE);
        timeZone.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_TIMEZONE).equals("true"));
        timeZone.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_TIMEZONE,timeZone.isChecked());
                if(timeZone.isChecked()) {
                    framework.startTimeZone();
                }else {
                    framework.stopTimeZone();
                }
                return true;
            }
        });

        final EditTextPreference frequencyTimeZone = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_TIMEZONE);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_TIMEZONE).length() > 0 ) {
            frequencyTimeZone.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_TIMEZONE) + " seconds");
        }
        frequencyTimeZone.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_TIMEZONE));
        frequencyTimeZone.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_TIMEZONE, (String) newValue);
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
            light_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference light = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LIGHT);
        light.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_LIGHT).equals("true"));
        light.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    light.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_LIGHT, false);
                    return false;
                }

                Aware.setSetting(sContext,Aware_Preferences.STATUS_LIGHT, light.isChecked());
                if(light.isChecked()) {
                    framework.startLight();
                }else {
                    framework.stopLight();
                }
                return true;
            }
        });

        final ListPreference frequency_light = (ListPreference) findPreference( FREQUENCY_LIGHT );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LIGHT).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_LIGHT);
            frequency_light.setSummary(freq);
        }
        frequency_light.setDefaultValue(Aware.getSetting(sContext, FREQUENCY_LIGHT));
        frequency_light.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_LIGHT, (String) newValue);
                frequency_light.setSummary( (String)newValue);
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
            magno_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference magnetometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MAGNETOMETER);
        magnetometer.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_MAGNETOMETER).equals("true"));
        magnetometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    magnetometer.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_MAGNETOMETER, false);
                    return false;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_MAGNETOMETER, magnetometer.isChecked());
                if(magnetometer.isChecked()) {
                    framework.startMagnetometer();
                }else {
                    framework.stopMagnetometer();
                }
                return true;
            }
        });

        final ListPreference frequency_magnetometer = (ListPreference) findPreference( FREQUENCY_MAGNETOMETER );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_MAGNETOMETER).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_MAGNETOMETER);
            frequency_magnetometer.setSummary(freq);
        }
        frequency_magnetometer.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_MAGNETOMETER) );
        frequency_magnetometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_MAGNETOMETER, (String) newValue);
                frequency_magnetometer.setSummary( (String)newValue);
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
            baro_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference pressure = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_BAROMETER);
        pressure.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_BAROMETER).equals("true"));
        pressure.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    pressure.setChecked(false);
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_BAROMETER, false);
                    return false;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_BAROMETER, pressure.isChecked());
                if(pressure.isChecked()) {
                    framework.startBarometer();
                }else {
                    framework.stopBarometer();
                }
                return true;
            }
        });

        final ListPreference frequency_pressure = (ListPreference) findPreference( FREQUENCY_BAROMETER );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_BAROMETER).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_BAROMETER);
            frequency_pressure.setSummary(freq);
        }
        frequency_pressure.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_BAROMETER) );
        frequency_pressure.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_BAROMETER, (String) newValue);
                frequency_pressure.setSummary( (String)newValue);
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
            proxi_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference proximity = (CheckBoxPreference) findPreference( Aware_Preferences.STATUS_PROXIMITY);
        proximity.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_PROXIMITY).equals("true"));
        proximity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    proximity.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_PROXIMITY, false);
                    return false;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_PROXIMITY, proximity.isChecked());
                if(proximity.isChecked()) {
                    framework.startProximity();
                }else {
                    framework.stopProximity();
                }
                return true;
            }
        });

        final ListPreference frequency_proximity = (ListPreference) findPreference( FREQUENCY_PROXIMITY );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_PROXIMITY).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_PROXIMITY);
            frequency_proximity.setSummary(freq);
        }
        frequency_proximity.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_PROXIMITY) );
        frequency_proximity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_PROXIMITY, (String) newValue);
                frequency_proximity.setSummary( (String)newValue);
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
            rotation_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference rotation = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ROTATION);
        rotation.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_ROTATION).equals("true"));
        rotation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    rotation.setChecked(false);
                    Aware.setSetting(sContext,Aware_Preferences.STATUS_ROTATION,false);
                    return false;
                }

                Aware.setSetting(sContext,Aware_Preferences.STATUS_ROTATION, rotation.isChecked());
                if(rotation.isChecked()) {
                    framework.startRotation();
                }else {
                    framework.stopRotation();
                }
                return true;
            }
        });

        final ListPreference frequency_rotation = (ListPreference) findPreference( FREQUENCY_ROTATION );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_ROTATION).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_ROTATION);
            frequency_rotation.setSummary(freq);
        }
        frequency_rotation.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_ROTATION) );
        frequency_rotation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_ROTATION, (String) newValue);
                frequency_rotation.setSummary( (String)newValue);
                framework.startRotation();
                return true;
            }
        });
    }

    /**
     * Telephony module settings UI
     */
    private void telephony() {
        final PreferenceScreen telephonies = (PreferenceScreen) findPreference("telephony");
        if( is_watch ) {
            telephonies.setEnabled(false);
            return;
        }
        final CheckBoxPreference telephony = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TELEPHONY);
        telephony.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_TELEPHONY).equals("true"));
        telephony.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.STATUS_TELEPHONY, telephony.isChecked());
                if (telephony.isChecked()) {
                    framework.startTelephony();
                } else {
                    framework.stopTelephony();
                }
                return true;
            }
        });
    }

    /**
     * Logging module settings UI components
     */
    public void logging() {
        webservices();
        mqtt();
    }

    /**
     * Webservices module settings UI
     */
    private void webservices() {
        final CheckBoxPreference webservice = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_WEBSERVICE);
        webservice.setChecked(Aware.getSetting(sContext,Aware_Preferences.STATUS_WEBSERVICE).equals("true"));
        webservice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if( Aware.getSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER).length() == 0 ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_WEBSERVICE, false);
                    webservice.setChecked(false);
                    return false;
                } else {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_WEBSERVICE, webservice.isChecked());
                    if( webservice.isChecked() && Aware.getSetting(sContext, WEBSERVICE_SERVER).length() > 0 ) {
                        //setup and send data
                        Intent study_config = new Intent(sContext, StudyConfig.class);
                        study_config.putExtra("study_url", Aware.getSetting(sContext, WEBSERVICE_SERVER));
                        sContext.startService(study_config);
                    }
                    return true;
                }
            }
        });

        final EditTextPreference webservice_server = (EditTextPreference) findPreference(Aware_Preferences.WEBSERVICE_SERVER);
        webservice_server.setText(Aware.getSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER));
        if( Aware.getSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER ).length() > 0 ) {
            webservice_server.setSummary("Server: " + Aware.getSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER));
        }
        webservice_server.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.WEBSERVICE_SERVER, (String) newValue );
                webservice_server.setSummary("Server: " + (String) newValue );
                return true;
            }
        });

        final CheckBoxPreference webservice_wifi_only = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        webservice_wifi_only.setChecked(Aware.getSetting(sContext,Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true"));
        webservice_wifi_only.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.WEBSERVICE_WIFI_ONLY, webservice_wifi_only.isChecked());
                return true;
            }
        });

        final EditTextPreference frequency_webservice = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WEBSERVICE);
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WEBSERVICE).length() > 0 ) {
            frequency_webservice.setSummary(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WEBSERVICE) + " minutes");
        }
        frequency_webservice.setText(Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_WEBSERVICE));
        frequency_webservice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_WEBSERVICE, (String) newValue);
                frequency_webservice.setSummary((String) newValue + " minutes");
                return true;
            }
        });

        final ListPreference clean_old_data = (ListPreference) findPreference( FREQUENCY_CLEAN_OLD_DATA );
        if( Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0 ) {
            String freq = Aware.getSetting(sContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA);
            if(freq.equals("0")) {
                clean_old_data.setSummary("Never");
            } else if ( freq.equals("1") ) {
                clean_old_data.setSummary("Weekly");
            } else if ( freq.equals("2") ) {
                clean_old_data.setSummary("Monthly");
            }
        }
        clean_old_data.setDefaultValue( Aware.getSetting(sContext, FREQUENCY_CLEAN_OLD_DATA) );
        clean_old_data.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,FREQUENCY_CLEAN_OLD_DATA, (String) newValue);
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
        mqtt.setChecked(Aware.getSetting(sContext, Aware_Preferences.STATUS_MQTT).equals("true"));
        mqtt.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if( Aware.getSetting(sContext, Aware_Preferences.MQTT_SERVER).length() == 0 ) {
                    sPreferences.showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    mqtt.setChecked(false);
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_MQTT, false);
                    return false;
                } else {
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_MQTT, mqtt.isChecked());
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
        mqttServer.setText(Aware.getSetting(sContext,Aware_Preferences.MQTT_SERVER));
        if( Aware.getSetting(sContext, Aware_Preferences.MQTT_SERVER).length() > 0 ) {
            mqttServer.setSummary("Server: " + Aware.getSetting(sContext, Aware_Preferences.MQTT_SERVER));
        }
        mqttServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext,Aware_Preferences.MQTT_SERVER, (String) newValue);
                mqttServer.setSummary("Server: " + (String) newValue);
                return true;
            }
        });

        final EditTextPreference mqttPort = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PORT);
        if( Aware.getSetting(sContext, Aware_Preferences.MQTT_PORT).length() > 0 ) {
            mqttPort.setSummary(Aware.getSetting(sContext, Aware_Preferences.MQTT_PORT));
        }
        mqttPort.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_PORT));
        mqttPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_PORT, (String) newValue);
                return true;
            }
        });

        final EditTextPreference mqttUsername = (EditTextPreference) findPreference(Aware_Preferences.MQTT_USERNAME);
        if( Aware.getSetting(sContext, Aware_Preferences.MQTT_USERNAME).length() > 0 ) {
            mqttUsername.setSummary(Aware.getSetting(sContext, Aware_Preferences.MQTT_USERNAME));
        }
        mqttUsername.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_USERNAME));
        mqttUsername.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_USERNAME, (String) newValue);
                return true;
            }
        });

        final EditTextPreference mqttPassword = (EditTextPreference) findPreference( Aware_Preferences.MQTT_PASSWORD);
        mqttPassword.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_PASSWORD));
        mqttPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_PASSWORD, (String) newValue);
                return true;
            }
        });

        final EditTextPreference mqttKeepAlive = (EditTextPreference) findPreference( Aware_Preferences.MQTT_KEEP_ALIVE );
        if( Aware.getSetting(sContext, Aware_Preferences.MQTT_KEEP_ALIVE).length() > 0 ) {
            mqttKeepAlive.setSummary(Aware.getSetting(sContext, Aware_Preferences.MQTT_KEEP_ALIVE) + " seconds");
        }
        mqttKeepAlive.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_KEEP_ALIVE));
        mqttKeepAlive.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_KEEP_ALIVE, (String) newValue);
                mqttKeepAlive.setSummary((String) newValue + " seconds");
                return true;
            }
        });

        final EditTextPreference mqttQoS = (EditTextPreference) findPreference(Aware_Preferences.MQTT_QOS);
        mqttQoS.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_QOS));
        mqttQoS.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_QOS, (String) newValue);
                return true;
            }
        });

        final EditTextPreference mqttProtocol = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PROTOCOL);
        mqttProtocol.setText(Aware.getSetting(sContext, Aware_Preferences.MQTT_PROTOCOL));
        mqttProtocol.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.MQTT_PROTOCOL, (String) newValue);
                return true;
            }
        });
    }

    /**
     * Developer UI options
     * - Debug flag
     * - Debug tag
     * - AWARE updates
     * - Device ID
     */
    public void developerOptions() {
        final CheckBoxPreference debug_flag = (CheckBoxPreference) findPreference(Aware_Preferences.DEBUG_FLAG);
        debug_flag.setChecked(Aware.getSetting(sContext,Aware_Preferences.DEBUG_FLAG).equals("true"));
        debug_flag.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.DEBUG = debug_flag.isChecked();
                Aware.setSetting(sContext,Aware_Preferences.DEBUG_FLAG, debug_flag.isChecked());
                return true;
            }
        });

        final EditTextPreference debug_tag = (EditTextPreference) findPreference( Aware_Preferences.DEBUG_TAG );
        debug_tag.setText(Aware.getSetting(sContext, Aware_Preferences.DEBUG_TAG));
        debug_tag.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.TAG = (String) newValue;
                Aware.setSetting(sContext,Aware_Preferences.DEBUG_TAG, (String) newValue);
                return true;
            }
        });

        final CheckBoxPreference auto_update = (CheckBoxPreference) findPreference(Aware_Preferences.AWARE_AUTO_UPDATE);
        auto_update.setChecked(Aware.getSetting(sContext,Aware_Preferences.AWARE_AUTO_UPDATE).equals("true"));

        PackageInfo awareInfo = null;
        try {
            awareInfo = sContext.getPackageManager().getPackageInfo(sContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        auto_update.setSummary("Current version is " + ((awareInfo != null)?awareInfo.versionCode:"???"));
        auto_update.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext, Aware_Preferences.AWARE_AUTO_UPDATE, auto_update.isChecked());
                return true;
            }
        });

        final CheckBoxPreference debug_db_slow = (CheckBoxPreference) findPreference(Aware_Preferences.DEBUG_DB_SLOW);
        debug_db_slow.setChecked(Aware.getSetting(sContext,Aware_Preferences.DEBUG_DB_SLOW).equals("true"));
        debug_db_slow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(sContext,Aware_Preferences.DEBUG_DB_SLOW, debug_db_slow.isChecked());
                return true;
            }
        });

        final EditTextPreference device_id = (EditTextPreference) findPreference(Aware_Preferences.DEVICE_ID);
        device_id.setSummary("UUID: " + Aware.getSetting(sContext, DEVICE_ID));
        device_id.setText(Aware.getSetting(sContext, Aware_Preferences.DEVICE_ID));
        device_id.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(sContext, Aware_Preferences.DEVICE_ID, (String) newValue);
                device_id.setSummary("UUID: " + Aware.getSetting(sContext, Aware_Preferences.DEVICE_ID));
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
    	super.onResume();

        if( ( Aware.getSetting( getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS).equals("true") || Aware.getSetting( getApplicationContext(), Aware_Preferences.STATUS_KEYBOARD).equals("true") ) && ! Applications.isAccessibilityServiceActive(getApplicationContext()) ) {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
            mBuilder.setSmallIcon(R.drawable.ic_stat_aware_accessibility);
            mBuilder.setContentTitle("AWARE configuration");
            mBuilder.setContentText(getResources().getString(R.string.aware_activate_accessibility));
            mBuilder.setDefaults(Notification.DEFAULT_ALL);
            mBuilder.setAutoCancel(true);

            Intent accessibilitySettings = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilitySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent clickIntent = PendingIntent.getActivity(getApplicationContext(), 0, accessibilitySettings, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(clickIntent);
            NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(Applications.ACCESSIBILITY_NOTIFICATION_ID, mBuilder.build());
    	}

        if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) {
            new Async_StudyData().execute(Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER));
        }
    }

    /**
     * Allows the dashboard to modify unitary settings for tweaking a configuration for devices.
     * @param c
     * @param configs
     */
    protected static void tweakSettings( Context c, JSONArray configs ) {
        for( int i = 0; i< configs.length(); i++ ) {
            try{
                JSONObject setting = configs.getJSONObject(i);
                Aware.setSetting( c, setting.getString("setting"), setting.get("value") );
            } catch (JSONException e ){
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     * @param context
     * @param configs
     */
    protected static void applySettings( Context context, JSONArray configs ) {

        boolean is_developer = Aware.getSetting(context, Aware_Preferences.DEBUG_FLAG).equals("true");

        //First reset the client to default settings...
        Aware.reset(context);

        if( is_developer ) Aware.setSetting(context, Aware_Preferences.DEBUG_FLAG, true);

        //Now apply the new settings
        JSONArray plugins = new JSONArray();
    	JSONArray sensors = new JSONArray();

        for( int i = 0; i<configs.length(); i++ ) {
            try {
                JSONObject element = configs.getJSONObject(i);
                if( element.has("plugins") ) {
                    plugins = element.getJSONArray("plugins");
                }
                if( element.has("sensors")) {
                    sensors = element.getJSONArray("sensors");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Set the sensors' settings first
        for( int i=0; i < sensors.length(); i++ ) {
            try {
                JSONObject sensor_config = sensors.getJSONObject(i);
                Aware.setSetting( context, sensor_config.getString("setting"), sensor_config.get("value") );
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Set the plugins' settings now
        ArrayList<String> active_plugins = new ArrayList<>();
        for( int i=0; i < plugins.length(); i++ ) {
            try{
                JSONObject plugin_config = plugins.getJSONObject(i);
                String package_name = plugin_config.getString("plugin");
                active_plugins.add(package_name);

                JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                for(int j=0; j<plugin_settings.length(); j++) {
                    JSONObject plugin_setting = plugin_settings.getJSONObject(j);

                    ContentValues newSettings = new ContentValues();
                    newSettings.put(Aware_Provider.Aware_Settings.SETTING_KEY, plugin_setting.getString("setting"));
                    newSettings.put(Aware_Provider.Aware_Settings.SETTING_VALUE, plugin_setting.get("value").toString() );
                    newSettings.put(Aware_Provider.Aware_Settings.SETTING_PACKAGE_NAME, package_name);
                    context.getContentResolver().insert(Aware_Provider.Aware_Settings.CONTENT_URI, newSettings);
                }
            }catch( JSONException e ) {
                e.printStackTrace();
            }
        }

        //Now check plugins
        new CheckPlugins(context).execute(active_plugins);

        //Send data to server
        Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        context.sendBroadcast(sync);
    }

    public static class CheckPlugins extends AsyncTask<ArrayList<String>, Void, Void> {
        private Context context;
        public CheckPlugins(Context c) {
            context = c;
        }

        @Override
        protected Void doInBackground(ArrayList<String>... params) {
            for( String package_name : params[0] ) {
                JSONObject json_package = null;
                HttpResponse http_request = new Https(context).dataGET("https://api.awareframework.com/index.php/plugins/get_plugin/" + package_name, true);
                if( http_request != null && http_request.getStatusLine().getStatusCode() == 200 ) {
                    try {
                        String json_string = Https.undoGZIP(http_request);
                        if( ! json_string.equals("[]") ) {
                            json_package = new JSONObject(json_string);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if( json_package != null ) {
                    if( ! Plugins_Manager.isInstalled(context, package_name) ) {
                        Aware.downloadPlugin(context, package_name, false);
                    } else {
                        try {
                            if( json_package.getInt("version") > Plugins_Manager.getVersion(context, package_name) ) {
                                Aware.downloadPlugin(context, package_name, true);
                            } else {
                                Aware.startPlugin(context, package_name);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return null;
        }
    }

    /**
     * Service that allows plugins/applications to send data to AWARE's dashboard study
     */
    public static class StudyConfig extends IntentService {

        /**
         * Received broadcast to join a study
         */
        public static final String EXTRA_JOIN_STUDY = "study_url";

        public StudyConfig() {
			super("Study Config Service");
		}

		@Override
    	protected void onHandleIntent(Intent intent) {
			String study_url = intent.getStringExtra(EXTRA_JOIN_STUDY);
			
			if( Aware.DEBUG ) Log.d(Aware.TAG, "Joining: " + study_url);
			
			if( study_url.startsWith("https://api.awareframework.com/") ) {

				//Request study settings
				ArrayList<NameValuePair> data = new ArrayList<>();
				data.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID)));
				HttpResponse answer = new Https(getApplicationContext()).dataPOST(study_url, data, true);
				try {
                    String json_str = Https.undoGZIP(answer);

                    JSONArray configs_study = new JSONArray(json_str);
					if( configs_study.getJSONObject(0).has("message") ) {
                        Toast.makeText(getApplicationContext(), "This study is not available.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
                    mBuilder.setSmallIcon(R.drawable.ic_action_aware_studies);
                    mBuilder.setContentTitle("AWARE");
                    mBuilder.setContentText("Thanks for joining!");
                    mBuilder.setAutoCancel(true);

                    NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notManager.notify(33, mBuilder.build());

					if( Aware.DEBUG ) Log.d(Aware.TAG, "Study configs: " + configs_study.toString(5));
					
					//Apply new configurations in AWARE Client
					applySettings( getApplicationContext(), configs_study );

				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
          	}
    	}
    }
}