
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
 * AWARE MQTT Content Provider Allows you to access all the recorded MQTT
 * messages and subscribed topics on the database Database is located at the
 * SDCard : /AWARE/mqtt.db
 * 
 * @author denzil
 * 
 */
public class Mqtt_Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 3;

	/**
	 * Authority of MQTT content provider
	 */
	public static String AUTHORITY = "com.aware.provider.mqtt";

	// ContentProvider query paths
	private static final int MQTT = 1;
	private static final int MQTT_ID = 2;
	private static final int MQTT_SUBSCRIPTION = 3;
	private static final int MQTT_SUBSCRIPTION_ID = 4;

	public static final class Mqtt_Messages implements BaseColumns {
		private Mqtt_Messages() {
		}

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Mqtt_Provider.AUTHORITY + "/mqtt_messages");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.mqtt.messages";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.mqtt.messages";

		public static final String MQTT_ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String TOPIC = "topic";
		public static final String MESSAGE = "message";
		public static final String STATUS = "status";
	}

	public static final class Mqtt_Subscriptions implements BaseColumns {
		private Mqtt_Subscriptions() {
		}

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ Mqtt_Provider.AUTHORITY + "/mqtt_subscriptions");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.mqtt.subscriptions";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.mqtt.subscriptions";

		public static final String MQTT_SUBSCRIPTION_ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String TOPIC = "topic";
	}

	public static String DATABASE_NAME = "mqtt.db";

	public static final String[] DATABASE_TABLES = { "mqtt_messages",
			"mqtt_subscriptions" };
	public static final String[] TABLES_FIELDS = {
			// mqtt messages
			Mqtt_Messages._ID + " integer primary key autoincrement,"
					+ Mqtt_Messages.TIMESTAMP + " real default 0,"
					+ Mqtt_Messages.DEVICE_ID + " text default '',"
					+ Mqtt_Messages.TOPIC + " text default '',"
					+ Mqtt_Messages.MESSAGE + " text default '',"
					+ Mqtt_Messages.STATUS + " integer default 0",
			// mqtt subscriptions
			Mqtt_Subscriptions._ID + " integer primary key autoincrement,"
					+ Mqtt_Subscriptions.TIMESTAMP + " real default 0,"
					+ Mqtt_Subscriptions.DEVICE_ID + " text default '',"
					+ Mqtt_Subscriptions.TOPIC + " text default ''" };

	private UriMatcher sUriMatcher = null;
	private HashMap<String, String> messagesMap = null;
	private HashMap<String, String> subscriptionMap = null;

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

		int count;
		switch (sUriMatcher.match(uri)) {
		case MQTT:
			count = database.delete(DATABASE_TABLES[0], selection,
					selectionArgs);
			break;
		case MQTT_SUBSCRIPTION:
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
		case MQTT:
			return Mqtt_Messages.CONTENT_TYPE;
		case MQTT_ID:
			return Mqtt_Messages.CONTENT_ITEM_TYPE;
		case MQTT_SUBSCRIPTION:
			return Mqtt_Subscriptions.CONTENT_TYPE;
		case MQTT_SUBSCRIPTION_ID:
			return Mqtt_Subscriptions.CONTENT_ITEM_TYPE;
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
		case MQTT:
			long mqtt_id = database.insertWithOnConflict(DATABASE_TABLES[0],
					Mqtt_Messages.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
            database.setTransactionSuccessful();
            database.endTransaction();
			if (mqtt_id > 0) {
				Uri mqttUri = ContentUris.withAppendedId(
						Mqtt_Messages.CONTENT_URI, mqtt_id);
				getContext().getContentResolver().notifyChange(mqttUri, null, false);
				return mqttUri;
			}
            database.endTransaction();
			throw new SQLException("Failed to insert row into " + uri);
		case MQTT_SUBSCRIPTION:
			long mqtt_sub_id = database.insertWithOnConflict(DATABASE_TABLES[1],
					Mqtt_Subscriptions.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
            database.setTransactionSuccessful();
            database.endTransaction();
			if (mqtt_sub_id > 0) {
				Uri mqttSubUri = ContentUris.withAppendedId(
						Mqtt_Subscriptions.CONTENT_URI, mqtt_sub_id);
				getContext().getContentResolver().notifyChange(mqttSubUri, null, false);
				return mqttSubUri;
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
		AUTHORITY = context.getPackageName() + ".provider.mqtt";
		return AUTHORITY;
	}

	@Override
	public boolean onCreate() {
	    AUTHORITY = getContext().getPackageName() + ".provider.mqtt";

	    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Mqtt_Provider.AUTHORITY, DATABASE_TABLES[0], MQTT);
        sUriMatcher.addURI(Mqtt_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#",
                MQTT_ID);
        sUriMatcher.addURI(Mqtt_Provider.AUTHORITY, DATABASE_TABLES[1],
                MQTT_SUBSCRIPTION);
        sUriMatcher.addURI(Mqtt_Provider.AUTHORITY, DATABASE_TABLES[1] + "/#",
                MQTT_SUBSCRIPTION_ID);

        messagesMap = new HashMap<String, String>();
        messagesMap.put(Mqtt_Messages.MQTT_ID, Mqtt_Messages.MQTT_ID);
        messagesMap.put(Mqtt_Messages.TIMESTAMP, Mqtt_Messages.TIMESTAMP);
        messagesMap.put(Mqtt_Messages.DEVICE_ID, Mqtt_Messages.DEVICE_ID);
        messagesMap.put(Mqtt_Messages.MESSAGE, Mqtt_Messages.MESSAGE);
        messagesMap.put(Mqtt_Messages.TOPIC, Mqtt_Messages.TOPIC);
        messagesMap.put(Mqtt_Messages.STATUS, Mqtt_Messages.STATUS);

        subscriptionMap = new HashMap<String, String>();
        subscriptionMap.put(Mqtt_Subscriptions.MQTT_SUBSCRIPTION_ID,
                Mqtt_Subscriptions.MQTT_SUBSCRIPTION_ID);
        subscriptionMap.put(Mqtt_Subscriptions.TIMESTAMP,
                Mqtt_Subscriptions.TIMESTAMP);
        subscriptionMap.put(Mqtt_Subscriptions.DEVICE_ID,
                Mqtt_Subscriptions.DEVICE_ID);
        subscriptionMap.put(Mqtt_Subscriptions.TOPIC, Mqtt_Subscriptions.TOPIC);
	    
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
		case MQTT:
			qb.setTables(DATABASE_TABLES[0]);
			qb.setProjectionMap(messagesMap);
			break;
		case MQTT_SUBSCRIPTION:
			qb.setTables(DATABASE_TABLES[1]);
			qb.setProjectionMap(subscriptionMap);
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
		case MQTT:
			count = database.update(DATABASE_TABLES[0], values, selection,
					selectionArgs);
			break;
		case MQTT_SUBSCRIPTION:
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