
package com.aware.utils;

import android.net.Uri;
import android.util.Log;

import com.aware.Aware;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;


/**
 * HTML POST/GET client wrapper
 * @author denzil
 */
public class Http {
	
	/**
	 * Logging tag (default = "AWARE")
	 */
	private String TAG = "AWARE::HTML";
    private int timeout = 60 * 1000;

	public Http() {}

    public Http setTimeout(int connection_timeout) {
        timeout = connection_timeout;
        return this;
    }

    /**
     * Request a GET from an URL.
     * @param url GET URL
     * @return String with the content of the reply
     */
    public String dataGET(final String url, final boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        try {

            URL path = new URL(url);
            HttpURLConnection path_connection = (HttpURLConnection) path.openConnection();
            path_connection.setReadTimeout(timeout);
            path_connection.setConnectTimeout(timeout);
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

            String result;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
                StringBuilder page_content = new StringBuilder("");
                String line;
                while ((line = br.readLine()) != null) {
                    page_content.append(line);
                }
                result = page_content.toString();
                br.close();
            }
            stream.close();

            return result;
        } catch (IOException e) {
            Log.e(TAG, "HTTP dataGet io/null error: " + e.getMessage());
            return null;
        }
    }

	/**
	 * Make a POST to the URL, with the Hashtable<String, String> data, using gzip compression
	 * @param url POST URL
	 * @param data Data to send
     * @param is_gzipped Gzip data or not
	 * @return String with server response. If GZipped, use Http.undoGZIP to recover data
	 */
	public String dataPOST(final String url, final Hashtable<String, String> data, final boolean is_gzipped) {
        if( url.length() == 0 ) return null;

		try{

            URL path = new URL(url);
            HttpURLConnection path_connection = (HttpURLConnection) path.openConnection();
            path_connection.setReadTimeout(timeout);
            path_connection.setConnectTimeout(timeout);
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

            String result;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
                StringBuilder page_content = new StringBuilder("");
                String line;
                while ((line = br.readLine()) != null) {
                    page_content.append(line);
                }
                result = page_content.toString();
                br.close();
            }
            stream.close();

            return result;

		}catch (UnsupportedEncodingException e) {
            if (Aware.DEBUG) Log.e(TAG, "HTTP dataPost encoding error: " + e.getMessage());
		} catch (IOException e) {
            if (Aware.DEBUG) Log.e(TAG, "HTTP dataPost io/null error: " + e.getMessage());
		} catch (IllegalStateException e ) {
            if (Aware.DEBUG) Log.e(TAG, "HTTP dataPost state error: " + e.getMessage());
		}
        return null;
	}
}
