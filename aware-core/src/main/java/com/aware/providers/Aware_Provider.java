/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware.providers;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

/**
 * AWARE framework content provider - Device information - Framework settings -
 * Plugins
 * 
 * @author df
 * 
 */
public class Aware_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 7;

	/**
	 * AWARE framework content authority
	 * com.aware.provider.aware
	 */
	public static String AUTHORITY = "com.aware.provider.aware";

	private static final int DEVICE_INFO = 1;
	private static final int DEVICE_INFO_ID = 2;
	private static final int SETTING = 3;
	private static final int SETTING_ID = 4;
	private static final int PLUGIN = 5;
	private static final int PLUGIN_ID = 6;

	/**
	 * Information about the device in which the framework is installed.
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Aware_Device implements BaseColumns {
		private Aware_Device() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://" + Aware_Provider.AUTHORITY + "/aware_device");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.device";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.device";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String BOARD = "board";
		public static final String BRAND = "brand";
		public static final String DEVICE = "device";
		public static final String BUILD_ID = "build_id";
		public static final String HARDWARE = "hardware";
		public static final String MANUFACTURER = "manufacturer";
		public static final String MODEL = "model";
		public static final String PRODUCT = "product";
		public static final String SERIAL = "serial";
		public static final String RELEASE = "release";
		public static final String RELEASE_TYPE = "release_type";
		public static final String SDK = "sdk";
		public static final String LABEL = "label";
	}

	/**
	 * Aware settings
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Aware_Settings implements BaseColumns {
		private Aware_Settings() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Aware_Provider.AUTHORITY + "/aware_settings");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.settings";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.settings";

		public static final String SETTING_ID = "_id";
		public static final String SETTING_KEY = "key";
		public static final String SETTING_VALUE = "value";
		public static final String SETTING_PACKAGE_NAME = "package_name";
	}

	/**
	 * Aware plugins
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Aware_Plugins implements BaseColumns {
		private Aware_Plugins() {};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Aware_Provider.AUTHORITY + "/aware_plugins");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugins";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugins";

		public static final String PLUGIN_ID = "_id";
		public static final String PLUGIN_PACKAGE_NAME = "package_name";
		public static final String PLUGIN_NAME = "plugin_name";
		public static final String PLUGIN_VERSION = "plugin_version";
		public static final String PLUGIN_STATUS = "plugin_status";
		public static final String PLUGIN_AUTHOR = "plugin_author";
		public static final String PLUGIN_ICON = "plugin_icon";
		public static final String PLUGIN_DESCRIPTION = "plugin_description";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "aware.db";
	public static final String[] DATABASE_TABLES = { "aware_device",
			"aware_settings", "aware_plugins" };
	public static final String[] TABLES_FIELDS = {
			// Device information
			Aware_Device._ID + " integer primary key autoincrement,"
			+ Aware_Device.TIMESTAMP + " real default 0,"
			+ Aware_Device.DEVICE_ID + " text default '',"
			+ Aware_Device.BOARD + " text default '',"
			+ Aware_Device.BRAND + " text default '',"
			+ Aware_Device.DEVICE + " text default '',"
			+ Aware_Device.BUILD_ID + " text default '',"
			+ Aware_Device.HARDWARE + " text default '',"
			+ Aware_Device.MANUFACTURER + " text default '',"
			+ Aware_Device.MODEL + " text default '',"
			+ Aware_Device.PRODUCT + " text default '',"
			+ Aware_Device.SERIAL + " text default '',"
			+ Aware_Device.RELEASE + " text default '',"
			+ Aware_Device.RELEASE_TYPE + " text default '',"
			+ Aware_Device.SDK + " integer default 0,"
			+ Aware_Device.LABEL + " text default '',"
			+ "UNIQUE (" + Aware_Device.TIMESTAMP + "," + Aware_Device.DEVICE_ID + ")",

			// Settings
			Aware_Settings.SETTING_ID + " integer primary key autoincrement,"
			+ Aware_Settings.SETTING_KEY + " text default '',"
			+ Aware_Settings.SETTING_VALUE + " text default '',"
			+ Aware_Settings.SETTING_PACKAGE_NAME + " text default ''",

			// Plugins
			Aware_Plugins.PLUGIN_ID + " integer primary key autoincrement,"
			+ Aware_Plugins.PLUGIN_PACKAGE_NAME + " text default '',"
			+ Aware_Plugins.PLUGIN_NAME + " text default '',"
			+ Aware_Plugins.PLUGIN_VERSION + " integer default 0,"
			+ Aware_Plugins.PLUGIN_STATUS + " integer default 0,"
			+ Aware_Plugins.PLUGIN_AUTHOR + " text default '',"
			+ Aware_Plugins.PLUGIN_ICON + " blob default null,"
			+ Aware_Plugins.PLUGIN_DESCRIPTION + " text default ''"
			};

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> deviceMap = null;
	private static HashMap<String, String> settingsMap = null;
	private static HashMap<String, String> pluginsMap = null;

	private static DatabaseHelper databaseHelper = null;
	private static SQLiteDatabase database = null;

	private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper( getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS );
        }
        if( databaseHelper != null && ( database == null || ! database.isOpen() )) {
            database = databaseHelper.getWritableDatabase();
        }
        return( database != null && databaseHelper != null);
    }
	
	/**
	 * Delete entry from the database
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		
	    if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

		int count = 0;
		switch (sUriMatcher.match(uri)) {
		case DEVICE_INFO:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case SETTING:
			count = database.delete(DATABASE_TABLES[1], selection,
					selectionArgs);
			break;
		case PLUGIN:
			count = database.delete(DATABASE_TABLES[2], selection,
					selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case DEVICE_INFO:
			return Aware_Device.CONTENT_TYPE;
		case DEVICE_INFO_ID:
			return Aware_Device.CONTENT_ITEM_TYPE;
		case SETTING:
			return Aware_Settings.CONTENT_TYPE;
		case SETTING_ID:
			return Aware_Settings.CONTENT_ITEM_TYPE;
		case PLUGIN:
			return Aware_Plugins.CONTENT_TYPE;
		case PLUGIN_ID:
			return Aware_Plugins.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	/**
	 * Insert entry to the database
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
	    
	    if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();

		switch (sUriMatcher.match(uri)) {
		case DEVICE_INFO:
			long dev_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Aware_Device.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
			if (dev_id > 0) {
				Uri devUri = ContentUris.withAppendedId(
						Aware_Device.CONTENT_URI, dev_id);
				getContext().getContentResolver().notifyChange(devUri, null);
				return devUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case SETTING:
			long sett_id = database.insertWithOnConflict(DATABASE_TABLES[1],
					Aware_Settings.SETTING_KEY, values, SQLiteDatabase.CONFLICT_IGNORE);
			if (sett_id > 0) {
				Uri settUri = ContentUris.withAppendedId(
						Aware_Settings.CONTENT_URI, sett_id);
				getContext().getContentResolver().notifyChange(settUri, null);
				return settUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case PLUGIN:
			long plug_id = database.insertWithOnConflict(DATABASE_TABLES[2],
					Aware_Plugins.PLUGIN_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
			if (plug_id > 0) {
				Uri settUri = ContentUris.withAppendedId(
						Aware_Plugins.CONTENT_URI, plug_id);
				getContext().getContentResolver().notifyChange(settUri, null);
				return settUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.aware";
	    
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[0], DEVICE_INFO);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#", DEVICE_INFO_ID);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[1], SETTING);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[1] + "/#", SETTING_ID);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[2], PLUGIN);
        sUriMatcher.addURI(Aware_Provider.AUTHORITY, DATABASE_TABLES[2] + "/#", PLUGIN_ID);

        deviceMap = new HashMap<String, String>();
        deviceMap.put(Aware_Device._ID, Aware_Device._ID);
        deviceMap.put(Aware_Device.TIMESTAMP, Aware_Device.TIMESTAMP);
        deviceMap.put(Aware_Device.DEVICE_ID, Aware_Device.DEVICE_ID);
        deviceMap.put(Aware_Device.BOARD, Aware_Device.BOARD);
        deviceMap.put(Aware_Device.BRAND, Aware_Device.BRAND);
        deviceMap.put(Aware_Device.DEVICE, Aware_Device.DEVICE);
        deviceMap.put(Aware_Device.BUILD_ID, Aware_Device.BUILD_ID);
        deviceMap.put(Aware_Device.HARDWARE, Aware_Device.HARDWARE);
        deviceMap.put(Aware_Device.MANUFACTURER, Aware_Device.MANUFACTURER);
        deviceMap.put(Aware_Device.MODEL, Aware_Device.MODEL);
        deviceMap.put(Aware_Device.PRODUCT, Aware_Device.PRODUCT);
        deviceMap.put(Aware_Device.SERIAL, Aware_Device.SERIAL);
        deviceMap.put(Aware_Device.RELEASE, Aware_Device.RELEASE);
        deviceMap.put(Aware_Device.RELEASE_TYPE, Aware_Device.RELEASE_TYPE);
        deviceMap.put(Aware_Device.SDK, Aware_Device.SDK);
        deviceMap.put(Aware_Device.LABEL, Aware_Device.LABEL);

        settingsMap = new HashMap<String, String>();
        settingsMap.put(Aware_Settings.SETTING_ID, Aware_Settings.SETTING_ID);
        settingsMap.put(Aware_Settings.SETTING_KEY, Aware_Settings.SETTING_KEY);
        settingsMap.put(Aware_Settings.SETTING_VALUE,Aware_Settings.SETTING_VALUE);
        settingsMap.put(Aware_Settings.SETTING_PACKAGE_NAME, Aware_Settings.SETTING_PACKAGE_NAME);

        pluginsMap = new HashMap<String, String>();
        pluginsMap.put(Aware_Plugins.PLUGIN_ID, Aware_Plugins.PLUGIN_ID);
        pluginsMap.put(Aware_Plugins.PLUGIN_PACKAGE_NAME,Aware_Plugins.PLUGIN_PACKAGE_NAME);
        pluginsMap.put(Aware_Plugins.PLUGIN_NAME, Aware_Plugins.PLUGIN_NAME);
        pluginsMap.put(Aware_Plugins.PLUGIN_VERSION,Aware_Plugins.PLUGIN_VERSION);
        pluginsMap.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugins.PLUGIN_STATUS);
        pluginsMap.put(Aware_Plugins.PLUGIN_AUTHOR, Aware_Plugins.PLUGIN_AUTHOR);
        pluginsMap.put(Aware_Plugins.PLUGIN_ICON, Aware_Plugins.PLUGIN_ICON);
        pluginsMap.put(Aware_Plugins.PLUGIN_DESCRIPTION, Aware_Plugins.PLUGIN_DESCRIPTION);
        
        return true;
	}

	

	/**
	 * Query entries from the database
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
	    if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (sUriMatcher.match(uri)) {
		case DEVICE_INFO:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(deviceMap);
			break;
		case SETTING:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(settingsMap);
			break;
		case PLUGIN:
			qb.setTables(DATABASE_TABLES[2]);
			qb.setProjectionMap(pluginsMap);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		try {
			Cursor c = qb.query(database, projection, selection, selectionArgs,
					null, null, sortOrder);
			c.setNotificationUri(getContext().getContentResolver(), uri);
			return c;
		} catch (IllegalStateException e) {
			if (Aware.DEBUG)
				Log.e(Aware.TAG, e.getMessage());
			return null;
		}
	}

	/**
	 * Update application on the database
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		
	    if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }
	    
		int count = 0;
		switch (sUriMatcher.match(uri)) {
		case DEVICE_INFO:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case SETTING:
			count = database.update(DATABASE_TABLES[1], values, selection,
					selectionArgs);
			break;
		case PLUGIN:
			count = database.update(DATABASE_TABLES[2], values, selection,
					selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
}
