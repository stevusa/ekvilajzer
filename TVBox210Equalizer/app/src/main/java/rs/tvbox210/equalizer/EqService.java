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
import android.os.SystemClock;

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
                handler.postDelayed(this, 2000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        createChannel();
        startForeground(210, notification("NXP globalni EQ - session 0"));
        prefs.edit()
                .putString("last_error", "")
                .putLong("service_started_ms", System.currentTimeMillis())
                .apply();
        if (prefs.getBoolean("enabled", false)) ensureGlobalEqualizer();
        handler.postDelayed(keeper, 1000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            prefs.edit().putBoolean("enabled", false).apply();
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
            if (equalizer == null || !safeHasControl()) {
                releaseEqualizer();
                equalizer = new Equalizer(PRIORITY, 0);
                equalizer.setControlStatusListener((effect, controlGranted) ->
                        prefs.edit().putBoolean("callback_has_control", controlGranted).apply());
                equalizer.setEnableStatusListener((effect, enabled) ->
                        prefs.edit().putBoolean("callback_enabled", enabled).apply());
                saveDescriptor();
            }

            boolean before = safeGetEnabled();
            int setResult = AudioEffect.SUCCESS;
            if (!before) {
                setResult = equalizer.setEnabled(true);
            }
            boolean after = safeGetEnabled();

            applyBands();

            boolean finalEnabled = safeGetEnabled();
            boolean control = safeHasControl();
            prefs.edit()
                    .putInt("set_enabled_result", setResult)
                    .putBoolean("enabled_before", before)
                    .putBoolean("enabled_after", after)
                    .putBoolean("enabled_final", finalEnabled)
                    .putBoolean("has_control", control)
                    .putLong("last_check_ms", System.currentTimeMillis())
                    .putString("last_error", "")
                    .apply();

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(210, notification("NXP session 0 | set=" + setResult + " | enabled=" + finalEnabled + " | control=" + control));
            }
        } catch (Throwable t) {
            prefs.edit()
                    .putString("last_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()))
                    .putBoolean("enabled_final", false)
                    .apply();
            releaseEqualizer();
        }
    }

    private boolean safeHasControl() {
        try { return equalizer != null && equalizer.hasControl(); }
        catch (Throwable ignored) { return false; }
    }

    private boolean safeGetEnabled() {
        try { return equalizer != null && equalizer.getEnabled(); }
        catch (Throwable ignored) { return false; }
    }

    private void saveDescriptor() {
        if (equalizer == null) return;
        try {
            AudioEffect.Descriptor d = equalizer.getDescriptor();
            SharedPreferences.Editor e = prefs.edit()
                    .putString("effect_name", d.name == null ? "Equalizer" : d.name)
                    .putString("effect_impl", d.implementor == null ? "" : d.implementor)
                    .putString("effect_uuid", d.uuid == null ? "" : d.uuid.toString())
                    .putString("effect_type", d.type == null ? "" : d.type.toString())
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
        } catch (Throwable t) {
            prefs.edit().putString("descriptor_error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())).apply();
        }
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
                try {
                    prefs.edit().putInt("actual_band_" + b, equalizer.getBandLevel(b)).apply();
                } catch (Throwable ignored) { }
            }

            int secondEnableResult = equalizer.setEnabled(true);
            prefs.edit()
                    .putInt("second_enable_result", secondEnableResult)
                    .putBoolean("enabled_after_bands", safeGetEnabled())
                    .apply();
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
        if (prefs != null) {
            prefs.edit()
                    .putBoolean("has_control", false)
                    .putBoolean("enabled_final", false)
                    .apply();
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
        return b.setContentTitle("TV BOX 210 NXP Equalizer")
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
