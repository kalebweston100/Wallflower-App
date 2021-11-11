package com.production.chat;

import android.content.Intent;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

//persistently running thread to receive messages from peer
//extends thread so it's methods can be called directly to stop it
public class ReceiveThread extends Thread {

    //connection to clear socket channel with
    private final Connection connection;
    //socket channel to peer
    private final SocketChannel socketChannel;
    //context and layout used to update UI with received messages
    private final ChatActivity chatActivity;
    //buffer to receive messages from socket channel
    private final ByteBuffer buffer;
    //boolean to stop running the thread
    private boolean running;

    //what to initialize buffer to?
    public ReceiveThread(Connection connection, ChatActivity chatActivity) {
        this.connection = connection;
        this.socketChannel = connection.getSocketChannel();
        this.chatActivity = chatActivity;
        //max character count in chat activity is 500
        //max bytes is 4 per character so no overflow error
        buffer = ByteBuffer.allocate(2048);
        //thread should run on create
        running = true;
    }

    //called by connection on chat activity stop
    //keeps setup connection from being run after socket close
    public void stopRunning() {
        running = false;
    }

    //sends broadcast to chat receiver to start setup connection if waiting for other user
    private void sendBroadcast() {
        Intent broadcast = new Intent();
        broadcast.setAction(ChatReceiver.SOCKET_DISCONNECT);
        chatActivity.sendBroadcast(broadcast);
    }

    //runs thread until running boolean is changed from chat activity
    @Override
    public void run() {
        while (running) {
            try {
                //read from socket and add message to UI
                //blocking so it only reads when there is a message so no thread sleep is necessary
                int numRead = socketChannel.read(buffer);
                //if the socket reaches end of file then return the socket channel to null
                //and send a broadcast to restart setup connection attempt
                if (numRead == -1) {
                    connection.clearSocketChannel();
                    sendBroadcast();
                    break;
                }
                buffer.rewind();
                buffer.limit(numRead);
                CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
                Message message = new Message(charBuffer.toString());
                chatActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chatActivity.displayReceived(message);
                    }
                });
                buffer.clear();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
