
package com.aware.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
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
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.ui.Aware_Activity;
import com.aware.phone.ui.Aware_Join_Study;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Https;
import com.aware.utils.SSLManager;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preferences dashboard for the AWARE Aware
 * Allows the researcher to configure all the modules, start and stop modules and where logging happens.
 *
 * @author df
 */
public class Aware_Client extends Aware_Activity {

    private static final int DIALOG_ERROR_MISSING_PARAMETERS = 2;
    private static final int DIALOG_ERROR_MISSING_SENSOR = 3;

    private static SensorManager mSensorMgr;
    public static ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();

    private Context awareContext;
    private PreferenceActivity clientUI;

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        switch (id) {
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
        awareContext = getApplicationContext();
        clientUI = this;

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        addPreferencesFromResource(R.xml.aware_preferences);
        setContentView(R.layout.aware_ui);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof PreferenceScreen) {
            Dialog subpref = ((PreferenceScreen) preference).getDialog();
            subpref.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    defaultSettings(); //updates the UI to reflect the changes in active sensors
                }
            });
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected void onStart() {
        super.onStart();

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            //Start AWARE framework background service
            Intent startAware = new Intent(awareContext, Aware.class);
            startService(startAware);

            SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
            if (prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
                prefs.edit().commit(); //commit changes
            } else {
                PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, false);
            }

            Map<String, ?> defaults = prefs.getAll();
            for (Map.Entry<String, ?> entry : defaults.entrySet()) {
                if (Aware.getSetting(getApplicationContext(), entry.getKey(), "com.aware.phone").length() == 0) {
                    Aware.setSetting(getApplicationContext(), entry.getKey(), entry.getValue(), "com.aware.phone");
                }
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                UUID uuid = UUID.randomUUID();
                Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, uuid.toString(), "com.aware.phone");
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER, "https://api.awareframework.com/index.php", "com.aware.phone");
            }

            //Check if AWARE is active on the accessibility services
            if (!Aware.is_watch(awareContext)) {
                Applications.isAccessibilityServiceActive(awareContext);
            }

            //Notify the user of no changes are allowed, if enrolled on a study.
            if (Aware.isStudy(getApplicationContext())) {
                Snackbar noChanges = Snackbar.make(aware_container, "Ongoing study, no changes allowed.", Snackbar.LENGTH_LONG);
                TextView output = (TextView) noChanges.getView().findViewById(android.support.design.R.id.snackbar_text);
                output.setTextColor(Color.WHITE);
                noChanges.show();
            }

            defaultSettings();

        } else {
            Intent permissionsHandler = new Intent(this, PermissionsHandler.class);
            permissionsHandler.putStringArrayListExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissionsHandler.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getClass().getName());
            startActivityForResult(permissionsHandler, PermissionsHandler.RC_PERMISSIONS);
            finish();
        }
    }

    private void defaultSettings() {
        final SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        if (!prefs.contains("intro_done")) {

            prefs.edit().putBoolean("intro_done", true).commit();

            final ViewGroup parent = (ViewGroup) findViewById(android.R.id.content);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            final View help_qrcode = inflater.inflate(R.layout.help_qrcode, null);
            final View help_menu = inflater.inflate(R.layout.help_menu, null);
            parent.addView(help_qrcode, params);
            help_qrcode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.removeView(help_qrcode);
                    parent.addView(help_menu, params);
                }
            });

            help_menu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.removeView(help_menu);
                }
            });
        }

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
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ESM).equals("true")) {
            mobile_esm.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_esm_active));
        } else {
            mobile_esm.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_esm));
        }

        final CheckBoxPreference esm = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ESM);
        esm.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_ESM).equals("true"));
        esm.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_ESM, esm.isChecked());
                if (esm.isChecked()) {
                    Aware.startESM(awareContext);
                } else {
                    Aware.stopESM(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) esm.setSelectable(false);
    }

    /**
     * Temperature module settings UI
     */
    private void temperature() {
        final PreferenceScreen temp_pref = (PreferenceScreen) findPreference("temperature");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true")) {
            temp_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_temperature_active));
        } else {
            temp_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_temperature));
        }

        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
        if (temp != null) {
            temp_pref.setSummary(temp_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            temp_pref.setSummary(temp_pref.getSummary().toString().replace("*", ""));
            temp_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference temperature = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TEMPERATURE);
        temperature.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true"));
        temperature.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_TEMPERATURE) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    temperature.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE, temperature.isChecked());
                if (temperature.isChecked()) {
                    Aware.startTemperature(awareContext);
                } else {
                    Aware.stopTemperature(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) temperature.setSelectable(false);

        final ListPreference frequency_temperature = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_TEMPERATURE);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TEMPERATURE).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TEMPERATURE);
            frequency_temperature.setSummary(freq);
        }
        frequency_temperature.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TEMPERATURE));
        frequency_temperature.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_TEMPERATURE, newValue);
                frequency_temperature.setSummary((String) newValue);
                Aware.startTemperature(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_temperature.setSelectable(false);

        final EditTextPreference threshold_temperature = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_TEMPERATURE);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_TEMPERATURE).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_TEMPERATURE);
            threshold_temperature.setSummary(threshold);
        }
        threshold_temperature.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_TEMPERATURE));
        threshold_temperature.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_TEMPERATURE, (String) newValue);
                threshold_temperature.setSummary((String) newValue);
                Aware.startTemperature(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_temperature.setSelectable(false);
    }

    /**
     * Accelerometer module settings UI
     */
    private void accelerometer() {
        final PreferenceScreen accel_pref = (PreferenceScreen) findPreference("accelerometer");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true")) {
            accel_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_accelerometer_active));
        } else {
            accel_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_accelerometer));
        }

        Sensor accel = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) {
            accel_pref.setSummary(accel_pref.getSummary().toString().replace("*", " - Power: " + accel.getPower() + " mA"));
        } else {
            accel_pref.setSummary(accel_pref.getSummary().toString().replace("*", ""));
            accel_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ACCELEROMETER);
        accelerometer.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true"));
        accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    accelerometer.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER, accelerometer.isChecked());
                if (accelerometer.isChecked()) {
                    Aware.startAccelerometer(awareContext);
                } else {
                    Aware.stopAccelerometer(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) accelerometer.setSelectable(false);

        final ListPreference frequency_accelerometer = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_ACCELEROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ACCELEROMETER).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ACCELEROMETER);
            frequency_accelerometer.setSummary(freq);
        }
        frequency_accelerometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ACCELEROMETER));
        frequency_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_ACCELEROMETER, (String) newValue);
                frequency_accelerometer.setSummary((String) newValue);
                Aware.startAccelerometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_accelerometer.setSelectable(false);

        final EditTextPreference threshold_accelerometer = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_ACCELEROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ACCELEROMETER).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ACCELEROMETER);
            threshold_accelerometer.setSummary(threshold);
        }
        threshold_accelerometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ACCELEROMETER));
        threshold_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_ACCELEROMETER, (String) newValue);
                threshold_accelerometer.setSummary((String) newValue);
                Aware.startAccelerometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_accelerometer.setSelectable(false);
    }

    /**
     * Linear Accelerometer module settings UI
     */
    private void linear_accelerometer() {

        final PreferenceScreen linear_pref = (PreferenceScreen) findPreference("linear_accelerometer");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true")) {
            linear_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_linear_acceleration_active));
        } else {
            linear_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_linear_acceleration));
        }

        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (temp != null) {
            linear_pref.setSummary(linear_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            linear_pref.setSummary(linear_pref.getSummary().toString().replace("*", ""));
            linear_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference linear_accelerometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER);
        linear_accelerometer.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true"));
        linear_accelerometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    linear_accelerometer.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, false);
                    return false;
                }
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, linear_accelerometer.isChecked());
                if (linear_accelerometer.isChecked()) {
                    Aware.startLinearAccelerometer(awareContext);
                } else {
                    Aware.stopLinearAccelerometer(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) linear_accelerometer.setSelectable(false);

        final ListPreference frequency_linear_accelerometer = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER);
            frequency_linear_accelerometer.setSummary(freq);
        }
        frequency_linear_accelerometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER));
        frequency_linear_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_LINEAR_ACCELEROMETER, (String) newValue);
                frequency_linear_accelerometer.setSummary((String) newValue);
                Aware.startLinearAccelerometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_linear_accelerometer.setSelectable(false);

        final EditTextPreference threshold_linear_accelerometer = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_LINEAR_ACCELEROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LINEAR_ACCELEROMETER).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LINEAR_ACCELEROMETER);
            threshold_linear_accelerometer.setSummary(threshold);
        }
        threshold_linear_accelerometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LINEAR_ACCELEROMETER));
        threshold_linear_accelerometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_LINEAR_ACCELEROMETER, (String) newValue);
                threshold_linear_accelerometer.setSummary((String) newValue);
                Aware.startLinearAccelerometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_linear_accelerometer.setSelectable(false);
    }

    /**
     * Applications module settings UI
     */
    private void applications() {
        final PreferenceScreen apps_pref = (PreferenceScreen) findPreference("applications");

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_CRASHES).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            apps_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_applications_active));
        } else {
            apps_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_applications));
        }

        final CheckBoxPreference notifications = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NOTIFICATIONS);
        notifications.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_NOTIFICATIONS).equals("true"));
        notifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Applications.isAccessibilityServiceActive(awareContext) && notifications.isChecked()) {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_NOTIFICATIONS, notifications.isChecked());
                    notifications.setChecked(true);
                    Aware.startApplications(awareContext);
                    return true;
                }
                Applications.isAccessibilityServiceActive(awareContext);
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_NOTIFICATIONS, false);
                notifications.setChecked(false);
                return false;
            }
        });
        if (Aware.isStudy(awareContext)) notifications.setSelectable(false);

        final CheckBoxPreference keyboard = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_KEYBOARD);
        keyboard.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD).equals("true"));
        keyboard.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Applications.isAccessibilityServiceActive(awareContext) && keyboard.isChecked()) {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD, keyboard.isChecked());
                    keyboard.setChecked(true);
                    Aware.startApplications(awareContext);
                    Aware.startKeyboard(awareContext);
                    return true;
                }
                Applications.isAccessibilityServiceActive(awareContext);
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD, false);
                keyboard.setChecked(false);
                Aware.stopKeyboard(awareContext);
                return false;
            }
        });
        if (Aware.isStudy(awareContext)) keyboard.setSelectable(false);

        final CheckBoxPreference crashes = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_CRASHES);
        crashes.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_CRASHES).equals("true"));
        crashes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Applications.isAccessibilityServiceActive(awareContext) && crashes.isChecked()) {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_CRASHES, crashes.isChecked());
                    crashes.setChecked(true);
                    Aware.startApplications(awareContext);
                    return true;
                }
                Applications.isAccessibilityServiceActive(awareContext);
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_CRASHES, false);
                crashes.setChecked(false);
                return false;
            }
        });
        if (Aware.isStudy(awareContext)) crashes.setSelectable(false);

        final CheckBoxPreference applications = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_APPLICATIONS);
        applications.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true"));
        applications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Applications.isAccessibilityServiceActive(awareContext) && applications.isChecked()) {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS, true);
                    applications.setChecked(true);
                    Aware.startApplications(awareContext);
                    return true;
                } else {
                    Applications.isAccessibilityServiceActive(awareContext);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS, false);
                    applications.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_NOTIFICATIONS, false);
                    notifications.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_CRASHES, false);
                    crashes.setChecked(false);
                    Aware.stopApplications(awareContext);
                    return false;
                }
            }
        });
        if (Aware.isStudy(awareContext)) applications.setSelectable(false);

        final EditTextPreference frequency_applications = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_APPLICATIONS);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_APPLICATIONS).length() > 0) {
            frequency_applications.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_APPLICATIONS) + " seconds");
        }
        frequency_applications.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_APPLICATIONS));
        frequency_applications.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_APPLICATIONS, (String) newValue);
                frequency_applications.setSummary((String) newValue + " seconds");
                Aware.startApplications(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_applications.setSelectable(false);

        final CheckBoxPreference installations = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_INSTALLATIONS);
        installations.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS).equals("true"));
        installations.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS, installations.isChecked());
                if (installations.isChecked()) {
                    Aware.startInstallations(awareContext);
                } else {
                    Aware.stopInstallations(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) installations.setSelectable(false);
    }

    /**
     * Battery module settings UI
     */
    private void battery() {
        final PreferenceScreen batt_pref = (PreferenceScreen) findPreference("battery");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BATTERY).equals("true")) {
            batt_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_battery_active));
        } else {
            batt_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_battery));
        }

        final CheckBoxPreference battery = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_BATTERY);
        battery.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_BATTERY).equals("true"));
        battery.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_BATTERY, battery.isChecked());
                if (battery.isChecked()) {
                    Aware.startBattery(awareContext);
                } else {
                    Aware.stopBattery(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) battery.setSelectable(false);
    }

    /**
     * Bluetooth module settings UI
     */
    private void bluetooth() {
        final PreferenceScreen bt_pref = (PreferenceScreen) findPreference("bluetooth");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH).equals("true")) {
            bt_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_bluetooth_active));
        } else {
            bt_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_bluetooth));
        }

        final CheckBoxPreference bluetooth = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_BLUETOOTH);
        bluetooth.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH).equals("true"));
        bluetooth.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if (btAdapter == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    bluetooth.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH, bluetooth.isChecked());
                if (bluetooth.isChecked()) {
                    Aware.startBluetooth(awareContext);
                } else {
                    Aware.stopBluetooth(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) bluetooth.setSelectable(false);

        final EditTextPreference bluetoothInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_BLUETOOTH);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BLUETOOTH).length() > 0) {
            bluetoothInterval.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BLUETOOTH) + " seconds");
        }
        bluetoothInterval.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BLUETOOTH));
        bluetoothInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_BLUETOOTH, (String) newValue);
                bluetoothInterval.setSummary((String) newValue + " seconds");
                Aware.startBluetooth(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) bluetoothInterval.setSelectable(false);
    }

    /**
     * Communication module settings UI
     */
    private void communication() {
        final PreferenceScreen communications = (PreferenceScreen) findPreference("communication");
        if (Aware.is_watch(awareContext)) {
            communications.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true")) {
            communications.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_communication_active));
        } else {
            communications.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_communication));
        }

        final CheckBoxPreference calls = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_CALLS);
        calls.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("true"));
        calls.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_CALLS, calls.isChecked());
                if (calls.isChecked()) {
                    Aware.startCommunication(awareContext);
                } else {
                    Aware.stopCommunication(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) calls.setSelectable(false);

        final CheckBoxPreference messages = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MESSAGES);
        messages.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("true"));
        messages.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_MESSAGES, messages.isChecked());
                if (messages.isChecked()) {
                    Aware.startCommunication(awareContext);
                } else {
                    Aware.stopCommunication(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) messages.setSelectable(false);

        final CheckBoxPreference communication = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_COMMUNICATION_EVENTS);
        communication.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true"));
        communication.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS, communication.isChecked());
                if (communication.isChecked()) {
                    Aware.startCommunication(awareContext);
                } else {
                    Aware.stopCommunication(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) communication.setSelectable(false);
    }

    /**
     * Gravity module settings UI
     */
    private void gravity() {
        final PreferenceScreen grav_pref = (PreferenceScreen) findPreference("gravity");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_GRAVITY).equals("true")) {
            grav_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_gravity_active));
        } else {
            grav_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_gravity));
        }

        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (temp != null) {
            grav_pref.setSummary(grav_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            grav_pref.setSummary(grav_pref.getSummary().toString().replace("*", ""));
            grav_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference gravity = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GRAVITY);
        gravity.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_GRAVITY).equals("true"));
        gravity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gravity.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_GRAVITY, false);
                    return false;
                }
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_GRAVITY, gravity.isChecked());
                if (gravity.isChecked()) {
                    Aware.startGravity(awareContext);
                } else {
                    Aware.stopGravity(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) gravity.setSelectable(false);

        final ListPreference frequency_gravity = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_GRAVITY);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GRAVITY).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GRAVITY);
            frequency_gravity.setSummary(freq);
        }
        frequency_gravity.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GRAVITY));
        frequency_gravity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_GRAVITY, (String) newValue);
                frequency_gravity.setSummary((String) newValue);
                Aware.startGravity(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_gravity.setSelectable(false);

        final EditTextPreference threshold_gravity = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_GRAVITY);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GRAVITY).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GRAVITY);
            threshold_gravity.setSummary(threshold);
        }
        threshold_gravity.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GRAVITY));
        threshold_gravity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_GRAVITY, (String) newValue);
                threshold_gravity.setSummary((String) newValue);
                Aware.startGravity(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_gravity.setSelectable(false);
    }

    /**
     * Gyroscope module settings UI
     */
    private void gyroscope() {
        final PreferenceScreen gyro_pref = (PreferenceScreen) findPreference("gyroscope");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE).equals("true")) {
            gyro_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_gyroscope_active));
        } else {
            gyro_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_gyroscope));
        }

        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (temp != null) {
            gyro_pref.setSummary(gyro_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            gyro_pref.setSummary(gyro_pref.getSummary().toString().replace("*", ""));
            gyro_pref.setEnabled(false);
            return;
        }

        final CheckBoxPreference gyroscope = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_GYROSCOPE);
        gyroscope.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE).equals("true"));
        gyroscope.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    gyroscope.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE, false);
                    return false;
                }
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE, gyroscope.isChecked());
                if (gyroscope.isChecked()) {
                    Aware.startGyroscope(awareContext);
                } else {
                    Aware.stopGyroscope(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) gyroscope.setSelectable(false);

        final ListPreference frequency_gyroscope = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_GYROSCOPE);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GYROSCOPE).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GYROSCOPE);
            frequency_gyroscope.setSummary(freq);
        }
        frequency_gyroscope.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_GYROSCOPE));
        frequency_gyroscope.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_GYROSCOPE, (String) newValue);
                frequency_gyroscope.setSummary((String) newValue);
                Aware.startGyroscope(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_gyroscope.setSelectable(false);

        final EditTextPreference threshold_gyroscope = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_GYROSCOPE);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GYROSCOPE).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GYROSCOPE);
            threshold_gyroscope.setSummary(threshold);
        }
        threshold_gyroscope.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_GYROSCOPE));
        threshold_gyroscope.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_GYROSCOPE, (String) newValue);
                threshold_gyroscope.setSummary((String) newValue);
                Aware.startGyroscope(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_gyroscope.setSelectable(false);
    }

    /**
     * Location module settings UI
     */
    private void locations() {
        final PreferenceScreen locations = (PreferenceScreen) findPreference("locations");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            locations.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_locations_active));
        } else {
            locations.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_locations));
        }
        if (Aware.is_watch(awareContext)) {
            locations.setEnabled(false);
            return;
        }

        final CheckBoxPreference location_gps = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_GPS);
        location_gps.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true"));
        location_gps.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                LocationManager localMng = (LocationManager) awareContext.getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();

                if (!providers.contains(LocationManager.GPS_PROVIDER)) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS, location_gps.isChecked());
                if (location_gps.isChecked()) {
                    Aware.startLocations(awareContext);
                } else {
                    Aware.stopLocations(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) location_gps.setSelectable(false);

        final CheckBoxPreference location_network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LOCATION_NETWORK);
        location_network.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true"));
        location_network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                LocationManager localMng = (LocationManager) awareContext.getSystemService(LOCATION_SERVICE);
                List<String> providers = localMng.getAllProviders();

                if (!providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    location_gps.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK, location_network.isChecked());
                if (location_network.isChecked()) {
                    Aware.startLocations(awareContext);
                } else {
                    Aware.stopLocations(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) location_network.setSelectable(false);

        final EditTextPreference gpsInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_GPS);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_GPS).length() > 0) {
            gpsInterval.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_GPS) + " seconds");
        }
        gpsInterval.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_GPS));
        gpsInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_GPS, (String) newValue);
                gpsInterval.setSummary((String) newValue + " seconds");
                Aware.startLocations(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) gpsInterval.setSelectable(false);

        final EditTextPreference networkInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_LOCATION_NETWORK);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK).length() > 0) {
            networkInterval.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK) + " seconds");
        }
        networkInterval.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK));
        networkInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_LOCATION_NETWORK, (String) newValue);
                networkInterval.setSummary((String) newValue + " seconds");
                Aware.startLocations(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) networkInterval.setSelectable(false);

        final EditTextPreference gpsAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_GPS_ACCURACY);
        if (Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY).length() > 0) {
            gpsAccuracy.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY) + " meters");
        }
        gpsAccuracy.setText(Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY));
        gpsAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY, (String) newValue);
                gpsAccuracy.setSummary((String) newValue + " meters");
                Aware.startLocations(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) gpsAccuracy.setSelectable(false);

        final EditTextPreference networkAccuracy = (EditTextPreference) findPreference(Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY);
        if (Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY).length() > 0) {
            networkAccuracy.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY) + " meters");
        }
        networkAccuracy.setText(Aware.getSetting(awareContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY));
        networkAccuracy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY, (String) newValue);
                networkAccuracy.setSummary((String) newValue + " meters");
                Aware.startLocations(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) networkAccuracy.setSelectable(false);

        final EditTextPreference expirateTime = (EditTextPreference) findPreference(Aware_Preferences.LOCATION_EXPIRATION_TIME);
        if (Aware.getSetting(awareContext, Aware_Preferences.LOCATION_EXPIRATION_TIME).length() > 0) {
            expirateTime.setSummary(Aware.getSetting(awareContext, Aware_Preferences.LOCATION_EXPIRATION_TIME) + " seconds");
        }
        expirateTime.setText(Aware.getSetting(awareContext, Aware_Preferences.LOCATION_EXPIRATION_TIME));
        expirateTime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.LOCATION_EXPIRATION_TIME, (String) newValue);
                expirateTime.setSummary((String) newValue + " seconds");
                Aware.startLocations(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) expirateTime.setSelectable(false);
    }

    /**
     * Network module settings UI
     */
    private void network() {
        final PreferenceScreen networks = (PreferenceScreen) findPreference("network");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true")
                || Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true")) {
            networks.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_network_active));
        } else {
            networks.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_network));
        }
        if (Aware.is_watch(awareContext)) {
            networks.setEnabled(false);
            return;
        }

        final CheckBoxPreference network_traffic = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_TRAFFIC);
        network_traffic.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true"));
        network_traffic.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC, network_traffic.isChecked());
                if (network_traffic.isChecked()) {
                    Aware.startTraffic(awareContext);
                } else {
                    Aware.stopTraffic(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) network_traffic.setSelectable(false);

        final EditTextPreference frequencyTraffic = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC).length() > 0) {
            frequencyTraffic.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC) + " seconds");
        }
        frequencyTraffic.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC));
        frequencyTraffic.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_NETWORK_TRAFFIC, (String) newValue);
                frequencyTraffic.setSummary((String) newValue + " seconds");
                if (network_traffic.isChecked()) {
                    Aware.startTraffic(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequencyTraffic.setSelectable(false);

        final CheckBoxPreference network = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_NETWORK_EVENTS);
        network.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true"));
        network.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS, network.isChecked());
                if (network.isChecked()) {
                    Aware.startNetwork(awareContext);
                } else {
                    Aware.stopNetwork(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) network.setSelectable(false);
    }

    /**
     * Screen module settings UI
     */
    private void screen() {
        final PreferenceScreen screen_pref = (PreferenceScreen) findPreference("screen");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_SCREEN).equals("true")) {
            screen_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_screen_active));
        } else {
            screen_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_screen));
        }

        final CheckBoxPreference screen = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_SCREEN);
        screen.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_SCREEN).equals("true"));
        screen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_SCREEN, screen.isChecked());
                if (screen.isChecked()) {
                    Aware.startScreen(awareContext);
                } else {
                    Aware.stopScreen(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) screen.setSelectable(false);
    }

    /**
     * WiFi module settings UI
     */
    private void wifi() {
        final PreferenceScreen wifis = (PreferenceScreen) findPreference("wifi");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_WIFI).equals("true")) {
            wifis.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_wifi_active));
        } else {
            wifis.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_wifi));
        }
        if (Aware.is_watch(awareContext)) {
            wifis.setEnabled(false);
            return;
        }

        final CheckBoxPreference wifi = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_WIFI);
        wifi.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_WIFI).equals("true"));
        wifi.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_WIFI, wifi.isChecked());
                if (wifi.isChecked()) {
                    Aware.startWiFi(awareContext);
                } else {
                    Aware.stopWiFi(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) wifi.setSelectable(false);

        final EditTextPreference wifiInterval = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WIFI);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WIFI).length() > 0) {
            wifiInterval.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WIFI) + " seconds");
        }
        wifiInterval.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WIFI));
        wifiInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_WIFI, (String) newValue);
                wifiInterval.setSummary((String) newValue + " seconds");
                Aware.startWiFi(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) wifiInterval.setSelectable(false);
    }

    /**
     * Processor module settings UI
     */
    private void processor() {
        final PreferenceScreen cpu_pref = (PreferenceScreen) findPreference("processor");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR).equals("true")) {
            cpu_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_processor_active));
        } else {
            cpu_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_processor));
        }
        final CheckBoxPreference processor = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_PROCESSOR);
        processor.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR).equals("true"));
        processor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR, processor.isChecked());
                if (processor.isChecked()) {
                    Aware.startProcessor(awareContext);
                } else {
                    Aware.stopProcessor(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) processor.setSelectable(false);

        final EditTextPreference frequencyProcessor = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_PROCESSOR);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROCESSOR).length() > 0) {
            frequencyProcessor.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROCESSOR) + " seconds");
        }
        frequencyProcessor.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROCESSOR));
        frequencyProcessor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_PROCESSOR, (String) newValue);
                frequencyProcessor.setSummary((String) newValue + " seconds");
                Aware.startProcessor(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequencyProcessor.setSelectable(false);
    }

    /**
     * Timezone module settings UI
     */
    private void timeZone() {
        final PreferenceScreen timezones = (PreferenceScreen) findPreference("timezone");
        if (Aware.is_watch(awareContext)) {
            timezones.setEnabled(false);
            return;
        }
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE).equals("true")) {
            timezones.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_timezone_active));
        } else {
            timezones.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_timezone));
        }

        final CheckBoxPreference timeZone = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TIMEZONE);
        timeZone.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE).equals("true"));
        timeZone.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE, timeZone.isChecked());
                if (timeZone.isChecked()) {
                    Aware.startTimeZone(awareContext);
                } else {
                    Aware.stopTimeZone(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) timeZone.setSelectable(false);

        final EditTextPreference frequencyTimeZone = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_TIMEZONE);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TIMEZONE).length() > 0) {
            frequencyTimeZone.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TIMEZONE) + " seconds");
        }
        frequencyTimeZone.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_TIMEZONE));
        frequencyTimeZone.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_TIMEZONE, (String) newValue);
                frequencyTimeZone.setSummary((String) newValue + " seconds");
                Aware.startTimeZone(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequencyTimeZone.setSelectable(false);
    }

    /**
     * Light module settings UI
     */
    private void light() {

        final PreferenceScreen light_pref = (PreferenceScreen) findPreference("light");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (temp != null) {
            light_pref.setSummary(light_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            light_pref.setSummary(light_pref.getSummary().toString().replace("*", ""));
            light_pref.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_LIGHT).equals("true")) {
            light_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_light_active));
        } else {
            light_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_light));
        }

        final CheckBoxPreference light = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_LIGHT);
        light.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_LIGHT).equals("true"));
        light.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    light.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_LIGHT, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_LIGHT, light.isChecked());
                if (light.isChecked()) {
                    Aware.startLight(awareContext);
                } else {
                    Aware.stopLight(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) light.setSelectable(false);

        final ListPreference frequency_light = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_LIGHT);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LIGHT).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LIGHT);
            frequency_light.setSummary(freq);
        }
        frequency_light.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_LIGHT));
        frequency_light.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_LIGHT, (String) newValue);
                frequency_light.setSummary((String) newValue);
                Aware.startLight(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_light.setSelectable(false);

        final EditTextPreference threshold_light = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_LIGHT);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LIGHT).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LIGHT);
            threshold_light.setSummary(threshold);
        }
        threshold_light.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_LIGHT));
        threshold_light.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_LIGHT, (String) newValue);
                threshold_light.setSummary((String) newValue);
                Aware.startLight(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_light.setSelectable(false);
    }

    /**
     * Magnetometer module settings UI
     */
    private void magnetometer() {
        final PreferenceScreen magno_pref = (PreferenceScreen) findPreference("magnetometer");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (temp != null) {
            magno_pref.setSummary(magno_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            magno_pref.setSummary(magno_pref.getSummary().toString().replace("*", ""));
            magno_pref.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER).equals("true")) {
            magno_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_magnetometer_active));
        } else {
            magno_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_magnetometer));
        }

        final CheckBoxPreference magnetometer = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MAGNETOMETER);
        magnetometer.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER).equals("true"));
        magnetometer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    magnetometer.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER, magnetometer.isChecked());
                if (magnetometer.isChecked()) {
                    Aware.startMagnetometer(awareContext);
                } else {
                    Aware.stopMagnetometer(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) magnetometer.setSelectable(false);

        final ListPreference frequency_magnetometer = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_MAGNETOMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_MAGNETOMETER).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_MAGNETOMETER);
            frequency_magnetometer.setSummary(freq);
        }
        frequency_magnetometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_MAGNETOMETER));
        frequency_magnetometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_MAGNETOMETER, (String) newValue);
                frequency_magnetometer.setSummary((String) newValue);
                Aware.startMagnetometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_magnetometer.setSelectable(false);

        final EditTextPreference threshold_magnetometer = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_MAGNETOMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_MAGNETOMETER).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_MAGNETOMETER);
            threshold_magnetometer.setSummary(threshold);
        }
        threshold_magnetometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_MAGNETOMETER));
        threshold_magnetometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_MAGNETOMETER, (String) newValue);
                threshold_magnetometer.setSummary((String) newValue);
                Aware.startMagnetometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_magnetometer.setSelectable(false);
    }

    /**
     * Atmospheric Pressure module settings UI
     */
    private void barometer() {
        final PreferenceScreen baro_pref = (PreferenceScreen) findPreference("barometer");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (temp != null) {
            baro_pref.setSummary(baro_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            baro_pref.setSummary(baro_pref.getSummary().toString().replace("*", ""));
            baro_pref.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_BAROMETER).equals("true")) {
            baro_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_barometer_active));
        } else {
            baro_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_barometer));
        }

        final CheckBoxPreference pressure = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_BAROMETER);
        pressure.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_BAROMETER).equals("true"));
        pressure.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    pressure.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_BAROMETER, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_BAROMETER, pressure.isChecked());
                if (pressure.isChecked()) {
                    Aware.startBarometer(awareContext);
                } else {
                    Aware.stopBarometer(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) pressure.setSelectable(false);

        final ListPreference frequency_pressure = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_BAROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BAROMETER).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BAROMETER);
            frequency_pressure.setSummary(freq);
        }
        frequency_pressure.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_BAROMETER));
        frequency_pressure.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_BAROMETER, (String) newValue);
                frequency_pressure.setSummary((String) newValue);
                Aware.startBarometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_pressure.setSelectable(false);

        final EditTextPreference threshold_barometer = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_BAROMETER);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_BAROMETER).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_BAROMETER);
            threshold_barometer.setSummary(threshold);
        }
        threshold_barometer.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_BAROMETER));
        threshold_barometer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_BAROMETER, (String) newValue);
                threshold_barometer.setSummary((String) newValue);
                Aware.startBarometer(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_barometer.setSelectable(false);
    }

    /**
     * Proximity module settings UI
     */
    private void proximity() {

        final PreferenceScreen proxi_pref = (PreferenceScreen) findPreference("proximity");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (temp != null) {
            proxi_pref.setSummary(proxi_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            proxi_pref.setSummary(proxi_pref.getSummary().toString().replace("*", ""));
            proxi_pref.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY).equals("true")) {
            proxi_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_proximity_active));
        } else {
            proxi_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_proximity));
        }

        final CheckBoxPreference proximity = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_PROXIMITY);
        proximity.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY).equals("true"));
        proximity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    proximity.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY, proximity.isChecked());
                if (proximity.isChecked()) {
                    Aware.startProximity(awareContext);
                } else {
                    Aware.stopProximity(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) proximity.setSelectable(false);

        final ListPreference frequency_proximity = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_PROXIMITY);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROXIMITY).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROXIMITY);
            frequency_proximity.setSummary(freq);
        }
        frequency_proximity.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_PROXIMITY));
        frequency_proximity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_PROXIMITY, (String) newValue);
                frequency_proximity.setSummary((String) newValue);
                Aware.startProximity(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_proximity.setSelectable(false);

        final EditTextPreference threshold_proximity = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_PROXIMITY);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_PROXIMITY).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_PROXIMITY);
            threshold_proximity.setSummary(threshold);
        }
        threshold_proximity.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_PROXIMITY));
        threshold_proximity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_PROXIMITY, (String) newValue);
                threshold_proximity.setSummary((String) newValue);
                Aware.startProximity(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_proximity.setSelectable(false);
    }

    /**
     * Rotation module settings UI
     */
    private void rotation() {

        final PreferenceScreen rotation_pref = (PreferenceScreen) findPreference("rotation");
        Sensor temp = mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (temp != null) {
            rotation_pref.setSummary(rotation_pref.getSummary().toString().replace("*", " - Power: " + temp.getPower() + " mA"));
        } else {
            rotation_pref.setSummary(rotation_pref.getSummary().toString().replace("*", ""));
            rotation_pref.setEnabled(false);
            return;
        }

        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_ROTATION).equals("true")) {
            rotation_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_rotation_active));
        } else {
            rotation_pref.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_rotation));
        }

        final CheckBoxPreference rotation = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_ROTATION);
        rotation.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_ROTATION).equals("true"));
        rotation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_SENSOR);
                    rotation.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_ROTATION, false);
                    return false;
                }

                Aware.setSetting(awareContext, Aware_Preferences.STATUS_ROTATION, rotation.isChecked());
                if (rotation.isChecked()) {
                    Aware.startRotation(awareContext);
                } else {
                    Aware.stopRotation(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) rotation.setSelectable(false);

        final ListPreference frequency_rotation = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_ROTATION);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ROTATION).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ROTATION);
            frequency_rotation.setSummary(freq);
        }
        frequency_rotation.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_ROTATION));
        frequency_rotation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_ROTATION, (String) newValue);
                frequency_rotation.setSummary((String) newValue);
                Aware.startRotation(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_rotation.setSelectable(false);

        final EditTextPreference threshold_rotation = (EditTextPreference) findPreference(Aware_Preferences.THRESHOLD_ROTATION);
        if (Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ROTATION).length() > 0) {
            String threshold = Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ROTATION);
            threshold_rotation.setSummary(threshold);
        }
        threshold_rotation.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.THRESHOLD_ROTATION));
        threshold_rotation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.THRESHOLD_ROTATION, (String) newValue);
                threshold_rotation.setSummary((String) newValue);
                Aware.startRotation(awareContext);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) threshold_rotation.setSelectable(false);
    }

    /**
     * Telephony module settings UI
     */
    private void telephony() {
        final PreferenceScreen telephonies = (PreferenceScreen) findPreference("telephony");
        if (Aware.is_watch(awareContext)) {
            telephonies.setEnabled(false);
            return;
        }
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY).equals("true")) {
            telephonies.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_telephony_active));
        } else {
            telephonies.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_telephony));
        }
        final CheckBoxPreference telephony = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_TELEPHONY);
        telephony.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY).equals("true"));
        telephony.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY, telephony.isChecked());
                if (telephony.isChecked()) {
                    Aware.startTelephony(awareContext);
                } else {
                    Aware.stopTelephony(awareContext);
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) telephony.setSelectable(false);
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
        final PreferenceScreen webservices = (PreferenceScreen) findPreference("webservice");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
            webservices.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_study_active));
        } else {
            webservices.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_study));
        }

        final CheckBoxPreference webservice = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_WEBSERVICE);
        webservice.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_WEBSERVICE).equals("true"));
        webservice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER).length() == 0) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_WEBSERVICE, false);
                    webservice.setChecked(false);
                    return false;
                } else {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_WEBSERVICE, webservice.isChecked());
                    if (webservice.isChecked() && Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER).length() > 0) {
                        Aware.joinStudy(awareContext, Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER));

                        Intent study_scan = new Intent();
                        study_scan.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER));
                        setResult(Activity.RESULT_OK, study_scan);
                        finish();
                    }
                    return true;
                }
            }
        });
        if (Aware.isStudy(awareContext)) webservice.setSelectable(false);

        final EditTextPreference webservice_server = (EditTextPreference) findPreference(Aware_Preferences.WEBSERVICE_SERVER);
        webservice_server.setText(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER));
        if (Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER).length() > 0) {
            webservice_server.setSummary(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER));
        }
        webservice_server.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER, (String) newValue);
                webservice_server.setSummary((String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) webservice_server.setSelectable(false);

        final CheckBoxPreference webservice_wifi_only = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        webservice_wifi_only.setChecked(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true"));
        webservice_wifi_only.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_WIFI_ONLY, webservice_wifi_only.isChecked());
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) webservice_wifi_only.setSelectable(false);

        final CheckBoxPreference webservice_charging = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_CHARGING);
        webservice_charging.setChecked(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_CHARGING).equals("true"));
        webservice_charging.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_CHARGING, webservice_charging.isChecked());
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) webservice_charging.setSelectable(false);

        final EditTextPreference frequency_webservice = (EditTextPreference) findPreference(Aware_Preferences.FREQUENCY_WEBSERVICE);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WEBSERVICE).length() > 0) {
            frequency_webservice.setSummary(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WEBSERVICE) + " minutes");
        }
        frequency_webservice.setText(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_WEBSERVICE));
        frequency_webservice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_WEBSERVICE, (String) newValue);
                frequency_webservice.setSummary((String) newValue + " minutes");
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) frequency_webservice.setSelectable(false);

        final ListPreference clean_old_data = (ListPreference) findPreference(Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA);
        if (Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).length() > 0) {
            String freq = Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA);
            if (freq.equals("0")) {
                clean_old_data.setSummary("Never");
            } else if (freq.equals("1")) {
                clean_old_data.setSummary("Weekly");
            } else if (freq.equals("2")) {
                clean_old_data.setSummary("Monthly");
            } else if (freq.equals("3")) {
                clean_old_data.setSummary("Daily");
            } else if (freq.equals("4")) {
                clean_old_data.setSummary("Always");
            }
        }
        clean_old_data.setDefaultValue(Aware.getSetting(awareContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA));
        clean_old_data.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA, (String) newValue);
                if (newValue.equals("0")) {
                    clean_old_data.setSummary("Never");
                } else if (((String) newValue).equals("1")) {
                    clean_old_data.setSummary("Weekly");
                } else if (((String) newValue).equals("2")) {
                    clean_old_data.setSummary("Monthly");
                } else if (((String) newValue).equals("3")) {
                    clean_old_data.setSummary("Daily");
                } else if (((String) newValue).equals("4")) {
                    clean_old_data.setSummary("Always");
                }
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) clean_old_data.setSelectable(false);
    }

    /**
     * MQTT module settings UI
     */
    private void mqtt() {
        final PreferenceScreen mqtts = (PreferenceScreen) findPreference("mqtt");
        if (Aware.getSetting(awareContext, Aware_Preferences.STATUS_MQTT).equals("true")) {
            mqtts.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_mqtt_active));
        } else {
            mqtts.setIcon(ContextCompat.getDrawable(awareContext, R.drawable.ic_action_mqtt));
        }

        final CheckBoxPreference mqtt = (CheckBoxPreference) findPreference(Aware_Preferences.STATUS_MQTT);
        mqtt.setChecked(Aware.getSetting(awareContext, Aware_Preferences.STATUS_MQTT).equals("true"));
        mqtt.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Aware.getSetting(awareContext, Aware_Preferences.MQTT_SERVER).length() == 0) {
                    clientUI.showDialog(DIALOG_ERROR_MISSING_PARAMETERS);
                    mqtt.setChecked(false);
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_MQTT, false);
                    return false;
                } else {
                    Aware.setSetting(awareContext, Aware_Preferences.STATUS_MQTT, mqtt.isChecked());
                    if (mqtt.isChecked()) {
                        Aware.startMQTT(awareContext);
                    } else {
                        Aware.stopMQTT(awareContext);
                    }
                    return true;
                }
            }
        });
        if (Aware.isStudy(awareContext)) mqtt.setSelectable(false);

        final EditTextPreference mqttServer = (EditTextPreference) findPreference(Aware_Preferences.MQTT_SERVER);
        mqttServer.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_SERVER));
        if (Aware.getSetting(awareContext, Aware_Preferences.MQTT_SERVER).length() > 0) {
            mqttServer.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MQTT_SERVER));
        }
        mqttServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_SERVER, (String) newValue);
                mqttServer.setSummary((String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttServer.setSelectable(false);

        final EditTextPreference mqttPort = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PORT);
        if (Aware.getSetting(awareContext, Aware_Preferences.MQTT_PORT).length() > 0) {
            mqttPort.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MQTT_PORT));
        }
        mqttPort.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_PORT));
        mqttPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_PORT, (String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttPort.setSelectable(false);

        final EditTextPreference mqttUsername = (EditTextPreference) findPreference(Aware_Preferences.MQTT_USERNAME);
        if (Aware.getSetting(awareContext, Aware_Preferences.MQTT_USERNAME).length() > 0) {
            mqttUsername.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MQTT_USERNAME));
        }
        mqttUsername.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_USERNAME));
        mqttUsername.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_USERNAME, (String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttUsername.setSelectable(false);

        final EditTextPreference mqttPassword = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PASSWORD);
        mqttPassword.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_PASSWORD));
        mqttPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_PASSWORD, (String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttPassword.setSelectable(false);

        final EditTextPreference mqttKeepAlive = (EditTextPreference) findPreference(Aware_Preferences.MQTT_KEEP_ALIVE);
        if (Aware.getSetting(awareContext, Aware_Preferences.MQTT_KEEP_ALIVE).length() > 0) {
            mqttKeepAlive.setSummary(Aware.getSetting(awareContext, Aware_Preferences.MQTT_KEEP_ALIVE) + " seconds");
        }
        mqttKeepAlive.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_KEEP_ALIVE));
        mqttKeepAlive.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_KEEP_ALIVE, (String) newValue);
                mqttKeepAlive.setSummary((String) newValue + " seconds");
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttKeepAlive.setSelectable(false);

        final EditTextPreference mqttQoS = (EditTextPreference) findPreference(Aware_Preferences.MQTT_QOS);
        mqttQoS.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_QOS));
        mqttQoS.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_QOS, (String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttQoS.setSelectable(false);

        final EditTextPreference mqttProtocol = (EditTextPreference) findPreference(Aware_Preferences.MQTT_PROTOCOL);
        mqttProtocol.setText(Aware.getSetting(awareContext, Aware_Preferences.MQTT_PROTOCOL));
        mqttProtocol.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.MQTT_PROTOCOL, (String) newValue);
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) mqttProtocol.setSelectable(false);
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
        debug_flag.setChecked(Aware.getSetting(awareContext, Aware_Preferences.DEBUG_FLAG).equals("true"));
        debug_flag.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.DEBUG = debug_flag.isChecked();
                Aware.setSetting(awareContext, Aware_Preferences.DEBUG_FLAG, debug_flag.isChecked());
                return true;
            }
        });

        final EditTextPreference debug_tag = (EditTextPreference) findPreference(Aware_Preferences.DEBUG_TAG);
        debug_tag.setText(Aware.getSetting(awareContext, Aware_Preferences.DEBUG_TAG));
        debug_tag.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.TAG = (String) newValue;
                Aware.setSetting(awareContext, Aware_Preferences.DEBUG_TAG, (String) newValue);
                return true;
            }
        });

        final EditTextPreference aware_version = (EditTextPreference) findPreference(Aware_Preferences.AWARE_VERSION);
        PackageInfo awareInfo = null;
        try {
            awareInfo = awareContext.getPackageManager().getPackageInfo(awareContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        aware_version.setSummary((awareInfo != null) ? "" + awareInfo.versionCode : "???");

        final CheckBoxPreference debug_db_slow = (CheckBoxPreference) findPreference(Aware_Preferences.DEBUG_DB_SLOW);
        debug_db_slow.setChecked(Aware.getSetting(awareContext, Aware_Preferences.DEBUG_DB_SLOW).equals("true"));
        debug_db_slow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.DEBUG_DB_SLOW, debug_db_slow.isChecked());
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) debug_db_slow.setSelectable(false);

        final EditTextPreference device_id = (EditTextPreference) findPreference(Aware_Preferences.DEVICE_ID);
        device_id.setSummary("UUID: " + Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
        device_id.setText(Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
        device_id.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.DEVICE_ID, (String) newValue);
                device_id.setSummary("UUID: " + Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
                return true;
            }
        });

        final EditTextPreference device_label = (EditTextPreference) findPreference(Aware_Preferences.DEVICE_LABEL);
        device_label.setSummary(Aware.getSetting(awareContext, Aware_Preferences.DEVICE_LABEL));
        device_label.setText(Aware.getSetting(awareContext, Aware_Preferences.DEVICE_LABEL));
        device_label.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Aware.setSetting(awareContext, Aware_Preferences.DEVICE_LABEL, newValue);
                device_label.setSummary(Aware.getSetting(awareContext, Aware_Preferences.DEVICE_LABEL));
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) device_label.setSelectable(false);


        final CheckBoxPreference webservice_simple = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_SIMPLE);
        webservice_simple.setChecked(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SIMPLE).equals("true"));
        webservice_simple.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_SIMPLE, webservice_simple.isChecked());
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) webservice_simple.setSelectable(false);

        final CheckBoxPreference webservice_remove_data = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_REMOVE_DATA);
        webservice_remove_data.setChecked(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_REMOVE_DATA).equals("true"));
        webservice_remove_data.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_REMOVE_DATA, webservice_remove_data.isChecked());
                return true;
            }
        });
        if (Aware.isStudy(awareContext)) webservice_remove_data.setSelectable(false);

        final CheckBoxPreference webservice_silent = (CheckBoxPreference) findPreference(Aware_Preferences.WEBSERVICE_SILENT);
        webservice_silent.setChecked(Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SILENT).equals("true"));
        webservice_silent.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.WEBSERVICE_SILENT, webservice_silent.isChecked());
                return true;
            }
        });
        // Users can always adjust this!  But it might be overwritten on next config update.
        //if (Aware.isStudy(awareContext)) webservice_silent.setSelectable(false);

        final CheckBoxPreference aware_donate_usage = (CheckBoxPreference) findPreference(Aware_Preferences.AWARE_DONATE_USAGE);
        aware_donate_usage.setChecked(Aware.getSetting(awareContext, Aware_Preferences.AWARE_DONATE_USAGE).equals("true"));
        aware_donate_usage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Aware.setSetting(awareContext, Aware_Preferences.AWARE_DONATE_USAGE, aware_donate_usage.isChecked());
                if (aware_donate_usage.isChecked()) {
                    Toast.makeText(awareContext, "Thanks!", Toast.LENGTH_SHORT).show();
                    new AsyncPing().execute();
                }
                return true;
            }
        });
        // Users can always choose to donate data, even if in a study.
        //if (Aware.isStudy(awareContext)) aware_donate_data.setSelectable(false);
    }

    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // Download the certificate, and block since we are already running in background
            // and we need the certificate immediately.
            SSLManager.downloadCertificate(awareContext, "api.awareframework.com", true);

            //Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
            device_ping.put("platform", "android");
            try {
                PackageInfo package_info = awareContext.getPackageManager().getPackageInfo(awareContext.getPackageName(), 0);
                device_ping.put("package_name", package_info.packageName);
                if (package_info.packageName.equals("com.aware.phone")) {
                    device_ping.put("package_version_code", String.valueOf(package_info.versionCode));
                    device_ping.put("package_version_name", String.valueOf(package_info.versionName));
                }
            } catch (PackageManager.NameNotFoundException e) {
            }

            try {
                new Https(awareContext, SSLManager.getHTTPS(getApplicationContext(), "https://api.awareframework.com/index.php")).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }
    }
}