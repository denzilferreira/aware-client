package com.aware.utils;

import android.net.Uri;
import android.util.Log;

import com.aware.Aware;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class Https {

    private final String TAG = "AWARE::HTTPS";
    private SSLSocketFactory sslSocketFactory;
    private int timeout = 60 * 1000;

    /**
     * Initialise a HTTPS client
     * @param certificate SSL certificate
     */
    public Https(InputStream certificate) {
        if (certificate == null) {
            //Log.e(TAG, "SSL: unable to read certificate!");
            return;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores

            //Load SSL public certificate so we can talk with the server
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = new BufferedInputStream(certificate);
            Certificate ca = cf.generateCertificate(caInput);

            keyStore.load(null, null); //initialize as empty keystore
            keyStore.setCertificateEntry("ca", ca); //add our certificate to keystore
            trustManagerFactory.init(keyStore); //add our keystore to the trusted keystores

            //Initialize a SSL connection context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            sslSocketFactory = sslContext.getSocketFactory();

            //Fix for known-bug on <= JellyBean (4.x)
            System.setProperty("http.keepAlive", "false");

        } catch (CertificateException e) {
            Log.e(TAG, "CertificateException " + e.getMessage());
        } catch (KeyManagementException e) {
            Log.e(TAG, "KeyManagementException " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "NoSuchAlgorithmException " + e.getMessage());
        } catch (KeyStoreException e) {
            Log.e(TAG, "KeyStoreException " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException " + e.getMessage());
        }
    }

    public Https setTimeout(int connection_timeout) {
        this.timeout = connection_timeout;
        return this;
    }

    /**
     * Make a POST to the URL, with the Hashtable<String, String> data, using gzip
     *
     * @param url POST URL
     * @param data Data to be sent
     * @param is_gzipped Gzip data or not
     * @return String with server response. If gzipped, use Https.undoGZIP on the response.
     */
    public String dataPOST(final String url, final Hashtable<String, String> data, final boolean is_gzipped) {

        if (url.length() == 0) return null;

        try {

            URL path = new URL(url);

            HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslSocketFactory);
            path_connection.setReadTimeout(timeout);
            path_connection.setConnectTimeout(timeout);
            path_connection.setRequestMethod("POST");
            path_connection.setDoOutput(true);

            if (is_gzipped) path_connection.setRequestProperty("accept-encoding", "gzip");

            Uri.Builder builder = new Uri.Builder();
            Enumeration e = data.keys();
            while (e.hasMoreElements()) {
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

            //only debug is there is a problem with the request
            if (path_connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                if (Aware.DEBUG) {
                    Log.d(TAG, "Request: POST, URL: " + url + "\nData:" + builder.build().getEncodedQuery());
                    Log.d(TAG, "Status: " + path_connection.getResponseCode());
                    Log.e(TAG, path_connection.getResponseMessage());
                }
                return null;
            }

            InputStream stream = path_connection.getInputStream();
            if ("gzip".equals(path_connection.getContentEncoding())) {
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
        } catch (UnsupportedEncodingException e) {
//            Log.e(TAG, "Sync HTTPS dataPost encoding error: " + e.getMessage());
        } catch (IOException | NullPointerException e) {
//            Log.e(TAG, "Sync HTTPS dataPost io/null error: " + e.getMessage());
        } catch (IllegalStateException e) {
//            Log.e(TAG, "Sync HTTPS dataPost state error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Request a GET from an URL.
     *
     * @param url GET URL
     * @return HttpEntity with the content of the reply. Use EntityUtils to get content.
     */
    public String dataGET(final String url, final boolean is_gzipped) {
        if (url.length() == 0) return null;

        try {

            URL path = new URL(url);
            HttpsURLConnection path_connection = (HttpsURLConnection) path.openConnection();
            path_connection.setSSLSocketFactory(sslSocketFactory);
            path_connection.setReadTimeout(timeout);
            path_connection.setConnectTimeout(timeout);
            path_connection.setRequestMethod("GET");
            path_connection.setDoInput(true);

            if (is_gzipped) path_connection.setRequestProperty("accept-encoding", "gzip");

            path_connection.connect();

            if (path_connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                if (Aware.DEBUG) {
                    Log.d(TAG, "Request: GET, URL: " + url);
                    Log.d(TAG, "Status: " + path_connection.getResponseCode());
                    Log.e(TAG, path_connection.getResponseMessage());
                }
                return null;
            }

            InputStream stream = path_connection.getInputStream();
            if ("gzip".equals(path_connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }

            String result;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
                StringBuilder page_content = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    page_content.append(line);
                }
                result = page_content.toString();
            }
            stream.close();

            return result;

        } catch (IOException | NullPointerException e) {
            //Log.e(TAG, "Sync HTTPS dataGet io/null error: " + e.getMessage());
        }

        return null;
    }

}
