package com.awareframework.aware_tests;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.aware.ESM;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Freetext;
import com.aware.ui.esms.ESM_Radio;

import org.json.JSONException;

/**
 * Created by denzilferreira on 29/02/16.
 */
public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ESMFactory factory = new ESMFactory();

        try {
            ESM_Freetext q1 = new ESM_Freetext();
            q1.setTitle("Question 1")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setNextButton("Next")
                    .setInstructions("This is question 1");

            factory.addESM(q1);

            ESM_Radio q2 = new ESM_Radio();
            q2.addRadio("Option 2");
            q2.setTitle("Question 2").setTrigger("test").setExpirationThreshold(0).setNextButton("OK").setInstructions("This is question 2");

            factory.addESM(q2);

            Log.d("DENZIL", factory.build());

            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
