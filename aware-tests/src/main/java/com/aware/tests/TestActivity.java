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

        //Test ESMs functionalitiess
        new TestESM().test(this);
    }
}
