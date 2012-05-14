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

import org.pixmob.freemobile.netstat.ExportService;
import org.pixmob.freemobile.netstat.R;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;

/**
 * Fragment for exporting the application database, showing a progress dialog.
 * @author Pixmob
 */
public class ExportDialogFragment extends DialogFragment implements
        ExportService.Listener, ServiceConnection {
    private Context context;
    private ExportService exportService;
    private ProgressDialog dialog;
    private boolean pendingStart;
    
    @Override
    public void onProgress(int current, int total) {
        dialog.setIndeterminate(false);
        dialog.setMax(total);
        dialog.setProgress(current);
    }
    
    @Override
    public void onPreExecute() {
    }
    
    @Override
    public void onPostExecute() {
        try {
            dismiss();
        } catch (Exception e) {
            // The framework throws a NPE when the device orientation
            // has changed. Why?
        }
    }
    
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        exportService = ((ExportService.Binder) service).getService();
        exportService.setListener(this);
        
        if (pendingStart) {
            exportService.start();
            pendingStart = false;
        }
    }
    
    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (exportService != null) {
            exportService.setListener(null);
            exportService = null;
        }
        pendingStart = false;
    }
    
    @Override
    public void show(FragmentManager manager, String tag) {
        super.show(manager, tag);
        if (exportService != null) {
            exportService.start();
        } else {
            pendingStart = true;
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getActivity().getApplicationContext();
        context.bindService(new Intent(context, ExportService.class), this,
            Context.BIND_AUTO_CREATE);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        context.unbindService(this);
    }
    
    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        context.stopService(new Intent(context, ExportService.class));
    }
    
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        dialog = new ProgressDialog(getActivity());
        dialog.setMessage(getString(R.string.exporting_data));
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        return dialog;
    }
}
