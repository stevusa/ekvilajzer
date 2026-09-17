package rs.tvbox210.equalizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

public class EqService extends Service {
    private static final String CHANNEL = "eq_active";
    private static final String ACTION_ATTACH = "rs.tvbox210.equalizer.ATTACH_SESSION";
    private static final String ACTION_DETACH = "rs.tvbox210.equalizer.DETACH_SESSION";
    private static final int PRIORITY = 1000;

    private SharedPreferences prefs;
    private final Map<Integer, Equalizer> effects = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable keeper = new Runnable() {
        @Override public void run() {
            try {
                if (prefs != null && prefs.getBoolean("enabled", false)) {
                    for (Map.Entry<Integer, Equalizer> e : effects.entrySet()) {
                        try {
                            if (!e.getValue().getEnabled()) e.getValue().setEnabled(true);
                            applyBands(e.getValue());
                        } catch (Throwable ignored) { }
                    }
                }
            } finally {
                handler.postDelayed(this, 2000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        createChannel();
        startForeground(210, notification("Čekam audio session"));
        handler.postDelayed(keeper, 1000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        int session = intent.getIntExtra("session", intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0));

        if ("STOP".equals(action)) {
            releaseAll();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_DETACH.equals(action) || AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            detach(session);
            return START_STICKY;
        }

        if (ACTION_ATTACH.equals(action) || AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            if (session > 0) attach(session);
            return START_STICKY;
        }

        if ("APPLY".equals(action)) {
            for (Equalizer eq : effects.values()) applyBands(eq);
            return START_STICKY;
        }

        return START_STICKY;
    }

    private synchronized void attach(int session) {
        if (session <= 0 || !prefs.getBoolean("enabled", true)) return;
        try {
            Equalizer old = effects.remove(session);
            if (old != null) try { old.release(); } catch (Throwable ignored) { }

            Equalizer eq = new Equalizer(PRIORITY, session);
            int result = eq.setEnabled(true);
            applyBands(eq);
            effects.put(session, eq);

            AudioEffect.Descriptor d = eq.getDescriptor();
            prefs.edit()
                    .putInt("target_session", session)
                    .putInt("session_set_result", result)
                    .putBoolean("session_enabled", eq.getEnabled())
                    .putBoolean("session_control", eq.hasControl())
                    .putString("session_effect_name", d.name == null ? "" : d.name)
                    .putString("session_effect_impl", d.implementor == null ? "" : d.implementor)
                    .putString("session_effect_uuid", d.uuid == null ? "" : d.uuid.toString())
                    .putString("last_error", "")
                    .apply();

            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(210, notification("NXP EQ na session " + session + " | enabled=" + eq.getEnabled()));
        } catch (Throwable t) {
            prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
        }
    }

    private synchronized void detach(int session) {
        Equalizer eq = effects.remove(session);
        if (eq != null) {
            try { eq.setEnabled(false); } catch (Throwable ignored) { }
            try { eq.release(); } catch (Throwable ignored) { }
        }
    }

    private void applyBands(Equalizer eq) {
        try {
            short bands = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            for (short b = 0; b < bands; b++) {
                int saved = prefs.getInt("band_" + b, 0);
                int value = saved;
                if (range != null && range.length >= 2) value = Math.max(range[0], Math.min(range[1], value));
                eq.setBandLevel(b, (short)value);
            }
            if (!eq.getEnabled()) eq.setEnabled(true);
        } catch (Throwable ignored) { }
    }

    private synchronized void releaseAll() {
        for (Equalizer eq : effects.values()) {
            try { eq.setEnabled(false); } catch (Throwable ignored) { }
            try { eq.release(); } catch (Throwable ignored) { }
        }
        effects.clear();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "TV BOX 210 Equalizer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("TV BOX 210 NXP Equalizer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(keeper);
        releaseAll();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
