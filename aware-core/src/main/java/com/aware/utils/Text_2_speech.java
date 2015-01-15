
package com.aware.utils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;

import com.aware.Aware;

public class Text_2_speech extends Service implements OnInitListener {
    
    private static final String TAG = "AWARE::TTS";
    public static final String EXTRA_TTS_TEXT = "tts_text";
    
    private TextToSpeech ttsObj = null;
    private String to_say = "";

    @Override
    public void onInit(int status) {
        if( status == TextToSpeech.SUCCESS ) {
            if( to_say.length() > 0 ) {
                if( Aware.DEBUG ) Log.d(TAG,"Added to the queue: " + to_say );
                ttsObj.speak(to_say, TextToSpeech.QUEUE_ADD, null);
            } else {
                if( Aware.DEBUG ) Log.e(TAG,"Nothing to say!");
            }
        } else {
            if( Aware.DEBUG ) Log.e(TAG,"Failed to initialize Text-to-Speech engine on this device!");
            stopSelf();
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        ttsObj = new TextToSpeech(this, this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( intent != null ) {
            to_say = intent.getStringExtra(EXTRA_TTS_TEXT);
            
            if( ttsObj != null ) {
                ttsObj.speak(to_say, TextToSpeech.QUEUE_ADD, null);
            }
        }
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if( ttsObj != null ) {
            ttsObj.shutdown();
            if( Aware.DEBUG ) Log.d(TAG,"Done with TTS!");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
