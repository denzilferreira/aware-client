package com.aware.utils;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
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
import java.util.Locale;
import java.util.Random;

public class Scheduler extends Service {

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";
    public static final String SCHEDULE_ID = "schedule_id";

    public static final String TRIGGER_HOUR = "hour"; //done
    public static final String TRIGGER_TIMER = "timer"; //done
    public static final String TRIGGER_WEEKDAY = "weekday"; //done
    public static final String TRIGGER_MONTH = "month"; //done
    public static final String TRIGGER_CONTEXT = "context"; //done

    public static final String TRIGGER_RANDOM = "random";
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

    @Override
    public void onCreate() {
        super.onCreate();

//        try {
//            Schedule schedule = new Schedule("sept_15h");
//            schedule.addHour(10)
//                    .addHour(14)
//                    .addHour(16)
//                    .addMonth("September")
//                    .setActionType(ACTION_TYPE_BROADCAST)
//                    .setActionClass("ACTION_AWARE_VIBRATE");
//
//            Log.d(Aware.TAG, schedule.build().toString(1));
//
//            saveSchedule( schedule );
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
    }

    public void saveSchedule( Schedule schedule ) {
        try {
            String schedule_id = schedule.getScheduleID();

            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule_id);
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());

            Cursor schedules = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "'", null, null);
            if( schedules != null && schedules.getCount() == 1 ) {
                Log.d(Aware.TAG, "Updating already existing schedule...");
                getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "'", null );
            } else {
                Log.d(Aware.TAG, "New schedule: " + data.toString());
                getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
            }
            if( schedules != null && ! schedules.isClosed() ) schedules.close();

        } catch( JSONException e ) {
            Log.e(Aware.TAG, "Error saving schedule");
        }
    }

    public void removeSchedule( String schedule_id ) {
        getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "'", null);
    }

    public class Schedule {
        private JSONObject schedule;
        private JSONObject trigger;
        private JSONObject action;

        public Schedule( String schedule_id ){
            this.schedule = new JSONObject();
            this.action = new JSONObject();
            this.trigger = new JSONObject();

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
         * @param schedule
         * @return
         */
        public Schedule rebuild( JSONObject schedule ) {
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
         * @return
         * @throws JSONException
         */
        public JSONObject build() throws JSONException {
            JSONObject schedule = new JSONObject();
            schedule.put( "schedule", this.schedule );
            return schedule;
        }

        public Schedule setActionType( String type ) throws JSONException {
            this.action.put(ACTION_TYPE, type);
            return this;
        }

        /**
         * Get type of action
         * @return
         * @throws JSONException
         */
        public String getActionType() throws JSONException{
            return this.action.getString(ACTION_TYPE);
        }

        /**
         * Get action class
         * @return
         * @throws JSONException
         */
        public String getActionClass() throws JSONException {
            return this.action.getString(ACTION_CLASS);
        }

        public Schedule setActionClass( String classname ) throws JSONException {
            this.action.put(ACTION_CLASS, classname);
            return this;
        }

        public Schedule addHour( int hour ) throws JSONException {
            JSONArray hours = getHours();
            hours.put(hour);
            return this;
        }

        public JSONArray getActionExtras() throws JSONException {
            if( ! this.action.has(ACTION_EXTRAS) ) {
                this.action.put(ACTION_EXTRAS, new JSONArray());
            }
            return this.action.getJSONArray(ACTION_EXTRAS);
        }

        public Schedule addActionExtra( String key, String value ) throws JSONException{
            JSONArray extras = getActionExtras();
            extras.put(new JSONObject().put(ACTION_EXTRA_KEY, key).put(ACTION_EXTRA_VALUE, value));
            return this;
        }

        /**
         * Get scheduled hours
         * @return
         */
        public JSONArray getHours() throws JSONException {
            if( ! this.trigger.has(TRIGGER_HOUR) ) {
                this.trigger.put(TRIGGER_HOUR, new JSONArray());
            }
            return this.trigger.getJSONArray(TRIGGER_HOUR);
        }

        /**
         * Set trigger to a specified date and time
         * @param date
         */
        public void setTimer( Calendar date ) throws JSONException {
            this.trigger.put(TRIGGER_TIMER, date.getTimeInMillis() );
        }

        /**
         * Get trigger specific unix timestamp
         * @return
         */
        public long getTimer() throws JSONException {
            if( this.trigger.has(TRIGGER_TIMER) ) {
                return this.trigger.getLong(TRIGGER_TIMER);
            }
            return -1;
        }

        /**
         * Add a weekday e.g., "Monday",...,"Sunday"
         * @param week_day
         * @return
         * @throws JSONException
         */
        public Schedule addWeekday( String week_day ) throws JSONException {
            JSONArray weekdays = getWeekdays();
            weekdays.put(week_day);
            return this;
        }

        /**
         * Get days of week in which this trigger is scheduled
         * @return
         */
        public JSONArray getWeekdays() throws JSONException {
            if( ! this.trigger.has(TRIGGER_WEEKDAY) ) {
                this.trigger.put(TRIGGER_WEEKDAY, new JSONArray());
            }
            return this.trigger.getJSONArray(TRIGGER_WEEKDAY);
        }

        /**
         * Add a month e.g., "January",...,"December"
         * @param month
         * @return
         * @throws JSONException
         */
        public Schedule addMonth( String month ) throws JSONException {
            JSONArray months = getMonths();
            months.put(month);
            return this;
        }

        /**
         * Get months where schedule is valid
         * @return
         */
        public JSONArray getMonths() throws JSONException {
            if( ! this.trigger.has(TRIGGER_MONTH) ) {
                this.trigger.put(TRIGGER_MONTH, new JSONArray());
            }
            return this.trigger.getJSONArray(TRIGGER_MONTH);
        }

        public JSONObject getRandom() throws JSONException {
            if( ! this.trigger.has(TRIGGER_RANDOM) ) {
                this.trigger.put(TRIGGER_RANDOM, new JSONObject());
            }
            return this.trigger.getJSONObject(TRIGGER_RANDOM);
        }

        /**
         * Listen for this contextual broadcast to trigger this schedule
         * @param broadcast e.g., ACTION_AWARE_CALL_ACCEPTED runs this schedule when the user has answered a phone call
         */
        public void setContext( String broadcast ) throws JSONException {
            this.trigger.put(TRIGGER_CONTEXT, broadcast);
        }

        /**
         * Get the contextual broadcast that triggers this schedule
         * @return
         */
        public String getContext() throws JSONException {
            if( this.trigger.has(TRIGGER_CONTEXT) ) {
                return this.trigger.getString(TRIGGER_CONTEXT);
            }
            return "";
        }

        /**
         * Get X random schedules from defined hour/weekday/month triggers
         * @throws JSONException
         */
        public void randomize(int random_amount) throws JSONException {
            JSONObject json_random = getRandom();

            Random random = new Random();
            JSONArray random_array;
            if( this.trigger.has(TRIGGER_HOUR) ) {
                random_array = this.trigger.getJSONArray(TRIGGER_HOUR);

                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    int selected_random = random_array.getInt(random.nextInt(random_array.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getInt(i) == selected_random ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_HOUR, selected );
            }
            if( this.trigger.has(TRIGGER_MONTH) ) {
                random_array = this.trigger.getJSONArray(TRIGGER_MONTH);

                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    String selected_random = random_array.getString(random.nextInt(random_array.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getString(i).equals(selected_random) ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_MONTH, selected );
            }
            if( this.trigger.has(TRIGGER_WEEKDAY) ) {
                random_array = this.trigger.getJSONArray(TRIGGER_WEEKDAY);

                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    String selected_random = random_array.getString(random.nextInt(random_array.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getString(i).equals(selected_random) ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_WEEKDAY, selected );
            }
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //Check if we have anything scheduled
        Cursor scheduled_tasks = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, null, null, Scheduler_Provider.Scheduler_Data.TIMESTAMP + " ASC");
        if( scheduled_tasks != null && scheduled_tasks.moveToFirst() ) {
            do {
                try {
                    Schedule schedule = new Schedule(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID)));
                    schedule.rebuild(new JSONObject(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE))));

                    //TODO register broadcast receivers for contextual triggers

                    if( is_trigger(schedule) ) {
                        ContentValues data = new ContentValues();
                        data.put(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED, System.currentTimeMillis());
                        getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null);
                        performAction(schedule);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (scheduled_tasks.moveToNext());
        }
        if( scheduled_tasks != null && ! scheduled_tasks.isClosed()) scheduled_tasks.close();
        return START_STICKY;
    }

    private boolean is_trigger ( Schedule schedule ) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            long last_triggered = 0;
            Cursor last_time_triggered = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, new String[]{Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED}, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null, null);
            if( last_time_triggered != null && last_time_triggered.moveToFirst() ) {
                last_triggered = last_time_triggered.getLong(last_time_triggered.getColumnIndex(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED));
            }
            if( last_time_triggered != null && ! last_time_triggered.isClosed() ) last_time_triggered.close();

            //This is a scheduled task with a precise time
            if( schedule.getTimer() != -1 && last_triggered == 0) { //not been triggered yet
                long trigger_time = schedule.getTimer();
                if( (now.getTimeInMillis()-trigger_time) < 5*60*1000 ) return true;
            }

            if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() == 0 ) { //triggered at given hours, regardless of weekday or month
                return is_trigger_hour(schedule, last_triggered);
            } else if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() == 0 ) { //triggered at given hours and week day
                return is_trigger_hour(schedule, last_triggered) && is_trigger_weekday(schedule, last_triggered);
            } else if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() > 0 ) { //triggered at given hours, week day and month
                return is_trigger_hour(schedule, last_triggered) && is_trigger_weekday(schedule, last_triggered) && is_trigger_month(schedule, last_triggered);
            }
        } catch (JSONException e ) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean is_trigger_hour( Schedule schedule, long last_triggered ) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());
        try {
            JSONArray hours = schedule.getHours();
            for( int i=0; i<hours.length(); i++ ) {
                int hour = hours.getInt(i);
                if( hour == now.get(Calendar.HOUR_OF_DAY) && last_triggered == 0 ) return true; //not triggered yet
                if( hour == now.get(Calendar.HOUR_OF_DAY) && last_triggered != 0 && now.getTimeInMillis()-last_triggered > 55*60*1000 ) return true; //triggered previously, been over an hour and should trigger at this hour
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean is_trigger_weekday( Schedule schedule, long last_triggered ) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(last_triggered);

        try {
            JSONArray weekdays = schedule.getWeekdays();
            for( int i=0; i<weekdays.length(); i++ ) {
                String weekday = weekdays.getString(i);
                if( weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())) && last_triggered == 0 ) return true;
                if( weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())) && last_triggered != 0 && now.get(Calendar.WEEK_OF_YEAR) > previous.get(Calendar.WEEK_OF_YEAR) ) return true; //triggered previously, been over a week and should trigger at this weekday
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean is_trigger_month( Schedule schedule, long last_triggered ) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(last_triggered);

        try {
            JSONArray months = schedule.getMonths();
            for( int i=0; i<months.length(); i++ ) {
                String month = months.getString(i);
                if( month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())) && last_triggered == 0 ) return true;
                if( month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())) && last_triggered != 0 && now.get(Calendar.MONTH) > previous.get(Calendar.MONTH) ) return true; //triggered previously, been over a month and should trigger at this month
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void performAction( Schedule schedule ) {
        try {
            //Trigger a broadcast
            if (schedule.getActionType().equals(ACTION_TYPE_BROADCAST)) {
                Intent broadcast = new Intent(schedule.getActionClass());
                JSONArray extras = schedule.getActionExtras();
                for (int i = 0; i < extras.length(); i++) {
                    JSONObject extra = extras.getJSONObject(i);
                    broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                }
                sendBroadcast(broadcast);
            //Trigger an activity
            } else if (schedule.getActionType().equals(ACTION_TYPE_ACTIVITY)) {
                try {
                    Class<?> activity_class = Class.forName(schedule.getActionClass());
                    Intent activity = new Intent(getApplicationContext(), activity_class);
                    activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    JSONArray extras = schedule.getActionExtras();
                    for (int i = 0; i < extras.length(); i++) {
                        JSONObject extra = extras.getJSONObject(i);
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    }
                    startActivity(activity);

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            //Trigger a service
            } else if (schedule.getActionType().equals(ACTION_TYPE_SERVICE)) {
                try {
                    Class<?> service_class = Class.forName(schedule.getActionClass());
                    Intent service = new Intent(getApplicationContext(), service_class);
                    service.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    JSONArray extras = schedule.getActionExtras();
                    for (int i = 0; i < extras.length(); i++) {
                        JSONObject extra = extras.getJSONObject(i);
                        service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    }
                    startService(service);

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }catch (JSONException e ){
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
