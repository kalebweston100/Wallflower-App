package com.production.chat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConnectService extends Service {

    //wifi peer to peer resources
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    //receiver to detect connection state change and request connection info
    private ServiceReceiver serviceReceiver;
    //used to send notifications over notification channel
    private NotificationManager notificationManager;
    //global so that they can be removed instead of clearing
    private WifiP2pUpnpServiceRequest serviceRequest;
    private WifiP2pUpnpServiceInfo serviceInfo;
    //make info listener global so device address can be set in response listener
    private InfoListener infoListener;

    //static int for consistency when chat activity cancels peer notification
    public static int SERVICE_NOTIFICATION = 1;
    public static int PEER_NOTIFICATION = 2;
    //static string for consistency with main activity to display stop looking button
    public static String SHOW_CANCEL = "SHOW_CANCEL";
    //constants for intent extras
    public static String OWNER = "OWNER";
    public static String IP_ADDRESS = "IP_ADDRESS";
    //intent extra for code from main activity
    public static String SECRET_CODE = "SECRET_CODE";

    //initialize service request and info in on create so it has time to be added before discovery in on start command
    @Override
    public void onCreate() throws SecurityException {
        super.onCreate();
        //initialize peer to peer resources
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        infoListener = new InfoListener(this);
        //add service request earliest because it must be set before service discovery in on start command
        //addServiceRequest();
        //get notification manager and create a channel to send notifications over
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(getPackageName(), getPackageName(), NotificationManager.IMPORTANCE_HIGH));
        //setup the receiver and register with intent filter
        setupReceiver();
        //create the service info so it can be removed in on destroy and won't receive a null exception
        //createServiceInfo();
        //add service info last because needs to have notifications and receiver setup in case it is discovered by a peer
    }

    //sets up the receiver to send a notification when the device is connected to a peer
    //requests connection info on connection change
    private void setupReceiver() {
        serviceReceiver = new ServiceReceiver(manager, channel, infoListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        registerReceiver(serviceReceiver, intentFilter);
    }

    //adds the service request for the app and sets the response listener to start connection when a peer is discovered
    private void addServiceRequest(String secretCode) throws SecurityException {
        String secretCodeService = "urn:schemas-upnp-org:service:Wallflower:" + secretCode;
        serviceRequest = WifiP2pUpnpServiceRequest.newInstance(secretCodeService);
        manager.addServiceRequest(channel, serviceRequest, null);
        manager.setUpnpServiceResponseListener(channel, new ResponseListener(manager, channel));
        manager.discoverServices(channel, null);
    }

    //adds the info to be discovered by a peer
    //security exception is never thrown because location permissions are checked before service is started
    private void createServiceInfo(String secretCode) throws SecurityException {
        String secretCodeService = "urn:schemas-upnp-org:service:Wallflower:" + secretCode;
        String serviceName = "Wallflower";
        String uuid = UUID.nameUUIDFromBytes(serviceName.getBytes()).toString();
        String device = "urn:schemas-upnp-org:device:Device:1";
        List<String> services = new ArrayList<>();
        services.add(secretCodeService);
        serviceInfo = WifiP2pUpnpServiceInfo.newInstance(uuid, device, services);
        manager.addLocalService(channel, serviceInfo, null);
    }

    //starts discovering peers on start command
    //service request must be added before discovery is called
    //security exception is never thrown because location permissions are checked before service is started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String secretCode = intent.getStringExtra(SECRET_CODE);
        addServiceRequest(secretCode);
        createServiceInfo(secretCode);
        serviceNotification();
        return START_STICKY;
    }

    //sends the foreground service notification on service start
    //pending intent that takes the user to the main activity with the stop looking button displayed
    private void serviceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        //set so the main activity knows the show the cancel button
        notificationIntent.setAction(SHOW_CANCEL);
        //flag cancel current also stops activities from stacking up
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        //get package name is the id of the notification channel
        Notification.Builder notificationBuilder = new Notification.Builder(this, getPackageName());
        //pending intent launches main activity on press
        notificationBuilder.setContentIntent(pendingIntent);
        //just looks like a circle, eventually add chat first icon
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round);
        notificationBuilder.setContentText(getString(R.string.service_notification));
        //start foreground notification because service is started as foreground service in main activity
        //throws error if foreground notification is not sent
        startForeground(SERVICE_NOTIFICATION, notificationBuilder.build());
    }

    //sends notification that when pressed starts the chat activity
    //pressing notification is the only way to start the chat activity to prevent stacking issues
    public void sendNotification(boolean owner, String ipAddress) {
        Intent notificationIntent = new Intent(this, ChatActivity.class);
        //receive owner and ip address data from the info listener that calls this method
        notificationIntent.putExtra(OWNER, owner);
        notificationIntent.putExtra(IP_ADDRESS, ipAddress);
        //update current so the intent extras are added
        //PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        //package name is notification channel
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, getPackageName());
        notificationBuilder.setContentIntent(pendingIntent);
        //also just round icon
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round);
        //make it clear that the user should tap the notification to go to the chat activity
        notificationBuilder.setContentText(getString(R.string.peer_notification));
        //peer notification id is used in chat activity to remove the notification once chat is started
        notificationManager.notify(PEER_NOTIFICATION, notificationBuilder.build());
    }

    //on destroy clean up the manager and resources
    @Override
    public void onDestroy() {
        super.onDestroy();
        //cancels any invitations etc to prevent issues in future connection attempts
        manager.cancelConnect(channel, null);
        //clear service requests and info because they're added every time the service is started
        manager.removeServiceRequest(channel, serviceRequest, null);
        manager.removeLocalService(channel, serviceInfo, null);
        //remove the current group to send the peer back to the main activity
        manager.removeGroup(channel, null);
        //unregister receiver and delete notification channel after manager actions to give time to complete in framework
        unregisterReceiver(serviceReceiver);
        notificationManager.deleteNotificationChannel(getPackageName());
        //close the channel after everything else
        channel.close();
    }

    //binding is not used with the service but method needs to be implemented
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
