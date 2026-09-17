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

public class EqService extends Service {
    private static final String CHANNEL = "eq_active";
    private static final String NXP_UUID = "ce772f20-847d-11df-bb17-0002a5d5c51b";
    private static final int PRIORITY = 1000;

    private SharedPreferences prefs;
    private Equalizer equalizer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable keeper = new Runnable() {
        @Override public void run() {
            try {
                if (prefs != null && prefs.getBoolean("enabled", false)) {
                    ensureGlobalEqualizer();
                }
            } finally {
                handler.postDelayed(this, 4000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        createChannel();
        startForeground(210, notification("Globalni EQ - session 0"));
        if (prefs.getBoolean("enabled", false)) ensureGlobalEqualizer();
        handler.postDelayed(keeper, 1500);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            releaseEqualizer();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (prefs.getBoolean("enabled", false)) {
            ensureGlobalEqualizer();
        } else {
            releaseEqualizer();
        }
        return START_STICKY;
    }

    private synchronized void ensureGlobalEqualizer() {
        if (!prefs.getBoolean("enabled", false)) return;

        try {
            if (equalizer == null || !equalizer.hasControl()) {
                releaseEqualizer();
                equalizer = new Equalizer(PRIORITY, 0);
                saveDescriptor();
            }

            if (!equalizer.getEnabled()) equalizer.setEnabled(true);
            applyBands();
            prefs.edit().putString("last_error", "").apply();
        } catch (Throwable t) {
            prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
            releaseEqualizer();
        }
    }

    private void saveDescriptor() {
        if (equalizer == null) return;
        try {
            AudioEffect.Descriptor d = equalizer.getDescriptor();
            SharedPreferences.Editor e = prefs.edit()
                    .putString("effect_name", d.name == null ? "Equalizer" : d.name)
                    .putString("effect_impl", d.implementor == null ? "" : d.implementor)
                    .putString("effect_uuid", d.uuid == null ? "" : d.uuid.toString())
                    .putBoolean("nxp_confirmed", d.uuid != null && NXP_UUID.equalsIgnoreCase(d.uuid.toString()));

            short bands = equalizer.getNumberOfBands();
            e.putInt("band_count", bands);
            short[] range = equalizer.getBandLevelRange();
            if (range != null && range.length >= 2) {
                e.putInt("range_low", range[0]);
                e.putInt("range_high", range[1]);
            }
            for (short b = 0; b < bands && b < 10; b++) {
                e.putInt("freq_" + b, equalizer.getCenterFreq(b));
            }
            e.apply();

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                String text = (d.uuid != null && NXP_UUID.equalsIgnoreCase(d.uuid.toString()))
                        ? "NXP Equalizer ACTIVE - session 0"
                        : "Equalizer ACTIVE - session 0";
                nm.notify(210, notification(text));
            }
        } catch (Throwable ignored) { }
    }

    private synchronized void applyBands() {
        if (equalizer == null) return;
        try {
            short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            for (short b = 0; b < bands; b++) {
                int saved = prefs.getInt("band_" + b, 0);
                int value = saved;
                if (range != null && range.length >= 2) {
                    value = Math.max(range[0], Math.min(range[1], saved));
                }
                equalizer.setBandLevel(b, (short) value);
            }
            if (!equalizer.getEnabled()) equalizer.setEnabled(true);
        } catch (Throwable t) {
            prefs.edit().putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
        }
    }

    private synchronized void releaseEqualizer() {
        if (equalizer != null) {
            try { equalizer.setEnabled(false); } catch (Throwable ignored) { }
            try { equalizer.release(); } catch (Throwable ignored) { }
            equalizer = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "TV BOX 210 Equalizer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("TV BOX 210 Global Equalizer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(keeper);
        releaseEqualizer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
