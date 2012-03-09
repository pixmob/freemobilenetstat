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
     * Is this application running in debug mode?
     */
    public static final boolean DEBUG = true;
    
    /**
     * Global {@link SharedPreferences} name.
     */
    public static final String SP_NAME = "netstat";
    /**
     * {@link SharedPreferences} key: do not display a notification when the
     * mobile operator is Free Mobile.
     */
    public static final String SP_KEY_SKIP_FREE_MOBILE_NOTIF = "skipFreeMobileNotif";
    /**
     * {@link SharedPreferences} key: do not display a notification when the
     * mobile operator is Orange.
     */
    public static final String SP_KEY_SKIP_ORANGE_NOTIF = "skipOrangeNotif";
    
    private Constants() {
    }
}
