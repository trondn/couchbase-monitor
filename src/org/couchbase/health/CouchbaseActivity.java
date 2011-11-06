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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

public class CouchbaseActivity extends Activity {

    private static final int CHECK_FOR_TTS = 1;
    private Intent stateMonitorIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        stateMonitorIntent = new Intent(this, StateMonitorService.class);

        Button saveButton = (Button) findViewById(R.id.save_button);
        saveButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                EditText edit = (EditText) findViewById(R.id.servername_field);
                String text = edit.getText().toString().trim();
                if (text.length() == 0) {
                    // @todo add error dialog with the fact that you need to
                    // enter something
                    return;
                }
                int port = 8091;
                String[] parts = text.split(":");

                if (parts.length == 2) {
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (Throwable t) {
                        // @todo write an error
                        return;
                    }
                }

                int pollInterval = 0;
                try {
                    edit = (EditText) findViewById(R.id.poll_field);
                    pollInterval = Integer.parseInt(edit.getText().toString()
                            .trim());
                } catch (Throwable t) {
                    // @todo add error dialog
                    return;
                }

                updateUI();
                SharedPreferences settings = getSharedPreferences(
                        StateMonitorService.PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("host", parts[0]);
                editor.putInt("port", port);
                editor.putInt("pollinterval", pollInterval);
                editor.commit();

                unregisterReceiver(receiver);
                stopService(stateMonitorIntent);
                startService(stateMonitorIntent);
                registerReceiver(receiver, new IntentFilter(
                        StateMonitorService.BROADCAST_ACTION));
            }
        });

        updateUI();

        Intent checkTTSAvailability = new Intent();
        checkTTSAvailability
                .setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTTSAvailability, CHECK_FOR_TTS);
    }

    /**
     * THe hanle to the speech subsystem.
     */
    private TextToSpeech speech;

    /**
     * Called by the framework when the requested activity terminated
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHECK_FOR_TTS) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                speech = new TextToSpeech(getApplicationContext(),
                        new TextToSpeech.OnInitListener() {

                            public void onInit(int status) {

                            }
                        });
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent
                        .setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Let the UI reflect the configuration settings
     */
    private void updateUI() {
        SharedPreferences settings = getSharedPreferences(
                StateMonitorService.PREFS_NAME, 0);
        String host = settings.getString("host", "localhost");
        int port = settings.getInt("port", 8091);
        EditText edit = (EditText) findViewById(R.id.servername_field);
        edit.setText(host + ":" + port);
        edit = (EditText) findViewById(R.id.poll_field);
        edit.setText("" + settings.getInt("pollinterval", 5 * 60));
    }

    /**
     * We are going to subscribe to notification messages from our monitor
     * service.
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onDataChanged(intent);
        }
    };

    /**
     * Handle the data notifications sent from the health monitor. Update the
     * Icon on the screen and use the text service to notify the user.
     * 
     * @param intent
     *            Currently not used...
     */
    public void onDataChanged(Intent intent) {
        State state = State.valueOf(intent.getStringExtra("state"));
        ImageView img = (ImageView) findViewById(R.id.widget_logo);
        if (state == State.GOOD) {
            if (speech != null) {
                String message = "Relax!! Everythings seems perfect!";
                speech.speak(message, TextToSpeech.QUEUE_FLUSH, null);
            }
            img.setImageResource(R.drawable.ic_launcher_logo_green);
        } else {
            if (speech != null) {
                String message = "HELP! There seems to be a failure";
                speech.speak(message, TextToSpeech.QUEUE_FLUSH, null);
            }
            img.setImageResource(R.drawable.ic_launcher_logo);
        }
    }

    /**
     * Called by the framework when the the user press the menu button. Inflate
     * the dialog and allow the user to kill the application
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    /**
     * Called by the framework when the user selected something in the menu.
     * Handle the option if it's one I'm interested in.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.exit:
            unregisterReceiver(receiver);
            stopService(stateMonitorIntent);
            System.exit(0);
            break;

        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Called by the framework when the Activity is about to be started. Start
     * the health monitor service.
     */
    @Override
    public void onResume() {
        super.onResume();
        startService(stateMonitorIntent);
        registerReceiver(receiver, new IntentFilter(
                StateMonitorService.BROADCAST_ACTION));
    }

    /**
     * Called by the framework when the Activity is about to be paused
     * (execution is about to be moved to another Activity). Stop the health
     * monitor service.
     */
    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        stopService(stateMonitorIntent);
    }
}