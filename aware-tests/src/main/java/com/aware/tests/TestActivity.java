package com.aware.tests;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.providers.Scheduler_Provider;
import com.aware.ui.ESM_Queue;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Freetext;
import com.aware.ui.esms.ESM_Question;
import com.aware.utils.Scheduler;

import org.json.JSONException;

public class TestActivity extends Activity {

    int REQUEST_STORAGE = 1;

    Button button_ESMNotification, scheduler_timer, button_delete_schedules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);

        Intent aware = new Intent(this, Aware.class);
        startService(aware);

        button_ESMNotification = (Button) findViewById(R.id.button_ESMNotification);
        button_ESMNotification.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM, true);
                TestESM testESM = new TestESM();
                testESM.test(getApplicationContext());
            }
        });

        scheduler_timer = (Button) findViewById(R.id.btn_test_timer);
        scheduler_timer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TestScheduler testScheduler = new TestScheduler();
                testScheduler.test(getApplicationContext());
            }
        });

        button_delete_schedules = (Button) findViewById(R.id.btn_clear_schedulers);
        button_delete_schedules.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Scheduler.clearSchedules(getApplicationContext());
                Toast.makeText(getApplicationContext(), "Cleared!", Toast.LENGTH_SHORT).show();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }
}
