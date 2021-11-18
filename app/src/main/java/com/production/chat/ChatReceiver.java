package com.production.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

//Broadcast receiver to respond to P2P and socket connection changes in Chat Activity
public class ChatReceiver extends BroadcastReceiver {

    //Socket disconnect broadcast sent from receive thread in Connection at end of file
    public static String SOCKET_DISCONNECT = "SOCKET_DISCONNECT";
    //Set as action when switching back to main if the peer ended the chat
    public static String CHAT_ENDED = "CHAT_ENDED";
    //Set action action when switching back to main if the user ended the chat
    public static String ENDED_CHAT = "ENDED_CHAT";
    //Used to start main or call the setup connection method
    private final ChatActivity chatActivity;

    public ChatReceiver(ChatActivity chatActivity) {
        super();
        this.chatActivity = chatActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //If the peer device ends the chat or closes the app completely then return to Main Activity and display a message
        if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //Get the current device info
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            //If the status of the current device is not connected then return to Main Activity
            if (device.status != WifiP2pDevice.CONNECTED) {
                Intent startMain = new Intent(chatActivity, MainActivity.class);
                //Set the action to notify the user that the peer ended the chat
                startMain.setAction(CHAT_ENDED);
                chatActivity.startActivity(startMain);
                //Finish the chat activity to call on destroy and end the service
                chatActivity.finish();
            }
        }
        //Otherwise if the socket disconnect broadcast is sent from the receive thread
        else if (SOCKET_DISCONNECT.equals(action)) {
            //Attempt to setup the socket connection again and wait for peer to return to the chat activity
            chatActivity.setupConnection();
        }
    }
}
