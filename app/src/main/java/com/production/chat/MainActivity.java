package com.production.chat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

public class MainActivity extends AppCompatActivity {

    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    private ActivityResultLauncher<String> permissionLauncher;
    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //sets up broadcast receiver and filter
        setupReceiver();
        //has different ui responsibilities based on custom intent action
        setupFromAction(getIntent().getAction());
        //initializes the permission launcher if it is necessary
        setupLauncher();
        //sets up ads
        setupAds();
        //shared preferences not written to in on destroy
        disableSafeMode();
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
        adView.resume();
    }

    //pause receiver and ads
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        adView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adView.destroy();
        Intent service = new Intent(this, ConnectService.class);
        stopService(service);
    }

    private void disableSafeMode() {
        SharedPreferences sharedPreferences = getSharedPreferences(ConnectService.SETTINGS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(ConnectService.SAFE_MODE, false);
        editor.apply();
    }

    private void setupReceiver() {
        receiver = new WifiReceiver(this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    public void toSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
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

    //sets up the adview to display ads
    private void setupAds() {
        adView = new AdView(this);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId("ca-app-pub-1697464259000196/2475813198");
        AdRequest.Builder builder = new AdRequest.Builder();
        adView.loadAd(builder.build());
        LinearLayout adLayout = findViewById(R.id.adLayout);
        adLayout.addView(adView);
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

    //requests permissions before starting the foreground service
    //displays message and shows cancel button to stop service
    public void discover(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Intent service = new Intent(this, ConnectService.class);
            startForegroundService(service);
            showCancel();
        }
        else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
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