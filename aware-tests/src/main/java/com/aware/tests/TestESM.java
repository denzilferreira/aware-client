package com.aware.tests;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aware.Aware;
import com.aware.ESM;
import com.aware.ui.ESM_Queue;
import com.aware.ui.esms.ESMFactory;
import com.aware.ui.esms.ESM_Checkbox;
import com.aware.ui.esms.ESM_DateTime;
import com.aware.ui.esms.ESM_Freetext;
import com.aware.ui.esms.ESM_Likert;
import com.aware.ui.esms.ESM_PAM;
import com.aware.ui.esms.ESM_Question;
import com.aware.ui.esms.ESM_QuickAnswer;
import com.aware.ui.esms.ESM_Radio;
import com.aware.ui.esms.ESM_Scale;
import com.aware.ui.esms.ESM_Number;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by denzil on 04/03/16.
 */
public class TestESM implements AwareTest {

    @Override
    public void test(Context context) {
//        testESMS(context);
//        trialESMS(context);
        testFlow(context);
//        testTimeoutQueue(context);
//        testNumeric(context);
//        testDateTime(context);
//        testPAM(context);
    }



    private void testPAM(Context context) {
        ESMFactory factory = new ESMFactory();

        try {
            ESM_PAM q1 = new ESM_PAM();
            q1.setTitle("PAM")
                    .setInstructions("Pick the closest to how you feel right now.")
                    .setSubmitButton("OK")
                    .setNotificationTimeout(10)
                    .setTrigger("AWARE Test");

            factory.addESM(q1);

            Log.d(Aware.TAG, factory.build());

            ESM.queueESM(context, factory.build());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void testDateTime(Context context) {
        ESMFactory factory = new ESMFactory();

        try {
            ESM_DateTime q1 = new ESM_DateTime();
            q1.setTitle("Date and time")
                    .setInstructions("When did this happen?")
                    .setSubmitButton("OK")
                    .setTrigger("AWARE Test");

            factory.addESM(q1);

            Log.d(Aware.TAG, factory.build());

            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            context.sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void testNumeric(Context context) {
        ESMFactory factory = new ESMFactory();

        try {
            ESM_Number q1 = new ESM_Number();
            q1.setTitle("Number")
                    .setInstructions("We only accept a number!")
                    .setSubmitButton("OK")
                    .setTrigger("AWARE Test");

            factory.addESM(q1);

            Log.d(Aware.TAG, factory.build());

            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            context.sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void testFlow(Context context) {
        ESMFactory factory = new ESMFactory();

        try {
            ESM_QuickAnswer q0 = new ESM_QuickAnswer();
            q0.addQuickAnswer("Yes")
                    .addQuickAnswer("No")
                    .setTitle("Is this a good time to answer?")
                    .addFlow("Yes", 1) //0-base index
                    .addFlow("No", 2);

            ESM_PAM q1 = new ESM_PAM();
            q1.setTitle("How do you feel today?")
                    .setSubmitButton("Thanks!");

            ESM_Radio q2 = new ESM_Radio();
            q2.addRadio("Eating")
                    .addRadio("Working")
                    .addRadio("Not alone")
                    .setTitle("Why is that?")
                    .setSubmitButton("Thanks!");

            factory.addESM(q0);
            factory.addESM(q1);
            factory.addESM(q2);

            ESM.queueESM(context, factory.build());

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void testESMS(Context context) {
        ESMFactory factory = new ESMFactory();
        try {
            ESM_Freetext esmFreetext = new ESM_Freetext();
            esmFreetext.setTitle("Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext Freetext ")
                    .setTrigger("test")
                    .setSubmitButton("OK")
                    .setNotificationTimeout(5)
                    .setInstructions("Freetext ESM");

            ESM_Checkbox esmCheckbox = new ESM_Checkbox();
            esmCheckbox.addCheck("Check 1")
                    .addCheck("Check 2")
                    .addCheck("Other")
                    .setTitle("Checkbox")
                    .setTrigger("test")
                    .setSubmitButton("OK")
                    .setInstructions("Checkbox ESM");

            ESM_Likert esmLikert = new ESM_Likert();
            esmLikert.setLikertMax(5)
                    .setLikertMaxLabel("Great")
                    .setLikertMinLabel("Poor")
                    .setLikertStep(1)
                    .setTitle("Likert")
                    .setInstructions("Likert ESM")
                    .setTrigger("test")
                    .setSubmitButton("OK");

            ESM_QuickAnswer esmQuickAnswer = new ESM_QuickAnswer();
            esmQuickAnswer.addQuickAnswer("Yes")
                    .addQuickAnswer("No")
                    .setTrigger("test")
                    .setInstructions("Quick Answers ESM");

            ESM_Radio esmRadio = new ESM_Radio();
            esmRadio.addRadio("Radio 1")
                    .addRadio("Radio 2")
                    .setTitle("Radios")
                    .setInstructions("Radios ESM")
                    .setSubmitButton("OK");

            ESM_Scale esmScale = new ESM_Scale();
            esmScale.setScaleMax(100)
                    .setScaleMin(0)
                    .setScaleStart(50)
                    .setScaleMaxLabel("Perfect")
                    .setScaleMinLabel("Poor")
                    .setScaleStep(10)
                    .setTitle("Scale")
                    .setInstructions("Scale ESM")
                    .setSubmitButton("OK");

            ESM_PAM esmPAM = new ESM_PAM();
            esmPAM.setTitle("PAM")
                    .setInstructions("Pick the closest to how you feel right now.")
                    .setSubmitButton("OK")
                    .setTrigger("AWARE Test");

            factory.addESM(esmFreetext);
            factory.addESM(esmCheckbox);
            factory.addESM(esmLikert);
            factory.addESM(esmQuickAnswer);
            factory.addESM(esmRadio);
            factory.addESM(esmScale);
            factory.addESM(esmPAM);

//            ESM.queueESM(context, factory.build());
            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            context.sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void trialESMS(Context context) {
        ESMFactory factory = new ESMFactory();
        try {
            ESM_Freetext esmFreetext = new ESM_Freetext();
            esmFreetext.setTitle("Freetext")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK")
                    .setInstructions("Freetext ESM");

            ESM_Checkbox esmCheckbox = new ESM_Checkbox();
            esmCheckbox.addCheck("Check 1")
                    .addCheck("Check 2")
                    .addCheck("Other")
                    .setTitle("Checkbox")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK")
                    .setInstructions("Checkbox ESM");

            ESM_Likert esmLikert = new ESM_Likert();
            esmLikert.setLikertMax(5)
                    .setLikertMaxLabel("Great")
                    .setLikertMinLabel("Poor")
                    .setLikertStep(1)
                    .setTitle("Likert")
                    .setInstructions("Likert ESM")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK");

            ESM_QuickAnswer esmQuickAnswer = new ESM_QuickAnswer();
            esmQuickAnswer.addQuickAnswer("Yes")
                    .addQuickAnswer("No")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK")
                    .setInstructions("Quick Answers ESM");

            ESM_Radio esmRadio = new ESM_Radio();
            esmRadio.addRadio("Radio 1")
                    .addRadio("Radio 2")
                    .setTitle("Radios")
                    .setInstructions("Radios ESM")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK");

            ESM_Scale esmScale = new ESM_Scale();
            esmScale.setScaleMax(100)
                    .setScaleMin(0)
                    .setScaleStart(50)
                    .setScaleMaxLabel("Perfect")
                    .setScaleMinLabel("Poor")
                    .setScaleStep(10)
                    .setTitle("Scale")
                    .setInstructions("Scale ESM")
                    .setExpirationThreshold(0)
                    .setSubmitButton("OK");

            factory.addESM(esmFreetext);
            factory.addESM(esmCheckbox);
            factory.addESM(esmLikert);
            factory.addESM(esmQuickAnswer);
            factory.addESM(esmRadio);
            factory.addESM(esmScale);

            Intent queue = new Intent(ESM.ACTION_AWARE_TRY_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            context.sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void testTimeoutQueue(Context context) {
        ESMFactory factory = new ESMFactory();
        try {
            ESM_Freetext esmFreetext = new ESM_Freetext();
            esmFreetext.setTitle("Freetext")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setSubmitButton("OK")
                    .setInstructions("Freetext ESM");

            ESM_Checkbox esmCheckbox = new ESM_Checkbox();
            esmCheckbox.addCheck("Check 1")
                    .addCheck("Check 2")
                    .addCheck("Other")
                    .setTitle("Checkbox")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setSubmitButton("OK")
                    .setInstructions("Checkbox ESM");

            ESM_Likert esmLikert = new ESM_Likert();
            esmLikert.setLikertMax(5)
                    .setLikertMaxLabel("Great")
                    .setLikertMinLabel("Poor")
                    .setLikertStep(1)
                    .setTitle("Likert")
                    .setInstructions("Likert ESM")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setSubmitButton("OK");

            ESM_QuickAnswer esmQuickAnswer = new ESM_QuickAnswer();
            esmQuickAnswer.addQuickAnswer("Yes")
                    .addQuickAnswer("No")
                    .setTrigger("test")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setInstructions("Quick Answers ESM");

            ESM_Radio esmRadio = new ESM_Radio();
            esmRadio.addRadio("Radio 1")
                    .addRadio("Radio 2")
                    .setTitle("Radios")
                    .setInstructions("Radios ESM")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setSubmitButton("OK");

            ESM_Scale esmScale = new ESM_Scale();
            esmScale.setScaleMax(100)
                    .setScaleMin(0)
                    .setScaleStart(50)
                    .setScaleMaxLabel("Perfect")
                    .setScaleMinLabel("Poor")
                    .setScaleStep(10)
                    .setTitle("Scale")
                    .setInstructions("Scale ESM")
                    .setExpirationThreshold(0)
                    .setNotificationTimeout(10)
                    .setSubmitButton("OK");

            factory.addESM(esmFreetext);
            factory.addESM(esmCheckbox);
            factory.addESM(esmLikert);
            factory.addESM(esmQuickAnswer);
            factory.addESM(esmRadio);
            factory.addESM(esmScale);

            Intent queue = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);
            queue.putExtra(ESM.EXTRA_ESM, factory.build());
            context.sendBroadcast(queue);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
