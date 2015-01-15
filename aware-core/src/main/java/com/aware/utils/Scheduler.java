package com.aware.utils;

import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by denzil on 16/12/14.
 * TODO: create a service that schedules intents with extras
 */
public class Scheduler extends Service {

    private static AlarmManager scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
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
