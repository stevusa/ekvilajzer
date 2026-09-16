package rs.tvbox210.equalizer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private SeekBar bass, virtualizer, loudness;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        bass = findViewById(R.id.bass);
        virtualizer = findViewById(R.id.virtualizer);
        loudness = findViewById(R.id.loudness);
        bind(bass, "bassboost");
        bind(virtualizer, "virtualizer");
        bind(loudness, "loudness");
        status.setText("Spreman - izaberi preset ili UKLJUČI");
    }

    private void bind(SeekBar s, String key) {
        int max = key.equals("loudness") ? 2000 : 1000;
        s.setMax(max);
        s.setProgress(prefs.getInt(key, 0));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                prefs.edit().putInt(key, value).apply();
                if (prefs.getBoolean("enabled", false)) applyEffects();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    public void toggle(View v) {
        boolean enabled = !prefs.getBoolean("enabled", false);
        prefs.edit().putBoolean("enabled", enabled).apply();
        ((Button) v).setText(enabled ? "ISKLJUČI" : "UKLJUČI");
        if (enabled) applyEffects();
        else {
            try { stopService(new Intent(this, EqService.class)); } catch (Throwable ignored) {}
            status.setText("Isključen");
        }
    }

    public void presetFlat(View v)      { setPreset("FLAT",      0,   0,   0,   new int[]{0,0,0,0,0}); }
    public void presetRock(View v)      { setPreset("ROCK",      500, 180, 400, new int[]{450,250,-100,250,500}); }
    public void presetDance(View v)     { setPreset("DANCE",     700, 240, 500, new int[]{650,350,0,250,450}); }
    public void presetPop(View v)       { setPreset("POP",       350, 160, 350, new int[]{200,350,450,300,150}); }
    public void presetJazz(View v)      { setPreset("JAZZ",      250, 220, 300, new int[]{300,150,150,300,500}); }
    public void presetClassical(View v) { setPreset("CLASSICAL", 120, 300, 220, new int[]{250,100,0,250,600}); }
    public void presetHipHop(View v)    { setPreset("HIP-HOP",   850, 180, 550, new int[]{800,550,100,250,300}); }
    public void presetMovie(View v)     { setPreset("MOVIE",     550, 350, 700, new int[]{450,250,100,300,450}); }

    private void setPreset(String name, int bassValue, int virtValue, int loudValue, int[] bands) {
        SharedPreferences.Editor e = prefs.edit()
                .putBoolean("enabled", true)
                .putInt("bassboost", bassValue)
                .putInt("virtualizer", virtValue)
                .putInt("loudness", loudValue);
        for (int i = 0; i < bands.length; i++) e.putInt("band_" + i, bands[i]);
        e.apply();
        bass.setProgress(bassValue);
        virtualizer.setProgress(virtValue);
        loudness.setProgress(loudValue);
        status.setText("Preset: " + name);
        applyEffects();
    }

    private void applyEffects() {
        try {
            Intent i = new Intent(this, EqService.class);
            i.setAction("APPLY");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable t) {
            status.setText("Servis nije mogao da se pokrene");
            Toast.makeText(this, t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
}
