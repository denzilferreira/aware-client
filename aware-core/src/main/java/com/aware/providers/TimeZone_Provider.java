
package com.aware.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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
import com.aware.BuildConfig;
import com.aware.utils.DatabaseHelper;

import java.io.File;
import java.util.HashMap;

/**
 * AWARE Timezone Content Provider Allows you to access recorded timezone changes from the database Database is located at the SDCard:
 * /AWARE/timezone.db
 *
 * @author Nikola
 */
public class TimeZone_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 7;

    /**
     * Provider authority: com.aware.TimeZoneProvider
     */
    public static String AUTHORITY = "com.aware.provider.timezone";

    private static final int TIMEZONE = 1;
    private static final int TIMEZONE_ID = 2;

    /**
     * Timezone data representation
     *
     * @author Nikola
     */
    public static final class TimeZone_Data implements BaseColumns {
        private TimeZone_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + TimeZone_Provider.AUTHORITY + "/timezone");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.timezone";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.timezone";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String TIMEZONE = "timezone";
    }

    public static String DATABASE_NAME = "timezone.db";

    public static final String[] DATABASE_TABLES = {"timezone"};

    public static final String[] TABLES_FIELDS = {
            // timezone
            TimeZone_Data._ID + " integer primary key autoincrement,"
                    + TimeZone_Data.TIMESTAMP + " real default 0,"
                    + TimeZone_Data.DEVICE_ID + " text default '',"
                    + TimeZone_Data.TIMEZONE + " text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> timeZoneMap = null;

    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    /**
     * Delete entry from the database
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        initialiseDatabase();

        //lock database for transaction
        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case TIMEZONE:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case TIMEZONE:
                return TimeZone_Data.CONTENT_TYPE;
            case TIMEZONE_ID:
                return TimeZone_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Insert entry to the database
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case TIMEZONE:
                long timezone_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        TimeZone_Data.TIMEZONE, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (timezone_id > 0) {
                    Uri tele_uri = ContentUris.withAppendedId(
                            TimeZone_Data.CONTENT_URI, timezone_id);
                    getContext().getContentResolver().notifyChange(tele_uri, null);
                    return tele_uri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.timezone";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(TimeZone_Provider.AUTHORITY, DATABASE_TABLES[0],
                TIMEZONE);
        sUriMatcher.addURI(TimeZone_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", TIMEZONE_ID);

        timeZoneMap = new HashMap<String, String>();
        timeZoneMap.put(TimeZone_Data._ID, TimeZone_Data._ID);
        timeZoneMap.put(TimeZone_Data.TIMESTAMP, TimeZone_Data.TIMESTAMP);
        timeZoneMap.put(TimeZone_Data.DEVICE_ID, TimeZone_Data.DEVICE_ID);
        timeZoneMap.put(TimeZone_Data.TIMEZONE, TimeZone_Data.TIMEZONE);

        return true;
    }

    /**
     * Query entries from the database
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {
            case TIMEZONE:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(timeZoneMap);
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

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case TIMEZONE:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}