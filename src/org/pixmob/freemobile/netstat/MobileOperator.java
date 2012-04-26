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

import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.util.Log;

/**
 * Mobile operator list.
 * @author Pixmob
 */
public enum MobileOperator {
    FREE_MOBILE, ORANGE;
    
    private static final Set<String> FREE_MOBILE_IDENTIFIERS = new HashSet<String>(
            2);
    private static final Set<String> ORANGE_IDENTIFIERS = new HashSet<String>(3);
    
    static {
        // MCC+MNC identifier list:
        // http://en.wikipedia.org/wiki/Mobile_Network_Code
        FREE_MOBILE_IDENTIFIERS.add("20814");
        FREE_MOBILE_IDENTIFIERS.add("20815");
        ORANGE_IDENTIFIERS.add("20800");
        ORANGE_IDENTIFIERS.add("20801");
        ORANGE_IDENTIFIERS.add("20802");
    }
    
    /**
     * Get a {@link MobileOperator} instance from a MCC+MNC identifier.
     */
    public static MobileOperator fromString(String mccMnc) {
        if (mccMnc == null) {
            return null;
        }
        if (FREE_MOBILE_IDENTIFIERS.contains(mccMnc)) {
            return FREE_MOBILE;
        }
        if (ORANGE_IDENTIFIERS.contains(mccMnc)) {
            return ORANGE;
        }
        Log.d(TAG, "Unknown MCC+MNC: " + mccMnc);
        return null;
    }
    
    public String toName(Context context) {
        if (FREE_MOBILE.equals(this)) {
            return context.getString(R.string.network_free_mobile);
        }
        if (ORANGE.equals(this)) {
            return context.getString(R.string.network_orange);
        }
        return context.getString(R.string.network_unknown);
    }
}
