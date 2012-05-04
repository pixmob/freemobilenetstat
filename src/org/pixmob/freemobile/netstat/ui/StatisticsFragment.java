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
import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.freemobile.netstat.Event;
import org.pixmob.freemobile.netstat.MobileOperator;
import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.content.NetstatContract.Events;
import org.pixmob.freemobile.netstat.ui.StatisticsFragment.Statistics;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
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
import android.util.Log;
import android.view.LayoutInflater;
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
    private ContentObserver contentMonitor;
    private View statisticsGroup;
    private ProgressBar progressBar;
    private MobileNetworkChart mobileNetworkChart;
    private BatteryChart batteryChart;
    private TextView onFreeMobileNetwork;
    private TextView onOrangeNetwork;
    private TextView statMobileNetwork;
    private TextView statMobileCode;
    private TextView statStartedSince;
    private TextView statScreenOn;
    private TextView statWifiOn;
    private TextView statOnOrange;
    private TextView statOnFreeMobile;
    private TextView statBattery;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
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
        statStartedSince = (TextView) a.findViewById(R.id.stat_started_since);
        statScreenOn = (TextView) a.findViewById(R.id.stat_screen);
        statWifiOn = (TextView) a.findViewById(R.id.stat_wifi);
        statOnOrange = (TextView) a.findViewById(R.id.stat_on_orange);
        statOnFreeMobile = (TextView) a.findViewById(R.id.stat_on_free_mobile);
        statBattery = (TextView) a.findViewById(R.id.stat_battery);
        
        // The fields are hidden the first time this fragment is displayed,
        // while statistics data are being loaded.
        statisticsGroup.setVisibility(View.INVISIBLE);
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
        setDurationText(statStartedSince, s.bootTime);
        setDurationText(statScreenOn, s.screenOnTime);
        setDurationText(statWifiOn, s.wifiOnTime);
        setDurationText(statOnOrange, s.orangeTime);
        setDurationText(statOnFreeMobile, s.freeMobileTime);
        
        statBattery.setText(s.battery == 0 ? STAT_NO_VALUE : (String
                .valueOf(s.battery) + "%"));
        
        batteryChart.setData(s.events);
        
        progressBar.setVisibility(View.INVISIBLE);
        statisticsGroup.setVisibility(View.VISIBLE);
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
            final long start = System.currentTimeMillis();
            if (DEBUG) {
                Log.d(TAG, "Loading statistics from ContentProvider");
            }
            
            final long now = System.currentTimeMillis();
            final long deviceBootTimestamp = now
                    - SystemClock.elapsedRealtime();
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
                            Events.MOBILE_OPERATOR, Events.BATTERY_LEVEL },
                    Events.TIMESTAMP + ">?",
                    new String[] { String.valueOf(deviceBootTimestamp) },
                    Events.TIMESTAMP + " ASC");
                final int rowCount = c.getCount();
                s.events = new Event[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    final Event e = new Event();
                    e.read(c);
                    s.events[i] = e;
                    
                    if (i > 0) {
                        final Event e0 = s.events[i - 1];
                        final long dt = e.timestamp - e0.timestamp;
                        
                        if (e.mobileConnected && e0.mobileConnected) {
                            final MobileOperator op = MobileOperator
                                    .fromString(e.mobileOperator);
                            final MobileOperator op0 = MobileOperator
                                    .fromString(e0.mobileOperator);
                            if (op != null && op.equals(op0)) {
                                if (MobileOperator.ORANGE.equals(op)) {
                                    s.orangeTime += dt;
                                } else if (MobileOperator.FREE_MOBILE
                                        .equals(op)) {
                                    s.freeMobileTime += dt;
                                }
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
                s.bootTime = now - deviceBootTimestamp;
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
                Log.d(TAG, "Statistics loaded in " + (end - start) + " ms");
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
        public long bootTime;
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
