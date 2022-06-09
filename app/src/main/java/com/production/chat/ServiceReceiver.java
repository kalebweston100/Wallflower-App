package com.production.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

//broadcast receiver for service to know when a connection is made
public class ServiceReceiver extends BroadcastReceiver {

    private final ConnectService connectService;

    public ServiceReceiver(ConnectService connectService) {
        super();
        this.connectService = connectService;
    }

    //when a connection is made, request the connection info
    //and the info listener will start the chat activity
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            if (wifiP2pInfo.groupOwnerAddress != null) {
                boolean owner = wifiP2pInfo.isGroupOwner;
                String ipAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
                //pass the necessary data for the chat activity to the method and send the notification
                connectService.sendNotification(owner, ipAddress);
            }
        }
    }
}
