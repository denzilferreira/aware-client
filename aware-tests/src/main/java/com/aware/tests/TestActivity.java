package com.aware.tests;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.aware.ESM;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Freetext;

import org.json.JSONException;

/**
 * Created by denzilferreira on 02/03/16.
 */
public class TestActivity extends Activity {
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

            Log.d("DENZIL", factory.build());

            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
