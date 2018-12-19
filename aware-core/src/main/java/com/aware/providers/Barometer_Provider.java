
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
import com.aware.Barometer;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * AWARE Content Provider Allows you to access all the recorded readings on the
 * database Database is located at the SDCard : /AWARE/barometer.db
 *
 * @author denzil
 */
public class Barometer_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 2;

    /**
     * Authority of content provider
     */
    public static String AUTHORITY = "com.aware.provider.barometer";

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
    public static final class Barometer_Sensor implements BaseColumns {
        private Barometer_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Barometer_Provider.AUTHORITY + "/sensor_barometer");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.barometer.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.barometer.sensor";

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
    public static final class Barometer_Data implements BaseColumns {
        private Barometer_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Barometer_Provider.AUTHORITY + "/barometer");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.barometer.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.barometer.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String AMBIENT_PRESSURE = "double_values_0";
        public static final String ACCURACY = "accuracy";
        public static final String LABEL = "label";
    }

    public static String DATABASE_NAME = "barometer.db";
    public static final String[] DATABASE_TABLES = {"sensor_barometer",
            "barometer"};
    public static final String[] TABLES_FIELDS = {
            // sensor device information
            Barometer_Sensor._ID + " integer primary key autoincrement,"
                    + Barometer_Sensor.TIMESTAMP + " real default 0,"
                    + Barometer_Sensor.DEVICE_ID + " text default '',"
                    + Barometer_Sensor.MAXIMUM_RANGE + " real default 0,"
                    + Barometer_Sensor.MINIMUM_DELAY + " real default 0,"
                    + Barometer_Sensor.NAME + " text default '',"
                    + Barometer_Sensor.POWER_MA + " real default 0,"
                    + Barometer_Sensor.RESOLUTION + " real default 0,"
                    + Barometer_Sensor.TYPE + " text default '',"
                    + Barometer_Sensor.VENDOR + " text default '',"
                    + Barometer_Sensor.VERSION + " text default '',"
                    + "UNIQUE(" + Barometer_Sensor.DEVICE_ID + ")",
            // sensor data
            Barometer_Data._ID + " integer primary key autoincrement,"
                    + Barometer_Data.TIMESTAMP + " real default 0,"
                    + Barometer_Data.DEVICE_ID + " text default '',"
                    + Barometer_Data.AMBIENT_PRESSURE + " real default 0,"
                    + Barometer_Data.ACCURACY + " integer default 0,"
                    + Barometer_Data.LABEL + " text default ''"};

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

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
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
                return Barometer_Sensor.CONTENT_TYPE;
            case SENSOR_DEV_ID:
                return Barometer_Sensor.CONTENT_ITEM_TYPE;
            case SENSOR_DATA:
                return Barometer_Data.CONTENT_TYPE;
            case SENSOR_DATA_ID:
                return Barometer_Data.CONTENT_ITEM_TYPE;
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
                        Barometer_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (accel_id > 0) {
                    Uri accelUri = ContentUris.withAppendedId(
                            Barometer_Sensor.CONTENT_URI, accel_id);
                    getContext().getContentResolver().notifyChange(accelUri, null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return accelUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case SENSOR_DATA:
                long accelData_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Barometer_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (accelData_id > 0) {
                    Uri accelDataUri = ContentUris.withAppendedId(
                            Barometer_Data.CONTENT_URI, accelData_id);
                    getContext().getContentResolver().notifyChange(accelDataUri,null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return accelDataUri;
                }
                database.endTransaction();
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
    public synchronized int bulkInsert(Uri uri, ContentValues[] values) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case SENSOR_DEV:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[0], Barometer_Sensor.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[0], Barometer_Sensor.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Barometer.TAG, "Failed to insert/replace row into " + uri);
                    } else {
                        count++;
                    }
                }
                break;
            case SENSOR_DATA:
                for (ContentValues v : values) {
                    long id;
                    try {
                        id = database.insertOrThrow(DATABASE_TABLES[1], Barometer_Data.DEVICE_ID, v);
                    } catch (SQLException e) {
                        id = database.replace(DATABASE_TABLES[1], Barometer_Data.DEVICE_ID, v);
                    }
                    if (id <= 0) {
                        Log.w(Barometer.TAG, "Failed to insert/replace row into " + uri);
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
        AUTHORITY = context.getPackageName() + ".provider.barometer";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.barometer";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Barometer_Provider.AUTHORITY, DATABASE_TABLES[0],
                SENSOR_DEV);
        sUriMatcher.addURI(Barometer_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", SENSOR_DEV_ID);
        sUriMatcher.addURI(Barometer_Provider.AUTHORITY, DATABASE_TABLES[1],
                SENSOR_DATA);
        sUriMatcher.addURI(Barometer_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", SENSOR_DATA_ID);

        sensorMap = new HashMap<String, String>();
        sensorMap.put(Barometer_Sensor._ID, Barometer_Sensor._ID);
        sensorMap.put(Barometer_Sensor.TIMESTAMP, Barometer_Sensor.TIMESTAMP);
        sensorMap.put(Barometer_Sensor.DEVICE_ID, Barometer_Sensor.DEVICE_ID);
        sensorMap.put(Barometer_Sensor.MAXIMUM_RANGE,
                Barometer_Sensor.MAXIMUM_RANGE);
        sensorMap.put(Barometer_Sensor.MINIMUM_DELAY,
                Barometer_Sensor.MINIMUM_DELAY);
        sensorMap.put(Barometer_Sensor.NAME, Barometer_Sensor.NAME);
        sensorMap.put(Barometer_Sensor.POWER_MA, Barometer_Sensor.POWER_MA);
        sensorMap.put(Barometer_Sensor.RESOLUTION, Barometer_Sensor.RESOLUTION);
        sensorMap.put(Barometer_Sensor.TYPE, Barometer_Sensor.TYPE);
        sensorMap.put(Barometer_Sensor.VENDOR, Barometer_Sensor.VENDOR);
        sensorMap.put(Barometer_Sensor.VERSION, Barometer_Sensor.VERSION);

        sensorDataMap = new HashMap<String, String>();
        sensorDataMap.put(Barometer_Data._ID, Barometer_Data._ID);
        sensorDataMap.put(Barometer_Data.TIMESTAMP, Barometer_Data.TIMESTAMP);
        sensorDataMap.put(Barometer_Data.DEVICE_ID, Barometer_Data.DEVICE_ID);
        sensorDataMap.put(Barometer_Data.AMBIENT_PRESSURE,
                Barometer_Data.AMBIENT_PRESSURE);
        sensorDataMap.put(Barometer_Data.ACCURACY, Barometer_Data.ACCURACY);
        sensorDataMap.put(Barometer_Data.LABEL, Barometer_Data.LABEL);

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

        int count;
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