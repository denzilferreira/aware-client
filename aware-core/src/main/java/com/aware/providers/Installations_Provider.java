
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
 * AWARE Installations Content Provider Allows you to access all the recorded
 * installations on the database Database is located at the SDCard :
 * /AWARE/installations.db
 *
 * @author denzil
 */
public class Installations_Provider extends ContentProvider {

    private static final int DATABASE_VERSION = 5;

    /**
     * Authority of Installations content provider
     */
    public static String AUTHORITY = "com.aware.provider.installations";

    // ContentProvider query paths
    private static final int INSTALLATIONS = 1;
    private static final int INSTALLATIONS_ID = 2;

    /**
     * Installations content representation
     *
     * @author denzil
     */
    public static final class Installations_Data implements BaseColumns {
        private Installations_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Installations_Provider.AUTHORITY + "/installations");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.applications.installations";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.applications.installations";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String APPLICATION_NAME = "application_name";
        public static final String INSTALLATION_STATUS = "installation_status";
        public static final String PACKAGE_VERSION_NAME = "version_name";
        public static final String PACKAGE_VERSION_CODE = "version_code";
    }

    public static String DATABASE_NAME = "installations.db";

    public static final String[] DATABASE_TABLES = {"installations"};

    public static final String[] TABLES_FIELDS = {
            // installations
            Installations_Data._ID + " integer primary key autoincrement,"
                    + Installations_Data.TIMESTAMP + " real default 0,"
                    + Installations_Data.DEVICE_ID + " text default '',"
                    + Installations_Data.PACKAGE_NAME + " text default '',"
                    + Installations_Data.APPLICATION_NAME + " text default '',"
                    + Installations_Data.INSTALLATION_STATUS + " integer default -1,"
                    + Installations_Data.PACKAGE_VERSION_NAME + " text default '',"
                    + Installations_Data.PACKAGE_VERSION_CODE + " integer default -1"
    };

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> installationsMap = null;

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
            case INSTALLATIONS:
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
            case INSTALLATIONS:
                return Installations_Data.CONTENT_TYPE;
            case INSTALLATIONS_ID:
                return Installations_Data.CONTENT_ITEM_TYPE;
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
            case INSTALLATIONS:
                long installations_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Installations_Data.PACKAGE_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (installations_id > 0) {
                    Uri installationsUri = ContentUris.withAppendedId(
                            Installations_Data.CONTENT_URI, installations_id);
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
        AUTHORITY = context.getPackageName() + ".provider.installations";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.installations";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Installations_Provider.AUTHORITY,
                DATABASE_TABLES[0], INSTALLATIONS);
        sUriMatcher.addURI(Installations_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", INSTALLATIONS_ID);

        installationsMap = new HashMap<>();
        installationsMap.put(Installations_Data._ID, Installations_Data._ID);
        installationsMap.put(Installations_Data.TIMESTAMP, Installations_Data.TIMESTAMP);
        installationsMap.put(Installations_Data.DEVICE_ID, Installations_Data.DEVICE_ID);
        installationsMap.put(Installations_Data.PACKAGE_NAME, Installations_Data.PACKAGE_NAME);
        installationsMap.put(Installations_Data.APPLICATION_NAME, Installations_Data.APPLICATION_NAME);
        installationsMap.put(Installations_Data.INSTALLATION_STATUS, Installations_Data.INSTALLATION_STATUS);
        installationsMap.put(Installations_Data.PACKAGE_VERSION_NAME, Installations_Data.PACKAGE_VERSION_NAME);
        installationsMap.put(Installations_Data.PACKAGE_VERSION_CODE, Installations_Data.PACKAGE_VERSION_CODE);

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
            case INSTALLATIONS:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(installationsMap);
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
            case INSTALLATIONS:
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