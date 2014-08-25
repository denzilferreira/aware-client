package com.aware.stats;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.BatteryManager;

import com.aware.providers.Battery_Provider.Battery_Data;

/**
 * Provides statistics on battery usage
 * @author dferreira
 *
 */
public class Stats_Battery {
	
	/**
	 * Provides for how long did the battery spent charging between two dates
	 * @param resolver
	 * @param timestamp_start
	 * @param timestamp_end
	 * @return total_time_charging in milliseconds
	 */
	public static long getTimeCharging(ContentResolver resolver, long timestamp_start, long timestamp_end) {
		
		long total_time_charging = 0;
		
		String selection = Battery_Data.TIMESTAMP + " between " + timestamp_start + " AND " + timestamp_end;
        Cursor battery_raw = resolver.query(Battery_Data.CONTENT_URI, null, selection, null, Battery_Data.TIMESTAMP + " ASC");
		if( battery_raw != null && battery_raw.moveToFirst() ) {
			
		    int last_battery_status = battery_raw.getInt(battery_raw.getColumnIndex(Battery_Data.STATUS));
			long last_battery_timestamp = battery_raw.getLong(battery_raw.getColumnIndex(Battery_Data.TIMESTAMP));
			
			while(battery_raw.moveToNext()) {
				int battery_status = battery_raw.getInt(battery_raw.getColumnIndex(Battery_Data.STATUS));
				long battery_timestamp = battery_raw.getLong(battery_raw.getColumnIndex(Battery_Data.TIMESTAMP));
				
				if( battery_status == BatteryManager.BATTERY_STATUS_CHARGING && last_battery_status == BatteryManager.BATTERY_STATUS_CHARGING ) { //continuing charging 
					total_time_charging += battery_timestamp-last_battery_timestamp;
				}
				last_battery_status = battery_status;
				last_battery_timestamp = battery_timestamp;
			}
		}
		if( battery_raw != null && ! battery_raw.isClosed() ) battery_raw.close();
		return total_time_charging;
	}
	
	/**
	 * For how long did the battery last between two dates
	 * @param resolver
	 * @param timestamp_start
	 * @param timestamp_end
	 * @return total_time_discharging in milliseconds
	 */
	public static long getTimeNotCharging(ContentResolver resolver, long timestamp_start, long timestamp_end) {
		long total_time_discharging = 0;
		
		String selection = Battery_Data.TIMESTAMP + " between " + timestamp_start + " AND " + timestamp_end;
        Cursor battery_raw = resolver.query(Battery_Data.CONTENT_URI, null, selection, null, Battery_Data.TIMESTAMP + " ASC");
		if( battery_raw != null && battery_raw.moveToFirst() ) {
			int last_battery_status = battery_raw.getInt(battery_raw.getColumnIndex(Battery_Data.STATUS));
			long last_battery_timestamp = battery_raw.getLong(battery_raw.getColumnIndex(Battery_Data.TIMESTAMP));
			
			while(battery_raw.moveToNext()) {
				int battery_status = battery_raw.getInt(battery_raw.getColumnIndex(Battery_Data.STATUS));
				long battery_timestamp = battery_raw.getLong(battery_raw.getColumnIndex(Battery_Data.TIMESTAMP));
				
				if( battery_status == BatteryManager.BATTERY_STATUS_DISCHARGING && last_battery_status == BatteryManager.BATTERY_STATUS_DISCHARGING ) { //continuing discharging 
					total_time_discharging += battery_timestamp-last_battery_timestamp;
				}
				
				last_battery_status = battery_status;
				last_battery_timestamp = battery_timestamp;
			}
		}
		if( battery_raw != null && ! battery_raw.isClosed() ) battery_raw.close();
		return total_time_discharging;
	}
}
