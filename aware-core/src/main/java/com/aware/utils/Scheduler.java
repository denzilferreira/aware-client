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

public class Scheduler extends Service {
    private static AlarmManager scheduler;

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";

    public static final String TRIGGER_HOUR = "hour";
    public static final String TRIGGER_TIMER = "timer";
    public static final String TRIGGER_WEEKDAY = "weekday";
    public static final String TRIGGER_MONTH = "month";
    public static final String TRIGGER_CONTEXT = "context";
    public static final String TRIGGER_RANDOM = "random";
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

    class Schedule {
        public JSONObject schedule;
        public JSONObject trigger;
        public JSONObject action;

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
         * Add hour to schedule
         * @param hour [0-23]
         */
        public void addHour( int hour ) {
            if( ! this.trigger.has(TRIGGER_HOUR) ) {
                try {
                    this.trigger.put(TRIGGER_HOUR, new JSONArray());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                JSONArray hours = this.trigger.getJSONArray(TRIGGER_HOUR);
                hours.put(hour);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        /**
         * Get scheduled hours
         * @return
         */
        public JSONArray getHours() {
            JSONArray hours;
            try {
                hours = this.trigger.getJSONArray(TRIGGER_HOUR);
            } catch (JSONException e) {
                hours = new JSONArray();
            }
            return hours;
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
