
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
 * ESM Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/esm.db
 *
 * @author denzil
 */
public class ESM_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 8;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.esm";

    private static final int ESMS_QUEUE = 1;
    private static final int ESMS_QUEUE_ID = 2;

    /**
     * ESM questions
     *
     * @author df
     */
    public static final class ESM_Data implements BaseColumns {
        private ESM_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + ESM_Provider.AUTHORITY + "/esms");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.esms";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.esms";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String JSON = "esm_json";
        public static final String STATUS = "esm_status";
        public static final String EXPIRATION_THRESHOLD = "esm_expiration_threshold";
        public static final String NOTIFICATION_TIMEOUT = "esm_notification_timeout";
        public static final String ANSWER_TIMESTAMP = "double_esm_user_answer_timestamp";
        public static final String ANSWER = "esm_user_answer";
        public static final String TRIGGER = "esm_trigger";
    }

    public static String DATABASE_NAME = "esms.db";

    public static final String[] DATABASE_TABLES = {"esms"};
    public static final String[] TABLES_FIELDS = {
            ESM_Data._ID + " integer primary key autoincrement,"
                    + ESM_Data.TIMESTAMP + " real default 0,"
                    + ESM_Data.DEVICE_ID + " text default '',"
                    + ESM_Data.JSON + " text default '',"
                    + ESM_Data.STATUS + " integer default 0,"
                    + ESM_Data.EXPIRATION_THRESHOLD + " integer default 0,"
                    + ESM_Data.NOTIFICATION_TIMEOUT + " integer default 0,"
                    + ESM_Data.ANSWER_TIMESTAMP + " real default 0,"
                    + ESM_Data.ANSWER + " text default '',"
                    + ESM_Data.TRIGGER + " text default ''"
    };

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> questionsMap = null;

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
            case ESMS_QUEUE:
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
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case ESMS_QUEUE:
                long quest_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        ESM_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (quest_id > 0) {
                    Uri questUri = ContentUris.withAppendedId(ESM_Data.CONTENT_URI,
                            quest_id);
                    getContext().getContentResolver().notifyChange(questUri, null, false);
                    return questUri;
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
        AUTHORITY = context.getPackageName() + ".provider.esm";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.esm";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ESM_Provider.AUTHORITY, DATABASE_TABLES[0],
                ESMS_QUEUE);
        sUriMatcher.addURI(ESM_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#",
                ESMS_QUEUE_ID);

        questionsMap = new HashMap<>();
        questionsMap.put(ESM_Data._ID, ESM_Data._ID);
        questionsMap.put(ESM_Data.TIMESTAMP, ESM_Data.TIMESTAMP);
        questionsMap.put(ESM_Data.DEVICE_ID, ESM_Data.DEVICE_ID);
        questionsMap.put(ESM_Data.JSON, ESM_Data.JSON);
        questionsMap.put(ESM_Data.STATUS, ESM_Data.STATUS);
        questionsMap.put(ESM_Data.ANSWER_TIMESTAMP, ESM_Data.ANSWER_TIMESTAMP);
        questionsMap.put(ESM_Data.ANSWER, ESM_Data.ANSWER);
        questionsMap.put(ESM_Data.NOTIFICATION_TIMEOUT, ESM_Data.NOTIFICATION_TIMEOUT);
        questionsMap.put(ESM_Data.TRIGGER, ESM_Data.TRIGGER);

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
    public synchronized int update(Uri uri, ContentValues values, String selection,
                                   String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case ESMS_QUEUE:
                count = database.update(DATABASE_TABLES[0], values, selection,
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