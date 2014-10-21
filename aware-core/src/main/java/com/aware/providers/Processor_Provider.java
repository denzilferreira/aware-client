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
 * AWARE Processor Content Provider logs processor activity Database is located
 * at the SDCard : /AWARE/processor.db
 * 
 * @author denzil
 */
public class Processor_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 3;

	/**
	 * Authority of Processor content provider
	 */
	public static String AUTHORITY = "com.aware.provider.processor";

	// ContentProvider query paths
	private static final int PROCESSOR = 1;
	private static final int PROCESSOR_ID = 2;

	/**
	 * Processor content representation
	 * 
	 * @author denzil
	 */
	public static final class Processor_Data implements BaseColumns {
		private Processor_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Processor_Provider.AUTHORITY + "/processor");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.processor";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.processor";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String LAST_USER = "double_last_user";
		public static final String LAST_SYSTEM = "double_last_system";
		public static final String LAST_IDLE = "double_last_idle";
		public static final String USER_LOAD = "double_user_load";
		public static final String SYSTEM_LOAD = "double_system_load";
		public static final String IDLE_LOAD = "double_idle_load";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "processor.db";
	public static final String[] DATABASE_TABLES = { "processor" };

	public static final String[] TABLES_FIELDS = {
	// processor
	Processor_Data._ID + " integer primary key autoincrement,"
			+ Processor_Data.TIMESTAMP + " real default 0,"
			+ Processor_Data.DEVICE_ID + " text default '',"
			+ Processor_Data.LAST_USER + " real default 0,"
			+ Processor_Data.LAST_SYSTEM + " real default 0,"
			+ Processor_Data.LAST_IDLE + " real default 0,"
			+ Processor_Data.USER_LOAD + " real default 0,"
			+ Processor_Data.SYSTEM_LOAD + " real default 0,"
			+ Processor_Data.IDLE_LOAD + " real default 0," + "UNIQUE("
			+ Processor_Data.TIMESTAMP + "," + Processor_Data.DEVICE_ID + ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> processorProjectionMap = null;
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
		case PROCESSOR:
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
		case PROCESSOR:
			return Processor_Data.CONTENT_TYPE;
		case PROCESSOR_ID:
			return Processor_Data.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	/**
	 * Insert entry to the database
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		if (database == null || !database.isOpen())
			database = databaseHelper.getWritableDatabase();

		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();

		switch (sUriMatcher.match(uri)) {
		case PROCESSOR:
			long processor_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Processor_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (processor_id > 0) {
				Uri processorUri = ContentUris.withAppendedId(
						Processor_Data.CONTENT_URI, processor_id);
				getContext().getContentResolver().notifyChange(processorUri,
						null);
				return processorUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
		AUTHORITY = getContext().getPackageName() + ".provider.processor";
		
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Processor_Provider.AUTHORITY, DATABASE_TABLES[0],
                PROCESSOR);
        sUriMatcher.addURI(Processor_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", PROCESSOR_ID);

        processorProjectionMap = new HashMap<String, String>();
        processorProjectionMap.put(Processor_Data._ID, Processor_Data._ID);
        processorProjectionMap.put(Processor_Data.TIMESTAMP,
                Processor_Data.TIMESTAMP);
        processorProjectionMap.put(Processor_Data.DEVICE_ID,
                Processor_Data.DEVICE_ID);
        processorProjectionMap.put(Processor_Data.LAST_USER,
                Processor_Data.LAST_USER);
        processorProjectionMap.put(Processor_Data.LAST_SYSTEM,
                Processor_Data.LAST_SYSTEM);
        processorProjectionMap.put(Processor_Data.LAST_IDLE,
                Processor_Data.LAST_IDLE);
        processorProjectionMap.put(Processor_Data.USER_LOAD,
                Processor_Data.USER_LOAD);
        processorProjectionMap.put(Processor_Data.SYSTEM_LOAD,
                Processor_Data.SYSTEM_LOAD);
        processorProjectionMap.put(Processor_Data.IDLE_LOAD,
                Processor_Data.IDLE_LOAD);
		
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
		case PROCESSOR:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(processorProjectionMap);
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
		case PROCESSOR:
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
