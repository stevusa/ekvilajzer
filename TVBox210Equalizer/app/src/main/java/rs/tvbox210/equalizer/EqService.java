package rs.tvbox210.equalizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
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

    private static class Chain {
        Equalizer eq;
        BassBoost bass;
    }

    private SharedPreferences prefs;
    private final Map<Integer, Chain> effects = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable keeper = new Runnable() {
        @Override public void run() {
            try {
                if (prefs != null && prefs.getBoolean("enabled", false)) enableAll();
            } catch (Throwable ignored) {
            } finally {
                handler.postDelayed(this, 2000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        createChannel();
        startForeground(210, notification("Čekam audio plejer"));
        handler.postDelayed(keeper, 1000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) return START_STICKY;
            String action = intent.getAction();
            int session = intent.getIntExtra("session", intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0));

            if ("STOP".equals(action)) {
                releaseAll();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            if ("DISABLE".equals(action)) {
                prefs.edit().putBoolean("enabled", false).apply();
                disableAll();
                notifyState("EQ isključen | sesije sačuvane: " + effects.size());
                return START_STICKY;
            }

            if ("ENABLE".equals(action)) {
                prefs.edit().putBoolean("enabled", true).apply();
                enableAll();
                notifyState(effects.isEmpty() ? "EQ uključen | čekam audio plejer" : "EQ uključen | sesija: " + prefs.getInt("target_session", 0));
                return START_STICKY;
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
                applyAll();
                return START_STICKY;
            }
        } catch (Throwable t) {
            saveError(t);
        }
        return START_STICKY;
    }

    private synchronized void attach(int session) {
        if (session <= 0) return;

        Chain old = effects.remove(session);
        if (old != null) releaseChain(old);

        Chain chain = new Chain();
        try {
            chain.eq = new Equalizer(PRIORITY, session);

            try {
                chain.bass = new BassBoost(PRIORITY - 1, session);
            } catch (Throwable ignored) {
                chain.bass = null;
            }

            effects.put(session, chain);
            applyChain(chain);
            setEnabled(chain, prefs.getBoolean("enabled", false));

            AudioEffect.Descriptor d = chain.eq.getDescriptor();
            prefs.edit()
                    .putInt("target_session", session)
                    .putBoolean("session_enabled", safeEnabled(chain.eq))
                    .putBoolean("session_control", safeControl(chain.eq))
                    .putBoolean("bassboost_available", chain.bass != null)
                    .putString("session_effect_name", d.name == null ? "" : d.name)
                    .putString("session_effect_impl", d.implementor == null ? "" : d.implementor)
                    .putString("session_effect_uuid", d.uuid == null ? "" : d.uuid.toString())
                    .putInt("attached_sessions", effects.size())
                    .putString("last_error", "")
                    .apply();
        } catch (Throwable t) {
            releaseChain(chain);
            effects.remove(session);
            saveError(t);
        }
    }

    private synchronized void enableAll() {
        boolean any = false;
        for (Map.Entry<Integer, Chain> entry : effects.entrySet()) {
            try {
                applyChain(entry.getValue());
                setEnabled(entry.getValue(), true);
                any |= safeEnabled(entry.getValue().eq);
                prefs.edit().putInt("target_session", entry.getKey()).apply();
            } catch (Throwable t) {
                saveError(t);
            }
        }
        prefs.edit().putBoolean("session_enabled", any).putInt("attached_sessions", effects.size()).apply();
    }

    private synchronized void disableAll() {
        for (Chain chain : effects.values()) {
            try { setEnabled(chain, false); } catch (Throwable ignored) { }
        }
        prefs.edit().putBoolean("session_enabled", false).putInt("attached_sessions", effects.size()).apply();
    }

    private synchronized void applyAll() {
        for (Chain chain : effects.values()) {
            try { applyChain(chain); } catch (Throwable t) { saveError(t); }
        }
        if (prefs.getBoolean("enabled", false)) enableAll();
    }

    private void applyChain(Chain chain) {
        if (chain == null || chain.eq == null) return;
        try {
            short bands = chain.eq.getNumberOfBands();
            short[] range = chain.eq.getBandLevelRange();
            int low = range != null && range.length >= 2 ? range[0] : -1500;
            int high = range != null && range.length >= 2 ? range[1] : 1500;

            for (short b = 0; b < bands; b++) {
                int wanted = prefs.getInt("band_" + b, 0);
                int applied = Math.max(low, Math.min(high, wanted));
                chain.eq.setBandLevel(b, (short) applied);
                prefs.edit().putInt("actual_band_" + b, applied).apply();
            }

            if (chain.bass != null) {
                int b0 = Math.max(0, prefs.getInt("band_0", 0));
                int b1 = Math.max(0, prefs.getInt("band_1", 0));
                int strength = Math.max(b0, b1) * 1000 / 3000;
                strength = Math.max(0, Math.min(1000, strength));
                try { chain.bass.setStrength((short) strength); } catch (Throwable ignored) { }
                prefs.edit().putInt("bass_strength", strength).apply();
            }
        } catch (Throwable t) {
            saveError(t);
        }
    }

    private void setEnabled(Chain chain, boolean enabled) {
        if (chain == null) return;
        if (chain.eq != null) {
            try { chain.eq.setEnabled(enabled); } catch (Throwable ignored) { }
        }
        if (chain.bass != null) {
            try { chain.bass.setEnabled(enabled); } catch (Throwable ignored) { }
        }
    }

    private boolean safeEnabled(AudioEffect effect) {
        try { return effect != null && effect.getEnabled(); } catch (Throwable ignored) { return false; }
    }

    private boolean safeControl(AudioEffect effect) {
        try { return effect != null && effect.hasControl(); } catch (Throwable ignored) { return false; }
    }

    private synchronized void detach(int session) {
        Chain chain = effects.remove(session);
        if (chain != null) releaseChain(chain);
        prefs.edit().putInt("attached_sessions", effects.size()).apply();
    }

    private void releaseChain(Chain chain) {
        if (chain == null) return;
        if (chain.eq != null) {
            try { chain.eq.setEnabled(false); } catch (Throwable ignored) { }
            try { chain.eq.release(); } catch (Throwable ignored) { }
        }
        if (chain.bass != null) {
            try { chain.bass.setEnabled(false); } catch (Throwable ignored) { }
            try { chain.bass.release(); } catch (Throwable ignored) { }
        }
    }

    private synchronized void releaseAll() {
        for (Chain chain : effects.values()) releaseChain(chain);
        effects.clear();
        if (prefs != null) prefs.edit().putBoolean("session_enabled", false).putInt("attached_sessions", 0).apply();
    }

    private void saveError(Throwable t) {
        try {
            prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
        } catch (Throwable ignored) { }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "TV BOX 210 Equalizer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private void notifyState(String text) {
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(210, notification(text));
        } catch (Throwable ignored) { }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("TV BOX 210 NXP Equalizer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        try { handler.removeCallbacks(keeper); } catch (Throwable ignored) { }
        releaseAll();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
