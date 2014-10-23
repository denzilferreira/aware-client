/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Device;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.providers.Aware_Provider.Aware_Settings;
import com.aware.ui.Plugins_Manager;
import com.aware.ui.Stream_UI;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.Converters;
import com.aware.utils.Https;
import com.aware.utils.WebserviceHelper;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
     * Received broadcast on all modules<br/>
     * - Cleans the data collected on the device
     */
    public static final String ACTION_AWARE_CLEAR_DATA = "ACTION_AWARE_CLEAR_DATA";
    
    /**
     * Received broadcast: refresh the framework active sensors.<br/>
     */
    public static final String ACTION_AWARE_REFRESH = "ACTION_AWARE_REFRESH";
    
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
     * Used by Android Wear to know which sensor has been activated to sync with phone.
     * Extras:
     * extra_config_setting: the setting key
     * extra_config_value: the setting value
     */
    public static final String ACTION_AWARE_CONFIG_CHANGED = "ACTION_AWARE_CONFIG_CHANGED";
    public static final String EXTRA_CONFIG_SETTING = "extra_config_setting";
    public static final String EXTRA_CONFIG_VALUE = "extra_config_value";
    
    /**
     * DownloadManager AWARE update ID, used to prompt user to install the update once finished downloading.
     */
    private static long AWARE_FRAMEWORK_DOWNLOAD_ID = 0;
    
    /**
     * DownloadManager queue for plugins, in case we have multiple dependencies to install.
     */
    private static ArrayList<Long> AWARE_PLUGIN_DOWNLOAD_IDS = new ArrayList<Long>();
    
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
    private static Intent androidWearSrv = null;
    private static Intent keyboard = null;
    
    private final String PREF_FREQUENCY_WATCHDOG = "frequency_watchdog";
    private final String PREF_LAST_UPDATE = "last_update";
    private final String PREF_LAST_SYNC = "last_sync";
    private final int CONST_FREQUENCY_WATCHDOG = 300;
    
    private SharedPreferences aware_preferences;
    
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

        aware_preferences = getSharedPreferences("aware_core_prefs", MODE_PRIVATE);
        if( aware_preferences.getAll().isEmpty() ) {
        	SharedPreferences.Editor editor = aware_preferences.edit();
        	editor.putInt(PREF_FREQUENCY_WATCHDOG, CONST_FREQUENCY_WATCHDOG);
        	editor.putLong(PREF_LAST_SYNC, 0);
        	editor.putLong(PREF_LAST_UPDATE, 0);
        	editor.commit();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        awareContext.registerReceiver(storage_BR, filter);
        
        filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_REFRESH);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        awareContext.registerReceiver(aware_BR, filter);
        
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        
        awareStatusMonitor = new Intent( getApplicationContext(), Aware.class );
        repeatingIntent = PendingIntent.getService(getApplicationContext(), 0,  awareStatusMonitor, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000, aware_preferences.getInt(PREF_FREQUENCY_WATCHDOG, 300) * 1000, repeatingIntent);
        
        Intent synchronise = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        webserviceUploadIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, synchronise, 0);
        
        if( ! Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ) {
            stopSelf();
        } else {
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
            
        	DEBUG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_FLAG).equals("true");
            TAG = Aware.getSetting(awareContext, Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG):TAG;
            
            get_device_info();
            
            if( Aware.DEBUG ) Log.d(TAG,"AWARE framework is created!");
            
            //Fixed: only the client application does a ping to AWARE's server
            if( getPackageName().equals("com.aware") ) {
            	new AsyncPing().execute();
            }
        }
    }
    
    private class AsyncPing extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			//Ping AWARE's server with awareContext device's information for framework's statistics log
	        ArrayList<NameValuePair> device_ping = new ArrayList<NameValuePair>();
	        device_ping.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID)));
	        device_ping.add(new BasicNameValuePair("ping", String.valueOf(System.currentTimeMillis())));
	        new Https(awareContext).dataPOST("https://api.awareframework.com/index.php/awaredev/alive", device_ping);
	        return true;
		}
    	
    	@Override
    	protected void onPostExecute(Boolean result) {
    		super.onPostExecute(result);
    		if( result && Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE).equals("true")) {
    			sendBroadcast(new Intent(Aware.ACTION_AWARE_SYNC_DATA));
    		}
    	}
    }
    
    private void get_device_info() {
        Cursor awareContextDevice = awareContext.getContentResolver().query(Aware_Device.CONTENT_URI, null, null, null, null);
        if( awareContextDevice == null || ! awareContextDevice.moveToFirst() ) {
            ContentValues rowData = new ContentValues();
            rowData.put("timestamp", System.currentTimeMillis());
            rowData.put("device_id", Aware.getSetting(awareContext, Aware_Preferences.DEVICE_ID));
            rowData.put("board", Build.BOARD);
            rowData.put("brand", Build.BRAND);
            rowData.put("device",Build.DEVICE);
            rowData.put("build_id", Build.DISPLAY);
            rowData.put("hardware", Build.HARDWARE);
            rowData.put("manufacturer", Build.MANUFACTURER);
            rowData.put("model", Build.MODEL);
            rowData.put("product", Build.PRODUCT);
            rowData.put("serial", Build.SERIAL);
            rowData.put("release", Build.VERSION.RELEASE);
            rowData.put("release_type", Build.TYPE);
            rowData.put("sdk", Build.VERSION.SDK_INT);
            
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

    //TODO: check if there is a better way to detect a watch...
    public static boolean is_watch(Context c) {
        boolean is_watch = false;
        Cursor device = c.getContentResolver().query(Aware_Provider.Aware_Device.CONTENT_URI, null, null, null, "1 LIMIT 1");
        if( device != null && device.moveToFirst() ) {
            //is_watch = device.getInt(device.getColumnIndex(Aware_Provider.Aware_Device.SDK))==20;
            is_watch = device.getString(device.getColumnIndex(Aware_Device.RELEASE)).contains("W");
        }
        if( device != null && ! device.isClosed() ) device.close();
        return is_watch;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ) {
            DEBUG = Aware.getSetting(awareContext,Aware_Preferences.DEBUG_FLAG).equals("true");
            TAG = Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(awareContext,Aware_Preferences.DEBUG_TAG):TAG;
            
            if( Aware.DEBUG ) Log.d(TAG,"AWARE framework is active...");
            startAllServices();

            Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
            if( enabled_plugins != null && enabled_plugins.moveToFirst() ) {
                do {
                    startPlugin(getApplicationContext(), enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME)));
                }while(enabled_plugins.moveToNext());
            }
            if( enabled_plugins != null && ! enabled_plugins.isClosed() ) enabled_plugins.close();

            //Only the client keeps the plugins running and checks for updates
            if( getPackageName().equals("com.aware") ) {
	            if( Aware.getSetting(getApplicationContext(), Aware_Preferences.AWARE_AUTO_UPDATE).equals("true") ) {
	            	if( aware_preferences.getLong(PREF_LAST_UPDATE, 0) == 0 || (aware_preferences.getLong(PREF_LAST_UPDATE, 0) > 0 && System.currentTimeMillis()-aware_preferences.getLong(PREF_LAST_UPDATE, 0) > 6*60*60*1000) ) { //check every 6h
	            		new Update_Check().execute();
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

                //Check if study is open or still exists
                new Study_Check().execute();
            }
            
            if( ! Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).equals("0") ) {
                Intent dataCleaning = new Intent(ACTION_AWARE_SPACE_MAINTENANCE);
                awareContext.sendBroadcast(dataCleaning);
            }
        } else { //Turn off all enabled plugins
        	Cursor enabled_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Aware_Plugin.STATUS_PLUGIN_ON, null, null);
        	if( enabled_plugins != null && enabled_plugins.moveToFirst() ) {
        		do {
        			stopPlugin(getApplicationContext(), enabled_plugins.getString(enabled_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME)));
        		}while(enabled_plugins.moveToNext());
        		enabled_plugins.close();
        	}
        	if( enabled_plugins != null && ! enabled_plugins.isClosed()) enabled_plugins.close();
        	if( Aware.DEBUG ) Log.w(TAG,"AWARE plugins disabled...");
        }
        return START_STICKY;
    }
    
    /**
     * Stops a plugin. Expects the package name of the plugin.
     * @param context
     * @param package_name
     */
    public static void stopPlugin(Context context, String package_name ) {
    	Cursor cached = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
    	if( cached != null && cached.moveToFirst() ) {
    		//it's installed, stop it!
    		Intent plugin = new Intent();
    		plugin.setClassName(package_name, package_name + ".Plugin");
    		context.stopService(plugin);
    		if( Aware.DEBUG ) Log.d(TAG, package_name + " stopped...");
    		
    		ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
            context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);            
    	}
    	if( cached != null && ! cached.isClosed() ) cached.close();
    }
    
    /**
     * Starts a plugin. Expects the package name of the plugin.<br/>
     * It checks if the plugin does exist on the phone. If it doesn't, it will request the user to install it automatically.
     * @param context
     * @param package_name
     */
    public static void startPlugin(Context context, String package_name ) {

        if( Aware.DEBUG ) Log.d(TAG, "Starting: " + package_name);

    	//Check if plugin is installed
    	Cursor cached = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
    	if( cached != null && cached.moveToFirst() ) {
            //Installed on the phone
    		if( isClassAvailable(context, package_name, "Plugin") ) {
    			Intent plugin = new Intent();
        		plugin.setClassName(package_name, package_name + ".Plugin");
        		context.startService(plugin);
        		if( Aware.DEBUG ) Log.d(TAG, package_name + " started...");
        		
        		ContentValues rowData = new ContentValues();
                rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
                context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null);
                cached.close();
                return;
    		}
    	}

        //Request the missing plugin
        new PluginDependencyTask().execute(package_name);
    }

    /**
     * Downloads missing plugins as a seperate thread.
     */
    private static class PluginDependencyTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {

            String package_name = params[0];

            HttpResponse response = new Https(awareContext).dataGET("https://api.awareframework.com/index.php/plugins/get_plugin/" + package_name);
            if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
                try {
                    String data = EntityUtils.toString(response.getEntity());
                    if( data.equals("[]") ) return null;

                    JSONObject json_package = new JSONObject(data);

                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(awareContext);
                    mBuilder.setSmallIcon(R.drawable.ic_stat_aware_plugin_dependency);
                    mBuilder.setContentTitle("AWARE");
                    mBuilder.setContentText("Missing: " + json_package.getString("title")+". Install?");
                    mBuilder.setDefaults(Notification.DEFAULT_ALL);
                    mBuilder.setAutoCancel(true);

                    Intent pluginIntent = new Intent(awareContext, DownloadPluginService.class);
                    pluginIntent.putExtra("package_name", package_name);
                    pluginIntent.putExtra("is_update", false);

                    PendingIntent clickIntent = PendingIntent.getService(awareContext, 0, pluginIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    mBuilder.setContentIntent(clickIntent);
                    NotificationManager notManager = (NotificationManager) awareContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    notManager.notify( json_package.getInt("id"), mBuilder.build());

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
    	LinearLayout card = new LinearLayout(context);
    	LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    	card.setLayoutParams(params);
    	card.setOrientation(LinearLayout.VERTICAL);
    	
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
				//Check if plugin has settings. If it does, tapping the card shows the settings
				if( isClassAvailable(context, package_name, "Settings") ) {
					ui.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent open_settings = new Intent();
							open_settings.setClassName(package_name, package_name + ".Settings");
							open_settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							context.startActivity(open_settings);
						}
					});
				}
				
				//Set card look-n-feel
				ui.setBackgroundColor(Color.WHITE);
				ui.setPadding(20, 20, 20, 20);
				card.addView(ui);
				
				LinearLayout shadow = new LinearLayout(context);
				LayoutParams params_shadow = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				params_shadow.setMargins(0, 0, 0, 10);
				shadow.setBackgroundColor(context.getResources().getColor(R.color.card_shadow));
				shadow.setMinimumHeight(5);
				shadow.setLayoutParams(params_shadow);
				card.addView(shadow);
				
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
        
    	boolean is_restricted_package = true;
    	
    	ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware_Preferences.DEBUG_FLAG);
        global_settings.add(Aware_Preferences.DEBUG_TAG);
        global_settings.add("study_id");
        global_settings.add("study_start");
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Aware_Preferences.STATUS_MQTT);
        global_settings.add(Aware_Preferences.MQTT_SERVER);
        global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
        global_settings.add(Aware_Preferences.MQTT_PORT);
        global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
        global_settings.add(Aware_Preferences.MQTT_USERNAME);
        global_settings.add(Aware_Preferences.MQTT_PASSWORD);
        global_settings.add(Aware_Preferences.STATUS_ANDROID_WEAR);
    	
    	if( global_settings.contains(key) ) {
    		is_restricted_package = false;
    	}
    	
    	String value = "";
        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "'" + ( is_restricted_package ? " AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'" : ""), null, null);
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
        
    	boolean is_restricted_package = true;
    	
    	ArrayList<String> global_settings = new ArrayList<String>();
    	global_settings.add(Aware_Preferences.DEBUG_FLAG);
    	global_settings.add(Aware_Preferences.DEBUG_TAG);
    	global_settings.add("study_id");
    	global_settings.add("study_start");
        global_settings.add(Aware_Preferences.DEVICE_ID);
        global_settings.add(Aware_Preferences.STATUS_WEBSERVICE);
        global_settings.add(Aware_Preferences.FREQUENCY_WEBSERVICE);
        global_settings.add(Aware_Preferences.WEBSERVICE_WIFI_ONLY);
        global_settings.add(Aware_Preferences.WEBSERVICE_SERVER);
        global_settings.add(Aware_Preferences.STATUS_MQTT);
        global_settings.add(Aware_Preferences.MQTT_SERVER);
        global_settings.add(Aware_Preferences.MQTT_KEEP_ALIVE);
        global_settings.add(Aware_Preferences.MQTT_PORT);
        global_settings.add(Aware_Preferences.MQTT_PROTOCOL);
        global_settings.add(Aware_Preferences.MQTT_USERNAME);
        global_settings.add(Aware_Preferences.MQTT_PASSWORD);
        global_settings.add(Aware_Preferences.STATUS_ANDROID_WEAR);

    	if( global_settings.contains(key) ) {
    		is_restricted_package = false;
    	}

        //Only the client can set the Device ID
        if( key.equals(Aware_Preferences.DEVICE_ID) && ! context.getPackageName().equals("com.aware") ) return;

    	ContentValues setting = new ContentValues();
        setting.put(Aware_Settings.SETTING_KEY, key);
        setting.put(Aware_Settings.SETTING_VALUE, value.toString());
        setting.put(Aware_Settings.SETTING_PACKAGE_NAME, context.getPackageName());

        Intent wearBroadcast = new Intent(ACTION_AWARE_CONFIG_CHANGED);
        wearBroadcast.putExtra(EXTRA_CONFIG_SETTING, key);
        wearBroadcast.putExtra(EXTRA_CONFIG_VALUE, value.toString());
        context.sendBroadcast(wearBroadcast);

        Cursor qry = context.getContentResolver().query(Aware_Settings.CONTENT_URI, null, Aware_Settings.SETTING_KEY + " LIKE '" + key + "'" + (is_restricted_package ? " AND " + Aware_Settings.SETTING_PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'" : ""), null, null);
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
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if( repeatingIntent != null ) alarmManager.cancel(repeatingIntent);
        if( webserviceUploadIntent != null) alarmManager.cancel(webserviceUploadIntent);
        
        if( aware_BR != null ) awareContext.unregisterReceiver(aware_BR);
        if( storage_BR != null ) awareContext.unregisterReceiver(storage_BR);
    }

    private class Study_Check extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            ArrayList<NameValuePair> data = new ArrayList<NameValuePair>();
            data.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID)));
            HttpResponse response = new Https(getApplicationContext()).dataPOST(Aware.getSetting(getApplicationContext(),Aware_Preferences.WEBSERVICE_SERVER), data);
            if( response != null && response.getStatusLine().getStatusCode() == 200) {
                try {
                    String json_str = EntityUtils.toString(response.getEntity());
                    JSONArray j_array = new JSONArray(json_str);
                    return j_array.getJSONObject(0).has("message");
                } catch (IOException e) {
                    e.printStackTrace();
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
                mBuilder.setDefaults(Notification.DEFAULT_ALL);
                mBuilder.setAutoCancel(true);

                NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notManager.notify(new Random(System.currentTimeMillis()).nextInt(), mBuilder.build());

                reset(getApplicationContext());
            }
        }
    }

    public static void reset(Context c) {
        if( ! c.getPackageName().equals("com.aware") ) return;

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

        //Restore old Device ID
        Aware.setSetting(c, Aware_Preferences.DEVICE_ID, device_id);

        //Turn off all active plugins
        Cursor active_plugins = c.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_STATUS + "=" + Plugins_Manager.PLUGIN_ACTIVE, null, null);
        if( active_plugins != null && active_plugins.moveToFirst() ) {
            do {
                Aware.stopPlugin(c, active_plugins.getString(active_plugins.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME)));
            } while(active_plugins.moveToNext());
            active_plugins.close();
        }

        //Apply fresh state
        Intent aware_apply = new Intent( Aware.ACTION_AWARE_REFRESH );
        c.sendBroadcast(aware_apply);

        Intent preferences = new Intent( c, Aware_Preferences.class);
        preferences.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK );
        c.startActivity(preferences);
    }

    private class Update_Check extends AsyncTask<Void, Void, Boolean> {
    	String filename = "", whats_new = "";
    	int version = 0;
    	PackageInfo awarePkg = null;
    	
    	@Override
    	protected Boolean doInBackground(Void... params) {
    		try {
				awarePkg = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			} catch (NameNotFoundException e1) {
				e1.printStackTrace();
				return false;
			}
			
    		HttpResponse response = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/awaredev/framework_latest");
	        if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
	        	try {
					JSONArray data = new JSONArray(EntityUtils.toString(response.getEntity()));
					JSONObject latest_framework = data.getJSONObject(0);
					
					if( Aware.DEBUG ) Log.d(Aware.TAG, "Latest: " + latest_framework.toString());
					
					filename = latest_framework.getString("filename");
					version = latest_framework.getInt("version");
					whats_new = latest_framework.getString("whats_new");
					
					if( version > awarePkg.versionCode ) {
						return true;
					}
					return false;
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
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
    			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
    			mBuilder.setSmallIcon(R.drawable.ic_stat_aware_update);
    			mBuilder.setContentTitle("AWARE update");
    			mBuilder.setContentText("New: " + whats_new + "\nVersion: " + version + "\nTap to install...");
                mBuilder.setDefaults(Notification.DEFAULT_ALL);
    			mBuilder.setAutoCancel(true);
    			
    			Intent updateIntent = new Intent(getApplicationContext(), UpdateFrameworkService.class);
    			updateIntent.putExtra("filename", filename);
    			
    			PendingIntent clickIntent = PendingIntent.getService(getApplicationContext(), 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    			mBuilder.setContentIntent(clickIntent);
    			NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    			notManager.notify(version, mBuilder.build());
    		}
    	}
    }
    
    /**
     * AWARE's plugin monitor
     * - Installs a plugin that was just downloaded
     * - Checks if a package is a plugin or not
     * @author denzilferreira
     *
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
                //Updating for new package
                if( extras.getBoolean(Intent.EXTRA_REPLACING) ) {
                    if(Aware.DEBUG) Log.d(TAG, packageName + " is updating!");
                    
                    ContentValues rowData = new ContentValues();
                    rowData.put(Aware_Plugins.PLUGIN_VERSION, getVersion(packageName));
                    
                    Cursor current_status = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, new String[]{Aware_Plugins.PLUGIN_STATUS}, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null, null);
                    if( current_status != null && current_status.moveToFirst() ) {
                        if( current_status.getInt(current_status.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Aware_Plugin.STATUS_PLUGIN_ON ) {
                            Intent aware = new Intent(Aware.ACTION_AWARE_REFRESH);
                            context.sendBroadcast(aware);
                        } 
                        if( current_status.getInt(current_status.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Plugins_Manager.PLUGIN_NOT_INSTALLED || current_status.getInt(current_status.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Plugins_Manager.PLUGIN_UPDATED ) {
                        	rowData.put(Aware_Plugins.PLUGIN_STATUS, Plugins_Manager.PLUGIN_ACTIVE);
                        }
                    }
                    if( current_status != null && ! current_status.isClosed() ) current_status.close();
                    
                    context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + packageName + "'", null);
                    
                    //Refresh plugin manager UI if visible
                    context.sendBroadcast(new Intent(ACTION_AWARE_PLUGIN_MANAGER_REFRESH));
                    
                    //Refresh stream UI if visible
                    context.sendBroadcast(new Intent(Stream_UI.ACTION_AWARE_UPDATE_STREAM));
                    
                    //all done
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
                
                //Refresh stream UI if visible
                context.sendBroadcast(new Intent(Stream_UI.ACTION_AWARE_UPDATE_STREAM));
                
                //Refresh Plugin manager UI if visible
                context.sendBroadcast(new Intent(ACTION_AWARE_PLUGIN_MANAGER_REFRESH));
            }
    	}
    	
    	private int getVersion( String package_name ) {
        	try {
    			PackageInfo pkgInfo = mPkgManager.getPackageInfo(package_name, PackageManager.GET_META_DATA);
    			return pkgInfo.versionCode;
    		} catch (NameNotFoundException e) {
    			if( Aware.DEBUG ) Log.d( Aware.TAG, e.getMessage());
    		}
        	return 0;
        }
    }
    
    /**
     * Fetches info from webservices on the installed plugins.
     * @author denzilferreira
     *
     */
    private static class Plugin_Info_Async extends AsyncTask<ApplicationInfo, Void, JSONObject> {
		private ApplicationInfo app;
        private byte[] icon;
    	
    	@Override
		protected JSONObject doInBackground(ApplicationInfo... params) {
			
    		app = params[0];
    		
    		JSONObject json_package = null;
            HttpResponse http_request = new Https(awareContext).dataGET("https://api.awareframework.com/index.php/plugins/get_plugin/" + app.packageName);
            if( http_request != null && http_request.getStatusLine().getStatusCode() == 200 ) {
            	try {
            		String json_string = EntityUtils.toString(http_request.getEntity());
            		if( ! json_string.equals("[]") ) {
            			json_package = new JSONObject(json_string);
                        icon = Plugins_Manager.cacheImage("http://api.awareframework.com" + json_package.getString("iconpath"), awareContext);
            		}
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
            return json_package;
		}
    	
		@Override
		protected void onPostExecute(JSONObject json_package) {
			super.onPostExecute(json_package);
			
			//If we already have cached information for this package, just update it
			boolean is_cached = false;
			Cursor plugin_cached = awareContext.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + app.packageName + "'", null, null);
			if( plugin_cached != null && plugin_cached.moveToFirst() ) {
				is_cached = true;
			}
			if( plugin_cached != null && ! plugin_cached.isClosed()) plugin_cached.close();
			
			ContentValues rowData = new ContentValues();
            rowData.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, app.packageName);
            rowData.put(Aware_Plugins.PLUGIN_NAME, app.loadLabel(awareContext.getPackageManager()).toString());
            rowData.put(Aware_Plugins.PLUGIN_VERSION, getVersion(app.packageName));
            rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
            if( json_package != null ) {
            	try {
            		rowData.put(Aware_Plugins.PLUGIN_ICON, icon);
					rowData.put(Aware_Plugins.PLUGIN_AUTHOR, json_package.getString("first_name") + " " + json_package.getString("last_name") + " - " + json_package.getString("email"));
					rowData.put(Aware_Plugins.PLUGIN_DESCRIPTION, json_package.getString("desc"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }
            
            if( ! is_cached ) {
            	awareContext.getContentResolver().insert(Aware_Plugins.CONTENT_URI, rowData);
            } else {
            	awareContext.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + app.packageName + "'", null);
            }
            
            if( Aware.DEBUG ) Log.d(TAG,"AWARE plugin added and activated:" + app.packageName);
            
            //Refresh stream UI if visible
            awareContext.sendBroadcast(new Intent(Stream_UI.ACTION_AWARE_UPDATE_STREAM));
            
            //Refresh Plugin Manager UI if visible
            awareContext.sendBroadcast(new Intent(ACTION_AWARE_PLUGIN_MANAGER_REFRESH));
            
            Intent aware = new Intent(Aware.ACTION_AWARE_REFRESH);
            awareContext.sendBroadcast(aware);
		}
		
		private int getVersion( String package_name ) {
        	try {
    			PackageInfo pkgInfo = awareContext.getPackageManager().getPackageInfo(package_name, PackageManager.GET_META_DATA);
    			return pkgInfo.versionCode;
    		} catch (NameNotFoundException e) {
    			if( Aware.DEBUG ) Log.d( Aware.TAG, e.getMessage());
    		}
        	return 0;
        }
    }
    
    /**
     * Background service to download missing plugins
     * @author denzilferreira
     *
     */
    public static class DownloadPluginService extends IntentService {
    	public DownloadPluginService() {
    		super("Download Plugin service");
    	}
    	
    	@Override
    	protected void onHandleIntent(Intent intent) {
    		String package_name = intent.getStringExtra("package_name");
    		boolean is_update = intent.getBooleanExtra("is_update", false);
    		
    		HttpResponse response = new Https(awareContext).dataGET("https://api.awareframework.com/index.php/plugins/get_plugin/" + package_name);
    		if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
    			try {
    				JSONObject json_package = new JSONObject(EntityUtils.toString(response.getEntity()));
    				
        			//Create the folder where all the databases will be stored on external storage
    		        File folders = new File(Environment.getExternalStorageDirectory()+"/AWARE/plugins/");
    		        folders.mkdirs();
    		        
    		        String package_url = "http://plugins.awareframework.com/" + json_package.getString("package_path").replace("/uploads/", "") + json_package.getString("package_name");
    				DownloadManager.Request request = new DownloadManager.Request(Uri.parse(package_url));
    		    	if( ! is_update ) {
    		    		request.setDescription("Downloading " + json_package.getString("title") );
    		    	} else {
    		    		request.setDescription("Updating " + json_package.getString("title") );
    		    	}
    		    	request.setTitle("AWARE");
    		    	request.setDestinationInExternalPublicDir("/", "AWARE/plugins/" + json_package.getString("package_name"));
    		    	
    		    	DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    		    	AWARE_PLUGIN_DOWNLOAD_IDS.add(manager.enqueue(request));
        		} catch (ParseException e) {
    				e.printStackTrace();
    			} catch (JSONException e) {
    				e.printStackTrace();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
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
			File releases = new File(Environment.getExternalStorageDirectory()+"/AWARE/releases/");
			releases.mkdirs();
			
			String url = "http://www.awareframework.com/" + filename;
			
			DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
			request.setDescription("Downloading newest AWARE... please wait...");
			request.setTitle("AWARE Update");
			request.setDestinationInExternalPublicDir("/", "AWARE/releases/"+filename);
			DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
			AWARE_FRAMEWORK_DOWNLOAD_ID = manager.enqueue(request);
		}
    }
    
    /**
     * BroadcastReceiver that monitors for AWARE framework actions:
     * - ACTION_AWARE_SYNC_DATA = upload data to remote webservice server.
     * - ACTION_AWARE_CLEAR_DATA = clears local device's AWARE modules databases.
     * - ACTION_AWARE_REFRESH - apply changes to the configuration.
     * - {@link DownloadManager#ACTION_DOWNLOAD_COMPLETE} - when AWARE framework update has been downloaded.
     * @author denzil
     *
     */
    public static class Aware_Broadcaster extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            
        	String[] DATABASE_TABLES = Aware_Provider.DATABASE_TABLES;
        	String[] TABLES_FIELDS = Aware_Provider.TABLES_FIELDS;
        	Uri[] CONTEXT_URIS = new Uri[]{ Aware_Device.CONTENT_URI };
        	
        	if( intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
            	Intent webserviceHelper = new Intent( WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE );
    			webserviceHelper.putExtra( WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[0] );
        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[0] );
        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[0].toString() );
        		context.startService(webserviceHelper);
            }
        	
            if( intent.getAction().equals(Aware.ACTION_AWARE_CLEAR_DATA) ) {
                context.getContentResolver().delete(Aware_Provider.Aware_Device.CONTENT_URI, null, null);
                if( Aware.DEBUG ) Log.d(TAG,"Cleared " + CONTEXT_URIS[0]);
                
                //Clear remotely if webservices are active
                if( Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
	        		Intent webserviceHelper = new Intent( WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE );
	        		webserviceHelper.putExtra( WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[0] );
	        		context.startService(webserviceHelper);
                }
            }
            
            if( intent.getAction().equals(Aware.ACTION_AWARE_REFRESH)) {
                Intent refresh = new Intent(context, Aware.class);
                context.startService(refresh);
            }
            
            if( intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE) ) {
            	DownloadManager manager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
            	long downloaded_id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            	
            	if( downloaded_id == AWARE_FRAMEWORK_DOWNLOAD_ID ) {
            		if( Aware.DEBUG ) Log.d(Aware.TAG, "AWARE framework update received...");
            		Query qry = new Query();
            		qry.setFilterById(AWARE_FRAMEWORK_DOWNLOAD_ID);
            		Cursor data = manager.query(qry);
            		if( data != null && data.moveToFirst() ) {
            			if( data.getInt(data.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL ) {
            				String filePath = data.getString(data.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
            				File mFile = new File( Uri.parse(filePath).getPath() );
            				Intent promptUpdate = new Intent(Intent.ACTION_VIEW);
            				promptUpdate.setDataAndType(Uri.fromFile(mFile), "application/vnd.android.package-archive");
            				promptUpdate.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            				context.startActivity(promptUpdate);
            			}
            		}
            		if( data != null && ! data.isClosed() ) data.close();
            	} 
            	
            	if( AWARE_PLUGIN_DOWNLOAD_IDS.size() > 0 ) {
            		for( int i = 0; i < AWARE_PLUGIN_DOWNLOAD_IDS.size(); i++ ) {
                	    long queue = AWARE_PLUGIN_DOWNLOAD_IDS.get(i);
                	    if( downloaded_id == queue ) {
                	        Cursor cur = manager.query(new Query().setFilterById(queue));
                            if( cur != null && cur.moveToFirst() ) {
                                if( cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL ) {
                                    String filePath = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                    
                                    if( Aware.DEBUG ) Log.d(Aware.TAG, "Plugin to install: " + filePath);
                                    
                                    File mFile = new File( Uri.parse(filePath).getPath() );
                                    Intent promptInstall = new Intent(Intent.ACTION_VIEW);
                                    promptInstall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    promptInstall.setDataAndType(Uri.fromFile(mFile), "application/vnd.android.package-archive");
                                    context.startActivity(promptInstall);
                                }
                            }
                            if( cur != null && ! cur.isClosed() ) cur.close();
                	    }
                	}
                	AWARE_PLUGIN_DOWNLOAD_IDS.remove(downloaded_id); //dequeue
            	}
            }
        }
    }
    private static final Aware_Broadcaster aware_BR = new Aware_Broadcaster();
    
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
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_APPLICATIONS).equals("true")) {
            startApplications();
        }else stopApplications();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ACCELEROMETER).equals("true") ) {
            startAccelerometer();
        }else stopAccelerometer();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_INSTALLATIONS).equals("true")) {
            startInstallations();
        }else stopInstallations();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("true") 
         || Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true") ) {
            startLocations();
        }else stopLocations();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BLUETOOTH).equals("true") ) {
            startBluetooth();
        }else stopBluetooth();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_SCREEN).equals("true") ) {
            startScreen();
        }else stopScreen();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BATTERY).equals("true") ) {
            startBattery();
        }else stopBattery();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_EVENTS).equals("true") ) {
            startNetwork();
        }else stopNetwork();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("true") ) {
            startTraffic();
        }else stopTraffic();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("true") 
    	 || Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("true") 
    	 || Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("true") ) {
            startCommunication();
        }else stopCommunication();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROCESSOR).equals("true") ) {
            startProcessor();
        }else stopProcessor();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TIMEZONE).equals("true") ) {
            startTimeZone();
        }else stopTimeZone();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_MQTT).equals("true") ) {
            startMQTT();
        }else stopMQTT();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_GYROSCOPE).equals("true") ) {
            startGyroscope();
        }else stopGyroscope();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_WIFI).equals("true") ) {
            startWiFi();
        }else stopWiFi();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TELEPHONY).equals("true") ) {
            startTelephony();
        }else stopTelephony();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ROTATION).equals("true") ) {
            startRotation();
        }else stopRotation();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LIGHT).equals("true") ) {
            startLight();
        }else stopLight();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_PROXIMITY).equals("true") ) {
            startProximity();
        }else stopProximity();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_MAGNETOMETER).equals("true") ) {
            startMagnetometer();
        }else stopMagnetometer();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_BAROMETER).equals("true") ) {
            startBarometer();
        }else stopBarometer();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_GRAVITY).equals("true") ) {
            startGravity();
        }else stopGravity();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LINEAR_ACCELEROMETER).equals("true") ) {
            startLinearAccelerometer();
        }else stopLinearAccelerometer();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_TEMPERATURE).equals("true") ) {
            startTemperature();
        }else stopTemperature();
        
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ESM).equals("true") ) {
            startESM();
        }else stopESM();

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_ANDROID_WEAR).equals("true") ) {
            startAndroidWear();
        }else stopAndroidWear();

        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_KEYBOARD).equals("true") ) {
            startKeyboard();
        }else stopKeyboard();
    }
    
    /**
     * Stop all services
     */
    protected void stopAllServices() {
        stopApplications();
        stopAccelerometer();
        stopBattery();
        stopBluetooth();
        stopCommunication();
        stopLocations();
        stopNetwork();
        stopTraffic();
        stopScreen();
        stopProcessor();
        stopMQTT();
        stopGyroscope();
        stopWiFi();
        stopTelephony();
        stopTimeZone();
        stopRotation();
        stopLight();
        stopProximity();
        stopMagnetometer();
        stopBarometer();
        stopGravity();
        stopLinearAccelerometer();
        stopTemperature();
        stopESM();
        stopInstallations();
        stopAndroidWear();
        stopKeyboard();
    }

    /**
     * Start keyboard module
     */
    protected void startKeyboard() {
        if( keyboard == null ) keyboard = new Intent(awareContext, Keyboard.class);
        awareContext.startService(keyboard);
    }

    /**
     * Stop keyboard module
     */
    protected void stopKeyboard() {
        if( keyboard != null ) awareContext.stopService(keyboard);
    }

    /**
     * Start Android Wear module
     */
    protected void startAndroidWear() {
        if( androidWearSrv == null ) androidWearSrv = new Intent(awareContext, Wear_Sync.class);
        awareContext.startService(androidWearSrv);
    }

    /**
     * Stop Android Wear module
     */
    protected void stopAndroidWear() {
        if( androidWearSrv != null ) awareContext.stopService(androidWearSrv);
    }

    /**
     * Start Applications module
     */
    protected void startApplications() {
        if( applicationsSrv == null) applicationsSrv = new Intent(awareContext, Applications.class);
        awareContext.startService(applicationsSrv);
    }
    
    /**
     * Stop Applications module
     */
    protected void stopApplications() {
        if( applicationsSrv != null) awareContext.stopService(applicationsSrv);
    }
    
    /**
     * Start Installations module
     */
    protected void startInstallations() {
        if(installationsSrv == null) installationsSrv = new Intent(awareContext, Installations.class);
        awareContext.startService(installationsSrv);
    }
    
    /**
     * Stop Installations module
     */
    protected void stopInstallations() {
        if(installationsSrv != null) awareContext.stopService(installationsSrv);
    }
    
    /**
     * Start ESM module
     */
    protected void startESM() {
        if( esmSrv == null ) esmSrv = new Intent(awareContext, ESM.class);
        awareContext.startService(esmSrv);
    }
    
    /**
     * Stop ESM module
     */
    protected void stopESM() {
        if( esmSrv != null ) awareContext.stopService(esmSrv);
    }
    
    /**
     * Start Temperature module
     */
    protected void startTemperature() {
        if( temperatureSrv == null ) temperatureSrv = new Intent(awareContext, Temperature.class);
        awareContext.startService(temperatureSrv);
    }
    
    /**
     * Stop Temperature module
     */
    protected void stopTemperature() {
        if( temperatureSrv != null ) awareContext.stopService(temperatureSrv);
    }
    
    /**
     * Start Linear Accelerometer module
     */
    protected void startLinearAccelerometer() {
        if( linear_accelSrv == null ) linear_accelSrv = new Intent(awareContext, LinearAccelerometer.class);
        awareContext.startService(linear_accelSrv);
    }
    
    /**
     * Stop Linear Accelerometer module
     */
    protected void stopLinearAccelerometer() {
        if( linear_accelSrv != null ) awareContext.stopService(linear_accelSrv);
    }
    
    /**
     * Start Gravity module
     */
    protected void startGravity() {
        if( gravitySrv == null ) gravitySrv = new Intent(awareContext, Gravity.class);
        awareContext.startService(gravitySrv);
    }
    
    /**
     * Stop Gravity module
     */
    protected void stopGravity() {
        if( gravitySrv != null ) awareContext.stopService(gravitySrv);
    }
    
    /**
     * Start Barometer module
     */
    protected void startBarometer() {
        if( barometerSrv == null ) barometerSrv = new Intent(awareContext, Barometer.class);
        awareContext.startService(barometerSrv);
    }
    
    /**
     * Stop Barometer module
     */
    protected void stopBarometer() {
        if( barometerSrv != null ) awareContext.stopService(barometerSrv);
    }
    
    /**
     * Start Magnetometer module
     */
    protected void startMagnetometer() {
        if( magnetoSrv == null ) magnetoSrv = new Intent(awareContext, Magnetometer.class);
        awareContext.startService(magnetoSrv);
    }
    
    /**
     * Stop Magnetometer module
     */
    protected void stopMagnetometer() {
        if( magnetoSrv != null ) awareContext.stopService(magnetoSrv);
    }
    
    /**
     * Start Proximity module
     */
    protected void startProximity() {
        if( proximitySrv == null ) proximitySrv = new Intent(awareContext, Proximity.class);
        awareContext.startService(proximitySrv);
    }
    
    /**
     * Stop Proximity module
     */
    protected void stopProximity() {
        if( proximitySrv != null ) awareContext.stopService(proximitySrv);
    }
    
    /**
     * Start Light module
     */
    protected void startLight() {
        if( lightSrv == null ) lightSrv = new Intent(awareContext, Light.class);
        awareContext.startService(lightSrv);
    }
    
    /**
     * Stop Light module
     */
    protected void stopLight() {
        if( lightSrv != null ) awareContext.stopService(lightSrv);
    }
    
    /**
     * Start Rotation module
     */
    protected void startRotation() {
        if( rotationSrv == null ) rotationSrv = new Intent(awareContext, Rotation.class);
        awareContext.startService(rotationSrv);
    }
    
    /**
     * Stop Rotation module
     */
    protected void stopRotation() {
        if( rotationSrv != null ) awareContext.stopService(rotationSrv);
    }
    
    /**
     * Start the Telephony module
     */
    protected void startTelephony() {
        if( telephonySrv == null) telephonySrv = new Intent(awareContext, Telephony.class);
        awareContext.startService(telephonySrv);
    }
    
    /**
     * Stop the Telephony module
     */
    protected void stopTelephony() {
        if( telephonySrv != null ) awareContext.stopService(telephonySrv);
    }
    
    /**
     * Start the WiFi module
     */
    protected void startWiFi() {
        if( wifiSrv == null ) wifiSrv = new Intent(awareContext, WiFi.class);
        awareContext.startService(wifiSrv);
    }
    
    protected void stopWiFi() {
        if( wifiSrv != null ) awareContext.stopService(wifiSrv);
    }
    
    /**
     * Start the gyroscope module
     */
    protected void startGyroscope() {
        if( gyroSrv == null ) gyroSrv = new Intent(awareContext, Gyroscope.class);
        awareContext.startService(gyroSrv);
    }
    
    /**
     * Stop the gyroscope module
     */
    protected void stopGyroscope() {
        if( gyroSrv != null ) awareContext.stopService(gyroSrv);
    }
    
    /**
     * Start the accelerometer module
     */
    protected void startAccelerometer() {
        if( accelerometerSrv == null ) accelerometerSrv = new Intent(awareContext, Accelerometer.class);
        awareContext.startService(accelerometerSrv);
    }
    
    /**
     * Stop the accelerometer module
     */
    protected void stopAccelerometer() {
        if( accelerometerSrv != null) awareContext.stopService(accelerometerSrv);
    }
    
    /**
     * Start the Processor module
     */
    protected void startProcessor() {
        if( processorSrv == null) processorSrv = new Intent(awareContext, Processor.class);
        awareContext.startService(processorSrv);
    }
    
    /**
     * Stop the Processor module
     */
    protected void stopProcessor() {
        if( processorSrv != null ) awareContext.stopService(processorSrv);
    }
    
    /**
     * Start the locations module
     */
    protected void startLocations() {
        if( locationsSrv == null) locationsSrv = new Intent(awareContext, Locations.class);
        awareContext.startService(locationsSrv);
    }
    
    /**
     * Stop the locations module
     */
    protected void stopLocations() {
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_GPS).equals("false") 
         && Aware.getSetting(awareContext, Aware_Preferences.STATUS_LOCATION_NETWORK).equals("false") ) {
            if(locationsSrv != null) awareContext.stopService(locationsSrv);
        }
    }
    
    /**
     * Start the bluetooth module
     */
    protected void startBluetooth() {
        if( bluetoothSrv == null) bluetoothSrv = new Intent(awareContext, Bluetooth.class);
        awareContext.startService(bluetoothSrv);
    }
    
    /**
     * Stop the bluetooth module
     */
    protected void stopBluetooth() {
        if(bluetoothSrv != null) awareContext.stopService(bluetoothSrv);
    }
    
    /**
     * Start the screen module
     */
    protected void startScreen() {
        if( screenSrv == null) screenSrv = new Intent(awareContext, Screen.class);
        awareContext.startService(screenSrv);
    }
    
    /**
     * Stop the screen module
     */
    protected void stopScreen() {
        if(screenSrv != null) awareContext.stopService(screenSrv);
    }
    
    /**
     * Start battery module
     */
    protected void startBattery() {
        if( batterySrv == null) batterySrv = new Intent(awareContext, Battery.class);
        awareContext.startService(batterySrv);
    }
    
    /**
     * Stop battery module
     */
    protected void stopBattery() {
        if(batterySrv != null) awareContext.stopService(batterySrv);
    }
    
    /**
     * Start network module
     */
    protected void startNetwork() {
        if( networkSrv == null ) networkSrv = new Intent(awareContext, Network.class);
        awareContext.startService(networkSrv);
    }
    
    /**
     * Stop network module
     */
    protected void stopNetwork() {
        if(networkSrv != null) awareContext.stopService(networkSrv);
    }
    
    /**
     * Start traffic module
     */
    protected void startTraffic() {
        if(trafficSrv == null) trafficSrv = new Intent(awareContext, Traffic.class);
        awareContext.startService(trafficSrv);
    }
    
    /**
     * Stop traffic module
     */
    protected void stopTraffic() {
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC).equals("false") ) {
            if( trafficSrv != null ) awareContext.stopService(trafficSrv);
        }
    }
    
    /**
     * Start the TimeZone module
     */
    protected void startTimeZone() {
        if(timeZoneSrv == null) timeZoneSrv = new Intent(awareContext, TimeZone.class);
        awareContext.startService(timeZoneSrv);
    }
    
    /**
     * Stop the TimeZone module
     */
    protected void stopTimeZone() {
        if( timeZoneSrv != null ) awareContext.stopService(timeZoneSrv);
    }
    
    /**
     * Start communication module
     */
    protected void startCommunication() {
        if( communicationSrv == null ) communicationSrv = new Intent(awareContext, Communication.class);
        awareContext.startService(communicationSrv);
    }
    
    /**
     * Stop communication module
     */
    protected void stopCommunication() {
        if( Aware.getSetting(awareContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS).equals("false") 
         && Aware.getSetting(awareContext, Aware_Preferences.STATUS_CALLS).equals("false") 
         && Aware.getSetting(awareContext, Aware_Preferences.STATUS_MESSAGES).equals("false") ) {
            if(communicationSrv != null) awareContext.stopService(communicationSrv);
        }
    }
    
    /**
     * Start MQTT module
     */
    protected void startMQTT() {
        if( mqttSrv == null ) mqttSrv = new Intent(awareContext, Mqtt.class);
        awareContext.startService(mqttSrv);
    }
    
    /**
     * Stop MQTT module
     */
    protected void stopMQTT() {
        if( mqttSrv != null ) awareContext.stopService(mqttSrv);
    }
}
