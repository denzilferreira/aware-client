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
 * AWARE Bluetooth Content Provider Allows you to access all the recorded
 * bluetooth devices on the database Database is located at the SDCard :
 * /AWARE/bluetooth.db
 * 
 * @author denzil
 * 
 */
public class Bluetooth_Provider extends ContentProvider {

	private static final int DATABASE_VERSION = 2;

	/**
	 * Authority of Bluetooth content provider
	 * com.aware.provider.bluetooth
	 */
	public static String AUTHORITY = "com.aware.provider.bluetooth";

	// ContentProvider query paths
	private static final int BT_DEV = 1;
	private static final int BT_DEV_ID = 2;
	private static final int BT_DATA = 3;
	private static final int BT_DATA_ID = 4;

	/**
	 * Bluetooth device info
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Bluetooth_Sensor implements BaseColumns {
		private Bluetooth_Sensor() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Bluetooth_Provider.AUTHORITY + "/sensor_bluetooth");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.bluetooth.device";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.bluetooth.device";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String BT_ADDRESS = "bt_address";
		public static final String BT_NAME = "bt_name";
	}

	/**
	 * Logged bluetooth data
	 * 
	 * @author df
	 * 
	 */
	public static final class Bluetooth_Data implements BaseColumns {
		private Bluetooth_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse( "content://" + Bluetooth_Provider.AUTHORITY + "/bluetooth" );
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.bluetooth.data";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.bluetooth.data";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String BT_ADDRESS = "bt_address";
		public static final String BT_NAME = "bt_name";
		public static final String BT_RSSI = "bt_rssi";
		public static final String BT_LABEL = "label";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "bluetooth.db";
	public static final String[] DATABASE_TABLES = { "sensor_bluetooth",
			"bluetooth" };
	public static final String[] TABLES_FIELDS = {
			// device
			Bluetooth_Sensor._ID + " integer primary key autoincrement,"
					+ Bluetooth_Sensor.TIMESTAMP + " real default 0,"
					+ Bluetooth_Sensor.DEVICE_ID + " text default '',"
					+ Bluetooth_Sensor.BT_ADDRESS + " text default '',"
					+ Bluetooth_Sensor.BT_NAME + " text default '',"
					+ "UNIQUE (" + Bluetooth_Sensor.TIMESTAMP + ","
					+ Bluetooth_Sensor.DEVICE_ID + ")",
			// data
			Bluetooth_Data._ID + " integer primary key autoincrement,"
					+ Bluetooth_Data.TIMESTAMP + " real default 0,"
					+ Bluetooth_Data.DEVICE_ID + " text default '',"
					+ Bluetooth_Data.BT_ADDRESS + " text default '',"
					+ Bluetooth_Data.BT_NAME + " text default '',"
					+ Bluetooth_Data.BT_RSSI + " integer default 0,"
					+ Bluetooth_Data.BT_LABEL + " text default '',"
					+ "UNIQUE (" + Bluetooth_Data.TIMESTAMP + ","
					+ Bluetooth_Data.DEVICE_ID + ","
					+ Bluetooth_Data.BT_ADDRESS + ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> bluetoothDeviceMap = null;
	private static HashMap<String, String> bluetoothDataMap = null;
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
	 * Delete bluetooth entry from the database
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
	    if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

		int count = 0;
		switch (sUriMatcher.match(uri)) {
		case BT_DEV:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case BT_DATA:
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
		case BT_DEV:
			return Bluetooth_Sensor.CONTENT_TYPE;
		case BT_DEV_ID:
			return Bluetooth_Sensor.CONTENT_ITEM_TYPE;
		case BT_DATA:
			return Bluetooth_Data.CONTENT_TYPE;
		case BT_DATA_ID:
			return Bluetooth_Data.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	/**
	 * Insert bluetooth entry to the database
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
		case BT_DEV:
			long rowId = database.insertWithOnConflict(DATABASE_TABLES[0],
					Bluetooth_Sensor.BT_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (rowId > 0) {
				Uri bluetoothUri = ContentUris.withAppendedId(
						Bluetooth_Sensor.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(bluetoothUri,
						null);
				return bluetoothUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case BT_DATA:
			long btId = database.insertWithOnConflict(DATABASE_TABLES[1],
					Bluetooth_Data.BT_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (btId > 0) {
				Uri bluetoothUri = ContentUris.withAppendedId(
						Bluetooth_Data.CONTENT_URI, btId);
				getContext().getContentResolver().notifyChange(bluetoothUri,
						null);
				return bluetoothUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.bluetooth";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[0],
                BT_DEV);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", BT_DEV_ID);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[1],
                BT_DATA);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", BT_DATA_ID);

        bluetoothDeviceMap = new HashMap<String, String>();
        bluetoothDeviceMap.put(Bluetooth_Sensor._ID, Bluetooth_Sensor._ID);
        bluetoothDeviceMap.put(Bluetooth_Sensor.TIMESTAMP,
                Bluetooth_Sensor.TIMESTAMP);
        bluetoothDeviceMap.put(Bluetooth_Sensor.DEVICE_ID,
                Bluetooth_Sensor.DEVICE_ID);
        bluetoothDeviceMap.put(Bluetooth_Sensor.BT_ADDRESS,
                Bluetooth_Sensor.BT_ADDRESS);
        bluetoothDeviceMap.put(Bluetooth_Sensor.BT_NAME,
                Bluetooth_Sensor.BT_NAME);

        bluetoothDataMap = new HashMap<String, String>();
        bluetoothDataMap.put(Bluetooth_Data._ID, Bluetooth_Data._ID);
        bluetoothDataMap
                .put(Bluetooth_Data.TIMESTAMP, Bluetooth_Data.TIMESTAMP);
        bluetoothDataMap
                .put(Bluetooth_Data.DEVICE_ID, Bluetooth_Data.DEVICE_ID);
        bluetoothDataMap.put(Bluetooth_Data.BT_ADDRESS,
                Bluetooth_Data.BT_ADDRESS);
        bluetoothDataMap.put(Bluetooth_Data.BT_NAME, Bluetooth_Data.BT_NAME);
        bluetoothDataMap.put(Bluetooth_Data.BT_RSSI, Bluetooth_Data.BT_RSSI);
        bluetoothDataMap.put(Bluetooth_Data.BT_LABEL, Bluetooth_Data.BT_LABEL);
	    
	    return true;
	}

	/**
	 * Query bluetooth entries from the database
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
		case BT_DEV:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(bluetoothDeviceMap);
			break;
		case BT_DATA:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(bluetoothDataMap);
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
	 * Update bluetooth on the database
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
		case BT_DEV:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case BT_DATA:
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