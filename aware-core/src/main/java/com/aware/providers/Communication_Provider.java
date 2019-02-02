
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
 * AWARE Communication Content Provider Allows you to access all the recorded
 * communication events on the database Database is located at the SDCard :
 * /AWARE/communication.db
 *
 * @author denzil
 */
public class Communication_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 3;

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
     */
    public static final class Calls_Data implements BaseColumns {
        private Calls_Data() {
        }

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
     */
    public static final class Messages_Data implements BaseColumns {
        private Messages_Data() {
        }

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

    public static String DATABASE_NAME = "communication.db";

    public static final String[] DATABASE_TABLES = {"calls", "messages"};

    public static final String[] TABLES_FIELDS = {
            // calls
            "_id integer primary key autoincrement,"
                    + "timestamp real default 0,"
                    + "device_id text default '',"
                    + "call_type integer default 0,"
                    + "call_duration integer default 0,"
                    + "trace text default ''",
            // messages
            "_id integer primary key autoincrement,"
                    + "timestamp real default 0,"
                    + "device_id text default '',"
                    + "message_type integer default 0,"
                    + "trace text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> callsProjectionMap = null;
    private HashMap<String, String> messageProjectionMap = null;

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
            case CALLS:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case MESSAGES:
                count = database.delete(DATABASE_TABLES[1], selection,
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
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case CALLS:
                long call_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Calls_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (call_id > 0) {
                    Uri callsUri = ContentUris.withAppendedId(
                            Calls_Data.CONTENT_URI, call_id);
                    getContext().getContentResolver().notifyChange(callsUri, null, false);
                    return callsUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case MESSAGES:
                long message_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Messages_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (message_id > 0) {
                    Uri messagesUri = ContentUris.withAppendedId(
                            Messages_Data.CONTENT_URI, message_id);
                    getContext().getContentResolver().notifyChange(messagesUri, null, false);
                    return messagesUri;
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
        AUTHORITY = context.getPackageName() + ".provider.communication";
        return AUTHORITY;
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

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
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
    public synchronized int update(Uri uri, ContentValues values, String selection,
                                   String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
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
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
}
