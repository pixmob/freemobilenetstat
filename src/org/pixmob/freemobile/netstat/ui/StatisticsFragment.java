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

import static org.pixmob.freemobile.netstat.BuildConfig.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_ONE_MONTH;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_ONE_WEEK;
import static org.pixmob.freemobile.netstat.Constants.INTERVAL_TODAY;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_TIME_INTERVAL;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.util.Calendar;
import java.util.Date;

import org.pixmob.freemobile.netstat.Event;
import org.pixmob.freemobile.netstat.MobileOperator;
import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.content.NetstatContract.Events;
import org.pixmob.freemobile.netstat.ui.StatisticsFragment.Statistics;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Fragment showing statistics using charts.
 * @author Pixmob
 */
public class StatisticsFragment extends Fragment implements
        LoaderCallbacks<Statistics> {
    private static final String STAT_NO_VALUE = "-";
    private static ExportTask exportTask;
    private ContentObserver contentMonitor;
    private View statisticsGroup;
    private ProgressBar progressBar;
    private MobileNetworkChart mobileNetworkChart;
    private BatteryChart batteryChart;
    private TextView onFreeMobileNetwork;
    private TextView onOrangeNetwork;
    private TextView statMobileNetwork;
    private TextView statMobileCode;
    private TextView statScreenOn;
    private TextView statWifiOn;
    private TextView statOnOrange;
    private TextView statOnFreeMobile;
    private TextView statBattery;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        if (!isSimSupported(getActivity())) {
            new UnsupportedSimDialogFragment().show(getFragmentManager(),
                "error");
        }
        
        if (exportTask != null) {
            exportTask.setFragmentManager(getFragmentManager());
        }
        
        // Monitor database updates: when new data is available, this fragment
        // is updated with the new values.
        contentMonitor = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                
                Log.i(TAG, "Content updated: refresh statistics");
                refresh();
            }
        };
        
        // Get widgets.
        final Activity a = getActivity();
        statisticsGroup = a.findViewById(R.id.statistics);
        statisticsGroup.setVisibility(View.INVISIBLE);
        progressBar = (ProgressBar) a.findViewById(R.id.states_progress);
        mobileNetworkChart = (MobileNetworkChart) a
                .findViewById(R.id.mobile_network_chart);
        batteryChart = (BatteryChart) a.findViewById(R.id.battery_chart);
        onOrangeNetwork = (TextView) a.findViewById(R.id.on_orange_network);
        onFreeMobileNetwork = (TextView) a
                .findViewById(R.id.on_free_mobile_network);
        statMobileNetwork = (TextView) a.findViewById(R.id.stat_mobile_network);
        statMobileCode = (TextView) a.findViewById(R.id.stat_mobile_code);
        statScreenOn = (TextView) a.findViewById(R.id.stat_screen);
        statWifiOn = (TextView) a.findViewById(R.id.stat_wifi);
        statOnOrange = (TextView) a.findViewById(R.id.stat_on_orange);
        statOnFreeMobile = (TextView) a.findViewById(R.id.stat_on_free_mobile);
        statBattery = (TextView) a.findViewById(R.id.stat_battery);
        
        // The fields are hidden the first time this fragment is displayed,
        // while statistics data are being loaded.
        statisticsGroup.setVisibility(View.INVISIBLE);
        
        setHasOptionsMenu(true);
    }
    
    /**
     * Check if the SIM card is supported.
     */
    @SuppressWarnings("unused")
    public static boolean isSimSupported(Context context) {
        if (DEBUG) {
            return true;
        }
        
        final TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        if (TelephonyManager.SIM_STATE_READY != tm.getSimState()) {
            return false;
        }
        return MobileOperator.FREE_MOBILE.equals(MobileOperator.fromString(tm
                .getSimOperator()));
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_statistics, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_export:
                onMenuExport();
                return true;
            case R.id.menu_preferences:
                onMenuPreferences();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void onMenuExport() {
        exportTask = new ExportTask(getActivity().getApplicationContext(),
                getFragmentManager());
        exportTask.execute();
    }
    
    private void onMenuPreferences() {
        startActivity(new Intent(getActivity(), Preferences.class));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Monitor database updates if the fragment is displayed.
        final ContentResolver cr = getActivity().getContentResolver();
        cr.registerContentObserver(Events.CONTENT_URI, true, contentMonitor);
        
        refresh();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop monitoring database updates.
        getActivity().getContentResolver().unregisterContentObserver(
            contentMonitor);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.statistics_fragment, container, false);
    }
    
    public void refresh() {
        getLoaderManager().restartLoader(0, null, this);
    }
    
    @Override
    public Loader<Statistics> onCreateLoader(int id, Bundle args) {
        progressBar.setVisibility(View.VISIBLE);
        
        return new StatisticsLoader(getActivity());
    }
    
    @Override
    public void onLoaderReset(Loader<Statistics> loader) {
    }
    
    @Override
    public void onLoadFinished(Loader<Statistics> loader, Statistics s) {
        Log.i(TAG, "Statistics loaded: " + s);
        
        onOrangeNetwork.setText(s.orangeUsePercent + "%");
        onFreeMobileNetwork.setText(s.freeMobileUsePercent + "%");
        mobileNetworkChart.setData(s.orangeUsePercent, s.freeMobileUsePercent);
        
        final Activity a = getActivity();
        statMobileNetwork.setText(s.mobileOperator == null ? STAT_NO_VALUE
                : s.mobileOperator.toName(a));
        statMobileCode.setText(s.mobileOperatorCode == null ? STAT_NO_VALUE
                : s.mobileOperatorCode);
        setDurationText(statScreenOn, s.screenOnTime);
        setDurationText(statWifiOn, s.wifiOnTime);
        setDurationText(statOnOrange, s.orangeTime);
        setDurationText(statOnFreeMobile, s.freeMobileTime);
        
        statBattery.setText(s.battery == 0 ? STAT_NO_VALUE : (String
                .valueOf(s.battery) + "%"));
        
        batteryChart.setData(s.events);
        
        progressBar.setVisibility(View.INVISIBLE);
        statisticsGroup.setVisibility(View.VISIBLE);
        statisticsGroup.invalidate();
    }
    
    private void setDurationText(TextView tv, long duration) {
        if (duration < 1) {
            tv.setText(STAT_NO_VALUE);
        } else {
            tv.setText(formatDuration(duration));
        }
    }
    
    /**
     * Return a formatted string for a duration value.
     */
    private CharSequence formatDuration(long duration) {
        if (duration == 0) {
            return STAT_NO_VALUE;
        }
        final long ds = duration / 1000;
        if (ds < 60) {
            return STAT_NO_VALUE;
        }
        
        final StringBuilder buf = new StringBuilder(32);
        if (ds < 3600) {
            final long m = ds / 60;
            buf.append(m).append(getString(R.string.minutes));
        } else if (ds < 86400) {
            final long h = ds / 3600;
            buf.append(h).append(getString(R.string.hours));
            
            final long m = (ds - h * 3600) / 60;
            if (m != 0) {
                if (m < 10) {
                    buf.append("0");
                }
                buf.append(m);
            }
        } else {
            final long d = ds / 86400;
            buf.append(d).append(getString(R.string.days));
            
            final long h = (ds - d * 86400) / 3600;
            if (h != 0) {
                buf.append(" ").append(h).append(getString(R.string.hours));
            }
            
            final long m = (ds - d * 86400 - h * 3600) / 60;
            if (m != 0) {
                if (h == 0) {
                    buf.append(" ");
                } else if (m < 10) {
                    buf.append("0");
                }
                buf.append(m);
                if (h == 0) {
                    buf.append(getString(R.string.minutes));
                }
            }
        }
        
        return buf;
    }
    
    /**
     * {@link Loader} implementation for loading events from the database, and
     * computing statistics.
     * @author Pixmob
     */
    private static class StatisticsLoader extends AsyncTaskLoader<Statistics> {
        public StatisticsLoader(final Context context) {
            super(context);
        }
        
        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }
        
        @Override
        public Statistics loadInBackground() {
            final long now = System.currentTimeMillis();
            
            final SharedPreferences prefs = getContext().getSharedPreferences(
                SP_NAME, Context.MODE_PRIVATE);
            final int interval = prefs.getInt(SP_KEY_TIME_INTERVAL, 0);
            final long fromTimestamp;
            if (interval == INTERVAL_ONE_MONTH) {
                final Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                cal.add(Calendar.MONTH, -1);
                fromTimestamp = cal.getTimeInMillis();
            } else if (interval == INTERVAL_ONE_WEEK) {
                final Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                cal.add(Calendar.DATE, -7);
                fromTimestamp = cal.getTimeInMillis();
            } else if (interval == INTERVAL_TODAY) {
                // Get the date at midnight today.
                final Time t = new Time();
                t.set(now);
                t.hour = 0;
                t.minute = 0;
                t.second = 0;
                fromTimestamp = t.toMillis(false);
            } else {
                fromTimestamp = now - SystemClock.elapsedRealtime();
            }
            
            Log.i(TAG, "Loading statistics from " + new Date(fromTimestamp)
                    + " to now");
            
            final Statistics s = new Statistics();
            
            final TelephonyManager tm = (TelephonyManager) getContext()
                    .getSystemService(Context.TELEPHONY_SERVICE);
            s.mobileOperatorCode = tm.getNetworkOperator();
            s.mobileOperator = MobileOperator.fromString(s.mobileOperatorCode);
            if (s.mobileOperator == null) {
                s.mobileOperatorCode = null;
            }
            
            long connectionTimestamp = 0;
            
            Cursor c = null;
            try {
                c = getContext().getContentResolver().query(
                    Events.CONTENT_URI,
                    new String[] { Events.TIMESTAMP, Events.SCREEN_ON,
                            Events.WIFI_CONNECTED, Events.MOBILE_CONNECTED,
                            Events.MOBILE_OPERATOR, Events.BATTERY_LEVEL,
                            Events.POWER_ON }, Events.TIMESTAMP + ">?",
                    new String[] { String.valueOf(fromTimestamp) },
                    Events.TIMESTAMP + " ASC");
                final int rowCount = c.getCount();
                s.events = new Event[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    final Event e = new Event();
                    e.read(c);
                    s.events[i] = e;
                    
                    if (i > 0) {
                        final Event e0 = s.events[i - 1];
                        if (e.powerOn && !e0.powerOn) {
                            continue;
                        }
                        final long dt = e.timestamp - e0.timestamp;
                        
                        final MobileOperator op = MobileOperator
                                .fromString(e.mobileOperator);
                        final MobileOperator op0 = MobileOperator
                                .fromString(e0.mobileOperator);
                        if (op != null && op.equals(op0)) {
                            if (MobileOperator.ORANGE.equals(op)) {
                                s.orangeTime += dt;
                            } else if (MobileOperator.FREE_MOBILE.equals(op)) {
                                s.freeMobileTime += dt;
                            }
                        }
                        if (e.mobileConnected && !e0.mobileConnected) {
                            connectionTimestamp = e.timestamp;
                        }
                        if (!e.mobileConnected) {
                            connectionTimestamp = 0;
                        }
                        if (e.wifiConnected && e0.wifiConnected) {
                            s.wifiOnTime += dt;
                        }
                        if (e.screenOn && e0.screenOn) {
                            s.screenOnTime += dt;
                        }
                    }
                }
                
                if (s.events.length > 0) {
                    s.battery = s.events[s.events.length - 1].batteryLevel;
                }
                
                final double sTime = (double) (s.orangeTime + s.freeMobileTime);
                s.freeMobileUsePercent = (int) Math.round(s.freeMobileTime
                        / sTime * 100d);
                s.orangeUsePercent = 100 - s.freeMobileUsePercent;
                s.connectionTime = now - connectionTimestamp;
            } catch (Exception e) {
                Log.e(TAG, "Failed to load statistics", e);
                s.events = new Event[0];
            } finally {
                try {
                    if (c != null) {
                        c.close();
                    }
                } catch (Exception ignore) {
                }
            }
            
            if (DEBUG) {
                final long end = System.currentTimeMillis();
                Log.d(TAG, "Statistics loaded in " + (end - now) + " ms");
            }
            
            return s;
        }
    }
    
    /**
     * Store statistics.
     * @author Pixmob
     */
    public static class Statistics {
        public Event[] events = new Event[0];
        public long orangeTime;
        public long freeMobileTime;
        public int orangeUsePercent;
        public int freeMobileUsePercent;
        public MobileOperator mobileOperator;
        public String mobileOperatorCode;
        public long connectionTime;
        public long screenOnTime;
        public long wifiOnTime;
        public int battery;
        
        @Override
        public String toString() {
            return "Statistics[events=" + events.length + "; orange="
                    + orangeUsePercent + "%; free=" + freeMobileUsePercent
                    + "%]";
        }
    }
}
