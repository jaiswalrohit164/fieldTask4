/*
 * Copyright (C) 2017 Smap Consulting Pty Ltd
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

package org.odk.collect.android.services;

import android.app.NotificationManager;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import com.google.android.gms.gcm.GcmListenerService;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.NotificationActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.tasks.DownloadTasksTask;
import org.odk.collect.android.utilities.NotificationUtils;

import timber.log.Timber;

/**
 * Created by neilpenman on 27/07/2017.
 */

/*
 * Respond to a notification from the server
 */
public class NotificationService extends GcmListenerService {

    @Override
    public void onDeletedMessages() {
        // TODO refresh
    }

    @Override
    public void onMessageReceived(final String from, Bundle data) {
        Timber.i("Message received beginning refresh");

        // make sure sd card is ready, if not don't try to send
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return;
        }

        Uri uri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        ConnectivityManager manager = (ConnectivityManager) Collect.getInstance().getBaseContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetworkInfo = manager.getActiveNetworkInfo();

        boolean automaticNofification = false;
        if (currentNetworkInfo != null
                && currentNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
            if (isFormAutoSendOptionEnabled(currentNetworkInfo)) {
                // Refresh
                DownloadTasksTask dt = new DownloadTasksTask();
                dt.doInBackground();

                automaticNofification = true;
            }
        }

        if (!automaticNofification) {
            NotificationUtils.showNotification(null,
                    NotificationActivity.NOTIFICATION_ID,
                    R.string.app_name,
                    getString(R.string.smap_server_changed), false);    // Add start
        }

    }

    private boolean isFormAutoSendOptionEnabled(NetworkInfo currentNetworkInfo) {
        // make sure autosend is enabled on the given connected interface
        String autosend = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_AUTOSEND);
        boolean sendwifi = autosend.equals("wifi_only");
        boolean sendnetwork = autosend.equals("cellular_only");
        if (autosend.equals("wifi_and_cellular")) {
            sendwifi = true;
            sendnetwork = true;
        }

        return (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
                && sendwifi || currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE
                && sendnetwork);
    }
}
