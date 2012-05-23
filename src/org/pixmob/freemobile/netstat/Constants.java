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

import android.content.SharedPreferences;

/**
 * Application constants.
 * @author Pixmob
 */
public final class Constants {
    /**
     * Logging tag.
     */
    public static final String TAG = "FreeMobileNetstat";
    
    /**
     * Global {@link SharedPreferences} name.
     */
    public static final String SP_NAME = "netstat";
    /**
     * {@link SharedPreferences} key: use a gray icon for the status bar
     * notification.
     */
    public static final String SP_KEY_STAT_NOTIF_ICON_GRAY = "pref_icon_in_gray";
    /**
     * {@link SharedPreferences} key: start the monitor service at boot.
     */
    public static final String SP_KEY_ENABLE_AT_BOOT = "pref_enable_at_boot";
    /**
     * {@link SharedPreferences} key: play a sound when the mobile operator is
     * updated.
     */
    public static final String SP_KEY_STAT_NOTIF_SOUND = "pref_notif_sound";
    /**
     * {@link SharedPreferences} key: set the time interval for displayed data.
     */
    public static final String SP_KEY_TIME_INTERVAL = "pref_time_interval";
    
    /**
     * Time interval value: show data since the device boot time.
     */
    public static final int INTERVAL_SINCE_BOOT = 0;
    /**
     * Time interval value: show data from today.
     */
    public static final int INTERVAL_TODAY = 1;
    /**
     * Time interval value: show data from one week.
     */
    public static final int INTERVAL_ONE_WEEK = 2;
    /**
     * Time interval value: show data from one month.
     */
    public static final int INTERVAL_ONE_MONTH = 3;
    
    private Constants() {
    }
}
