package com.production.chat;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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

import java.lang.reflect.Type;
import java.util.Timer;
import java.util.TimerTask;

//Activity that is started by the notification intent when opened after a P2P connection is established
public class ChatActivity extends AppCompatActivity {

    //Owner and IP address are extras from the notification intent
    //whether or not this device is the group owner of the Wifi Direct P2P connection
    private boolean owner;
    //IP address of the group owner in the Wifi Direct P2P group
    private String ipAddress;

    //Contains the socket channel and functionality for socket connection
    private Connection connection;
    //Layout is updated to display sent and received messages
    private LinearLayout messageLayout;
    //Receiver of broadcast for end of P2P connection or loss of socket connection
    private ChatReceiver chatReceiver;
    //Intent filter for receiver
    private IntentFilter intentFilter;
    //Timer to attempt to reconnect to the group owner if the socket connection is lost
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        //Store the extras from the starting intent
        owner = getIntent().getBooleanExtra(ConnectService.OWNER, false);
        ipAddress = getIntent().getStringExtra(ConnectService.IP_ADDRESS);
        //Connection is initialized with a null socket channel
        connection = new Connection(this);
        setupLayout();
        setupReceiver();
        //Remove the notification that takes user to the chat activity to prevent it from being started again
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

    //The socket connection is restarted every time the app is opened
    @Override
    protected void onStart() {
        super.onStart();
        setupConnection();
    }

    //When the app is closed cancel the timer and clear the socket connection
    @Override
    protected void onStop() {
        super.onStop();
        timer.cancel();
        connection.clearSockets();
    }

    //Stop the P2P connection by stopping the service
    //Chat Activity is destroyed by the done button or in the receiver on a connection disconnect
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent service = new Intent(this, ConnectService.class);
        stopService(service);
    }

    //Sets up the layout to scroll to the bottom when a message is sent or received
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

    //Receiver is registered with device changed action to detect P2P disconnect
    //and socket disconnect to detect socket disconnect broadcast from Connection receive thread
    private void setupReceiver() {
        chatReceiver = new ChatReceiver(this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.addAction(ChatReceiver.SOCKET_DISCONNECT);
    }

    //Sets up the socket connection
    //Called from onStart and the broadcast receiver
    public void setupConnection() {
        //Initialize the timer every time the method is called to prevent an error with scheduling a canceled timer
        timer = new Timer();
        //Used to access the chat activity from within the inner runnable
        ChatActivity self = this;
        //Notifies the user of when the other user has stopped the app
        TextView chatNotify = findViewById(R.id.chatNotify);
        chatNotify.setText(getString(R.string.notify_waiting_peer));
        //If the group owner then start a socket channel to receive the client connection from the peer
        if (owner) {
            //Only a single thread because server socket channel is blocking and not necessary to run multiple times
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //Start connection override for server socket
                    connection.startConnection();
                    //The socket channel is set if not null and is reset to null when not connected
                    //always reset to null when not connected
                    if (connection.socketChannelSet()) {
                        //Starts the receive thread in Connection
                        //connection.receiveChats();
                        connection.exchangeKeys();
                        //Resets the textview so the user knows they can send messages
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
        //If not owner then start a polling connection attempt to the server socket
        //so it connects when the other user starts the app and opens the server socket
        else {
            timer.schedule(new TimerTask() {
                //attempt the connection every 500 milliseconds
                @Override
                public void run() {
                    //Start connection override for a client socket connect
                    connection.startConnection(ipAddress);
                    //If the connection attempt is successful then socket channel won't be null
                    if (connection.socketChannelSet()) {
                        //Start the receive thread
                        //connection.receiveChats();
                        connection.exchangeKeys();
                        //Cancel the timer to stop the polling
                        timer.cancel();
                        //Clear the message that is shown when waiting on peer
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

    //Ends the connection when the Done button is pressed in Chat Activity
    public void chatBack(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ChatReceiver.ENDED_CHAT);
        startActivity(intent);
        this.finish();
    }

    //Sends a message to the peer over the socket channel
    public void sendChat(View view) {
        EditText editText = findViewById(R.id.messageText);
        String text = editText.getText().toString();
        //If connected and the message isn't blank
        if (connection.socketChannelSet() && !text.equals("")) {
            Message message = new Message(text);
            //Clears the text from previous message
            editText.setText(null);
            //Sends message with thread in Connection
            connection.sendChat(message);
            displaySent(message);
        }
    }

    //Displays the sent message in the message layout
    private void displaySent(Message message) {
        TextView textView = new TextView(this);
        textView.setText(message.getText());
        styleSent(textView);
        messageLayout.addView(textView);
    }

    //Called with runOnUiThread from receive thread in Connection to display received messages
    public void displayReceived(Message message) {
        TextView textView = new TextView(this);
        textView.setText(message.getText());
        styleReceived(textView);
        messageLayout.addView(textView);
    }

    //Styling for sent messages to be on the right of the screen
    private void styleSent(TextView textView) {
        styleTextView(textView);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layoutParams.gravity = Gravity.END;
        layoutParams.setMargins(10, 10, 10, 10);
        textView.setLayoutParams(layoutParams);
    }

    //Styling for received messages to be on the left of the screen
    private void styleReceived(TextView textView) {
        styleTextView(textView);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layoutParams.gravity = Gravity.START;
        layoutParams.setMargins(10, 10, 10, 10);
        textView.setLayoutParams(layoutParams);
    }

    //Styling for the message textview that is the same for both sent and received messages
    private void styleTextView(TextView textView) {
        textView.setBackgroundColor(getColor(R.color.dark_blue));
        textView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        textView.setTextColor(getResources().getColor(R.color.yellow));
        textView.setSingleLine(false);
        textView.setMaxWidth(500);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textView.setPadding(10, 10, 10, 10);
    }
}