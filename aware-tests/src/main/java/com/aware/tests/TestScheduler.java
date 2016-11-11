package com.aware.tests;

import android.content.Context;
import android.database.Cursor;

import com.aware.Aware;
import com.aware.providers.Scheduler_Provider;
import com.aware.utils.Scheduler;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by denzil on 11/11/2016.
 */

public class TestScheduler implements AwareTest {

    @Override
    public void test(Context context) {

        context.getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE '" + context.getPackageName() + "'", null);

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(System.currentTimeMillis());
        start.add(Calendar.MINUTE, 5);

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(System.currentTimeMillis());
        end.add(Calendar.MINUTE, 40);

        ArrayList<Long> random = Scheduler.random_times(start, end, 5, 5);
        for (Long time : random) {
            Calendar aux = Calendar.getInstance();
            aux.setTimeInMillis(time);
            setTimerScheduler(context, aux);
        }

        Aware.startScheduler(context);
    }

    private void setTimerScheduler(Context c, Calendar time) {
        try {
            Scheduler.Schedule timer = new Scheduler.Schedule("test_scheduler_" + time.getTimeInMillis());
            timer.setTimer(time)
                    .setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                    .setActionClass("TEST_SCHEDULER");

            Scheduler.saveSchedule(c, timer);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
