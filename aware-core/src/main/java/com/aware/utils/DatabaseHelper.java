
package com.aware.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
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

    private final boolean DEBUG = false;

    private final String TAG = "DatabaseHelper";

    private final String database_name;
    private final String[] database_tables;
    private final String[] table_fields;
    private final int new_version;

    private HashMap<String, String> renamed_columns = new HashMap<>();

    private Context mContext;
    private SQLiteDatabase database;

    private SQLiteDatabase getDatabase() {
        //Avoid multiple instances of the same database object, singleton enforced
        if (database != null) {

            if (DEBUG)
                Log.d(TAG, "Already initialised database: " + database.getPath());

            return database;
        }

        File aware_folder;
        if (!mContext.getResources().getBoolean(R.bool.standalone)) {
            aware_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE").toString()); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            aware_folder = new File(ContextCompat.getExternalFilesDirs(mContext, null)[0] + "/AWARE"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }

        if (!aware_folder.exists()) {
            aware_folder.mkdirs();
        }

        if (DEBUG)
            Log.d(TAG, "Initialising database: " + new File(aware_folder, this.database_name).getPath());

        return SQLiteDatabase.openOrCreateDatabase(new File(aware_folder, this.database_name).getPath(), null);
    }

    public DatabaseHelper(Context context, String database_name, CursorFactory cursor_factory, int database_version, String[] database_tables, String[] table_fields) {
        super(context, database_name, cursor_factory, database_version);

        if (DEBUG)
            Log.d(TAG, "Loading: " + database_name + "... version: " + database_version);

        this.mContext = context;
        this.database_name = database_name;
        this.database_tables = database_tables;
        this.table_fields = table_fields;
        this.new_version = database_version;

        database = getWritableDatabase();
    }

    public void setRenamedColumns(HashMap<String, String> renamed) {
        renamed_columns = renamed;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DEBUG) Log.w(TAG, "Creating database: " + db.getPath());

        for (int i = 0; i < database_tables.length; i++) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + database_tables[i] + " (" + table_fields[i] + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS time_device ON " + database_tables[i] + " (timestamp, device_id);");
        }
        db.setVersion(new_version);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DEBUG) Log.w(TAG, "Upgrading database: " + db.getPath());

        for (int i = 0; i < database_tables.length; i++) {
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
    }

    /**
     * Creates a String of a JSONArray representation of a database cursor result
     *
     * @param cursor
     * @return String
     */
    public static String cursorToString(Cursor cursor) {
        JSONArray jsonArray = new JSONArray();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int nColumns = cursor.getColumnCount();
                JSONObject row = new JSONObject();
                for (int i = 0; i < nColumns; i++) {
                    String colName = cursor.getColumnName(i);
                    if (colName != null) {
                        try {
                            switch (cursor.getType(i)) {
                                case Cursor.FIELD_TYPE_BLOB:
                                    row.put(colName, cursor.getBlob(i).toString());
                                    break;
                                case Cursor.FIELD_TYPE_FLOAT:
                                    row.put(colName, cursor.getDouble(i));
                                    break;
                                case Cursor.FIELD_TYPE_INTEGER:
                                    row.put(colName, cursor.getLong(i));
                                    break;
                                case Cursor.FIELD_TYPE_NULL:
                                    row.put(colName, null);
                                    break;
                                case Cursor.FIELD_TYPE_STRING:
                                    row.put(colName, cursor.getString(i));
                                    break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
                jsonArray.put(row);
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) cursor.close();

        return jsonArray.toString();
    }

    private static List<String> getColumns(SQLiteDatabase db, String tableName) {
        List<String> columns = null;
        Cursor database_meta = null;
        try {
            database_meta = db.rawQuery("SELECT * FROM " + tableName + " LIMIT 1", null);
            if (database_meta != null) {
                columns = new ArrayList<>(Arrays.asList(database_meta.getColumnNames()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (database_meta != null) database_meta.close();
        }
        return columns;
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        if (database != null) {
            if (!database.isOpen()) {
                database = null;
            } else if (!database.isReadOnly()) {
                return database;
            }
        }
        try {
            database = getDatabase();
            int current_version = database.getVersion();
            if (current_version != new_version) {

                database.beginTransaction();

                try {
                    if (current_version == 0) {
                        onCreate(database);
                    } else {
                        onUpgrade(database, current_version, new_version);
                    }

                    database.setTransactionSuccessful();

                } finally {
                    database.endTransaction();
                }
            }
            onOpen(database);
            return database;
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public synchronized SQLiteDatabase getReadableDatabase() {
        if (database != null) {
            if (!database.isOpen()) {
                database = null;
            } else {
                return database;
            }
        }
        return getWritableDatabase();
    }
}