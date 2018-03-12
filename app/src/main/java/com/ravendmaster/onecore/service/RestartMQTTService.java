package com.ravendmaster.onecore.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class RestartMQTTService extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("RestartMQTTService", "onReceive");

        Intent broadcastIntent = new Intent(new Intent(context, MQTTService.class));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(broadcastIntent);
        } else {
            context.startService(broadcastIntent);
        }
        //context.startForegroundService(new Intent(context, MQTTService.class));;
    }

}
