
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

import com.aware.Accelerometer;
import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * AWARE Accelerometer Content Provider Allows you to access all the recorded
 * sync_accelerometer readings on the database Database is located at the SDCard :
 * /AWARE/sync_accelerometer.db
 *
 * @author denzil
 */
public class Accelerometer_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 5;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.accelerometer";

    // ContentProvider query paths
    private final int ACCEL_DEV = 1;
    private final int ACCEL_DEV_ID = 2;
    private final int ACCEL_DATA = 3;
    private final int ACCEL_DATA_ID = 4;

    private UriMatcher sUriMatcher;
    private HashMap<String, String> accelDeviceMap;
    private HashMap<String, String> accelDataMap;

    /**
     * Accelerometer device info
     *
     * @author denzil
     */
    public static final class Accelerometer_Sensor implements BaseColumns {
        private Accelerometer_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Accelerometer_Provider.AUTHORITY + "/sensor_accelerometer");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.accelerometer.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.accelerometer.sensor";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String MAXIMUM_RANGE = "double_sensor_maximum_range";
        public static final String MINIMUM_DELAY = "double_sensor_minimum_delay";
        public static final String NAME = "sensor_name";
        public static final String POWER_MA = "double_sensor_power_ma";
        public static final String RESOLUTION = "double_sensor_resolution";
        public static final String TYPE = "sensor_type";
        public static final String VENDOR = "sensor_vendor";
        public static final String VERSION = "sensor_version";
    }

    /**
     * Logged sync_accelerometer data
     *
     * @author df
     */
    public static final class Accelerometer_Data implements BaseColumns {
        private Accelerometer_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Accelerometer_Provider.AUTHORITY + "/accelerometer");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.accelerometer.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.accelerometer.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String VALUES_0 = "double_values_0";
        public static final String VALUES_1 = "double_values_1";
        public static final String VALUES_2 = "double_values_2";
        public static final String ACCURACY = "accuracy";
        public static final String LABEL = "label";
    }

    public static String DATABASE_NAME = "accelerometer.db";
    public static final String[] DATABASE_TABLES = {"sensor_accelerometer", "accelerometer"};
    public static final String[] TABLES_FIELDS = {
            // sync_accelerometer device information
            Accelerometer_Sensor._ID + " integer primary key autoincrement,"
                    + Accelerometer_Sensor.TIMESTAMP + " real default 0,"
                    + Accelerometer_Sensor.DEVICE_ID + " text default '',"
                    + Accelerometer_Sensor.MAXIMUM_RANGE + " real default 0,"
                    + Accelerometer_Sensor.MINIMUM_DELAY + " real default 0,"
                    + Accelerometer_Sensor.NAME + " text default '',"
                    + Accelerometer_Sensor.POWER_MA + " real default 0,"
                    + Accelerometer_Sensor.RESOLUTION + " real default 0,"
                    + Accelerometer_Sensor.TYPE + " text default '',"
                    + Accelerometer_Sensor.VENDOR + " text default '',"
                    + Accelerometer_Sensor.VERSION + " text default '',"
                    + "UNIQUE(" + Accelerometer_Sensor.DEVICE_ID + ")",

            // sync_accelerometer data
            Accelerometer_Data._ID + " integer primary key autoincrement,"
                    + Accelerometer_Data.TIMESTAMP + " real default 0,"
                    + Accelerometer_Data.DEVICE_ID + " text default '',"
                    + Accelerometer_Data.VALUES_0 + " real default 0,"
                    + Accelerometer_Data.VALUES_1 + " real default 0,"
                    + Accelerometer_Data.VALUES_2 + " real default 0,"
                    + Accelerometer_Data.ACCURACY + " integer default 0,"
                    + Accelerometer_Data.LABEL + " text default ''"};

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
            case ACCEL_DEV:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            case ACCEL_DATA:
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
            case ACCEL_DEV:
                return Accelerometer_Sensor.CONTENT_TYPE;
            case ACCEL_DEV_ID:
                return Accelerometer_Sensor.CONTENT_ITEM_TYPE;
            case ACCEL_DATA:
                return Accelerometer_Data.CONTENT_TYPE;
            case ACCEL_DATA_ID:
                return Accelerometer_Data.CONTENT_ITEM_TYPE;
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
            case ACCEL_DEV:
                long accel_id = database.insertWithOnConflict(DATABASE_TABLES[0], Accelerometer_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (accel_id > 0) {
                    Uri accelUri = ContentUris.withAppendedId(Accelerometer_Sensor.CONTENT_URI, accel_id);
                    getContext().getContentResolver().notifyChange(accelUri, null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return accelUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case ACCEL_DATA:
                long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1], Accelerometer_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (accelData_id > 0) {
                    Uri accelDataUri = ContentUris.withAppendedId(Accelerometer_Data.CONTENT_URI, accelData_id);
                    getContext().getContentResolver().notifyChange(accelDataUri, null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return accelDataUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Batch insert for high performance sensors (e.g., sync_accelerometer, etc)
     *
     * @param uri
     * @param values
     * @return values.length
     */
    @Override
    public synchronized int bulkInsert(Uri uri, ContentValues[] values) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case ACCEL_DEV:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[0], Accelerometer_Sensor.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[0], Accelerometer_Sensor.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Accelerometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
                break;
            case ACCEL_DATA:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[1], Accelerometer_Data.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[1], Accelerometer_Data.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Accelerometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
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

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.accelerometer";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.accelerometer";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Accelerometer_Provider.AUTHORITY, DATABASE_TABLES[0], ACCEL_DEV);
        sUriMatcher.addURI(Accelerometer_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#", ACCEL_DEV_ID);
        sUriMatcher.addURI(Accelerometer_Provider.AUTHORITY, DATABASE_TABLES[1], ACCEL_DATA);
        sUriMatcher.addURI(Accelerometer_Provider.AUTHORITY, DATABASE_TABLES[1] + "/#", ACCEL_DATA_ID);

        accelDeviceMap = new HashMap<>();
        accelDeviceMap.put(Accelerometer_Sensor._ID, Accelerometer_Sensor._ID);
        accelDeviceMap.put(Accelerometer_Sensor.TIMESTAMP, Accelerometer_Sensor.TIMESTAMP);
        accelDeviceMap.put(Accelerometer_Sensor.DEVICE_ID, Accelerometer_Sensor.DEVICE_ID);
        accelDeviceMap.put(Accelerometer_Sensor.MAXIMUM_RANGE, Accelerometer_Sensor.MAXIMUM_RANGE);
        accelDeviceMap.put(Accelerometer_Sensor.MINIMUM_DELAY, Accelerometer_Sensor.MINIMUM_DELAY);
        accelDeviceMap.put(Accelerometer_Sensor.NAME, Accelerometer_Sensor.NAME);
        accelDeviceMap.put(Accelerometer_Sensor.POWER_MA, Accelerometer_Sensor.POWER_MA);
        accelDeviceMap.put(Accelerometer_Sensor.RESOLUTION, Accelerometer_Sensor.RESOLUTION);
        accelDeviceMap.put(Accelerometer_Sensor.TYPE, Accelerometer_Sensor.TYPE);
        accelDeviceMap.put(Accelerometer_Sensor.VENDOR, Accelerometer_Sensor.VENDOR);
        accelDeviceMap.put(Accelerometer_Sensor.VERSION, Accelerometer_Sensor.VERSION);

        accelDataMap = new HashMap<>();
        accelDataMap.put(Accelerometer_Data._ID, Accelerometer_Data._ID);
        accelDataMap.put(Accelerometer_Data.TIMESTAMP, Accelerometer_Data.TIMESTAMP);
        accelDataMap.put(Accelerometer_Data.DEVICE_ID, Accelerometer_Data.DEVICE_ID);
        accelDataMap.put(Accelerometer_Data.VALUES_0, Accelerometer_Data.VALUES_0);
        accelDataMap.put(Accelerometer_Data.VALUES_1, Accelerometer_Data.VALUES_1);
        accelDataMap.put(Accelerometer_Data.VALUES_2, Accelerometer_Data.VALUES_2);
        accelDataMap.put(Accelerometer_Data.ACCURACY, Accelerometer_Data.ACCURACY);
        accelDataMap.put(Accelerometer_Data.LABEL, Accelerometer_Data.LABEL);

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
            case ACCEL_DEV:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(accelDeviceMap);
                break;
            case ACCEL_DATA:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(accelDataMap);
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
            case ACCEL_DEV:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;
            case ACCEL_DATA:
                count = database.update(DATABASE_TABLES[1], values, selection, selectionArgs);
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
