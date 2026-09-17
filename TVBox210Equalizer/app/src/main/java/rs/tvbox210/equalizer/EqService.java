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
    private static final String ACTION_ENABLE = "ENABLE";
    private static final String ACTION_DISABLE = "DISABLE";
    private static final int PRIORITY = 1000;

    private static class Chain {
        Equalizer first;
        Equalizer second;
    }

    private SharedPreferences prefs;
    private final Map<Integer, Chain> effects = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable keeper = new Runnable() {
        @Override public void run() {
            try {
                if (prefs != null && prefs.getBoolean("enabled", false)) enableAll();
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
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        int session = intent.getIntExtra("session", intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0));

        if ("STOP".equals(action)) {
            releaseAll();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_DISABLE.equals(action)) {
            prefs.edit().putBoolean("enabled", false).apply();
            disableAll();
            notifyState("EQ isključen | sesije sačuvane: " + effects.size());
            return START_STICKY;
        }

        if (ACTION_ENABLE.equals(action)) {
            prefs.edit().putBoolean("enabled", true).apply();
            enableAll();
            notifyState(effects.isEmpty()
                    ? "EQ uključen | čekam audio plejer"
                    : "EQ uključen | aktivnih sesija: " + effects.size());
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
            applyBandsToAll();
            if (prefs.getBoolean("enabled", false)) enableAll();
            return START_STICKY;
        }

        return START_STICKY;
    }

    private synchronized void attach(int session) {
        if (session <= 0) return;

        Chain old = effects.remove(session);
        if (old != null) releaseChain(old);

        Chain chain = new Chain();
        try {
            chain.first = new Equalizer(PRIORITY, session);

            try {
                chain.second = new Equalizer(PRIORITY - 1, session);
            } catch (Throwable secondError) {
                chain.second = null;
                prefs.edit().putString("second_eq_error",
                        secondError.getClass().getSimpleName() + ": " + String.valueOf(secondError.getMessage())).apply();
            }

            effects.put(session, chain);
            applyBands(chain);

            boolean wantEnabled = prefs.getBoolean("enabled", false);
            setChainEnabled(chain, wantEnabled);

            AudioEffect.Descriptor d = chain.first.getDescriptor();
            prefs.edit()
                    .putInt("target_session", session)
                    .putBoolean("session_enabled", isChainEnabled(chain))
                    .putBoolean("session_control", chain.first.hasControl())
                    .putBoolean("dual_nxp", chain.second != null)
                    .putString("session_effect_name", d.name == null ? "" : d.name)
                    .putString("session_effect_impl", d.implementor == null ? "" : d.implementor)
                    .putString("session_effect_uuid", d.uuid == null ? "" : d.uuid.toString())
                    .putInt("attached_sessions", effects.size())
                    .putString("last_error", "")
                    .apply();

            notifyState((wantEnabled ? "NXP EQ aktivan" : "Sesija zapamćena")
                    + " | session " + session
                    + (chain.second != null ? " | DUAL" : " | SINGLE"));
        } catch (Throwable t) {
            releaseChain(chain);
            effects.remove(session);
            prefs.edit()
                    .putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()))
                    .apply();
        }
    }

    private synchronized void enableAll() {
        boolean anyEnabled = false;
        for (Map.Entry<Integer, Chain> entry : effects.entrySet()) {
            Chain chain = entry.getValue();
            try {
                applyBands(chain);
                setChainEnabled(chain, true);
                anyEnabled |= isChainEnabled(chain);
                prefs.edit()
                        .putInt("target_session", entry.getKey())
                        .putBoolean("session_enabled", isChainEnabled(chain))
                        .putBoolean("session_control", chain.first != null && chain.first.hasControl())
                        .putBoolean("dual_nxp", chain.second != null)
                        .apply();
            } catch (Throwable t) {
                prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
            }
        }
        prefs.edit()
                .putBoolean("session_enabled", anyEnabled)
                .putInt("attached_sessions", effects.size())
                .apply();
    }

    private synchronized void disableAll() {
        for (Chain chain : effects.values()) {
            try { setChainEnabled(chain, false); } catch (Throwable ignored) { }
        }
        prefs.edit()
                .putBoolean("session_enabled", false)
                .putInt("attached_sessions", effects.size())
                .apply();
    }

    private synchronized void applyBandsToAll() {
        for (Chain chain : effects.values()) applyBands(chain);
    }

    private synchronized void detach(int session) {
        Chain chain = effects.remove(session);
        if (chain != null) releaseChain(chain);
        prefs.edit().putInt("attached_sessions", effects.size()).apply();
    }

    private void applyBands(Chain chain) {
        if (chain == null || chain.first == null) return;
        try {
            short bands = chain.first.getNumberOfBands();
            short[] r1 = chain.first.getBandLevelRange();
            short[] r2 = chain.second != null ? chain.second.getBandLevelRange() : null;

            for (short b = 0; b < bands; b++) {
                int wanted = prefs.getInt("band_" + b, 0);

                int firstValue = clamp(wanted,
                        r1 != null && r1.length >= 2 ? r1[0] : -1500,
                        r1 != null && r1.length >= 2 ? r1[1] : 1500);
                int remaining = wanted - firstValue;
                int secondValue = 0;

                if (chain.second != null) {
                    secondValue = clamp(remaining,
                            r2 != null && r2.length >= 2 ? r2[0] : -1500,
                            r2 != null && r2.length >= 2 ? r2[1] : 1500);
                }

                chain.first.setBandLevel(b, (short) firstValue);
                if (chain.second != null && b < chain.second.getNumberOfBands()) {
                    chain.second.setBandLevel(b, (short) secondValue);
                }

                int actual = 0;
                try { actual += chain.first.getBandLevel(b); } catch (Throwable ignored) { }
                if (chain.second != null) {
                    try { actual += chain.second.getBandLevel(b); } catch (Throwable ignored) { }
                }
                prefs.edit().putInt("actual_band_" + b, actual).apply();
            }
        } catch (Throwable t) {
            prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
        }
    }

    private int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private void setChainEnabled(Chain chain, boolean enabled) {
        if (chain == null) return;
        if (chain.first != null) {
            try { chain.first.setEnabled(enabled); } catch (Throwable ignored) { }
        }
        if (chain.second != null) {
            try { chain.second.setEnabled(enabled); } catch (Throwable ignored) { }
        }
    }

    private boolean isChainEnabled(Chain chain) {
        if (chain == null || chain.first == null) return false;
        boolean firstEnabled;
        try { firstEnabled = chain.first.getEnabled(); } catch (Throwable ignored) { firstEnabled = false; }
        if (chain.second == null) return firstEnabled;
        boolean secondEnabled;
        try { secondEnabled = chain.second.getEnabled(); } catch (Throwable ignored) { secondEnabled = false; }
        return firstEnabled && secondEnabled;
    }

    private void releaseChain(Chain chain) {
        if (chain == null) return;
        if (chain.first != null) {
            try { chain.first.setEnabled(false); } catch (Throwable ignored) { }
            try { chain.first.release(); } catch (Throwable ignored) { }
        }
        if (chain.second != null) {
            try { chain.second.setEnabled(false); } catch (Throwable ignored) { }
            try { chain.second.release(); } catch (Throwable ignored) { }
        }
    }

    private synchronized void releaseAll() {
        for (Chain chain : effects.values()) releaseChain(chain);
        effects.clear();
        if (prefs != null) {
            prefs.edit()
                    .putBoolean("session_enabled", false)
                    .putInt("attached_sessions", 0)
                    .apply();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "TV BOX 210 Equalizer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private void notifyState(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(210, notification(text));
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
        handler.removeCallbacks(keeper);
        releaseAll();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
