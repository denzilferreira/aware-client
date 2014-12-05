/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware.utils;

import java.io.File;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

/**
 * ContentProvider database helper<br/>
 * This class is responsible to make sure we have the most up-to-date database structures from plugins and sensors
 * @author denzil
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {
	
	private final boolean DEBUG = true;
	private final String TAG = "DatabaseHelper";
	
	private final String database_name;
	private final String[] database_tables;
	private final String[] table_fields;
	private final int new_version;
	
	private SQLiteDatabase database = null;
	
	public DatabaseHelper(Context context, String database_name, CursorFactory cursor_factory, int database_version, String[] database_tables, String[] table_fields) {
        super(context, database_name, cursor_factory, database_version);
        
        this.database_name = database_name;
        this.database_tables = database_tables;
        this.table_fields = table_fields;
        this.new_version = database_version;
        
        //Create the folder where all the databases will be stored on external storage
        File folders = new File(Environment.getExternalStorageDirectory()+"/AWARE/");
        folders.mkdirs();
    }
	
	@Override
    public void onCreate(SQLiteDatabase db) {
		if(DEBUG) Log.w(TAG, "Database in use: " + db.getPath());
		
		for (int i=0; i < database_tables.length;i++)
        {
           db.execSQL("CREATE TABLE IF NOT EXISTS "+database_tables[i] +" ("+table_fields[i]+");");
        }
		db.setVersion(new_version);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    	if(DEBUG) Log.w(TAG, "Upgrading database: " + db.getPath());
    	
    	for (int i=0; i < database_tables.length;i++)
        {
            db.execSQL("DROP TABLE IF EXISTS "+database_tables[i]);
        }
    	onCreate(db);
    }
    
    @Override
    public SQLiteDatabase getWritableDatabase() {
    	if( database != null ) {
    		if( ! database.isOpen() ) {
    			database = null;
    		} else if ( ! database.isReadOnly() ) {
    			//Database is ready, return it for efficiency
    			return database;
    		}
    	}
    	
    	//Get reference to database file, we might not have it.
    	File database_file = new File(database_name);
    	try {
    	    SQLiteDatabase current_database = SQLiteDatabase.openDatabase(database_file.getPath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
    	    int current_version = current_database.getVersion();
            
            if( current_version != new_version ) {
                current_database.beginTransaction();
                try {
                    if( current_version == 0 ) {
                        onCreate(current_database);
                    } else {
                        onUpgrade(current_database, current_version, new_version);
                    }
                    current_database.setVersion(new_version);
                    current_database.setTransactionSuccessful();
                }finally {
                    current_database.endTransaction();
                }
            }
            onOpen(current_database);
            database = current_database;
            return database;
    	} catch (SQLException e ) {
    	    return null;
    	}
    }
    
    @Override
    public SQLiteDatabase getReadableDatabase() {
    	if( database != null ) {
    		if( ! database.isOpen() ) {
    			database = null;
    		} else if ( ! database.isReadOnly() ) {
    			//Database is ready, return it for efficiency
    			return database;
    		}
    	}
    	
    	try {
    		return getWritableDatabase();
    	} catch( SQLException e ) {
    		//we will try to open it read-only as requested.
    	}
    	
    	//Get reference to database file, we might not have it.
    	File database_file = new File(database_name);
    	SQLiteDatabase current_database = SQLiteDatabase.openDatabase(database_file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    	onOpen(current_database);
    	database = current_database;
    	return database; 
    }
}