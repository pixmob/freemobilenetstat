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

import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.pixmob.freemobile.netstat.R;
import org.pixmob.freemobile.netstat.content.NetstatContract.Events;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

/**
 * Export database to a file on the external storage.
 * @author Pixmob
 */
class ExportTask extends AsyncTask<Void, Integer, Void> {
    private static final String DIALOG_TAG = "export";
    private static final String LINE_SEP = "\r\n";
    private static final String COL_SEP = ";";
    private static final String DATE_FORMAT = "HH:mm:ss dd/MM/YYYY";
    private final Context context;
    private FragmentManager fragmentManager;
    
    public ExportTask(final Context context,
            final FragmentManager fragmentManager) {
        this.context = context;
        this.fragmentManager = fragmentManager;
    }
    
    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }
    
    @Override
    protected Void doInBackground(Void... params) {
        try {
            export();
            Log.i(TAG, "Export done");
        } catch (IOException e) {
            Log.e(TAG, "Failed to export database", e);
        }
        return null;
    }
    
    @Override
    protected void onProgressUpdate(Integer... values) {
        final ExportDialogFragment f = (ExportDialogFragment) fragmentManager
                .findFragmentByTag(DIALOG_TAG);
        if (f != null) {
            final int current = values[0];
            final int total = values[1];
            f.update(current, total);
        }
    }
    
    @Override
    protected void onPreExecute() {
        ExportDialogFragment.newInstance(this)
                .show(fragmentManager, DIALOG_TAG);
    }
    
    @Override
    protected void onPostExecute(Void result) {
        dismissDialog();
    }
    
    @Override
    protected void onCancelled(Void result) {
        dismissDialog();
    }
    
    private void dismissDialog() {
        final DialogFragment f = (DialogFragment) fragmentManager
                .findFragmentByTag(DIALOG_TAG);
        if (f != null) {
            f.dismiss();
        }
    }
    
    private void export() throws IOException {
        if (!Environment.getExternalStorageState().equals(
            Environment.MEDIA_MOUNTED)) {
            Log.w(TAG, "External storage is not available");
            Toast.makeText(context,
                context.getString(R.string.external_storage_not_available),
                Toast.LENGTH_SHORT).show();
            return;
        }
        
        final File outputFile = new File(
                Environment.getExternalStorageDirectory(),
                "freemobilenetstat.csv");
        Log.i(TAG, "Exporting database to " + outputFile.getPath());
        
        final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), "UTF-8"));
        
        final Cursor c = context.getContentResolver().query(
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
            
            while (c.moveToNext()) {
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
                
                publishProgress(++currentRow, rowCount);
            }
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {
            }
            c.close();
        }
    }
}
