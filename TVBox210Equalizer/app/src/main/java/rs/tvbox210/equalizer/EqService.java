package rs.tvbox210.equalizer;

import android.app.*;
import android.content.*;
import android.media.audiofx.*;
import android.os.*;
import java.util.*;

public class EqService extends Service {
    private static final String CHANNEL = "eq_active";
    private final Map<Integer, EffectSet> effects = new HashMap<>();
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        createChannel();
        startForeground(210, notification("Ekvilajzer je aktivan"));
        if (prefs.getBoolean("enabled", true)) openSession(0);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("APPLY".equals(action)) {
                applyAll();
            } else if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                openSession(intent.getIntExtra("session", 0));
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                closeSession(intent.getIntExtra("session", 0));
            }
        }
        return START_STICKY;
    }

    private void openSession(int session) {
        if (!prefs.getBoolean("enabled", true) || effects.containsKey(session)) return;
        try {
            EffectSet e = new EffectSet(session);
            effects.put(session, e);
            apply(e);
        } catch (Throwable ignored) { }
    }

    private void closeSession(int session) {
        EffectSet e = effects.remove(session);
        if (e != null) e.release();
    }

    private void applyAll() {
        boolean on = prefs.getBoolean("enabled", true);
        if (on && effects.isEmpty()) openSession(0);
        for (EffectSet e : effects.values()) apply(e);
    }

    private void apply(EffectSet e) {
        boolean on = prefs.getBoolean("enabled", true);
        try { e.eq.setEnabled(on); } catch (Throwable ignored) {}
        try { e.bass.setEnabled(on && prefs.getInt("bassboost", 0) > 0); } catch (Throwable ignored) {}
        try { e.virt.setEnabled(on && prefs.getInt("virtualizer", 0) > 0); } catch (Throwable ignored) {}
        try { e.loud.setEnabled(on && prefs.getInt("loudness", 0) > 0); } catch (Throwable ignored) {}
        if (!on) return;

        try {
            short bands = e.eq.getNumberOfBands();
            for (short b = 0; b < bands; b++) {
                int saved = prefs.getInt("band_" + b, 0);
                short[] range = e.eq.getBandLevelRange();
                int v = Math.max(range[0], Math.min(range[1], saved));
                e.eq.setBandLevel(b, (short)v);
            }
        } catch (Throwable ignored) {}
        try { e.bass.setStrength((short)Math.max(0, Math.min(1000, prefs.getInt("bassboost", 0)))); } catch (Throwable ignored) {}
        try { e.virt.setStrength((short)Math.max(0, Math.min(1000, prefs.getInt("virtualizer", 0)))); } catch (Throwable ignored) {}
        try { e.loud.setTargetGain(Math.max(0, Math.min(2000, prefs.getInt("loudness", 0)))); } catch (Throwable ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "TV BOX 210 Equalizer", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("TV BOX 210 Equalizer").setContentText(text).setSmallIcon(android.R.drawable.ic_media_play).build();
    }

    @Override public void onDestroy() {
        for (EffectSet e : effects.values()) e.release();
        effects.clear();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    static class EffectSet {
        Equalizer eq; BassBoost bass; Virtualizer virt; LoudnessEnhancer loud;
        EffectSet(int s) {
            eq = new Equalizer(0, s);
            bass = new BassBoost(0, s);
            virt = new Virtualizer(0, s);
            loud = new LoudnessEnhancer(s);
        }
        void release() {
            try { eq.release(); } catch (Throwable ignored) {}
            try { bass.release(); } catch (Throwable ignored) {}
            try { virt.release(); } catch (Throwable ignored) {}
            try { loud.release(); } catch (Throwable ignored) {}
        }
    }
}
