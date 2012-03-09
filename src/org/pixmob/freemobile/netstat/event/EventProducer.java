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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This class listens for phone state update and produces {@link Event}
 * instances.
 * @author Pixmob
 */
public class EventProducer extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Event e = Event.getCurrent(context);
        if (e != null) {
            if (DEBUG) {
                Log.d(TAG, "Phone state updated: enabled=" + e.mobileEnabled
                        + ", operator=" + e.mobileOperator);
            }
            
            // Start a background service for writing the event.
            final Context c = context.getApplicationContext();
            final Intent i = new Intent(c, EventWriter.class);
            i.putExtra(EventWriter.EXTRA_EVENT, e);
            c.startService(i);
        }
    }
}
