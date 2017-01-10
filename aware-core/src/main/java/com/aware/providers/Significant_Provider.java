
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
import com.aware.SignificantMotion;
import com.aware.utils.DatabaseHelper;

import java.io.File;
import java.util.HashMap;

/**
 * AWARE Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/temperature.db
 *
 * @author denzil
 */
public class Significant_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 1;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.significant";

    // ContentProvider query paths
    private static final int SENSOR_DEV = 1;
    private static final int SENSOR_DEV_ID = 2;
    private static final int SENSOR_DATA = 3;
    private static final int SENSOR_DATA_ID = 4;

    /**
     * Sensor device info
     *
     * @author denzil
     */
    public static final class Significant_Sensor implements BaseColumns {
        private Significant_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Significant_Provider.AUTHORITY + "/sensor_significant");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.significant.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.significant.sensor";

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
     * Logged sensor data
     *
     * @author df
     */
    public static final class Significant_Data implements BaseColumns {
        private Significant_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Significant_Provider.AUTHORITY + "/significant");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.significant.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.significant.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String IS_MOVING = "is_moving";
    }

    public static String DATABASE_NAME = "significant.db";

    public static final String[] DATABASE_TABLES = {"sensor_significant",
            "significant"};

    public static final String[] TABLES_FIELDS = {
            // sensor device information
            Significant_Sensor._ID + " integer primary key autoincrement,"
                    + Significant_Sensor.TIMESTAMP + " real default 0,"
                    + Significant_Sensor.DEVICE_ID + " text default '',"
                    + Significant_Sensor.MAXIMUM_RANGE + " real default 0,"
                    + Significant_Sensor.MINIMUM_DELAY + " real default 0,"
                    + Significant_Sensor.NAME + " text default '',"
                    + Significant_Sensor.POWER_MA + " real default 0,"
                    + Significant_Sensor.RESOLUTION + " real default 0,"
                    + Significant_Sensor.TYPE + " text default '',"
                    + Significant_Sensor.VENDOR + " text default '',"
                    + Significant_Sensor.VERSION + " text default '',"
                    + "UNIQUE(" + Significant_Sensor.DEVICE_ID + ")",
            // sensor data
            Significant_Data._ID + " integer primary key autoincrement,"
                    + Significant_Data.TIMESTAMP + " real default 0,"
                    + Significant_Data.DEVICE_ID + " text default '',"
                    + Significant_Data.IS_MOVING + " integer default 0"
    };

    private static UriMatcher sUriMatcher = null;
    private static HashMap<String, String> sensorMap = null;
    private static HashMap<String, String> sensorDataMap = null;
    private DatabaseHelper databaseHelper = null;
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
     * Delete entry from the database
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (!initializeDB()) {
            Log.w(SignificantMotion.TAG, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case SENSOR_DATA:
                database.beginTransaction();
                count = database.delete(DATABASE_TABLES[1], selection,
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
            case SENSOR_DEV:
                return Significant_Sensor.CONTENT_TYPE;
            case SENSOR_DEV_ID:
                return Significant_Sensor.CONTENT_ITEM_TYPE;
            case SENSOR_DATA:
                return Significant_Data.CONTENT_TYPE;
            case SENSOR_DATA_ID:
                return Significant_Data.CONTENT_ITEM_TYPE;
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
            Log.w(SignificantMotion.TAG, "Database unavailable...");
            return null;
        }

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();

        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                database.beginTransaction();
                long accel_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Significant_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accel_id > 0) {
                    Uri accelUri = ContentUris.withAppendedId(
                            Significant_Sensor.CONTENT_URI, accel_id);
                    getContext().getContentResolver().notifyChange(accelUri, null);
                    return accelUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            case SENSOR_DATA:
                database.beginTransaction();
                long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Significant_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accelData_id > 0) {
                    Uri accelDataUri = ContentUris.withAppendedId(
                            Significant_Data.CONTENT_URI, accelData_id);
                    getContext().getContentResolver().notifyChange(accelDataUri,
                            null);
                    return accelDataUri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:

                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Batch insert for high performance sensors (e.g., accelerometer, etc)
     *
     * @param uri
     * @param values
     * @return values.length
     */
    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        if (!initializeDB()) {
            Log.w(SignificantMotion.TAG, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                database.beginTransaction();
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[0], Significant_Sensor.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[0], Significant_Sensor.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Accelerometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
                database.setTransactionSuccessful();
                database.endTransaction();
                getContext().getContentResolver().notifyChange(uri, null);
                return count;
            case SENSOR_DATA:
                database.beginTransaction();
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[1], Significant_Data.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[1], Significant_Data.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Accelerometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
                database.setTransactionSuccessful();
                database.endTransaction();
                getContext().getContentResolver().notifyChange(uri, null);
                return count;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.significant";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Significant_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Significant_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", SENSOR_DEV_ID);
        sUriMatcher.addURI(Significant_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Significant_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", SENSOR_DATA_ID);

        sensorMap = new HashMap<>();
        sensorMap.put(Significant_Sensor._ID, Significant_Sensor._ID);
        sensorMap.put(Significant_Sensor.TIMESTAMP,
                Significant_Sensor.TIMESTAMP);
        sensorMap.put(Significant_Sensor.DEVICE_ID,
                Significant_Sensor.DEVICE_ID);
        sensorMap.put(Significant_Sensor.MAXIMUM_RANGE,
                Significant_Sensor.MAXIMUM_RANGE);
        sensorMap.put(Significant_Sensor.MINIMUM_DELAY,
                Significant_Sensor.MINIMUM_DELAY);
        sensorMap.put(Significant_Sensor.NAME, Significant_Sensor.NAME);
        sensorMap.put(Significant_Sensor.POWER_MA, Significant_Sensor.POWER_MA);
        sensorMap.put(Significant_Sensor.RESOLUTION,
                Significant_Sensor.RESOLUTION);
        sensorMap.put(Significant_Sensor.TYPE, Significant_Sensor.TYPE);
        sensorMap.put(Significant_Sensor.VENDOR, Significant_Sensor.VENDOR);
        sensorMap.put(Significant_Sensor.VERSION, Significant_Sensor.VERSION);

        sensorDataMap = new HashMap<>();
        sensorDataMap.put(Significant_Data._ID, Significant_Data._ID);
        sensorDataMap.put(Significant_Data.TIMESTAMP,
                Significant_Data.TIMESTAMP);
        sensorDataMap.put(Significant_Data.DEVICE_ID,
                Significant_Data.DEVICE_ID);
        sensorDataMap.put(Significant_Data.IS_MOVING,
                Significant_Data.IS_MOVING);

        return true;
    }

    /**
     * Query entries from the database
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (!initializeDB()) {
            Log.w(SignificantMotion.TAG, "Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(sensorMap);
                break;
            case SENSOR_DATA:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(sensorDataMap);
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
            Log.w(SignificantMotion.TAG, "Database unavailable...");
            return 0;
        }
        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                database.setTransactionSuccessful();
                database.endTransaction();
                break;
            case SENSOR_DATA:
                database.beginTransaction();
                count = database.update(DATABASE_TABLES[1], values, selection,
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
