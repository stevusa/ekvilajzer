package rs.tvbox210.equalizer;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private android.content.SharedPreferences prefs;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("eq",MODE_PRIVATE);setContentView(R.layout.activity_main);bind(R.id.enabled,"enabled",1);bind(R.id.bass,"bassboost",10);bind(R.id.virtualizer,"virtualizer",10);bind(R.id.loudness,"loudness",20);startServiceNow();}
    private void bind(int id,String key,int step){SeekBar s=findViewById(id);int max=key.equals("loudness")?2000:1000;s.setMax(max);s.setProgress(prefs.getInt(key,0));s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){prefs.edit().putInt(key,p).apply();apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});}
    public void toggle(View v){boolean n=!prefs.getBoolean("enabled",true);prefs.edit().putBoolean("enabled",n).apply();((Button)v).setText(n?"ON":"OFF");apply();}
    private void apply(){Intent i=new Intent(this,EqService.class);i.setAction("APPLY");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void startServiceNow(){apply();}
}
