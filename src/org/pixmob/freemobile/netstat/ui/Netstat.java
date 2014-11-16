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

import static org.pixmob.freemobile.netstat.Constants.SP_KEY_STAT_NOTIF_SOUND;
import org.pixmob.freemobile.netstat.MonitorService;
import org.pixmob.freemobile.netstat.SyncService;
import org.pixmob.freemobile.netstat.feature.Features;
import org.pixmob.freemobile.netstat.feature.SharedPreferencesSaverFeature;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.Window;

/**
 * Main application activity.
 * @author Pixmob
 */
@SuppressLint("CommitPrefEdits")
public class Netstat extends FragmentActivity {
	private StatisticsFragment statisticsFragment;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        if (getSupportFragmentManager().findFragmentById(android.R.id.content) == null) {
        	statisticsFragment = new StatisticsFragment();
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, statisticsFragment).commit();
        }
        else statisticsFragment = (StatisticsFragment)getSupportFragmentManager().findFragmentById(android.R.id.content);

        final Context c = getApplicationContext();
        final Intent i = new Intent(c, MonitorService.class);
        c.startService(i);

        SyncService.schedule(this, true);

        final int applicationVersion;
        try {
            applicationVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            // Unlikely to happen.
            throw new RuntimeException("Failed to get application version", e);
        }

        final String versionKey = "version";
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        final int lastKnownVersion = prefs.getInt(versionKey, 0);
        if (lastKnownVersion != applicationVersion) {
            // Store the current application version.
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putInt(versionKey, applicationVersion);
            prefsEditor.putString(SP_KEY_STAT_NOTIF_SOUND, null); // FIXME > We should delete this in next releases.
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);

            // The application was updated: let's show changelog.
            startActivity(new Intent(this, DocumentBrowser.class).putExtra(DocumentBrowser.INTENT_EXTRA_URL,
                    "CHANGELOG.html"));
        }
    }
    
    public void enlargeChart(View view) {
    	Intent intent = new Intent(this, MobileNetworkChartActivity.class);
    	startActivity(intent);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Enable "better" gradients:
        // http://stackoverflow.com/a/2932030/422906
        final Window window = getWindow();
        window.setFormat(PixelFormat.RGBA_8888);
        window.getDecorView().getBackground().setDither(true);
    }
}
