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

import static org.pixmob.freemobile.netstat.Constants.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.TAG;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

/**
 * Monitor phone screen.
 * @author Pixmob
 */
public class ScreenMonitor extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        final BroadcastReceiver screenListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final boolean screenOn;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    if (DEBUG) {
                        Log.d(TAG, "Screen is off");
                    }
                    screenOn = false;
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "Screen is on");
                    }
                    screenOn = true;
                }
                
                final Context c = context.getApplicationContext();
                c.startService(new Intent(c, BatteryOptimizer.class).putExtra(
                    BatteryOptimizer.EXTRA_SCREEN_ON, screenOn));
            }
        };
        
        Log.i(TAG, "Listening for screen state updates");
        
        final IntentFilter screenListenerFilter = new IntentFilter();
        screenListenerFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenListenerFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenListener, screenListenerFilter);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) {
            Log.d(TAG, "Screen monitoring service destroyed");
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
