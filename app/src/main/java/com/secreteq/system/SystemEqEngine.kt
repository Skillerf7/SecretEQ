package com.secreteq.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqState
import kotlin.math.roundToInt

/**
 * Best-effort system audio processing without root / Shizuku / ADB.
 *
 * Strategies (in order of preference when available):
 * 1) DynamicsProcessing on session 0 (more bands, API 28+)
 * 2) Classic Equalizer + BassBoost + LoudnessEnhancer on session 0
 * 3) Attach same effects to audio sessions announced via
 *    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION (cooperative players only)
 *
 * EHRLICHKEIT:
 * - Session 0 is deprecated; many OEMs (incl. Samsung) ignore or block it for games.
 * - Games (Unity / OpenSL / AAudio exclusive / HW-offload) often never hit the effect chain.
 * - Creating an effect object ≠ external audio is modified. Status reflects that.
 * - GRID Autosport and similar titles are frequently NOT processable without root.
 */
class SystemEqEngine {

    companion object {
        private const val TAG = "SecretEQ.Engine"
        private const val GLOBAL_SESSION = 0
        private const val PRIORITY = Int.MAX_VALUE / 2
        /** Prefer more bands on DynamicsProcessing when the HAL allows. */
        private const val DP_BANDS = 10
    }

    enum class Backend {
        NONE,
        DYNAMICS_PROCESSING,
        CLASSIC_EQUALIZER
    }

    /**
     * Honest processing status — never claim external audio is affected
     * just because an effect object exists.
     */
    data class ProcessStatus(
        val backend: Backend = Backend.NONE,
        val effectCreated: Boolean = false,
        val effectEnabled: Boolean = false,
        val systemBandCount: Int = 0,
        val sessionIds: List<Int> = emptyList(),
        val externalEffectProven: Boolean = false,
        val lastError: String? = null,
        val limitationNote: String = ""
    ) {
        fun shortText(): String {
            if (!effectCreated) {
                return lastError ?: "Kein System-Audioeffekt verfügbar (Session 0 abgelehnt)."
            }
            val bands = if (systemBandCount > 0) "$systemBandCount System-Bänder" else "Bänder unbekannt"
            val backendName = when (backend) {
                Backend.DYNAMICS_PROCESSING -> "DynamicsProcessing"
                Backend.CLASSIC_EQUALIZER -> "Equalizer"
                Backend.NONE -> "—"
            }
            val enabled = if (effectEnabled) "aktiviert" else "pausiert"
            val prove = if (externalEffectProven)
                "Externe Session angehängt."
            else
                "Wirkung auf fremde Apps (Spiele/YouTube) ist geräteabhängig und hier NICHT nachgewiesen."
            return "$backendName $enabled ($bands, Session ${sessionIds.joinToString()}). $prove"
        }
    }

    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    /** Extra effects attached to sessions from OPEN_AUDIO_EFFECT_CONTROL_SESSION. */
    private val sessionEffects = mutableMapOf<Int, Equalizer>()

    @Volatile
    var status: ProcessStatus = ProcessStatus()
        private set

    @Volatile
    private var currentState: EqState = EqState()

    private var sessionReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun start(context: Context? = null): ProcessStatus {
        release()
        appContext = context?.applicationContext
        val errors = mutableListOf<String>()

        // --- 1) DynamicsProcessing on session 0 (more bands) ---
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    /*channelCount*/ 2,
                    /*preEqInUse*/ true, DP_BANDS,
                    /*mbcInUse*/ false, 0,
                    /*postEqInUse*/ true, DP_BANDS,
                    /*limiterInUse*/ true
                ).build()
                val dp = DynamicsProcessing(PRIORITY, GLOBAL_SESSION, cfg)
                dp.enabled = false
                dynamics = dp
                status = ProcessStatus(
                    backend = Backend.DYNAMICS_PROCESSING,
                    effectCreated = true,
                    effectEnabled = false,
                    systemBandCount = DP_BANDS,
                    sessionIds = listOf(GLOBAL_SESSION),
                    externalEffectProven = false,
                    limitationNote = "DynamicsProcessing Session 0 — deprecated global mix; games may bypass."
                )
                Log.i(TAG, "DynamicsProcessing session 0 created ($DP_BANDS bands)")
            } catch (t: Throwable) {
                errors += "DynamicsProcessing: ${t.message}"
                Log.w(TAG, "DynamicsProcessing session 0 failed", t)
            }
        }

        // --- 2) Classic Equalizer fallback / parallel ---
        if (dynamics == null) {
            try {
                val eq = Equalizer(PRIORITY, GLOBAL_SESSION)
                eq.enabled = false
                equalizer = eq
                val n = eq.numberOfBands.toInt()
                try {
                    bassBoost = BassBoost(PRIORITY - 1, GLOBAL_SESSION).also { it.enabled = false }
                } catch (e: Exception) {
                    Log.w(TAG, "BassBoost: ${e.message}")
                }
                try {
                    loudness = LoudnessEnhancer(GLOBAL_SESSION).also { it.enabled = false }
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer: ${e.message}")
                }
                status = ProcessStatus(
                    backend = Backend.CLASSIC_EQUALIZER,
                    effectCreated = true,
                    effectEnabled = false,
                    systemBandCount = n,
                    sessionIds = listOf(GLOBAL_SESSION),
                    externalEffectProven = false,
                    limitationNote = "Classic Equalizer Session 0 — typically 5 bands; games may bypass."
                )
                Log.i(TAG, "Equalizer session 0 created ($n bands)")
            } catch (t: Throwable) {
                errors += "Equalizer: ${t.message}"
                Log.e(TAG, "Equalizer session 0 failed", t)
            }
        }

        if (!status.effectCreated) {
            status = ProcessStatus(
                backend = Backend.NONE,
                effectCreated = false,
                lastError = errors.joinToString(" | ").ifBlank {
                    "Session-0-Effekte auf diesem Gerät nicht verfügbar."
                },
                limitationNote = "Ohne Root/Shizuku kann Secret EQ fremde Audio-Sessions nicht erzwingen."
            )
        }

        // --- 3) Listen for cooperative media sessions ---
        registerSessionReceiver()

        return status
    }

    private fun registerSessionReceiver() {
        val ctx = appContext ?: return
        unregisterSessionReceiver()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                if (sessionId <= 0) return
                when (intent.action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                        Log.i(TAG, "OPEN session $sessionId from ${intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)}")
                        attachToSession(sessionId)
                    }
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                        Log.i(TAG, "CLOSE session $sessionId")
                        detachSession(sessionId)
                    }
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                ctx.registerReceiver(receiver, filter)
            }
            sessionReceiver = receiver
        } catch (t: Throwable) {
            Log.w(TAG, "session receiver: ${t.message}")
        }
    }

    private fun unregisterSessionReceiver() {
        val ctx = appContext
        val r = sessionReceiver
        if (ctx != null && r != null) {
            try { ctx.unregisterReceiver(r) } catch (_: Exception) {}
        }
        sessionReceiver = null
    }

    private fun attachToSession(sessionId: Int) {
        if (sessionId == GLOBAL_SESSION || sessionEffects.containsKey(sessionId)) return
        try {
            val eq = Equalizer(PRIORITY, sessionId)
            applyClassicTo(eq, currentState)
            eq.enabled = currentState.enabled
            sessionEffects[sessionId] = eq
            status = status.copy(
                sessionIds = (status.sessionIds + sessionId).distinct(),
                externalEffectProven = true,
                effectEnabled = currentState.enabled
            )
            Log.i(TAG, "Attached Equalizer to session $sessionId")
        } catch (t: Throwable) {
            Log.w(TAG, "attach session $sessionId failed: ${t.message}")
        }
    }

    private fun detachSession(sessionId: Int) {
        sessionEffects.remove(sessionId)?.let {
            try { it.release() } catch (_: Exception) {}
        }
        status = status.copy(sessionIds = status.sessionIds.filter { it != sessionId })
    }

    fun apply(state: EqState): ProcessStatus {
        currentState = state
        if (!status.effectCreated) {
            start(appContext)
            if (!status.effectCreated) return status
        }
        return try {
            when {
                dynamics != null && Build.VERSION.SDK_INT >= 28 -> applyDynamics(state)
                equalizer != null -> applyClassic(state)
                else -> {
                    status = status.copy(effectEnabled = false, lastError = "Kein Backend")
                    status
                }
            }.also {
                // Keep per-session attachments in sync
                sessionEffects.values.forEach { eq ->
                    try {
                        applyClassicTo(eq, state)
                        eq.enabled = state.enabled
                    } catch (_: Exception) {}
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "apply failed", t)
            status = status.copy(lastError = t.message, effectEnabled = false)
            status
        }
    }

    private fun applyDynamics(state: EqState): ProcessStatus {
        val dp = dynamics ?: return status
        if (!state.enabled) {
            dp.enabled = false
            status = status.copy(effectEnabled = false)
            return status
        }
        val chCount = try { dp.channelCount } catch (_: Exception) { 2 }
        // Build frequency grid for mapping 32-band curve → DP bands
        val bandCount = DP_BANDS
        val freqs = DoubleArray(bandCount) { i ->
            EqMath.F_MIN * Math.pow(EqMath.F_MAX / EqMath.F_MIN, i / (bandCount - 1.0))
        }
        val resp = EqMath.responseDb(state, freqs, 48000.0)
        for (ch in 0 until chCount) {
            for (b in 0 until bandCount) {
                val cutoff = freqs[b].toFloat().coerceIn(30f, 20000f)
                val gain = resp[b].toFloat().coerceIn(-12f, 12f)
                try {
                    val preBand = DynamicsProcessing.EqBand(true, cutoff, gain)
                    dp.setPreEqBandAllChannelsTo(b, preBand)
                    val postBand = DynamicsProcessing.EqBand(true, cutoff, gain * 0.5f)
                    dp.setPostEqBandAllChannelsTo(b, postBand)
                } catch (e: Exception) {
                    // Some HALs reject per-band writes; continue best-effort
                    Log.w(TAG, "DP band $b: ${e.message}")
                }
            }
            try {
                dp.setInputGainAllChannelsTo(state.preDb.coerceIn(-12f, 12f))
            } catch (_: Exception) {}
        }
        dp.enabled = true
        status = status.copy(
            backend = Backend.DYNAMICS_PROCESSING,
            effectEnabled = true,
            systemBandCount = bandCount,
            externalEffectProven = status.externalEffectProven,
            lastError = null
        )
        return status
    }

    private fun applyClassic(state: EqState): ProcessStatus {
        val eq = equalizer ?: return status
        if (!state.enabled) {
            eq.enabled = false
            bassBoost?.enabled = false
            loudness?.enabled = false
            status = status.copy(effectEnabled = false)
            return status
        }
        applyClassicTo(eq, state)
        eq.enabled = true

        bassBoost?.let { bb ->
            if (state.bassBoost || state.bass > 1f) {
                val str = ((state.bass.coerceIn(0f, 12f) / 12f) * 1000).roundToInt().coerceIn(0, 1000)
                bb.setStrength(str.toShort())
                bb.enabled = true
            } else bb.enabled = false
        }
        loudness?.let { le ->
            val mb = (state.preDb.coerceIn(-12f, 12f) * 100).roundToInt()
            if (mb > 0) {
                le.setTargetGain(mb)
                le.enabled = true
            } else le.enabled = false
        }

        status = status.copy(
            backend = Backend.CLASSIC_EQUALIZER,
            effectEnabled = true,
            systemBandCount = eq.numberOfBands.toInt(),
            lastError = null
        )
        return status
    }

    private fun applyClassicTo(eq: Equalizer, state: EqState) {
        val n = eq.numberOfBands.toInt()
        if (n <= 0) return
        val hz = DoubleArray(n) { i ->
            val range = eq.getBandFreqRange(i.toShort())
            (range[0] + range[1]) / 2.0 / 1000.0
        }
        val resp = EqMath.responseDb(state, hz, 48000.0)
        val levelRange = eq.bandLevelRange
        val minMb = levelRange[0].toInt()
        val maxMb = levelRange[1].toInt()
        for (i in 0 until n) {
            val mb = (resp[i] * 100).roundToInt().coerceIn(minMb, maxMb)
            eq.setBandLevel(i.toShort(), mb.toShort())
        }
    }

    fun bandCount(): Int = status.systemBandCount

    fun statusText(): String = status.shortText()

    fun release() {
        unregisterSessionReceiver()
        sessionEffects.values.forEach { try { it.release() } catch (_: Exception) {} }
        sessionEffects.clear()
        try { dynamics?.release() } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        dynamics = null
        equalizer = null
        bassBoost = null
        loudness = null
        status = ProcessStatus()
    }
}
