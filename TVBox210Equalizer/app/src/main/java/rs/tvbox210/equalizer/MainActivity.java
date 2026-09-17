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
        if (prefs.getBoolean("enabled", false)) applyEffects();
    }

    private void bindBand(final int index) {
        SeekBar bar = bars[index];
        bar.setMax(3000);
        int level = prefs.getInt("band_" + index, 0);
        bar.setProgress(Math.max(0, Math.min(3000, level + 1500)));
        updateLabel(index, level);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabel(index, progress - 1500);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int value = seekBar.getProgress() - 1500;
                prefs.edit().putInt("band_" + index, value).apply();
                if (prefs.getBoolean("enabled", false)) applyEffects();
            }
        });
    }

    private void updateLabel(int index, int levelMb) {
        int freqMilliHz = prefs.getInt("freq_" + index, 0);
        String freq = freqMilliHz > 0 ? formatFrequency(freqMilliHz) : defaultFrequency(index);
        labels[index].setText(freq + "   " + formatDb(levelMb));
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
        if (enabled) {
            applyEffects();
        } else {
            Intent i = new Intent(this, EqService.class);
            i.setAction("STOP");
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            } catch (Throwable ignored) {
                stopService(new Intent(this, EqService.class));
            }
        }
        handler.postDelayed(this::refreshUi, 300);
    }

    public void presetFlat(View v)  { setPreset("RAVNO",  new int[]{0, 0, 0, 0, 0}); }
    public void presetBass(View v)  { setPreset("BAS +",  new int[]{900, 600, 200, 0, 0}); }
    public void presetMusic(View v) { setPreset("MUZIKA", new int[]{350, 150, -100, 200, 400}); }
    public void presetMovie(View v) { setPreset("FILM",   new int[]{450, 200, 0, 300, 450}); }
    public void presetVoice(View v) { setPreset("GOVOR", new int[]{-300, -100, 250, 550, 300}); }

    private void setPreset(String name, int[] levels) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("enabled", true).putString("preset", name);
        for (int i = 0; i < 5; i++) e.putInt("band_" + i, levels[i]);
        e.apply();
        for (int i = 0; i < 5; i++) {
            bars[i].setProgress(levels[i] + 1500);
            updateLabel(i, levels[i]);
        }
        applyEffects();
        handler.postDelayed(this::refreshUi, 300);
    }

    private void applyEffects() {
        try {
            Intent i = new Intent(this, EqService.class);
            i.setAction("APPLY");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable t) {
            status.setText("Greška: " + t.getClass().getSimpleName());
        }
    }

    private void refreshUi() {
        boolean enabled = prefs.getBoolean("enabled", false);
        toggleButton.setText(enabled ? "ISKLJUČI EKVILAJZER" : "UKLJUČI EKVILAJZER");

        for (int i = 0; i < 5; i++) updateLabel(i, bars[i].getProgress() - 1500);

        if (!enabled) {
            status.setText("Ekvilajzer je isključen");
            return;
        }

        int session = prefs.getInt("target_session", 0);
        boolean sessionEnabled = prefs.getBoolean("session_enabled", false);
        String impl = prefs.getString("session_effect_impl", "");
        String error = prefs.getString("last_error", "");

        if (error != null && !error.isEmpty()) {
            status.setText("Greška: " + error);
        } else if (session > 0) {
            status.setText("Aktivan | session " + session + " | " + impl + " | enabled=" + sessionEnabled);
        } else {
            status.setText("Aktivan — čeka audio plejer");
        }
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
