
package com.aware.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.aware.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * ContentProvider database helper<br/>
 * This class is responsible to make sure we have the most up-to-date database structures from plugins and sensors
 *
 * @author denzil
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private final boolean DEBUG = true;
    private final String TAG = "DatabaseHelper";

    private final String database_name;
    private final String[] database_tables;
    private final String[] table_fields;
    private final int new_version;
    private HashMap<String, String> renamed_columns = new HashMap<>();

    private SQLiteDatabase database;
    private Context mContext;

    /**
     * Get the folder for storing the database.  In a separate method so that it can be
     * customized more easily.
     *
     * @param context Application context
     * @return Folder for Aware databases
     */
    public File getAwareDatabaseDirectory(Context context) {
        File aware_folder;
        if (!context.getResources().getBoolean(R.bool.standalone)) {
            // sdcard/AWARE/     (shareable, does not delete when uninstalling)
            aware_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE").toString());
        } else {
            // sdcard/Android/<app_package_name>/AWARE/    (not shareable, deletes when uninstalling package)
            aware_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE"); //compatible with API 10+
        }
        return aware_folder;
    }

    /**
     * Get a certain database directory.  Thin wrapper over getAwareDatabaseDirectory.
     *
     * @param context       Application context (for getting files dir)
     * @param database_name Name of database we want
     * @return File of database.
     */
    public File getAwareDatabaseFile(Context context, String database_name) {
        return new File(getAwareDatabaseDirectory(context), database_name);
    }

    public DatabaseHelper(Context context, String database_name, CursorFactory cursor_factory, int database_version, String[] database_tables, String[] table_fields) {
        super(context, database_name, cursor_factory, database_version);

        this.database_name = database_name;
        this.database_tables = database_tables;
        this.table_fields = table_fields;
        this.new_version = database_version;
        this.mContext = context;

        File aware_folder = getAwareDatabaseDirectory(context);
        aware_folder.mkdirs();
    }

    public void setRenamedColumns(HashMap<String, String> renamed) {
        renamed_columns = renamed;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DEBUG) Log.w(TAG, "Database in use: " + db.getPath());
        for (int i = 0; i < database_tables.length; i++) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + database_tables[i] + " (" + table_fields[i] + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS time_device ON " + database_tables[i] + " (timestamp, device_id);");
        }
        db.setVersion(new_version);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DEBUG) Log.w(TAG, "Upgrading database: " + db.getPath());

        db.beginTransaction();
        for (int i = 0; i < database_tables.length; i++) {

            //Create a new table if doesn't exist
            db.execSQL("CREATE TABLE IF NOT EXISTS " + database_tables[i] + " (" + table_fields[i] + ");");

            //Modify existing tables if there are changes, while retaining old data. This also works for brand new tables, where nothing is changed.
            List<String> columns = getColumns(db, database_tables[i]);
            db.execSQL("ALTER TABLE " + database_tables[i] + " RENAME TO temp_" + database_tables[i] + ";");
            db.execSQL("CREATE TABLE " + database_tables[i] + " (" + table_fields[i] + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS time_device ON " + database_tables[i] + " (timestamp, device_id);");

            columns.retainAll(getColumns(db, database_tables[i]));

            String cols = TextUtils.join(",", columns);
            String new_cols = cols;

            if (renamed_columns.size() > 0) {
                for (String key : renamed_columns.keySet()) {
                    if (DEBUG) Log.d(TAG, "Renaming: " + key + " -> " + renamed_columns.get(key));
                    new_cols = new_cols.replace(key, renamed_columns.get(key));
                }
            }

            //restore old data back
            if (DEBUG)
                Log.d(TAG, String.format("INSERT INTO %s (%s) SELECT %s from temp_%s;", database_tables[i], new_cols, cols, database_tables[i]));

            db.execSQL(String.format("INSERT INTO %s (%s) SELECT %s from temp_%s;", database_tables[i], new_cols, cols, database_tables[i]));
            db.execSQL("DROP TABLE temp_" + database_tables[i] + ";");
        }
        db.setVersion(new_version);
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    /**
     * Creates a String of a JSONArray representation of a database cursor result
     *
     * @param crs
     * @return String
     */
    public static String cursorToString(Cursor crs) {
        JSONArray arr = new JSONArray();
        crs.moveToFirst();
        while (!crs.isAfterLast()) {
            int nColumns = crs.getColumnCount();
            JSONObject row = new JSONObject();
            for (int i = 0; i < nColumns; i++) {
                String colName = crs.getColumnName(i);
                if (colName != null) {
                    try {
                        switch (crs.getType(i)) {
                            case Cursor.FIELD_TYPE_BLOB:
                                row.put(colName, crs.getBlob(i).toString());
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                row.put(colName, crs.getDouble(i));
                                break;
                            case Cursor.FIELD_TYPE_INTEGER:
                                row.put(colName, crs.getLong(i));
                                break;
                            case Cursor.FIELD_TYPE_NULL:
                                row.put(colName, null);
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                row.put(colName, crs.getString(i));
                                break;
                        }
                    } catch (JSONException e) {
                    }
                }
            }
            arr.put(row);
            if (!crs.moveToNext()) break;
        }
        return arr.toString();
    }

    public static List<String> getColumns(SQLiteDatabase db, String tableName) {
        List<String> ar = null;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT * FROM " + tableName + " LIMIT 1", null);
            if (c != null) {
                ar = new ArrayList<>(Arrays.asList(c.getColumnNames()));
            }
        } catch (Exception e) {
            Log.v(tableName, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (c != null) c.close();
        }
        return ar;
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        if (database != null) {
            if (!database.isOpen()) {
                database = null;
            } else if (!database.isReadOnly()) {
                //Database is ready, return it for efficiency
                return database;
            }
        }

        //Get reference to database file, we might not have it.
        File database_file = getAwareDatabaseFile(mContext, database_name);
        try {
            SQLiteDatabase current_database = SQLiteDatabase.openDatabase(database_file.getPath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
            int current_version = current_database.getVersion();

            if (current_version != new_version) {
                if (current_version == 0) {
                    onCreate(current_database);
                } else {
                    onUpgrade(current_database, current_version, new_version);
                }
            }
            onOpen(current_database);
            database = current_database;
            return database;
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        if (database != null) {
            if (!database.isOpen()) {
                database = null;
            } else if (!database.isReadOnly()) {
                //Database is ready, return it for efficiency
                return database;
            }
        }

        try {
            return getWritableDatabase();
        } catch (SQLException e) {
            //we will try to open it read-only as requested.
        }

        //Get reference to database file, we might not have it.
        File database_file = getAwareDatabaseFile(mContext, database_name);
        try {
            SQLiteDatabase current_database = SQLiteDatabase.openDatabase(database_file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            onOpen(current_database);
            database = current_database;
            return database;
        } catch (SQLException e) {
            return null;
        }
    }
}