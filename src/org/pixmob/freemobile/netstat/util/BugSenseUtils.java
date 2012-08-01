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
package org.pixmob.freemobile.netstat.util;

import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.Context;
import android.util.Log;

import com.bugsense.trace.BugSenseHandler;

/**
 * BugSense (error reporting) utilities.
 * @author Pixmob
 */
public final class BugSenseUtils {
    private BugSenseUtils() {
    }

    /**
     * Setup the BugSense framework for reporting errors in the application. The
     * API key is loaded from application assets. If the key is not found, a
     * warning message is logged.
     */
    public static void setup(Context context) {
        String apiKey = null;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(context.getAssets().open("bugsense.txt")));

            for (String line; (line = reader.readLine()) != null;) {
                line = line.trim();
                if (!line.startsWith("#") && line.length() != 0) {
                    apiKey = line;
                    break;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load BugSense API key", e);
        } finally {
            IOUtils.close(reader);
        }

        if (apiKey != null) {
            Log.i(TAG, "BugSense (error reporting) enabled");
            BugSenseHandler.setup(context, apiKey);
        } else {
            Log.w(TAG, "BugSense (error reporting) is DISABLED");
        }
    }
}