package com.aware.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.content.Context;
import android.util.Log;

import com.aware.Aware;
import com.aware.R;
//import org.apache.http.conn.ssl.SSLSocketFactory;
//import org.apache.http.conn.ssl.X509HostnameVerifier;

public class Https extends DefaultHttpClient {

	private static final String TAG = "AWARE::HTTPS";
	
	private static Context sContext;
	private static Scheme sScheme;
	
	public Https(Context c) {
		sContext = c;
		SSLContext sslCtx = createSSLContext();
		HostnameVerifier hostVerifier = new BrowserCompatHostnameVerifier();
		sScheme = new Scheme("https", new AwareSSLSocketFactory(sslCtx, (X509HostnameVerifier) hostVerifier), 443);
		getConnectionManager().getSchemeRegistry().register(sScheme);
	}
	
	private SSLContext createSSLContext() {
		try {
			//Load local trusted keystore
			KeyStore sKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream inStream = sContext.getResources().openRawResource(R.raw.awareframework);
			sKeyStore.load(inStream, "awareframework".toCharArray());
			inStream.close();
			
			AwareTrustManager trustManager = new AwareTrustManager(sKeyStore);
			TrustManager[] tms = new TrustManager[]{ trustManager };
			
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tms, null);
			return context;
			
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public class AwareSSLSocketFactory implements LayeredSocketFactory {
		private SSLSocketFactory socketFactory;
		private X509HostnameVerifier hostnameVerifier;
		
		public AwareSSLSocketFactory(SSLContext sslCtx, X509HostnameVerifier hostVerifier) {
			this.socketFactory = sslCtx.getSocketFactory();
			this.hostnameVerifier = hostVerifier;
		}
		
		@Override
		public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort, HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
			if (host == null) {
	            throw new IllegalArgumentException("Target host may not be null.");
	        }
	        if (params == null) {
	            throw new IllegalArgumentException("Parameters may not be null.");
	        }

	        SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket());
	        if ((localAddress != null) || (localPort > 0)) {
	            if (localPort < 0) localPort = 0;
	            InetSocketAddress isa = new InetSocketAddress(localAddress, localPort);
	            sslsock.bind(isa);
	        }

	        int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
	        int soTimeout = HttpConnectionParams.getSoTimeout(params);

	        InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
	        sslsock.connect(remoteAddress, connTimeout);
	        sslsock.setSoTimeout(soTimeout);
	        try {
	            hostnameVerifier.verify(host, sslsock);
	        } catch (IOException iox) {
	            try {
	                sslsock.close();
	            } catch (Exception x) {
	            }
	            throw iox;
	        }
	        return sslsock;
		}

		@Override
		public Socket createSocket() throws IOException {
			return socketFactory.createSocket();
		}

		@Override
		public boolean isSecure(Socket sock) throws IllegalArgumentException {
			if (sock == null) {
	            throw new IllegalArgumentException("Socket may not be null.");
	        }
	        if (!(sock instanceof SSLSocket)) {
	            throw new IllegalArgumentException("Socket not created by this factory.");
	        }
	        if (sock.isClosed()) {
	            throw new IllegalArgumentException("Socket is closed.");
	        }
	        return true;
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
			SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(socket, host, port, autoClose);
	        hostnameVerifier.verify(host, sslSocket);
	        return sslSocket;
		}
	}
	
	/**
	 * Make a POST to the URL, with the ArrayList<NameValuePair> data
	 * @param url
	 * @param data
	 * @return HttpEntity with server response. Use EntityUtils to extract values or object
	 */
	public HttpResponse dataPOST(String url, ArrayList<NameValuePair> data) {
		try{
			HttpPost httpPost = new HttpPost(url);
			httpPost.setEntity(new UrlEncodedFormEntity(data));
			HttpResponse httpResponse = this.execute(httpPost);
			
			int statusCode = httpResponse.getStatusLine().getStatusCode();
			if (statusCode != 200 ) {
				if(Aware.DEBUG) {
					Log.d(TAG,"URL:" + url + "\nData:"+ data.toString());
					Log.d(TAG, "Status: " + statusCode );
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
			HttpGet httpGet = new HttpGet(url);
			HttpResponse httpResponse = this.execute(httpGet);
		
			int statusCode = httpResponse.getStatusLine().getStatusCode();
			if( statusCode != 200 ) {
				if(Aware.DEBUG) {
					Log.d(TAG,"Status: "+ statusCode);
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
