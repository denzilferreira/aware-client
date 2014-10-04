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
 * AWARE Communication Content Provider Allows you to access all the recorded
 * communication events on the database Database is located at the SDCard :
 * /AWARE/communication.db
 * 
 * @author denzil
 * 
 */
public class Communication_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 2;

	/**
	 * Authority of Screen content provider
	 */
	public static String AUTHORITY = "com.aware.provider.communication";

	// ContentProvider query paths
	private static final int CALLS = 1;
	private static final int CALLS_ID = 2;
	private static final int MESSAGES = 3;
	private static final int MESSAGES_ID = 4;

	/**
	 * Calls content representation
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Calls_Data implements BaseColumns {
		private Calls_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Communication_Provider.AUTHORITY + "/calls");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.calls";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.calls";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String TYPE = "call_type";
		public static final String DURATION = "call_duration";
		public static final String TRACE = "trace";
	}

	/**
	 * Messages content representation
	 * 
	 * @author denzil
	 * 
	 */
	public static final class Messages_Data implements BaseColumns {
		private Messages_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Communication_Provider.AUTHORITY + "/messages");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.messages";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.messages";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String TYPE = "message_type";
		public static final String TRACE = "trace";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "communication.db";

	public static final String[] DATABASE_TABLES = { "calls", "messages" };

	public static final String[] TABLES_FIELDS = {
			// calls
			"_id integer primary key autoincrement,"
					+ "timestamp real default 0,"
					+ "device_id text default '',"
					+ "call_type integer default 0,"
					+ "call_duration integer default 0,"
					+ "trace text default ''," + "UNIQUE ("
					+ Calls_Data.TIMESTAMP + "," + Calls_Data.DEVICE_ID + ")",
			// messages
			"_id integer primary key autoincrement,"
					+ "timestamp real default 0,"
					+ "device_id text default '',"
					+ "message_type integer default 0,"
					+ "trace text default ''," + "UNIQUE ("
					+ Messages_Data.TIMESTAMP + "," + Messages_Data.DEVICE_ID
					+ ")" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> callsProjectionMap = null;
	private static HashMap<String, String> messageProjectionMap = null;
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
		case CALLS:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case MESSAGES:
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
		case CALLS:
			return Calls_Data.CONTENT_TYPE;
		case CALLS_ID:
			return Calls_Data.CONTENT_ITEM_TYPE;
		case MESSAGES:
			return Messages_Data.CONTENT_TYPE;
		case MESSAGES_ID:
			return Messages_Data.CONTENT_ITEM_TYPE;
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
		case CALLS:
			long call_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Calls_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (call_id > 0) {
				Uri callsUri = ContentUris.withAppendedId(
						Calls_Data.CONTENT_URI, call_id);
				getContext().getContentResolver().notifyChange(callsUri, null);
				return callsUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		case MESSAGES:
			long message_id = database.insertWithOnConflict(DATABASE_TABLES[1],
					Messages_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (message_id > 0) {
				Uri messagesUri = ContentUris.withAppendedId(
						Messages_Data.CONTENT_URI, message_id);
				getContext().getContentResolver().notifyChange(messagesUri,
						null);
				return messagesUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:

			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.communication";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Communication_Provider.AUTHORITY,
                DATABASE_TABLES[0], CALLS);
        sUriMatcher.addURI(Communication_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", CALLS_ID);
        sUriMatcher.addURI(Communication_Provider.AUTHORITY,
                DATABASE_TABLES[1], MESSAGES);
        sUriMatcher.addURI(Communication_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", MESSAGES_ID);

        callsProjectionMap = new HashMap<String, String>();
        callsProjectionMap.put(Calls_Data._ID, Calls_Data._ID);
        callsProjectionMap.put(Calls_Data.TIMESTAMP, Calls_Data.TIMESTAMP);
        callsProjectionMap.put(Calls_Data.DEVICE_ID, Calls_Data.DEVICE_ID);
        callsProjectionMap.put(Calls_Data.TYPE, Calls_Data.TYPE);
        callsProjectionMap.put(Calls_Data.DURATION, Calls_Data.DURATION);
        callsProjectionMap.put(Calls_Data.TRACE, Calls_Data.TRACE);

        messageProjectionMap = new HashMap<String, String>();
        messageProjectionMap.put(Messages_Data._ID, Messages_Data._ID);
        messageProjectionMap.put(Messages_Data.TIMESTAMP,
                Messages_Data.TIMESTAMP);
        messageProjectionMap.put(Messages_Data.DEVICE_ID,
                Messages_Data.DEVICE_ID);
        messageProjectionMap.put(Messages_Data.TYPE, Messages_Data.TYPE);
        messageProjectionMap.put(Messages_Data.TRACE, Messages_Data.TRACE);
	    
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
		case CALLS:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(callsProjectionMap);
			break;
		case MESSAGES:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(messageProjectionMap);
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
		case CALLS:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case MESSAGES:
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
