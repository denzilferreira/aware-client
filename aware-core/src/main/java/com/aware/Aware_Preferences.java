
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
    public static final String GROUP_ID = "group_id";

    /**
     * Automatically check for updates on the client
     */
    public static final String AWARE_AUTO_UPDATE = "aware_auto_update";

    /**
     * Activate/deactivate accelerometer log (boolean)
     */
    public static final String STATUS_ACCELEROMETER = "status_accelerometer";

    /**
     * Accelerometer frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ACCELEROMETER = "frequency_accelerometer";

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
     * Gravity frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GRAVITY = "frequency_gravity";

    /**
     * Activate/deactivate gyroscope log (boolean)
     */
    public static final String STATUS_GYROSCOPE = "status_gyroscope";

    /**
     * Gyroscope frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_GYROSCOPE = "frequency_gyroscope";

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
     * Activate/deactivate light sensor log (boolean)
     */
    public static final String STATUS_LIGHT = "status_light";

    /**
     * Light frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LIGHT = "frequency_light";

    /**
     * Activate/deactivate linear accelerometer log (boolean)
     */
    public static final String STATUS_LINEAR_ACCELEROMETER = "status_linear_accelerometer";

    /**
     * Linear accelerometer frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_LINEAR_ACCELEROMETER = "frequency_linear_accelerometer";

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
     * Magnetometer frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_MAGNETOMETER = "frequency_magnetometer";

    /**
     * Activate/deactivate barometer log (boolean)
     */
    public static final String STATUS_BAROMETER = "status_barometer";

    /**
     * Barometer frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_BAROMETER = "frequency_barometer";

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
     * Proximity frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_PROXIMITY = "frequency_proximity";

    /**
     * Activate/deactivate rotation log (boolean)
     */
    public static final String STATUS_ROTATION = "status_rotation";

    /**
     * Rotation frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_ROTATION = "frequency_rotation";

    /**
     * Activate/deactivate screen usage log (boolean)
     */
    public static final String STATUS_SCREEN = "status_screen";

    /**
     * Activate/deactivate temperature sensor log (boolean)
     */
    public static final String STATUS_TEMPERATURE = "status_temperature";

    /**
     * Temperature frequency in milliseconds: e.g.,
     * 0 - fastest
     * 20000 - game
     * 60000 - UI
     * 200000 - normal (default)
     */
    public static final String FREQUENCY_TEMPERATURE = "frequency_temperature";

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
     * MQTT Connection protocol (default = tcp)
     * tcp - unsecure
     * ssl - secure
     */
    public static final String MQTT_PROTOCOL = "mqtt_protocol";

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
     * AWARE webservice frequency (default = 30), in minutes
     */
    public static final String FREQUENCY_WEBSERVICE = "frequency_webservice";

    /**
     * How frequently to clean old data?
     * 0 - never
     * 1 - weekly
     * 2 - monthly
     */
    public static final String FREQUENCY_CLEAN_OLD_DATA = "frequency_clean_old_data";

    /**
     * Activate/deactivate keyboard logging
     */
    public static final String STATUS_KEYBOARD = "status_keyboard";
}