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

import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;

import org.pixmob.freemobile.netstat.content.NetstatContract.Events;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

/**
 * This service is responsible for exporting the application database.
 * @author Pixmob
 */
public class ExportService extends Service {
    /**
     * Service listener.
     * @author Pixmob
     */
    public static interface Listener {
        void onProgress(int current, int total);
        
        void onPreExecute();
        
        void onPostExecute();
    }
    
    /**
     * Local service binder.
     * @author Pixmob
     */
    public static class Binder extends android.os.Binder {
        private final ExportService exportService;
        
        public Binder(final ExportService exportService) {
            this.exportService = exportService;
        }
        
        public ExportService getService() {
            return exportService;
        }
    }
    
    private static final int LISTENER_ON_PROGRESS = 0;
    private static final int LISTENER_PRE_EXECUTE = 1;
    private static final int LISTENER_POST_EXECUTE = 2;
    private static final String LINE_SEP = "\r\n";
    private static final String COL_SEP = ";";
    private static final String DATE_FORMAT = "HH:mm:ss dd/MM/YYYY";
    private final Object listenerLock = new Object();
    private WeakReference<Listener> listenerRef;
    private Handler listenerHandler;
    private volatile boolean running;
    private Thread exportTask;
    
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        listenerHandler = new Handler(getMainLooper()) {
            public void handleMessage(Message msg) {
                synchronized (listenerLock) {
                    final Listener listener = listenerRef != null ? listenerRef
                            .get() : null;
                    if (listener != null) {
                        switch (msg.what) {
                            case LISTENER_PRE_EXECUTE:
                                listener.onPreExecute();
                                break;
                            case LISTENER_POST_EXECUTE:
                                listener.onPostExecute();
                                break;
                            case LISTENER_ON_PROGRESS:
                                listener.onProgress(msg.arg1, msg.arg2);
                                break;
                        }
                    }
                }
            }
        };
    }
    
    public void start() {
        if (running) {
            throw new IllegalStateException("An export is running");
        }
        
        if (!Environment.getExternalStorageState().equals(
            Environment.MEDIA_MOUNTED)) {
            Log.w(TAG, "External storage is not available");
            Toast.makeText(this,
                getString(R.string.external_storage_not_available),
                Toast.LENGTH_SHORT).show();
            return;
        }
        
        exportTask = new ExportTask();
        exportTask.start();
    }
    
    public void stop() {
        running = false;
        if (exportTask != null) {
            try {
                exportTask.join();
            } catch (InterruptedException e) {
            }
            exportTask = null;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
    }
    
    private void doRun() {
        try {
            listenerHandler.sendEmptyMessage(LISTENER_PRE_EXECUTE);
            
            final File outputFile = new File(
                    Environment.getExternalStorageDirectory(),
                    "freemobilenetstat.csv");
            Log.i(TAG, "Exporting database to " + outputFile.getPath());
            
            try {
                export(outputFile);
            } catch (IOException e) {
                Log.w(TAG, "Failed to export data", e);
            }
        } finally {
            listenerHandler.sendEmptyMessage(LISTENER_POST_EXECUTE);
            Log.i(TAG, "Database export done");
        }
    }
    
    private void export(File outputFile) throws IOException {
        final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), "UTF-8"));
        
        final Cursor c = getContentResolver().query(
            Events.CONTENT_URI,
            new String[] { Events.TIMESTAMP, Events.MOBILE_OPERATOR,
                    Events.MOBILE_CONNECTED, Events.WIFI_CONNECTED,
                    Events.BATTERY_LEVEL, Events.SCREEN_ON }, null, null, null);
        try {
            final int rowCount = c.getCount();
            int currentRow = 0;
            
            final StringBuilder buf = new StringBuilder(1024);
            buf.append("#Timestamp").append(COL_SEP).append("Mobile Operator")
                    .append(COL_SEP).append("Mobile Connected").append(COL_SEP)
                    .append("Wi-Fi Connected").append(COL_SEP)
                    .append("Screen On").append(COL_SEP).append("Battery")
                    .append(LINE_SEP);
            out.write(buf.toString());
            
            while (c.moveToNext() && running) {
                final long t = c.getLong(0);
                final String mobOp = c.getString(1);
                final boolean mobConn = c.getInt(2) == 1;
                final boolean wifiOn = c.getInt(3) == 1;
                final int bat = c.getInt(4);
                final boolean screenOn = c.getInt(5) == 1;
                
                buf.delete(0, buf.length());
                buf.append(DateFormat.format(DATE_FORMAT, t)).append(COL_SEP)
                        .append(mobOp).append(COL_SEP).append(mobConn)
                        .append(COL_SEP).append(wifiOn).append(COL_SEP)
                        .append(screenOn).append(COL_SEP).append(bat)
                        .append(LINE_SEP);
                out.write(buf.toString());
                
                // The listener is called in the main thread.
                final Message m = new Message();
                m.what = LISTENER_ON_PROGRESS;
                m.arg1 = ++currentRow;
                m.arg2 = rowCount;
                listenerHandler.sendMessage(m);
            }
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {
            }
            c.close();
        }
    }
    
    public void setListener(Listener listener) {
        synchronized (listenerLock) {
            this.listenerRef = new WeakReference<Listener>(listener);
        }
    }
    
    /**
     * Internal background task for exporting database.
     * @author Pixmob
     */
    private class ExportTask extends Thread {
        @Override
        public void run() {
            running = true;
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                doRun();
            } finally {
                running = false;
            }
        }
    }
}
