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
 * AWARE Battery Content Provider Allows you to access all the recorded battery
 * events on the database Database is located at the SDCard : /AWARE/battery.db
 * 
 * @author denzil
 * 
 */
public class Battery_Provider extends ContentProvider {

	private static final int DATABASE_VERSION = 3;

	/**
	 * Authority of Battery content provider
	 */
	public static String AUTHORITY = "com.aware.provider.battery";

	// ContentProvider query paths
	private static final int BATTERY = 1;
	private static final int BATTERY_ID = 2;
	private static final int BATTERY_DISCHARGE = 3;
	private static final int BATTERY_DISCHARGE_ID = 4;
	private static final int BATTERY_CHARGE = 5;
	private static final int BATTERY_CHARGE_ID = 6;

	/**
	 * Battery content representation
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Battery_Data implements BaseColumns {
		private Battery_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Battery_Provider.AUTHORITY + "/battery");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.battery";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.battery";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String STATUS = "battery_status";
		public static final String LEVEL = "battery_level";
		public static final String SCALE = "battery_scale";
		public static final String VOLTAGE = "battery_voltage";
		public static final String TEMPERATURE = "battery_temperature";
		public static final String PLUG_ADAPTOR = "battery_adaptor";
		public static final String HEALTH = "battery_health";
		public static final String TECHNOLOGY = "battery_technology";
	}

	public static final class Battery_Discharges implements BaseColumns {
		private Battery_Discharges() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Battery_Provider.AUTHORITY + "/battery_discharges");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.battery.discharges";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.battery.discharges";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String BATTERY_START = "battery_start";
		public static final String BATTERY_END = "battery_end";
		public static final String END_TIMESTAMP = "double_end_timestamp";
	}

	public static final class Battery_Charges implements BaseColumns {
		private Battery_Charges() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Battery_Provider.AUTHORITY + "/battery_charges");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.battery.charges";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.battery.charges";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String BATTERY_START = "battery_start";
		public static final String BATTERY_END = "battery_end";
		public static final String END_TIMESTAMP = "double_end_timestamp";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "battery.db";
	public static final String[] DATABASE_TABLES = { "battery",
			"battery_discharges", "battery_charges" };
	public static final String[] TABLES_FIELDS = {
			// battery
			Battery_Data._ID + " integer primary key autoincrement,"
					+ Battery_Data.TIMESTAMP + " real default 0,"
					+ Battery_Data.DEVICE_ID + " text default '',"
					+ Battery_Data.STATUS + " integer default 0,"
					+ Battery_Data.LEVEL + " integer default 0,"
					+ Battery_Data.SCALE + " integer default 0,"
					+ Battery_Data.VOLTAGE + " integer default 0,"
					+ Battery_Data.TEMPERATURE + " integer default 0,"
					+ Battery_Data.PLUG_ADAPTOR + " integer default 0,"
					+ Battery_Data.HEALTH + " integer default 0,"
					+ Battery_Data.TECHNOLOGY + " text default '',"
					+ "UNIQUE (" + Battery_Data.TIMESTAMP + ","
					+ Battery_Data.DEVICE_ID + ")",
			// battery discharges
			Battery_Discharges._ID + " integer primary key autoincrement,"
					+ Battery_Discharges.TIMESTAMP + " real default 0,"
					+ Battery_Discharges.DEVICE_ID + " text default '',"
					+ Battery_Discharges.BATTERY_START + " integer default 0,"
					+ Battery_Discharges.BATTERY_END + " integer default 0,"
					+ Battery_Discharges.END_TIMESTAMP + " real default 0,"
					+ "UNIQUE (" + Battery_Discharges.TIMESTAMP + ","
					+ Battery_Discharges.DEVICE_ID + ")",
			// battery charges
			Battery_Charges._ID + " integer primary key autoincrement,"
					+ Battery_Charges.TIMESTAMP + " real default 0,"
					+ Battery_Charges.DEVICE_ID + " text default '',"
					+ Battery_Charges.BATTERY_START + " integer default 0,"
					+ Battery_Charges.BATTERY_END + " integer default 0,"
					+ Battery_Charges.END_TIMESTAMP + " real default 0,"
					+ "UNIQUE (" + Battery_Charges.TIMESTAMP + ","
					+ Battery_Charges.DEVICE_ID + ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> batteryProjectionMap = null;
	private static HashMap<String, String> batteryDischargesMap = null;
	private static HashMap<String, String> batteryChargesMap = null;
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
		case BATTERY:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case BATTERY_DISCHARGE:
			count = database.delete(DATABASE_TABLES[1], selection,
					selectionArgs);
			break;
		case BATTERY_CHARGE:
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
		case BATTERY:
			return Battery_Data.CONTENT_TYPE;
		case BATTERY_ID:
			return Battery_Data.CONTENT_ITEM_TYPE;
		case BATTERY_DISCHARGE:
			return Battery_Discharges.CONTENT_TYPE;
		case BATTERY_DISCHARGE_ID:
			return Battery_Discharges.CONTENT_ITEM_TYPE;
		case BATTERY_CHARGE:
			return Battery_Charges.CONTENT_TYPE;
		case BATTERY_CHARGE_ID:
			return Battery_Charges.CONTENT_ITEM_TYPE;
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
		case BATTERY:
			long battery_id = database.insertWithOnConflict(DATABASE_TABLES[0], Battery_Data.TECHNOLOGY, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (battery_id > 0) {
				Uri batteryUri = ContentUris.withAppendedId(
						Battery_Data.CONTENT_URI, battery_id);
				getContext().getContentResolver()
						.notifyChange(batteryUri, null);
				return batteryUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case BATTERY_DISCHARGE:
			long battery_d_id = database.insertWithOnConflict(DATABASE_TABLES[1], Battery_Discharges.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (battery_d_id > 0) {
				Uri batteryUri = ContentUris.withAppendedId(
						Battery_Discharges.CONTENT_URI, battery_d_id);
				getContext().getContentResolver()
						.notifyChange(batteryUri, null);
				return batteryUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case BATTERY_CHARGE:
			long battery_c_id = database.insertWithOnConflict(DATABASE_TABLES[2],
					Battery_Charges.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (battery_c_id > 0) {
				Uri batteryUri = ContentUris.withAppendedId(
						Battery_Charges.CONTENT_URI, battery_c_id);
				getContext().getContentResolver()
						.notifyChange(batteryUri, null);
				return batteryUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.battery";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[0],
                BATTERY);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", BATTERY_ID);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[1],
                BATTERY_DISCHARGE);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", BATTERY_DISCHARGE_ID);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[2],
                BATTERY_CHARGE);
        sUriMatcher.addURI(Battery_Provider.AUTHORITY, DATABASE_TABLES[2]
                + "/#", BATTERY_CHARGE_ID);

        batteryProjectionMap = new HashMap<String, String>();
        batteryProjectionMap.put(Battery_Data._ID, Battery_Data._ID);
        batteryProjectionMap
                .put(Battery_Data.TIMESTAMP, Battery_Data.TIMESTAMP);
        batteryProjectionMap
                .put(Battery_Data.DEVICE_ID, Battery_Data.DEVICE_ID);
        batteryProjectionMap.put(Battery_Data.STATUS, Battery_Data.STATUS);
        batteryProjectionMap.put(Battery_Data.LEVEL, Battery_Data.LEVEL);
        batteryProjectionMap.put(Battery_Data.SCALE, Battery_Data.SCALE);
        batteryProjectionMap.put(Battery_Data.VOLTAGE, Battery_Data.VOLTAGE);
        batteryProjectionMap.put(Battery_Data.TEMPERATURE,
                Battery_Data.TEMPERATURE);
        batteryProjectionMap.put(Battery_Data.PLUG_ADAPTOR,
                Battery_Data.PLUG_ADAPTOR);
        batteryProjectionMap.put(Battery_Data.HEALTH, Battery_Data.HEALTH);
        batteryProjectionMap.put(Battery_Data.TECHNOLOGY,
                Battery_Data.TECHNOLOGY);

        batteryDischargesMap = new HashMap<String, String>();
        batteryDischargesMap
                .put(Battery_Discharges._ID, Battery_Discharges._ID);
        batteryDischargesMap.put(Battery_Discharges.TIMESTAMP,
                Battery_Discharges.TIMESTAMP);
        batteryDischargesMap.put(Battery_Discharges.DEVICE_ID,
                Battery_Discharges.DEVICE_ID);
        batteryDischargesMap.put(Battery_Discharges.BATTERY_START,
                Battery_Discharges.BATTERY_START);
        batteryDischargesMap.put(Battery_Discharges.BATTERY_END,
                Battery_Discharges.BATTERY_END);
        batteryDischargesMap.put(Battery_Discharges.END_TIMESTAMP,
                Battery_Discharges.END_TIMESTAMP);

        batteryChargesMap = new HashMap<String, String>();
        batteryChargesMap.put(Battery_Charges._ID, Battery_Charges._ID);
        batteryChargesMap.put(Battery_Charges.TIMESTAMP,
                Battery_Charges.TIMESTAMP);
        batteryChargesMap.put(Battery_Charges.DEVICE_ID,
                Battery_Charges.DEVICE_ID);
        batteryChargesMap.put(Battery_Charges.BATTERY_START,
                Battery_Charges.BATTERY_START);
        batteryChargesMap.put(Battery_Charges.BATTERY_END,
                Battery_Charges.BATTERY_END);
        batteryChargesMap.put(Battery_Charges.END_TIMESTAMP,
                Battery_Charges.END_TIMESTAMP);
	    
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
		case BATTERY:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(batteryProjectionMap);
			break;
		case BATTERY_DISCHARGE:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(batteryDischargesMap);
			break;
		case BATTERY_CHARGE:
			qb.setTables(DATABASE_TABLES[2]);
			qb.setProjectionMap(batteryChargesMap);
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

		int count;
		switch (sUriMatcher.match(uri)) {
		case BATTERY:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case BATTERY_DISCHARGE:
			count = database.update(DATABASE_TABLES[1], values, selection,
					selectionArgs);
			break;
		case BATTERY_CHARGE:
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
