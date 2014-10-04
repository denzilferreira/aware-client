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
 * AWARE Locations Content Provider Allows you to access all the recorded
 * locations on the database Database is located at the SDCard :
 * /AWARE/locations.db
 * 
 * @author denzil
 * 
 */
public class Locations_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 2;

	/**
	 * Authority of Locations content provider
	 */
	public static String AUTHORITY = "com.aware.provider.locations";

	// ContentProvider query paths
	private static final int LOCATIONS = 1;
	private static final int LOCATIONS_ID = 2;

	/**
	 * Locations content representation
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Locations_Data implements BaseColumns {
		private Locations_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Locations_Provider.AUTHORITY + "/locations");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.locations";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.locations";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String LATITUDE = "double_latitude";
		public static final String LONGITUDE = "double_longitude";
		public static final String BEARING = "double_bearing";
		public static final String SPEED = "double_speed";
		public static final String ALTITUDE = "double_altitude";
		public static final String PROVIDER = "provider";
		public static final String ACCURACY = "accuracy";
		public static final String LABEL = "label";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "locations.db";

	public static final String[] DATABASE_TABLES = { "locations" };

	public static final String[] TABLES_FIELDS = { Locations_Data._ID
			+ " integer primary key autoincrement," + Locations_Data.TIMESTAMP
			+ " real default 0," + Locations_Data.DEVICE_ID
			+ " text default ''," + Locations_Data.LATITUDE
			+ " real default 0," + Locations_Data.LONGITUDE
			+ " real default 0," + Locations_Data.BEARING + " real default 0,"
			+ Locations_Data.SPEED + " real default 0,"
			+ Locations_Data.ALTITUDE + " real default 0,"
			+ Locations_Data.PROVIDER + " text default '',"
			+ Locations_Data.ACCURACY + " real default 0,"
			+ Locations_Data.LABEL + " text default ''," + "UNIQUE("
			+ Locations_Data.TIMESTAMP + "," + Locations_Data.DEVICE_ID + ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> locationsProjectionMap = null;
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
		case LOCATIONS:
			count = database.delete(DATABASE_TABLES[0], selection,
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
		case LOCATIONS:
			return Locations_Data.CONTENT_TYPE;
		case LOCATIONS_ID:
			return Locations_Data.CONTENT_ITEM_TYPE;
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
		case LOCATIONS:
			long location_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Locations_Data.PROVIDER, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (location_id > 0) {
				Uri locationUri = ContentUris.withAppendedId(
						Locations_Data.CONTENT_URI, location_id);
				getContext().getContentResolver().notifyChange(locationUri,
						null);
				return locationUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.locations";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Locations_Provider.AUTHORITY, DATABASE_TABLES[0],
                LOCATIONS);

        locationsProjectionMap = new HashMap<String, String>();
        locationsProjectionMap.put(Locations_Data._ID, Locations_Data._ID);
        locationsProjectionMap.put(Locations_Data.TIMESTAMP,
                Locations_Data.TIMESTAMP);
        locationsProjectionMap.put(Locations_Data.DEVICE_ID,
                Locations_Data.DEVICE_ID);
        locationsProjectionMap.put(Locations_Data.LATITUDE,
                Locations_Data.LATITUDE);
        locationsProjectionMap.put(Locations_Data.LONGITUDE,
                Locations_Data.LONGITUDE);
        locationsProjectionMap.put(Locations_Data.BEARING,
                Locations_Data.BEARING);
        locationsProjectionMap.put(Locations_Data.SPEED, Locations_Data.SPEED);
        locationsProjectionMap.put(Locations_Data.ALTITUDE,
                Locations_Data.ALTITUDE);
        locationsProjectionMap.put(Locations_Data.PROVIDER,
                Locations_Data.PROVIDER);
        locationsProjectionMap.put(Locations_Data.ACCURACY,
                Locations_Data.ACCURACY);
        locationsProjectionMap.put(Locations_Data.LABEL, Locations_Data.LABEL);
	    
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
		case LOCATIONS:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(locationsProjectionMap);
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
	 * Update entry on the database
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
		case LOCATIONS:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
}