package com.aware.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.aware.Aware;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by denzil on 16/12/15.
 */
public class SSLUtils {

    private SSLContext sslContext;
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
            sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            //Fix for known-bug on <= JellyBean (4.x)
            System.setProperty("http.keepAlive", "false");
            return(sslContext.getSocketFactory());

        } catch (Exception x){
            //ignore error of SSL
            x.printStackTrace();
        }

//        try {
//            //load SSL certificate
//            InputStream crt = SSLManager.getCertificate(mContext, host);
//
//            //Load SSL public certificate so we can talk with the server
//            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
//            Certificate ca = cf.generateCertificate(crt);
//
//            KeyStore caKS = KeyStore.getInstance("BKS");
//            caKS.load(null, null);
//            caKS.setCertificateEntry("certificate", ca);
//            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
//            trustManagerFactory.init(caKS);
//
//            //Initialize a SSL connection context, TLSv1.2 (Mosquitto)
//            sslContext = SSLContext.getInstance("TLSv1.2");
//            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
//
//            //Fix for known-bug on <= JellyBean (4.x)
//            System.setProperty("http.keepAlive", "false");
//            return(sslContext.getSocketFactory());
//
//        } catch (Exception x){
//            //ignore error of SSL
//            x.printStackTrace();
//        }
        return null;
    }
}
