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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.pixmob.freemobile.netstat.feature.Features;
import org.pixmob.freemobile.netstat.feature.SharedPreferencesSaverFeature;
import org.pixmob.freemobile.netstat.feature.StrictModeFeature;

import java.util.HashMap;
import java.util.Map;

import static org.pixmob.freemobile.netstat.BuildConfig.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_SINCE_BOOT;
import static org.pixmob.freemobile.netstat.Constants.NOTIF_ACTION_STATISTICS;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_ENABLE_AT_BOOT;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_ENABLE_AUTO_RESTART_SERVICE;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_ENABLE_NOTIF_ACTIONS;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_NOTIF_ACTION;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_THEME;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_TIME_INTERVAL;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_UPLOAD_STATS;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;
import static org.pixmob.freemobile.netstat.Constants.THEME_DEFAULT;

/**
 * Global application state.
 * @author Pixmob
 * add ACRA setup : https://github.com/ACRA/acra/wiki/BasicSetup
 */
@ReportsCrashes(
        formUri = "https://freemobilenetstat.iriscouch.com/acra-freemobilenetstat/_design/acra-storage/_update/report",
        reportType = HttpSender.Type.JSON,
        httpMethod = HttpSender.Method.PUT,
        formUriBasicAuthLogin = "",
        formUriBasicAuthPassword = ""
)
public class Application extends android.app.Application {
    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate() {
        super.onCreate();

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
        ACRA.getErrorReporter().setEnabled(!DEBUG);

        if (DEBUG) {
            // StrictMode is a developer only feature.
            Log.i(TAG, "Enabling StrictMode settings");
            Features.getFeature(StrictModeFeature.class).enable();
        }

        final Map<String, Object> defaultValues = new HashMap<>();
        defaultValues.put(SP_KEY_ENABLE_AT_BOOT, true);
        defaultValues.put(SP_KEY_TIME_INTERVAL, INTERVAL_SINCE_BOOT);
        defaultValues.put(SP_KEY_UPLOAD_STATS, true);
        defaultValues.put(SP_KEY_NOTIF_ACTION, NOTIF_ACTION_STATISTICS);
        defaultValues.put(SP_KEY_ENABLE_NOTIF_ACTIONS, true);
        defaultValues.put(SP_KEY_THEME, THEME_DEFAULT);
        defaultValues.put(SP_KEY_ENABLE_AUTO_RESTART_SERVICE, true);

        // Set default values for preferences.
        final SharedPreferences prefs = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        for (final Map.Entry<String, Object> e : defaultValues.entrySet()) {
            final String key = e.getKey();
            final Object value = e.getValue();
            if (!prefs.contains(key)) {
                if (value instanceof Boolean) {
                    prefsEditor.putBoolean(key, (Boolean) value);
                }
                if (value instanceof String) {
                    prefsEditor.putString(key, (String) value);
                }
                if (value instanceof Integer) {
                    prefsEditor.putInt(key, (Integer) value);
                }
            }
        }
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
    }
}
