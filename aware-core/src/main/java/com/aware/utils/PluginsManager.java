
package com.aware.utils;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.aware.Aware;
import com.aware.providers.Aware_Provider;
import com.aware.providers.Aware_Provider.Aware_Plugins;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

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
        ComponentName cpn = new ComponentName(context.getPackageName(), package_name + ".Plugin");
        try {
            ServiceInfo serviceInfo = pkgManager.getServiceInfo(cpn, PackageManager.GET_META_DATA); //throws NameNotFoundException if non-existing

            Cursor cached = context.getContentResolver().query(Aware_Provider.Aware_Plugins.CONTENT_URI, null, Aware_Provider.Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + package_name + "'", null, null);
            if (cached == null || !cached.moveToFirst()) {
                //Fixed: add a bundled plugin to the list of installed plugins on the self-contained apps
                ContentValues rowData = new ContentValues();
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_AUTHOR, "Bundle");
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_DESCRIPTION, "Bundled with " + context.getPackageName());
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_NAME, package_name);
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_PACKAGE_NAME, package_name);
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
                rowData.put(Aware_Provider.Aware_Plugins.PLUGIN_VERSION, 1);
                context.getContentResolver().insert(Aware_Provider.Aware_Plugins.CONTENT_URI, rowData);
                if (Aware.DEBUG) Log.d(Aware.TAG, "Added bundled plugin: " + package_name + " to " + context.getPackageName());
            }
            if (cached != null && !cached.isClosed()) cached.close();

            PackageInfo pkgInfo = new PackageInfo();
            pkgInfo.packageName = serviceInfo.packageName;
            pkgInfo.versionName = "bundled";

            return pkgInfo;
        } catch (NameNotFoundException e) {
            //Service not found, it's ok. We'll check externally installed packages
        }

        List<PackageInfo> packages = pkgManager.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pkg : packages) {
            if (pkg.packageName.equals(package_name)) return pkg;
        }
        return null;
    }

    /**
     * Check if plugin is running or not
     * @param context
     * @param pkg_name
     * @return
     */
    public static boolean isRunning(Context context, String pkg_name) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getPackageName().equalsIgnoreCase(pkg_name)) return true;
        }
        return false;
    }

    /**
     * Check if a plugin is set as enabled or not
     * @param context
     * @param pkg_name
     * @return
     */
    public static boolean isEnabled(Context context, String pkg_name) {
        boolean result = false;
        Cursor enabled = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null, null);
        if (enabled != null && enabled.moveToFirst()) {
            result = (enabled.getInt(enabled.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Aware_Plugin.STATUS_PLUGIN_ON);
        }
        if (enabled != null && ! enabled.isClosed()) enabled.close();
        return result;
    }

    /**
     * Check if a plugin is set as disabled or not
     * @param context
     * @param pkg_name
     * @return
     */
    public static boolean isDisabled(Context context, String pkg_name) {
        boolean result = false;
        Cursor disabled = context.getContentResolver().query(Aware_Plugins.CONTENT_URI, null, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null, null);
        if (disabled != null && disabled.moveToFirst()) {
            result = (disabled.getInt(disabled.getColumnIndex(Aware_Plugins.PLUGIN_STATUS)) == Aware_Plugin.STATUS_PLUGIN_OFF);
        }
        if (disabled != null && ! disabled.isClosed()) disabled.close();
        return result;
    }

    /**
     * Tell AWARE that this plugin should be enabled
     * @param context
     * @param pkg_name
     */
    public static void enablePlugin(Context context, String pkg_name) {
        ContentValues rowData = new ContentValues();
        rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_ON);
        context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null);
    }

    /**
     * Tell AWARE that this plugin should be disabled
     * @param context
     * @param pkg_name
     */
    public static void disablePlugin(Context context, String pkg_name) {
        ContentValues rowData = new ContentValues();
        rowData.put(Aware_Plugins.PLUGIN_STATUS, Aware_Plugin.STATUS_PLUGIN_OFF);
        context.getContentResolver().update(Aware_Plugins.CONTENT_URI, rowData, Aware_Plugins.PLUGIN_PACKAGE_NAME + " LIKE '" + pkg_name + "'", null);
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
            //may be bundled
        }
        return package_name;
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
            //may be bundled
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
