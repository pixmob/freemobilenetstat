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

import static org.pixmob.freemobile.netstat.Constants.INTERVAL_ONE_MONTH;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_ONE_WEEK;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_SINCE_BOOT;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_TODAY;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_TIME_INTERVAL;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.util.IOUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * Application preferences screen.
 * @author Pixmob
 */
public class Preferences extends PreferenceActivity implements
        OnPreferenceClickListener, OnPreferenceChangeListener {
    private static final String SP_KEY_VERSION = "pref_version";
    private static final String SP_KEY_CHANGELOG = "pref_changelog";
    private static final String SP_KEY_LICENSE = "pref_license";
    private final SparseArray<CharSequence> timeIntervals = new SparseArray<CharSequence>(
            4);
    
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        timeIntervals.clear();
        timeIntervals.append(INTERVAL_SINCE_BOOT,
            getString(R.string.interval_since_boot));
        timeIntervals
                .append(INTERVAL_TODAY, getString(R.string.interval_today));
        timeIntervals.append(INTERVAL_ONE_WEEK,
            getString(R.string.interval_one_week));
        timeIntervals.append(INTERVAL_ONE_MONTH,
            getString(R.string.interval_one_month));
        
        final PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesMode(MODE_PRIVATE);
        pm.setSharedPreferencesName(SP_NAME);
        
        addPreferencesFromResource(R.xml.prefs);
        
        String version = "0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot get application version", e);
        }
        
        Preference p = findPreference(SP_KEY_VERSION);
        p.setTitle(String.format(getString(R.string.pref_version), version));
        
        findPreference(SP_KEY_CHANGELOG).setOnPreferenceClickListener(this);
        findPreference(SP_KEY_LICENSE).setOnPreferenceClickListener(this);
        
        final IntListPreference lp = (IntListPreference) findPreference(SP_KEY_TIME_INTERVAL);
        lp.setEntries(getValues(timeIntervals));
        lp.setEntryValues(getKeys(timeIntervals));
        
        final int currentInterval = pm.getSharedPreferences().getInt(
            SP_KEY_TIME_INTERVAL, 0);
        lp.setSummary(timeIntervals.get(currentInterval));
        lp.setValue(currentInterval);
        lp.setOnPreferenceChangeListener(this);
    }
    
    @Override
    public boolean onPreferenceChange(Preference p, Object value) {
        final IntListPreference lp = (IntListPreference) p;
        final int intValue = Integer.parseInt((String) value);
        lp.setSummary(timeIntervals.get(intValue));
        
        return true;
    }
    
    private static <T> int[] getKeys(SparseArray<T> a) {
        final int s = a.size();
        final int[] keys = new int[s];
        for (int i = 0; i < s; ++i) {
            keys[i] = a.keyAt(i);
        }
        return keys;
    }
    
    private static CharSequence[] getValues(SparseArray<CharSequence> a) {
        final int s = a.size();
        final CharSequence[] values = new CharSequence[s];
        for (int i = 0; i < s; ++i) {
            values[i] = a.get(i);
        }
        return values;
    }
    
    @Override
    public boolean onPreferenceClick(Preference p) {
        final String k = p.getKey();
        if (SP_KEY_CHANGELOG.equals(k)) {
            openBrowser(getString(R.string.url_changelog));
        } else if (SP_KEY_LICENSE.equals(k)) {
            showDialog(0);
        }
        
        return true;
    }
    
    private void openBrowser(String url) {
        final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(i);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        final StringBuilder licenseText = new StringBuilder(1024);
        loadAsset("NOTICE.txt", licenseText);
        licenseText.append('\n');
        loadAsset("LICENSE.txt", licenseText);
        
        final View licenseContent = LayoutInflater.from(this).inflate(
            R.layout.license, null);
        final TextView license = (TextView) licenseContent
                .findViewById(R.id.license);
        license.setText(licenseText);
        
        return new AlertDialog.Builder(this).setTitle(R.string.pref_license)
                .setView(licenseContent)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.ok, null).create();
    }
    
    private void loadAsset(String fileName, StringBuilder buf) {
        InputStream input = null;
        try {
            input = getResources().getAssets().open(fileName);
            
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input));
            for (String line; (line = reader.readLine()) != null;) {
                buf.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load asset " + fileName, e);
        } finally {
            IOUtils.close(input);
        }
    }
}
