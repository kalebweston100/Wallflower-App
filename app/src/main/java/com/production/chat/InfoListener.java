package com.production.chat;

import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

//sends notification that starts chat activity when connection info is available
public class InfoListener implements WifiP2pManager.ConnectionInfoListener {

    //used to send notification in service by calling method
    private final ConnectService connectService;

    public InfoListener(ConnectService connectService) {
        this.connectService = connectService;
    }

    //when connection info is received, check if it is a reachable peer using the app
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        //if connection info is available and the group owner is not null
        if (info.groupOwnerAddress != null) {
            boolean owner = info.isGroupOwner;
            String ipAddress = info.groupOwnerAddress.getHostAddress();
            //pass the necessary data for the chat activity to the method and send the notification
            connectService.sendNotification(owner, ipAddress);
        }
    }
}
