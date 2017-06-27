package com.aware.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.aware.Aware;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by denzil on 16/12/15.
 */
public class SSLUtils {

    private Context mContext;

    public SSLUtils( Context c ) {
        mContext = c;
    }

    public SSLSocketFactory getSocketFactory( String host ) {
        try {
            //load SSL certificate
            InputStream crt = SSLManager.getCertificate(mContext, host);

            //Load SSL public certificate so we can talk with the server
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(crt);

            KeyStore caKS = KeyStore.getInstance("BKS");
            caKS.load(null, null);
            caKS.setCertificateEntry("certificate", cert);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(caKS);

            //Initialize a SSL connection context, TLSv1.2 (Mosquitto)
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            //Fix for known-bug on <= JellyBean (4.x)
            System.setProperty("http.keepAlive", "false");
            return(sslContext.getSocketFactory());

        } catch (IOException e){
//            Log.e(Aware.TAG, "Error reading certificate from storage." + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
//            Log.e(Aware.TAG, "Algorithm not supported." + e.getMessage());
        } catch (CertificateException e) {
//            Log.e(Aware.TAG, "Certificate exception." + e.getMessage());
        } catch (KeyManagementException e) {
//            Log.e(Aware.TAG, "Failed KeyManagement." + e.getMessage());
        } catch (KeyStoreException e) {
//            Log.e(Aware.TAG, "Failed KeyStore." + e.getMessage());
        }
        return null;
    }
}
