package com.aware.tests

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aware.Aware
import com.aware.plugin.sentimental.Plugin
import com.aware.plugin.sentimental.Settings

class TestSentimental : AwareTest {
    override fun test(context: Context?) {
        Aware.setSetting(context, Settings.PLUGIN_SENTIMENTAL_PACKAGES, "com.whatsapp", "com.aware.plugin.sentimental")
        Aware.startPlugin(context, "com.aware.plugin.sentimental")
        Plugin.setSensorObserver(object : Plugin.Companion.AWARESensorObserver {
            override fun onTextContextChanged(data: ContentValues) {
                Log.d("TestSentimental", data.toString())
            }
        })
    }
}
