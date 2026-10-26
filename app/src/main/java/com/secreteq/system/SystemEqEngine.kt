package com.secreteq.system

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqState
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * System-EQ über Android AudioEffect (Session 0 = globaler Ausgang, sofern vom Gerät erlaubt).
 *
 * EHRLICH:
 * - Nicht jedes Gerät/Android-Version unterstützt Session-0-Effekte zuverlässig.
 * - Android Equalizer hat typisch 5 Bänder — unsere 32-Band-Kurve wird darauf abgebildet.
 * - Volle 32-Band-Biquad-Kette wie in Secret Player greift systemweit nur, wenn das OS
 *   die Effekte am globalen Mix zulässt; YouTube/Browser sind **nicht garantiert**.
 * - AudioPlaybackCapture ist KEINE systemweite EQ-Lösung für die Ausgabe.
 */
class SystemEqEngine {
    companion object {
        private const val TAG = "SecretEQ"
        /** Session 0 = output mix, wenn unterstützt. */
        private const val GLOBAL_SESSION = 0
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    @Volatile var lastStatus: IntegrationStatus = IntegrationStatus.NOT_STARTED
        private set

    enum class IntegrationStatus {
        NOT_STARTED,
        /** Effekt-Objekte erzeugt — Wirkung auf fremde Apps noch nicht bewiesen. */
        INITIALIZED,
        ENABLED,
        UNSUPPORTED,
        ERROR
    }

    fun start(): IntegrationStatus {
        release()
        return try {
            val eq = Equalizer(Int.MAX_VALUE / 2, GLOBAL_SESSION)
            eq.enabled = false
            equalizer = eq
            try {
                bassBoost = BassBoost(Int.MAX_VALUE / 2 - 1, GLOBAL_SESSION).also { it.enabled = false }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost unavailable: ${e.message}")
            }
            if (Build.VERSION.SDK_INT >= 19) {
                try {
                    loudness = LoudnessEnhancer(GLOBAL_SESSION).also { it.enabled = false }
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
                }
            }
            lastStatus = IntegrationStatus.INITIALIZED
            lastStatus
        } catch (t: Throwable) {
            Log.e(TAG, "Equalizer session 0 failed", t)
            lastStatus = IntegrationStatus.UNSUPPORTED
            lastStatus
        }
    }

    fun apply(state: EqState): IntegrationStatus {
        val eq = equalizer ?: return start().also {
            if (it == IntegrationStatus.INITIALIZED || it == IntegrationStatus.ENABLED) apply(state)
        }
        return try {
            if (!state.enabled) {
                eq.enabled = false
                bassBoost?.enabled = false
                loudness?.enabled = false
                lastStatus = IntegrationStatus.INITIALIZED
                return lastStatus
            }
            val n = eq.numberOfBands.toInt()
            if (n <= 0) {
                lastStatus = IntegrationStatus.UNSUPPORTED
                return lastStatus
            }
            // Map 32-band response onto system band center frequencies
            val freqs = DoubleArray(n) { i ->
                val range = eq.getBandFreqRange(i.toShort())
                ((range[0].toLong() + range[1].toLong()) / 2).toDouble() / 1000.0 // mHz → Hz in API is milliHz
            }
            // Android returns millihertz
            val hz = DoubleArray(n) { i ->
                val range = eq.getBandFreqRange(i.toShort())
                (range[0] + range[1]) / 2.0 / 1000.0
            }
            val resp = EqMath.responseDb(state, hz, 48000.0)
            val levelRange = eq.bandLevelRange // milliBel
            val minMb = levelRange[0].toInt()
            val maxMb = levelRange[1].toInt()
            for (i in 0 until n) {
                val mb = (resp[i] * 100).roundToInt().coerceIn(minMb, maxMb)
                eq.setBandLevel(i.toShort(), mb.toShort())
            }
            eq.enabled = true

            bassBoost?.let { bb ->
                if (state.bassBoost || state.bass > 1f) {
                    val str = ((state.bass.coerceIn(0f, 12f) / 12f) * 1000).roundToInt()
                        .coerceIn(0, 1000)
                    bb.setStrength(str.toShort())
                    bb.enabled = true
                } else {
                    bb.enabled = false
                }
            }
            loudness?.let { le ->
                // Preamp-ish: positive pre only via loudness enhancer (mB)
                val mb = (state.preDb.coerceIn(-12f, 12f) * 100).roundToInt()
                if (mb > 0) {
                    le.setTargetGain(mb)
                    le.enabled = true
                } else {
                    le.enabled = false
                }
            }
            lastStatus = IntegrationStatus.ENABLED
            lastStatus
        } catch (t: Throwable) {
            Log.e(TAG, "apply failed", t)
            lastStatus = IntegrationStatus.ERROR
            lastStatus
        }
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0

    fun statusText(): String = when (lastStatus) {
        IntegrationStatus.NOT_STARTED -> "Noch nicht gestartet"
        IntegrationStatus.INITIALIZED ->
            "Effekt initialisiert (${bandCount()} System-Bänder). Wirkung auf YouTube/Browser: noch nicht nachgewiesen."
        IntegrationStatus.ENABLED ->
            "System-EQ aktiv (${bandCount()} Bänder, Session 0). " +
                "32-Band-Kurve wird auf diese Bänder abgebildet. Gerät kann globale Effekte blockieren."
        IntegrationStatus.UNSUPPORTED ->
            "Session-0-Equalizer auf diesem Gerät nicht verfügbar / abgelehnt."
        IntegrationStatus.ERROR -> "Fehler beim Anwenden der EQ-Parameter."
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        equalizer = null; bassBoost = null; loudness = null
        lastStatus = IntegrationStatus.NOT_STARTED
    }
}
