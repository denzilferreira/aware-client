package com.aware.phone;

import android.app.Application;
import android.content.Intent;
import android.test.ApplicationTestCase;
import android.util.Log;

import com.aware.ESM;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Freetext;

import org.json.JSONException;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 * Created by denzil on 02/03/16.
 */
public class AwareTest extends ApplicationTestCase<Application> {
    public AwareTest() {
        super(Application.class);
    }

    public void testEsmRequest() {
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
            getApplication().sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
