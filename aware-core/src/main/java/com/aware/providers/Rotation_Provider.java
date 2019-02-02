
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
 * AWARE Rotation Content Provider Allows you to access all the recorded
 * rotation readings on the database Database is located at the SDCard :
 * /AWARE/rotation.db
 *
 * @author denzil
 */
public class Rotation_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 4;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.rotation";

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
    public static final class Rotation_Sensor implements BaseColumns {
        private Rotation_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Rotation_Provider.AUTHORITY + "/sensor_rotation");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.rotation.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.rotation.sensor";

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
     * Sensor data
     *
     * @author df
     */
    public static final class Rotation_Data implements BaseColumns {
        private Rotation_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Rotation_Provider.AUTHORITY + "/rotation");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.rotation.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.rotation.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String VALUES_0 = "double_values_0";
        public static final String VALUES_1 = "double_values_1";
        public static final String VALUES_2 = "double_values_2";
        public static final String VALUES_3 = "double_values_3";
        public static final String ACCURACY = "accuracy";
        public static final String LABEL = "label";
    }

    public static String DATABASE_NAME = "rotation.db";
    public static final String[] DATABASE_TABLES = {"sensor_rotation",
            "rotation"};
    public static final String[] TABLES_FIELDS = {
            // sensor device information
            Rotation_Sensor._ID + " integer primary key autoincrement,"
                    + Rotation_Sensor.TIMESTAMP + " real default 0,"
                    + Rotation_Sensor.DEVICE_ID + " text default '',"
                    + Rotation_Sensor.MAXIMUM_RANGE + " real default 0,"
                    + Rotation_Sensor.MINIMUM_DELAY + " real default 0,"
                    + Rotation_Sensor.NAME + " text default '',"
                    + Rotation_Sensor.POWER_MA + " real default 0,"
                    + Rotation_Sensor.RESOLUTION + " real default 0,"
                    + Rotation_Sensor.TYPE + " text default '',"
                    + Rotation_Sensor.VENDOR + " text default '',"
                    + Rotation_Sensor.VERSION + " text default '',"
                    + "UNIQUE(" + Rotation_Sensor.DEVICE_ID + ")",
            // sensor data
            Rotation_Data._ID + " integer primary key autoincrement,"
                    + Rotation_Data.TIMESTAMP + " real default 0,"
                    + Rotation_Data.DEVICE_ID + " text default '',"
                    + Rotation_Data.VALUES_0 + " real default 0,"
                    + Rotation_Data.VALUES_1 + " real default 0,"
                    + Rotation_Data.VALUES_2 + " real default 0,"
                    + Rotation_Data.VALUES_3 + " real default 0,"
                    + Rotation_Data.ACCURACY + " integer default 0,"
                    + Rotation_Data.LABEL + " text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> sensorMap = null;
    private HashMap<String, String> sensorDataMap = null;

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

        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                return Rotation_Sensor.CONTENT_TYPE;
            case SENSOR_DEV_ID:
                return Rotation_Sensor.CONTENT_ITEM_TYPE;
            case SENSOR_DATA:
                return Rotation_Data.CONTENT_TYPE;
            case SENSOR_DATA_ID:
                return Rotation_Data.CONTENT_ITEM_TYPE;
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
            case SENSOR_DEV:
                long accel_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Rotation_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accel_id > 0) {
                    Uri accelUri = ContentUris.withAppendedId(
                            Rotation_Sensor.CONTENT_URI, accel_id);
                    getContext().getContentResolver().notifyChange(accelUri, null, false);
                    return accelUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case SENSOR_DATA:
                long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Rotation_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (accelData_id > 0) {
                    Uri accelDataUri = ContentUris.withAppendedId(
                            Rotation_Data.CONTENT_URI, accelData_id);
                    getContext().getContentResolver().notifyChange(accelDataUri,null, false);
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
    public synchronized int bulkInsert(Uri uri, ContentValues[] values) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[0], Rotation_Sensor.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[0], Rotation_Sensor.DEVICE_ID, v);
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
                        id = database.insertOrThrow(DATABASE_TABLES[1], Rotation_Data.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[1], Rotation_Data.DEVICE_ID, v);
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

        getContext().getContentResolver().notifyChange(uri, null,false);

        return count;
    }

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.rotation";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.rotation";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Rotation_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Rotation_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", SENSOR_DEV_ID);
        sUriMatcher.addURI(Rotation_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Rotation_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", SENSOR_DATA_ID);

        sensorMap = new HashMap<String, String>();
        sensorMap.put(Rotation_Sensor._ID, Rotation_Sensor._ID);
        sensorMap.put(Rotation_Sensor.TIMESTAMP, Rotation_Sensor.TIMESTAMP);
        sensorMap.put(Rotation_Sensor.DEVICE_ID, Rotation_Sensor.DEVICE_ID);
        sensorMap.put(Rotation_Sensor.MAXIMUM_RANGE,
                Rotation_Sensor.MAXIMUM_RANGE);
        sensorMap.put(Rotation_Sensor.MINIMUM_DELAY,
                Rotation_Sensor.MINIMUM_DELAY);
        sensorMap.put(Rotation_Sensor.NAME, Rotation_Sensor.NAME);
        sensorMap.put(Rotation_Sensor.POWER_MA, Rotation_Sensor.POWER_MA);
        sensorMap.put(Rotation_Sensor.RESOLUTION, Rotation_Sensor.RESOLUTION);
        sensorMap.put(Rotation_Sensor.TYPE, Rotation_Sensor.TYPE);
        sensorMap.put(Rotation_Sensor.VENDOR, Rotation_Sensor.VENDOR);
        sensorMap.put(Rotation_Sensor.VERSION, Rotation_Sensor.VERSION);

        sensorDataMap = new HashMap<String, String>();
        sensorDataMap.put(Rotation_Data._ID, Rotation_Data._ID);
        sensorDataMap.put(Rotation_Data.TIMESTAMP, Rotation_Data.TIMESTAMP);
        sensorDataMap.put(Rotation_Data.DEVICE_ID, Rotation_Data.DEVICE_ID);
        sensorDataMap.put(Rotation_Data.VALUES_0, Rotation_Data.VALUES_0);
        sensorDataMap.put(Rotation_Data.VALUES_1, Rotation_Data.VALUES_1);
        sensorDataMap.put(Rotation_Data.VALUES_2, Rotation_Data.VALUES_2);
        sensorDataMap.put(Rotation_Data.VALUES_3, Rotation_Data.VALUES_3);
        sensorDataMap.put(Rotation_Data.ACCURACY, Rotation_Data.ACCURACY);
        sensorDataMap.put(Rotation_Data.LABEL, Rotation_Data.LABEL);

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
    public synchronized int update(Uri uri, ContentValues values, String selection,
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
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
}
