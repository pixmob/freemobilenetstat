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

import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.monitor.MobileOperator;
import org.pixmob.freemobile.netstat.provider.NetstatContract.BatteryEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.PhoneEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.ScreenEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.WifiEvents;
import org.pixmob.freemobile.netstat.ui.StatisticsFragment.StatisticsData;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.actionbarsherlock.app.SherlockFragment;

/**
 * Fragment showing statistics using charts.
 * @author Pixmob
 */
public class StatisticsFragment extends SherlockFragment implements
        LoaderCallbacks<StatisticsData> {
    private ContentObserver contentMonitor;
    private BatteryLevelChart batteryChart;
    private StateChart orangeNetworkChart;
    private StateChart wifiChart;
    private StateChart screenChart;
    private View statisticsGroup;
    private ProgressBar progressBar;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        contentMonitor = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                
                Log.i(TAG, "Content updated: refresh statistics");
                refresh();
            }
        };
        
        final Activity a = getActivity();
        batteryChart = (BatteryLevelChart) a.findViewById(R.id.battery_levels);
        orangeNetworkChart = (StateChart) a
                .findViewById(R.id.orange_network_states);
        wifiChart = (StateChart) a.findViewById(R.id.wifi_states);
        screenChart = (StateChart) a.findViewById(R.id.screen_states);
        statisticsGroup = a.findViewById(R.id.statistics);
        progressBar = (ProgressBar) a.findViewById(R.id.states_progress);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        final ContentResolver cr = getActivity().getContentResolver();
        cr.registerContentObserver(PhoneEvents.CONTENT_URI, true,
            contentMonitor);
        cr.registerContentObserver(BatteryEvents.CONTENT_URI, true,
            contentMonitor);
        cr.registerContentObserver(WifiEvents.CONTENT_URI, true, contentMonitor);
        cr.registerContentObserver(ScreenEvents.CONTENT_URI, true,
            contentMonitor);
        
        refresh();
    }
    
    @Override
    public void onPause() {
        super.onPause();
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
    public Loader<StatisticsData> onCreateLoader(int id, Bundle args) {
        progressBar.setVisibility(View.VISIBLE);
        statisticsGroup.setVisibility(View.INVISIBLE);
        
        return new StatisticsLoader(getActivity());
    }
    
    @Override
    public void onLoaderReset(Loader<StatisticsData> loader) {
    }
    
    @Override
    public void onLoadFinished(Loader<StatisticsData> loader, StatisticsData d) {
        if (d == null) {
            batteryChart.setData(null, null);
            orangeNetworkChart.setData(null, null);
            wifiChart.setData(null, null);
            screenChart.setData(null, null);
        } else {
            batteryChart.setData(d.batteryTimestamps, d.batteryLevels);
            orangeNetworkChart.setData(d.orangeNetworkTimestamps,
                d.orangeNetworkStates);
            wifiChart.setData(d.wifiTimestamps, d.wifiStates);
            screenChart.setData(d.screenTimestamps, d.screenStates);
        }
        
        progressBar.setVisibility(View.INVISIBLE);
        statisticsGroup.setVisibility(View.VISIBLE);
        
        batteryChart.invalidate();
        wifiChart.invalidate();
        screenChart.invalidate();
    }
    
    /**
     * {@link Loader} implementation for loading statistics from the database.
     * @author Pixmob
     */
    private static class StatisticsLoader extends
            AsyncTaskLoader<StatisticsData> {
        public StatisticsLoader(final Context context) {
            super(context);
        }
        
        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }
        
        @Override
        public StatisticsData loadInBackground() {
            int[] batteryLevels = null;
            long[] batteryTimestamps = null;
            boolean[] freeMobileStates;
            long[] freeMobileTimestamps;
            boolean[] wifiStates = null;
            long[] wifiTimestamps = null;
            boolean[] screenStates = null;
            long[] screenTimestamps = null;
            
            final long start = System.currentTimeMillis();
            if (DEBUG) {
                Log.d(TAG, "Loading statistics from ContentProvider");
            }
            
            final long internalStartTimestamp = System.currentTimeMillis()
                    - SystemClock.elapsedRealtime();
            
            Cursor c = null;
            try {
                c = getContext().getContentResolver()
                        .query(
                            BatteryEvents.CONTENT_URI,
                            new String[] { BatteryEvents.TIMESTAMP,
                                    BatteryEvents.LEVEL },
                            BatteryEvents.TIMESTAMP + ">?",
                            new String[] { String
                                    .valueOf(internalStartTimestamp) },
                            BatteryEvents.TIMESTAMP + " ASC");
                int rowCount = c.getCount();
                batteryLevels = new int[rowCount];
                batteryTimestamps = new long[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    batteryTimestamps[i] = c.getLong(0);
                    batteryLevels[i] = c.getInt(1);
                }
                
                if (batteryTimestamps.length == 0) {
                    return null;
                }
                
                final long intervalStopTimestamp = batteryTimestamps[batteryTimestamps.length - 1];
                c.close();
                c = getContext().getContentResolver().query(
                    WifiEvents.CONTENT_URI,
                    new String[] { WifiEvents.TIMESTAMP,
                            WifiEvents.WIFI_CONNECTED },
                    WifiEvents.TIMESTAMP + ">? AND " + WifiEvents.TIMESTAMP
                            + "<=?",
                    new String[] { String.valueOf(internalStartTimestamp),
                            String.valueOf(intervalStopTimestamp) },
                    WifiEvents.TIMESTAMP + " ASC");
                rowCount = c.getCount();
                wifiStates = new boolean[rowCount];
                wifiTimestamps = new long[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    wifiTimestamps[i] = c.getLong(0);
                    wifiStates[i] = c.getInt(1) == 1;
                }
                
                c.close();
                c = getContext().getContentResolver().query(
                    ScreenEvents.CONTENT_URI,
                    new String[] { ScreenEvents.TIMESTAMP,
                            ScreenEvents.SCREEN_ON },
                    ScreenEvents.TIMESTAMP + ">? AND " + ScreenEvents.TIMESTAMP
                            + "<=?",
                    new String[] { String.valueOf(internalStartTimestamp),
                            String.valueOf(intervalStopTimestamp) },
                    ScreenEvents.TIMESTAMP + " ASC");
                rowCount = c.getCount();
                screenStates = new boolean[rowCount];
                screenTimestamps = new long[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    screenTimestamps[i] = c.getLong(0);
                    screenStates[i] = c.getInt(1) == 1;
                }
                
                c.close();
                c = getContext().getContentResolver().query(
                    PhoneEvents.CONTENT_URI,
                    new String[] { PhoneEvents.TIMESTAMP,
                            PhoneEvents.MOBILE_OPERATOR,
                            PhoneEvents.MOBILE_CONNECTED },
                    PhoneEvents.TIMESTAMP + ">? AND " + PhoneEvents.TIMESTAMP
                            + "<=?",
                    new String[] { String.valueOf(internalStartTimestamp),
                            String.valueOf(intervalStopTimestamp) },
                    PhoneEvents.TIMESTAMP + " ASC");
                rowCount = c.getCount();
                freeMobileStates = new boolean[rowCount];
                freeMobileTimestamps = new long[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    freeMobileTimestamps[i] = c.getLong(0);
                    freeMobileStates[i] = c.getInt(2) == 1
                            && MobileOperator.ORANGE.equals(MobileOperator
                                    .fromString(c.getString(1)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load statistics", e);
                return null;
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
            
            return new StatisticsData(batteryTimestamps, batteryLevels,
                    freeMobileTimestamps, freeMobileStates, wifiTimestamps,
                    wifiStates, screenTimestamps, screenStates);
        }
    }
    
    /**
     * Store statistics data.
     * @author Pixmob
     */
    public static class StatisticsData {
        public final int[] batteryLevels;
        public final long[] batteryTimestamps;
        public final long[] orangeNetworkTimestamps;
        public final boolean[] orangeNetworkStates;
        public final boolean[] wifiStates;
        public final long[] wifiTimestamps;
        public final boolean[] screenStates;
        public final long[] screenTimestamps;
        
        public StatisticsData(final long[] batteryTimestamps,
                final int[] batteryLevels, final long[] freeMobileTimestamps,
                final boolean[] freeMobileStates, final long[] wifiTimestamps,
                final boolean[] wifiStates, final long[] screenTimestamps,
                final boolean[] screenStates) {
            this.batteryTimestamps = batteryTimestamps;
            this.batteryLevels = batteryLevels;
            this.orangeNetworkTimestamps = freeMobileTimestamps;
            this.orangeNetworkStates = freeMobileStates;
            this.wifiTimestamps = wifiTimestamps;
            this.wifiStates = wifiStates;
            this.screenTimestamps = screenTimestamps;
            this.screenStates = screenStates;
        }
    }
}
