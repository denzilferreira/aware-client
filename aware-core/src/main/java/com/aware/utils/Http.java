
package com.aware.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aware.Aware;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

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
        Intent wearClient = new Intent(sContext, WearClient.class);
        sContext.startService(wearClient);
	}

    /**
     * Request a GET from an URL.
     * @param url
     * @return HttpEntity with the content of the reply. Use EntityUtils to get content.
     */
    public synchronized HttpResponse dataGET(String url, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {

            if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTP GET request...\n" + "URL:" + url );

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_GET);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();
            while( WearProxy.wearResponse == null ){
                if( WearProxy.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear GET benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            HttpResponse response = WearProxy.wearResponse;
            WearProxy.wearResponse = null;

            return response;
        }
        try {
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(url);
            if( is_gzipped ) httpGet.addHeader("Accept-Encoding", "gzip"); //send data compressed
            HttpResponse httpResponse = httpClient.execute(httpGet);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if( statusCode != 200 ) {
                if(Aware.DEBUG) {
                    Log.d(TAG, "Status: "+ statusCode);
                    Log.e(TAG,"URL:" + url);
                    Log.e(TAG,EntityUtils.toString(httpResponse.getEntity()));
                }
            }
            return httpResponse;
        } catch (ClientProtocolException e) {
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            return null;
        } catch (IOException e) {
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            return null;
        }
    }

	/**
	 * Make a POST to the URL, with the ArrayList<NameValuePair> data, using gzip compression
	 * @param url
	 * @param data
     * @param is_gzipped
	 * @return HttpEntity with server response. Use EntityUtils to extract values or object
	 */
	public synchronized HttpResponse dataPOST(String url, ArrayList<NameValuePair> data, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {
			JSONObject data_json = new JSONObject();
			for(NameValuePair valuePair : data ) {
				try {
					data_json.put(valuePair.getName(),valuePair.getValue());
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTP POST request...\n" + "URL:" + url + "\nData:" + data_json.toString());

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_POST);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_DATA, data_json.toString());
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();
            while( WearProxy.wearResponse == null ){
                if( WearProxy.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear POST benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            HttpResponse response = WearProxy.wearResponse;
            WearProxy.wearResponse = null;

            return response;
		}

		try{
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(url);
            if( is_gzipped ) httpPost.addHeader("Accept-Encoding", "gzip"); //send data compressed
			httpPost.setEntity(new UrlEncodedFormEntity(data));
			HttpResponse httpResponse = httpClient.execute(httpPost);
		
			int statusCode = httpResponse.getStatusLine().getStatusCode();
			if (statusCode != 200 ) {
				if(Aware.DEBUG) {
					Log.d(TAG, "Status: " + statusCode );
					Log.e(TAG, "URL:" + url);
					Log.e(TAG, EntityUtils.toString(httpResponse.getEntity()) );
				}
			}
            return httpResponse;
		}catch (UnsupportedEncodingException e) {
			Log.e(TAG,e.getMessage());
			return null;
		} catch (ClientProtocolException e) {
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

    /**
     * Given a gzipped server response, restore content
     * @param response
     * @return
     */
    public static String undoGZIP(HttpResponse response) {
        String decoded = "";
        HttpEntity gzipped = response.getEntity();
        if( gzipped != null ) {
            try {
                InputStream in = gzipped.getContent();
                Header contentEncode = response.getFirstHeader("Content-Encoding");
                if( contentEncode != null && contentEncode.getValue().equalsIgnoreCase("gzip") ) {
                    in = new GZIPInputStream(in);
                    decoded = restore(in);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return decoded;
    }
    
    /**
     * Decodes an compressed stream
     * @param is
     * @return
     */
    private static String restore(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
