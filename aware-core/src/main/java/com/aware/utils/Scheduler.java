package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Scheduler_Provider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class Scheduler extends Service {

    private static String TAG = "AWARE::Scheduler";

    public static final String ACTION_AWARE_SCHEDULER_TRIGGERED = "ACTION_AWARE_SCHEDULER_TRIGGERED";
    public static final String EXTRA_SCHEDULER_ID = "extra_scheduler_id";

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";
    public static final String SCHEDULE_ID = "schedule_id";

    public static final String TRIGGER_INTERVAL = "interval";
    public static final String TRIGGER_MINUTE = "minute";
    public static final String TRIGGER_HOUR = "hour";
    public static final String TRIGGER_TIMER = "timer";
    public static final String TRIGGER_WEEKDAY = "weekday";
    public static final String TRIGGER_MONTH = "month";
    public static final String TRIGGER_CONTEXT = "context";
    public static final String TRIGGER_CONDITION = "condition";
    public static final String TRIGGER_RANDOM = "random";

    public static final int RANDOM_TYPE_HOUR = 0;
    public static final int RANDOM_TYPE_WEEKDAY = 1;
    public static final int RANDOM_TYPE_MONTH = 2;
    public static final int RANDOM_TYPE_MINUTE = 3;

    public static final String CONDITION_URI = "condition_uri";
    public static final String CONDITION_WHERE = "condition_where";

    public static final String RANDOM_MINUTE = "random_minute";
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

    //String is the scheduler ID, and hashtable contains list of intentfilters and broadcastreceivers
    private static final Hashtable<String, Hashtable<IntentFilter, BroadcastReceiver>> schedulerListeners = new Hashtable<>();
    //String is the scheduler ID, and hashtable contains list of table content_uri and created contentobservers
    private static final Hashtable<String, Hashtable<Uri, ContentObserver>> schedulerContentObservers = new Hashtable<>();

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
            ArrayList<String> global_settings = new ArrayList<String>();
            global_settings.add(Aware.SCHEDULE_SPACE_MAINTENANCE);
            global_settings.add(Aware.SCHEDULE_SYNC_DATA);

            boolean is_global = global_settings.contains(schedule.getScheduleID());

            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
            data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, (is_global) ? "com.aware.phone" : context.getPackageName());

            Cursor schedules = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null, null);
            if (schedules != null && schedules.getCount() == 1) {
                Log.d(Aware.TAG, "Updating already existing schedule...");
                context.getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null);
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
     * Allow setting a schedule for a specific package (e.g., plugin-specific schedulers)
     *
     * @param context
     * @param schedule
     * @param package_name
     */
    public static void saveSchedule(Context context, Schedule schedule, String package_name) {
        try {
            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());
            data.put(Scheduler_Provider.Scheduler_Data.PACKAGE_NAME, package_name);

            Cursor schedules = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
            if (schedules != null && schedules.getCount() == 1) {
                Log.d(Aware.TAG, "Updating already existing schedule...");
                context.getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + package_name + "'", null);
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

        ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware.SCHEDULE_SPACE_MAINTENANCE);
        global_settings.add(Aware.SCHEDULE_SYNC_DATA);

        boolean is_global = global_settings.contains(schedule_id);

        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null);
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
    }

    /**
     * Return a schedule from current package
     *
     * @param context
     * @param schedule_id
     * @return
     */
    public static Schedule getSchedule(Context context, String schedule_id) {

        ArrayList<String> global_settings = new ArrayList<String>();
        global_settings.add(Aware.SCHEDULE_SPACE_MAINTENANCE);
        global_settings.add(Aware.SCHEDULE_SYNC_DATA);

        boolean is_global = global_settings.contains(schedule_id);

        Schedule output = null;

        Cursor scheduleData = context.getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "' AND " + Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + ((is_global) ? "com.aware.phone" : context.getPackageName()) + "'", null, null);
        if (scheduleData != null && scheduleData.moveToFirst()) {
            try {
                JSONObject jsonSchedule = new JSONObject(scheduleData.getString(scheduleData.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE)));
                output = new Schedule(jsonSchedule);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            scheduleData.close();
        }
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
            scheduleData.close();
        }
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
                if (Aware.DEBUG) Log.d(Scheduler.TAG, "Error in JSON: " + e.getMessage());
            }
        }

        //Apply new schedules
        Aware.startScheduler(c);
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

        public Schedule setInterval(long minutes) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_INTERVAL, minutes);
            return this;
        }

        public Schedule addHour(int hour) throws JSONException {
            JSONArray hours = getHours();
            hours.put(hour);
            return this;
        }

        public Schedule addMinute(int minute) throws JSONException {
            JSONArray minutes = getMinutes();
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
            extras.put(new JSONObject().put(ACTION_EXTRA_KEY, key).put(ACTION_EXTRA_VALUE, value));
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
         * Get X random schedules from defined minute/hour/weekday/month triggers
         *
         * @throws JSONException
         */
        public Schedule randomize(int random_type) throws JSONException {
            JSONObject json_random = getRandom();
            switch (random_type) {
                case RANDOM_TYPE_MINUTE:
                    json_random.put(RANDOM_MINUTE, true);
                    break;
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

                    if (schedule.getContexts().length() > 0) {

                        if (schedulerListeners.containsKey(schedule.getScheduleID())) {
                            Hashtable<IntentFilter, BroadcastReceiver> scheduled = schedulerListeners.get(schedule.getScheduleID());
                            for (IntentFilter filter : scheduled.keySet()) {
                                try {
                                    unregisterReceiver(scheduled.get(filter));
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                }
                            }
                            schedulerListeners.remove(schedule.getScheduleID());
                        }

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
                                    if (Aware.DEBUG)
                                        Log.d(Aware.TAG, "Received contextual trigger: " + contexts.toString());
                                    performAction(schedule);
                                }
                            }
                        };

                        Hashtable<IntentFilter, BroadcastReceiver> scheduler_listener = new Hashtable<>();
                        scheduler_listener.put(filter, listener);
                        schedulerListeners.put(schedule.getScheduleID(), scheduler_listener);

                        registerReceiver(listener, filter);

                        if (Aware.DEBUG)
                            Log.d(Aware.TAG, "Registered a contextual trigger for " + contexts.toString());

                        continue;
                    }
                    if (schedule.getConditions().length() > 0) {
                        if (schedulerContentObservers.containsKey(schedule.getScheduleID())) {
                            Hashtable<Uri, ContentObserver> scheduled = schedulerContentObservers.get(schedule.getScheduleID());
                            for (Uri table : scheduled.keySet()) {
                                try {
                                    getContentResolver().unregisterContentObserver(scheduled.get(table));
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                }
                            }
                            schedulerContentObservers.remove(schedule.getScheduleID());
                        }

                        final JSONArray conditions = schedule.getConditions();

                        for (int i = 0; i < conditions.length(); i++) {
                            JSONObject condition = conditions.getJSONObject(i);

                            Uri content_uri = Uri.parse(condition.getString(CONDITION_URI));
                            String content_where = condition.getString(CONDITION_WHERE);

                            DBObservers dbObserver = new DBObservers(new Handler());
                            dbObserver.setCondition(content_where);
                            dbObserver.setSchedule(schedule);
                            dbObserver.setTable(content_uri);

                            Hashtable<Uri, ContentObserver> scheduler_observer = new Hashtable<>();
                            scheduler_observer.put(content_uri, dbObserver);
                            schedulerContentObservers.put(schedule.getScheduleID(), scheduler_observer);

                            getContentResolver().registerContentObserver(content_uri, true, dbObserver);

                            if (Aware.DEBUG)
                                Log.d(Aware.TAG, "Registered a conditional trigger for: " + content_uri.toString() + " where: " + content_where);
                        }

                        continue;
                    }

                    if (is_trigger(schedule)) {
                        if (Aware.DEBUG)
                            Log.d(Aware.TAG, "Triggering scheduled task: " + schedule.toString());
                        performAction(schedule);
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

    /**
     * Used by the conditional schedulers
     */
    private class DBObservers extends ContentObserver {
        private Uri table;
        private String condition;
        private Schedule schedule;

        public DBObservers(Handler handler) {
            super(handler);
        }

        public DBObservers setSchedule(Schedule s) {
            this.schedule = s;
            return this;
        }

        public DBObservers setTable(Uri content_uri) {
            this.table = content_uri;
            return this;
        }

        public DBObservers setCondition(String where) {
            this.condition = where;
            return this;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (table != null && condition.length() > 0) {
                //Consider the latest data as fresh as the last 1 seconds.
                boolean condition_met = false;
                Cursor data = getContentResolver().query(table, null, condition + " AND timestamp BETWEEN " + (System.currentTimeMillis()-1000) + " AND " + System.currentTimeMillis(), null, "timestamp DESC LIMIT 1");
                if (data != null && data.moveToFirst()) {
                    condition_met = true;
                }
                if (data != null && !data.isClosed()) data.close();

                if (condition_met) {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Condition triggered: " + table.toString() + " where: " + condition);

                    performAction(schedule);
                }
            } else {
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Missing parameters");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //Remove broadcast receivers
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

        //Remove content observers
        for (String schedule_id : schedulerContentObservers.keySet()) {
            Hashtable<Uri, ContentObserver> scheduled = schedulerContentObservers.get(schedule_id);
            for (Uri table : scheduled.keySet()) {
                try {
                    getContentResolver().unregisterContentObserver(scheduled.get(table));
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
            //Context and condition schedulers do not have time constrains and it is handled by the broadcast receiver or the content observer
            if (schedule.getContexts().length() > 0 || schedule.getConditions().length() > 0) {
                return true;
            }

            //Has this scheduler been triggered before?
            long last_triggered = 0;
            Cursor last_time_triggered = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, new String[]{Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED}, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null, null);
            if (last_time_triggered != null && last_time_triggered.moveToFirst()) {
                last_triggered = last_time_triggered.getLong(last_time_triggered.getColumnIndex(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED));
                last_time_triggered.close();
            }

            // This is a scheduled task with a set timestamp.
            // We trigger it within a 5 minute interval (before & after). The framework checks this at inexact 5 minutes
            if (schedule.getTimer() != -1 && last_triggered == 0) { //not been triggered yet
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Checking trigger set for a specific timestamp: " + schedule.getTimer());
                if (Math.abs(now.getTimeInMillis() - schedule.getTimer()) < 5 * 60 * 1000)
                    return true; //trigger within a 5-minute window
            }

            Calendar previous = null;
            if (last_triggered != 0) {
                previous = Calendar.getInstance();
                previous.setTimeInMillis(last_triggered);
            }

            boolean execute = false;

            if (schedule.getInterval() > 0 && previous == null) {
                execute = true;
            } else if (schedule.getInterval() > 0 && previous != null) {
                execute = is_interval_elapsed(now, previous, schedule.getInterval());
            }
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Trigger interval: " + execute);

            if (schedule.getMinutes().length() > 0) {
                if (previous != null && is_same_minute_hour(now, previous)) {
                    execute = false;
                } else
                    execute = is_trigger_minute(schedule);
            }
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Trigger minute: " + execute);

            if (schedule.getHours().length() > 0) {
                if (previous != null && is_same_hour_day(now, previous)) {
                    execute = false;
                } else
                    execute = is_trigger_hour(schedule);
            }
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Trigger hour: " + execute);

            if (schedule.getWeekdays().length() > 0) {
                if (previous != null && is_same_weekday(now, previous)) {
                    execute = false;
                } else
                    execute = is_trigger_weekday(schedule);
            }
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Trigger weekday: " + execute);

            if (schedule.getMonths().length() > 0) {
                if (previous != null && is_same_month(now, previous)) {
                    execute = false;
                } else
                    execute = is_trigger_month(schedule);
            }
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "Trigger month: " + execute);

            return execute;

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean is_interval_elapsed(Calendar date_one, Calendar date_two, long required_minutes) {
        long elapsed = (date_one.getTimeInMillis() - date_two.getTimeInMillis()) / 1000 / 60;
        if (Aware.DEBUG)
            Log.d(Aware.TAG, "Checking interval elapsed: " + elapsed + " vs " + required_minutes + " minutes elapsed");
        return (elapsed >= required_minutes);
    }

    private boolean is_same_minute_hour(Calendar date_one, Calendar date_two) {
        return date_one.get(Calendar.YEAR) == date_two.get(Calendar.YEAR)
                && date_one.get(Calendar.DAY_OF_YEAR) == date_two.get(Calendar.DAY_OF_YEAR)
                && date_one.get(Calendar.HOUR_OF_DAY) == date_two.get(Calendar.HOUR_OF_DAY)
                && date_one.get(Calendar.MINUTE) == date_two.get(Calendar.MINUTE);
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
     * Check if this trigger should be triggered at this minute
     *
     * @param schedule
     * @return
     */
    private boolean is_trigger_minute(Schedule schedule) {
        if (Aware.DEBUG) Log.d(Aware.TAG, "Checking minute matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            JSONArray minutes = schedule.getMinutes();

            if (schedule.getRandom().optBoolean(RANDOM_MINUTE)) {

                Random random = new Random();
                int random_minute = minutes.getInt(random.nextInt(minutes.length()));

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Random minute " + random_minute + " vs now " + now.get(Calendar.MINUTE) + " in trigger minutes: " + minutes.toString());

                if (random_minute == now.get(Calendar.MINUTE)) return true;

            } else {
                for (int i = 0; i < minutes.length(); i++) {
                    int minute = minutes.getInt(i);

                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Minute " + minute + " vs now " + now.get(Calendar.MINUTE) + " in trigger minutes: " + minutes.toString());

                    if (now.get(Calendar.MINUTE) == minute) return true;
                }
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

    /**
     * Given a timeframe, a number of randoms and a minimum time interval, return a list of timestamps
     *
     * @param leftLimit
     * @param rightLimit
     * @param size
     * @param minDifference
     * @return
     */
    public static ArrayList<Long> random_times(Calendar leftLimit, Calendar rightLimit, int size, int minDifference) {
        if (size <= 0) {
            return null;
        }

        ArrayList<Long> randomList = new ArrayList<>();
        int minDifferenceMillis = minDifference * 60 * 1000;

        while (randomList.size() < size) {
            boolean valid_random = true;

            long random = leftLimit.getTimeInMillis() + (long) (Math.random() * (rightLimit.getTimeInMillis() - leftLimit.getTimeInMillis()));

            if (randomList.size() == 0) {
                randomList.add(random);
            } else {
                for (int i = 0; i < randomList.size(); i++) {
                    Long timestamp = randomList.get(i);
                    if (Math.abs(timestamp - random) < minDifferenceMillis) {
                        valid_random = false;
                    }
                }
                if (valid_random) {
                    randomList.add(random);
                }
            }
        }
        return randomList;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
