
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
 * AWARE Screen Content Provider Allows you to access all the recorded screen
 * events on the database Database is located at the SDCard : /AWARE/screen.db
 *
 * @author denzil
 */
public class Screen_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 5;

    /**
     * Authority of Screen content provider
     */
    public static String AUTHORITY = "com.aware.provider.screen";

    // ContentProvider query paths
    private static final int SCREEN = 1;
    private static final int SCREEN_ID = 2;
    private static final int TOUCH = 3;
    private static final int TOUCH_ID = 4;

    /**
     * Screen content representation
     *
     * @author denzil
     */
    public static final class Screen_Data implements BaseColumns {
        private Screen_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Screen_Provider.AUTHORITY + "/screen");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.screen";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.screen";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String SCREEN_STATUS = "screen_status";
    }

    public static final class Screen_Touch implements BaseColumns {
        private Screen_Touch() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Screen_Provider.AUTHORITY + "/touch");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.touch";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.touch";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String TOUCH_APP = "touch_app";
        public static final String TOUCH_ACTION = "touch_action";
        public static final String TOUCH_ACTION_TEXT = "touch_action_text";
        public static final String TOUCH_INDEX_ITEMS = "scroll_items";
        public static final String TOUCH_FROM_INDEX = "scroll_from_index";
        public static final String TOUCH_TO_INDEX = "scroll_to_index";
    }

    public static String DATABASE_NAME = "screen.db";
    public static final String[] DATABASE_TABLES = {"screen", "touch"};

    public static final String[] TABLES_FIELDS = {
            // screen
            Screen_Data._ID + " integer primary key autoincrement,"
                    + Screen_Data.TIMESTAMP + " real default 0,"
                    + Screen_Data.DEVICE_ID + " text default '',"
                    + Screen_Data.SCREEN_STATUS + " integer default 0",
            // touch
            Screen_Touch._ID + " integer primary key autoincrement,"
                    + Screen_Touch.TIMESTAMP + " real default 0,"
                    + Screen_Touch.DEVICE_ID + " text default '',"
                    + Screen_Touch.TOUCH_APP + " text default '',"
                    + Screen_Touch.TOUCH_ACTION + " text default '',"
                    + Screen_Touch.TOUCH_ACTION_TEXT + " text default '',"
                    + Screen_Touch.TOUCH_INDEX_ITEMS + " integer default -1,"
                    + Screen_Touch.TOUCH_FROM_INDEX + " integer default -1,"
                    + Screen_Touch.TOUCH_TO_INDEX + " integer default -1"
    };

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> screenProjectionMap = null;
    private HashMap<String, String> touchProjectionMap = null;

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

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SCREEN:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case TOUCH:
                count = database.delete(DATABASE_TABLES[1], selection, selectionArgs);
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
            case SCREEN:
                return Screen_Data.CONTENT_TYPE;
            case SCREEN_ID:
                return Screen_Data.CONTENT_ITEM_TYPE;
            case TOUCH:
                return Screen_Touch.CONTENT_TYPE;
            case TOUCH_ID:
                return Screen_Touch.CONTENT_ITEM_TYPE;
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
            case SCREEN:
                long screen_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Screen_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (screen_id > 0) {
                    Uri screenUri = ContentUris.withAppendedId(
                            Screen_Data.CONTENT_URI, screen_id);
                    getContext().getContentResolver().notifyChange(screenUri, null, false);
                    return screenUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case TOUCH:
                long touch_id = database.insertWithOnConflict(DATABASE_TABLES[1], Screen_Touch.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (touch_id > 0) {
                    Uri screenUri = ContentUris.withAppendedId(
                            Screen_Touch.CONTENT_URI, touch_id);
                    getContext().getContentResolver().notifyChange(screenUri, null, false);
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
     *
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.screen";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.screen";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Screen_Provider.AUTHORITY, DATABASE_TABLES[0], SCREEN);
        sUriMatcher.addURI(Screen_Provider.AUTHORITY,DATABASE_TABLES[0] + "/#", SCREEN_ID);
        sUriMatcher.addURI(Screen_Provider.AUTHORITY, DATABASE_TABLES[1], TOUCH);
        sUriMatcher.addURI(Screen_Provider.AUTHORITY, DATABASE_TABLES[1]+ "/#", TOUCH_ID);

        screenProjectionMap = new HashMap<>();
        screenProjectionMap.put(Screen_Data._ID, Screen_Data._ID);
        screenProjectionMap.put(Screen_Data.TIMESTAMP, Screen_Data.TIMESTAMP);
        screenProjectionMap.put(Screen_Data.DEVICE_ID, Screen_Data.DEVICE_ID);
        screenProjectionMap.put(Screen_Data.SCREEN_STATUS, Screen_Data.SCREEN_STATUS);

        touchProjectionMap = new HashMap<>();
        touchProjectionMap.put(Screen_Touch._ID, Screen_Touch._ID);
        touchProjectionMap.put(Screen_Touch.TIMESTAMP, Screen_Touch.TIMESTAMP);
        touchProjectionMap.put(Screen_Touch.DEVICE_ID, Screen_Touch.DEVICE_ID);
        touchProjectionMap.put(Screen_Touch.TOUCH_APP, Screen_Touch.TOUCH_APP);
        touchProjectionMap.put(Screen_Touch.TOUCH_ACTION, Screen_Touch.TOUCH_ACTION);
        touchProjectionMap.put(Screen_Touch.TOUCH_ACTION_TEXT, Screen_Touch.TOUCH_ACTION_TEXT);
        touchProjectionMap.put(Screen_Touch.TOUCH_INDEX_ITEMS, Screen_Touch.TOUCH_INDEX_ITEMS);
        touchProjectionMap.put(Screen_Touch.TOUCH_FROM_INDEX, Screen_Touch.TOUCH_FROM_INDEX);
        touchProjectionMap.put(Screen_Touch.TOUCH_TO_INDEX, Screen_Touch.TOUCH_TO_INDEX);

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
            case SCREEN:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(screenProjectionMap);
                break;
            case TOUCH:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(touchProjectionMap);
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

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SCREEN:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case TOUCH:
                count = database.update(DATABASE_TABLES[1], values, selection,
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
}