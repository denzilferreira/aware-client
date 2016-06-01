
package com.aware.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.aware.Aware;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

/**
 * HTML POST/GET client wrapper
 * @author denzil
 */
public class Http {
	
	/**
	 * Logging tag (default = "AWARE")
	 */
	private static String TAG = "AWARE::HTML";

	private static Context sContext;

	public Http(Context c) {
		sContext = c;
	}

    /**
     * Request a GET from an URL.
     * @param url
     * @return String with the content of the reply
     */
    public synchronized String dataGET(String url, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        try {

            URL path = new URL(url);
            HttpURLConnection path_connection = (HttpURLConnection) path.openConnection();
            path_connection.setReadTimeout(10000);
            path_connection.setConnectTimeout(10000);
            path_connection.setRequestMethod("GET");
            path_connection.setDoInput(true);

            if( is_gzipped ) path_connection.setRequestProperty("accept-encoding","gzip");

            path_connection.connect();

            if( path_connection.getResponseCode() != HttpURLConnection.HTTP_OK ) {
                if (Aware.DEBUG) {
                    Log.d(TAG,"Request: GET, URL: " + url);
                    Log.d(TAG, "Status: " + path_connection.getResponseCode() );
                    Log.e(TAG, path_connection.getResponseMessage() );
                }
                return null;
            }

            InputStream stream = path_connection.getInputStream();
            if("gzip".equals(path_connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(stream));

            String page_content = "";
            String line;
            while( (line = br.readLine()) != null ) {
                page_content+=line;
            }

            if (Aware.DEBUG) {
//                Log.i(TAG,"Request: GET, URL: " + url);
//                Log.i(TAG,"Answer:" + page_content );
            }

            return page_content;

        } catch (IOException e) {
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            return null;
        }
    }

	/**
	 * Make a POST to the URL, with the Hashtable<String, String> data, using gzip compression
	 * @param url
	 * @param data
     * @param is_gzipped
	 * @return String with server response. If GZipped, use Http.undoGZIP to recover data
	 */
	public synchronized String dataPOST(String url, Hashtable<String, String> data, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

		try{

            URL path = new URL(url);
            HttpURLConnection path_connection = (HttpURLConnection) path.openConnection();
            path_connection.setReadTimeout(10000);
            path_connection.setConnectTimeout(10000);
            path_connection.setRequestMethod("POST");
            path_connection.setDoOutput(true);

            if( is_gzipped ) path_connection.setRequestProperty("accept-encoding","gzip");

            Uri.Builder builder = new Uri.Builder();
            Enumeration e = data.keys();
            while(e.hasMoreElements()) {
                String key = (String) e.nextElement();
                builder.appendQueryParameter(key, data.get(key));
            }

            OutputStream os = path_connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(builder.build().getEncodedQuery());
            writer.flush();
            writer.close();
            os.close();

            path_connection.connect();

            if( path_connection.getResponseCode() != HttpURLConnection.HTTP_OK ) {
                if (Aware.DEBUG) {
                    Log.d(TAG,"Request: POST, URL: " + url + "\nData:" + builder.build().getEncodedQuery());
                    Log.d(TAG, "Status: " + path_connection.getResponseCode() );
                    Log.e(TAG, path_connection.getResponseMessage() );
                }
                return null;
            }

            InputStream stream = path_connection.getInputStream();
            if("gzip".equals(path_connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(stream));

            String page_content = "";
            String line;
            while( (line = br.readLine()) != null ) {
                page_content+=line;
            }

            if (Aware.DEBUG) {
//                Log.d(TAG, "Request: POST, URL: " + url + "\nData:" + builder.build().getEncodedQuery());
//                Log.i(TAG,"Answer:" + page_content );
            }

            return page_content;
		}catch (UnsupportedEncodingException e) {
			Log.e(TAG,e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e(TAG,e.getMessage());
			return null;
		} catch (IllegalStateException e ) {
			Log.e(TAG,e.getMessage());
			return null;
		}
	}
}
