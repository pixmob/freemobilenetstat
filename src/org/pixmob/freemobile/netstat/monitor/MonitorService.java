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
package org.pixmob.freemobile.netstat.monitor;

import static org.pixmob.freemobile.netstat.Constants.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.provider.NetstatContract.BatteryEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.PhoneEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.ScreenEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.WifiEvents;
import org.pixmob.freemobile.netstat.ui.Netstat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Process;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

/**
 * This foreground service is monitoring phone state and battery level. A
 * notification shows which mobile network is the phone is connected to.
 * @author Pixmob
 */
public class MonitorService extends Service {
    /**
     * Match network types from {@link TelephonyManager} with the corresponding
     * string.
     */
    private static final SparseIntArray NETWORK_TYPE_STRINGS = new SparseIntArray(
            8);
    /**
     * Special data used for terminating the PendingInsert worker thread.
     */
    private static final PendingContent STOP_PENDING_CONTENT_MARKER = new PendingContent(
            null, null);
    /**
     * This intent will open the main UI.
     */
    private PendingIntent openUIPendingIntent;
    private TelephonyManager tm;
    private ConnectivityManager cm;
    private BroadcastReceiver screenMonitor;
    private PhoneStateListener phoneMonitor;
    private BroadcastReceiver connectionMonitor;
    private BroadcastReceiver batteryMonitor;
    private Boolean lastWifiConnected;
    private Boolean lastMobileNetworkConnected;
    private String lastMobileOperatorId;
    private Integer lastBatteryLevel;
    private String mobileOperatorId;
    private boolean mobileNetworkConnected;
    private int mobileNetworkType;
    private BlockingQueue<PendingContent> pendingInsert;
    
    static {
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_EDGE,
            R.string.network_type_edge);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_GPRS,
            R.string.network_type_gprs);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSDPA,
            R.string.network_type_hsdpa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSPA,
            R.string.network_type_hspa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSPAP,
            R.string.network_type_hspap);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSUPA,
            R.string.network_type_hsupa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_UMTS,
            R.string.network_type_umts);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
            R.string.network_type_unknown);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize and start a worker thread for inserting rows into the
        // application database.
        final Context c = getApplicationContext();
        pendingInsert = new ArrayBlockingQueue<PendingContent>(8);
        new PendingInsertWorker(c, pendingInsert).start();
        
        // This intent is fired when the application notification is clicked.
        openUIPendingIntent = PendingIntent.getActivity(c, 0, new Intent(c,
                Netstat.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_CANCEL_CURRENT);
        
        // Watch screen light: is the screen on?
        screenMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final boolean screenOn = Intent.ACTION_SCREEN_ON.equals(intent
                        .getAction());
                onScreenUpdated(screenOn);
            }
        };
        
        final IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenMonitor, screenIntentFilter);
        
        // Watch Wi-Fi connections.
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectionMonitor = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                onConnectivityUpdated();
            }
        };
        
        final IntentFilter connectionIntentFilter = new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionMonitor, connectionIntentFilter);
        
        // Watch mobile connections.
        phoneMonitor = new PhoneStateListener() {
            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                mobileNetworkType = networkType;
                updateNotification();
            }
            
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                mobileNetworkConnected = serviceState.getState() == ServiceState.STATE_IN_SERVICE;
                onPhoneStateUpdated();
                updateNotification();
            }
        };
        
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        tm.listen(phoneMonitor, PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
        
        // Watch battery level.
        batteryMonitor = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final int level = intent.getIntExtra(
                    BatteryManager.EXTRA_LEVEL, 0);
                final int scale = intent.getIntExtra(
                    BatteryManager.EXTRA_SCALE, 0);
                
                final int levelPercent = (int) Math.round(level * 100d / scale);
                onBatteryUpdated(levelPercent);
            }
        };
        
        final IntentFilter batteryIntentFilter = new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryMonitor, batteryIntentFilter);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Tell the PendingInsert worker thread to stop.
        try {
            pendingInsert.put(STOP_PENDING_CONTENT_MARKER);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to stop PendingInsert worker thread", e);
        }
        
        // Stop listening to system events.
        unregisterReceiver(screenMonitor);
        tm.listen(phoneMonitor, PhoneStateListener.LISTEN_NONE);
        tm = null;
        unregisterReceiver(connectionMonitor);
        cm = null;
        unregisterReceiver(batteryMonitor);
        
        // Remove the status bar notification.
        stopForeground(true);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Update with current state.
        onConnectivityUpdated();
        onPhoneStateUpdated();
        updateNotification();
        
        return START_STICKY;
    }
    
    /**
     * Update the status bar notification.
     */
    private void updateNotification() {
        final MobileOperator mobOp = MobileOperator
                .fromString(mobileOperatorId);
        if (mobOp == null || !mobileNetworkConnected) {
            stopForeground(true);
            return;
        }
        
        final String contentText = String.format(
            getString(R.string.mobile_network_type),
            getString(NETWORK_TYPE_STRINGS.get(mobileNetworkType)));
        
        final int iconRes;
        final String tickerText;
        if (MobileOperator.FREE_MOBILE.equals(mobOp)) {
            iconRes = R.drawable.ic_stat_connected_to_free_mobile;
            tickerText = String.format(
                getString(R.string.stat_connected_to_mobile_network),
                getString(R.string.network_free_mobile));
        } else {
            iconRes = R.drawable.ic_stat_connected_to_orange;
            tickerText = String.format(
                getString(R.string.stat_connected_to_mobile_network),
                getString(R.string.network_orange));
        }
        final Notification n = new Notification(iconRes, tickerText, 0);
        n.setLatestEventInfo(getApplicationContext(), tickerText, contentText,
            openUIPendingIntent);
        
        startForeground(R.string.stat_connected_to_mobile_network, n);
    }
    
    /**
     * This method is called when the screen light is updated.
     */
    private void onScreenUpdated(boolean screenOn) {
        Log.i(TAG, "Screen is " + (screenOn ? "on" : "off"));
        
        final ContentValues cv = new ContentValues(4);
        cv.put(ScreenEvents.SCREEN_ON, screenOn ? 1 : 0);
        cv.put(ScreenEvents.TIMESTAMP, System.currentTimeMillis());
        cv.put(ScreenEvents.SYNC_ID, UUID.randomUUID().toString());
        cv.put(ScreenEvents.SYNC_STATUS, 0);
        
        if (!pendingInsert.offer(new PendingContent(ScreenEvents.CONTENT_URI,
                cv))) {
            Log.w(TAG, "Failed to schedule screen event insertion");
        }
    }
    
    /**
     * This method is called when the phone data connectivity is updated.
     */
    private void onConnectivityUpdated() {
        // Get the Wi-Fi connectivity state.
        final NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final boolean wifiNetworkConnected = ni != null && ni.isConnected();
        
        // Prevent duplicated inserts.
        if (lastWifiConnected != null
                && lastWifiConnected.booleanValue() == wifiNetworkConnected) {
            return;
        }
        lastWifiConnected = wifiNetworkConnected;
        
        Log.i(TAG, "Wifi state updated: connected=" + wifiNetworkConnected);
        
        final ContentValues cv = new ContentValues(4);
        cv.put(WifiEvents.WIFI_CONNECTED, wifiNetworkConnected ? 1 : 0);
        cv.put(WifiEvents.TIMESTAMP, System.currentTimeMillis());
        cv.put(WifiEvents.SYNC_ID, UUID.randomUUID().toString());
        cv.put(WifiEvents.SYNC_STATUS, 0);
        
        if (!pendingInsert
                .offer(new PendingContent(WifiEvents.CONTENT_URI, cv))) {
            Log.w(TAG, "Failed to schedule wifi event insertion");
        }
    }
    
    /**
     * This method is called when the phone service state is updated.
     */
    private void onPhoneStateUpdated() {
        mobileOperatorId = tm.getNetworkOperator();
        if (TextUtils.isEmpty(mobileOperatorId)) {
            mobileOperatorId = null;
        }
        
        // Prevent duplicated inserts.
        if (lastMobileNetworkConnected != null
                && lastMobileOperatorId != null
                && lastMobileNetworkConnected.booleanValue() == mobileNetworkConnected
                && lastMobileOperatorId.equals(mobileOperatorId)) {
            return;
        }
        lastMobileNetworkConnected = mobileNetworkConnected;
        lastMobileOperatorId = mobileOperatorId;
        
        Log.i(TAG, "Phone state updated: operator=" + mobileOperatorId
                + "; connected=" + mobileNetworkConnected);
        
        final ContentValues cv = new ContentValues(5);
        cv.put(PhoneEvents.MOBILE_CONNECTED, mobileNetworkConnected ? 1 : 0);
        cv.put(PhoneEvents.MOBILE_OPERATOR, mobileOperatorId);
        cv.put(PhoneEvents.TIMESTAMP, System.currentTimeMillis());
        cv.put(PhoneEvents.SYNC_ID, UUID.randomUUID().toString());
        cv.put(PhoneEvents.SYNC_STATUS, 0);
        
        if (!pendingInsert
                .offer(new PendingContent(PhoneEvents.CONTENT_URI, cv))) {
            Log.w(TAG, "Failed to schedule phone event insertion");
        }
    }
    
    /**
     * This method is called when the battery level is updated.
     */
    private void onBatteryUpdated(int level) {
        // Prevent duplicated inserts.
        if (lastBatteryLevel != null && lastBatteryLevel.intValue() == level) {
            return;
        }
        lastBatteryLevel = level;
        
        Log.i(TAG, "Baterry level updated: " + level + "%");
        
        final ContentValues cv = new ContentValues(4);
        cv.put(BatteryEvents.LEVEL, level);
        cv.put(WifiEvents.TIMESTAMP, System.currentTimeMillis());
        cv.put(WifiEvents.SYNC_ID, UUID.randomUUID().toString());
        cv.put(WifiEvents.SYNC_STATUS, 0);
        
        if (!pendingInsert.offer(new PendingContent(BatteryEvents.CONTENT_URI,
                cv))) {
            Log.w(TAG, "Failed to schedule battery event insertion");
        }
    }
    
    /**
     * Pending database content to insert.
     * @author Pixmob
     */
    private static class PendingContent {
        public final Uri contentUri;
        public final ContentValues contentValues;
        
        public PendingContent(final Uri contentUri,
                final ContentValues contentValues) {
            this.contentUri = contentUri;
            this.contentValues = contentValues;
        }
    }
    
    /**
     * This internal thread is responsible for inserting data into the
     * application database. This thread will prevent the main loop from being
     * used for interacting with the database, which could cause
     * "Application Not Responding" dialogs.
     */
    private static class PendingInsertWorker extends Thread {
        private final Context context;
        private final BlockingQueue<PendingContent> pendingInsert;
        
        public PendingInsertWorker(final Context context,
                final BlockingQueue<PendingContent> pendingInsert) {
            super("FreeMobileNetstat/PendingInsert");
            setDaemon(true);
            this.context = context;
            this.pendingInsert = pendingInsert;
        }
        
        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "PendingInsert worker thread is started");
            }
            
            // Set a lower priority to prevent UI from lagging.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            
            boolean running = true;
            while (running) {
                try {
                    final PendingContent data = pendingInsert.take();
                    if (STOP_PENDING_CONTENT_MARKER == data) {
                        running = false;
                    }
                    
                    if (DEBUG) {
                        Log.d(TAG, "Inserting new row to " + data.contentUri);
                    }
                    
                    context.getContentResolver().insert(data.contentUri,
                        data.contentValues);
                } catch (InterruptedException e) {
                    running = false;
                } catch (Exception e) {
                    Log.e(TAG, "Pending insert failed", e);
                }
            }
            
            if (DEBUG) {
                Log.d(TAG, "PendingInsert worker thread is terminated");
            }
        }
    }
}
