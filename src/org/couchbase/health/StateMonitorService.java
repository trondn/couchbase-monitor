/*
 *     Copyright 2011 Couchbase, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.couchbase.health;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * The StateMonitorService is a service that Activities may use in order to get
 * notifications when something happens to the cluster.
 * 
 * It should really be rewritten to use dedicated threads to communicate over
 * the network.
 * 
 * @author Trond Norbye
 */
public class StateMonitorService extends Service {
    /**
     * Name of the preference database on the device
     */
    public static final String PREFS_NAME = "CouchbaseHealthPrefs";

    /**
     * The name of the action we're going to broadcast when the data change
     */
    public static final String BROADCAST_ACTION = "org.couchbase.health.statemonitorservice.updatedata";

    /**
     * The handler object we're using to request our next invocation to poll the
     * state
     */
    private Handler pollHandler = new Handler();

    /**
     * The state on the cluster
     */
    private State state;

    /**
     * The reader we're using to get the current health of the cluster
     */
    private PoolHealthReader healthReader;

    /**
     * The intent object so send every time we see a change in the configuration
     */
    private Intent broadcastIntent;

    /**
     * The hostname we're currently using (cached from the preferences to avoid
     * reading it off disk every time)
     */
    private String host;
    /**
     * The port we're currently using (cached from the preferences to avoid
     * reading it off disk every time)
     */
    private int port;
    /**
     * The number milliseconds to sleep between each time we're going to poll
     * the server
     */
    private int pollInterval;

    /**
     * The Runnable object to use to refresh the state and reschedule the next
     * poll for status.
     * 
     * This should be refactored to run in it's own thread to avoid blocking the
     * rest of of the server.
     */
    private Runnable poller = new Runnable() {

        public void run() {
            refreshState();
            reschedule();
        }
    };

    /**
     * Connect to the couchbase cluster and read the state f the server..
     * 
     * @return the "aggregated" state of the cluster.
     */
    private State doGetState() {
        State[] states;
        try {
            states = healthReader.getStates();
        } catch (IOException e) {
            e.printStackTrace();
            // @todo add a notification that it failed ;)
            return State.NETWORK_ERROR;
        }

        State ret = State.GOOD;
        for (State s : states) {
            if (s != State.GOOD) {
                ret = State.DEGRADED;
                break;
            }
        }

        return ret;
    }

    private void refreshState() {
        State next = doGetState();

        if (next != state) {
            // Broadcast a notification that we've got a state change
            broadcastIntent.putExtra("state", next.toString());
            sendBroadcast(broadcastIntent);
            State prev = state;
            state = next;

            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager notificationManager = (NotificationManager) getSystemService(ns);

            if (next == State.GOOD) {
                notificationManager.cancelAll();
            }

            if (prev == State.UNINITIALISED && next == State.GOOD) {
                return;
            }

            // Send notification events if we went from
            // @todo we should send an alarm per node ;)
            CharSequence tickerText = "" + state;
            long when = System.currentTimeMillis();
            int icon;
            switch (state) {
            case DEGRADED:
                icon = R.drawable.ic_stat_looking_bad;
                break;
            case GOOD:
                icon = R.drawable.ic_stat_looking_good;
                break;
            case NETWORK_ERROR:
                icon = R.drawable.ic_stat_update_error;
                break;
            default:
                icon = R.drawable.ic_stat_update_error;
            }

            Notification notification = new Notification(icon, tickerText, when);
            notification.flags = Notification.FLAG_AUTO_CANCEL;

            Context context = getApplicationContext();
            CharSequence contentTitle = "Couchbase notification";
            CharSequence contentText = "The state is now: " + state;
            Intent notificationIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://" + host + ":" + port + "/index.html"));

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            notification.setLatestEventInfo(context, contentTitle, contentText,
                    contentIntent);

            notificationManager.notify(0, notification);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastIntent = new Intent(BROADCAST_ACTION);

        // Restore preferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        host = settings.getString("host", "localhost");
        port = settings.getInt("port", 8091);
        pollInterval = settings.getInt("pollinterval", 5 * 60);
        pollInterval *= 1000;
        healthReader = new PoolHealthReader(host, port, "default");

        Log.d("org.couchbase.health", "Using Couchbase Server" + host + ":"
                + port);

        state = State.UNINITIALISED;
        pollHandler.post(poller);
    }

    private void reschedule() {
        pollHandler.removeCallbacks(poller);
        pollHandler.postDelayed(poller, pollInterval);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(poller);
    }

    private Binder binder = new MyBinder();

    private int clients;

    @Override
    public IBinder onBind(Intent intent) {
        ++clients;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (--clients == 0) {
            pollHandler.removeCallbacks(poller);
        }
        return super.onUnbind(intent);
    }

    public class MyBinder extends Binder {
        StateMonitorService getService() {
            return StateMonitorService.this;
        }
    }
}
