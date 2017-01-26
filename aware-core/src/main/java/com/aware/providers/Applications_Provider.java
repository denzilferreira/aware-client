
package com.aware.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;
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
 * AWARE Applications Content Provider Allows you to access all the recorded
 * applications on the database Database is located at the SDCard :
 * /AWARE/applications.db
 *
 * @author denzil
 */
public class Applications_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 7;

    /**
     * Authority of Application content provider
     */
    public static String AUTHORITY = "com.aware.provider.applications";

    // ContentProvider query paths
    private static final int FOREGROUND = 1;
    private static final int FOREGROUND_ID = 2;
    private static final int APPLICATIONS = 3;
    private static final int APPLICATIONS_ID = 4;
    private static final int NOTIFICATIONS = 5;
    private static final int NOTIFICATIONS_ID = 6;
    private static final int ERROR = 7;
    private static final int ERROR_ID = 8;

    /**
     * Applications running on the foreground
     *
     * @author denzil
     */
    public static final class Applications_Foreground implements BaseColumns {
        private Applications_Foreground() {
        }

        ;

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Applications_Provider.AUTHORITY + "/applications_foreground");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.applications.foreground";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.applications.foreground";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String APPLICATION_NAME = "application_name";
        public static final String IS_SYSTEM_APP = "is_system_app";
    }

    /**
     * Both background and foreground applications running, use process
     * importance to distinguish.
     *
     * @author denzil
     */
    public static final class Applications_History implements BaseColumns {
        private Applications_History() {
        }

        ;

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Applications_Provider.AUTHORITY + "/applications_history");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.applications.history";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.applications.history";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String APPLICATION_NAME = "application_name";
        public static final String PROCESS_IMPORTANCE = "process_importance";
        public static final String PROCESS_ID = "process_id";
        public static final String END_TIMESTAMP = "double_end_timestamp";
        public static final String IS_SYSTEM_APP = "is_system_app";
    }

    /**
     * Notifications received by any application.
     *
     * @author denzil
     */
    public static final class Applications_Notifications implements BaseColumns {
        private Applications_Notifications() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Applications_Provider.AUTHORITY + "/applications_notifications");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.applications.notifications";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.applications.notifications";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String APPLICATION_NAME = "application_name";
        public static final String TEXT = "text";
        public static final String SOUND = "sound";
        public static final String VIBRATE = "vibrate";
        public static final String DEFAULTS = "defaults";
        public static final String FLAGS = "flags";
    }

    public static final class Applications_Crashes implements BaseColumns {
        private Applications_Crashes() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Applications_Provider.AUTHORITY + "/applications_crashes");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.applications.crashes";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.applications.crashes";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String APPLICATION_NAME = "application_name";
        public static final String APPLICATION_VERSION = "application_version";
        public static final String ERROR_SHORT = "error_short";
        public static final String ERROR_LONG = "error_long";
        public static final String ERROR_CONDITION = "error_condition";
        public static final String IS_SYSTEM_APP = "is_system_app";
    }

    public static String DATABASE_NAME = "applications.db";

    public static final String[] DATABASE_TABLES = {"applications_foreground",
            "applications_history", "applications_notifications",
            "applications_crashes"};
    public static final String[] TABLES_FIELDS = {
            // Foreground
            Applications_Foreground._ID + " integer primary key autoincrement,"
                    + Applications_Foreground.TIMESTAMP + " real default 0,"
                    + Applications_Foreground.DEVICE_ID + " text default '',"
                    + Applications_Foreground.PACKAGE_NAME + " text default '',"
                    + Applications_Foreground.APPLICATION_NAME + " text default '',"
                    + Applications_Foreground.IS_SYSTEM_APP + " integer default 0",

            // Applications
            Applications_History._ID + " integer primary key autoincrement,"
                    + Applications_History.TIMESTAMP + " real default 0,"
                    + Applications_History.DEVICE_ID + " text default '',"
                    + Applications_History.PACKAGE_NAME + " text default '',"
                    + Applications_History.APPLICATION_NAME + " text default '',"
                    + Applications_History.PROCESS_IMPORTANCE + " integer default 0,"
                    + Applications_History.PROCESS_ID + " integer default 0,"
                    + Applications_History.END_TIMESTAMP + " real default 1,"
                    + Applications_History.IS_SYSTEM_APP + " integer default 0",

            // Notifications
            Applications_Notifications._ID
                    + " integer primary key autoincrement,"
                    + Applications_Notifications.TIMESTAMP + " real default 0,"
                    + Applications_Notifications.DEVICE_ID + " text default '',"
                    + Applications_Notifications.PACKAGE_NAME + " text default '',"
                    + Applications_Notifications.APPLICATION_NAME + " text default '',"
                    + Applications_Notifications.TEXT + " text default '',"
                    + Applications_Notifications.SOUND + " text default '',"
                    + Applications_Notifications.VIBRATE + " text default '',"
                    + Applications_Notifications.DEFAULTS + " integer default -1,"
                    + Applications_Notifications.FLAGS + " integer default -1",

            // Crashes
            Applications_Crashes._ID + " integer primary key autoincrement,"
                    + Applications_Crashes.TIMESTAMP + " real default 0,"
                    + Applications_Crashes.DEVICE_ID + " text default '',"
                    + Applications_Crashes.PACKAGE_NAME + " text default '',"
                    + Applications_Crashes.APPLICATION_NAME + " text default '',"
                    + Applications_Crashes.APPLICATION_VERSION + " real default 0,"
                    + Applications_Crashes.ERROR_SHORT + " text default '',"
                    + Applications_Crashes.ERROR_LONG + " text default '',"
                    + Applications_Crashes.ERROR_CONDITION + " integer default 0,"
                    + Applications_Crashes.IS_SYSTEM_APP + " integer default 0"};

    private static UriMatcher sUriMatcher = null;
    private static HashMap<String, String> foregroundMap = null;
    private static HashMap<String, String> applicationsMap = null;
    private static HashMap<String, String> notificationMap = null;
    private static HashMap<String, String> crashesMap = null;
    private static DatabaseHelper databaseHelper = null;
    private static SQLiteDatabase database = null;

    private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        }
        if (databaseHelper != null && (database == null || !database.isOpen())) {
            database = databaseHelper.getWritableDatabase();
        }
        return (database != null && databaseHelper != null);
    }

    /**
     * Recreates the ContentProvider
     */
    public static void resetDB(Context c) {
        Log.d("AWARE", "Resetting " + DATABASE_NAME + "...");

        File db = new File(DATABASE_NAME);
        db.delete();
        databaseHelper = new DatabaseHelper(c, DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (databaseHelper != null) {
            database = databaseHelper.getWritableDatabase();
        }
    }

    /**
     * Delete entry from the database
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        if (!initializeDB()) {
            Log.w(AUTHORITY, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case FOREGROUND:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case APPLICATIONS:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[1], selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case NOTIFICATIONS:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[2], selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case ERROR:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[3], selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
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
            case FOREGROUND:
                return Applications_Foreground.CONTENT_TYPE;
            case FOREGROUND_ID:
                return Applications_Foreground.CONTENT_ITEM_TYPE;
            case APPLICATIONS:
                return Applications_History.CONTENT_TYPE;
            case APPLICATIONS_ID:
                return Applications_History.CONTENT_ITEM_TYPE;
            case NOTIFICATIONS:
                return Applications_Notifications.CONTENT_TYPE;
            case NOTIFICATIONS_ID:
                return Applications_Notifications.CONTENT_ITEM_TYPE;
            case ERROR:
                return Applications_Crashes.CONTENT_TYPE;
            case ERROR_ID:
                return Applications_Crashes.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Insert entry to the database
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        if (!initializeDB()) {
            Log.w(AUTHORITY, "Database unavailable...");
            return null;
        }

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();
        switch (sUriMatcher.match(uri)) {
            case FOREGROUND:
                database.beginTransaction();
                long foreground_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Applications_Foreground.APPLICATION_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (foreground_id > 0) {
                    Uri foregroundUri = ContentUris.withAppendedId(
                            Applications_Foreground.CONTENT_URI, foreground_id);
                    getContext().getContentResolver().notifyChange(foregroundUri,
                            null);
                    return foregroundUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            case APPLICATIONS:
                database.beginTransaction();
                long applications_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Applications_History.PACKAGE_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (applications_id > 0) {
                    Uri applicationsUri = ContentUris.withAppendedId(
                            Applications_History.CONTENT_URI, applications_id);
                    getContext().getContentResolver().notifyChange(applicationsUri,
                            null);
                    return applicationsUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            case NOTIFICATIONS:
                database.beginTransaction();
                long notifications_id = database.insertWithOnConflict(DATABASE_TABLES[2],
                        Applications_Notifications.PACKAGE_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (notifications_id > 0) {
                    Uri notificationsUri = ContentUris.withAppendedId(
                            Applications_Notifications.CONTENT_URI,
                            notifications_id);
                    getContext().getContentResolver().notifyChange(
                            notificationsUri, null);
                    return notificationsUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            case ERROR:
                database.beginTransaction();
                long error_id = database.insertWithOnConflict(DATABASE_TABLES[3],
                        Applications_Crashes.PACKAGE_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (error_id > 0) {
                    Uri errorsUri = ContentUris.withAppendedId(
                            Applications_Crashes.CONTENT_URI, error_id);
                    getContext().getContentResolver().notifyChange(errorsUri, null);
                    return errorsUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.applications";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[0],
                FOREGROUND);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", FOREGROUND_ID);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[1],
                APPLICATIONS);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", APPLICATIONS_ID);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[2],
                NOTIFICATIONS);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[2]
                + "/#", NOTIFICATIONS_ID);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[3],
                ERROR);
        sUriMatcher.addURI(Applications_Provider.AUTHORITY, DATABASE_TABLES[3]
                + "/#", ERROR_ID);

        foregroundMap = new HashMap<String, String>();
        foregroundMap.put(Applications_Foreground._ID,
                Applications_Foreground._ID);
        foregroundMap.put(Applications_Foreground.TIMESTAMP,
                Applications_Foreground.TIMESTAMP);
        foregroundMap.put(Applications_Foreground.DEVICE_ID,
                Applications_Foreground.DEVICE_ID);
        foregroundMap.put(Applications_Foreground.PACKAGE_NAME,
                Applications_Foreground.PACKAGE_NAME);
        foregroundMap.put(Applications_Foreground.APPLICATION_NAME,
                Applications_Foreground.APPLICATION_NAME);
        foregroundMap.put(Applications_Foreground.IS_SYSTEM_APP,
                Applications_Foreground.IS_SYSTEM_APP);

        applicationsMap = new HashMap<String, String>();
        applicationsMap.put(Applications_History._ID, Applications_History._ID);
        applicationsMap.put(Applications_History.TIMESTAMP,
                Applications_History.TIMESTAMP);
        applicationsMap.put(Applications_History.DEVICE_ID,
                Applications_History.DEVICE_ID);
        applicationsMap.put(Applications_History.PACKAGE_NAME,
                Applications_History.PACKAGE_NAME);
        applicationsMap.put(Applications_History.APPLICATION_NAME,
                Applications_History.APPLICATION_NAME);
        applicationsMap.put(Applications_History.PROCESS_IMPORTANCE,
                Applications_History.PROCESS_IMPORTANCE);
        applicationsMap.put(Applications_History.PROCESS_ID,
                Applications_History.PROCESS_ID);
        applicationsMap.put(Applications_History.END_TIMESTAMP,
                Applications_History.END_TIMESTAMP);
        applicationsMap.put(Applications_History.IS_SYSTEM_APP,
                Applications_History.IS_SYSTEM_APP);

        notificationMap = new HashMap<String, String>();
        notificationMap.put(Applications_Notifications._ID,
                Applications_Notifications._ID);
        notificationMap.put(Applications_Notifications.TIMESTAMP,
                Applications_Notifications.TIMESTAMP);
        notificationMap.put(Applications_Notifications.DEVICE_ID,
                Applications_Notifications.DEVICE_ID);
        notificationMap.put(Applications_Notifications.PACKAGE_NAME,
                Applications_Notifications.PACKAGE_NAME);
        notificationMap.put(Applications_Notifications.APPLICATION_NAME,
                Applications_Notifications.APPLICATION_NAME);
        notificationMap.put(Applications_Notifications.TEXT,
                Applications_Notifications.TEXT);
        notificationMap.put(Applications_Notifications.SOUND,
                Applications_Notifications.SOUND);
        notificationMap.put(Applications_Notifications.VIBRATE,
                Applications_Notifications.VIBRATE);
        notificationMap.put(Applications_Notifications.FLAGS,
                Applications_Notifications.FLAGS);
        notificationMap.put(Applications_Notifications.DEFAULTS,
                Applications_Notifications.DEFAULTS);

        crashesMap = new HashMap<String, String>();
        crashesMap.put(Applications_Crashes._ID, Applications_Crashes._ID);
        crashesMap.put(Applications_Crashes.TIMESTAMP,
                Applications_Crashes.TIMESTAMP);
        crashesMap.put(Applications_Crashes.DEVICE_ID,
                Applications_Crashes.DEVICE_ID);
        crashesMap.put(Applications_Crashes.PACKAGE_NAME,
                Applications_Crashes.PACKAGE_NAME);
        crashesMap.put(Applications_Crashes.APPLICATION_NAME,
                Applications_Crashes.APPLICATION_NAME);
        crashesMap.put(Applications_Crashes.APPLICATION_VERSION,
                Applications_Crashes.APPLICATION_VERSION);
        crashesMap.put(Applications_Crashes.ERROR_SHORT,
                Applications_Crashes.ERROR_SHORT);
        crashesMap.put(Applications_Crashes.ERROR_LONG,
                Applications_Crashes.ERROR_LONG);
        crashesMap.put(Applications_Crashes.ERROR_CONDITION,
                Applications_Crashes.ERROR_CONDITION);
        crashesMap.put(Applications_Crashes.IS_SYSTEM_APP,
                Applications_Crashes.IS_SYSTEM_APP);

        return true;
    }

    /**
     * Query entries from the database
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        if (!initializeDB()) {
            Log.w(AUTHORITY, "Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {
            case FOREGROUND:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(foregroundMap);
                break;
            case APPLICATIONS:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(applicationsMap);
                break;
            case NOTIFICATIONS:
                qb.setTables(DATABASE_TABLES[2]);
                qb.setProjectionMap(notificationMap);
                break;
            case ERROR:
                qb.setTables(DATABASE_TABLES[3]);
                qb.setProjectionMap(crashesMap);
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

        if (!initializeDB()) {
            Log.w(AUTHORITY, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case FOREGROUND:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case APPLICATIONS:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[1], values, selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case NOTIFICATIONS:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[2], values, selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case ERROR:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[3], values, selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
