/*
 * Copyright (C) 2011 Cloudtec Pty Ltd
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

package org.odk.collect.android.tasks;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.URI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.NotificationActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.database.Assignment;
import org.odk.collect.android.database.TaskAssignment;
import org.odk.collect.android.database.TraceUtilities;
import org.odk.collect.android.formmanagement.ServerFormDetails;
import org.odk.collect.android.instances.Instance;
import org.odk.collect.android.listeners.DownloadFormsTaskListener;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.notifications.Notifier;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminSharedPreferences;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.GuidanceHint;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.listeners.TaskDownloaderListener;
import org.odk.collect.android.loaders.TaskEntry;
import org.odk.collect.android.taskModel.FormLocator;
import org.odk.collect.android.taskModel.TaskCompletionInfo;
import org.odk.collect.android.taskModel.TaskResponse;
import org.odk.collect.android.utilities.ManageForm;
import org.odk.collect.android.utilities.ManageForm.ManageFormDetails;
import org.odk.collect.android.utilities.ManageFormResponse;
import org.odk.collect.android.utilities.MultiFormDownloader;
import org.odk.collect.android.utilities.Utilities;
import org.odk.collect.android.utilities.WebCredentialsUtils;

import java.io.File;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;



/**
 * Background task for downloading tasks 
 * 
 * @author Neil Penman (neilpenman@gmail.com)
 */
public class DownloadTasksTask extends AsyncTask<Void, String, HashMap<String, String>> {

	private TaskDownloaderListener mStateListener;
	HashMap<String, String> results = null;
    SharedPreferences sharedPreferences = null;
    ArrayList<TaskEntry> tasks = new ArrayList<TaskEntry>();
    HashMap<Long, TaskStatus> taskMap = new HashMap<Long, TaskStatus>();
    Gson gson = null;
    TaskResponse tr = null;                         // Data returned from the server
    String serverUrl = null;                        // Current server
    String source = null;                           // Server name
    String taskURL = null;                          // Url to get tasks
    int count;                                      // Record number of deletes

    @Inject
    OpenRosaHttpInterface httpInterface;

    @Inject
    WebCredentialsUtils webCredentialsUtils;

    @Inject
    MultiFormDownloader multiFormDownloader;

    @Inject
    Notifier notifier;
    
    private FormsDao formsDao;
	
	/*
	 * class used to store status of existing tasks in the database and their database id
	 * A hash is created of the data stored in these object to uniquely identify the task
	 */
	
	private class TaskStatus {
		@SuppressWarnings("unused")
		public long tid;
		@SuppressWarnings("unused")
		public String status;
		@SuppressWarnings("unused")
		public boolean keep;
		
		public TaskStatus(long tid, String status) {
			this.tid = tid;
			this.status = status;
			keep = false;
		}
	}

	public DownloadTasksTask(){Collect.getInstance().getComponent().inject(this);}
    /*
     * Add a custom date parser as old versions of the server will send an invalid date format
     */
    public class DateDeserializer implements JsonDeserializer<Date> {
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            SimpleDateFormat sdfOld = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            SimpleDateFormat sdfNew = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            sdfOld.setTimeZone(TimeZone.getTimeZone("UTC"));
            sdfNew.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = null;
            try {
                Timber.i("Date string primitive: " + json.getAsJsonPrimitive().getAsString());
                try {
                    date = sdfNew.parse(json.getAsJsonPrimitive().getAsString());
                } catch (Exception e) {
                    date = sdfOld.parse(json.getAsJsonPrimitive().getAsString());
                }
                Timber.i("Parsed date: " + date.getTime());
                return date;
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return date;
        }
    }


    @Override
    public HashMap<String, String> doInBackground(Void... values) {

		results = new HashMap<String,String>();
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        source = Utilities.getSource();
        serverUrl = sharedPreferences.getString(GeneralKeys.KEY_SERVER_URL, null);
        taskURL = serverUrl + "/surveyKPI/myassignments";

        // Should mostly work may be better to add a lock however any error is recoverable
        if(Collect.getInstance().isDownloading()) {
            return null;
        } else {
            Collect.getInstance().setDownloading(true);
        }

        try {

            notifier.showNotification(null,
                    NotificationActivity.NOTIFICATION_ID,
                    R.string.app_name,
                    Collect.getInstance().getBaseContext().getString(R.string.smap_refresh_started),
                    true);

            synchronise();      // Synchronise the phone with the server
        } finally {
            // Set refresh done notification icon
            StringBuilder message = Utilities.getUploadMessage(results);

            Intent notifyIntent = new Intent(Collect.getInstance(), NotificationActivity.class);
            notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            notifyIntent.putExtra(NotificationActivity.NOTIFICATION_MESSAGE, message.toString().trim());
            PendingIntent pendingNotify = PendingIntent.getActivity(Collect.getInstance(), 0,
                    notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notifier.showNotification(pendingNotify,
                    NotificationActivity.NOTIFICATION_ID,
                    R.string.app_name,
                    message.toString().trim(),
                    false);

            Collect.getInstance().setDownloading(false);
        }

        // Refresh task list
        Intent intent = new Intent("org.smap.smapTask.refresh");
        LocalBroadcastManager.getInstance(Collect.getInstance()).sendBroadcast(intent);
        Timber.i("######## send org.smap.smapTask.refresh from downloadTasksTask");  // smap

        return results;
    }

    @Override
    protected void onPostExecute(HashMap<String, String> value) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.taskDownloadingComplete(value);
            }
        }
    }

    /*
     * Clean up after cancel
     */
    @Override
    protected void onCancelled() {

    }

    @Override
    protected void onProgressUpdate(String... values) {
        synchronized (this) {
            if (mStateListener != null && values.length > 0) {
                mStateListener.progressUpdate(values[0]);
            }
        }

    }

    public void setDownloaderListener(TaskDownloaderListener sl, Context context) {
        synchronized (this) {
            mStateListener = sl;
        }
    }
  
  
    /*
     * Synchronise the tasks stored on the phone with those on the server
     */
    private void synchronise() {

    	Timber.i("Synchronise()");
        
        if(source != null) {
	        try {

                /*
                 * Close tasks which were cancelled on the phone and
                 *  have been synchronised with the server
                 */
                count = Utilities.markClosedTasksWithStatus(Utilities.STATUS_T_CANCELLED);
                if(count > 0) {
                    results.put(Collect.getInstance().getString(R.string.smap_cancelled), count +
                            " " + Collect.getInstance().getString(R.string.smap_deleted));
                }

                /*
                 * Mark closed any surveys that were submitted last time and not deleted
                 */
                Utilities.markClosedTasksWithStatus(Utilities.STATUS_T_SUBMITTED);

	            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels

                /*
                 * Submit any completed forms
                 */
                InstanceUploaderTask.Outcome submitOutcome = submitCompletedForms();
                if(submitOutcome != null && submitOutcome.messagesByInstanceId != null) {
                    for (String key : submitOutcome.messagesByInstanceId.keySet()) {
                        results.put(key, submitOutcome.messagesByInstanceId.get(key));
                    }
                }

                /*
                 * Get an array of the existing server tasks on the phone and create a hashmap indexed on the assignment id
                 */
                Utilities.getTasks(tasks, false, "", "", true, false);
                for(TaskEntry t : tasks) {
                    TaskStatus ts = new TaskStatus(t.assId, t.taskStatus);
                    taskMap.put(t.assId, ts);
                }

                /*
	        	 * Get new forms and tasks from the server
	        	 */
                publishProgress(Collect.getInstance().getString(R.string.smap_new_forms));

                if(taskURL.startsWith("null")) {
                    throw new Exception(Collect.getInstance().getString(R.string.smap_no_server));
                }

                Uri u = Uri.parse(taskURL);

                HashMap<String, String> headers = new HashMap<String, String> ();
                // Send location with request (if available)  TODO check a parameter to see if this is turned on otherwise don't do it
                try {
                    Location locn = Collect.getInstance().getLocation();
                    if (locn != null) {
                        String lat = String.valueOf(locn.getLatitude());
                        String lon = String.valueOf(locn.getLongitude());
                        headers.put("lat", lat);
                        headers.put("lon", lon);
                    }
                } catch (Exception e) {

                }
                // Send device time with request
                headers.put("devicetime", String.valueOf(System.currentTimeMillis()));

                URI uri = URI.create(taskURL);
                String resp = httpInterface.getRequest(uri, "application/json", webCredentialsUtils.getCredentials(uri), headers);
                GsonBuilder gb = new GsonBuilder().registerTypeAdapter(Date.class, new DateDeserializer());
                gson = gb.create();

                if(resp.equals("Unauthorized")) {
                    throw new Exception(resp);
                }
                tr = gson.fromJson(resp, TaskResponse.class);
                Timber.i("Message:" + tr.message);

                // Report time difference
                if(Math.abs(tr.time_difference) > 60000 ) {
                    String msg = Collect.getInstance().getString(R.string.smap_time_difference);
                    long minutes = tr.time_difference / 60000;
                    msg = msg.replace("%s", String.valueOf(minutes));
                    results.put(Collect.getInstance().getString(R.string.smap_warning) + ":", msg );
                }

                if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels

                if(tr.settings !=null ) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(GeneralKeys.KEY_SMAP_LOCATION_TRIGGER, tr.settings.ft_location_trigger);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ODK_STYLE_MENUS, tr.settings.ft_odk_style_menus);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ODK_INSTANCENAME, tr.settings.ft_specify_instancename);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_PREVENT_DISABLE_TRACK, tr.settings.ft_prevent_disable_track);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ENABLE_GEOFENCE, tr.settings.ft_enable_geofence == null || tr.settings.ft_enable_geofence.equals("on"));
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ODK_ADMIN_MENU, tr.settings.ft_admin_menu);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ADMIN_SERVER_MENU, tr.settings.ft_server_menu);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_ADMIN_META_MENU, tr.settings.ft_meta_menu);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_EXIT_TRACK_MENU, tr.settings.ft_exit_track_menu);
                    editor.putBoolean(GeneralKeys.KEY_SMAP_REVIEW_FINAL, tr.settings.ft_review_final);

                    /*
                     * Override the user trail setting if this is set from the server
                     */
                    if(tr.settings.ft_send_location == null || tr.settings.ft_send_location.equals("off")) {
                        editor.putBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false);
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, true);
                    } else if(tr.settings.ft_send_location.equals("on")) {
                        editor.putBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, true);
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, true);
                    } else {
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, false);
                    }

                    /*
                     * Override the autosend setting if this is set from the server
                     */
                    if(tr.settings.ft_send != null) {
                        // Server version is 17.11+ or new setting has not been used
                        if(tr.settings.ft_send.equals("off") || tr.settings.ft_send.equals("wifi_only") || tr.settings.ft_send.equals("wifi_and_cellular")) {
                            // Set the preference value using the server value and disable from local editing
                            editor.putString(GeneralKeys.KEY_AUTOSEND, tr.settings.ft_send);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_SYNC, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_SYNC, false);
                        }
                    } else {
                        // Support legacy servers / settings
                        String autoSend = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_AUTOSEND);
                        if (tr.settings.ft_send_wifi_cell) {
                            autoSend = "wifi_and_cellular";
                        } else if (tr.settings.ft_send_wifi) {
                            autoSend = "wifi_only";
                        }
                        editor.putString(GeneralKeys.KEY_AUTOSEND, autoSend);
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_SYNC, false);
                    }

                    /*
                     * Override the delete after send setting if this is set from the server
                     */
                    if(tr.settings.ft_delete != null) {
                        if(tr.settings.ft_delete.equals("off")) {
                            editor.putBoolean(GeneralKeys.KEY_DELETE_AFTER_SEND, false);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_DELETE, true);
                        } else if(tr.settings.ft_delete.equals("on")) {
                            editor.putBoolean(GeneralKeys.KEY_DELETE_AFTER_SEND, true);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_DELETE, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_DELETE, false);
                        }

                    } else {
                        // Support legacy servers / settings
                        editor.putBoolean(GeneralKeys.KEY_DELETE_AFTER_SEND, tr.settings.ft_delete_submitted);
                    }

                    /*
                     * Override the camera image size setting
                     */
                    if(tr.settings.ft_image_size != null) {
                        if(!tr.settings.ft_image_size.equals("not set")) {
                            editor.putString(GeneralKeys.KEY_IMAGE_SIZE, tr.settings.ft_image_size);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_IMAGE_SIZE, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_IMAGE_SIZE, false);
                        }

                    } else {
                        // Leave the local settings as they are and enable for local editing
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_IMAGE_SIZE, false);
                    }

                    /*
                     * Override the guidance setting
                     */
                    if(tr.settings.ft_guidance != null) {
                        if(tr.settings.ft_guidance.equals("no")) {
                            editor.putString(GeneralKeys.KEY_GUIDANCE_HINT, GuidanceHint.No.toString());
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_GUIDANCE, true);
                        } else if(tr.settings.ft_guidance.equals("yes always")) {
                            editor.putString(GeneralKeys.KEY_GUIDANCE_HINT, GuidanceHint.Yes.toString());
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_GUIDANCE, true);
                        } else if(tr.settings.ft_guidance.equals("yes collapsed")) {
                            editor.putString(GeneralKeys.KEY_GUIDANCE_HINT, GuidanceHint.YesCollapsed.toString());
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_GUIDANCE, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_GUIDANCE, false);
                        }
                    }

                    /*
                     * Override the high resolution video
                     */
                    if(tr.settings.ft_high_res_video != null) {
                        if(tr.settings.ft_high_res_video.equals("off")) {
                            editor.putBoolean(GeneralKeys.KEY_HIGH_RESOLUTION, false);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_HIGH_RES_VIDEO, true);
                        } else if(tr.settings.ft_high_res_video.equals("on")) {
                            editor.putBoolean(GeneralKeys.KEY_HIGH_RESOLUTION, true);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_HIGH_RES_VIDEO, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_HIGH_RES_VIDEO, false);
                        }
                    }

                    /*
                     * Set the password policy
                     */
                    editor.putString(GeneralKeys.KEY_SMAP_PASSWORD_POLICY, String.valueOf(tr.settings.ft_pw_policy));

                    /*
                     * Override backward navigation setting
                     */
                    if(tr.settings.ft_backward_navigation != null) {
                        if(tr.settings.ft_backward_navigation.equals("disable")) {
                            // Disable moving backwards
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_MOVING_BACKWARDS, false);
                            AdminSharedPreferences.getInstance().save(AdminKeys.ALLOW_OTHER_WAYS_OF_EDITING_FORM, false);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_EDIT_SAVED, false);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_SAVE_MID, false);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_JUMP_TO, false);
                            GeneralSharedPreferences.getInstance().save(GeneralKeys.KEY_CONSTRAINT_BEHAVIOR, GeneralKeys.CONSTRAINT_BEHAVIOR_ON_SWIPE);

                            AdminSharedPreferences.getInstance().getInstance().save(AdminKeys.KEY_SMAP_OVERRIDE_MOVING_BACKWARDS, true);
                        } else if(tr.settings.ft_backward_navigation.equals("enable")) {
                            // Enable moving backwards
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_MOVING_BACKWARDS, true);
                            AdminSharedPreferences.getInstance().save(AdminKeys.ALLOW_OTHER_WAYS_OF_EDITING_FORM, true);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_EDIT_SAVED, true);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_SAVE_MID, true);
                            AdminSharedPreferences.getInstance().save(AdminKeys.KEY_JUMP_TO, true);
                            GeneralSharedPreferences.getInstance().save(GeneralKeys.KEY_CONSTRAINT_BEHAVIOR, GeneralKeys.CONSTRAINT_BEHAVIOR_ON_SWIPE);

                            AdminSharedPreferences.getInstance().getInstance().save(AdminKeys.KEY_SMAP_OVERRIDE_MOVING_BACKWARDS, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            AdminSharedPreferences.getInstance().getInstance().save(AdminKeys.KEY_SMAP_OVERRIDE_MOVING_BACKWARDS, false);
                        }
                    } else {
                        // Leave the local settings as they are and enable for local editing
                        AdminSharedPreferences.getInstance().getInstance().save(AdminKeys.KEY_SMAP_OVERRIDE_MOVING_BACKWARDS, false);
                    }

                    /*
                     * Override the screen navigation setting
                     */
                    if(tr.settings.ft_navigation != null) {
                        if(!tr.settings.ft_navigation.equals("not set")) {
                            editor.putString(GeneralKeys.KEY_NAVIGATION, tr.settings.ft_navigation);
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_NAVIGATION, true);
                        } else {
                            // Leave the local settings as they are and enable for local editing
                            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_NAVIGATION, false);
                        }

                    } else {
                        // Leave the local settings as they are and enable for local editing
                        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_NAVIGATION, false);
                    }

                    editor.apply();
                }

                /*
                 * Synchronise forms
                 *  Get any forms the user does not currently have
                 *  Delete any forms that are no longer accessible to the user
                 */
                HashMap<ServerFormDetails, String> outcome = synchroniseForms(tr.forms);
                if(outcome != null) {
                    for (ServerFormDetails key : outcome.keySet()) {
                        results.put(key.getFormName(), outcome.get(key));
                    }
                }

                if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels

                /*
                 * Apply task changes
                 *  Add new tasks
                 *  Update the status of tasks on the phone that have been cancelled on the server
                 */
                addAndUpdateEntries();

            	/*
            	 * Notify the server of the phone state
            	 *  (1) Update on the server all tasks that have a status of "accepted", "rejected" or "submitted" or "cancelled" or "completed"
            	 *      Note in the case of "cancelled" the client is merely acknowledging that it received the cancellation notice
            	 *  (2) Pass the list of forms and versions that have been applied back to the server
            	 */
		        updateTaskStatusToServer();

                if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels


            	/*
            	 * Mark entries that we are finished with as closed
            	 */
                Utilities.deleteRejectedTasks();    // Adds the deleted date if it is not already there and removes files
                count = Utilities.markClosedTasksWithStatus(Utilities.STATUS_T_REJECTED);
                if(count > 0) {
                    results.put(Collect.getInstance().getString(R.string.smap_rejected), count +
                            " " + Collect.getInstance().getString(R.string.smap_deleted));
                }


	        } catch(JsonSyntaxException e) {
	        	
	        	Timber.e("JSON Syntax Error:" + " for URL " + taskURL);
	        	publishProgress(e.getMessage());
	        	e.printStackTrace();
	        	results.put(Collect.getInstance().getString(R.string.smap_error) + ":", e.getMessage());
	        	
	        } catch (CancelException e) {	
	        	
	        	Timber.i("Info: Download cancelled by user.");

	        } catch (Exception e) {	

	        	Timber.e("Error:" + " for URL " + taskURL);
                e.printStackTrace();
                String msg = Utilities.translateMsg(e, null);
	        	publishProgress(msg);
	        	results.put(Collect.getInstance().getString(R.string.smap_error) + ":", msg );
	
	        }
        }
    }

    private InstanceUploaderTask.Outcome submitCompletedForms() {
       
        String selection = InstanceColumns.SOURCE + "=? and (" + InstanceColumns.STATUS + "=? or " + 
        		InstanceColumns.STATUS + "=?)" +
                " and " + InstanceColumns.DELETED_DATE + " is null ";
        String selectionArgs[] = {
        		Utilities.getSource(),
        		Instance.STATUS_COMPLETE,
                Instance.STATUS_SUBMISSION_FAILED
            };

        ArrayList<Long> toUpload = new ArrayList<Long>();
        Cursor c = null;
        try {
            c = Collect.getInstance().getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection,
                selectionArgs, null);
            
            if (c != null && c.getCount() > 0) {
                c.move(-1);
                while (c.moveToNext()) {
                    Long l = c.getLong(c.getColumnIndex(InstanceColumns._ID));
                    toUpload.add(Long.valueOf(l));
                }
            }

            } catch (Exception e) {
            	e.printStackTrace();
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            if(toUpload.size() > 0) {
                InstanceUploaderTask instanceUploaderTask = new InstanceServerUploaderTask();
                publishProgress(Collect.getInstance().getString(R.string.smap_submitting, toUpload.size()));
                instanceUploaderTask.setUploaderListener((InstanceUploaderListener) mStateListener);

                Long[] toSendArray = new Long[toUpload.size()];
                toUpload.toArray(toSendArray);
                Timber.i("Submitting " + toUpload.size() + " finalised surveys");

                InstanceUploaderTask.Outcome o = instanceUploaderTask.doInBackground(toSendArray);	// Already running a background task so call direct
            	instanceUploaderTask.onPostExecute(o);
                return o;
            } else {
            	return null;
            }
        
    }

    /*
	 * Loop through the task entries in the database
	 *  (1) Update on the server all that have a status of "accepted", "rejected" or "submitted"
	 *  (2) Send details on submitted tasks, such as where they were completed and optionally the trace of user movements, to the server
	 */
	private void updateTaskStatusToServer() throws Exception {

        TaskResponse updateResponse = new TaskResponse();
        
        // Add device id to response
        updateResponse.deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                .getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);

        // Get tasks that have not been synchronised
        ArrayList<TaskEntry> nonSynchTasks = new ArrayList<TaskEntry>();
        Utilities.getTasks(nonSynchTasks, true, "", "", true, false);

        /*
         * Set updates to task status
         */
        updateResponse.taskAssignments = new ArrayList<TaskAssignment> ();          // Updates to task status

        for(TaskEntry t : nonSynchTasks) {
  	  		if(t.taskStatus != null && t.isSynced.equals(Utilities.STATUS_SYNC_NO)) {
  	  			TaskAssignment ta = new TaskAssignment();
  	  			ta.assignment = new Assignment();
  	  			ta.assignment.assignment_id = (int) t.assId;
  	  			ta.assignment.dbId = (int) t.id;
  	  			ta.assignment.assignment_status = t.taskStatus;
                ta.assignment.task_comment = t.taskComment;
                ta.assignment.uuid = t.uuid;

	            updateResponse.taskAssignments.add(ta);
  	  		}
        }

        /*
         * Set details on submitted tasks
         */
        boolean sendLocation = (Boolean) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_SMAP_USER_LOCATION);
        long lastTraceIdSent = 0;
        if(sendLocation) {
            updateResponse.taskCompletionInfo = new ArrayList<>();   // Details on completed tasks

            for (TaskEntry t : nonSynchTasks) {
                if ((t.taskStatus.equals(Utilities.STATUS_T_SUBMITTED) || t.taskStatus.equals(Utilities.STATUS_T_CLOSED))
                        && t.isSynced.equals(Utilities.STATUS_SYNC_NO)) {
                    TaskCompletionInfo tci = new TaskCompletionInfo();
                    tci.actFinish = t.actFinish;
                    tci.lat = t.actLat;
                    tci.lon = t.actLon;
                    tci.ident = t.ident;
                    tci.uuid = t.uuid;
                    tci.assId = t.assId;

                    updateResponse.taskCompletionInfo.add(tci);
                }
            }

            // Get Points
            updateResponse.userTrail = new ArrayList<>(100);
            lastTraceIdSent = TraceUtilities.getPoints(updateResponse.userTrail, 10000, false);
        } else {
            // Delete any points that had been collected
            TraceUtilities.deleteSource(0);
        }
        Collect.getInstance().setSavedLocation(null);

        if(updateResponse.taskAssignments.size() > 0 ||
                (updateResponse.taskCompletionInfo != null && updateResponse.taskCompletionInfo.size() > 0) ||
                (updateResponse.userTrail != null && updateResponse.userTrail.size() > 0)) {

            publishProgress(Collect.getInstance().getString(R.string.smap_update_task_status));

            URI uri = URI.create(taskURL);
            try {
                // OOM
                httpInterface.uploadTaskStatus(updateResponse, uri, webCredentialsUtils.getCredentials(uri));
            } catch (Exception e) {
                results.put(Collect.getInstance().getString(R.string.smap_get_tasks),
                        e.getMessage());
                throw new Exception(e.getMessage());
            }


            for (TaskAssignment ta : updateResponse.taskAssignments) {
                Utilities.setTaskSynchronized((long) ta.assignment.dbId);        // Mark the task status as synchronised
            }
            TraceUtilities.deleteSource(lastTraceIdSent);
        }
	}
	
	/*
     * Loop through the entries from the source
     *   (1) Add entries that have a status of "new", "accepted" and are not already on the phone
     *   (2) Update the status of database entries where the source status is set to "cancelled"
     */
	private void addAndUpdateEntries() throws Exception {

    	if(tr.taskAssignments != null) {
            int count = 1;
        	for(TaskAssignment ta : tr.taskAssignments) {

                if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels

                Assignment assignment = ta.assignment;

                Timber.i("Task: " + assignment.assignment_id + " Status:" +
                        assignment.assignment_status + " Mode:" + ta.task.assignment_mode +
                        " Address: " + ta.task.address +
                        " NFC: " + ta.task.location_trigger +
                        " Form: " + ta.task.form_id + " version: " + ta.task.form_version +
                        "Assignee: " + assignment.assignee);


                // Find out if this task is already on the phone
                TaskStatus ts = taskMap.get(Long.valueOf((long) assignment.assignment_id));
                if(ts == null) {
                    Timber.i("New task: " + assignment.assignment_id);
                    // New task
                    if(assignment.assignment_status.equals(Utilities.STATUS_T_ACCEPTED) ||
                            assignment.assignment_status.equals(Utilities.STATUS_T_NEW)) {

                        // Ensure the instance data is available on the phone
                        // Use update_id in preference to initial_data url
                        if(tr.version < 1) {
                            if(ta.task.update_id != null) {
                                ta.task.initial_data = serverUrl + "/instanceXML/" +
                                        ta.task.form_id + "/0?key=instanceid&keyval=" + ta.task.update_id;
                            }
                        } else {
                            if(ta.task.initial_data_source != null && ta.task.initial_data_source.equals("task")) {
                                ta.task.initial_data = serverUrl + "/webForm/instance/" +
                                        ta.task.form_id + "/task/" + ta.task.id;
                            } else {
                                if(ta.task.update_id != null) {
                                    ta.task.initial_data = serverUrl + "/webForm/instance/" +
                                            ta.task.form_id + "/" + ta.task.update_id;
                                }
                            }
                        }
                        Timber.i("Instance url: " + ta.task.initial_data);

                        // Add instance data
                        ManageForm mf = new ManageForm();
                        ManageFormResponse mfr = mf.insertInstance(ta, assignment.assignment_id, source, serverUrl, tr.version);
                        if(!mfr.isError) {
                            results.put(ta.task.title, Collect.getInstance().getString(R.string.smap_created));
                            publishProgress(ta.task.title, Integer.valueOf(count).toString(), Integer.valueOf(tr.taskAssignments.size())
                                    .toString());
                        } else {
                            publishProgress(ta.task.title + " : Failed", Integer.valueOf(count).toString(), Integer.valueOf(tr.taskAssignments.size())
                                    .toString());
                            results.put(ta.task.title, "Creation failed: " + mfr.statusMsg );
                        }

                    }
                } else {        	// Existing task
                    Timber.i("Existing Task: " + assignment.assignment_id + " : " + assignment.assignment_status);

                    if(assignment.assignment_status.equals(Utilities.STATUS_T_CANCELLED) && !ts.status.equals(Utilities.STATUS_T_CANCELLED)) {
                        Utilities.setStatusForAssignment(assignment.assignment_id, assignment.assignment_status);
                        results.put(ta.task.title, assignment.assignment_status);
                    }

                    // Update the task if its status is not incomplete
                    Utilities.updateParametersForAssignment(assignment.assignment_id, ta);
                }
            }// end tasks loop
    	}

        // Remove any tasks that have been deleted from the server
        Utilities.rejectObsoleteTasks(tr.taskAssignments);

    	// Clean up the history table and remove old deleted instances
        Utilities.cleanHistory();
    	
    	return;
	}
	
	/*
     * Synchronise the forms on the server with those on the phone
     *   (1) Download forms on the server that are not on the phone
     *   (2) Delete forms not on the server or older versions of forms
     *       unless there is an uncompleted data instance using that form
     */
	private HashMap<ServerFormDetails, String> synchroniseForms(List<FormLocator> forms) throws Exception {
    	

		HashMap<ServerFormDetails, String> dfResults = null;
    	
    	if(forms == null) {
        	publishProgress(Collect.getInstance().getString(R.string.smap_no_forms));
    	} else {
    		
    		HashMap <String, String> formMap = new HashMap <String, String> ();
          	ManageForm mf = new ManageForm();
    		ArrayList<ServerFormDetails> toDownload = new ArrayList<> ();
    		
    		// Create an array of ODK form details
        	for(FormLocator form : forms) {
        		String formVersionString = String.valueOf(form.version);
        		ManageFormDetails mfd = mf.getFormDetails(form.ident, formVersionString, source);    // Get the form details
                Timber.i("+++ Form: " + form.ident + ":" + formVersionString);
        		if(!mfd.exists || form.dirty) {
                    Timber.i("+++ Form does not exist or is dirty: " + form.ident + ":" + formVersionString +
                            " dirty: " + form.dirty);
        			form.url = serverUrl + "/formXML?key=" + form.ident;	// Set the form url from the server address and form ident
        			if(form.hasManifest) {
        				form.manifestUrl = serverUrl + "/xformsManifest?key=" + form.ident;
        			}

                    ServerFormDetails fd = new ServerFormDetails(form.name, form.url, form.manifestUrl, form.ident, formVersionString,
                            null,               // form hash
                            null,      // manifest hash
                            !mfd.exists,        // New form version available
                            form.hasManifest,   // Are newer media files available
                            !mfd.exists,
                            form.tasks_only,
                            mfd.formPath,
                            form.project);
        			toDownload.add(fd);
        		} else {
                    // Update form details
                    mf.updateFormDetails(mfd.id, form.name, form.tasks_only);
                }

        		// Store a hashmap of new forms so we can delete existing forms not in the list
        		String entryHash = form.ident + "_v_" + form.version;
        		formMap.put(entryHash, entryHash);
        	}

            // Delete any forms no longer required
            Timber.i("=================================  delete forms");
            mf.deleteForms(formMap, results);

            Timber.i("Downloading " + toDownload.size() + " forms");
            if(toDownload.size() > 0) {
                DownloadFormsTask downloadFormsTask = new DownloadFormsTask(multiFormDownloader);
                publishProgress(Collect.getInstance().getString(R.string.smap_downloading, toDownload.size()));

                downloadFormsTask.setDownloaderListener((DownloadFormsTaskListener) mStateListener);
                dfResults = downloadFormsTask.doInBackground(toDownload);   // Not in background as called directly
            }

        	processSharedFiles();   // Remove shared files no longer used, load shared sql files
    	}
    	
    	return dfResults;
	}

    /*
     * Remove any shared media files from the current organisation that are not referenced by a form
     */
    private void processSharedFiles() {

        HashMap<String, String> referencedFiles = new HashMap<> ();

        formsDao = new FormsDao();
        String tag = "Process Shared Files";

        File orgMediaDir = new File(Utilities.getOrgMediaPath());
        Log.i(tag,"====================== Check shared files");
        if (orgMediaDir.exists() && orgMediaDir.isDirectory()) {

            //publishProgress(Collect.getInstance().getString(R.string.smap_downloading, sharedMedia.size())); TODO

            // 1. Get the list of shared files
            File[] sharedFiles = orgMediaDir.listFiles();
            for(File sf : sharedFiles) {
                Log.i(tag, "Shared File: " + sf.getAbsolutePath());
            }

            // 2. Get the files used by this organisation
            if(sharedFiles.length > 0) {
                List<UriFile> uriToUpdate = new ArrayList<UriFile>();
                Cursor cursor = null;
                try {
                    cursor = formsDao.getFormsCursor();
                    if (cursor == null) {
                        publishProgress("Internal Error: Unable to access Forms content provider\r\n");
                        return;
                    }

                    cursor.moveToPosition(-1);

                    while (cursor.moveToNext()) {
                        // Get the media files for each form
                        String f = cursor.getString(
                                cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
                        if (f != null) {
                            int idx = f.lastIndexOf('.');
                            if (idx >= 0) {
                                String mPath = f.substring(0, idx) + "-media";
                                File mDir = new File(mPath);
                                Log.i(tag, "Media Dir is: " + mPath);
                                if (mDir.exists() && mDir.isDirectory()) {
                                    File[] mFiles = mDir.listFiles();
                                    for (File mf : mFiles) {
                                        Log.i(tag, "Adding reference file: " + mf.getName());
                                        referencedFiles.put(mf.getName(), mf.getName());
                                    }
                                }
                            }
                        }
                    }

                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                // 3. Remove shared files that are not referenced
                for(File sf : sharedFiles) {
                    if(referencedFiles.get(sf.getName()) == null) {
                        Log.i(tag, "Deleting shared file: " + sf.getName());
                        sf.delete();
                    } else {
                        Log.i(tag, "Retaining shared file: " + sf.getName());
                    }
                }
            }
        }
    }

    private static class UriFile {
        public final Uri uri;
        public final File file;

        UriFile(Uri uri, File file) {
            this.uri = uri;
            this.file = file;
        }
    }
    
}
