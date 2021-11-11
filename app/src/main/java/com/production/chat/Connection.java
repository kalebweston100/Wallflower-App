package com.production.chat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

//handles socket connection actions
public class Connection {

    //uses chat activity to run on ui thread
    private final ChatActivity chatActivity;
    //executor to keep send chats from writing to socket channel at the same time
    private final ThreadExecute threadExecute;
    //socket channel to peer
    private SocketChannel socketChannel;
    //thread that reads from socket channel and displays messages
    private ReceiveThread receiveThread;
    //server socket channel to create socket channel connection
    //kept as global so it is ensured to be closed
    private ServerSocketChannel serverSocketChannel;

    //store the chat activity and create the executor
    //socket channel, receive thread, and server socket channel are kept as null
    //when not connected or being used
    public Connection(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
        threadExecute = new ThreadExecute();
        socketChannel = null;
        receiveThread = null;
        serverSocketChannel = null;
    }

    //start connection method for the
    public void startConnection() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(8000));
            socketChannel = serverSocketChannel.accept();
            serverSocketChannel.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    //connection attempt that is looped in chat activity until server socket is opened on other user's device
    public void startConnection(String ipAddress) {
        try {
            InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName(ipAddress), 8000);
            socketChannel = SocketChannel.open(socketAddress);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    //used to check if the socket channel is connected yet
    public boolean socketChannelSet() {
        boolean set = false;
        if (socketChannel != null) {
            set = true;
        }
        return set;
    }

    //used in receive thread to get socket channel
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    //used in receive thread to clear socket channel to show that it's not set
    public void clearSocketChannel() {
        try {
            socketChannel.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        socketChannel = null;
    }

    //executes a runnable to write a message to the socket channel
    //uses executor to prevent multiples being written at the same time
    public void sendChat(Message message) {
        threadExecute.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(message.getText());
                    socketChannel.write(buffer);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //runs the receive thread to receive messages from the peer
    public void startReceiving() {
        receiveThread = new ReceiveThread(this, chatActivity);
        receiveThread.start();
    }

    //closes the socket channel and server socket channel if still open
    //each in separate try block because both must always be closed
    public void stopReceiving() {
        if (receiveThread != null) {
            receiveThread.stopRunning();
            receiveThread = null;
        }
        if (socketChannel != null) {
            try {
                socketChannel.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            socketChannel = null;
        }
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            serverSocketChannel = null;
        }
    }
}
