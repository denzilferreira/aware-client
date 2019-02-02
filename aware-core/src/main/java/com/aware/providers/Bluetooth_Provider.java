
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
 * AWARE Bluetooth Content Provider Allows you to access all the recorded
 * bluetooth devices on the database Database is located at the SDCard :
 * /AWARE/bluetooth.db
 *
 * @author denzil
 */
public class Bluetooth_Provider extends ContentProvider {

    private static final int DATABASE_VERSION = 3;

    /**
     * Authority of Bluetooth content provider
     * com.aware.provider.bluetooth
     */
    public static String AUTHORITY = "com.aware.provider.bluetooth";

    // ContentProvider query paths
    private static final int BT_DEV = 1;
    private static final int BT_DEV_ID = 2;
    private static final int BT_DATA = 3;
    private static final int BT_DATA_ID = 4;

    /**
     * Bluetooth device info
     *
     * @author denzil
     */
    public static final class Bluetooth_Sensor implements BaseColumns {
        private Bluetooth_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Bluetooth_Provider.AUTHORITY + "/sensor_bluetooth");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.bluetooth.device";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.bluetooth.device";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String BT_ADDRESS = "bt_address";
        public static final String BT_NAME = "bt_name";
    }

    /**
     * Logged bluetooth data
     *
     * @author df
     */
    public static final class Bluetooth_Data implements BaseColumns {
        private Bluetooth_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + Bluetooth_Provider.AUTHORITY + "/bluetooth");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.bluetooth.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.bluetooth.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String BT_ADDRESS = "bt_address";
        public static final String BT_NAME = "bt_name";
        public static final String BT_RSSI = "bt_rssi";
        public static final String BT_LABEL = "label";
    }

    public static String DATABASE_NAME = "bluetooth.db";

    public static final String[] DATABASE_TABLES = {"sensor_bluetooth",
            "bluetooth"};
    public static final String[] TABLES_FIELDS = {
            // device
            Bluetooth_Sensor._ID + " integer primary key autoincrement,"
                    + Bluetooth_Sensor.TIMESTAMP + " real default 0,"
                    + Bluetooth_Sensor.DEVICE_ID + " text default '',"
                    + Bluetooth_Sensor.BT_ADDRESS + " text default '',"
                    + Bluetooth_Sensor.BT_NAME + " text default ''",
            // data
            Bluetooth_Data._ID + " integer primary key autoincrement,"
                    + Bluetooth_Data.TIMESTAMP + " real default 0,"
                    + Bluetooth_Data.DEVICE_ID + " text default '',"
                    + Bluetooth_Data.BT_ADDRESS + " text default '',"
                    + Bluetooth_Data.BT_NAME + " text default '',"
                    + Bluetooth_Data.BT_RSSI + " integer default 0,"
                    + Bluetooth_Data.BT_LABEL + " text default ''"
    };

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> bluetoothDeviceMap = null;
    private HashMap<String, String> bluetoothDataMap = null;

    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    /**
     * Delete bluetooth entry from the database
     */
    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {

        initialiseDatabase();

        //lock database for transaction
        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case BT_DEV:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case BT_DATA:
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
            case BT_DEV:
                return Bluetooth_Sensor.CONTENT_TYPE;
            case BT_DEV_ID:
                return Bluetooth_Sensor.CONTENT_ITEM_TYPE;
            case BT_DATA:
                return Bluetooth_Data.CONTENT_TYPE;
            case BT_DATA_ID:
                return Bluetooth_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Insert bluetooth entry to the database
     */
    @Override
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case BT_DEV:
                long rowId = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Bluetooth_Sensor.BT_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (rowId > 0) {
                    Uri bluetoothUri = ContentUris.withAppendedId(
                            Bluetooth_Sensor.CONTENT_URI, rowId);
                    getContext().getContentResolver().notifyChange(bluetoothUri,null,false);
                    return bluetoothUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case BT_DATA:
                long btId = database.insertWithOnConflict(DATABASE_TABLES[1],
                        Bluetooth_Data.BT_NAME, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (btId > 0) {
                    Uri bluetoothUri = ContentUris.withAppendedId(
                            Bluetooth_Data.CONTENT_URI, btId);
                    getContext().getContentResolver().notifyChange(bluetoothUri,null,false);
                    return bluetoothUri;
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
     *
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.bluetooth";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.bluetooth";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[0],
                BT_DEV);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", BT_DEV_ID);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[1],
                BT_DATA);
        sUriMatcher.addURI(Bluetooth_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", BT_DATA_ID);

        bluetoothDeviceMap = new HashMap<String, String>();
        bluetoothDeviceMap.put(Bluetooth_Sensor._ID, Bluetooth_Sensor._ID);
        bluetoothDeviceMap.put(Bluetooth_Sensor.TIMESTAMP,
                Bluetooth_Sensor.TIMESTAMP);
        bluetoothDeviceMap.put(Bluetooth_Sensor.DEVICE_ID,
                Bluetooth_Sensor.DEVICE_ID);
        bluetoothDeviceMap.put(Bluetooth_Sensor.BT_ADDRESS,
                Bluetooth_Sensor.BT_ADDRESS);
        bluetoothDeviceMap.put(Bluetooth_Sensor.BT_NAME,
                Bluetooth_Sensor.BT_NAME);

        bluetoothDataMap = new HashMap<String, String>();
        bluetoothDataMap.put(Bluetooth_Data._ID, Bluetooth_Data._ID);
        bluetoothDataMap
                .put(Bluetooth_Data.TIMESTAMP, Bluetooth_Data.TIMESTAMP);
        bluetoothDataMap
                .put(Bluetooth_Data.DEVICE_ID, Bluetooth_Data.DEVICE_ID);
        bluetoothDataMap.put(Bluetooth_Data.BT_ADDRESS,
                Bluetooth_Data.BT_ADDRESS);
        bluetoothDataMap.put(Bluetooth_Data.BT_NAME, Bluetooth_Data.BT_NAME);
        bluetoothDataMap.put(Bluetooth_Data.BT_RSSI, Bluetooth_Data.BT_RSSI);
        bluetoothDataMap.put(Bluetooth_Data.BT_LABEL, Bluetooth_Data.BT_LABEL);

        return true;
    }

    /**
     * Query bluetooth entries from the database
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        switch (sUriMatcher.match(uri)) {
            case BT_DEV:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(bluetoothDeviceMap);
                break;
            case BT_DATA:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(bluetoothDataMap);
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
     * Update bluetooth on the database
     */
    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection,
                                   String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case BT_DEV:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case BT_DATA:
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