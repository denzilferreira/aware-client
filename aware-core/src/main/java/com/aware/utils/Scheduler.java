package com.aware.utils;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.aware.providers.Scheduler_Provider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class Scheduler extends Aware_Sensor {

    public static final String ACTION_AWARE_SCHEDULER_CHECK = "ACTION_AWARE_SCHEDULER_CHECK";
    public static final String ACTION_AWARE_SCHEDULER_TRIGGERED = "ACTION_AWARE_SCHEDULER_TRIGGERED";
    public static final String EXTRA_SCHEDULER_ID = "extra_scheduler_id";
    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";
    public static final String SCHEDULE_ID = "schedule_id";
    public static final String TRIGGER_INTERVAL = "interval";
    public static final String TRIGGER_INTERVAL_DELAYED = "interval_delayed";
    public static final String TRIGGER_MINUTE = "minute";
    public static final String TRIGGER_HOUR = "hour";
    public static final String TRIGGER_TIMER = "timer";
    public static final String TRIGGER_WEEKDAY = "weekday";
    public static final String TRIGGER_MONTH = "month";
    public static final String TRIGGER_CONTEXT = "context";
    public static final String TRIGGER_CONDITION = "condition";
    public static final String TRIGGER_RANDOM = "random";
    public static final String CONDITION_URI = "condition_uri";
    public static final String CONDITION_WHERE = "condition_where";
    /**
     * How many times per day
     */
    public static final String RANDOM_TIMES = "random_times";
    /**
     * Minimum amount of elapsed time until we may trigger again
     */
    public static final String RANDOM_INTERVAL = "random_interval";
    /**
     * Defines the type of action (e.g., broadcast, service or activity)
     */
    public static final String ACTION_TYPE = "type";
    public static final String ACTION_TYPE_BROADCAST = "broadcast";
    public static final String ACTION_TYPE_SERVICE = "service";
    public static final String ACTION_TYPE_ACTIVITY = "activity";
    /**
     * Used only if action type is service or activity
     * Defined as package/package.service_class or package/package.activity_class
     * e.g.,
     * com.aware.phone/com.aware.phone.ui.Aware_Client
     * <br/>
     * Would open the main client UI with the Aware_Client activity class
     */
    public static final String ACTION_CLASS = "class";
    /**
     * Used to specify the intent action for broadcasts, activities and services
     */
    public static final String ACTION_INTENT_ACTION = "intent_action";
    /**
     * Used to define intent extras, if needed
     */
    public static final String ACTION_EXTRAS = "extras";
    public static final String ACTION_EXTRA_KEY = "extra_key";
    public static final String ACTION_EXTRA_VALUE = "extra_value";
    //String is the scheduler ID, and hashtable contains list of IntentFilters and BroadcastReceivers
    private static final Hashtable<String, Hashtable<IntentFilter, BroadcastReceiver>> schedulerListeners = new Hashtable<>();
    //String is the scheduler ID, and hashtable contains list of Uri and ContentObservers
    private static final Hashtable<String, Hashtable<Uri, ContentObserver>> schedulerDataObservers = new Hashtable<>();
    private static String TAG = "AWARE::Scheduler";

    /**
     * Save the defined scheduled task
     *
     * @param context
     * @param schedule
     */
    public static void saveSchedule(Context context, Schedule schedule) {
        try {
            ArrayList<String> global_settings = new ArrayList<>();
            //global_settings.add(Aware.SCHEDULE_SYNC_DATA);

            boolean is_global = global_settings.contains(schedule.getScheduleID());

            if (context.getResources().getBoolean(R.bool.standalone))
                is_global = false;

            if (schedule.getRandom().length() != 0 && schedule.getTimer() == -1) {

                JSONObject random = schedule.getRandom();

                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                Calendar now = Calendar.getInstance();

                int earliest = schedule.getDailyEarliest();
                int latest = schedule.getDailyLatest();

                start.set(Calendar.HOUR_OF_DAY, earliest);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);

                end.set(Calendar.HOUR_OF_DAY, latest);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                end.set(Calendar.MILLISECOND, 999);

                String original_id = schedule.getScheduleID();
                String random_seed = original_id;
                random_seed += "-" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
                // Get the random events for today
                ArrayList<Long> randoms = random_times(start, end, random.getInt(RANDOM_TIMES), random.getInt(RANDOM_INTERVAL), random_seed);
                // Remove events that are in the past
                Iterator<Long> iter = randoms.iterator();
                while (iter.hasNext()) {
                    if (iter.next() < now.getTimeInMillis() + 2 * 1000) {
                        iter.remove();
                    }
                }
                Log.d(TAG, "Random times for today between " + start.getTime().toString() + " and " + end.getTime().toString() + ":  " + randoms.size() + " left");
                // If we have no events left today, reschedule for tomorrow instead.
                if (randoms.size() <= 0) {
                    start.add(Calendar.DAY_OF_YEAR, 1);
                    end.add(Calendar.DAY_OF_YEAR, 1);
                    randoms = random_times(start, end, random.getInt(RANDOM_TIMES), random.getInt(RANDOM_INTERVAL), random_seed);
                    Log.d(TAG, "Random times set for tomorrow between " + start.getTime().toString() + " and " + end.getTime().toString());
                }

                long max = getLastRandom(randoms);
                for (Long r : randoms) {
                    Calendar timer = Calendar.getInstance();
                    timer.setTimeInMillis(r);

                    Log.d(TAG, "RANDOM TIME:" + timer.getTime().toString() + "\n");

                    schedule.setTimer(timer);

                    if (r == max) {
                        schedule.setScheduleID(original_id + "_random_" + r + "_last");
                    } else {
                        schedule.setScheduleID(original_id + "_random_" + r);
                    }

                    Log.d(Scheduler.TAG, "Random schedule: " + schedule.getScheduleID() + "\n");
                    // Recursively call saveSchedule.  This does not end up here again, because
                    // now there is a timer set.
                    saveSchedule(context, schedule);
                }
            } else {
                ContentValues data = new ContentValues();
                data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
                data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
                data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, (is_global) ? "com.aware.phone" : context.getPackageName());

                Cursor schedules = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null, null);
                if (schedules != null && schedules.getCount() == 1) {
                    try {
                        Log.d(Scheduler.TAG, "Updating already existing schedule...");
                        context.getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                } else {
                    try {
                        Log.d(Scheduler.TAG, "New schedule: " + data.toString());
                        context.getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                }

                if (schedules != null && !schedules.isClosed()) schedules.close();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(Scheduler.TAG, "Error saving schedule");
        }
    }

    /**
     * Allow setting a schedule for a specific package (e.g., plugin-specific schedulers)
     *
     * @param context
     * @param schedule
     * @param package_name
     */
    public static void saveSchedule(Context context, Schedule schedule, String package_name) {
        try {
            if (schedule.getRandom().length() != 0 && schedule.getTimer() == -1) {

                JSONObject random = schedule.getRandom();

                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                Calendar now = Calendar.getInstance();

                int earliest = schedule.getDailyEarliest();
                int latest = schedule.getDailyLatest();

                start.set(Calendar.HOUR_OF_DAY, earliest);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);

                end.set(Calendar.HOUR_OF_DAY, latest);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                end.set(Calendar.MILLISECOND, 999);

                String original_id = schedule.getScheduleID();
                String random_seed = original_id;
                random_seed += "-" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
                // Get the random events for today
                ArrayList<Long> randoms = random_times(start, end, random.getInt(RANDOM_TIMES), random.getInt(RANDOM_INTERVAL), random_seed);
                // Remove events that are in the past
                Iterator<Long> iter = randoms.iterator();
                while (iter.hasNext()) {
                    if (iter.next() < now.getTimeInMillis() + 2 * 1000) {
                        iter.remove();
                    }
                }
                Log.d(TAG, "Random times for today between " + start.getTime().toString() + " and " + end.getTime().toString() + ":  " + randoms.size() + " left");
                // If we have no events left today, reschedule for tomorrow instead.
                if (randoms.size() <= 0) {
                    start.add(Calendar.DAY_OF_YEAR, 1);
                    end.add(Calendar.DAY_OF_YEAR, 1);
                    randoms = random_times(start, end, random.getInt(RANDOM_TIMES), random.getInt(RANDOM_INTERVAL), random_seed);
                    Log.d(TAG, "Random times set for tomorrow between " + start.getTime().toString() + " and " + end.getTime().toString());
                }

                long max = getLastRandom(randoms);

                for (Long r : randoms) {
                    Calendar timer = Calendar.getInstance();
                    timer.setTimeInMillis(r);

                    Log.d(TAG, "RANDOM TIME:" + timer.getTime().toString() + "\n");

                    schedule.setTimer(timer);

                    if (r == max) {
                        schedule.setScheduleID(original_id + "_random_" + r + "_last");
                    } else {
                        schedule.setScheduleID(original_id + "_random_" + r);
                    }

                    Log.d(Scheduler.TAG, "Random schedule: " + schedule.getScheduleID() + "\n");
                    // Recursively call saveSchedule.  This does not end up here again, because
                    // now there is a timer set.
                    saveSchedule(context, schedule, package_name);
                }
            } else {
                ContentValues data = new ContentValues();
                data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
                data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
                data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, package_name);

                Cursor schedules = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
                if (schedules != null && schedules.getCount() == 1) {
                    Log.d(Scheduler.TAG, "Updating already existing schedule...");
                    context.getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null);
                } else {
                    Log.d(Scheduler.TAG, "New schedule: " + data.toString());
                    context.getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
                }
                if (schedules != null && !schedules.isClosed()) schedules.close();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(Scheduler.TAG, "Error saving schedule");
        }
    }

    private static long getLastRandom(ArrayList<Long> randoms) {
        long max = 0;
        for (Long r : randoms) {
            if (r >= max) max = r;
        }
        return max;
    }

    private static void rescheduleRandom(Context context, Schedule schedule) {
        try {
            JSONObject random = schedule.getRandom();

            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();

            int earliest = schedule.getDailyEarliest();
            int latest = schedule.getDailyLatest();

            start.set(Calendar.HOUR_OF_DAY, earliest);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);

            end.set(Calendar.HOUR_OF_DAY, latest);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            end.set(Calendar.MILLISECOND, 999);

            //moving dates to tomorrow
            start.add(Calendar.DAY_OF_YEAR, 1);
            end.add(Calendar.DAY_OF_YEAR, 1);

            Log.d(TAG, "Random times set for tomorrow between " + start.getTime().toString() + " and " + end.getTime().toString());

            String original_id = schedule.getScheduleID();
            String random_seed = original_id;
            random_seed += "-" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
            ArrayList<Long> randoms = random_times(start, end, random.getInt(RANDOM_TIMES), random.getInt(RANDOM_INTERVAL), random_seed);

            long max = getLastRandom(randoms);

            for (Long r : randoms) {
                Calendar timer = Calendar.getInstance();
                timer.setTimeInMillis(r);

                Log.d(TAG, "RANDOM TIME:" + timer.getTime().toString() + "\n");

                schedule.setTimer(timer);

                if (r == max) {
                    schedule.setScheduleID(original_id + "_random_" + r + "_last");
                } else {
                    schedule.setScheduleID(original_id + "_random_" + r);
                }

                ContentValues data = new ContentValues();
                data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
                data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
                data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
                data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, context.getPackageName());

                Log.d(Scheduler.TAG, "Random schedule: " + data.toString() + "\n");
                context.getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove previously defined schedule
     *
     * @param context
     * @param schedule_id
     */
    public static void removeSchedule(Context context, String schedule_id) {

        ArrayList<String> global_settings = new ArrayList<>();
        //global_settings.add(Aware.SCHEDULE_SYNC_DATA);

        boolean is_global = global_settings.contains(schedule_id);
        if (context.getResources().getBoolean(R.bool.standalone))
            is_global = false;

        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null);

        clearReceivers(context, schedule_id);
        clearContentObservers(context, schedule_id);
    }

    /**
     * Allow removing a schedule for a specific package
     *
     * @param context
     * @param schedule_id
     * @param package_name
     */
    public static void removeSchedule(Context context, String schedule_id, String package_name) {
        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null);

        clearReceivers(context, schedule_id);
        clearContentObservers(context, schedule_id);
    }

    /**
     * Return a schedule from current package
     *
     * @param context
     * @param schedule_id
     * @return
     */
    public static Schedule getSchedule(Context context, String schedule_id) {

        ArrayList<String> global_settings = new ArrayList<>();
        //global_settings.add(Aware.SCHEDULE_SYNC_DATA);

        boolean is_global = global_settings.contains(schedule_id);
        if (context.getResources().getBoolean(R.bool.standalone))
            is_global = false;

        Schedule output = null;
        Cursor scheduleData = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null, null);
        if (scheduleData != null && scheduleData.moveToFirst()) {
            try {
                JSONObject jsonSchedule = new JSONObject(scheduleData.getString(scheduleData.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE)));
                output = new Schedule(jsonSchedule);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (scheduleData != null && !scheduleData.isClosed()) scheduleData.close();
        return output;
    }

    /**
     * Allow retrieving a schedule for a specific package
     *
     * @param context
     * @param schedule_id
     * @param package_name
     * @return
     */
    public static Schedule getSchedule(Context context, String schedule_id, String package_name) {
        Schedule output = null;
        Cursor scheduleData = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
        if (scheduleData != null && scheduleData.moveToFirst()) {
            try {
                JSONObject jsonSchedule = new JSONObject(scheduleData.getString(scheduleData.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE)));
                output = new Schedule(jsonSchedule);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (scheduleData != null && !scheduleData.isClosed()) scheduleData.close();
        return output;
    }

    /**
     * Allow for setting predetermined schedules from MQTT, study configs or other applications
     * JSONArray contains an object with two variables: schedule and package
     *
     * @param schedules
     */
    public static void setSchedules(Context c, JSONArray schedules) {
        for (int i = 0; i < schedules.length(); i++) {
            try {
                JSONObject schedule = schedules.getJSONObject(i);
                Schedule s = new Schedule(schedule);
                saveSchedule(c, s, schedule.getString("package"));
            } catch (JSONException e) {
                e.printStackTrace();
                if (Aware.DEBUG) Log.d(Scheduler.TAG, "Error in JSON: " + e.getMessage());
            }
        }

        //Apply new schedules immediately
        Aware.startScheduler(c);
    }

    /**
     * Clear and unregister all schedulers
     *
     * @param c
     */
    public static void clearSchedules(Context c) {
        String standalone = "";
        if (c.getResources().getBoolean(R.bool.standalone)) {
            standalone = " OR " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE 'com.aware.phone'";
        }

        Cursor scheduled_tasks = c.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + c.getPackageName() + "'" + standalone, null, Scheduler_Provider.Scheduler_Data.TIMESTAMP + " ASC");
        if (scheduled_tasks != null && scheduled_tasks.moveToFirst()) {
            do {
                removeSchedule(c, scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID)));
            } while (scheduled_tasks.moveToNext());
        }
        if (scheduled_tasks != null && !scheduled_tasks.isClosed()) scheduled_tasks.close();
    }

    private static void clearReceivers(Context c, String schedule_id) {
        if (schedulerListeners.size() == 0) return;
        Hashtable<IntentFilter, BroadcastReceiver> scheduled = schedulerListeners.get(schedule_id);
        for (IntentFilter filter : scheduled.keySet()) {
            try {
                c.unregisterReceiver(scheduled.get(filter));
            } catch (IllegalArgumentException | NullPointerException e) {
            }
        }
        schedulerListeners.remove(schedule_id);
    }

    private static void clearContentObservers(Context c, String schedule_id) {
        if (schedulerDataObservers.size() == 0) return;
        Hashtable<Uri, ContentObserver> scheduled = schedulerDataObservers.get(schedule_id);
        for (Uri data : scheduled.keySet()) {
            try {
                c.getContentResolver().unregisterContentObserver(scheduled.get(data));
            } catch (IllegalArgumentException | NullPointerException e) {
            }
        }
        schedulerDataObservers.remove(schedule_id);
    }

    /**
     * Given a timeframe, a number of randoms and a minimum time interval, return a list of timestamps
     *
     * @param start
     * @param end
     * @param amount           number of times
     * @param interval_minutes how much time is set between timestamps, in minutes
     * @return ArrayList<Long> of timestamps between interval
     */
    public static ArrayList<Long> random_times(Calendar start, Calendar end, int amount, int interval_minutes, String random_seed) {
        //String seed = "hJYAe7cV";
        ArrayList<Long> randomList = new ArrayList<>();
        random_seed = String.format("%s-%d-%d", random_seed, start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR));
        long random_seed_int = 13;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(random_seed.getBytes("UTF-8"));
            byte[] digest = md.digest();
            random_seed_int = (((((((digest[0] << 8 + digest[1]) << 8 + digest[2]) << 8 + digest[3]) << 8)
                    + digest[4] << 8) + digest[5] << 8) + digest[6] << 8) + digest[7];
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Random rng = new Random(random_seed_int);

        long totalInterval = end.getTimeInMillis() - start.getTimeInMillis();
        long minDifferenceMillis = interval_minutes * 60 * 1000;
        long effectiveInterval = totalInterval - minDifferenceMillis * (amount - 1);

        // Create random intervals without the minimum interval.
        while (randomList.size() < amount) {
            long random = start.getTimeInMillis() + (long) (rng.nextDouble() * effectiveInterval);
            randomList.add(random);
        }
        // Sort and add the minimum intervals between all events.
        Collections.sort(randomList);
        for (int i = 0; i < randomList.size(); i++) {
            randomList.set(i, randomList.get(i) + i * minDifferenceMillis);
        }

        return randomList;
    }

    /**
     * Given a timeframe, a number of random at a minimum interval in between, returns a list of possible timestamps in milliseconds
     *
     * @param start
     * @param end
     * @param amount
     * @param interval_minutes
     * @return ArrayList<Long> of timestamps between interval
     */
    public static ArrayList<Long> random_times(Calendar start, Calendar end, int amount, int interval_minutes) {
        ArrayList<Long> randomList = new ArrayList<>();

        Random rng = new Random();
        long totalInterval = end.getTimeInMillis() - start.getTimeInMillis();
        long minDifferenceMillis = interval_minutes * 60 * 1000;
        long effectiveInterval = totalInterval - minDifferenceMillis * (amount - 1);

        // Create random intervals without the minimum interval.
        while (randomList.size() < amount) {
            long random = start.getTimeInMillis() + (long) (rng.nextDouble() * effectiveInterval);
            randomList.add(random);
        }
        // Sort and add the minimum intervals between all events.
        Collections.sort(randomList);
        for (int i = 0; i < randomList.size(); i++) {
            randomList.set(i, randomList.get(i) + i * minDifferenceMillis);
        }

        return randomList;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Scheduler is created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            if (DEBUG) Log.d(TAG, "Checking for scheduled tasks: " + getPackageName());

            String standalone = "";
            if (getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                standalone = " OR " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE 'com.aware.phone'";
            }

            Cursor scheduled_tasks = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + getPackageName() + "'" + standalone, null, Scheduler_Provider.Scheduler_Data.TIMESTAMP + " ASC");
            if (scheduled_tasks != null && scheduled_tasks.moveToFirst()) {

                if (DEBUG)
                    Log.d(TAG, "Scheduled tasks for " + getPackageName() + ": " + scheduled_tasks.getCount());

                do {
                    try {
                        final Schedule schedule = getSchedule(this, scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID)));

                        //unable to load schedule. This should never happen.
                        if (schedule == null) {
                            if (DEBUG)
                                Log.e(TAG, "Failed to load schedule... something is wrong with the database.");

                            continue;
                        }

                        //Schedulers triggered by broadcasts
                        if (schedule.getContexts().length() > 0) {

                            //Check if we already registered the broadcastreceiver for this schedule
                            if (!schedulerListeners.containsKey(schedule.getScheduleID())) {

                                final JSONArray contexts = schedule.getContexts();
                                IntentFilter filter = new IntentFilter();
                                for (int i = 0; i < contexts.length(); i++) {
                                    String context = contexts.getString(i);
                                    filter.addAction(context);
                                }

                                BroadcastReceiver listener = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        if (is_trigger(schedule)) {
                                            if (DEBUG)
                                                Log.d(TAG, "Triggered contextual trigger: " + contexts.toString());

                                            performAction(schedule);
                                        }
                                    }
                                };

                                Hashtable<IntentFilter, BroadcastReceiver> scheduler_listener = new Hashtable<>();
                                scheduler_listener.put(filter, listener);

                                schedulerListeners.put(schedule.getScheduleID(), scheduler_listener);

                                registerReceiver(listener, filter);

                                if (DEBUG)
                                    Log.d(TAG, "Registered a contextual trigger for " + contexts.toString());

                            } else {

                                if (DEBUG)
                                    Log.d(TAG, "Contextual triggers are active: " + schedule.getContexts().toString());

                            }

                            continue;
                        }

                        //Schedulers triggered by database changes
                        if (schedule.getConditions().length() > 0) {

                            //Check if we already registered the ContentObservers for this schedule
                            if (!schedulerDataObservers.containsKey(schedule.getScheduleID())) {

                                Hashtable<Uri, ContentObserver> dataObs = new Hashtable<>();

                                final JSONArray conditions = schedule.getConditions();
                                for (int i = 0; i < conditions.length(); i++) {

                                    JSONObject condition = conditions.getJSONObject(i);

                                    Uri content_uri = Uri.parse(condition.getString(CONDITION_URI));
                                    String content_where = condition.getString(CONDITION_WHERE);

                                    DBObserver dbObs = new DBObserver(new Handler())
                                            .setCondition(content_where)
                                            .setData(content_uri)
                                            .setSchedule(schedule);

                                    dataObs.put(content_uri, dbObs);

                                    getContentResolver().registerContentObserver(content_uri, true, dbObs);
                                }

                                schedulerDataObservers.put(schedule.getScheduleID(), dataObs);

                                if (DEBUG)
                                    Log.d(TAG, "Registered conditional triggers: " + conditions.toString());

                            } else {
                                if (DEBUG)
                                    Log.d(TAG, "Conditional triggers are active: " + schedule.getConditions().toString());
                            }

                            continue;
                        }

                        //Not contextual or conditional scheduler, it is time-based
                        if (is_trigger(schedule)) {
                            if (DEBUG)
                                Log.d(TAG, "Triggering scheduled task: " + schedule.getScheduleID() + " in package: " + getPackageName());
                            performAction(schedule);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } while (scheduled_tasks.moveToNext());
            } else {
                if (DEBUG) Log.d(TAG, "No scheduled tasks for " + getPackageName());
            }
            if (scheduled_tasks != null && !scheduled_tasks.isClosed()) scheduled_tasks.close();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Remove broadcast receivers
        for (String schedule_id : schedulerListeners.keySet()) {
            clearReceivers(getApplicationContext(), schedule_id);
        }

        //Remove contentobservers
        for (String schedule_id : schedulerDataObservers.keySet()) {
            clearContentObservers(getApplicationContext(), schedule_id);
        }
    }

    /**
     * Checks for time con
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger(Schedule schedule) {

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        if (DEBUG) {
            Log.i(TAG, "Time now is: " + now.getTime().toString());

            try {
                Log.i(TAG, "Scheduler info: id=" + schedule.getScheduleID() + " schedule=" + schedule.build().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            //No time constaints, trigger it!
            if (schedule.getTimer() == -1
                    && schedule.getHours().length() == 0
                    && schedule.getMinutes().length() == 0
                    && schedule.getInterval() == 0
                    && schedule.getIntervalDelayed() == 0
                    && schedule.getWeekdays().length() == 0
                    && schedule.getMonths().length() == 0)
                return true;

            //Has this scheduler been triggered before?
            long last_triggered = 0;
            long sched_timestamp = 0;
            Cursor schedule_data_cursor = getContentResolver().query(
                    Scheduler_Provider.Scheduler_Data.CONTENT_URI,
                    new String[]{Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED, Scheduler_Provider.Scheduler_Data.TIMESTAMP},
                    Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'",
                    null, null);

            if (schedule_data_cursor != null && schedule_data_cursor.moveToFirst()) {
                last_triggered = schedule_data_cursor.getLong(schedule_data_cursor.getColumnIndex(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED));
                sched_timestamp = schedule_data_cursor.getLong(schedule_data_cursor.getColumnIndex(Scheduler_Provider.Scheduler_Data.TIMESTAMP));
                schedule_data_cursor.close();
            }
            if (schedule_data_cursor != null && !schedule_data_cursor.isClosed())
                schedule_data_cursor.close();

            // This is a scheduled task on a specific timestamp.
            if (schedule.getTimer() != -1 && last_triggered == 0) { //not been triggered yet
                Calendar schedulerTimer = Calendar.getInstance();
                schedulerTimer.setTimeInMillis(schedule.getTimer());

                if (DEBUG)
                    Log.d(Scheduler.TAG, "Checking trigger set for a specific timestamp: " + schedulerTimer.getTime().toString());

                if (now.getTimeInMillis() >= schedule.getTimer()) {
                    return true;
                } else {
                    if (DEBUG) Log.d(Scheduler.TAG,
                            "Not the right time to trigger...: \nNow: " + now.getTime().toString() + " vs trigger: " + new Date(schedule.getTimer()).toString()
                                    + "\n Time to trigger: " + Converters.readable_elapsed(schedule.getTimer() - now.getTimeInMillis()));
                    return false;
                }
            } else if (schedule.getTimer() != -1 && last_triggered != 0) {
                return false; //already triggered, do nothing.
            }

            Calendar previous = null;
            if (last_triggered != 0) {
                previous = Calendar.getInstance();
                previous.setTimeInMillis(last_triggered);
                if (DEBUG) Log.i(TAG, "Scheduler last triggered: " + previous.getTime().toString());
            }

            Boolean execute_interval = null;
            if (schedule.getInterval() > 0 && previous == null) {
                execute_interval = true;
            } else if (previous != null && schedule.getInterval() > 0) {
                execute_interval = is_interval_elapsed(now, previous, schedule.getInterval());
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger interval: " + execute_interval);
            }
            // For some schedules, we don't want to execute until an initial delay has passed.
            // For these, we have the separate interval_delayed key.  interval_delayed should
            // never be set at the same time as interval.
            if (schedule.getIntervalDelayed() != 0) {
                Calendar creation_time = Calendar.getInstance();
                creation_time.setTimeInMillis(sched_timestamp);
                if (previous != null && is_interval_elapsed(now, previous, schedule.getIntervalDelayed())) {
                    // Run schedule if interval is elapsed since last run time
                    execute_interval = true;
                } else { // Otherwise, only execute if interval elapsed since
                    execute_interval = (is_interval_elapsed(now, creation_time, schedule.getIntervalDelayed()));
                }
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger interval (delayed): " + execute_interval);
            }

            // This is used to prevent executing the schedule multiple times for the same interval.
            // It defaults to true, and if a schedule repeats in the same time period (for example,
            // an hour trigger and the schedule runner runs multiple times in that hour), then
            // it will be set to false.  But, the most fine-grained condition takes precedence.
            // For example, if we have an hour condition, it shouldn't run twice in an hour, but it
            // should run multiple times in the same day.
            Boolean execute_not_same_interval = true;

            Boolean execute_month = null;
            if (schedule.getMonths().length() > 0) {
                execute_month = is_trigger_month(schedule);
                if (execute_month)
                    execute_not_same_interval = (previous == null) || !is_same_month(previous, now);
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger month: " + execute_month);
            }

            Boolean execute_weekdays = null;
            if (schedule.getWeekdays().length() > 0) {
                execute_weekdays = is_trigger_weekday(schedule);
                if (execute_weekdays)
                    execute_not_same_interval = (previous == null) || !is_same_weekday(previous, now);
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger weekday: " + execute_weekdays);
            }

            Boolean execute_hours = null;
            if (schedule.getHours().length() > 0) {
                execute_hours = is_trigger_hour(schedule);
                if (execute_hours)
                    execute_not_same_interval = (previous == null) || !is_same_hour_day(previous, now);
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger hour: " + execute_hours);
            }

            Boolean execute_minutes = null;
            if (schedule.getMinutes().length() > 0) {
                execute_minutes = is_trigger_minute(schedule);
                if (execute_minutes)
                    execute_not_same_interval = (previous == null) || !is_same_minute_hour(previous, now);
                if (DEBUG) Log.d(Scheduler.TAG, "Trigger minute: " + execute_minutes);
            }

            ArrayList<Boolean> executers = new ArrayList<>();
            if (execute_interval != null) executers.add(execute_interval);
            if (execute_month != null) executers.add(execute_month);
            if (execute_weekdays != null) executers.add(execute_weekdays);
            if (execute_hours != null) executers.add(execute_hours);
            if (execute_minutes != null) executers.add(execute_minutes);
            if (execute_not_same_interval != null) executers.add(execute_not_same_interval);

            return !executers.contains(false);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean is_interval_elapsed(Calendar date_one, Calendar date_two, long required_minutes) {
        long elapsed = Math.round((date_one.getTimeInMillis() - date_two.getTimeInMillis()) / 1000 / 60.0);
        if (DEBUG)
            Log.d(Scheduler.TAG, "Checking interval elapsed: " + elapsed + " vs " + required_minutes + " minutes elapsed");

        return (elapsed >= required_minutes);
    }

    public boolean is_same_minute_hour(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.DAY_OF_YEAR) == date_two.get(Calendar.DAY_OF_YEAR)
                && date_one.get(Calendar.HOUR_OF_DAY) == date_two.get(Calendar.HOUR_OF_DAY)
                && date_one.get(Calendar.MINUTE) == date_two.get(Calendar.MINUTE);
    }

    public boolean is_same_hour_day(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.DAY_OF_YEAR) == date_two.get(Calendar.DAY_OF_YEAR)
                && date_one.get(Calendar.HOUR_OF_DAY) == date_two.get(Calendar.HOUR_OF_DAY);
    }

    public boolean is_same_weekday(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.WEEK_OF_YEAR) == date_two.get(Calendar.WEEK_OF_YEAR)
                && date_one.get(Calendar.DAY_OF_WEEK) == date_two.get(Calendar.DAY_OF_WEEK);
    }

    public boolean is_same_month(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.MONTH) == date_two.get(Calendar.MONTH);
    }

    /**
     * Check if this trigger should be triggered at this minute
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_minute(Schedule schedule) {
        if (DEBUG) Log.d(Scheduler.TAG, "Checking minute matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray minutes = schedule.getMinutes();

            for (int i = 0; i < minutes.length(); i++) {
                int minute = minutes.getInt(i);

                if (DEBUG)
                    Log.d(Scheduler.TAG, "Minute " + minute + " vs now " + now.get(Calendar.MINUTE) + " in trigger minutes: " + minutes.toString());

                if ((int) now.get(Calendar.MINUTE) == minute) return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if this trigger should be triggered at this hour
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_hour(Schedule schedule) {

        if (DEBUG) Log.d(Scheduler.TAG, "Checking hour matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray hours = schedule.getHours();

            for (int i = 0; i < hours.length(); i++) {
                int hour = hours.getInt(i);

                if (DEBUG)
                    Log.d(Scheduler.TAG, "Hour " + hour + " vs now " + now.get(Calendar.HOUR_OF_DAY) + " in trigger hours: " + hours.toString());

                if (hour == (int) now.get(Calendar.HOUR_OF_DAY)) return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if this schedule should be triggered this weekday
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_weekday(Schedule schedule) {

        if (DEBUG) Log.d(Scheduler.TAG, "Checking weekday matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray weekdays = schedule.getWeekdays();

            for (int i = 0; i < weekdays.length(); i++) {
                String weekday = weekdays.getString(i);

                if (DEBUG)
                    Log.d(Scheduler.TAG, "Weekday " + weekday.toUpperCase() + " vs now " + now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger weekdays: " + weekdays.toString());

                if (weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                    return true;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if this schedule should be triggered this month
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_month(Schedule schedule) {

        if (DEBUG) Log.d(Scheduler.TAG, "Checking month matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray months = schedule.getMonths();

            for (int i = 0; i < months.length(); i++) {
                String month = months.getString(i);

                if (DEBUG)
                    Log.d(Scheduler.TAG, "Month " + month.toUpperCase() + " vs now " + now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger months: " + months.toString());

                if (month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                    return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void performAction(Schedule schedule) {
        try {

            Intent scheduler_action = new Intent(Scheduler.ACTION_AWARE_SCHEDULER_TRIGGERED);
            scheduler_action.putExtra(EXTRA_SCHEDULER_ID, schedule.getScheduleID());
            sendBroadcast(scheduler_action);

            Aware.debug(this, "Scheduler triggered: " + schedule.getScheduleID() + " schedule: " + schedule.build().toString() + " package: " + getPackageName());

            if (schedule.getActionType().equals(ACTION_TYPE_BROADCAST)) {
                Intent broadcast = new Intent(schedule.getActionIntentAction());
                JSONArray extras = schedule.getActionExtras();
                for (int i = 0; i < extras.length(); i++) {
                    JSONObject extra = extras.getJSONObject(i);

                    Object extra_obj = extra.get(ACTION_EXTRA_VALUE);
                    if (extra_obj instanceof String) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Integer) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getInt(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Double) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getDouble(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Long) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getLong(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Boolean) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getBoolean(ACTION_EXTRA_VALUE));
                    }
                }
                sendBroadcast(broadcast);
            }

            if (schedule.getActionType().equals(ACTION_TYPE_ACTIVITY)) {
                String[] activity_info = schedule.getActionClass().split("/");

                Intent activity = new Intent();
                activity.setComponent(new ComponentName(activity_info[0], activity_info[1]));
                activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (schedule.getActionIntentAction().length() > 0) {
                    activity.setAction(schedule.getActionIntentAction());
                }

                JSONArray extras = schedule.getActionExtras();
                for (int i = 0; i < extras.length(); i++) {
                    JSONObject extra = extras.getJSONObject(i);
                    Object extra_obj = extra.get(ACTION_EXTRA_VALUE);
                    if (extra_obj instanceof String) {
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Integer) {
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getInt(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Double) {
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getDouble(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Long) {
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getLong(ACTION_EXTRA_VALUE));
                    } else if (extra_obj instanceof Boolean) {
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getBoolean(ACTION_EXTRA_VALUE));
                    }
                }
                startActivity(activity);
            }

            if (schedule.getActionType().equals(ACTION_TYPE_SERVICE)) {
                try {
                    String[] service_info = schedule.getActionClass().split("/");

                    Intent service = new Intent();
                    service.setComponent(new ComponentName(service_info[0], service_info[1]));

                    if (schedule.getActionIntentAction().length() > 0) {
                        service.setAction(schedule.getActionIntentAction());
                    }

                    JSONArray extras = schedule.getActionExtras();
                    for (int i = 0; i < extras.length(); i++) {
                        JSONObject extra = extras.getJSONObject(i);
                        Object extra_obj = extra.get(ACTION_EXTRA_VALUE);
                        if (extra_obj instanceof String) {
                            service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                        } else if (extra_obj instanceof Integer) {
                            service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getInt(ACTION_EXTRA_VALUE));
                        } else if (extra_obj instanceof Double) {
                            service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getDouble(ACTION_EXTRA_VALUE));
                        } else if (extra_obj instanceof Long) {
                            service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getLong(ACTION_EXTRA_VALUE));
                        } else if (extra_obj instanceof Boolean) {
                            service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getBoolean(ACTION_EXTRA_VALUE));
                        }
                    }
                    startService(service);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (schedule.getTimer() != -1) {

                //removeSchedule(getApplicationContext(), schedule.getScheduleID());

                //Check if this scheduler is a random and it is the last time it was triggered, re-schedule new randoms
                if (schedule.getRandom().length() > 0 && schedule.getScheduleID().contains("_last") && schedule.getScheduleID().contains("_random_")) {

                    //clean-up random strings
                    String originalScheduleID = schedule.getScheduleID().substring(0, schedule.getScheduleID().indexOf("_random_"));
                    schedule.setScheduleID(originalScheduleID);

                    //recreate random scheduler for tomorrow
                    Scheduler.rescheduleRandom(getApplicationContext(), schedule);
                }
            }

            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED, System.currentTimeMillis());

            if (getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND (" + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + getPackageName() + "' OR " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE 'com.aware.phone')", null);
            } else {
                getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + getPackageName() + "'", null);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Scheduler object that contains<br/>
     * - schedule ID<br/>
     * - schedule action<br/>
     * - schedule trigger
     */
    public static class Schedule {

        private JSONObject schedule = new JSONObject();
        private JSONObject trigger = new JSONObject();
        private JSONObject action = new JSONObject();

        public Schedule(String schedule_id) {
            try {
                this.schedule.put(SCHEDULE_ID, schedule_id);
                this.schedule.put(SCHEDULE_ACTION, this.action);
                this.schedule.put(SCHEDULE_TRIGGER, this.trigger);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public Schedule(JSONObject schedule) {
            this.rebuild(schedule);
        }

        /**
         * Rebuild schedule object from database JSON
         *
         * @param schedule
         * @return
         */
        public Schedule rebuild(JSONObject schedule) {
            try {
                this.schedule = schedule.getJSONObject("schedule");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return this;
        }

        public String getScheduleID() throws JSONException {
            return this.schedule.getString(SCHEDULE_ID);
        }

        public Schedule setScheduleID(String new_id) throws JSONException {
            this.schedule.put(SCHEDULE_ID, new_id);
            return this;
        }

        /**
         * Generates a JSONObject representation for saving JSON to database
         *
         * @return
         * @throws JSONException
         */
        public JSONObject build() throws JSONException {
            JSONObject schedule = new JSONObject();
            schedule.put("schedule", this.schedule);
            return schedule;
        }

        /**
         * Get type of action
         *
         * @return
         * @throws JSONException
         */
        public String getActionType() throws JSONException {
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_TYPE);
        }

        public Schedule setActionType(String type) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_TYPE, type);
            return this;
        }

        /**
         * Get action class
         *
         * @return
         * @throws JSONException
         */
        public String getActionClass() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_ACTION).has(ACTION_CLASS)) {
                this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_CLASS, "");
            }
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_CLASS);
        }

        public Schedule setActionClass(String classname) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_CLASS, classname);
            return this;
        }

        public String getActionIntentAction() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_ACTION).has(ACTION_INTENT_ACTION)) {
                this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_INTENT_ACTION, "");
            }
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_INTENT_ACTION);
        }

        public Schedule setActionIntentAction(String action) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_INTENT_ACTION, action);
            return this;
        }

        public Schedule addHour(int hour) throws JSONException {
            JSONArray hours = getHours();
            for (int i = 0; i < hours.length(); i++) {
                int m = hours.getInt(i);
                if (m == hour) return this;
            }
            hours.put(hour);
            return this;
        }

        /**
         * Return the latest hour of the day in which a random scheduler can be scheduled
         *
         * @return latest hour
         * @throws JSONException
         */
        public int getDailyLatest() throws JSONException {
            JSONArray hours = getHours();

            //Handle case when random is requested without a time frame
            if (hours.length() == 0) {
                return 23;
            }

            int max = 0;
            for (int i = 0; i < hours.length(); i++) {
                if (hours.getInt(i) >= max) max = hours.getInt(i);
            }

            Log.d(TAG, "Latest random hour: " + max);

            return max;
        }

        /**
         * Return the earliest hour of the day in which a random scheduler can be scheduled
         *
         * @return earliest hour
         * @throws JSONException
         */
        public int getDailyEarliest() throws JSONException {
            JSONArray hours = getHours();

            //Handle case when random is requested without a time frame
            if (hours.length() == 0) {
                return 0;
            }

            int min = 23;
            for (int i = 0; i < hours.length(); i++) {
                if (hours.getInt(i) <= min) min = hours.getInt(i);
            }

            Log.d(TAG, "Earliest random hour: " + min);

            return min;
        }

        public Schedule addMinute(int minute) throws JSONException {
            JSONArray minutes = getMinutes();
            for (int i = 0; i < minutes.length(); i++) {
                int m = minutes.getInt(i);
                if (m == minute) return this;
            }
            minutes.put(minute);
            return this;
        }

        public JSONArray getActionExtras() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_ACTION).has(ACTION_EXTRAS)) {
                this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_EXTRAS, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getJSONArray(ACTION_EXTRAS);
        }

        public Schedule addActionExtra(String key, Object value) throws JSONException {
            JSONArray extras = getActionExtras();

            boolean update = false;
            for (int i = 0; i < extras.length(); i++) {
                JSONObject extra = extras.getJSONObject(i);
                if (extra.opt(key) != null) {
                    extra.put(key, value); //updates value
                    update = true;
                    break;
                }
            }
            if (!update) {
                extras.put(new JSONObject().put(ACTION_EXTRA_KEY, key).put(ACTION_EXTRA_VALUE, value));
            }
            return this;
        }

        /**
         * Get scheduled interval
         *
         * @return
         * @throws JSONException
         */
        public long getInterval() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_INTERVAL)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_INTERVAL, 0);
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getLong(TRIGGER_INTERVAL);
        }

        public Schedule setInterval(long minutes) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_INTERVAL, minutes);
            return this;
        }

        /**
         * Get scheduled interval delay
         * For some schedules, we don't want it to run immediately after we set it (mainly
         * a schedule which updates the config itself).  If this setting is true, then do not
         *
         * @return true or false
         * @throws JSONException
         */
        public long getIntervalDelayed() throws JSONException {
            if (this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_INTERVAL_DELAYED)) {
                return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getLong(TRIGGER_INTERVAL_DELAYED);
            }
            return 0;
        }

        /**
         * Get scheduled minutes
         *
         * @return
         * @throws JSONException
         */
        public JSONArray getMinutes() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_MINUTE)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_MINUTE, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_MINUTE);
        }

        /**
         * Get scheduled hours
         *
         * @return
         */
        public JSONArray getHours() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_HOUR)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_HOUR, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_HOUR);
        }

        /**
         * Get trigger specific unix timestamp
         *
         * @return
         */
        public long getTimer() throws JSONException {
            if (this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_TIMER)) {
                return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getLong(TRIGGER_TIMER);
            }
            return -1;
        }

        /**
         * Set trigger to a specified date and time
         *
         * @param date
         */
        public Schedule setTimer(Calendar date) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_TIMER, date.getTimeInMillis());
            return this;
        }

        /**
         * Add a weekday e.g., "Monday",...,"Sunday"
         *
         * @param week_day
         * @return
         * @throws JSONException
         */
        public Schedule addWeekday(String week_day) throws JSONException {
            JSONArray weekdays = getWeekdays();
            for (int i = 0; i < weekdays.length(); i++) {
                String m = weekdays.getString(i);
                if (m.equalsIgnoreCase(week_day)) return this;
            }
            weekdays.put(week_day);
            return this;
        }

        /**
         * Get days of week in which this trigger is scheduled
         *
         * @return
         */
        public JSONArray getWeekdays() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_WEEKDAY)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_WEEKDAY, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_WEEKDAY);
        }

        /**
         * Add a month e.g., "January",...,"December"
         *
         * @param month
         * @return
         * @throws JSONException
         */
        public Schedule addMonth(String month) throws JSONException {
            JSONArray months = getMonths();
            for (int i = 0; i < months.length(); i++) {
                String m = months.getString(i);
                if (m.equalsIgnoreCase(month)) return this;
            }
            months.put(month);
            return this;
        }

        /**
         * Get months where schedule is valid
         *
         * @return
         */
        public JSONArray getMonths() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_MONTH)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_MONTH, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_MONTH);
        }

        public JSONObject getRandom() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_RANDOM)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_RANDOM, new JSONObject());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONObject(TRIGGER_RANDOM);
        }

        public Schedule addCondition(Uri content_uri, String where) throws JSONException {
            JSONArray conditions = getConditions();
            JSONObject condition = new JSONObject();
            condition.put(CONDITION_URI, content_uri.toString());
            condition.put(CONDITION_WHERE, where);
            conditions.put(condition);
            return this;
        }

        public JSONArray getConditions() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_CONDITION)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_CONDITION, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_CONDITION);
        }

        /**
         * Listen for this contextual broadcast to trigger this schedule
         *
         * @param broadcast e.g., ACTION_AWARE_CALL_ACCEPTED runs this schedule when the user has answered a phone call
         */
        public Schedule addContext(String broadcast) throws JSONException {
            JSONArray contexts = getContexts();
            contexts.put(broadcast);
            return this;
        }

        /**
         * Returns the list of contexts which trigger this schedule
         *
         * @return
         * @throws JSONException
         */
        public JSONArray getContexts() throws JSONException {
            if (!this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_CONTEXT)) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_CONTEXT, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_CONTEXT);
        }

        /**
         * Set random schedules between two dates. Define the total amount of times per day, with at least X interval minutes apart
         *
         * @throws JSONException
         */
        public Schedule random(int daily_amount, int minimum_interval) throws JSONException {
            JSONObject json_random = getRandom();
            json_random.put(RANDOM_TIMES, daily_amount);
            json_random.put(RANDOM_INTERVAL, minimum_interval);
            return this;
        }
    }

    /**
     * Scheduler's ContentObservers
     */
    private class DBObserver extends ContentObserver {
        private Uri data;
        private String condition;
        private Schedule schedule;

        DBObserver(Handler h) {
            super(h);
        }

        DBObserver setSchedule(Schedule s) {
            this.schedule = s;
            return this;
        }

        DBObserver setData(Uri content_uri) {
            this.data = content_uri;
            return this;
        }

        DBObserver setCondition(String where) {
            this.condition = where;
            return this;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            if (DEBUG)
                Log.d(Aware.TAG, "Checking condition : " + data.toString() + " where: " + condition);

            if (this.data != null && this.condition.length() > 0) {

                boolean condition_met = false;

                Cursor rows = getContentResolver().query(this.data, null, this.condition, null, null);
                if (rows != null && rows.moveToFirst() && rows.getCount() > 0) {
                    condition_met = true;
                }
                if (rows != null && !rows.isClosed()) rows.close();

                if (condition_met) {
                    if (is_trigger(schedule)) {
                        performAction(schedule);
                        if (DEBUG)
                            Log.d(Aware.TAG, "Condition triggered: " + data.toString() + " where: " + condition);
                    }
                }
            }
        }
    }
}
