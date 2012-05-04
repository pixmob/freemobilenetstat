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
package org.pixmob.freemobile.netstat.feature;

import java.util.HashMap;
import java.util.Map;

import android.os.Build;

/**
 * The feature helper enables backward compatibility for every Android devices,
 * from API level 8 to the latest available.
 * @author Pixmob
 */
public final class Features {
    private static final Map<Class<?>, Object> FEATURES = new HashMap<Class<?>, Object>(
            4);
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            FEATURES.put(StrictModeFeature.class,
                new HoneycombStrictModeFeature());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            FEATURES.put(StrictModeFeature.class,
                new GingerbreadStrictModeFeature());
        } else {
            FEATURES.put(StrictModeFeature.class, new LegacyStrictModeFeature());
        }
    }
    
    private Features() {
    }
    
    /**
     * Get a feature compatible with the current device.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFeature(Class<T> featureClass) {
        final T feature = (T) FEATURES.get(featureClass);
        if (feature == null) {
            throw new IllegalArgumentException("Unsupported feature: "
                    + featureClass.getName());
        }
        return feature;
    }
}
