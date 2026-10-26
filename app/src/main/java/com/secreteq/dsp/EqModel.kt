package com.secreteq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Band(val f: Float, val g: Float, val q: Float)

data class EqState(
    val enabled: Boolean = true,
    val preDb: Float = 0f,
    val bands: List<Band> = EqMath.flatBands(),
    val bass: Float = 0f,
    val treble: Float = 0f,
    val bassBoost: Boolean = false,
    val trebleBoost: Boolean = false,
    val limiter: Boolean = true,
    val autoHeadroom: Boolean = true,
    val pLowDb: Float = 0f,
    val pLowHz: Float = EqMath.PARAM_LOW_HZ,
    val pLowQ: Float = EqMath.PARAM_LOW_Q,
    val pHighDb: Float = 0f,
    val pHighHz: Float = EqMath.PARAM_HIGH_HZ,
    val pHighQ: Float = EqMath.PARAM_HIGH_Q,
    val presetName: String = "Flat"
)

/** Biquad magnitude (RBJ) for curve display — same math family as Secret Player. */
class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    fun magDb(f: Double, fs: Double): Double {
        val w = 2 * PI * f / fs
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2; val ni = -(b1 * sw + b2 * s2)
        val dr = 1 + a1 * cw + a2 * c2; val di = -(a1 * sw + a2 * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di + 1e-30) + 1e-30)
    }
    fun setPeak(fs: Double, f0: Double, db: Double, q: Double) {
        val A = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cosw = cos(w0)
        val b0n = 1 + alpha * A; val b1n = -2 * cosw; val b2n = 1 - alpha * A
        val a0 = 1 + alpha / A; val a1n = -2 * cosw; val a2n = 1 - alpha / A
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }
    fun setLowShelf(fs: Double, f0: Double, db: Double, q: Double) {
        val A = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cosw = cos(w0)
        val twoSqrtA = 2 * sqrt(A) * alpha
        val b0n = A * ((A + 1) - (A - 1) * cosw + twoSqrtA)
        val b1n = 2 * A * ((A - 1) - (A + 1) * cosw)
        val b2n = A * ((A + 1) - (A - 1) * cosw - twoSqrtA)
        val a0 = (A + 1) + (A - 1) * cosw + twoSqrtA
        val a1n = -2 * ((A - 1) + (A + 1) * cosw)
        val a2n = (A + 1) + (A - 1) * cosw - twoSqrtA
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }
    fun setHighShelf(fs: Double, f0: Double, db: Double, q: Double) {
        val A = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cosw = cos(w0)
        val twoSqrtA = 2 * sqrt(A) * alpha
        val b0n = A * ((A + 1) + (A - 1) * cosw + twoSqrtA)
        val b1n = -2 * A * ((A - 1) + (A + 1) * cosw)
        val b2n = A * ((A + 1) + (A - 1) * cosw - twoSqrtA)
        val a0 = (A + 1) - (A - 1) * cosw + twoSqrtA
        val a1n = 2 * ((A - 1) - (A + 1) * cosw)
        val a2n = (A + 1) - (A - 1) * cosw - twoSqrtA
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }
}

object EqMath {
    const val BAND_COUNT = 32
    const val F_MIN = 20.0
    const val F_MAX = 20000.0
    const val DEFAULT_Q = 4.3f
    const val MAX_GAIN = 12f
    const val PARAM_LOW_HZ = 100f
    const val PARAM_LOW_Q = 1.03f
    const val PARAM_HIGH_HZ = 8000f
    const val PARAM_HIGH_Q = 0.83f

    fun centerFreq(i: Int): Float =
        (F_MIN * (F_MAX / F_MIN).pow(i / (BAND_COUNT - 1.0))).toFloat()

    fun flatBands(): List<Band> =
        List(BAND_COUNT) { Band(centerFreq(it), 0f, DEFAULT_Q) }

    fun fromAnchors(anchorF: List<Float>, gains: List<Float>): List<Band> =
        List(BAND_COUNT) { i ->
            val f = centerFreq(i)
            // linear interp in log-f
            var g = 0f
            if (anchorF.isNotEmpty() && gains.isNotEmpty()) {
                val n = minOf(anchorF.size, gains.size)
                when {
                    f <= anchorF[0] -> g = gains[0]
                    f >= anchorF[n - 1] -> g = gains[n - 1]
                    else -> {
                        var j = 0
                        while (j < n - 1 && f > anchorF[j + 1]) j++
                        val f0 = kotlin.math.ln(anchorF[j].toDouble())
                        val f1 = kotlin.math.ln(anchorF[j + 1].toDouble())
                        val t = ((kotlin.math.ln(f.toDouble()) - f0) / (f1 - f0 + 1e-12)).toFloat()
                        g = gains[j] * (1 - t) + gains[j + 1] * t
                    }
                }
            }
            Band(f, ((g * 10f).roundToInt() / 10f).coerceIn(-MAX_GAIN, MAX_GAIN), DEFAULT_Q)
        }

    /** Full response curve at given freqs (display + mapping). */
    fun responseDb(state: EqState, freqs: DoubleArray, fs: Double = 48000.0): DoubleArray {
        val filters = ArrayList<Biquad>()
        for (b in state.bands) {
            if (kotlin.math.abs(b.g) < 0.05f) continue
            val q = Biquad(); q.setPeak(fs, b.f.toDouble(), b.g.toDouble(), b.q.toDouble().coerceAtLeast(0.1))
            filters += q
        }
        val bassG = state.bass + if (state.bassBoost) 3f else 0f
        if (kotlin.math.abs(bassG) >= 0.05f) {
            val q = Biquad(); q.setLowShelf(fs, 100.0, bassG.toDouble(), 0.7)
            filters += q
        }
        if (kotlin.math.abs(state.pLowDb) >= 0.05f) {
            val q = Biquad(); q.setLowShelf(fs, state.pLowHz.toDouble(), state.pLowDb.toDouble(), state.pLowQ.toDouble())
            filters += q
        }
        val trebleG = state.treble + if (state.trebleBoost) 3f else 0f
        if (kotlin.math.abs(trebleG) >= 0.05f) {
            val q = Biquad(); q.setHighShelf(fs, 8000.0, trebleG.toDouble(), 0.7)
            filters += q
        }
        if (kotlin.math.abs(state.pHighDb) >= 0.05f) {
            val q = Biquad(); q.setHighShelf(fs, state.pHighHz.toDouble(), state.pHighDb.toDouble(), state.pHighQ.toDouble())
            filters += q
        }
        val pre = state.preDb.toDouble() + if (state.autoHeadroom) -autoHeadroomDb(state) else 0.0
        return DoubleArray(freqs.size) { i ->
            var db = pre
            for (f in filters) db += f.magDb(freqs[i], fs)
            db
        }
    }

    /** Estimate peak boost to subtract for headroom (same idea as Secret Player). */
    fun autoHeadroomDb(state: EqState): Double {
        val freqs = DoubleArray(64) { i ->
            F_MIN * (F_MAX / F_MIN).pow(i / 63.0)
        }
        // temporary without auto headroom recursion
        val s = state.copy(autoHeadroom = false, preDb = 0f)
        val resp = responseDb(s, freqs)
        val peak = resp.maxOrNull() ?: 0.0
        return (peak * 0.95).coerceAtLeast(0.0)
    }

    fun formatHz(f: Float): String = when {
        f >= 1000f -> String.format("%.1fk", f / 1000f)
        else -> String.format("%.0f", f)
    }
}

object EqPresets {
    private fun ten(g: List<Float>) = EqState(
        preDb = -((g.maxOrNull() ?: 0f) * 0.5f).coerceAtLeast(0f),
        bands = EqMath.fromAnchors(
            listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f), g
        )
    )

    val ALL: LinkedHashMap<String, EqState> = linkedMapOf(
        "Flat" to EqState(),
        "Bass Boost" to ten(listOf(6f, 5.5f, 4.5f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
        "Deep Bass" to EqState(
            bass = 5f, bassBoost = true, preDb = -4f, limiter = true,
            pLowDb = 5f,
            bands = EqMath.fromAnchors(
                listOf(35f, 55f, 80f, 120f, 250f, 500f, 1200f, 3000f, 6000f, 12000f),
                listOf(4f, 5f, 3.5f, 1.5f, -1f, -2f, -1f, 0f, 0.5f, 1f)
            )
        ),
        "Subbass" to EqState(
            bass = 6f, bassBoost = true, preDb = -5f, limiter = true, pLowDb = 6.5f,
            bands = EqMath.fromAnchors(
                listOf(30f, 45f, 65f, 100f, 200f, 400f, 1000f, 2500f, 6000f, 12000f),
                listOf(5.5f, 5f, 3f, 0.5f, -2f, -3f, -1.5f, 0f, 0f, 0.5f)
            )
        ),
        "Hardtekk" to EqState(
            bass = 4f, treble = 3f, bassBoost = true, preDb = -5f, limiter = true,
            pLowDb = 5.5f, pHighDb = 3f,
            bands = EqMath.fromAnchors(
                listOf(40f, 60f, 90f, 150f, 300f, 600f, 1500f, 3500f, 7000f, 12000f),
                listOf(3.5f, 4.5f, 2.5f, 0f, -3f, -4f, -2f, 1f, 3f, 3.5f)
            )
        ),
        "Rock" to ten(listOf(4.5f, 3.5f, 2.5f, 1f, -0.5f, -1f, 1f, 2.5f, 3.5f, 4.5f)),
        "Pop" to ten(listOf(-1.5f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f, -1.5f)),
        "Electronic" to ten(listOf(4f, 3.5f, 1f, 0f, -2f, 2f, 1f, 1.5f, 3.5f, 4f)),
        "Hip-Hop" to ten(listOf(5f, 4.5f, 1.5f, 3f, -1f, -1f, 1.5f, -0.5f, 2f, 3f)),
        "Vocal" to ten(listOf(-2f, -3f, -3f, 1.5f, 3.5f, 3.5f, 3.5f, 2f, 0f, -1f)),
        "Druck" to EqState(
            bass = 3f, treble = 2f, bassBoost = true, pLowDb = 7f, pHighDb = 6.2f,
            bands = EqMath.fromAnchors(
                listOf(40f, 70f, 110f, 200f, 350f, 600f, 1200f, 3000f, 6000f, 12000f),
                listOf(1.5f, 2.5f, 3f, 0f, -4f, -6f, -5f, -1f, 1f, 2f)
            )
        )
    )
}
