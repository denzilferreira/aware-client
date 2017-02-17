
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
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Accelerometer;
import com.aware.Aware;
import com.aware.BuildConfig;
import com.aware.utils.DatabaseHelper;

import java.io.File;
import java.util.HashMap;

/**
 * AWARE Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/proximity.db
 *
 * @author denzil
 */
public class Proximity_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 3;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.proximity";

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
    public static final class Proximity_Sensor implements BaseColumns {
        private Proximity_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Proximity_Provider.AUTHORITY + "/sensor_proximity");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.proximity.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.proximity.sensor";

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
    public static final class Proximity_Data implements BaseColumns {
        private Proximity_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Proximity_Provider.AUTHORITY + "/proximity");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.proximity.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.proximity.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PROXIMITY = "double_proximity";
        public static final String ACCURACY = "accuracy";
        public static final String LABEL = "label";
    }

    public static String DATABASE_NAME = "proximity.db";

    public static final String[] DATABASE_TABLES = {"sensor_proximity",
            "proximity"};
    public static final String[] TABLES_FIELDS = {
            // sensor device information
            Proximity_Sensor._ID + " integer primary key autoincrement,"
                    + Proximity_Sensor.TIMESTAMP + " real default 0,"
                    + Proximity_Sensor.DEVICE_ID + " text default '',"
                    + Proximity_Sensor.MAXIMUM_RANGE + " real default 0,"
                    + Proximity_Sensor.MINIMUM_DELAY + " real default 0,"
                    + Proximity_Sensor.NAME + " text default '',"
                    + Proximity_Sensor.POWER_MA + " real default 0,"
                    + Proximity_Sensor.RESOLUTION + " real default 0,"
                    + Proximity_Sensor.TYPE + " text default '',"
                    + Proximity_Sensor.VENDOR + " text default '',"
                    + Proximity_Sensor.VERSION + " text default '',"
                    + "UNIQUE(" + Proximity_Sensor.DEVICE_ID + ")",
            // sensor data
            Proximity_Data._ID + " integer primary key autoincrement,"
                    + Proximity_Data.TIMESTAMP + " real default 0,"
                    + Proximity_Data.DEVICE_ID + " text default '',"
                    + Proximity_Data.PROXIMITY + " real default 0,"
                    + Proximity_Data.ACCURACY + " integer default 0,"
                    + Proximity_Data.LABEL + " text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> sensorMap = null;
    private HashMap<String, String> sensorDataMap = null;

    private static DatabaseHelper dbHelper;
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
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        initialiseDatabase();

        //lock database for transaction
        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case SENSOR_DATA:
                count = database.delete(DATABASE_TABLES[1], selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                return Proximity_Sensor.CONTENT_TYPE;
            case SENSOR_DEV_ID:
                return Proximity_Sensor.CONTENT_ITEM_TYPE;
            case SENSOR_DATA:
                return Proximity_Data.CONTENT_TYPE;
            case SENSOR_DATA_ID:
                return Proximity_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Insert entry to the database
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                long accel_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Proximity_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accel_id > 0) {
                    Uri accelUri = ContentUris.withAppendedId(
                            Proximity_Sensor.CONTENT_URI, accel_id);
                    getContext().getContentResolver().notifyChange(accelUri, null);
                    return accelUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case SENSOR_DATA:
                long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Proximity_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accelData_id > 0) {
                    Uri accelDataUri = ContentUris.withAppendedId(
                            Proximity_Data.CONTENT_URI, accelData_id);
                    getContext().getContentResolver().notifyChange(accelDataUri,
                            null);
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
     * Batch insert for high performance sensors (e.g., accelerometer, etc)
     *
     * @param uri
     * @param values
     * @return values.length
     */
    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[0], Proximity_Sensor.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[0], Proximity_Sensor.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Accelerometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
                break;
            case SENSOR_DATA:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[1], Proximity_Data.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[1], Proximity_Data.DEVICE_ID, v);
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

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.proximity";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Proximity_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Proximity_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", SENSOR_DEV_ID);
        sUriMatcher.addURI(Proximity_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Proximity_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", SENSOR_DATA_ID);

        sensorMap = new HashMap<String, String>();
        sensorMap.put(Proximity_Sensor._ID, Proximity_Sensor._ID);
        sensorMap.put(Proximity_Sensor.TIMESTAMP, Proximity_Sensor.TIMESTAMP);
        sensorMap.put(Proximity_Sensor.DEVICE_ID, Proximity_Sensor.DEVICE_ID);
        sensorMap.put(Proximity_Sensor.MAXIMUM_RANGE,
                Proximity_Sensor.MAXIMUM_RANGE);
        sensorMap.put(Proximity_Sensor.MINIMUM_DELAY,
                Proximity_Sensor.MINIMUM_DELAY);
        sensorMap.put(Proximity_Sensor.NAME, Proximity_Sensor.NAME);
        sensorMap.put(Proximity_Sensor.POWER_MA, Proximity_Sensor.POWER_MA);
        sensorMap.put(Proximity_Sensor.RESOLUTION, Proximity_Sensor.RESOLUTION);
        sensorMap.put(Proximity_Sensor.TYPE, Proximity_Sensor.TYPE);
        sensorMap.put(Proximity_Sensor.VENDOR, Proximity_Sensor.VENDOR);
        sensorMap.put(Proximity_Sensor.VERSION, Proximity_Sensor.VERSION);

        sensorDataMap = new HashMap<String, String>();
        sensorDataMap.put(Proximity_Data._ID, Proximity_Data._ID);
        sensorDataMap.put(Proximity_Data.TIMESTAMP, Proximity_Data.TIMESTAMP);
        sensorDataMap.put(Proximity_Data.DEVICE_ID, Proximity_Data.DEVICE_ID);
        sensorDataMap.put(Proximity_Data.PROXIMITY, Proximity_Data.PROXIMITY);
        sensorDataMap.put(Proximity_Data.ACCURACY, Proximity_Data.ACCURACY);
        sensorDataMap.put(Proximity_Data.LABEL, Proximity_Data.LABEL);

        initialiseDatabase();

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

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case SENSOR_DATA:
                count = database.update(DATABASE_TABLES[1], values, selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}