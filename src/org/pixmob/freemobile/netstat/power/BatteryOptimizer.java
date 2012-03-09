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
package org.pixmob.freemobile.netstat.power;

import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.actionservice.ActionExecutionFailedException;
import org.pixmob.actionservice.ActionService;
import org.pixmob.freemobile.netstat.event.Event;
import org.pixmob.freemobile.netstat.event.EventProducer;
import org.pixmob.freemobile.netstat.event.EventWriter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Background service for optimizing battery usage.
 * @author Pixmob
 */
public class BatteryOptimizer extends ActionService {
    public static final String EXTRA_SCREEN_ON = "screenOn";
    private static final String EXTRA_PERIODIC = "periodic";
    private AlarmManager am;
    private ComponentName eventProducerComp;
    private PendingIntent periodicScanIntent;
    
    public BatteryOptimizer() {
        super("FreeMobileNetstat/BatteryOptimizer", 1000 * 10, 2);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(ALARM_SERVICE);
        
        final Context c = getApplicationContext();
        eventProducerComp = new ComponentName(c, EventProducer.class);
        periodicScanIntent = PendingIntent.getService(c, 0, new Intent(c,
                BatteryOptimizer.class).putExtra(EXTRA_PERIODIC, true),
            PendingIntent.FLAG_CANCEL_CURRENT);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        am = null;
        eventProducerComp = null;
        periodicScanIntent = null;
    }
    
    @Override
    protected void onActionError(Intent intent, Exception error) {
        Log.e(TAG, "Failed to handle battery usage", error);
    }
    
    @Override
    protected void onHandleAction(Intent intent)
            throws ActionExecutionFailedException, InterruptedException {
        // EventProducer is disabled if the screen is off, in order to
        // optimize battery usage: this broadcast receiver will not receive
        // network state updates.
        // When the screen is off, a periodic alarm is scheduled. This alarm
        // will monitor network state updates, as long as EventProducer is
        // disabled.
        // WHen the screen is on, this alarm is not scheduled anymore, and
        // EventProducer is enabled again.
        
        final boolean screenOn = intent.getBooleanExtra(EXTRA_SCREEN_ON, false);
        if (screenOn) {
            // The user is actually using the phone: EventProducer is enabled.
            enablePassiveScan();
        } else if (intent.getBooleanExtra(EXTRA_PERIODIC, false)) {
            // The periodic alarm was triggered.
            doPeriodicScan();
        } else {
            // The screen is off: EventProducer is disabled.
            disablePassiveScan();
        }
    }
    
    private void enablePassiveScan() {
        getPackageManager().setComponentEnabledSetting(eventProducerComp,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
        am.cancel(periodicScanIntent);
        
        Log.i(TAG, "Passive scan enabled");
    }
    
    private void disablePassiveScan() {
        getPackageManager().setComponentEnabledSetting(eventProducerComp,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
        
        final long alarmPeriod = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(), alarmPeriod, periodicScanIntent);
        
        Log.i(TAG, "Passive scan disabled");
    }
    
    private void doPeriodicScan() {
        Log.i(TAG, "Peridioc alarm triggered");
        
        final Event e = Event.getCurrent(this);
        if (e != null) {
            final Intent i = new Intent(getApplicationContext(),
                    EventWriter.class);
            i.putExtra(EventWriter.EXTRA_EVENT, e);
            getApplicationContext().startService(i);
        }
    }
}
