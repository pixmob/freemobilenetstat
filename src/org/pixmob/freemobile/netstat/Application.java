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
package org.pixmob.freemobile.netstat;

import static org.pixmob.freemobile.netstat.BuildConfig.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_ENABLE_AT_BOOT;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_STAT_NOTIF_ICON_GRAY;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.freemobile.netstat.feature.Features;
import org.pixmob.freemobile.netstat.feature.SharedPreferencesSaverFeature;
import org.pixmob.freemobile.netstat.feature.StrictModeFeature;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * Global application state.
 * @author Pixmob
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        if (DEBUG) {
            // StrictMode is a developer only feature.
            Log.i(TAG, "Enabling StrictMode settings");
            Features.getFeature(StrictModeFeature.class).enable();
        }
        
        // Set default values for preferences.
        final SharedPreferences prefs = getSharedPreferences(SP_NAME,
            MODE_PRIVATE);
        if (prefs.getAll().isEmpty()) {
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putBoolean(SP_KEY_ENABLE_AT_BOOT, true);
            prefsEditor.putBoolean(SP_KEY_STAT_NOTIF_ICON_GRAY, false);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(
                prefsEditor);
        }
    }
}
