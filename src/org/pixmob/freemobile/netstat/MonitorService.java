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

import static org.pixmob.freemobile.netstat.BuildConfig.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.ACTION_NOTIFICATION;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_ENABLE_NOTIF_ACTIONS;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_STAT_NOTIF_SOUND;
import static org.pixmob.freemobile.netstat.Constants.SP_KEY_THEME;
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;
import static org.pixmob.freemobile.netstat.Constants.THEME_COLOR;
import static org.pixmob.freemobile.netstat.Constants.THEME_DEFAULT;
import static org.pixmob.freemobile.netstat.Constants.THEME_PIE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.pixmob.freemobile.netstat.content.NetstatContract.Events;
import org.pixmob.freemobile.netstat.util.IntentFactory;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

/**
 * This foreground service is monitoring phone state and battery level. A notification shows which mobile network is the
 * phone is connected to.
 * 
 * @author Pixmob
 */
public class MonitorService extends Service implements OnSharedPreferenceChangeListener {
    /**
     * Notification themes.
     */
    private static final Map< String, Theme> THEMES = new HashMap< String, Theme>(3);
    /**
     * Match network types from {@link TelephonyManager} with the corresponding string.
     */
    private static final SparseIntArray NETWORK_TYPE_STRINGS = new SparseIntArray(8);
    /**
     * Special data used for terminating the PendingInsert worker thread.
     */
    private static final Event STOP_PENDING_CONTENT_MARKER = new Event();
    
    private static final String FREE_MOBILE_FEMTOCELL_LAC_CODE = "98";
    
    /**
     * This intent will open the main UI.
     */
    private PendingIntent openUIPendingIntent;
    private PendingIntent networkOperatorSettingsPendingIntent;
    private IntentFilter batteryIntentFilter;
    private PowerManager pm;
    private TelephonyManager tm;
    private ConnectivityManager cm;
    private BroadcastReceiver screenMonitor;
    private PhoneStateListener phoneMonitor;
    private BroadcastReceiver connectionMonitor;
    private BroadcastReceiver batteryMonitor;
    private BroadcastReceiver shutdownMonitor;
    private Boolean lastWifiConnected;
    private Boolean lastMobileNetworkConnected;
    private boolean powerOn = true;
    private String lastMobileOperatorId;
    private String mobileOperatorId;
    private boolean isFemtocell;
    private Boolean lastIsFemtocell;
    private boolean mobileNetworkConnected;
    private int mobileNetworkType;
    private BlockingQueue< Event> pendingInsert;
    private SharedPreferences prefs;
    private Bitmap freeLargeIcon;
    private Bitmap freeFemtoLargeIcon;
    private Bitmap orangeLargeIcon;

	static {
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_EDGE, R.string.network_type_edge);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_GPRS, R.string.network_type_gprs);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSDPA, R.string.network_type_hsdpa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSPA, R.string.network_type_hspa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSPAP, R.string.network_type_hspap);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_HSUPA, R.string.network_type_hsupa);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_UMTS, R.string.network_type_umts);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_LTE, R.string.network_type_lte);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_CDMA, R.string.network_type_cdma);
        NETWORK_TYPE_STRINGS.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, R.string.network_type_unknown);

        THEMES.put(THEME_DEFAULT, new Theme(R.drawable.ic_stat_notify_service_free,
            R.drawable.ic_stat_notify_service_free_femto, R.drawable.ic_stat_notify_service_orange));
        THEMES.put(THEME_COLOR, new Theme(R.drawable.ic_stat_notify_service_free_color,
            R.drawable.ic_stat_notify_service_free_femto_color, R.drawable.ic_stat_notify_service_orange_color));
        THEMES.put(THEME_PIE, new Theme(R.drawable.ic_stat_notify_service_free_pie,
        		R.drawable.ic_stat_notify_service_free_femto_pie, R.drawable.ic_stat_notify_service_orange_pie));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SP_KEY_THEME.equals(key) || SP_KEY_ENABLE_NOTIF_ACTIONS.equals(key)) {
            updateNotification(false);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final int largeIconWidth =
                getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            final int largeIconHeight =
                getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            freeLargeIcon =
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_stat_notify_service_free_large), largeIconWidth, largeIconHeight, true);
            freeFemtoLargeIcon =
        		Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_stat_notify_service_free_femto_large), largeIconWidth, largeIconHeight, true);
            orangeLargeIcon =
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_stat_notify_service_orange_large), largeIconWidth, largeIconHeight, true);
        }

        pm = (PowerManager) getSystemService(POWER_SERVICE);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // Initialize and start a worker thread for inserting rows into the
        // application database.
        final Context c = getApplicationContext();
        pendingInsert = new ArrayBlockingQueue< Event>(8);
        new PendingInsertWorker(c, pendingInsert).start();

        // This intent is fired when the application notification is clicked.
        openUIPendingIntent =
            PendingIntent
                .getBroadcast(this, 0, new Intent(ACTION_NOTIFICATION), PendingIntent.FLAG_CANCEL_CURRENT);

        // This intent is only available as a Jelly Bean notification action in
        // order to open network operator settings.
        networkOperatorSettingsPendingIntent =
            PendingIntent.getActivity(this, 0, IntentFactory.networkOperatorSettings(this),
                PendingIntent.FLAG_CANCEL_CURRENT);

        // Watch screen light: is the screen on?
        screenMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateEventDatabase();
            }
        };

        final IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenMonitor, screenIntentFilter);

        // Watch Wi-Fi connections.
        connectionMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (onConnectivityUpdated()) {
                    updateEventDatabase();
                }
            }
        };

        final IntentFilter connectionIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionMonitor, connectionIntentFilter);

        // Watch mobile connections.
        phoneMonitor = new PhoneStateListener() {
            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                mobileNetworkType = networkType;
                updateNotification(false);
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (!DEBUG) {
                    // Check if the SIM card is compatible.
                    if (tm != null && TelephonyManager.SIM_STATE_READY == tm.getSimState()) {
                        final String rawMobOp = tm.getSimOperator();
                        final MobileOperator mobOp = MobileOperator.fromString(rawMobOp);
                        if (!MobileOperator.FREE_MOBILE.equals(mobOp)) {
                            Log.e(TAG, "SIM card is not compatible: " + rawMobOp);

                            // The service is stopped, since the SIM card is not
                            // compatible.
                            stopSelf();
                        }
                    }
                }

                mobileNetworkConnected =
                    serviceState != null && serviceState.getState() == ServiceState.STATE_IN_SERVICE;
                
                //check for femtocell
                final boolean phoneStateUpdated = onPhoneStateUpdated();
                if (phoneStateUpdated) {
                    updateEventDatabase();
                }
                updateNotification(phoneStateUpdated);
            }
        };
        tm.listen(phoneMonitor, PhoneStateListener.LISTEN_SERVICE_STATE |
            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        // Watch battery level.
        batteryMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateEventDatabase();
            }
        };

        batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryMonitor, batteryIntentFilter);

        shutdownMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onDeviceShutdown();
            }
        };
        final IntentFilter shutdownIntentFilter = new IntentFilter();
        shutdownIntentFilter.addAction(Intent.ACTION_SHUTDOWN);
        // HTC devices use a different Intent action:
        // http://stackoverflow.com/q/5076410/422906
        shutdownIntentFilter.addAction("android.intent.action.QUICKBOOT_POWEROFF");
        registerReceiver(shutdownMonitor, shutdownIntentFilter);
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
        unregisterReceiver(connectionMonitor);
        unregisterReceiver(batteryMonitor);
        unregisterReceiver(shutdownMonitor);

        tm = null;
        cm = null;
        pm = null;

        // Remove the status bar notification.
        stopForeground(true);

        prefs.unregisterOnSharedPreferenceChangeListener(this);
        prefs = null;

        if (freeLargeIcon != null) {
            freeLargeIcon.recycle();
            freeLargeIcon = null;
        }
        if (orangeLargeIcon != null) {
            orangeLargeIcon.recycle();
            orangeLargeIcon = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Update with current state.
        onConnectivityUpdated();
        onPhoneStateUpdated();
        updateNotification(false);

        return START_STICKY;
    }

    /**
     * Update the status bar notification.
     */
    private void updateNotification(boolean playSound) {
        final MobileOperator mobOp = MobileOperator.fromString(mobileOperatorId);
        if (!mobileNetworkConnected) {
            // Not connected to a mobile network: plane mode may be enabled.
            stopForeground(true);
            return;
        }

        final NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(getApplicationContext());
        if (mobOp == null) {
            // Connected to a foreign mobile network.
            final String tickerText = getString(R.string.stat_connected_to_foreign_mobile_network);
            final String contentText = getString(R.string.notif_action_open_network_operator_settings);

            nBuilder.setTicker(tickerText).setContentText(contentText).setContentTitle(tickerText).setSmallIcon(
                android.R.drawable.stat_sys_warning).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(networkOperatorSettingsPendingIntent).setWhen(0);
        } else {
            final String tickerText =
                String.format(getString(R.string.stat_connected_to_mobile_network), mobOp.toName(this));
            Integer networkTypeRes = NETWORK_TYPE_STRINGS.get(mobileNetworkType);
            if(networkTypeRes == null) {
                networkTypeRes = R.string.network_type_unknown;
            }
            String contentText =
                String.format(getString(R.string.mobile_network_type), getString(networkTypeRes));

            if ((mobOp == MobileOperator.FREE_MOBILE) && (isFemtocell)) 
            	contentText = getString(R.string.network_free_femtocell, contentText);
            
            final int iconRes = getStatIcon(mobOp);
            nBuilder.setSmallIcon(iconRes).setLargeIcon(getStatLargeIcon(mobOp)).setTicker(tickerText)
                .setContentText(contentText).setContentTitle(tickerText).setPriority(
                    NotificationCompat.PRIORITY_LOW).setContentIntent(openUIPendingIntent).setWhen(0);

            if (prefs.getBoolean(SP_KEY_ENABLE_NOTIF_ACTIONS, true)) {
                nBuilder.addAction(R.drawable.ic_stat_notify_action_network_operator_settings,
                    getString(R.string.notif_action_open_network_operator_settings),
                    networkOperatorSettingsPendingIntent);
            }
        }

        if (playSound) {
            final String rawSoundUri = prefs.getString(SP_KEY_STAT_NOTIF_SOUND, null);
            if (rawSoundUri != null) {
                final Uri soundUri = Uri.parse(rawSoundUri);
                nBuilder.setSound(soundUri);
            }
        }

        final Notification n = nBuilder.build();
        startForeground(R.string.stat_connected_to_mobile_network, n);
    }

    private int getStatIcon(MobileOperator op) {
        final String themeKey = prefs.getString(SP_KEY_THEME, THEME_DEFAULT);
        Theme theme = THEMES.get(themeKey);
        if (theme == null) {
            theme = THEMES.get(THEME_DEFAULT);
        }

        if (MobileOperator.FREE_MOBILE.equals(op) && isFemtocell) {
            return theme.freeFemtoIcon;
        } else if (MobileOperator.FREE_MOBILE.equals(op)) {
            return theme.freeIcon;
        } else if (MobileOperator.ORANGE.equals(op)) {
            return theme.orangeIcon;
        }
        return android.R.drawable.ic_dialog_alert;
    }

    private Bitmap getStatLargeIcon(MobileOperator op) {
        if (MobileOperator.FREE_MOBILE.equals(op) && isFemtocell) {
            return freeFemtoLargeIcon;
        } else if (MobileOperator.FREE_MOBILE.equals(op)) {
            return freeLargeIcon;
        } else if (MobileOperator.ORANGE.equals(op)) {
            return orangeLargeIcon;
        }
        return null;
    }

    private void onDeviceShutdown() {
        Log.i(TAG, "Device is about to shut down");
        powerOn = false;
        updateEventDatabase();
    }

    /**
     * This method is called when the phone data connectivity is updated.
     */
    private boolean onConnectivityUpdated() {
        // Get the Wi-Fi connectivity state.
        final NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final boolean wifiNetworkConnected = ni != null && ni.isConnected();

        // Prevent duplicated inserts.
        if (lastWifiConnected != null && lastWifiConnected.booleanValue() == wifiNetworkConnected) {
            return false;
        }
        lastWifiConnected = wifiNetworkConnected;

        Log.i(TAG, "Wifi state updated: connected=" + wifiNetworkConnected);
        return true;
    }

    /**
     * This method is called when the phone service state is updated.
     */
	private boolean onPhoneStateUpdated() {
        mobileOperatorId = tm != null ? tm.getNetworkOperator() : null;
        if (TextUtils.isEmpty(mobileOperatorId)) {
            mobileOperatorId = null;
        }

        updateFemtocellStatus();
        
        // Prevent duplicated inserts.
        if (lastMobileNetworkConnected != null && lastMobileOperatorId != null
        	&& lastIsFemtocell != null
            && lastMobileNetworkConnected.booleanValue() == mobileNetworkConnected
            && lastIsFemtocell == isFemtocell 
            && lastMobileOperatorId.equals(mobileOperatorId)) {
            return false;
        }
        lastMobileNetworkConnected = mobileNetworkConnected;
        lastMobileOperatorId = mobileOperatorId;
        lastIsFemtocell = isFemtocell;
        
        Log.i(TAG, "Phone state updated: operator=" + mobileOperatorId + "; connected=" + mobileNetworkConnected + "; femtocell=" + isFemtocell);
        return true;
    }

	/**
	 * Check if we are connected on a Free Mobile femtocell
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private void updateFemtocellStatus() {
		Integer lac = null;
        if (tm != null) {
        	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        	
        		//get the cell list
        		List<CellInfo> cellInfos = tm.getAllCellInfo();
        		if (cellInfos != null) {
	        		for (CellInfo cellInfo : cellInfos) {
	        			
	        			if (cellInfo.isRegistered()) { //we use only registered cells
		        			if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) 
		        					&& (cellInfo instanceof CellInfoWcdma)) { //manage the wcdma cell case
		        				Log.d(TAG, "We got a WCDMA cell");
		        				CellIdentityWcdma ci = ((CellInfoWcdma) cellInfo).getCellIdentity();
		        				if (ci != null) { //save the LAC and exit loop
		        					lac = ci.getLac();
		        					Log.d(TAG, "We got the LAC - exit loop");
		        					break;
		        				}
		        				
		        			} else if (cellInfo instanceof CellInfoGsm) { //test the gsm case
		        				CellIdentityGsm ci = ((CellInfoGsm) cellInfo).getCellIdentity();
		        				Log.d(TAG, "We got a CDMA cell");
		        				if (ci != null) { //save the LAC and exit loop
		        					lac = ci.getLac();
		        					Log.d(TAG, "We got the LAC - exit loop");
		        					break;
		        				}
		        			}
		        			
	        			} else
	        				Log.d(TAG, "Unregistered cell - skipping");
	        		}
        		} else
    				Log.d(TAG, "No cell infos available");
        		
        	}
        	if (lac == null) { //use old API if LAC was not found with the new method (useful for buggy devices such as Samsung Galaxy S5) or if SDK is too old
	    		CellLocation cellLocation = tm.getCellLocation(); //cell location might be null... handle with care
	    		if ((cellLocation != null) && (cellLocation instanceof GsmCellLocation)) {
	    			Log.d(TAG, "We got a old GSM cell with LAC");
	    			lac = ((GsmCellLocation) cellLocation).getLac();
	    		}
        	}
        }
        /*/ Fake femtocell
        lac = 3981;
        //*/
        Log.d(TAG, "LAC value : " + lac);
        if (lac != null) {
        	String lacAsString = String.valueOf(lac);
        	isFemtocell = (lacAsString.length() == 4) && (lacAsString.subSequence(1, 3).equals(FREE_MOBILE_FEMTOCELL_LAC_CODE));
        }
        Log.i(TAG, "Femtocell value : " + isFemtocell);
	}

    private void updateEventDatabase() {
        final Event e = new Event();
        e.timestamp = System.currentTimeMillis();
        e.screenOn = pm != null ? pm.isScreenOn() : false;
        e.batteryLevel = getBatteryLevel();
        e.wifiConnected = Boolean.TRUE.equals(lastWifiConnected);
        e.mobileConnected = powerOn ? Boolean.TRUE.equals(lastMobileNetworkConnected) : false;
        e.mobileOperator = lastMobileOperatorId;
        e.powerOn = powerOn;
        e.femtocell = lastIsFemtocell;

        try {
            pendingInsert.put(e);
        } catch (InterruptedException ex) {
            Log.w(TAG, "Failed to schedule event insertion", ex);
        }
    }

    private int getBatteryLevel() {
        if (batteryIntentFilter == null) {
            return 100;
        }
        final Intent i = registerReceiver(null, batteryIntentFilter);
        if (i == null) {
            return 100;
        }

        final int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        final int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 0);

        return scale == 0 ? 100 : (int) Math.round(level * 100d / scale);
    }

    /**
     * This internal thread is responsible for inserting data into the application database. This thread will prevent
     * the main loop from being used for interacting with the database, which could cause "Application Not Responding"
     * dialogs.
     */
    private static class PendingInsertWorker extends Thread {
        private final Context context;
        private final BlockingQueue< Event> pendingInsert;

        public PendingInsertWorker(final Context context, final BlockingQueue< Event> pendingInsert) {
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

            final ContentValues cv = new ContentValues(7);
            final ContentResolver cr = context.getContentResolver();

            final ContentValues lastCV = new ContentValues(7);
            long lastEventHashCode = 0;

            boolean running = true;
            while (running) {
                try {
                    final Event e = pendingInsert.take();
                    if (STOP_PENDING_CONTENT_MARKER == e) {
                        running = false;
                    } else {
                        e.write(cv);

                        // Check the last inserted event hash code:
                        // if the hash code is the same, the event is not
                        // inserted.
                        lastCV.putAll(cv);
                        lastCV.remove(Events.TIMESTAMP);
                        if (e.powerOn && lastCV.hashCode() == lastEventHashCode) {
                            if (DEBUG) {
                                Log.d(TAG, "Skip event insertion: " + e);
                            }
                        } else {
                            if (DEBUG) {
                                Log.d(TAG, "Inserting new event into database: " + e);
                            }
                            cr.insert(Events.CONTENT_URI, cv);
                        }
                        lastEventHashCode = lastCV.hashCode();
                        lastCV.clear();
                    }
                    cv.clear();
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

    /**
     * Notification theme.
     * 
     * @author Pixmob
     */
    private static class Theme {
        public final int freeIcon;
        public final int freeFemtoIcon;
        public final int orangeIcon;

        public Theme(final int freeIcon, final int freeFemtoIcon, final int orangeIcon) {
            this.freeIcon = freeIcon;
            this.freeFemtoIcon = freeFemtoIcon;
            this.orangeIcon = orangeIcon;
        }
    }
}
