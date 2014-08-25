/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.aware.Aware;

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
	 * Make a POST to the URL, with the ArrayList<NameValuePair> data
	 * @param url
	 * @param data
	 * @return HttpEntity with server response. Use EntityUtils to extract values or object
	 */
	public HttpResponse dataPOST(String url, ArrayList<NameValuePair> data) {
		try{
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(url);
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
	 * Request a GET from an URL. 
	 * @param url
	 * @return HttpEntity with the content of the reply. Use EntityUtils to get content.
	 */
	public HttpResponse dataGET(String url) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(url);
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
