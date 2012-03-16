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
package org.pixmob.freemobile.netstat.ui;

import org.pixmob.freemobile.netstat.monitor.MonitorService;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;

/**
 * Main application activity.
 * @author Pixmob
 */
public class Netstat extends SherlockFragmentActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportFragmentManager().findFragmentById(android.R.id.content) == null) {
            final BatteryLevelChartFragment f = new BatteryLevelChartFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, f).commit();
        }
        
        final Context c = getApplicationContext();
        final Intent i = new Intent(c, MonitorService.class);
        c.startService(i);
    }
}
