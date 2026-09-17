package rs.tvbox210.equalizer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int UI_MIN = -3000;
    private static final int UI_MAX = 3000;
    private static final int UI_OFFSET = 3000;
    private static final int UI_PROGRESS_MAX = 6000;

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private TextView status;
    private Button toggleButton;
    private final SeekBar[] bars = new SeekBar[5];
    private final TextView[] labels = new TextView[5];

    private final Runnable uiUpdater = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        toggleButton = findViewById(R.id.toggleButton);
        bars[0] = findViewById(R.id.band0);
        bars[1] = findViewById(R.id.band1);
        bars[2] = findViewById(R.id.band2);
        bars[3] = findViewById(R.id.band3);
        bars[4] = findViewById(R.id.band4);
        labels[0] = findViewById(R.id.label0);
        labels[1] = findViewById(R.id.label1);
        labels[2] = findViewById(R.id.label2);
        labels[3] = findViewById(R.id.label3);
        labels[4] = findViewById(R.id.label4);

        for (int i = 0; i < bars.length; i++) bindBand(i);
        refreshUi();
        if (prefs.getBoolean("enabled", false)) sendServiceAction("ENABLE");
    }

    private void bindBand(final int index) {
        SeekBar bar = bars[index];
        bar.setMax(UI_PROGRESS_MAX);
        int level = clampUi(prefs.getInt("band_" + index, 0));
        bar.setProgress(level + UI_OFFSET);
        updateLabel(index, level);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabel(index, progress - UI_OFFSET);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int value = clampUi(seekBar.getProgress() - UI_OFFSET);
                prefs.edit().putInt("band_" + index, value).putString("preset", "RUČNO").apply();
                if (prefs.getBoolean("enabled", false)) applyEffects();
            }
        });
    }

    private int clampUi(int value) {
        return Math.max(UI_MIN, Math.min(UI_MAX, value));
    }

    private void updateLabel(int index, int levelMb) {
        int freqMilliHz = prefs.getInt("freq_" + index, 0);
        String freq = freqMilliHz > 0 ? formatFrequency(freqMilliHz) : defaultFrequency(index);
        int actual = prefs.getInt("actual_band_" + index, Math.max(-1500, Math.min(1500, levelMb)));
        labels[index].setText(freq + "   " + formatDb(levelMb) + "   DSP " + formatDb(actual));
    }

    private String defaultFrequency(int index) {
        String[] f = {"60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"};
        return f[Math.max(0, Math.min(index, f.length - 1))];
    }

    private String formatFrequency(int milliHz) {
        double hz = milliHz / 1000.0;
        if (hz >= 1000.0) return String.format(java.util.Locale.US, "%.1f kHz", hz / 1000.0);
        return String.format(java.util.Locale.US, "%.0f Hz", hz);
    }

    private String formatDb(int milliBel) {
        return String.format(java.util.Locale.US, "%+.1f dB", milliBel / 100.0);
    }

    public void toggle(View v) {
        boolean enabled = !prefs.getBoolean("enabled", false);
        prefs.edit().putBoolean("enabled", enabled).apply();
        sendServiceAction(enabled ? "ENABLE" : "DISABLE");
        handler.postDelayed(this::refreshUi, 250);
    }

    public void presetRock(View v)      { setPreset("ROCK",      new int[]{3000, 1800, -200, 1500, 3000}); }
    public void presetDance(View v)     { setPreset("DANCE",     new int[]{2600, 1800, 100, 900, 1400}); }
    public void presetPop(View v)       { setPreset("POP",       new int[]{800, 1000, 1200, 900, 800}); }
    public void presetJazz(View v)      { setPreset("JAZZ",      new int[]{900, 600, 400, 1000, 1600}); }
    public void presetClassical(View v) { setPreset("CLASSICAL", new int[]{500, 200, -200, 600, 1600}); }
    public void presetHipHop(View v)    { setPreset("HIP-HOP",   new int[]{3000, 2200, 300, 500, 900}); }
    public void presetBass(View v)      { setPreset("BAS MAX",   new int[]{3000, 2800, 700, -400, -900}); }
    public void presetMusic(View v)     { setPreset("MUZIKA",    new int[]{1100, 700, -200, 800, 1200}); }
    public void presetMovie(View v)     { setPreset("FILM",      new int[]{1400, 800, 0, 1000, 1300}); }
    public void presetVoice(View v)     { setPreset("GOVOR",     new int[]{-700, -300, 600, 1400, 800}); }
    public void presetFlat(View v)      { setPreset("RAVNO",     new int[]{0, 0, 0, 0, 0}); }

    private void setPreset(String name, int[] levels) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("enabled", true).putString("preset", name);
        for (int i = 0; i < 5; i++) e.putInt("band_" + i, clampUi(levels[i]));
        e.apply();
        for (int i = 0; i < 5; i++) {
            int value = clampUi(levels[i]);
            bars[i].setProgress(value + UI_OFFSET);
            updateLabel(i, value);
        }
        sendServiceAction("ENABLE");
        applyEffects();
        handler.postDelayed(this::refreshUi, 250);
    }

    private void applyEffects() {
        sendServiceAction("APPLY");
    }

    private void sendServiceAction(String action) {
        try {
            Intent i = new Intent(this, EqService.class);
            i.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable t) {
            try { status.setText("Greška: " + t.getClass().getSimpleName()); } catch (Throwable ignored) { }
        }
    }

    private void refreshUi() {
        try {
            boolean enabled = prefs.getBoolean("enabled", false);
            toggleButton.setText(enabled ? "ISKLJUČI EKVILAJZER" : "UKLJUČI EKVILAJZER");

            for (int i = 0; i < 5; i++) updateLabel(i, bars[i].getProgress() - UI_OFFSET);

            String preset = prefs.getString("preset", "RAVNO");
            int session = prefs.getInt("target_session", 0);
            int attached = prefs.getInt("attached_sessions", 0);
            boolean sessionEnabled = prefs.getBoolean("session_enabled", false);
            boolean bassAvailable = prefs.getBoolean("bassboost_available", false);
            int bassStrength = prefs.getInt("bass_strength", 0);
            String impl = prefs.getString("session_effect_impl", "");
            String error = prefs.getString("last_error", "");

            if (error != null && !error.isEmpty()) {
                status.setText("Greška: " + error);
                return;
            }

            if (!enabled) {
                status.setText("Preset: " + preset + " | EQ ISKLJUČEN | sačuvanih sesija: " + attached);
            } else if (session > 0) {
                status.setText("Preset: " + preset + " | session " + session + " | " + impl
                        + " | EQ=" + sessionEnabled
                        + (bassAvailable ? " | BASS=" + bassStrength : ""));
            } else {
                status.setText("Preset: " + preset + " | EQ UKLJUČEN — čeka audio plejer");
            }
        } catch (Throwable ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(uiUpdater);
        handler.post(uiUpdater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(uiUpdater);
        super.onPause();
    }
}
