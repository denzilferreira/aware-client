
package com.aware;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Device;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.providers.Aware_Provider.Aware_Settings;
import com.aware.ui.Plugins_Manager;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.DownloadPluginService;
import com.aware.utils.Http;
import com.aware.utils.Https;
import com.aware.utils.Scheduler;
import com.aware.utils.WearClient;
import com.aware.utils.WebserviceHelper;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Main AWARE framework service. awareContext will start and manage all the services and settings.
 * @author denzil
 *
 */
public class Aware extends Service {
    /**
     * Debug flag (default = false).
     */
    public static boolean DEBUG = false;
    
    /**
     * Debug tag (default = "AWARE").
     */
    public static String TAG = "AWARE";
    
    /**
     * Broadcasted event: awareContext device information is available
     */
    public static final String ACTION_AWARE_DEVICE_INFORMATION = "ACTION_AWARE_DEVICE_INFORMATION";
    
    /**
     * Received broadcast on all modules
     * - Sends the data to the defined webserver
     */
    public static final String ACTION_AWARE_SYNC_DATA = "ACTION_AWARE_SYNC_DATA";
    
    /**
     * Received broadcast on all modules
     * - Cleans the data collected on the device
     */
    public static final String ACTION_AWARE_CLEAR_DATA = "ACTION_AWARE_CLEAR_DATA";
    
    /**
     * Received broadcast: refresh the framework active sensors.
     */
    protected static final String ACTION_AWARE_REFRESH = "ACTION_AWARE_REFRESH";
    
    /**
     * Received broadcast: plugins must implement awareContext broadcast receiver to share their current status.
     */
    public static final String ACTION_AWARE_CURRENT_CONTEXT = "ACTION_AWARE_CURRENT_CONTEXT";
    
    /**
     * Stop all plugins
     */
    public static final String ACTION_AWARE_STOP_PLUGINS = "ACTION_AWARE_STOP_PLUGINS";
    
    /**
     * Stop all sensors
     */
    public static final String ACTION_AWARE_STOP_SENSORS = "ACTION_AWARE_STOP_SENSORS";
    
    /**
     * Received broadcast on all modules
     * - Cleans old data from the content providers
     */
    public static final String ACTION_AWARE_SPACE_MAINTENANCE = "ACTION_AWARE_SPACE_MAINTENANCE";
    
    /**
     * Used by Plugin Manager to refresh UI
     */
    public static final String ACTION_AWARE_PLUGIN_MANAGER_REFRESH = "ACTION_AWARE_PLUGIN_MANAGER_REFRESH";

    /**
     * Used when quitting a study. This will reset the device to default settings.
     */
    public static final String ACTION_QUIT_STUDY = "ACTION_QUIT_STUDY";

    /**
     * Ask the client to check if there are any updates on the server
     */
    public static final String ACTION_AWARE_CHECK_UPDATE = "ACTION_AWARE_CHECK_UPDATE";

    public static String STUDY_ID = "study_id";
    public static String STUDY_START = "study_start";

    private static AlarmManager alarmManager = null;
    private static PendingIntent repeatingIntent = null;
    private static Context awareContext = null;
    private static PendingIntent webserviceUploadIntent = null;
    
    private static Intent awareStatusMonitor = null;
    private static Intent applicationsSrv = null;
    private static Intent accelerometerSrv = null;
    private static Intent locationsSrv = null;
    private static Intent bluetoothSrv = null;
    private static Intent screenSrv = null;
    private static Intent batterySrv = null;
    private static Intent networkSrv = null;
    private static Intent trafficSrv = null;
    private static Intent communicationSrv = null;
    private static Intent processorSrv = null;
    private static Intent mqttSrv = null;
    private static Intent gyroSrv = null;
    private static Intent wifiSrv = null;
    private static Intent telephonySrv = null;
    private static Intent timeZoneSrv = null;
    private static Intent rotationSrv = null;
    private static Intent lightSrv = null;
    private static Intent proximitySrv = null;
    private static Intent magnetoSrv = null;
    private static Intent barometerSrv = null;
    private static Intent gravitySrv = null;
    private static Intent linear_accelSrv = null;
    private static Intent temperatureSrv = null;
    private static Intent esmSrv = null;
    private static Intent installationsSrv = null;
    private static Intent keyboard = null;
    private static Intent wearClient = null;
    private static Intent scheduler = null;
    
    private final static String PREF_FREQUENCY_WATCHDOG = "frequency_watchdog";
    private final static String PREF_LAST_UPDATE = "last_update";
    private final static String PREF_LAST_SYNC = "last_sync";
    private final static int CONST_FREQUENCY_WATCHDOG = 5 * 60; //5 minutes check
    
    private static SharedPreferences aware_preferences;
    
    /**
     * Singleton instance of the framework
     */
    private static Aware awareSrv = Aware.getService();
    
    /**
     * Get the singleton instance to the AWARE framework
     * @return {@link Aware} obj
     */
    public static Aware getService() {
        if( awareSrv == null ) awareSrv = new Aware();
        return awareSrv;

    }

    /**
     * Activity-Service binder
     */
    private final IBinder serviceBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        Aware getService() {
            return Aware.getService();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        awareContext = getApplicationContext();
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        awareContext.registerReceiver(storage_BR, filter);
        
        filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_REFRESH);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_QUIT_STUDY);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(Aware.ACTION_AWARE_CHECK_UPDATE);
        awareContext.registerReceiver(aware_BR, filter);

        Intent synchronise = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        webserviceUploadIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, synchronise, 0);
        
        if( ! Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ) {
            stopSelf();
            return;
        }

        aware_preferences = getSharedPreferences("aware_core_prefs", MODE_PRIVATE);
        if( aware_preferences.getAll().isEmpty() ) {
            SharedPreferences.Editor editor = aware_preferences.edit();
            editor.putInt(PREF_FREQUENCY_WATCHDOG, CONST_FREQUENCY_WATCHDOG);
            editor.putLong(PREF_LAST_SYNC, 0);
            editor.putLong(PREF_LAST_UPDATE, 0);
            editor.commit();
        }

        //this sets the default settings to all plugins too
        SharedPreferences prefs = getSharedPreferences( "com.aware", Context.MODE_PRIVATE );
        if( prefs.getAll().isEmpty() && Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0 ) {
            PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
            prefs.edit().commit(); //commit changes
        } else {
            PreferenceManager.setDefaultValues(getApplicationContext(), getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, false);
        }

        Map<String,?> defaults = prefs.getAll();
        for(Map.Entry<String, ?> entry : defaults.entrySet()) {
            if( Aware.getSetting(getApplicationContext(), entry.getKey(), "com.aware").length() == 0 ) {
                Aware.setSetting(getApplicationContext(), entry.getKey(), entry.getValue(), "com.aware"); //default AWARE settings
            }
        }

        if( Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID).length() == 0 ) {
            UUID uuid = UUID.randomUUID();
            Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID, uuid.toString(), "com.aware");
        }

        DEBUG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_FLAG).equals("true");
        TAG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG):TAG;

        get_device_info();

        if( Aware.DEBUG ) Log.d(TAG,"AWARE framework is created!");

        //Only the official client will do this.
        if ( getPackageName().equals("com.aware") ) {
            if (DEBUG) Log.d(TAG, "Starting Android Wear HTTP proxy...");
            wearClient = new Intent(this, WearClient.class);
            startService(wearClient);

            if( Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER).contains("api.awareframework.com") ) {
                new AsyncPing().execute();
            }
        }

        awareStatusMonitor = new Intent(this, Aware.class);
        repeatingIntent = PendingIntent.getService(getApplicationContext(), 0, awareStatusMonitor, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, aware_preferences.getInt(PREF_FREQUENCY_WATCHDOG, 300) * 1000, repeatingIntent);
    }
    
    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			//Ping AWARE's server with awareContext device's information for framework's statistics log
            Hashtable<String, String> device_ping = new Hashtable<>();
            device_ping.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
	        device_ping.put("ping", String.valueOf(System.currentTimeMillis()));
	        new Https(awareContext, getResources().openRawResource(R.raw.awareframework)).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping, true);
	        return true;
		}
    }
    
    private void get_device_info() {
        Cursor awareContextDevice = awareContext.getContentResolver().query(Aware_Device.CONTENT_URI, null, null, null, null);
        if( awareContextDevice == null || ! awareContextDevice.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Device.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Aware_Device.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            rowData.put(Aware_Device.BOARD, Build.BOARD);
            rowData.put(Aware_Device.BRAND, Build.BRAND);
            rowData.put(Aware_Device.DEVICE,Build.DEVICE);
            rowData.put(Aware_Device.BUILD_ID, Build.DISPLAY);
            rowData.put(Aware_Device.HARDWARE, Build.HARDWARE);
            rowData.put(Aware_Device.MANUFACTURER, Build.MANUFACTURER);
            rowData.put(Aware_Device.MODEL, Build.MODEL);
            rowData.put(Aware_Device.PRODUCT, Build.PRODUCT);
            rowData.put(Aware_Device.SERIAL, Build.SERIAL);
            rowData.put(Aware_Device.RELEASE, Build.VERSION.RELEASE);
            rowData.put(Aware_Device.RELEASE_TYPE, Build.TYPE);
            rowData.put(Aware_Device.SDK, Build.VERSION.SDK_INT);

            //Added research group as label
            rowData.put(Aware_Device.LABEL, Aware.getSetting(awareContext, Aware_Preferences.GROUP_ID));
            
            try {
                awareContext.getContentResolver().insert(Aware_Device.CONTENT_URI, rowData);
                
                Intent deviceData = new Intent(ACTION_AWARE_DEVICE_INFORMATION);
                sendBroadcast(deviceData);
                
                if( Aware.DEBUG ) Log.d(TAG, "Device information:"+ rowData.toString());
                
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }
        if( awareContextDevice != null && ! awareContextDevice.isClosed()) awareContextDevice.close();
    }

    /**
     * Identifies if the device is a watch or a phone.
     * @param c
     * @return boolean
     */
    public static boolean is_watch(Context c) {
        UiModeManager uiManager = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        if( uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH ) {
            return true;
        }
        return false;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ) {
            DEBUG = Aware.getSetting(awareContext,Aware_Preferences.DEBUG_FLAG).equals("true");
            TAG = Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG):TAG;
            
            if( Aware.DEBUG ) Log.d(TAG,"AWARE framework is active...");

            //Boot AWARE services
            startAllServices();

            //Get the active plugins
            ArrayList<String> active_plugins = new ArrayList<>();
            Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if( enabled_plugins != null && enabled_plugins.moveToFirst() ) {
                do {
                    String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    active_plugins.add(package_name);
                }while(enabled_plugins.moveToNext());
            }
            if( enabled_plugins != null && ! enabled_plugins.isClosed() ) enabled_plugins.close();

            //The official client takes care of staying updated to avoid compromising studies
            if( getPackageName().equals("com.aware") ) {
                //Check if there are updates on the plugins
                if( active_plugins.size() > 0 ) {
                    //the phone takes care of updating the watch packages
                    if( ! Aware.is_watch(this) ) {
                        new CheckPlugins().execute(active_plugins);
                    }
                }

                if( Aware.getSetting(getApplicationContext(), Aware_Preferences.AWARE_AUTO_UPDATE).equals("true") ) {
	            	if( aware_preferences.getLong(PREF_LAST_UPDATE, 0) == 0 || (aware_preferences.getLong(PREF_LAST_UPDATE, 0) > 0 && System.currentTimeMillis()-aware_preferences.getLong(PREF_LAST_UPDATE, 0) > 6*60*60*1000) ) { //check every 6h
                        //Check if there are updates to the client
	            		if( ! Aware.is_watch(this) ) {
                            new Update_Check().execute();
                        }
	            		SharedPreferences.Editor editor = aware_preferences.edit();
	            		editor.putLong(PREF_LAST_UPDATE, System.currentTimeMillis());
	            		editor.commit();
	            	}
	            }
            }
            
            if( Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {    
                int frequency_webservice = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE));
                if( frequency_webservice == 0 ) {
                    if(DEBUG) {
                        Log.d(TAG,"Data sync is disabled.");
                    }
                    alarmManager.cancel(webserviceUploadIntent);
                } else if( frequency_webservice > 0 ) {

                    //Checks if study is still active
                    new Study_Check().execute();

                    //Fixed: set alarm only once if not set yet.
                    if( aware_preferences.getLong(PREF_LAST_SYNC, 0) == 0 || (aware_preferences.getLong(PREF_LAST_SYNC, 0) > 0 && System.currentTimeMillis() - aware_preferences.getLong(PREF_LAST_SYNC, 0) > frequency_webservice * 60 * 1000 ) ) {
                    	if( DEBUG ) {
                            Log.d(TAG,"Data sync every " + frequency_webservice + " minute(s)");
                        }
                    	SharedPreferences.Editor editor = aware_preferences.edit();
                    	editor.putLong(PREF_LAST_SYNC, System.currentTimeMillis());
                    	editor.commit();
                    	alarmManager.setInexactRepeating(AlarmManager.RTC, aware_preferences.getLong(PREF_LAST_SYNC, 0), frequency_webservice * 60 * 1000, webserviceUploadIntent);
                    }
                }
            }
            
            if( ! Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).equals("0") ) {
                Intent dataCleaning = new Intent(ACTION_AWARE_SPACE_MAINTENANCE);
                awareContext.sendBroadcast(dataCleaning);
            }

            if( active_plugins.size() > 0 ) {
                for(String package_name : active_plugins ) {
                    startPlugin(getApplicationContext(), package_name);
                }
            }

        } else { //Turn off all enabled plugins and services

            stopAllServices();

            ArrayList<String> active_plugins = new ArrayList<>();
            Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if( enabled_plugins != null && enabled_plugins.moveToFirst() ) {
                do {
                    String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                    active_plugins.add(package_name);
                }while(enabled_plugins.moveToNext());
            }
            if( enabled_plugins != null && ! enabled_plugins.isClosed() ) enabled_plugins.close();

            if( active_plugins.size() > 0 ) {
                for(String package_name : active_plugins ) {
                    stopPlugin(getApplicationContext(), package_name);
                }
                if( Aware.DEBUG ) Log.w(TAG,"AWARE plugins disabled...");
            }
        }
        return START_STICKY;
    }
    
    /**
     * Stops a plugin. Expects the package name of the plugin.
     * @param context
     * @param package_name
     */
    public static void stopPlugin( final Context context, final String package_name ) {
        if( awareContext == null ) awareContext = context;

        //Check if plugin is bundled within an application/plugin
        Intent bundled = new Intent();
        bundled.setClassName( context.getPackageName(), package_name + ".Plugin");
        boolean result = context.stopService(bundled);

        if( result ) {
            if( Aware.DEBUG ) Log.d(TAG, "Bundled " + package_name + ".Plugin stopped...");

            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
            context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);
            return;
        }

        boolean is_installed = false;
        Cursor cached = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
    	if( cached != null && cached.moveToFirst() ) {
            is_installed = true;
    	}
    	if( cached != null && ! cached.isClosed() ) cached.close();

        if( is_installed ) {
            Intent plugin = new Intent();
            plugin.setClassName(package_name, package_name + ".Plugin");
            context.stopService(plugin);

            if( Aware.DEBUG ) Log.d(TAG, package_name + " stopped...");
        }

        ContentValues rowData = new ContentValues();
        rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
        context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);
    }
    
    /**
     * Starts a plugin. Expects the package name of the plugin.
     * It checks if the plugin does exist on the phone. If it doesn't, it will request the user to install it automatically.
     * @param context
     * @param package_name
     */
    public static void startPlugin( final Context context, final String package_name ) {
        if( awareContext == null ) awareContext = context;

        //Check if plugin is bundled within an application/plugin
        Intent bundled = new Intent();
        bundled.setClassName(context.getPackageName(), package_name + ".Plugin");
        ComponentName bundledResult = context.startService(bundled);
        if( bundledResult != null ) {
            if( Aware.DEBUG ) Log.d(TAG, "Bundled " + package_name + ".Plugin started...");

            //Check if plugin is cached
            Cursor cached = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
            if( cached == null || ! cached.moveToFirst() ) {
                //Fixed: add the bundled plugin to the list of installed plugins on the self-contained apps
                ContentValues rowData = new ContentValues();
                rowData.put(Aware_Plugins.PLUGIN_AUTHOR, "Self-packaged");
                rowData.put(Aware_Plugins.PLUGIN_DESCRIPTION, "Bundled with " + context.getPackageName());
                rowData.put(Aware_Plugins.PLUGIN_NAME, "Self-packaged");
                rowData.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, package_name);
                rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                rowData.put(Aware_Plugins.PLUGIN_VERSION, 1);
                context.getContentResolver().insert(Aware_Plugins.CONTENT_URI, rowData);
                if(Aware.DEBUG) Log.d(TAG, "Added self-package " + package_name + " to " + context.getPackageName());
            }
            if( cached != null && ! cached.isClosed() ) cached.close();

        }

        //Check if plugin is cached
        Cursor cached = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if( cached != null && cached.moveToFirst() ) {
            //Installed on the phone
            if( isClassAvailable(context, package_name, "Plugin") ) {
                Intent plugin = new Intent();
                plugin.setClassName(package_name, package_name + ".Plugin");
                ComponentName cachedResult = context.startService(plugin);
                if( cachedResult != null ) {
                    if( Aware.DEBUG ) Log.d(TAG, package_name + " started...");
                    ContentValues rowData = new ContentValues();
                    rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                    context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);
                }
            }
        }
        if( cached != null && ! cached.isClosed() ) cached.close();
    }

    /**
     * Requests the download of a plugin given the package name from AWARE webservices.
     * @param context
     * @param package_name
     * @param is_update
     */
    public static void downloadPlugin( Context context, String package_name, boolean is_update ) {
        Intent pluginIntent = new Intent(context, DownloadPluginService.class);
    	pluginIntent.putExtra("package_name", package_name);
    	pluginIntent.putExtra("is_update", is_update);
		context.startService(pluginIntent);
    }
    
    /**
     * Given a plugin's package name, fetch the context card for reuse.
     * @param context: application context
     * @param package_name: plugin's package name
     * @return View for reuse (instance of LinearLayout)
     */
    public static View getContextCard( final Context context, final String package_name ) {

        if( ! isClassAvailable(context, package_name, "ContextCard") ) {
    		return null;
    	}
    	
    	String ui_class = package_name + ".ContextCard";
    	CardView card = new CardView(context);
    	LayoutParams params = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        params.setMargins(0, 0, 0, 10);
        card.setLayoutParams(params);

    	try {
			Context packageContext = context.createPackageContext(package_name, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
			
			Class<?> fragment_loader = packageContext.getClassLoader().loadClass(ui_class);
            Object fragment = fragment_loader.newInstance();
            Method[] allMethods = fragment_loader.getDeclaredMethods();
            Method m = null;
            for( Method mItem : allMethods ) {
                String mName = mItem.getName();
                if( mName.contains("getContextCard") ) {
                    mItem.setAccessible(true);
                    m = mItem;
                    break;
                }
            }

            View ui = (View) m.invoke( fragment, packageContext );
			if( ui != null ) {
				//Set card look-n-feel
				ui.setBackgroundColor(Color.WHITE);
                ui.setPadding(20, 20, 20, 20);
                card.addView(ui);

                //Check if plugin has settings. Add button if it does.
                if( isClassAvailable(context, package_name, "Settings") ) {
                    RelativeLayout info = new RelativeLayout(context);
                    info.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.BOTTOM);

                    ImageView infoSettings = new ImageView(context);
                    infoSettings.setBackgroundResource(R.drawable.ic_action_plugin_settings);
                    infoSettings.setAdjustViewBounds(true);
                    infoSettings.setMaxWidth(10);
                    infoSettings.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent open_settings = new Intent();
                            open_settings.setClassName(package_name, package_name + ".Settings");
                            open_settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(open_settings);
                        }
                    });

                    info.addView(infoSettings);
                    card.addView(info);
                }
				return card;
			} else {
				return null;
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getPluginName( Context c, String package_name ) {
        String name = "";
        Cursor plugin_name = c.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null, null);
        if( plugin_name != null && plugin_name.moveToFirst() ) {
            name = plugin_name.getString(plugin_name.getColumnIndex(Aware_Plugins.PLUGIN_NAME));
        }
        if( plugin_name != null && ! plugin_name.isClosed()) plugin_name.close();
        return name;
    }
    
    /**
	 * Given a package and class name, check if the class exists or not.
	 * @param package_name
	 * @param class_name
	 * @return true if exists, false otherwise
	 */
	private static boolean isClassAvailable( Context context, String package_name, String class_name ) {
		try{
			Context package_context = context.createPackageContext(package_name, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE); 
			package_context.getClassLoader().loadClass(package_name+"."+class_name);			
		} catch ( ClassNotFoundException e ) {
			return false;
		} catch ( NameNotFoundException e ) {
			return false;
		}
		return true;
	}
    
    /**
     * Retrieve setting value given key.
     * @param key
     * @return value
     */
    public static String getSetting( Context context, String key ) {
        
    	boolean is_global;
    	
    	ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware_Preferences.DEBUG_FLAG);
        global_settings.add(Aware_Preferences.DEBUG_TAG);
        global_settings.add(Aware.STUDY_ID);
        global_settings.add(Aware.STUDY_START);
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.GROUP_ID);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Aware_Preferences.STATUS_APPLICATIONS);
        global_settings.add(Applications.STATUS_AWARE_ACCESSIBILITY);

        //allow plugin's to react to MQTT
        global_settings.add(Aware_Preferences.STATUS_MQTT);
        global_settings.add(Aware_Preferences.MQTT_USERNAME);
        global_settings.add(Aware_Preferences.MQTT_PASSWORD);
        global_settings.add(Aware_Preferences.MQTT_SERVER);
        global_settings.add(Aware_Preferences.MQTT_PORT);
        global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
        global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
        global_settings.add(Aware_Preferences.MQTT_QOS);

        is_global = global_settings.contains(key);
    	
    	String value = "";
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + (( is_global ) ? "'com.aware'" : "'" + context.getPackageName() + "'") + (( is_global ) ? " OR " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE ''":""), null, null);
        if( qry != null && qry.moveToFirst() ) {
            value = qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE));
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
        return value;
    }

    /**
     * Retrieve setting value given a key of a plugin's settings
     * @param context
     * @param key
     * @param package_name
     * @return value
     */
    public static String getSetting( Context context, String key, String package_name ) {
        String value = "";
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if( qry != null && qry.moveToFirst() ) {
            value = qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE));
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
        return value;
    }
    
    /**
     * Insert / Update settings of the framework
     * @param key
     * @param value
     */
    public static void setSetting( Context context, String key, Object value ) {
    	boolean is_global;
    	
    	ArrayList<String> global_settings = new ArrayList<String>();
    	global_settings.add(Aware_Preferences.DEBUG_FLAG);
    	global_settings.add(Aware_Preferences.DEBUG_TAG);
    	global_settings.add(Aware.STUDY_ID);
    	global_settings.add(Aware.STUDY_START);
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.GROUP_ID);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Applications.STATUS_AWARE_ACCESSIBILITY);

        //allow plugins to get accessibility events
        global_settings.add(Aware_Preferences.STATUS_APPLICATIONS);

        //allow plugin's to react to MQTT
        global_settings.add(Aware_Preferences.STATUS_MQTT);
        global_settings.add(Aware_Preferences.MQTT_USERNAME);
        global_settings.add(Aware_Preferences.MQTT_PASSWORD);
        global_settings.add(Aware_Preferences.MQTT_SERVER);
        global_settings.add(Aware_Preferences.MQTT_PORT);
        global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
        global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
        global_settings.add(Aware_Preferences.MQTT_QOS);

        is_global = global_settings.contains(key);

        //We already have a Device ID or Group ID, bail-out!
        if( key.equals(Aware_Preferences.DEVICE_ID) && Aware.getSetting(context, Aware_Preferences.DEVICE_ID).length() > 0 ) return;
        if( key.equals(Aware_Preferences.GROUP_ID) && Aware.getSetting(context, Aware_Preferences.GROUP_ID).length() > 0 ) return;

    	ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        if( is_global ) {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, "com.aware");
        } else {
            setting.put(Aware_Settings.SETTING_PACKAGE_NAME, context.getPackageName());
        }

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE " + (( is_global ) ? "'com.aware'" : "'" + context.getPackageName() + "'"), null, null);
        //update
        if( qry != null && qry.moveToFirst() ) {
            try {
                if( ! qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE)).equals(value.toString()) ) {
                    context.getContentResolver().update(Aware_Settings.CONTENT_URI, setting, Aware_Settings.SETTING_ID + "=" + qry.getInt(qry.getColumnIndex(Aware_Settings.SETTING_ID)), null);
                    if( Aware.DEBUG) Log.d(Aware.TAG,"Updated: "+key+"="+value);
                }
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        //insert
        } else {
            try {
                context.getContentResolver().insert(Aware_Settings.CONTENT_URI, setting);
                if( Aware.DEBUG) Log.d(Aware.TAG,"Added: " + key + "=" + value);
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
    }

    /**
     * Insert / Update settings of a plugin
     * @param key
     * @param value
     * @param package_name
     */
    public static void setSetting( Context context, String key, Object value, String package_name ) {

        //We already have a device ID, bail-out!
        if( key.equals(Aware_Preferences.DEVICE_ID) && Aware.getSetting(context, Aware_Preferences.DEVICE_ID).length() > 0 ) return;

        ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        setting.put(Aware_Settings.SETTING_PACKAGE_NAME, package_name);

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "' AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        //update
        if( qry != null && qry.moveToFirst() ) {
            try {
                if( ! qry.getString(qry.getColumnIndex(Aware_Settings.SETTING_VALUE)).equals(value.toString()) ) {
                    context.getContentResolver().update(Aware_Settings.CONTENT_URI, setting, Aware_Settings.SETTING_ID + "=" + qry.getInt(qry.getColumnIndex(Aware_Settings.SETTING_ID)), null);
                    if( Aware.DEBUG) Log.d(Aware.TAG,"Updated: "+key+"="+value + " in " + package_name);
                }
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
            //insert
        } else {
            try {
                context.getContentResolver().insert(Aware_Settings.CONTENT_URI, setting);
                if( Aware.DEBUG) Log.d(Aware.TAG,"Added: " + key + "=" + value + " in " + package_name);
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.d(TAG,e.getMessage());
            }
        }
        if( qry != null && ! qry.isClosed() ) qry.close();
    }

    /**
     * Ask AWARE to start the sensor, AFTER the settings have been defined
     * @param sensor
     */
    public static void startSensor( Context context, String sensor ) {
        if( sensor.equals(Aware_Preferences.STATUS_ESM) ) {
            startESM(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_APPLICATIONS) ) {
            startApplications(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
            startAccelerometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
            startInstallations(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LOCATION_GPS) || sensor.equals(Aware_Preferences.STATUS_LOCATION_NETWORK) ) {
            startLocations(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BLUETOOTH) ) {
            startBluetooth(context);
        }
        if( sensor.equals( Aware_Preferences.STATUS_SCREEN) ) {
            startScreen(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BATTERY) ) {
            startBattery(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_NETWORK_EVENTS) ) {
            startNetwork(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_NETWORK_TRAFFIC) ) {
            startTraffic(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_COMMUNICATION_EVENTS) || sensor.equals(Aware_Preferences.STATUS_CALLS) || sensor.equals(Aware_Preferences.STATUS_MESSAGES) ) {
            startCommunication(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_PROCESSOR) ) {
            startProcessor(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TIMEZONE) ) {
            startTimeZone(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_MQTT) ) {
            startMQTT(context);
        }
        if( sensor.equals( Aware_Preferences.STATUS_GYROSCOPE) ) {
            startGyroscope(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_WIFI) ) {
            startWiFi(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TELEPHONY) ) {
            startTelephony(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_ROTATION) ) {
            startRotation(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LIGHT) ) {
            startLight(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_PROXIMITY) ) {
            startProximity(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
            startMagnetometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BAROMETER)) {
            startBarometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_GRAVITY) ) {
            startGravity(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER) ) {
            startLinearAccelerometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
            startTemperature(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_KEYBOARD) ) {
            startKeyboard(context);
        }
    }

    /**
     * Ask AWARE to stop a sensor
     * @param sensor
     */
    public static void stopSensor(Context context, String sensor ) {
        if( sensor.equals(Aware_Preferences.STATUS_ESM) ) {
            stopESM(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_APPLICATIONS) ) {
            stopApplications(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_ACCELEROMETER) ) {
            stopAccelerometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_INSTALLATIONS) ) {
            stopInstallations(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LOCATION_GPS) || sensor.equals(Aware_Preferences.STATUS_LOCATION_NETWORK) ) {
            stopLocations(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BLUETOOTH) ) {
            stopBluetooth(context);
        }
        if( sensor.equals( Aware_Preferences.STATUS_SCREEN) ) {
            stopScreen(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BATTERY) ) {
            stopBattery(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_NETWORK_EVENTS) ) {
            stopNetwork(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_NETWORK_TRAFFIC) ) {
            stopTraffic(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_COMMUNICATION_EVENTS) || sensor.equals(Aware_Preferences.STATUS_CALLS) || sensor.equals(Aware_Preferences.STATUS_MESSAGES) ) {
            stopCommunication(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_PROCESSOR) ) {
            stopProcessor(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TIMEZONE) ) {
            stopTimeZone(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_MQTT) ) {
            stopMQTT(context);
        }
        if( sensor.equals( Aware_Preferences.STATUS_GYROSCOPE) ) {
            stopGyroscope(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_WIFI) ) {
            stopWiFi(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TELEPHONY) ) {
            stopTelephony(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_ROTATION) ) {
            stopRotation(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LIGHT) ) {
            stopLight(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_PROXIMITY) ) {
            stopProximity(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_MAGNETOMETER) ) {
            stopMagnetometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_BAROMETER)) {
            stopBarometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_GRAVITY) ) {
            stopGravity(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_LINEAR_ACCELEROMETER) ) {
            stopLinearAccelerometer(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_TEMPERATURE) ) {
            stopTemperature(context);
        }
        if( sensor.equals(Aware_Preferences.STATUS_KEYBOARD) ) {
            stopKeyboard(context);
        }
    }

    /**
     * Allows self-contained apps to join a study
     * @param context
     * @param study_url
     */
    public static void joinStudy( Context context, String study_url ) {
        Intent join = new Intent( context, JoinStudy.class );
        join.putExtra(Aware_Preferences.StudyConfig.EXTRA_JOIN_STUDY, study_url);
        context.startService(join);
    }

    /**
     * Used by self-contained apps to join a study
     */
    public static class JoinStudy extends Aware_Preferences.StudyConfig {
        @Override
        protected void onHandleIntent(Intent intent) {
            String study_url = intent.getStringExtra(EXTRA_JOIN_STUDY);

            if( Aware.DEBUG ) Log.d(Aware.TAG, "Joining: " + study_url);

            //Request study settings
            Hashtable<String, String> data = new Hashtable<>();
            data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));

            String protocol = study_url.substring(0, study_url.indexOf(":"));

            String answer;
            if( protocol.equals("https") ) {
                answer = new Https(getApplicationContext(), getResources().openRawResource(R.raw.awareframework)).dataPOST(study_url, data, true);
            } else {
                answer = new Http(getApplicationContext()).dataPOST(study_url, data, true);
            }

            if( answer == null ) {
                Toast.makeText(getApplicationContext(), "Failed to connect to server, try again.", Toast.LENGTH_LONG).show();
                return;
            }

            try {
                JSONArray configs = new JSONArray(answer);
                if (configs.getJSONObject(0).has("message")) {
                    Toast.makeText(getApplicationContext(), "This study is no longer available.", Toast.LENGTH_LONG).show();
                    return;
                }

                //Apply study settings
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
                        Aware.setSetting( getApplicationContext(), sensor_config.getString("setting"), sensor_config.get("value") );
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
                            Aware.setSetting(getApplicationContext(), plugin_setting.getString("setting"), plugin_setting.get("value"), package_name);
                        }
                    }catch( JSONException e ) {
                        e.printStackTrace();
                    }
                }

                //Start bundled plugins
                for( String p : active_plugins ) {
                    Aware.startPlugin(getApplicationContext(), p);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            //Send data to server
            Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
            sendBroadcast(sync);

            Intent applyNew = new Intent(Aware.ACTION_AWARE_REFRESH);
            sendBroadcast(applyNew);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( repeatingIntent != null ) alarmManager.cancel(repeatingIntent);
        if( webserviceUploadIntent != null) alarmManager.cancel(webserviceUploadIntent);
        
        if( aware_BR != null ) awareContext.unregisterReceiver(aware_BR);
        if( storage_BR != null ) awareContext.unregisterReceiver(storage_BR);
    }

    /**
     * Client: check if a certain study is still ongoing, resets client otherwise.
     */
    private class Study_Check extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {

            Hashtable<String, String> data = new Hashtable<>();
            data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));

            String study_url = Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER);
            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
            String protocol = study_url.substring(0, study_url.indexOf(":"));

            String response;
            if( protocol.equals("https") ) {
                response = new Https(getApplicationContext(), getResources().openRawResource(R.raw.awareframework)).dataPOST(study_url, data, true);
            } else {
                response = new Http(getApplicationContext()).dataPOST(study_url, data, true);
            }
            if( response != null ) {
                try {
                    JSONArray j_array = new JSONArray(response);
                    JSONObject io = j_array.getJSONObject(0);
                    if( io.has("message") ) {
                        if( io.getString("message").equals("This study is not ongoing anymore.") ) return true;
                        Log.d(Aware.TAG, io.getString("message"));
                    }
                    return false;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
        @Override
        protected void onPostExecute(Boolean is_closed) {
            super.onPostExecute(is_closed);
            if( is_closed ) {
                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
                mBuilder.setSmallIcon(R.drawable.ic_action_aware_studies);
                mBuilder.setContentTitle("AWARE");
                mBuilder.setContentText("The study has ended! Thanks!");
                mBuilder.setAutoCancel(true);

                NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notManager.notify(new Random(System.currentTimeMillis()).nextInt(), mBuilder.build());

                reset(getApplicationContext());
            }
        }
    }

    public static void reset(Context c) {
        String device_id = Aware.getSetting( c, Aware_Preferences.DEVICE_ID );

        //Remove all settings
        c.getContentResolver().delete( Aware_Settings.CONTENT_URI, null, null );

        //Read default client settings
        SharedPreferences prefs = c.getSharedPreferences( c.getPackageName(), Context.MODE_PRIVATE );
        PreferenceManager.setDefaultValues(c, c.getPackageName(), Context.MODE_PRIVATE, R.xml.aware_preferences, true);
        prefs.edit().commit();

        Map<String,?> defaults = prefs.getAll();
        for(Map.Entry<String, ?> entry : defaults.entrySet()) {
            Aware.setSetting(c, entry.getKey(), entry.getValue());
        }

        //Keep previous AWARE Device ID
        Aware.setSetting(c, Aware_Preferences.DEVICE_ID, device_id);

        //Turn off all active plugins
        ArrayList<String> active_plugins = new ArrayList<>();
        Cursor enabled_plugins = c.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
        if( enabled_plugins != null && enabled_plugins.moveToFirst() ) {
            do {
                String package_name = enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
                active_plugins.add(package_name);
            }while(enabled_plugins.moveToNext());
        }
        if( enabled_plugins != null && ! enabled_plugins.isClosed() ) enabled_plugins.close();
        if( active_plugins.size() > 0 ) {
            for(String package_name : active_plugins ) {
                stopPlugin(c, package_name);
            }
            if( Aware.DEBUG ) Log.w(TAG,"AWARE plugins disabled...");
        }
    }

    private class CheckPlugins extends AsyncTask<ArrayList<String>, Void, Boolean> {
        private ArrayList<String> updated = new ArrayList<>();
        @Override
        protected Boolean doInBackground(ArrayList<String>... params) {
            for( String package_name : params[0] ) {

                String study_url = Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER);
                String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
                String protocol = study_url.substring(0, study_url.indexOf(":"));

                String http_request;
                if( protocol.equals("https") ) {
                    http_request = new Https(getApplicationContext(), getResources().openRawResource(R.raw.awareframework)).dataGET( study_host + "/index.php/plugins/get_plugin/" + package_name, true);
                } else {
                    http_request = new Http(getApplicationContext()).dataGET( study_host + "/index.php/plugins/get_plugin/" + package_name, true);
                }

                if( http_request != null ) {
                    if( ! http_request.equals("[]") ) {
                        try {
                            JSONObject json_package = new JSONObject(http_request);
                            if( json_package.getInt("version") > Plugins_Manager.getVersion(getApplicationContext(), package_name) ) {
                                updated.add(package_name);
                            }
                        } catch (JSONException e) {}
                    }
                }
            }
            return (updated.size() > 0);
        }

        @Override
        protected void onPostExecute(Boolean updates) {
            super.onPostExecute(updates);
            if( updates ) {
                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
                mBuilder.setSmallIcon(R.drawable.ic_stat_aware_plugin_dependency);
                mBuilder.setContentTitle("AWARE Update");
                mBuilder.setContentText("Found " + updated.size() + " updated plugin(s). Install?");
                mBuilder.setAutoCancel(true);

                Intent updateIntent = new Intent(getApplicationContext(), UpdatePlugins.class);
                updateIntent.putExtra("updated", updated);

                PendingIntent clickIntent = PendingIntent.getService(getApplicationContext(), 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                mBuilder.setContentIntent(clickIntent);
                NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notManager.notify(updated.size(), mBuilder.build());
            }
        }
    }

    /**
     * Client: check if there is an update to the client.
     */
    public static class Update_Check extends AsyncTask<Void, Void, Boolean> {
    	String filename = "", whats_new = "";
    	int version = 0;
    	PackageInfo awarePkg = null;
    	
    	@Override
    	protected Boolean doInBackground(Void... params) {
            if( ! Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER).contains("api.awareframework.com") ) return false;

            try {
				awarePkg = awareContext.getPackageManager().getPackageInfo("com.aware", PackageManager.GET_META_DATA);
			} catch (NameNotFoundException e1) {
				e1.printStackTrace();
				return false;
			}

    		String response = new Https(awareContext, awareContext.getResources().openRawResource(R.raw.awareframework)).dataGET("https://api.awareframework.com/index.php/awaredev/framework_latest", true);
	        if( response != null ) {
	        	try {
					JSONArray data = new JSONArray(response);
					JSONObject latest_framework = data.getJSONObject(0);
					
					if( Aware.DEBUG ) Log.d(Aware.TAG, "Latest: " + latest_framework.toString());
					
					filename = latest_framework.getString("filename");
					version = latest_framework.getInt("version");
					whats_new = latest_framework.getString("whats_new");
					
					if( version > awarePkg.versionCode ) {
						return true;
					}
					return false;
				} catch (JSONException e) {
					e.printStackTrace();
				}
	        } else {
	        	if( Aware.DEBUG ) Log.d(Aware.TAG, "Unable to fetch latest framework from AWARE repository...");
	        }
    		return false;
    	}
    	
    	@Override
    	protected void onPostExecute(Boolean result) {
    		super.onPostExecute(result);
    		if( result ) {
    			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(awareContext);
    			mBuilder.setSmallIcon(R.drawable.ic_stat_aware_update);
    			mBuilder.setContentTitle("AWARE Update");
    			mBuilder.setContentText("Version: " + version + ". Install?");
                mBuilder.setAutoCancel(true);
    			
    			Intent updateIntent = new Intent(awareContext, UpdateFrameworkService.class);
    			updateIntent.putExtra("filename", filename);
    			
    			PendingIntent clickIntent = PendingIntent.getService(awareContext, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    			mBuilder.setContentIntent(clickIntent);
    			NotificationManager notManager = (NotificationManager) awareContext.getSystemService(Context.NOTIFICATION_SERVICE);
    			notManager.notify(version, mBuilder.build());
    		}
    	}
    }
    
    /**
     * Client's plugin monitor
     * - Installs a plugin that was just downloaded
     * - Checks if a package is a plugin or not
     * @author denzilferreira
     */
    public static class PluginMonitor extends BroadcastReceiver {
    	private static PackageManager mPkgManager;
    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		mPkgManager = context.getPackageManager();
    		
        	Bundle extras = intent.getExtras();
            Uri packageUri = intent.getData();
            if( packageUri == null ) return;
            String packageName = packageUri.getSchemeSpecificPart();
            if( packageName == null ) return;
            
            if( ! packageName.matches("com.aware.plugin.*") ) return;
        	
            if( intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED) ) {
                //Updating a package
                if( extras.getBoolean(Intent.EXTRA_REPLACING) ) {
                    if(Aware.DEBUG) Log.d(TAG, packageName + " is updating!");
                    
                    ContentValues rowData = new ContentValues();
                    rowData.put(Aware_Plugins.PLUGIN_VERSION, Plugins_Manager.getVersion(context, packageName));
                    try {
                        rowData.put(Aware_Plugins.PLUGIN_ICON, Plugins_Manager.getPluginIcon(context, mPkgManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)));
                    } catch (NameNotFoundException e ) {
                        e.printStackTrace();
                    }

                    Cursor current_status = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, new String[]{Aware_Plugins.PLUGIN_STATUS}, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null, null);
                    if( current_status != null && current_status.moveToFirst() ) {
                        if( current_status.getInt(current_status.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Plugins_Manager.PLUGIN_UPDATED ) { //was updated, set to active now
                        	rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                        }
                    }
                    if( current_status != null && ! current_status.isClosed() ) current_status.close();
                    
                    context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);

                    //Start plugin
                    Aware.startPlugin(context, packageName);
                    return;
                }
                
                //Installing new
                try {
                    ApplicationInfo appInfo = mPkgManager.getApplicationInfo( packageName, PackageManager.GET_ACTIVITIES );
                    //Check if this is a package for which we have more info from the server
                    new Plugin_Info_Async().execute(appInfo);
                } catch( final NameNotFoundException e ) {
                	e.printStackTrace();
                }
            }
            
            if( intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED) ) {
                //Updating
                if( extras.getBoolean(Intent.EXTRA_REPLACING) ) {
                    //this is an update, bail out.
                    return;
                }

                //Deleting
                context.getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);
                if( Aware.DEBUG ) Log.d(TAG,"AWARE plugin removed:" + packageName);
            }
    	}
    }
    
    /**
     * Fetches info from webservices on installed plugins.
     * @author denzilferreira
     *
     */
    private static class Plugin_Info_Async extends AsyncTask<ApplicationInfo, Void, JSONObject> {

        private ApplicationInfo app;

    	@Override
		protected JSONObject doInBackground(ApplicationInfo... params) {
			
    		app = params[0];
    		
    		JSONObject json_package = null;

            String study_url = Aware.getSetting(awareContext, Aware_Preferences.WEBSERVICE_SERVER);
            String study_host = study_url.substring(0, study_url.indexOf("/index.php"));
            String protocol = study_url.substring(0, study_url.indexOf(":"));

            String http_request;
            if( protocol.equals("https")) {
                http_request = new Https(awareContext, awareContext.getResources().openRawResource(R.raw.awareframework)).dataGET(study_host + "/index.php/plugins/get_plugin/" + app.packageName, true);
            } else {
                http_request = new Http(awareContext).dataGET(study_host + "/index.php/plugins/get_plugin/" + app.packageName, true);
            }
            if( http_request != null ) {
            	try {
            		if( ! http_request.trim().equalsIgnoreCase("[]") ) {
            			json_package = new JSONObject(http_request);
            		}
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }
            return json_package;
		}
    	
		@Override
		protected void onPostExecute(JSONObject json_package) {
			super.onPostExecute(json_package);

            ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, app.packageName);
            rowData.put(Aware_Plugins.PLUGIN_NAME, app.loadLabel(awareContext.getPackageManager()).toString());
            rowData.put(Aware_Plugins.PLUGIN_VERSION, Plugins_Manager.getVersion(awareContext, app.packageName));
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
            try {
                rowData.put(Aware_Plugins.PLUGIN_ICON, Plugins_Manager.getPluginIcon(awareContext, awareContext.getPackageManager().getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)));
            } catch (NameNotFoundException e ) {
                e.printStackTrace();
            }

            try {
                if( json_package != null ) {
                    rowData.put(Aware_Plugins.PLUGIN_AUTHOR, json_package.getString("first_name") + " " + json_package.getString("last_name") + " - " + json_package.getString("email"));
                    rowData.put(Aware_Plugins.PLUGIN_DESCRIPTION, json_package.getString("desc"));
                }
            } catch ( JSONException e ) {
                e.printStackTrace();
            }

			//If we already have cached information for this package, just update it
			boolean is_cached = false;
			Cursor plugin_cached = awareContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + app.packageName + "'", null, null);
			if( plugin_cached != null && plugin_cached.moveToFirst() ) {
				is_cached = true;
			}
			if( plugin_cached != null && ! plugin_cached.isClosed()) plugin_cached.close();

            if( ! is_cached ) {
            	awareContext.getContentResolver().insert(Aware_Plugins.CONTENT_URI, rowData);
            } else {
            	awareContext.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + app.packageName + "'", null);
            }
            
            if( Aware.DEBUG ) Log.d(TAG,"AWARE plugin added and activated:" + app.packageName);

            //Start plugin
            Aware.startPlugin(awareContext, app.packageName);
		}
    }

    public static class UpdatePlugins extends IntentService {
        public UpdatePlugins() {super("Update Plugins service");}
        @Override
        protected void onHandleIntent(Intent intent) {
            ArrayList<String> packages = intent.getStringArrayListExtra("updated");
            for( String package_name : packages ) {
                Aware.downloadPlugin(getApplicationContext(), package_name, true);
            }
        }
    }

    /**
     * Background service to download latest version of AWARE
     * @author denzilferreira
     *
     */
    public static class UpdateFrameworkService extends IntentService {
		public UpdateFrameworkService() {
			super("Update Framework service");			
		}
		@Override
		protected void onHandleIntent(Intent intent) {
			String filename = intent.getStringExtra("filename");
			
			//Make sure we have the releases folder
			File releases = new File( getExternalFilesDir(null)+"/Documents/AWARE", "releases");
			releases.mkdirs();
			
			String url = "http://www.awareframework.com/" + filename;

            Toast.makeText(this, "Updating AWARE...", Toast.LENGTH_SHORT).show();

            Ion.with(getApplicationContext())
                    .load(url)
                    .write(new File(getExternalFilesDir(null)+"/Documents/AWARE/releases/" + filename))
                    .setCallback(new FutureCallback<File>() {
                        @Override
                        public void onCompleted(Exception e, File result) {
                            if (result != null) {
                                Intent promptInstall = new Intent(Intent.ACTION_VIEW);
                                promptInstall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                promptInstall.setDataAndType(Uri.fromFile(result), "application/vnd.android.package-archive");
                                startActivity(promptInstall);
                            }
                        }
                    });
		}
    }
    
    /**
     * BroadcastReceiver that monitors for AWARE framework actions:
     * - ACTION_AWARE_SYNC_DATA = upload data to remote webservice server.
     * - ACTION_AWARE_CLEAR_DATA = clears local device's AWARE modules databases.
     * - ACTION_AWARE_REFRESH - apply changes to the configuration.
     * - {}@link WifiManager#WIFI_STATE_CHANGED_ACTION} - when Wi-Fi is available to sync
     * @author denzil
     *
     */
    public static class Aware_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            //We are only synching the device information, not aware's settings and active plugins.
        	String[] DATABASE_TABLES = Aware_Provider.DATABASE_TABLES;
        	String[] TABLES_FIELDS = Aware_Provider.TABLES_FIELDS;
        	Uri[] CONTEXT_URIS = new Uri[]{ Aware_Device.CONTENT_URI };

        	if( intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
            	Intent webserviceHelper = new Intent( context, WebserviceHelper.class );
                webserviceHelper.setAction( WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE );
    			webserviceHelper.putExtra( WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[0] );
        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[0] );
        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[0].toString() );
        		context.startService(webserviceHelper);
            }

            //Monitor if the user just connected to Wi-Fi and the client is supposed to sync the data to a study when he does
            if( intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION) ) {
                int wifi_state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if( wifi_state == WifiManager.WIFI_STATE_ENABLED ) {
                    if( Aware.getSetting(context, Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true") && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if( activeNetwork != null && activeNetwork.isConnectedOrConnecting() && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI ) {
                            if(Aware.DEBUG) Log.d(Aware.TAG, "Internet is available, let's sync!");
                            context.sendBroadcast(new Intent(Aware.ACTION_AWARE_SYNC_DATA));
                        }
                    }
                }
            }
        	
            if( intent.getAction().equals(Aware.ACTION_AWARE_CLEAR_DATA) ) {
                context.getContentResolver().delete(Aware_Provider.Aware_Device.CONTENT_URI, null, null);
                if( Aware.DEBUG ) Log.d(TAG,"Cleared " + CONTEXT_URIS[0]);
                
                //Clear remotely if webservices are active
                if( Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
	        		Intent webserviceHelper = new Intent( context, WebserviceHelper.class );
                    webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE );
	        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[0] );
	        		context.startService(webserviceHelper);
                }
            }

            if( intent.getAction().equals(Aware.ACTION_AWARE_CHECK_UPDATE) ) {
                //Check if there are updates to the client
                if( ! Aware.is_watch(context) ) {
                    new Update_Check().execute();
                }
                SharedPreferences.Editor editor = aware_preferences.edit();
                editor.putLong(PREF_LAST_UPDATE, System.currentTimeMillis());
                editor.commit();
            }

            if( intent.getAction().equals(Aware.ACTION_QUIT_STUDY) ) {
                Aware.reset(context);
                if( context.getPackageName().equals("com.aware") ) {
                    Intent preferences = new Intent(context, Aware_Preferences.class);
                    preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(preferences);
                }
            }

            if( intent.getAction().equals(Aware.ACTION_AWARE_REFRESH)) {
                Intent refresh = new Intent(context, com.aware.Aware.class);
                context.startService(refresh);
            }
        }
    }
    private static final Aware_Broadcaster aware_BR = new Aware_Broadcaster();

    /**
     * Checks if we have access to the storage of the device. Turns off AWARE when we don't, turns it back on when available again.
     */
    public static class Storage_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED) ) {
                if( Aware.DEBUG ) Log.d(TAG,"Resuming AWARE data logging...");
            }
            if ( intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED) ) {
                if( Aware.DEBUG ) Log.w(TAG,"Stopping AWARE data logging until the SDCard is available again...");
            }
            Intent aware = new Intent(context, Aware.class);
            context.startService(aware);
        }
    }
    private static final Storage_Broadcaster storage_BR = new Storage_Broadcaster();
    
    /**
     * Start active services
     */
    protected void startAllServices() {
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ESM).equals("true") ) {
            startESM(awareContext);
        } else stopESM(awareContext);

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            startApplications(awareContext);
        }else stopApplications(awareContext);

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true") ) {
            startAccelerometer(awareContext);
        }else stopAccelerometer(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations(awareContext);
        }else stopInstallations(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true") ) {
            startLocations(awareContext);
        } else stopLocations(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH).equals("true") ) {
            startBluetooth(awareContext);
        }else stopBluetooth(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_SCREEN).equals("true") ) {
            startScreen(awareContext);
        }else stopScreen(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BATTERY).equals("true") ) {
            startBattery(awareContext);
        }else stopBattery(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true") ) {
            startNetwork(awareContext);
        }else stopNetwork(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true") ) {
            startTraffic(awareContext);
        }else stopTraffic(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("true") || Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("true") ){
            startCommunication(awareContext);
        } else stopCommunication(awareContext);

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR).equals("true") ) {
            startProcessor(awareContext);
        }else stopProcessor(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE).equals("true") ) {
            startTimeZone(awareContext);
        }else stopTimeZone(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_MQTT).equals("true") ) {
            startMQTT(awareContext);
        }else stopMQTT(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE).equals("true") ) {
            startGyroscope(awareContext);
        }else stopGyroscope(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_WIFI).equals("true") ) {
            startWiFi(awareContext);
        }else stopWiFi(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY).equals("true") ) {
            startTelephony(awareContext);
        }else stopTelephony(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ROTATION).equals("true") ) {
            startRotation(awareContext);
        }else stopRotation(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LIGHT).equals("true") ) {
            startLight(awareContext);
        }else stopLight(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY).equals("true") ) {
            startProximity(awareContext);
        }else stopProximity(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER).equals("true") ) {
            startMagnetometer(awareContext);
        }else stopMagnetometer(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BAROMETER).equals("true") ) {
            startBarometer(awareContext);
        }else stopBarometer(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_GRAVITY).equals("true") ) {
            startGravity(awareContext);
        }else stopGravity(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true") ) {
            startLinearAccelerometer(awareContext);
        }else stopLinearAccelerometer(awareContext);
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true") ) {
            startTemperature(awareContext);
        }else stopTemperature(awareContext);

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD).equals("true") ) {
            startKeyboard(awareContext);
        }else stopKeyboard(awareContext);

        //Start Android Wear HTTP/s proxy
        if( getPackageName().equals("com.aware") ) {
            awareContext.startService(wearClient);
        }

        //Start task scheduler
        scheduler = new Intent(awareContext, Scheduler.class);
        awareContext.startService(scheduler);
    }

    /**
     * Stop all services
     */
    protected void stopAllServices() {
        stopApplications(awareContext);
        stopAccelerometer(awareContext);
        stopBattery(awareContext);
        stopBluetooth(awareContext);
        stopCommunication(awareContext);
        stopLocations(awareContext);
        stopNetwork(awareContext);
        stopTraffic(awareContext);
        stopScreen(awareContext);
        stopProcessor(awareContext);
        stopMQTT(awareContext);
        stopGyroscope(awareContext);
        stopWiFi(awareContext);
        stopTelephony(awareContext);
        stopTimeZone(awareContext);
        stopRotation(awareContext);
        stopLight(awareContext);
        stopProximity(awareContext);
        stopMagnetometer(awareContext);
        stopBarometer(awareContext);
        stopGravity(awareContext);
        stopLinearAccelerometer(awareContext);
        stopTemperature(awareContext);
        stopESM(awareContext);
        stopInstallations(awareContext);
        stopKeyboard(awareContext);

        //Stop Android Wear HTTP/s proxy
        if( getPackageName().equals("com.aware") ) {
            awareContext.stopService(wearClient);
        }

        awareContext.stopService(scheduler);
    }

    /**
     * Start keyboard module
     */
    protected static void startKeyboard(Context context) {
        awareContext = context;
        if( keyboard == null ) keyboard = new Intent(awareContext, Keyboard.class);
        awareContext.startService(keyboard);
    }

    /**
     * Stop keyboard module
     */
    protected static void stopKeyboard(Context context) {
        awareContext = context;
        if( keyboard != null ) awareContext.stopService(keyboard);
    }

    /**
     * Start Applications module
     */
    protected static void startApplications(Context context) {
        awareContext = context;
        if( applicationsSrv == null) {
            applicationsSrv = new Intent(awareContext, Applications.class);
        }
        try{
            ComponentName service = awareContext.startService(applicationsSrv);
        } catch (RuntimeException e ) {
            //Gingerbread and Jelly Bean complain when we start the service explicitly. In these, it is handled by the OS
        }
    }
    
    /**
     * Stop Applications module
     */
    protected static void stopApplications(Context context) {
        awareContext = context;
        if( applicationsSrv != null) {
            try {
                awareContext.stopService(applicationsSrv);
            } catch (RuntimeException e ) {
                //Gingerbread and Jelly Bean complain when we stop the serive explicitly. In these, it is handled by the OS
            }
        }
    }
    
    /**
     * Start Installations module
     */
    protected static void startInstallations(Context context) {
        awareContext = context;
        if(installationsSrv == null) installationsSrv = new Intent(awareContext, Installations.class);
        awareContext.startService(installationsSrv);
    }
    
    /**
     * Stop Installations module
     */
    protected static void stopInstallations(Context context) {
        awareContext = context;
        if(installationsSrv != null) awareContext.stopService(installationsSrv);
    }
    
    /**
     * Start ESM module
     */
    protected static void startESM(Context context) {
        awareContext = context;
        if( esmSrv == null ) esmSrv = new Intent(awareContext, ESM.class);
        awareContext.startService(esmSrv);
    }
    
    /**
     * Stop ESM module
     */
    protected static void stopESM(Context context) {
        awareContext = context;
        if( esmSrv != null ) awareContext.stopService(esmSrv);
    }
    
    /**
     * Start Temperature module
     */
    protected static void startTemperature(Context context) {
        awareContext = context;
        if( temperatureSrv == null ) temperatureSrv = new Intent(awareContext, Temperature.class);
        awareContext.startService(temperatureSrv);
    }
    
    /**
     * Stop Temperature module
     */
    protected static void stopTemperature(Context context) {
        awareContext = context;
        if( temperatureSrv != null ) awareContext.stopService(temperatureSrv);
    }
    
    /**
     * Start Linear Accelerometer module
     */
    protected static void startLinearAccelerometer(Context context) {
        awareContext = context;
        if( linear_accelSrv == null ) linear_accelSrv = new Intent(awareContext, LinearAccelerometer.class);
        awareContext.startService(linear_accelSrv);
    }
    
    /**
     * Stop Linear Accelerometer module
     */
    protected static void stopLinearAccelerometer(Context context) {
        awareContext = context;
        if( linear_accelSrv != null ) awareContext.stopService(linear_accelSrv);
    }
    
    /**
     * Start Gravity module
     */
    protected static void startGravity(Context context) {
        awareContext = context;
        if( gravitySrv == null ) gravitySrv = new Intent(awareContext, Gravity.class);
        awareContext.startService(gravitySrv);
    }
    
    /**
     * Stop Gravity module
     */
    protected static void stopGravity(Context context) {
        awareContext = context;
        if( gravitySrv != null ) awareContext.stopService(gravitySrv);
    }
    
    /**
     * Start Barometer module
     */
    protected static void startBarometer(Context context) {
        awareContext = context;
        if( barometerSrv == null ) barometerSrv = new Intent(awareContext, Barometer.class);
        awareContext.startService(barometerSrv);
    }
    
    /**
     * Stop Barometer module
     */
    protected static void stopBarometer(Context context) {
        awareContext = context;
        if( barometerSrv != null ) awareContext.stopService(barometerSrv);
    }
    
    /**
     * Start Magnetometer module
     */
    protected static void startMagnetometer(Context context) {
        awareContext = context;
        if( magnetoSrv == null ) magnetoSrv = new Intent(awareContext, Magnetometer.class);
        awareContext.startService(magnetoSrv);
    }
    
    /**
     * Stop Magnetometer module
     */
    protected static void stopMagnetometer(Context context) {
        awareContext = context;
        if( magnetoSrv != null ) awareContext.stopService(magnetoSrv);
    }
    
    /**
     * Start Proximity module
     */
    protected static void startProximity(Context context) {
        awareContext = context;
        if( proximitySrv == null ) proximitySrv = new Intent(awareContext, Proximity.class);
        awareContext.startService(proximitySrv);
    }
    
    /**
     * Stop Proximity module
     */
    protected static void stopProximity(Context context) {
        awareContext = context;
        if( proximitySrv != null ) awareContext.stopService(proximitySrv);
    }
    
    /**
     * Start Light module
     */
    protected static void startLight(Context context) {
        awareContext = context;
        if( lightSrv == null ) lightSrv = new Intent(awareContext, Light.class);
        awareContext.startService(lightSrv);
    }
    
    /**
     * Stop Light module
     */
    protected static void stopLight(Context context) {
        awareContext = context;
        if( lightSrv != null ) awareContext.stopService(lightSrv);
    }
    
    /**
     * Start Rotation module
     */
    protected static void startRotation(Context context) {
        awareContext = context;
        if( rotationSrv == null ) rotationSrv = new Intent(awareContext, Rotation.class);
        awareContext.startService(rotationSrv);
    }
    
    /**
     * Stop Rotation module
     */
    protected static void stopRotation(Context context) {
        awareContext = context;
        if( rotationSrv != null ) awareContext.stopService(rotationSrv);
    }
    
    /**
     * Start the Telephony module
     */
    protected static void startTelephony(Context context) {
        awareContext = context;
        if( telephonySrv == null) telephonySrv = new Intent(awareContext, Telephony.class);
        awareContext.startService(telephonySrv);
    }
    
    /**
     * Stop the Telephony module
     */
    protected static void stopTelephony(Context context) {
        awareContext = context;
        if( telephonySrv != null ) awareContext.stopService(telephonySrv);
    }
    
    /**
     * Start the WiFi module
     */
    protected static void startWiFi(Context context) {
        awareContext = context;
        if( wifiSrv == null ) wifiSrv = new Intent(awareContext, WiFi.class);
        awareContext.startService(wifiSrv);
    }
    
    protected static void stopWiFi(Context context) {
        awareContext = context;
        if( wifiSrv != null ) awareContext.stopService(wifiSrv);
    }
    
    /**
     * Start the gyroscope module
     */
    protected static void startGyroscope(Context context) {
        awareContext = context;
        if( gyroSrv == null ) gyroSrv = new Intent(awareContext, Gyroscope.class);
        awareContext.startService(gyroSrv);
    }
    
    /**
     * Stop the gyroscope module
     */
    protected static void stopGyroscope(Context context) {
        awareContext = context;
        if( gyroSrv != null ) awareContext.stopService(gyroSrv);
    }
    
    /**
     * Start the accelerometer module
     */
    protected static void startAccelerometer(Context context) {
        awareContext = context;
        if( accelerometerSrv == null ) accelerometerSrv = new Intent(awareContext, Accelerometer.class);
        awareContext.startService(accelerometerSrv);
    }
    
    /**
     * Stop the accelerometer module
     */
    protected static void stopAccelerometer(Context context) {
        awareContext = context;
        if( accelerometerSrv != null) awareContext.stopService(accelerometerSrv);
    }
    
    /**
     * Start the Processor module
     */
    protected static void startProcessor(Context context) {
        awareContext = context;
        if( processorSrv == null) processorSrv = new Intent(awareContext, Processor.class);
        awareContext.startService(processorSrv);
    }
    
    /**
     * Stop the Processor module
     */
    protected static void stopProcessor(Context context) {
        awareContext = context;
        if( processorSrv != null ) awareContext.stopService(processorSrv);
    }
    
    /**
     * Start the locations module
     */
    protected static void startLocations(Context context) {
        awareContext = context;
        if( locationsSrv == null) locationsSrv = new Intent(awareContext, Locations.class);
        awareContext.startService(locationsSrv);
    }
    
    /**
     * Stop the locations module
     */
    protected static void stopLocations(Context context) {
        awareContext = context;
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("false") && Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("false") ) {
            if(locationsSrv != null) awareContext.stopService(locationsSrv);
        }
    }
    
    /**
     * Start the bluetooth module
     */
    protected static void startBluetooth(Context context) {
        awareContext = context;
        if( bluetoothSrv == null) bluetoothSrv = new Intent(awareContext, Bluetooth.class);
        awareContext.startService(bluetoothSrv);
    }
    
    /**
     * Stop the bluetooth module
     */
    protected static void stopBluetooth(Context context) {
        awareContext = context;
        if(bluetoothSrv != null) awareContext.stopService(bluetoothSrv);
    }
    
    /**
     * Start the screen module
     */
    protected static void startScreen(Context context) {
        awareContext = context;
        if( screenSrv == null) screenSrv = new Intent(awareContext, Screen.class);
        awareContext.startService(screenSrv);
    }
    
    /**
     * Stop the screen module
     */
    protected static void stopScreen(Context context) {
        awareContext = context;
        if(screenSrv != null) awareContext.stopService(screenSrv);
    }
    
    /**
     * Start battery module
     */
    protected static void startBattery(Context context) {
        awareContext = context;
        if( batterySrv == null) batterySrv = new Intent(awareContext, Battery.class);
        awareContext.startService(batterySrv);
    }
    
    /**
     * Stop battery module
     */
    protected static void stopBattery(Context context) {
        awareContext = context;
        if(batterySrv != null) awareContext.stopService(batterySrv);
    }
    
    /**
     * Start network module
     */
    protected static void startNetwork(Context context) {
        awareContext = context;
        if( networkSrv == null ) networkSrv = new Intent(awareContext, Network.class);
        awareContext.startService(networkSrv);
    }
    
    /**
     * Stop network module
     */
    protected static void stopNetwork(Context context) {
        awareContext = context;
        if(networkSrv != null) awareContext.stopService(networkSrv);
    }
    
    /**
     * Start traffic module
     */
    protected static void startTraffic(Context context) {
        awareContext = context;
        if(trafficSrv == null) trafficSrv = new Intent(awareContext, Traffic.class);
        awareContext.startService(trafficSrv);
    }
    
    /**
     * Stop traffic module
     */
    protected static void stopTraffic(Context context) {
        awareContext = context;
        if( trafficSrv != null ) awareContext.stopService(trafficSrv);
    }
    
    /**
     * Start the TimeZone module
     */
    protected static void startTimeZone(Context context) {
        awareContext = context;
        if(timeZoneSrv == null) timeZoneSrv = new Intent(awareContext, TimeZone.class);
        awareContext.startService(timeZoneSrv);
    }
    
    /**
     * Stop the TimeZone module
     */
    protected static void stopTimeZone(Context context) {
        awareContext = context;
        if( timeZoneSrv != null ) awareContext.stopService(timeZoneSrv);
    }
    
    /**
     * Start communication module
     */
    protected static void startCommunication(Context context) {
        awareContext = context;
        if( communicationSrv == null ) communicationSrv = new Intent(awareContext, Communication.class);
        awareContext.startService(communicationSrv);
    }
    
    /**
     * Stop communication module
     */
    protected static void stopCommunication(Context context) {
        awareContext = context;
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("false") 
         && Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("false") 
         && Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("false") ) {
            if(communicationSrv != null) awareContext.stopService(communicationSrv);
        }
    }
    
    /**
     * Start MQTT module
     */
    protected static void startMQTT(Context context) {
        awareContext = context;
        if( mqttSrv == null ) mqttSrv = new Intent(awareContext, Mqtt.class);
        awareContext.startService(mqttSrv);
    }
    
    /**
     * Stop MQTT module
     */
    protected static void stopMQTT(Context context) {
        awareContext = context;
        if( mqttSrv != null ) awareContext.stopService(mqttSrv);
    }
}
