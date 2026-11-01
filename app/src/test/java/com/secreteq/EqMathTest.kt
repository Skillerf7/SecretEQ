package com.secreteq

import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqPresets
import com.secreteq.dsp.EqState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqMathTest {
    @Test
    fun thirtyTwoBandsLogSpaced() {
        val b = EqMath.flatBands()
        assertEquals(32, b.size)
        assertTrue(b.first().f in 19f..21f)
        assertTrue(b.last().f in 19000f..21000f)
        for (i in 1 until b.size) assertTrue(b[i].f > b[i - 1].f)
    }

    @Test
    fun flatResponseNearZero() {
        val freqs = DoubleArray(16) { i -> 20.0 * Math.pow(1000.0, i / 15.0) }
        val r = EqMath.responseDb(EqState(), freqs)
        assertTrue(r.all { kotlin.math.abs(it) < 0.5 })
    }

    @Test
    fun bassBoostRaisesLowEnd() {
        val freqs = doubleArrayOf(40.0, 100.0, 1000.0, 8000.0)
        val flat = EqMath.responseDb(EqState(), freqs)
        val bass = EqMath.responseDb(EqState(bass = 6f, bassBoost = true), freqs)
        // Auto-Headroom zieht von der ganzen Kurve einen konstanten Wert ab (hier ~8,5 dB).
        // Deshalb Tiefen relativ zu den Höhen vergleichen, nicht absolut.
        val bassTilt = bass[0] - bass[3]
        val flatTilt = flat[0] - flat[3]
        assertTrue(bassTilt > flatTilt + 2)
    }

    @Test
    fun presetsExist() {
        assertTrue(EqPresets.ALL.containsKey("Flat"))
        assertTrue(EqPresets.ALL.containsKey("Hardtekk"))
        assertEquals(32, EqPresets.ALL["Deep Bass"]!!.bands.size)
    }
}
