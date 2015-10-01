package com.aware.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.aware.Aware;
import com.aware.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;

public class Https {

	private static final String TAG = "AWARE::HTTPS";

    private static SSLContext sslContext;
//    private static HostnameVerifier sslHostVerifier;
    private static Context sContext;

	public Https(Context c) {
		sContext = c;

        if( c.getPackageName().equalsIgnoreCase("com.aware") ) {
            Intent wearClient = new Intent(sContext, WearClient.class);
            sContext.startService(wearClient);
        }

        try {
            //Load AWARE's SSL public certificate so we can talk with our server
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = new BufferedInputStream(sContext.getResources().openRawResource(R.raw.awareframework));
            Certificate ca = cf.generateCertificate(caInput);
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null); //initialize as empty keystore
            keyStore.setCertificateEntry("ca", ca); //add our certificate to keystore

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores

            //Initialize a SSL connection context
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            //Fix for known-bug on <= JellyBean (4.x)
            System.setProperty("http.keepAlive", "false");

        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	/**
	 * Make a POST to the URL, with the Hashtable<String, String> data, using gzip
	 * @param url
	 * @param data
     * @param is_gzipped
	 * @return String with server response. If gzipped, use Https.undoGZIP on the response.
	 */
	public synchronized String dataPOST(String url, Hashtable<String, String> data, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {

            JSONObject data_json = new JSONObject();

			Enumeration e = data.keys();
			while(e.hasMoreElements()) {
				String key = (String) e.nextElement();
				try {
					data_json.put(key, data.get(key));
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
			}

			if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTPS POST request...\n" + "URL:" + url + "\nData:" + data_json.toString());

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_POST);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_DATA, data_json.toString());
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();

            while( WearClient.wearResponse == null ){
				if( WearClient.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear POST benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            String response = WearClient.wearResponse;
            WearClient.wearResponse = null;

            return response;
		}

		try{

			URL path = new URL(url);

			HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslContext.getSocketFactory());
            path_connection.setReadTimeout(10000);
			path_connection.setConnectTimeout(10000);
			path_connection.setRequestMethod("POST");
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

            if(Aware.DEBUG) {
                print_https_cert(path_connection);
            }

			path_connection.connect();

            if( path_connection.getResponseCode() != HttpsURLConnection.HTTP_OK ) {
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
                Log.d(TAG, "Request: POST, URL: " + url + "\nData:" + builder.build().getEncodedQuery());
                Log.i(TAG,"Answer:" + page_content );
            }

            return page_content;
		}catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
			return null;
		} catch (IllegalStateException e ) {
			Log.e(TAG, e.getMessage());
			return null;
		}
	}

    /**
     * Request a GET from an URL.
     * @param url
     * @return HttpEntity with the content of the reply. Use EntityUtils to get content.
     */
    public synchronized String dataGET(String url, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {

            if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTPS GET request...\n" + "URL:" + url );

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_GET);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();
            while( WearClient.wearResponse == null ){
				if( WearClient.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear GET benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            String response = WearClient.wearResponse;
            WearClient.wearResponse = null;
            return response;
        }

        try {

            URL path = new URL(url);
            HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslContext.getSocketFactory());
            path_connection.setReadTimeout(10000);
            path_connection.setConnectTimeout(10000);
            path_connection.setRequestMethod("GET");
            if( is_gzipped ) path_connection.setRequestProperty("accept-encoding","gzip");

            if(Aware.DEBUG) {
                print_https_cert(path_connection);
            }

            path_connection.connect();

            if( path_connection.getResponseCode() != HttpsURLConnection.HTTP_OK ) {
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
                Log.i(TAG,"Request: GET, URL: " + url);
                Log.i(TAG,"Answer:" + page_content );
            }

            return page_content;

        } catch (IOException e) {
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            return null;
        }
    }

    private void print_https_cert(HttpsURLConnection con){
        if(con!=null){
            try {
                String output = "";

                output+="Response Code : " + con.getResponseCode() +"\n";
                output+="Cipher Suite : " + con.getCipherSuite() + "\n";

                Certificate[] certs = con.getServerCertificates();
                for(Certificate cert : certs){
                    output+=("Cert Type : " + cert.getType()) + "\n";
                    output+=("Cert Hash Code : " + cert.hashCode()) + "\n";
                    output+=("Cert Public Key Algorithm : " + cert.getPublicKey().getAlgorithm()) + "\n";
                    output+=("Cert Public Key Format : " + cert.getPublicKey().getFormat()) + "\n\n";
                }

                Log.d(TAG, output);

            } catch (SSLPeerUnverifiedException e) {
                e.printStackTrace();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

    }
}
