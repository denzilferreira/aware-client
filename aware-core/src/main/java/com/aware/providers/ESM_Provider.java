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
 * ESM Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/esm.db
 * 
 * @author denzil
 * 
 */
public class ESM_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 2;

	/**
	 * Authority of content provider
	 */
	public static String AUTHORITY = "com.aware.provider.esm";

	// ContentProvider query paths
	private static final int ESMS_QUEUE = 1;
	private static final int ESMS_QUEUE_ID = 2;

	/**
	 * ESM questions
	 * 
	 * @author df
	 * 
	 */
	public static final class ESM_Data implements BaseColumns {
		private ESM_Data() {
		};

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ ESM_Provider.AUTHORITY + "/esms");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.esms";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.esms";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String TYPE = "esm_type";
		public static final String TITLE = "esm_title";
		public static final String SUBMIT = "esm_submit";
		public static final String INSTRUCTIONS = "esm_instructions";
		public static final String RADIOS = "esm_radios";
		public static final String CHECKBOXES = "esm_checkboxes";
		public static final String LIKERT_MAX = "esm_likert_max";
		public static final String LIKERT_MAX_LABEL = "esm_likert_max_label";
		public static final String LIKERT_MIN_LABEL = "esm_likert_min_label";
		public static final String LIKERT_STEP = "esm_likert_step";
		public static final String QUICK_ANSWERS = "esm_quick_answers";
		public static final String EXPIRATION_THREASHOLD = "esm_expiration_threashold";
		public static final String STATUS = "esm_status";
		public static final String ANSWER_TIMESTAMP = "double_esm_user_answer_timestamp";
		public static final String ANSWER = "esm_user_answer";
		public static final String TRIGGER = "esm_trigger";
	}

	public static String DATABASE_NAME = Environment
			.getExternalStorageDirectory() + "/AWARE/" + "esms.db";
	public static final String[] DATABASE_TABLES = { "esms" };
	public static final String[] TABLES_FIELDS = { ESM_Data._ID
			+ " integer primary key autoincrement," + ESM_Data.TIMESTAMP
			+ " real default 0," + ESM_Data.DEVICE_ID + " text default '',"
			+ ESM_Data.TYPE + " integer default 0," + ESM_Data.TITLE
			+ " text default ''," + ESM_Data.SUBMIT + " text default '',"
			+ ESM_Data.INSTRUCTIONS + " text default ''," + ESM_Data.RADIOS
			+ " text default ''," + ESM_Data.CHECKBOXES + " text default '',"
			+ ESM_Data.LIKERT_MAX + " integer default 0,"
			+ ESM_Data.LIKERT_MAX_LABEL + " text default '',"
			+ ESM_Data.LIKERT_MIN_LABEL + " text default '',"
			+ ESM_Data.LIKERT_STEP + " real default 0,"
			+ ESM_Data.QUICK_ANSWERS + " text default '',"
			+ ESM_Data.EXPIRATION_THREASHOLD + " integer default 0,"
			+ ESM_Data.STATUS + " integer default 0,"
			+ ESM_Data.ANSWER_TIMESTAMP + " real default 0," + ESM_Data.ANSWER
			+ " text default ''," + ESM_Data.TRIGGER + " text default ''" };

	private static UriMatcher sUriMatcher = null;
	private static HashMap<String, String> questionsMap = null;
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
		case ESMS_QUEUE:
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
		case ESMS_QUEUE:
			return ESM_Data.CONTENT_TYPE;
		case ESMS_QUEUE_ID:
			return ESM_Data.CONTENT_ITEM_TYPE;
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
		case ESMS_QUEUE:
			long quest_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					ESM_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

			if (quest_id > 0) {
				Uri questUri = ContentUris.withAppendedId(ESM_Data.CONTENT_URI,
						quest_id);
				getContext().getContentResolver().notifyChange(questUri, null);
				return questUri;
			}
			throw new SQLException("Failed to insert row into " + uri);
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.esm";
	    
	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ESM_Provider.AUTHORITY, DATABASE_TABLES[0],
                ESMS_QUEUE);
        sUriMatcher.addURI(ESM_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#",
                ESMS_QUEUE_ID);

        questionsMap = new HashMap<String, String>();
        questionsMap.put(ESM_Data._ID, ESM_Data._ID);
        questionsMap.put(ESM_Data.TIMESTAMP, ESM_Data.TIMESTAMP);
        questionsMap.put(ESM_Data.DEVICE_ID, ESM_Data.DEVICE_ID);
        questionsMap.put(ESM_Data.TYPE, ESM_Data.TYPE);
        questionsMap.put(ESM_Data.TITLE, ESM_Data.TITLE);
        questionsMap.put(ESM_Data.SUBMIT, ESM_Data.SUBMIT);
        questionsMap.put(ESM_Data.INSTRUCTIONS, ESM_Data.INSTRUCTIONS);
        questionsMap.put(ESM_Data.RADIOS, ESM_Data.RADIOS);
        questionsMap.put(ESM_Data.CHECKBOXES, ESM_Data.CHECKBOXES);
        questionsMap.put(ESM_Data.LIKERT_MAX, ESM_Data.LIKERT_MAX);
        questionsMap.put(ESM_Data.LIKERT_MAX_LABEL, ESM_Data.LIKERT_MAX_LABEL);
        questionsMap.put(ESM_Data.LIKERT_MIN_LABEL, ESM_Data.LIKERT_MIN_LABEL);
        questionsMap.put(ESM_Data.LIKERT_STEP, ESM_Data.LIKERT_STEP);
        questionsMap.put(ESM_Data.QUICK_ANSWERS, ESM_Data.QUICK_ANSWERS);
        questionsMap.put(ESM_Data.EXPIRATION_THREASHOLD,
                ESM_Data.EXPIRATION_THREASHOLD);
        questionsMap.put(ESM_Data.STATUS, ESM_Data.STATUS);
        questionsMap.put(ESM_Data.ANSWER_TIMESTAMP, ESM_Data.ANSWER_TIMESTAMP);
        questionsMap.put(ESM_Data.ANSWER, ESM_Data.ANSWER);
        questionsMap.put(ESM_Data.TRIGGER, ESM_Data.TRIGGER);
	    
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
		case ESMS_QUEUE:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(questionsMap);
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
		case ESMS_QUEUE:
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