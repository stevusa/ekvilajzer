package rs.tvbox210.equalizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("eq", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("enabled", false)) return;

            Intent service = new Intent(context, EqService.class);
            service.setAction("ENABLE");
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Throwable ignored) {
        }
    }
}
