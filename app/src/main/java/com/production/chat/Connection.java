package com.production.chat;

import android.content.Intent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

//handles socket connection actions
public class Connection {

    //uses chat activity to run on ui thread
    private final ChatActivity chatActivity;
    //executor to keep send chats from writing to socket channel at the same time
    private final ThreadExecute threadExecute;
    //key and encryption handler
    private final KeyHandler keyHandler;
    //socket channel to peer
    private SocketChannel socketChannel = null;
    //server socket channel to create socket channel connection
    //kept as global so it is ensured to be closed
    private ServerSocketChannel serverSocketChannel = null;

    //store the chat activity and create the executor
    //socket channel, receive thread, and server socket channel are kept as null
    //when not connected or being used
    public Connection(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
        threadExecute = new ThreadExecute();
        keyHandler = new KeyHandler();
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
        return socketChannel != null;
    }

    //executes a runnable to write a message to the socket channel
    //uses executor to prevent multiples being written at the same time
    public void sendChat(Message message) {
        threadExecute.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer buffer = ByteBuffer.wrap(keyHandler.encryptMessage(message));
                    socketChannel.write(buffer);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean readData(ByteBuffer byteBuffer) {
        boolean success = false;
        try {
            int numRead = socketChannel.read(byteBuffer);
            if (numRead == -1) {
                Intent broadcast = new Intent();
                broadcast.setAction(ChatReceiver.SOCKET_DISCONNECT);
                chatActivity.sendBroadcast(broadcast);
                clearSockets();
            }
            else {
                byteBuffer.rewind();
                byteBuffer.limit(numRead);
                success = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return success;
    }

    public void clearSockets() {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            serverSocketChannel = null;
        }
    }

    public void exchangeKeys() {
        Thread receiveKey = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(294);
                    readData(byteBuffer);
                    keyHandler.setPeerPublicEncrypt(byteBuffer);
                    receiveChats();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Thread sendKey = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(keyHandler.getPublicEncrypt());
                    socketChannel.write(byteBuffer);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        if (keyHandler.needPeerPublic()) {
            receiveKey.start();
            sendKey.start();
        }
        else {
            receiveChats();
        }
    }

    private void receiveChats() {
        Thread receiveChats = new Thread(new Runnable() {
            @Override
            public void run() {
                ByteBuffer byteBuffer = ByteBuffer.allocate(256);
                while (readData(byteBuffer)) {
                    Message message = keyHandler.decryptMessage(byteBuffer);
                    chatActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            chatActivity.displayReceived(message);
                        }
                    });
                    byteBuffer.clear();
                }

            }
        });
        receiveChats.start();
    }
}
