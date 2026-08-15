package com.codex.xwaveeq

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

class AudioEngine(private val context: Context) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    val isAvailable: Boolean
        get() = equalizer != null

    fun start(): Result<Unit> = runCatching {
        release()
        // Session 0 requests output-mix/global processing. Some Android firmwares block it.
        equalizer = Equalizer(0, 0)
        bassBoost = runCatching { BassBoost(0, 0) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, 0) }.getOrNull()

        equalizer?.enabled = true
        bassBoost?.enabled = true
        virtualizer?.enabled = true

        restore()
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
        prefs().edit().putBoolean("enabled", enabled).apply()
    }

    fun getBandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0

    fun getBandCenterHz(band: Int): Int =
        (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun getBandRange(): Pair<Short, Short> {
        val r = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        return r[0] to r[1]
    }

    fun getBandLevel(band: Int): Short =
        runCatching { equalizer?.getBandLevel(band.toShort()) ?: 0 }.getOrDefault(0)

    fun setBandLevel(band: Int, levelMb: Short) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMb) }
        prefs().edit().putInt("band_$band", levelMb.toInt()).apply()
    }

    fun setBass(strength: Short) {
        if (bassBoost?.strengthSupported == true) {
            runCatching { bassBoost?.setStrength(strength) }
        }
        prefs().edit().putInt("bass", strength.toInt()).apply()
    }

    fun setVirtualizer(strength: Short) {
        if (virtualizer?.strengthSupported == true) {
            runCatching { virtualizer?.setStrength(strength) }
        }
        prefs().edit().putInt("virt", strength.toInt()).apply()
    }

    fun reset() {
        val count = getBandCount()
        for (i in 0 until count) setBandLevel(i, 0)
        setBass(0)
        setVirtualizer(0)
    }

    private fun restore() {
        val p = prefs()
        setEnabled(p.getBoolean("enabled", true))
        for (i in 0 until getBandCount()) {
            setBandLevel(i, p.getInt("band_$i", 0).toShort())
        }
        setBass(p.getInt("bass", 0).coerceIn(0, 1000).toShort())
        setVirtualizer(p.getInt("virt", 0).coerceIn(0, 1000).toShort())
    }

    fun savedBass(): Int = prefs().getInt("bass", 0).coerceIn(0, 1000)
    fun savedVirtualizer(): Int = prefs().getInt("virt", 0).coerceIn(0, 1000)
    fun savedEnabled(): Boolean = prefs().getBoolean("enabled", true)

    private fun prefs() =
        context.getSharedPreferences("xwave_eq", Context.MODE_PRIVATE)

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
