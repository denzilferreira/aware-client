
package com.aware.phone;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ListAdapter;
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
 * @author df
 */
public class Aware_Client extends Aware_Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();
    private static boolean permissions_ok = true;
    private static Hashtable<String, Boolean> listSensorType;
    private List<String[]> optionalSensors = new ArrayList<>();

    private static SharedPreferences prefs;
    private SettingsSync settingsSync = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        optionalSensors.add(new String[]{Aware_Preferences.STATUS_ACCELEROMETER, Sensor.STRING_TYPE_ACCELEROMETER});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_SIGNIFICANT_MOTION, Sensor.STRING_TYPE_ACCELEROMETER});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_BAROMETER, Sensor.STRING_TYPE_PRESSURE});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_GRAVITY, Sensor.STRING_TYPE_GRAVITY});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_GYROSCOPE, Sensor.STRING_TYPE_GYROSCOPE});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_LIGHT, Sensor.STRING_TYPE_LIGHT});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_LINEAR_ACCELEROMETER, Sensor.STRING_TYPE_LINEAR_ACCELERATION});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_MAGNETOMETER, Sensor.STRING_TYPE_MAGNETIC_FIELD});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_PROXIMITY, Sensor.STRING_TYPE_PROXIMITY});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_ROTATION, Sensor.STRING_TYPE_ROTATION_VECTOR});
        optionalSensors.add(new String[]{Aware_Preferences.STATUS_TEMPERATURE, Sensor.STRING_TYPE_AMBIENT_TEMPERATURE});

        SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
        listSensorType = new Hashtable<>();
        for (int i = 0; i < sensors.size(); i++) {
            listSensorType.put(sensors.get(i).getStringType(), true);
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Sensor: " + sensors.get(i).getStringType() + " " + sensors.get(i).getType() + " " + Sensor.TYPE_GYROSCOPE);
        }

        addPreferencesFromResource(R.xml.aware_preferences);
        setContentView(R.layout.aware_ui);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_WIFI_STATE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.CAMERA);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_PHONE_STATE);

        permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {

            Intent startAware = new Intent(this, Aware.class);
            startService(startAware);

            prefs = getSharedPreferences("com.aware.phone", Context.MODE_PRIVATE);
            if (prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                PreferenceManager.setDefaultValues(getApplicationContext(), "com.aware.phone", Context.MODE_PRIVATE, com.aware.R.xml.aware_preferences, true);
                prefs.edit().commit();
            } else {
                PreferenceManager.setDefaultValues(getApplicationContext(), "com.aware.phone", Context.MODE_PRIVATE, R.xml.aware_preferences, false);
            }

            Map<String, ?> defaults = prefs.getAll();
            for (Map.Entry<String, ?> entry : defaults.entrySet()) {
                if (Aware.getSetting(getApplicationContext(), entry.getKey(), "com.aware.phone").length() == 0) {
                    Aware.setSetting(getApplicationContext(), entry.getKey(), entry.getValue(), "com.aware.phone"); //default AWARE settings
                }
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0) {
                UUID uuid = UUID.randomUUID();
                Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, uuid.toString(), "com.aware.phone");
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER, "https://api.awareframework.com/index.php");
            }

            for (String[] optionalSensor : optionalSensors) {
                Preference pref = findPreference(optionalSensor[0]);
                PreferenceGroup parent = getPreferenceParent(pref);
                if (pref.getKey().equalsIgnoreCase(optionalSensor[0]) && !listSensorType.containsKey(optionalSensor[1]))
                    parent.setEnabled(false);
            }

            try {
                PackageInfo awareInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_ACTIVITIES);
                Aware.setSetting(getApplicationContext(), Aware_Preferences.AWARE_VERSION, awareInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            //Check if AWARE is active on the accessibility services
            if (!Aware.is_watch(this)) {
                Applications.isAccessibilityServiceActive(this);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, final Preference preference) {
        if (preference instanceof PreferenceScreen) {
            Dialog subpref = ((PreferenceScreen) preference).getDialog();
            ViewGroup root = (ViewGroup) subpref.findViewById(android.R.id.content).getParent();
            Toolbar toolbar = new Toolbar(this);
            toolbar.setBackgroundColor(ContextCompat.getColor(preferenceScreen.getContext(), R.color.primary));
            toolbar.setTitleTextColor(ContextCompat.getColor(preferenceScreen.getContext(), android.R.color.white));
            toolbar.setTitle(preference.getTitle());
            root.addView(toolbar, 0); //add to the top

            subpref.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (settingsSync == null) {
                        settingsSync = new SettingsSync();
                        settingsSync.execute(preference);
                    }
                }
            });
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String value = "";
        Map<String, ?> keys = sharedPreferences.getAll();
        if (keys.containsKey(key)) {
            Object entry = keys.get(key);
            if (entry instanceof Boolean)
                value = String.valueOf(sharedPreferences.getBoolean(key, false));
            else if (entry instanceof String)
                value = String.valueOf(sharedPreferences.getString(key, "error"));
            else if (entry instanceof Integer)
                value = String.valueOf(sharedPreferences.getInt(key, 0));
        }

        Aware.setSetting(getApplicationContext(), key, value);
        Preference pref = findPreference(key);
        if (CheckBoxPreference.class.isInstance(pref)) {
            CheckBoxPreference check = (CheckBoxPreference) findPreference(key);
            check.setChecked(Aware.getSetting(getApplicationContext(), key).equals("true"));

            //update the parent to show active/inactive
            new SettingsSync().execute(pref);
        }
        if (EditTextPreference.class.isInstance(pref)) {
            EditTextPreference text = (EditTextPreference) findPreference(key);
            text.setSummary(Aware.getSetting(getApplicationContext(), key));
        }
        if (ListPreference.class.isInstance(pref)) {
            ListPreference list = (ListPreference) findPreference(key);
            list.setSummary(list.getEntry());
        }

        Aware.toggleSensors(getApplicationContext());
    }

    private class SettingsSync extends AsyncTask<Preference, Preference, Void> {
        @Override
        protected Void doInBackground(Preference... params) {
            for (Preference pref : params) {
                publishProgress(pref);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Preference... values) {
            super.onProgressUpdate(values);

            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Preference: " + values[0].getKey() + " parent: " + getPreferenceParent(values[0]).getKey());

            Preference pref = values[0];

            if (CheckBoxPreference.class.isInstance(pref)) {

                CheckBoxPreference check = (CheckBoxPreference) findPreference(pref.getKey());
                check.setChecked(Aware.getSetting(getApplicationContext(), pref.getKey()).equals("true"));

                if (check.isChecked()) {
                    if (pref.getKey().equalsIgnoreCase(Aware_Preferences.AWARE_DONATE_USAGE)) {
                        Toast.makeText(getApplicationContext(), "Thanks!", Toast.LENGTH_SHORT).show();
                        new AsyncPing().execute();
                    }
                    if (pref.getKey().equalsIgnoreCase(Aware_Preferences.STATUS_WEBSERVICE)) {
                        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER).length() == 0) {
                            Toast.makeText(getApplicationContext(), "Study URL missing...", Toast.LENGTH_SHORT).show();
                        } else if (!Aware.isStudy(getApplicationContext())) {
                            Aware.joinStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                            Intent study_scan = new Intent();
                            study_scan.putExtra(Aware_Join_Study.EXTRA_STUDY_URL, Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                            setResult(Activity.RESULT_OK, study_scan);
                            finish();
                        }
                    }
                }
                if (check.getKey().contains("status_")) {
                    Preference parent = getPreferenceParent(check);
                    if (PreferenceScreen.class.isInstance(parent)) {
                        PreferenceScreen parent_category = (PreferenceScreen) findPreference(parent.getKey());
                        if (parent_category != null) {
                            Drawable category_icon = parent_category.getIcon();
                            if (category_icon != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
                                if (check.isChecked()) {
                                    category_icon.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.accent), PorterDuff.Mode.SRC_IN));
                                } else {
                                    category_icon.clearColorFilter();
                                }
                                //Fixed: the icons are redrawn
                                onContentChanged();
                            }
                        }
                    }
                }
            }

            if (EditTextPreference.class.isInstance(pref)) {
                EditTextPreference text = (EditTextPreference) findPreference(pref.getKey());
                text.setSummary(Aware.getSetting(getApplicationContext(), pref.getKey()));
            }

            if (ListPreference.class.isInstance(pref)) {
                ListPreference list = (ListPreference) findPreference(pref.getKey());
                list.setSummary(list.getEntry());
            }

            if (PreferenceScreen.class.isInstance(pref)) {
                PreferenceScreen category = (PreferenceScreen) findPreference(pref.getKey());
                if (category != null) {
                    Drawable category_icon = category.getIcon();
                    if (category_icon != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
                        ListAdapter children = category.getRootAdapter();
                        for (int i = 0; i < children.getCount(); i++) {
                            Object obj = children.getItem(i);
                            if (CheckBoxPreference.class.isInstance(obj)) {
                                CheckBoxPreference child = (CheckBoxPreference) obj;
                                if (child.getKey().contains("status_")) {
                                    if (child.isChecked()) {
                                        category_icon.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.accent), PorterDuff.Mode.SRC_IN));
                                    } else {
                                        category_icon.clearColorFilter();
                                    }
                                }
                            }
                        }
                        //Fixed: the icons are redrawn
                        onContentChanged();
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            settingsSync = null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            settingsSync = null;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!permissions_ok) {
            Intent permissionsHandler = new Intent(this, PermissionsHandler.class);
            permissionsHandler.putStringArrayListExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissionsHandler.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getClass().getName());
            startActivityForResult(permissionsHandler, PermissionsHandler.RC_PERMISSIONS);
            finish();
            return;
        }

        prefs = getSharedPreferences("com.aware.phone", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (settingsSync == null) settingsSync = new SettingsSync();
        if (settingsSync.getStatus() != AsyncTask.Status.RUNNING) {
            settingsSync.execute(
                    findPreference(Aware_Preferences.DEVICE_ID),
                    findPreference(Aware_Preferences.DEVICE_LABEL),
                    findPreference(Aware_Preferences.AWARE_VERSION),
                    findPreference(Aware_Preferences.STATUS_ACCELEROMETER),
                    findPreference(Aware_Preferences.STATUS_APPLICATIONS),
                    findPreference(Aware_Preferences.STATUS_BAROMETER),
                    findPreference(Aware_Preferences.STATUS_BATTERY),
                    findPreference(Aware_Preferences.STATUS_BLUETOOTH),
                    findPreference(Aware_Preferences.STATUS_COMMUNICATION_EVENTS),
                    findPreference(Aware_Preferences.STATUS_CALLS),
                    findPreference(Aware_Preferences.STATUS_MESSAGES),
                    findPreference(Aware_Preferences.STATUS_ESM),
                    findPreference(Aware_Preferences.STATUS_GRAVITY),
                    findPreference(Aware_Preferences.STATUS_GYROSCOPE),
                    findPreference(Aware_Preferences.STATUS_LOCATION_NETWORK),
                    findPreference(Aware_Preferences.STATUS_LOCATION_GPS),
                    findPreference(Aware_Preferences.STATUS_LIGHT),
                    findPreference(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER),
                    findPreference(Aware_Preferences.STATUS_NETWORK_EVENTS),
                    findPreference(Aware_Preferences.STATUS_NETWORK_TRAFFIC),
                    findPreference(Aware_Preferences.STATUS_MAGNETOMETER),
                    findPreference(Aware_Preferences.STATUS_PROCESSOR),
                    findPreference(Aware_Preferences.STATUS_TIMEZONE),
                    findPreference(Aware_Preferences.STATUS_PROXIMITY),
                    findPreference(Aware_Preferences.STATUS_ROTATION),
                    findPreference(Aware_Preferences.STATUS_SCREEN),
                    findPreference(Aware_Preferences.STATUS_SIGNIFICANT_MOTION),
                    findPreference(Aware_Preferences.STATUS_TEMPERATURE),
                    findPreference(Aware_Preferences.STATUS_TELEPHONY),
                    findPreference(Aware_Preferences.STATUS_WIFI),
                    findPreference(Aware_Preferences.STATUS_MQTT),
                    findPreference(Aware_Preferences.STATUS_WEBSERVICE)
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (prefs != null)
            prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // Download the certificate, and block since we are already running in background
            // and we need the certificate immediately.
            SSLManager.downloadCertificate(getApplicationContext(), "api.awareframework.com", true);

            //Ping AWARE's server with getApplicationContext() device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
            device_ping.put("platform", "android");
            try {
                PackageInfo package_info = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
                device_ping.put("package_name", package_info.packageName);
                if (package_info.packageName.equals("com.aware.phone")) {
                    device_ping.put("package_version_code", String.valueOf(package_info.versionCode));
                    device_ping.put("package_version_name", String.valueOf(package_info.versionName));
                }
            } catch (PackageManager.NameNotFoundException e) {
            }

            try {
                new Https(getApplicationContext(), SSLManager.getHTTPS(getApplicationContext(), "https://api.awareframework.com/index.php")).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }
    }
}