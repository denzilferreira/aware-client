
package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Aware_Provider.Aware_Plugins;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Helper class for fetching information on installed plugins
 *
 * @author denzil
 */
public class PluginsManager {
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

    /**
     * Checks if a plugin is installed on the device
     *
     * @param context
     * @param package_name
     * @return
     */
    public static PackageInfo isInstalled(Context context, String package_name) {
        PackageManager pkgManager = context.getPackageManager();
        List<PackageInfo> packages = pkgManager.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : packages) {
            if (pkg.packageName.equals(package_name)) return pkg;
        }
        return null;
    }

    /**
     * Check if plugin is on AWARE's database already
     *
     * @param context
     * @param pkg_name
     * @return
     */
    public static boolean isLocal(Context context, String pkg_name) {
        boolean is_local = false;
        Cursor in_db = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null, null);
        if (in_db != null && in_db.getCount() > 0) {
            is_local = true;
        }
        if (in_db != null && !in_db.isClosed()) in_db.close();
        return is_local;
    }

    /**
     * Get a list of all plugins' PackageInfo installed on the device
     * @param c
     * @return
     */
    public static ArrayList<PackageInfo> getInstalledPlugins(Context c) {
        ArrayList<PackageInfo> installed = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<PackageInfo> all_packages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : all_packages) {
            if (pkg.packageName.matches("com.aware.plugin.*")) installed.add(pkg);
        }
        return installed;
    }

    /**
     * Returns the currently installed plugin's version
     *
     * @param context
     * @param package_name
     * @return
     */
    public static String getPluginVersion(Context context, String package_name) {
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(package_name, PackageManager.GET_META_DATA);
            return pkgInfo.versionName;
        } catch (NameNotFoundException e) {
            if (Aware.DEBUG) Log.d(Aware.TAG, e.getMessage());
        }
        return "";
    }

    /**
     * Get plugin label
     * @param context
     * @param package_name
     * @return
     */
    public static String getPluginName(Context context, String package_name) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(package_name, PackageManager.GET_META_DATA)).toString();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Get a plugins' icon as byte[] for database storage
     */
    public static byte[] getPluginIcon(Context c, String package_name) {
        try {
            PackageManager pm = c.getPackageManager();
            Drawable d = pm.getPackageInfo(package_name, PackageManager.GET_META_DATA).applicationInfo.loadIcon(c.getPackageManager());
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if this plugin is also on the server
     * @param server_plugins
     * @param local_package
     * @return
     * @throws JSONException
     */
    public static boolean isOnServerRepository(JSONArray server_plugins, String local_package) throws JSONException {
        if (server_plugins == null) return false;
        for (int i = 0; i < server_plugins.length(); i++) {
            JSONObject server_pkg = server_plugins.getJSONObject(i);
            if (server_pkg.getString("package").equals(local_package)) return true;
        }
        return false;
    }

    /**
     * Get plugin's information from server
     * @param server_pkgs
     * @param package_name
     * @return
     */
    public static JSONObject getPluginOnlineInfo(JSONArray server_pkgs, String package_name) {
        try {
            for (int i = 0; i < server_pkgs.length(); i++) {
                JSONObject pkg = server_pkgs.getJSONObject(i);
                if (pkg.getString("package").equalsIgnoreCase(package_name))
                    return pkg;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
