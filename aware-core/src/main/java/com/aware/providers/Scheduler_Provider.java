
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
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * Scheduler Provider: keeps a record of scheduled tasks that need to be performed on triggered events
 */
public class Scheduler_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 3;

    /**
     * Authority of Scheduler content provider
     */
    public static String AUTHORITY = "com.aware.provider.scheduler";

    // ContentProvider query paths
    private final int SCHEDULER = 1;
    private final int SCHEDULER_ID = 2;

    public static final class Scheduler_Data implements BaseColumns {
        private Scheduler_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Scheduler_Provider.AUTHORITY + "/scheduler");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.scheduler";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.scheduler";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String SCHEDULE_ID = "schedule_id";
        public static final String SCHEDULE = "schedule";
        public static final String LAST_TRIGGERED = "last_triggered";
        public static final String PACKAGE_NAME = "package_name";
    }

    public static String DATABASE_NAME = "scheduler.db";
    public static final String[] DATABASE_TABLES = {"scheduler"};

    public static final String[] TABLES_FIELDS = {
            Scheduler_Data._ID + " integer primary key autoincrement,"
                    + Scheduler_Data.TIMESTAMP + " real default 0,"
                    + Scheduler_Data.DEVICE_ID + " text default '',"
                    + Scheduler_Data.SCHEDULE_ID + " text default '',"
                    + Scheduler_Data.SCHEDULE + " text default '',"
                    + Scheduler_Data.LAST_TRIGGERED + " real default 0,"
                    + Scheduler_Data.PACKAGE_NAME + " text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> dataMap = null;

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
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case SCHEDULER:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null, false);

        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case SCHEDULER:
                return Scheduler_Data.CONTENT_TYPE;
            case SCHEDULER_ID:
                return Scheduler_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Insert entry to the database
     */
    @Override
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case SCHEDULER:
                long screen_id = database.insertWithOnConflict(DATABASE_TABLES[0], Scheduler_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (screen_id > 0) {
                    Uri screenUri = ContentUris.withAppendedId(Scheduler_Data.CONTENT_URI, screen_id);
                    getContext().getContentResolver().notifyChange(screenUri, null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return screenUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.scheduler";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.scheduler";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Scheduler_Provider.AUTHORITY, DATABASE_TABLES[0], SCHEDULER);
        sUriMatcher.addURI(Scheduler_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#", SCHEDULER_ID);

        dataMap = new HashMap<>();
        dataMap.put(Scheduler_Data._ID, Scheduler_Data._ID);
        dataMap.put(Scheduler_Data.TIMESTAMP, Scheduler_Data.TIMESTAMP);
        dataMap.put(Scheduler_Data.DEVICE_ID, Scheduler_Data.DEVICE_ID);
        dataMap.put(Scheduler_Data.SCHEDULE_ID, Scheduler_Data.SCHEDULE_ID);
        dataMap.put(Scheduler_Data.SCHEDULE, Scheduler_Data.SCHEDULE);
        dataMap.put(Scheduler_Data.LAST_TRIGGERED, Scheduler_Data.LAST_TRIGGERED);
        dataMap.put(Scheduler_Data.PACKAGE_NAME, Scheduler_Data.PACKAGE_NAME);

        return true;
    }

    /**
     * Query entries from the database
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        switch (sUriMatcher.match(uri)) {
            case SCHEDULER:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(dataMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG) Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    /**
     * Update application on the database
     */
    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case SCHEDULER:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null, false);

        return count;
    }
}