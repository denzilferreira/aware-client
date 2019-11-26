package com.aware

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aware.utils.Aware_Sensor

class EventBus : Aware_Sensor() {

    companion object {
        val TAG = "AWARE::EventBus"
        val ACTION_EVENTBUS_CONNECTED = "ACTION_EVENTBUS_CONNECTED"
        val ACTION_EVENTBUS_DISCONNECTED = "ACTION_EVENTBUS_DISCONNECTED"
    }

    override fun onCreate() {
        super.onCreate()

        if (Aware.DEBUG) Log.d(TAG, "EventBus service created!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(applicationContext, Aware_Preferences.DEBUG_FLAG) == "true"
            Aware.setSetting(applicationContext, Aware_Preferences.STATUS_EVENTBUS, true)

            if (Aware.DEBUG) Log.d(TAG, "EventBus service active...")
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        if (Aware.DEBUG) Log.d(TAG, "EventBus service terminated...")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}