package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Scheduler_Provider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Random;

public class Scheduler extends Service {

    private static String TAG = "AWARE::Scheduler";

    public static final String ACTION_AWARE_SCHEDULER_TRIGGERED = "ACTION_AWARE_SCHEDULER_TRIGGERED";
    public static final String EXTRA_SCHEDULER_ID = "extra_scheduler_id";

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";
    public static final String SCHEDULE_ID = "schedule_id";

    public static final String TRIGGER_HOUR = "hour";
    public static final String TRIGGER_TIMER = "timer";
    public static final String TRIGGER_WEEKDAY = "weekday";
    public static final String TRIGGER_MONTH = "month";
    public static final String TRIGGER_CONTEXT = "context";
    public static final String TRIGGER_RANDOM = "random";

    public static final int RANDOM_TYPE_HOUR = 0;
    public static final int RANDOM_TYPE_WEEKDAY = 1;
    public static final int RANDOM_TYPE_MONTH = 2;

    public static final String RANDOM_HOUR = "random_hour";
    public static final String RANDOM_MONTH = "random_month";
    public static final String RANDOM_WEEKDAY = "random_weekday";

    public static final String ACTION_TYPE = "type";
    public static final String ACTION_TYPE_BROADCAST = "broadcast";
    public static final String ACTION_TYPE_SERVICE = "service";
    public static final String ACTION_TYPE_ACTIVITY = "activity";

    public static final String ACTION_CLASS = "class";
    public static final String ACTION_EXTRAS = "extras";
    public static final String ACTION_EXTRA_KEY = "extra_key";
    public static final String ACTION_EXTRA_VALUE = "extra_value";

    //String is the scheduler ID
    private static final Hashtable<String, Hashtable<IntentFilter, BroadcastReceiver>> schedulerListeners = new Hashtable<>();

    @Override
    public void onCreate() {
        super.onCreate();
        if (Aware.DEBUG) Log.d(TAG, "Scheduler is created");
    }

    /**
     * Save the defined scheduled task
     *
     * @param context
     * @param schedule
     */
    public static void saveSchedule(Context context, Schedule schedule) {
        try {
            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
            data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, context.getPackageName());

            Cursor schedules = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null, null);
            if (schedules != null && schedules.getCount() == 1) {
                Log.d(Aware.TAG, "Updating already existing schedule...");
                context.getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null);
            } else {
                Log.d(Aware.TAG, "New schedule: " + data.toString());
                context.getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
            }
            if (schedules != null && !schedules.isClosed()) schedules.close();

        } catch (JSONException e) {
            Log.e(Aware.TAG, "Error saving schedule");
        }
    }

    /**
     * Remove previously defined schedule
     *
     * @param context
     * @param schedule_id
     */
    public static void removeSchedule(Context context, String schedule_id) {
        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null);
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

        public Schedule setActionType(String type) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_TYPE, type);
            return this;
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

        /**
         * Get action class
         *
         * @return
         * @throws JSONException
         */
        public String getActionClass() throws JSONException {
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_CLASS);
        }

        public Schedule setActionClass(String classname) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_CLASS, classname);
            return this;
        }

        public Schedule addHour(int hour) throws JSONException {
            JSONArray hours = getHours();
            hours.put(hour);
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
            extras.put(new JSONObject().put(ACTION_EXTRA_KEY, key).put(ACTION_EXTRA_VALUE, value));
            return this;
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
         * Set trigger to a specified date and time
         *
         * @param date
         */
        public Schedule setTimer(Calendar date) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_TIMER, date.getTimeInMillis());
            return this;
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
         * Add a weekday e.g., "Monday",...,"Sunday"
         *
         * @param week_day
         * @return
         * @throws JSONException
         */
        public Schedule addWeekday(String week_day) throws JSONException {
            JSONArray weekdays = getWeekdays();
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

        /**
         * Listen for this contextual broadcast to trigger this schedule
         *
         * @param broadcast e.g., ACTION_AWARE_CALL_ACCEPTED runs this schedule when the user has answered a phone call
         */
        public Schedule setContext(String broadcast) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_CONTEXT, broadcast);
            return this;
        }

        /**
         * Get the contextual broadcast that triggers this schedule
         *
         * @return
         */
        public String getContext() throws JSONException {
            if (this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_CONTEXT)) {
                return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getString(TRIGGER_CONTEXT);
            }
            return "";
        }

        /**
         * Get X random schedules from defined hour/weekday/month triggers
         *
         * @throws JSONException
         */
        public Schedule randomize(int random_type) throws JSONException {
            JSONObject json_random = getRandom();
            switch (random_type) {
                case RANDOM_TYPE_HOUR:
                    json_random.put(RANDOM_HOUR, true);
                    break;
                case RANDOM_TYPE_WEEKDAY:
                    json_random.put(RANDOM_WEEKDAY, true);
                    break;
                case RANDOM_TYPE_MONTH:
                    json_random.put(RANDOM_MONTH, true);
                    break;
            }
            return this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Aware.DEBUG) Log.d(TAG, "Checking for scheduled tasks: " + getPackageName());

        Cursor scheduled_tasks = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + getPackageName() + "'", null, Scheduler_Provider.Scheduler_Data.TIMESTAMP + " ASC");
        if (scheduled_tasks != null && scheduled_tasks.moveToFirst()) {
            if (Aware.DEBUG)
                Log.d(TAG, "Scheduled tasks for " + getPackageName() + ": " + scheduled_tasks.getCount());
            do {
                try {
                    final Schedule schedule = new Schedule(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID)));
                    schedule.rebuild(new JSONObject(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE))));

                    if (schedule.getContext().length() > 0) {
                        if (!schedulerListeners.containsKey(schedule.getScheduleID())) {
                            BroadcastReceiver listener = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    if (is_trigger(schedule)) {
                                        if (Aware.DEBUG)
                                            Log.d(Aware.TAG, "Received contextual trigger: " + intent.getAction());
                                        performAction(schedule);
                                    }
                                }
                            };

                            IntentFilter filter = new IntentFilter(schedule.getContext());

                            Hashtable<IntentFilter, BroadcastReceiver> scheduler_listener = new Hashtable<>();
                            scheduler_listener.put(filter, listener);
                            schedulerListeners.put(schedule.getScheduleID(), scheduler_listener);

                            registerReceiver(listener, filter);

                            if (Aware.DEBUG)
                                Log.d(Aware.TAG, "Registered a contextual trigger for " + schedule.getContext());
                        }

                    } else {
                        if (is_trigger(schedule)) {
                            if (Aware.DEBUG)
                                Log.d(Aware.TAG, "Triggering scheduled task: " + schedule.toString());
                            performAction(schedule);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (scheduled_tasks.moveToNext());
        } else {
            if (Aware.DEBUG) Log.d(TAG, "No scheduled tasks for " + getPackageName());
        }
        if (scheduled_tasks != null && !scheduled_tasks.isClosed()) scheduled_tasks.close();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (String schedule_id : schedulerListeners.keySet()) {
            Hashtable<IntentFilter, BroadcastReceiver> scheduled = schedulerListeners.get(schedule_id);
            for (IntentFilter filter : scheduled.keySet()) {
                try {
                    unregisterReceiver(scheduled.get(filter));
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean is_trigger(Schedule schedule) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            //Context schedulers do not have time constrains
            if (schedule.getContext().length() > 0) {
                if (schedule.getTimer() == -1 && schedule.getHours().length() == 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() == 0)
                    return true;
            }

            long last_triggered = 0;
            Cursor last_time_triggered = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, new String[]{Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED}, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null, null);
            if (last_time_triggered != null && last_time_triggered.moveToFirst()) {
                last_triggered = last_time_triggered.getLong(last_time_triggered.getColumnIndex(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED));
            }
            if (last_time_triggered != null && !last_time_triggered.isClosed())
                last_time_triggered.close();

            //This is a scheduled task with a precise time
            if (schedule.getTimer() != -1 && last_triggered == 0) { //not been triggered yet
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Checking trigger set for a specific timestamp: " + schedule.getTimer());
                if ((now.getTimeInMillis() - schedule.getTimer()) < 5 * 60 * 1000)
                    return true; //trigger within a 5-minute window
            }

            Calendar previous = null;
            if (last_triggered != 0) {
                previous = Calendar.getInstance();
                previous.setTimeInMillis(last_triggered);
            }

            //triggered at the given hours, regardless of weekday or month
            if (schedule.getHours().length() > 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() == 0) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Checking trigger at given hour, regardless of weekday or month");
                if (previous != null && is_same_hour_day(now, previous)) return false;
                return is_trigger_hour(schedule);
                //triggered at given hours and week day
            } else if (schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() == 0) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Checking trigger at given hour, and weekday");
                if (previous != null && is_same_hour_day(now, previous) && is_same_weekday(now, previous))
                    return false;
                return is_trigger_hour(schedule) && is_trigger_weekday(schedule);
                //triggered at given hours, week day and month
            } else if (schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() > 0) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Checking trigger at given hour, weekday and month");
                if (previous != null && is_same_hour_day(now, previous) && is_same_weekday(now, previous) && is_same_month(now, previous))
                    return false;
                return is_trigger_hour(schedule) && is_trigger_weekday(schedule) && is_trigger_month(schedule);
                //triggered at given weekday, regardless of time or month
            } else if (schedule.getHours().length() == 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() == 0) {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Checking trigger at given weekday, regardless of hour or month");
                if (previous != null && is_same_weekday(now, previous)) return false;
                return is_trigger_weekday(schedule);
                //triggered at given weekday and month
            } else if (schedule.getHours().length() == 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() > 0) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Checking trigger at given weekday, and month");
                if (previous != null && is_same_weekday(now, previous) && is_same_month(now, previous))
                    return false;
                return is_trigger_weekday(schedule) && is_trigger_month(schedule);
                //triggered at given month
            } else if (schedule.getHours().length() == 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() > 0) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Checking trigger at given month");
                if (previous != null && is_same_month(now, previous)) return false;
                return is_trigger_month(schedule);
                //Triggered at given hour and months
            } else if (schedule.getHours().length() > 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() > 0) {
                if (Aware.DEBUG) Log.d(Aware.TAG, "Checking trigger at given hour and month");
                if (previous != null && is_same_hour_day(now, previous) && is_same_month(now, previous))
                    return false;
                return is_trigger_hour(schedule) && is_trigger_month(schedule);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean is_same_hour_day(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.DAY_OF_YEAR) == date_two.get(Calendar.DAY_OF_YEAR)
                && date_one.get(Calendar.HOUR_OF_DAY) == date_two.get(Calendar.HOUR_OF_DAY);
    }

    private boolean is_same_weekday(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.WEEK_OF_YEAR) == date_two.get(Calendar.WEEK_OF_YEAR)
                && date_one.get(Calendar.DAY_OF_WEEK) == date_two.get(Calendar.DAY_OF_WEEK);
    }

    private boolean is_same_month(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.MONTH) == date_two.get(Calendar.MONTH);
    }

    /**
     * Check if this trigger should be triggered at this hour
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_hour(Schedule schedule) {

        if (Aware.DEBUG) Log.d(Aware.TAG, "Checking hour matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray hours = schedule.getHours();
            if (schedule.getRandom().optBoolean(RANDOM_HOUR)) {
                Random random = new Random();
                int random_hour = hours.getInt(random.nextInt(hours.length()));

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Random hour " + random_hour + " vs now " + now.get(Calendar.HOUR_OF_DAY) + " in trigger hours: " + hours.toString());

                if (random_hour == now.get(Calendar.HOUR_OF_DAY)) return true;

            } else {
                for (int i = 0; i < hours.length(); i++) {
                    int hour = hours.getInt(i);

                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Hour " + hour + " vs now " + now.get(Calendar.HOUR_OF_DAY) + " in trigger hours: " + hours.toString());

                    if (hour == now.get(Calendar.HOUR_OF_DAY)) return true;
                }
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

        if (Aware.DEBUG) Log.d(Aware.TAG, "Checking weekday matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray weekdays = schedule.getWeekdays();
            if (schedule.getRandom().optBoolean(RANDOM_WEEKDAY)) {
                Random random = new Random();
                String random_weekday = weekdays.getString(random.nextInt(weekdays.length()));

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Random weekday " + random_weekday.toUpperCase() + " vs now " + now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger weekdays: " + weekdays.toString());

                if (random_weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                    return true;

            } else {
                for (int i = 0; i < weekdays.length(); i++) {
                    String weekday = weekdays.getString(i);

                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Weekday " + weekday.toUpperCase() + " vs now " + now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger weekdays: " + weekdays.toString());

                    if (weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                        return true;
                }
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

        if (Aware.DEBUG) Log.d(Aware.TAG, "Checking month matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray months = schedule.getMonths();

            if (schedule.getRandom().optBoolean(RANDOM_MONTH)) {
                Random random = new Random();
                String random_month = months.getString(random.nextInt(months.length()));

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Random month " + random_month.toUpperCase() + " vs now " + now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger months: " + months.toString());

                if (random_month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                    return true;

            } else {

                for (int i = 0; i < months.length(); i++) {
                    String month = months.getString(i);

                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Month " + month.toUpperCase() + " vs now " + now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase() + " in trigger months: " + months.toString());

                    if (month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase()))
                        return true;
                }
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

            if (schedule.getActionType().equals(ACTION_TYPE_BROADCAST)) {
                Intent broadcast = new Intent(schedule.getActionClass());
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
                getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + getPackageName() + "'", null);
            } else {
                ContentValues data = new ContentValues();
                data.put(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED, System.currentTimeMillis());
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
}
