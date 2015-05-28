
package com.aware.utils;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class WebserviceHelper extends IntentService {
	
	public static final String ACTION_AWARE_WEBSERVICE_SYNC_TABLE = "ACTION_AWARE_WEBSERVICE_SYNC_TABLE";
	public static final String ACTION_AWARE_WEBSERVICE_CLEAR_TABLE = "ACTION_AWARE_WEBSERVICE_CLEAR_TABLE";
	
	public static final String EXTRA_TABLE = "table";
	public static final String EXTRA_FIELDS = "fields";
	public static final String EXTRA_CONTENT_URI = "uri";

    public WebserviceHelper() {
		super(Aware.TAG + " Webservice Sync");
	}

	private boolean exists( String[] array, String find ) {
		for( String a : array ) {
			if( a.equals(find) ) return true;
		}
		return false;
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {

        int batch_size = 10000; //default for phones
		if( Aware.is_watch(getApplicationContext()) ) {
            batch_size = 100; //default for watch (we have a limit of 100KB of data packet size (Message API restrictions)
        }

		String WEBSERVER = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER);
		String DEVICE_ID = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
		boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");
		String DATABASE_TABLE = intent.getStringExtra(EXTRA_TABLE);
		String TABLES_FIELDS = intent.getStringExtra(EXTRA_FIELDS);
		Uri CONTENT_URI = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI));

		//Fixed: not using webservices
		if( WEBSERVER.length() == 0 ) return;
		
		if( intent.getAction().equals(ACTION_AWARE_WEBSERVICE_SYNC_TABLE) ) {

			if( ! Aware.is_watch(getApplicationContext()) ) { //watch doesn't care about Wi-Fi or not.
				//Check if we should do this only over Wi-Fi
				boolean wifi_only = Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_WIFI_ONLY).equals("true");
				if( wifi_only ) {
					ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
					NetworkInfo active_network = cm.getActiveNetworkInfo();
					if( active_network != null && active_network.getType() != ConnectivityManager.TYPE_WIFI ) {
						return;
					}
				}
			}

            if( Aware.DEBUG ) Log.d(Aware.TAG, "Synching data..." + DATABASE_TABLE);

			//Check first if we have database table remotely, otherwise create it!
			ArrayList<NameValuePair> fields = new ArrayList<NameValuePair>();
    		fields.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, DEVICE_ID));
    		fields.add(new BasicNameValuePair(EXTRA_FIELDS, TABLES_FIELDS));

    		//Create table if doesn't exist on the remote webservice server
    		HttpResponse response = new Https(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/create_table", fields, true);
    		if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
    		    if( DEBUG ) {
                    HttpResponse copy = response;
                    try {
                        if( DEBUG ) Log.d(Aware.TAG, Https.undoGZIP(copy));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
    			
    			String[] columnsStr = new String[]{};
    			Cursor columnsDB = getContentResolver().query(CONTENT_URI, null, null, null, null);
    			if( columnsDB != null && columnsDB.moveToFirst() ) {
    				columnsStr = columnsDB.getColumnNames();
    			}
    			if( columnsDB != null && ! columnsDB.isClosed() ) columnsDB.close();
    			
				try {
					ArrayList<NameValuePair> request = new ArrayList<NameValuePair>();
    				request.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, DEVICE_ID));
    				
    				//check the latest entry in remote database
    				HttpResponse latest = new Https(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/latest", request, true);
    				if( latest == null ) return;
    				
    				String data = "[]";
    				try {
    				    data = Https.undoGZIP(latest);
    				} catch( IllegalStateException e ) {
    				    Log.d(Aware.TAG,"Unable to connect to webservices...");
    				}

                    if( DEBUG ) Log.d(Aware.TAG, "Server answer: " + data);

    				//If in a study, get from joined date onwards
    				String study_condition = "";
					if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 && Aware.getSetting(getApplicationContext(), "study_start").length() > 0 ) {
						study_condition = " AND timestamp > " + Long.parseLong(Aware.getSetting(getApplicationContext(), "study_start"));
					}

                    //We always want to sync the device's profile
					if( DATABASE_TABLE.equalsIgnoreCase("aware_device") ) study_condition = "";

					JSONArray remoteData = new JSONArray(data);

					Cursor context_data;
					if( remoteData.length() == 0 ) {
						if( exists(columnsStr, "double_end_timestamp") ) {
							context_data = getContentResolver().query(CONTENT_URI, null, "double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
						} else if (exists(columnsStr, "double_esm_user_answer_timestamp")) {
							context_data = getContentResolver().query(CONTENT_URI, null, "double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
						} else {
							context_data = getContentResolver().query(CONTENT_URI, null, "1" + study_condition, null, "timestamp ASC");
						}
					} else {
						long last;
						if ( exists(columnsStr, "double_end_timestamp") ) {
							last = remoteData.getJSONObject(0).getLong("double_end_timestamp");
							context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_end_timestamp != 0" + study_condition, null, "timestamp ASC");
						} else if( exists(columnsStr, "double_esm_user_answer_timestamp") ) {
							last = remoteData.getJSONObject(0).getLong("double_esm_user_answer_timestamp");
							context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + " AND double_esm_user_answer_timestamp != 0" + study_condition, null, "timestamp ASC");
						} else {
							last = remoteData.getJSONObject(0).getLong("timestamp");
							context_data = getContentResolver().query(CONTENT_URI, null, "timestamp > " + last + study_condition, null, "timestamp ASC");
						}
					}
					
					JSONArray context_data_entries = new JSONArray();
					if( context_data != null && context_data.moveToFirst() ) {

                        int batch_total = (Math.round(context_data.getCount()/batch_size) > 0 ? Math.round(context_data.getCount()/batch_size) : 1 );
                        int batch_count = 0;

                        if( DEBUG ) Log.d(Aware.TAG, "Syncing " + context_data.getCount() + " from " + DATABASE_TABLE + " in " + batch_total + " batches");
						long start = System.currentTimeMillis();

						do {
							JSONObject entry = new JSONObject();
							
							String[] columns = context_data.getColumnNames();
							for(String c_name : columns) {
								
								//Skip local database ID
								if( c_name.equals("_id") ) continue;
								
								if( c_name.equals("timestamp") || c_name.contains("double") ) {
									entry.put(c_name, context_data.getDouble(context_data.getColumnIndex(c_name)));
								} else if (c_name.contains("float")) {
									entry.put(c_name, context_data.getFloat(context_data.getColumnIndex(c_name)));
								} else if (c_name.contains("long")) {
									entry.put(c_name, context_data.getLong(context_data.getColumnIndex(c_name)));
								} else if (c_name.contains("blob")) {
									entry.put(c_name, context_data.getBlob(context_data.getColumnIndex(c_name)));
								} else if (c_name.contains("integer")) {
									entry.put(c_name, context_data.getInt(context_data.getColumnIndex(c_name)));
								} else {
									entry.put(c_name, context_data.getString(context_data.getColumnIndex(c_name)));
								}
							}
							context_data_entries.put(entry);
							
							if( context_data_entries.length() == batch_size ) {
                                batch_count++;
                                if( DEBUG ) Log.d(Aware.TAG, "Sync batch "+ batch_count + "/" + batch_total);

                                request = new ArrayList<NameValuePair>();
								request.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, DEVICE_ID));
								request.add(new BasicNameValuePair("data", context_data_entries.toString()));
								new Https(getApplicationContext()).dataPOST( WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
								
								context_data_entries = new JSONArray();
							}

						} while ( context_data.moveToNext() );
						
						if( context_data_entries.length() > 0 ) {
                            batch_count++;
                            if( DEBUG ) Log.d(Aware.TAG, "Sync batch "+ batch_count + "/" + batch_total);

                            request = new ArrayList<NameValuePair>();
							request.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, DEVICE_ID));
							request.add(new BasicNameValuePair("data", context_data_entries.toString()));
							new Https(getApplicationContext()).dataPOST( WEBSERVER + "/" + DATABASE_TABLE + "/insert", request, true);
						}
                        if( DEBUG ) Log.d(Aware.TAG, "Sync time: " + DateUtils.formatElapsedTime((System.currentTimeMillis()-start)/1000));
					}
					
					if( context_data != null && ! context_data.isClosed() ) context_data.close();
					
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
    		}
		}
		
		//Clear database table remotely
		if( intent.getAction().equals(ACTION_AWARE_WEBSERVICE_CLEAR_TABLE) ) {

            if( Aware.DEBUG ) Log.d(Aware.TAG, "Clearing data..." + DATABASE_TABLE);

			ArrayList<NameValuePair> request = new ArrayList<NameValuePair>();
    		request.add(new BasicNameValuePair(Aware_Preferences.DEVICE_ID, DEVICE_ID));
    		new Https(getApplicationContext()).dataPOST(WEBSERVER + "/" + DATABASE_TABLE + "/clear_table", request, true);
		}
	}
}
