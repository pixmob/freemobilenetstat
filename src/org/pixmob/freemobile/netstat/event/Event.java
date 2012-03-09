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

import java.io.Serializable;
import java.util.UUID;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

/**
 * This class contains phone state and service related information.
 * @author Pixmob
 */
public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique row identifier (when the instance exists in the database).
     */
    public int id;
    /**
     * Epoch-time for this event, in milliseconds.
     */
    public long timestamp;
    /**
     * The device is using a mobile connection.
     */
    public boolean mobileEnabled;
    /**
     * MCC+MNC operator identifier.
     */
    public String mobileOperator;
    /**
     * The device is using a mobile connection with roaming enabled.
     */
    public boolean mobileRoaming;
    /**
     * Synchronization identifier (unique).
     */
    public String syncId = UUID.randomUUID().toString();
    /**
     * Synchronization status.
     */
    public int syncStatus;
    
    @Override
    public String toString() {
        return "Event[timestamp=" + timestamp + ", mobileEnabled="
                + mobileEnabled + ", mobileOperator=" + mobileOperator
                + ", mobileRoaming=" + mobileRoaming + ", syncId=" + syncId
                + ", syncStatus=" + syncStatus + "]";
    }
    
    public static Event getCurrent(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo ni = cm
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (ni == null) {
            return null;
        }
        
        final TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        
        final Event e = new Event();
        e.timestamp = System.currentTimeMillis();
        e.mobileEnabled = ni.isConnected();
        e.mobileRoaming = tm.isNetworkRoaming();
        e.mobileOperator = tm.getNetworkOperator();
        if (TextUtils.isEmpty(e.mobileOperator)) {
            e.mobileOperator = null;
        }
        return e;
    }
}
