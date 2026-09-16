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

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("eq", MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        bind(R.id.bass, "bassboost");
        bind(R.id.virtualizer, "virtualizer");
        bind(R.id.loudness, "loudness");
        status.setText("Spreman - pritisni UKLJUČI");
    }

    private void bind(int id, String key) {
        SeekBar s = findViewById(id);
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
        if (enabled) {
            applyEffects();
        } else {
            try { stopService(new Intent(this, EqService.class)); } catch (Throwable ignored) {}
            status.setText("Isključen");
        }
    }

    private void applyEffects() {
        try {
            Intent i = new Intent(this, EqService.class);
            i.setAction("APPLY");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Ekvilajzer pokrenut");
        } catch (Throwable t) {
            status.setText("Servis nije mogao da se pokrene");
            Toast.makeText(this, t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
}
