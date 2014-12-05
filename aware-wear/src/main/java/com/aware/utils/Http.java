/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware.utils;

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
	
	/**
	 * Make a POST to the URL, with the ArrayList<NameValuePair> data, using gzip compression
	 * @param url
	 * @param data
     * @param is_gzipped
	 * @return HttpEntity with server response. Use EntityUtils to extract values or object
	 */
	public HttpResponse dataPOST(String url, ArrayList<NameValuePair> data, boolean is_gzipped) {
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
	
	/**
	 * Request a GET from an URL. 
	 * @param url
	 * @return HttpEntity with the content of the reply. Use EntityUtils to get content.
	 */
	public HttpResponse dataGET(String url, boolean is_gzipped) {
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
}
