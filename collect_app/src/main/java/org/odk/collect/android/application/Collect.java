/*
 * Copyright (C) 2017 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.application;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.location.Location;       // smap
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDex;

import com.crashlytics.android.Crashlytics;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobManagerCreateException;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import net.danlew.android.joda.JodaTimeAndroid;

import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.amazonaws.mobile.AWSMobileClient;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.external.ExternalDataManager;
import org.odk.collect.android.external.handler.SmapRemoteDataItem; 
import org.odk.collect.android.injection.config.AppDependencyComponent;
import org.odk.collect.android.injection.config.DaggerAppDependencyComponent;
import org.odk.collect.android.jobs.CollectJobCreator;
import org.odk.collect.android.loaders.GeofenceEntry;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.logic.FormInfo;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.preferences.AdminSharedPreferences;
import org.odk.collect.android.preferences.AutoSendPreferenceMigrator;
import org.odk.collect.android.taskModel.FormLaunchDetail;
import org.odk.collect.android.taskModel.FormRestartDetails;
import org.odk.collect.android.utilities.LocaleHelper;
import org.odk.collect.android.preferences.FormMetadataMigrator;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PrefMigrator;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.tasks.sms.SmsNotificationReceiver;
import org.odk.collect.android.tasks.sms.SmsSentBroadcastReceiver;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.LocaleHelper;
import org.odk.collect.android.utilities.NotificationUtils;
import org.odk.collect.utilities.UserAgentProvider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Stack;

import javax.inject.Inject;

import timber.log.Timber;

import static org.odk.collect.android.logic.PropertyManager.PROPMGR_USERNAME;
import static org.odk.collect.android.logic.PropertyManager.SCHEME_USERNAME;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_APP_LANGUAGE;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_FONT_SIZE;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_USERNAME;
import static org.odk.collect.android.tasks.sms.SmsNotificationReceiver.SMS_NOTIFICATION_ACTION;
import static org.odk.collect.android.tasks.sms.SmsSender.SMS_SEND_ACTION;

/**
 * The Open Data Kit Collect application.
 *
 * @author carlhartung
 */
public class Collect extends Application {

    public static final String DEFAULT_FONTSIZE = "21";
    public static final int DEFAULT_FONTSIZE_INT = 21;

    public static final int CLICK_DEBOUNCE_MS = 1000;

    public static String defaultSysLanguage;
    private static Collect singleton;
    private static long lastClickTime;
    private static String lastClickName;

    @Nullable
    private FormController formController;
    private ExternalDataManager externalDataManager;
    private FirebaseAnalytics firebaseAnalytics;
    private AppDependencyComponent applicationComponent;

    private Location location = null;                   // smap
    private ArrayList<GeofenceEntry> geofences = new ArrayList<GeofenceEntry>();    // smap
    private boolean recordLocation = false;             // smap
    private FormInfo formInfo = null;                   // smap
    private boolean tasksDownloading = false;           // smap
    // Keep a reference to form entry activity to allow cancel dialogs to be shown during remote calls
    private FormEntryActivity formEntryActivity = null; // smap
    private HashMap<String, SmapRemoteDataItem> remoteCache = null;         // smap
    private HashMap<String, String> remoteCalls = null;                     // smap
    private Stack<FormLaunchDetail> formStack = new Stack<>();              // smap
    private FormRestartDetails mRestartDetails;                             // smap
    @Inject
    UserAgentProvider userAgentProvider;

    @Inject
    public
    CollectJobCreator collectJobCreator;

    public static Collect getInstance() {
        return singleton;
    }

    public static int getQuestionFontsize() {
        // For testing:
        Collect instance = Collect.getInstance();
        if (instance == null) {
            return Collect.DEFAULT_FONTSIZE_INT;
        }

        return Integer.parseInt(String.valueOf(GeneralSharedPreferences.getInstance().get(KEY_FONT_SIZE)));
    }

    /**
     * Predicate that tests whether a directory path might refer to an
     * ODK Tables instance data directory (e.g., for media attachments).
     */
    public static boolean isODKTablesInstanceDataDirectory(File directory) {
        /*
         * Special check to prevent deletion of files that
         * could be in use by ODK Tables.
         */
        String dirPath = directory.getAbsolutePath();
        StoragePathProvider storagePathProvider = new StoragePathProvider();
        if (dirPath.startsWith(storagePathProvider.getStorageRootDirPath())) {
            dirPath = dirPath.substring(storagePathProvider.getStorageRootDirPath().length());
            String[] parts = dirPath.split(File.separatorChar == '\\' ? "\\\\" : File.separator);
            // [appName, instances, tableId, instanceId ]
            if (parts.length == 4 && parts[1].equals("instances")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public FormController getFormController() {
        return formController;
    }

    public void setFormController(@Nullable FormController controller) {
        formController = controller;
    }

    public ExternalDataManager getExternalDataManager() {
        return externalDataManager;
    }

    public void setExternalDataManager(ExternalDataManager externalDataManager) {
        this.externalDataManager = externalDataManager;
    }

    /**
     * Get a User-Agent string that provides the platform details followed by the application ID
     * and application version name: {@code Dalvik/<version> (platform info) org.odk.collect.android/v<version>}.
     *
     * This deviates from the recommended format as described in https://github.com/opendatakit/collect/issues/3253.
     */

    public boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetworkInfo = manager.getActiveNetworkInfo();
        return currentNetworkInfo != null && currentNetworkInfo.isConnected();
    }

    /*
        Adds support for multidex support library. For more info check out the link below,
        https://developer.android.com/studio/build/multidex.html
    */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        setupDagger();

        NotificationUtils.createNotificationChannel(singleton);

        registerReceiver(new SmsSentBroadcastReceiver(), new IntentFilter(SMS_SEND_ACTION));
        registerReceiver(new SmsNotificationReceiver(), new IntentFilter(SMS_NOTIFICATION_ACTION));

        try {
            JobManager
                    .create(this)
                    .addJobCreator(collectJobCreator);
        } catch (JobManagerCreateException e) {
            Timber.e(e);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        FormMetadataMigrator.migrate(prefs);
        PrefMigrator.migrateSharedPrefs();
        AutoSendPreferenceMigrator.migrate();

        reloadSharedPreferences();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        JodaTimeAndroid.init(this);

        defaultSysLanguage = Locale.getDefault().getLanguage();
        new LocaleHelper().updateLocale(this);

        initializeJavaRosa();

        if (BuildConfig.BUILD_TYPE.equals("odkCollectRelease")) {
            Timber.plant(new CrashReportingTree());
        } else {
            Timber.plant(new Timber.DebugTree());
        }

        setupRemoteAnalytics();
        setupLeakCanary();
        setupOSMDroid();

        // Force inclusion of scoped storage strings so they can be translated
        Timber.i("%s %s", getString(R.string.scoped_storage_banner_text),
                                   getString(R.string.scoped_storage_learn_more));
    }

    private void setupRemoteAnalytics() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isAnalyticsEnabled = settings.getBoolean(GeneralKeys.KEY_ANALYTICS, true);
        setAnalyticsCollectionEnabled(isAnalyticsEnabled);
    }

    protected void setupOSMDroid() {
        org.osmdroid.config.Configuration.getInstance().setUserAgentValue(userAgentProvider.getUserAgent());
    }

    private void setupDagger() {
        applicationComponent = DaggerAppDependencyComponent.builder()
                .application(this)
                .build();

        applicationComponent.inject(this);
    }

    protected RefWatcher setupLeakCanary() {
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return RefWatcher.DISABLED;
        }
        return LeakCanary.install(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        //noinspection deprecation
        defaultSysLanguage = newConfig.locale.getLanguage();
        boolean isUsingSysLanguage = GeneralSharedPreferences.getInstance().get(KEY_APP_LANGUAGE).equals("");
        if (!isUsingSysLanguage) {
            new LocaleHelper().updateLocale(this);
        }
    }

    // Begin Smap
    // start, set and get location
    public void setFormInfo(FormInfo v) {
        formInfo = v;
    }
    public FormInfo getFormInfo() {
        return formInfo;
    }

    public void setLocation(Location l) {
        location = l;
    }
    public Location getLocation() {
        return location;
    }

    public void setGeofences(ArrayList<GeofenceEntry> geofences) {
        this.geofences = geofences;
    }
    public ArrayList<GeofenceEntry> getGeofences() {
        return geofences;
    }

    public void setDownloading(boolean v) {
        tasksDownloading = v;
    }
    public boolean  isDownloading() {
        return tasksDownloading;
    }
    // Initialise AWS
    private void initializeApplication() {
        AWSMobileClient.initializeMobileClientIfNecessary(getApplicationContext());

        // ...Put any application-specific initialization logic here...
    }
    // Set form entry activity
    public void setFormEntryActivity(FormEntryActivity activity) {
        formEntryActivity = activity;
    }
    public FormEntryActivity getFormEntryActivity() {
        return formEntryActivity;
    }
    public void clearRemoteServiceCaches() {
        remoteCache = new HashMap<String, SmapRemoteDataItem>();
    }
    public void initRemoteServiceCaches() {
        if(remoteCache == null) {
            remoteCache = new HashMap<String, SmapRemoteDataItem>();
        } else {
            ArrayList<String> expired = new ArrayList<String>();
            for(String key : remoteCache.keySet()) {
                SmapRemoteDataItem item = remoteCache.get(key);
                if(item.perSubmission) {
                    expired.add(key);

                }
            }
            if(expired.size() > 0) {
                for(String key : expired) {
                    remoteCache.remove(key);
                }
            }
        }
        remoteCalls = new HashMap<String, String>();
    }
    public String getRemoteData(String key) {
        SmapRemoteDataItem item = remoteCache.get(key);
        if(item != null) {
            return item.data;
        } else {
            return null;
        }
    }
    public void setRemoteItem(SmapRemoteDataItem item) {
        if(item.data == null) {
            // There was a network error
            remoteCache.remove(item.key);
        } else {
            remoteCache.put(item.key, item);
        }
    }
    public void startRemoteCall(String key) {
        remoteCalls.put(key, "started");
    }
    public void endRemoteCall(String key) {
        remoteCalls.remove(key);
    }
    public String getRemoteCall(String key) {
        return remoteCalls.get(key);
    }
    public boolean inRemoteCall() {
        return remoteCalls.size() > 0;
    }

    public void setFormRestartDetails(FormRestartDetails restartDetails) {
        mRestartDetails = restartDetails;
    }
    public FormRestartDetails getFormRestartDetails() {
        return mRestartDetails;
    }
    // End Smap


    /**
     * Gets the default {@link } for this {@link Application}.
     *
     * @return tracker
     *
     * smap commented out tracker and error reporting functions
    public synchronized Tracker getDefaultTracker() {
        /* smap disable tracker
        if (tracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            tracker = analytics.newTracker(R.xml.global_tracker);
        }

        return tracker;
    }
    */

    /*
     * smap
     * Push a FormLaunchDetail to the stack
     * this form should then be launched by SmapMain
     */
    public void pushToFormStack(FormLaunchDetail fld) {
        formStack.push(fld);
    }

    /*
     * smap
     * Pop a FormLaunchDetails from the stack
     */
    public FormLaunchDetail popFromFormStack() {
        if(formStack.empty()) {
            return null;
        } else {
            return formStack.pop();
        }
    }

    public void logRemoteAnalytics(String event, String action, String label) {
	/* smap
        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("label", label);
        firebaseAnalytics.logEvent(event, bundle);
        */
    }

    public void setAnalyticsCollectionEnabled(boolean isAnalyticsEnabled) {
    //    firebaseAnalytics.setAnalyticsCollectionEnabled(isAnalyticsEnabled);       // smap disable
    }

    private static class CrashReportingTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
                return;
            }

            Crashlytics.log(priority, tag, message);

            if (t != null && priority == Log.ERROR) {
                Crashlytics.logException(t);
            }
        }
    }

    public void initializeJavaRosa() {
        PropertyManager mgr = new PropertyManager(this);

        // Use the server username by default if the metadata username is not defined
        if (mgr.getSingularProperty(PROPMGR_USERNAME) == null || mgr.getSingularProperty(PROPMGR_USERNAME).isEmpty()) {
            mgr.putProperty(PROPMGR_USERNAME, SCHEME_USERNAME, (String) GeneralSharedPreferences.getInstance().get(KEY_USERNAME));
        }

        FormController.initializeJavaRosa(mgr);
    }

    // This method reloads shared preferences in order to load default values for new preferences
    private void reloadSharedPreferences() {
        GeneralSharedPreferences.getInstance().reloadPreferences();
        AdminSharedPreferences.getInstance().reloadPreferences();
    }

    // Debounce multiple clicks within the same screen
    public static boolean allowClick(String className) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        boolean isSameClass = className.equals(lastClickName);
        boolean isBeyondThreshold = elapsedRealtime - lastClickTime > CLICK_DEBOUNCE_MS;
        boolean isBeyondTestThreshold = lastClickTime == 0 || lastClickTime == elapsedRealtime; // just for tests
        boolean allowClick = !isSameClass || isBeyondThreshold || isBeyondTestThreshold;
        if (allowClick) {
            lastClickTime = elapsedRealtime;
            lastClickName = className;
        }
        return allowClick;
    }

    public AppDependencyComponent getComponent() {
        return applicationComponent;
    }

    public void setComponent(AppDependencyComponent applicationComponent) {
        this.applicationComponent = applicationComponent;
        applicationComponent.inject(this);
    }

    /**
     * Gets a unique, privacy-preserving identifier for the current form.
     *
     * @return md5 hash of the form title, a space, the form ID
     */
    public static String getCurrentFormIdentifierHash() {
        FormController formController = getInstance().getFormController();
        if (formController != null) {
            return formController.getCurrentFormIdentifierHash();
        }

        return "";
    }

    /**
     * Gets a unique, privacy-preserving identifier for a form based on its id and version.
     * @param formId id of a form
     * @param formVersion version of a form
     * @return md5 hash of the form title, a space, the form ID
     */
    public static String getFormIdentifierHash(String formId, String formVersion) {
        String formIdentifier = new FormsDao().getFormTitleForFormIdAndFormVersion(formId, formVersion) + " " + formId;
        return FileUtils.getMd5Hash(new ByteArrayInputStream(formIdentifier.getBytes()));
    }
}
