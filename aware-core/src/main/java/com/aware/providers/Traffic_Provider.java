
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
 * AWARE Traffic Content Provider Allows you to access all the recorded network
 * traffic on the database Database is located at the SDCard :
 * /AWARE/network_traffic.db
 *
 * @author denzil
 */
public class Traffic_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 5;

    /**
     * Authority of Screen content provider
     */
    public static String AUTHORITY = "com.aware.provider.traffic";

    // ContentProvider query paths
    private static final int TRAFFIC = 1;
    private static final int TRAFFIC_ID = 2;

    /**
     * Traffic content representation
     *
     * @author denzil
     */
    public static final class Traffic_Data implements BaseColumns {
        private Traffic_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Traffic_Provider.AUTHORITY + "/network_traffic");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.network.traffic";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.network.traffic";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String NETWORK_TYPE = "network_type";
        public static final String RECEIVED_BYTES = "double_received_bytes";
        public static final String SENT_BYTES = "double_sent_bytes";
        public static final String RECEIVED_PACKETS = "double_received_packets";
        public static final String SENT_PACKETS = "double_sent_packets";
    }

    public static String DATABASE_NAME = "network_traffic.db";

    public static final String[] DATABASE_TABLES = {"network_traffic"};

    public static final String[] TABLES_FIELDS = {Traffic_Data._ID
            + " integer primary key autoincrement," + Traffic_Data.TIMESTAMP
            + " real default 0," + Traffic_Data.DEVICE_ID + " text default '',"
            + Traffic_Data.NETWORK_TYPE + " integer default 0,"
            + Traffic_Data.RECEIVED_BYTES + " real default 0,"
            + Traffic_Data.SENT_BYTES + " real default 0,"
            + Traffic_Data.RECEIVED_PACKETS + " real default 0,"
            + Traffic_Data.SENT_PACKETS + " real default 0"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> trafficProjectionMap = null;

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
            case TRAFFIC:
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
            case TRAFFIC:
                return Traffic_Data.CONTENT_TYPE;
            case TRAFFIC_ID:
                return Traffic_Data.CONTENT_ITEM_TYPE;
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
            case TRAFFIC:
                long traffic_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Traffic_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (traffic_id > 0) {
                    Uri trafficUri = ContentUris.withAppendedId(
                            Traffic_Data.CONTENT_URI, traffic_id);
                    getContext().getContentResolver().notifyChange(trafficUri, null, false);
                    return trafficUri;
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
        AUTHORITY = context.getPackageName() + ".provider.traffic";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.traffic";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Traffic_Provider.AUTHORITY, DATABASE_TABLES[0],
                TRAFFIC);
        sUriMatcher.addURI(Traffic_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", TRAFFIC_ID);

        trafficProjectionMap = new HashMap<String, String>();
        trafficProjectionMap.put(Traffic_Data._ID, Traffic_Data._ID);
        trafficProjectionMap
                .put(Traffic_Data.TIMESTAMP, Traffic_Data.TIMESTAMP);
        trafficProjectionMap
                .put(Traffic_Data.DEVICE_ID, Traffic_Data.DEVICE_ID);
        trafficProjectionMap.put(Traffic_Data.NETWORK_TYPE,
                Traffic_Data.NETWORK_TYPE);
        trafficProjectionMap.put(Traffic_Data.RECEIVED_BYTES,
                Traffic_Data.RECEIVED_BYTES);
        trafficProjectionMap.put(Traffic_Data.SENT_BYTES,
                Traffic_Data.SENT_BYTES);
        trafficProjectionMap.put(Traffic_Data.RECEIVED_PACKETS,
                Traffic_Data.RECEIVED_PACKETS);
        trafficProjectionMap.put(Traffic_Data.SENT_PACKETS,
                Traffic_Data.SENT_PACKETS);

        return true;
    }

    static {

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
            case TRAFFIC:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(trafficProjectionMap);
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
            case TRAFFIC:
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
