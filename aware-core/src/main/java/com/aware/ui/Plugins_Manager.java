
package com.aware.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.R;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import com.aware.utils.Https;
import com.aware.utils.WearClient;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * UI to manage installed plugins. 
 * @author denzil
 *
 */
public class Plugins_Manager extends Aware_Activity {
    
	/**
	 * Plugin installed but disabled
	 */
	public static final int PLUGIN_DISABLED = 0;

	/**
	 * Plugin installed and active
	 */
	public static final int PLUGIN_ACTIVE = 1;

	/**
	 * Plugin installed but there is a new version on the server
	 */
	public static final int PLUGIN_UPDATED = 2;

	/**
	 * Plugin not installed but available on the server
	 */
	public static final int PLUGIN_NOT_INSTALLED = 3;
	
	private static LayoutInflater inflater;
	private static GridView store_grid;
    private static SwipeRefreshLayout swipeToRefresh;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.plugins_store_ui);

    	inflater = getLayoutInflater();
    	store_grid = (GridView) findViewById(R.id.plugins_store_grid);

        swipeToRefresh = (SwipeRefreshLayout) findViewById(R.id.refresh_plugins);
        swipeToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Async_PluginUpdater().execute();
            }
        });

        bootRefresh();

    	IntentFilter filter = new IntentFilter();
    	filter.addAction(Aware.ACTION_AWARE_PLUGIN_MANAGER_REFRESH);
    	registerReceiver(plugins_listener, filter);
    }

    private void bootRefresh() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                swipeToRefresh.setRefreshing(true);
                new Async_PluginUpdater().execute();
            }
        });
    }
	
	//Monitors for external changes in plugin's states and refresh the UI
	private Plugins_Listener plugins_listener = new Plugins_Listener();
	public class Plugins_Listener extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if( intent.getAction().equals(Aware.ACTION_AWARE_PLUGIN_MANAGER_REFRESH) ) {
                //Update grid
                updateGrid();
			}
		}
	}

    private void updateGrid() {
        Cursor installed_plugins = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, null, null, Aware_Plugins.PLUGIN_NAME + " ASC");
        PluginAdapter pluginAdapter = new PluginAdapter(getApplicationContext(), installed_plugins, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        store_grid.setAdapter(pluginAdapter);
    }

    public class PluginAdapter extends CursorAdapter {
        public PluginAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.plugins_store_pkg_list_item, parent, false);
        }

        @Override
        public void bindView(final View pkg_view, Context context, Cursor cursor) {
            final String package_name = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_PACKAGE_NAME));
            final String name = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_NAME));
            final String description = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_DESCRIPTION));
            final String developer = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_AUTHOR));
            final String version = cursor.getString(cursor.getColumnIndex(Aware_Plugins.PLUGIN_VERSION));
            final int status = cursor.getInt(cursor.getColumnIndex(Aware_Plugins.PLUGIN_STATUS));
            final byte[] icon = cursor.getBlob(cursor.getColumnIndex(Aware_Plugins.PLUGIN_ICON));
            final ImageView pkg_icon = (ImageView) pkg_view.findViewById(R.id.pkg_icon);
            final TextView pkg_title = (TextView) pkg_view.findViewById(R.id.pkg_title);
            final ImageView pkg_state = (ImageView) pkg_view.findViewById(R.id.pkg_state);

            try {
                if ( status != PLUGIN_NOT_INSTALLED ) {
                    ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(package_name, PackageManager.GET_META_DATA);
                    pkg_icon.setImageDrawable(appInfo.loadIcon(getPackageManager()));
                } else {
                    if (icon != null && icon.length > 0) pkg_icon.setImageBitmap(BitmapFactory.decodeByteArray(icon, 0, icon.length));
                }

                pkg_title.setText(name);

                switch (status) {
                    case PLUGIN_DISABLED:
                        pkg_state.setVisibility(View.INVISIBLE);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (isClassAvailable(package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setClassName(package_name, package_name + ".Settings");
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.startPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_ACTIVE:
                        pkg_state.setImageResource(R.drawable.ic_pkg_active);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (isClassAvailable(package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setClassName(package_name, package_name + ".Settings");
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setPositiveButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_UPDATED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_updated);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                if (isClassAvailable(package_name, "Settings")) {
                                    builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            Intent open_settings = new Intent();
                                            open_settings.setClassName(package_name, package_name + ".Settings");
                                            startActivity(open_settings);
                                        }
                                    });
                                }
                                builder.setNeutralButton("Deactivate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Aware.stopPlugin(getApplicationContext(), package_name);
                                        updateGrid();
                                    }
                                });
                                builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        pkg_view.setAlpha(0.5f);
                                        pkg_title.setText("Updating...");
                                        pkg_view.setOnClickListener(null);
                                        Aware.downloadPlugin(getApplicationContext(), package_name, true);
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                    case PLUGIN_NOT_INSTALLED:
                        pkg_state.setImageResource(R.drawable.ic_pkg_download);
                        pkg_view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = getPluginInfoDialog(name, version, description, developer);
                                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                                builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        pkg_view.setAlpha(0.5f);

                                        if( ! Aware.is_watch(getApplicationContext()) ) {
                                            Toast.makeText(getApplicationContext(), "Downloading... please wait", Toast.LENGTH_SHORT).show();
                                            Aware.downloadPlugin(getApplicationContext(), package_name, false);

                                        } else {
                                            //Ask phone to install plugin. If there is a wearable version bundled, it's installed on the watch too.
                                            Intent requestPhone = new Intent(WearClient.ACTION_AWARE_ANDROID_WEAR_INSTALL_PLUGIN);
                                            requestPhone.putExtra(WearClient.EXTRA_PACKAGE_NAME, package_name);
                                            sendBroadcast(requestPhone);

                                            Toast.makeText(getApplicationContext(), "Continue on phone...", Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                                builder.create().show();
                            }
                        });
                        break;
                }
                pkg_view.refreshDrawableState();
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
	
	private AlertDialog.Builder getPluginInfoDialog( String name, String version, String description, String developer ) {
		AlertDialog.Builder builder = new AlertDialog.Builder( Plugins_Manager.this );
		View plugin_info_view = inflater.inflate(R.layout.plugins_store_pkg_detail, null);
		TextView plugin_name = (TextView) plugin_info_view.findViewById(R.id.plugin_name);
		TextView plugin_version = (TextView) plugin_info_view.findViewById(R.id.plugin_version);
		TextView plugin_description = (TextView) plugin_info_view.findViewById(R.id.plugin_description);
		TextView plugin_developer = (TextView) plugin_info_view.findViewById(R.id.plugin_developer);
		
		plugin_name.setText(name);
		plugin_version.setText("Version: " + version);
		plugin_description.setText(description);
		plugin_developer.setText("Developer: " + developer);
		builder.setView(plugin_info_view);
		
		return builder;
	}
	
	@Override
	protected void onResume() {
        super.onResume();
        updateGrid();
	}

    @Override
    protected void onDestroy() {
    	super.onDestroy();
        unregisterReceiver(plugins_listener);
    }

    /**
	 * Downloads and compresses image for optimized icon caching
	 * @param image_url
	 * @return
	 */
	public static byte[] cacheImage( String image_url, Context sContext ) {
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			InputStream caInput = sContext.getResources().openRawResource(R.raw.aware);
			Certificate ca;
			try {
				ca = cf.generateCertificate(caInput);
			} finally {
				caInput.close();
			}
			
			KeyStore sKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream inStream = sContext.getResources().openRawResource(R.raw.awareframework);
			sKeyStore.load(inStream, "awareframework".toCharArray());
			inStream.close();
			
			sKeyStore.setCertificateEntry("ca", ca);
			
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
			tmf.init(sKeyStore);
			
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			
			//Fetch image now that we recognise SSL
			URL image_path = new URL(image_url.replace("http://", "https://")); //make sure we are fetching the images over https
			HttpsURLConnection image_connection = (HttpsURLConnection) image_path.openConnection();
			image_connection.setSSLSocketFactory(context.getSocketFactory());
			
			InputStream in_stream = image_connection.getInputStream();
			Bitmap tmpBitmap = BitmapFactory.decodeStream(in_stream);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			tmpBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
			
			return output.toByteArray();
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
            e.printStackTrace();
        }
		return null;
	}
    
	/**
	 * Given a package and class name, check if the class exists or not.
	 * @param package_name
	 * @param class_name
	 * @return boolean
	 */
	private boolean isClassAvailable( String package_name, String class_name ) {
		try{
			Context package_context = createPackageContext(package_name, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE); 
			package_context.getClassLoader().loadClass(package_name+"."+class_name);			
		} catch ( ClassNotFoundException e ) {
			return false;
		} catch ( NameNotFoundException e ) {
			return false;
		}
		return true;
	}

    /**
     * Checks if a plugin is installed on the device
     * @param context
     * @param package_name
     * @return
     */
    public static boolean isInstalled( Context context, String package_name ) {
        PackageManager pkgManager = context.getPackageManager();
        List<PackageInfo> packages = pkgManager.getInstalledPackages(PackageManager.GET_META_DATA);
        for( PackageInfo pkg : packages) {
            if( pkg.packageName.equals(package_name) ) return true;
        }
        return false;
    }

    /**
     * Returns the currently installed plugin's version
     * @param context
     * @param package_name
     * @return
     */
    public static int getVersion( Context context, String package_name ) {
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(package_name, PackageManager.GET_META_DATA);
            return pkgInfo.versionCode;
        } catch (NameNotFoundException e) {
            if( Aware.DEBUG ) Log.d( Aware.TAG, e.getMessage());
        }
        return 0;
    }
	
    /**
     * Checks for changes on the server side and updates database.
     * If changes were detected, result is true and a refresh of UI is requested.
     * @author denzil
     */
    public class Async_PluginUpdater extends AsyncTask<Void, View, Boolean> {

        private boolean needsRefresh;

        @Override
		protected Boolean doInBackground(Void... params) {

            needsRefresh = false;

    		//Check for updates on the server side
    		HttpResponse response = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/plugins/get_plugins" + (( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) ? "/" + Aware.getSetting(getApplicationContext(), "study_id") : ""), true );
			if( response != null && response.getStatusLine().getStatusCode() == 200 ) {
				try {
					JSONArray plugins = new JSONArray(Https.undoGZIP(response));

					for( int i=0; i< plugins.length(); i++ ) {
						JSONObject plugin = plugins.getJSONObject(i);

                        boolean new_data = false;
                        Cursor is_cached = getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + plugin.getString("package") + "'", null, null );
						if( is_cached != null && is_cached.moveToFirst() ) {

							if( ! Plugins_Manager.isInstalled(getApplicationContext(), plugin.getString("package")) ) {

                                //We used to have it installed, now we don't, remove from database, add the new server-side package back
                                getContentResolver().delete(Aware_Plugins.CONTENT_URI, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + plugin.getString("package") + "'", null);
                                new_data = true;
                                needsRefresh = true;

                            } else {

                                int version = is_cached.getInt(is_cached.getColumnIndex(Aware_Plugins.PLUGIN_VERSION));
                                //Lets check if it is updated
                                if( plugin.getInt("version") > version ) {
                                    ContentValues data = new ContentValues();
                                    data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                                    data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                                    data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                                    data.put(Aware_Plugins.PLUGIN_ICON, ! Aware.is_watch(getApplicationContext())?cacheImage("http://api.awareframework.com" + plugin.getString("iconpath"), getApplicationContext()):null);
                                    data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_UPDATED);
                                    getContentResolver().update(Aware_Plugins.CONTENT_URI, data, Aware_Plugins._ID + "=" + is_cached.getInt(is_cached.getColumnIndex(Aware_Plugins._ID)), null);
                                    needsRefresh = true;
                                }
                            }
						} else {
                            new_data = true;
                        }
                        if( is_cached != null && ! is_cached.isClosed() ) is_cached.close();

                        if( new_data ) {
                            //this is a new plugin available on the server that we don't have yet!
                            ContentValues data = new ContentValues();
                            data.put(Aware_Plugins.PLUGIN_NAME, plugin.getString("title"));
                            data.put(Aware_Plugins.PLUGIN_DESCRIPTION, plugin.getString("desc"));
                            data.put(Aware_Plugins.PLUGIN_VERSION, plugin.getInt("version"));
                            data.put(Aware_Plugins.PLUGIN_PACKAGE_NAME, plugin.getString("package"));
                            data.put(Aware_Plugins.PLUGIN_AUTHOR, plugin.getString("first_name") + " " + plugin.getString("last_name") + " - " + plugin.getString("email"));
                            data.put(Aware_Plugins.PLUGIN_STATUS, PLUGIN_NOT_INSTALLED);
                            data.put(Aware_Plugins.PLUGIN_ICON, ! Aware.is_watch(getApplicationContext())?cacheImage("http://api.awareframework.com" + plugin.getString("iconpath"), getApplicationContext()):null);
                            getContentResolver().insert(Aware_Plugins.CONTENT_URI, data);
                            needsRefresh = true;
                        }
					}
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			return needsRefresh;
		}
    	
		@Override
		protected void onPostExecute(Boolean refresh) {
			super.onPostExecute(refresh);
            if( swipeToRefresh != null ) {
                swipeToRefresh.setRefreshing(false);
            }
            if( refresh ) updateGrid();
		}
    }
}
