package org.odk.collect.android.preferences;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.utilities.QuestionFontSizeUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public final class GeneralKeys {
    // server_preferences.xml
    public static final String KEY_PROTOCOL                 = "protocol";

    // aggregate_preferences.xml
    public static final String KEY_SERVER_URL               = "server_url";
    public static final String KEY_USERNAME                 = "username";
    public static final String KEY_PASSWORD                 = "password";

    // other_preferences.xml
    public static final String KEY_FORMLIST_URL             = "formlist_url";
    public static final String KEY_SUBMISSION_URL           = "submission_url";

    // google_preferences.xml
    public static final String KEY_SELECTED_GOOGLE_ACCOUNT  = "selected_google_account";
    public static final String KEY_GOOGLE_SHEETS_URL        = "google_sheets_url";

    // user_interface_preferences.xml
    public static final String KEY_APP_THEME                = "appTheme";
    public static final String KEY_APP_LANGUAGE             = "app_language";
    public static final String KEY_FONT_SIZE                = "font_size";
    public static final String KEY_NAVIGATION               = "navigation";
    public static final String KEY_SHOW_SPLASH              = "showSplash";
    public static final String KEY_SPLASH_PATH              = "splashPath";

    // map_preferences.xml
    public static final String KEY_BASEMAP_SOURCE           = "basemap_source";

    // basemap styles
    public static final String KEY_GOOGLE_MAP_STYLE         = "google_map_style";
    public static final String KEY_MAPBOX_MAP_STYLE         = "mapbox_map_style";
    public static final String KEY_USGS_MAP_STYLE           = "usgs_map_style";
    public static final String KEY_CARTO_MAP_STYLE          = "carto_map_style";

    public static final String KEY_REFERENCE_LAYER          = "reference_layer";

    // form_management_preferences.xml
    public static final String KEY_PERIODIC_FORM_UPDATES_CHECK = "periodic_form_updates_check";
    public static final String KEY_AUTOMATIC_UPDATE         = "automatic_update";
    public static final String KEY_HIDE_OLD_FORM_VERSIONS   = "hide_old_form_versions";
    public static final String KEY_AUTOSEND                 = "autosend";
    public static final String KEY_DELETE_AFTER_SEND        = "delete_send";
    public static final String KEY_COMPLETED_DEFAULT        = "default_completed";
    public static final String KEY_CONSTRAINT_BEHAVIOR      = "constraint_behavior";
    public static final String KEY_HIGH_RESOLUTION          = "high_resolution";
    public static final String KEY_IMAGE_SIZE               = "image_size";
    public static final String KEY_GUIDANCE_HINT            = "guidance_hint";
    public static final String KEY_INSTANCE_SYNC            = "instance_sync";

    // identity_preferences.xml
    public static final String KEY_ANALYTICS                = "analytics";

    // form_metadata_preferences.xml
    public static final String KEY_METADATA_USERNAME        = "metadata_username";
    public static final String KEY_METADATA_PHONENUMBER     = "metadata_phonenumber";
    public static final String KEY_METADATA_EMAIL           = "metadata_email";

    static final String KEY_FORM_METADATA                   = "form_metadata";

    // other keys
    public static final String KEY_LAST_VERSION             = "lastVersion";
    public static final String KEY_FIRST_RUN                = "firstRun";
    public static final String KEY_SCOPED_STORAGE_USED      = "scoped_storage_used";
    public static final String KEY_MAPBOX_INITIALIZED       = "mapbox_initialized";
    public static final String KEY_GOOGLE_BUG_154855417_FIXED = "google_bug_154855417_fixed";

    /** Whether any existing username and email values have been migrated to form metadata */
    static final String KEY_METADATA_MIGRATED               = "metadata_migrated";
    public static final String KEY_INSTALL_ID               = "metadata_installid";

    public static final String KEY_BACKGROUND_LOCATION      = "background_location";

    // values
    public static final String NAVIGATION_SWIPE             = "swipe";
    public static final String CONSTRAINT_BEHAVIOR_ON_SWIPE = "on_swipe";
    public static final String CONSTRAINT_BEHAVIOR_ON_FINALIZE = "on_finalize";       // smap
    public static final String NAVIGATION_BUTTONS           = "buttons";
    public static final String GOOGLE_MAPS                 = "google_maps";     // smap make public
    private static final String AUTOSEND_OFF                = "off";
    private static final String GUIDANCE_HINT_OFF           = "no";
    static final String KEY_AUTOSEND_WIFI                   = "autosend_wifi";
    static final String KEY_AUTOSEND_NETWORK                = "autosend_network";

    // basemap section
    public static final String CATEGORY_BASEMAP             = "category_basemap";

    // basemap source values
    public static final String BASEMAP_SOURCE_GOOGLE        = "google";
    public static final String BASEMAP_SOURCE_MAPBOX        = "mapbox";
    public static final String BASEMAP_SOURCE_OSM           = "osm";
    public static final String BASEMAP_SOURCE_USGS          = "usgs";
    public static final String BASEMAP_SOURCE_STAMEN        = "stamen";
    public static final String BASEMAP_SOURCE_CARTO         = "carto";

    // Not currently used
    public static final String KEY_SMS_GATEWAY              = "sms_gateway";
    public static final String KEY_SUBMISSION_TRANSPORT_TYPE = "submission_transport_type";
    public static final String KEY_TRANSPORT_PREFERENCE      = "submission_transport_preference";
    public static final String KEY_SMS_PREFERENCE            = "sms_preference";

    // start smap
    public static final String KEY_SMAP_REVIEW_FINAL = "review_final";    // Allow review of Form after finalising
    public static final String KEY_SMAP_USER_LOCATION = "smap_gps_trail";    // Record a user trail
    public static final String KEY_SMAP_LOCATION_TRIGGER = "location_trigger";  // Enable triggering of forms by location
    public static final String KEY_SMAP_ODK_STYLE_MENUS = "odk_style_menus";  // Show ODK style menus as well as refresh
    public static final String KEY_SMAP_ODK_INSTANCENAME = "odk_instancename";  // Allow user to change instance name
    public static final String KEY_SMAP_PREVENT_DISABLE_TRACK = "disable_prevent_track";  // Prevent the user from disabling tracking
    public static final String KEY_SMAP_ODK_ADMIN_MENU = "odk_admin_menu";  // Show ODK admin menu
    public static final String KEY_SMAP_ADMIN_SERVER_MENU = "admin_server_menu";  // Show server menu in general settings
    public static final String KEY_SMAP_ADMIN_META_MENU = "admin_meta_menu";  // Show meta menu in general settings
    public static final String KEY_SMAP_EXIT_TRACK_MENU = "smap_exit_track_menu";  // Show ODK admin menu
    public static final String KEY_SMAP_OVERRIDE_SYNC = "smap_override_sync";  // Override the local settings for synchronisation
    public static final String KEY_SMAP_OVERRIDE_LOCATION = "smap_override_location";  // Override the local settings for user trail
    public static final String KEY_SMAP_OVERRIDE_DELETE = "smap_override_del";  // Override the local settings for delete after send
    public static final String KEY_SMAP_OVERRIDE_HIGH_RES_VIDEO = "smap_override_high_res_video";  // Override the local settings for video resolution
    public static final String KEY_SMAP_OVERRIDE_GUIDANCE = "smap_override_guidance";  // Override the local settings for guidance hint
    public static final String KEY_SMAP_OVERRIDE_IMAGE_SIZE = "smap_override_image_size";  // Override the local settings for the image size
    public static final String KEY_SMAP_OVERRIDE_NAVIGATION = "smap_override_navigation";  // Override the local settings for the screen navigation
    public static final String KEY_SMAP_REGISTRATION_ID = "registration_id";  // Android notifications id
    public static final String KEY_SMAP_REGISTRATION_SERVER = "registration_server";  // Server name that has been registered
    public static final String KEY_SMAP_REGISTRATION_USER = "registration_user";  // User name that has been registered
    public static final String KEY_SMAP_LAST_LOGIN = "last_login";  // System time in milli seconds that the user last logged in
    public static final String KEY_SMAP_PASSWORD_POLICY = "pw_policy";
    // end smap

    private static HashMap<String, Object> getHashMap() {
        HashMap<String, Object> hashMap = new HashMap<>();
        // aggregate_preferences.xml
        hashMap.put(KEY_SERVER_URL,                 Collect.getInstance().getString(R.string.default_server_url));
        hashMap.put(KEY_USERNAME,                   "");
        hashMap.put(KEY_PASSWORD,                   "");
        // form_management_preferences.xml
        hashMap.put(KEY_AUTOSEND,                   AUTOSEND_OFF);
        hashMap.put(KEY_GUIDANCE_HINT,              GUIDANCE_HINT_OFF);
        hashMap.put(KEY_DELETE_AFTER_SEND,          false);
        hashMap.put(KEY_COMPLETED_DEFAULT,          true);
        hashMap.put(KEY_CONSTRAINT_BEHAVIOR,        CONSTRAINT_BEHAVIOR_ON_SWIPE);
        hashMap.put(KEY_HIGH_RESOLUTION,            false);
        hashMap.put(KEY_IMAGE_SIZE,                 "original_image_size");
        hashMap.put(KEY_INSTANCE_SYNC,              true);
        hashMap.put(KEY_PERIODIC_FORM_UPDATES_CHECK, "never");
        hashMap.put(KEY_AUTOMATIC_UPDATE,           false);
        hashMap.put(KEY_HIDE_OLD_FORM_VERSIONS,     true);
        hashMap.put(KEY_BACKGROUND_LOCATION,        true);
        // form_metadata_preferences.xml
        hashMap.put(KEY_METADATA_USERNAME,          "");
        hashMap.put(KEY_METADATA_PHONENUMBER,       "");
        hashMap.put(KEY_METADATA_EMAIL,             "");
        // google_preferences.xml
        hashMap.put(KEY_SELECTED_GOOGLE_ACCOUNT,    "");
        hashMap.put(KEY_GOOGLE_SHEETS_URL,          "");
        // identity_preferences.xml
        hashMap.put(KEY_ANALYTICS,                  true);
        // other_preferences.xml
        hashMap.put(KEY_FORMLIST_URL,               Collect.getInstance().getString(R.string.default_odk_formlist));
        hashMap.put(KEY_SUBMISSION_URL,             Collect.getInstance().getString(R.string.default_odk_submission));
        // server_preferences.xml
        hashMap.put(KEY_PROTOCOL,                   Collect.getInstance().getString(R.string.protocol_odk_default));
        hashMap.put(KEY_SMS_GATEWAY,                "");
        hashMap.put(KEY_SUBMISSION_TRANSPORT_TYPE,  Collect.getInstance().getString(R.string.transport_type_value_internet));
        // user_interface_preferences.xml
        hashMap.put(KEY_APP_THEME,                  Collect.getInstance().getString(R.string.app_theme_light));
        hashMap.put(KEY_APP_LANGUAGE,               "");
        hashMap.put(KEY_FONT_SIZE,                  String.valueOf(QuestionFontSizeUtils.DEFAULT_FONT_SIZE));
        hashMap.put(KEY_NAVIGATION,                 NAVIGATION_SWIPE);
        hashMap.put(KEY_SHOW_SPLASH,                false);
        hashMap.put(KEY_SPLASH_PATH,                Collect.getInstance().getString(R.string.default_splash_path));

        // start smap
        hashMap.put(KEY_SMAP_REVIEW_FINAL, true);
        hashMap.put(KEY_SMAP_USER_LOCATION, false);
        hashMap.put(KEY_SMAP_LOCATION_TRIGGER, true);
        hashMap.put(KEY_SMAP_ODK_STYLE_MENUS, true);
        hashMap.put(KEY_SMAP_ODK_INSTANCENAME, false);
        hashMap.put(KEY_SMAP_PREVENT_DISABLE_TRACK, false);
        hashMap.put(KEY_SMAP_ODK_ADMIN_MENU, false);
        hashMap.put(KEY_SMAP_ADMIN_SERVER_MENU, true);
        hashMap.put(KEY_SMAP_ADMIN_META_MENU, true);
        hashMap.put(KEY_SMAP_EXIT_TRACK_MENU, false);

        hashMap.put(KEY_SMAP_OVERRIDE_SYNC, false);
        hashMap.put(KEY_SMAP_OVERRIDE_DELETE, false);
        hashMap.put(KEY_SMAP_OVERRIDE_HIGH_RES_VIDEO, false);
        hashMap.put(KEY_SMAP_OVERRIDE_GUIDANCE, false);
        hashMap.put(KEY_SMAP_OVERRIDE_IMAGE_SIZE, false);
        hashMap.put(KEY_SMAP_OVERRIDE_NAVIGATION, false);
        hashMap.put(KEY_SMAP_OVERRIDE_LOCATION, false);
        hashMap.put(KEY_SMAP_REGISTRATION_ID, "");
        hashMap.put(KEY_SMAP_REGISTRATION_SERVER, "");
        hashMap.put(KEY_SMAP_REGISTRATION_USER, "");
        hashMap.put(KEY_SMAP_LAST_LOGIN, "0");
        hashMap.put(KEY_SMAP_PASSWORD_POLICY, "-1");
        // end smap

        // map_preferences.xml
        hashMap.put(KEY_BASEMAP_SOURCE,             BASEMAP_SOURCE_GOOGLE);
        return hashMap;
    }

    static final Collection<String> KEYS_WE_SHOULD_NOT_RESET = Arrays.asList(
            KEY_LAST_VERSION,
            KEY_FIRST_RUN,
            KEY_METADATA_MIGRATED,
            KEY_AUTOSEND_WIFI,
            KEY_AUTOSEND_NETWORK,
            KEY_SCOPED_STORAGE_USED,
            KEY_MAPBOX_INITIALIZED
    );

    public static final HashMap<String, Object> DEFAULTS = getHashMap();

    private GeneralKeys() {

    }

}
