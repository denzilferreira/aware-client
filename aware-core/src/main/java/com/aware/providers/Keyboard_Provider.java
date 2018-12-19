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
* Created by denzil on 21/10/14.
*/
public class Keyboard_Provider extends ContentProvider {
    private static final int DATABASE_VERSION = 2;

    /**
     * Authority of Installations content provider
     */
    public static String AUTHORITY = "com.aware.provider.keyboard";

    // ContentProvider query paths
    private static final int KEYBOARD = 1;
    private static final int KEYBOARD_ID = 2;

    /**
     * Content Provider definition
     * @author denzil
     *
     */
    public static final class Keyboard_Data implements BaseColumns {
        private Keyboard_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Keyboard_Provider.AUTHORITY + "/keyboard");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.keyboard";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.keyboard";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String BEFORE_TEXT = "before_text";
        public static final String CURRENT_TEXT = "current_text";
        public static final String IS_PASSWORD = "is_password";
    }

    public static String DATABASE_NAME = "keyboard.db";
    public static final String[] DATABASE_TABLES = { "keyboard" };

    public static final String[] TABLES_FIELDS = {
            Keyboard_Data._ID + " integer primary key autoincrement,"
                    + Keyboard_Data.TIMESTAMP + " real default 0,"
                    + Keyboard_Data.DEVICE_ID + " text default '',"
                    + Keyboard_Data.PACKAGE_NAME + " text default '',"
                    + Keyboard_Data.BEFORE_TEXT + " text default '',"
                    + Keyboard_Data.CURRENT_TEXT + " text default '',"
                    + Keyboard_Data.IS_PASSWORD + " integer default -1" };

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

        //lock database for transaction
        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case KEYBOARD:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
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
            case KEYBOARD:
                return Keyboard_Data.CONTENT_TYPE;
            case KEYBOARD_ID:
                return Keyboard_Data.CONTENT_ITEM_TYPE;
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
            case KEYBOARD:
                long keyboard_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Keyboard_Data.PACKAGE_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (keyboard_id > 0) {
                    Uri installationsUri = ContentUris.withAppendedId(
                            Keyboard_Data.CONTENT_URI, keyboard_id);
                    getContext().getContentResolver().notifyChange(installationsUri, null, false);
                    return installationsUri;
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
        AUTHORITY = context.getPackageName() + ".provider.keyboard";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.keyboard";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Keyboard_Provider.AUTHORITY,
                DATABASE_TABLES[0], KEYBOARD);
        sUriMatcher.addURI(Installations_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", KEYBOARD_ID);

        dataMap = new HashMap<String, String>();
        dataMap.put(Keyboard_Data._ID, Keyboard_Data._ID);
        dataMap.put(Keyboard_Data.TIMESTAMP,
                Keyboard_Data.TIMESTAMP);
        dataMap.put(Keyboard_Data.DEVICE_ID,
                Keyboard_Data.DEVICE_ID);
        dataMap.put(Keyboard_Data.PACKAGE_NAME,
                Keyboard_Data.PACKAGE_NAME);
        dataMap.put(Keyboard_Data.BEFORE_TEXT,
                Keyboard_Data.BEFORE_TEXT);
        dataMap.put(Keyboard_Data.CURRENT_TEXT,
                Keyboard_Data.CURRENT_TEXT);
        dataMap.put(Keyboard_Data.IS_PASSWORD,
                Keyboard_Data.IS_PASSWORD);

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
        qb.setStrict(true);
        switch (sUriMatcher.match(uri)) {
            case KEYBOARD:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(dataMap);
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
    public synchronized int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case KEYBOARD:
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
