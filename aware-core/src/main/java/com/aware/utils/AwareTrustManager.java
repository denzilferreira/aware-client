package com.aware.utils;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

//Inspired by: http://nelenkov.blogspot.ie/2011/12/using-custom-certificate-trust-store-on.html
public class AwareTrustManager implements X509TrustManager {
	
	private X509TrustManager defaultTrust;
	private X509TrustManager localTrust;
	
	private X509Certificate[] acceptedIssuers;
	
	public AwareTrustManager( KeyStore localStore ) {
		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init((KeyStore) localStore);
			
			defaultTrust = findX509TrustManager(tmf);
			if( defaultTrust == null ) {
				throw new IllegalStateException("Couldn't find X509TrustManager");
			}
			
			localTrust = new LocalStoreTrustManager(localStore);
			
			List<X509Certificate> allIssuers = new ArrayList<X509Certificate>();
			for( X509Certificate cert : defaultTrust.getAcceptedIssuers() ) {
				allIssuers.add(cert);
			}
			for( X509Certificate cert : localTrust.getAcceptedIssuers() ) {
				allIssuers.add(cert);
			}
			acceptedIssuers = allIssuers.toArray(new X509Certificate[allIssuers.size()]);
		} catch ( GeneralSecurityException e ) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			defaultTrust.checkClientTrusted(chain, authType);
		} catch ( CertificateException ce ) {
			localTrust.checkClientTrusted(chain, authType);
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			defaultTrust.checkServerTrusted(chain, authType);
		} catch ( CertificateException ce ) {
			localTrust.checkServerTrusted(chain, authType);
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return acceptedIssuers;
	}
	
	static class LocalStoreTrustManager implements X509TrustManager {
		private X509TrustManager trustManager;
		public LocalStoreTrustManager( KeyStore localStore ) {
			try {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(localStore);
				trustManager = findX509TrustManager(tmf);
				if( trustManager == null ){
					throw new IllegalStateException("Couldn't find X509TrustManager");
				}
			} catch (GeneralSecurityException e ) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			trustManager.checkClientTrusted(chain, authType);
		}
		
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			trustManager.checkServerTrusted(chain, authType);
		}
		
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return trustManager.getAcceptedIssuers();
		}
	}
	
	static X509TrustManager findX509TrustManager(TrustManagerFactory tmf) {
		TrustManager tms[] = tmf.getTrustManagers();
		for(int i = 0; i < tms.length; i++ ) {
			if( tms[i] instanceof X509TrustManager ) {
				return (X509TrustManager) tms[i];
			}
		}
		return null;
	}
}
