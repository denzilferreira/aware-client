
package com.aware;

/**
 * AWARE Framework core parameters
 *
 * @author denzilferreira
 */
public class Aware_Preferences {

    /**
     * Callback ID for joining a study
     */
    public static final int REQUEST_JOIN_STUDY = 1;

    /**
     * Activate/deactive AWARE debug messages (boolean)
     */
    public static final String DEBUG_FLAG = "debug_flag";

    /**
     * Debug tag on Logcat
     */
    public static final String DEBUG_TAG = "debug_tag";

    /**
     * Disables database writing
     */
    public static final String DEBUG_DB_SLOW = "debug_db_slow";

    /**
     * AWARE Device ID (UUID)
     */
    public static final String DEVICE_ID = "device_id";

    /**
     * AWARE Group ID, used for assigning clients to a specific research group upon deployment
     */
    public static final String DEVICE_LABEL = "device_label";

    /**
     * Automatically check for updates on the client
     */
    public static final String AWARE_VERSION = "aware_version";

    /**
     * Donate usage data to AWARE project.  Note that this only sends device ID and
     * version information, no actual data of any sort.
     */
    public static final String AWARE_DONATE_USAGE = "aware_donate_usage";

    /**
     * Activate/deactivate accelerometer log (boolean)
     */
    public static final String STATUS_ACCELEROMETER = "status_accelerometer";

    /**
     * Accelerometer frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ACCELEROMETER = "frequency_accelerometer";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_ACCELEROMETER_ENFORCE = "frequency_accelerometer_enforce";

    /**
     * Accelerometer threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_ACCELEROMETER = "threshold_accelerometer";

    /**
     * Activate/deactivate application usage log (boolean)
     */
    public static final String STATUS_APPLICATIONS = "status_applications";

    /**
     * Background applications update frequency (default = 30) seconds
     */
    public static final String FREQUENCY_APPLICATIONS = "frequency_applications";

    /**
     * Activate/deactivate application installation log (boolean)
     */
    public static final String STATUS_INSTALLATIONS = "status_installations";

    /**
     * Activate/deactivate device notifications log (boolean)
     */
    public static final String STATUS_NOTIFICATIONS = "status_notifications";

    /**
     * Activate/deactivate application crashes (boolean)
     */
    public static final String STATUS_CRASHES = "status_crashes";

    /**
     * Activate/deactivate battery log (boolean)
     */
    public static final String STATUS_BATTERY = "status_battery";

    /**
     * Activate/deactivate bluetooth scan log (boolean)
     */
    public static final String STATUS_BLUETOOTH = "status_bluetooth";

    /**
     * Frequency of bluetooth scans, in seconds (default = 60)
     */
    public static final String FREQUENCY_BLUETOOTH = "frequency_bluetooth";

    /**
     * Activate/deactivate communication events (boolean)
     */
    public static final String STATUS_COMMUNICATION_EVENTS = "status_communication_events";

    /**
     * Activate/deactivate calls log (boolean)
     */
    public static final String STATUS_CALLS = "status_calls";

    /**
     * Activate/deactivate messages log (boolean)
     */
    public static final String STATUS_MESSAGES = "status_messages";

    /**
     * Activate/deactivate gravity log (boolean)
     */
    public static final String STATUS_GRAVITY = "status_gravity";

    /**
     * Gravity frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GRAVITY = "frequency_gravity";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_GRAVITY_ENFORCE = "frequency_gravity_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_GRAVITY = "threshold_gravity";

    /**
     * Activate/deactivate gyroscope log (boolean)
     */
    public static final String STATUS_GYROSCOPE = "status_gyroscope";

    /**
     * Gyroscope frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GYROSCOPE = "frequency_gyroscope";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_GYROSCOPE_ENFORCE = "frequency_gyroscope_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_GYROSCOPE = "threshold_gyroscope";

    /**
     * Activate/deactivate GPS location log (boolean)
     */
    public static final String STATUS_LOCATION_GPS = "status_location_gps";

    /**
     * GPS location frequency in seconds (default = 180). 0 is always on.
     */
    public static final String FREQUENCY_LOCATION_GPS = "frequency_location_gps";

    /**
     * GPS location minimum acceptable accuracy (default = 150), in meters
     */
    public static final String MIN_LOCATION_GPS_ACCURACY = "min_location_gps_accuracy";

    /**
     * Activate/deactivate network location log (boolean)
     */
    public static final String STATUS_LOCATION_NETWORK = "status_location_network";

    /**
     * Network location frequency in seconds (default = 300). 0 is always on.
     */
    public static final String FREQUENCY_LOCATION_NETWORK = "frequency_location_network";

    /**
     * Network location minimum acceptable accuracy (default = 1500), in meters
     */
    public static final String MIN_LOCATION_NETWORK_ACCURACY = "min_location_network_accuracy";

    /**
     * Location expiration time (default = 300), in seconds
     */
    public static final String LOCATION_EXPIRATION_TIME = "location_expiration_time";

    /**
     * Location geofence.  If given and location does NOT fall within, then do not
     * record location points.
     * Format: "fence1 fence2 ..." (space separated string)
     *   Circle fence: "lat,lon,radius".  Radius in METERS.
     *   Rectangle fence: "rect,lat1,lon1,lat2,lon2". Literal "rect", then lats/lons.
     */
    public static final String LOCATION_GEOFENCE = "location_geofence";

    /**
     * Save all locations.  All locations given to the location service will be saved,
     * without applying any logic to find the currently most accurate locations.
     * This makes for a slightly more complicated analysis later on, but more data
     * to work with.
     */
    public static final String LOCATION_SAVE_ALL = "location_save_all";


    /**
     * Activate/deactivate passive location log (boolean).  This does not turn on GPS/network
     * location tracking, but if any other application turns on location requests, Aware will
     * receive the locations too, with little battery overhead.
     */
    public static final String STATUS_LOCATION_PASSIVE = "status_location_passive";

    /**
     * Activate/deactivate light sensor log (boolean)
     */
    public static final String STATUS_LIGHT = "status_light";

    /**
     * Light frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LIGHT = "frequency_light";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_LIGHT_ENFORCE = "frequency_light_enforce";

    /**
     * Light threshold (float).  Do not record consecutive points if
     * change in value is less than this.
     */
    public static final String THRESHOLD_LIGHT = "threshold_light";

    /**
     * Activate/deactivate linear accelerometer log (boolean)
     */
    public static final String STATUS_LINEAR_ACCELEROMETER = "status_linear_accelerometer";

    /**
     * Linear accelerometer frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LINEAR_ACCELEROMETER = "frequency_linear_accelerometer";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_LINEAR_ACCELEROMETER_ENFORCE = "frequency_linear_accelerometer_enforce";

    /**
     * Linear accelerometer threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_LINEAR_ACCELEROMETER = "threshold_linear_accelerometer";

    /**
     * Activate/deactivate network usage events (boolean)
     */
    public static final String STATUS_NETWORK_EVENTS = "status_network_events";

    /**
     * Activate/deactivate network traffic log (boolean)
     */
    public static final String STATUS_NETWORK_TRAFFIC = "status_network_traffic";

    /**
     * Network traffic frequency (default = 60), in seconds
     */
    public static final String FREQUENCY_NETWORK_TRAFFIC = "frequency_network_traffic";

    /**
     * Activate/deactivate magnetometer log (boolean)
     */
    public static final String STATUS_MAGNETOMETER = "status_magnetometer";

    /**
     * Magnetometer frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_MAGNETOMETER = "frequency_magnetometer";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_MAGNETOMETER_ENFORCE = "frequency_magnetometer_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_MAGNETOMETER = "threshold_magnetometer";

    /**
     * Activate/deactivate barometer log (boolean)
     */
    public static final String STATUS_BAROMETER = "status_barometer";

    /**
     * Barometer frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_BAROMETER = "frequency_barometer";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_BAROMETER_ENFORCE = "frequency_barometer_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_BAROMETER = "threshold_barometer";

    /**
     * Activate/deactivate processor log (boolean)
     */
    public static final String STATUS_PROCESSOR = "status_processor";

    /**
     * Processor frequency (default = 10), in seconds
     */
    public static final String FREQUENCY_PROCESSOR = "frequency_processor";

    /**
     * Activate/deactivate timezone log (boolean)
     */
    public static final String STATUS_TIMEZONE = "status_timezone";

    /**
     * Timezone frequency (default = 3600) in seconds
     */
    public static final String FREQUENCY_TIMEZONE = "frequency_timezone";

    /**
     * Activate/deactivate proximity log (boolean)
     */
    public static final String STATUS_PROXIMITY = "status_proximity";

    /**
     * Proximity frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_PROXIMITY = "frequency_proximity";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_PROXIMITY_ENFORCE = "frequency_proximity_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_PROXIMITY = "threshold_proximity";

    /**
     * Activate/deactivate rotation log (boolean)
     */
    public static final String STATUS_ROTATION = "status_rotation";

    /**
     * Rotation frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ROTATION = "frequency_rotation";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_ROTATION_ENFORCE = "frequency_rotation_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_ROTATION = "threshold_rotation";

    /**
     * Activate/deactivate screen usage log (boolean)
     */
    public static final String STATUS_SCREEN = "status_screen";

    /**
     * Activate/deactivate temperature sensor log (boolean)
     */
    public static final String STATUS_TEMPERATURE = "status_temperature";

    /**
     * Temperature frequency in microseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_TEMPERATURE = "frequency_temperature";

    /**
     * Discard sensor events that come in more often than frequency
     */
    public static final String FREQUENCY_TEMPERATURE_ENFORCE = "frequency_temperature_enforce";

    /**
     * Threshold (float).  Do not record consecutive points if
     * change in value of all axes is less than this.
     */
    public static final String THRESHOLD_TEMPERATURE = "threshold_temperature";

    /**
     * Activate/deactivate telephony log (boolean)
     */
    public static final String STATUS_TELEPHONY = "status_telephony";

    /**
     * Activate/deactivate wifi scanning log (boolean)
     */
    public static final String STATUS_WIFI = "status_wifi";

    /**
     * Wifi scan frequency (default = 60), in seconds.
     */
    public static final String FREQUENCY_WIFI = "frequency_wifi";

    /**
     * Activate/deactivate mobile ESM (boolean)
     */
    public static final String STATUS_ESM = "status_esm";

    /**
     * Activate/deactivate MQTT client (boolean)
     */
    public static final String STATUS_MQTT = "status_mqtt";

    /**
     * MQTT Server IP/URL
     */
    public static final String MQTT_SERVER = "mqtt_server";

    /**
     * MQTT Server port (default = 1883)
     */
    public static final String MQTT_PORT = "mqtt_port";

    /**
     * MQTT Client username
     */
    public static final String MQTT_USERNAME = "mqtt_username";

    /**
     * MQTT Client password
     */
    public static final String MQTT_PASSWORD = "mqtt_password";

    /**
     * MQTT Client keep alive (default = 600), in seconds
     */
    public static final String MQTT_KEEP_ALIVE = "mqtt_keep_alive";

    /**
     * MQTT QoS (default = 2)
     * 0 - no guarantee
     * 1 - at least once
     * 2 - exactly once
     */
    public static final String MQTT_QOS = "mqtt_qos";

    /**
     * Activate/deactivate AWARE webservice (boolean)
     */
    public static final String STATUS_WEBSERVICE = "status_webservice";

    /**
     * AWARE webservice URL
     */
    public static final String WEBSERVICE_SERVER = "webservice_server";

    /**
     * AWARE webservice sync only over Wi-Fi connection
     */
    public static final String WEBSERVICE_WIFI_ONLY = "webservice_wifi_only";

    /**
     * AWARE webservice sync only if charging
     */
    public static final String WEBSERVICE_CHARGING = "webservice_charging";

    /**
     * AWARE webservice frequency (default = 30), in minutes
     */
    public static final String FREQUENCY_WEBSERVICE = "frequency_webservice";

    /**
     * AWARE webservice simple: don't sync tables.  See webservice_remove_data for how
     * this interacts with /latest.
     */
    public static final String WEBSERVICE_SIMPLE = "webservice_simple";

    /**
     * AWARE webservice remove data: Always delete data after it is uploaded.  User beware,
     * this may break some plugins!  If this AND webservice_simple are true, then do not do
     * the /latest calls, and the client only contacts servers if there is new data (as
     * detected by more than zero rows in the database).
     */
    public static final String WEBSERVICE_REMOVE_DATA = "webservice_remove_data";


    /**
     * AWARE webservice silence: If "true", then don't show a notification when uploading
     * data.  This only affects tasks that interact with webservice in the background.
     */
    public static final String WEBSERVICE_SILENT = "webservice_silent";

    /**
     * Key management strategy.
     * - "once" = keys are not updated once downloaded.
     * - "" = keys are updated as often as needed.
     */
    public static final String KEY_STRATEGY = "key_strategy";


    /**
     * How frequently to clean old data?
     * 0 - never
     * 1 - weekly
     * 2 - monthly
     * 3 - daily
     * 4 - always
     */
    public static final String FREQUENCY_CLEAN_OLD_DATA = "frequency_clean_old_data";

    /**
     * Activate/deactivate keyboard logging
     */
    public static final String STATUS_KEYBOARD = "status_keyboard";

    /**
     * Mask keyboard input
     */
    public static final String MASK_KEYBOARD = "mask_keyboard";

    /**
     * Preferred hash function
     */
    public static final String HASH_FUNCTION = "hash_function";

    /**
     * hash function salt
     */
    public static final String HASH_SALT = "hash_salt";

    /**
     * hash function phone.  If "device_id", then salt with this device's device_id.
     * This can be a hash name or a hash program
     */
    public static final String HASH_FUNCTION_PHONE = "hash_function_phone";

    /**
     * hash function MAC.  Do we hash MAC addresses?
     * blank=unhashed, non-blank=run this hash program (see Encrypter).
     */
    public static final String HASH_FUNCTION_MAC = "hash_function_mac";


    /**
     * hash function for SSID/network names/bluetooth names.
     * blank=unhashed, non-blank=run this hash program.
     */
    public static final String HASH_FUNCTION_SSID = "hash_function_ssid";

    /**
     * Activate/deactivate significant motion sensing
     */
    public static final String STATUS_SIGNIFICANT_MOTION = "status_significant_motion";

    /**
     * If in a study, remind the user to charge the phone when at 15%.
     */
    public static final String REMIND_TO_CHARGE = "remind_to_charge";

    /**
     * For all sensors, discard sensor events that come in more often than frequency.
     * The frequency is enforced if this setting OR the sensor-specific setting is set.
     */
    public static final String ENFORCE_FREQUENCY_ALL = "enforce_frequency_all";

    /**
     * Makes AWARE a foreground service
     */
    public static final String FOREGROUND_PRIORITY = "foreground_priority";

    /**
     * Fallback to network sync after xh have elapsed without WiFi synching
     */
    public static final String WEBSERVICE_FALLBACK_NETWORK = "fallback_network";

    /**
     * Log touch and gesture events
     */
    public static final String STATUS_TOUCH = "status_touch";

    /**
     * Masks text produced by touch events
     */
    public static final String MASK_TOUCH_TEXT = "mask_touch_text";

    /**
     * Lock interface after participant joins study
     */
    public static final String INTERFACE_LOCKED = "interface_locked";

    /**
     * Enable real-time streaming through eventbus
     */
    public static final String STATUS_WEBSOCKET = "status_websocket";

    /**
     * Where the websocket server is located (https://domain or IP)
     */
    public static final String WEBSOCKET_SERVER = "websocket_server";
}