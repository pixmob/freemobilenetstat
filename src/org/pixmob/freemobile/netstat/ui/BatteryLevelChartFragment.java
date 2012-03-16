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

import static org.pixmob.freemobile.netstat.Constants.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.provider.NetstatContract.BatteryEvents;
import org.pixmob.freemobile.netstat.ui.BatteryLevelChartFragment.BatteryLevelsData;

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
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Fragment showing battery levels chart.
 * @author Pixmob
 */
public class BatteryLevelChartFragment extends SherlockFragment implements
        LoaderCallbacks<BatteryLevelsData> {
    private BatteryLevelChart chart;
    private ProgressBar progressBar;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        
        chart = (BatteryLevelChart) getActivity().findViewById(
            R.id.battery_levels);
        progressBar = (ProgressBar) getActivity().findViewById(
            R.id.battery_levels_progress);
        
        onMenuRefresh();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main, container, false);
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, R.string.menu_refresh, Menu.NONE,
            R.string.menu_refresh).setIcon(R.drawable.ic_menu_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.string.menu_refresh:
                onMenuRefresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void onMenuRefresh() {
        getLoaderManager().restartLoader(0, null, this);
    }
    
    @Override
    public Loader<BatteryLevelsData> onCreateLoader(int id, Bundle args) {
        progressBar.setVisibility(View.VISIBLE);
        chart.setVisibility(View.INVISIBLE);
        return new BatteryLevelsLoader(getActivity());
    }
    
    @Override
    public void onLoaderReset(Loader<BatteryLevelsData> loader) {
    }
    
    @Override
    public void onLoadFinished(Loader<BatteryLevelsData> loader,
            BatteryLevelsData d) {
        if (d == null || d.timestamps == null || d.levels == null) {
            chart.setData(null, null);
        } else {
            chart.setData(d.timestamps, d.levels);
        }
        chart.invalidate();
        
        progressBar.setVisibility(View.INVISIBLE);
        chart.setVisibility(View.VISIBLE);
    }
    
    /**
     * Battery levels loader.
     * @author Pixmob
     */
    private static class BatteryLevelsLoader extends
            AsyncTaskLoader<BatteryLevelsData> {
        public BatteryLevelsLoader(final Context context) {
            super(context);
        }
        
        @Override
        public BatteryLevelsData loadInBackground() {
            final long start = System.currentTimeMillis();
            if (DEBUG) {
                Log.d(TAG, "Loading battery levels from ContentProvider");
            }
            
            int[] levels = null;
            long[] timestamps = null;
            
            final long deviceBootTime = System.currentTimeMillis()
                    - SystemClock.elapsedRealtime();
            
            Cursor c = null;
            try {
                c = getContext().getContentResolver()
                        .query(
                            BatteryEvents.CONTENT_URI,
                            new String[] { BatteryEvents.TIMESTAMP,
                                    BatteryEvents.LEVEL },
                            BatteryEvents.TIMESTAMP + ">?",
                            new String[] { String.valueOf(deviceBootTime) },
                            BatteryEvents.TIMESTAMP + " ASC");
                final int rowCount = c.getCount();
                
                levels = new int[rowCount];
                timestamps = new long[rowCount];
                for (int i = 0; c.moveToNext(); ++i) {
                    timestamps[i] = c.getLong(0);
                    levels[i] = c.getInt(1);
                }
                
                if (DEBUG) {
                    final long end = System.currentTimeMillis();
                    Log.d(TAG, "Batterry levels loaded in " + (end - start)
                            + " ms: " + rowCount + " element(s)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load battery levels", e);
                levels = null;
                timestamps = null;
            } finally {
                try {
                    if (c != null) {
                        c.close();
                    }
                } catch (Exception ignore) {
                }
            }
            
            return new BatteryLevelsData(timestamps, levels);
        }
        
        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }
    }
    
    /**
     * Store pairs of battery levels and timestamps.
     * @author Pixmob
     */
    public static class BatteryLevelsData {
        public final int[] levels;
        public final long[] timestamps;
        
        public BatteryLevelsData(final long[] timestamps, final int[] levels) {
            this.levels = levels;
            this.timestamps = timestamps;
        }
    }
}
