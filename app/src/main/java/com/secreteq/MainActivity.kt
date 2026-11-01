package com.secreteq

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.secreteq.accessibility.ForegroundProbe
import com.secreteq.data.EqPrefs
import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqPresets
import com.secreteq.dsp.EqState
import com.secreteq.profile.GameRegistry
import com.secreteq.profile.ProfileManager
import com.secreteq.system.EqService
import com.secreteq.system.SystemEqEngine
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var prefs: EqPrefs
    private lateinit var profiles: ProfileManager
    private lateinit var games: GameRegistry
    private val engine = SystemEqEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = EqPrefs(this)
        profiles = ProfileManager(this)
        games = profiles.games
        engine.start(this)
        startForegroundService(Intent(this, EqService::class.java).setAction(EqService.ACTION_APPLY))

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
                var a11yEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
                var autoSwitch by remember { mutableStateOf(profiles.autoSwitchEnabled) }
                var editingMode by remember { mutableStateOf(ProfileManager.Mode.DEFAULT) }
                var showGamePicker by remember { mutableStateOf(false) }
                var gameListTick by remember { mutableIntStateOf(0) }
                var probeResult by remember { mutableStateOf<String?>(null) }
                val detection = profiles.loadLastDetection()

                fun refreshStatus() {
                    a11yEnabled = isAccessibilityEnabled()
                    status = engine.statusText()
                }

                fun commit(s: EqState) {
                    state = s
                    when (editingMode) {
                        ProfileManager.Mode.DEFAULT -> {
                            prefs.save(s)
                            startForegroundService(
                                Intent(this@MainActivity, EqService::class.java)
                                    .setAction(EqService.ACTION_APPLY)
                            )
                        }
                        ProfileManager.Mode.GAMING -> profiles.saveGamingPreset(s)
                        ProfileManager.Mode.BROWSER_VIDEO -> profiles.saveBrowserVideoPreset(s)
                        ProfileManager.Mode.CUSTOM_APP -> {}
                    }
                    engine.apply(s)
                    status = engine.statusText()
                }

                fun loadMode(mode: ProfileManager.Mode) {
                    editingMode = mode
                    state = when (mode) {
                        ProfileManager.Mode.DEFAULT -> prefs.load()
                        ProfileManager.Mode.GAMING -> profiles.getGamingPreset()
                        ProfileManager.Mode.BROWSER_VIDEO -> profiles.getBrowserVideoPreset()
                        ProfileManager.Mode.CUSTOM_APP -> prefs.load()
                    }
                    engine.apply(state)
                    status = engine.statusText()
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
                        // ===== A) App-Erkennung =====
                        Text("A) App-Erkennung", color = Color(0xFFB388FF), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (a11yEnabled) "Bedienungshilfe: aktiv"
                            else "Bedienungshilfe: aus — bitte aktivieren",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (a11yEnabled) Color(0xFF81C784) else Color(0xFFE57373)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text(if (a11yEnabled) "Einstellungen" else "Aktivieren") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { refreshStatus() }) { Text("Status") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Profilumschaltung", Modifier.weight(1f), color = Color.White)
                            Switch(checked = autoSwitch, onCheckedChange = {
                                autoSwitch = it
                                profiles.autoSwitchEnabled = it
                            })
                        }

                        detection?.let { d ->
                            Spacer(Modifier.height(6.dp))
                            Text("Zuletzt erkannt:", color = Color(0xFFC4B5D4), style = MaterialTheme.typography.labelMedium)
                            Text("App: ${games.labelFor(d.packageName)}", color = Color.White)
                            Text("Paket: ${d.packageName}", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Als Spiel: ${if (d.isGame) "JA" else "NEIN"} · ${games.detectionReason(d.packageName)}",
                                color = if (d.isGame) Color(0xFF81C784) else Color(0xFFFFCC80),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Methode: ${d.source} · Profil-Modus: ${d.mode}",
                                color = Color(0xFFB0BEC5),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } ?: Text(
                            "Noch keine Erkennung — Spiel starten oder „Erkennung testen“.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE)
                        )

                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                // UsageStats-Probe + manueller Resolve
                                val usagePkg = ForegroundProbe.queryRecentForeground(this@MainActivity)
                                val testPkg = usagePkg ?: detection?.packageName
                                probeResult = if (testPkg != null) {
                                    val info = profiles.resolveForPackage(testPkg)
                                    profiles.recordDetection(
                                        testPkg, "MANUAL_TEST", info.mode.name, games.isGame(testPkg)
                                    )
                                    startForegroundService(
                                        Intent(this@MainActivity, EqService::class.java)
                                            .setAction(EqService.ACTION_APPLY_PROFILE)
                                            .putExtra(EqService.EXTRA_PACKAGE, testPkg)
                                            .putExtra(EqService.EXTRA_MODE, info.mode.name)
                                    )
                                    "Test: $testPkg → ${info.mode} / Spiel=${games.isGame(testPkg)}"
                                } else {
                                    if (!ForegroundProbe.hasUsageAccess(this@MainActivity)) {
                                        ForegroundProbe.openUsageAccessSettings(this@MainActivity)
                                        "Usage-Zugriff fehlt — Einstellungen geöffnet. Danach erneut testen."
                                    } else {
                                        "Kein Vordergrund-Paket ermittelbar. Spiel starten und Accessibility nutzen."
                                    }
                                }
                                refreshStatus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Erkennung testen") }
                        probeResult?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF80DEEA))
                        }

                        Spacer(Modifier.height(12.dp))
                        Divider(color = Color(0xFF3A2A4A))
                        Spacer(Modifier.height(8.dp))

                        // ===== Spieleverwaltung =====
                        Text("Spieleverwaltung", color = Color(0xFFB388FF), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "GRID Autosport: com.feralinteractive.gridas (Built-in).\n" +
                                "Weitere Spiele manuell hinzufügen, falls nicht erkannt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC4B5D4)
                        )
                        // GRID status
                        val gridPkg = "com.feralinteractive.gridas"
                        val gridRegistered = games.isGame(gridPkg)
                        Text(
                            "GRID Autosport registriert: ${if (gridRegistered) "JA" else "NEIN"}",
                            color = if (gridRegistered) Color(0xFF81C784) else Color(0xFFE57373),
                            style = MaterialTheme.typography.bodySmall
                        )

                        key(gameListTick) {
                            val registered = games.allRegisteredGames()
                            Text("Registrierte Spiele (${registered.size}):", color = Color.White)
                            registered.take(12).forEach { (pkg, label) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                        Text(pkg, color = Color(0xFF78909C), style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (!games.builtinGames.containsKey(pkg)) {
                                        TextButton(onClick = {
                                            games.removeUserGame(pkg)
                                            gameListTick++
                                        }) { Text("Entf.", color = Color(0xFFE57373)) }
                                    }
                                }
                            }
                            if (registered.size > 12) {
                                Text("… +${registered.size - 12} weitere", color = Color(0xFF90A4AE))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showGamePicker = true }, modifier = Modifier.weight(1f)) {
                                Text("Spiel hinzufügen")
                            }
                            OutlinedButton(
                                onClick = {
                                    val n = games.autoRegisterInstalledGames()
                                    games.addUserGame(gridPkg, "GRID Autosport")
                                    games.addUserGame(
                                        "com.feralinteractive.gridautosport_edition_android",
                                        "GRID Autosport Custom Edition"
                                    )
                                    gameListTick++
                                    probeResult = "Scan: $n neue Spiele registriert (plus GRID)."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Spiele scannen") }
                        }
                        Text(
                            "Scan nutzt System-Flag FLAG_IS_GAME, Kategorie „Spiel“ und Paket-Heuristik — dann Auto-Umschaltung auf Gaming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE)
                        )

                        if (showGamePicker) {
                            AlertDialog(
                                onDismissRequest = { showGamePicker = false },
                                title = { Text("Installierte Apps") },
                                text = {
                                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                                        games.listInstalledLaunchable().forEach { app ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(app.label, color = Color.White)
                                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                                                }
                                                if (app.isGameFlag || games.isGame(app.packageName)) {
                                                    Text("✓", color = Color(0xFF81C784))
                                                } else {
                                                    TextButton(onClick = {
                                                        games.addUserGame(app.packageName, app.label)
                                                        gameListTick++
                                                    }) { Text("Als Spiel") }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showGamePicker = false }) { Text("Fertig") }
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Divider(color = Color(0xFF3A2A4A))
                        Spacer(Modifier.height(8.dp))

                        // ===== B) Audio =====
                        Text("B) Audioverarbeitung", color = Color(0xFFB388FF), style = MaterialTheme.typography.titleMedium)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4B5D4))
                        Text(
                            "Erkennung ≠ Klangänderung. Session-0/DynamicsProcessing greifen nicht bei allen Spielen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFAB91)
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Profil bearbeiten", color = Color(0xFFB388FF))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ProfileManager.Mode.DEFAULT to "Standard",
                                ProfileManager.Mode.GAMING to "Gaming",
                                ProfileManager.Mode.BROWSER_VIDEO to "Browser/Video"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = editingMode == mode,
                                    onClick = { loadMode(mode) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Equalizer aktiv", Modifier.weight(1f), color = Color.White)
                            Switch(checked = state.enabled, onCheckedChange = {
                                commit(state.copy(enabled = it))
                            })
                        }
                        EqCurve(state)
                        Spacer(Modifier.height(8.dp))
                        Text("Presets", color = Color(0xFFB388FF))
                        EqPresets.ALL.keys.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { name ->
                                    OutlinedButton(
                                        onClick = {
                                            commit(
                                                EqPresets.ALL[name]!!.copy(
                                                    presetName = name,
                                                    enabled = state.enabled
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(name, maxLines = 1) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { commit(EqState(enabled = state.enabled, presetName = "Flat")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Alles auf Flat") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    override fun onDestroy() {
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
