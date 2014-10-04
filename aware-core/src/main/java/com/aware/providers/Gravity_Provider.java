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
 * AWARE Content Provider Allows you to access all the recorded sensor readings
 * on the database Database is located at the SDCard : /AWARE/gravity.db
 * 
 * @author denzil
 * 
 */
public class Gravity_Provider extends ContentProvider {

	private static final int DATABASE_VERSION = 2;

	/**
	 * Authority of content provider
	 */
	public static String AUTHORITY = "com.aware.provider.gravity";

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
	public static final class Gravity_Sensor implements BaseColumns {
		private Gravity_Sensor() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Gravity_Provider.AUTHORITY + "/sensor_gravity");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.gravity.sensor";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.gravity.sensor";

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
	 * @author df
	 * 
	 */
	public static final class Gravity_Data implements BaseColumns {
		private Gravity_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Gravity_Provider.AUTHORITY + "/gravity");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.gravity.data";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.gravity.data";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String VALUES_0 = "double_values_0";
		public static final String VALUES_1 = "double_values_1";
		public static final String VALUES_2 = "double_values_2";
		public static final String ACCURACY = "accuracy";
		public static final String LABEL = "label";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "gravity.db";
	public static final String[] DATABASE_TABLES = { "sensor_gravity",
			"gravity" };
	public static final String[] TABLES_FIELDS = {
			// sensor device information
			Gravity_Sensor._ID + " integer primary key autoincrement,"
					+ Gravity_Sensor.TIMESTAMP + " real default 0,"
					+ Gravity_Sensor.DEVICE_ID + " text default '',"
					+ Gravity_Sensor.MAXIMUM_RANGE + " real default 0,"
					+ Gravity_Sensor.MINIMUM_DELAY + " real default 0,"
					+ Gravity_Sensor.NAME + " text default '',"
					+ Gravity_Sensor.POWER_MA + " real default 0,"
					+ Gravity_Sensor.RESOLUTION + " real default 0,"
					+ Gravity_Sensor.TYPE + " text default '',"
					+ Gravity_Sensor.VENDOR + " text default '',"
					+ Gravity_Sensor.VERSION + " text default ''," + "UNIQUE ("
					+ Gravity_Sensor.TIMESTAMP + "," + Gravity_Sensor.DEVICE_ID
					+ ")",
			// sensor data
			Gravity_Data._ID + " integer primary key autoincrement,"
					+ Gravity_Data.TIMESTAMP + " real default 0,"
					+ Gravity_Data.DEVICE_ID + " text default '',"
					+ Gravity_Data.VALUES_0 + " real default 0,"
					+ Gravity_Data.VALUES_1 + " real default 0,"
					+ Gravity_Data.VALUES_2 + " real default 0,"
					+ Gravity_Data.ACCURACY + " integer default 0,"
					+ Gravity_Data.LABEL + " text default ''," + "UNIQUE ("
					+ Gravity_Data.TIMESTAMP + "," + Gravity_Data.DEVICE_ID
					+ ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> sensorDeviceMap = null;
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
			return Gravity_Sensor.CONTENT_TYPE;
		case SENSOR_DEV_ID:
			return Gravity_Sensor.CONTENT_ITEM_TYPE;
		case SENSOR_DATA:
			return Gravity_Data.CONTENT_TYPE;
		case SENSOR_DATA_ID:
			return Gravity_Data.CONTENT_ITEM_TYPE;
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
					Gravity_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (accel_id > 0) {
				Uri accelUri = ContentUris.withAppendedId(
						Gravity_Sensor.CONTENT_URI, accel_id);
				getContext().getContentResolver().notifyChange(accelUri, null);
				return accelUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case SENSOR_DATA:
			long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
					Gravity_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (accelData_id > 0) {
				Uri accelDataUri = ContentUris.withAppendedId(
						Gravity_Data.CONTENT_URI, accelData_id);
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
	    AUTHORITY = getContext().getPackageName() + ".provider.gravity";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Gravity_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Gravity_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", SENSOR_DEV_ID);
        sUriMatcher.addURI(Gravity_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Gravity_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", SENSOR_DATA_ID);

        sensorDeviceMap = new HashMap<String, String>();
        sensorDeviceMap.put(Gravity_Sensor._ID, Gravity_Sensor._ID);
        sensorDeviceMap.put(Gravity_Sensor.TIMESTAMP, Gravity_Sensor.TIMESTAMP);
        sensorDeviceMap.put(Gravity_Sensor.DEVICE_ID, Gravity_Sensor.DEVICE_ID);
        sensorDeviceMap.put(Gravity_Sensor.MAXIMUM_RANGE,
                Gravity_Sensor.MAXIMUM_RANGE);
        sensorDeviceMap.put(Gravity_Sensor.MINIMUM_DELAY,
                Gravity_Sensor.MINIMUM_DELAY);
        sensorDeviceMap.put(Gravity_Sensor.NAME, Gravity_Sensor.NAME);
        sensorDeviceMap.put(Gravity_Sensor.POWER_MA, Gravity_Sensor.POWER_MA);
        sensorDeviceMap.put(Gravity_Sensor.RESOLUTION,
                Gravity_Sensor.RESOLUTION);
        sensorDeviceMap.put(Gravity_Sensor.TYPE, Gravity_Sensor.TYPE);
        sensorDeviceMap.put(Gravity_Sensor.VENDOR, Gravity_Sensor.VENDOR);
        sensorDeviceMap.put(Gravity_Sensor.VERSION, Gravity_Sensor.VERSION);

        sensorDataMap = new HashMap<String, String>();
        sensorDataMap.put(Gravity_Data._ID, Gravity_Data._ID);
        sensorDataMap.put(Gravity_Data.TIMESTAMP, Gravity_Data.TIMESTAMP);
        sensorDataMap.put(Gravity_Data.DEVICE_ID, Gravity_Data.DEVICE_ID);
        sensorDataMap.put(Gravity_Data.VALUES_0, Gravity_Data.VALUES_0);
        sensorDataMap.put(Gravity_Data.VALUES_1, Gravity_Data.VALUES_1);
        sensorDataMap.put(Gravity_Data.VALUES_2, Gravity_Data.VALUES_2);
        sensorDataMap.put(Gravity_Data.ACCURACY, Gravity_Data.ACCURACY);
        sensorDataMap.put(Gravity_Data.LABEL, Gravity_Data.LABEL);
	    
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
			qb.setProjectionMap(sensorDeviceMap);
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