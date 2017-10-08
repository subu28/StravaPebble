package com.sanyaas.stravapebble;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

public class NotificationListener extends NotificationListenerService {

    private String TAG = this.getClass().getSimpleName();
    private NLServiceReceiver nlservicereciver;
    private Boolean pebbleAppStatus = false;
    private Integer paceSplitStartTime = 0;
    private Float paceSplitStartDistance = Float.valueOf(0);
    private String paceSplitPace = "0:00";
    @Override
    public void onCreate() {
        super.onCreate();
        nlservicereciver = new NLServiceReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.sanyaas.stravapebble.NOTIFICATION_LISTENER_SERVICE");
        registerReceiver(nlservicereciver,filter);
        PebbleKit.registerReceivedDataHandler(getApplicationContext(), receiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(nlservicereciver);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!"com.strava".equalsIgnoreCase(sbn.getPackageName())){
            return;
        }
        Log.i(TAG,"**********  onNotificationPosted");
        Log.i(TAG,"ID :" + sbn.getId() + "\t" + sbn.getNotification().tickerText + "\t" + sbn.getPackageName());
        Intent i = new  Intent("com.sanyaas.stravapebble.NOTIFICATION_LISTENER");
        i.putExtra("notification_event","onNotificationPosted :" + sbn.getPackageName() + "\n");
        if(!pebbleAppStatus){
            PebbleKit.startAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
            PebbleDictionary dict = new PebbleDictionary();

            PebbleKit.sendDataToPebble(getApplicationContext(), Constants.SPORTS_UUID, dict);
            pebbleAppStatus = true;
        }
        if ("stop".equalsIgnoreCase(sbn.getNotification().actions[0].title.toString())) {
            PebbleDictionary dict = new PebbleDictionary();

            String[] text = sbn.getNotification().extras.get("android.title").toString().split(" ");

            // Show a value for duration and distance
            dict.addString(Constants.SPORTS_TIME_KEY, text[2]);
            dict.addString(Constants.SPORTS_DISTANCE_KEY, text[4]);
            dict.addUint8(Constants.SPORTS_LABEL_KEY, (byte)Constants.SPORTS_DATA_PACE);
            dict.addString(Constants.SPORTS_DATA_KEY, paceMonitor(text[2],text[4]));

            dict.addUint8(Constants.SPORTS_UNITS_KEY, (byte) Constants.SPORTS_UNITS_METRIC);

            PebbleKit.sendDataToPebble(getApplicationContext(), Constants.SPORTS_UUID, dict);
        }
        sendBroadcast(i);

    }

    private String paceMonitor (String time, String distance) {
        Float dist = Float.parseFloat(distance);
        Float splitDistance = dist - paceSplitStartDistance;
        if (splitDistance > 0) {
            String[] timeSplit = time.split(":");
            Integer currentTime = (Integer.parseInt(timeSplit[0]) * 60) + Integer.parseInt(timeSplit[1]);
            Integer secondsPerKM = Math.round((currentTime-paceSplitStartTime)/splitDistance);
            Integer minutesPart = secondsPerKM/60;
            Integer secondsPart = secondsPerKM%60;
            String secondsPartString = secondsPart + "";
            if (secondsPart < 10) {
                secondsPartString = "0" + secondsPartString;
            }
            paceSplitPace = minutesPart + ":" + secondsPartString;
            paceSplitStartTime = currentTime;
            paceSplitStartDistance = dist;
            pebbleAppStatus = false;
        }
        return paceSplitPace;
    }

    @Override
    public void onNotificationRemoved (StatusBarNotification sbn) {
        if (!"com.strava".equalsIgnoreCase(sbn.getPackageName())){
            return;
        }
        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
        pebbleAppStatus = false;
    }

    class NLServiceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            Intent i1 = new  Intent("com.sanyaas.stravapebble.NOTIFICATION_LISTENER");
            i1.putExtra("notification_event","=====================");
            sendBroadcast(i1);
            int i=1;
            for (StatusBarNotification sbn : NotificationListener.this.getActiveNotifications()) {
                Intent i2 = new  Intent("com.sanyaas.stravapebble.NOTIFICATION_LISTENER");
                i2.putExtra("notification_event",i +" " + sbn.getPackageName() + "\n");
                sendBroadcast(i2);
                i++;

            }
            Intent i3 = new  Intent("com.sanyaas.stravapebble.NOTIFICATION_LISTENER");
            i3.putExtra("notification_event","===== Notification List ====");
            sendBroadcast(i3);

        }
    }

    PebbleKit.PebbleDataReceiver receiver = new PebbleKit.PebbleDataReceiver(Constants.SPORTS_UUID) {

        @Override
        public void receiveData(Context context, int id, PebbleDictionary data) {
            // Always ACKnowledge the last message to prevent timeouts
            PebbleKit.sendAckToPebble(getApplicationContext(), id);

            Long value = data.getUnsignedIntegerAsLong(Constants.SPORTS_STATE_KEY);
            if(value != null) {
                int state = value.intValue();
                if (state == Constants.SPORTS_STATE_PAUSED){
                    for (StatusBarNotification sbn : NotificationListener.this.getActiveNotifications()) {
                        if ("com.strava".equalsIgnoreCase(sbn.getPackageName()) && "start".equalsIgnoreCase(sbn.getNotification().actions[0].title.toString())){
                            try {
                                sbn.getNotification().actions[0].actionIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    for (StatusBarNotification sbn : NotificationListener.this.getActiveNotifications()) {
                        if ("com.strava".equalsIgnoreCase(sbn.getPackageName()) && "stop".equalsIgnoreCase(sbn.getNotification().actions[0].title.toString())){
                            try {
                                sbn.getNotification().actions[0].actionIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

    };
}
