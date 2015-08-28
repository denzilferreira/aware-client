package com.aware.utils;

import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class Scheduler extends Service {
    private static AlarmManager scheduler;

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";

    public static final String TRIGGER_HOUR = "hour"; //done
    public static final String TRIGGER_TIMER = "timer"; //done
    public static final String TRIGGER_WEEKDAY = "weekday"; //done
    public static final String TRIGGER_MONTH = "month"; //done
    public static final String TRIGGER_CONTEXT = "context"; //done

    public static final String TRIGGER_RANDOM = "random";
    public static final String TRIGGER_RANDOM_TYPE = "random_type";
    public static final int RANDOM_TYPE_HOUR = 0;
    public static final int RANDOM_TYPE_WEEKDAY = 1;
    public static final int RANDOM_TYPE_MONTH = 2;
    public static final String TRIGGER_RANDOM_ELEMENTS = "random_elements";
    public static final String TRIGGER_RANDOM_MAX = "random_max";

    public static final String ACTION_TYPE = "type";
    public static final String ACTION_CLASS = "class";
    public static final String ACTION_EXTRAS = "extras";

    @Override
    public void onCreate() {
        super.onCreate();

        scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

//            JSONObject new_schedule = new JSONObject();
//            JSONObject trigger = new JSONObject();
//            trigger.put("hour", new JSONArray().put("21:00"));
//
//            JSONObject action = new JSONObject();
//            action.put("type", "broadcast");
//            action.put("classname","ACTION_AWARE_VIBRATE");
//
//            JSONArray extras = new JSONArray();
//            JSONObject extra = new JSONObject();
//            extra.put("key","test");
//            extra.put("value","123");
//            extras.put(extra);
//
//            action.put("extras", extras);
//
//            new_schedule.put("trigger", trigger);
//            new_schedule.put("action", action);
//
//            JSONObject scheduleJSON = new JSONObject().put("schedule", new_schedule);

            Schedule schedule = new Schedule();

            Log.d(Aware.TAG, schedule.toString());
    }

    public class Schedule {
        private JSONObject schedule;
        private JSONObject trigger;
        private JSONObject action;

        public Schedule(){
            this.schedule = new JSONObject();
            this.action = new JSONObject();
            this.trigger = new JSONObject();

            try {
                this.schedule.put(SCHEDULE_ACTION, this.action);
                this.schedule.put(SCHEDULE_TRIGGER, this.trigger);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            String json = "";
            try {
                json = this.schedule.toString(5);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return json;
        }

        /**
         * Get schedule trigger
         * @return
         */
        public JSONObject getTrigger() {
            return this.trigger;
        }

        /**
         * Get schedule action
         * @return
         */
        public JSONObject getAction() {
            return this.action;
        }

        /**
         * Add trigger hours. JSONArray with 0-23 hours
         * e.g. [ 9, 10, 11, 12 ] triggers this at 9AM, 10AM, 11AM and noon.
         * @param hours
         */
        public void setHours( JSONArray hours ) {
            this.trigger.put(TRIGGER_HOUR, hours);
        }

        /**
         * Get scheduled hours
         * @return
         */
        public JSONArray getHours() {
            if( this.trigger.has(TRIGGER_HOUR) ) {
                return this.trigger.get(TRIGGER_HOUR);
            }
            return new JSONArray();
        }

        /**
         * Set trigger to a specified date and time
         * @param date
         */
        public void setTimer( Calendar date ) {
            this.trigger.put(TRIGGER_TIMER, date.getTimeInMillis() );
        }

        /**
         * Get trigger specific date and time
         * @return
         */
        public Calendar getTimer() {
            if( this.trigger.has(TRIGGER_TIMER) ) {
                return new Calendar().setTimeInMillis(this.trigger.get(TRIGGER_TIMER));
            }
            return null;
        }

        /**
         * Set trigger on days of the week
         * e.g., ['Monday','Tuesday'] for every Monday and Tuesdays
         * @param days_of_week
         */
        public void setWeekdays( JSONArray days_of_week ) {
            this.trigger.put(TRIGGER_WEEKDAY, days_of_week );
        }

        /**
         * Get days of week in which this trigger is scheduled
         * @return
         */
        public JSONArray getWeekdays() {
            if( this.trigger.has(TRIGGER_WEEKDAY) ) {
                return this.trigger.get(TRIGGER_WEEKDAY);
            }
            return new JSONArray();
        }

        /**
         * Set months where schedule occurs
         * e.g., ['January','February'] for every January and February
         * @param months
         */
        public void setMonths( JSONArray months ) {
            this.trigger.put(TRIGGER_MONTH, months);
        }

        /**
         * Get months where schedule is valid
         * @return
         */
        public JSONArray getMonths() {
            if( this.trigger.has(TRIGGER_MONTH) ) {
                return this.trigger.get(TRIGGER_MONTH);
            }
            return new JSONArray();
        }

        /**
         * Listen for this contextual broadcast to trigger this schedule
         * e.g., ACTION_AWARE_CALL_ACCEPTED runs this schedule when the user has answered a phone call
         * @param broadcasts
         */
        public void setContext( String broadcast ) {
            this.trigger.put(TRIGGER_CONTEXT, broadcast);
        }

        /**
         * Get the contextual broadcast that triggers this schedule
         * @return
         */
        public String getContexts() {
            if( this.trigger.has(TRIGGER_CONTEXT) ) {
                return this.trigger.get(TRIGGER_CONTEXT);
            }
            return "";
        }

        public void setRandom( int RANDOM_TYPE ) {
            switch( RANDOM_TYPE ) {
                case RANDOM_TYPE_HOUR:
                    //Get valid hours
                    if( this.trigger.has(TRIGGER_HOUR) ) {
                        JSONArray hours = this.trigger.get(TRIGGER_HOUR);

                    }
                    break;
                case RANDOM_TYPE_MONTH:
                    break;
                case RANDOM_TYPE_WEEKDAY:
                    break;
            }
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
