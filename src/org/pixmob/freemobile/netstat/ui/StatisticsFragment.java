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
import org.pixmob.freemobile.netstat.provider.NetstatContract.BatteryEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.ScreenEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.WifiEvents;
import org.pixmob.freemobile.netstat.ui.StatisticsFragment.StatisticsData;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
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
    private BatteryLevelChart batteryChart;
    private StateChart wifiChart;
    private StateChart screenChart;
    private ProgressBar progressBar;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        batteryChart = (BatteryLevelChart) getActivity().findViewById(
            R.id.battery_levels);
        wifiChart = (StateChart) getActivity().findViewById(R.id.wifi_states);
        screenChart = (StateChart) getActivity().findViewById(
            R.id.screen_states);
        progressBar = (ProgressBar) getActivity().findViewById(
            R.id.states_progress);
        
        refresh();
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
        batteryChart.setVisibility(View.INVISIBLE);
        wifiChart.setVisibility(View.INVISIBLE);
        screenChart.setVisibility(View.INVISIBLE);
        
        return new StatisticsLoader(getActivity());
    }
    
    @Override
    public void onLoaderReset(Loader<StatisticsData> loader) {
    }
    
    @Override
    public void onLoadFinished(Loader<StatisticsData> loader, StatisticsData d) {
        if (d == null) {
            batteryChart.setData(null, null);
            wifiChart.setData(null, null);
            screenChart.setData(null, null);
        } else {
            batteryChart.setData(d.batteryTimestamps, d.batteryLevels);
            wifiChart.setData(d.wifiTimestamps, d.wifiStates);
            screenChart.setData(d.screenTimestamps, d.screenStates);
        }
        
        batteryChart.invalidate();
        wifiChart.invalidate();
        screenChart.invalidate();
        
        progressBar.setVisibility(View.INVISIBLE);
        batteryChart.setVisibility(View.VISIBLE);
        wifiChart.setVisibility(View.VISIBLE);
        screenChart.setVisibility(View.VISIBLE);
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
                    wifiTimestamps, wifiStates, screenTimestamps, screenStates);
        }
    }
    
    /**
     * Store statistics data.
     * @author Pixmob
     */
    public static class StatisticsData {
        public final int[] batteryLevels;
        public final long[] batteryTimestamps;
        public final boolean[] wifiStates;
        public final long[] wifiTimestamps;
        public final boolean[] screenStates;
        public final long[] screenTimestamps;
        
        public StatisticsData(final long[] batteryTimestamps,
                final int[] batteryLevels, final long[] wifiTimestamps,
                final boolean[] wifiStates, final long[] screenTimestamps,
                final boolean[] screenStates) {
            this.batteryTimestamps = batteryTimestamps;
            this.batteryLevels = batteryLevels;
            this.wifiTimestamps = wifiTimestamps;
            this.wifiStates = wifiStates;
            this.screenTimestamps = screenTimestamps;
            this.screenStates = screenStates;
        }
    }
}
