package com.aware.tests;

import android.content.Context;

import com.aware.utils.Jenkins;

/**
 * Created by dteixeir on 01/02/2018.
 */

public class TestJenkins implements AwareTest {
    @Override
    public void test(Context context) {
        Jenkins jenkins = new Jenkins(context, "http://jenkins.awareframework.com/job/SUPv2-UW/", true, false);

    }
}
