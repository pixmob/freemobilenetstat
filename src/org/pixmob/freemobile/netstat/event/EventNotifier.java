/*
 * Copyright (C) 2012 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.freemobile.netstat.event;

import static org.pixmob.freemobile.netstat.Constants.SP_KEY_SKIP_FREE_MOBILE_NOTIF;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_SKIP_ORANGE_NOTIF;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.util.HashMap;
import java.util.Map;

import org.pixmob.actionservice.ActionExecutionFailedException;
import org.pixmob.actionservice.ActionService;
import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.provider.NetstatContract.Events;
import org.pixmob.freemobile.netstat.ui.Netstat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

/**
 * This service is responsible for notifying the user when an {@link Event} is
 * fired.
 * @author Pixmob
 */
public class EventNotifier extends ActionService {
    /**
     * Status bar strings for each mobile operator.
     */
    private static final Map<MobileOperator, Integer> STAT_MOB_OP_STRINGS = new HashMap<MobileOperator, Integer>(
            2);
    /**
     * Status bar icons for each mobile operator.
     */
    private static final Map<MobileOperator, Integer> STAT_MOB_OP_ICONS = new HashMap<MobileOperator, Integer>(
            2);
    
    static {
        STAT_MOB_OP_STRINGS.put(MobileOperator.FREE_MOBILE,
            R.string.network_free_mobile);
        STAT_MOB_OP_STRINGS.put(MobileOperator.ORANGE, R.string.network_orange);
        
        STAT_MOB_OP_ICONS.put(MobileOperator.FREE_MOBILE,
            R.drawable.ic_stat_connected_to_free_mobile);
        STAT_MOB_OP_ICONS.put(MobileOperator.ORANGE,
            R.drawable.ic_stat_connected_to_orange);
    }
    
    private NotificationManager nm;
    private PendingIntent openActivityPendingIntent;
    
    public EventNotifier() {
        super("FreeMobileNetstat/EventNotifier", 1000 * 2, 2);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        final Intent openActivityIntent = new Intent(getApplicationContext(),
                Netstat.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openActivityPendingIntent = PendingIntent.getActivity(
            getApplicationContext(), 0, openActivityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        nm = null;
        openActivityPendingIntent = null;
    }
    
    @Override
    protected void onActionError(Intent intent, Exception error) {
        Log.e(TAG, "Failed to notify user of new events", error);
    }
    
    @Override
    protected void onHandleAction(Intent intent)
            throws ActionExecutionFailedException, InterruptedException {
        // Compare the current mobile operator with the previous one.
        final Cursor c = getContentResolver().query(Events.CONTENT_URI,
            new String[] { Events.MOBILE_ENABLED, Events.MOBILE_OPERATOR, },
            null, null, Events.TIMESTAMP + " DESC");
        try {
            if (c.moveToNext()) {
                final boolean currentMobileEnabled = c.getInt(0) == 1;
                final String currentMobileOp = c.getString(1);
                
                if (!currentMobileEnabled) {
                    // The phone is not connected to a mobile network.
                    fireDeviceDisconnected();
                } else if (!TextUtils.isEmpty(currentMobileOp)) {
                    // The phone is connected to a mobile network.
                    fireMobileOperatorNotification(currentMobileOp);
                }
            } else {
                // Database is empty: weird?
                Log.w(TAG, "Event database is empty");
            }
        } finally {
            c.close();
        }
    }
    
    private void fireDeviceDisconnected() {
        Log.i(TAG, "Phone is not connected to any mobile network");
        nm.cancel(R.string.stat_connected_to_mobile_network);
    }
    
    private boolean skipMobileOperatorNotification(MobileOperator mobileOperator) {
        final SharedPreferences prefs = getSharedPreferences(SP_NAME,
            MODE_PRIVATE);
        if (MobileOperator.FREE_MOBILE.equals(mobileOperator)
                && prefs.getBoolean(SP_KEY_SKIP_FREE_MOBILE_NOTIF, false)) {
            return true;
        }
        if (MobileOperator.ORANGE.equals(mobileOperator)
                && prefs.getBoolean(SP_KEY_SKIP_ORANGE_NOTIF, false)) {
            return true;
        }
        return false;
    }
    
    private void fireMobileOperatorNotification(String rawMobileOperator) {
        final MobileOperator mobileOperator = MobileOperator
                .fromString(rawMobileOperator);
        if (skipMobileOperatorNotification(mobileOperator)) {
            Log.d(TAG, "Skip notification for mobile network: "
                    + mobileOperator);
            return;
        }
        
        final String mobileNetwork = getString(STAT_MOB_OP_STRINGS
                .get(mobileOperator));
        final String title = String
                .format(getString(R.string.stat_connected_to_mobile_network),
                    mobileNetwork);
        
        Log.i(TAG, "Phone is connected to the mobile network: "
                + mobileOperator);
        
        final String content = getString(R.string.tap_to_open_stats);
        
        final Notification n = new Notification(
                STAT_MOB_OP_ICONS.get(mobileOperator), title, 0);
        n.setLatestEventInfo(this, title, content, openActivityPendingIntent);
        n.flags = Notification.FLAG_ONGOING_EVENT;
        nm.notify(R.string.stat_connected_to_mobile_network, n);
    }
}
