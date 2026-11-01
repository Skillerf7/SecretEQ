package com.secreteq.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.secreteq.MainActivity
import com.secreteq.data.EqPrefs
import com.secreteq.dsp.EqState
import com.secreteq.profile.ProfileManager

class EqService : Service() {
    private val engine = SystemEqEngine()
    private lateinit var prefs: EqPrefs
    private lateinit var profiles: ProfileManager

    override fun onCreate() {
        super.onCreate()
        prefs = EqPrefs(this)
        profiles = ProfileManager(this)
        ensureChannel()
        engine.start(this)
        val state = prefs.load()
        engine.apply(state)
        syncAudioStatus(state)
        RuntimeStatus.autoSwitch = profiles.autoSwitchEnabled
        RuntimeStatus.eqEnabled = state.enabled
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_EQ -> {
                val s = prefs.load()
                val next = s.copy(enabled = !s.enabled)
                prefs.save(next)
                engine.apply(next)
                RuntimeStatus.eqEnabled = next.enabled
                syncAudioStatus(next)
            }
            ACTION_TOGGLE_AUTO -> {
                profiles.autoSwitchEnabled = !profiles.autoSwitchEnabled
                RuntimeStatus.autoSwitch = profiles.autoSwitchEnabled
            }
            ACTION_APPLY -> {
                val state = prefs.load()
                engine.apply(state)
                RuntimeStatus.mode = ProfileManager.Mode.DEFAULT
                RuntimeStatus.profileName = state.presetName
                RuntimeStatus.packageName = null
                RuntimeStatus.appLabel = "Standard"
                RuntimeStatus.isGame = false
                RuntimeStatus.detectionSource = "MANUAL"
                RuntimeStatus.eqEnabled = state.enabled
                syncAudioStatus(state)
            }
            ACTION_APPLY_PROFILE -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE)
                val info = profiles.resolveForPackage(pkg)
                val label = profiles.games.labelFor(pkg ?: "")
                RuntimeStatus.packageName = pkg
                RuntimeStatus.appLabel = label
                RuntimeStatus.mode = info.mode
                RuntimeStatus.profileName = info.profileName
                RuntimeStatus.isGame = profiles.games.isGame(pkg ?: "")
                RuntimeStatus.detectionSource = intent.getStringExtra(EXTRA_SOURCE) ?: "A11Y"
                engine.apply(info.state)
                RuntimeStatus.eqEnabled = info.state.enabled
                syncAudioStatus(info.state)
                profiles.updateLastActive(info)
                Log.i(TAG, "profile ${info.profileName} for $pkg")
            }
            else -> {
                engine.apply(prefs.load())
                syncAudioStatus(prefs.load())
            }
        }
        RuntimeStatus.lastUpdateMs = System.currentTimeMillis()
        startForeground(NOTIF_ID, buildNotif())
        SecretEqTileService.requestUpdate(this)
        return START_STICKY
    }

    private fun syncAudioStatus(state: EqState) {
        val st = engine.status
        RuntimeStatus.audioEffectCreated = st.effectCreated
        RuntimeStatus.audioEffectEnabled = st.effectEnabled && state.enabled
        RuntimeStatus.audioBands = st.systemBandCount
        RuntimeStatus.externalAudioProven = st.externalEffectProven
        RuntimeStatus.audioSummary = st.shortText()
        RuntimeStatus.eqEnabled = state.enabled
    }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleEq = PendingIntent.getService(
            this, 1,
            Intent(this, EqService::class.java).setAction(ACTION_TOGGLE_EQ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAuto = PendingIntent.getService(
            this, 2,
            Intent(this, EqService::class.java).setAction(ACTION_TOGGLE_AUTO),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appLine = "Anwendung: ${RuntimeStatus.appLabel}"
        val detectLine = "Erkennung: ${RuntimeStatus.detectionLabel()}"
        val modeLine = "Profil: ${RuntimeStatus.modeLabel()} (${RuntimeStatus.profileName})"
        val eqLine = "Equalizer: ${if (RuntimeStatus.eqEnabled) "AKTIV" else "AUS"}"
        val audioLine = "Audio: ${RuntimeStatus.audioLine()}"
        val autoLine = "Auto-Switch: ${if (RuntimeStatus.autoSwitch) "AN" else "AUS"}"
        val pkgLine = RuntimeStatus.packageName?.let { "Paket: $it" } ?: ""

        val big = listOf(appLine, detectLine, modeLine, eqLine, audioLine, autoLine, pkgLine)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("SECRET EQ – AKTIV")
            .setContentText("$appLine · ${RuntimeStatus.modeLabel()}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, if (RuntimeStatus.eqEnabled) "EQ AUS" else "EQ AN", toggleEq)
            .addAction(0, if (RuntimeStatus.autoSwitch) "Auto AUS" else "Auto AN", toggleAuto)
            .addAction(0, "Öffnen", open)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Secret EQ Status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Dauerhafte Statusanzeige für Secret EQ"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SecretEQ.Service"
        const val CHANNEL = "secret_eq_status"
        const val NOTIF_ID = 42
        const val ACTION_APPLY = "com.secreteq.APPLY"
        const val ACTION_APPLY_PROFILE = "com.secreteq.APPLY_PROFILE"
        const val ACTION_TOGGLE_EQ = "com.secreteq.TOGGLE_EQ"
        const val ACTION_TOGGLE_AUTO = "com.secreteq.TOGGLE_AUTO"
        const val ACTION_STOP = "com.secreteq.STOP"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SOURCE = "source"
    }
}
