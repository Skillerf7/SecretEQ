package com.secreteq

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.secreteq.data.EqPrefs
import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqPresets
import com.secreteq.dsp.EqState
import com.secreteq.system.EqService
import com.secreteq.system.SystemEqEngine
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var prefs: EqPrefs
    private val engine = SystemEqEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = EqPrefs(this)
        engine.start()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB388FF),
                    background = Color(0xFF12081A),
                    surface = Color(0xFF1A1028)
                )
            ) {
                var state by remember { mutableStateOf(prefs.load()) }
                var status by remember { mutableStateOf(engine.statusText()) }
                fun commit(s: EqState) {
                    state = s
                    prefs.save(s)
                    engine.apply(s)
                    status = engine.statusText()
                    // keep service in sync
                    startForegroundService(Intent(this, EqService::class.java).setAction(EqService.ACTION_APPLY))
                }
                Scaffold(
                    containerColor = Color(0xFF12081A),
                    topBar = {
                        TopAppBar(
                            title = { Text("Secret EQ") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF1A1028),
                                titleContentColor = Color(0xFFB388FF)
                            )
                        )
                    }
                ) { pad ->
                    Column(
                        Modifier
                            .padding(pad)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Equalizer aktiv", Modifier.weight(1f), color = Color.White)
                            Switch(checked = state.enabled, onCheckedChange = {
                                commit(state.copy(enabled = it))
                            })
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4B5D4))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hinweis: Systemweite Wirkung hängt vom Gerät ab. " +
                                "32 Bänder werden auf die ${engine.bandCount()} System-Bänder abgebildet. " +
                                "YouTube/Browser: auf dem Gerät prüfen — hier nicht nachgewiesen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFAB91)
                        )
                        Spacer(Modifier.height(12.dp))
                        EqCurve(state)
                        Spacer(Modifier.height(12.dp))
                        Text("Presets", color = Color(0xFFB388FF))
                        EqPresets.ALL.keys.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { name ->
                                    OutlinedButton(
                                        onClick = {
                                            val p = EqPresets.ALL[name]!!.copy(presetName = name, enabled = state.enabled)
                                            commit(p)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(name, maxLines = 1) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Bass ${"%.1f".format(state.bass)} dB", color = Color.White)
                        Slider(
                            value = state.bass,
                            onValueChange = { commit(state.copy(bass = it, presetName = "Custom")) },
                            valueRange = -12f..12f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bass-Boost", Modifier.weight(1f), color = Color.White)
                            Switch(checked = state.bassBoost, onCheckedChange = {
                                commit(state.copy(bassBoost = it, presetName = "Custom"))
                            })
                        }
                        Text("Höhen ${"%.1f".format(state.treble)} dB", color = Color.White)
                        Slider(
                            value = state.treble,
                            onValueChange = { commit(state.copy(treble = it, presetName = "Custom")) },
                            valueRange = -12f..12f
                        )
                        Text("Preamp ${"%.1f".format(state.preDb)} dB", color = Color.White)
                        Slider(
                            value = state.preDb,
                            onValueChange = { commit(state.copy(preDb = it, presetName = "Custom")) },
                            valueRange = -12f..12f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Headroom", Modifier.weight(1f), color = Color.White)
                            Switch(checked = state.autoHeadroom, onCheckedChange = {
                                commit(state.copy(autoHeadroom = it))
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Limiter (UI-Flag; System-API begrenzt)", Modifier.weight(1f), color = Color.White)
                            Switch(checked = state.limiter, onCheckedChange = {
                                commit(state.copy(limiter = it))
                            })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("32 Bänder (Gain)", color = Color(0xFFB388FF))
                        state.bands.forEachIndexed { i, band ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    EqMath.formatHz(band.f),
                                    Modifier.width(52.dp),
                                    color = Color(0xFFC4B5D4),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = band.g,
                                    onValueChange = { g ->
                                        val nb = state.bands.toMutableList()
                                        nb[i] = band.copy(g = g)
                                        commit(state.copy(bands = nb, presetName = "Custom"))
                                    },
                                    valueRange = -12f..12f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${"%.1f".format(band.g)}",
                                    Modifier.width(40.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            commit(EqState(enabled = state.enabled))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Alles auf Flat")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // leave service running if user wants; only release activity-local engine
        engine.release()
        super.onDestroy()
    }
}

@Composable
private fun EqCurve(state: EqState) {
    val freqs = remember {
        DoubleArray(120) { i ->
            EqMath.F_MIN * Math.pow(EqMath.F_MAX / EqMath.F_MIN, i / 119.0)
        }
    }
    val resp = remember(state) { EqMath.responseDb(state, freqs) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF0D0614))
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        drawLine(Color(0xFF3A2A4A), Offset(0f, mid), Offset(w, mid), strokeWidth = 2f)
        val path = Path()
        for (i in resp.indices) {
            val x = w * (ln(freqs[i] / EqMath.F_MIN) / ln(EqMath.F_MAX / EqMath.F_MIN)).toFloat()
            val y = mid - (resp[i] / 12.0 * (h / 2 * 0.9)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFFB388FF), style = Stroke(width = 3f))
    }
}
