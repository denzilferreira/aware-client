package com.aware.utils;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.R;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.concurrent.ExecutionException;

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
     * @param context app context
     * @param url     full URL, including protocol and query arguments
     * @param block   if true, this method blocks, otherwise downloading is in background.
     */
    public static void handleUrl(Context context, String url, boolean block) {
        // Warning: jelly_bean changes behavior of decoding "+".  Make sure that both
        // " " and "+" are %-encoded.
        Uri study_uri = Uri.parse(url);
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
                    downloadCertificate(context, hostname, block);
                } else {
                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, "Certificates: Already present and key_management=once: " + hostname);
                }
            } else {
                if (!hasCertificate(context, hostname)) {
                    if (Aware.DEBUG) Log.d(Aware.TAG, "Certificates: Downloading certificate: " + hostname);
                    downloadCertificate(context, hostname, block);
                }
            }
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
    public static void downloadCertificate(Context context, String hostname, boolean block) {
        //Fixed: make sure we have a valid hostname
        if (hostname == null || hostname.length() == 0) return;

        // api.awareframework.com is an exception: we download from a different host.
        // cert_host is the host from which we actually download.
        String cert_host;
        if (hostname.contains("api.awareframework.com")) {
            cert_host = "awareframework.com";
        } else cert_host = hostname;

        File root_folder;
        if (!context.getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE"), "/credentials/" + hostname); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0], "/AWARE/credentials/" + hostname); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        root_folder.mkdirs();

        Future https = Ion.with(context.getApplicationContext())
                .load("http://" + cert_host + "/public/server.crt")
                .noCache()
                .write(new File(root_folder.toString() + "/server.crt"))
                .setCallback(new FutureCallback<File>() {
                    @Override
                    public void onCompleted(Exception e, File result) {
                        if (e != null) {
                            Log.d(Aware.TAG, "ERROR SSL certificate: " + e.getMessage());
                        }
                    }
                });

        if (block) {
            try {
                https.get();
            } catch (java.lang.InterruptedException | ExecutionException e) {
                // What to do here?
            }
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
    private static boolean hasCertificate(Context context, String hostname) {
        if (hostname == null || hostname.length() == 0) return false;

        File root_folder;
        if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE") + "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }
        File host_credentials = new File(root_folder.toString(), hostname);
        return host_credentials.exists();
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
        if (!context.getResources().getBoolean(R.bool.standalone)) {
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
     * @param server server URL, http://{hostname}/index.php
     * @return FileInputStream of certificate
     * @throws FileNotFoundException
     */
    public static InputStream getHTTPS(Context context, String server) throws FileNotFoundException {
        Uri study_uri = Uri.parse(server);
        String hostname = study_uri.getHost();

        if (hostname == null || hostname.length() == 0) return null;

        File root_folder;
        if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE")+ "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        File host_credentials = new File(root_folder.toString(), hostname);
        if (host_credentials.exists()) {
            File[] certs = host_credentials.listFiles();
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
     * @param server server hostname
     * @return Input stream of opened certificate.
     * @throws FileNotFoundException
     */
    static InputStream getCertificate(Context context, String server) throws FileNotFoundException {
        //Fixed: make sure we have a valid server name
        if (server == null || server.length() == 0) return null;

        File root_folder;
        if (!context.getResources().getBoolean(R.bool.standalone)) {
            root_folder = new File(Environment.getExternalStoragePublicDirectory("AWARE")+ "/credentials"); // sdcard/AWARE/ (shareable, does not delete when uninstalling)
        } else {
            root_folder = new File(ContextCompat.getExternalFilesDirs(context, null)[0] + "/AWARE/credentials"); // sdcard/Android/<app_package_name>/AWARE/ (not shareable, deletes when uninstalling package)
        }
        if (!root_folder.exists()) {
            root_folder.mkdirs();
        }

        File host_credentials = new File(root_folder.toString(), server);
        if (host_credentials.exists()) {
            File[] certs = host_credentials.listFiles();
            for (File crt : certs) {
                if (crt.getName().equals("server.crt")) return new FileInputStream(crt);
            }
        }
        return null;
    }
}
