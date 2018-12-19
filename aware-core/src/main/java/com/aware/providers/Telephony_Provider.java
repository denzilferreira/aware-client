
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
 * AWARE Telephony Content Provider Allows you to access recorded telephony,
 * cell and neighbor towers from the database Database is located at the SDCard:
 * /AWARE/telephony.db
 *
 * @author df
 */
public class Telephony_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 9;

    /**
     * Provider authority: com.aware.TelephonyProvider
     */
    public static String AUTHORITY = "com.aware.provider.telephony";

    private static final int TELEPHONY = 1;
    private static final int TELEPHONY_ID = 2;
    private static final int GSM = 3;
    private static final int GSM_ID = 4;
    private static final int NEIGHBOR = 5;
    private static final int NEIGHBOR_ID = 6;
    private static final int CDMA = 7;
    private static final int CDMA_ID = 8;

    /**
     * Telephony data representation
     *
     * @author df
     */
    public static final class Telephony_Data implements BaseColumns {
        private Telephony_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Telephony_Provider.AUTHORITY + "/telephony");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.telephony";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.telephony";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String DATA_ENABLED = "data_enabled";
        public static final String IMEI_MEID_ESN = "imei_meid_esn";
        public static final String SOFTWARE_VERSION = "software_version";
        public static final String LINE_NUMBER = "line_number";
        public static final String NETWORK_COUNTRY_ISO_MCC = "network_country_iso_mcc";
        public static final String NETWORK_OPERATOR_CODE = "network_operator_code";
        public static final String NETWORK_OPERATOR_NAME = "network_operator_name";
        public static final String NETWORK_TYPE = "network_type";
        public static final String PHONE_TYPE = "phone_type";
        public static final String SIM_STATE = "sim_state";
        public static final String SIM_OPERATOR_CODE = "sim_operator_code";
        public static final String SIM_OPERATOR_NAME = "sim_operator_name";
        public static final String SIM_SERIAL = "sim_serial";
        public static final String SUBSCRIBER_ID = "subscriber_id";
    }

    /**
     * GSM data representation
     *
     * @author df
     */
    public static final class GSM_Data implements BaseColumns {
        private GSM_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Telephony_Provider.AUTHORITY + "/gsm");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.gsm";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.gsm";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String CID = "cid";
        public static final String LAC = "lac";
        public static final String PSC = "psc";
        public static final String SIGNAL_STRENGTH = "signal_strength";
        public static final String GSM_BER = "bit_error_rate";
    }

    /**
     * GSM neighbor data representation
     *
     * @author df
     */
    public static final class GSM_Neighbors_Data implements BaseColumns {
        private GSM_Neighbors_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Telephony_Provider.AUTHORITY + "/gsm_neighbor");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.gsm.neighbor";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.gsm.neighbor";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String CID = "cid";
        public static final String LAC = "lac";
        public static final String PSC = "psc";
        public static final String SIGNAL_STRENGTH = "signal_strength";
    }

    /**
     * CDMA data representation
     *
     * @author df
     */
    public static final class CDMA_Data implements BaseColumns {
        private CDMA_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Telephony_Provider.AUTHORITY + "/cdma");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.cdma";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.cdma";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String BASE_STATION_ID = "base_station_id";
        public static final String BASE_STATION_LATITUDE = "double_base_station_latitude";
        public static final String BASE_STATION_LONGITUDE = "double_base_station_longitude";
        public static final String NETWORK_ID = "network_id";
        public static final String SYSTEM_ID = "system_id";
        public static final String SIGNAL_STRENGTH = "signal_strength";
        public static final String CDMA_ECIO = "cdma_ecio";
        public static final String EVDO_DBM = "evdo_dbm";
        public static final String EVDO_ECIO = "evdo_ecio";
        public static final String EVDO_SNR = "evdo_snr";
    }

    public static String DATABASE_NAME = "telephony.db";

    public static final String[] DATABASE_TABLES = {"telephony", "gsm",
            "gsm_neighbor", "cdma"};

    public static final String[] TABLES_FIELDS = {
            // telephony
            Telephony_Data._ID + " integer primary key autoincrement,"
                    + Telephony_Data.TIMESTAMP + " real default 0,"
                    + Telephony_Data.DEVICE_ID + " text default '',"
                    + Telephony_Data.DATA_ENABLED + " integer default 0,"
                    + Telephony_Data.IMEI_MEID_ESN + " text default '',"
                    + Telephony_Data.SOFTWARE_VERSION + " text default '',"
                    + Telephony_Data.LINE_NUMBER + " text default '',"
                    + Telephony_Data.NETWORK_COUNTRY_ISO_MCC
                    + " text default '',"
                    + Telephony_Data.NETWORK_OPERATOR_CODE
                    + " text default '',"
                    + Telephony_Data.NETWORK_OPERATOR_NAME
                    + " text default ''," + Telephony_Data.NETWORK_TYPE
                    + " integer default 0," + Telephony_Data.PHONE_TYPE
                    + " integer default 0," + Telephony_Data.SIM_STATE
                    + " integer default 0," + Telephony_Data.SIM_OPERATOR_CODE
                    + " text default ''," + Telephony_Data.SIM_OPERATOR_NAME
                    + " text default ''," + Telephony_Data.SIM_SERIAL
                    + " text default ''," + Telephony_Data.SUBSCRIBER_ID
                    + " text default ''",
            // GSM data
            GSM_Data._ID + " integer primary key autoincrement,"
                    + GSM_Data.TIMESTAMP + " real default 0,"
                    + GSM_Data.DEVICE_ID + " text default ''," + GSM_Data.CID
                    + " integer default -1," + GSM_Data.LAC
                    + " integer default -1," + GSM_Data.PSC
                    + " integer default 0," + GSM_Data.SIGNAL_STRENGTH
                    + " integer default -1," + GSM_Data.GSM_BER
                    + " integer default -1",
            // GSM neighbors data
            GSM_Neighbors_Data._ID + " integer primary key autoincrement,"
                    + GSM_Neighbors_Data.TIMESTAMP + " real default 0,"
                    + GSM_Neighbors_Data.DEVICE_ID + " text default '',"
                    + GSM_Neighbors_Data.CID + " integer default -1,"
                    + GSM_Neighbors_Data.LAC + " integer default -1,"
                    + GSM_Neighbors_Data.PSC + " integer default -1,"
                    + GSM_Neighbors_Data.SIGNAL_STRENGTH + " integer default 0",
            // CDMA data
            CDMA_Data._ID + " integer primary key autoincrement,"
                    + CDMA_Data.TIMESTAMP + " real default 0,"
                    + CDMA_Data.DEVICE_ID + " text default '',"
                    + CDMA_Data.BASE_STATION_ID + " integer default 0,"
                    + CDMA_Data.BASE_STATION_LATITUDE + " real default 0,"
                    + CDMA_Data.BASE_STATION_LONGITUDE + " real default 0,"
                    + CDMA_Data.NETWORK_ID + " integer default 0,"
                    + CDMA_Data.SYSTEM_ID + " integer default 0,"
                    + CDMA_Data.SIGNAL_STRENGTH + " integer default -1,"
                    + CDMA_Data.CDMA_ECIO + " integer default -1,"
                    + CDMA_Data.EVDO_DBM + " integer default -1,"
                    + CDMA_Data.EVDO_ECIO + " integer default -1,"
                    + CDMA_Data.EVDO_SNR + " integer default -1"};

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> telephonyMap = null;
    private HashMap<String, String> gsmMap = null;
    private HashMap<String, String> gsmNeighborsMap = null;
    private HashMap<String, String> cdmaMap = null;

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

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case TELEPHONY:
                count = database.delete(DATABASE_TABLES[0], selection,
                        selectionArgs);
                break;
            case GSM:
                count = database.delete(DATABASE_TABLES[1], selection,
                        selectionArgs);
                break;
            case NEIGHBOR:
                count = database.delete(DATABASE_TABLES[2], selection,
                        selectionArgs);
                break;
            case CDMA:
                count = database.delete(DATABASE_TABLES[3], selection,
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
            case TELEPHONY:
                return Telephony_Data.CONTENT_TYPE;
            case TELEPHONY_ID:
                return Telephony_Data.CONTENT_ITEM_TYPE;
            case GSM:
                return GSM_Data.CONTENT_TYPE;
            case GSM_ID:
                return GSM_Data.CONTENT_ITEM_TYPE;
            case NEIGHBOR:
                return GSM_Neighbors_Data.CONTENT_TYPE;
            case NEIGHBOR_ID:
                return GSM_Neighbors_Data.CONTENT_ITEM_TYPE;
            case CDMA:
                return CDMA_Data.CONTENT_TYPE;
            case CDMA_ID:
                return CDMA_Data.CONTENT_ITEM_TYPE;
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
            case TELEPHONY:
                long tele_id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Telephony_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (tele_id > 0) {
                    Uri tele_uri = ContentUris.withAppendedId(
                            Telephony_Data.CONTENT_URI, tele_id);
                    getContext().getContentResolver().notifyChange(tele_uri, null, false);
                    return tele_uri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case GSM:
                long gsm_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        GSM_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (gsm_id > 0) {
                    Uri gsm_uri = ContentUris.withAppendedId(GSM_Data.CONTENT_URI,
                            gsm_id);
                    getContext().getContentResolver().notifyChange(gsm_uri, null, false);
                    return gsm_uri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case NEIGHBOR:
                long neighbor_id = database.insertWithOnConflict(DATABASE_TABLES[2],
                        GSM_Neighbors_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (neighbor_id > 0) {
                    Uri neighbor_uri = ContentUris.withAppendedId(
                            GSM_Neighbors_Data.CONTENT_URI, neighbor_id);
                    getContext().getContentResolver().notifyChange(neighbor_uri,null, false);
                    return neighbor_uri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case CDMA:
                long cdma_id = database.insertWithOnConflict(DATABASE_TABLES[3],
                        CDMA_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (cdma_id > 0) {
                    Uri cdma_uri = ContentUris.withAppendedId(
                            CDMA_Data.CONTENT_URI, cdma_id);
                    getContext().getContentResolver().notifyChange(cdma_uri, null, false);
                    return cdma_uri;
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
        AUTHORITY = context.getPackageName() + ".provider.telephony";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.telephony";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[0],
                TELEPHONY);
        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[0]
                + "/#", TELEPHONY_ID);

        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[1],
                GSM);
        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[1]
                + "/#", GSM_ID);

        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[2],
                NEIGHBOR);
        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[2]
                + "/#", NEIGHBOR_ID);

        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[3],
                CDMA);
        sUriMatcher.addURI(Telephony_Provider.AUTHORITY, DATABASE_TABLES[3]
                + "/#", CDMA_ID);

        telephonyMap = new HashMap<String, String>();
        telephonyMap.put(Telephony_Data._ID, Telephony_Data._ID);
        telephonyMap.put(Telephony_Data.TIMESTAMP, Telephony_Data.TIMESTAMP);
        telephonyMap.put(Telephony_Data.DEVICE_ID, Telephony_Data.DEVICE_ID);
        telephonyMap.put(Telephony_Data.DATA_ENABLED,
                Telephony_Data.DATA_ENABLED);
        telephonyMap.put(Telephony_Data.IMEI_MEID_ESN,
                Telephony_Data.IMEI_MEID_ESN);
        telephonyMap.put(Telephony_Data.SOFTWARE_VERSION,
                Telephony_Data.SOFTWARE_VERSION);
        telephonyMap
                .put(Telephony_Data.LINE_NUMBER, Telephony_Data.LINE_NUMBER);
        telephonyMap.put(Telephony_Data.NETWORK_COUNTRY_ISO_MCC,
                Telephony_Data.NETWORK_COUNTRY_ISO_MCC);
        telephonyMap.put(Telephony_Data.NETWORK_OPERATOR_CODE,
                Telephony_Data.NETWORK_OPERATOR_CODE);
        telephonyMap.put(Telephony_Data.NETWORK_OPERATOR_NAME,
                Telephony_Data.NETWORK_OPERATOR_NAME);
        telephonyMap.put(Telephony_Data.NETWORK_TYPE,
                Telephony_Data.NETWORK_TYPE);
        telephonyMap.put(Telephony_Data.PHONE_TYPE, Telephony_Data.PHONE_TYPE);
        telephonyMap.put(Telephony_Data.SIM_STATE, Telephony_Data.SIM_STATE);
        telephonyMap.put(Telephony_Data.SIM_OPERATOR_CODE,
                Telephony_Data.SIM_OPERATOR_CODE);
        telephonyMap.put(Telephony_Data.SIM_OPERATOR_NAME,
                Telephony_Data.SIM_OPERATOR_NAME);
        telephonyMap.put(Telephony_Data.SIM_SERIAL, Telephony_Data.SIM_SERIAL);
        telephonyMap.put(Telephony_Data.SUBSCRIBER_ID,
                Telephony_Data.SUBSCRIBER_ID);

        gsmMap = new HashMap<String, String>();
        gsmMap.put(GSM_Data._ID, GSM_Data._ID);
        gsmMap.put(GSM_Data.TIMESTAMP, GSM_Data.TIMESTAMP);
        gsmMap.put(GSM_Data.DEVICE_ID, GSM_Data.DEVICE_ID);
        gsmMap.put(GSM_Data.CID, GSM_Data.CID);
        gsmMap.put(GSM_Data.LAC, GSM_Data.LAC);
        gsmMap.put(GSM_Data.PSC, GSM_Data.PSC);
        gsmMap.put(GSM_Data.SIGNAL_STRENGTH, GSM_Data.SIGNAL_STRENGTH);
        gsmMap.put(GSM_Data.GSM_BER, GSM_Data.GSM_BER);

        gsmNeighborsMap = new HashMap<String, String>();
        gsmNeighborsMap.put(GSM_Neighbors_Data._ID, GSM_Neighbors_Data._ID);
        gsmNeighborsMap.put(GSM_Neighbors_Data.TIMESTAMP,
                GSM_Neighbors_Data.TIMESTAMP);
        gsmNeighborsMap.put(GSM_Neighbors_Data.DEVICE_ID,
                GSM_Neighbors_Data.DEVICE_ID);
        gsmNeighborsMap.put(GSM_Neighbors_Data.CID, GSM_Neighbors_Data.CID);
        gsmNeighborsMap.put(GSM_Neighbors_Data.LAC, GSM_Neighbors_Data.LAC);
        gsmNeighborsMap.put(GSM_Neighbors_Data.PSC, GSM_Neighbors_Data.PSC);
        gsmNeighborsMap.put(GSM_Neighbors_Data.SIGNAL_STRENGTH,
                GSM_Neighbors_Data.SIGNAL_STRENGTH);

        cdmaMap = new HashMap<String, String>();
        cdmaMap.put(CDMA_Data._ID, CDMA_Data._ID);
        cdmaMap.put(CDMA_Data.TIMESTAMP, CDMA_Data.TIMESTAMP);
        cdmaMap.put(CDMA_Data.DEVICE_ID, CDMA_Data.DEVICE_ID);
        cdmaMap.put(CDMA_Data.BASE_STATION_ID, CDMA_Data.BASE_STATION_ID);
        cdmaMap.put(CDMA_Data.BASE_STATION_LATITUDE,
                CDMA_Data.BASE_STATION_LATITUDE);
        cdmaMap.put(CDMA_Data.BASE_STATION_LONGITUDE,
                CDMA_Data.BASE_STATION_LONGITUDE);
        cdmaMap.put(CDMA_Data.NETWORK_ID, CDMA_Data.NETWORK_ID);
        cdmaMap.put(CDMA_Data.SYSTEM_ID, CDMA_Data.SYSTEM_ID);
        cdmaMap.put(CDMA_Data.SIGNAL_STRENGTH, CDMA_Data.SIGNAL_STRENGTH);
        cdmaMap.put(CDMA_Data.CDMA_ECIO, CDMA_Data.CDMA_ECIO);
        cdmaMap.put(CDMA_Data.EVDO_DBM, CDMA_Data.EVDO_DBM);
        cdmaMap.put(CDMA_Data.EVDO_ECIO, CDMA_Data.EVDO_ECIO);
        cdmaMap.put(CDMA_Data.EVDO_SNR, CDMA_Data.EVDO_SNR);

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
            case TELEPHONY:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(telephonyMap);
                break;
            case GSM:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(gsmMap);
                break;
            case NEIGHBOR:
                qb.setTables(DATABASE_TABLES[2]);
                qb.setProjectionMap(gsmNeighborsMap);
                break;
            case CDMA:
                qb.setTables(DATABASE_TABLES[3]);
                qb.setProjectionMap(cdmaMap);
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
            case TELEPHONY:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case GSM:
                count = database.update(DATABASE_TABLES[1], values, selection,
                        selectionArgs);
                break;
            case NEIGHBOR:
                count = database.update(DATABASE_TABLES[2], values, selection,
                        selectionArgs);
                break;
            case CDMA:
                count = database.update(DATABASE_TABLES[3], values, selection,
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