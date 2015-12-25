package com.aware.utils;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aware.Aware;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by denzil on 15/12/15.
 *
 * This class will make sure we have the latest server SSL certificate to allow downloads from self-hosted servers
 * It also makes sure the client has the most up-to-date certificate and nothing breaks when certificates need to be renewed.
 */
public class SSLManager extends IntentService {

    /**
     * The server we need certificates from
     */
    public static final String EXTRA_SERVER = "aware_server";

    public SSLManager() { super(Aware.TAG + " SSL manager"); }

    @Override
    protected void onHandleIntent(Intent intent) {
        String server_url = intent.getStringExtra("aware_server");
        String aware_host = server_url.substring(0, server_url.indexOf("/index.php"));
        aware_host = aware_host.substring(aware_host.indexOf("//")+2, aware_host.length());
        File host_credentials = new File( getExternalFilesDir(null) + "/Documents/", "credentials/"+ aware_host );
        host_credentials.mkdirs();

//        Ion.with(getApplicationContext())
//                .load("http://" + aware_host + "/public/ca.crt")
//                .write(new File(getExternalFilesDir(null) + "/Documents/credentials/" + aware_host + "/ca.crt"));
        Ion.with(getApplicationContext())
                .load("http://" + aware_host + "/public/server.crt")
                .write(new File(getExternalFilesDir(null) + "/Documents/credentials/" + aware_host + "/server.crt"));
    }

    /**
     * Load the server.crt for HTTPS
     * @param c
     * @param server
     * @return
     * @throws FileNotFoundException
     */
    public static InputStream getHTTPS(Context c, String server) throws FileNotFoundException {
        String aware_host = server.substring(0, server.indexOf("/index.php"));
        aware_host = aware_host.substring(aware_host.indexOf("//")+2, aware_host.length());

        if( aware_host.equalsIgnoreCase("api.awareframework.com") ) {
            aware_host = "awareframework.com"; //we are using a different host for AWARE...
        }

        File host_credentials = new File( c.getExternalFilesDir(null) + "/Documents/", "credentials/"+ aware_host );
        if( host_credentials.exists() ) {
            File[] certs = host_credentials.listFiles();
            for(File crt : certs ) {
                if( crt.getName().equals("server.crt") ) return new FileInputStream(crt);
            }
        }
        return null;
    }

    /**
     * Load the server.crt for MQTT
     * @param c
     * @param server
     * @return
     * @throws FileNotFoundException
     */
    public static InputStream getCA(Context c, String server) throws FileNotFoundException {

        Log.d(Aware.TAG, "Load SSL certificate for Mosquitto: " + server);

        File host_credentials = new File( c.getExternalFilesDir(null) + "/Documents/", "credentials/"+ server );
        if( host_credentials.exists() ) {
            File[] certs = host_credentials.listFiles();
            for(File crt : certs ) {
                if( crt.getName().equals("server.crt") ) return new FileInputStream(crt);
            }
        }
        return null;
    }
}
