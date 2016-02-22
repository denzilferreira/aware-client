package com.aware;

import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import android.test.AndroidTestCase;

import com.aware.ui.esms.ESM_Freetext;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by denzilferreira on 22/02/16.
 */
public class AwareTests extends AndroidTestCase {

    public void testESMFreetext() {
        Intent aware = new Intent(getContext(), Aware.class);
        getContext().startService(aware);

        Aware.setSetting(getContext(), Aware_Preferences.STATUS_ESM, true);
        Aware.startSensor(getContext(), Aware_Preferences.STATUS_ESM);

        try {
            ESM_Freetext freetext = new ESM_Freetext();
            freetext.setTitle("Test title")
                    .setInstructions("Test instructions")
                    .setNextButton("OK")
                    .setExpirationThreshold(0)
                    .setTrigger(getName());

            Intent esmIntent = new Intent(ESM.ACTION_AWARE_QUEUE_ESM);

            JSONArray queue = new JSONArray();
            queue.put(freetext.build());

            esmIntent.putExtra(ESM.EXTRA_ESM, queue.toString());

            getContext().sendBroadcast(esmIntent);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
