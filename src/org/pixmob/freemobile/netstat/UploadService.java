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
import static org.pixmob.freemobile.netstat.Constants.SP_NAME;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.pixmob.freemobile.netstat.content.NetstatContract.Events;
import org.pixmob.freemobile.netstat.util.DateUtils;
import org.pixmob.httpclient.HttpClient;
import org.pixmob.httpclient.HttpClientException;
import org.pixmob.httpclient.HttpResponse;
import org.pixmob.httpclient.HttpResponseHandler;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.util.LongSparseArray;
import android.text.format.DateFormat;
import android.util.Log;

/**
 * This background service uploads statistics.
 * @author Pixmob
 */
public class UploadService extends IntentService {
    private static final int SERVER_API_VERSION = 1;
    private static final String EXTRA_DEVICE_REG = "deviceReg";
    private static final long DAY_IN_MILLISECONDS = 86400 * 1000;
    private static final int SYNC_UPLOADED = 1;
    private static final int SYNC_PENDING = 0;
    private static String httpUserAgent;
    private SharedPreferences prefs;
    private ConnectivityManager cm;
    private PowerManager pm;
    private SQLiteOpenHelper dbHelper;

    public UploadService() {
        super("FreeMobileNetstat/Upload");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        dbHelper = new UploadDatabaseHelper(this);
    }

    @Override
    public void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Check if statistics upload is enabled.
        if (!prefs.getBoolean(Constants.SP_KEY_UPLOAD_STATS, false)) {
            Log.d(TAG, "Statistics upload is disabled: skip upload");
            return;
        }

        // Check if an Internet connection is available.
        final NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isAvailable() || !netInfo.isConnected()) {
            Log.d(TAG, "Network connectivity is not available: skip upload");
            return;
        }

        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        SQLiteDatabase db = null;
        try {
            wl.acquire();
            db = dbHelper.getWritableDatabase();
            run(intent, db);
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload statistics", e);
        } finally {
            if (db != null) {
                db.close();
            }
            wl.release();
        }
    }

    private void run(Intent intent, final SQLiteDatabase db) throws Exception {
        final long now = dateAtMidnight(System.currentTimeMillis());

        Log.i(TAG, "Initializing statistics before uploading");

        final LongSparseArray<DailyStat> stats = new LongSparseArray<DailyStat>(15);
        final Set<Long> uploadedStats = new HashSet<Long>(15);
        final long statTimestampStart = now - 30 * DAY_IN_MILLISECONDS;

        // Get pending uploads.
        Cursor c = db.query("daily_stat", new String[] { "stat_timestamp", "orange", "free_mobile", "sync" },
                "stat_timestamp>=? AND stat_timestamp<?", new String[] { String.valueOf(statTimestampStart),
                        String.valueOf(now) }, null, null, null);
        try {
            while (c.moveToNext()) {
                final long d = c.getLong(0);
                final int sync = c.getInt(3);
                if (SYNC_UPLOADED == sync) {
                    uploadedStats.add(d);
                } else if (SYNC_PENDING == sync) {
                    final DailyStat s = new DailyStat();
                    s.orange = c.getInt(1);
                    s.freeMobile = c.getInt(2);
                    stats.put(d, s);
                }
            }
        } finally {
            c.close();
        }

        // Compute missing uploads.
        final ContentValues cv = new ContentValues();
        db.beginTransaction();
        try {
            for (long d = statTimestampStart; d < now; d += DAY_IN_MILLISECONDS) {
                if (stats.get(d) == null && !uploadedStats.contains(d)) {
                    final DailyStat s = computeDailyStat(d);
                    cv.put("stat_timestamp", d);
                    cv.put("orange", s.orange);
                    cv.put("free_mobile", s.freeMobile);
                    cv.put("sync", SYNC_PENDING);
                    db.insertOrThrow("daily_stat", null, cv);
                    stats.put(d, s);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // Delete old statistics.
        if (DEBUG) {
            Log.d(TAG, "Cleaning up upload database");
        }
        db.delete("daily_stat", "stat_timestamp<?", new String[] { String.valueOf(statTimestampStart) });

        // Check if there are any statistics to upload.
        final int statsLen = stats.size();
        if (statsLen == 0) {
            Log.i(TAG, "Nothing to upload");
            return;
        }

        // Check if the remote server is up.
        final String baseUrl = "https://freemobilenetstat.appspot.com/" + SERVER_API_VERSION;
        final HttpClient client = createHttpClient();
        try {
            client.head(baseUrl).execute();
        } catch (HttpClientException e) {
            Log.w(TAG, "Remote server is not available: cannot upload statistics", e);
            return;
        }

        // Upload statistics.
        Log.i(TAG, "Uploading statistics");
        final JSONObject json = new JSONObject();
        for (int i = 0; i < statsLen; ++i) {
            final long d = stats.keyAt(i);
            final DailyStat s = stats.get(d);

            try {
                json.put("timeOnOrange", s.orange);
                json.put("timeOnFreeMobile", s.freeMobile);
            } catch (JSONException e) {
                throw new IOException("Failed to prepare statistics upload", e);
            }

            final String url = baseUrl + "/device/" + getDeviceId() + "/daily/"
                    + DateFormat.format("yyyyMMdd", d);
            if (DEBUG) {
                Log.d(TAG, "Uploading statistics for " + DateUtils.formatDate(d) + " to: " + url);
            }

            final byte[] rawJson = json.toString().getBytes("UTF-8");
            final boolean deviceWasRegistered = intent.hasExtra(EXTRA_DEVICE_REG);
            try {
                client.post(url)
                        .expectStatusCode(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND)
                        .setContent(rawJson).setContentType("application/json")
                        .setHandler(new HttpResponseHandler() {
                            @Override
                            public void onResponse(HttpResponse response) throws Exception {
                                final int sc = response.getStatusCode();
                                if (HttpURLConnection.HTTP_NOT_FOUND == sc) {
                                    // Check if the device has just been
                                    // registered.
                                    if (deviceWasRegistered) {
                                        Log.w(TAG, "Failed to upload statistics");
                                    } else {
                                        // Got 404: the device does not exist.
                                        // We need to register this device.
                                        try {
                                            registerDevice();

                                            // Restart this service.
                                            startService(new Intent(UploadService.this, UploadService.class)
                                                    .putExtra(EXTRA_DEVICE_REG, true));
                                        } catch (HttpClientException e) {
                                            Log.w(TAG, "Failed to register device", e);
                                        }
                                    }
                                } else if (HttpURLConnection.HTTP_OK == sc) {
                                    // Update upload database.
                                    cv.clear();
                                    cv.put("sync", SYNC_UPLOADED);
                                    db.update("daily_stat", cv, "stat_timestamp=?",
                                            new String[] { String.valueOf(d) });

                                    if (DEBUG) {
                                        Log.d(TAG, "Upload done for " + DateUtils.formatDate(d));
                                    }
                                }
                            }
                        }).execute();
            } catch (HttpClientException e) {
                throw new IOException("Failed to send request with statistics", e);
            }
        }

        Log.i(TAG, "Statistics upload done");
    }

    private DailyStat computeDailyStat(long date) {
        long timeOnOrange = 0;
        long timeOnFreeMobile = 0;

        if (DEBUG) {
            Log.d(TAG, "Computing statistics for " + DateUtils.formatDate(date));
        }

        final Cursor c = getContentResolver().query(Events.CONTENT_URI,
                new String[] { Events.TIMESTAMP, Events.MOBILE_OPERATOR },
                Events.TIMESTAMP + ">=? AND " + Events.TIMESTAMP + "<=?",
                new String[] { String.valueOf(date), String.valueOf(date + 86400 * 1000) }, Events.TIMESTAMP);
        try {
            long t0 = 0;
            MobileOperator op0 = null;
            CharArrayBuffer cBuf = new CharArrayBuffer(6);

            while (c.moveToNext()) {
                final long t = c.getLong(0);
                c.copyStringToBuffer(1, cBuf);
                final MobileOperator op = MobileOperator.fromString(cBuf);

                if (t0 != 0) {
                    if (op != null && op.equals(op0)) {
                        final long dt = t - t0;
                        if (MobileOperator.ORANGE.equals(op)) {
                            timeOnOrange += dt;
                        } else if (MobileOperator.FREE_MOBILE.equals(op)) {
                            timeOnFreeMobile += dt;
                        }
                    }
                }

                t0 = t;
                op0 = op;
            }
        } finally {
            c.close();
        }

        final DailyStat s = new DailyStat();
        s.orange = timeOnOrange;
        s.freeMobile = timeOnFreeMobile;
        return s;
    }

    private void registerDevice() throws IOException, HttpClientException {
        final String url = "https://freemobilenetstat.appspot.com/device/" + getDeviceId();
        final JSONObject json = new JSONObject();
        try {
            json.put("brand", Build.BRAND);
            json.put("model", Build.MODEL);
        } catch (JSONException e) {
            throw new IOException("Failed to prepare device registration request", e);
        }

        final byte[] rawJson = json.toString().getBytes("UTF-8");
        Log.i(TAG, "Registering device");

        final HttpClient client = createHttpClient();
        client.put(url).expectStatusCode(HttpURLConnection.HTTP_CREATED).setContent(rawJson)
                .setContentType("application/json").execute();
    }

    private HttpClient createHttpClient() {
        if (httpUserAgent == null) {
            final PackageManager pm = getPackageManager();
            String applicationVersion = "0";
            try {
                final PackageInfo pkgInfo = pm.getPackageInfo(getPackageName(), 0);
                applicationVersion = pkgInfo.versionName;
            } catch (NameNotFoundException e) {
            }
            httpUserAgent = "FreeMobileNetstat/" + applicationVersion;
        }

        final HttpClient client = new HttpClient(this);
        client.setConnectTimeout(10000);
        client.setReadTimeout(20000);
        client.setUserAgent(httpUserAgent);
        return client;
    }

    private String getDeviceId() {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final Cursor c = db.query("device", new String[] { "device_id" }, null, null, null, null, null);
        String deviceId = null;
        try {
            if (c.moveToNext()) {
                deviceId = c.getString(0);
            }
        } finally {
            c.close();
        }
        if (deviceId == null) {
            // Generate a new device identifier.
            deviceId = UUID.randomUUID().toString();

            // Store this device identifier in the database.
            final ContentValues cv = new ContentValues(1);
            cv.put("device_id", deviceId);
            db.insertOrThrow("device", null, cv);
        }
        return deviceId;
    }

    private static long dateAtMidnight(long d) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(d);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTimeInMillis();
    }

    private static class UploadDatabaseHelper extends SQLiteOpenHelper {
        public UploadDatabaseHelper(final Context context) {
            super(context, "upload.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (!db.isReadOnly()) {
                String req = "CREATE TABLE daily_stat (stat_timestamp TIMESTAMP PRIMARY KEY, "
                        + "orange INTEGER NOT NULL, free_mobile INTEGER NOT NULL, sync INTEGER NOT NULL)";
                db.execSQL(req);

                req = "CREATE TABLE device (device_id TEXT PRIMARY KEY)";
                db.execSQL(req);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (!db.isReadOnly()) {
                db.execSQL("DROP TABLE IF EXISTS daily_stat");
                db.execSQL("DROP TABLE IF EXISTS device");
                onCreate(db);
            }
        }
    }

    private static class DailyStat {
        public long orange;
        public long freeMobile;
    }
}
