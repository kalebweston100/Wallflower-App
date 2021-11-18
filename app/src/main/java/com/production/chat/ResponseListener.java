package com.production.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

import java.util.List;

//registered to respond to a service request response
public class ResponseListener implements WifiP2pManager.UpnpServiceResponseListener {

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;

    public ResponseListener(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        this.manager = manager;
        this.channel = channel;
    }

    //attempts connect with a device that is also running the app
    //security exception is not an issues because it is never called without location permissions in main activity
    @Override
    public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) throws SecurityException {
        String deviceAddress = srcDevice.deviceAddress;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        manager.connect(channel, config, null);
    }
}
