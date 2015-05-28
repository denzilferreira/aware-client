package com.aware.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aware.Aware;
import com.aware.R;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

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
	 * Make a POST to the URL, with the ArrayList<NameValuePair> data, using gzip
	 * @param url
	 * @param data
     * @param is_gzipped
	 * @return HttpResponse with server response. Use EntityUtils on response's getEntity().getContent() to extract values or object. If gzipped, use Https.undoGZIP on the response.
	 */
	public synchronized HttpResponse dataPOST(String url, ArrayList<NameValuePair> data, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {

            JSONObject data_json = new JSONObject();
			for(NameValuePair valuePair : data ) {
				try {
					data_json.put(valuePair.getName(),valuePair.getValue());
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTPS POST request...\n" + "URL:" + url + "\nData:" + data_json.toString());

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_POST);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_DATA, data_json.toString());
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();

            while( WearProxy.wearResponse == null ){
				if( WearProxy.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear POST benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            HttpResponse response = WearProxy.wearResponse;
			WearProxy.wearResponse = null;

            return response;
		}

		try{
			HttpPost httpPost = new HttpPost(url);
			httpPost.setEntity(new UrlEncodedFormEntity(data));
            if( is_gzipped ) httpPost.addHeader("Accept-Encoding", "gzip"); //send data compressed
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
    public synchronized HttpResponse dataGET(String url, boolean is_gzipped) {
        if( url.length() == 0 ) return null;

        if( Aware.is_watch(sContext) ) {

            if( Aware.DEBUG ) Log.d(TAG, "Waiting for phone's HTTPS GET request...\n" + "URL:" + url );

            Intent phoneRequest = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_HTTP_GET);
            phoneRequest.putExtra(WearClient.EXTRA_URL, url);
            phoneRequest.putExtra(WearClient.EXTRA_GZIP, is_gzipped);
            sContext.sendBroadcast(phoneRequest);

            long time = System.currentTimeMillis();
            while( WearProxy.wearResponse == null ){
				if( WearProxy.wearResponse != null || (System.currentTimeMillis()-time) > 60000 ) {
                    if( System.currentTimeMillis() - time > 60000 ) Log.w(TAG,"HTTP request timeout...");
                    break;
                }
            }

            if( Aware.DEBUG ) {
                Log.d(TAG, "AndroidWear GET benchmark: " + (System.currentTimeMillis() - time)/1000 + " seconds");
            }

            HttpResponse response = WearProxy.wearResponse;
			WearProxy.wearResponse = null;
            return response;
        }
        try {
            HttpGet httpGet = new HttpGet(url);
            if( is_gzipped ) httpGet.addHeader("Accept-Encoding", "gzip"); //send data compressed
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

    /**
     * Given a gzipped server response, restore content
     * @param response
     * @return
     */
    public static String undoGZIP(HttpResponse response) {
        String decoded = "";
        HttpEntity gzipped = response.getEntity();
        if( gzipped != null ) {
            try {
                InputStream in = gzipped.getContent();
                Header contentEncode = response.getFirstHeader("Content-Encoding");
                if( contentEncode != null && contentEncode.getValue().equalsIgnoreCase("gzip") ) {
                    in = new GZIPInputStream(in);
                    decoded = restore(in);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return decoded;
    }

    /**
     * Decodes an compressed stream
     * @param is
     * @return
     */
    private static String restore(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
