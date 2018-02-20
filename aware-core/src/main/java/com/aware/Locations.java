package com.aware;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.aware.providers.Locations_Provider;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.aware.utils.Aware_Sensor;

/**
 * Location service for Aware framework
 * Provides mobile device network triangulation and GPS location
 *
 * @author denzil
 */
public class Locations extends Aware_Sensor implements LocationListener {

    private static LocationManager locationManager = null;

    /**
     * This listener will keep track for failed GPS location requests
     * TODO: extend to log satellite information
     */
    private final GpsStatus.Listener gps_status_listener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    break;
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    break;
                case GpsStatus.GPS_EVENT_STARTED:
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    //Save best location, could be GPS or network
                    //This covers the case when the GPS stopped and we did not get a location fix.
                    Location lastGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                    Location lastNetwork = null;
                    //Do a quick check on the network provider
                    if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
                        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, Locations.this, getMainLooper());
                        lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }

                    Location bestLocation;
                    if (isBetterLocation(lastNetwork, lastGPS)) {
                        bestLocation = lastNetwork;
                    } else {
                        bestLocation = lastGPS;
                    }

                    // Are we within the geofence, if we are given one?
                    Boolean permitted;
                    if (bestLocation != null) {
                        permitted = testGeoFence(bestLocation.getLatitude(), bestLocation.getLongitude());
                    } else {
                        permitted = true;  // unused because no location.
                    }
                    if (Aware.DEBUG) Log.d(TAG, "Locations: geofencing: permitted=" + permitted);

                    if (bestLocation != null) {
                        ContentValues rowData = new ContentValues();
                        rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
                        rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        rowData.put(Locations_Data.PROVIDER, bestLocation.getProvider());
                        if (permitted) {
                            rowData.put(Locations_Data.LATITUDE, bestLocation.getLatitude());
                            rowData.put(Locations_Data.LONGITUDE, bestLocation.getLongitude());
                            rowData.put(Locations_Data.BEARING, bestLocation.getBearing());
                            rowData.put(Locations_Data.SPEED, bestLocation.getSpeed());
                            rowData.put(Locations_Data.ALTITUDE, bestLocation.getAltitude());
                            rowData.put(Locations_Data.ACCURACY, bestLocation.getAccuracy());
                        } else {
                            rowData.put(Locations_Data.LABEL, "outofbounds");
                        }

                        try {
                            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
                        } catch (SQLException e) {
                            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                        }

                        Intent locationEvent = new Intent(ACTION_AWARE_LOCATIONS);
                        sendBroadcast(locationEvent);
                    }
                    break;
            }
        }
    };

    public String TAG = "AWARE Sensor Location";

    /**
     * Broadcasted event: New location available
     */
    public static final String ACTION_AWARE_LOCATIONS = "ACTION_AWARE_LOCATIONS";

    /**
     * Broadcasted event: GPS location is active
     */
    public static final String ACTION_AWARE_GPS_LOCATION_ENABLED = "ACTION_AWARE_GPS_LOCATION_ENABLED";

    /**
     * Broadcasted event: Network location is active
     */
    public static final String ACTION_AWARE_NETWORK_LOCATION_ENABLED = "ACTION_AWARE_NETWORK_LOCATION_ENABLED";

    /**
     * Broadcasted event: GPS location disabled
     */
    public static final String ACTION_AWARE_GPS_LOCATION_DISABLED = "ACTION_AWARE_GPS_LOCATION_DISABLED";

    /**
     * Broadcasted event: Network location disabled
     */
    public static final String ACTION_AWARE_NETWORK_LOCATION_DISABLED = "ACTION_AWARE_NETWORK_LOCATION_DISABLED";

    private static int FREQUENCY_NETWORK = -1;
    private static int FREQUENCY_GPS = -1;
    private static int FREQUENCY_PASSIVE = -1;

    /**
     * Geofencing function.  Tests if a lat and lon is allowed, based
     * on the Aware "location_geofence" setting.
     */
    public Boolean testGeoFence(Double lat0, Double lon0) {
        // Find fence and if we even need to fence.
        String geofences = Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_GEOFENCE);
        if (Aware.DEBUG) Log.d(TAG, "Location geofence: testing against config=" + geofences);
        // If no value, then always accept locations
        if (geofences.length() == 0 || geofences.equals("null"))
            return true;

        // Separate geofence string by spaces, tabs, and semicolon
        String[] fences = geofences.split("[ \t;]+");
        // Test each part separately, if any part is true, return true.
        for (Integer i = 0; i < fences.length; i++) {
            String[] parts = fences[i].split(",");
            // Circular fences.  Distance in METERS.
            if (parts.length == 3) {
                Double lat1 = Double.parseDouble(parts[0]);
                Double lon1 = Double.parseDouble(parts[1]);
                Double radius = Double.parseDouble(parts[2]);
                if (wgs84_dist(lat0, lon0, lat1, lon1) < radius) {
                    if (Aware.DEBUG) Log.d(TAG, "Location geofence: within " + fences[i]);
                    return true;
                }
            }
            // Rectungular fence
            if (parts[0].equals("rect") && parts.length == 5) {
                Double lat1 = Double.parseDouble(parts[1]);
                Double lon1 = Double.parseDouble(parts[2]);
                Double lat2 = Double.parseDouble(parts[3]);
                Double lon2 = Double.parseDouble(parts[4]);
                // Be safe in case order of xxx1 and xxx2 are reversed,
                // so test twice.  Is there a better way to do this?
                if (((lat1 < lat0 && lat0 < lat2)
                        || (lat2 < lat0 && lat0 < lat1))
                        && ((lon1 < lon0 && lon0 < lon2)
                        || (lon2 < lon0 && lon0 < lon1))
                        ) {
                    if (Aware.DEBUG) Log.d(TAG, "Location geofence: within " + fences[i]);
                    return true;
                }
            }
        }
        if (Aware.DEBUG) Log.d(TAG, "Location geofence: not in any fences");
        return false;
    }

    /**
     * Haversine formula for geographic distances.  Returns distance in meters.
     */
    public static Double wgs84_dist(Double lat1, Double lon1, Double lat2, Double lon2) {
        Double EARTH_RADIUS = 6378137.;

        Double dLat = Math.toRadians(lat2 - lat1);
        Double dLon = Math.toRadians(lon2 - lon1);
        Double a = (Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2));
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        Double d = EARTH_RADIUS * c;
        return d;
    }

    private static Locations.AWARESensorObserver awareSensor;

    public static void setSensorObserver(Locations.AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static Locations.AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onLocationChanged(ContentValues data);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param newLocation  The new Location that you want to evaluate
     * @param lastLocation The last location fix, to which you want to compare the new one
     */
    private boolean isBetterLocation(Location newLocation, Location lastLocation) {
        if (newLocation == null && lastLocation == null) return false;
        if (newLocation != null && lastLocation == null) return true;
        if (newLocation == null) return false;

        long timeDelta = newLocation.getTime() - lastLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > 1000 * Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME));
        boolean isSignificantlyOlder = timeDelta < -(1000 * Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME)));
        boolean isNewer = timeDelta > 0;

        if (isSignificantlyNewer) {
            return true;
        } else if (isSignificantlyOlder) {
            return false;
        }

        int accuracyDelta = (int) (newLocation.getAccuracy() - lastLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
        boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), lastLocation.getProvider());

        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Locations_Provider.getAuthority(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (Aware.DEBUG) Log.d(TAG, "Location sensor is created!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (PERMISSIONS_OK) locationManager.removeUpdates(this);
        locationManager.removeGpsStatusListener(gps_status_listener);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Locations_Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Locations_Provider.getAuthority(this),
                Bundle.EMPTY
        );

        if (Aware.DEBUG) Log.d(TAG, "Locations service terminated...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS, 180);
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY, 150);
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK, 300);
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY, 1500);
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME).length() == 0) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.LOCATION_EXPIRATION_TIME, 300);
            }

            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS).equals("true")) {
                if (locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                    if (FREQUENCY_GPS != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS))) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS)) * 1000,
                                Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_GPS_ACCURACY)), this);
                        locationManager.removeGpsStatusListener(gps_status_listener);
                        locationManager.addGpsStatusListener(gps_status_listener);

                        FREQUENCY_GPS = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_GPS));
                    }
                    if (Aware.DEBUG)
                        Log.d(TAG, "Location tracking with GPS is active: " + FREQUENCY_GPS + "s");
                } else {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    rowData.put(Locations_Data.PROVIDER, LocationManager.GPS_PROVIDER);
                    rowData.put(Locations_Data.LABEL, "disabled");
                    try {
                        getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                    if (Aware.DEBUG) Log.d(TAG, "Location tracking with GPS is not available");
                }
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
                if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
                    if (FREQUENCY_NETWORK != Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK))) {
                        locationManager.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK)) * 1000,
                                Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.MIN_LOCATION_NETWORK_ACCURACY)), this);

                        FREQUENCY_NETWORK = Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_LOCATION_NETWORK));
                    }
                    if (Aware.DEBUG)
                        Log.d(TAG, "Location tracking with Network is active: " + FREQUENCY_NETWORK + "s");
                } else {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    rowData.put(Locations_Data.PROVIDER, LocationManager.NETWORK_PROVIDER);
                    rowData.put(Locations_Data.LABEL, "disabled");
                    try {
                        getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                    if (Aware.DEBUG) Log.d(TAG, "Location tracking with Network is not available");
                }
            }
            if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_PASSIVE).equals("true")) {
                if (locationManager.getProvider(LocationManager.PASSIVE_PROVIDER) != null) {
                    // We treat this provider differently.  Since there is no battery use
                    // and we don't have actual control over frequency, we register for
                    // frequency=60s and no movement threshold.
                    int static_frequency_passive = 60 * 1000;
                    if (FREQUENCY_PASSIVE != static_frequency_passive) {
                        locationManager.requestLocationUpdates(
                                LocationManager.PASSIVE_PROVIDER,
                                static_frequency_passive, 0, this);
                        FREQUENCY_PASSIVE = static_frequency_passive;
                    }
                    if (Aware.DEBUG)
                        Log.d(TAG, "Location tracking with passive provider is active: " + FREQUENCY_PASSIVE + "s");
                } else {
                    ContentValues rowData = new ContentValues();
                    rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
                    rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    rowData.put(Locations_Data.PROVIDER, LocationManager.PASSIVE_PROVIDER);
                    rowData.put(Locations_Data.LABEL, "disabled");
                    try {
                        getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
                    } catch (SQLiteException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    } catch (SQLException e) {
                        if (Aware.DEBUG) Log.d(TAG, e.getMessage());
                    }
                    if (Aware.DEBUG)
                        Log.d(TAG, "Location tracking with passive provider is not available");
                }
            }

            if (Aware.isStudy(this)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Locations_Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Locations_Provider.getAuthority(this), true);
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(Aware.getAWAREAccount(this), Locations_Provider.getAuthority(this))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location newLocation) {
        if (Aware.DEBUG)
            Log.d(TAG, "onLocationChanged: provider=" + newLocation.getProvider() + " location=" + newLocation);
        // We save ALL locations, no matter which provider it comes from, for the most complete
        // history and future analysis.
        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_SAVE_ALL).equals("true")) {
            saveLocation(newLocation);
            Intent locationEvent = new Intent(ACTION_AWARE_LOCATIONS);
            sendBroadcast(locationEvent);
            return;
        }

        Location bestLocation;

        //If we have both GPS and Network active, check if we got a better location. Otherwise always keep the latest.
        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_GPS).equals("true") && Aware.getSetting(getApplicationContext(), Aware_Preferences.STATUS_LOCATION_NETWORK).equals("true")) {
            Location lastGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (isBetterLocation(lastNetwork, lastGPS)) {
                if (isBetterLocation(newLocation, lastNetwork)) {
                    bestLocation = newLocation;
                } else {
                    bestLocation = lastNetwork;
                }
            } else {
                if (isBetterLocation(newLocation, lastGPS)) {
                    bestLocation = newLocation;
                } else {
                    bestLocation = lastGPS;
                }
            }
        } else {
            bestLocation = newLocation;
        }

        saveLocation(bestLocation);

        Intent locationEvent = new Intent(ACTION_AWARE_LOCATIONS);
        sendBroadcast(locationEvent);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (Aware.DEBUG)
            Log.d(TAG, "onProviderDisabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_GPS_LOCATION_DISABLED);
            Intent gps = new Intent(ACTION_AWARE_GPS_LOCATION_DISABLED);
            sendBroadcast(gps);

            ContentValues rowData = new ContentValues();
            rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Locations_Data.PROVIDER, LocationManager.GPS_PROVIDER);
            rowData.put(Locations_Data.LABEL, "disabled");
            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
        }
        if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
            if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_NETWORK_LOCATION_DISABLED);
            Intent network = new Intent(ACTION_AWARE_NETWORK_LOCATION_DISABLED);
            sendBroadcast(network);

            ContentValues rowData = new ContentValues();
            rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Locations_Data.PROVIDER, LocationManager.NETWORK_PROVIDER);
            rowData.put(Locations_Data.LABEL, "disabled");
            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (Aware.DEBUG)
            Log.d(TAG, "onProviderDisabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_GPS_LOCATION_ENABLED);
            Intent gps = new Intent(ACTION_AWARE_GPS_LOCATION_ENABLED);
            sendBroadcast(gps);

            ContentValues rowData = new ContentValues();
            rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Locations_Data.PROVIDER, LocationManager.GPS_PROVIDER);
            rowData.put(Locations_Data.LABEL, "enabled");
            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
        }
        if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
            if (Aware.DEBUG) Log.d(TAG, ACTION_AWARE_NETWORK_LOCATION_ENABLED);
            Intent network = new Intent(ACTION_AWARE_NETWORK_LOCATION_ENABLED);
            sendBroadcast(network);

            ContentValues rowData = new ContentValues();
            rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Locations_Data.PROVIDER, LocationManager.NETWORK_PROVIDER);
            rowData.put(Locations_Data.LABEL, "enabled");
            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (Aware.DEBUG)
            Log.d(TAG, "onStatusChanged: " + provider + " Status:" + status + " Extras:" + ((extras != null) ? extras.toString() : ""));

        // Save ALL locations, no matter which provider it comes from or how it relates to past
        // locations.
        if (Aware.getSetting(getApplicationContext(), Aware_Preferences.LOCATION_SAVE_ALL).equals("true")) {
            boolean updated = false;
            Location newLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (newLocation != null) {
                saveLocation(newLocation);
                updated = true;
            }
            newLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (newLocation != null) {
                saveLocation(newLocation);
                updated = true;
            }
            if (updated) {
                Intent locationEvent = new Intent(ACTION_AWARE_LOCATIONS);
                sendBroadcast(locationEvent);
            }
            return;
        }

        //Save best location, could be GPS or network
        //This covers the case when the GPS stopped and we did not get a location fix.
        Location lastGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        Location lastNetwork = null;
        //Do a quick check on the network provider
        if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, Locations.this, getMainLooper());
            lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        Location bestLocation;
        if (isBetterLocation(lastNetwork, lastGPS)) {
            bestLocation = lastNetwork;
        } else {
            bestLocation = lastGPS;
        }

        saveLocation(bestLocation);

        Intent locationEvent = new Intent(ACTION_AWARE_LOCATIONS);
        sendBroadcast(locationEvent);
    }

    /**
     * Save a location, handling geofencing.
     *
     * @param bestLocation Location to save
     */
    public void saveLocation(Location bestLocation) {

        if (bestLocation == null) return; //no location available

        // Are we within the geofence, if we are given one?
        Boolean permitted = testGeoFence(bestLocation.getLatitude(), bestLocation.getLongitude());
        if (Aware.DEBUG) Log.d(TAG, "geofencing: permitted=" + permitted);

        ContentValues rowData = new ContentValues();
        rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
        rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        rowData.put(Locations_Data.PROVIDER, bestLocation.getProvider());
        if (permitted) {
            rowData.put(Locations_Data.LATITUDE, bestLocation.getLatitude());
            rowData.put(Locations_Data.LONGITUDE, bestLocation.getLongitude());
            rowData.put(Locations_Data.BEARING, bestLocation.getBearing());
            rowData.put(Locations_Data.SPEED, bestLocation.getSpeed());
            rowData.put(Locations_Data.ALTITUDE, bestLocation.getAltitude());
            rowData.put(Locations_Data.ACCURACY, bestLocation.getAccuracy());
        } else {
            rowData.put(Locations_Data.LABEL, "outofbounds");
        }

        try {
            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);

            if (awareSensor != null) awareSensor.onLocationChanged(rowData);

        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }

    }
}