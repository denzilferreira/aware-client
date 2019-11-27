package com.aware

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aware.utils.Aware_Sensor
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.WebSocket

class Websocket : Aware_Sensor() {

    companion object {
        val ACTION_WEBSOCKET_CONNECTED = "ACTION_WEBSOCKET_CONNECTED"
        val ACTION_WEBSOCKET_DISCONNECTED = "ACTION_WEBSOCKET_DISCONNECTED"

        lateinit var websocket: WebSocket
        lateinit var awareSensor : AWARESensorObserver

        fun getSensorObserver() : AWARESensorObserver {
            return awareSensor
        }
    }

    interface AWARESensorObserver {
        fun sendMessage(message : String) {
            websocket.send(message)
        }
        fun onConnected(context : Context) {
            context.sendBroadcast(Intent(ACTION_WEBSOCKET_CONNECTED))
        }
        fun onDisconnected(context: Context) {
            context.sendBroadcast(Intent(ACTION_WEBSOCKET_DISCONNECTED))
        }
    }

    override fun onCreate() {
        super.onCreate()

        TAG = "AWARE::Websocket"

        if (DEBUG) Log.d(TAG, "Websocket service created!")

        AsyncHttpClient.getDefaultInstance().websocket(Aware.getSetting(applicationContext, Aware_Preferences.WEBSOCKET_SERVER), "post",
                object : AsyncHttpClient.WebSocketConnectCallback {
                    override fun onCompleted(ex: java.lang.Exception?, webSocket: WebSocket?) {
                        if (ex != null) {
                            ex.printStackTrace()
                            return
                        }
                        if (webSocket == null) {
                            Log.d(TAG, "Websocket failed to connect?")
                            return
                        }
                        websocket = webSocket
                    }
                })

        awareSensor = object : AWARESensorObserver {
            override fun onConnected(context: Context) {
                super.onConnected(context)
            }
            override fun onDisconnected(context: Context) {
                super.onDisconnected(context)
            }
            override fun sendMessage(message: String) {
                super.sendMessage(message)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(applicationContext, Aware_Preferences.DEBUG_FLAG) == "true"
            Aware.setSetting(applicationContext, Aware_Preferences.STATUS_WEBSOCKET, true)
            if (DEBUG) Log.d(TAG, "Websocket service active...")
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (DEBUG) Log.d(TAG, "Websocket service terminated...")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}