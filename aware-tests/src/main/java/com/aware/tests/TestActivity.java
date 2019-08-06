package com.aware.tests;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Scheduler;

public class TestActivity extends Activity {

    int REQUEST_STORAGE = 1;

    Button button_ESMNotification, scheduler_timer, button_delete_schedules, button_jenkins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);

        Intent aware = new Intent(this, Aware.class);
        startService(aware);

        Aware.setSetting(this, Aware_Preferences.DEBUG_FLAG, true);

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

        button_jenkins = findViewById(R.id.btn_jenkins);
        button_jenkins.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TestJenkins jenkins = new TestJenkins();
                jenkins.test(getApplicationContext());
            }
        });

        Button btnSentimentat = findViewById(R.id.btnSentimental);
        btnSentimentat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TestSentimental sentimental = new TestSentimental();
                sentimental.test(getApplicationContext());
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }
}
