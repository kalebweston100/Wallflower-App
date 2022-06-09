package com.production.chat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    //file to store info
    public static String INFO = "INFO";
    //name of user agreement boolean in settings file
    public static String USER_AGREED = "USER_AGREED";

    private WifiReceiver receiver;
    private IntentFilter intentFilter;
    private ActivityResultLauncher<String> permissionLauncher;
    private SharedPreferences.Editor editor;
    private boolean userAgreed;
    private boolean useBroadcast = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences sharedPreferences = getSharedPreferences(INFO, MODE_PRIVATE);
        editor = sharedPreferences.edit();
        userAgreed = sharedPreferences.getBoolean(USER_AGREED, false);
        //sets up broadcast receiver and filter
        setupReceiver();
        //has different ui responsibilities based on custom intent action
        setupFromAction(getIntent().getAction());
        //initializes the permission launcher if it is necessary
        setupLauncher();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setupFromAction(intent.getAction());
    }

    //resume receiver and ads after pause
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    //pause receiver and ads
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent service = new Intent(this, ConnectService.class);
        stopService(service);
    }

    private void setupReceiver() {
        receiver = new WifiReceiver(this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    //displays message depending on if they ended the chat or if the other user did
    //show cancel displays the stop service button if the service notification is pressed
    private void setupFromAction(String action) {
        if (ChatReceiver.CHAT_ENDED.equals(action)) {
            notifyChatEnd();
            showDiscover();
        }
        else if (ChatReceiver.ENDED_CHAT.equals(action)) {
            notifyEndChat();
            showDiscover();
        }
        else if (ConnectService.SHOW_CANCEL.equals(action)) {
            showCancel();
        }
    }

    //sets up the permission launcher to display messages based on the users response to the permissions request
    private void setupLauncher() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                TextView textView = findViewById(R.id.mainNotify);
                if (result) {
                    textView.setText(getString(R.string.notify_location_allowed));
                }
                else {
                    textView.setText(getString(R.string.notify_location_needed));
                }
            }
        });
    }

    public void toggleBroadcast(View view) {
        Switch broadcastSwitch = findViewById(R.id.broadcastSwitch);
        LinearLayout codeLayout = findViewById(R.id.codeLayout);
        if (broadcastSwitch.isChecked()) {
            broadcastSwitch.setText(R.string.broadcast_on);
            codeLayout.setVisibility(View.INVISIBLE);
            useBroadcast = true;
        }
        else {
            broadcastSwitch.setText(R.string.broadcast_off);
            codeLayout.setVisibility(View.VISIBLE);
            useBroadcast = false;
        }
    }

    //method to stop the service
    //called on the press of the cancel button which is hidden by default
    public void cancelDiscover(View view) {
        Intent service = new Intent(this, ConnectService.class);
        stopService(service);
        showDiscover();
        TextView mainNotify = findViewById(R.id.mainNotify);
        mainNotify.setText(getString(R.string.main_label));
    }

    //shows the discover button and hides the cancel button
    private void showDiscover() {
        Button discoverButton = findViewById(R.id.discoverButton);
        Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setVisibility(View.GONE);
        discoverButton.setVisibility(View.VISIBLE);
    }

    //shows the cancel button and hides the discover button
    private void showCancel() {
        Button discoverButton = findViewById(R.id.discoverButton);
        Button cancelButton = findViewById(R.id.cancelButton);
        discoverButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        TextView mainNotify = findViewById(R.id.mainNotify);
        mainNotify.setText(getString(R.string.notify_searching));
    }

    private void showUserPolicy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.user_agree_title);
        builder.setMessage(R.string.user_agree_content);
        builder.setPositiveButton(R.string.user_agree_accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                userAgreed = true;
                editor.putBoolean(USER_AGREED, true);
                editor.apply();
            }
        });
        builder.setNegativeButton(R.string.user_agree_decline, null);
        builder.show();
    }

    private void startLooking() {
        boolean validCode = true;
        String secretCode;

        if (useBroadcast) {
            secretCode = ConnectService.CODE_BROADCAST;
        }
        else {
            EditText codeInput = findViewById(R.id.codeInput);
            secretCode = codeInput.getText().toString();

            if (secretCode.length() != 6) {
                TextView mainNotify = findViewById(R.id.mainNotify);
                mainNotify.setText(getString(R.string.notify_pin_length));
                validCode = false;
            }
        }

        if (validCode) {
            Intent service = new Intent(this, ConnectService.class);
            service.putExtra(ConnectService.SECRET_CODE, secretCode);
            startForegroundService(service);
            showCancel();
        }
    }

    //requests permissions before starting the foreground service
    //displays message and shows cancel button to stop service
    public void discover(View view) {

        if (userAgreed) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLooking();
            }
            else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        else {
            showUserPolicy();
        }
    }

    public void showSwitchInfo(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.switch_info);
        builder.setNeutralButton(R.string.info_okay, null);
        builder.show();
    }

    public void showCodeInfo(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.code_info);
        builder.setNeutralButton(R.string.info_okay, null);
        builder.show();
    }

    //called from wifi receiver if wifi direct is not available
    public void notifyDisabled() {
        TextView textView = findViewById(R.id.mainNotify);
        textView.setText(getString(R.string.notify_disabled));
    }

    //notifies the user that the peer ended the chat
    public void notifyChatEnd() {
        TextView textView = findViewById(R.id.mainNotify);
        textView.setText(getString(R.string.notify_chat_end));
    }

    //notifies that they ended the chat
    public void notifyEndChat() {
        TextView textView = findViewById(R.id.mainNotify);
        textView.setText(getString(R.string.notify_end_chat));
    }
}