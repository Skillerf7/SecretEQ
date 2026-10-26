package com.secreteq.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.secreteq.MainActivity
import com.secreteq.data.EqPrefs

/**
 * Hält den System-EQ im Vordergrund, solange der Nutzer ihn aktiv will.
 * Garantiert nicht, dass andere Apps den Effekt anwenden — nur dass unsere Engine läuft.
 */
class EqService : Service() {
    private val engine = SystemEqEngine()
    private lateinit var prefs: EqPrefs

    override fun onCreate() {
        super.onCreate()
        prefs = EqPrefs(this)
        ensureChannel()
        engine.start()
        engine.apply(prefs.load())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPLY -> {
                engine.apply(prefs.load())
            }
            else -> {
                engine.apply(prefs.load())
            }
        }
        startForeground(NOTIF_ID, buildNotif())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Secret EQ")
            .setContentText(engine.statusText())
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Secret EQ", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL = "secret_eq"
        const val NOTIF_ID = 42
        const val ACTION_APPLY = "com.secreteq.APPLY"
        const val ACTION_STOP = "com.secreteq.STOP"
    }
}
