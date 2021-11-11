package com.production.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

//broadcast receiver for service to know when a connection is made
public class ServiceReceiver extends BroadcastReceiver {

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final InfoListener infoListener;

    public ServiceReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, InfoListener infoListener) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.infoListener = infoListener;
    }

    //when a connection is made, request the connection info
    //and the info listener will start the chat activity
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            manager.requestConnectionInfo(channel, infoListener);
        }
    }
}
