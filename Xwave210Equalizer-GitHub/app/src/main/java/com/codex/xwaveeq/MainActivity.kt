package com.codex.xwaveeq

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var engine: AudioEngine
    private lateinit var bandsContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var bassSeek: SeekBar
    private lateinit var virtSeek: SeekBar
    private val bandSeekBars = mutableListOf<SeekBar>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bandsContainer = findViewById(R.id.bandsContainer)
        status = findViewById(R.id.status)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        bassSeek = findViewById(R.id.bassSeek)
        virtSeek = findViewById(R.id.virtSeek)

        engine = AudioEngine(this)
        val result = engine.start()

        if (result.isSuccess && engine.isAvailable) {
            status.text = "EQ aktivan. Broj hardverskih bandova: ${engine.getBandCount()}"
            setupEqualizerControls()
        } else {
            status.text = "Firmware je odbio globalni AudioEffect: ${result.exceptionOrNull()?.message ?: "nije podržano"}"
        }

        enabledSwitch.isChecked = engine.savedEnabled()
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            engine.setEnabled(checked)
        }

        bassSeek.progress = engine.savedBass()
        bassSeek.setOnSeekBarChangeListener(simpleSeek { engine.setBass(it.toShort()) })

        virtSeek.progress = engine.savedVirtualizer()
        virtSeek.setOnSeekBarChangeListener(simpleSeek { engine.setVirtualizer(it.toShort()) })

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            engine.reset()
            bandSeekBars.forEach { it.progress = it.max / 2 }
            bassSeek.progress = 0
            virtSeek.progress = 0
            Toast.makeText(this, "EQ resetovan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEqualizerControls() {
        val (minLevel, maxLevel) = engine.getBandRange()
        val range = (maxLevel - minLevel).toInt().coerceAtLeast(1)

        for (band in 0 until engine.getBandCount()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val label = TextView(this).apply {
                val hz = engine.getBandCenterHz(band)
                text = if (hz >= 1000) "${hz / 1000.0} kHz" else "$hz Hz"
                setTextColor(getColor(R.color.text))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(dp(105), dp(48))
                gravity = Gravity.CENTER_VERTICAL
            }

            val value = TextView(this).apply {
                setTextColor(getColor(R.color.muted))
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(48))
            }

            val seek = SeekBar(this).apply {
                max = range
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                progress = (engine.getBandLevel(band) - minLevel).toInt().coerceIn(0, range)
            }

            fun updateText(progress: Int) {
                val mb = minLevel + progress
                value.text = String.format("%.1f dB", mb / 100.0)
            }
            updateText(seek.progress)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    val level = (minLevel + progress).toShort()
                    updateText(progress)
                    if (fromUser) engine.setBandLevel(band, level)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })

            // Better D-pad behavior on Android TV.
            seek.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val step = (range / 30).coerceAtLeast(1)
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        seek.progress = (seek.progress - step).coerceAtLeast(0)
                        engine.setBandLevel(band, (minLevel + seek.progress).toShort())
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        seek.progress = (seek.progress + step).coerceAtMost(range)
                        engine.setBandLevel(band, (minLevel + seek.progress).toShort())
                        true
                    }
                    else -> false
                }
            }

            bandSeekBars += seek
            row.addView(label)
            row.addView(seek)
            row.addView(value)
            bandsContainer.addView(row)
        }
    }

    private fun simpleSeek(onChanged: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
