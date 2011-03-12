/*
 * Copyright (C) 2011 Alexandre Roman
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
package com.pixmob.actionservice;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Base class for executing actions. When an action is executed, a lock is held
 * to prevent the CPU from sleeping. Like <code>IntentService</code>, actions
 * are queued if an action is currently running, but the queue size is fixed.
 * The service is automatically terminated when no action is received for a
 * configurable amount of time.
 * <p>
 * This class is a replacement for <code>IntentService</code>, as power
 * management and limited environment execution are supported by
 * <code>ActionService</code>.
 * </p>
 * <p>
 * The following permission is required:
 * </p>
 * <ul>
 * <li><code>android.permission.WAKE_LOCK</code>.</li>
 * </ul>
 * @author Pixmob
 */
public abstract class ActionService extends Service {
    private static final String TAG = "ActionService";
    private final String serviceName;
    private final long idleTimeout;
    private final int intentBacklogSize;
    private final AtomicBoolean cancelAction = new AtomicBoolean();
    private BlockingQueue<Intent> intentBacklog;
    private Thread actionDispatcher;
    private PowerManager.WakeLock wakeLock;
    
    /**
     * Create a new instance.
     * @param serviceName name used to identify the action dispatcher thread
     * @param idleTimeout timeout in ms to wait for an action before the service
     *            is stopped
     * @param intentBacklogSize number of pending actions
     */
    public ActionService(final String serviceName, final long idleTimeout,
            final int intentBacklogSize) {
        super();
        if (serviceName == null) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (idleTimeout < 0) {
            throw new IllegalArgumentException(
                    "Idle timeout must be positive: " + idleTimeout);
        }
        if (intentBacklogSize < 0) {
            throw new IllegalArgumentException(
                    "Intent backlog size must be positive: "
                            + intentBacklogSize);
        }
        this.serviceName = serviceName;
        this.idleTimeout = idleTimeout;
        this.intentBacklogSize = intentBacklogSize;
    }
    
    /**
     * Create a new instance, with reasonable defaults.
     * @see #ActionService(String, long, int)
     */
    public ActionService(final String serviceName) {
        this(serviceName, 60 * 1000, 4);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            ActionService.class.getName() + "#" + serviceName);
        
        intentBacklog = new ArrayBlockingQueue<Intent>(intentBacklogSize);
        
        actionDispatcher = new ActionDispatcher();
        actionDispatcher.start();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (intentBacklog != null) {
            intentBacklog.clear();
            intentBacklog = null;
        }
        if (actionDispatcher != null) {
            actionDispatcher.interrupt();
            actionDispatcher = null;
        }
        wakeLock = null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean actionCancelled = false;
        try {
            actionCancelled = isActionCancelled(intent);
        } catch (Exception e) {
            Log.e(TAG,
                serviceName + " got an error when executing isCancelled", e);
        }
        
        if (actionCancelled) {
            Log.d(TAG, "Cancelling current action for " + serviceName);
            cancelAction.set(true);
            actionDispatcher.interrupt();
        } else if (!intentBacklog.offer(intent)) {
            try {
                onActionRejected(intent);
            } catch (Exception e) {
                Log.e(TAG, serviceName
                        + " got an error when executing onActionRejected", e);
            }
        } else {
            Log.d(TAG, "Action queued for " + serviceName + ": " + intent);
        }
        return START_NOT_STICKY;
    }
    
    /**
     * This method is invoked when an action could not be queued for execution.
     * @param intent rejected action
     */
    protected void onActionRejected(Intent intent) {
        Log.w(TAG, "Action backlog is full for " + serviceName
                + ": cannot queue intent " + intent);
    }
    
    /**
     * This method is invoked when an action failed to execute.
     * @param intent action which failed
     * @param error action error
     */
    protected void onActionError(Intent intent, Exception error) {
        Log.w(TAG, serviceName + " got an error when executing action: "
                + intent, error);
    }
    
    /**
     * Check if this intent should interrupt the current action. The internal
     * thread executing the action will be interrupted: the methode
     * {@link #handleAction(Intent)} will have to handle the
     * {@link InterruptedException} error.
     * @param intent to check
     * @return <code>true</code> if the current action should be interrupted
     */
    protected boolean isActionCancelled(Intent intent) {
        return false;
    }
    
    /**
     * This method is invoked for executing an action. The action may be
     * interrupted if a "cancel" intent is received: interruptible calls (such
     * as I/O) may throw the {@link InterruptedException} error.
     * <p>
     * A <code>WakeLock</code> is acquired while this action is executing.
     * </p>
     * @param intent to process
     * @throws InterruptedException if the action is cancelled
     * @throws ActionExecutionFailedException if the action failed to execute
     * @see #isActionCancelled(Intent)
     */
    protected abstract void handleAction(Intent intent)
            throws ActionExecutionFailedException, InterruptedException;
    
    /**
     * Internal thread for executing actions.
     * @author Pixmob
     */
    private class ActionDispatcher extends Thread {
        public ActionDispatcher() {
            super(serviceName);
        }
        
        @Override
        public void run() {
            boolean running = true;
            Intent nextIntent = null;
            
            // a wakelock is acquired: this prevents the CPU from sleeping
            wakeLock.acquire();
            
            try {
                Log.d(TAG, serviceName + " is running");
                
                while (running) {
                    try {
                        nextIntent = intentBacklog.poll(idleTimeout,
                            TimeUnit.MILLISECONDS);
                        if (nextIntent == null) {
                            Log.d(TAG, "No action was recently received: "
                                    + serviceName + " is stopping");
                            running = false;
                        } else {
                            Log.d(TAG, serviceName + " is executing action "
                                    + nextIntent);
                            
                            // reset the cancel flag
                            cancelAction.set(false);
                            
                            // delegate to handleAction
                            handleAction(nextIntent);
                        }
                    } catch (InterruptedException e) {
                        if (Thread.interrupted() && !cancelAction.get()) {
                            Log.d(TAG, serviceName + " was interrupted");
                            running = false;
                        } else {
                            // the action was canceled as the result of
                            // interrupting the thread
                            Log.d(TAG, "Action was canceled: rescheduling "
                                    + serviceName);
                        }
                    } catch (Exception e) {
                        onActionError(nextIntent, e);
                    }
                }
                
                Log.d(TAG, serviceName + " is stopped");
            } finally {
                // don't forget to release the wakelock!
                wakeLock.release();
            }
            
            stopSelf();
        }
    }
}
