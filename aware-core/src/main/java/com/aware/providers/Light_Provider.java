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
 * AWARE Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/light.db
 * 
 * @author denzil
 * 
 */
public class Light_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 2;

	/**
	 * Authority of content provider
	 */
	public static String AUTHORITY = "com.aware.provider.light";

	// ContentProvider query paths
	private static final int SENSOR_DEV = 1;
	private static final int SENSOR_DEV_ID = 2;
	private static final int SENSOR_DATA = 3;
	private static final int SENSOR_DATA_ID = 4;

	/**
	 * Sensor device info
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Light_Sensor implements BaseColumns {
		private Light_Sensor() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Light_Provider.AUTHORITY + "/sensor_light");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.light.device";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.light.device";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String MAXIMUM_RANGE = "double_sensor_maximum_range";
		public static final String MINIMUM_DELAY = "double_sensor_minimum_delay";
		public static final String NAME = "sensor_name";
		public static final String POWER_MA = "double_sensor_power_ma";
		public static final String RESOLUTION = "double_sensor_resolution";
		public static final String TYPE = "sensor_type";
		public static final String VENDOR = "sensor_vendor";
		public static final String VERSION = "sensor_version";
	}

	/**
	 * Logged sensor data
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Light_Data implements BaseColumns {
		private Light_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Light_Provider.AUTHORITY + "/light");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.light.data";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.light.data";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String LIGHT_LUX = "double_light_lux";
		public static final String ACCURACY = "accuracy";
		public static final String LABEL = "label";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "light.db";
	public static final String[] DATABASE_TABLES = { "sensor_light", "light" };
	public static final String[] TABLES_FIELDS = {
			// sensor device information
			Light_Sensor._ID + " integer primary key autoincrement,"
					+ Light_Sensor.TIMESTAMP + " real default 0,"
					+ Light_Sensor.DEVICE_ID + " text default '',"
					+ Light_Sensor.MAXIMUM_RANGE + " real default 0,"
					+ Light_Sensor.MINIMUM_DELAY + " real default 0,"
					+ Light_Sensor.NAME + " text default '',"
					+ Light_Sensor.POWER_MA + " real default 0,"
					+ Light_Sensor.RESOLUTION + " real default 0,"
					+ Light_Sensor.TYPE + " text default '',"
					+ Light_Sensor.VENDOR + " text default '',"
					+ Light_Sensor.VERSION + " text default ''," + "UNIQUE("
					+ Light_Sensor.TIMESTAMP + "," + Light_Sensor.DEVICE_ID
					+ ")",
			// sensor data
			Light_Data._ID + " integer primary key autoincrement,"
					+ Light_Data.TIMESTAMP + " real default 0,"
					+ Light_Data.DEVICE_ID + " text default '',"
					+ Light_Data.LIGHT_LUX + " real default 0,"
					+ Light_Data.ACCURACY + " integer default 0,"
					+ Light_Data.LABEL + " text default ''," + "UNIQUE("
					+ Light_Data.TIMESTAMP + "," + Light_Data.DEVICE_ID + ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> sensorMap = null;
	private static HashMap<String, String> sensorDataMap = null;
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
		case SENSOR_DEV:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case SENSOR_DATA:
			count = database.delete(DATABASE_TABLES[1], selection,
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
		case SENSOR_DEV:
			return Light_Sensor.CONTENT_TYPE;
		case SENSOR_DEV_ID:
			return Light_Sensor.CONTENT_ITEM_TYPE;
		case SENSOR_DATA:
			return Light_Data.CONTENT_TYPE;
		case SENSOR_DATA_ID:
			return Light_Data.CONTENT_ITEM_TYPE;
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
		case SENSOR_DEV:
			long accel_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Light_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (accel_id > 0) {
				Uri accelUri = ContentUris.withAppendedId(
						Light_Sensor.CONTENT_URI, accel_id);
				getContext().getContentResolver().notifyChange(accelUri, null);
				return accelUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case SENSOR_DATA:
			long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
					Light_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (accelData_id > 0) {
				Uri accelDataUri = ContentUris.withAppendedId(
						Light_Data.CONTENT_URI, accelData_id);
				getContext().getContentResolver().notifyChange(accelDataUri,
						null);
				return accelDataUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.light";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Light_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Light_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#",
                SENSOR_DEV_ID);
        sUriMatcher.addURI(Light_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Light_Provider.AUTHORITY, DATABASE_TABLES[1] + "/#",
                SENSOR_DATA_ID);

        sensorMap = new HashMap<String, String>();
        sensorMap.put(Light_Sensor._ID, Light_Sensor._ID);
        sensorMap.put(Light_Sensor.TIMESTAMP, Light_Sensor.TIMESTAMP);
        sensorMap.put(Light_Sensor.DEVICE_ID, Light_Sensor.DEVICE_ID);
        sensorMap.put(Light_Sensor.MAXIMUM_RANGE, Light_Sensor.MAXIMUM_RANGE);
        sensorMap.put(Light_Sensor.MINIMUM_DELAY, Light_Sensor.MINIMUM_DELAY);
        sensorMap.put(Light_Sensor.NAME, Light_Sensor.NAME);
        sensorMap.put(Light_Sensor.POWER_MA, Light_Sensor.POWER_MA);
        sensorMap.put(Light_Sensor.RESOLUTION, Light_Sensor.RESOLUTION);
        sensorMap.put(Light_Sensor.TYPE, Light_Sensor.TYPE);
        sensorMap.put(Light_Sensor.VENDOR, Light_Sensor.VENDOR);
        sensorMap.put(Light_Sensor.VERSION, Light_Sensor.VERSION);

        sensorDataMap = new HashMap<String, String>();
        sensorDataMap.put(Light_Data._ID, Light_Data._ID);
        sensorDataMap.put(Light_Data.TIMESTAMP, Light_Data.TIMESTAMP);
        sensorDataMap.put(Light_Data.DEVICE_ID, Light_Data.DEVICE_ID);
        sensorDataMap.put(Light_Data.LIGHT_LUX, Light_Data.LIGHT_LUX);
        sensorDataMap.put(Light_Data.ACCURACY, Light_Data.ACCURACY);
        sensorDataMap.put(Light_Data.LABEL, Light_Data.LABEL);
	    
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
		case SENSOR_DEV:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(sensorMap);
			break;
		case SENSOR_DATA:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(sensorDataMap);
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
		case SENSOR_DEV:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case SENSOR_DATA:
			count = database.update(DATABASE_TABLES[1], values, selection,
					selectionArgs);
			break;
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
}