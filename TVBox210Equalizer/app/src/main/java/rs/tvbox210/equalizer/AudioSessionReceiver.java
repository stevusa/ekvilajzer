package rs.tvbox210.equalizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Build;

public class AudioSessionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        Intent s = new Intent(context, EqService.class);
        s.setAction(intent.getAction());
        s.putExtra("session", session);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);
    }
}
