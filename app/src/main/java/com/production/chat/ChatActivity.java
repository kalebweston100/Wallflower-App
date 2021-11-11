package com.production.chat;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

//activity that's started after p2p connection is established
public class ChatActivity extends AppCompatActivity {

    //store owner, ip address, and device address from initial intent started from notification
    //whether or not the device is the group owner of the p2p connection
    private boolean owner;
    //p2p ip address used for socket connection
    private String ipAddress;
    //device mac address of peer retrieved from response listener if this device is the client
    private String deviceAddress;

    //contains the socket channel and functionality for socket connection
    private Connection connection;
    //layout updated to display sent and received messages
    private LinearLayout messageLayout;
    //receiver for broadcast of socket or p2p connection close
    private ChatReceiver chatReceiver;
    //intent filter for receiver
    private IntentFilter intentFilter;
    //timer for the pinging client socket connection attempts
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        //store the values from the start intent in their variables
        owner = getIntent().getBooleanExtra(ConnectService.OWNER, false);
        ipAddress = getIntent().getStringExtra(ConnectService.IP_ADDRESS);
        deviceAddress = getIntent().getStringExtra(ConnectService.DEVICE_ADDRESS);
        //connection is initialized with a null socket channel
        connection = new Connection(this);
        setupLayout();
        setupReceiver();
        //remove the notification that takes user to the chat activity to prevent it from being started again
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(ConnectService.PEER_NOTIFICATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(chatReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(chatReceiver);
    }

    //the socket connection is closed and cleaned up on stop
    //so set it up again on start every time
    @Override
    protected void onStart() {
        super.onStart();
        setupConnection();
    }

    //if the app is closed then cancel the timer and stop receiving cleans up socket resources
    //setup connection reconnects the resources stopped by on stop when the app is restarted
    @Override
    protected void onStop() {
        super.onStop();
        timer.cancel();
        connection.stopReceiving();
    }

    //on destroy of the chat activity, stop the peer to peer connection by stopping the service
    //chat activity is destroyed by the done chatting button or the receiver on a connection disconnect
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent service = new Intent(this, ConnectService.class);
        stopService(service);
    }

    //sets up the layout to scroll to the bottom on a message sent or received
    private void setupLayout() {
        messageLayout = findViewById(R.id.messageLayout);
        messageLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                ScrollView scrollView = findViewById(R.id.messageScroll);
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    //receiver is registered with device changed action to detect peer to peer disconnect
    //and socket disconnect to detect socket disconnect broadcast from receive thread
    private void setupReceiver() {
        chatReceiver = new ChatReceiver(this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.addAction(ChatReceiver.SOCKET_DISCONNECT);
    }

    //sets up the socket connection
    //called from on start and the broadcast receiver
    public void setupConnection() {
        //initialize the timer every time the method is called to prevent an error with scheduling a canceled timer
        timer = new Timer();
        //used to access the chat activity from within the inner runnable
        ChatActivity self = this;
        //notifies the user of when the other user has stopped the app
        TextView chatNotify = findViewById(R.id.chatNotify);
        chatNotify.setText(getString(R.string.notify_waiting_peer));
        //if the group owner then start a socket channel to receive the client connection from the peer
        if (owner) {
            //thread because server socket channel is blocking and not necessary to run multiple times
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //start connection override for server socket
                    connection.startConnection();
                    //the socket channel is set if not null
                    //always reset to null when not connected
                    if (connection.socketChannelSet()) {
                        //starts the receive thread in connection
                        connection.startReceiving();
                        //reset the textview so the user knows they can send messages
                        self.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                chatNotify.setText(null);
                            }
                        });
                    }
                }
            });
            thread.start();
        }
        //if not owner then start a polling connection attempt to the server socket
        //so it connects when the other user starts the app and opens the server socket
        else {
            timer.schedule(new TimerTask() {
                //attempt the connection every 500 milliseconds
                @Override
                public void run() {
                    //start connection override for a client socket connect
                    connection.startConnection(ipAddress);
                    //if the connection attempt is successful then socket channel won't be null
                    if (connection.socketChannelSet()) {
                        //start the receive thread
                        connection.startReceiving();
                        //cancel the timer to stop the polling
                        timer.cancel();
                        //clear the message that they're waiting on the peer
                        self.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                chatNotify.setText(null);
                            }
                        });
                    }
                }
                //500 milliseconds seems to be a reasonable time period
            }, 0, 500);
        }
    }

    //used to block the address if the connection is a client and the mac was retrieved from the response listener
    public void blockAddress(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (deviceAddress != null) {
            builder.setTitle(getString(R.string.alert_block_title));
            builder.setMessage(getString(R.string.alert_block_content));
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    addBlocked(deviceAddress);
                    enableSafeMode();
                    endChat();
                }
            });
        }
        else {
            builder.setTitle(getString(R.string.alert_no_block_title));
            builder.setMessage(getString(R.string.alert_no_block_content));
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enableSafeMode();
                    endChat();
                }
            });
        }
        builder.setNegativeButton(android.R.string.no, null);
        builder.show();
    }

    //adds the blocked mac address to the shared preferences file
    private void addBlocked(String blockedAddress) {
        SharedPreferences sharedPreferences = getSharedPreferences(ConnectService.BLOCKED_MACS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        //key is used to search for the mac address so the value isn't relevant
        editor.putString(blockedAddress, "BLOCKED");
        editor.apply();
    }

    //enables safe mode in the shared preferences setting file
    //it is disabled again on main activity start or from settings
    private void enableSafeMode() {
        SharedPreferences sharedPreferences = getSharedPreferences(ConnectService.SETTINGS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(ConnectService.SAFE_MODE, true);
        editor.apply();
    }

    //calls the end chat method from the done chatting button
    public void chatBack(View view) {
        endChat();
    }

    //ends the chat activity, stops service in on destroy, and notifies the peer that the chat was ended
    private void endChat() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ChatReceiver.ENDED_CHAT);
        startActivity(intent);
        this.finish();
    }

    //sends a message to the peer over the socket channel
    public void sendChat(View view) {
        EditText editText = findViewById(R.id.messageText);
        String text = editText.getText().toString();
        if (connection.socketChannelSet() && !text.equals("")) {
            Message message = new Message(text);
            editText.setText(null);
            connection.sendChat(message);
            displaySent(message);
        }
    }

    //display the sent message in the message layout
    private void displaySent(Message message) {
        TextView textView = new TextView(this);
        textView.setText(message.getText());
        styleSent(textView);
        messageLayout.addView(textView);
    }

    //display received messages
    //called from the receive thread with run on ui thread method
    public void displayReceived(Message message) {
        TextView textView = new TextView(this);
        textView.setText(message.getText());
        styleReceived(textView);
        messageLayout.addView(textView);
    }

    //styling for send messages to be on the right of the screen
    private void styleSent(TextView textView) {
        styleTextView(textView);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layoutParams.gravity = Gravity.END;
        layoutParams.setMargins(10, 10, 10, 10);
        textView.setLayoutParams(layoutParams);
    }

    //styling for received messages to be on the left of the screen
    private void styleReceived(TextView textView) {
        styleTextView(textView);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layoutParams.gravity = Gravity.START;
        layoutParams.setMargins(10, 10, 10, 10);
        textView.setLayoutParams(layoutParams);
    }

    //styling for the message textview that is the same for both sent and received messages
    private void styleTextView(TextView textView) {
        textView.setBackgroundColor(getColor(R.color.teal_200));
        textView.setTextColor(Color.WHITE);
        textView.setSingleLine(false);
        textView.setMaxWidth(500);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textView.setPadding(10, 10, 10, 10);
    }
}