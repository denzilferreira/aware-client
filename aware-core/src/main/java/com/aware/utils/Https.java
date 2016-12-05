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
//    private static Context sContext;

    /**
     * The InputStream certificate should be:
     * getResources().openRawResource(R.raw.yourcertificate)<br/>
     * where the certificate is a .crt public key for connecting to your server.
     * @param c
     * @param certificate
     */
	public Https(Context c, InputStream certificate ) {
//		sContext = c;

        try {

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores

            if( certificate != null ) {
                //Load SSL public certificate so we can talk with the server
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                InputStream caInput = new BufferedInputStream(certificate);
                Certificate ca = cf.generateCertificate(caInput);
                keyStore.load(null, null); //initialize as empty keystore
                keyStore.setCertificateEntry("ca", ca); //add our certificate to keystore
                trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores
            }

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

		try{

			URL path = new URL(url);

			HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslContext.getSocketFactory());
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

            if(Aware.DEBUG) {
//                print_https_cert(path_connection);
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

//            if (Aware.DEBUG) {
//                Log.d(TAG, "Request: POST, URL: " + url + "\nData:" + builder.build().getEncodedQuery());
//                Log.i(TAG,"Answer:" + page_content );
//            }

            return page_content;
		}catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.getMessage());
			return null;
		} catch (IOException | NullPointerException e) {
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

        try {

            URL path = new URL(url);
            HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslContext.getSocketFactory());
            path_connection.setReadTimeout(10000);
            path_connection.setConnectTimeout(10000);
            path_connection.setRequestMethod("GET");
            path_connection.setDoInput(true);

            if( is_gzipped ) path_connection.setRequestProperty("accept-encoding","gzip");

            if(Aware.DEBUG) {
//                print_https_cert(path_connection);
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
//                Log.i(TAG,"Request: GET, URL: " + url);
//                Log.i(TAG,"Answer:" + page_content );
            }

            return page_content;

        } catch (IOException | NullPointerException e) {
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            return null;
        }
    }

    private void print_https_cert(HttpsURLConnection con){
        if(con!=null){
            try {
                String output = "Using SSL to connect to server!\n";
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
