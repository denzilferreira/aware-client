package com.aware.tests;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.aware.ESM;
import com.aware.ui.ESM_Queue;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Freetext;
import com.aware.ui.esms.ESM_Question;

import org.json.JSONException;

/**
 * Created by denzilferreira on 02/03/16.
 */
public class TestActivity extends Activity {

    int REQUEST_STORAGE = 1;

    Button button_ESMNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);

        button_ESMNotification=(Button)findViewById(R.id.button_ESMNotification);
        button_ESMNotification.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    TestESM testESM = new TestESM();
                    testESM.test(getApplicationContext());
                }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }
}
