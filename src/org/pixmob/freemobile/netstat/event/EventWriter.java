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

import static org.pixmob.freemobile.netstat.Constants.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.actionservice.ActionExecutionFailedException;
import org.pixmob.actionservice.ActionService;
import org.pixmob.freemobile.netstat.provider.NetstatContentProvider;
import org.pixmob.freemobile.netstat.provider.NetstatContract.Events;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

/**
 * Background service used for writing an {@link Event} instance to the
 * application database. Any received {@link Intent} with the extra value
 * {@link #EXTRA_EVENT} set to an {@link Event} instance will use the
 * {@link NetstatContentProvider} to insert data in the database.
 * @author Pixmob
 */
public class EventWriter extends ActionService {
    /**
     * {@link Intent} extra key for attaching an {@link Event} instance.
     */
    public static final String EXTRA_EVENT = "event";
    private Intent eventNotifierIntent;
    private String lastMobileOperator;
    private boolean lastMobileEnabled;
    
    public EventWriter() {
        super("FreeMobileNetstat/EventWriter", 1000 * 10, 2);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        eventNotifierIntent = new Intent(getApplicationContext(),
                EventNotifier.class);
        
        // Load previous state from the database.
        final Cursor c = getContentResolver().query(Events.CONTENT_URI,
            new String[] { Events.MOBILE_ENABLED, Events.MOBILE_OPERATOR, },
            null, null, Events.TIMESTAMP + " DESC");
        try {
            if (c.moveToNext()) {
                lastMobileEnabled = c.getInt(0) == 1;
                lastMobileOperator = c.getString(1);
            }
        } finally {
            c.close();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        eventNotifierIntent = null;
    }
    
    @Override
    protected void onActionError(Intent intent, Exception error) {
        Log.e(TAG, "Failed to write event", error);
    }
    
    @Override
    protected void onHandleAction(Intent intent)
            throws ActionExecutionFailedException, InterruptedException {
        // Get Event instance from Intent.
        final Event e = (Event) intent.getSerializableExtra(EXTRA_EVENT);
        if (e == null) {
            throw new ActionExecutionFailedException("Missing event in intent");
        }
        
        if (lastMobileEnabled == e.mobileEnabled) {
            if (lastMobileOperator == null && e.mobileOperator == null
                    || lastMobileOperator != null
                    && lastMobileOperator.equals(e.mobileOperator)) {
                if (DEBUG) {
                    Log.d(TAG, "Skip event write: " + e);
                }
                return;
            }
        }
        
        Log.i(TAG, "Writing event to the database: " + e);
        
        // Store this event in the database.
        final ContentValues cv = new ContentValues(5);
        cv.put(Events.TIMESTAMP, e.timestamp);
        cv.put(Events.MOBILE_ENABLED, Integer.valueOf(e.mobileEnabled ? 1 : 0));
        cv.put(Events.MOBILE_OPERATOR, e.mobileOperator);
        cv.put(Events.MOBILE_ROAMING, Integer.valueOf(e.mobileRoaming ? 1 : 0));
        cv.put(Events.SYNC_ID, e.syncId);
        cv.put(Events.SYNC_STATUS, e.syncStatus);
        getContentResolver().insert(Events.CONTENT_URI, cv);
        
        // Store current state.
        lastMobileEnabled = e.mobileEnabled;
        lastMobileOperator = e.mobileOperator;
        
        // Start a background service for notifying the user.
        getApplicationContext().startService(eventNotifierIntent);
    }
}
