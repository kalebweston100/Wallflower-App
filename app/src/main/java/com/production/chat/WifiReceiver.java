package com.production.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

//receives broadcasts in main activity to notify is wifi direct is not available
public class WifiReceiver extends BroadcastReceiver {

    //main activity to call notify disabled method
    private final MainActivity mainActivity;

    //call super to setup broadcast receiving
    public WifiReceiver(MainActivity mainActivity) {
        super();
        this.mainActivity = mainActivity;
    }

    //if the wifi p2p state is not enabled then call notify method in main
    //already running in the ui thread so run on ui thread isn't necessary
    @Override
    public void onReceive(Context context, Intent intent) throws SecurityException {
        //the value of the broadcast
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                mainActivity.notifyDisabled();
            }
        }
    }
}
