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

    public void presetFlat(View v)  { setPreset("FLAT", 0, 0, 0, new int[]{0,0,0,0,0}); }
    public void presetBass(View v)  { setPreset("BASS", 800, 120, 500, new int[]{900,650,150,-100,-150}); }
    public void presetVoice(View v) { setPreset("VOICE", 100, 0, 450, new int[]{-200,-50,450,650,300}); }
    public void presetMovie(View v) { setPreset("MOVIE", 550, 300, 700, new int[]{450,250,100,250,400}); }
    public void presetMusic(View v) { setPreset("MUSIC", 350, 180, 350, new int[]{300,150,0,150,300}); }

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
