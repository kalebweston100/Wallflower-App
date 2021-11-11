package com.production.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

//broadcast receiver to respond to peer to peer and socket connection changes
public class ChatReceiver extends BroadcastReceiver {

    //static strings to keep the intent actions consistent
    //socket disconnect broadcast sent from receive thread at end of file
    public static String SOCKET_DISCONNECT = "SOCKET_DISCONNECT";
    //set as action when switching back to main if the peer ended the chat
    public static String CHAT_ENDED = "CHAT_ENDED";
    //set action action when switching back to main if the user ended the chat
    public static String ENDED_CHAT = "ENDED_CHAT";
    //used to start main or call setup connection
    private final ChatActivity chatActivity;

    //save the chat activity context
    public ChatReceiver(ChatActivity chatActivity) {
        super();
        this.chatActivity = chatActivity;
    }

    //responds to received broadcasts
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //if the peer device ends the chat or closes the app then return to main and display a message
        if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //get the current device info
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            //if the status of the current device is not connected then return to main
            if (device.status != WifiP2pDevice.CONNECTED) {
                Intent startMain = new Intent(chatActivity, MainActivity.class);
                //set the action to notify the user that the peer ended the chat
                startMain.setAction(CHAT_ENDED);
                chatActivity.startActivity(startMain);
                //finish the chat activity to call on destroy and end the service
                chatActivity.finish();
            }
        }
        //otherwise if the socket disconnect broadcast is received from the receive thread
        else if (SOCKET_DISCONNECT.equals(action)) {
            //attempt to setup the socket connection again and wait for peer to return to the chat activity
            chatActivity.setupConnection();
        }
    }
}
