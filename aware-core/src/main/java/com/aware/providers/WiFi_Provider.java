
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
 * AWARE WiFi Content Provider Allows you to access all the recorded wifi AP
 * devices on the database Database is located at the SDCard : /AWARE/wifi.db
 *
 * @author denzil
 */
public class WiFi_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 6;

    /**
     * Authority of WiFi content provider
     */
    public static String AUTHORITY = "com.aware.provider.wifi";

    // ContentProvider query paths
    private static final int WIFI_DATA = 1;
    private static final int WIFI_DATA_ID = 2;
    private static final int WIFI_DEV = 3;
    private static final int WIFI_DEV_ID = 4;

    /**
     * WiFi device info
     *
     * @author df
     */
    public static final class WiFi_Sensor implements BaseColumns {
        private WiFi_Sensor() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + WiFi_Provider.AUTHORITY + "/sensor_wifi");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.wifi.sensor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.wifi.sensor";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String MAC_ADDRESS = "mac_address";
        public static final String BSSID = "bssid";
        public static final String SSID = "ssid";
    }

    /**
     * Logged WiFi data
     *
     * @author df
     */
    public static final class WiFi_Data implements BaseColumns {
        private WiFi_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + WiFi_Provider.AUTHORITY + "/wifi");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.wifi.data";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.wifi.data";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String BSSID = "bssid";
        public static final String SSID = "ssid";
        public static final String SECURITY = "security";
        public static final String FREQUENCY = "frequency";
        public static final String RSSI = "rssi";
        public static final String LABEL = "label";
    }

    public static String DATABASE_NAME = "wifi.db";

    public static final String[] DATABASE_TABLES = {"wifi", "sensor_wifi"};

    public static final String[] TABLES_FIELDS = {
            // data
            WiFi_Data._ID + " integer primary key autoincrement,"
                    + WiFi_Data.TIMESTAMP + " real default 0,"
                    + WiFi_Data.DEVICE_ID + " text default '',"
                    + WiFi_Data.BSSID + " text default '',"
                    + WiFi_Data.SSID + " text default '',"
                    + WiFi_Data.SECURITY + " text default '',"
                    + WiFi_Data.FREQUENCY + " integer default 0,"
                    + WiFi_Data.RSSI + " integer default 0,"
                    + WiFi_Data.LABEL + " text default ''",
            // device
            WiFi_Sensor._ID + " integer primary key autoincrement,"
                    + WiFi_Sensor.TIMESTAMP + " real default 0,"
                    + WiFi_Sensor.DEVICE_ID + " text default '',"
                    + WiFi_Sensor.MAC_ADDRESS + " text default '',"
                    + WiFi_Data.SSID + " text default '',"
                    + WiFi_Data.BSSID + " text default ''"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> wifiDataMap = null;
    private HashMap<String, String> wifiDeviceMap = null;
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
            case WIFI_DATA:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case WIFI_DEV:
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
            case WIFI_DATA:
                return WiFi_Data.CONTENT_TYPE;
            case WIFI_DATA_ID:
                return WiFi_Data.CONTENT_ITEM_TYPE;
            case WIFI_DEV:
                return WiFi_Data.CONTENT_TYPE;
            case WIFI_DEV_ID:
                return WiFi_Data.CONTENT_ITEM_TYPE;
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
            case WIFI_DATA:
                long wifiID = database.insertWithOnConflict(DATABASE_TABLES[0],
                        WiFi_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (wifiID > 0) {
                    Uri wifiUri = ContentUris.withAppendedId(WiFi_Data.CONTENT_URI,
                            wifiID);
                    getContext().getContentResolver().notifyChange(wifiUri, null, false);
                    return wifiUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case WIFI_DEV:
                long wifiDevID = database.insertWithOnConflict(DATABASE_TABLES[1],
                        WiFi_Sensor.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (wifiDevID > 0) {
                    Uri wifiUri = ContentUris.withAppendedId(
                            WiFi_Sensor.CONTENT_URI, wifiDevID);
                    getContext().getContentResolver().notifyChange(wifiUri, null, false);
                    return wifiUri;
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
        AUTHORITY = context.getPackageName() + ".provider.wifi";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.wifi";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(WiFi_Provider.AUTHORITY, DATABASE_TABLES[0],
                WIFI_DATA);
        sUriMatcher.addURI(WiFi_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#",
                WIFI_DATA_ID);
        sUriMatcher.addURI(WiFi_Provider.AUTHORITY, DATABASE_TABLES[1],
                WIFI_DEV);
        sUriMatcher.addURI(WiFi_Provider.AUTHORITY, DATABASE_TABLES[1] + "/#",
                WIFI_DEV_ID);

        wifiDataMap = new HashMap<String, String>();
        wifiDataMap.put(WiFi_Data._ID, WiFi_Data._ID);
        wifiDataMap.put(WiFi_Data.TIMESTAMP, WiFi_Data.TIMESTAMP);
        wifiDataMap.put(WiFi_Data.DEVICE_ID, WiFi_Data.DEVICE_ID);
        wifiDataMap.put(WiFi_Data.BSSID, WiFi_Data.BSSID);
        wifiDataMap.put(WiFi_Data.SSID, WiFi_Data.SSID);
        wifiDataMap.put(WiFi_Data.SECURITY, WiFi_Data.SECURITY);
        wifiDataMap.put(WiFi_Data.FREQUENCY, WiFi_Data.FREQUENCY);
        wifiDataMap.put(WiFi_Data.RSSI, WiFi_Data.RSSI);
        wifiDataMap.put(WiFi_Data.LABEL, WiFi_Data.LABEL);

        wifiDeviceMap = new HashMap<String, String>();
        wifiDeviceMap.put(WiFi_Sensor._ID, WiFi_Sensor._ID);
        wifiDeviceMap.put(WiFi_Sensor.DEVICE_ID, WiFi_Sensor.DEVICE_ID);
        wifiDeviceMap.put(WiFi_Sensor.TIMESTAMP, WiFi_Sensor.TIMESTAMP);
        wifiDeviceMap.put(WiFi_Sensor.MAC_ADDRESS, WiFi_Sensor.MAC_ADDRESS);
        wifiDeviceMap.put(WiFi_Sensor.BSSID, WiFi_Sensor.BSSID);
        wifiDeviceMap.put(WiFi_Sensor.SSID, WiFi_Sensor.SSID);

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
            case WIFI_DATA:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(wifiDataMap);
                break;
            case WIFI_DEV:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(wifiDeviceMap);
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
     * Update on the database
     */
    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case WIFI_DATA:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case WIFI_DEV:
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
