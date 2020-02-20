package com.aware.utils;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;

/**
 * Created by denzil on 15/12/15.
 * This class will make sure we have the latest server SSL certificate to allow downloads from self-hosted servers
 * It also makes sure the client has the most up-to-date certificate and nothing breaks when certificates need to be renewed.
 */
public class SSLManager {
    /**
     * Handle a study URL.  Fetch data from query parameters if it is there.  Otherwise,
     * use the classic method of downloading the certificate over http.  Enforces the key
     * management policy.
     *
     * @param context app context
     * @param url     full URL, including protocol and query arguments
     * @param block   if true, this method blocks, otherwise downloading is in background.
     */
    public static void handleUrl(Context context, String url, boolean block) {
        // Warning: jelly_bean changes behavior of decoding "+".  Make sure that both
        // " " and "+" are %-encoded.
        Uri study_uri = Uri.parse(url);
        String protocol = study_uri.getScheme();
        if (protocol.equalsIgnoreCase("http")) return; //no need to do anything for http server

        String hostname = study_uri.getHost();
        if (study_uri.getQuery() != null) {
            // If it is in URL parameters, always unconditionally handle it
            String crt = study_uri.getQueryParameter("crt");
            String crt_url = study_uri.getQueryParameter("crt_url");
            String crt_sha256 = study_uri.getQueryParameter("crt_sha256");
            if (crt != null || crt_url != null || crt_sha256 != null)
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Certificates: Handling URL via query parameters: " + hostname);
            handleCrtParameters(context, hostname, crt, crt_sha256, crt_url);
        } else {
            if (Aware.getSetting(context, Aware_Preferences.KEY_STRATEGY).equals("once")) {
                // With "once" management, we only retrieve if we have not gotten cert yet.
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Certificates: Downloading crt if not present: " + hostname);
                if (!hasCertificate(context, hostname)) {
                    downloadCertificate(context, protocol, hostname, true);
                } else {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Certificates: Already present and key_management=once: " + hostname);
                }
            } else {
                try {
                    if (!hasCertificate(context, hostname)) {
                        if (Aware.DEBUG) Log.d(Aware.TAG, "Certificates: Downloading for the first time SSL certificate: " + protocol+"://"+hostname);
                        downloadCertificate(context, protocol, hostname, true);
                    } else {
                        //Cached certificate information
                        InputStream localCertificate = getCertificate(context, hostname);
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(localCertificate);
                        new CheckCertificates(context, url).execute(cert);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (CertificateException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class CheckCertificates extends AsyncTask<X509Certificate, Void, Void> {
        private String url;
        private String protocol;
        private String hostname;
        private Context context;

        CheckCertificates(Context context, String URL) {
            this.url = URL;
            this.context = context;

            Uri study_uri = Uri.parse(url);
            this.hostname = study_uri.getHost();

            this.protocol = "http";
            try {
                protocol = new URL(url).getProtocol();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(X509Certificate... x509Certificate) {
            try {
                X509Certificate remote_certificate = retrieveRemoteCertificate(new URL(protocol+"://"+hostname));
                if (!x509Certificate[0].equals(remote_certificate)) { //local certificate is expired or different, download new certificate
                    downloadCertificate(context, protocol, hostname, true);
                    //this will force download of SSL certificate from the server. Checked every 15 minutes until successful update to up-to-date certificate.
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Downloads the certificate directly from the URL, instead of a public folder. This only happens once when joining the study.
     * We are trusting the certificate that is sent to us by the URL only once to avoid man-in-the-middle attacks
     * @param url
     * @return
     */
    public static X509Certificate retrieveRemoteCertificate(URL url) {
        try {

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, null);

            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ctx.getSocketFactory());
            conn.setConnectTimeout(5000); //5 seconds to connect
            conn.setReadTimeout(10000); //10 seconds to acknowledge the response
            conn.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            conn.connect();

            // retrieve the N-length signing chain for the server certificates
            // certs[0] is the server's certificate
            Certificate[] certs = conn.getServerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                return ((X509Certificate) certs[0]);
            }

            Log.d(Aware.TAG, "Not an X509Certificate! " + certs[0].getType() + " : " + certs[0].toString());

            // connection is not HTTPS or server is not signed with an X.509 certificate, return null
            return null;
        } catch (SSLPeerUnverifiedException | NoSuchAlgorithmException | KeyManagementException spue) {
            // connection to server is not verified, unable to get certificates
            Log.d(Aware.TAG, "Certificates: " + spue.getMessage());
            return null;
        } catch (IllegalStateException ise) {
            // shouldn't get here -- indicates attempt to get certificates before
            // connection is established
            Log.d(Aware.TAG, "Certificates: " + ise.getMessage());
            return null;
        } catch (IOException ioe) {
            // error connecting to URL -- this must be caught last since
            // other exceptions are subclasses of IOException
            Log.d(Aware.TAG, "Certificates: " + ioe.getMessage());
            return null;
        }
    }

    /**
     * Classic method: Download certificate unconditionally.  This is the old
     * method of certificate fetching.  This method blocks if the block parameter is true,
     * otherwise is nonblocking.
     *
     * @param context  app contexto
     * @param hostname Hostname to download.
     * @param block    If true, block until certificate retrieved, otherwise do not.
     */
    private static void downloadCertificate(Context context, String protocol, String hostname, boolean block) {
        File root_folder;
        if (context.getApplicationContext().getResources().getBoolean(R.bool.internalstorage)) {
            root_folder = new File(context.getFilesDir(), "/credentials/" + hostname);
        } else if (!context.getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE"), "/credentials/" + hostname); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0], "/AWARE/credentials/" + hostname); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        root_folder.mkdirs();

        try {
            X509Certificate certificate = retrieveRemoteCertificate(new URL(protocol+"://"+hostname));
            byte[] certificate_data = certificate.getEncoded();
            FileOutputStream outputStream = new FileOutputStream(new File(root_folder.toString() + "/server.crt"));
            outputStream.write(certificate_data);
            outputStream.close();
        } catch (CertificateEncodingException | IOException | NullPointerException e) {
            Log.d(Aware.TAG, "SSL error: " + e.getMessage());
        }
    }


    /**
     * Handle a certificate the new way, via the parameters crt, crt_sha256, and crt_url.
     * If crt is given, use that data.  Otherwise, if crt_url is given, download crt from
     * that URL.  In all cases, verify against crt_sha256.  Then save the certificate.
     *
     * @param context    app context
     * @param hostname   hostname to save
     * @param crt        raw String certificate data (contents of file)
     * @param crt_sha256 sha256 hash of certificate data to validate
     * @param crt_url    URL from which to fetch certificate if it is not given.
     */
    private static void handleCrtParameters(Context context, String hostname, String crt, String crt_sha256, String crt_url) {
        if (Aware.DEBUG) {
            Log.d(Aware.TAG, "handleCrtParameters");
            Log.d(Aware.TAG, "crt=" + crt);
            Log.d(Aware.TAG, "crt_url=" + crt_url);
            Log.d(Aware.TAG, "crt_sha256=" + crt_sha256);
        }

        // There are two independent options here.  crt can have the binary data directly, or it
        // can be downloaded from crt_url.  These are mutually exclusive
        if (crt != null) {
            // Nop: crt already contains the binary crt data, use it directly.
            // This block is here only to make the logic immediately clear, so that
            // in the future, should this need to be adjusted, both cases can be handling.
            // If this was not here, there wolud have to be an extra conditional in the
            // else clause (making it an elif), so instead we make the logic directly obvious.
            //crt = crt;
        } else if (crt_url != null) {
            try {
                InputStream crt_stream = new URL(crt_url).openStream();
                // Convert input stream to String
                BufferedReader br = new BufferedReader(new InputStreamReader(crt_stream));
                StringBuilder sb = new StringBuilder();
                // Someone please turn this into a proper way to get data from URL in java.
                int nextchar;
                while ((nextchar = br.read()) != -1) {
                    sb.append((char) nextchar);
                }
                br.close();
                // Final result that we actually need.
                crt = sb.toString();
                if (Aware.DEBUG) {
                    Log.d(Aware.TAG, "Downloaded crt=" + crt);
                }
            } catch (IOException e) {
                Log.e(Aware.TAG, "Certificates: Can not download crt: " + crt_url);
                // TODO: error handling
                return;
            }
        } else {
            // TODO: error handling
            Log.e(Aware.TAG, "Certificates: Both crt and crt_url are null: ");
            return;
        }

        // Validate certificate using hash
        if (crt_sha256 != null) {
            String actual_hash = Encrypter.hashGeneric(crt, "SHA-256");
            if (!actual_hash.equals(crt_sha256)) {
                Log.e(Aware.TAG, "Invalid certificate hash: " + crt_sha256 + "!=" + actual_hash);
                return;
            }
        }

        // Set the certificate
        setCertificate(context, hostname, crt);
    }

    /**
     * Do we have a certificate for this hostname?
     *
     * @param context  context
     * @param hostname hostname to check (only hostname, no protocol or anything.)
     * @return true if a certificate exists, false otherwise
     */
    public static boolean hasCertificate(Context context, String hostname) {
        if (hostname == null || hostname.length() == 0) return false;

        File root_folder;
        if (context.getResources().getBoolean(R.bool.internalstorage)) {
            root_folder = new File(context.getFilesDir() + "/credentials");
        } else if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE") + "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        File host_credentials = new File(root_folder.toString(), hostname + "/server.crt");
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(new FileInputStream(host_credentials.getPath()));
            certificate.checkValidity();
            return true;
        } catch (Exception e) {
            Log.d(Aware.TAG, "Error getting HTTPS SSL certificate: " + e.getMessage());
            return false;
        }
    }


    /**
     * Write a certificate do disk, given by name.
     *
     * @param context   app context
     * @param hostname  hostname to check
     * @param cert_data certificate data, as String.
     */
    private static void setCertificate(Context context, String hostname, String cert_data) {

        if (hostname == null || hostname.length() == 0) return;

        File root_folder;
        if (context.getResources().getBoolean(R.bool.internalstorage)) {
            root_folder = new File(context.getFilesDir() + "/credentials");
        } else if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE") + "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        //Create folder if not existent
        File host_credentials = new File(root_folder.toString(), hostname);
        host_credentials.mkdirs();

        File cert_file = new File(host_credentials.toString(), "server.crt");
        try {
            FileOutputStream stream = new FileOutputStream(cert_file);
            OutputStreamWriter cert_f = new OutputStreamWriter(stream);
            cert_f.write(cert_data);
            cert_f.close();
            Log.d(Aware.TAG, "Set certificate for " + hostname);
        } catch (java.io.IOException e) {
            Log.d(Aware.TAG, "Can not write certificate: " + cert_file);
            e.printStackTrace();
        }
    }


    /**
     * Load HTTPS certificate from server: server.crt
     *
     * @param context context
     * @param server  server URL, http://{hostname}/index.php
     * @return FileInputStream of certificate
     * @throws FileNotFoundException
     */
    public static InputStream getHTTPS(Context context, String server) throws FileNotFoundException {
        Uri study_uri = Uri.parse(server);
        String hostname = study_uri.getHost();

        if (hostname == null || hostname.length() == 0) return null;

        File root_folder;
        if (context.getResources().getBoolean(R.bool.internalstorage)) {
            root_folder = new File(context.getFilesDir() + "/credentials");
        } else if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE") + "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        File host_credentials = new File(root_folder.toString(), hostname);
        if (host_credentials.exists()) {
            File[] certs = host_credentials.listFiles();
            if(certs != null)
                for (File crt : certs) {
                    if (crt.getName().equals("server.crt")) return new FileInputStream(crt);
                }
        }
        return null;
    }

    /**
     * Load certificate for MQTT server: server.crt
     * NOTE: different from getHTTPS. Here, we have the MQTT server address/IP as input parameter.
     *
     * @param context context
     * @param server  server hostname
     * @return Input stream of opened certificate.
     * @throws FileNotFoundException
     */
    static InputStream getCertificate(Context context, String server) throws FileNotFoundException {
        //Fixed: make sure we have a valid server name
        if (server == null || server.length() == 0) return null;

        File root_folder;
        if (context.getResources().getBoolean(R.bool.internalstorage)) {
            root_folder = new File(context.getFilesDir() + "/credentials");
        } else if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE") + "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        File host_credentials = new File(root_folder.toString(), server);
        if (host_credentials.exists()) {
            File[] certs = host_credentials.listFiles();
            if (certs == null) return null;
            for (File crt : certs) {
                if (crt.getName().equals("server.crt")) return new FileInputStream(crt);
            }
        }
        return null;
    }
}
